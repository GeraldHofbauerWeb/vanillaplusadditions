package net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRenderer;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugRenderUtil;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.StationaryChunkLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.block.ChunkAnchorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug overlay: outlines the forced area of every nearby Chunk Anchor as one box, distinct from the
 * loader-rail borders — <b>green while the anchor is powered/active</b> and <b>grey while inactive</b>
 * (no redstone signal). Anchors are located by a cheap palette scan; the powered state is read from
 * the (client-synced) block state.
 */
public final class AnchorBorderRenderer implements DebugOverlayRenderer {

    private static final int SCAN_INTERVAL = 20;
    private static final int SCAN_RADIUS = 8;     // chunks around the player
    private static final int VERTICAL_SPAN = 24;  // blocks above/below the anchor

    /** anchor block pos -> powered (active). */
    private final Map<BlockPos, Boolean> anchors = new HashMap<>();
    private long lastScan = Long.MIN_VALUE;

    @Override
    public void clientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now >= lastScan + SCAN_INTERVAL) {
            lastScan = now;
            scanAnchors(mc);
        }
    }

    @Override
    public void renderWorld(RenderLevelStageEvent event, PoseStack pose,
                            MultiBufferSource.BufferSource buffers, Vec3 cameraPos, float partialTick) {
        if (anchors.isEmpty()) {
            return;
        }
        int radius = StationaryChunkLoaderModule.getChunkLoadRadius();
        VertexConsumer lines = buffers.getBuffer(DebugRenderUtil.DEPTH_LINES);
        VertexConsumer quads = buffers.getBuffer(DebugRenderUtil.DEPTH_QUADS);

        for (Map.Entry<BlockPos, Boolean> entry : anchors.entrySet()) {
            BlockPos pos = entry.getKey();
            boolean powered = entry.getValue();
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            double x0 = (cx - radius) * 16.0;
            double z0 = (cz - radius) * 16.0;
            double x1 = (cx + radius + 1) * 16.0;
            double z1 = (cz + radius + 1) * 16.0;
            double cy = pos.getY();
            if (powered) {
                // Green — active (forcing).
                DebugRenderUtil.renderBox(pose, lines, quads, x0, z0, x1, z1,
                        cy - VERTICAL_SPAN, cy + VERTICAL_SPAN, 0.2f, 1.0f, 0.35f, 0.7f, 0.03f);
            } else {
                // Grey — inactive (no redstone signal).
                DebugRenderUtil.renderBox(pose, lines, quads, x0, z0, x1, z1,
                        cy - VERTICAL_SPAN, cy + VERTICAL_SPAN, 0.6f, 0.6f, 0.6f, 0.5f, 0.02f);
            }
        }
    }

    private void scanAnchors(Minecraft mc) {
        Level level = mc.level;
        Block anchor = StationaryChunkLoaderModule.CHUNK_ANCHOR.get();
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;
        int minY = level.getMinBuildHeight();

        Map<BlockPos, Boolean> found = new HashMap<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
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
                                if (state.is(anchor)) {
                                    boolean powered = state.hasProperty(ChunkAnchorBlock.POWERED)
                                            && state.getValue(ChunkAnchorBlock.POWERED);
                                    found.put(new BlockPos(((pcx + dx) << 4) + x, baseY + y, ((pcz + dz) << 4) + z),
                                            powered);
                                }
                            }
                        }
                    }
                }
            }
        }
        anchors.clear();
        anchors.putAll(found);
    }
}
