package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.MobLoaderBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.MobUnloaderBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.MobLoaderBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.MobUnloaderBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.config.MobCartLoaderConfig;
import net.minecraft.core.registries.Registries;
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
 * Mob Cart Loader module: two blocks that automate loading/unloading mobs into minecarts on the
 * adjacent rail.
 *
 * <ul>
 *   <li><b>Mob Loader</b> — boards a mob standing in the adjacent pen into a parked, empty rideable
 *       minecart on its facing side.</li>
 *   <li><b>Mob Unloader</b> — ejects a mob riding a parked minecart into the adjacent pen.</li>
 * </ul>
 *
 * <p>Both blocks are active by default and disabled by a redstone signal (inverse control), face the
 * rail (FACING), only act on directly adjacent mobs/carts, and never touch players. Each shows the
 * relevant mob as a spinning mini-model (BER); with Create's Engineer's Goggles a stats panel appears
 * (mob type + icon, health on sneak). The module has no hard Create dependency.</p>
 */
public class MobCartLoaderModule extends AbstractModule<MobCartLoaderModule, MobCartLoaderConfig> {

    // ---- Deferred registers ----

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VanillaPlusAdditions.MODID);

    // ---- Registered content ----

    public static final DeferredBlock<MobLoaderBlock> MOB_LOADER =
            BLOCKS.register("mob_loader", () -> new MobLoaderBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GREEN)
                            .strength(1.5F, 3.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> MOB_LOADER_ITEM =
            ITEMS.register("mob_loader", () -> new BlockItem(MOB_LOADER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobLoaderBlockEntity>> MOB_LOADER_BE =
            BLOCK_ENTITY_TYPES.register("mob_loader",
                    () -> BlockEntityType.Builder.of(MobLoaderBlockEntity::new, MOB_LOADER.get()).build(null));

    public static final DeferredBlock<MobUnloaderBlock> MOB_UNLOADER =
            BLOCKS.register("mob_unloader", () -> new MobUnloaderBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(1.5F, 3.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> MOB_UNLOADER_ITEM =
            ITEMS.register("mob_unloader", () -> new BlockItem(MOB_UNLOADER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobUnloaderBlockEntity>> MOB_UNLOADER_BE =
            BLOCK_ENTITY_TYPES.register("mob_unloader",
                    () -> BlockEntityType.Builder.of(MobUnloaderBlockEntity::new, MOB_UNLOADER.get()).build(null));

    // ---- Singleton for static config access ----

    private static MobCartLoaderModule instance;

    public MobCartLoaderModule() {
        super("mob_cart_loader",
                "Mob Cart Loader",
                "Two blocks that load/unload mobs into minecarts on the adjacent rail, showing the mob "
                        + "as a spinning model and (with Create goggles) its stats.",
                MobCartLoaderConfig::new);
        instance = this;
    }

    /**
     * @return the configured block-entity scan cadence in ticks (default 5).
     */
    public static int getCheckIntervalTicks() {
        MobCartLoaderModule module = instance;
        return module != null ? module.getConfig().getCheckIntervalTicks() : 5;
    }

    // ---- Lifecycle ----

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        BLOCK_ENTITY_TYPES.register(getModEventBus());

        VanillaPlusCreativeTabs.addToMainTab(MOB_LOADER_ITEM);
        VanillaPlusCreativeTabs.addToMainTab(MOB_UNLOADER_ITEM);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Mob Cart Loader module initialized");
    }

    // ---- Recipe injection (in-code, no JSON — see CLAUDE.md) ----

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new MobCartLoaderRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyRecipes(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }

        // Glass on the top and bottom rows, the functional trio in the middle.
        // Loader:  hopper | saddle | minecart  (intake).
        addShaped(merged, "mob_loader", new ItemStack(MOB_LOADER_ITEM.get()),
                Map.of('G', Ingredient.of(Items.GLASS),
                        'H', Ingredient.of(Items.HOPPER),
                        'S', Ingredient.of(Items.SADDLE),
                        'M', Ingredient.of(Items.MINECART)),
                "GGG", "HSM", "GGG");
        // Unloader: minecart | saddle | dropper  (eject).
        addShaped(merged, "mob_unloader", new ItemStack(MOB_UNLOADER_ITEM.get()),
                Map.of('G', Ingredient.of(Items.GLASS),
                        'M', Ingredient.of(Items.MINECART),
                        'S', Ingredient.of(Items.SADDLE),
                        'D', Ingredient.of(Items.DROPPER)),
                "GGG", "MSD", "GGG");

        recipeManager.replaceRecipes(merged.values());
    }

    private static void addShaped(Map<ResourceLocation, RecipeHolder<?>> merged, String path,
                                  ItemStack result, Map<Character, Ingredient> key, String... rows) {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, rows);
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, path);
        merged.put(id, new RecipeHolder<>(id, recipe));
    }

    private final class MobCartLoaderRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private MobCartLoaderRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_mob_cart_loader_recipes";
        }
    }
}
