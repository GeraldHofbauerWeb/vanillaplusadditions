package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network.OpenItemVaultViewerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ItemVaultViewerClientEvents {
    private ItemVaultViewerClientEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ItemVaultViewerModule.isCreateLoaded()) {
            return;
        }
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (player.isShiftKeyDown()) {
            return;
        }
        if (!ItemVaultViewerKeybinds.isModifierDown()) {
            return;
        }
        if (!GogglesItem.isWearingGoggles(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!ItemVaultBlock.isVault(event.getLevel().getBlockState(pos))) {
            return;
        }

        Module module = ModuleManager.getInstance().getModule("item_vault_viewer");
        if (!(module instanceof ItemVaultViewerModule viewerModule) || !viewerModule.isModuleEnabled()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        PacketDistributor.sendToServer(new OpenItemVaultViewerPacket(pos));
    }
}
