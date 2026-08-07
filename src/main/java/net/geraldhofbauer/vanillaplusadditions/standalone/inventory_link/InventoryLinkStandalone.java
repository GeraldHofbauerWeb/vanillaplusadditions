package net.geraldhofbauer.vanillaplusadditions.standalone.inventory_link;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.InventoryLinkModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the inventory_link module (jar {@code vpa_inventory_link}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_inventory_link")
public final class InventoryLinkStandalone {

    public InventoryLinkStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new InventoryLinkModule(), modEventBus, modContainer);
    }
}
