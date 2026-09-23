package net.geraldhofbauer.vanillaplusadditions.modules.create_redstone_link_rebinder;

import com.mojang.brigadier.Command;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.create_redstone_link_rebinder.config.CreateRedstoneLinkRebinderConfig;
import net.geraldhofbauer.vanillaplusadditions.util.blocktracking.PostLoadScheduler;
import net.geraldhofbauer.vanillaplusadditions.util.blocktracking.TrackedPositionRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Create companion module: re-registers redstone links that are missing from Create's link network.
 *
 * <p>Create keeps its link network purely in memory and builds it from
 * {@code LinkBehaviour.initialize()}, which is reached only through {@code SmartBlockEntity.tick()}
 * behind a non-persisted {@code initialized} flag. The registration therefore happens on the block
 * entity's <em>first tick</em> and is rebuilt from scratch after every reload. A link that loads but
 * never ticks silently stays out of the network - and because {@code neighborChanged} needs no tick,
 * its {@code Transmit} value still tracks the lever next to it, so nothing about the block looks
 * wrong. The receiver simply never hears it.</p>
 *
 * <p>This module remembers where the links are (chunk load, placement), then re-checks them shortly
 * after their chunk loads and periodically afterwards: if a link is not in the member set of its own
 * frequency, it is added. That is idempotent - the network is a {@code Set}, and Create's
 * {@code addToNetwork} ends in {@code updateNetworkOf}, so a transmitter rejoining while it holds a
 * signal pushes that signal to its receivers immediately.</p>
 *
 * <p>No Create types are referenced at compile time; everything goes through the reflection-only
 * {@link RedstoneLinkNetworkAccess} (the Ponder gotcha - see {@code project_create_integration}).</p>
 */
public class CreateRedstoneLinkRebinderModule
        extends AbstractModule<CreateRedstoneLinkRebinderModule, CreateRedstoneLinkRebinderConfig> {

    /** Create's redstone link block - the only block carrying the link behaviour we repair. */
    private static final ResourceLocation LINK_BLOCK = ResourceLocation.fromNamespaceAndPath("create", "redstone_link");

    private TrackedPositionRegistry registry;
    private PostLoadScheduler scheduler;
    private int sweepCountdown;

    /**
     * Creates the module.
     */
    public CreateRedstoneLinkRebinderModule() {
        super(
                "create_redstone_link_rebinder",
                "Create Redstone Link Rebinder",
                "Re-registers Create redstone links that dropped out of their frequency network after a chunk reload.",
                CreateRedstoneLinkRebinderConfig::new
        );
    }

    /**
     * Whether the Create mod is present.
     *
     * @return true if Create is loaded
     */
    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    /**
     * Whether a block state is Create's redstone link.
     *
     * @param state The block state to test
     * @return true for a redstone link block
     */
    private static boolean isLinkBlock(BlockState state) {
        return LINK_BLOCK.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    @Override
    protected boolean shouldInitialize() {
        return isCreateLoaded();
    }

    @Override
    protected void onInitialize() {
        registry = new TrackedPositionRegistry(CreateRedstoneLinkRebinderModule::isLinkBlock);
        scheduler = new PostLoadScheduler();
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Create Redstone Link Rebinder module initialized (Create reflection available: {})",
                RedstoneLinkNetworkAccess.isAvailable());
    }

    /**
     * Remembers the links in a freshly loaded chunk and queues them for the targeted post-load
     * check. May fire off the server thread during world generation.
     *
     * @param event The chunk load event
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!isModuleEnabled() || registry == null) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        List<BlockPos> found = registry.discoverChunk(level, chunk);
        if (found.isEmpty()) {
            return;
        }
        if (getConfig().shouldDebugLog()) {
            getLogger().info("[create_redstone_link_rebinder] discovery: {} link(s) in chunk {} of {}: {}",
                    found.size(), event.getChunk().getPos(), level.dimension().location(), found);
        }
        scheduler.enqueue(level, found);
    }

    /**
     * Drops tracked links of an unloading chunk - they are rediscovered on re-load.
     *
     * @param event The chunk unload event
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registry.forgetChunk(level, event.getChunk().getPos());
        scheduler.forgetChunk(level, event.getChunk().getPos());
    }

    /**
     * Remembers a newly placed link and queues it for a check. A fresh placement normally registers
     * itself on the next tick; the check is idempotent and costs nothing.
     *
     * @param event The block place event
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!isModuleEnabled() || registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (registry.onBlockPlaced(level, event.getPos(), event.getPlacedBlock())) {
            scheduler.enqueue(level, List.of(event.getPos().immutable()));
        }
    }

    /**
     * Forgets a broken link.
     *
     * @param event The block break event
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (registry.onBlockBroken(level, event.getPos(), event.getState())) {
            scheduler.forget(level, event.getPos());
        }
    }

    /**
     * Runs the due post-load checks and, on its own cadence, the periodic sweep.
     *
     * @param event The server tick event
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!isModuleEnabled() || registry == null) {
            return;
        }
        CreateRedstoneLinkRebinderConfig config = getConfig();

        scheduler.runDue(event.getServer(), config.getPostLoadDelayTicks(), (level, pos) -> {
            if (config.isAutoRebindOnChunkLoadEnabled()) {
                checkOne(level, pos, "post-load");
            }
        });

        if (--sweepCountdown > 0) {
            return;
        }
        sweepCountdown = config.getCheckIntervalTicks();
        if (config.isAutoRebindDuringSweepEnabled()) {
            sweep(event.getServer(), "sweep");
        }
    }

    /**
     * Walks every tracked link in every loaded chunk and re-registers the ones that are missing.
     *
     * @param server The running server
     * @param reason Label for the log line
     * @return how many links were re-registered
     */
    private int sweep(MinecraftServer server, String reason) {
        int rebound = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : new ArrayList<>(registry.positionsIfPresent(level))) {
                if (checkOne(level, pos, reason)) {
                    rebound++;
                }
            }
        }
        return rebound;
    }

    /**
     * Checks a single tracked position and re-registers the link if it fell out of its network.
     * Positions whose chunk is not loaded are skipped; positions that no longer hold a link are
     * dropped from the registry.
     *
     * @param level  The server level
     * @param pos    The tracked position
     * @param reason Label for the log line
     * @return true if the link was re-registered
     */
    private boolean checkOne(ServerLevel level, BlockPos pos, String reason) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!registry.isStillTracked(level, pos)) {
            registry.remove(level, pos);
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        RedstoneLinkNetworkAccess.Result result = RedstoneLinkNetworkAccess.rebindIfMissing(level, blockEntity);
        if (result == RedstoneLinkNetworkAccess.Result.REBOUND) {
            getLogger().info("[create_redstone_link_rebinder] {}: re-registered link at {} in {}",
                    reason, pos, level.dimension().location());
            return true;
        }
        if (result == RedstoneLinkNetworkAccess.Result.FAILED) {
            getLogger().warn("[create_redstone_link_rebinder] {}: could not inspect link at {} in {}",
                    reason, pos, level.dimension().location());
        }
        return false;
    }

    /**
     * Registers {@code /vparelink} (op-only): sweeps every tracked link in loaded chunks on demand
     * and reports how many were missing from their network.
     *
     * @param event The command registration event
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!isCreateLoaded()) {
            return;
        }
        event.getDispatcher().register(Commands.literal("vparelink")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    if (!isModuleEnabled() || registry == null) {
                        ctx.getSource().sendFailure(
                                Component.literal("Redstone Link Rebinder module is disabled."));
                        return 0;
                    }
                    getLogger().info("[create_redstone_link_rebinder] /vparelink invoked by {}",
                            ctx.getSource().getTextName());
                    int rebound = sweep(ctx.getSource().getServer(), "command");
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[Redstone Link Rebinder] Re-registered " + rebound
                                    + " link(s) that had fallen out of their network."), true);
                    return rebound == 0 ? Command.SINGLE_SUCCESS : rebound;
                }));
    }

    /**
     * Drops all state of an unloading level.
     *
     * @param event The level unload event
     */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registry.forgetLevel(level);
        scheduler.forgetLevel(level);
    }

    /**
     * Clears all tracked state once the server has stopped.
     *
     * @param event The server stopped event
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (registry == null) {
            return;
        }
        registry.clearAll();
        scheduler.clearAll();
    }
}
