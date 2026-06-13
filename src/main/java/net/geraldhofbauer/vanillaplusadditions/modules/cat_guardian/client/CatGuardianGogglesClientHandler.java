package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianGogglesClientHandler {

    private static final RenderType XRAY_LINES = RenderType.create(
            "cat_guardian_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    /** How long (ticks) the overlay stays visible after the player looks away. */
    private static final long OVERLAY_TIMEOUT_TICKS = 300L; // 15 seconds

    /** cat entity ID → game tick when overlay expires */
    private static final Map<Integer, Long> CAT_OVERLAY_EXPIRY = new HashMap<>();
    /** station/bowl pos → game tick when radius expires */
    private static final Map<BlockPos, Long> STATION_RADIUS_EXPIRY = new HashMap<>();

    private static List<Component> activeTooltip = null;
    private static final List<LivingEntity> PENDING_TARGET_OUTLINES = new ArrayList<>();
    private static final List<Cat> PENDING_CAT_OUTLINES = new ArrayList<>();
    private static final List<BlockPos> PENDING_RADIUS_POSITIONS = new ArrayList<>();

    private CatGuardianGogglesClientHandler() { }

    public static void onClientTick(Minecraft mc) {
        activeTooltip = null;
        PENDING_TARGET_OUTLINES.clear();
        PENDING_CAT_OUTLINES.clear();
        PENDING_RADIUS_POSITIONS.clear();

        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!isWearingGoggles(mc.player)) {
            return;
        }

        long gameTime = mc.level.getGameTime();

        // Block hit: bowl tooltip + refresh radius expiry
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockEntity be = mc.level.getBlockEntity(blockHitResult.getBlockPos());
            if (be instanceof AbstractCatBowlBlockEntity bowl) {
                int count = bowl.getAssociatedCats().size();
                int max = CatGuardianModule.getMaxCatsPerStation();
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(
                        "gui.vanillaplusadditions.cat_guardian.associated_cats", count, max));
                activeTooltip = tooltip;
                STATION_RADIUS_EXPIRY.put(blockHitResult.getBlockPos(), gameTime + OVERLAY_TIMEOUT_TICKS);
            }
        }

        // Cone search for guardian cats (up to 20 blocks, ~14° cone)
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookDir = mc.player.getLookAngle();
        double maxRange = 20.0;
        Cat lookedAtCat = null;
        double bestDot = 0.93; // ~21° cone — small low cats are hard to look at precisely
        for (Cat cat : mc.level.getEntitiesOfClass(Cat.class,
                mc.player.getBoundingBox().inflate(maxRange),
                c -> CatGuardianModule.isGuardianCat(c))) {
            Vec3 toCenter = cat.position().add(0, cat.getBbHeight() / 2.0, 0).subtract(eyePos);
            double dist = toCenter.length();
            if (dist < 0.5 || dist > maxRange) {
                continue;
            }
            double dot = toCenter.normalize().dot(lookDir);
            if (dot > bestDot) {
                bestDot = dot;
                lookedAtCat = cat;
            }
        }
        if (lookedAtCat != null) {
            CAT_OVERLAY_EXPIRY.put(lookedAtCat.getId(), gameTime + OVERLAY_TIMEOUT_TICKS);
        }

        // Populate render lists from expiry maps; evict stale entries
        Iterator<Map.Entry<Integer, Long>> catIt = CAT_OVERLAY_EXPIRY.entrySet().iterator();
        while (catIt.hasNext()) {
            Map.Entry<Integer, Long> entry = catIt.next();
            if (entry.getValue() < gameTime) {
                catIt.remove();
                continue;
            }
            if (mc.level.getEntity(entry.getKey()) instanceof Cat cat
                    && CatGuardianModule.isGuardianCat(cat)) {
                // Always outline the looked-at cat itself — confirmation it is recognised,
                // even when it currently has no target.
                if (!PENDING_CAT_OUTLINES.contains(cat)) {
                    PENDING_CAT_OUTLINES.add(cat);
                }
                Integer targetId = CatGuardianClientEvents.CAT_TARGET_MAP.get(cat.getId());
                if (targetId != null
                        && mc.level.getEntity(targetId) instanceof LivingEntity living
                        && living.isAlive()
                        && !PENDING_TARGET_OUTLINES.contains(living)) {
                    PENDING_TARGET_OUTLINES.add(living);
                }
            } else {
                catIt.remove();
            }
        }

        Iterator<Map.Entry<BlockPos, Long>> stationIt = STATION_RADIUS_EXPIRY.entrySet().iterator();
        while (stationIt.hasNext()) {
            Map.Entry<BlockPos, Long> entry = stationIt.next();
            if (entry.getValue() < gameTime) {
                stationIt.remove();
                continue;
            }
            PENDING_RADIUS_POSITIONS.add(entry.getKey());
        }
    }

    public static boolean isWearingGoggles(Player player) {
        return GogglesItem.isWearingGoggles(player);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeTooltip != null) {
            int x = event.getGuiGraphics().guiWidth() / 2 + 10;
            int y = event.getGuiGraphics().guiHeight() / 2 + 10;
            event.getGuiGraphics().renderComponentTooltip(
                    Minecraft.getInstance().font, activeTooltip, x, y);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (PENDING_TARGET_OUTLINES.isEmpty() && PENDING_CAT_OUTLINES.isEmpty()
                && PENDING_RADIUS_POSITIONS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(XRAY_LINES);

        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        for (LivingEntity target : PENDING_TARGET_OUTLINES) {
            double ex = Mth.lerp(partialTick, target.xOld, target.getX());
            double ey = Mth.lerp(partialTick, target.yOld, target.getY());
            double ez = Mth.lerp(partialTick, target.zOld, target.getZ());
            AABB box = target.getBoundingBox();
            double hw = box.getXsize() / 2.0;
            double h  = box.getYsize();
            double hd = box.getZsize() / 2.0;
            LevelRenderer.renderLineBox(ps, consumer,
                    ex - hw, ey, ez - hd,
                    ex + hw, ey + h, ez + hd,
                    1.0f, 0.2f, 0.2f, 0.9f);
        }

        // Outline the looked-at guardian cats themselves (soft cyan/green) for feedback.
        for (Cat cat : PENDING_CAT_OUTLINES) {
            double ex = Mth.lerp(partialTick, cat.xOld, cat.getX());
            double ey = Mth.lerp(partialTick, cat.yOld, cat.getY());
            double ez = Mth.lerp(partialTick, cat.zOld, cat.getZ());
            AABB box = cat.getBoundingBox();
            double hw = box.getXsize() / 2.0;
            double h  = box.getYsize();
            double hd = box.getZsize() / 2.0;
            LevelRenderer.renderLineBox(ps, consumer,
                    ex - hw, ey, ez - hd,
                    ex + hw, ey + h, ez + hd,
                    0.3f, 1.0f, 0.6f, 0.9f);
        }

        double guardRadius = CatGuardianModule.getGuardRadius();
        double guardRadiusY = CatGuardianModule.getGuardRadiusY();
        for (BlockPos radiusPos : PENDING_RADIUS_POSITIONS) {
            AABB guardBox = new AABB(radiusPos).inflate(guardRadius, guardRadiusY, guardRadius);
            LevelRenderer.renderLineBox(ps, consumer, guardBox, 0.2f, 0.6f, 1.0f, 0.6f);
        }

        ps.popPose();
        mc.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }
}
