package net.geraldhofbauer.vanillaplusadditions.modules.chunk_reset;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChunkResetModule extends AbstractModule<ChunkResetModule, AbstractModuleConfig.DefaultModuleConfig<ChunkResetModule>> {

    /**
     * A reset awaiting confirmation. The dimension is part of the record because the confirm
     * arrives as a separate command: without it, confirming after a dimension change would apply
     * the remembered coordinates to whatever level the player is standing in now.
     */
    private record PendingReset(ChunkPos center, int radius, ResourceKey<Level> dimension) { }

    private final Map<UUID, PendingReset> pendingResets = new HashMap<>();

    /**
     * Chunks whose data still has to be deleted, per level, with the game time at which waiting is
     * abandoned. Deleting the region entry of a chunk that is still in memory does not stick - the
     * next save writes it straight back - so a loaded target is parked here instead.
     */
    private final Map<ResourceKey<Level>, Map<ChunkPos, Long>> pendingDeletions = new HashMap<>();

    /**
     * Chunks that have just been unloaded, with the game time at which their entry is cleared.
     *
     * <p>The delay is the whole point. {@code ChunkMap.processUnloads} takes the chunk out of the
     * map that {@code getVisibleChunkIfPresent} reads, and only <em>afterwards</em> does
     * {@code scheduleUnload} call {@code save(chunkaccess)}. Polling "is it still loaded?" therefore
     * hits a window in which the chunk already looks gone but has not been written yet - and the
     * write that follows puts it straight back over the cleared entry. Waiting a few ticks past the
     * unload event lands the deletion behind that save, where it holds.</p>
     */
    private final Map<ResourceKey<Level>, Map<ChunkPos, Long>> unloadedDeletions = new HashMap<>();

    private static final int MAX_RADIUS = 5;

    /** How long a queued chunk is waited on before giving up: 12000 ticks = ten minutes. */
    private static final long PENDING_DELETE_TIMEOUT_TICKS = 12000L;

    /** How often the give-up deadline is examined; once a second is plenty for something this slow. */
    private static final int PENDING_DELETE_CHECK_INTERVAL = 20;

    /**
     * Ticks to wait after a chunk unloads before clearing its entry.
     *
     * <p>Long enough to be behind {@code ChunkMap.save}, which runs from the unload queue a moment
     * after {@code ChunkEvent.Unload} is posted. The write itself goes through the same
     * {@code IOWorker}, whose {@code store} replaces whatever is still pending for that chunk, so
     * arriving later is exactly what is wanted.</p>
     */
    private static final long POST_UNLOAD_DELETE_DELAY_TICKS = 5L;

    // Cached via type-safe lookup so the method name doesn't need to be hardcoded
    private static volatile Method chunkStorageWriteMethod;

    public ChunkResetModule() {
        super("chunk_reset",
                "Chunk Reset Command",
                "Provides a command to delete and regenerate chunks from world generation",
                AbstractModuleConfig::createDefault
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Chunk Reset module initialized - /chunkreset command ready!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("chunkreset")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> executeChunkReset(ctx, 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                                .executes(ctx -> executeChunkReset(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                        )
                        .then(Commands.literal("confirm")
                                .executes(this::executeConfirm)
                        )
                        .then(Commands.literal("cancel")
                                .executes(this::executeCancel)
                        )
        );
    }

    /**
     * Drops a player's pending reset when they leave. Without this the entry would outlive the
     * session and a confirm from a later login would still fire.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingResets.remove(event.getEntity().getUUID());
    }

    /**
     * Notices that a watched chunk has unloaded and schedules its deletion a few ticks later.
     *
     * @param event the unload event
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (pendingDeletions.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<ChunkPos, Long> watched = pendingDeletions.get(level.dimension());
        ChunkPos pos = event.getChunk().getPos();
        if (watched == null || watched.remove(pos) == null) {
            return;
        }
        if (watched.isEmpty()) {
            pendingDeletions.remove(level.dimension());
        }
        unloadedDeletions.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(pos, level.getGameTime() + POST_UNLOAD_DELETE_DELAY_TICKS);
        getLogger().info("Chunk [{}, {}] in {} unloaded; clearing its entry in {} ticks, once the "
                        + "unload save is through.",
                pos.x, pos.z, level.dimension().location(), POST_UNLOAD_DELETE_DELAY_TICKS);
    }

    /**
     * Deletes queued chunks as soon as they leave memory.
     *
     * <p>Deliberately not gated on {@code isModuleEnabled()}: everything in the queue was already
     * asked for and confirmed by an operator. Switching the module off should stop it taking new
     * orders, not make it forget one it accepted.</p>
     *
     * @param event the server tick
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!unloadedDeletions.isEmpty()) {
            drainUnloaded(server);
        }
        if (pendingDeletions.isEmpty()) {
            return;
        }
        // Every tick, not on an interval: the clean flag has to be taken away again as fast as the
        // world sets it, or an autosave lands in between and undoes the whole thing.

        Iterator<Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>>> levels =
                pendingDeletions.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>> byLevel = levels.next();
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                continue; // dimension not currently loaded - keep waiting
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<ChunkPos, Long>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ChunkPos, Long> entry = it.next();
                ChunkPos pos = entry.getKey();
                if (now >= entry.getValue()) {
                    getLogger().warn("Stopped holding chunk [{}, {}] in {} after ten minutes. Its "
                                    + "entry was cleared, but from now on a save may write it back "
                                    + "again. Run /chunkreset once more if it is still there.",
                            pos.x, pos.z, byLevel.getKey().location());
                    it.remove();
                    continue;
                }
                holdClean(level, pos);
            }
            if (byLevel.getValue().isEmpty()) {
                levels.remove();
            }
        }
    }

    /**
     * Keeps a chunk marked as saved so that nothing writes it back over the cleared entry.
     *
     * <p>This is the whole trick, and it took two wrong attempts to get to. Waiting for the chunk to
     * unload cannot work: {@code ChunkMap.processUnloads} drops it from the visible map and only
     * <em>then</em> does {@code scheduleUnload} save it, and on a server with view-distance 32 the
     * chunk is usually back in memory before that plays out - measured on games2, 291 ms between the
     * unload and the reload.</p>
     *
     * <p>So instead of racing the save, take it away. {@code ChunkMap.save} starts with
     * {@code if (!chunk.isUnsaved()) return false;}, so a chunk that is marked clean is never
     * written - not by the autosave, not by the unload, not at shutdown. Clearing the flag every
     * tick keeps it that way even while the chunk is being modified; anything a player does in there
     * is discarded, which is exactly what a reset means.</p>
     *
     * <p>When the chunk is finally loaded again from disk, the entry is gone and it regenerates.</p>
     *
     * @param level the level the chunk belongs to
     * @param pos   the chunk being held
     */
    private static void holdClean(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) {
            chunk.setUnsaved(false);
        }
    }

    /**
     * Clears the entries of chunks whose unload save is far enough behind them.
     *
     * @param server the running server
     */
    private void drainUnloaded(MinecraftServer server) {
        Iterator<Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>>> levels =
                unloadedDeletions.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>> byLevel = levels.next();
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<ChunkPos, Long>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ChunkPos, Long> entry = it.next();
                if (now < entry.getValue()) {
                    continue;
                }
                ChunkPos pos = entry.getKey();
                if (resetSingleChunk(level, pos)) {
                    getLogger().info("Chunk [{}, {}] in {} unloaded and its entry is cleared - it "
                                    + "regenerates the next time it is loaded.",
                            pos.x, pos.z, byLevel.getKey().location());
                }
                if (isChunkLoaded(level, pos)) {
                    // Already back in memory. It was loaded from a cleared entry, so what is in
                    // memory is the fresh chunk - nothing left to do.
                    getLogger().info("Chunk [{}, {}] in {} is already loaded again, freshly generated.",
                            pos.x, pos.z, byLevel.getKey().location());
                }
                it.remove();
            }
            if (byLevel.getValue().isEmpty()) {
                levels.remove();
            }
        }
    }

    /**
     * Warns about resets that never completed, while there is still a log to warn into.
     *
     * @param event the shutdown event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        int outstanding = pendingDeletions.values().stream().mapToInt(Map::size).sum()
                + unloadedDeletions.values().stream().mapToInt(Map::size).sum();
        if (outstanding > 0) {
            getLogger().warn("Server is stopping with {} chunk(s) still being watched. Their entries "
                    + "were cleared when the command ran, but they stayed in memory and the shutdown "
                    + "save writes them back - run /chunkreset again after the restart, when nothing "
                    + "has loaded them yet.", outstanding);
        }
        pendingDeletions.clear();
        unloadedDeletions.clear();
        pendingResets.clear();
    }

    private int executeChunkReset(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ChunkPos center = player.chunkPosition();
        PendingReset replaced = pendingResets.put(player.getUUID(),
                new PendingReset(center, radius, source.getLevel().dimension()));

        int side = 2 * radius + 1;
        int chunkCount = side * side;

        source.sendSuccess(() -> Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .withStyle(ChatFormatting.DARK_RED), false);
        source.sendSuccess(() -> Component.literal("⚠ Chunk Reset Warning")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);

        if (radius == 0) {
            source.sendSuccess(() -> Component.literal("Chunk [x=" + center.x + ", z=" + center.z + "] will be ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("PERMANENTLY").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" deleted and regenerated.").withStyle(ChatFormatting.YELLOW)), false);
        } else {
            source.sendSuccess(() -> Component.literal(chunkCount + " chunks (" + side + "×" + side
                            + ") around [x=" + center.x + ", z=" + center.z + "] will be ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("PERMANENTLY").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" deleted and regenerated.").withStyle(ChatFormatting.YELLOW)), false);
        }
        source.sendSuccess(() -> Component.literal("All blocks, entities and structures will be LOST!")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);

        if (replaced != null) {
            source.sendSuccess(() -> Component.literal("(This replaces your earlier request for [x="
                            + replaced.center().x + ", z=" + replaced.center().z + "] - only one reset "
                            + "can be pending at a time.)")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        MutableComponent confirmBtn = Component.literal("[✓ Confirm Reset]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chunkreset confirm"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to permanently reset the chunk(s)"))));
        MutableComponent cancelBtn = Component.literal("  [✗ Cancel]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chunkreset cancel"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Cancel chunk reset"))));

        source.sendSuccess(() -> confirmBtn.copy().append(cancelBtn), false);
        return 1;
    }

    private int executeConfirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PendingReset pending = pendingResets.remove(player.getUUID());

        if (pending == null) {
            source.sendFailure(Component.literal("No pending chunk reset. Run /chunkreset first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel serverLevel = source.getLevel();
        if (!serverLevel.dimension().equals(pending.dimension())) {
            source.sendFailure(Component.literal("That reset was requested in "
                            + pending.dimension().location() + ", but you are in "
                            + serverLevel.dimension().location()
                            + ". Run /chunkreset again where you want it to happen.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        performReset(serverLevel, pending.center(), pending.radius(), source);
        return 1;
    }

    private int executeCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (pendingResets.remove(player.getUUID()) != null) {
            source.sendSuccess(() -> Component.literal("Chunk reset cancelled.")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("No pending chunk reset to cancel.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private void performReset(ServerLevel level, ChunkPos center, int radius, CommandSourceStack source) {
        Set<ChunkPos> targets = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                targets.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        // Move players out of affected chunks before deleting. The destination sits one chunk
        // beyond the square's southern edge; its surface is looked up so nobody is dropped inside
        // stone or left hanging at whatever height they happened to be standing at.
        double safeX = center.getMiddleBlockX();
        double safeZ = ((center.z + radius) * 16.0) + 17.0;
        double safeY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(safeX, level.getMinBuildHeight(), safeZ)).getY();
        for (ServerPlayer nearby : level.players()) {
            if (targets.contains(nearby.chunkPosition())) {
                nearby.teleportTo(safeX, safeY, safeZ);
                nearby.sendSystemMessage(Component.literal("⚠ You were moved out of a chunk being reset.")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        // Every target is cleared right now. A chunk that is still in memory is ALSO queued,
        // because this write alone would not last: ChunkMap.save puts it back over the cleared
        // entry at the next autosave, at unload and at shutdown. The queue repeats the write once
        // the chunk has left memory, which is the moment it actually sticks.
        //
        // Queueing *instead* of writing was wrong, and obviously so in hindsight: you run this
        // command standing in the chunk, the rescue teleport moves you one chunk away, and there it
        // sits inside your view distance never unloading - so nothing was deleted at all.
        long now = level.getGameTime();
        Map<ChunkPos, Long> queue = pendingDeletions.computeIfAbsent(level.dimension(),
                key -> new HashMap<>());
        int deleted = 0;
        int queued = 0;
        int failed = 0;
        for (ChunkPos pos : targets) {
            if (!resetSingleChunk(level, pos)) {
                failed++;
                continue;
            }
            deleted++;
            if (isChunkLoaded(level, pos)) {
                queue.put(pos, now + PENDING_DELETE_TIMEOUT_TICKS);
                queued++;
            } else {
                queue.remove(pos);
            }
        }
        if (queue.isEmpty()) {
            pendingDeletions.remove(level.dimension());
        }
        getLogger().info("/chunkreset at [{}, {}] r={} in {}: {} entries cleared now, {} still loaded "
                        + "and queued until they unload, {} failed.",
                center.x, center.z, radius, level.dimension().location(), deleted, queued, failed);

        int total = targets.size();
        if (deleted == 0) {
            source.sendFailure(Component.literal("✗ Nothing was reset - every write failed. "
                            + "See the server log for the reason.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        int finalDeleted = deleted;
        String where = "[x=" + center.x + ", z=" + center.z + "]";
        source.sendSuccess(() -> Component.literal("✓ " + finalDeleted + "/" + total
                        + (total == 1 ? " chunk " : " chunks ") + "at " + where + " deleted.")
                .withStyle(ChatFormatting.GREEN), true);
        if (queued > 0) {
            int finalQueued = queued;
            source.sendSuccess(() -> Component.literal("⚠ " + finalQueued + " of them are still in "
                            + "memory, blocks and all - that is why you can still see them. They are "
                            + "now held unsaved, so nothing can write them back. Leave the area and "
                            + "come back and they will have regenerated. Anything built there in the "
                            + "meantime is discarded.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.literal("They will regenerate when next loaded.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (failed > 0) {
            int finalFailed = failed;
            source.sendFailure(Component.literal("✗ " + finalFailed + " of " + total
                            + " chunks could not be deleted - see the server log.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Whether the given chunk is currently held in memory by the level.
     *
     * @param level the level to ask
     * @param pos   the chunk to look up
     * @return true if the chunk is loaded and therefore still writable by the next save
     */
    private static boolean isChunkLoaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos.toLong()) != null;
    }

    private boolean resetSingleChunk(ServerLevel level, ChunkPos pos) {
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            ChunkMap chunkMap = chunkSource.chunkMap;

            // Drop vanilla's /forceload ticket for this chunk if it has one. That is all this
            // call does (ServerLevel.setChunkForced only edits ForcedChunksSavedData) - mod
            // tickets, spawn chunks and player tickets keep the chunk loaded regardless.
            level.setChunkForced(pos.x, pos.z, false);

            // Write null to queue deletion in the region file on next flush.
            // We search by parameter types so the call is robust against mapping differences.
            Method writeMethod = resolveChunkStorageWriteMethod();
            writeMethod.invoke(chunkMap, pos, null);
            return true;
        } catch (Exception e) {
            getLogger().error("Failed to reset chunk [{}, {}]: {}", pos.x, pos.z, e.getMessage(), e);
            return false;
        }
    }

    private static Method resolveChunkStorageWriteMethod() throws NoSuchMethodException {
        if (chunkStorageWriteMethod != null) {
            return chunkStorageWriteMethod;
        }
        for (Method m : ChunkStorage.class.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0] == ChunkPos.class && params[1] == CompoundTag.class) {
                m.setAccessible(true);
                chunkStorageWriteMethod = m;
                return m;
            }
        }
        throw new NoSuchMethodException("ChunkStorage write(ChunkPos, CompoundTag) not found");
    }
}
