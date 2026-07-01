package net.geraldhofbauer.vanillaplusadditions.standalone.stationary_chunk_loader;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.StationaryChunkLoaderModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the stationary_chunk_loader module (jar {@code vpa_stationary_chunk_loader}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_stationary_chunk_loader")
public final class StationaryChunkLoaderStandalone {

    public StationaryChunkLoaderStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new StationaryChunkLoaderModule(), modEventBus, modContainer);
    }
}
