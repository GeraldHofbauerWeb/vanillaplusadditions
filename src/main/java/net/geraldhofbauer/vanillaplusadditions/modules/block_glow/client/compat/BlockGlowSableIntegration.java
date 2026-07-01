package net.geraldhofbauer.vanillaplusadditions.modules.block_glow.client.compat;

import net.geraldhofbauer.vanillaplusadditions.core.Vpa;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.client.BlockGlowHighlight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Optional Sable/Create Aeronautics integration for BlockGlow.
 * Uses reflection so the mod still loads cleanly without Sable installed.
 *
 * This class is intentionally placed in the client.compat package because it
 * references ClientLevel, a client-only class, and must never be loaded on
 * a dedicated server.
 */
public final class BlockGlowSableIntegration {
    private static final String SABLE_MODID = "sable";
    private static volatile SableReflectionApi reflectionApi;
    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionWarningLogged;

    private BlockGlowSableIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(SABLE_MODID);
    }

    public static void collectHighlights(ClientLevel level, Block targetBlock, AABB searchBox, Vec3 playerPos,
            Consumer<BlockGlowHighlight> consumer) {
        if (!isLoaded()) {
            return;
        }

        SableReflectionApi api = getReflectionApi();
        if (api == null) {
            return;
        }

        try {
            Object container = api.getContainer.invoke(null, level);
            if (container == null) {
                return;
            }

            // Create a fresh BoundingBox3d from the original AABB for the container query.
            // NOTE: BoundingBox3d.transformInverse(Pose3dc) mutates the instance in-place
            // (JOML-style mutable API), so we must create a fresh copy per sub-level
            // iteration to avoid using an already-transformed box in subsequent iterations.
            Object queryBox = api.boundingBoxFromAabb.newInstance(searchBox);
            Iterable<?> subLevels = (Iterable<?>) api.queryIntersecting.invoke(container, queryBox);
            for (Object subLevel : subLevels) {
                Object pose = api.logicalPose.invoke(subLevel);

                // Fresh local search box for each sub-level to prevent mutation carry-over
                Object localSearchBox = api.boundingBoxFromAabb.newInstance(searchBox);
                api.transformInverse.invoke(localSearchBox, pose);

                double minX = (double) api.minX.invoke(localSearchBox);
                double minY = (double) api.minY.invoke(localSearchBox);
                double minZ = (double) api.minZ.invoke(localSearchBox);
                double maxX = (double) api.maxX.invoke(localSearchBox);
                double maxY = (double) api.maxY.invoke(localSearchBox);
                double maxZ = (double) api.maxZ.invoke(localSearchBox);

                int startX = Mth.floor(minX);
                int startY = Mth.floor(minY);
                int startZ = Mth.floor(minZ);
                int endX = Mth.ceil(maxX) - 1;
                int endY = Mth.ceil(maxY) - 1;
                int endZ = Mth.ceil(maxZ) - 1;

                if (endX < startX || endY < startY || endZ < startZ) {
                    continue;
                }

                ClientLevel subLevelClientLevel = (ClientLevel) api.getLevel.invoke(subLevel);
                for (int x = startX; x <= endX; x++) {
                    for (int y = startY; y <= endY; y++) {
                        for (int z = startZ; z <= endZ; z++) {
                            BlockPos localPos = new BlockPos(x, y, z);
                            if (!subLevelClientLevel.getBlockState(localPos).is(targetBlock)) {
                                continue;
                            }

                            Object localBlockBox = api.boundingBoxFromBlockPos.newInstance(localPos);
                            Object transformedWorldBox = api.transform.invoke(localBlockBox, pose);
                            AABB worldAabb = (AABB) api.toMojang.invoke(transformedWorldBox);
                            consumer.accept(new BlockGlowHighlight(worldAabb, worldAabb.getCenter().distanceToSqr(playerPos)));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            warnOnce("Failed to access Sable sub-level data for BlockGlow", ex);
        }
    }

    private static SableReflectionApi getReflectionApi() {
        if (reflectionInitialized) {
            return reflectionApi;
        }

        synchronized (BlockGlowSableIntegration.class) {
            if (!reflectionInitialized) {
                try {
                    reflectionApi = new SableReflectionApi();
                } catch (ReflectiveOperationException ex) {
                    warnOnce("Failed to initialize Sable BlockGlow integration", ex);
                    reflectionApi = null;
                }
                reflectionInitialized = true;
            }
            return reflectionApi;
        }
    }

    private static void warnOnce(String message, Throwable ex) {
        if (reflectionWarningLogged) {
            return;
        }

        reflectionWarningLogged = true;
        Vpa.LOGGER.warn(message + ": {}", ex.getMessage());
    }

    private static final class SableReflectionApi {
        private final Constructor<?> boundingBoxFromAabb;
        private final Constructor<?> boundingBoxFromBlockPos;
        private final Method getContainer;
        private final Method queryIntersecting;
        private final Method logicalPose;
        private final Method getLevel;
        private final Method transformInverse;
        private final Method transform;
        private final Method toMojang;
        private final Method minX;
        private final Method minY;
        private final Method minZ;
        private final Method maxX;
        private final Method maxY;
        private final Method maxZ;

        private SableReflectionApi() throws ReflectiveOperationException {
            Class<?> subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> boundingBox3dcClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            Class<?> boundingBox3dClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
            Class<?> pose3dcClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");

            this.boundingBoxFromAabb = boundingBox3dClass.getConstructor(AABB.class);
            this.boundingBoxFromBlockPos = boundingBox3dClass.getConstructor(BlockPos.class);
            this.getContainer = subLevelContainerClass.getMethod("getContainer", ClientLevel.class);
            this.queryIntersecting = subLevelContainerClass.getMethod("queryIntersecting", boundingBox3dcClass);
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            this.logicalPose = subLevelClass.getMethod("logicalPose");
            this.getLevel = subLevelClass.getMethod("getLevel");
            this.transformInverse = boundingBox3dClass.getMethod("transformInverse", pose3dcClass);
            this.transform = boundingBox3dClass.getMethod("transform", pose3dcClass);
            this.toMojang = boundingBox3dcClass.getMethod("toMojang");
            this.minX = boundingBox3dcClass.getMethod("minX");
            this.minY = boundingBox3dcClass.getMethod("minY");
            this.minZ = boundingBox3dcClass.getMethod("minZ");
            this.maxX = boundingBox3dcClass.getMethod("maxX");
            this.maxY = boundingBox3dcClass.getMethod("maxY");
            this.maxZ = boundingBox3dcClass.getMethod("maxZ");
        }
    }
}

