package net.geraldhofbauer.vanillaplusadditions.standalone.free_anvil_repair;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.FreeAnvilRepairModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Free Anvil Repair module (jar
 * {@code vpa_free_anvil_repair}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_free_anvil_repair")
public final class FreeAnvilRepairStandalone {

    public FreeAnvilRepairStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new FreeAnvilRepairModule(), modEventBus, modContainer);
    }
}
