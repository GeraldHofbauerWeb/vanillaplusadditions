package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.command.BluemapSignsCommands;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.compat.BlueMapBridge;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.config.BluemapSignsConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Turns {@code [bm]} signs into curated BlueMap map-markers and adds {@code /bmsigns} management
 * (list/add/remove/edit/help). Server-side; fully inert (and BlueMap-class-free) when BlueMap is not
 * installed. Markers persist per-dimension in {@link MapSignData}; the BlueMap MarkerSet is mirrored
 * via the isolated {@link BlueMapBridge}.
 */
public class BluemapSignsModule extends AbstractModule<BluemapSignsModule, BluemapSignsConfig> {

    private static BluemapSignsModule instance;

    private final MapSignManager manager = new MapSignManager();
    private boolean bluemapPresent;

    public BluemapSignsModule() {
        super("bluemap_signs",
                "BlueMap Signs",
                "Turns [bm] signs into curated BlueMap markers; /bmsigns to manage them.",
                BluemapSignsConfig::new);
        instance = this;
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        bluemapPresent = ModList.get().isLoaded("bluemap");
        if (bluemapPresent) {
            // Instantiate the BlueMap-importing bridge ONLY here, so its de.bluecolored.* types are
            // never linked when BlueMap is absent.
            BlueMapBridge bridge = new BlueMapBridge(this, manager);
            bridge.register();
            manager.setBackend(bridge);
            getLogger().info("BlueMap Signs: BlueMap detected, marker integration active");
        } else {
            getLogger().info("BlueMap Signs: BlueMap not installed, module inert");
        }
    }

    public boolean isBluemapPresent() {
        return bluemapPresent;
    }

    public MapSignManager getManager() {
        return manager;
    }

    // ---- mixin hook (called from SignBlockEntityTextMixin on the server thread) ----

    public static void onSignChanged(SignBlockEntity sign) {
        BluemapSignsModule module = instance;
        if (module == null || !module.bluemapPresent || !module.isModuleEnabled()) {
            return;
        }
        if (!(sign.getLevel() instanceof ServerLevel level)) {
            return;
        }
        module.manager.handleSignEdit(level, sign.getBlockPos(),
                SignReader.readMarker(sign, module.getConfig().getPrefix()));
    }

    // ---- events ----

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        manager.setServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        manager.setServer(null);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!bluemapPresent || !isModuleEnabled()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        manager.reconcileChunk(level, chunk, getConfig().getPrefix());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!bluemapPresent || !isModuleEnabled()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getState().getBlock() instanceof SignBlock) {
            manager.removeSignAt(level, event.getPos());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BluemapSignsCommands.register(event.getDispatcher(), this);
    }
}
