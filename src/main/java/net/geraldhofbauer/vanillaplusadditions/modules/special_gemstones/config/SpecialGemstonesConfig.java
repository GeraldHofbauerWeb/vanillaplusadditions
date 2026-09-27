package net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.SpecialGemstonesModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for the two size gemstones.
 *
 * <p>The two recipe keys carry a whole recipe in the format {@code custom_crafting_recipes} uses, so
 * the shape can be changed in the config without a new build — the mod's recipes never come from
 * datapack JSON (they do not load reliably here), and hard-coding a shape would mean a release every
 * time the pack wants a different price.
 */
public class SpecialGemstonesConfig
        extends AbstractModuleConfig<SpecialGemstonesModule, SpecialGemstonesConfig> {

    /**
     * A block of emerald cored with four crimson wart blocks and four amethyst shards — Sebi's
     * design. The nether half is the reason: the Nether is the compact dimension, so growing and
     * shrinking is its kind of magic, and crimson (red) reads as bigger the way warped (blue) reads
     * as smaller. It is also expensive on purpose: nine emeralds, thirty-six nether warts and four
     * shards buy one permanent change to one creature.
     */
    public static final String DEFAULT_GROWTH_RECIPE =
            "vanillaplusadditions:growth_gemstone;vanillaplusadditions:growth_gemstone;1;"
                    + "\"AWA\" \"WEW\" \"AWA\";"
                    + "A=minecraft:amethyst_shard,W=minecraft:nether_wart_block,E=minecraft:emerald_block";

    /** The same price, with warped wart blocks instead of crimson: blue for smaller. */
    public static final String DEFAULT_SHRINKING_RECIPE =
            "vanillaplusadditions:shrinking_gemstone;vanillaplusadditions:shrinking_gemstone;1;"
                    + "\"AWA\" \"WEW\" \"AWA\";"
                    + "A=minecraft:amethyst_shard,W=minecraft:warped_wart_block,E=minecraft:emerald_block";

    /**
     * Bosses. The Ender Dragon ignores the attribute anyway ({@code EnderDragon.sanitizeScale}
     * returns a hard {@code 1.0F}), but it belongs here so the refusal is a readable message instead
     * of a gemstone that silently does nothing.
     */
    private static final List<String> DEFAULT_DENIED_ENTITIES = List.of(
            "minecraft:ender_dragon",
            "minecraft:wither");

    /**
     * The stats that follow the size. The three a horse rolls per animal — health, speed and jump
     * strength — are all in here, because those are exactly the hidden numbers that make one horse
     * better than another and they have to move with the size. Step height is in for the same
     * reason: a creature twice the size that still trips over the same kerb looks wrong.
     */
    private static final List<String> DEFAULT_SCALED_ATTRIBUTES = List.of(
            "minecraft:generic.max_health",
            "minecraft:generic.attack_damage",
            "minecraft:generic.movement_speed",
            "minecraft:generic.jump_strength",
            "minecraft:generic.step_height",
            "minecraft:generic.armor");

    /** Structure chests that may hold a gemstone, with the chance per chest. */
    private static final List<String> DEFAULT_LOOT_TABLES = List.of(
            "minecraft:chests/simple_dungeon;0.10",
            "minecraft:chests/abandoned_mineshaft;0.08",
            "minecraft:chests/desert_pyramid;0.15",
            "minecraft:chests/jungle_temple;0.20",
            "minecraft:chests/stronghold_corridor;0.15",
            "minecraft:chests/ancient_city;0.25",
            "minecraft:chests/woodland_mansion;0.30");

    private ModConfigSpec.DoubleValue scaleFactor;
    private ModConfigSpec.DoubleValue statFactor;
    private ModConfigSpec.DoubleValue minNaturalScale;
    private ModConfigSpec.DoubleValue maxNaturalScale;
    private ModConfigSpec.BooleanValue allowPlayers;
    private ModConfigSpec.BooleanValue requireTamed;
    private ModConfigSpec.BooleanValue checkSpace;
    private ModConfigSpec.BooleanValue consumeItem;
    private ModConfigSpec.BooleanValue scaleCreeperBlast;
    private ModConfigSpec.ConfigValue<List<? extends String>> deniedEntities;
    private ModConfigSpec.ConfigValue<List<? extends String>> scaledAttributes;
    private ModConfigSpec.ConfigValue<String> growthRecipe;
    private ModConfigSpec.ConfigValue<String> shrinkingRecipe;
    private ModConfigSpec.ConfigValue<List<? extends String>> lootTables;

    public SpecialGemstonesConfig(SpecialGemstonesModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        scaleFactor = builder
                .comment("How much one gemstone changes the SIZE. The Growth Gemstone MULTIPLIES",
                        "generic.scale by this, the Shrinking Gemstone DIVIDES by it.",
                        "A creature can only ever be one step from its natural size — shrunk, natural",
                        "or grown — so the sizes reachable with the default are x0.5, x1 and x2.",
                        "The cap is deliberate: without it the gemstones would stack into arbitrarily",
                        "huge mobs.")
                .defineInRange("scale_factor", 2.0D, 1.05D, 8.0D);

        statFactor = builder
                .comment("How much one gemstone changes the STATS — health, attack, speed, jump and",
                        "armour, whichever of them the creature has (see scaled_attributes).",
                        "A grown creature gets x this, a shrunk one gets divided by it. Deliberately",
                        "smaller than scale_factor: twice the size should be noticeably stronger, not",
                        "twice as strong.")
                .defineInRange("stat_factor", 1.5D, 1.0D, 8.0D);

        minNaturalScale = builder
                .comment("Smallest NATURAL size a creature may have for the gemstones to work on it.",
                        "This is about the size it was born with, not the size it is now.")
                .defineInRange("min_natural_scale", 0.5D, 0.0625D, 16.0D);

        maxNaturalScale = builder
                .comment("Largest NATURAL size a creature may have for the gemstones to work on it.",
                        "Anything already bigger than this is out of their reach on purpose: an",
                        "oversized wolf from a dungeon, or Sif from Grim Kingdoms, stays exactly as",
                        "rare as it was found and cannot be grown further or cut down to size.")
                .defineInRange("max_natural_scale", 2.0D, 0.0625D, 16.0D);

        allowPlayers = builder
                .comment("Let the gemstones resize players too. Off by default: a shrunk player sees",
                        "over no stair and a grown one suffocates in its own corridors.")
                .define("allow_players", false);

        requireTamed = builder
                .comment("Only allow resizing animals that belong to the player holding the gemstone.",
                        "A safety switch for shared servers — off by default so a giant creeper stays",
                        "possible.")
                .define("require_tamed", false);

        checkSpace = builder
                .comment("Refuse to grow a mob when its new hitbox would not fit where it stands.",
                        "Without this the mob ends up inside the ceiling and suffocates.")
                .define("check_space", true);

        consumeItem = builder
                .comment("Use up the gemstone on success. Creative mode never consumes it.")
                .define("consume_item", true);

        scaleCreeperBlast = builder
                .comment("Let a creeper's blast follow its size, scaled by stat_factor.",
                        "A creeper's explosion is the one stat that is not an attribute — it is a plain",
                        "number on the mob — so it is handled separately. At the defaults a grown creeper",
                        "goes from 3 to 4 and a shrunk one down to 2; a charged creeper still doubles on",
                        "top of that. Turn off if you would rather not hand anyone a bigger hole.")
                .define("scale_creeper_blast", true);

        deniedEntities = builder
                .comment("Entity types the gemstones refuse to touch, e.g. minecraft:wither.")
                .defineList("denied_entities", DEFAULT_DENIED_ENTITIES,
                        () -> "minecraft:wither",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);

        scaledAttributes = builder
                .comment("The attributes that follow the size, scaled by stat_factor.",
                        "A creature that does not have one of them simply skips it — a cow has no",
                        "attack damage, and only horses and their relatives roll a jump strength.")
                .defineList("scaled_attributes", DEFAULT_SCALED_ATTRIBUTES,
                        () -> "minecraft:generic.max_health",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);

        growthRecipe = builder
                .comment("Crafting recipe for the Growth Gemstone.",
                        "Format: recipe_id;result_item;result_count;pattern;keys",
                        "Pattern: \"AAA\" \"BBB\" \"AAA\" (quoted, keeps spaces) or AAA|BBB|AAA",
                        "Keys: A=minecraft:amethyst_shard,B=#minecraft:planks (a # means item tag)",
                        "Leave empty to make the gemstone uncraftable.")
                .define("growth_recipe", DEFAULT_GROWTH_RECIPE);

        shrinkingRecipe = builder
                .comment("Crafting recipe for the Shrinking Gemstone. Same format as growth_recipe.")
                .define("shrinking_recipe", DEFAULT_SHRINKING_RECIPE);

        lootTables = builder
                .comment("Chests that may contain a gemstone. Format: loot_table;chance",
                        "The chance is rolled once per chest; which of the two gemstones drops is 50/50.")
                .defineList("loot_tables", DEFAULT_LOOT_TABLES,
                        () -> "minecraft:chests/simple_dungeon;0.10",
                        o -> o instanceof String s && s.split(";").length == 2);
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        getModule().reloadDeniedEntities();
    }

    public double getScaleFactor() {
        return scaleFactor != null ? scaleFactor.get() : 2.0D;
    }

    public double getStatFactor() {
        return statFactor != null ? statFactor.get() : 1.5D;
    }

    public double getMinNaturalScale() {
        return minNaturalScale != null ? minNaturalScale.get() : 0.5D;
    }

    public double getMaxNaturalScale() {
        return maxNaturalScale != null ? maxNaturalScale.get() : 2.0D;
    }

    public List<String> getScaledAttributes() {
        return scaledAttributes != null
                ? new ArrayList<>(scaledAttributes.get())
                : new ArrayList<>(DEFAULT_SCALED_ATTRIBUTES);
    }

    public boolean arePlayersAllowed() {
        return allowPlayers != null && allowPlayers.get();
    }

    public boolean isTamedRequired() {
        return requireTamed != null && requireTamed.get();
    }

    public boolean isSpaceChecked() {
        return checkSpace == null || checkSpace.get();
    }

    public boolean isCreeperBlastScaled() {
        return scaleCreeperBlast == null || scaleCreeperBlast.get();
    }

    public boolean isItemConsumed() {
        return consumeItem == null || consumeItem.get();
    }

    public List<String> getDeniedEntities() {
        return deniedEntities != null ? new ArrayList<>(deniedEntities.get()) : new ArrayList<>(DEFAULT_DENIED_ENTITIES);
    }

    public String getGrowthRecipe() {
        return growthRecipe != null ? growthRecipe.get() : DEFAULT_GROWTH_RECIPE;
    }

    public String getShrinkingRecipe() {
        return shrinkingRecipe != null ? shrinkingRecipe.get() : DEFAULT_SHRINKING_RECIPE;
    }

    public List<String> getLootTables() {
        return lootTables != null ? new ArrayList<>(lootTables.get()) : new ArrayList<>(DEFAULT_LOOT_TABLES);
    }
}
