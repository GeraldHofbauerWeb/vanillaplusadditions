package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.config.MobSpawnOverlayConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws one flat marker per spawn position: a striped translucent field lying on the floor, an
 * additive shimmer sweeping across it, and an outline where a spider would also fit.
 *
 * <p>Stripe UVs are derived from world coordinates so the 45° pattern runs continuously across
 * adjacent markers instead of restarting per block, and are wrapped into {@code [0,1)} per quad —
 * the texture repeats with period 1, so wrapping keeps the pattern seamless while avoiding float
 * precision loss far from the world origin.</p>
 */
public final class SpawnOverlayRenderer {

    /** Keeps neighbouring markers visually separated and avoids z-fighting at block borders. */
    private static final float INSET = 0.03F;
    /** Lift above the floor surface so the marker never z-fights with the block below. */
    private static final float LIFT = 0.015F;
    /** The shimmer uses wider bands and travels faster than the base stripes. */
    private static final float SHIMMER_BAND_FACTOR = 0.35F;
    private static final float SHIMMER_SPEED_FACTOR = 3.5F;

    private SpawnOverlayRenderer() { }

    /**
     * Emits every marker into {@code buffers}. The caller's {@link PoseStack} must already be
     * translated by {@code -cameraPos}; flushing the render types is the caller's job.
     *
     * @param pose      camera-relative pose stack
     * @param buffers   buffer source to emit into
     * @param markers   the positions from the last scan
     * @param config    module configuration (colors, stripe look, x-ray)
     * @param timeTicks game time including partial tick, used to animate the stripes
     */
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers,
                              List<SpawnMarker> markers, MobSpawnOverlayConfig config,
                              float timeTicks) {
        if (markers.isEmpty()) {
            return;
        }

        boolean xray = config.isSeeThroughBlocks();
        float[] nowColor = config.getSpawnNowColor();
        float[] nightColor = config.getSpawnAtNightColor();
        float[] spiderColor = config.getSpiderOutlineColor();
        float shimmerStrength = config.getShimmerStrength();
        boolean drawShimmer = shimmerStrength > 0.0F;

        float seconds = timeTicks / 20.0F;
        float stripeUvPerBlock = 1.0F / config.getStripeScale();
        float stripeScroll = seconds * config.getScrollSpeed() * stripeUvPerBlock;
        float shimmerUvPerBlock = stripeUvPerBlock * SHIMMER_BAND_FACTOR;
        float shimmerScroll = seconds * config.getScrollSpeed() * SHIMMER_SPEED_FACTOR * shimmerUvPerBlock;

        Matrix4f matrix = pose.last().pose();
        VertexConsumer stripes = buffers.getBuffer(SpawnOverlayRenderTypes.stripes(xray));
        VertexConsumer shimmer = drawShimmer
                ? buffers.getBuffer(SpawnOverlayRenderTypes.shimmer(xray)) : null;
        VertexConsumer outline = buffers.getBuffer(SpawnOverlayRenderTypes.outline(xray));

        for (SpawnMarker marker : markers) {
            float x0 = marker.x() + INSET;
            float x1 = marker.x() + 1.0F - INSET;
            float z0 = marker.z() + INSET;
            float z1 = marker.z() + 1.0F - INSET;
            float y = marker.surfaceY() + LIFT;
            float[] color = marker.spawnsNow() ? nowColor : nightColor;

            quad(stripes, matrix, x0, y, z0, x1, z1, stripeUvPerBlock, stripeScroll,
                    color[0], color[1], color[2], color[3]);

            if (drawShimmer) {
                // Additive blending multiplies rgb by alpha, so keeping the marker's hue here
                // makes the glint match the field it sweeps over instead of washing it white.
                quad(shimmer, matrix, x0, y, z0, x1, z1, shimmerUvPerBlock, shimmerScroll,
                        color[0], color[1], color[2], shimmerStrength);
            }

            if (marker.spiderRoom()) {
                outlineRect(outline, pose, x0, y, z0, x1, z1,
                        spiderColor[0], spiderColor[1], spiderColor[2], spiderColor[3]);
            }
        }
    }

    /** One horizontal textured quad with world-derived, time-scrolled UVs. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x0, float y, float z0, float x1, float z1,
                             float uvPerBlock, float scroll,
                             float r, float g, float b, float a) {
        float u0 = wrap(x0 * uvPerBlock + scroll);
        float v0 = wrap(z0 * uvPerBlock + scroll);
        float u1 = u0 + (x1 - x0) * uvPerBlock;
        float v1 = v0 + (z1 - z0) * uvPerBlock;

        consumer.addVertex(matrix, x0, y, z0).setUv(u0, v0).setColor(r, g, b, a);
        consumer.addVertex(matrix, x0, y, z1).setUv(u0, v1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x1, y, z1).setUv(u1, v1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x1, y, z0).setUv(u1, v0).setColor(r, g, b, a);
    }

    /** Four line segments around the marker, flagging "a spider fits here too". */
    private static void outlineRect(VertexConsumer consumer, PoseStack pose,
                                    float x0, float y, float z0, float x1, float z1,
                                    float r, float g, float b, float a) {
        line(consumer, pose, x0, y, z0, x1, y, z0, 1.0F, 0.0F, 0.0F, r, g, b, a);
        line(consumer, pose, x1, y, z0, x1, y, z1, 0.0F, 0.0F, 1.0F, r, g, b, a);
        line(consumer, pose, x1, y, z1, x0, y, z1, -1.0F, 0.0F, 0.0F, r, g, b, a);
        line(consumer, pose, x0, y, z1, x0, y, z0, 0.0F, 0.0F, -1.0F, r, g, b, a);
    }

    private static void line(VertexConsumer consumer, PoseStack pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float nx, float ny, float nz,
                             float r, float g, float b, float a) {
        Matrix4f matrix = pose.last().pose();
        consumer.addVertex(matrix, ax, ay, az).setColor(r, g, b, a).setNormal(pose.last(), nx, ny, nz);
        consumer.addVertex(matrix, bx, by, bz).setColor(r, g, b, a).setNormal(pose.last(), nx, ny, nz);
    }

    /** Wraps into {@code [0,1)} — the stripe texture repeats with period 1 in both axes. */
    private static float wrap(float value) {
        return value - Mth.floor(value);
    }
}
