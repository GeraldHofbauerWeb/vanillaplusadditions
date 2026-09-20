package net.geraldhofbauer.vanillaplusadditions.standalone.mob_drops;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_drops.MobDropsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Mob Drops module (jar {@code vpa_mob_drops}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_mob_drops")
public final class MobDropsStandalone {

    public MobDropsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MobDropsModule(), modEventBus, modContainer);
    }
}
