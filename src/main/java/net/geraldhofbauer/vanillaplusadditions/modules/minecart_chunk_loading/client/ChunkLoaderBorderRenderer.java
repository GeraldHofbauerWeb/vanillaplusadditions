package net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRenderer;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugRenderUtil;
import net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.MinecartChunkLoadingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug overlay: permanently outlines every nearby chunk that contains a Chunk Loader Rail.
 * A chunk is drawn blue normally and <b>red while it is currently being force-loaded</b> (i.e. a
 * minecart is active on a loader rail within the load radius). Rail chunks are found by a cheap
 * palette scan; the loaded state mirrors the server's logic client-side (no networking needed).
 */
public final class ChunkLoaderBorderRenderer implements DebugOverlayRenderer {

    private static final int SCAN_INTERVAL = 10;

    /** chunk -> representative rail Y inside it. */
    private final Map<ChunkPos, Integer> railChunks = new HashMap<>();
    /** chunk of a loader rail a minecart is currently on -> last-seen game tick. */
    private final Map<ChunkPos, Long> activeRailChunks = new HashMap<>();
    private long lastScan = Long.MIN_VALUE;

    @Override
    public void clientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Block rail = MinecartChunkLoadingModule.CHUNK_LOADER_RAIL.get();

        // 1. Track rails a minecart is currently riding (mirrors the server's "active" set).
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AbstractMinecart cart) {
                BlockPos railPos = railAt(mc.level, cart.blockPosition(), rail);
                if (railPos != null) {
                    activeRailChunks.put(new ChunkPos(railPos), now);
                }
            }
        }
        long timeout = MinecartChunkLoadingModule.getActiveTimeoutTicks();
        activeRailChunks.entrySet().removeIf(e -> now - e.getValue() > timeout);

        // 2. Periodically rescan nearby chunks for loader rails.
        // NOTE: use "now >= lastScan + INTERVAL", not "now - lastScan", to avoid long overflow
        // when lastScan is Long.MIN_VALUE (which made the scan never run).
        if (now >= lastScan + SCAN_INTERVAL) {
            lastScan = now;
            scanRailChunks(mc, rail);
        }
    }

    @Override
    public void renderWorld(RenderLevelStageEvent event, PoseStack pose,
                            MultiBufferSource.BufferSource buffers, Vec3 cameraPos, float partialTick) {
        if (railChunks.isEmpty()) {
            return;
        }
        int radius = MinecartChunkLoadingModule.getChunkLoadRadius();
        int span = MinecartChunkLoadingModule.getChunkBorderVerticalSpan();

        // Depth-tested: the borders are occluded by terrain instead of x-raying through blocks.
        VertexConsumer lines = buffers.getBuffer(DebugRenderUtil.DEPTH_LINES);
        VertexConsumer quads = buffers.getBuffer(DebugRenderUtil.DEPTH_QUADS);

        for (Map.Entry<ChunkPos, Integer> entry : railChunks.entrySet()) {
            ChunkPos cp = entry.getKey();
            double centerY = entry.getValue();
            boolean loaded = isLoaded(cp, radius);
            if (loaded) {
                DebugRenderUtil.renderChunkBorder(pose, lines, quads, cp,
                        centerY - span, centerY + span, 1.0f, 0.2f, 0.2f, 0.9f, 0.14f);
            } else {
                DebugRenderUtil.renderChunkBorder(pose, lines, quads, cp,
                        centerY - span, centerY + span, 0.25f, 0.55f, 1.0f, 0.85f, 0.10f);
            }
        }
    }

    /** A rail chunk is "loaded" when an active rail is within the Chebyshev load radius. */
    private boolean isLoaded(ChunkPos chunk, int radius) {
        for (ChunkPos active : activeRailChunks.keySet()) {
            if (Math.max(Math.abs(active.x - chunk.x), Math.abs(active.z - chunk.z)) <= radius) {
                return true;
            }
        }
        return false;
    }

    private void scanRailChunks(Minecraft mc, Block rail) {
        Level level = mc.level;
        int scanRadius = Math.max(1, Math.min(16, MinecartChunkLoadingModule.getChunkBorderScanRadius()));
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;
        int minY = level.getMinBuildHeight();

        Map<ChunkPos, Integer> found = new HashMap<>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                // 2-arg getChunk returns the loaded chunk (or an empty one) without the
                // hasChunk()/maybeHas() palette path that was failing on the client.
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                sectionLoop:
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    int baseY = minY + i * 16;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                if (section.getBlockState(x, y, z).is(rail)) {
                                    found.put(new ChunkPos(cx, cz), baseY + y);
                                    break sectionLoop;
                                }
                            }
                        }
                    }
                }
            }
        }
        railChunks.clear();
        railChunks.putAll(found);
    }

    private static BlockPos railAt(Level level, BlockPos pos, Block rail) {
        BlockState at = level.getBlockState(pos);
        if (at.is(rail)) {
            return pos;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).is(rail)) {
            return below;
        }
        return null;
    }
}
