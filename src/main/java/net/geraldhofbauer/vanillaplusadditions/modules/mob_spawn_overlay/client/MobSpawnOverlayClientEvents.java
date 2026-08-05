package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.MobSpawnOverlayModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.config.MobSpawnOverlayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Drives the spawn overlay: rescans on a timer while it is switched on and draws the markers in
 * world space. The toggle itself arrives from {@code KeyboardHandlerDebugKeyMixin} (F3 + M).
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class MobSpawnOverlayClientEvents {

    private static int tickCounter;

    private MobSpawnOverlayClientEvents() { }

    /**
     * Flips the overlay and tells the player. Called from the debug-key mixin, which has already
     * confirmed the module is enabled.
     */
    public static void toggleFromDebugKey() {
        boolean on = SpawnOverlayState.toggle();
        tickCounter = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(on
                    ? "message.vanillaplusadditions.mob_spawn_overlay.on"
                    : "message.vanillaplusadditions.mob_spawn_overlay.off"), true);
        }
        if (on) {
            rescan(mc);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!SpawnOverlayState.isEnabled() || !MobSpawnOverlayModule.isActiveClientSide()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MobSpawnOverlayConfig config = config();
        if (config == null) {
            return;
        }
        if (++tickCounter < config.getRescanIntervalTicks()) {
            return;
        }
        tickCounter = 0;
        rescan(mc);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!SpawnOverlayState.isEnabled() || SpawnOverlayState.getMarkers().isEmpty()) {
            return;
        }
        MobSpawnOverlayConfig config = config();
        Minecraft mc = Minecraft.getInstance();
        if (config == null || mc.level == null) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float timeTicks = mc.level.getGameTime() % 24000L + partialTick;

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        SpawnOverlayRenderer.render(pose, buffers, SpawnOverlayState.getMarkers(), config, timeTicks);
        pose.popPose();

        // Our render types are not part of vanilla's sorted batch, so flush them explicitly.
        for (var type : SpawnOverlayRenderTypes.all(config.isSeeThroughBlocks())) {
            buffers.endBatch(type);
        }
    }

    private static void rescan(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        MobSpawnOverlayConfig config = config();
        if (level == null || player == null || config == null) {
            return;
        }
        SpawnScanner.scan(level, player.blockPosition(), config);
    }

    private static MobSpawnOverlayConfig config() {
        MobSpawnOverlayModule module = MobSpawnOverlayModule.getInstance();
        return module == null ? null : module.getConfig();
    }
}
