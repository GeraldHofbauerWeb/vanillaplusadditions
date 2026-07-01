package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.client;

import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ItemVaultViewerClientSetup {
    private ItemVaultViewerClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        if (!ItemVaultViewerModule.isCreateLoaded()) {
            return;
        }
        event.register(ItemVaultViewerModule.ITEM_VAULT_VIEWER_MENU.get(), ItemVaultViewerScreen::new);
    }
}
