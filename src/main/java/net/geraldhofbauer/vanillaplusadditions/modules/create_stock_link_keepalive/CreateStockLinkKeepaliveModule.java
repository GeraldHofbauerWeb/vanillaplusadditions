package net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive.config.CreateStockLinkKeepaliveConfig;
import net.geraldhofbauer.vanillaplusadditions.util.blocktracking.PostLoadScheduler;
import net.geraldhofbauer.vanillaplusadditions.util.blocktracking.TrackedPositionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Create companion module: stops Factory Gauges from ordering against a stock level they cannot yet
 * see, in the first moments after their chunk loads.
 *
 * <p><strong>The bug, as measured on the live server.</strong> A gauge decides to order when its
 * filter item is below target and Create's own guard {@code waitingForNetwork} is false. That guard
 * comes from {@code totalLinks.size() - loadedLinks.size()}, while the stock figure comes from a
 * completely different structure - the {@code LogisticallyLinkedBehaviour.LINKS} cache, a
 * {@code TickBasedCache} with a twenty-tick timeout that every link refreshes from its own
 * {@code lazyTick()}. The two never agree right after a chunk load: the links have not re-registered
 * yet, so the inventory summary is empty, while the guard already reports a complete network. A
 * capture of the Factory Gauge at -3293/75/-2101 shows exactly that - all four panels at
 * {@code LastLevel: 0} with {@code Waiting: 0b} and {@code LastUnloadedLinks: 0} for one sample,
 * then {@code LastPromised} climbing to 1 and 2 while the copper-ingot stock ticked down from 2942,
 * with a full Item Vault standing right there.</p>
 *
 * <p><strong>The fix.</strong> {@code tickRequests()} returns early while the panel's {@code timer}
 * is above zero - before it reads any stock level at all. So for a short grace window after a chunk
 * load, this module simply calls the public {@code resetTimer()} on every panel of every tracked
 * gauge, once per tick. Nothing of Create's own bookkeeping is written to, no state is faked, and
 * once the window closes the panel carries on exactly as designed - by which time the links have
 * long since re-registered.</p>
 *
 * <p>No Create types are referenced at compile time; everything goes through the reflection-only
 * {@link FactoryPanelAccess} (the Ponder gotcha - see {@code project_create_integration}).</p>
 */
public class CreateStockLinkKeepaliveModule
        extends AbstractModule<CreateStockLinkKeepaliveModule, CreateStockLinkKeepaliveConfig> {

    /** Create's Factory Gauge block - the block carrying up to four factory panels. */
    private static final ResourceLocation GAUGE_BLOCK =
            ResourceLocation.fromNamespaceAndPath("create", "factory_gauge");

    private TrackedPositionRegistry registry;
    private PostLoadScheduler scheduler;

    /** Per dimension, the game time until which a gauge's panels are held back. */
    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> holdUntil = new HashMap<>();

    /**
     * Creates the module.
     */
    public CreateStockLinkKeepaliveModule() {
        super(
                "create_stock_link_keepalive",
                "Create Stock Link Keepalive",
                "Stops Create Factory Gauges from re-ordering stock they already have, right after a chunk load.",
                CreateStockLinkKeepaliveConfig::new
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
     * Whether a block state is Create's Factory Gauge.
     *
     * @param state The block state to test
     * @return true for a factory gauge block
     */
    private static boolean isGaugeBlock(BlockState state) {
        return GAUGE_BLOCK.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    @Override
    protected boolean shouldInitialize() {
        return isCreateLoaded();
    }

    @Override
    protected void onInitialize() {
        registry = new TrackedPositionRegistry(CreateStockLinkKeepaliveModule::isGaugeBlock);
        scheduler = new PostLoadScheduler();
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Create Stock Link Keepalive module initialized (Create reflection available: {})",
                FactoryPanelAccess.isAvailable());
    }

    /**
     * Remembers the gauges in a freshly loaded chunk and queues them for the grace window. May fire
     * off the server thread during world generation, which is why the deadline is only computed
     * once the position reaches the server tick.
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
            getLogger().info("[create_stock_link_keepalive] discovery: {} gauge(s) in chunk {} of {}: {}",
                    found.size(), event.getChunk().getPos(), level.dimension().location(), found);
        }
        scheduler.enqueue(level, found);
    }

    /**
     * Drops tracked gauges of an unloading chunk - they are rediscovered on re-load.
     *
     * @param event The chunk unload event
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunkPos = event.getChunk().getPos();
        registry.forgetChunk(level, chunkPos);
        scheduler.forgetChunk(level, chunkPos);
        Map<BlockPos, Long> byLevel = holdUntil.get(level.dimension());
        if (byLevel != null) {
            byLevel.keySet().removeIf(pos -> chunkPos.x == (pos.getX() >> 4) && chunkPos.z == (pos.getZ() >> 4));
        }
    }

    /**
     * Remembers a newly placed gauge. No grace window is needed for a fresh placement - the player
     * is standing right there and the network is live.
     *
     * @param event The block place event
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!isModuleEnabled() || registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registry.onBlockPlaced(level, event.getPos(), event.getPlacedBlock());
    }

    /**
     * Forgets a broken gauge.
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
            Map<BlockPos, Long> byLevel = holdUntil.get(level.dimension());
            if (byLevel != null) {
                byLevel.remove(event.getPos());
            }
        }
    }

    /**
     * Opens the grace window for newly loaded gauges, then holds every panel still inside its
     * window.
     *
     * @param event The server tick event
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!isModuleEnabled() || registry == null) {
            return;
        }
        CreateStockLinkKeepaliveConfig config = getConfig();
        if (!config.isHoldOnChunkLoadEnabled()) {
            scheduler.clearAll();
            holdUntil.clear();
            return;
        }

        // Delay 0: the scheduler is used only to get chunk-load positions onto the server thread,
        // where reading the level's game time is safe. The window itself starts right here.
        scheduler.runDue(event.getServer(), 0, (level, pos) -> openWindow(level, pos, config.getGraceTicks()));

        for (ServerLevel level : event.getServer().getAllLevels()) {
            holdPanels(level, config);
        }
    }

    /**
     * Starts the grace window for a single gauge.
     *
     * @param level      The server level
     * @param pos        The gauge position
     * @param graceTicks How long the window lasts
     */
    private void openWindow(ServerLevel level, BlockPos pos, int graceTicks) {
        if (graceTicks <= 0) {
            return;
        }
        holdUntil.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .merge(pos, level.getGameTime() + graceTicks, Math::max);
    }

    /**
     * Holds every panel of every gauge still inside its grace window, and forgets the ones whose
     * window has closed.
     *
     * @param level  The server level
     * @param config The module config
     */
    private void holdPanels(ServerLevel level, CreateStockLinkKeepaliveConfig config) {
        Map<BlockPos, Long> byLevel = holdUntil.get(level.dimension());
        if (byLevel == null || byLevel.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> it = byLevel.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos pos = entry.getKey();
            if (entry.getValue() <= now) {
                if (config.shouldDebugLog()) {
                    logWindowClosed(level, pos);
                }
                it.remove();
                continue;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!registry.isStillTracked(level, pos)) {
                registry.remove(level, pos);
                it.remove();
                continue;
            }
            FactoryPanelAccess.holdTimers(level.getBlockEntity(pos));
        }
    }

    /**
     * Logs what the panels see at the moment their grace window closes - the value that Create would
     * have acted on had the module not held them.
     *
     * @param level The server level
     * @param pos   The gauge position
     */
    private void logWindowClosed(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        String state = FactoryPanelAccess.describe(blockEntity);
        if (!state.isEmpty()) {
            getLogger().info("[create_stock_link_keepalive] window closed at {} in {}: {}",
                    pos, level.dimension().location(), state);
        }
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
        holdUntil.remove(level.dimension());
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
        holdUntil.clear();
    }
}
