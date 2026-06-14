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
import net.minecraft.world.item.ItemStack;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    @Nullable
    private static Cat lookedAtCat = null;
    private static long nextStatRequestTick = Long.MIN_VALUE;
    private static final List<LivingEntity> PENDING_TARGET_OUTLINES = new ArrayList<>();
    private static final List<Cat> PENDING_CAT_OUTLINES = new ArrayList<>();
    private static final List<BlockPos> PENDING_RADIUS_POSITIONS = new ArrayList<>();

    /**
     * Master toggle for all goggle overlays (cats, targets, station radius + tooltip).
     * Off by default — this is a debug/inspection view, enabled on demand via the keybind.
     */
    private static boolean overlaysEnabled = false;

    private CatGuardianGogglesClientHandler() { }

    /** Flips the overlay master toggle and returns the new state. */
    public static boolean toggleOverlays() {
        overlaysEnabled = !overlaysEnabled;
        return overlaysEnabled;
    }

    public static boolean isOverlaysEnabled() {
        return overlaysEnabled;
    }

    public static void onClientTick(Minecraft mc) {
        activeTooltip = null;
        lookedAtCat = null;
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

        // Block hit: bowl/station "Associated Cats" tooltip — ALWAYS shown while wearing goggles
        // (expected, non-intrusive info). The radius box below is part of the debug overlay.
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
                if (overlaysEnabled) {
                    STATION_RADIUS_EXPIRY.put(blockHitResult.getBlockPos(), gameTime + OVERLAY_TIMEOUT_TICKS);
                }
            }
        }

        // Ray-AABB detection — only triggers when looking directly at the cat's hitbox
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 end = eyePos.add(mc.player.getLookAngle().scale(20.0));
        double closestHit = Double.MAX_VALUE;
        for (Cat cat : mc.level.getEntitiesOfClass(Cat.class,
                mc.player.getBoundingBox().inflate(20.0),
                c -> isOverlayCandidate(c, mc.player))) {
            java.util.Optional<Vec3> hit = cat.getBoundingBox().inflate(0.1).clip(eyePos, end);
            if (hit.isPresent()) {
                double dist = hit.get().distanceToSqr(eyePos);
                if (dist < closestHit) {
                    closestHit = dist;
                    lookedAtCat = cat;
                }
            }
        }
        if (lookedAtCat != null) {
            CAT_OVERLAY_EXPIRY.put(lookedAtCat.getId(), gameTime + OVERLAY_TIMEOUT_TICKS);
            if (gameTime >= nextStatRequestTick) {
                nextStatRequestTick = gameTime + 20;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.RequestCatStatsPacket(
                                lookedAtCat.getId()));
            }
        }

        // Everything below (cat outlines, target boxes, station radius) is the debug overlay.
        if (!overlaysEnabled) {
            return;
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
                    && isOverlayCandidate(cat, mc.player)) {
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

    /**
     * Client-side overlay eligibility. The server-only CAT_BOWL_POS attachment is not synced to
     * the client, so {@link CatGuardianModule#isGuardianCat} always returns false here. Instead we
     * match the local player's own tame cats (owner UUID IS synced), which in practice are the
     * guardian cats around the player's base. Non-guardian pets simply show an outline with no
     * target box, which is harmless.
     */
    private static boolean isOverlayCandidate(Cat cat, Player player) {
        return cat.isTame() && player.getUUID().equals(cat.getOwnerUUID());
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeTooltip != null) {
            int x = event.getGuiGraphics().guiWidth() / 2 + 10;
            int y = event.getGuiGraphics().guiHeight() / 2 + 10;
            event.getGuiGraphics().renderComponentTooltip(
                    Minecraft.getInstance().font, activeTooltip, x, y);
        }
        if (lookedAtCat != null) {
            renderCatStatsOverlay(event.getGuiGraphics(), Minecraft.getInstance(), lookedAtCat);
        }
    }

    private static void renderCatStatsOverlay(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, Cat cat) {
        int hp = Math.round(cat.getHealth());
        int maxHp = Math.round(cat.getMaxHealth());
        String healthStr = hp + "/" + maxHp;

        ItemStack armor = cat.getData(CatGuardianModule.CAT_INVENTORY.get()).getArmor();
        boolean hasArmor = !armor.isEmpty();
        String armorStr;
        if (hasArmor) {
            int remaining = armor.getMaxDamage() - armor.getDamageValue();
            armorStr = remaining + "/" + armor.getMaxDamage();
        } else {
            armorStr = "No armor";
        }

        int[] xpData = CatGuardianClientEvents.CAT_XP_MAP.get(cat.getId());
        String xpStr = xpData != null
                ? xpData[0] + "/" + xpData[1]
                : "?/" + CatGuardianModule.getCatXpCapacity();

        // --- Layout: unified icon size for all three rows ---
        net.minecraft.client.gui.Font font = mc.font;
        int iconSize = 14;                        // all icons the same size
        float itemScale = iconSize / 16f;         // scale for item icons (0.875)
        int iconGap = iconSize + 3;             // space between icon and text
        int rowH = iconSize + 2;             // uniform row height for all rows
        int textOff = (rowH - font.lineHeight) / 2; // vertical centering of text in row

        int armorGap = hasArmor ? iconGap : 0;
        int contentW = Math.max(iconGap + font.width(healthStr),
                Math.max(armorGap + font.width(armorStr),
                        iconGap + font.width(xpStr)));
        int contentH = rowH * 3;
        int pad = 4;

        // Position: anchored to the right edge of the screen, centred on crosshair
        int x = g.guiWidth() - contentW - pad * 2 - 10;
        int y = g.guiHeight() / 2 - contentH / 2;

        // Background (Minecraft tooltip style)
        g.fillGradient(x - pad - 1, y - pad - 1, x + contentW + pad + 1, y + contentH + pad + 1, 0xF0100010, 0xF0100010);
        g.fillGradient(x - pad - 1, y - pad - 1, x + contentW + pad + 1, y - pad, 0xFF5000FF, 0xFF5000FF);
        g.fillGradient(x - pad - 1, y + contentH + pad, x + contentW + pad + 1, y + contentH + pad + 1, 0xFF28007F, 0xFF28007F);
        g.fillGradient(x - pad - 1, y - pad, x - pad, y + contentH + pad, 0xFF5000FF, 0xFF28007F);
        g.fillGradient(x + contentW + pad, y - pad, x + contentW + pad + 1, y + contentH + pad, 0xFF5000FF, 0xFF28007F);

        // Row 0: heart sprite + HP
        g.blitSprite(net.minecraft.resources.ResourceLocation.parse("minecraft:hud/heart/full"),
                x, y, iconSize, iconSize);
        g.drawString(font, healthStr, x + iconGap, y + textOff, 0xFFCC2222, false);

        // Row 1: armor item icon (scaled to iconSize) + durability
        int row1Y = y + rowH;
        if (hasArmor) {
            g.pose().pushPose();
            g.pose().translate(x, row1Y, 0);
            g.pose().scale(itemScale, itemScale, 1f);
            g.renderItem(armor, 0, 0);
            g.pose().popPose();
            g.drawString(font, armorStr, x + iconGap, row1Y + textOff, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, armorStr, x, row1Y + textOff, 0xFFAAAAAA, false);
        }

        // Row 2: XP bottle icon (scaled to iconSize) + XP value
        int row2Y = y + rowH * 2;
        ItemStack xpBottle = new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
        g.pose().pushPose();
        g.pose().translate(x, row2Y, 0);
        g.pose().scale(itemScale, itemScale, 1f);
        g.renderItem(xpBottle, 0, 0);
        g.pose().popPose();
        g.drawString(font, xpStr, x + iconGap, row2Y + textOff, 0xFF7BE018, false);
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
