package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian;

import com.mojang.serialization.Codec;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlBowlBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlFeedingStationBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AbstractAxolotlBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlFeedingStationBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlInventoryData;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client.AxolotlGuardianClientEvents;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.config.AxolotlGuardianConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.item.AxolotlArmorItem;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.menu.AxolotlFeedingStationMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.menu.AxolotlInventoryMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.OpenAxolotlInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.RequestAxolotlStatsPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlOwnerPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlPathPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlStatsPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlTargetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Aquatic counterpart of the cat_guardian module: fed axolotls guard an underwater base around
 * their bowl/feeding station, hunting hostile mobs that are in water, collecting their loot + XP.
 *
 * <p>Key architectural differences from cats (both are deliberate):
 * <ul>
 *   <li>Axolotls are <b>brain mobs</b> (empty goal selectors) — the guard AI drives the vanilla
 *       brain via {@code ATTACK_TARGET}/{@code WALK_TARGET} memories instead of injected goals.
 *       The vanilla FIGHT activity handles pursuit and melee natively; no mixins are needed
 *       (axolotls swim natively — the cat module's whole amphibious layer has no counterpart).</li>
 *   <li>Axolotls are not {@code TamableAnimal}s — ownership lives in the {@code AXOLOTL_OWNER}
 *       attachment (set by taming with a tropical fish) and is mirrored to clients via
 *       {@link SyncAxolotlOwnerPacket}.</li>
 *   <li>Targets must be in water ({@code isInWaterOrBubble}) — axolotls are pure water
 *       specialists and never fight on land.</li>
 *   <li>No creeper one-shot: that was cat lore ("creepers fear cats"), not ported on purpose.</li>
 * </ul>
 */
public class AxolotlGuardianModule extends AbstractModule<AxolotlGuardianModule, AxolotlGuardianConfig> {

    // ---- Deferred registers ----

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VanillaPlusAdditions.MODID);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VanillaPlusAdditions.MODID);

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, VanillaPlusAdditions.MODID);

    // ---- Blocks ----

    public static final DeferredBlock<AxolotlBowlBlock> AXOLOTL_BOWL =
            BLOCKS.register("axolotl_bowl", () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_CYAN)
                        .strength(1.5F, 6.0F)
                        .sound(SoundType.STONE)
                        .noOcclusion();
                if (ModList.get().isLoaded("sable")) {
                    // Indirection via SableAxolotlBlocks keeps Sable types out of this class's
                    // bytecode — a direct "new SableAxolotlBowlBlock" here makes the verifier load
                    // Sable classes while LINKING this module class, crashing without Sable.
                    return net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.sable
                            .SableAxolotlBlocks.createBowl(props);
                }
                return new AxolotlBowlBlock(props);
            });

    public static final DeferredBlock<AxolotlFeedingStationBlock> AXOLOTL_FEEDING_STATION =
            BLOCKS.register("axolotl_feeding_station", () -> {
                // No requiresCorrectToolForDrops(): the block sits in no mineable/pickaxe tag
                // (datapack tags are unreliable in this mod), so a "correct tool" could never
                // match — it would break yielding nothing and mine at hand speed forever.
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_CYAN)
                        .strength(3.5F, 12.0F)
                        .sound(SoundType.STONE);
                if (ModList.get().isLoaded("sable")) {
                    return net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.sable
                            .SableAxolotlBlocks.createFeedingStation(props);
                }
                return new AxolotlFeedingStationBlock(props);
            });

    // ---- Block items ----

    public static final DeferredItem<BlockItem> AXOLOTL_BOWL_ITEM =
            ITEMS.register("axolotl_bowl", () -> new BlockItem(AXOLOTL_BOWL.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> AXOLOTL_FEEDING_STATION_ITEM =
            ITEMS.register("axolotl_feeding_station",
                    () -> new BlockItem(AXOLOTL_FEEDING_STATION.get(), new Item.Properties()));

    // ---- Axolotl armor items ----

    public static final DeferredItem<AxolotlArmorItem> AXOLOTL_ARMOR_IRON =
            ITEMS.register("axolotl_armor_iron",
                    () -> new AxolotlArmorItem(AxolotlArmorItem.Tier.IRON, new Item.Properties()));

    public static final DeferredItem<AxolotlArmorItem> AXOLOTL_ARMOR_GOLD =
            ITEMS.register("axolotl_armor_gold",
                    () -> new AxolotlArmorItem(AxolotlArmorItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<AxolotlArmorItem> AXOLOTL_ARMOR_DIAMOND =
            ITEMS.register("axolotl_armor_diamond",
                    () -> new AxolotlArmorItem(AxolotlArmorItem.Tier.DIAMOND, new Item.Properties()));

    public static final DeferredItem<AxolotlArmorItem> AXOLOTL_ARMOR_NETHERITE =
            ITEMS.register("axolotl_armor_netherite",
                    () -> new AxolotlArmorItem(AxolotlArmorItem.Tier.NETHERITE, new Item.Properties().fireResistant()));

    // ---- Block entity types ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AxolotlBowlBlockEntity>> AXOLOTL_BOWL_BE =
            BLOCK_ENTITY_TYPES.register("axolotl_bowl",
                    () -> BlockEntityType.Builder.of(AxolotlBowlBlockEntity::new, AXOLOTL_BOWL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AxolotlFeedingStationBlockEntity>> AXOLOTL_FEEDING_STATION_BE =
            BLOCK_ENTITY_TYPES.register("axolotl_feeding_station",
                    () -> BlockEntityType.Builder.of(AxolotlFeedingStationBlockEntity::new,
                            AXOLOTL_FEEDING_STATION.get()).build(null));

    // ---- Menu types ----

    public static final DeferredHolder<MenuType<?>, MenuType<AxolotlFeedingStationMenu>> AXOLOTL_FEEDING_STATION_MENU =
            MENUS.register("axolotl_feeding_station",
                    () -> IMenuTypeExtension.create(AxolotlFeedingStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AxolotlInventoryMenu>> AXOLOTL_INVENTORY_MENU =
            MENUS.register("axolotl_inventory",
                    () -> IMenuTypeExtension.create(AxolotlInventoryMenu::new));

    // ---- Entity attachment types (persisted in entity NBT) ----

    /**
     * Owner UUID as string; {@code ""} = unowned. Axolotls are not TamableAnimals, so there is no
     * vanilla owner — and attachments never sync to clients on their own, hence
     * {@link SyncAxolotlOwnerPacket} mirrors this into the client-side attachment.
     */
    public static final Supplier<AttachmentType<String>> AXOLOTL_OWNER =
            ATTACHMENT_TYPES.register("axolotl_owner", () ->
                    AttachmentType.<String>builder(() -> "")
                            .serialize(Codec.STRING)
                            .build());

    public static final Supplier<AttachmentType<Long>> AXOLOTL_BOWL_POS =
            ATTACHMENT_TYPES.register("axolotl_bowl_pos", () ->
                    AttachmentType.<Long>builder(() -> Long.MIN_VALUE)
                            .serialize(Codec.LONG)
                            .build());

    public static final Supplier<AttachmentType<Integer>> AXOLOTL_FED_TICKS =
            ATTACHMENT_TYPES.register("axolotl_fed_ticks", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    public static final Supplier<AttachmentType<AxolotlInventoryData>> AXOLOTL_INVENTORY =
            ATTACHMENT_TYPES.register("axolotl_inventory", () ->
                    AttachmentType.<AxolotlInventoryData>builder(AxolotlInventoryData::new)
                            .serialize(AxolotlInventoryData.CODEC)
                            .build());

    public static final Supplier<AttachmentType<Boolean>> AXOLOTL_RETURNING =
            ATTACHMENT_TYPES.register("axolotl_returning", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    /**
     * Low-health retreat flag: absolute, ignores all mobs and flees to base until healed.
     */
    public static final Supplier<AttachmentType<Boolean>> AXOLOTL_FLEEING =
            ATTACHMENT_TYPES.register("axolotl_fleeing", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    public static final Supplier<AttachmentType<Integer>> AXOLOTL_XP =
            ATTACHMENT_TYPES.register("axolotl_xp", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    // ---- Attribute modifier IDs ----

    private static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "axolotl_armor_bonus");

    // Guard follow-range boost: doubles the A* node budget (16→32 blocks) for guard-radius pathing
    private static final ResourceLocation GUARDIAN_FOLLOW_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "axolotl_guardian_follow_range");

    // ---- Bucket payload ----

    /** Sub-tag inside BUCKET_ENTITY_DATA carrying the guardian payload through a bucket round-trip. */
    private static final String GUARDIAN_BUCKET_TAG = "vpa_guardian";

    /**
     * Buffer (blocks) for target eligibility — avoids flicker at the boundary.
     */
    private static final double TARGET_ZONE_BUFFER = 4.0;
    /**
     * Larger buffer for the axolotl's own travel — allows edge melee + brief boundary stepping.
     */
    private static final double AXOLOTL_ZONE_BUFFER = 10.0;

    private static final long BLACKLIST_TICKS = 1200L; // 60 seconds

    // ---- Singleton reference for config access from static context ----

    private static AxolotlGuardianModule instance;

    // Accumulated ticks an axolotl has spent in the ordinary (interruptible) returning state.
    private final Map<UUID, Integer> returningAge = new HashMap<>();
    // Per-axolotl target-search cooldown, in tick-cycles (tickAxolotl runs every 10 ticks).
    private final Map<UUID, Integer> searchCooldown = new HashMap<>();
    // Per-axolotl temporarily blacklisted mob entity IDs (value = game time when entry expires).
    private final Map<UUID, Map<Integer, Long>> blockedTargets = new HashMap<>();
    // Last combat-target entity ID synced to clients (NO_TARGET when none) — brain mobs have no
    // goal start()/stop() hooks, so target sync is edge-detected here.
    private final Map<UUID, Integer> lastSyncedTarget = new HashMap<>();
    // Stuck detection: last sampled position + consecutive no-progress strikes.
    private final Map<UUID, net.minecraft.world.phys.Vec3> stuckSample = new HashMap<>();
    private final Map<UUID, Integer> stuckStrikes = new HashMap<>();
    // Homebound progress sample: last sampled distance to the bowl while returning/fleeing.
    // Catches "moving but circling" — position deltas alone never call that stuck.
    private final Map<UUID, Double> homeDistSample = new HashMap<>();

    // Maps dead entity ID → guardian axolotl entity ID; used to redirect XP to the axolotl.
    private final Map<Integer, Integer> pendingXpCapture = new HashMap<>();

    // Guardian payloads captured from a used axolotl bucket, waiting for the spawned entity.
    private record PendingBucketRestore(CompoundTag payload, ResourceKey<Level> dimension,
                                        BlockPos pos, long gameTime) {
    }

    private final List<PendingBucketRestore> pendingBucketRestores = new ArrayList<>();

    // ---- Static helpers (config access with fallbacks, ownership) ----

    public static double getAssociationRadius() {
        return instance != null ? instance.getConfig().getAssociationRadius() : 64.0D;
    }

    public static int getFedDurationTicks() {
        return instance != null ? instance.getConfig().getFedDurationTicks() : 6000;
    }

    public static boolean isModuleActive() {
        return instance != null && instance.isModuleEnabled();
    }

    public static int getMaxAxolotlsPerStation() {
        return instance != null ? instance.getConfig().getMaxAxolotlsPerStation() : 8;
    }

    public static double getGuardRadius() {
        return instance != null ? instance.getConfig().getGuardRadius() : 32.0;
    }

    public static double getGuardRadiusY() {
        return instance != null ? instance.getConfig().getGuardRadiusY() : 16.0;
    }

    public static int getStationXpCapacity() {
        return instance != null ? instance.getConfig().getStationXpCapacity() : 5000;
    }

    public static int getAxolotlXpCapacity() {
        return instance != null ? instance.getConfig().getAxolotlXpCapacity() : 500;
    }

    public static int getGlowDurationTicks() {
        return instance != null ? instance.getConfig().getGlowDurationTicks() : 600;
    }

    /** The single food predicate: bowls, station and hand-feeding all accept exactly this. */
    public static boolean isAxolotlFood(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TROPICAL_FISH);
    }

    /**
     * Returns true if this axolotl is registered as a guardian (has a bowl assignment).
     */
    public static boolean isGuardianAxolotl(Axolotl axolotl) {
        if (instance == null || !instance.isModuleEnabled()) {
            return false;
        }
        try {
            return axolotl.getData(AXOLOTL_BOWL_POS.get()) != Long.MIN_VALUE;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isOwned(Axolotl axolotl) {
        return getOwnerUUID(axolotl) != null;
    }

    public static boolean isOwnedBy(Axolotl axolotl, UUID playerUUID) {
        return Objects.equals(getOwnerUUID(axolotl), playerUUID);
    }

    /** Owner UUID, or null if unowned. Works on both sides (client mirror via owner sync packet). */
    public static UUID getOwnerUUID(Axolotl axolotl) {
        String owner = axolotl.getData(AXOLOTL_OWNER.get());
        if (owner.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- Constructor ----

    public AxolotlGuardianModule() {
        super(
                "axolotl_guardian",
                "Axolotl Guardian",
                "Adds axolotl food bowls and feeding stations. Tamed, fed axolotls actively guard "
                        + "your underwater base against hostile mobs.",
                AxolotlGuardianConfig::new
        );
        instance = this;
    }

    // ---- Module lifecycle ----

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        BLOCK_ENTITY_TYPES.register(getModEventBus());
        ATTACHMENT_TYPES.register(getModEventBus());
        MENUS.register(getModEventBus());

        getModEventBus().addListener(this::onRegisterCapabilities);
        getModEventBus().addListener(this::onRegisterPayloadHandlers);

        VanillaPlusCreativeTabs.addAllToMainTab(
                AXOLOTL_BOWL_ITEM, AXOLOTL_FEEDING_STATION_ITEM,
                AXOLOTL_ARMOR_IRON, AXOLOTL_ARMOR_GOLD, AXOLOTL_ARMOR_DIAMOND, AXOLOTL_ARMOR_NETHERITE);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Axolotl Guardian module initialized");
    }

    // ---- Recipes (in code — datapack recipe JSONs don't load reliably in this mod) ----

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new AxolotlGuardianRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyAxolotlGuardianRecipes(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> mergedRecipes = new LinkedHashMap<>();
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            mergedRecipes.put(recipeHolder.id(), recipeHolder);
        }

        addArmorShapedRecipe(mergedRecipes, "axolotl_armor_iron", AXOLOTL_ARMOR_IRON.get(), Items.IRON_INGOT);
        addArmorShapedRecipe(mergedRecipes, "axolotl_armor_gold", AXOLOTL_ARMOR_GOLD.get(), Items.GOLD_INGOT);
        addArmorShapedRecipe(mergedRecipes, "axolotl_armor_diamond", AXOLOTL_ARMOR_DIAMOND.get(), Items.DIAMOND);
        addArmorShapedRecipe(mergedRecipes, "axolotl_armor_netherite", AXOLOTL_ARMOR_NETHERITE.get(), Items.NETHERITE_INGOT);

        // Axolotl bowl: prismarine U-shape (ocean counterpart of the smooth-stone cat bowl)
        addShapedRecipe(mergedRecipes, "axolotl_bowl",
                Map.of('P', Ingredient.of(Items.PRISMARINE)),
                CraftingBookCategory.MISC, new ItemStack(AXOLOTL_BOWL_ITEM.get()),
                "P P", "PPP");

        // Axolotl feeding station: glass_pane top/sides, cauldron center, prismarine bottom
        addShapedRecipe(mergedRecipes, "axolotl_feeding_station",
                Map.of('G', Ingredient.of(Items.GLASS_PANE),
                        'C', Ingredient.of(Items.CAULDRON),
                        'P', Ingredient.of(Items.PRISMARINE)),
                CraftingBookCategory.MISC, new ItemStack(AXOLOTL_FEEDING_STATION_ITEM.get()),
                "GGG", "GCG", "PPP");

        recipeManager.replaceRecipes(mergedRecipes.values());
    }

    private void addArmorShapedRecipe(Map<ResourceLocation, RecipeHolder<?>> recipes, String name,
                                      Item resultItem, Item material) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, name);
        Map<Character, Ingredient> key = Map.of(
                'S', Ingredient.of(Items.TURTLE_SCUTE),
                'X', Ingredient.of(material)
        );
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, "X  ", "XXX", "S S");
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.EQUIPMENT, pattern, new ItemStack(resultItem));
        recipes.put(id, new RecipeHolder<>(id, recipe));
    }

    private void addShapedRecipe(Map<ResourceLocation, RecipeHolder<?>> recipes, String name,
                                 Map<Character, Ingredient> key, CraftingBookCategory category,
                                 ItemStack result, String... rows) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, name);
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, rows);
        ShapedRecipe recipe = new ShapedRecipe("", category, pattern, result);
        recipes.put(id, new RecipeHolder<>(id, recipe));
    }

    private final class AxolotlGuardianRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private AxolotlGuardianRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyAxolotlGuardianRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_axolotl_guardian_recipes";
        }
    }

    // ---- Capabilities + network ----

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AXOLOTL_FEEDING_STATION_BE.get(),
                (be, side) -> side == Direction.DOWN ? be.getLootInventory() : be.getInventory()
        );
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenAxolotlInventoryPacket.TYPE, OpenAxolotlInventoryPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled()) {
                        return;
                    }
                    ServerPlayer player = (ServerPlayer) ctx.player();
                    Entity entity = player.level().getEntity(packet.entityId());
                    if (!(entity instanceof Axolotl axolotl)) {
                        return;
                    }
                    if (!isOwnedBy(axolotl, player.getUUID())) {
                        return;
                    }
                    Component title = axolotl.getName().copy()
                            .append(Component.literal(" (" + player.getGameProfile().getName() + ")"));
                    player.openMenu(
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new AxolotlInventoryMenu(id, inv, axolotl),
                                    title
                            ),
                            buf -> buf.writeInt(axolotl.getId())
                    );
                })
        );

        event.registrar("1").playToClient(SyncAxolotlInventoryPacket.TYPE, SyncAxolotlInventoryPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> AxolotlGuardianClientEvents.handleSyncAxolotlInventory(packet))
        );

        event.registrar("1").playToClient(SyncAxolotlStatsPacket.TYPE, SyncAxolotlStatsPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> AxolotlGuardianClientEvents.handleSyncAxolotlStats(packet))
        );

        event.registrar("1").playToClient(SyncAxolotlTargetPacket.TYPE, SyncAxolotlTargetPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> AxolotlGuardianClientEvents.handleSyncAxolotlTarget(packet))
        );

        event.registrar("1").playToClient(SyncAxolotlPathPacket.TYPE, SyncAxolotlPathPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> AxolotlGuardianClientEvents.handleSyncAxolotlPath(packet))
        );

        event.registrar("1").playToClient(SyncAxolotlOwnerPacket.TYPE, SyncAxolotlOwnerPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> AxolotlGuardianClientEvents.handleSyncAxolotlOwner(packet))
        );

        event.registrar("1").playToServer(RequestAxolotlStatsPacket.TYPE, RequestAxolotlStatsPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled()) {
                        return;
                    }
                    ServerPlayer player = (ServerPlayer) ctx.player();
                    if (!(player.level().getEntity(packet.axolotlId()) instanceof Axolotl axolotl)) {
                        return;
                    }
                    if (player.distanceToSqr(axolotl) > 64.0 * 64.0) {
                        return;
                    }
                    PacketDistributor.sendToPlayer(player, new SyncAxolotlInventoryPacket(axolotl.getId(),
                            axolotl.getData(AXOLOTL_INVENTORY.get()).getArmor()));
                    PacketDistributor.sendToPlayer(player, new SyncAxolotlStatsPacket(axolotl.getId(),
                            axolotl.getData(AXOLOTL_XP.get()), getAxolotlXpCapacity()));
                })
        );
    }

    // ---- Sync broadcasts ----

    public static void broadcastOwnerSync(Axolotl axolotl) {
        if (axolotl.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(axolotl, new SyncAxolotlOwnerPacket(
                axolotl.getId(),
                axolotl.getData(AXOLOTL_OWNER.get()),
                axolotl.getData(AXOLOTL_BOWL_POS.get())));
    }

    private static void broadcastArmorSync(Axolotl axolotl) {
        ItemStack armor = axolotl.getData(AXOLOTL_INVENTORY.get()).getArmor();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(axolotl,
                new SyncAxolotlInventoryPacket(axolotl.getId(), armor));
    }

    static void broadcastStatsSync(Axolotl axolotl) {
        int xp = axolotl.getData(AXOLOTL_XP.get());
        int cap = getAxolotlXpCapacity();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(axolotl,
                new SyncAxolotlStatsPacket(axolotl.getId(), xp, cap));
    }

    // ---- Join level — attribute boost, armor restore, bucket-payload restore ----

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Axolotl axolotl)) {
            return;
        }

        // Guardian payload restore from a just-emptied axolotl bucket. Runs before vanilla's
        // loadFromBucketTag (MobBucketItem applies that after type.spawn returns), which only
        // touches Variant/Age/HuntingCooldown — our attachments are untouched by it.
        applyPendingBucketRestore(axolotl);

        // Boost follow-range so the A* node budget covers the guard radius (base axolotl: 16)
        var followRangeAttr = axolotl.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRangeAttr != null && !followRangeAttr.hasModifier(GUARDIAN_FOLLOW_RANGE_ID)) {
            followRangeAttr.addPermanentModifier(new AttributeModifier(
                    GUARDIAN_FOLLOW_RANGE_ID, 16.0, AttributeModifier.Operation.ADD_VALUE));
        }

        // Restore armor attack bonus after chunk load / dimension change
        AxolotlInventoryData invData = axolotl.getData(AXOLOTL_INVENTORY.get());
        ItemStack armor = invData.getArmor();
        if (!armor.isEmpty()) {
            applyArmorAttribute(axolotl, armor);
            broadcastArmorSync(axolotl);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Axolotl axolotl)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        // Owner + bowl assignment (attachments never sync on their own)
        PacketDistributor.sendToPlayer(player, new SyncAxolotlOwnerPacket(
                axolotl.getId(),
                axolotl.getData(AXOLOTL_OWNER.get()),
                axolotl.getData(AXOLOTL_BOWL_POS.get())));
        ItemStack armor = axolotl.getData(AXOLOTL_INVENTORY.get()).getArmor();
        if (!armor.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new SyncAxolotlInventoryPacket(axolotl.getId(), armor));
        }
        PacketDistributor.sendToPlayer(player,
                new SyncAxolotlStatsPacket(axolotl.getId(), axolotl.getData(AXOLOTL_XP.get()), getAxolotlXpCapacity()));
        // Resync current combat target so the goggles overlay shows immediately when a player
        // approaches an axolotl that is already fighting.
        LivingEntity target = axolotl.getTarget();
        if (target != null && target.isAlive() && isGuardianAxolotl(axolotl)) {
            PacketDistributor.sendToPlayer(player,
                    new SyncAxolotlTargetPacket(axolotl.getId(), target.getId()));
        }
    }

    @SubscribeEvent
    public void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getParentA() instanceof Axolotl parentA)) {
            return;
        }
        if (!(event.getParentB() instanceof Axolotl parentB)) {
            return;
        }
        if (!(event.getChild() instanceof Axolotl baby)) {
            return;
        }

        UUID ownerA = getOwnerUUID(parentA);
        UUID ownerB = getOwnerUUID(parentB);

        UUID chosenOwner = null;
        if (ownerA != null && ownerB != null) {
            chosenOwner = baby.getRandom().nextBoolean() ? ownerA : ownerB;
        } else if (ownerA != null) {
            chosenOwner = ownerA;
        } else if (ownerB != null) {
            chosenOwner = ownerB;
        }

        if (chosenOwner == null) {
            return;
        }

        baby.setData(AXOLOTL_OWNER.get(), chosenOwner.toString());
        broadcastOwnerSync(baby);
    }

    // ---- Axolotl tick logic ----

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Axolotl axolotl)) {
            return;
        }
        if (axolotl.level().isClientSide()) {
            return;
        }
        if (axolotl.tickCount % 10 != 0) {
            return;
        }
        if (!isOwned(axolotl)) {
            return;
        }

        tickAxolotl(axolotl);
    }

    private void tickAxolotl(Axolotl axolotl) {
        AxolotlGuardianConfig config = getConfig();

        long bowlPosLong = axolotl.getData(AXOLOTL_BOWL_POS.get());
        boolean hasBowl = bowlPosLong != Long.MIN_VALUE;

        UUID uid = axolotl.getUUID();
        if (!hasBowl) {
            tryAutoAssociate(axolotl, config.getAutoAssociateRadius());
            return;
        }

        // Sync navigation path to clients every 20 ticks for the debug overlay.
        if (axolotl.tickCount % 20 == 0) {
            syncPathToClients(axolotl);
        }

        BlockPos bowlPos = BlockPos.of(bowlPosLong);
        if (getBowlEntity(axolotl, bowlPos) == null) {
            // Bowl gone — attachment was cleared by getBowlEntity; sync + stop here.
            broadcastOwnerSync(axolotl);
            syncTargetIfChanged(axolotl);
            return;
        }

        // Dry-out safety (inverse of the cat's drowning check): a beached axolotl slowly dries
        // out. If it has been out of water for a while, abandon the target and head home — the
        // bowl sits in/under water.
        if (!axolotl.isInWaterOrBubble() && axolotl.getAirSupply() < 1200
                && !axolotl.getData(AXOLOTL_FLEEING.get())) {
            dropTarget(axolotl, true);
        }

        // While playing dead the brain's PLAY_DEAD activity outranks FIGHT and movement is
        // frozen — don't fight it. Drop any combat target: vanilla rolled play-dead BEFORE our
        // damage handler ran, so a retaliation target could otherwise sit stale (and long gone /
        // out of zone) until the axolotl wakes up. Fresh targets are re-acquired afterwards.
        if (axolotl.isPlayingDead()) {
            eraseAttackTarget(axolotl);
            decrementFedTicks(axolotl);
            syncTargetIfChanged(axolotl);
            return;
        }

        // Low-health flee: at <20% HP the axolotl enters an ABSOLUTE retreat — it ignores all
        // mobs and swims home, heals at base, and only resumes guarding once recovered above
        // 40% HP (hysteresis prevents yo-yoing in and out of combat at the edge).
        float healthPct = axolotl.getHealth() / axolotl.getMaxHealth();
        boolean fleeing = axolotl.getData(AXOLOTL_FLEEING.get());
        if (!fleeing && healthPct < 0.20f) {
            fleeing = true;
            axolotl.setData(AXOLOTL_FLEEING.get(), true);
        }
        if (fleeing) {
            eraseAttackTarget(axolotl);
            axolotl.setData(AXOLOTL_RETURNING.get(), false);
            returningAge.remove(uid);
            double distSqToBowl = axolotl.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
            boolean atBase = distSqToBowl <= 16.0;
            if (atBase) {
                axolotl.getNavigation().resetMaxVisitedNodesMultiplier();
                // Heal at base; resume duty once safely recovered
                axolotl.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
                AbstractAxolotlBowlBlockEntity fleeingBowl = getBowlEntity(axolotl, bowlPos);
                if (fleeingBowl instanceof AxolotlFeedingStationBlockEntity fleeingStation) {
                    transferLootToStation(axolotl, fleeingStation);
                }
                if (healthPct > 0.40f) {
                    axolotl.setData(AXOLOTL_FLEEING.get(), false);
                }
            } else {
                moveHome(axolotl, bowlPos, 1.0f);
            }
            decrementFedTicks(axolotl);
            syncTargetIfChanged(axolotl);
            return;
        }

        // Ordinary post-combat return: assert the WALK_TARGET home each cycle (MoveToTargetSink
        // erases it when it gives up — re-asserting restarts the attempt, which is the brain-mob
        // version of the cat's repath loop). Interruptible by new targets. Clears when within
        // ~4 blocks of the bowl; a return that hasn't arrived after a minute teleports home.
        if (axolotl.getData(AXOLOTL_RETURNING.get())) {
            int age = returningAge.merge(uid, 10, Integer::sum); // tickAxolotl runs every 10 ticks
            double distSqToBowl = axolotl.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
            // A full minute without arriving means the path has failed for good — don't strand
            // the axolotl wherever it drifted, emergency-port it home. Only if even the teleport
            // finds no water spot (bowl area broken) give up in place at 90s.
            if (distSqToBowl > 16.0 && age >= 1200 && teleportToBowl(axolotl, bowlPos)) {
                distSqToBowl = axolotl.distanceToSqr(
                        bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
            }
            if (distSqToBowl <= 16.0 || age >= 1800) {
                axolotl.setData(AXOLOTL_RETURNING.get(), false);
                returningAge.remove(uid);
                axolotl.getNavigation().resetMaxVisitedNodesMultiplier();
                if (distSqToBowl <= 16.0) {
                    AbstractAxolotlBowlBlockEntity returnBowl = getBowlEntity(axolotl, bowlPos);
                    if (returnBowl instanceof AxolotlFeedingStationBlockEntity returnStation) {
                        transferLootToStation(axolotl, returnStation);
                    }
                }
            } else {
                moveHome(axolotl, bowlPos, 1.0f);
            }
        } else {
            returningAge.remove(uid);
        }

        // Zone enforcement on the current brain target (also leashes vanilla fish hunting).
        enforceTargetZone(axolotl, bowlPos);

        // Stuck detection while the axolotl actively wants to move somewhere.
        tickStuckDetection(axolotl);

        // Decrement fed ticks regardless of other state
        int fedTicks = axolotl.getData(AXOLOTL_FED_TICKS.get());
        if (fedTicks > 0) {
            axolotl.setData(AXOLOTL_FED_TICKS.get(), Math.max(0, fedTicks - 10));

            maybeAcquireTarget(axolotl, bowlPos);

            // Idle leash: no combat, not returning → stay near the bowl (vanilla RandomStroll
            // only runs while WALK_TARGET is absent, so close to home it wanders freely).
            if (axolotl.getTarget() == null && !axolotl.getData(AXOLOTL_RETURNING.get())) {
                double idleDistSq = axolotl.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
                if (idleDistSq > 64.0) {
                    // Route the trip through the returning machinery (doubled node budget,
                    // circling detection, emergency teleport) instead of a bare walk target
                    // that silently dies when A* fails and leaves the axolotl adrift.
                    axolotl.setData(AXOLOTL_RETURNING.get(), true);
                } else if (idleDistSq <= 16.0) {
                    AbstractAxolotlBowlBlockEntity idleBowl = getBowlEntity(axolotl, bowlPos);
                    if (idleBowl instanceof AxolotlFeedingStationBlockEntity idleStation) {
                        transferLootToStation(axolotl, idleStation);
                    }
                }
            }
            syncTargetIfChanged(axolotl);
            return;
        }

        // Unfed: head to the bowl and eat
        AbstractAxolotlBowlBlockEntity bowl = getBowlEntity(axolotl, bowlPos);
        if (bowl == null || !bowl.hasFish()) {
            syncTargetIfChanged(axolotl);
            return;
        }

        double distSq = axolotl.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
        if (distSq > 4.0) {
            walkTargetTo(axolotl, bowlPos, 0.8f);
        } else {
            var fish = bowl.takeFish();
            if (!fish.isEmpty()) {
                axolotl.setData(AXOLOTL_FED_TICKS.get(), config.getFedDurationTicks());
                axolotl.playSound(SoundEvents.GENERIC_EAT, 0.5F, axolotl.getVoicePitch());
                axolotl.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                if (bowl instanceof AxolotlFeedingStationBlockEntity station) {
                    transferLootToStation(axolotl, station);
                }
            }
        }
        syncTargetIfChanged(axolotl);
    }

    private void decrementFedTicks(Axolotl axolotl) {
        int fed = axolotl.getData(AXOLOTL_FED_TICKS.get());
        if (fed > 0) {
            axolotl.setData(AXOLOTL_FED_TICKS.get(), Math.max(0, fed - 10));
        }
    }

    // ---- Brain-memory movement/targeting helpers ----

    private static void walkTargetTo(Axolotl axolotl, BlockPos pos, float speed) {
        axolotl.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, 2));
    }

    /**
     * Home-bound movement with a doubled A* node budget — the return path may span the whole
     * guard radius. Reset once the axolotl arrives (see returning/fleeing arrival branches).
     */
    private static void moveHome(Axolotl axolotl, BlockPos bowlPos, float speed) {
        axolotl.getNavigation().setMaxVisitedNodesMultiplier(2.0f);
        walkTargetTo(axolotl, bowlPos, speed);
    }

    private static void eraseAttackTarget(Axolotl axolotl) {
        Brain<?> brain = axolotl.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    /** Erases the combat target and (optionally) commits to returning home. */
    private void dropTarget(Axolotl axolotl, boolean returnHome) {
        eraseAttackTarget(axolotl);
        if (returnHome && axolotl.getData(AXOLOTL_BOWL_POS.get()) != Long.MIN_VALUE) {
            axolotl.setData(AXOLOTL_RETURNING.get(), true);
        }
    }

    private boolean isBlacklisted(Axolotl axolotl, LivingEntity target) {
        Map<Integer, Long> map = blockedTargets.get(axolotl.getUUID());
        if (map == null) {
            return false;
        }
        Long until = map.get(target.getId());
        if (until == null) {
            return false;
        }
        if (axolotl.level().getGameTime() >= until) {
            map.remove(target.getId());
            return false;
        }
        return true;
    }

    private void blacklist(Axolotl axolotl, LivingEntity target) {
        blockedTargets.computeIfAbsent(axolotl.getUUID(), k -> new HashMap<>())
                .put(target.getId(), axolotl.level().getGameTime() + BLACKLIST_TICKS);
    }

    /**
     * Drops or blacklists the current brain target when it (or the axolotl) violates the guard
     * zone or leaves the water. Applies to any target — including vanilla fish/squid hunting —
     * so a guardian can never be dragged away from its post.
     */
    private void enforceTargetZone(Axolotl axolotl, BlockPos bowlPos) {
        LivingEntity target = axolotl.getTarget();
        if (target == null) {
            return;
        }
        if (!target.isAlive()) {
            dropTarget(axolotl, true);
            return;
        }
        // Vanilla's own StartAttacking (attackables sensor) re-acquires any nearby hostile and
        // knows nothing about our blacklist — the tick after strike 3 drops an unreachable mob,
        // vanilla locks right back onto it and the axolotl keeps pressing against the obstacle.
        // Keep erasing it for as long as the blacklist entry lasts.
        if (isBlacklisted(axolotl, target)) {
            dropTarget(axolotl, true);
            return;
        }
        if (axolotl.getData(AXOLOTL_FED_TICKS.get()) <= 0) {
            // Fed state ran out mid-fight → disengage, go home
            dropTarget(axolotl, true);
            return;
        }
        // Water-only combat: the moment the target beaches itself, let it go.
        if (!target.isInWaterOrBubble()) {
            blacklist(axolotl, target);
            dropTarget(axolotl, true);
            return;
        }
        if (!isWithinGuardZone(axolotl, target.getX(), target.getY(), target.getZ(), TARGET_ZONE_BUFFER)) {
            blacklist(axolotl, target);
            dropTarget(axolotl, true);
            return;
        }
        if (!isWithinGuardZone(axolotl, axolotl.getX(), axolotl.getY(), axolotl.getZ(), AXOLOTL_ZONE_BUFFER)) {
            blacklist(axolotl, target);
            dropTarget(axolotl, true);
            return;
        }
        // Every 20 ticks: evict expired blacklist entries and check that the live navigation
        // path (recomputed by the brain as the target moves) still stays inside the guard zone.
        if (axolotl.tickCount % 20 == 0) {
            Map<Integer, Long> map = blockedTargets.get(axolotl.getUUID());
            if (map != null) {
                map.entrySet().removeIf(e -> axolotl.level().getGameTime() >= e.getValue());
            }
            net.minecraft.world.level.pathfinder.Path livePath = axolotl.getNavigation().getPath();
            if (livePath != null && !isPathWithinZone(livePath, axolotl)) {
                blacklist(axolotl, target);
                dropTarget(axolotl, true);
            }
        }
    }

    /**
     * Acquires a new combat target by setting the brain's ATTACK_TARGET memory. The vanilla
     * FIGHT activity (chase + melee) takes over from there. Candidates must be Monsters in
     * water inside the guard zone, ranked by actual A* path length.
     */
    private void maybeAcquireTarget(Axolotl axolotl, BlockPos bowlPos) {
        if (axolotl.getData(AXOLOTL_FLEEING.get())) {
            return;
        }
        // Water specialist: only engage while in water (a beached axolotl heads home instead).
        if (!axolotl.isInWaterOrBubble()) {
            return;
        }
        // If the axolotl has strayed outside its zone, commit to returning home first.
        if (!isWithinGuardZone(axolotl, axolotl.getX(), axolotl.getY(), axolotl.getZ(), AXOLOTL_ZONE_BUFFER)) {
            return;
        }
        LivingEntity current = axolotl.getTarget();
        if (current instanceof Monster && current.isAlive()) {
            return; // already fighting a hostile
        }

        UUID uid = axolotl.getUUID();
        Integer cooldown = searchCooldown.get(uid);
        if (cooldown != null && cooldown > 0) {
            searchCooldown.put(uid, cooldown - 1);
            return;
        }

        boolean returning = axolotl.getData(AXOLOTL_RETURNING.get());
        if (returning && isLootFull(axolotl)) {
            // Don't interrupt a return trip if all loot slots are full — the axolotl must
            // deposit before it can pick up more loot anyway; let it finish the trip.
            return;
        }

        Monster best = findTarget(axolotl, bowlPos);
        if (best == null) {
            searchCooldown.put(uid, 2); // ~20 ticks — A* per candidate is expensive
            return;
        }
        if (returning) {
            axolotl.setData(AXOLOTL_RETURNING.get(), false);
            returningAge.remove(uid);
            // The return trip doubled the A* node budget — don't drag that into combat.
            axolotl.getNavigation().resetMaxVisitedNodesMultiplier();
        }
        Brain<?> brain = axolotl.getBrain();
        brain.setMemory(MemoryModuleType.ATTACK_TARGET, best);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private boolean isLootFull(Axolotl axolotl) {
        var inv = axolotl.getData(AXOLOTL_INVENTORY.get()).getInventory();
        for (int s = AxolotlInventoryData.LOOT_START; s < AxolotlInventoryData.TOTAL_SLOTS; s++) {
            if (inv.getStackInSlot(s).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Selects the best in-water Monster around the bowl by A* path length. Pre-sorts by
     * Euclidean distance and evaluates the top 8 via actual path computation, discarding
     * partial paths (end node further than 2 blocks from the target) and routes that would
     * lead the axolotl out of its guard zone.
     */
    private Monster findTarget(Axolotl axolotl, BlockPos bowlPos) {
        double radius = getGuardRadius();
        double radiusY = getGuardRadiusY();
        AABB searchBox = new AABB(bowlPos).inflate(radius, radiusY, radius);
        List<Monster> hostiles = axolotl.level().getEntitiesOfClass(
                Monster.class, searchBox,
                m -> !m.isDeadOrDying() && m.isInWaterOrBubble() && !isBlacklisted(axolotl, m));
        if (hostiles.isEmpty()) {
            return null;
        }
        hostiles.sort(Comparator.comparingDouble(axolotl::distanceToSqr));
        int limit = Math.min(8, hostiles.size());
        Monster best = null;
        double bestLength = Double.MAX_VALUE;
        for (int i = 0; i < limit; i++) {
            Monster mob = hostiles.get(i);
            net.minecraft.world.level.pathfinder.Path path =
                    axolotl.getNavigation().createPath(mob.getX(), mob.getY(), mob.getZ(), 0);
            if (!endNodeNear(path, mob.getX(), mob.getY(), mob.getZ(), 2.0)) {
                continue; // unreachable or partial path — discard early instead of running at it
            }
            if (!isPathWithinZone(path, axolotl)) {
                continue; // route exits guard zone
            }
            double length = 0.0;
            for (int j = 1; j < path.getNodeCount(); j++) {
                net.minecraft.world.level.pathfinder.Node a = path.getNode(j - 1);
                net.minecraft.world.level.pathfinder.Node b = path.getNode(j);
                double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
                length += Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            if (length < bestLength) {
                bestLength = length;
                best = mob;
            }
        }
        return best;
    }

    /**
     * True if the path's closest reachable end node is within {@code maxDist} of the goal.
     */
    private static boolean endNodeNear(net.minecraft.world.level.pathfinder.Path path,
                                       double gx, double gy, double gz, double maxDist) {
        if (path == null) {
            return false;
        }
        net.minecraft.world.level.pathfinder.Node end = path.getEndNode();
        if (end == null) {
            return false;
        }
        double dx = (end.x + 0.5) - gx;
        double dy = end.y - gy;
        double dz = (end.z + 0.5) - gz;
        return dx * dx + dy * dy + dz * dz <= maxDist * maxDist;
    }

    /**
     * True if every node of {@code path} lies within the axolotl's guard zone (zero buffer).
     */
    private static boolean isPathWithinZone(net.minecraft.world.level.pathfinder.Path path, Axolotl axolotl) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            net.minecraft.world.level.pathfinder.Node n = path.getNode(i);
            if (!isWithinGuardZone(axolotl, n.x + 0.5, n.y, n.z + 0.5, 0.0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the given position is inside the axolotl's guard zone (bowl-centred, asymmetric
     * XZ/Y radius) plus the given hysteresis buffer. Returns false without a bowl.
     */
    private static boolean isWithinGuardZone(Axolotl axolotl, double x, double y, double z, double buffer) {
        long bowlLong = axolotl.getData(AXOLOTL_BOWL_POS.get());
        if (bowlLong == Long.MIN_VALUE) {
            return false;
        }
        BlockPos bowl = BlockPos.of(bowlLong);
        double radius = getGuardRadius();
        double radiusY = getGuardRadiusY();
        return Math.abs(x - (bowl.getX() + 0.5)) <= radius + buffer
                && Math.abs(z - (bowl.getZ() + 0.5)) <= radius + buffer
                && Math.abs(y - (bowl.getY() + 0.5)) <= radiusY + buffer;
    }

    /**
     * Stuck detection: samples every 40 ticks while the axolotl actively wants to move (combat
     * target, returning or fleeing). A strike is either too little position movement OR — on
     * homebound trips — swimming without getting closer to the bowl (failed paths degrade into
     * RandomSwim circling). Every strike erases the PATH memory so MoveToTargetSink recomputes;
     * strike 3 blacklists the target and commits to returning home; strike ≥ 5 while
     * returning/fleeing (~20s stuck) emergency-teleports the axolotl into a water block next
     * to the bowl — the guardian pendant of the vanilla pet owner-teleport, so no axolotl ever
     * stays stranded.
     */
    private void tickStuckDetection(Axolotl axolotl) {
        UUID uid = axolotl.getUUID();
        boolean homebound = axolotl.getData(AXOLOTL_RETURNING.get())
                || axolotl.getData(AXOLOTL_FLEEING.get());
        boolean wantsToMove = axolotl.getTarget() != null || homebound;
        if (!wantsToMove) {
            stuckSample.remove(uid);
            stuckStrikes.remove(uid);
            homeDistSample.remove(uid);
            return;
        }
        if (axolotl.tickCount % 40 != 0) {
            return;
        }
        net.minecraft.world.phys.Vec3 now = axolotl.position();
        net.minecraft.world.phys.Vec3 prev = stuckSample.put(uid, now);

        // Homebound trips also track distance to the bowl: a failed return path degrades into
        // vanilla RandomSwim circling — plenty of movement, zero progress. Only counts as a
        // strike while still >5 blocks out (fighting/wandering around home is fine).
        Double distNow = null;
        Double distPrev = null;
        long bowlLong = axolotl.getData(AXOLOTL_BOWL_POS.get());
        if (homebound && bowlLong != Long.MIN_VALUE) {
            BlockPos bowl = BlockPos.of(bowlLong);
            distNow = Math.sqrt(axolotl.distanceToSqr(
                    bowl.getX() + 0.5, bowl.getY() + 0.5, bowl.getZ() + 0.5));
            distPrev = homeDistSample.put(uid, distNow);
        } else {
            homeDistSample.remove(uid);
        }
        if (prev == null) {
            return;
        }
        boolean noMove = prev.distanceToSqr(now) < 0.75 * 0.75;
        boolean noHomeProgress = distNow != null && distPrev != null
                && distNow > 5.0 && distPrev - distNow < 0.5;
        if (!noMove && !noHomeProgress) {
            stuckStrikes.remove(uid);
            return;
        }
        int strikes = stuckStrikes.merge(uid, 1, Integer::sum);
        // Force a fresh path attempt — MoveToTargetSink keeps following its stored PATH memory.
        axolotl.getBrain().eraseMemory(MemoryModuleType.PATH);
        axolotl.getNavigation().stop();
        if (strikes == 3) {
            LivingEntity target = axolotl.getTarget();
            if (target != null) {
                blacklist(axolotl, target);
            }
            dropTarget(axolotl, true);
        }
        boolean headingHome = axolotl.getData(AXOLOTL_RETURNING.get())
                || axolotl.getData(AXOLOTL_FLEEING.get());
        if (strikes >= 5 && headingHome
                && bowlLong != Long.MIN_VALUE && teleportToBowl(axolotl, BlockPos.of(bowlLong))) {
            stuckStrikes.remove(uid);
            stuckSample.remove(uid);
            homeDistSample.remove(uid);
        }
    }

    /**
     * Emergency teleport into a water block next to the bowl. The bowl sits in/under water by
     * module design, so a candidate practically always exists; returns false otherwise and the
     * caller simply retries on a later strike.
     */
    private boolean teleportToBowl(Axolotl axolotl, BlockPos bowlPos) {
        var level = axolotl.level();
        for (BlockPos candidate : BlockPos.betweenClosed(bowlPos.offset(-2, -1, -2), bowlPos.offset(2, 1, 2))) {
            if (candidate.equals(bowlPos)) {
                continue; // don't drop the axolotl into the bowl block itself
            }
            boolean swimmable =
                    level.getFluidState(candidate).is(net.minecraft.tags.FluidTags.WATER)
                            && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty();
            if (!swimmable) {
                continue;
            }
            axolotl.moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5,
                    axolotl.getYRot(), axolotl.getXRot());
            axolotl.getNavigation().stop();
            axolotl.getBrain().eraseMemory(MemoryModuleType.PATH);
            return true;
        }
        return false;
    }

    private void syncTargetIfChanged(Axolotl axolotl) {
        LivingEntity target = axolotl.getTarget();
        int targetId = (target != null && target.isAlive())
                ? target.getId()
                : SyncAxolotlTargetPacket.NO_TARGET;
        Integer last = lastSyncedTarget.get(axolotl.getUUID());
        if (last != null && last == targetId) {
            return;
        }
        lastSyncedTarget.put(axolotl.getUUID(), targetId);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(axolotl,
                new SyncAxolotlTargetPacket(axolotl.getId(), targetId));
    }

    // ---- Loot / station transfer ----

    private void transferLootToStation(Axolotl axolotl, AxolotlFeedingStationBlockEntity station) {
        AxolotlInventoryData axolotlInv = axolotl.getData(AXOLOTL_INVENTORY.get());
        var axolotlLoot = axolotlInv.getInventory();
        var stationLoot = station.getLootInventory();
        for (int slot = AxolotlInventoryData.LOOT_START; slot < AxolotlInventoryData.TOTAL_SLOTS; slot++) {
            ItemStack stack = axolotlLoot.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int ss = 0; ss < stationLoot.getSlots(); ss++) {
                stack = stationLoot.insertItem(ss, stack, false);
                if (stack.isEmpty()) {
                    break;
                }
            }
            axolotlLoot.setStackInSlot(slot, stack);
        }

        // XP: axolotl buffer → station counter
        int axolotlXp = axolotl.getData(AXOLOTL_XP.get());
        if (axolotlXp > 0) {
            int stationCap = getConfig().getStationXpCapacity();
            int canStore = stationCap - station.getStoredXp();
            int toTransfer = Math.min(axolotlXp, canStore);
            if (toTransfer > 0) {
                station.addStoredXp(toTransfer);
                axolotl.setData(AXOLOTL_XP.get(), axolotlXp - toTransfer);
                broadcastStatsSync(axolotl);
            }
        }

        // Convert station XP into XP Bottles in the loot inventory
        int xpPerBottle = getConfig().getXpPerBottle();
        while (station.getStoredXp() >= xpPerBottle) {
            ItemStack bottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
            boolean inserted = false;
            for (int s = 0; s < stationLoot.getSlots(); s++) {
                ItemStack rem = stationLoot.insertItem(s, bottle, false);
                if (rem.isEmpty()) {
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                break; // loot inventory full
            }
            station.addStoredXp(-xpPerBottle);
        }
    }

    private AbstractAxolotlBowlBlockEntity getBowlEntity(Axolotl axolotl, BlockPos bowlPos) {
        var be = axolotl.level().getBlockEntity(bowlPos);
        if (be instanceof AbstractAxolotlBowlBlockEntity bowl) {
            return bowl;
        }
        axolotl.setData(AXOLOTL_BOWL_POS.get(), Long.MIN_VALUE);
        return null;
    }

    private void tryAutoAssociate(Axolotl axolotl, double autoRadius) {
        BlockPos axolotlPos = axolotl.blockPosition();
        int r = (int) Math.ceil(autoRadius);
        double radiusSq = autoRadius * autoRadius;

        // Collect all valid bowls in range, then pick the nearest one. BlockPos.betweenClosed
        // iterates in YXZ order so first-found is not necessarily closest.
        BlockPos nearestPos = null;
        AbstractAxolotlBowlBlockEntity nearestBowl = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (BlockPos checkPos : BlockPos.betweenClosed(
                axolotlPos.offset(-r, -r, -r), axolotlPos.offset(r, r, r))) {
            if (!(axolotl.level().getBlockEntity(checkPos) instanceof AbstractAxolotlBowlBlockEntity bowl)
                    || !bowl.canAddAxolotl(axolotl.getUUID())) {
                continue;
            }
            double distSq = axolotl.distanceToSqr(
                    checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
            if (distSq <= radiusSq && distSq < nearestDistSq) {
                nearestPos = checkPos.immutable();
                nearestBowl = bowl;
                nearestDistSq = distSq;
            }
        }

        if (nearestPos != null) {
            axolotl.setData(AXOLOTL_BOWL_POS.get(), nearestPos.asLong());
            nearestBowl.addAxolotl(axolotl.getUUID());
            broadcastOwnerSync(axolotl);
        }
    }

    // ---- Path sync for debug overlay ----

    private void syncPathToClients(Axolotl axolotl) {
        net.minecraft.world.level.pathfinder.Path path = axolotl.getNavigation().getPath();
        if (path == null || path.isDone()) {
            // Keep the last path on the client while the axolotl has a target so the approach
            // path stays visible through momentary isDone() gaps. Only clear when truly idle.
            if (axolotl.getTarget() != null) {
                return;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(axolotl,
                    new SyncAxolotlPathPacket(axolotl.getId(),
                            SyncAxolotlPathPacket.EMPTY, SyncAxolotlPathPacket.EMPTY, SyncAxolotlPathPacket.EMPTY, 0));
            return;
        }
        int n = path.getNodeCount();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        for (int i = 0; i < n; i++) {
            net.minecraft.world.level.pathfinder.Node node = path.getNode(i);
            xs[i] = node.x;
            ys[i] = node.y;
            zs[i] = node.z;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(axolotl,
                new SyncAxolotlPathPacket(axolotl.getId(), xs, ys, zs, path.getNextNodeIndex()));
    }

    // ---- Interaction: taming, feeding, armor equip, bucket handling ----

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Axolotl axolotl)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldItem = event.getItemStack();

        // Bucket pickup on an OWNED axolotl: replicate vanilla pickup but carry the guardian
        // payload along — the vanilla path discards the entity and keeps only Variant/Age,
        // silently destroying owner, XP, armor and loot.
        if (heldItem.is(Items.WATER_BUCKET) && isOwned(axolotl)) {
            // SUCCESS (not the default PASS) — a PASS cancellation makes the client fall
            // through to the use-item stage, which would place the water bucket's contents.
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(
                    event.getLevel().isClientSide()));
            if (!event.getLevel().isClientSide() && axolotl.isAlive()) {
                pickUpOwnedAxolotl(player, event.getHand(), axolotl);
            }
            return;
        }

        // Tropical fish (the plain item — vanilla only breeds via the bucket, we also accept
        // the plain item so Gerry can mass-breed without farming buckets):
        // unowned → taming attempt; owned → feed (any player may feed, like cats) + breed.
        if (isAxolotlFood(heldItem)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(
                    event.getLevel().isClientSide()));
            if (event.getLevel().isClientSide()) {
                return;
            }
            if (!player.isCreative()) {
                heldItem.shrink(1);
            }
            if (!isOwned(axolotl)) {
                if (axolotl.getRandom().nextInt(3) == 0) {
                    axolotl.setData(AXOLOTL_OWNER.get(), player.getUUID().toString());
                    broadcastOwnerSync(axolotl);
                    axolotl.level().playSound(null, axolotl.getX(), axolotl.getY(), axolotl.getZ(),
                            SoundEvents.AXOLOTL_IDLE_WATER, axolotl.getSoundSource(), 1.0f, 1.0f);
                    if (axolotl.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.HEART,
                                axolotl.getX(), axolotl.getEyeY(), axolotl.getZ(), 7, 0.3, 0.3, 0.3, 0);
                    }
                } else if (axolotl.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SMOKE,
                            axolotl.getX(), axolotl.getEyeY(), axolotl.getZ(), 5, 0.3, 0.3, 0.3, 0);
                }
            } else {
                axolotl.setData(AXOLOTL_FED_TICKS.get(), getFedDurationTicks());
                axolotl.setHealth(axolotl.getMaxHealth());
                axolotl.level().playSound(null, axolotl.getX(), axolotl.getY(), axolotl.getZ(),
                        SoundEvents.AXOLOTL_IDLE_WATER, axolotl.getSoundSource(), 1.0f, 1.0f);

                // Mass breeding: an adult, breed-ready owned axolotl (guardian or not) goes into
                // love mode on the same feed, mirroring Animal#mobInteract. Vanilla only allows
                // this via the tropical-fish bucket; we allow it via the plain (farmable) fish
                // too, since Gerry needs to breed axolotls at scale. setInLove() spawns its own
                // heart particles, so we skip the manual ones in that branch to avoid doubling up.
                if (axolotl.getAge() == 0 && axolotl.canFallInLove()) {
                    axolotl.setInLove(player);
                } else if (axolotl.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HEART,
                            axolotl.getX(), axolotl.getEyeY(), axolotl.getZ(), 7, 0.3, 0.3, 0.3, 0);
                }
            }
            return;
        }

        if (!isOwnedBy(axolotl, player.getUUID())) {
            return;
        }

        // Armor equip (owner only)
        AxolotlInventoryData invData = axolotl.getData(AXOLOTL_INVENTORY.get());
        ItemStack currentArmor = invData.getArmor();

        if (heldItem.getItem() instanceof AxolotlArmorItem) {
            if (!event.getLevel().isClientSide()) {
                if (!currentArmor.isEmpty() && !player.getInventory().add(currentArmor)) {
                    player.drop(currentArmor, false);
                }
                ItemStack newArmor = heldItem.copyWithCount(1);
                invData.setArmor(newArmor);
                applyArmorAttribute(axolotl, newArmor);
                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }
                broadcastArmorSync(axolotl);
            }
            event.setCanceled(true);
        }
        // Armor is removed by dragging it out of the axolotl inventory GUI. Modifier+right-click
        // (default Ctrl) opens that GUI client-side (see AxolotlGuardianClientEvents).
    }

    /**
     * Replicates {@code Bucketable.bucketMobPickup} for an owned axolotl, additionally writing
     * the guardian payload (owner, bowl, fed state, XP, inventory incl. armor) into the bucket's
     * BUCKET_ENTITY_DATA so a bucket round-trip loses nothing. Restore happens in
     * {@link #onRightClickBlock} + {@link #applyPendingBucketRestore}.
     */
    private void pickUpOwnedAxolotl(Player player, InteractionHand hand, Axolotl axolotl) {
        ItemStack held = player.getItemInHand(hand);
        axolotl.playSound(axolotl.getPickupSound(), 1.0F, 1.0F);
        ItemStack bucket = axolotl.getBucketItemStack();
        axolotl.saveToBucketTag(bucket);

        CompoundTag payload = new CompoundTag();
        payload.putString("owner", axolotl.getData(AXOLOTL_OWNER.get()));
        payload.putLong("bowl_pos", axolotl.getData(AXOLOTL_BOWL_POS.get()));
        payload.putInt("fed_ticks", axolotl.getData(AXOLOTL_FED_TICKS.get()));
        payload.putInt("xp", axolotl.getData(AXOLOTL_XP.get()));
        payload.put("inventory", axolotl.getData(AXOLOTL_INVENTORY.get()).getInventory()
                .serializeNBT(axolotl.registryAccess()));
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, tag -> tag.put(GUARDIAN_BUCKET_TAG, payload));

        ItemStack result = ItemUtils.createFilledResult(held, player, bucket, false);
        player.setItemInHand(hand, result);

        // Detach from the bowl BE — the new entity gets a fresh UUID and re-registers on restore.
        long bowlLong = axolotl.getData(AXOLOTL_BOWL_POS.get());
        if (bowlLong != Long.MIN_VALUE
                && axolotl.level().getBlockEntity(BlockPos.of(bowlLong)) instanceof AbstractAxolotlBowlBlockEntity bowl) {
            bowl.removeAxolotl(axolotl.getUUID());
        }
        clearAxolotlState(axolotl.getUUID()); // discarded entity never dies — clean the maps here
        axolotl.discard();
    }

    /** Drops all per-UUID module state for an axolotl that left the world (death or bucket pickup). */
    private void clearAxolotlState(UUID uid) {
        returningAge.remove(uid);
        searchCooldown.remove(uid);
        blockedTargets.remove(uid);
        lastSyncedTarget.remove(uid);
        stuckSample.remove(uid);
        stuckStrikes.remove(uid);
        homeDistSample.remove(uid);
    }

    /**
     * Pre-captures the guardian payload when a marked axolotl bucket is about to be emptied.
     * The spawned entity is matched in {@link #applyPendingBucketRestore} (same dimension,
     * same tick window, near the clicked position). Dispenser placements bypass this event and
     * lose the payload — acceptable edge case.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(Items.AXOLOTL_BUCKET)) {
            return;
        }
        CustomData data = held.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(GUARDIAN_BUCKET_TAG)) {
            return;
        }
        pendingBucketRestores.add(new PendingBucketRestore(
                tag.getCompound(GUARDIAN_BUCKET_TAG),
                event.getLevel().dimension(),
                event.getPos().relative(event.getFace() != null ? event.getFace() : Direction.UP),
                event.getLevel().getGameTime()));
    }

    private void applyPendingBucketRestore(Axolotl axolotl) {
        if (pendingBucketRestores.isEmpty()) {
            return;
        }
        long now = axolotl.level().getGameTime();
        Iterator<PendingBucketRestore> iter = pendingBucketRestores.iterator();
        CompoundTag payload = null;
        while (iter.hasNext()) {
            PendingBucketRestore pending = iter.next();
            if (now - pending.gameTime() > 1) {
                iter.remove(); // stale (bucket use failed or was blocked)
                continue;
            }
            if (payload == null
                    && pending.dimension().equals(axolotl.level().dimension())
                    && pending.pos().distToCenterSqr(axolotl.getX(), axolotl.getY(), axolotl.getZ()) <= 9.0) {
                payload = pending.payload();
                iter.remove();
            }
        }
        if (payload == null) {
            return;
        }

        axolotl.setData(AXOLOTL_OWNER.get(), payload.getString("owner"));
        axolotl.setData(AXOLOTL_FED_TICKS.get(), payload.getInt("fed_ticks"));
        axolotl.setData(AXOLOTL_XP.get(), payload.getInt("xp"));
        if (payload.contains("inventory")) {
            axolotl.getData(AXOLOTL_INVENTORY.get()).getInventory()
                    .deserializeNBT(axolotl.registryAccess(), payload.getCompound("inventory"));
        }
        ItemStack armor = axolotl.getData(AXOLOTL_INVENTORY.get()).getArmor();
        if (!armor.isEmpty()) {
            applyArmorAttribute(axolotl, armor);
        }

        long bowlLong = payload.getLong("bowl_pos");
        if (bowlLong != Long.MIN_VALUE
                && axolotl.level().getBlockEntity(BlockPos.of(bowlLong)) instanceof AbstractAxolotlBowlBlockEntity bowl
                && bowl.canAddAxolotl(axolotl.getUUID())) {
            axolotl.setData(AXOLOTL_BOWL_POS.get(), bowlLong);
            bowl.addAxolotl(axolotl.getUUID());
        }
        // Trackers don't exist yet at join time — PlayerEvent.StartTracking syncs owner/armor/
        // stats to each player as they start tracking the new entity.
    }

    // ---- Petting (empty-hand left-click on an owned axolotl) ----

    @SubscribeEvent
    public void onAttackAxolotl(AttackEntityEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Axolotl axolotl)) {
            return;
        }
        if (!isOwned(axolotl)) {
            return;
        }
        if (!event.getEntity().getMainHandItem().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        axolotl.level().playSound(null, axolotl.getX(), axolotl.getY(), axolotl.getZ(),
                SoundEvents.AXOLOTL_IDLE_WATER, axolotl.getSoundSource(), 1.0f, 1.0f);
        if (axolotl.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART,
                    axolotl.getX(), axolotl.getEyeY(), axolotl.getZ(), 5, 0.3, 0.3, 0.3, 0);
        }
    }

    // ---- Armor damage absorption + retaliation ----

    @SubscribeEvent
    public void onAxolotlHurt(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Axolotl axolotl)) {
            return;
        }
        AxolotlInventoryData invData = axolotl.getData(AXOLOTL_INVENTORY.get());
        ItemStack armor = invData.getArmor();
        if (armor.getItem() instanceof AxolotlArmorItem) {
            // Armor absorbs 100% of damage; each damage point drains 1 durability.
            // Note: vanilla play-dead already rolled on the RAW damage in Axolotl.hurt() before
            // this event — an armored guardian can still flop over. Deliberate flavor; if it
            // annoys in testing, erase PLAY_DEAD_TICKS here.
            float absorbed = event.getNewDamage();
            event.setNewDamage(0f);

            armor.hurtAndBreak(Math.max(1, (int) Math.ceil(absorbed)), axolotl,
                    net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (armor.isEmpty()) {
                invData.setArmor(ItemStack.EMPTY);
                removeArmorAttribute(axolotl);
            }
            broadcastArmorSync(axolotl);
        }

        // Retaliation: if hit by a Monster in water inside the zone, engage it directly.
        // AXOLOTL_FLEEING axolotls ignore this — they're already running home. Playing-dead
        // axolotls too: PLAY_DEAD outranks FIGHT, so a target set now would just sit stale
        // (and possibly long gone) until the axolotl wakes up.
        if (!axolotl.level().isClientSide() && isGuardianAxolotl(axolotl)
                && !axolotl.getData(AXOLOTL_FLEEING.get()) && !axolotl.isPlayingDead()) {
            Entity direct = event.getSource().getDirectEntity();
            Entity indirect = event.getSource().getEntity();
            LivingEntity attacker = direct instanceof Monster m ? m
                    : indirect instanceof Monster m2 ? m2 : null;
            // A blacklisted attacker was already proven unreachable (strike 3 / zone exit).
            // Re-engaging would pin the axolotl against the obstacle again — and clearing
            // RETURNING below would disarm the strike-5 emergency teleport. A trident drowned
            // behind a wall stays ignored; only a melee-range hit (provably reachable)
            // overrides the blacklist.
            if (attacker != null && !attacker.isDeadOrDying() && attacker.isInWaterOrBubble()
                    && isWithinGuardZone(axolotl, attacker.getX(), attacker.getY(), attacker.getZ(),
                    TARGET_ZONE_BUFFER)
                    && (!isBlacklisted(axolotl, attacker) || axolotl.distanceToSqr(attacker) <= 9.0)) {
                // End any return trip FIRST: otherwise the returning branch keeps re-asserting
                // WALK_TARGET(home) every cycle while the FIGHT activity steers toward the
                // attacker — a permanent tug-of-war (maybeAcquireTarget never clears RETURNING
                // for a target it didn't acquire itself).
                if (axolotl.getData(AXOLOTL_RETURNING.get())) {
                    axolotl.setData(AXOLOTL_RETURNING.get(), false);
                    returningAge.remove(axolotl.getUUID());
                    axolotl.getNavigation().resetMaxVisitedNodesMultiplier();
                }
                axolotl.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
            }
        }
    }

    /**
     * Credits the axolotl's owner as the attacker on any mob a guardian axolotl damages.
     *
     * <p>Vanilla only drops experience (and player-conditioned loot) when the victim was recently
     * hurt by a player ({@code lastHurtByPlayerTime > 0}). A mob killed purely by an axolotl
     * therefore drops no XP and {@link #onExperienceDrop} never fires. Marking the owner as the
     * last player attacker makes vanilla drop XP normally, which is then redirected into the
     * axolotl's buffer. An offline owner means no XP — exact parity with the cat module.
     */
    @SubscribeEvent
    public void onMobDamagedByAxolotl(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Axolotl) {
            return; // axolotl-as-victim is handled by onAxolotlHurt
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity indirect = event.getSource().getEntity();
        Axolotl axolotl = direct instanceof Axolotl a ? a
                : indirect instanceof Axolotl a2 ? a2 : null;
        if (axolotl == null || !isGuardianAxolotl(axolotl)) {
            return;
        }
        UUID owner = getOwnerUUID(axolotl);
        if (owner != null && axolotl.level().getPlayerByUUID(owner) instanceof Player ownerPlayer) {
            victim.setLastHurtByPlayer(ownerPlayer);
        }
        // Pre-record XP redirection so it is set before death regardless of event ordering.
        pendingXpCapture.put(victim.getId(), axolotl.getId());
    }

    // ---- Loot collection ----

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity indirect = event.getSource().getEntity();
        Axolotl axolotl = null;
        if (direct instanceof Axolotl a && a.getData(AXOLOTL_BOWL_POS.get()) != Long.MIN_VALUE) {
            axolotl = a;
        } else if (indirect instanceof Axolotl a && a.getData(AXOLOTL_BOWL_POS.get()) != Long.MIN_VALUE) {
            axolotl = a;
        }
        if (axolotl == null) {
            return;
        }

        // Record for XP redirection (consumed by onExperienceDrop in the same tick)
        pendingXpCapture.put(event.getEntity().getId(), axolotl.getId());

        AxolotlInventoryData axolotlInv = axolotl.getData(AXOLOTL_INVENTORY.get());
        var lootHandler = axolotlInv.getInventory();

        Iterator<ItemEntity> iter = event.getDrops().iterator();
        while (iter.hasNext()) {
            ItemEntity itemEntity = iter.next();
            ItemStack drop = itemEntity.getItem().copy();
            for (int slot = AxolotlInventoryData.LOOT_START; slot < AxolotlInventoryData.TOTAL_SLOTS; slot++) {
                drop = lootHandler.insertItem(slot, drop, false);
                if (drop.isEmpty()) {
                    break;
                }
            }
            if (drop.isEmpty()) {
                iter.remove();
            } else {
                itemEntity.setItem(drop);
            }
        }
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        Integer axolotlEntityId = pendingXpCapture.remove(event.getEntity().getId());
        if (axolotlEntityId == null) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(serverLevel.getEntity(axolotlEntityId) instanceof Axolotl axolotl)) {
            return;
        }
        int xp = event.getDroppedExperience();
        int capacity = getConfig().getAxolotlXpCapacity();
        int current = axolotl.getData(AXOLOTL_XP.get());
        int canAbsorb = capacity - current;
        if (canAbsorb <= 0) {
            return;
        }
        int absorbed = Math.min(xp, canAbsorb);
        axolotl.setData(AXOLOTL_XP.get(), current + absorbed);
        broadcastStatsSync(axolotl);
        event.setDroppedExperience(xp - absorbed);
    }

    @SubscribeEvent
    public void onAxolotlDeath(LivingDeathEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Axolotl axolotl)) {
            return;
        }
        if (!(axolotl.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID uid = axolotl.getUUID();
        clearAxolotlState(uid);
        long bowlLong = axolotl.getData(AXOLOTL_BOWL_POS.get());
        if (bowlLong == Long.MIN_VALUE) {
            return;
        }
        BlockPos bowlPos = BlockPos.of(bowlLong);
        if (serverLevel.getBlockEntity(bowlPos) instanceof AbstractAxolotlBowlBlockEntity bowl) {
            bowl.removeAxolotl(uid);
        }
        axolotl.setData(AXOLOTL_BOWL_POS.get(), Long.MIN_VALUE);
    }

    // ---- Armor attribute helpers ----

    private void applyArmorAttribute(Axolotl axolotl, ItemStack armor) {
        var attrInstance = axolotl.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrInstance == null) {
            return;
        }
        attrInstance.removeModifier(ARMOR_MODIFIER_ID);
        if (armor.getItem() instanceof AxolotlArmorItem axolotlArmor) {
            attrInstance.addPermanentModifier(new AttributeModifier(
                    ARMOR_MODIFIER_ID, axolotlArmor.getTier().getAttackBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void removeArmorAttribute(Axolotl axolotl) {
        var attrInstance = axolotl.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrInstance != null) {
            attrInstance.removeModifier(ARMOR_MODIFIER_ID);
        }
    }
}
