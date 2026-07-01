package net.geraldhofbauer.vanillaplusadditions.standalone.debug_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.DebugOverlayModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the debug_overlay module (jar {@code vpa_debug_overlay}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_debug_overlay")
public final class DebugOverlayStandalone {

    public DebugOverlayStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new DebugOverlayModule(), modEventBus, modContainer);
    }
}
