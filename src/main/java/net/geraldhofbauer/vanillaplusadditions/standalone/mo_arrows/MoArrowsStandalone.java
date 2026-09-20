package net.geraldhofbauer.vanillaplusadditions.standalone.mo_arrows;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.MoArrowsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Mo' Arrows module (jar {@code vpa_mo_arrows}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_mo_arrows")
public final class MoArrowsStandalone {

    public MoArrowsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MoArrowsModule(), modEventBus, modContainer);
    }
}
