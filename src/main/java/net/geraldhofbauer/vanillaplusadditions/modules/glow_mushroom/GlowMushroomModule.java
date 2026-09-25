package net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom.block.GlowMushroomBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom.config.GlowMushroomConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Adds a small glowing mushroom that grows on the ground like the vanilla red and brown ones, plus a
 * stew brewed from it. Both hand out the Glowing effect - the mushroom briefly, the stew for longer.
 *
 * <p>The counterpart to the shroomlight-capped giant mushrooms that the
 * {@code vpa_mushroom_fields_plus} world datapack generates: those are scenery, this one can be
 * picked up, planted and cooked.</p>
 *
 * <p>The stew deliberately ships <em>no</em> texture of its own. Its model points at
 * {@code minecraft:item/mushroom_stew}, so the game draws the texture it already has instead of us
 * redistributing a Mojang asset, and the permanent sparkle comes from the
 * {@code enchantment_glint_override} component rather than from an enchantment.</p>
 */
public class GlowMushroomModule extends AbstractModule<GlowMushroomModule, GlowMushroomConfig> {

    /** Huge variant grown by bone meal - supplied by the world datapack, absent is harmless. */
    private static final ResourceKey<ConfiguredFeature<?, ?>> HUGE_GLOW_MUSHROOM = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath("vpa", "glow_mushroom_branched"));

    /** Percent chance that a broken cap block yields the block itself. */
    private static final int CAP_BLOCK_DROP_PERCENT = 5;
    /** Percent chance that it yields a glow mushroom instead. */
    private static final int CAP_MUSHROOM_DROP_PERCENT = 35;
    /** How far sideways a cap block may sit from its stem. */
    private static final int STEM_SEARCH_RADIUS = 5;
    /** How far below the broken block the stem may start. */
    private static final int STEM_SEARCH_DOWN = 8;
    /** How far above - a cap block can sit below the topmost stem block. */
    private static final int STEM_SEARCH_UP = 2;

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    /** The planted mushroom. Emits a little light of its own, like a scaled-down shroomlight. */
    public static final DeferredBlock<GlowMushroomBlock> GLOW_MUSHROOM =
            BLOCKS.register("glow_mushroom", () -> new GlowMushroomBlock(HUGE_GLOW_MUSHROOM,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .noCollission()
                            .randomTicks()
                            .instabreak()
                            .lightLevel(state -> 12)
                            .sound(SoundType.GRASS)
                            .pushReaction(PushReaction.DESTROY)));

    /** Edible in the hand: a short Glowing effect, barely any nourishment. */
    public static final DeferredItem<BlockItem> GLOW_MUSHROOM_ITEM =
            ITEMS.register("glow_mushroom", () -> new BlockItem(GLOW_MUSHROOM.get(),
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.1F)
                            .effect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), 1.0F)
                            .build())));

    /** The stew: proper food, a long Glowing effect, and the bowl comes back. */
    public static final DeferredItem<Item> GLOW_MUSHROOM_STEW =
            ITEMS.register("glow_mushroom_stew", () -> new Item(
                    new Item.Properties()
                            .stacksTo(1)
                            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                            .food(new FoodProperties.Builder()
                                    .nutrition(6)
                                    .saturationModifier(0.6F)
                                    .usingConvertsTo(Items.BOWL)
                                    .effect(new MobEffectInstance(MobEffects.GLOWING, 600, 0), 1.0F)
                                    .build())));

    /**
     * Creates the module.
     */
    public GlowMushroomModule() {
        super("glow_mushroom",
                "Glow Mushroom",
                "A small glowing mushroom that can be planted and cooked into a glowing stew.",
                GlowMushroomConfig::new);
    }

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addAllToMainTab(GLOW_MUSHROOM_ITEM, GLOW_MUSHROOM_STEW);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Glow Mushroom module initialized");
    }

    /**
     * Injects the stew recipe. Recipes live in code in this project - datapack JSON does not load
     * reliably here (see CLAUDE.md).
     *
     * @param event The reload listener registration event
     */
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new StewRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /**
     * Bowl plus one glowing and one brown mushroom, shapeless - the vanilla stew recipe with the red
     * mushroom swapped out.
     *
     * @param recipeManager The server's recipe manager
     */
    private void applyStewRecipe(RecipeManager recipeManager) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.BOWL));
        ingredients.add(Ingredient.of(GLOW_MUSHROOM_ITEM.get()));
        ingredients.add(Ingredient.of(Items.BROWN_MUSHROOM));

        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC,
                new ItemStack(GLOW_MUSHROOM_STEW.get()), ingredients);
        RecipeHolder<ShapelessRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "glow_mushroom_stew"),
                recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }


    /**
     * Throttles what the cap of a big glow mushroom yields. Its blocks are plain vanilla
     * {@code shroomlight} and {@code honey_block}, so their loot tables cannot be touched without
     * changing them everywhere - a Nether shroomlight or a redstone builder's honey block must keep
     * behaving normally. The rule is therefore applied here and only when a mushroom stem stands
     * nearby, which is true inside a mushroom and essentially never anywhere else.
     *
     * <p>Silk Touch is honoured and returns the block untouched.</p>
     *
     * @param event The block drops event
     */
    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        var block = event.getState().getBlock();
        if (block != Blocks.SHROOMLIGHT && block != Blocks.HONEY_BLOCK) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !isPartOfMushroom(level, event.getPos())) {
            return;
        }
        var silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silk, event.getTool()) > 0) {
            return;
        }

        List<ItemEntity> drops = event.getDrops();
        ItemStack replacement = pickCapDrop(level, block);
        if (drops.isEmpty()) {
            return;
        }
        ItemEntity first = drops.get(0);
        drops.clear();
        if (!replacement.isEmpty()) {
            first.setItem(replacement);
            drops.add(first);
        }
    }

    /**
     * Rolls what a broken cap block yields: rarely the block itself, more often a glow mushroom,
     * usually nothing.
     *
     * @param level The level (for its random source)
     * @param block The broken block
     * @return the stack to drop, or an empty stack for nothing
     */
    private ItemStack pickCapDrop(ServerLevel level, net.minecraft.world.level.block.Block block) {
        int roll = level.getRandom().nextInt(100);
        if (roll < CAP_BLOCK_DROP_PERCENT) {
            return new ItemStack(block);
        }
        if (roll < CAP_BLOCK_DROP_PERCENT + CAP_MUSHROOM_DROP_PERCENT) {
            return new ItemStack(GLOW_MUSHROOM_ITEM.get());
        }
        return ItemStack.EMPTY;
    }

    /**
     * Whether a mushroom stem stands close enough for the broken block to be part of a mushroom.
     * The search box covers a cap's reach sideways and the stem below it.
     *
     * @param level The level
     * @param pos   The broken position
     * @return true if a mushroom stem is in range
     */
    private boolean isPartOfMushroom(ServerLevel level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-STEM_SEARCH_RADIUS, -STEM_SEARCH_DOWN, -STEM_SEARCH_RADIUS),
                pos.offset(STEM_SEARCH_RADIUS, STEM_SEARCH_UP, STEM_SEARCH_RADIUS))) {
            if (level.getBlockState(candidate).is(Blocks.MUSHROOM_STEM)) {
                return true;
            }
        }
        return false;
    }

    /** Runs {@link #applyStewRecipe} after every datapack reload. */
    private final class StewRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private StewRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyStewRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_glow_mushroom_stew_recipe";
        }
    }
}
