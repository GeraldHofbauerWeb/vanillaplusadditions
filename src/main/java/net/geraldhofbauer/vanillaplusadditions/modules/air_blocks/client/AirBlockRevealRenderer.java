package net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.AirBlocksModule;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugRenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Purely client-side reveal for {@link AirBlocksModule#AIR_BLOCK}: while the player holds an Air
 * Block Revealer (main or off hand), nearby Air Blocks are drawn as faint translucent boxes so they
 * can be found and mined. Modelled on the anchor-border renderer, but self-contained (its own
 * tick/render listeners) and independent of the goggle-gated debug overlay.
 */
public final class AirBlockRevealRenderer {

    private static final int SCAN_INTERVAL = 10;   // ticks between chunk scans
    private static final AirBlockRevealRenderer INSTANCE = new AirBlockRevealRenderer();

    /** Positions of nearby Air Blocks found by the latest scan. */
    private final List<BlockPos> found = new ArrayList<>();
    private long lastScan = Long.MIN_VALUE;

    private AirBlockRevealRenderer() { }

    /** Registers the tick/render listeners on the game event bus. Call once during client setup. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(INSTANCE::onClientTick);
        NeoForge.EVENT_BUS.addListener(INSTANCE::onRenderLevelStage);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!isHoldingRevealer(mc.player)) {
            if (!found.isEmpty()) {
                found.clear();
            }
            return;
        }
        long now = mc.level.getGameTime();
        if (now >= lastScan + SCAN_INTERVAL) {
            lastScan = now;
            scan(mc);
        }
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (found.isEmpty() || mc.player == null || !isHoldingRevealer(mc.player)) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(DebugRenderUtil.XRAY_LINES);
        VertexConsumer quads = buffers.getBuffer(DebugRenderUtil.XRAY_QUADS);

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (BlockPos pos : found) {
            double x0 = pos.getX();
            double y0 = pos.getY();
            double z0 = pos.getZ();
            // Cyan-ish faint box outlining the single block cell.
            DebugRenderUtil.renderBox(pose, lines, quads, x0, z0, x0 + 1.0, z0 + 1.0,
                    y0, y0 + 1.0, 0.25f, 0.85f, 1.0f, 0.8f, 0.15f);
        }
        pose.popPose();
        buffers.endBatch(DebugRenderUtil.XRAY_LINES);
        buffers.endBatch(DebugRenderUtil.XRAY_QUADS);
    }

    private static boolean isHoldingRevealer(LocalPlayer player) {
        return isRevealer(player.getItemInHand(InteractionHand.MAIN_HAND))
                || isRevealer(player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static boolean isRevealer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AirBlocksModule.AIR_BLOCK_REVEALER.get());
    }

    private void scan(Minecraft mc) {
        Level level = mc.level;
        Block airBlock = AirBlocksModule.AIR_BLOCK.get();
        int radius = AirBlocksModule.getRevealRadius();
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;
        int minY = level.getMinBuildHeight();

        List<BlockPos> hits = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(pcx + dx, pcz + dz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    int baseY = minY + i * 16;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (state.is(airBlock)) {
                                    hits.add(new BlockPos(((pcx + dx) << 4) + x, baseY + y,
                                            ((pcz + dz) << 4) + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        found.clear();
        found.addAll(hits);
    }
}
