package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.TextureKillModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TextureKillConfig extends AbstractModuleConfig<TextureKillModule, TextureKillConfig> {
    private ModConfigSpec.ConfigValue<List<? extends String>> killedTextures;

    public TextureKillConfig(TextureKillModule module) {
        super(module);
    }

    private static final List<String> DEFAULT_KILLED_TEXTURES = List.of(
        "create:textures/entity/train_hat.png",
        "create:textures/entity/logistics_hat.png"
    );

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        killedTextures = builder
            .comment(
                "List of texture ResourceLocations to replace with a fully transparent 1x1 PNG.",
                "Format: namespace:textures/category/name.png",
                "Examples:",
                "  create:textures/entity/train_hat.png",
                "  minecraft:textures/block/stone.png"
            )
            .defineList(
                "killed_textures",
                DEFAULT_KILLED_TEXTURES,
                () -> "create:textures/entity/example.png",
                o -> o instanceof String s && s.contains(":")
            );
    }

    public Set<ResourceLocation> getKilledTextures() {
        if (killedTextures == null) {
            return Set.of();
        }
        Set<ResourceLocation> result = new HashSet<>();
        for (String entry : killedTextures.get()) {
            ResourceLocation loc = ResourceLocation.tryParse(entry);
            if (loc != null) {
                result.add(loc);
            }
        }
        return result;
    }
}
