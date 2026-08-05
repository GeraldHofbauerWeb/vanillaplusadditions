package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.config.MobSpawnOverlayConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the positions where hostile mobs can spawn around the player, mirroring vanilla's own
 * spawn checks so the overlay does not drift from actual game behaviour.
 *
 * <p>Per position, in order of cost:</p>
 * <ol>
 *   <li>block light against {@link DimensionType#monsterSpawnBlockLightLimit()} — cheapest reject</li>
 *   <li>{@link SpawnPlacementTypes#ON_GROUND} placement, which is what vanilla asks: the block
 *       below must be a valid spawn surface and this block plus the one above must pass
 *       {@code NaturalSpawner.isValidEmptySpawnBlock} (no full collision shape, no redstone
 *       source, no fluid, not {@code #prevent_mob_spawning_inside}, not dangerous)</li>
 *   <li>no collision for the mob's actual spawn hitbox — vanilla checks this too, and it is what
 *       keeps wide mobs out of narrow gaps</li>
 *   <li>the light test itself, evaluated against the dimension's {@link IntProvider} range rather
 *       than a hardcoded 0..7, so the Nether and the End come out right as well</li>
 * </ol>
 *
 * <p>Deliberately not checked: the 24-block player distance and the mob cap. Both are momentary,
 * and for spawn-proofing you want to see the permanent state of a room.</p>
 */
public final class SpawnScanner {

    /** Stand-in for the common 1-wide monsters (zombie, skeleton, creeper, …). */
    private static final EntityType<?> GROUND_MONSTER = EntityType.ZOMBIE;

    private SpawnScanner() { }

    /**
     * Scans the configured box around {@code center} and stores the result in
     * {@link SpawnOverlayState}.
     *
     * @param level  the client level to inspect
     * @param center the player's block position
     * @param config the module configuration (radii, cap, spider toggle)
     */
    public static void scan(ClientLevel level, BlockPos center, MobSpawnOverlayConfig config) {
        int radiusH = config.getHorizontalRadius();
        int radiusV = config.getVerticalRadius();
        int cap = config.getMaxMarkers();
        boolean wantSpiders = config.shouldMarkSpiderSpots();

        DimensionType dimension = level.dimensionType();
        int blockLightLimit = dimension.monsterSpawnBlockLightLimit();
        IntProvider lightTest = dimension.monsterSpawnLightTest();
        int alwaysDarkEnough = lightTest.getMinValue();
        int everDarkEnough = lightTest.getMaxValue();

        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radiusV);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radiusV);

        List<SpawnMarker> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean hitCap = false;

        for (int y = minY; y <= maxY && !hitCap; y++) {
            for (int x = center.getX() - radiusH; x <= center.getX() + radiusH && !hitCap; x++) {
                for (int z = center.getZ() - radiusH; z <= center.getZ() + radiusH; z++) {
                    pos.set(x, y, z);

                    if (level.getBrightness(LightLayer.BLOCK, pos) > blockLightLimit) {
                        continue;
                    }
                    int localBrightness = level.getMaxLocalRawBrightness(pos);
                    if (localBrightness > everDarkEnough) {
                        continue;
                    }
                    if (!fits(level, pos, GROUND_MONSTER)) {
                        continue;
                    }

                    // Vanilla additionally rolls against the raw sky light: a position that still
                    // sees the sky can fail the check even at night, so only sky light 0 counts
                    // as a guaranteed spawn.
                    boolean spawnsNow = localBrightness <= alwaysDarkEnough
                            && level.getBrightness(LightLayer.SKY, pos) == 0;
                    boolean spiderRoom = wantSpiders && fits(level, pos, EntityType.SPIDER);

                    found.add(new SpawnMarker(x, y, z, surfaceY(level, pos), spawnsNow, spiderRoom));
                    if (found.size() >= cap) {
                        hitCap = true;
                        break;
                    }
                }
            }
        }

        SpawnOverlayState.setResult(found, hitCap);
    }

    /**
     * Whether {@code type} could be placed at {@code pos}: vanilla's ON_GROUND placement plus a
     * collision test against the type's real spawn hitbox.
     */
    private static boolean fits(ClientLevel level, BlockPos pos, EntityType<?> type) {
        if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, type)) {
            return false;
        }
        AABB box = type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return level.noCollision(box);
    }

    /**
     * Absolute world Y of the surface the mob stands on: the top of the block below's collision
     * shape. Keeps markers flush on slabs, farmland and similar rather than floating at full
     * block height. Falls back to the block boundary when the shape is empty.
     */
    private static float surfaceY(ClientLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
        double top = shape.isEmpty() ? 1.0D : shape.max(Direction.Axis.Y);
        return (float) (below.getY() + Mth.clamp(top, 0.0D, 1.0D));
    }
}
