package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianGogglesClientHandler {

    private static final TagKey<Item> ARM_GOGGLES_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "arm_goggles"));

    private static List<Component> activeTooltip = null;
    // Target entity to outline this frame (set in onClientTick, consumed by onRenderLevelStage)
    private static LivingEntity pendingTargetOutline = null;

    private CatGuardianGogglesClientHandler() { }

    public static void onClientTick(Minecraft mc) {
        activeTooltip = null;
        pendingTargetOutline = null;

        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }

        if (!isWearingGoggles(mc.player)) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockEntity be = mc.level.getBlockEntity(blockHitResult.getBlockPos());
            if (be instanceof AbstractCatBowlBlockEntity bowl) {
                int count = bowl.getAssociatedCats().size();
                int max = CatGuardianModule.getMaxCatsPerStation();

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.vanillaplusadditions.cat_guardian.associated_cats", count, max));
                activeTooltip = tooltip;
            }
        } else if (hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof Cat lookedAtCat
                && CatGuardianModule.isGuardianCat(lookedAtCat)) {
            // Show the target's bounding box when looking at a guardian cat
            Integer targetId = CatGuardianClientEvents.CAT_TARGET_MAP.get(lookedAtCat.getId());
            if (targetId != null) {
                Entity targetEntity = mc.level.getEntity(targetId);
                if (targetEntity instanceof LivingEntity living && living.isAlive()) {
                    pendingTargetOutline = living;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeTooltip != null) {
            int x = event.getGuiGraphics().guiWidth() / 2 + 10;
            int y = event.getGuiGraphics().guiHeight() / 2 + 10;
            event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font, activeTooltip, x, y);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        LivingEntity target = pendingTargetOutline;
        if (target == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = event.getCamera().getPosition();

        AABB box = target.getBoundingBox();
        double x = target.getX(partialTick) - camPos.x;
        double y = target.getY() - camPos.y;
        double z = target.getZ(partialTick) - camPos.z;
        double hw = box.getXsize() / 2.0;
        double h  = box.getYsize();
        double hd = box.getZsize() / 2.0;

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(x, y, z);

        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(ps, lines, -hw, 0, -hd, hw, h, hd,
                1.0f, 0.2f, 0.2f, 1.0f);

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        ps.popPose();
    }

    private static boolean isWearingGoggles(Player player) {
        if (GogglesItem.isWearingGoggles(player)) {
            return true;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ARM_GOGGLES_TAG);
    }
}
