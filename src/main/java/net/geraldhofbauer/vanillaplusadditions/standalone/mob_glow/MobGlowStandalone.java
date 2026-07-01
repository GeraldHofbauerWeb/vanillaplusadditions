package net.geraldhofbauer.vanillaplusadditions.standalone.mob_glow;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_glow.MobGlowModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the mob_glow module (jar {@code vpa_mob_glow}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_mob_glow")
public final class MobGlowStandalone {

    public MobGlowStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MobGlowModule(), modEventBus, modContainer);
    }
}
