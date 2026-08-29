package net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 15 biomes Quark's Pathfinder's Quill can target (reverse-engineered from
 * {@code PathfinderMapsModule.register()} in Quark 4.1-482, which has no published sources — the
 * biome keys and color ints below are copied verbatim from that bytecode so crafted quills look
 * identical to traded ones), paired with a block that's thematically tied to each biome — each
 * biome's own leaves where it has a signature tree, a defining terrain/ground block otherwise.
 */
public final class PathfinderQuillRecipes {

    /** One craftable Pathfinder's Quill variant: target biome, its ingredient block, its color. */
    public record BiomeEntry(String biomeId, Item block, int color) { }

    public static final List<BiomeEntry> ENTRIES = List.of(
            new BiomeEntry("minecraft:snowy_plains", Items.SNOW_BLOCK, -8395521),
            new BiomeEntry("minecraft:windswept_hills", Items.GRAVEL, -7697782),
            new BiomeEntry("minecraft:dark_forest", Items.OAK_LEAVES, -16754422),
            new BiomeEntry("minecraft:desert", Items.SAND, -3360434),
            new BiomeEntry("minecraft:savanna", Items.ACACIA_LEAVES, -6576798),
            new BiomeEntry("minecraft:swamp", Items.MUD, -14534897),
            new BiomeEntry("minecraft:mangrove_swamp", Items.MANGROVE_LEAVES, -14534897),
            new BiomeEntry("minecraft:old_growth_pine_taiga", Items.PODZOL, -10796513),
            new BiomeEntry("minecraft:flower_forest", Items.POPPY, -3258654),
            new BiomeEntry("minecraft:jungle", Items.JUNGLE_LEAVES, -14502400),
            new BiomeEntry("minecraft:bamboo_jungle", Items.BAMBOO, -12721641),
            new BiomeEntry("minecraft:badlands", Items.TERRACOTTA, -3768542),
            new BiomeEntry("minecraft:mushroom_fields", Items.BROWN_MUSHROOM, -11713933),
            new BiomeEntry("minecraft:ice_spikes", Items.PACKED_ICE, -14761783),
            new BiomeEntry("minecraft:cherry_grove", Items.CHERRY_LEAVES, -1463832)
    );

    public static final Map<String, BiomeEntry> BY_BIOME;

    static {
        Map<String, BiomeEntry> byBiome = new LinkedHashMap<>();
        for (BiomeEntry entry : ENTRIES) {
            byBiome.put(entry.biomeId(), entry);
        }
        BY_BIOME = Map.copyOf(byBiome);
    }

    private PathfinderQuillRecipes() { }
}
