package net.geraldhofbauer.vanillaplusadditions.standalone.chunk_reset;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.chunk_reset.ChunkResetModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the chunk_reset module (jar {@code vpa_chunk_reset}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_chunk_reset")
public final class ChunkResetStandalone {

    public ChunkResetStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new ChunkResetModule(), modEventBus, modContainer);
    }
}
