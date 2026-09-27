package net.geraldhofbauer.vanillaplusadditions.standalone.quark_fresh_animations;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations.QuarkFreshAnimationsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the quark_fresh_animations module (jar
 * {@code vpa_quark_fresh_animations}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_quark_fresh_animations")
public final class QuarkFreshAnimationsStandalone {

    public QuarkFreshAnimationsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new QuarkFreshAnimationsModule(), modEventBus, modContainer);
    }
}
