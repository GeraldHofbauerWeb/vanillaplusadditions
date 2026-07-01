package net.geraldhofbauer.vanillaplusadditions.standalone.better_mobs;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.better_mobs.BetterMobsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the better_mobs module (jar {@code vpa_better_mobs}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_better_mobs")
public final class BetterMobsStandalone {

    public BetterMobsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new BetterMobsModule(), modEventBus, modContainer);
    }
}
