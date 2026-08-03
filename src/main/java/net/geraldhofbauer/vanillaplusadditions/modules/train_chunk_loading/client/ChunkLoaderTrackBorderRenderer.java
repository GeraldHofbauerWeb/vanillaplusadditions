package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRenderer;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugRenderUtil;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.TrainChunkLoadingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug overlay: permanently outlines every nearby chunk that contains a Chunk Loader Track.
 * A chunk is drawn blue normally and <b>red while it is currently being force-loaded</b> (i.e. a
 * train carriage is active on a loader track within the load radius). Track chunks are found by a
 * cheap palette scan; the loaded state mirrors the server's logic client-side (no networking
 * needed). Copy of the minecart module's renderer with the carriage footprint scan swapped in.
 */
public final class ChunkLoaderTrackBorderRenderer implements DebugOverlayRenderer {

    private static final int SCAN_INTERVAL = 10;

    /** chunk -> representative track Y inside it. */
    private final Map<ChunkPos, Integer> trackChunks = new HashMap<>();
    /** chunk of a loader track a carriage is currently on -> last-seen game tick. */
    private final Map<ChunkPos, Long> activeTrackChunks = new HashMap<>();
    private long lastScan = Long.MIN_VALUE;

    @Override
    public void clientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Block track = TrainChunkLoadingModule.CHUNK_LOADER_TRACK.get();

        // 1. Track loader tracks a carriage is currently over (mirrors the server's "active" set).
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CarriageContraptionEntity carriage) {
                markCarriageTracks(mc.level, carriage, track, now);
            }
        }
        long timeout = TrainChunkLoadingModule.getActiveTimeoutTicks();
        activeTrackChunks.entrySet().removeIf(e -> now - e.getValue() > timeout);

        // 2. Periodically rescan nearby chunks for loader tracks.
        // NOTE: use "now >= lastScan + INTERVAL", not "now - lastScan", to avoid long overflow
        // when lastScan is Long.MIN_VALUE (which made the scan never run).
        if (now >= lastScan + SCAN_INTERVAL) {
            lastScan = now;
            scanTrackChunks(mc, track);
        }
    }

    private void markCarriageTracks(Level level, CarriageContraptionEntity carriage, Block track, long now) {
        AABB box = carriage.getBoundingBox();
        int baseY = Mth.floor(box.minY);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), baseY - 2, Mth.floor(box.minZ),
                Mth.floor(box.maxX), baseY + 1, Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(track)) {
                activeTrackChunks.put(new ChunkPos(pos), now);
            }
        }
    }

    @Override
    public void renderWorld(RenderLevelStageEvent event, PoseStack pose,
                            MultiBufferSource.BufferSource buffers, Vec3 cameraPos, float partialTick) {
        if (trackChunks.isEmpty()) {
            return;
        }
        int radius = TrainChunkLoadingModule.getChunkLoadRadius();
        int span = TrainChunkLoadingModule.getChunkBorderVerticalSpan();

        // Depth-tested: the borders are occluded by terrain instead of x-raying through blocks.
        VertexConsumer lines = buffers.getBuffer(DebugRenderUtil.DEPTH_LINES);
        VertexConsumer quads = buffers.getBuffer(DebugRenderUtil.DEPTH_QUADS);

        // Precompute each track chunk's loaded state so we can cull shared internal walls.
        Map<ChunkPos, Boolean> loadedState = new HashMap<>();
        for (ChunkPos cp : trackChunks.keySet()) {
            loadedState.put(cp, isLoaded(cp, radius));
        }

        for (Map.Entry<ChunkPos, Integer> entry : trackChunks.entrySet()) {
            ChunkPos cp = entry.getKey();
            double centerY = entry.getValue();
            boolean loaded = loadedState.get(cp);
            // Draw a fill wall on a side only when the neighbour there is NOT a track chunk of the
            // same state — i.e. only the outer boundary of each same-state region, never internal
            // walls. This stops the translucent fills from stacking into an opaque fog.
            boolean north = !sameState(loadedState, cp.x, cp.z - 1, loaded);
            boolean south = !sameState(loadedState, cp.x, cp.z + 1, loaded);
            boolean west = !sameState(loadedState, cp.x - 1, cp.z, loaded);
            boolean east = !sameState(loadedState, cp.x + 1, cp.z, loaded);

            if (loaded) {
                // Red (loaded). Fills are single-layer (internal walls culled), so a moderate alpha.
                DebugRenderUtil.renderChunkBorder(pose, lines, quads, cp,
                        centerY - span, centerY + span, 1.0f, 0.2f, 0.2f, 0.45f, 0.02f,
                        north, south, west, east);
            } else {
                // Blue (not loaded).
                DebugRenderUtil.renderChunkBorder(pose, lines, quads, cp,
                        centerY - span, centerY + span, 0.25f, 0.55f, 1.0f, 0.6f, 0.03f,
                        north, south, west, east);
            }
        }
    }

    /** True if the neighbour chunk (nx,nz) is a tracked loader chunk with the same loaded state. */
    private static boolean sameState(Map<ChunkPos, Boolean> states, int nx, int nz, boolean loaded) {
        Boolean s = states.get(new ChunkPos(nx, nz));
        return s != null && s == loaded;
    }

    /** A track chunk is "loaded" when an active track is within the Chebyshev load radius. */
    private boolean isLoaded(ChunkPos chunk, int radius) {
        for (ChunkPos active : activeTrackChunks.keySet()) {
            if (Math.max(Math.abs(active.x - chunk.x), Math.abs(active.z - chunk.z)) <= radius) {
                return true;
            }
        }
        return false;
    }

    private void scanTrackChunks(Minecraft mc, Block track) {
        Level level = mc.level;
        int scanRadius = Math.max(1, Math.min(16, TrainChunkLoadingModule.getChunkBorderScanRadius()));
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
                                if (section.getBlockState(x, y, z).is(track)) {
                                    found.put(new ChunkPos(cx, cz), baseY + y);
                                    break sectionLoop;
                                }
                            }
                        }
                    }
                }
            }
        }
        trackChunks.clear();
        trackChunks.putAll(found);
    }
}
