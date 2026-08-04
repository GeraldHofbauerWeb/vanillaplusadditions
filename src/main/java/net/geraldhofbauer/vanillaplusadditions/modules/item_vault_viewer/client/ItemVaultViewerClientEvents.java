package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.createmod.catnip.data.Couple;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ItemVaultViewerClientEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemVaultViewerClientEvents.class);

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
     * at HIGH priority (before Create's own handler) and mirror Create's own lookup exactly:
     * the same ray inputs (already clamped to the nearest world block), the same contraption
     * registry ({@code ContraptionHandler.loadedContraptions}) and the same ray trace. Only when
     * the hit block is a vault do we take over; anything else falls through to Create untouched.
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
        if (player == null || mc.level == null || player.isSpectator()) {
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
            diag("modifier held but no goggles worn — ignoring");
            return;
        }

        // Identical inputs to Create's own handler: origin at the eyes, target already shortened
        // to the nearest world block so we never reach through walls.
        Couple<Vec3> rayInputs = ContraptionHandlerClient.getRayInputs(player);
        Vec3 origin = rayInputs.getFirst();
        Vec3 target = rayInputs.getSecond();
        AABB searchBox = new AABB(origin, target).inflate(16.0);

        AbstractContraptionEntity bestEntity = null;
        BlockPos bestLocalPos = null;
        double bestDistance = Double.MAX_VALUE;
        int loaded = 0;
        int inRange = 0;
        int rayHits = 0;
        String lastHitBlock = "-";

        for (WeakReference<AbstractContraptionEntity> ref
                : ContraptionHandler.loadedContraptions.get(mc.level).values()) {
            AbstractContraptionEntity entity = ref.get();
            if (entity == null) {
                continue;
            }
            loaded++;
            if (!entity.getBoundingBox().intersects(searchBox)) {
                continue;
            }
            inRange++;
            BlockHitResult hit = ContraptionHandlerClient.rayTraceContraption(origin, target, entity);
            if (hit == null) {
                continue;
            }
            rayHits++;
            StructureTemplate.StructureBlockInfo info =
                    entity.getContraption().getBlocks().get(hit.getBlockPos());
            if (info == null) {
                lastHitBlock = "<not in blocks map>";
                continue;
            }
            lastHitBlock = String.valueOf(info.state().getBlock());
            if (!ItemVaultBlock.isVault(info.state())) {
                continue;
            }
            double distance = entity.toGlobalVector(hit.getLocation(), 1.0f).distanceTo(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestEntity = entity;
                bestLocalPos = hit.getBlockPos();
            }
        }

        if (bestEntity == null) {
            diag("no vault hit — contraptions loaded={} inRange={} rayHits={} lastHitBlock={} "
                    + "origin={} target={}", loaded, inRange, rayHits, lastHitBlock, origin, target);
            return;
        }

        diag("vault hit on contraption #{} at local {} (dist {}) — sending packet",
                bestEntity.getId(), bestLocalPos, String.format("%.2f", bestDistance));
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(
                new OpenContraptionVaultViewerPacket(bestEntity.getId(), bestLocalPos));
    }

    /**
     * Traces why a contraption click did or did not open the viewer. Only ever reached on a
     * deliberate modifier + use-key press; kept at debug level so it is one log config away when
     * the ray trace needs investigating again.
     */
    private static void diag(String message, Object... args) {
        LOGGER.debug("[IVV/contraption] " + message, args);
    }
}
