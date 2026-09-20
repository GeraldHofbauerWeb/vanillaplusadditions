package net.geraldhofbauer.vanillaplusadditions.standalone.tipped_arrows;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.tipped_arrows.TippedArrowsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Tipped Arrows from Potions module (jar
 * {@code vpa_tipped_arrows}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_tipped_arrows")
public final class TippedArrowsStandalone {

    public TippedArrowsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new TippedArrowsModule(), modEventBus, modContainer);
    }
}
