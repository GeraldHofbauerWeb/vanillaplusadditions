package net.geraldhofbauer.vanillaplusadditions.standalone.options;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.options.OptionsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the options module (jar {@code vpa_options}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_options")
public final class OptionsStandalone {

    public OptionsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new OptionsModule(), modEventBus, modContainer);
    }
}
