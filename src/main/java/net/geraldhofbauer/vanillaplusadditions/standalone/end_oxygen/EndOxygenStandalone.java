package net.geraldhofbauer.vanillaplusadditions.standalone.end_oxygen;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.EndOxygenModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the end_oxygen module (jar {@code vpa_end_oxygen}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_end_oxygen")
public final class EndOxygenStandalone {

    public EndOxygenStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new EndOxygenModule(), modEventBus, modContainer);
    }
}
