package net.geraldhofbauer.vanillaplusadditions.standalone.static_fov;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.static_fov.StaticFovModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Static FOV module (jar {@code vpa_static_fov}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_static_fov")
public final class StaticFovStandalone {

    public StaticFovStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new StaticFovModule(), modEventBus, modContainer);
    }
}
