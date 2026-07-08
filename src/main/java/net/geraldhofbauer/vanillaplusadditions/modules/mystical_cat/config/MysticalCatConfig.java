package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Configuration for the Mystical Cat module.
 *
 * <p>Spawning, spot placement, win rewards/behaviour and every mini-game's weight and time limit
 * are configurable. Per-game weights double as an enable switch: a weight of {@code 0} removes that
 * game from the pool (used both to disable a game and to force-test a single game in isolation).
 */
public class MysticalCatConfig
        extends AbstractModuleConfig<MysticalCatModule, MysticalCatConfig> {

    /** Canonical mini-game ids (also the {@link net.minecraft.network.chat.Component} lang keys). */
    public static final List<String> GAME_IDS = List.of(
            "fetch_riddle", "wisp_chase", "hot_cold", "simon_says", "parkour", "wool_hunt",
            "candle_keeper", "pedestal_offering", "shadow_waves", "target_range",
            "snowball_targets", "ghost_escort");

    private static final List<String> DEFAULT_GAME_WEIGHTS = List.of(
            "fetch_riddle=10", "wisp_chase=10", "hot_cold=10", "simon_says=10", "parkour=8",
            "wool_hunt=10", "candle_keeper=8", "pedestal_offering=8", "shadow_waves=6",
            "target_range=8", "snowball_targets=8", "ghost_escort=6");

    private static final List<String> DEFAULT_GAME_TIME_LIMITS = List.of(
            "fetch_riddle=300", "wisp_chase=60", "hot_cold=120", "simon_says=90", "parkour=90",
            "wool_hunt=180", "candle_keeper=90", "pedestal_offering=120", "shadow_waves=150",
            "target_range=60", "snowball_targets=60", "ghost_escort=90");

    private static final List<String> DEFAULT_ALLOWED_DIMENSIONS = List.of("minecraft:overworld");

    /** Substrings matched against biome ids; a spot is rejected if its biome id contains any. */
    private static final List<String> DEFAULT_DENIED_BIOMES = List.of("ocean", "river", "beach");

    private static final List<String> DEFAULT_FETCH_RIDDLE_ITEMS = List.of(
            "minecraft:torchflower=riddle.torchflower",
            "minecraft:glow_berries=riddle.glow_berries",
            "minecraft:amethyst_shard=riddle.amethyst_shard",
            "minecraft:honeycomb=riddle.honeycomb",
            "minecraft:ink_sac=riddle.ink_sac",
            "minecraft:cobweb=riddle.cobweb",
            "minecraft:pufferfish=riddle.pufferfish",
            "minecraft:nautilus_shell=riddle.nautilus_shell");

    private static final List<String> DEFAULT_TRADE_REWARDS_TIER1 = List.of(
            "minecraft:diamond*2@10", "minecraft:emerald*4@10", "minecraft:experience_bottle*8@8",
            "minecraft:golden_apple*1@6");
    private static final List<String> DEFAULT_TRADE_REWARDS_TIER2 = List.of(
            "minecraft:diamond*6@10", "minecraft:netherite_scrap*1@6",
            "minecraft:enchanted_golden_apple*1@3", "minecraft:experience_bottle*24@8");
    private static final List<String> DEFAULT_TRADE_REWARDS_TIER3 = List.of(
            "minecraft:netherite_ingot*1@10", "minecraft:enchanted_golden_apple*2@6",
            "minecraft:nether_star*1@2", "minecraft:diamond_block*1@8");

    private ModConfigSpec.BooleanValue naturalSpawning;
    private ModConfigSpec.IntValue spawnChancePerChunk;
    private ModConfigSpec.IntValue minSpotDistance;
    private ModConfigSpec.ConfigValue<List<? extends String>> allowedDimensions;
    private ModConfigSpec.ConfigValue<List<? extends String>> deniedBiomes;

    private ModConfigSpec.EnumValue<CatWinBehavior> catWinBehavior;
    private ModConfigSpec.IntValue winCooldownMinutes;
    private ModConfigSpec.IntValue failRetryCooldownSeconds;
    private ModConfigSpec.IntValue pawsPerWin;
    private ModConfigSpec.IntValue maxConcurrentSessions;
    private ModConfigSpec.IntValue abortDistance;

    private ModConfigSpec.ConfigValue<List<? extends String>> gameWeights;
    private ModConfigSpec.ConfigValue<List<? extends String>> gameTimeLimits;
    private ModConfigSpec.ConfigValue<List<? extends String>> fetchRiddleItems;

    private ModConfigSpec.ConfigValue<List<? extends Integer>> tradeTiers;
    private ModConfigSpec.ConfigValue<List<? extends String>> tradeRewardsTier1;
    private ModConfigSpec.ConfigValue<List<? extends String>> tradeRewardsTier2;
    private ModConfigSpec.ConfigValue<List<? extends String>> tradeRewardsTier3;

    public MysticalCatConfig(MysticalCatModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        builder.push("spawning");
        naturalSpawning = builder
                .comment("Whether Mystical Cat spots generate naturally in newly generated chunks.")
                .define("natural_spawning", true);
        spawnChancePerChunk = builder
                .comment("Average: one spot attempt per this many newly generated chunks (higher = rarer).")
                .defineInRange("spawn_chance_per_chunk", 400, 1, 100_000);
        minSpotDistance = builder
                .comment("Minimum distance (blocks) between two naturally generated cat spots.")
                .defineInRange("min_spot_distance", 512, 16, 100_000);
        allowedDimensions = builder
                .comment("Dimension ids in which spots may generate naturally.")
                .defineList("allowed_dimensions", DEFAULT_ALLOWED_DIMENSIONS,
                        () -> "minecraft:overworld", MysticalCatConfig::isNonBlankString);
        deniedBiomes = builder
                .comment("Spot generation is skipped when the biome id contains any of these substrings.")
                .defineList("denied_biome_substrings", DEFAULT_DENIED_BIOMES,
                        () -> "ocean", MysticalCatConfig::isNonBlankString);
        builder.pop();

        builder.push("rewards");
        catWinBehavior = builder
                .comment("What the cat does after a win: COOLDOWN (stays, playable again later) or DESPAWN.")
                .defineEnum("cat_win_behavior", CatWinBehavior.COOLDOWN);
        winCooldownMinutes = builder
                .comment("Minutes before a cat can be played again after a win (COOLDOWN behaviour).")
                .defineInRange("win_cooldown_minutes", 60, 0, 100_000);
        failRetryCooldownSeconds = builder
                .comment("Seconds before a cat offers a new game after a loss.")
                .defineInRange("fail_retry_cooldown_seconds", 30, 0, 100_000);
        pawsPerWin = builder
                .comment("How many Mystical Paws a win awards.")
                .defineInRange("paws_per_win", 1, 1, 64);
        tradeTiers = builder
                .comment("Paw counts unlocking each trade tier (ascending).")
                .defineList("trade_tiers", List.of(5, 15, 30),
                        () -> 5, o -> o instanceof Integer i && i > 0);
        tradeRewardsTier1 = builder
                .comment("Tier 1 rewards, format item*count@weight (e.g. minecraft:diamond*2@10).")
                .defineList("trade_rewards_tier1", DEFAULT_TRADE_REWARDS_TIER1,
                        () -> "minecraft:diamond*2@10", MysticalCatConfig::isRewardEntry);
        tradeRewardsTier2 = builder
                .comment("Tier 2 rewards, format item*count@weight.")
                .defineList("trade_rewards_tier2", DEFAULT_TRADE_REWARDS_TIER2,
                        () -> "minecraft:diamond*6@10", MysticalCatConfig::isRewardEntry);
        tradeRewardsTier3 = builder
                .comment("Tier 3 rewards, format item*count@weight.")
                .defineList("trade_rewards_tier3", DEFAULT_TRADE_REWARDS_TIER3,
                        () -> "minecraft:netherite_ingot*1@10", MysticalCatConfig::isRewardEntry);
        builder.pop();

        builder.push("games");
        maxConcurrentSessions = builder
                .comment("Maximum number of mini-games running server-wide at once.")
                .defineInRange("max_concurrent_sessions", 8, 1, 1000);
        abortDistance = builder
                .comment("A running game is aborted if the player strays more than this many blocks from the cat.")
                .defineInRange("abort_distance", 48, 8, 512);
        gameWeights = builder
                .comment("Random-pick weight per game, format id=weight. Weight 0 disables/removes a game.",
                        "Valid ids: " + String.join(", ", GAME_IDS))
                .defineList("game_weights", DEFAULT_GAME_WEIGHTS,
                        () -> "fetch_riddle=10", MysticalCatConfig::isIdIntEntry);
        gameTimeLimits = builder
                .comment("Time limit (seconds) per game, format id=seconds.")
                .defineList("game_time_limits", DEFAULT_GAME_TIME_LIMITS,
                        () -> "fetch_riddle=300", MysticalCatConfig::isIdIntEntry);
        fetchRiddleItems = builder
                .comment("Fetch Riddle pool, format itemId=riddleLangKey.")
                .defineList("fetch_riddle_items", DEFAULT_FETCH_RIDDLE_ITEMS,
                        () -> "minecraft:torchflower=riddle.torchflower", MysticalCatConfig::isIdEqualsEntry);
        builder.pop();
    }

    // ---- validators ----

    private static boolean isNonBlankString(Object o) {
        return o instanceof String s && !s.isBlank();
    }

    private static boolean isIdEqualsEntry(Object o) {
        if (!(o instanceof String s)) {
            return false;
        }
        int eq = s.indexOf('=');
        return eq > 0 && eq < s.length() - 1;
    }

    private static boolean isIdIntEntry(Object o) {
        if (!(o instanceof String s)) {
            return false;
        }
        int eq = s.indexOf('=');
        if (eq <= 0 || eq >= s.length() - 1) {
            return false;
        }
        try {
            Integer.parseInt(s.substring(eq + 1).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isRewardEntry(Object o) {
        return o instanceof String s && s.indexOf('*') > 0 && s.indexOf('@') > s.indexOf('*');
    }

    // ---- accessors ----

    public boolean isNaturalSpawning() {
        return naturalSpawning == null || naturalSpawning.get();
    }

    public int getSpawnChancePerChunk() {
        return spawnChancePerChunk == null ? 400 : spawnChancePerChunk.get();
    }

    public int getMinSpotDistance() {
        return minSpotDistance == null ? 512 : minSpotDistance.get();
    }

    public List<? extends String> getAllowedDimensions() {
        return allowedDimensions == null ? DEFAULT_ALLOWED_DIMENSIONS : allowedDimensions.get();
    }

    public List<? extends String> getDeniedBiomes() {
        return deniedBiomes == null ? DEFAULT_DENIED_BIOMES : deniedBiomes.get();
    }

    public CatWinBehavior getCatWinBehavior() {
        return catWinBehavior == null ? CatWinBehavior.COOLDOWN : catWinBehavior.get();
    }

    public int getWinCooldownMinutes() {
        return winCooldownMinutes == null ? 60 : winCooldownMinutes.get();
    }

    public int getFailRetryCooldownSeconds() {
        return failRetryCooldownSeconds == null ? 30 : failRetryCooldownSeconds.get();
    }

    public int getPawsPerWin() {
        return pawsPerWin == null ? 1 : pawsPerWin.get();
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions == null ? 8 : maxConcurrentSessions.get();
    }

    public int getAbortDistance() {
        return abortDistance == null ? 48 : abortDistance.get();
    }

    public List<? extends String> getGameWeights() {
        return gameWeights == null ? DEFAULT_GAME_WEIGHTS : gameWeights.get();
    }

    public List<? extends String> getGameTimeLimits() {
        return gameTimeLimits == null ? DEFAULT_GAME_TIME_LIMITS : gameTimeLimits.get();
    }

    public List<? extends String> getFetchRiddleItems() {
        return fetchRiddleItems == null ? DEFAULT_FETCH_RIDDLE_ITEMS : fetchRiddleItems.get();
    }

    @SuppressWarnings("unchecked")
    public List<? extends Integer> getTradeTiers() {
        return tradeTiers == null ? List.of(5, 15, 30) : (List<? extends Integer>) tradeTiers.get();
    }

    public List<? extends String> getTradeRewardsTier1() {
        return tradeRewardsTier1 == null ? DEFAULT_TRADE_REWARDS_TIER1 : tradeRewardsTier1.get();
    }

    public List<? extends String> getTradeRewardsTier2() {
        return tradeRewardsTier2 == null ? DEFAULT_TRADE_REWARDS_TIER2 : tradeRewardsTier2.get();
    }

    public List<? extends String> getTradeRewardsTier3() {
        return tradeRewardsTier3 == null ? DEFAULT_TRADE_REWARDS_TIER3 : tradeRewardsTier3.get();
    }
}
