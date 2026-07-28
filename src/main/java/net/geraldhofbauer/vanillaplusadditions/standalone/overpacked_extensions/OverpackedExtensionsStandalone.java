package net.geraldhofbauer.vanillaplusadditions.standalone.overpacked_extensions;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.OverpackedExtensionsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the overpacked_extensions module (jar
 * {@code vpa_overpacked_extensions}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_overpacked_extensions")
public final class OverpackedExtensionsStandalone {

    public OverpackedExtensionsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new OverpackedExtensionsModule(), modEventBus, modContainer);
    }
}
