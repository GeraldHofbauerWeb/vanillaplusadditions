package net.geraldhofbauer.vanillaplusadditions.standalone.item_vault_viewer;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the item_vault_viewer module (jar {@code vpa_item_vault_viewer}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_item_vault_viewer")
public final class ItemVaultViewerStandalone {

    public ItemVaultViewerStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new ItemVaultViewerModule(), modEventBus, modContainer);
    }
}
