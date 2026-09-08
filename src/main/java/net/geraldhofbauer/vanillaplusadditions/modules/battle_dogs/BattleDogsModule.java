package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.config.BattleDogsConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.item.WolfArmorItem;
import net.geraldhofbauer.vanillaplusadditions.util.MobArmorEnchantments;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.network.BiteDirections;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.network.WolfBiteDirectionPacket;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class BattleDogsModule extends AbstractModule<BattleDogsModule, BattleDogsConfig> {

    private static BattleDogsModule instance;

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    private static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "wolf_armor_bonus");

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_IRON =
            ITEMS.register("wolf_armor_iron",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.IRON, new Item.Properties()));

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_GOLD =
            ITEMS.register("wolf_armor_gold",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_DIAMOND =
            ITEMS.register("wolf_armor_diamond",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.DIAMOND, new Item.Properties()));

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_NETHERITE =
            ITEMS.register("wolf_armor_netherite",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.NETHERITE, new Item.Properties().fireResistant()));

    public BattleDogsModule() {
        super(
                "battle_dogs",
                "Battle Dogs",
                "Adds iron, gold, diamond, and netherite armor for tamed wolves.",
                BattleDogsConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        instance = this;
        getModEventBus().addListener(this::onRegisterPayloadHandlers);
        ITEMS.register(getModEventBus());

        VanillaPlusCreativeTabs.addAllToMainTab(
                WOLF_ARMOR_IRON, WOLF_ARMOR_GOLD, WOLF_ARMOR_DIAMOND, WOLF_ARMOR_NETHERITE);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Battle Dogs module initialized");
    }

    // ---- Static accessors for the client-side bite animation mixin ----------------------------
    // WolfBiteAnimationMixin is merged into net.minecraft.client.model.WolfModel and has no other
    // way to reach module state.

    /**
     * Whether the module is loaded and enabled.
     *
     * @return true if the module is active
     */
    public static boolean isModuleActive() {
        return instance != null && instance.isModuleEnabled();
    }

    /**
     * Diagnostic channel for the bite animation, active only with debug_logging = ON.
     *
     * <p>The animation spans a client mixin, a common mixin and vanilla's swing timer; when it
     * stays invisible there is no way to tell from the outside which of the three is the one not
     * firing. This makes each of them say so.
     *
     * @param message the log message
     * @param args    message arguments
     */
    public static void debug(String message, Object... args) {
        if (instance != null && instance.getConfig().shouldDebugLog()) {
            instance.getLogger().info(message, args);
        }
    }

    /**
     * Whether a biting wolf should visibly snap its head.
     *
     * @return true if the bite animation is enabled
     */
    public static boolean isBiteAnimation() {
        return instance != null && instance.getConfig().isBiteAnimation();
    }

    /**
     * Whether the bite animation is restricted to a ridden wolf.
     *
     * @return true if only mounts animate
     */
    public static boolean isBiteAnimationOnlyWhenRidden() {
        return instance != null && instance.getConfig().isBiteAnimationOnlyWhenRidden();
    }

    /**
     * How far the head swings, 1.0 being roughly 50 degrees.
     *
     * @return the configured strength multiplier
     */
    public static double getBiteAnimationStrength() {
        return instance != null ? instance.getConfig().getBiteAnimationStrength() : 1.0D;
    }

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Wolf wolf)) {
            return;
        }
        if (!wolf.isTame() || !wolf.isOwnedBy(event.getEntity())) {
            return;
        }

        ItemStack stack = event.getItemStack();
        boolean isClient = event.getLevel().isClientSide();

        if (stack.getItem() instanceof WolfArmorItem && wolf.getBodyArmorItem().isEmpty() && !wolf.isBaby()) {
            if (!isClient) {
                wolf.setBodyArmorItem(stack.copyWithCount(1));
                if (!event.getEntity().isCreative()) {
                    stack.shrink(1);
                }
                wolf.playSound(SoundEvents.ARMOR_EQUIP_WOLF.value());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(isClient));
            return;
        }

        if (stack.canPerformAction(ItemAbilities.SHEARS_REMOVE_ARMOR)
                && wolf.getBodyArmorItem().getItem() instanceof WolfArmorItem) {
            if (!isClient) {
                ItemStack armor = wolf.getBodyArmorItem();
                wolf.setBodyArmorItem(ItemStack.EMPTY);
                wolf.spawnAtLocation(armor);
                wolf.playSound(SoundEvents.ARMOR_UNEQUIP_WOLF);
                EquipmentSlot handSlot = event.getHand() == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, event.getEntity(), handSlot);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(isClient));
        }
    }

    /**
     * Tells nearby clients which way a wolf just bit, so the lunge can aim at the victim.
     *
     * <p>Separate from {@link #onMobDamagedByWolf} on purpose: that one bails out on a wolf without
     * our armor, while the animation belongs to every wolf. Sent per landed bite rather than held
     * in synced data — a bite is an instant, and an instant is what a packet is for.
     *
     * @param event the damage event for the entity the wolf is biting
     */
    @SubscribeEvent
    public void onWolfBiteDirection(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled() || !getConfig().isBiteAnimation()) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Wolf wolf) || wolf.level().isClientSide()) {
            return;
        }
        double dx = event.getEntity().getX() - wolf.getX();
        double dz = event.getEntity().getZ() - wolf.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        PacketDistributor.sendToPlayersTrackingEntity(wolf, new WolfBiteDirectionPacket(wolf.getId(), yaw));
    }

    /**
     * Registers the bite direction channel as <b>optional</b>.
     *
     * <p>Deliberate: {@code NetworkComponentNegotiator} fails the whole handshake when one side has
     * a non-optional channel the other lacks, and the client is then disconnected as incompatible.
     * Locking a player out of a server over a cosmetic animation hint would be absurd. Optional
     * means a mismatched pair simply drops the channel — the wolf then lunges along its head yaw,
     * which is what it did before this packet existed.
     *
     * @param event the payload registration event
     */
    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(
                WolfBiteDirectionPacket.TYPE,
                WolfBiteDirectionPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(
                        () -> BiteDirections.record(packet.wolfId(), packet.yaw())));
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Wolf wolf)) {
            return;
        }
        if (event.getSlot() != EquipmentSlot.BODY) {
            return;
        }

        updateArmorAttribute(wolf, event.getTo());
    }

    @SubscribeEvent
    public void onWolfHurt(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Wolf wolf)) {
            return;
        }

        ItemStack armor = wolf.getBodyArmorItem();
        if (!(armor.getItem() instanceof WolfArmorItem)) {
            return;
        }

        // Armor absorbs 100% of damage; each damage point drains 1 durability
        float absorbed = event.getNewDamage();
        // Read Thorns before the armor possibly breaks below, so the reflect still fires.
        int thornsLevel = MobArmorEnchantments.getThornsLevel(armor);
        event.setNewDamage(0f);

        armor.hurtAndBreak(Math.max(1, (int) Math.ceil(absorbed)), wolf, EquipmentSlot.BODY);

        // Thorns: reflect a share of the absorbed damage back to a living attacker.
        MobArmorEnchantments.reflectThorns(wolf, event.getSource(), absorbed, thornsLevel,
                getConfig().getThornsReflectFraction());
    }

    /**
     * Sharpness: an armored guardian wolf deals bonus outgoing damage (read off its body armor).
     * Mirrors the axolotl/cat attack handlers; the wolf module previously had no attack-side hook.
     *
     * @param event the damage-pre event for the mob the wolf is attacking
     */
    @SubscribeEvent
    public void onMobDamagedByWolf(LivingDamageEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Wolf) {
            return; // wolf-as-victim is handled by onWolfHurt
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity indirect = event.getSource().getEntity();
        Wolf wolf = direct instanceof Wolf w ? w
                : indirect instanceof Wolf w2 ? w2 : null;
        if (wolf == null) {
            return;
        }
        ItemStack armor = wolf.getBodyArmorItem();
        if (!(armor.getItem() instanceof WolfArmorItem)) {
            return;
        }
        int sharpnessLevel = MobArmorEnchantments.getSharpnessLevel(armor);
        if (sharpnessLevel > 0) {
            event.setNewDamage(event.getNewDamage() + 0.5f + 0.5f * sharpnessLevel);
        }
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new BattleDogsRecipeReloadListener(
                event.getServerResources().getRecipeManager(), event.getRegistryAccess()));
    }

    private void applyBattleDogsRecipes(RecipeManager recipeManager, RegistryAccess registryAccess) {
        Map<ResourceLocation, RecipeHolder<?>> mergedRecipes = new LinkedHashMap<>();
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            mergedRecipes.put(recipeHolder.id(), recipeHolder);
        }

        addShapedRecipe(mergedRecipes, "wolf_armor_iron", WOLF_ARMOR_IRON.get(), Items.IRON_INGOT, registryAccess);
        addShapedRecipe(mergedRecipes, "wolf_armor_gold", WOLF_ARMOR_GOLD.get(), Items.GOLD_INGOT, registryAccess);
        addShapedRecipe(mergedRecipes, "wolf_armor_diamond", WOLF_ARMOR_DIAMOND.get(), Items.DIAMOND, registryAccess);
        addShapedRecipe(mergedRecipes, "wolf_armor_netherite", WOLF_ARMOR_NETHERITE.get(), Items.NETHERITE_INGOT,
                registryAccess);

        recipeManager.replaceRecipes(mergedRecipes.values());
    }

    private void addShapedRecipe(Map<ResourceLocation, RecipeHolder<?>> recipes, String name, Item resultItem,
                                 Item material, RegistryAccess registryAccess) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, name);
        Map<Character, Ingredient> key = Map.of(
                'S', Ingredient.of(Items.ARMADILLO_SCUTE),
                'X', Ingredient.of(material)
        );
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, "S  ", "XXX", "X X");
        ItemStack result = new ItemStack(resultItem);
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.EQUIPMENT, pattern, result);
        recipes.put(id, new RecipeHolder<>(id, recipe));
    }

    private final class BattleDogsRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;
        private final RegistryAccess registryAccess;

        private BattleDogsRecipeReloadListener(RecipeManager recipeManager, RegistryAccess registryAccess) {
            this.recipeManager = recipeManager;
            this.registryAccess = registryAccess;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyBattleDogsRecipes(recipeManager, registryAccess), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_battle_dogs_recipes";
        }
    }

    private void updateArmorAttribute(Wolf wolf, ItemStack stack) {
        var attr = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) {
            return;
        }
        attr.removeModifier(ARMOR_MODIFIER_ID);
        if (stack.getItem() instanceof WolfArmorItem wolfArmor) {
            attr.addPermanentModifier(new AttributeModifier(
                    ARMOR_MODIFIER_ID,
                    wolfArmor.getTier().getAttackBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
