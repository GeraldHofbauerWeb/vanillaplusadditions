package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;

/**
 * General client-side debug-overlay framework.
 *
 * <p>Provides one shared toggle ({@link net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayState})
 * bound to a single keybind, a {@link net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRegistry}
 * that other modules plug renderers into, shared goggles detection and world-render helpers.
 * Renderers only draw while the toggle is on and the player wears Engineer's Goggles.</p>
 *
 * <p>The module itself has no server-side behaviour; all logic lives in {@code client/} classes
 * that are only loaded on {@code Dist.CLIENT}.</p>
 */
public class DebugOverlayModule
        extends AbstractModule<DebugOverlayModule, AbstractModuleConfig.DefaultModuleConfig<DebugOverlayModule>> {

    public DebugOverlayModule() {
        super("debug_overlay",
                "Debug Overlay",
                "Shared goggles debug overlay framework (toggle + chunk borders, cat stats, ...).",
                AbstractModuleConfig::createDefault);
    }

    @Override
    protected void onInitialize() {
        // Client keybind/render handlers register themselves via @EventBusSubscriber(Dist.CLIENT).
        // Nothing to wire server-side.
    }
}
