package net.geraldhofbauer.vanillaplusadditions.standalone.minecart_chunk_loading;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.MinecartChunkLoadingModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the minecart_chunk_loading module (jar {@code vpa_minecart_chunk_loading}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_minecart_chunk_loading")
public final class MinecartChunkLoadingStandalone {

    public MinecartChunkLoadingStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MinecartChunkLoadingModule(), modEventBus, modContainer);
    }
}
