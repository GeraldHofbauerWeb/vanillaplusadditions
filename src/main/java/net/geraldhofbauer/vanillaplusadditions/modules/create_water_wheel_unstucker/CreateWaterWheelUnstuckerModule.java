package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.mojang.brigadier.Command;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker.config.CreateWaterWheelUnstuckerConfig;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

import java.util.List;

/**
 * Create companion module: water wheels sometimes stop rotating after their chunk is unloaded and
 * reloaded (kinetic-network / flow-score desync - a known Create issue that players normally fix by
 * wrenching or re-placing a block). This module remembers wheel positions as chunks load (which
 * covers server start) and on placements, then periodically checks ONLY those positions and kicks
 * stalled wheels back into rotation - a soft flow recompute first, escalating to a full kinetic
 * detach/re-attach. Wheels that are overstressed or genuinely without water flow are left alone.
 *
 * <p>This class contains no Create imports; it only initializes when Create is present. Compile-safe
 * Create <em>Block</em> references live in {@link WaterWheelRegistry}, and all block-entity access
 * goes through the reflection-only {@link WaterWheelKinetics} (Ponder gotcha).</p>
 */
public class CreateWaterWheelUnstuckerModule
        extends AbstractModule<CreateWaterWheelUnstuckerModule, CreateWaterWheelUnstuckerConfig> {

    private WaterWheelRegistry registry;
    private WaterWheelStallManager stallManager;

    /**
     * Creates the module.
     */
    public CreateWaterWheelUnstuckerModule() {
        super(
                "create_water_wheel_unstucker",
                "Create Water Wheel Unstucker",
                "Detects Create water wheels that stalled after a chunk reload and kicks them back into rotation.",
                CreateWaterWheelUnstuckerConfig::new
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

    @Override
    protected boolean shouldInitialize() {
        return isCreateLoaded();
    }

    @Override
    protected void onInitialize() {
        registry = new WaterWheelRegistry();
        stallManager = new WaterWheelStallManager(this, registry);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Create Water Wheel Unstucker module initialized (Create reflection available: {})",
                WaterWheelKinetics.isAvailable());
    }

    /**
     * Discovers wheels in freshly loaded chunks (block-entity map only - cheap) and queues them for
     * a targeted post-load stall check. May fire off the server thread during world generation.
     *
     * @param event The chunk load event
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        List<BlockPos> found = registry.discoverChunk(level, chunk);
        if (!found.isEmpty()) {
            if (getConfig().shouldDebugLog()) {
                getLogger().info("[create_water_wheel_unstucker] discovery: {} wheel(s) in chunk {} of {}: {}",
                        found.size(), event.getChunk().getPos(), level.dimension().location(), found);
            }
            stallManager.enqueuePostLoadCheck(level, found);
        }
    }

    /**
     * Drops tracked wheels of an unloading chunk - they are rediscovered on re-load.
     *
     * @param event The chunk unload event
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registry.forgetChunk(level, event.getChunk().getPos());
        stallManager.forgetChunk(level, event.getChunk().getPos());
    }

    /**
     * Registers newly placed wheels and queues them for a delayed check (idempotent and cheap;
     * fresh placements normally initialize themselves).
     *
     * @param event The block place event
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!isModuleEnabled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (WaterWheelRegistry.isWheelBlock(event.getPlacedBlock())) {
            registry.onBlockPlaced(level, event.getPos(), event.getPlacedBlock());
            stallManager.enqueuePostLoadCheck(level, List.of(event.getPos().immutable()));
        }
    }

    /**
     * Unregisters broken wheels (structural shell breaks resolve to the large wheel's center).
     *
     * @param event The block break event
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (registry == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registry.onBlockBroken(level, event.getPos(), event.getState());
        stallManager.forgetWheel(level, event.getPos());
    }

    /**
     * Per-tick driver for the stall manager (internally throttled).
     *
     * @param event The server tick event
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        stallManager.tick(event.getServer());
    }

    /**
     * Registers {@code /vpaunstuck} (op-only): re-initialises all tracked, stalled water wheels in
     * loaded chunks on demand (break + re-place, preserving orientation and material).
     *
     * @param event The command registration event
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!isCreateLoaded()) {
            return;
        }
        event.getDispatcher().register(Commands.literal("vpaunstuck")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    if (!isModuleEnabled() || stallManager == null) {
                        ctx.getSource().sendFailure(
                                Component.literal("Water Wheel Unstucker module is disabled."));
                        return 0;
                    }
                    getLogger().info("[create_water_wheel_unstucker] /vpaunstuck invoked by {}",
                            ctx.getSource().getTextName());
                    int started = stallManager.unstickAll(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[Water Wheel Unstucker] Re-initialising " + started
                                    + " stalled water wheel(s) in loaded chunks."), true);
                    return started == 0 ? Command.SINGLE_SUCCESS : started;
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
        stallManager.forgetLevel(level);
    }

    /**
     * Clears everything when the server stopped.
     *
     * @param event The server stopped event
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (registry == null) {
            return;
        }
        registry.clearAll();
        stallManager.clearAll();
    }
}
