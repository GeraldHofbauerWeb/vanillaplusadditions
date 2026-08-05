package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Render types for the spawn overlay: a translucent striped fill, an additive shimmer pass on top
 * of it and plain lines for the spider outline — each in a depth-tested and an x-ray variant.
 *
 * <p>The stripes come from a tiling 45° texture whose UVs the renderer derives from world
 * coordinates and scrolls over time. Doing the animation through UVs rather than a texture matrix
 * keeps it predictable under Iris/Sodium, which the pack runs.</p>
 */
public final class SpawnOverlayRenderTypes {

    /** Seamless 45° stripe texture; the alpha channel carries the pattern. */
    public static final ResourceLocation STRIPES = ResourceLocation.fromNamespaceAndPath(
            "vanillaplusadditions", "textures/misc/spawn_overlay_stripes.png");

    public static final RenderType STRIPES_DEPTH = textured("vpa_spawn_stripes", false, false);
    public static final RenderType STRIPES_XRAY = textured("vpa_spawn_stripes_xray", false, true);
    public static final RenderType SHIMMER_DEPTH = textured("vpa_spawn_shimmer", true, false);
    public static final RenderType SHIMMER_XRAY = textured("vpa_spawn_shimmer_xray", true, true);
    public static final RenderType LINES_DEPTH = lines("vpa_spawn_lines", false);
    public static final RenderType LINES_XRAY = lines("vpa_spawn_lines_xray", true);

    private SpawnOverlayRenderTypes() { }

    /** Every render type the overlay may emit into, in draw order — used to flush the batches. */
    public static List<RenderType> all(boolean xray) {
        return xray
                ? List.of(STRIPES_XRAY, SHIMMER_XRAY, LINES_XRAY)
                : List.of(STRIPES_DEPTH, SHIMMER_DEPTH, LINES_DEPTH);
    }

    public static RenderType stripes(boolean xray) {
        return xray ? STRIPES_XRAY : STRIPES_DEPTH;
    }

    public static RenderType shimmer(boolean xray) {
        return xray ? SHIMMER_XRAY : SHIMMER_DEPTH;
    }

    public static RenderType outline(boolean xray) {
        return xray ? LINES_XRAY : LINES_DEPTH;
    }

    /**
     * Textured quads tinted per vertex. {@code additive} switches the translucent stripe pass to
     * the additive shimmer pass; {@code xray} drops the depth test so markers stay visible through
     * terrain.
     */
    private static RenderType textured(String name, boolean additive, boolean xray) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(
                                GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(STRIPES, true, false))
                        .setTransparencyState(additive
                                ? RenderStateShard.ADDITIVE_TRANSPARENCY
                                : RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(xray
                                ? RenderStateShard.NO_DEPTH_TEST
                                : RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false)
        );
    }

    /** Thin lines for the spider outline (same recipe as the shared debug-overlay line type). */
    private static RenderType lines(String name, boolean xray) {
        return RenderType.create(
                name,
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
                        .setDepthTestState(xray
                                ? RenderStateShard.NO_DEPTH_TEST
                                : RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false)
        );
    }
}
