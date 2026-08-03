package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network.OpenContraptionVaultViewerPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network.OpenItemVaultViewerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
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

    /**
     * Contraption vaults never fire {@link PlayerInteractEvent.RightClickBlock} — Create routes
     * contraption clicks through this input event ({@code ContraptionHandlerClient}). We hook it
     * at HIGH priority (before Create's own handler), ray trace nearby contraptions ourselves and
     * open the viewer for a hit vault block. Ray inputs are computed catnip-free.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseKeyOnContraption(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ItemVaultViewerModule.isCreateLoaded()) {
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (player.isShiftKeyDown() || !ItemVaultViewerKeybinds.isModifierDown()) {
            return;
        }
        Module module = ModuleManager.getInstance().getModule("item_vault_viewer");
        if (!(module instanceof ItemVaultViewerModule viewerModule) || !viewerModule.isModuleEnabled()) {
            return;
        }
        if (!GogglesItem.isWearingGoggles(player)) {
            return;
        }

        double reach = player.blockInteractionRange();
        Vec3 from = player.getEyePosition(1.0f);
        Vec3 to = from.add(player.getViewVector(1.0f).scale(reach));

        AbstractContraptionEntity bestEntity = null;
        BlockPos bestLocalPos = null;
        double bestDistSqr = Double.MAX_VALUE;
        AABB searchBox = player.getBoundingBox().inflate(reach + 16);
        for (AbstractContraptionEntity entity
                : mc.level.getEntitiesOfClass(AbstractContraptionEntity.class, searchBox)) {
            BlockHitResult hit = ContraptionHandlerClient.rayTraceContraption(from, to, entity);
            if (hit == null) {
                continue;
            }
            StructureTemplate.StructureBlockInfo info =
                    entity.getContraption().getBlocks().get(hit.getBlockPos());
            if (info == null || !ItemVaultBlock.isVault(info.state())) {
                continue;
            }
            double distSqr = entity.toGlobalVector(hit.getLocation(), 1.0f).distanceToSqr(from);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                bestEntity = entity;
                bestLocalPos = hit.getBlockPos();
            }
        }
        if (bestEntity == null) {
            return;
        }
        // Don't reach "through" a closer world block the crosshair is actually pointing at.
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.hitResult.getLocation().distanceToSqr(from) < bestDistSqr) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(
                new OpenContraptionVaultViewerPacket(bestEntity.getId(), bestLocalPos));
    }
}
