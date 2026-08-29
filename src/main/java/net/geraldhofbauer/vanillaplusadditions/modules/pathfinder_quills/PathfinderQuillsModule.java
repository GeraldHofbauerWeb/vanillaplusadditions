package net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.recipe.PathfinderQuillRecipe;
import net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.recipe.PathfinderQuillRecipeSerializer;
import net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.recipe.PathfinderQuillRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lets players craft Quark's Pathfinder's Quill (normally trade-only) with a Feather, an Eye of
 * Ender, and a block tied to the target biome — see {@link PathfinderQuillRecipes} for the full
 * biome/block/color table. Inactive without Quark; {@link #shouldInitialize()} keeps every
 * Quark-referencing class in this module (the recipe, its serializer) from ever being
 * classloaded when Quark is absent.
 */
public class PathfinderQuillsModule
        extends AbstractModule<PathfinderQuillsModule, AbstractModuleConfig.DefaultModuleConfig<PathfinderQuillsModule>> {

    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, VanillaPlusAdditions.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, PathfinderQuillRecipeSerializer> QUILL_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("pathfinder_quill", PathfinderQuillRecipeSerializer::new);

    public PathfinderQuillsModule() {
        super("pathfinder_quills",
                "Pathfinder Quills",
                "Craft Quark's biome-targeted Pathfinder's Quills instead of only trading for them.",
                AbstractModuleConfig::createDefault
        );
    }

    @Override
    protected boolean shouldInitialize() {
        return ModList.get().isLoaded("quark");
    }

    @Override
    protected void onInitialize() {
        RECIPE_SERIALIZERS.register(getModEventBus());
        NeoForge.EVENT_BUS.register(this);
    }

    // ---- Crafting recipes (registered in code, gated on the module being enabled) ----
    // Pure additions, no vanilla recipe to override. JSON datapack recipes don't load reliably
    // in this mod (see CLAUDE.md), so this goes through RecipeManager injection like the other
    // code-registered recipes (e.g. MinecartChunkLoadingModule).

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new QuillRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyQuillRecipes(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }

        for (PathfinderQuillRecipes.BiomeEntry entry : PathfinderQuillRecipes.ENTRIES) {
            PathfinderQuillRecipe recipe = new PathfinderQuillRecipe(CraftingBookCategory.MISC, entry.biomeId());
            ResourceLocation biomeId = ResourceLocation.parse(entry.biomeId());
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "pathfinder_quill_" + biomeId.getPath());
            merged.put(recipeId, new RecipeHolder<>(recipeId, recipe));
        }

        recipeManager.replaceRecipes(merged.values());
    }

    private final class QuillRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private QuillRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyQuillRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_pathfinder_quill_recipes";
        }
    }
}
