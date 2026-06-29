package net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.block.ChunkAnchorBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.config.StationaryChunkLoaderConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Adds a "Chunk Anchor" block that permanently keeps the chunk it stands in (plus a configurable
 * radius) loaded — a stationary chunk loader for redstone/Create contraptions that must keep ticking
 * even when no player is nearby. By default it only loads while at least one player is online.
 *
 * <p>This complements the minecart loader rail (which only loads transiently around traveling
 * carts); for a fixed contraption the anchor is the robust choice.</p>
 */
public class StationaryChunkLoaderModule
        extends AbstractModule<StationaryChunkLoaderModule, StationaryChunkLoaderConfig> {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    public static final DeferredBlock<ChunkAnchorBlock> CHUNK_ANCHOR =
            BLOCKS.register("chunk_anchor", () -> new ChunkAnchorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_CYAN)
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> CHUNK_ANCHOR_ITEM =
            ITEMS.register("chunk_anchor",
                    () -> new BlockItem(CHUNK_ANCHOR.get(), new Item.Properties()));

    private static StationaryChunkLoaderModule instance;

    private final StationaryChunkLoaderManager manager = new StationaryChunkLoaderManager();

    /** Whether force-loading is currently active (server-wide player gate). */
    private boolean forcingEnabled = false;

    public StationaryChunkLoaderModule() {
        super("stationary_chunk_loader",
                "Stationary Chunk Loader",
                "Chunk Anchor block keeps its chunk loaded for redstone/Create contraptions.",
                StationaryChunkLoaderConfig::new);
        instance = this;
    }

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addToMainTab(CHUNK_ANCHOR_ITEM);

        getModEventBus().addListener(this::onRegisterTicketControllers);
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Stationary Chunk Loader module initialized");
    }

    @Override
    protected void onClientSetup() {
        // Plug the anchor-border renderer into the shared debug overlay framework (green = active,
        // grey = inactive), distinct from the loader-rail borders.
        net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRegistry.register(
                new net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.client
                        .AnchorBorderRenderer());
    }

    /** Client accessor for the overlay: forced radius (Chebyshev) around each anchor. */
    public static int getChunkLoadRadius() {
        return instance != null ? instance.getConfig().getChunkLoadRadius() : 0;
    }

    private void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        TicketController controller = new TicketController(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "stationary_chunk_loader"),
                // On world load, drop every ticket we own: anchors are rebuilt from persistent data.
                (level, helper) -> new ArrayList<>(helper.getBlockTickets().keySet())
                        .forEach(owner -> helper.removeAllTickets(owner)));
        event.register(controller);
        manager.setController(controller);
    }

    /**
     * Server-wide player gate: enables force-loading while players are online (config). On the
     * transition into "enabled" (server start / first join) it resumes persisted anchors; on the
     * transition into "disabled" (last player left) it pauses.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        boolean playersOnline = server.getPlayerList().getPlayerCount() > 0;
        boolean shouldLoad = !getConfig().isOnlyWhilePlayersOnline() || playersOnline;

        if (shouldLoad && !forcingEnabled) {
            int radius = getConfig().getChunkLoadRadius();
            for (ServerLevel level : server.getAllLevels()) {
                manager.resume(level, radius);
            }
        } else if (!shouldLoad && forcingEnabled) {
            for (ServerLevel level : server.getAllLevels()) {
                manager.releaseAll(level);
            }
        }
        forcingEnabled = shouldLoad;
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            manager.forgetLevel(level);
        }
    }

    // ---- Block callbacks (from ChunkAnchorBlock) ----
    // An anchor force-loads its chunk only while redstone-powered; the block reports the
    // active/inactive transitions (place-while-powered, redstone on/off, broken).

    /** Anchor became active (placed powered, or redstone turned on): persist + force-load. */
    public static void onAnchorActive(ServerLevel level, BlockPos pos) {
        if (instance == null || !instance.isModuleEnabled()) {
            return;
        }
        instance.manager.addAnchor(level, pos, instance.getConfig().getChunkLoadRadius(),
                instance.forcingEnabled);
    }

    /** Anchor became inactive (redstone turned off, or broken): forget + release its chunk. */
    public static void onAnchorInactive(ServerLevel level, BlockPos pos) {
        if (instance == null) {
            return;
        }
        instance.manager.removeAnchor(level, pos);
    }

    // ---- Crafting recipe (registered in code, gated on the module being enabled) ----
    // JSON datapack recipes don't load reliably in this mod (see CLAUDE.md), so register in code.

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new ChunkAnchorRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /** 4 iron + 4 ender pearls ringing 1 ender eye → 1 chunk anchor. */
    private void applyChunkAnchorRecipe(RecipeManager recipeManager) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('I', Ingredient.of(Items.IRON_INGOT));
        key.put('P', Ingredient.of(Items.ENDER_PEARL));
        key.put('Y', Ingredient.of(Items.ENDER_EYE));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of("IPI", "PYP", "IPI"));
        ItemStack result = new ItemStack(CHUNK_ANCHOR_ITEM.get(), 1);
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "chunk_anchor"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    private final class ChunkAnchorRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private ChunkAnchorRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyChunkAnchorRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_chunk_anchor_recipe";
        }
    }
}
