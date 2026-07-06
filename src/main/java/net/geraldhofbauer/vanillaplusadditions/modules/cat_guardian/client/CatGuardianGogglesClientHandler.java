package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayState;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.GogglesUtil;
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

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
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
    private static final Map<Integer,
            net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket>
            PENDING_CAT_PATHS = new HashMap<>();

    private CatGuardianGogglesClientHandler() { }

    public static void onClientTick(Minecraft mc) {
        activeTooltip = null;
        lookedAtCat = null;
        PENDING_TARGET_OUTLINES.clear();
        PENDING_CAT_OUTLINES.clear();
        PENDING_RADIUS_POSITIONS.clear();
        PENDING_CAT_PATHS.clear();

        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!isWearingGoggles(mc.player)) {
            return;
        }
        // Two independent triggers (goggles required for both):
        //  - The info POPUP (cat-stats panel + bowl "Associated Cats" tooltip) is hold-to-peek on
        //    the cat keybind (default Left Ctrl, see CatGuardianKeybinds): held + looking = shown,
        //    released = gone.
        //  - The 3D BOXES (cat/target outlines, guard radius, path) stay on the debug-overlay
        //    toggle (default Numpad +).
        // Wearing goggles alone shows nothing until one of the two is active.
        boolean popupHeld = CatGuardianKeybinds.isModifierDown();
        boolean boxesOn = DebugOverlayState.isEnabled();
        if (!popupHeld && !boxesOn) {
            return;
        }

        long gameTime = mc.level.getGameTime();

        // Block hit: bowl/station "Associated Cats" tooltip (popup) + guard-radius box.
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockEntity be = mc.level.getBlockEntity(blockHitResult.getBlockPos());
            if (be instanceof AbstractCatBowlBlockEntity bowl) {
                if (popupHeld) {
                    int count = bowl.getAssociatedCats().size();
                    int max = CatGuardianModule.getMaxCatsPerStation();
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable(
                            "gui.vanillaplusadditions.cat_guardian.associated_cats", count, max));
                    activeTooltip = tooltip;
                }
                if (boxesOn) {
                    STATION_RADIUS_EXPIRY.put(blockHitResult.getBlockPos(), gameTime + OVERLAY_TIMEOUT_TICKS);
                }
            }
        }

        // Ray-AABB detection — only triggers when looking directly at the cat's hitbox
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 end = eyePos.add(mc.player.getLookAngle().scale(20.0));
        double closestHit = Double.MAX_VALUE;
        Cat detectedCat = null;
        Vec3 detectedCatHit = null;
        for (Cat cat : mc.level.getEntitiesOfClass(Cat.class,
                mc.player.getBoundingBox().inflate(20.0),
                c -> isOverlayCandidate(c, mc.player))) {
            java.util.Optional<Vec3> hit = cat.getBoundingBox().inflate(0.1).clip(eyePos, end);
            if (hit.isPresent()) {
                double dist = hit.get().distanceToSqr(eyePos);
                if (dist < closestHit) {
                    closestHit = dist;
                    detectedCat = cat;
                    detectedCatHit = hit.get();
                }
            }
        }
        if (detectedCat != null) {
            // The stats POPUP requires clear line-of-sight: a solid block between the eye and the
            // cat must hide it (the 3D boxes below stay xray on purpose — that's the debug view).
            if (popupHeld && hasLineOfSight(mc, eyePos, detectedCatHit)) {
                // Drives the stats popup (rendered in onRenderGui) while the key is held.
                lookedAtCat = detectedCat;
                if (gameTime >= nextStatRequestTick) {
                    nextStatRequestTick = gameTime + 20;
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.RequestCatStatsPacket(
                                    detectedCat.getId()));
                }
            }
            if (boxesOn) {
                CAT_OVERLAY_EXPIRY.put(detectedCat.getId(), gameTime + OVERLAY_TIMEOUT_TICKS);
            }
        }

        // Everything below is the 3D-box overlay (toggle only).
        if (!boxesOn) {
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

        // Populate navigation paths for cats the player has looked at recently.
        for (Integer catId : CAT_OVERLAY_EXPIRY.keySet()) {
            net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket pathPkt =
                    CatGuardianClientEvents.CAT_PATH_MAP.get(catId);
            if (pathPkt != null && pathPkt.nodeX().length > 0) {
                PENDING_CAT_PATHS.put(catId, pathPkt);
            }
        }
    }

    public static boolean isWearingGoggles(Player player) {
        return GogglesUtil.isWearingGoggles(player);
    }

    /**
     * True when no solid block sits between the player's eye and the cat hit point — so the stats
     * popup is hidden when the cat is behind a wall.
     */
    private static boolean hasLineOfSight(Minecraft mc, Vec3 eyePos, Vec3 catHit) {
        if (catHit == null || mc.level == null) {
            return false;
        }
        BlockHitResult clip = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eyePos, catHit,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player));
        if (clip.getType() != HitResult.Type.BLOCK) {
            return true;
        }
        // A block strictly before the cat point occludes it (tiny epsilon for coplanar hits).
        return clip.getLocation().distanceToSqr(eyePos) >= eyePos.distanceToSqr(catHit) - 1.0e-4;
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

        String ownerStr = resolveOwnerName(mc, cat.getOwnerUUID());

        // --- Layout: unified icon size for all three rows ---
        net.minecraft.client.gui.Font font = mc.font;
        int iconSize = 14;                        // all icons the same size
        float itemScale = iconSize / 16f;         // scale for item icons (0.875)
        int iconGap = iconSize + 3;             // space between icon and text
        int rowH = iconSize + 2;             // uniform row height for all rows
        int textOff = (rowH - font.lineHeight) / 2; // vertical centering of text in row

        int armorGap = hasArmor ? iconGap : 0;
        int panelW = Math.max(iconGap + font.width(healthStr),
                Math.max(armorGap + font.width(armorStr),
                        Math.max(iconGap + font.width(xpStr), iconGap + font.width(ownerStr))));
        int contentH = rowH * 4;
        int pad = 4;

        // Position: to the right of the crosshair, same as station tooltip
        int x = g.guiWidth() / 2 + 10;
        int y = g.guiHeight() / 2 + 10;

        // Background (semi-transparent tooltip style)
        g.fillGradient(x - pad - 1, y - pad - 1, x + panelW + pad + 1, y + contentH + pad + 1, 0x80100010, 0x80100010);
        g.fillGradient(x - pad - 1, y - pad - 1, x + panelW + pad + 1, y - pad, 0xA05000FF, 0xA05000FF);
        g.fillGradient(x - pad - 1, y + contentH + pad, x + panelW + pad + 1, y + contentH + pad + 1, 0xA028007F, 0xA028007F);
        g.fillGradient(x - pad - 1, y - pad, x - pad, y + contentH + pad, 0xA05000FF, 0xA028007F);
        g.fillGradient(x + panelW + pad, y - pad, x + panelW + pad + 1, y + contentH + pad, 0xA05000FF, 0xA028007F);

        // Row 0: heart sprite (left) + HP value (right-aligned)
        g.blitSprite(net.minecraft.resources.ResourceLocation.parse("minecraft:hud/heart/full"),
                x, y, iconSize, iconSize);
        g.drawString(font, healthStr, x + panelW - font.width(healthStr), y + textOff, 0xFFCC2222, false);

        // Row 1: armor item icon (left) + durability (right-aligned)
        int row1Y = y + rowH;
        if (hasArmor) {
            g.pose().pushPose();
            g.pose().translate(x, row1Y, 0);
            g.pose().scale(itemScale, itemScale, 1f);
            g.renderItem(armor, 0, 0);
            g.pose().popPose();
        }
        int armorColor = hasArmor ? 0xFFFFFFFF : 0xFFAAAAAA;
        g.drawString(font, armorStr, x + panelW - font.width(armorStr), row1Y + textOff, armorColor, false);

        // Row 2: XP bottle icon (left) + XP value (right-aligned)
        int row2Y = y + rowH * 2;
        ItemStack xpBottle = new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
        g.pose().pushPose();
        g.pose().translate(x, row2Y, 0);
        g.pose().scale(itemScale, itemScale, 1f);
        g.renderItem(xpBottle, 0, 0);
        g.pose().popPose();
        g.drawString(font, xpStr, x + panelW - font.width(xpStr), row2Y + textOff, 0xFF7BE018, false);

        // Row 3: player head icon (left) + owner name (right-aligned)
        int row3Y = y + rowH * 3;
        ItemStack playerHead = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        g.pose().pushPose();
        g.pose().translate(x, row3Y, 0);
        g.pose().scale(itemScale, itemScale, 1f);
        g.renderItem(playerHead, 0, 0);
        g.pose().popPose();
        g.drawString(font, ownerStr, x + panelW - font.width(ownerStr), row3Y + textOff, 0xFFAAAAFF, false);
    }

    /** Resolves an owner UUID to a display name via the client's tab-list player info. */
    private static String resolveOwnerName(Minecraft mc, @Nullable java.util.UUID ownerId) {
        if (ownerId == null) {
            return "?";
        }
        if (mc.getConnection() != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = mc.getConnection().getPlayerInfo(ownerId);
            if (info != null) {
                return info.getProfile().getName();
            }
        }
        return ownerId.toString().substring(0, 8);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (PENDING_TARGET_OUTLINES.isEmpty() && PENDING_CAT_OUTLINES.isEmpty()
                && PENDING_RADIUS_POSITIONS.isEmpty() && PENDING_CAT_PATHS.isEmpty()) {
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

        // Navigation path: node boxes (gold-yellow) + line segments between nodes.
        // All nodes rendered at block-center (x+0.5, z+0.5). PoseStack is already translated
        // by -camPos so world coordinates can be used directly.
        if (!PENDING_CAT_PATHS.isEmpty()) {
            Matrix4f mat = ps.last().pose();
            PoseStack.Pose pose = ps.last();
            for (net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket pathPkt
                    : PENDING_CAT_PATHS.values()) {
                int n = pathPkt.nodeX().length;
                int nextIdx = pathPkt.nextNodeIndex();
                // Node boxes
                for (int i = 0; i < n; i++) {
                    double wx = pathPkt.nodeX()[i] + 0.5;
                    double wy = pathPkt.nodeY()[i];
                    double wz = pathPkt.nodeZ()[i] + 0.5;
                    boolean isNext = (i == nextIdx);
                    double infl = isNext ? 0.18 : 0.12;
                    float g = isNext ? 0.5f : 0.85f;
                    float a = isNext ? 1.0f : 0.75f;
                    LevelRenderer.renderLineBox(ps, consumer,
                            wx - infl, wy + 0.05, wz - infl,
                            wx + infl, wy + 0.05 + infl * 2, wz + infl,
                            1.0f, g, 0.0f, a);
                }
                // Line segments between consecutive nodes
                for (int i = 0; i < n - 1; i++) {
                    float ax = (float) (pathPkt.nodeX()[i]     + 0.5);
                    float ay = (float) (pathPkt.nodeY()[i]     + 0.22);
                    float az = (float) (pathPkt.nodeZ()[i]     + 0.5);
                    float bx = (float) (pathPkt.nodeX()[i + 1] + 0.5);
                    float by = (float) (pathPkt.nodeY()[i + 1] + 0.22);
                    float bz = (float) (pathPkt.nodeZ()[i + 1] + 0.5);
                    float dx = bx - ax, dy = by - ay, dz = bz - az;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len > 0.0f) {
                        dx /= len; dy /= len; dz /= len;
                    }
                    consumer.addVertex(mat, ax, ay, az).setColor(1.0f, 0.85f, 0.0f, 0.5f).setNormal(pose, dx, dy, dz);
                    consumer.addVertex(mat, bx, by, bz).setColor(1.0f, 0.85f, 0.0f, 0.5f).setNormal(pose, dx, dy, dz);
                }
            }
        }

        ps.popPose();
        mc.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }
}
