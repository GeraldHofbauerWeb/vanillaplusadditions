package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.compat.ChunkLoaderTrackCompat;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.compat.TrainChunkLoadingEvents;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.config.TrainChunkLoadingConfig;
import net.geraldhofbauer.vanillaplusadditions.util.chunkload.ChunkLoaderManager;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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
 * Adds a "Chunk Loader Track" — a Create train track variant that keeps chunks loaded around
 * trains passing over it, so onboard machines, signals and schedules keep working far from
 * players. Create itself only "simulates" trains through unloaded chunks. Chunks are forced
 * while a carriage is over a loader track and released after a timeout, exactly mirroring the
 * minecart module's Chunk Loader Rail semantics (see {@link ChunkLoaderManager}).
 *
 * <p>All Create-typed code lives in {@code compat}/{@code block}/{@code client} classes that are
 * only classloaded once {@link #shouldInitialize()} confirmed Create is present.</p>
 */
public class TrainChunkLoadingModule
        extends AbstractModule<TrainChunkLoadingModule, TrainChunkLoadingConfig> {

    /** Reconcile cadence in ticks (force/release chunks). */
    private static final int RECONCILE_INTERVAL = 10;

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    // Vanilla-typed on purpose: the suppliers only classload the Create-typed compat class when
    // invoked, and the registers are only bound to the bus in onInitialize() (Create present).
    public static final DeferredBlock<Block> CHUNK_LOADER_TRACK =
            BLOCKS.register("chunk_loader_track", ChunkLoaderTrackCompat::createBlock);

    public static final DeferredItem<BlockItem> CHUNK_LOADER_TRACK_ITEM =
            ITEMS.register("chunk_loader_track", ChunkLoaderTrackCompat::createItem);

    private static TrainChunkLoadingModule instance;

    private final ChunkLoaderManager manager = new ChunkLoaderManager("vanillaplusadditions_train_chunk_loader");

    /** Whether force-loading is currently active (server-wide player gate). */
    private boolean forcingEnabled = false;

    public TrainChunkLoadingModule() {
        super("train_chunk_loading",
                "Train Chunk Loading",
                "Chunk Loader Track keeps chunks loaded around traveling Create trains.",
                TrainChunkLoadingConfig::new);
        instance = this;
    }

    @Override
    protected boolean shouldInitialize() {
        return ModList.get().isLoaded("create");
    }

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addToMainTab(CHUNK_LOADER_TRACK_ITEM);

        getModEventBus().addListener(this::onRegisterTicketControllers);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new TrainChunkLoadingEvents(this));

        getLogger().info("Train Chunk Loading module initialized");
    }

    @Override
    protected void onCommonSetup() {
        // Let Create's track block entity accept our block, so curved-connection BEs persist.
        ChunkLoaderTrackCompat.extendTrackBlockEntityType(CHUNK_LOADER_TRACK.get(), getLogger());
    }

    @Override
    protected void onClientSetup() {
        // Plug the chunk-border renderer into the shared debug overlay framework.
        net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRegistry.register(
                new net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client
                        .ChunkLoaderTrackBorderRenderer());
        // Ponder entry (hold W on the item): explains what the track does and how to space them.
        net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client
                .ChunkLoaderTrackPonder.register();
    }

    public ChunkLoaderManager getManager() {
        return manager;
    }

    private void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        TicketController controller = new TicketController(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "train_chunk_loading"),
                // On world load, drop every ticket we own: active state is rebuilt from train movement.
                (level, helper) -> new ArrayList<>(helper.getBlockTickets().keySet())
                        .forEach(owner -> helper.removeAllTickets(owner)));
        event.register(controller);
        manager.setController(controller);
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!isModuleEnabled() || !forcingEnabled) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now % RECONCILE_INTERVAL != 0) {
            return;
        }
        manager.reconcile(level, now, getConfig().getChunkLoadRadius(),
                getConfig().getActiveTimeoutSeconds() * 20L);
    }

    /**
     * Server-wide player gate: enables force-loading while players are online (config), and on the
     * transition into "enabled" (server start / first join) resumes the persisted track chunks so
     * waiting trains continue. On the transition into "disabled" (last player left) it pauses.
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

    // ---- Crafting recipe (registered in code, gated on the module being enabled) ----
    // Our own block's recipe lives in this module so the track is always craftable while the
    // module is active. JSON datapack recipes don't load reliably in this mod (see CLAUDE.md).

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new ChunkLoaderTrackRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /** 8 Create train tracks ringed around 1 ender pearl → 8 chunk loader tracks. */
    private void applyChunkLoaderTrackRecipe(RecipeManager recipeManager) {
        Item createTrack = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "track"));
        if (createTrack == Items.AIR) {
            getLogger().warn("create:track item not found — skipping Chunk Loader Track recipe");
            return;
        }
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('T', Ingredient.of(createTrack));
        key.put('E', Ingredient.of(Items.ENDER_PEARL));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of("TTT", "TET", "TTT"));
        ItemStack result = new ItemStack(CHUNK_LOADER_TRACK_ITEM.get(), 8);
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "chunk_loader_track"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    private final class ChunkLoaderTrackRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private ChunkLoaderTrackRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyChunkLoaderTrackRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_chunk_loader_track_recipe";
        }
    }

    // ---- Static accessors for the client renderer ----

    public static int getChunkLoadRadius() {
        return instance != null ? instance.getConfig().getChunkLoadRadius() : 2;
    }

    public static long getActiveTimeoutTicks() {
        return (instance != null ? instance.getConfig().getActiveTimeoutSeconds() : 15) * 20L;
    }

    public static int getChunkBorderScanRadius() {
        return instance != null ? instance.getConfig().getChunkBorderScanRadius() : 8;
    }

    public static int getChunkBorderVerticalSpan() {
        return instance != null ? instance.getConfig().getChunkBorderVerticalSpan() : 24;
    }
}
