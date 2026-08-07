package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.GogglesUtil;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.InventoryLinkModule;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client.compat.InventoryLinkSableRender;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.LinkMode;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.PendingSelection;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.LinkDisplay;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.LinkOverlayPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.OpenLinkScreenPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.RequestLinkOverlayPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.RequestOpenLinkScreenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.OptionalDouble;

/**
 * Client half of the Inventory Linker: the see-through outlines for links, the Ctrl+right-click
 * shortcut into the settings screen, and the throttled queries that feed both.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class InventoryLinkClientEvents {

    private static final RenderType XRAY_LINES = RenderType.create(
            "inventorylink_xray_lines",
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

    /** Minimum ticks between two overlay requests, so looking around does not spam the server. */
    private static final long REQUEST_COOLDOWN_TICKS = 10L;

    private static long lastRequestTick = Long.MIN_VALUE;
    private static BlockPos lastRequestPos;

    private InventoryLinkClientEvents() {
    }

    // ---- Ctrl + right-click: open the settings screen instead of linking ----

    /**
     * Runs before the use-item flow starts, so cancelling here also suppresses the vanilla
     * interaction packet — the server never sees a click that was meant to open the screen.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !isEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !isHoldingLinker(minecraft.player)) {
            return;
        }
        if (!InventoryLinkKeybinds.isModifierDown()) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        PacketDistributor.sendToServer(new RequestOpenLinkScreenPacket(hit.getBlockPos()));
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    // ---- Overlay data ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }
        long now = minecraft.level.getGameTime();
        InventoryLinkClientCache.prune(now);
        // While a screen is open the crosshair is frozen — asking again would only re-fetch the
        // block behind the GUI.
        if (minecraft.screen != null || !isEnabled() || !shouldShowOverlay(minecraft.player)) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        InventoryLinkClientCache.Entry entry = InventoryLinkClientCache.get(pos);
        if (entry != null && !entry.needsRefresh(now)) {
            return;
        }
        if (pos.equals(lastRequestPos) && now - lastRequestTick < REQUEST_COOLDOWN_TICKS) {
            return;
        }
        lastRequestPos = pos.immutable();
        lastRequestTick = now;
        PacketDistributor.sendToServer(new RequestLinkOverlayPacket(pos));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        InventoryLinkClientCache.clear();
        lastRequestPos = null;
        lastRequestTick = Long.MIN_VALUE;
    }

    // ---- Rendering ----

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !isEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean holdingLinker = isHoldingLinker(minecraft.player);
        if (!holdingLinker && !GogglesUtil.isWearingGoggles(minecraft.player)) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(XRAY_LINES);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        if (holdingLinker) {
            renderPendingSelection(minecraft, poseStack, consumer);
        }
        renderLinks(minecraft, poseStack, consumer);

        poseStack.popPose();
        minecraft.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }

    /** The block picked by the first click, waiting for its partner. */
    private static void renderPendingSelection(Minecraft minecraft, PoseStack poseStack, VertexConsumer consumer) {
        ItemStack stack = linkerInHand(minecraft.player);
        if (stack == null) {
            return;
        }
        PendingSelection pending = stack.get(InventoryLinkModule.PENDING_LINK.get());
        if (pending == null) {
            return;
        }
        if (!pending.dimension().equals(minecraft.level.dimension().location().toString())) {
            return;
        }
        AABB box = InventoryLinkSableRender.toRenderBox(minecraft.level, pending.pos(), new AABB(pending.pos()));
        LevelRenderer.renderLineBox(poseStack, consumer, box.inflate(0.002), 1.0f, 1.0f, 1.0f, 0.9f);
    }

    private static void renderLinks(Minecraft minecraft, PoseStack poseStack, VertexConsumer consumer) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        InventoryLinkClientCache.Entry entry = InventoryLinkClientCache.get(hit.getBlockPos());
        if (entry == null || entry.isExpired(minecraft.level.getGameTime())) {
            return;
        }
        for (LinkDisplay link : entry.links()) {
            AABB nearBox = InventoryLinkSableRender.toRenderBox(minecraft.level, link.nearPos(), link.nearBox());
            AABB farBox = InventoryLinkSableRender.toRenderBox(minecraft.level, link.farPos(), link.farBox());

            drawBox(poseStack, consumer, nearBox, LinkMode.fromId(link.nearMode()));
            drawBox(poseStack, consumer, farBox, LinkMode.fromId(link.farMode()));
            drawLine(poseStack, consumer, nearBox.getCenter(), farBox.getCenter());
        }
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer consumer, AABB box, LinkMode mode) {
        float red;
        float green;
        float blue;
        switch (mode) {
            case INPUT -> {
                red = 0.2f;
                green = 0.8f;
                blue = 1.0f;
            }
            case OUTPUT -> {
                red = 1.0f;
                green = 0.6f;
                blue = 0.15f;
            }
            default -> {
                red = InventoryLinkModule.getOutlineRed();
                green = InventoryLinkModule.getOutlineGreen();
                blue = InventoryLinkModule.getOutlineBlue();
            }
        }
        LevelRenderer.renderLineBox(poseStack, consumer, box.inflate(0.002), red, green, blue,
                InventoryLinkModule.getOutlineAlpha());
    }

    private static void drawLine(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Vector3f normal = new Vector3f(
                (float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0e-6f) {
            return;
        }
        normal.normalize();

        float red = InventoryLinkModule.getOutlineRed();
        float green = InventoryLinkModule.getOutlineGreen();
        float blue = InventoryLinkModule.getOutlineBlue();
        float alpha = InventoryLinkModule.getOutlineAlpha();
        consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normal.x, normal.y, normal.z);
        consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }

    // ---- Packet handlers ----

    public static void handleLinkOverlay(LinkOverlayPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        InventoryLinkClientCache.put(packet.queried(), packet.links(), now);
        // Only the screen's own block may update it — never swap its contents for another block's.
        if (minecraft.screen instanceof InventoryLinkScreen screen && screen.isShowing(packet.queried())) {
            screen.refresh(packet.queried(), packet.links());
        }
    }

    public static void handleOpenScreen(OpenLinkScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        // Deferred: opening a screen straight from the packet handler can be overridden by the
        // screen change that is already in flight this tick.
        minecraft.tell(() -> minecraft.setScreen(new InventoryLinkScreen(packet.pos(), packet.links())));
    }

    // ---- Helpers ----

    private static boolean isEnabled() {
        Module module = ModuleManager.getInstance().getModule("inventory_link");
        return module instanceof InventoryLinkModule linkModule && linkModule.isModuleEnabled();
    }

    private static boolean shouldShowOverlay(Player player) {
        return isHoldingLinker(player) || GogglesUtil.isWearingGoggles(player);
    }

    private static boolean isHoldingLinker(Player player) {
        return linkerInHand(player) != null;
    }

    private static ItemStack linkerInHand(Player player) {
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(InventoryLinkModule.INVENTORY_LINKER.get())) {
                return stack;
            }
        }
        return null;
    }
}
