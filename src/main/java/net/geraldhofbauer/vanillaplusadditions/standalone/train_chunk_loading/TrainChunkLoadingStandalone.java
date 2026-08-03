package net.geraldhofbauer.vanillaplusadditions.standalone.train_chunk_loading;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.TrainChunkLoadingModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the train_chunk_loading module (jar {@code vpa_train_chunk_loading}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_train_chunk_loading")
public final class TrainChunkLoadingStandalone {

    public TrainChunkLoadingStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new TrainChunkLoadingModule(), modEventBus, modContainer);
    }
}
