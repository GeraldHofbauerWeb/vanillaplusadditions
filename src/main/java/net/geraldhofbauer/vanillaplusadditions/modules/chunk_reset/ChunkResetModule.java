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
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChunkResetModule extends AbstractModule<ChunkResetModule, AbstractModuleConfig.DefaultModuleConfig<ChunkResetModule>> {

    private record PendingReset(ChunkPos center, int radius) { }

    private final Map<UUID, PendingReset> pendingResets = new HashMap<>();
    private static final int MAX_RADIUS = 5;

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

    private int executeChunkReset(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ChunkPos center = player.chunkPosition();
        pendingResets.put(player.getUUID(), new PendingReset(center, radius));

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
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("This command can only be used in a server level.")
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

        // Move players out of affected chunks before deleting
        double safeX = center.getMiddleBlockX();
        double safeZ = ((center.z + radius) * 16.0) + 17.0;
        for (ServerPlayer nearby : level.players()) {
            if (targets.contains(nearby.chunkPosition())) {
                nearby.teleportTo(safeX, nearby.getY(), safeZ);
                nearby.sendSystemMessage(Component.literal("⚠ You were moved out of a chunk being reset.")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        int succeeded = 0;
        for (ChunkPos pos : targets) {
            if (resetSingleChunk(level, pos)) {
                succeeded++;
            }
        }

        int finalSucceeded = succeeded;
        int total = targets.size();
        if (radius == 0) {
            source.sendSuccess(() -> Component.literal("✓ Chunk [x=" + center.x + ", z=" + center.z
                            + "] reset. It will regenerate when next loaded.")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendSuccess(() -> Component.literal("✓ " + finalSucceeded + "/" + total
                            + " chunks reset around [x=" + center.x + ", z=" + center.z
                            + "]. They will regenerate when next loaded.")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    private boolean resetSingleChunk(ServerLevel level, ChunkPos pos) {
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            ChunkMap chunkMap = chunkSource.chunkMap;

            // Remove any forced loading ticket first
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
