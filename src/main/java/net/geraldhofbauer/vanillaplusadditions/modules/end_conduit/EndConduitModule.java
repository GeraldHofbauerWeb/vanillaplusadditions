package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.block.EndConduitBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.blockentity.EndConduitBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.config.EndConduitConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.SoundType;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * End Conduit module: an End-only conduit upgrade. A distinct, craftable item that places a block
 * rendered identically to the vanilla conduit (same texture + animation, via a copied renderer) but
 * which activates only in {@code minecraft:the_end}, forms its frame from Glowstone / End Stone /
 * End Stone Bricks / Sea Lantern, needs no water, and grants Conduit Power on dry land. Combined
 * with the {@code end_oxygen} module's Conduit-Power breathing branch, standing in its radius gives
 * effectively unlimited air in the End.
 *
 * <p>Crafting: chorus fruit in the corners, eyes of ender on the edges, a vanilla conduit in the
 * centre.
 */
public class EndConduitModule extends AbstractModule<EndConduitModule, EndConduitConfig> {

    // ---- Deferred registers ----

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VanillaPlusAdditions.MODID);

    // ---- Registered content ----

    public static final DeferredBlock<EndConduitBlock> END_CONDUIT =
            BLOCKS.register("end_conduit", () -> new EndConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.0F, 3.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 15)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> END_CONDUIT_ITEM =
            ITEMS.register("end_conduit", () -> new BlockItem(END_CONDUIT.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndConduitBlockEntity>> END_CONDUIT_BE =
            BLOCK_ENTITY_TYPES.register("end_conduit",
                    () -> BlockEntityType.Builder.of(EndConduitBlockEntity::new, END_CONDUIT.get()).build(null));

    // ---- Singleton for static access from the block entity ----

    private static EndConduitModule instance;

    public EndConduitModule() {
        super("end_conduit",
                "End Conduit",
                "An End-only conduit upgrade: activates in the End, grants Conduit Power (and thus air) "
                        + "with a Glowstone/End Stone/End Stone Bricks/Sea Lantern frame, no water needed.",
                EndConduitConfig::new);
        instance = this;
    }

    /**
     * @return true if the module is registered and enabled (mixin/BE gate).
     */
    public static boolean isModuleActive() {
        EndConduitModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * @return the minimum frame-block count needed to activate (config, default 16).
     */
    public static int minFrames() {
        EndConduitModule module = instance;
        return module != null ? module.getConfig().getMinFrames() : 16;
    }

    /**
     * Conduit Power radius for a conduit with the given frame-block count: the vanilla effect radius
     * ({@code frames / 7 * 16}) divided by the configured divisor.
     *
     * @param frames number of active frame blocks
     * @return the effect radius in blocks (at least 1)
     */
    public static int effectRadius(int frames) {
        EndConduitModule module = instance;
        int divisor = module != null ? Math.max(1, module.getConfig().getEffectRadiusDivisor()) : 1;
        return Math.max(1, frames / 7 * 16 / divisor);
    }

    // ---- Lifecycle ----

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        BLOCK_ENTITY_TYPES.register(getModEventBus());

        VanillaPlusCreativeTabs.addToMainTab(END_CONDUIT_ITEM);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("End Conduit module initialized");
    }

    // ---- Recipe injection (in-code, no JSON — see CLAUDE.md) ----

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new EndConduitRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyEndConduitRecipe(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }

        // F E F  — F = chorus fruit (corners), E = eye of ender (edges), C = vanilla conduit (centre)
        // E C E
        // F E F
        Map<Character, Ingredient> key = Map.of(
                'F', Ingredient.of(Items.CHORUS_FRUIT),
                'E', Ingredient.of(Items.ENDER_EYE),
                'C', Ingredient.of(Items.CONDUIT));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, "FEF", "ECE", "FEF");
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern,
                new ItemStack(END_CONDUIT_ITEM.get()));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "end_conduit");
        merged.put(id, new RecipeHolder<>(id, recipe));

        recipeManager.replaceRecipes(merged.values());
    }

    private final class EndConduitRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private EndConduitRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyEndConduitRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_end_conduit_recipes";
        }
    }
}
