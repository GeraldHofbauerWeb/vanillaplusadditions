package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.spot;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config.MysticalCatConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Code-driven natural generation of cat spots (no worldgen JSON, per repo convention). A freshly
 * generated chunk that passes a deterministic per-chunk roll is queued; the queue is drained a
 * little each level tick, and each candidate is placed only if the terrain is a flat, dry,
 * allowed-biome patch far enough from existing spots.
 */
public final class SpotSpawner {

    private static final SpotSpawner INSTANCE = new SpotSpawner();
    private static final int MAX_PLACEMENTS_PER_TICK = 1;

    /** Per-level queue of candidate chunks (packed {@link ChunkPos}). */
    private final Map<ResourceKey<Level>, Deque<Long>> queued = new HashMap<>();

    private SpotSpawner() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!MysticalCatModule.isActive() || !event.isNewChunk()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MysticalCatConfig cfg = MysticalCatModule.config();
        if (cfg == null || !cfg.isNaturalSpawning()) {
            return;
        }
        if (!isAllowedDimension(level, cfg)) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        long roll = mix(level.getSeed(), pos.x, pos.z);
        if (Math.floorMod(roll, Math.max(1, cfg.getSpawnChancePerChunk())) != 0) {
            return;
        }
        queued.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).add(pos.toLong());
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Deque<Long> queue = queued.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        MysticalCatConfig cfg = MysticalCatModule.config();
        if (cfg == null || !MysticalCatModule.isActive()) {
            queue.clear();
            return;
        }
        for (int i = 0; i < MAX_PLACEMENTS_PER_TICK && !queue.isEmpty(); i++) {
            ChunkPos chunk = new ChunkPos(queue.poll());
            tryPlace(level, cfg, chunk);
        }
    }

    private void tryPlace(ServerLevel level, MysticalCatConfig cfg, ChunkPos chunk) {
        // Keep the 3×3 pad well inside the chunk (≥4 from every edge).
        int x = chunk.getMinBlockX() + 4 + level.getRandom().nextInt(8);
        int z = chunk.getMinBlockZ() + 4 + level.getRandom().nextInt(8);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos center = new BlockPos(x, y, z);

        if (isDeniedBiome(level, cfg, center)) {
            return;
        }
        if (MysticalCatSpotData.get(level).hasSpotWithin(center, cfg.getMinSpotDistance())) {
            return;
        }
        if (!isFlatDry(level, x, z, y)) {
            return;
        }
        SpotLayoutBuilder.buildSpot(level, x, z);
    }

    /** 5×5 flatness: surface heights differ by ≤1, ground is solid, and nothing is a fluid. */
    private boolean isFlatDry(ServerLevel level, int cx, int cz, int centerY) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(h - centerY) > 1) {
                    return false;
                }
                FluidState fluidTop = level.getFluidState(new BlockPos(x, h, z));
                FluidState fluidBelow = level.getFluidState(new BlockPos(x, h - 1, z));
                if (!fluidTop.isEmpty() || !fluidBelow.isEmpty()) {
                    return false;
                }
                if (!level.getBlockState(new BlockPos(x, h - 1, z)).isSolid()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isAllowedDimension(ServerLevel level, MysticalCatConfig cfg) {
        String id = level.dimension().location().toString();
        for (String allowed : cfg.getAllowedDimensions()) {
            if (id.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeniedBiome(ServerLevel level, MysticalCatConfig cfg, BlockPos pos) {
        String biomeId = level.getBiome(pos).unwrapKey()
                .map(k -> k.location().toString()).orElse("");
        for (String denied : cfg.getDeniedBiomes()) {
            if (!denied.isBlank() && biomeId.contains(denied)) {
                return true;
            }
        }
        return false;
    }

    /** Deterministic per-chunk hash (SplitMix64-style) of the level seed and chunk coords. */
    private static long mix(long seed, int chunkX, int chunkZ) {
        long h = seed ^ (chunkX * 0x9E3779B97F4A7C15L) ^ (chunkZ * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return h;
    }
}
