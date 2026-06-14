package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.config.TextureKillConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class TextureRegionEraser implements PreparableReloadListener {
    // Populated in apply(), read by TransparentTexturePack.getResource().
    // Cleared at the start of prepare() so the pack returns null during reload,
    // letting ResourceManager serve originals from lower-priority packs.
    static final Map<ResourceLocation, byte[]> CACHE = new ConcurrentHashMap<>();

    private final TextureKillConfig config;

    public TextureRegionEraser(TextureKillConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture
                .supplyAsync(() -> prepare(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(prepared -> apply(prepared, applyProfiler), gameExecutor);
    }

    private Map<ResourceLocation, byte[]> prepare(ResourceManager resourceManager) {
        // Clear cache so TransparentTexturePack falls through to the original packs during
        // this reload, allowing us to read unmodified originals below.
        CACHE.clear();

        Map<ResourceLocation, List<int[]>> regions = config.getErasedRegions();
        Map<ResourceLocation, byte[]> result = new HashMap<>();

        for (Map.Entry<ResourceLocation, List<int[]>> entry : regions.entrySet()) {
            ResourceLocation loc = entry.getKey();
            Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(loc);
            if (resource.isEmpty()) {
                continue;
            }

            try {
                BufferedImage original = ImageIO.read(resource.get().open());
                if (original == null) {
                    VanillaPlusAdditions.LOGGER.warn("[TextureKill] Could not decode image: {}", loc);
                    continue;
                }

                // Work on an ARGB copy so setRGB(x, y, 0) produces transparent pixels.
                BufferedImage img = new BufferedImage(
                        original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.drawImage(original, 0, 0, null);
                g.dispose();

                for (int[] region : entry.getValue()) {
                    int x1 = region[0], y1 = region[1], x2 = region[2], y2 = region[3];
                    for (int y = y1; y < y2 && y < img.getHeight(); y++) {
                        for (int x = x1; x < x2 && x < img.getWidth(); x++) {
                            img.setRGB(x, y, 0x00000000);
                        }
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "PNG", baos);
                result.put(loc, baos.toByteArray());
            } catch (IOException e) {
                VanillaPlusAdditions.LOGGER.warn("[TextureKill] Failed to process region erase for {}: {}", loc, e.getMessage());
            }
        }

        VanillaPlusAdditions.LOGGER.debug("[TextureKill] Prepared {} region-erased textures", result.size());
        return result;
    }

    private void apply(Map<ResourceLocation, byte[]> prepared, ProfilerFiller profiler) {
        // Populate resource-pack cache so TransparentTexturePack.getResource() can serve
        // modified PNGs for any code path that goes through ResourceManager (e.g. ETF).
        CACHE.putAll(prepared);

        // Belt-and-suspenders: also register as DynamicTextures so TextureManager entries
        // that ETF may have already cached get replaced with our modified version.
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (Map.Entry<ResourceLocation, byte[]> entry : prepared.entrySet()) {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(entry.getValue()));
                DynamicTexture dt = new DynamicTexture(img);
                textureManager.register(entry.getKey(), dt);
                dt.upload();
            } catch (IOException e) {
                VanillaPlusAdditions.LOGGER.warn("[TextureKill] DynamicTexture failed for {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }
}
