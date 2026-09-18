package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.config.CompassOverhaulConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Compass Overhaul Module
 *
 * <p>Four repairs around the compass, all switchable on their own:
 *
 * <ul>
 *   <li><strong>Lodestone binding.</strong> Vanilla's {@code LodestoneTracker.tick} asks the POI
 *       manager whether a lodestone still stands at the tracked position — every tick, without ever
 *       checking that the chunk is loaded. {@code PoiManager.exists} turns "no data" into "no
 *       lodestone", so an unloaded chunk, an unreadable POI file or a single failed read wipes the
 *       coordinates out of the item, irreversibly. The mixin decides that question from the block
 *       itself and keeps the binding whenever there is no proof either way.</li>
 *   <li><strong>The needle inside sub-levels.</strong> Sable already rotates the needle for Create
 *       Aeronautics airships, but reads the ship's pose from the previous tick and locates the ship
 *       only through the viewer's chunk position. We redo that with the interpolated render pose and
 *       the tracked sub-level, so the needle neither jitters nor gives up when riding a seat.</li>
 *   <li><strong>Quark's replacement needle.</strong> Quark's <em>Compasses Work Everywhere</em>
 *       registers its own angle function for the compass, so for an ordinary compass none of the
 *       above is reached at all. It aims at the raw block position — the corner with the smallest X
 *       and Z, the north-west one — instead of the block's centre, knows nothing about sub-levels,
 *       and drops an item frame's rotation steps. All three are corrected in place.</li>
 *   <li><strong>The World Compass.</strong> A second compass that points at world north instead of
 *       a target — in every dimension, and aboard a ship that turns underneath you.</li>
 * </ul>
 *
 * <p>Nothing outside {@code compat} references a Sable class, so the module links and runs fine
 * without Sable installed.
 */
public class CompassOverhaulModule
        extends AbstractModule<CompassOverhaulModule, CompassOverhaulConfig> {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    /**
     * A compass that points at world north instead of a target — in every dimension, and aboard a
     * turning airship. The needle logic lives in the client-side property function; the item itself
     * carries no state at all.
     */
    public static final DeferredItem<Item> WORLD_COMPASS =
            ITEMS.register("world_compass", () -> new Item(new Item.Properties()));

    private static CompassOverhaulModule instance;

    public CompassOverhaulModule() {
        super("compass_overhaul",
                "Compass Overhaul",
                "Keeps lodestone bindings, fixes the needle aboard airships and adds a compass that "
                        + "always points north",
                CompassOverhaulConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addToMainTab(WORLD_COMPASS);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Compass Overhaul module initialized - lodestone bindings guarded, World Compass registered");
    }

    /**
     * Injects the World Compass recipe. Datapack JSON does not load reliably in this mod, so
     * recipes are built in code and re-applied on every reload.
     *
     * @param event the server resource reload event
     */
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled() || !getConfig().isWorldCompassEnabledValue()) {
            return;
        }
        event.addListener(new WorldCompassRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /** A compass ringed by three ender eyes with an amethyst shard on top → one World Compass. */
    private void applyWorldCompassRecipe(RecipeManager recipeManager) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('A', Ingredient.of(Items.AMETHYST_SHARD));
        key.put('E', Ingredient.of(Items.ENDER_EYE));
        key.put('C', Ingredient.of(Items.COMPASS));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of(" A ", "ECE", " E "));
        ItemStack result = new ItemStack(WORLD_COMPASS.get());
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "world_compass"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    private final class WorldCompassRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private WorldCompassRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyWorldCompassRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_world_compass_recipe";
        }
    }

    /**
     * Mixin gate: whether the module is registered and enabled at all.
     *
     * @return true if the module is active
     */
    public static boolean isActive() {
        CompassOverhaulModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * Whether a lodestone binding is kept unless the lodestone is provably gone.
     *
     * @return true if the binding should be guarded (default true)
     */
    public static boolean guardsLodestone() {
        CompassOverhaulModule module = instance;
        return isActive() && module.getConfig().isGuardLodestoneBindingValue();
    }

    /**
     * Whether the compass needle is corrected inside Sable sub-levels.
     *
     * @return true if the sub-level correction should run (default true)
     */
    public static boolean fixesSubLevelNeedle() {
        CompassOverhaulModule module = instance;
        return isActive() && module.getConfig().isFixSubLevelNeedleValue();
    }

    /**
     * Whether Quark's replacement compass needle is repaired.
     *
     * @return true if Quark's needle should be corrected (default true)
     */
    public static boolean fixesQuarkCompass() {
        CompassOverhaulModule module = instance;
        return isActive() && module.getConfig().isFixQuarkCompassValue();
    }

    /**
     * Whether the World Compass item and its recipe are available.
     *
     * @return true if the World Compass is enabled (default true)
     */
    public static boolean worldCompassEnabled() {
        CompassOverhaulModule module = instance;
        return isActive() && module.getConfig().isWorldCompassEnabledValue();
    }
}
