package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.TextureKillModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

public class TextureKillConfig extends AbstractModuleConfig<TextureKillModule, TextureKillConfig> {
    private ModConfigSpec.ConfigValue<List<? extends String>> killedTextures;
    private ModConfigSpec.ConfigValue<List<? extends String>> erasedRegions;

    public TextureKillConfig(TextureKillModule module) {
        super(module);
    }

    private static final List<String> DEFAULT_KILLED_TEXTURES = List.of(
        "create:textures/entity/train_hat.png",
        "create:textures/entity/logistics_hat.png"
    );

    private static final List<String> DEFAULT_ERASED_REGIONS = List.of(
            // AL's Zombies Revamped - hat region (outer head layer at textureOffset [32,0] in 128x64 textures)
            "minecraft:textures/entity/zombie/drowned.png@32:0-64:16",
            "minecraft:textures/entity/zombie/drowned_necromancer.png@32:0-64:16",
            "minecraft:textures/entity/zombie/zombie_ollie.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned2.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned3.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned4.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned5.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned6.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned7.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned_outer_layer5.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/drowned_outer_layer6.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/husk2.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/husk5.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/husk7.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/husk8.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/husk9.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome2.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome4.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome5.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome6.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome7.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome8.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome9.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome10.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zom_biome11.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie2.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie3.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie4.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie5.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie6.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie7.png@32:0-64:16",
            "minecraft:optifine/mob/zombie/zombie_ollie8.png@32:0-64:16",
            // Hat brim (Krempe) + side panels — JEM headwear textureOffsets [86,46], [100,30], [112,28], [112,36]
            "minecraft:optifine/mob/zombie/zom_biome2.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zom_biome3.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zom_biome6.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/drowned_outer_layer2.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/drowned_outer_layer3.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/drowned_outer_layer7.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/drowned_outer_layer8.png@86:28-128:60",
            "minecraft:textures/entity/zombie/zombie_ollie.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie2.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie3.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie4.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie5.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie6.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie7.png@86:28-128:60",
            "minecraft:optifine/mob/zombie/zombie_ollie8.png@86:28-128:60",
            "minecraft:textures/entity/zombie/drowned_necromancer.png@86:28-128:60",
            // Sword / object through head — JEM headwear textureOffset [64,0]
            "minecraft:optifine/mob/zombie/drowned7.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/drowned_outer_layer5.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/husk8.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/husk9.png@64:0-82:9",
            "minecraft:textures/entity/zombie/zombie_ollie.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/zombie_ollie2.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/zombie_ollie4.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/zombie_ollie5.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/zombie_ollie6.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/zombie_ollie7.png@64:0-82:9",
            "minecraft:optifine/mob/zombie/zombie_ollie8.png@64:0-82:9",
            "minecraft:textures/entity/zombie/drowned_necromancer.png@64:0-82:9",
            // Above-head accessory — JEM headwear textureOffset [70,20]
            "minecraft:optifine/mob/zombie/zombie_rare2.png@70:20-102:36",
            "minecraft:textures/entity/zombie/drowned_necromancer.png@70:20-102:36"
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

        erasedRegions = builder
                .comment(
                        "List of texture regions to erase (make transparent) within a texture.",
                        "Format: namespace:textures/path.png@x1:y1-x2:y2  (exclusive end coordinates)",
                        "Multiple regions on the same texture are supported by repeating the path with different @... suffixes.",
                        "Examples:",
                        "  minecraft:textures/entity/zombie/drowned.png@32:0-64:16"
                )
                .defineList(
                        "erase_regions",
                        DEFAULT_ERASED_REGIONS,
                        () -> "minecraft:textures/entity/example.png@0:0-8:8",
                        o -> o instanceof String s && s.contains(":") && s.contains("@")
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

    // Returns map of ResourceLocation → list of {x1, y1, x2, y2} regions to erase.
    public Map<ResourceLocation, List<int[]>> getErasedRegions() {
        if (erasedRegions == null) {
            return Map.of();
        }
        Map<ResourceLocation, List<int[]>> result = new HashMap<>();
        for (String entry : erasedRegions.get()) {
            int at = entry.indexOf('@');
            if (at < 0) {
                continue;
            }
            String locPart = entry.substring(0, at);
            String regionPart = entry.substring(at + 1);
            ResourceLocation loc = ResourceLocation.tryParse(locPart);
            if (loc == null) {
                continue;
            }
            int[] coords = parseRegion(regionPart);
            if (coords == null) {
                continue;
            }
            result.computeIfAbsent(loc, k -> new ArrayList<>()).add(coords);
        }
        return result;
    }

    // Parses "x1:y1-x2:y2" into int[]{x1, y1, x2, y2}, or null on failure.
    private static int[] parseRegion(String region) {
        try {
            int dash = region.indexOf('-');
            if (dash < 0) {
                return null;
            }
            String[] start = region.substring(0, dash).split(":");
            String[] end = region.substring(dash + 1).split(":");
            if (start.length != 2 || end.length != 2) {
                return null;
            }
            return new int[]{
                    Integer.parseInt(start[0]), Integer.parseInt(start[1]),
                    Integer.parseInt(end[0]), Integer.parseInt(end[1])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
