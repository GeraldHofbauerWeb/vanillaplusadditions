package net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.block.ChunkLoaderRailBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.config.MinecartChunkLoadingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
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
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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
 * Adds a "Chunk Loader Rail" that keeps chunks loaded around minecarts while they travel, so
 * long-distance rail networks don't stall at chunk borders. Chunks are forced only while a cart
 * is active on a loader rail and released after a timeout (see {@link ChunkLoaderManager}).
 */
public class MinecartChunkLoadingModule
        extends AbstractModule<MinecartChunkLoadingModule, MinecartChunkLoadingConfig> {

    /** Reconcile cadence in ticks (force/release chunks). */
    private static final int RECONCILE_INTERVAL = 10;

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    public static final DeferredBlock<ChunkLoaderRailBlock> CHUNK_LOADER_RAIL =
            BLOCKS.register("chunk_loader_rail", () -> new ChunkLoaderRailBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_CYAN)
                            .noCollission()
                            .strength(0.7F)
                            .sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> CHUNK_LOADER_RAIL_ITEM =
            ITEMS.register("chunk_loader_rail",
                    () -> new BlockItem(CHUNK_LOADER_RAIL.get(), new Item.Properties()));

    private static MinecartChunkLoadingModule instance;

    private final ChunkLoaderManager manager = new ChunkLoaderManager();

    public MinecartChunkLoadingModule() {
        super("minecart_chunk_loading",
                "Minecart Chunk Loading",
                "Chunk Loader Rail keeps chunks loaded around traveling minecarts.",
                MinecartChunkLoadingConfig::new);
        instance = this;
    }

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addToMainTab(CHUNK_LOADER_RAIL_ITEM);

        getModEventBus().addListener(this::onRegisterTicketControllers);
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Minecart Chunk Loading module initialized");
    }

    @Override
    protected void onClientSetup() {
        // Plug the chunk-border renderer into the shared debug overlay framework.
        net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.DebugOverlayRegistry.register(
                new net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.client
                        .ChunkLoaderBorderRenderer());
    }

    private void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        TicketController controller = new TicketController(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "minecart_chunk_loading"),
                // On world load, drop every ticket we own: active state is rebuilt from cart movement.
                (level, helper) -> new ArrayList<>(helper.getBlockTickets().keySet())
                        .forEach(owner -> helper.removeAllTickets(owner)));
        event.register(controller);
        manager.setController(controller);
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractMinecart cart)) {
            return;
        }
        if (!(cart.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos railPos = railAt(level, cart.blockPosition());
        if (railPos != null) {
            manager.markActive(level, railPos, level.getGameTime());
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!isModuleEnabled()) {
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

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            manager.forgetLevel(level);
        }
    }

    // ---- Crafting recipe (registered in code, gated on the module being enabled) ----
    // Our own block's recipe lives in this module so the rail is always craftable while the
    // module is active. JSON datapack recipes don't load reliably in this mod (see CLAUDE.md).

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new ChunkLoaderRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /** 8 powered rails ringed around 1 ender pearl → 8 chunk loader rails. */
    private void applyChunkLoaderRailRecipe(RecipeManager recipeManager) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('R', Ingredient.of(Items.POWERED_RAIL));
        key.put('E', Ingredient.of(Items.ENDER_PEARL));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of("RRR", "RER", "RRR"));
        ItemStack result = new ItemStack(CHUNK_LOADER_RAIL_ITEM.get(), 8);
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "chunk_loader_rail"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    private final class ChunkLoaderRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private ChunkLoaderRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyChunkLoaderRailRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_chunk_loader_rail_recipe";
        }
    }

    /** Returns the loader-rail position the cart sits on (same block or directly below), or null. */
    private static BlockPos railAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ChunkLoaderRailBlock) {
            return pos;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).getBlock() instanceof ChunkLoaderRailBlock) {
            return below;
        }
        return null;
    }

    // ---- Static accessors for the client renderer ----

    public static int getChunkLoadRadius() {
        return instance != null ? instance.getConfig().getChunkLoadRadius() : 1;
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
