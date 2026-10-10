package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.FastColor;
import org.slf4j.Logger;

/**
 * Builds the blue Chunk Loader Track sprites at atlas-stitch time from Create's own track textures.
 *
 * <p><b>Why:</b> up to beta.103 the jar carried four recoloured copies of Create's
 * {@code standard_track}, {@code standard_track_mip}, {@code standard_track_crossing} and
 * {@code item/track}. Create's assets are "All Rights Reserved", so those PNGs could not go into a
 * public release. This source reads Create's textures from the player's resource stack instead -
 * they are there whenever the track exists at all, because the block only registers with Create -
 * and recolours them in Java. The jar ships code and an atlas entry, not a single Create pixel. Same
 * pattern as the world compass ({@code WorldCompassSpriteSource}); the atlas JSON has to live under
 * {@code assets/minecraft/atlases/blocks.json} for the reason explained there.
 *
 * <p>Side effect worth having: the loader track now follows a resource pack that redraws Create's
 * track, instead of staying frozen in the default look next to restyled ordinary track.
 *
 * <p>The sprite IDs are exactly the ones the old PNGs had ({@code vanillaplusadditions:block/
 * chunk_loader_track} and so on), so not one model or blockstate had to change. Without Create the
 * source textures are absent and the sprites are simply skipped - nothing references them then.
 */
public class ChunkLoaderTrackSpriteSource implements SpriteSource {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set once from {@code RegisterSpriteSourceTypesEvent}; see {@link #type()}. */
    @Nullable
    private static SpriteSourceType registeredType;

    /** Target hue, a clear "signal blue" (about 204 degrees), as a fraction of a full turn. */
    private static final float HUE = 0.567F;
    /** Saturation curve {@code s' = BASE + LINEAR * s + QUADRATIC * s^2}, capped at 1. */
    private static final float SAT_BASE = 0.06F;
    private static final float SAT_LINEAR = 0.6F;
    private static final float SAT_QUADRATIC = 6.0F;

    public static final MapCodec<ChunkLoaderTrackSpriteSource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("source").forGetter(s -> s.source),
                            ResourceLocation.CODEC.fieldOf("sprite").forGetter(s -> s.sprite))
                    .apply(instance, ChunkLoaderTrackSpriteSource::new));

    /** Texture ID of Create's original, e.g. {@code create:block/standard_track}. */
    private final ResourceLocation source;
    /** Sprite ID we produce, e.g. {@code vanillaplusadditions:block/chunk_loader_track}. */
    private final ResourceLocation sprite;

    public ChunkLoaderTrackSpriteSource(ResourceLocation source, ResourceLocation sprite) {
        this.source = source;
        this.sprite = sprite;
    }

    /** Remembers the type NeoForge hands back on registration. */
    public static void setRegisteredType(SpriteSourceType type) {
        registeredType = type;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        ResourceLocation file = TEXTURE_ID_CONVERTER.idToFile(source);
        Optional<Resource> resource = resourceManager.getResource(file);
        if (resource.isEmpty()) {
            // Normal without Create: the track block does not exist then, so nobody asks for it.
            LOGGER.debug("Chunk Loader Track: {} not found, skipping sprite {}", file, sprite);
            return;
        }
        Resource texture = resource.get();
        output.add(sprite, loader -> loader.loadSprite(sprite, texture, ChunkLoaderTrackSpriteSource::recolour));
    }

    @Nullable
    private static SpriteContents recolour(ResourceLocation id, FrameSize frameSize, NativeImage image,
                                           ResourceMetadata metadata) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int packed = image.getPixelRGBA(x, y);
                int alpha = FastColor.ABGR32.alpha(packed);
                if (alpha != 0) {
                    image.setPixelRGBA(x, y, toBlue(packed, alpha));
                }
            }
        }
        return new SpriteContents(id, frameSize, image, metadata);
    }

    /**
     * Moves one pixel into the blue while keeping its brightness.
     *
     * <p>In HSV terms: V (the brightest channel) is kept exactly, so shading survives and the
     * piece still tiles against ordinary track; H is set to {@link #HUE}; S follows a curve that
     * leaves the nearly grey rails grey with a faint blue cast and turns the brown sleepers a
     * saturated blue. The curve was fitted to the hand-made textures shipped before beta.104 (mean
     * deviation below 3 of 255 per channel), so the track looks the same as before.
     */
    private static int toBlue(int packed, int alpha) {
        float r = FastColor.ABGR32.red(packed) / 255F;
        float g = FastColor.ABGR32.green(packed) / 255F;
        float b = FastColor.ABGR32.blue(packed) / 255F;
        float value = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float saturation = value > 0F ? (value - min) / value : 0F;
        float newSaturation = Math.min(1F, SAT_BASE + SAT_LINEAR * saturation
                + SAT_QUADRATIC * saturation * saturation);
        int rgb = hsvToRgb(HUE, newSaturation, value);
        return FastColor.ABGR32.color(alpha, rgb & 0xFF, (rgb >> 8) & 0xFF, (rgb >> 16) & 0xFF);
    }

    /** Plain HSV to 0xRRGGBB, all inputs in [0, 1]. */
    private static int hsvToRgb(float hue, float saturation, float value) {
        return (channel(5F, hue, saturation, value) << 16)
                | (channel(3F, hue, saturation, value) << 8)
                | channel(1F, hue, saturation, value);
    }

    /** One channel of the closed-form HSV conversion: {@code v - v*s*clamp(min(k, 4-k), 0, 1)}. */
    private static int channel(float offset, float hue, float saturation, float value) {
        float k = (offset + hue * 6F) % 6F;
        float weight = Math.max(0F, Math.min(1F, Math.min(k, 4F - k)));
        return Math.round((value - value * saturation * weight) * 255F);
    }

    @Override
    public SpriteSourceType type() {
        if (registeredType == null) {
            throw new IllegalStateException("Chunk Loader Track sprite source type was never registered");
        }
        return registeredType;
    }
}
