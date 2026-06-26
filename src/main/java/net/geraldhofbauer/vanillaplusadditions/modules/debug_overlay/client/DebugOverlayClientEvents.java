package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Drives the shared debug overlay: consumes the toggle keybind and dispatches tick/world/HUD
 * rendering to every registered {@link DebugOverlayRenderer}. Renderers run only while the
 * toggle is on AND the player wears goggles, so they never re-check either condition.
 */
@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DebugOverlayClientEvents {

    private DebugOverlayClientEvents() { }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (DebugOverlayKeybinds.TOGGLE.consumeClick()) {
            boolean on = DebugOverlayState.toggle();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(on
                        ? "message.vanillaplusadditions.debug_overlay.on"
                        : "message.vanillaplusadditions.debug_overlay.off"), true);
            }
        }

        if (!active(mc)) {
            return;
        }
        for (DebugOverlayRenderer renderer : DebugOverlayRegistry.renderers()) {
            renderer.clientTick(mc);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || DebugOverlayRegistry.renderers().isEmpty()) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (DebugOverlayRenderer renderer : DebugOverlayRegistry.renderers()) {
            renderer.renderWorld(event, pose, buffers, cam, partialTick);
        }
        pose.popPose();
        // Flush our render types explicitly (matches the proven cat-overlay flush).
        buffers.endBatch(DebugRenderUtil.DEPTH_LINES);
        buffers.endBatch(DebugRenderUtil.DEPTH_QUADS);
        buffers.endBatch(DebugRenderUtil.XRAY_LINES);
        buffers.endBatch(DebugRenderUtil.XRAY_QUADS);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) {
            return;
        }
        for (DebugOverlayRenderer renderer : DebugOverlayRegistry.renderers()) {
            renderer.renderHud(event.getGuiGraphics(), mc);
        }
    }

    /** Overlay is live when toggled on, in-world, and wearing goggles. */
    private static boolean active(Minecraft mc) {
        return DebugOverlayState.isEnabled()
                && mc.player != null
                && mc.level != null
                && GogglesUtil.isWearingGoggles(mc.player);
    }
}
