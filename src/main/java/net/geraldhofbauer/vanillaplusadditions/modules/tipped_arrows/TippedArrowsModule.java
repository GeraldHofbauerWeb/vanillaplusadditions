package net.geraldhofbauer.vanillaplusadditions.modules.tipped_arrows;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.tipped_arrows.recipe.PotionTippedArrowRecipe;
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
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lets tipped arrows be crafted from a normal potion instead of a lingering potion, so players
 * don't need Dragon's Breath just to tip arrows. Replaces vanilla's {@code minecraft:tipped_arrow}
 * special recipe in-place (see {@link PotionTippedArrowRecipe}) — lingering potions still work
 * too, existing stock isn't stranded.
 */
public class TippedArrowsModule
        extends AbstractModule<TippedArrowsModule, AbstractModuleConfig.DefaultModuleConfig<TippedArrowsModule>> {

    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, VanillaPlusAdditions.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<PotionTippedArrowRecipe>>
            TIPPED_ARROW_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("potion_tipped_arrow",
                    () -> new SimpleCraftingRecipeSerializer<>(PotionTippedArrowRecipe::new));

    public TippedArrowsModule() {
        super("tipped_arrows",
                "Tipped Arrows from Potions",
                "Crafts tipped arrows from a normal potion (lingering potions still work too).",
                AbstractModuleConfig::createDefault
        );
    }

    @Override
    protected void onInitialize() {
        RECIPE_SERIALIZERS.register(getModEventBus());
        NeoForge.EVENT_BUS.register(this);
    }

    // ---- Crafting recipe (registered in code, gated on the module being enabled) ----
    // Overrides vanilla's minecraft:tipped_arrow special recipe. JSON datapack recipes don't
    // load reliably in this mod (see CLAUDE.md), so this goes through RecipeManager injection
    // like the other code-registered recipes (e.g. MinecartChunkLoadingModule).

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new TippedArrowRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyTippedArrowRecipe(RecipeManager recipeManager) {
        PotionTippedArrowRecipe recipe = new PotionTippedArrowRecipe(CraftingBookCategory.MISC);
        RecipeHolder<PotionTippedArrowRecipe> holder =
                new RecipeHolder<>(ResourceLocation.withDefaultNamespace("tipped_arrow"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    private final class TippedArrowRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private TippedArrowRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyTippedArrowRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_tipped_arrow_recipe";
        }
    }
}
