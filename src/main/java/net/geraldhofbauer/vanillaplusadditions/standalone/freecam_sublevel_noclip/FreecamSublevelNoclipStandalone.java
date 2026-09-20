package net.geraldhofbauer.vanillaplusadditions.standalone.freecam_sublevel_noclip;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.freecam_sublevel_noclip.FreecamSublevelNoclipModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Freecam Sub-Level Noclip module (jar
 * {@code vpa_freecam_sublevel_noclip}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_freecam_sublevel_noclip")
public final class FreecamSublevelNoclipStandalone {

    public FreecamSublevelNoclipStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new FreecamSublevelNoclipModule(), modEventBus, modContainer);
    }
}
