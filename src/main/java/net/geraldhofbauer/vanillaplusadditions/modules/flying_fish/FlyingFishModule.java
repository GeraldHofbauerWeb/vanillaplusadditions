package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.client.FlyingFishClientHooks;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.config.FlyingFishConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.config.LeapMode;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.entity.FlyingFishEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.item.FlyingFishBootsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FlyingFishModule extends AbstractModule<FlyingFishModule, FlyingFishConfig> {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VanillaPlusAdditions.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VanillaPlusAdditions.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FlyingFishEntity>> FLYING_FISH = ENTITY_TYPES.register(
            "flying_fish",
            () -> EntityType.Builder.of(FlyingFishEntity::new, MobCategory.WATER_AMBIENT)
                    .sized(0.5F, 0.3F)
                    .clientTrackingRange(4)
                    .build(VanillaPlusAdditions.MODID + ":flying_fish")
    );

    public static final DeferredItem<FlyingFishBootsItem> FLYING_FISH_BOOTS = ITEMS.register(
            "flying_fish_boots",
            () -> new FlyingFishBootsItem(
                    new Item.Properties()
                            .durability(ArmorItem.Type.BOOTS.getDurability(33))
                            .rarity(Rarity.UNCOMMON)
            )
    );

    public static final DeferredItem<Item> RAW_FLYING_FISH = ITEMS.register(
            "flying_fish",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.4F).build())
                    .rarity(Rarity.COMMON))
    );

    public static final DeferredItem<Item> COOKED_FLYING_FISH = ITEMS.register(
            "cooked_flying_fish",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build())
                    .rarity(Rarity.COMMON))
    );

    public static final DeferredItem<DeferredSpawnEggItem> FLYING_FISH_SPAWN_EGG = ITEMS.register(
            "flying_fish_spawn_egg",
            () -> new DeferredSpawnEggItem(FLYING_FISH, 0x4F8AA6, 0xE8F2F7, new Item.Properties())
    );

    public static final DeferredItem<MobBucketItem> FLYING_FISH_BUCKET = ITEMS.register(
            "flying_fish_bucket",
            () -> new MobBucketItem(
                    FLYING_FISH.get(),
                    Fluids.WATER,
                    SoundEvents.BUCKET_EMPTY_FISH,
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)
            )
    );

    private static volatile boolean contentRegistered;

    private final Map<UUID, Integer> playerLeapCooldowns = new HashMap<>();
    private final Map<UUID, Integer> playerGlideGraceTicks = new HashMap<>();

    private static final double MAX_SURFACE_SPEED = 0.95D;
    private static final ResourceLocation FLYING_FISH_BOOTS_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "flying_fish_boots");

    public FlyingFishModule() {
        super(
                "flying_fish",
                "Flying Fish",
                "Adds leaping flying fish and water-skimming Flying Fish Boots.",
                FlyingFishConfig::new
        );
    }

    public static boolean isContentRegistered() {
        return contentRegistered;
    }

    @Override
    protected void onInitialize() {
        ITEMS.register(getModEventBus());
        ENTITY_TYPES.register(getModEventBus());
        getModEventBus().addListener(this::onEntityAttributeCreation);
        getModEventBus().addListener(this::onRegisterSpawnPlacements);

        VanillaPlusCreativeTabs.addAllToMainTab(
                FLYING_FISH_BOOTS,
                RAW_FLYING_FISH,
                COOKED_FLYING_FISH,
                FLYING_FISH_BUCKET,
                FLYING_FISH_SPAWN_EGG
        );

        if (FMLEnvironment.dist == Dist.CLIENT) {
            getModEventBus().addListener(FlyingFishClientHooks::onRegisterRenderers);
        }

        NeoForge.EVENT_BUS.register(this);
        contentRegistered = true;
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(
                FLYING_FISH.get(),
                AbstractFish.createAttributes()
                        .add(Attributes.MOVEMENT_SPEED, 1.1D)
                        .build()
        );
    }

    private void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                FLYING_FISH.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WaterAnimal::checkSurfaceWaterAnimalSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        if (!event.getEntity().getType().equals(FLYING_FISH.get())) {
            return;
        }

        if (!event.getEntity().level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }

        boolean hasFishDrop = event.getDrops().stream()
                .map(ItemEntity::getItem)
                .anyMatch(stack -> stack.is(RAW_FLYING_FISH.get()) || stack.is(COOKED_FLYING_FISH.get()));

        if (hasFishDrop) {
            return;
        }

        Item fallbackDrop = event.getEntity().isOnFire() ? COOKED_FLYING_FISH.get() : RAW_FLYING_FISH.get();
        event.getDrops().add(new ItemEntity(
                event.getEntity().level(),
                event.getEntity().getX(),
                event.getEntity().getY(),
                event.getEntity().getZ(),
                new ItemStack(fallbackDrop)
        ));
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        event.addListener(new FlyingFishRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyConfiguredFlyingFishRecipes(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> mergedRecipes = new LinkedHashMap<>();
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            mergedRecipes.put(recipeHolder.id(), recipeHolder);
        }

        RecipeHolder<ShapelessRecipe> bootsRecipe = createFlyingFishBootsRecipe();
        mergedRecipes.put(bootsRecipe.id(), bootsRecipe);
        recipeManager.replaceRecipes(mergedRecipes.values());
    }

    private RecipeHolder<ShapelessRecipe> createFlyingFishBootsRecipe() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.DIAMOND_BOOTS));
        ingredients.add(Ingredient.of(FLYING_FISH_BUCKET.get()));

        ItemStack result = new ItemStack(FLYING_FISH_BOOTS.get());
        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.EQUIPMENT, result, ingredients);
        return new RecipeHolder<>(FLYING_FISH_BOOTS_RECIPE_ID, recipe);
    }

    private final class FlyingFishRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private FlyingFishRecipeReloadListener(RecipeManager recipeManager) {
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
                    .thenRunAsync(() -> applyConfiguredFlyingFishRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_flying_fish_recipes";
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }

        Player player = event.getEntity();
        if (player.level().isClientSide() || player.isPassenger()) {
            return;
        }

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        UUID playerId = player.getUUID();

        if (!boots.is(FLYING_FISH_BOOTS.get())) {
            playerLeapCooldowns.remove(playerId);
            playerGlideGraceTicks.remove(playerId);
            return;
        }

        tickTimers(playerId);

        LeapMode leapMode = getConfig().getLeapMode();

        if (shouldBoostPlayer(player)) {
            applyWaterSkimBoost(player);
        }

        if (leapMode != LeapMode.REALISTIC && shouldLaunchPlayer(playerId, player, leapMode)) {
            launchPlayer(playerId, player);
        }

        if (playerGlideGraceTicks.getOrDefault(playerId, 0) > 0 && !player.isInWaterOrBubble()) {
            applyGlide(player);
        }
    }

    private void tickTimers(UUID playerId) {
        decrementTimer(playerLeapCooldowns, playerId);
        decrementTimer(playerGlideGraceTicks, playerId);
    }

    private void decrementTimer(Map<UUID, Integer> timers, UUID playerId) {
        int current = timers.getOrDefault(playerId, 0);
        if (current <= 1) {
            timers.remove(playerId);
            return;
        }
        timers.put(playerId, current - 1);
    }

    private boolean shouldBoostPlayer(Player player) {
        return player.isSprinting() && !player.isShiftKeyDown() && isTouchingWater(player);
    }

    private boolean shouldLaunchPlayer(UUID playerId, Player player, LeapMode leapMode) {
        if (playerLeapCooldowns.getOrDefault(playerId, 0) > 0) {
            return false;
        }
        if (!shouldBoostPlayer(player)) {
            return false;
        }
        // ARCADE: leap whenever touching water (not only near the surface)
        if (leapMode == LeapMode.ARCADE) {
            return isTouchingWater(player);
        }
        // DEFAULT: only leap when near the water surface
        return isNearWaterSurface(player);
    }

    private void applyWaterSkimBoost(Player player) {
        Vec3 lookDirection = getHorizontalLook(player);
        Vec3 deltaMovement = player.getDeltaMovement().add(lookDirection.scale(getConfig().getBootsHorizontalBoost()));
        player.setDeltaMovement(clampHorizontalSpeed(deltaMovement));
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    private void launchPlayer(UUID playerId, Player player) {
        Vec3 lookDirection = getHorizontalLook(player);
        Vec3 deltaMovement = player.getDeltaMovement();
        Vec3 launchedMovement = new Vec3(
                deltaMovement.x * 0.6D + lookDirection.x * 0.55D,
                Math.max(deltaMovement.y, getConfig().getBootsVerticalBoost()),
                deltaMovement.z * 0.6D + lookDirection.z * 0.55D
        );

        player.setDeltaMovement(launchedMovement);
        player.fallDistance = 0.0F;
        player.hasImpulse = true;
        player.hurtMarked = true;

        int cooldown = getConfig().getLeapCooldownTicks();
        if (getConfig().getLeapMode() == LeapMode.ARCADE) {
            cooldown = Math.max(1, cooldown / 2);
        }
        playerLeapCooldowns.put(playerId, cooldown);
        playerGlideGraceTicks.put(playerId, 16);
    }

    private void applyGlide(Player player) {
        Vec3 deltaMovement = player.getDeltaMovement();
        double maxFallSpeed = -getConfig().getMaxGlideFallSpeed();
        if (deltaMovement.y < maxFallSpeed) {
            player.setDeltaMovement(deltaMovement.x, maxFallSpeed, deltaMovement.z);
        }
        player.fallDistance = 0.0F;
    }

    private boolean isTouchingWater(Player player) {
        return player.isInWaterOrBubble()
                || isWaterAt(player, -0.1D)
                || isWaterAt(player, -0.6D)
                || isWaterAt(player, -1.0D);
    }

    private boolean isNearWaterSurface(Player player) {
        return !isWaterAt(player, 0.6D)
                && (isWaterAt(player, -0.1D)
                || isWaterAt(player, -0.6D)
                || isWaterAt(player, -1.0D));
    }

    private boolean isWaterAt(Player player, double yOffset) {
        BlockPos blockPos = BlockPos.containing(player.getX(), player.getY() + yOffset, player.getZ());
        return player.level().getFluidState(blockPos).is(net.minecraft.tags.FluidTags.WATER);
    }

    private Vec3 getHorizontalLook(Player player) {
        Vec3 lookAngle = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(lookAngle.x, 0.0D, lookAngle.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return horizontalLook.normalize();
    }

    private Vec3 clampHorizontalSpeed(Vec3 movement) {
        double horizontalSpeedSqr = movement.x * movement.x + movement.z * movement.z;
        double maxHorizontalSpeedSqr = MAX_SURFACE_SPEED * MAX_SURFACE_SPEED;
        if (horizontalSpeedSqr <= maxHorizontalSpeedSqr) {
            return movement;
        }

        double scale = MAX_SURFACE_SPEED / Math.sqrt(horizontalSpeedSqr);
        return new Vec3(movement.x * scale, movement.y, movement.z * scale);
    }
}

