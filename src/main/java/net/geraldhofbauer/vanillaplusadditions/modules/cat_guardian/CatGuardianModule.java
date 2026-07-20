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
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client.CatGuardianClientEvents;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config.CatGuardianConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item.CatArmorItem;
import net.geraldhofbauer.vanillaplusadditions.util.MobArmorEnchantments;
import net.minecraft.core.RegistryAccess;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatFeedingStationMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatInventoryMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathType;
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
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.FlyingFishModule;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

import java.util.*;
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
                    // Indirection via SableCatBlocks keeps Sable types out of this class's
                    // bytecode — a direct "new SableCatBowlBlock" here makes the verifier load
                    // Sable classes while LINKING this module class, crashing without Sable.
                    return net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable
                            .SableCatBlocks.createBowl(props);
                }
                return new CatBowlBlock(props);
            });

    public static final DeferredBlock<CatFeedingStationBlock> CAT_FEEDING_STATION =
            BLOCKS.register("cat_feeding_station", () -> {
                // No requiresCorrectToolForDrops(): the block sits in no mineable/pickaxe tag
                // (datapack tags are unreliable in this mod), so a "correct tool" could never
                // match — it would break yielding nothing and mine at hand speed forever.
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(3.5F, 12.0F)
                        .sound(SoundType.STONE);
                if (ModList.get().isLoaded("sable")) {
                    return net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable
                            .SableCatBlocks.createFeedingStation(props);
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

    /**
     * Low-health retreat flag: absolute, ignores all mobs and flees to base until healed.
     */
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

    // Accumulated ticks a cat has spent in the ordinary (interruptible) returning state.
    private final Map<UUID, Integer> catReturningAge = new HashMap<>();
    // Throttled cache of a cat's computed path toward its goal (for stuck-drive direction + reach).
    private final Map<UUID, net.minecraft.world.level.pathfinder.Path> catGoalPath = new HashMap<>();
    private final Map<UUID, Integer> catGoalPathTick = new HashMap<>();

    // Maps dead entity ID → guardian cat entity ID; used to redirect XP to the cat.
    // Populated in onLivingDrops, consumed in onExperienceDrop (same death tick).
    private final Map<Integer, Integer> pendingXpCapture = new HashMap<>();

    // Stuck detection: last sampled position + consecutive no-progress strikes.
    private final Map<UUID, net.minecraft.world.phys.Vec3> stuckSample = new HashMap<>();
    private final Map<UUID, Integer> stuckStrikes = new HashMap<>();

    // Chest/bed idle-goal handling for cats on duty (see CatSitGoalSuppressor).
    private final CatSitGoalSuppressor sitGoalSuppressor = new CatSitGoalSuppressor();

    public static double getAssociationRadius() {
        return instance != null ? instance.getConfig().getAssociationRadius() : 64.0D;
    }

    public static int getFedDurationTicks() {
        return instance != null ? instance.getConfig().getFedDurationTicks() : 6000;
    }

    /**
     * Returns true if this cat is registered as a guardian (has a bowl assignment).
     */
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

    public static boolean isModuleActive() {
        return instance != null && instance.isModuleEnabled();
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

    public static int getGlowDurationTicks() {
        return instance != null ? instance.getConfig().getGlowDurationTicks() : 600;
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
        event.addListener(new CatGuardianRecipeReloadListener(
                event.getServerResources().getRecipeManager(), event.getRegistryAccess()));
    }

    private void applyCatGuardianRecipes(RecipeManager recipeManager, RegistryAccess registryAccess) {
        Map<ResourceLocation, RecipeHolder<?>> mergedRecipes = new LinkedHashMap<>();
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            mergedRecipes.put(recipeHolder.id(), recipeHolder);
        }

        addCatShapedRecipe(mergedRecipes, "cat_armor_iron", CAT_ARMOR_IRON.get(), Items.IRON_INGOT, registryAccess);
        addCatShapedRecipe(mergedRecipes, "cat_armor_gold", CAT_ARMOR_GOLD.get(), Items.GOLD_INGOT, registryAccess);
        addCatShapedRecipe(mergedRecipes, "cat_armor_diamond", CAT_ARMOR_DIAMOND.get(), Items.DIAMOND, registryAccess);
        addCatShapedRecipe(mergedRecipes, "cat_armor_netherite", CAT_ARMOR_NETHERITE.get(), Items.NETHERITE_INGOT,
                registryAccess);

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

    private void addCatShapedRecipe(Map<ResourceLocation, RecipeHolder<?>> recipes, String name, Item resultItem,
                                    Item material, RegistryAccess registryAccess) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, name);
        Map<Character, Ingredient> key = Map.of(
                'S', Ingredient.of(Items.ARMADILLO_SCUTE),
                'X', Ingredient.of(material)
        );
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, "X  ", "XXX", "S S");
        ItemStack result = new ItemStack(resultItem);
        CatGuardianConfig config = getConfig();
        MobArmorEnchantments.applyDefaults(result, registryAccess,
                config.getDefaultUnbreakingLevel(),
                config.getDefaultSharpnessLevel(),
                config.getDefaultThornsLevel());
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.EQUIPMENT, pattern, result);
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
        private final RegistryAccess registryAccess;

        private CatGuardianRecipeReloadListener(RecipeManager recipeManager, RegistryAccess registryAccess) {
            this.recipeManager = recipeManager;
            this.registryAccess = registryAccess;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyCatGuardianRecipes(recipeManager, registryAccess), gameExecutor);
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
                    Component title = cat.getName().copy()
                            .append(Component.literal(" (" + player.getGameProfile().getName() + ")"));
                    player.openMenu(
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new CatInventoryMenu(id, inv, cat),
                                    title
                            ),
                            buf -> buf.writeInt(cat.getId())
                    );
                })
        );

        event.registrar("1").playToClient(SyncCatInventoryPacket.TYPE, SyncCatInventoryPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> CatGuardianClientEvents.handleSyncCatInventory(packet))
        );

        event.registrar("1").playToClient(SyncCatStatsPacket.TYPE, SyncCatStatsPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> CatGuardianClientEvents.handleSyncCatStats(packet))
        );

        event.registrar("1").playToClient(net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket.TYPE,
                net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> CatGuardianClientEvents.handleSyncCatTarget(packet))
        );

        event.registrar("1").playToClient(
                net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket.TYPE,
                net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> CatGuardianClientEvents.handleSyncCatPath(packet))
        );

        event.registrar("1").playToServer(RequestCatStatsPacket.TYPE, RequestCatStatsPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled()) {
                        return;
                    }
                    ServerPlayer player = (ServerPlayer) ctx.player();
                    if (!(player.level().getEntity(packet.catId()) instanceof Cat cat)) {
                        return;
                    }
                    if (player.distanceToSqr(cat) > 64.0 * 64.0) {
                        return;
                    }
                    PacketDistributor.sendToPlayer(player, new SyncCatInventoryPacket(cat.getId(),
                            cat.getData(CAT_INVENTORY.get()).getArmor()));
                    PacketDistributor.sendToPlayer(player, new SyncCatStatsPacket(cat.getId(),
                            cat.getData(CAT_XP.get()), getCatXpCapacity()));
                })
        );

        // Note: the old RequestCatGlowPacket round-trip (server-side GLOWING effect on all
        // associated cats, visible to every player) was replaced by a purely client-side glow —
        // see CatGuardianClientEvents.tickLocalGlow. Only the looking player sees it, and only
        // their own cats light up.
    }

    private static void broadcastArmorSync(Cat cat) {
        ItemStack armor = cat.getData(CAT_INVENTORY.get()).getArmor();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat, new SyncCatInventoryPacket(cat.getId(), armor));
    }

    static void broadcastStatsSync(Cat cat) {
        int xp = cat.getData(CAT_XP.get());
        int cap = getCatXpCapacity();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat, new SyncCatStatsPacket(cat.getId(), xp, cap));
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
        // considers walkable. The STEP_HEIGHT attribute is the authoritative source — read by both
        // the WalkNodeEvaluator and the movement code — so no maxUpStep mixin is needed. Cats=0.6.
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
        PacketDistributor.sendToPlayer(player,
                new SyncCatStatsPacket(cat.getId(), cat.getData(CAT_XP.get()), getCatXpCapacity()));
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
        // Force-sit: without this, the kitten's FollowOwnerGoal/panic-teleport immediately
        // yanks it across the map toward its (possibly far-away) new owner.
        baby.setOrderedToSit(true);
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

        // Per-tick ledge-jump assist so cats reliably clear ~1.5-block steps.
        assistLedgeJump(cat);

        // Stuck detection: repath when a guardian stops making progress toward its goal.
        tickStuckDetection(cat);

        if (cat.tickCount % 10 != 0) {
            return;
        }

        tickCat(cat);
    }

    /**
     * The cat's current movement goal: its combat target if any, else its bowl. Null if neither.
     */
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

    /**
     * True if every node of {@code path} lies within the cat's guard zone (zero buffer).
     */
    private static boolean isPathWithinZone(
            net.minecraft.world.level.pathfinder.Path path, Cat cat) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            net.minecraft.world.level.pathfinder.Node n = path.getNode(i);
            if (!isWithinGuardZone(cat, n.x + 0.5, n.y, n.z + 0.5, 0.0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Throttled path toward a goal position (A* is costly), recomputed at most every 10 ticks.
     */
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

    /**
     * True if the path's closest reachable end node is within {@code maxDist} of the goal.
     */
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

    /**
     * Horizontal direction toward the first path node meaningfully ahead of the cat; null if none.
     */
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

    /**
     * Direction the cat should currently head toward its goal, preferring pathfinding over bee-line.
     */
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
            // Don't jump unless there's a solid block to clear in the jump direction.
            // If the block below the forward step is water, the "wall" is just a shallow bank —
            // require ONE BLOCK HIGHER to be solid so the cat doesn't leap over water-edge banks
            // it can simply walk out of. On dry land, the feet-level check is sufficient (fences,
            // 1-block steps, etc.).
            net.minecraft.core.Direction facing = net.minecraft.core.Direction.getNearest(dir.x, 0, dir.z);
            BlockPos aheadFeet = cat.blockPosition().relative(facing);
            boolean nearWaterEdge =
                    cat.level().getFluidState(aheadFeet).is(net.minecraft.tags.FluidTags.WATER)
                            || cat.level().getFluidState(aheadFeet.below()).is(net.minecraft.tags.FluidTags.WATER);
            BlockPos checkPos = nearWaterEdge ? aheadFeet.above() : aheadFeet;
            // isSolid() is false for chests, slabs, fences etc. (it tracks render/AO behaviour,
            // not collision), which made the cat stop dead in front of a chest instead of
            // hopping it. Check the actual collision shape so any physical obstacle qualifies.
            if (cat.level().getBlockState(checkPos).getCollisionShape(cat.level(), checkPos).isEmpty()) {
                return;
            }
            // Headroom check: an overhanging block (e.g. a stair-stepped/jutting build) can sit
            // directly above the jump arc. Without this, the cat repeatedly hops, bonks its head,
            // and falls right back — an endless stuck loop. Require the column above both the
            // takeoff and landing spots to be clear up to the ~1.5-block jump peak.
            BlockPos takeoffHead = cat.blockPosition().above(2);
            BlockPos landingHead = aheadFeet.above(2);
            if (!cat.level().getBlockState(takeoffHead).getCollisionShape(cat.level(), takeoffHead).isEmpty()
                    || !cat.level().getBlockState(landingHead).getCollisionShape(cat.level(), landingHead).isEmpty()) {
                return;
            }
            vx = dir.x * 0.28;
            vz = dir.z * 0.28;
        }
        cat.setDeltaMovement(vx, 0.5, vz); // peak ≈ 1.5 blocks, with forward carry onto the ledge
        cat.hasImpulse = true;
    }

    /**
     * If the pathfinder can't produce a route home (nav stays done), nudge the cat directly
     * toward the bowl so it can escape narrow spaces. Shared by the returning and fleeing
     * home-navigation branches.
     */
    private static void nudgeTowardBowlIfPathless(Cat cat, BlockPos bowlPos) {
        if (cat.isInWater() || !cat.getNavigation().isDone()) {
            return;
        }
        net.minecraft.world.phys.Vec3 towardBowl = new net.minecraft.world.phys.Vec3(
                bowlPos.getX() + 0.5 - cat.getX(), 0,
                bowlPos.getZ() + 0.5 - cat.getZ()).normalize().scale(0.18);
        net.minecraft.world.phys.Vec3 vm = cat.getDeltaMovement();
        cat.setDeltaMovement(towardBowl.x, vm.y, towardBowl.z);
    }

    /**
     * Stuck detection (guardians with an active movement goal — combat target, returning or
     * fleeing): samples the position every 40 ticks; too little progress adds a strike. The
     * strikes escalate instead of just repathing forever:
     * <ol>
     *   <li>every strike: force a repath</li>
     *   <li>strike ≥ 2: active hop toward the goal direction — frees cats parked on chests,
     *       slabs and other partial blocks where the pathfinder's route silently fails</li>
     *   <li>strike 3: blacklist the combat target (if any) and commit to returning home</li>
     *   <li>strike ≥ 5 while returning/fleeing (~20s stuck): emergency-teleport to a safe spot
     *       next to the bowl — the guardian pendant of the vanilla pet owner-teleport, so no
     *       cat ever stays stranded</li>
     * </ol>
     */
    private void tickStuckDetection(Cat cat) {
        if (!isGuardianCat(cat)) {
            return;
        }
        UUID uid = cat.getUUID();
        boolean wantsToMove = cat.getTarget() != null
                || cat.getData(CAT_RETURNING.get())
                || cat.getData(CAT_FLEEING.get());
        if (!wantsToMove) {
            stuckSample.remove(uid);
            stuckStrikes.remove(uid);
            return;
        }
        if (cat.tickCount % 40 != 0) {
            return;
        }
        net.minecraft.world.phys.Vec3 now = cat.position();
        net.minecraft.world.phys.Vec3 prev = stuckSample.put(uid, now);
        if (prev == null) {
            return;
        }
        if (prev.distanceToSqr(now) >= 0.75 * 0.75) {
            stuckStrikes.remove(uid);
            return;
        }
        int strikes = stuckStrikes.merge(uid, 1, Integer::sum);
        cat.getNavigation().recomputePath();
        if (strikes >= 2) {
            unstickHop(cat);
        }
        if (strikes == 3) {
            LivingEntity target = cat.getTarget();
            if (target != null) {
                cat.targetSelector.getAvailableGoals().stream()
                        .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .filter(g -> g instanceof CatGuardTargetGoal)
                        .findFirst()
                        .ifPresent(g -> ((CatGuardTargetGoal) g).blockTarget(target));
                cat.setTarget(null);
            }
            if (cat.getData(CAT_BOWL_POS.get()) != Long.MIN_VALUE) {
                cat.setData(CAT_RETURNING.get(), true);
            }
        }
        boolean headingHome = cat.getData(CAT_RETURNING.get()) || cat.getData(CAT_FLEEING.get());
        if (strikes >= 5 && headingHome) {
            long bowlLong = cat.getData(CAT_BOWL_POS.get());
            if (bowlLong != Long.MIN_VALUE && teleportToBowl(cat, BlockPos.of(bowlLong))) {
                stuckStrikes.remove(uid);
                stuckSample.remove(uid);
            }
        }
    }

    /**
     * Active unstick hop: like {@link #assistLedgeJump}, but without requiring a horizontal
     * collision — a cat standing ON a chest/slab collides with nothing, its path just never
     * executes. Only the headroom guard is kept so the hop can't turn into a head-bonk loop.
     */
    private void unstickHop(Cat cat) {
        if (!cat.onGround() || cat.isInWater()) {
            return;
        }
        net.minecraft.world.phys.Vec3 dir = goalDir(cat);
        if (dir == null) {
            return;
        }
        Direction facing = Direction.getNearest(dir.x, 0, dir.z);
        BlockPos takeoffHead = cat.blockPosition().above(2);
        BlockPos landingHead = cat.blockPosition().relative(facing).above(2);
        if (!cat.level().getBlockState(takeoffHead).getCollisionShape(cat.level(), takeoffHead).isEmpty()
                || !cat.level().getBlockState(landingHead).getCollisionShape(cat.level(), landingHead).isEmpty()) {
            return;
        }
        cat.setDeltaMovement(dir.x * 0.28, 0.5, dir.z * 0.28);
        cat.hasImpulse = true;
    }

    /**
     * Emergency teleport onto a standable block next to the bowl (vanilla pet-teleport style:
     * solid floor, two collision-free blocks of clearance). Returns false when no candidate
     * around the bowl qualifies — the caller simply retries on a later strike.
     */
    private boolean teleportToBowl(Cat cat, BlockPos bowlPos) {
        var level = cat.level();
        for (BlockPos candidate : BlockPos.betweenClosed(bowlPos.offset(-2, -1, -2), bowlPos.offset(2, 1, 2))) {
            if (candidate.equals(bowlPos)) {
                continue; // don't drop the cat into the bowl block itself
            }
            BlockPos floor = candidate.below();
            boolean standable =
                    level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                            && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                            && level.getBlockState(candidate.above())
                                    .getCollisionShape(level, candidate.above()).isEmpty()
                            && level.getFluidState(candidate).isEmpty();
            if (!standable) {
                continue;
            }
            cat.moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5,
                    cat.getYRot(), cat.getXRot());
            cat.getNavigation().stop();
            return true;
        }
        return false;
    }

    private static boolean isFishItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(net.minecraft.tags.ItemTags.FISHES)) {
            return true;
        }
        Item item = stack.getItem();
        return item == FlyingFishModule.RAW_FLYING_FISH.get()
                || item == FlyingFishModule.COOKED_FLYING_FISH.get();
    }

    private void tickCat(Cat cat) {
        CatGuardianConfig config = getConfig();

        long bowlPosLong = cat.getData(CAT_BOWL_POS.get());
        boolean hasBowl = bowlPosLong != Long.MIN_VALUE;

        UUID uid = cat.getUUID();
        if (!hasBowl) {
            sitGoalSuppressor.restore(cat); // duty ended (bowl gone) — give idle behaviors back
            tryAutoAssociate(cat, config.getAutoAssociateRadius());
            return;
        }

        // Let guardian cats path over fences (1.5 high) — their raised step height + jump assist
        // climb them physically, and otherwise the pathfinder rejects fence nodes outright, hiding
        // shorter routes through the base. Guardian-only so penned pet cats stay contained.
        if (cat.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation gpn) {
            gpn.setCanWalkOverFences(true);
        }

        // Guardian cats may cross shallow water (FloatGoal keeps them at the surface — they can
        // never dive anymore). Per-instance malus, so vanilla pet cats keep avoiding water.
        cat.setPathfindingMalus(PathType.WATER, 0.0f);
        cat.setPathfindingMalus(PathType.WATER_BORDER, 2.0f);

        suppressFollowingBehaviors(cat);
        sitGoalSuppressor.suppress(cat);

        // Sync navigation path to clients every 20 ticks for the debug overlay.
        if (cat.tickCount % 20 == 0) {
            syncPathToClients(cat);
        }

        // Drowning escape: if air runs critically low, abandon the current target and return
        // home immediately. Triggered before the health-flee check so it takes priority even
        // when the cat is healthy. canUse() also blocks new target acquisition while drowning.
        if (cat.isInWater() && cat.getAirSupply() < 60 && !cat.getData(CAT_FLEEING.get())) {
            cat.setTarget(null);
            cat.setData(CAT_RETURNING.get(), true);
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
                cat.getNavigation().resetMaxVisitedNodesMultiplier();
                // Heal at base; resume duty once safely recovered
                cat.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
                AbstractCatBowlBlockEntity fleeingBowl = getBowlEntity(cat, bowlPos);
                if (fleeingBowl instanceof CatFeedingStationBlockEntity fleeingStation) {
                    transferLootToStation(cat, fleeingStation);
                }
                if (healthPct > 0.40f) {
                    cat.setData(CAT_FLEEING.get(), false);
                } else if (!cat.isOrderedToSit()) {
                    cat.setOrderedToSit(true);
                }
            } else {
                if (cat.isOrderedToSit()) {
                    cat.setOrderedToSit(false);
                }
                // Doubled A* node budget: the way home may span the whole guard radius.
                cat.getNavigation().setMaxVisitedNodesMultiplier(2.0f);
                cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 1.0);
                nudgeTowardBowlIfPathless(cat, bowlPos);
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
            if (distSqToBowl <= 16.0 || returningAge >= 1200) {
                cat.setData(CAT_RETURNING.get(), false);
                catReturningAge.remove(uid);
                cat.getNavigation().resetMaxVisitedNodesMultiplier();
                if (distSqToBowl <= 16.0) {
                    AbstractCatBowlBlockEntity returnBowl = getBowlEntity(cat, bowlPos);
                    if (returnBowl instanceof CatFeedingStationBlockEntity returnStation) {
                        transferLootToStation(cat, returnStation);
                    }
                }
            } else {
                // Doubled A* node budget: the way home may span the whole guard radius.
                cat.getNavigation().setMaxVisitedNodesMultiplier(2.0f);
                cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 1.0);
                nudgeTowardBowlIfPathless(cat, bowlPos);
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
                } else {
                    // At the station — deposit loot while idling (no fetch trigger here;
                    // fetch is only triggered from the natural return/eat/flee events)
                    AbstractCatBowlBlockEntity idleBowl = getBowlEntity(cat, bowlPos);
                    if (idleBowl instanceof CatFeedingStationBlockEntity idleStation) {
                        transferLootToStation(cat, idleStation);
                    }
                    if (!cat.isOrderedToSit()) {
                        cat.setOrderedToSit(true);
                    }
                }
            }
//            logCatHungerReturn(cat, bowlPos, "fed_ticks_active", null, fedTicks);
            return;
        }

        // Try to eat when unfed
        AbstractCatBowlBlockEntity bowl = getBowlEntity(cat, bowlPos);
        if (bowl == null) {
//            logCatHungerReturn(cat, bowlPos, "bowl_missing", null, fedTicks);
            return;
        }
        if (!bowl.hasFish()) {
//            logCatHungerReturn(cat, bowlPos, "bowl_empty", bowl, fedTicks);
            return;
        }

        double distSq = cat.distanceToSqr(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);
//        logCatHungerState(cat, bowlPos, bowl, "pre_eat_check", distSq, fedTicks);

        if (cat.isOrderedToSit()) {
            // A hungry cat must be able to get up again even if it was previously sitting at the station.
            cat.setOrderedToSit(false);
        }
        if (distSq > 4.0) {
            if (cat.getNavigation().isDone()) {
                cat.getNavigation().moveTo(bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 0.8);
            }
//            logCatHungerReturn(cat, bowlPos, "moving_toward_station", bowl, fedTicks);
        } else if (distSq <= 4.0) {
            var fish = bowl.takeFish();
            if (!fish.isEmpty()) {
                cat.setData(CAT_FED_TICKS.get(), config.getFedDurationTicks());
                cat.playSound(SoundEvents.GENERIC_EAT, 0.5F, cat.getVoicePitch());
                cat.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                if (bowl instanceof CatFeedingStationBlockEntity station) {
                    transferLootToStation(cat, station);
                }
//                logCatHungerState(cat, bowlPos, bowl, "ate_fish", distSq, fedTicks);
            }
        }
    }

    private void logCatHungerReturn(Cat cat, BlockPos bowlPos, String reason,
                                    AbstractCatBowlBlockEntity bowl, Integer fedTicks) {
        if (!getConfig().shouldDebugLog()) {
            return;
        }
        String targetName = cat.getTarget() != null ? cat.getTarget().getType().toShortString() : "none";
        getLogger().debug(
                "[cat_guardian] hunger return reason={} cat={} bowl={} fedTicks={} orderedToSit={} returning={} hasFish={} target={}",
                reason, cat.getUUID(), bowlPos, fedTicks, cat.isOrderedToSit(),
                cat.getData(CAT_RETURNING.get()), bowl != null && bowl.hasFish(), targetName);
    }

    private void logCatHungerState(Cat cat, BlockPos bowlPos, AbstractCatBowlBlockEntity bowl,
                                   String state, Double distSq, Integer fedTicks) {
        if (!getConfig().shouldDebugLog()) {
            return;
        }
        String distText = distSq == null ? "n/a" : String.format(Locale.ROOT, "%.3f", distSq);
        String targetName = cat.getTarget() != null ? cat.getTarget().getType().toShortString() : "none";
        getLogger().debug(
                "[cat_guardian] hunger state={} cat={} bowl={} distSq={} fedTicks={} orderedToSit={} "
                        + "returning={} hasFish={} target={} navDone={}",
                state, cat.getUUID(), bowlPos, distText, fedTicks, cat.isOrderedToSit(),
                cat.getData(CAT_RETURNING.get()), bowl != null && bowl.hasFish(),
                targetName, cat.getNavigation().isDone());
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
                broadcastStatsSync(cat);
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

        // Collect all valid bowls in range, then pick the nearest one. BlockPos.betweenClosed
        // iterates in YXZ order so first-found is not necessarily closest.
        BlockPos nearestPos = null;
        AbstractCatBowlBlockEntity nearestBowl = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (BlockPos checkPos : BlockPos.betweenClosed(
                catPos.offset(-r, -r, -r), catPos.offset(r, r, r))) {
            if (!(cat.level().getBlockEntity(checkPos) instanceof AbstractCatBowlBlockEntity bowl) || !bowl.canAddCat(cat.getUUID())) {
                continue;
            }
            double distSq = cat.distanceToSqr(
                    checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
            if (distSq <= radiusSq && distSq < nearestDistSq) {
                nearestPos = checkPos.immutable();
                nearestBowl = bowl;
                nearestDistSq = distSq;
            }
        }

        if (nearestPos != null) {
            cat.setData(CAT_BOWL_POS.get(), nearestPos.asLong());
            nearestBowl.addCat(cat.getUUID());
        }
    }

    // ---- Path sync for debug overlay ----

    private void syncPathToClients(Cat cat) {
        net.minecraft.world.level.pathfinder.Path path = cat.getNavigation().getPath();
        if (path == null || path.isDone()) {
            // During combat, MeleeAttackGoal resets the path every tick — the 20-tick sync window
            // may catch a momentary isDone() gap. Keep the last path on the client while the cat
            // has a target so the approach path stays visible. Only clear when truly idle.
            if (cat.getTarget() != null) {
                return;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                    new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket(
                            cat.getId(), new int[0], new int[0], new int[0], 0));
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket(
                        cat.getId(), xs, ys, zs, path.getNextNodeIndex()));
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

        // Fish feeding — any player can feed any tamed cat (no ownership required)
        if (cat.isTame() && isFishItem(event.getItemStack())) {
            if (!isGuardianCat(cat)) {
                // We allow feeding non-guardian cats to be handled by vanilla (e.g. for breeding), but guardian cats
                // don't get to f*ck, they have to fight — so we cancel the interaction for guardian cats only to prevent
                // vanilla feeding logic from applying.
//                event.setCanceled(false);
                return;
            }
            if (!event.getLevel().isClientSide()) {
                Player feeder = event.getEntity();
                if (!feeder.isCreative()) {
                    event.getItemStack().shrink(1);
                }
                cat.setData(CAT_FED_TICKS.get(), getFedDurationTicks());
                cat.setHealth(cat.getMaxHealth());
                cat.level().playSound(null, cat.getX(), cat.getY(), cat.getZ(),
                        SoundEvents.CAT_PURREOW, cat.getSoundSource(), 1.0f, 1.0f);
                if (cat.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HEART,
                            cat.getX(), cat.getEyeY(), cat.getZ(), 7, 0.3, 0.3, 0.3, 0);
                }
            }
            event.setCanceled(true);
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
        }
        // Armor is removed by dragging it out of the cat inventory GUI (no click gesture for it).
        // Modifier+right-click (default Ctrl) opens that GUI client-side (see
        // CatGuardianClientEvents#onEntityInteract, gated on CatGuardianKeybinds); a plain
        // right-click falls through to vanilla sit/stand, and Shift+right-click stays with Carry On.
    }

    // ---- Cat petting (empty-hand left-click) ----

    @SubscribeEvent
    public void onAttackCat(AttackEntityEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Cat cat)) {
            return;
        }
        if (!event.getEntity().getMainHandItem().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        cat.level().playSound(null, cat.getX(), cat.getY(), cat.getZ(),
                SoundEvents.CAT_PURR, cat.getSoundSource(), 1.0f, 1.0f);
        if (cat.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART,
                    cat.getX(), cat.getEyeY(), cat.getZ(), 5, 0.3, 0.3, 0.3, 0);
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
        // Read Thorns before the armor possibly breaks below, so the reflect still fires.
        int thornsLevel = MobArmorEnchantments.getThornsLevel(armor);
        event.setNewDamage(0f);

        armor.hurtAndBreak(Math.max(1, (int) Math.ceil(absorbed)), cat, net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (armor.isEmpty()) {
            invData.setArmor(ItemStack.EMPTY);
            removeArmorAttribute(cat);
        }
        broadcastArmorSync(cat);

        // Thorns: reflect a share of the absorbed damage back to a living attacker.
        MobArmorEnchantments.reflectThorns(cat, event.getSource(), absorbed, thornsLevel,
                getConfig().getThornsReflectFraction());

        // Retaliation: if hit by a Monster in zone, re-run full path-length target selection.
        // CAT_FLEEING cats ignore this — they're already running home.
        if (!cat.level().isClientSide() && isGuardianCat(cat)
                && !cat.getData(CAT_FLEEING.get())) {
            Entity direct = event.getSource().getDirectEntity();
            Entity indirect = event.getSource().getEntity();
            LivingEntity attacker = direct instanceof Monster m ? m
                    : indirect instanceof Monster m2 ? m2 : null;
            if (attacker != null && !attacker.isDeadOrDying()
                    && isWithinGuardZone(cat, attacker.getX(), attacker.getY(), attacker.getZ(),
                    TARGET_ZONE_BUFFER)) {
                cat.targetSelector.getAvailableGoals().stream()
                        .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .filter(g -> g instanceof CatGuardTargetGoal)
                        .findFirst()
                        .ifPresent(g -> ((CatGuardTargetGoal) g).triggerRetarget());
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
        // Sharpness: armored guardians deal bonus outgoing damage (read off the equipped armor).
        int sharpnessLevel = MobArmorEnchantments.getSharpnessLevel(
                cat.getData(CAT_INVENTORY.get()).getArmor());
        if (sharpnessLevel > 0) {
            event.setNewDamage(event.getNewDamage() + 0.5f + 0.5f * sharpnessLevel);
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

        Iterator<ItemEntity> iter = event.getDrops().iterator();
        while (iter.hasNext()) {
            ItemEntity itemEntity = iter.next();
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
        broadcastStatsSync(cat);
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
        UUID uid = cat.getUUID();
        catReturningAge.remove(uid);
        catGoalPath.remove(uid);
        catGoalPathTick.remove(uid);
        stuckSample.remove(uid);
        stuckStrikes.remove(uid);
        sitGoalSuppressor.forget(uid);
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
        // Remove owner-teleport sources and other approach behaviors to ensure cats don't move
        // towards their owner: FollowOwnerGoal (priority 6) and the panic goal (priority 1).
        // Sit-on-chest/lie-on-bed goals are handled separately (suppressSitGoals, duty-gated).
        cat.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof FollowOwnerGoal
                          || w.getGoal() instanceof net.minecraft.world.entity.ai.goal.PanicGoal)
                .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .toList()
                .forEach(cat.goalSelector::removeGoal);
    }

    private static void suppressFollowingBehaviors(Cat cat) {
        // Remove goals that would fight with guard-station behaviour or cause wandering/approaching
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


    /**
     * Buffer (blocks) for target eligibility — avoids flicker at the boundary.
     */
    private static final double TARGET_ZONE_BUFFER = 4.0;
    /**
     * Larger buffer for the cat's own travel — allows edge melee + brief boundary stepping.
     */
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
        /**
         * Throttle: skip A* target searches when recently found no valid target.
         */
        private int targetSearchCooldown = 0;
        /**
         * Mob entity IDs that are temporarily blacklisted (value = game time when blacklist expires).
         */
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
            if (cat.isInWater() && cat.getAirSupply() < 60) {
                return false; // drowning: surface first, don't acquire new targets
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
            // Throttle: A* path computation per candidate is expensive; skip the search
            // for a few ticks after a failed search rather than running it every tick.
            if (targetSearchCooldown > 0) {
                targetSearchCooldown--;
                return false;
            }
            boolean found = findAndSetTarget(BlockPos.of(bowlLong));
            if (!found) {
                targetSearchCooldown = 20;
            }
            if (found && cat.getData(CAT_RETURNING.get())) {
                // Don't interrupt a return trip if all loot slots are full — the cat must deposit
                // before it can pick up more loot anyway; let it finish the trip.
                var catInv = cat.getData(CAT_INVENTORY.get()).getInventory();
                boolean lootFull = true;
                for (int s = CatInventoryData.LOOT_START; s < CatInventoryData.TOTAL_SLOTS; s++) {
                    if (catInv.getStackInSlot(s).isEmpty()) {
                        lootFull = false;
                        break;
                    }
                }
                if (lootFull) {
                    return false;
                }
                cat.setData(CAT_RETURNING.get(), false); // interrupt return to engage
                // The return trip doubled the A* node budget — don't drag that into combat.
                cat.getNavigation().resetMaxVisitedNodesMultiplier();
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
            // Cats are land guardians: the moment a target fully submerges (e.g. a drowned
            // diving away), let it go — cats can't dive anymore. No blacklist: the target is
            // simply filtered while underwater and re-engaged when it surfaces.
            if (target.isUnderWater()) {
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

            // Every 20 ticks: evict expired blacklist entries and check that the live
            // navigation path (recalculated by MeleeAttackGoal as the monster moves) still
            // stays inside the guard zone. AmphibiousPathNavigation produces full 3D paths
            // including underwater nodes, so this check works uniformly for all targets.
            if (cat.tickCount % 20 == 0) {
                blockedTargets.entrySet().removeIf(e -> cat.level().getGameTime() >= e.getValue());
                net.minecraft.world.level.pathfinder.Path livePath = cat.getNavigation().getPath();
                if (livePath != null && !isPathWithinZone(livePath, cat)) {
                    blacklist(target);
                    return false;
                }
            }
            return true;
        }

        void blockTarget(LivingEntity target) {
            blacklist(target);
        }

        void triggerRetarget() {
            cat.setTarget(null);
            this.targetMob = null;
            targetSearchCooldown = 0; // allow immediate re-search
            long bowlLong = cat.getData(CAT_BOWL_POS.get());
            if (bowlLong == Long.MIN_VALUE) {
                return;
            }
            findAndSetTarget(BlockPos.of(bowlLong));
            LivingEntity t = cat.getTarget();
            if (t != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(cat,
                        new net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket(
                                cat.getId(), t.getId()));
            }
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

        @Override
        public void start() {
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

        /**
         * Selects the best target from {@code candidates} using A* path length.
         * Pre-sorts by Euclidean distance and evaluates the top 8 via actual path computation.
         * AmphibiousPathNavigation produces underwater nodes, so endNodeNear and isPathWithinZone
         * work uniformly for both land and submerged targets.
         */
        private Monster selectTargetByPathLength(List<Monster> candidates) {
            candidates.sort(Comparator.comparingDouble(cat::distanceToSqr));
            int limit = Math.min(8, candidates.size());
            Monster best = null;
            double bestLength = Double.MAX_VALUE;
            for (int i = 0; i < limit; i++) {
                Monster mob = candidates.get(i);
                net.minecraft.world.level.pathfinder.Path path =
                        cat.getNavigation().createPath(mob.getX(), mob.getY(), mob.getZ(), 0);
                if (!instance.endNodeNear(path,
                        new net.minecraft.world.phys.Vec3(mob.getX(), mob.getY(), mob.getZ()), 2.0)) {
                    continue; // unreachable or partial path — discard early instead of running at it
                }
                if (!isPathWithinZone(path, cat)) {
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

        private boolean findAndSetTarget(BlockPos bowlPos) {
            double radius = instance != null ? instance.getConfig().getGuardRadius() : 32.0;
            double radiusY = instance != null ? instance.getConfig().getGuardRadiusY() : 16.0;
            AABB searchBox = new AABB(bowlPos).inflate(radius, radiusY, radius);
            List<Monster> hostiles = cat.level().getEntitiesOfClass(
                    Monster.class, searchBox, m -> !m.isDeadOrDying() && !isBlocked(m) && !m.isUnderWater());
            if (hostiles.isEmpty()) {
                return false;
            }
            Monster best = selectTargetByPathLength(hostiles);
            if (best == null) {
                cat.setData(CAT_RETURNING.get(), true); // no reachable in-zone mob → go home
                return false;
            }
            this.targetMob = best;
            cat.setTarget(best);
            cat.setOrderedToSit(false); // stand up immediately so MeleeAttackGoal isn't blocked
            return true;
        }
    }
}
