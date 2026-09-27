package net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.config.SpecialGemstonesConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.item.GemstoneItem;
import net.geraldhofbauer.vanillaplusadditions.util.ConfiguredRecipes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Special Gemstones — two emerald-priced gems that change how big a creature is, for good.
 *
 * <p>Right-clicking a living thing with the <strong>Growth Gemstone</strong> multiplies its
 * {@code generic.scale} by the configured factor, the <strong>Shrinking Gemstone</strong> divides by
 * it. Because one is the other's inverse, a shrink undoes exactly one growth at any starting size.
 * The mechanics live in {@link EntityScaling}; this class is registration, the click, and the two
 * ways a gemstone can be obtained.
 *
 * <p>The pairing with {@code wolf_mount} is the point: that module only lets you ride a wolf whose
 * scale is at least {@code 2.0}, and with its defaults no naturally occurring wolf qualifies. One
 * Growth Gemstone on a tamed vanilla wolf is the first way to get there without a command.
 */
public class SpecialGemstonesModule
        extends AbstractModule<SpecialGemstonesModule, SpecialGemstonesConfig> {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    /** Makes the creature you click bigger. */
    public static final DeferredItem<Item> GROWTH_GEMSTONE =
            ITEMS.register("growth_gemstone", () -> new GemstoneItem(new Item.Properties(), true));

    /** Makes the creature you click smaller. */
    public static final DeferredItem<Item> SHRINKING_GEMSTONE =
            ITEMS.register("shrinking_gemstone", () -> new GemstoneItem(new Item.Properties(), false));

    /**
     * Name of the loot pool we add. {@code LootTable.addPool} throws on a duplicate pool name, so the
     * name is also the guard that a second {@code /reload} does not crash the server.
     */
    private static final String LOOT_POOL_NAME = "vanillaplusadditions_special_gemstones";

    /** Entity-type ids the gemstones refuse, parsed once per config load. */
    private final Set<String> deniedEntities = new HashSet<>();

    /** Whether {@link #reloadDeniedEntities()} has run. An empty denylist is a valid setting. */
    private boolean deniedEntitiesLoaded;

    public SpecialGemstonesModule() {
        super("special_gemstones",
                "Special Gemstones",
                "Adds two emerald gemstones that permanently grow or shrink the creature you "
                        + "right-click, via the vanilla generic.scale attribute.",
                SpecialGemstonesConfig::new);
    }

    @Override
    protected void onInitialize() {
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addAllToMainTab(GROWTH_GEMSTONE, SHRINKING_GEMSTONE);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Special Gemstones module initialized - Growth and Shrinking Gemstone registered");
    }

    // ------------------------------------------------------------------------------------------
    // Using a gemstone
    // ------------------------------------------------------------------------------------------

    /**
     * The ordinary right-click on a creature.
     *
     * <p>This has to be {@code EntityInteract} and not {@code Item#interactLivingEntity}:
     * {@code Player.interactOn} asks the entity first and returns as soon as the entity consumed the
     * click. A tamed wolf toggles sit/stand on any unknown item, so an item hook would never fire on
     * exactly the animals you want to resize.
     */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!handlesClick(event.getItemStack(), event.getTarget(), event.getHand())) {
            return;
        }
        // Consume the click on both sides, and cancel with sidedSuccess, never with PASS: a PASS
        // cancellation lets the client fall through to the item-use stage.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        resize(event.getLevel(), event.getEntity(), event.getTarget(), event.getItemStack());
    }

    /** The same click when it lands on a specific part of the hitbox; fires before the generic one. */
    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!handlesClick(event.getItemStack(), event.getTarget(), event.getHand())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        resize(event.getLevel(), event.getEntity(), event.getTarget(), event.getItemStack());
    }

    /** Whether this is a main-hand click with a gemstone on something alive. */
    private boolean handlesClick(ItemStack stack, Entity target, InteractionHand hand) {
        return isModuleEnabled()
                && hand == InteractionHand.MAIN_HAND
                && stack.getItem() instanceof GemstoneItem
                && target instanceof LivingEntity;
    }

    private void resize(Level level, Player player, Entity target, ItemStack stack) {
        if (level.isClientSide()
                || !(stack.getItem() instanceof GemstoneItem gemstone)
                || !(target instanceof LivingEntity living)) {
            return;
        }

        if (!deniedEntitiesLoaded) {
            reloadDeniedEntities();
        }
        EntityScaling.Result result =
                EntityScaling.apply(living, player, gemstone.grows(), getConfig(), deniedEntities);

        if (result.outcome() != EntityScaling.Outcome.CHANGED) {
            player.displayClientMessage(refusalMessage(result.outcome(), living), true);
            return;
        }

        if (getConfig().isItemConsumed() && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        celebrate(level, living, gemstone.grows());
        player.displayClientMessage(Component.translatable(
                "message.vanillaplusadditions.special_gemstones.resized",
                living.getDisplayName(), formatScale(result.relativeScale())), true);

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("{} scaled {} to {}x its natural size", player.getGameProfile().getName(),
                    living.getType(), result.relativeScale());
        }
    }

    private Component refusalMessage(EntityScaling.Outcome outcome, LivingEntity target) {
        String key = switch (outcome) {
            case AT_LIMIT -> "message.vanillaplusadditions.special_gemstones.at_limit";
            case NO_SPACE -> "message.vanillaplusadditions.special_gemstones.no_space";
            case NOT_OWNED -> "message.vanillaplusadditions.special_gemstones.not_owned";
            case OUT_OF_REACH -> "message.vanillaplusadditions.special_gemstones.out_of_reach";
            default -> "message.vanillaplusadditions.special_gemstones.denied";
        };
        return Component.translatable(key, target.getDisplayName());
    }

    /** A chime and a ring of end-rod sparks, so the change reads as deliberate magic. */
    private void celebrate(Level level, LivingEntity target, boolean grows) {
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, grows ? 1.4F : 0.7F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY(0.5D), target.getZ(),
                    24, target.getBbWidth() * 0.6D, target.getBbHeight() * 0.4D,
                    target.getBbWidth() * 0.6D, 0.02D);
        }
    }

    /** "2" instead of "2.0", but "1.5" stays "1.5". */
    private static String formatScale(double scale) {
        if (scale == Math.rint(scale)) {
            return String.valueOf((long) scale);
        }
        return String.format(Locale.ROOT, "%.3f", scale).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * Settles a creature's stats against its size whenever it enters the world.
     *
     * <p>This is what lets a creature that was <em>born</em> big earn the bonus — Sif, a boss from
     * another mod, anything a command made large. The gemstones are only one of the ways a creature
     * ends up a given size, and the stats follow the size, not the gemstone.
     *
     * <p>Cheap enough to run on every entity: the work stops at the first check for anything of
     * ordinary size, and {@code refreshSizeStats} compares against the modifier already present, so a
     * creature that is already settled is not touched at all.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isModuleEnabled() || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            EntityScaling.refreshSizeStats(living, getConfig());
        }
    }

    /**
     * Lets a few creatures be born the wrong size.
     *
     * <p>A small chance, on natural spawns only — never a spawner, an egg, a breeding or anything a
     * command placed, because those are someone deliberately asking for a creature and getting a
     * giant instead would be a nuisance rather than a surprise.
     *
     * <p>The result is an ordinary grown or shrunk creature: it carries the same marker a gemstone
     * would leave, earns the same size-derived stats, and a Shrinking Gemstone can walk it back. That
     * is also what makes an oversized Foxhound worth hunting for rather than just an odd zombie —
     * Quark's Nether wolf spawns down there already and tames with coal, it simply is not a monster
     * by type, so it is named in the config rather than caught by the hostile rule.
     */
    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!isModuleEnabled() || event.getLevel().isClientSide()) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        double chance = getConfig().getNaturalSpawnChance();
        if (chance <= 0.0D) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (!isSpawnEligible(entity)) {
            return;
        }
        RandomSource random = entity.getRandom();
        if (random.nextDouble() >= chance) {
            return;
        }

        if (!deniedEntitiesLoaded) {
            reloadDeniedEntities();
        }
        boolean grow = random.nextDouble() >= getConfig().getNaturalSpawnShrinkShare();
        if (EntityScaling.applyAtSpawn(entity, grow, getConfig(), deniedEntities) && getConfig().shouldDebugLog()) {
            getLogger().debug("{} spawned {}", entity.getType(), grow ? "large" : "small");
        }
    }

    /** Whether this creature is allowed to be born an odd size at all. */
    private boolean isSpawnEligible(LivingEntity entity) {
        if (getConfig().isNaturalSpawnHostile() && entity instanceof Monster) {
            return true;
        }
        String id = EntityType.getKey(entity.getType()).toString();
        for (String extra : getConfig().getNaturalSpawnExtra()) {
            if (id.equals(extra.trim())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Getting a gemstone: crafting
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new GemstoneRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /**
     * Injects the two configured recipes. Own-item recipes belong to their own module by project
     * convention, so they exist exactly as long as this module is enabled — but the shape itself comes
     * from the config, in the one format {@link ConfiguredRecipes} parses, so the pack can re-price
     * the gemstones without a new build.
     */
    private void applyGemstoneRecipes(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }

        int applied = 0;
        applied += addRecipe(merged, getConfig().getGrowthRecipe(), "growth");
        applied += addRecipe(merged, getConfig().getShrinkingRecipe(), "shrinking");
        if (applied == 0) {
            return;
        }

        recipeManager.replaceRecipes(merged.values());
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Applied {} gemstone recipes.", applied);
        }
    }

    private int addRecipe(Map<ResourceLocation, RecipeHolder<?>> merged, String entry, String which) {
        if (entry == null || entry.isBlank()) {
            return 0;
        }
        try {
            RecipeHolder<ShapedRecipe> holder = ConfiguredRecipes.Shaped.parse(entry).toRecipeHolder();
            merged.put(holder.id(), holder);
            return 1;
        } catch (IllegalArgumentException exception) {
            getLogger().error("Invalid {}_recipe for the gemstones: {}", which, entry);
            getLogger().error("Reason: {}", exception.getMessage());
            return 0;
        } catch (Exception exception) {
            getLogger().error("Failed to parse {}_recipe for the gemstones: {}", which, entry, exception);
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Getting a gemstone: chest loot
    // ------------------------------------------------------------------------------------------

    /**
     * Adds one pool to each configured chest table. Foreign loot tables are edited through this event
     * rather than a datapack JSON, the same way {@code enhanced_ai_leader_loot} does it.
     */
    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        String tableName = event.getName().toString();
        for (String entry : getConfig().getLootTables()) {
            String[] parts = entry.split(";");
            if (parts.length != 2 || !parts[0].trim().equals(tableName)) {
                continue;
            }
            try {
                float chance = Float.parseFloat(parts[1].trim());
                if (chance <= 0.0F) {
                    continue;
                }
                // addPool throws on a duplicate name, and this event fires again on every /reload.
                if (event.getTable().getPool(LOOT_POOL_NAME) != null) {
                    return;
                }
                event.getTable().addPool(LootPool.lootPool()
                        .name(LOOT_POOL_NAME)
                        .setRolls(ConstantValue.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(chance))
                        .add(LootItem.lootTableItem(GROWTH_GEMSTONE.get()).setWeight(1))
                        .add(LootItem.lootTableItem(SHRINKING_GEMSTONE.get()).setWeight(1))
                        .build());
            } catch (NumberFormatException exception) {
                getLogger().warn("Gemstone loot: not a chance value: {} (entry ignored)", entry);
            }
            return;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Config caches
    // ------------------------------------------------------------------------------------------

    /**
     * Re-reads the denylist. {@code ENTITY_TYPE} is a {@code DefaultedRegistry} whose {@code get()}
     * never returns null — an unknown id silently comes back as {@code minecraft:pig}, so a typo would
     * protect pigs instead of reporting itself. Hence {@code containsKey} first.
     */
    public void reloadDeniedEntities() {
        deniedEntities.clear();
        deniedEntitiesLoaded = true;
        List<String> configured = getConfig().getDeniedEntities();
        for (String raw : configured) {
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id == null) {
                getLogger().warn("Gemstone denylist: not an entity id: {} (ignored)", raw);
                continue;
            }
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                getLogger().warn("Gemstone denylist: entity type not found: {} (ignored)", id);
                continue;
            }
            deniedEntities.add(id.toString());
        }
    }

    private final class GemstoneRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private GemstoneRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                              ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler,
                                              ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor,
                                              Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyGemstoneRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_special_gemstones_recipes";
        }
    }
}
