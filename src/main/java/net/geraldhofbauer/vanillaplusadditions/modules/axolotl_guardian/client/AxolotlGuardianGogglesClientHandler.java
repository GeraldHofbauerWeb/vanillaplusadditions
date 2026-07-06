package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayState;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.GogglesUtil;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AbstractAxolotlBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.RequestAxolotlStatsPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlPathPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AxolotlGuardianGogglesClientHandler {

    private static final RenderType XRAY_LINES = RenderType.create(
            "axolotl_guardian_xray_lines",
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

    /** axolotl entity ID → game tick when overlay expires */
    private static final Map<Integer, Long> AXOLOTL_OVERLAY_EXPIRY = new HashMap<>();
    /** station/bowl pos → game tick when radius expires */
    private static final Map<BlockPos, Long> STATION_RADIUS_EXPIRY = new HashMap<>();

    private static List<Component> activeTooltip = null;
    @Nullable
    private static Axolotl lookedAtAxolotl = null;
    private static long nextStatRequestTick = Long.MIN_VALUE;
    private static final List<LivingEntity> PENDING_TARGET_OUTLINES = new ArrayList<>();
    private static final List<Axolotl> PENDING_AXOLOTL_OUTLINES = new ArrayList<>();
    private static final List<BlockPos> PENDING_RADIUS_POSITIONS = new ArrayList<>();
    private static final Map<Integer, SyncAxolotlPathPacket> PENDING_AXOLOTL_PATHS = new HashMap<>();

    private AxolotlGuardianGogglesClientHandler() { }

    public static void onClientTick(Minecraft mc) {
        activeTooltip = null;
        lookedAtAxolotl = null;
        PENDING_TARGET_OUTLINES.clear();
        PENDING_AXOLOTL_OUTLINES.clear();
        PENDING_RADIUS_POSITIONS.clear();
        PENDING_AXOLOTL_PATHS.clear();

        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!isWearingGoggles(mc.player)) {
            return;
        }
        // Two independent triggers (goggles required for both):
        //  - The info POPUP (axolotl-stats panel + bowl tooltip) is hold-to-peek on the
        //    modifier keybind: held + looking = shown, released = gone.
        //  - The 3D BOXES (axolotl/target outlines, guard radius, path) stay on the
        //    debug-overlay toggle.
        boolean popupHeld = AxolotlGuardianKeybinds.isModifierDown();
        boolean boxesOn = DebugOverlayState.isEnabled();
        if (!popupHeld && !boxesOn) {
            return;
        }

        long gameTime = mc.level.getGameTime();

        // Block hit: bowl/station "Associated Axolotls" tooltip (popup) + guard-radius box.
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockEntity be = mc.level.getBlockEntity(blockHitResult.getBlockPos());
            if (be instanceof AbstractAxolotlBowlBlockEntity bowl) {
                if (popupHeld) {
                    int count = bowl.getAssociatedAxolotls().size();
                    int max = AxolotlGuardianModule.getMaxAxolotlsPerStation();
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable(
                            "gui.vanillaplusadditions.axolotl_guardian.associated_axolotls", count, max));
                    activeTooltip = tooltip;
                }
                if (boxesOn) {
                    STATION_RADIUS_EXPIRY.put(blockHitResult.getBlockPos(), gameTime + OVERLAY_TIMEOUT_TICKS);
                }
            }
        }

        // Ray-AABB detection — only triggers when looking directly at the axolotl's hitbox
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 end = eyePos.add(mc.player.getLookAngle().scale(20.0));
        double closestHit = Double.MAX_VALUE;
        Axolotl detectedAxolotl = null;
        Vec3 detectedAxolotlHit = null;
        for (Axolotl axolotl : mc.level.getEntitiesOfClass(Axolotl.class,
                mc.player.getBoundingBox().inflate(20.0),
                a -> isOverlayCandidate(a, mc.player))) {
            java.util.Optional<Vec3> hit = axolotl.getBoundingBox().inflate(0.1).clip(eyePos, end);
            if (hit.isPresent()) {
                double dist = hit.get().distanceToSqr(eyePos);
                if (dist < closestHit) {
                    closestHit = dist;
                    detectedAxolotl = axolotl;
                    detectedAxolotlHit = hit.get();
                }
            }
        }
        if (detectedAxolotl != null) {
            // The stats POPUP requires clear line-of-sight: a solid block between the eye and
            // the axolotl must hide it (the 3D boxes stay xray on purpose — debug view).
            if (popupHeld && hasLineOfSight(mc, eyePos, detectedAxolotlHit)) {
                lookedAtAxolotl = detectedAxolotl;
                if (gameTime >= nextStatRequestTick) {
                    nextStatRequestTick = gameTime + 20;
                    PacketDistributor.sendToServer(new RequestAxolotlStatsPacket(detectedAxolotl.getId()));
                }
            }
            if (boxesOn) {
                AXOLOTL_OVERLAY_EXPIRY.put(detectedAxolotl.getId(), gameTime + OVERLAY_TIMEOUT_TICKS);
            }
        }

        // Everything below is the 3D-box overlay (toggle only).
        if (!boxesOn) {
            return;
        }

        // Populate render lists from expiry maps; evict stale entries
        Iterator<Map.Entry<Integer, Long>> axolotlIt = AXOLOTL_OVERLAY_EXPIRY.entrySet().iterator();
        while (axolotlIt.hasNext()) {
            Map.Entry<Integer, Long> entry = axolotlIt.next();
            if (entry.getValue() < gameTime) {
                axolotlIt.remove();
                continue;
            }
            if (mc.level.getEntity(entry.getKey()) instanceof Axolotl axolotl
                    && isOverlayCandidate(axolotl, mc.player)) {
                if (!PENDING_AXOLOTL_OUTLINES.contains(axolotl)) {
                    PENDING_AXOLOTL_OUTLINES.add(axolotl);
                }
                Integer targetId = AxolotlGuardianClientEvents.AXOLOTL_TARGET_MAP.get(axolotl.getId());
                if (targetId != null
                        && mc.level.getEntity(targetId) instanceof LivingEntity living
                        && living.isAlive()
                        && !PENDING_TARGET_OUTLINES.contains(living)) {
                    PENDING_TARGET_OUTLINES.add(living);
                }
            } else {
                axolotlIt.remove();
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

        // Populate navigation paths for axolotls the player has looked at recently.
        for (Integer axolotlId : AXOLOTL_OVERLAY_EXPIRY.keySet()) {
            SyncAxolotlPathPacket pathPkt = AxolotlGuardianClientEvents.AXOLOTL_PATH_MAP.get(axolotlId);
            if (pathPkt != null && pathPkt.nodeX().length > 0) {
                PENDING_AXOLOTL_PATHS.put(axolotlId, pathPkt);
            }
        }
    }

    public static boolean isWearingGoggles(Player player) {
        return GogglesUtil.isWearingGoggles(player);
    }

    /**
     * True when no solid block sits between the player's eye and the hit point — so the stats
     * popup is hidden when the axolotl is behind a wall.
     */
    private static boolean hasLineOfSight(Minecraft mc, Vec3 eyePos, Vec3 hitPoint) {
        if (hitPoint == null || mc.level == null) {
            return false;
        }
        BlockHitResult clip = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eyePos, hitPoint,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player));
        if (clip.getType() != HitResult.Type.BLOCK) {
            return true;
        }
        return clip.getLocation().distanceToSqr(eyePos) >= eyePos.distanceToSqr(hitPoint) - 1.0e-4;
    }

    /**
     * Client-side overlay eligibility: the player's own axolotls. Ownership is available
     * client-side because SyncAxolotlOwnerPacket mirrors the owner attachment.
     */
    private static boolean isOverlayCandidate(Axolotl axolotl, Player player) {
        return AxolotlGuardianModule.isOwnedBy(axolotl, player.getUUID());
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeTooltip != null) {
            int x = event.getGuiGraphics().guiWidth() / 2 + 10;
            int y = event.getGuiGraphics().guiHeight() / 2 + 10;
            event.getGuiGraphics().renderComponentTooltip(
                    Minecraft.getInstance().font, activeTooltip, x, y);
        }
        if (lookedAtAxolotl != null) {
            renderAxolotlStatsOverlay(event.getGuiGraphics(), Minecraft.getInstance(), lookedAtAxolotl);
        }
    }

    private static void renderAxolotlStatsOverlay(net.minecraft.client.gui.GuiGraphics g, Minecraft mc,
                                                  Axolotl axolotl) {
        int hp = Math.round(axolotl.getHealth());
        int maxHp = Math.round(axolotl.getMaxHealth());
        String healthStr = hp + "/" + maxHp;

        ItemStack armor = axolotl.getData(AxolotlGuardianModule.AXOLOTL_INVENTORY.get()).getArmor();
        boolean hasArmor = !armor.isEmpty();
        String armorStr;
        if (hasArmor) {
            int remaining = armor.getMaxDamage() - armor.getDamageValue();
            armorStr = remaining + "/" + armor.getMaxDamage();
        } else {
            armorStr = "No armor";
        }

        int[] xpData = AxolotlGuardianClientEvents.AXOLOTL_XP_MAP.get(axolotl.getId());
        String xpStr = xpData != null
                ? xpData[0] + "/" + xpData[1]
                : "?/" + AxolotlGuardianModule.getAxolotlXpCapacity();

        String ownerStr = resolveOwnerName(mc, AxolotlGuardianModule.getOwnerUUID(axolotl));

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
        if (PENDING_TARGET_OUTLINES.isEmpty() && PENDING_AXOLOTL_OUTLINES.isEmpty()
                && PENDING_RADIUS_POSITIONS.isEmpty() && PENDING_AXOLOTL_PATHS.isEmpty()) {
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

        // Outline the looked-at guardian axolotls themselves (soft pink) for feedback.
        for (Axolotl axolotl : PENDING_AXOLOTL_OUTLINES) {
            double ex = Mth.lerp(partialTick, axolotl.xOld, axolotl.getX());
            double ey = Mth.lerp(partialTick, axolotl.yOld, axolotl.getY());
            double ez = Mth.lerp(partialTick, axolotl.zOld, axolotl.getZ());
            AABB box = axolotl.getBoundingBox();
            double hw = box.getXsize() / 2.0;
            double h  = box.getYsize();
            double hd = box.getZsize() / 2.0;
            LevelRenderer.renderLineBox(ps, consumer,
                    ex - hw, ey, ez - hd,
                    ex + hw, ey + h, ez + hd,
                    1.0f, 0.55f, 0.8f, 0.9f);
        }

        double guardRadius = AxolotlGuardianModule.getGuardRadius();
        double guardRadiusY = AxolotlGuardianModule.getGuardRadiusY();
        for (BlockPos radiusPos : PENDING_RADIUS_POSITIONS) {
            AABB guardBox = new AABB(radiusPos).inflate(guardRadius, guardRadiusY, guardRadius);
            LevelRenderer.renderLineBox(ps, consumer, guardBox, 0.2f, 0.6f, 1.0f, 0.6f);
        }

        // Navigation path: node boxes (gold-yellow) + line segments between nodes.
        if (!PENDING_AXOLOTL_PATHS.isEmpty()) {
            Matrix4f mat = ps.last().pose();
            PoseStack.Pose pose = ps.last();
            for (SyncAxolotlPathPacket pathPkt : PENDING_AXOLOTL_PATHS.values()) {
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
                        dx /= len;
                        dy /= len;
                        dz /= len;
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
