package net.geraldhofbauer.vanillaplusadditions.standalone.wither_skeleton;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.WitherSkeletonModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the wither_skeleton module (jar {@code vpa_wither_skeleton}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_wither_skeleton")
public final class WitherSkeletonStandalone {

    public WitherSkeletonStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new WitherSkeletonModule(), modEventBus, modContainer);
    }
}
