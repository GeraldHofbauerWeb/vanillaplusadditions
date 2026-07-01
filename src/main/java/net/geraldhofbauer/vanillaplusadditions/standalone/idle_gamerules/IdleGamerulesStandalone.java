package net.geraldhofbauer.vanillaplusadditions.standalone.idle_gamerules;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules.IdleGamerulesModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Idle Gamerules module (jar {@code vpa_idle_gamerules}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_idle_gamerules")
public final class IdleGamerulesStandalone {

    public IdleGamerulesStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new IdleGamerulesModule(), modEventBus, modContainer);
    }
}
