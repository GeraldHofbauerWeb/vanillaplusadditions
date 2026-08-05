package net.geraldhofbauer.vanillaplusadditions.standalone.mob_spawn_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.MobSpawnOverlayModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the mob_spawn_overlay module (jar
 * {@code vpa_mob_spawn_overlay}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_mob_spawn_overlay")
public final class MobSpawnOverlayStandalone {

    public MobSpawnOverlayStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MobSpawnOverlayModule(), modEventBus, modContainer);
    }
}
