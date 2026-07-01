package net.geraldhofbauer.vanillaplusadditions.standalone.flying_fish;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.FlyingFishModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Flying Fish module (jar {@code vpa_flying_fish}), depending
 * on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_flying_fish")
public final class FlyingFishStandalone {

    public FlyingFishStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new FlyingFishModule(), modEventBus, modContainer);
    }
}
