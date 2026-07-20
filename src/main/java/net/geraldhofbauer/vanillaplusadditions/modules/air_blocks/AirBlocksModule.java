package net.geraldhofbauer.vanillaplusadditions.modules.air_blocks;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.block.AirBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.config.AirBlocksConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Adds "Air Blocks": invisible, craftable blocks that act like solid air. Entities (players, mobs,
 * items, projectiles) pass straight through them because their collision shape is empty, but they
 * occupy their cell so fluids (water, lava, modded) cannot flow into or through them — perfect for
 * invisible dams or water-tight, walk-through barriers.
 *
 * <p>Air Blocks render invisibly like a Barrier; a separate client-side Air Block Revealer tool
 * shows nearby Air Blocks as translucent boxes while it is held so they can be found and removed
 * (removal = mining them normally, which returns the item).</p>
 */
public class AirBlocksModule extends AbstractModule<AirBlocksModule, AirBlocksConfig> {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    public static final DeferredBlock<AirBlock> AIR_BLOCK =
            BLOCKS.register("air_block", () -> new AirBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(1.0F, 3.0F)
                            .sound(SoundType.WOOL)
                            // forceSolidOn(): the block is treated as "solid" (so water, lava and modded
                            // fluids cannot flow into its cell, and Create sees a normal, glueable,
                            // contraption-movable block) even though AirBlock#getCollisionShape is empty so
                            // entities still pass straight through.
                            .forceSolidOn()
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .pushReaction(PushReaction.NORMAL)));

    public static final DeferredItem<BlockItem> AIR_BLOCK_ITEM =
            ITEMS.register("air_block",
                    () -> new BlockItem(AIR_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<Item> AIR_BLOCK_REVEALER =
            ITEMS.register("air_block_revealer",
                    () -> new Item(new Item.Properties().stacksTo(1)));

    private static AirBlocksModule instance;

    public AirBlocksModule() {
        super("air_blocks",
                "Air Blocks",
                "Invisible, walk-through blocks that hold back fluids; revealed by a held tool.",
                AirBlocksConfig::new);
        instance = this;
    }

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addAllToMainTab(AIR_BLOCK_ITEM, AIR_BLOCK_REVEALER);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Air Blocks module initialized");
    }

    @Override
    protected void onClientSetup() {
        net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.client.AirBlockRevealRenderer.register();
    }

    /** Client accessor for the reveal renderer: chunk scan radius around the player. */
    public static int getRevealRadius() {
        return instance != null ? instance.getConfig().getRevealRadius() : 6;
    }

    // ---- Crafting recipe (registered in code, gated on the module being enabled) ----
    // JSON datapack recipes don't load reliably in this mod (see CLAUDE.md), so register in code.

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled() || !getConfig().isCraftingEnabled()) {
            return;
        }
        event.addListener(new AirBlockRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /**
     * Registers the in-code crafting recipes:
     * <ul>
     *   <li>8 glass panes ringing 1 phantom membrane → 8 air blocks;</li>
     *   <li>1 ender eye above 1 spyglass → 1 air block revealer (thematically "see-through/track").</li>
     * </ul>
     */
    private void applyAirBlockRecipe(RecipeManager recipeManager) {
        // Air Block: 8 glass panes ringing 1 phantom membrane → 8 air blocks.
        Map<Character, Ingredient> blockKey = new LinkedHashMap<>();
        blockKey.put('G', Ingredient.of(Items.GLASS_PANE));
        blockKey.put('M', Ingredient.of(Items.PHANTOM_MEMBRANE));
        ShapedRecipePattern blockPattern = ShapedRecipePattern.of(blockKey, List.of("GGG", "GMG", "GGG"));
        ShapedRecipe blockRecipe = new ShapedRecipe("", CraftingBookCategory.BUILDING, blockPattern,
                new ItemStack(AIR_BLOCK_ITEM.get(), 8));
        RecipeHolder<ShapedRecipe> blockHolder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "air_block"), blockRecipe);

        // Air Block Revealer: 1 ender eye above 1 spyglass → 1 revealer.
        Map<Character, Ingredient> revealerKey = new LinkedHashMap<>();
        revealerKey.put('E', Ingredient.of(Items.ENDER_EYE));
        revealerKey.put('S', Ingredient.of(Items.SPYGLASS));
        ShapedRecipePattern revealerPattern = ShapedRecipePattern.of(revealerKey, List.of("E", "S"));
        ShapedRecipe revealerRecipe = new ShapedRecipe("", CraftingBookCategory.EQUIPMENT, revealerPattern,
                new ItemStack(AIR_BLOCK_REVEALER.get(), 1));
        RecipeHolder<ShapedRecipe> revealerHolder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "air_block_revealer"),
                revealerRecipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(blockHolder.id(), blockHolder);
        merged.put(revealerHolder.id(), revealerHolder);
        recipeManager.replaceRecipes(merged.values());
    }

    private final class AirBlockRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private AirBlockRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyAirBlockRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_air_block_recipe";
        }
    }
}
