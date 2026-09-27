package net.geraldhofbauer.vanillaplusadditions.standalone.special_gemstones;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.SpecialGemstonesModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Special Gemstones module (jar
 * {@code vpa_special_gemstones}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_special_gemstones")
public final class SpecialGemstonesStandalone {

    public SpecialGemstonesStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new SpecialGemstonesModule(), modEventBus, modContainer);
    }
}
