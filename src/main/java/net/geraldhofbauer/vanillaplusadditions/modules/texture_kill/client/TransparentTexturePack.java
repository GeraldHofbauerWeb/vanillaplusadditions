package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.client;

import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.config.TextureKillConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

public final class TransparentTexturePack extends AbstractPackResources {
    // MC 1.21.1 client resource pack format
    private static final int PACK_FORMAT = 34;
    private static final byte[] TRANSPARENT_PNG = generateTransparentPng();

    private final TextureKillConfig config;

    private TransparentTexturePack(PackLocationInfo info, TextureKillConfig config) {
        super(info);
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if ("pack".equals(deserializer.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("VPA Texture Kill"), PACK_FORMAT);
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.CLIENT_RESOURCES && config.getKilledTextures().contains(location)) {
            return () -> new ByteArrayInputStream(TRANSPARENT_PNG);
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        for (ResourceLocation loc : config.getKilledTextures()) {
            if (loc.getNamespace().equals(namespace) && loc.getPath().startsWith(path)) {
                output.accept(loc, () -> new ByteArrayInputStream(TRANSPARENT_PNG));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        return config.getKilledTextures().stream()
            .map(ResourceLocation::getNamespace)
            .collect(Collectors.toSet());
    }

    @Override
    public void close() { }

    private static byte[] generateTransparentPng() {
        try {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 0x00000000);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate transparent PNG for TextureKillModule", e);
        }
    }

    public static class Supplier implements Pack.ResourcesSupplier {
        private final TextureKillConfig config;

        public Supplier(TextureKillConfig config) {
            this.config = config;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo info) {
            return new TransparentTexturePack(info, config);
        }

        @Override
        public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
            return openPrimary(info);
        }
    }
}
