package net.geraldhofbauer.vanillaplusadditions.standalone.bluemap_signs;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.BluemapSignsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the bluemap_signs module (jar {@code vpa_bluemap_signs}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_bluemap_signs")
public final class BluemapSignsStandalone {

    public BluemapSignsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new BluemapSignsModule(), modEventBus, modContainer);
    }
}
