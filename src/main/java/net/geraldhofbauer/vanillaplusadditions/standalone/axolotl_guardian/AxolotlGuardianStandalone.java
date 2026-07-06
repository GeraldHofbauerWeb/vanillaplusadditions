package net.geraldhofbauer.vanillaplusadditions.standalone.axolotl_guardian;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the axolotl_guardian module (jar {@code vpa_axolotl_guardian}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_axolotl_guardian")
public final class AxolotlGuardianStandalone {

    public AxolotlGuardianStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new AxolotlGuardianModule(), modEventBus, modContainer);
    }
}
