package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

import java.util.OptionalDouble;

/**
 * Shared world-space render types and helpers for debug overlays. Both render types are
 * depth-test-free ("xray") so overlays stay visible through terrain, matching the cat overlay.
 */
public final class DebugRenderUtil {

    /** See-through line render type (copied from the cat overlay). */
    public static final RenderType XRAY_LINES = RenderType.create(
            "vpa_debug_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    /** See-through translucent quad render type for faint fills/bands. */
    public static final RenderType XRAY_QUADS = RenderType.create(
            "vpa_debug_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    /** Depth-tested line render type — occluded by terrain (not visible through blocks). */
    public static final RenderType DEPTH_LINES = RenderType.create(
            "vpa_debug_lines_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    /** Depth-tested translucent quad render type — occluded by terrain. */
    public static final RenderType DEPTH_QUADS = RenderType.create(
            "vpa_debug_quads_depth",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private DebugRenderUtil() { }

    /**
     * Draws a chunk's vertical border as bright thin edge lines plus faint translucent side bands
     * (F3+G-style strips with a transparent background), over the Y band {@code [minY, maxY]}.
     * Coordinates are world-space; the caller's PoseStack must already be camera-relative.
     */
    public static void renderChunkBorder(PoseStack pose, VertexConsumer lines, VertexConsumer quads,
                                         ChunkPos chunk, double minY, double maxY,
                                         float r, float g, float b, float lineAlpha, float fillAlpha) {
        renderChunkBorder(pose, lines, quads, chunk, minY, maxY, r, g, b, lineAlpha, fillAlpha,
                true, true, true, true);
    }

    /**
     * As {@link #renderChunkBorder}, but each translucent side face (N/S/W/E) is only drawn when its
     * flag is set. The bright box outline is always drawn. Callers use this to skip the shared
     * internal walls between two same-state chunks so the fills don't stack into an opaque fog.
     */
    public static void renderChunkBorder(PoseStack pose, VertexConsumer lines, VertexConsumer quads,
                                         ChunkPos chunk, double minY, double maxY,
                                         float r, float g, float b, float lineAlpha, float fillAlpha,
                                         boolean fillNorth, boolean fillSouth,
                                         boolean fillWest, boolean fillEast) {
        double x0 = chunk.getMinBlockX();
        double z0 = chunk.getMinBlockZ();
        double x1 = x0 + 16.0;
        double z1 = z0 + 16.0;

        // Bright box outline (12 edges) — the thin stripes. Always drawn (keeps the chunk grid).
        LevelRenderer.renderLineBox(pose, lines, x0, minY, z0, x1, maxY, z1, r, g, b, lineAlpha);

        // Faint translucent side faces — only on requested (boundary) sides.
        Matrix4f mat = pose.last().pose();
        if (fillNorth) {
            sideQuad(quads, mat, x0, minY, z0, x1, maxY, z0, r, g, b, fillAlpha); // z = z0
        }
        if (fillSouth) {
            sideQuad(quads, mat, x0, minY, z1, x1, maxY, z1, r, g, b, fillAlpha); // z = z1
        }
        if (fillWest) {
            sideQuad(quads, mat, x0, minY, z0, x0, maxY, z1, r, g, b, fillAlpha); // x = x0
        }
        if (fillEast) {
            sideQuad(quads, mat, x1, minY, z0, x1, maxY, z1, r, g, b, fillAlpha); // x = x1
        }
    }

    /**
     * Draws an arbitrary axis-aligned vertical box (bright outline + 4 faint translucent side faces)
     * over the world-space rectangle [x0,x1]×[z0,z1] and Y band [minY,maxY]. Used e.g. to outline a
     * Chunk Anchor's whole forced area as one box (no internal walls).
     */
    public static void renderBox(PoseStack pose, VertexConsumer lines, VertexConsumer quads,
                                 double x0, double z0, double x1, double z1, double minY, double maxY,
                                 float r, float g, float b, float lineAlpha, float fillAlpha) {
        LevelRenderer.renderLineBox(pose, lines, x0, minY, z0, x1, maxY, z1, r, g, b, lineAlpha);
        Matrix4f mat = pose.last().pose();
        sideQuad(quads, mat, x0, minY, z0, x1, maxY, z0, r, g, b, fillAlpha); // north
        sideQuad(quads, mat, x0, minY, z1, x1, maxY, z1, r, g, b, fillAlpha); // south
        sideQuad(quads, mat, x0, minY, z0, x0, maxY, z1, r, g, b, fillAlpha); // west
        sideQuad(quads, mat, x1, minY, z0, x1, maxY, z1, r, g, b, fillAlpha); // east
    }

    /** A single vertical quad from (ax,minY,az)→(bx,maxY,bz). NO_CULL makes winding irrelevant. */
    private static void sideQuad(VertexConsumer quads, Matrix4f mat,
                                 double ax, double minY, double az,
                                 double bx, double maxY, double bz,
                                 float r, float g, float b, float a) {
        float fax = (float) ax;
        float faz = (float) az;
        float fbx = (float) bx;
        float fbz = (float) bz;
        float lo = (float) minY;
        float hi = (float) maxY;
        quads.addVertex(mat, fax, lo, faz).setColor(r, g, b, a);
        quads.addVertex(mat, fbx, lo, fbz).setColor(r, g, b, a);
        quads.addVertex(mat, fbx, hi, fbz).setColor(r, g, b, a);
        quads.addVertex(mat, fax, hi, faz).setColor(r, g, b, a);
    }
}
