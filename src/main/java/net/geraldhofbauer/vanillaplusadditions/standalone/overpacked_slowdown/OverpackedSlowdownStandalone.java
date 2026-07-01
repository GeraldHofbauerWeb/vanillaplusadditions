package net.geraldhofbauer.vanillaplusadditions.standalone.overpacked_slowdown;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_slowdown.OverpackedSlowdownModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the overpacked_slowdown module (jar {@code vpa_overpacked_slowdown}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_overpacked_slowdown")
public final class OverpackedSlowdownStandalone {

    public OverpackedSlowdownStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new OverpackedSlowdownModule(), modEventBus, modContainer);
    }
}
