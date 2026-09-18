package net.geraldhofbauer.vanillaplusadditions.standalone.glider_lightning_guard;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.GliderLightningGuardModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Glider Lightning Guard module (jar
 * {@code vpa_glider_lightning_guard}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_glider_lightning_guard")
public final class GliderLightningGuardStandalone {

    public GliderLightningGuardStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new GliderLightningGuardModule(), modEventBus, modContainer);
    }
}
