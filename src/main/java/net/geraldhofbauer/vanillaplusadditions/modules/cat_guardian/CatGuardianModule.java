package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian;

import com.mojang.serialization.Codec;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatBowlBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatFeedingStationBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatFeedingStationBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatInventoryData;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config.CatGuardianConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item.CatArmorItem;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatFeedingStationMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatInventoryMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client.CatGuardianClientEvents;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.OpenCatInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.RequestCatGlowPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatInventoryPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
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

import java.util.Comparator;
import java.util.EnumSet;
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

public class CatGuardianModule extends AbstractModule<CatGuardianModule, CatGuardianConfig> {

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

    public static final DeferredBlock<CatBowlBlock> CAT_BOWL =
            BLOCKS.register("cat_bowl", () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(1.5F, 6.0F)
                        .sound(SoundType.STONE)
                        .noOcclusion();
                if (ModList.get().isLoaded("sable")) {
                    return new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable
                            .SableCatBowlBlock(props);
                }
                return new CatBowlBlock(props);
            });

    public static final DeferredBlock<CatFeedingStationBlock> CAT_FEEDING_STATION =
            BLOCKS.register("cat_feeding_station", () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(3.5F, 12.0F)
                        .sound(SoundType.STONE)
                        .requiresCorrectToolForDrops();
                if (ModList.get().isLoaded("sable")) {
                    return new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable
                            .SableCatFeedingStationBlock(props);
                }
                return new CatFeedingStationBlock(props);
            });

    // ---- Block items ----

    public static final DeferredItem<BlockItem> CAT_BOWL_ITEM =
            ITEMS.register("cat_bowl", () -> new BlockItem(CAT_BOWL.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> CAT_FEEDING_STATION_ITEM =
            ITEMS.register("cat_feeding_station",
                    () -> new BlockItem(CAT_FEEDING_STATION.get(), new Item.Properties()));

    // ---- Cat armor items ----

    public static final DeferredItem<CatArmorItem> CAT_ARMOR_IRON =
            ITEMS.register("cat_armor_iron",
                    () -> new CatArmorItem(CatArmorItem.Tier.IRON, new Item.Properties()));

    public static final DeferredItem<CatArmorItem> CAT_ARMOR_GOLD =
            ITEMS.register("cat_armor_gold",
                    () -> new CatArmorItem(CatArmorItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<CatArmorItem> CAT_ARMOR_DIAMOND =
            ITEMS.register("cat_armor_diamond",
                    () -> new CatArmorItem(CatArmorItem.Tier.DIAMOND, new Item.Properties()));

    public static final DeferredItem<CatArmorItem> CAT_ARMOR_NETHERITE =
            ITEMS.register("cat_armor_netherite",
                    () -> new CatArmorItem(CatArmorItem.Tier.NETHERITE, new Item.Properties().fireResistant()));

    // ---- Block entity types ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatBowlBlockEntity>> CAT_BOWL_BE =
            BLOCK_ENTITY_TYPES.register("cat_bowl",
                    () -> BlockEntityType.Builder.of(CatBowlBlockEntity::new, CAT_BOWL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatFeedingStationBlockEntity>> CAT_FEEDING_STATION_BE =
            BLOCK_ENTITY_TYPES.register("cat_feeding_station",
                    () -> BlockEntityType.Builder.of(CatFeedingStationBlockEntity::new,
                            CAT_FEEDING_STATION.get()).build(null));

    // ---- Menu types ----

    public static final DeferredHolder<MenuType<?>, MenuType<CatFeedingStationMenu>> CAT_FEEDING_STATION_MENU =
            MENUS.register("cat_feeding_station",
                    () -> IMenuTypeExtension.create(CatFeedingStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CatInventoryMenu>> CAT_INVENTORY_MENU =
            MENUS.register("cat_inventory",
                    () -> IMenuTypeExtension.create(CatInventoryMenu::new));

    // ---- Entity attachment types (persisted in entity NBT) ----

    public static final Supplier<AttachmentType<Long>> CAT_BOWL_POS =
            ATTACHMENT_TYPES.register("cat_bowl_pos", () ->
                    AttachmentType.<Long>builder(() -> Long.MIN_VALUE)
                            .serialize(Codec.LONG)
                            .build());

    public static final Supplier<AttachmentType<Integer>> CAT_FED_TICKS =
            ATTACHMENT_TYPES.register("cat_fed_ticks", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    public static final Supplier<AttachmentType<CatInventoryData>> CAT_INVENTORY =
            ATTACHMENT_TYPES.register("cat_inventory", () ->
                    AttachmentType.<CatInventoryData>builder(CatInventoryData::new)
                            .serialize(CatInventoryData.CODEC)
                            .build());

    public static final Supplier<AttachmentType<Boolean>> CAT_RETURNING =
            ATTACHMENT_TYPES.register("cat_returning", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    /** Low-health retreat flag: absolute, ignores all mobs and flees to base until healed. */
    public static final Supplier<AttachmentType<Boolean>> CAT_FLEEING =
            ATTACHMENT_TYPES.register("cat_fleeing", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    public static final Supplier<AttachmentType<Integer>> CAT_XP =
            ATTACHMENT_TYPES.register("cat_xp", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    // ---- Armor attribute modifier ID ----

    private static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "cat_armor_bonus");

    // ---- Guard follow-range boost: doubles A* node budget (16→32 blocks) for indoor navigation ----
    private static final ResourceLocation GUARDIAN_FOLLOW_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "guardian_follow_range");

    // ---- Step-height boost: lets cats climb 1.5-block ledges, matching the pathfinder's calc ----
    private static final ResourceLocation GUARDIAN_STEP_HEIGHT_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "guardian_step_height");

    // ---- Singleton reference for config access from static context ----

    private static CatGuardianModule instance;

    // ---- Stuck-detection state (transient, not persisted) ----
    // Stores a position snapshot every tick; after 60 ticks checks net displacement.
    // Using Vec3 (not BlockPos) catches oscillation between two adjacent block positions.
    private final Map<UUID, net.minecraft.world.phys.Vec3> catNavRefPos = new HashMap<>();
    private final Map<UUID, Integer> catNavRefAge = new HashMap<>();
    // Accumulated ticks a cat has spent in the ordinary (interruptible) returning state.
    private final Map<UUID, Integer> catReturningAge = new HashMap<>();
    // Throttled cache of a cat's computed path toward its goal (for stuck-drive direction + reach).
    private final Map<UUID, net.minecraft.world.level.pathfinder.Path> catGoalPath = new HashMap<>();
    private final Map<UUID, Integer> catGoalPathTick = new HashMap<>();

    // Maps dead entity ID → guardian cat entity ID; used to redirect XP to the cat.
    // Populated in onLivingDrops, consumed in onExperienceDrop (same death tick).
    private final Map<Integer, Integer> pendingXpCapture = new HashMap<>();

    public static double getAssociationRadius() {
        return instance != null ? instance.getConfig().getAssociationRadius() : 64.0D;
    }

    public static int getFedDurationTicks() {
        return instance != null ? instance.getConfig().getFedDurationTicks() : 6000;
    }

    /** Returns true if this cat is registered as a guardian (has a bowl assignment). */
    public static boolean isGuardianCat(Cat cat) {
        if (instance == null || !instance.isModuleEnabled()) {
            return false;
        }
        try {
            return cat.getData(CAT_BOWL_POS.get()) != Long.MIN_VALUE;
        } catch (Exception e) {
            return false;
        }
    }

    public static int getMaxCatsPerStation() {
        return instance != null ? instance.getConfig().getMaxCatsPerStation() : 8;
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

    public static int getCatXpCapacity() {
        return instance != null ? instance.getConfig().getCatXpCapacity() : 500;
    }

    // ---- Constructor ----

    public CatGuardianModule() {
        super(
                "cat_guardian",
                "Cat Guardian",
                "Adds cat food bowls and feeding stations. Fed cats actively guard your base against hostile mobs.",
                CatGuardianConfig::new
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
                CAT_BOWL_ITEM, CAT_FEEDING_STATION_ITEM,
                CAT_ARMOR_IRON, CAT_ARMOR_GOLD, CAT_ARMOR_DIAMOND, CAT_ARMOR_NETHERITE);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Cat Guardian module initialized");
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new CatGuardianRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyCatGuardianRecipes(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> mergedRecipes = new LinkedHashMap<>();
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            mergedRecipes.put(recipeHolder.id(), recipeHolder);
        }

        addCatShapedRecipe(mergedRecipes, "cat_armor_iron", CAT_ARMOR_IRON.get(), Items.IRON_INGOT);
        addCatShapedRecipe(mergedRecipes, "cat_armor_gold", CAT_ARMOR_GOLD.get(), Items.GOLD_INGOT);
        addCatShapedRecipe(mergedRecipes, "cat_armor_diamond", CAT_ARMOR_DIAMOND.get(), Items.DIAMOND);
        addCatShapedRecipe(mergedRecipes, "cat_armor_netherite", CAT_ARMOR_NETHERITE.get(), Items.NETHERITE_INGOT);

        // Cat bowl: smooth_stone U-shape
        addShapedRecipe(mergedRecipes, "cat_bowl",
                Map.of('S', Ingredient.of(Items.SMOOTH_STONE)),
                CraftingBookCategory.MISC, new ItemStack(CAT_BOWL_ITEM.get()),
                "S S", "SSS");

        // Cat feeding station: glass_pane top/sides, cauldron center, smooth_stone bottom
        addShapedRecipe(mergedRecipes, "cat_feeding_station",
                Map.of('G', Ingredient.of(Items.GLASS_PANE),
                       'C', Ingredient.of(Items.CAULDRON),
                       'S', Ingredient.of(Items.SMOOTH_STONE)),
                CraftingBookCategory.MISC, new ItemStack(CAT_FEEDING_STATION_ITEM.get()),
                "GGG", "GCG", "SSS");

        recipeManager.replaceRecipes(mergedRecipes.values());
    }

    private void addCatShapedRecipe(Map<ResourceLocation, RecipeHolder<?>> recipes, String name, Item resultItem, Item material) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, name);
        Map<Character, Ingredient> key = Map.of(
                'S', Ingredient.of(Items.ARMADILLO_SCUTE),
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

    private final class CatGuardianRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private CatGuardianRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyCatGuardianRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_cat_guardian_recipes";
        }
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CAT_FEEDING_STATION_BE.get(),
                (be, side) -> side == Direction.DOWN ? be.getLootInventory() : be.getInventory()
        );
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenCatInventoryPacket.TYPE, OpenCatInventoryPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> {
                if (!isModuleEnabled()) {
                    return;
                }
                ServerPlayer player = (ServerPlayer) ctx.player();
                Entity entity = player.level().getEntity(packet.entityId());
                if (!(entity instanceof Cat cat)) {
                    return;
                }
                if (!cat.isTame() || !Objects.equals(cat.getOwnerUUID(), player.getUUID())) {
                    return;
                }
                player.openMenu(
                    new SimpleMenuProvider(
                        (id, inv, p) -> new CatInventoryMenu(id, inv, cat),
                        cat.getName()
                    ),
                    buf -> buf.writeInt(cat.getId())
                );
            })
        );

        event.registrar("1").playToClient(SyncCatInventoryPacket.TYPE, SyncCatInventoryPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> CatGuardianClientEvents.handleSyncCatInventory(packet))
        );

        event.registrar("1").playToClient(net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket.TYPE,
            net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> CatGuardianClientEvents.handleSyncCatTarget(packet))
        );

        event.registrar("1").playToServer(RequestCatGlowPacket.TYPE, RequestCatGlowPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> {
                if (!isModuleEnabled()) {
                    return;
                }
                ServerPlayer player = (ServerPlayer) ctx.player();
                BlockPos bowlPos = packet.bowlPos();
                if (player.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY() + 0.5, bowlPos.getZ() + 0.5) > 64.0 * 64.0) {
                    return;
                }
                if (!(player.level().getBlockEntity(bowlPos) instanceof AbstractCatBowlBlockEntity bowl)) {
                    return;
                }
                ServerLevel serverLevel = (ServerLevel) player.level();
                int durationTicks = getConfig().getGlowDurationTicks();
                for (UUID catUUID : bowl.getAssociatedCats()) {
                    Entity entity = serverLevel.getEntity(catUUID);
                    if (entity instanceof Cat cat && cat.isAlive()) {
                        MobEffectInstance existing = cat.getEffect(MobEffects.GLOWING);
                        if (existing == null || existing.getDuration() < 200) {
                            cat.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0, false, false));
                        }
                    }
                }
            })
        );
    }

    private static void broadcastArmorSync(Cat cat) {
        ItemStack armor = cat.getData(CAT_INVENTORY.get()).getArmor();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat, new SyncCatInventoryPacket(cat.getId(), armor));
    }

    // ---- Cat join level — inject guard target goal + restore armor attribute ----

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }

        // Allow cats to navigate through water (costly but not impassable)
        cat.setPathfindingMalus(PathType.WATER, 8.0f);
        cat.setPathfindingMalus(PathType.WATER_BORDER, 4.0f);

        boolean alreadyAdded = cat.targetSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof CatGuardTargetGoal);
        if (!alreadyAdded) {
            cat.targetSelector.addGoal(1, new CatGuardTargetGoal(cat));
            boostAttackGoalPriority(cat);
        }

        // Boost follow-range for pathfinding in complex indoor environments (doubles A* node budget)
        var followRangeAttr = cat.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (followRangeAttr != null && !followRangeAttr.hasModifier(GUARDIAN_FOLLOW_RANGE_ID)) {
            followRangeAttr.addPermanentModifier(new AttributeModifier(
                    GUARDIAN_FOLLOW_RANGE_ID, 16.0, AttributeModifier.Operation.ADD_VALUE));
        }

        // Raise step height to 1.5 so cats actually climb the 1.5-block ledges the pathfinder
        // considers walkable (the maxUpStep mixin is best-effort; the attribute is authoritative
        // and is read by both the WalkNodeEvaluator and the movement code). Vanilla cats are 0.6.
        var stepHeightAttr = cat.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeightAttr != null && !stepHeightAttr.hasModifier(GUARDIAN_STEP_HEIGHT_ID)) {
            stepHeightAttr.addPermanentModifier(new AttributeModifier(
                    GUARDIAN_STEP_HEIGHT_ID, 0.9, AttributeModifier.Operation.ADD_VALUE));
        }

        // Restore armor attack bonus after chunk load / dimension change
        CatInventoryData invData = cat.getData(CAT_INVENTORY.get());
        ItemStack armor = invData.getArmor();
        if (!armor.isEmpty()) {
            applyArmorAttribute(cat, armor);
            broadcastArmorSync(cat);
        }

        // Bowl-assigned cats should not teleport to their owner or switch targets
        if (cat.getData(CAT_BOWL_POS.get()) != Long.MIN_VALUE) {
            suppressFollowingBehaviors(cat);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Cat cat)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ItemStack armor = cat.getData(CAT_INVENTORY.get()).getArmor();
        if (!armor.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new SyncCatInventoryPacket(cat.getId(), armor));
        }
        // Resync current combat target so the goggles overlay shows immediately when a player
        // approaches a cat that is already fighting.
        LivingEntity target = cat.getTarget();
        if (target != null && target.isAlive() && isGuardianCat(cat)) {
            PacketDistributor.sendToPlayer(player,
                    new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket(
                            cat.getId(), target.getId()));
        }
    }

    @SubscribeEvent
    public void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getParentA() instanceof Cat parentA)) {
            return;
        }
        if (!(event.getParentB() instanceof Cat parentB)) {
            return;
        }
        if (!(event.getChild() instanceof Cat baby)) {
            return;
        }

        UUID ownerA = parentA.isTame() ? parentA.getOwnerUUID() : null;
        UUID ownerB = parentB.isTame() ? parentB.getOwnerUUID() : null;

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

        baby.setTame(true, false);
        baby.setOwnerUUID(chosenOwner);
    }

    // ---- Cat tick logic ----

    @SubscribeEvent
    public void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Cat cat) || cat.level().isClientSide()) {
            return;
        }
        // Strip FollowOwnerGoal BEFORE the goal selector runs this tick, so an associated cat
        // can never teleport to a far-away owner (doing this in Post left a one-tick window in
        // which the goal could still fire — e.g. while a cat was returning to its station).
        suppressOwnerFollow(cat);
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }
        if (cat.level().isClientSide()) {
            return;
        }
        if (!cat.isTame() || cat.getOwnerUUID() == null) {
            return;
        }

        // Per-tick water steering: ground navigation can't path through water and the current
        // sweeps the cat away, so manually swim toward the goal (dive for underwater targets).
        steerThroughWater(cat);

        // When the ground pathfinder gives up before a climbable ledge, nudge the cat toward
        // its goal so it walks into the ledge — then the jump assist below lifts it over.
        driveTowardGoalWhenStuck(cat);

        // Per-tick ledge-jump assist so cats reliably clear ~1.5-block steps.
        assistLedgeJump(cat);

        if (cat.tickCount % 10 != 0) {
            return;
        }

        tickCat(cat);
    }

    /**
     * Drives a guardian cat horizontally toward its goal (current target, else its bowl) when the
     * ground navigation has given up — typically because the only way to the goal is up a ledge the
     * pathfinder won't plan. This walks the cat into the ledge so {@link #assistLedgeJump} can lift
     * it over. Does nothing while navigation is actively running, or when the cat is already near
     * the goal, or when the goal is directly above (no horizontal direction to push).
     */
    private void driveTowardGoalWhenStuck(Cat cat) {
        if (!isGuardianCat(cat) || !cat.onGround() || cat.isInWater()) {
            return;
        }
        if (!cat.getNavigation().isDone()) {
            return; // navigation is handling movement
        }
        net.minecraft.world.phys.Vec3 goal = currentGoalPos(cat);
        if (goal == null) {
            return;
        }
        double dx = goal.x - cat.getX();
        double dz = goal.z - cat.getZ();
        double dy = goal.y - cat.getY();
        if (dx * dx + dy * dy + dz * dz < 4.0) {
            return; // close enough — let normal idle/attack logic take over
        }

        net.minecraft.world.level.pathfinder.Path path = goalPath(cat, goal.x, goal.y, goal.z);
        // Only drive if the path's closest reachable point is within jump range of the goal (i.e. a
        // climbable ledge). If it ends far from the goal — a truly unreachable target (mob on the
        // ceiling) OR a bowl walled off from this side — don't bee-line the cat into the obstacle;
        // let the blacklist switch the target / let navigation take the real route.
        if (!endNodeNear(path, goal, 2.5)) {
            return;
        }

        // Follow the PATH's next node, not the straight line to the goal — path and target often
        // diverge (the route goes around obstacles, up a side ledge, etc.).
        net.minecraft.world.phys.Vec3 dir = pathDir(cat, path);
        if (dir == null) {
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz < 0.6) {
                return; // no path step and goal is straight up/down — a nudge can't help
            }
            dir = new net.minecraft.world.phys.Vec3(dx / horiz, 0, dz / horiz);
        }
        net.minecraft.world.phys.Vec3 v = cat.getDeltaMovement();
        cat.setDeltaMovement(dir.x * 0.13, v.y, dir.z * 0.13);
        cat.getLookControl().setLookAt(cat.getX() + dir.x, cat.getEyeY(), cat.getZ() + dir.z);
        cat.hasImpulse = true;
    }

    /** The cat's current movement goal: its combat target if any, else its bowl. Null if neither. */
    private net.minecraft.world.phys.Vec3 currentGoalPos(Cat cat) {
        LivingEntity target = cat.getTarget();
        if (target != null) {
            return target.position();
        }
        long bowlLong = cat.getData(CAT_BOWL_POS.get());
        if (bowlLong != Long.MIN_VALUE) {
            BlockPos b = BlockPos.of(bowlLong);
            return new net.minecraft.world.phys.Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        }
        return null;
    }

    /** Throttled path toward a goal position (A* is costly), recomputed at most every 10 ticks. */
    private net.minecraft.world.level.pathfinder.Path goalPath(Cat cat, double gx, double gy, double gz) {
        UUID uid = cat.getUUID();
        int now = cat.tickCount;
        Integer last = catGoalPathTick.get(uid);
        if (last == null || now - last >= 10) {
            catGoalPath.put(uid, cat.getNavigation().createPath(gx, gy, gz, 0));
            catGoalPathTick.put(uid, now);
        }
        return catGoalPath.get(uid);
    }

    /** True if the path's closest reachable end node is within {@code maxDist} of the goal. */
    private boolean endNodeNear(net.minecraft.world.level.pathfinder.Path path,
                                net.minecraft.world.phys.Vec3 goal, double maxDist) {
        if (path == null) {
            return false;
        }
        net.minecraft.world.level.pathfinder.Node end = path.getEndNode();
        if (end == null) {
            return false;
        }
        double dx = (end.x + 0.5) - goal.x;
        double dy = end.y - goal.y;
        double dz = (end.z + 0.5) - goal.z;
        return dx * dx + dy * dy + dz * dz <= maxDist * maxDist;
    }

    /** Horizontal direction toward the first path node meaningfully ahead of the cat; null if none. */
    private net.minecraft.world.phys.Vec3 pathDir(Cat cat, net.minecraft.world.level.pathfinder.Path path) {
        if (path == null) {
            return null;
        }
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            net.minecraft.world.level.pathfinder.Node n = path.getNode(i);
            double dx = (n.x + 0.5) - cat.getX();
            double dz = (n.z + 0.5) - cat.getZ();
            if (dx * dx + dz * dz > 0.25) {
                return new net.minecraft.world.phys.Vec3(dx, 0, dz).normalize();
            }
        }
        return null;
    }

    /** Direction the cat should currently head toward its goal, preferring pathfinding over bee-line. */
    private net.minecraft.world.phys.Vec3 goalDir(Cat cat) {
        net.minecraft.world.phys.Vec3 d = pathDir(cat, cat.getNavigation().getPath());
        if (d != null) {
            return d;
        }
        net.minecraft.world.phys.Vec3 goal = currentGoalPos(cat);
        if (goal == null) {
            return null;
        }
        d = pathDir(cat, goalPath(cat, goal.x, goal.y, goal.z));
        if (d != null) {
            return d;
        }
        double dx = goal.x - cat.getX();
        double dz = goal.z - cat.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return horiz > 0.6 ? new net.minecraft.world.phys.Vec3(dx / horiz, 0, dz / horiz) : null;
    }

    /**
     * Makes a guardian cat hop when it is actively moving and bumps into a ledge. Auto-step
     * (step height) and the ground pathfinder only reliably handle ~1-block rises, so without
     * this cats only get over 1.5-block steps by accident (e.g. when shoved). The hop reaches
     * ~1.5 blocks AND carries a forward impulse toward the path/target, so each attempt actually
     * lands the cat on the ledge instead of bouncing in place — failed hops simply retry on the
     * next ground tick until it gets up.
     */
    private void assistLedgeJump(Cat cat) {
        if (!isGuardianCat(cat)) {
            return;
        }
        if (!cat.onGround() || !cat.horizontalCollision || cat.isInWater()) {
            return;
        }
        boolean wantsToMove = cat.getTarget() != null
                || cat.getData(CAT_RETURNING.get())
                || cat.getData(CAT_FLEEING.get())
                || !cat.getNavigation().isDone();
        if (!wantsToMove) {
            return;
        }

        // Hop along the PATHFINDING direction (next path node), not the bee-line to the target —
        // the wall has killed the cat's horizontal velocity, so re-supply forward momentum that
        // follows the actual route (which often diverges from the straight line to the target).
        net.minecraft.world.phys.Vec3 dir = goalDir(cat);
        double vx = cat.getDeltaMovement().x;
        double vz = cat.getDeltaMovement().z;
        if (dir != null) {
            vx = dir.x * 0.28;
            vz = dir.z * 0.28;
        }
        cat.setDeltaMovement(vx, 0.5, vz); // peak ≈ 1.5 blocks, with forward carry onto the ledge
        cat.hasImpulse = true;
    }

    /**
     * Manually steers a guardian cat through water toward its goal — ground navigation is useless
     * in water, and flowing water otherwise sweeps the cat away. Dives toward an underwater target,
     * otherwise swims at/near the surface toward the target or back to its bowl, with enough force
     * to beat the current.
     */
    private void steerThroughWater(Cat cat) {
        if (!isGuardianCat(cat) || !cat.isInWater()) {
            return;
        }
        LivingEntity target = cat.getTarget();
        net.minecraft.world.phys.Vec3 goalPos = null;
        boolean diveTarget = false;
        if (target != null) {
            goalPos = target.getEyePosition();
            diveTarget = target.isInWater() || target.isUnderWater();
        } else {
            long bowlLong = cat.getData(CAT_BOWL_POS.get());
            if (bowlLong != Long.MIN_VALUE) {
                BlockPos b = BlockPos.of(bowlLong);
                if (cat.distanceToSqr(b.getX() + 0.5, b.getY(), b.getZ() + 0.5) > 4.0) {
                    goalPos = new net.minecraft.world.phys.Vec3(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
                }
            }
        }
        if (goalPos == null) {
            return;
        }
        cat.getNavigation().stop(); // ground nav can't help in water; take manual control

        net.minecraft.world.phys.Vec3 v = cat.getDeltaMovement();
        if (diveTarget) {
            // Dive in 3D toward the submerged target (Y descent overrides the surface buoyancy).
            cat.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false));
            net.minecraft.world.phys.Vec3 dir = goalPos.subtract(cat.position()).normalize();
            cat.setDeltaMovement(
                    v.x * 0.6 + dir.x * 0.06,
                    v.y * 0.6 + dir.y * 0.08,
                    v.z * 0.6 + dir.z * 0.06);
        } else {
            // Swim at/near the surface toward the goal, strongly enough to overcome the current
            // (water flow pushes ~0.014/tick; 0.11 dominates it).
            net.minecraft.world.phys.Vec3 flat =
                    new net.minecraft.world.phys.Vec3(goalPos.x - cat.getX(), 0, goalPos.z - cat.getZ());
            if (flat.lengthSqr() > 1.0E-3) {
                flat = flat.normalize();
                cat.setDeltaMovement(flat.x * 0.11, Math.max(v.y, 0.04), flat.z * 0.11);
            }
        }
    }

    private void tickCat(Cat cat) {
        CatGuardianConfig config = getConfig();

        long bowlPosLong = cat.getData(CAT_BOWL_POS.get());
        boolean hasBowl = bowlPosLong != Long.MIN_VALUE;

        UUID uid = cat.getUUID();
        if (!hasBowl) {
            catNavRefPos.remove(uid);
            catNavRefAge.remove(uid);
            tryAutoAssociate(cat, config.getAutoAssociateRadius());
            return;
        }

        // Let guardian cats path over fences (1.5 high) — their raised step height + jump assist
        // climb them physically, and otherwise the pathfinder rejects fence nodes outright, hiding
        // shorter routes through the base. Guardian-only so penned pet cats stay contained.
        if (cat.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation gpn) {
            gpn.setCanWalkOverFences(true);
        }

        // Stuck detection: every 60 ticks check net displacement while nav is active.
        // Vec3-based check catches oscillation between two adjacent block positions
        // (blockPosition() equality misses that case because the int pos alternates).
        if (cat.getNavigation().isInProgress()) {
            int age = catNavRefAge.merge(uid, 1, Integer::sum);
            net.minecraft.world.phys.Vec3 refPos = catNavRefPos.get(uid);
            net.minecraft.world.phys.Vec3 curPos = cat.position();
            if (refPos == null) {
                catNavRefPos.put(uid, curPos);
                catNavRefAge.put(uid, 0);
            } else if (age >= 60) {
                if (curPos.distanceToSqr(refPos) < 1.0) {
                    // Moved less than 1 block in 60 ticks — spinning or trapped
                    cat.getNavigation().stop();
                    LivingEntity stuckTarget = cat.getTarget();
                    boolean aquatic = stuckTarget != null
                            && (stuckTarget.isInWater() || stuckTarget.isUnderWater());
                    // Aquatic targets are pursued by manual dive steering, not navigation —
                    // keep the target. Only abandon genuinely-stuck land targets.
                    if (!aquatic) {
                        cat.setTarget(null);
                    }
                }
                catNavRefPos.put(uid, curPos);
                catNavRefAge.put(uid, 0);
            }
        } else {
            catNavRefAge.remove(uid);
            catNavRefPos.remove(uid);
        }

        suppressFollowingBehaviors(cat);

        // Water breathing while pursuing an aquatic target
        LivingEntity currentTarget = cat.getTarget();
        boolean targetUnderwater = currentTarget != null && (currentTarget.isInWater() || currentTarget.isUnderWater());
        if (targetUnderwater) {
            cat.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, false, false));
        }

        // When in water without an active aquatic target, push the cat upward so it can
        // exit the water. RandomSwimmingGoal was removed because it fights directed navigation
        // (priority 0 vs GuardianCatAttackGoal priority 4); FloatGoal (vanilla) handles
        // idle floating; directed nav handles pursuit paths through water.
        if (cat.isInWater() && !targetUnderwater) {
            cat.setDeltaMovement(
                    cat.getDeltaMovement().x,
                    Math.max(cat.getDeltaMovement().y, 0.12),
                    cat.getDeltaMovement().z);
        }

        BlockPos bowlPos = BlockPos.of(bowlPosLong);

        // Low-health flee: at <20% HP the cat enters an ABSOLUTE retreat — it ignores all
        // mobs and runs home, heals at base, and only resumes guarding once it has recovered
        // above 40% HP (hysteresis prevents yo-yoing in and out of combat at the edge).
        float healthPct = cat.getHealth() / cat.getMaxHealth();
        boolean fleeing = cat.getData(CAT_FLEEING.get());
        if (!fleeing && healthPct < 0.20f) {
            fleeing = true;
            cat.setData(CAT_FLEEING.get(), true);
        }
        if (fleeing) {
            cat.setTarget(null); // ignore all mobs while fleeing
            cat.setData(CAT_RETURNING.get(), false);
            catReturningAge.remove(uid);
            double distSqToBowl = cat.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
            boolean atBase = distSqToBowl <= 16.0;
            if (atBase) {
                // Heal at base; resume duty once safely recovered
                cat.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
                if (healthPct > 0.40f) {
                    cat.setData(CAT_FLEEING.get(), false);
                } else if (!cat.isOrderedToSit()) {
                    cat.setOrderedToSit(true);
                }
            } else {
                if (cat.isOrderedToSit()) {
                    cat.setOrderedToSit(false);
                }
                cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 1.0);
            }
            // Keep fed-state ticking down while fleeing, then skip all normal duty logic
            int fedWhileFleeing = cat.getData(CAT_FED_TICKS.get());
            if (fedWhileFleeing > 0) {
                cat.setData(CAT_FED_TICKS.get(), Math.max(0, fedWhileFleeing - 10));
            }
            return;
        }

        // Ordinary post-combat return: navigate back to the bowl. Interruptible — the target
        // goal re-engages nearby threats (see CatGuardTargetGoal.canUse). Clears when within
        // ~4 blocks of the bowl, or after a time cap so a failed path never strands the cat.
        if (cat.getData(CAT_RETURNING.get())) {
            if (cat.isOrderedToSit()) {
                cat.setOrderedToSit(false);
            }
            int returningAge = catReturningAge.merge(uid, 10, Integer::sum); // tickCat runs every 10 ticks
            double distSqToBowl = cat.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
            if (distSqToBowl <= 16.0 || returningAge >= 200) {
                cat.setData(CAT_RETURNING.get(), false);
                catReturningAge.remove(uid);
            } else {
                cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 1.0);
            }
        } else {
            catReturningAge.remove(uid);
        }

        // Decrement fed ticks regardless of other state
        int fedTicks = cat.getData(CAT_FED_TICKS.get());
        if (fedTicks > 0) {
            cat.setData(CAT_FED_TICKS.get(), Math.max(0, fedTicks - 10));
            // Idle: sit near bowl; if drifted away (e.g. stepped onto fence/wall), navigate back first
            if (!cat.getData(CAT_RETURNING.get()) && cat.getTarget() == null) {
                double idleDistSq = cat.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
                if (idleDistSq > 4.0) {
                    // Far from bowl — navigate home rather than sitting in place
                    if (cat.isOrderedToSit()) {
                        cat.setOrderedToSit(false);
                    }
                    if (cat.getNavigation().isDone()) {
                        cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 0.8);
                    }
                } else if (!cat.isOrderedToSit()) {
                    cat.setOrderedToSit(true);
                }
            }
            return;
        }

        // Try to eat when unfed
        AbstractCatBowlBlockEntity bowl = getBowlEntity(cat, bowlPos);
        if (bowl == null) {
            return;
        }
        if (!bowl.hasFish()) {
            return;
        }

        double distSq = cat.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);

        if (distSq > 4.0 && !cat.isOrderedToSit()) {
            if (cat.getNavigation().isDone()) {
                cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 0.8);
            }
        } else if (distSq <= 4.0) {
            var fish = bowl.takeFish();
            if (!fish.isEmpty()) {
                cat.setData(CAT_FED_TICKS.get(), config.getFedDurationTicks());
                cat.playSound(SoundEvents.GENERIC_EAT, 0.5F, cat.getVoicePitch());
                cat.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                if (bowl instanceof CatFeedingStationBlockEntity station) {
                    transferLootToStation(cat, station);
                }
            }
        }
    }

    private void transferLootToStation(Cat cat, CatFeedingStationBlockEntity station) {
        CatInventoryData catInv = cat.getData(CAT_INVENTORY.get());
        var catLoot = catInv.getInventory();
        var stationLoot = station.getLootInventory();
        for (int slot = CatInventoryData.LOOT_START; slot < CatInventoryData.TOTAL_SLOTS; slot++) {
            ItemStack stack = catLoot.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int ss = 0; ss < stationLoot.getSlots(); ss++) {
                stack = stationLoot.insertItem(ss, stack, false);
                if (stack.isEmpty()) {
                    break;
                }
            }
            catLoot.setStackInSlot(slot, stack);
        }

        // XP: cat buffer → station counter
        int catXp = cat.getData(CAT_XP.get());
        if (catXp > 0) {
            int stationCap = getConfig().getStationXpCapacity();
            int canStore = stationCap - station.getStoredXp();
            int toTransfer = Math.min(catXp, canStore);
            if (toTransfer > 0) {
                station.addStoredXp(toTransfer);
                cat.setData(CAT_XP.get(), catXp - toTransfer);
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

    private AbstractCatBowlBlockEntity getBowlEntity(Cat cat, BlockPos bowlPos) {
        var be = cat.level().getBlockEntity(bowlPos);
        if (be instanceof AbstractCatBowlBlockEntity bowl) {
            return bowl;
        }
        cat.setData(CAT_BOWL_POS.get(), Long.MIN_VALUE);
        return null;
    }

    private void tryAutoAssociate(Cat cat, double autoRadius) {
        BlockPos catPos = cat.blockPosition();
        int r = (int) Math.ceil(autoRadius);
        double radiusSq = autoRadius * autoRadius;

        for (BlockPos checkPos : BlockPos.betweenClosed(
                catPos.offset(-r, -r, -r), catPos.offset(r, r, r))) {
            if (!(cat.level().getBlockEntity(checkPos) instanceof AbstractCatBowlBlockEntity bowl) || !bowl.canAddCat(cat.getUUID())) {
                continue;
            }
            double distSq = cat.distanceToSqr(
                    checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
            if (distSq <= radiusSq) {
                cat.setData(CAT_BOWL_POS.get(), checkPos.asLong());
                bowl.addCat(cat.getUUID());
                break;
            }
        }
    }

    // ---- Cat armor equip / unequip ----

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Cat cat)) {
            return;
        }
        if (!cat.isTame() || !Objects.equals(cat.getOwnerUUID(), event.getEntity().getUUID())) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldItem = event.getItemStack();
        CatInventoryData invData = cat.getData(CAT_INVENTORY.get());
        ItemStack currentArmor = invData.getArmor();

        if (heldItem.getItem() instanceof CatArmorItem) {
            if (!event.getLevel().isClientSide()) {
                if (!currentArmor.isEmpty() && !player.getInventory().add(currentArmor)) {
                    player.drop(currentArmor, false);
                }
                ItemStack newArmor = heldItem.copyWithCount(1);
                invData.setArmor(newArmor);
                applyArmorAttribute(cat, newArmor);
                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }
                broadcastArmorSync(cat);
            }
            event.setCanceled(true);
        } else if (heldItem.isEmpty() && player.isShiftKeyDown() && !currentArmor.isEmpty()) {
            if (!event.getLevel().isClientSide()) {
                if (!player.getInventory().add(currentArmor)) {
                    player.drop(currentArmor, false);
                }
                invData.setArmor(ItemStack.EMPTY);
                removeArmorAttribute(cat);
                broadcastArmorSync(cat);
            }
            event.setCanceled(true);
        } else if (isGuardianCat(cat)) {
            // Prevent vanilla sit/stand toggle on guardian cats; client uses alt-click to toggle overlays.
            // Server can't detect the Alt key, so we cancel all non-armor interact on guardian cats.
            // Cancelling server-side (not client) avoids the custom_payload EncoderException
            // that occurs when the integrated server tries to sync state after the interact.
            event.setCanceled(true);
        }
    }

    // ---- Cat armor damage reduction ----

    @SubscribeEvent
    public void onCatHurt(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }
        CatInventoryData invData = cat.getData(CAT_INVENTORY.get());
        ItemStack armor = invData.getArmor();
        if (!(armor.getItem() instanceof CatArmorItem)) {
            return;
        }

        // Armor absorbs 100% of damage; each damage point drains 1 durability
        float absorbed = event.getNewDamage();
        event.setNewDamage(0f);

        armor.hurtAndBreak(Math.max(1, (int) Math.ceil(absorbed)), cat, net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (armor.isEmpty()) {
            invData.setArmor(ItemStack.EMPTY);
            removeArmorAttribute(cat);
        }

        // Retaliation: if hit by a hostile mob, immediately switch target to attacker
        if (!cat.level().isClientSide() && isGuardianCat(cat)) {
            Entity direct = event.getSource().getDirectEntity();
            Entity indirect = event.getSource().getEntity();
            LivingEntity attacker = direct instanceof Monster m ? m
                    : indirect instanceof Monster m2 ? m2 : null;
            if (attacker != null && !attacker.isDeadOrDying()
                    && isWithinGuardZone(cat, attacker.getX(), attacker.getY(), attacker.getZ(),
                            TARGET_ZONE_BUFFER)) {
                cat.setTarget(attacker);
                cat.targetSelector.getAvailableGoals().stream()
                        .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .filter(g -> g instanceof CatGuardTargetGoal)
                        .findFirst()
                        .ifPresent(g -> ((CatGuardTargetGoal) g).retaliateAgainst(attacker));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                        new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket(
                                cat.getId(), attacker.getId()));
            }
        }
    }

    /**
     * Credits the cat's owner as the attacker on any mob a guardian cat damages.
     *
     * <p>Vanilla only drops experience (and player-conditioned loot) when the victim was recently
     * hurt by a player ({@code lastHurtByPlayerTime > 0}). A mob killed purely by a cat therefore
     * drops no XP and {@link #onExperienceDrop} never fires. Marking the owner as the last player
     * attacker makes vanilla drop XP normally, which is then redirected into the cat's buffer.
     */
    @SubscribeEvent
    public void onMobDamagedByCat(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Cat) {
            return; // cat-as-victim is handled by onCatHurt
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity indirect = event.getSource().getEntity();
        Cat cat = direct instanceof Cat c ? c
                : indirect instanceof Cat c2 ? c2 : null;
        if (cat == null || !cat.isTame() || !isGuardianCat(cat)) {
            return;
        }
        if (cat.getOwner() instanceof Player owner) {
            victim.setLastHurtByPlayer(owner);
        }
        // Pre-record XP redirection so it is set before death regardless of event ordering.
        pendingXpCapture.put(victim.getId(), cat.getId());

        // Creepers fear cats — give that a real consequence: a guardian cat one-shots a creeper,
        // killing it before its fuse can ignite (no explosion). Loot/XP still drop (owner credited).
        if (victim instanceof net.minecraft.world.entity.monster.Creeper) {
            event.setNewDamage(Math.max(event.getNewDamage(), victim.getHealth() + 1000.0f));
        }
    }

    // ---- Cat loot collection ----

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity indirect = event.getSource().getEntity();
        Cat cat = null;
        if (direct instanceof Cat c && c.isTame() && c.getData(CAT_BOWL_POS.get()) != Long.MIN_VALUE) {
            cat = c;
        } else if (indirect instanceof Cat c && c.isTame() && c.getData(CAT_BOWL_POS.get()) != Long.MIN_VALUE) {
            cat = c;
        }
        if (cat == null) {
            return;
        }

        // Record for XP redirection (consumed by onExperienceDrop in the same tick)
        pendingXpCapture.put(event.getEntity().getId(), cat.getId());

        CatInventoryData catInv = cat.getData(CAT_INVENTORY.get());
        var lootHandler = catInv.getInventory();

        Iterator<net.minecraft.world.entity.item.ItemEntity> iter = event.getDrops().iterator();
        while (iter.hasNext()) {
            net.minecraft.world.entity.item.ItemEntity itemEntity = iter.next();
            ItemStack drop = itemEntity.getItem().copy();
            for (int slot = CatInventoryData.LOOT_START; slot < CatInventoryData.TOTAL_SLOTS; slot++) {
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
        Integer catEntityId = pendingXpCapture.remove(event.getEntity().getId());
        if (catEntityId == null) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(serverLevel.getEntity(catEntityId) instanceof Cat cat)) {
            return;
        }
        int xp = event.getDroppedExperience();
        int capacity = getConfig().getCatXpCapacity();
        int current = cat.getData(CAT_XP.get());
        int canAbsorb = capacity - current;
        if (canAbsorb <= 0) {
            return;
        }
        int absorbed = Math.min(xp, canAbsorb);
        cat.setData(CAT_XP.get(), current + absorbed);
        event.setDroppedExperience(xp - absorbed);
    }

    @SubscribeEvent
    public void onCatDeath(LivingDeathEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }
        if (!cat.isTame()) {
            return;
        }
        if (!(cat.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long bowlLong = cat.getData(CAT_BOWL_POS.get());
        if (bowlLong == Long.MIN_VALUE) {
            return;
        }
        BlockPos bowlPos = BlockPos.of(bowlLong);
        if (serverLevel.getBlockEntity(bowlPos) instanceof AbstractCatBowlBlockEntity bowl) {
            bowl.removeCat(cat.getUUID());
        }
        cat.setData(CAT_BOWL_POS.get(), Long.MIN_VALUE);
    }

    // ---- Armor attribute helpers ----

    private void applyArmorAttribute(Cat cat, ItemStack armor) {
        var attrInstance = cat.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrInstance == null) {
            return;
        }
        attrInstance.removeModifier(ARMOR_MODIFIER_ID);
        if (armor.getItem() instanceof CatArmorItem catArmor) {
            attrInstance.addPermanentModifier(new AttributeModifier(
                    ARMOR_MODIFIER_ID, catArmor.getTier().getAttackBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void removeArmorAttribute(Cat cat) {
        var attrInstance = cat.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrInstance != null) {
            attrInstance.removeModifier(ARMOR_MODIFIER_ID);
        }
    }

    /**
     * Vanilla cats register LeapAtTargetGoal (prio 8) and OcelotAttackGoal (prio 9) BELOW
     * CatLieOnBedGoal (prio 5), so bed-lying blocks combat. Re-register the attack goals
     * at prio 3/4 so combat takes precedence while idle behaviour (bed, stroll) still works
     * when no target is present.
     */
    private static void boostAttackGoalPriority(Cat cat) {
        List<Goal> leapGoals = cat.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof LeapAtTargetGoal)
                .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .toList();
        // Remove vanilla attack goal; replace with wider-range guardian version
        cat.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof OcelotAttackGoal
                          || w.getGoal() instanceof GuardianCatAttackGoal)
                .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .toList()
                .forEach(cat.goalSelector::removeGoal);
        leapGoals.forEach(cat.goalSelector::removeGoal);
        leapGoals.forEach(g -> cat.goalSelector.addGoal(3, g));
        cat.goalSelector.addGoal(4, new GuardianCatAttackGoal(cat));
    }

    private static final class GuardianCatAttackGoal extends MeleeAttackGoal {
        GuardianCatAttackGoal(Cat cat) {
            super(cat, 1.0, true);
        }

        @Override
        protected boolean canPerformAttack(LivingEntity entity) {
            if (!isTimeToAttack()) {
                return false;
            }
            // Skip LOS for underwater targets — water always blocks rays, but the cat can reach them
            if (!entity.isInWater() && !entity.isUnderWater()
                    && !this.mob.getSensing().hasLineOfSight(entity)) {
                return false;
            }
            // Creepers are one-shot on hit, so keep that strictly point-blank (<1 block) — no
            // cheaty ranged kill; the cat must actually get right next to the creeper. Other mobs
            // keep the wider ~3.3-block reach (vs zombies etc.).
            double reach = entity instanceof net.minecraft.world.entity.monster.Creeper
                    ? 1.0
                    : this.mob.getBbWidth() * 2.0 + entity.getBbWidth() + 1.5;
            return this.mob.distanceToSqr(entity.getX(), entity.getY(), entity.getZ()) <= reach * reach;
        }
    }

    /**
     * Removes {@link FollowOwnerGoal} from an associated (guardian) cat. Runs every tick so a
     * guardian cat never teleports to a distant owner — the broader {@link #suppressFollowingBehaviors}
     * only runs every 10 ticks, which left a window for the teleport to fire. Non-associated pet
     * cats keep their normal follow/teleport behaviour.
     */
    private void suppressOwnerFollow(Cat cat) {
        if (!isGuardianCat(cat)) {
            return;
        }
        // Remove BOTH owner-teleport sources: FollowOwnerGoal (priority 6) and the panic goal
        // (TamableAnimal.TamableAnimalPanicGoal, priority 1) — the latter teleports a hurt cat to
        // a far-away owner mid-combat. Our own CAT_FLEEING logic replaces vanilla panic.
        cat.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof FollowOwnerGoal
                          || w.getGoal() instanceof net.minecraft.world.entity.ai.goal.PanicGoal)
                .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .toList()
                .forEach(cat.goalSelector::removeGoal);
    }

    private static void suppressFollowingBehaviors(Cat cat) {
        // Remove goals that would fight with guard-station behaviour or cause wandering
        cat.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof FollowOwnerGoal
                          || w.getGoal() instanceof WaterAvoidingRandomStrollGoal)
                .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .toList()
                .forEach(cat.goalSelector::removeGoal);
        cat.targetSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof HurtByTargetGoal)
                .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .toList()
                .forEach(cat.targetSelector::removeGoal);
    }

    /** Buffer (blocks) for target eligibility — avoids flicker at the boundary. */
    private static final double TARGET_ZONE_BUFFER = 4.0;
    /** Larger buffer for the cat's own travel — allows edge melee + brief boundary stepping. */
    private static final double CAT_ZONE_BUFFER = 10.0;

    /**
     * True if the given position is inside the cat's guard zone (bowl-centred, asymmetric
     * XZ/Y radius) plus the given hysteresis buffer. Used to bound both target eligibility and
     * how far the cat itself may travel while pursuing, so cats never wander out of their
     * assigned area (e.g. chasing a mob whose only path leads through a cave mouth outside the
     * radius). Returns false if the cat has no bowl.
     */
    private static boolean isWithinGuardZone(Cat cat, double x, double y, double z, double buffer) {
        long bowlLong = cat.getData(CAT_BOWL_POS.get());
        if (bowlLong == Long.MIN_VALUE) {
            return false;
        }
        BlockPos bowl = BlockPos.of(bowlLong);
        double radius = instance != null ? instance.getConfig().getGuardRadius() : 32.0;
        double radiusY = instance != null ? instance.getConfig().getGuardRadiusY() : 16.0;
        return Math.abs(x - (bowl.getX() + 0.5)) <= radius + buffer
                && Math.abs(z - (bowl.getZ() + 0.5)) <= radius + buffer
                && Math.abs(y - (bowl.getY() + 0.5)) <= radiusY + buffer;
    }

    // ---- CatGuardTargetGoal — proper AI target goal for guarding ----

    private static final class CatGuardTargetGoal extends TargetGoal {

        private final Cat cat;
        /** Counts 20-tick intervals where the target is alive but unreachable. */
        private int unreachableChecks = 0;
        /** Mob entity IDs that are temporarily blacklisted (value = game time when blacklist expires). */
        private final Map<Integer, Long> blockedTargets = new HashMap<>();
        private static final long BLACKLIST_TICKS = 1200L; // 60 seconds

        CatGuardTargetGoal(Cat cat) {
            super(cat, false, false);
            this.cat = cat;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!cat.isTame() || cat.getOwnerUUID() == null) {
                return false;
            }
            if (cat.getData(CAT_FLEEING.get())) {
                return false; // low health: ignore all mobs, flee to base
            }
            int fedTicks = cat.getData(CAT_FED_TICKS.get());
            if (fedTicks <= 0) {
                return false;
            }
            long bowlLong = cat.getData(CAT_BOWL_POS.get());
            if (bowlLong == Long.MIN_VALUE) {
                return false;
            }
            // If the cat has strayed outside its zone, commit to returning home before
            // acquiring new targets — prevents oscillating at a cave mouth / boundary where a
            // mob keeps pulling it back out. (The return path itself is unconstrained.)
            if (!isWithinGuardZone(cat, cat.getX(), cat.getY(), cat.getZ(), CAT_ZONE_BUFFER)) {
                return false;
            }
            boolean found = findAndSetTarget(BlockPos.of(bowlLong));
            if (found) {
                cat.setData(CAT_RETURNING.get(), false); // interrupt ordinary return to engage
            }
            return found;
        }

        @Override
        public boolean canContinueToUse() {
            int fedTicks = cat.getData(CAT_FED_TICKS.get());
            if (fedTicks <= 0) {
                return false;
            }
            LivingEntity target = cat.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            // Drop the target the moment it leaves the guard zone (e.g. wanders off or falls
            // into a ravine) so the cat returns to base instead of chasing it indefinitely.
            if (!isWithinGuardZone(cat, target.getX(), target.getY(), target.getZ(), TARGET_ZONE_BUFFER)) {
                return false;
            }
            // Bound the cat's OWN travel: if pursuit drags it too far from base (e.g. the only
            // path to an in-zone mob leads out through a cave mouth outside the radius),
            // blacklist that mob and head home rather than wandering off.
            if (!isWithinGuardZone(cat, cat.getX(), cat.getY(), cat.getZ(), CAT_ZONE_BUFFER)) {
                blacklist(target);
                return false;
            }

            // Every 20 ticks: if the cat is >4 blocks away and navigation is idle,
            // the target is probably unreachable. Allow 8 consecutive checks (~8 s)
            // so cats have time to navigate complex indoor paths (stairs, corridors)
            // without hanging on a truly unreachable mob for too long.
            // Aquatic targets: skip the navIdle check since swimming nav is slower.
            if (cat.tickCount % 20 == 0) {
                boolean tooFar = cat.distanceToSqr(target) > 16.0;
                boolean navIdle = cat.getNavigation().isDone();
                boolean targetInWater = target.isInWater();
                if (tooFar && navIdle && !targetInWater) {
                    if (++unreachableChecks >= 8) {
                        unreachableChecks = 0;
                        blacklist(target); // prevent immediately re-selecting this mob
                        long bowlLong = cat.getData(CAT_BOWL_POS.get());
                        LivingEntity alt = bowlLong != Long.MIN_VALUE
                                ? findAlternative(BlockPos.of(bowlLong), target) : null;
                        if (alt != null) {
                            this.targetMob = alt;
                            cat.setTarget(alt);
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                                    new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket(
                                            cat.getId(), alt.getId()));
                            return true;
                        }
                        return false;
                    }
                } else {
                    unreachableChecks = 0;
                }
            }
            return true;
        }

        void retaliateAgainst(LivingEntity attacker) {
            this.targetMob = attacker;
            this.unreachableChecks = 0;
            blockedTargets.remove(attacker.getId()); // retaliating overrides any blacklist
        }

        private boolean isBlocked(Monster m) {
            Long until = blockedTargets.get(m.getId());
            if (until == null) {
                return false;
            }
            if (cat.level().getGameTime() >= until) {
                blockedTargets.remove(m.getId());
                return false;
            }
            return true;
        }

        private void blacklist(LivingEntity target) {
            blockedTargets.put(target.getId(), cat.level().getGameTime() + BLACKLIST_TICKS);
        }

        private LivingEntity findAlternative(BlockPos bowlPos, LivingEntity excluded) {
            double radius = instance != null ? instance.getConfig().getGuardRadius() : 32.0;
            double radiusY = instance != null ? instance.getConfig().getGuardRadiusY() : 16.0;
            AABB searchBox = new AABB(bowlPos).inflate(radius, radiusY, radius);
            List<Monster> hostiles = cat.level().getEntitiesOfClass(
                    Monster.class, searchBox, m -> !m.isDeadOrDying() && m != excluded && !isBlocked(m));
            return hostiles.stream()
                    .min(Comparator.comparingDouble(cat::distanceToSqr))
                    .orElse(null);
        }

        @Override
        public void start() {
            unreachableChecks = 0;
            cat.setOrderedToSit(false);
            super.start();
            LivingEntity t = cat.getTarget();
            if (t != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                        new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket(
                                cat.getId(), t.getId()));
            }
        }

        @Override
        public void stop() {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                    new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket(
                            cat.getId(),
                            net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket.NO_TARGET));
            cat.setTarget(null);
            this.targetMob = null;
            // Always path back to bowl after losing a target (blacklist, mob death, low HP, etc.)
            if (cat.getData(CAT_BOWL_POS.get()) != Long.MIN_VALUE) {
                cat.setData(CAT_RETURNING.get(), true);
            }
        }

        private boolean findAndSetTarget(BlockPos bowlPos) {
            double radius = instance != null ? instance.getConfig().getGuardRadius() : 32.0;
            double radiusY = instance != null ? instance.getConfig().getGuardRadiusY() : 16.0;
            AABB searchBox = new AABB(bowlPos).inflate(radius, radiusY, radius);
            List<Monster> hostiles = cat.level().getEntitiesOfClass(
                    Monster.class, searchBox, m -> !m.isDeadOrDying() && !isBlocked(m));
            if (hostiles.isEmpty()) {
                return false;
            }

            LivingEntity nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (Monster m : hostiles) {
                double d = cat.distanceToSqr(m);
                if (d < nearestDistSq) {
                    nearestDistSq = d;
                    nearest = m;
                }
            }
            if (nearest == null) {
                return false;
            }
            this.targetMob = nearest;
            cat.setTarget(nearest);
            return true;
        }
    }
}
