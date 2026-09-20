package net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.config.MoArrowsConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.item.FireArrowItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Mo' Arrows — arrows vanilla does not have.
 *
 * <p>So far there is one: the <strong>Fire Arrow</strong>, an arrow plus a fire charge. It leaves
 * the bow burning, so everything vanilla gives a Flame-enchanted arrow comes for free, and on top of
 * that it <em>starts a fire where it lands</em> — the part a Flame bow has never done.
 *
 * <p>The arrow entity is vanilla's own {@link net.minecraft.world.entity.projectile.Arrow}: no entity
 * type, no renderer, no spawn packet. The fire it lays is hung off {@link ProjectileImpactEvent}
 * instead, which recognises our arrow by {@code getPickupItemStackOrigin()} — the stack the arrow
 * would drop, which is exactly the item it was fired from.
 */
public class MoArrowsModule extends AbstractModule<MoArrowsModule, MoArrowsConfig> {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    /** An arrow that burns in flight, ignites what it hits and sets the ground alight. */
    public static final DeferredItem<Item> FIRE_ARROW =
            ITEMS.register("fire_arrow", () -> new FireArrowItem(new Item.Properties()));

    public MoArrowsModule() {
        super("mo_arrows",
                "Mo' Arrows",
                "Adds the Fire Arrow: crafted from an arrow and a fire charge, it burns in flight, "
                        + "ignites what it hits and starts a fire where it lands.",
                MoArrowsConfig::new);
    }

    @Override
    protected void onInitialize() {
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addToMainTab(FIRE_ARROW);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Mo' Arrows module initialized - Fire Arrow registered");
    }

    /**
     * Sets the ground alight where a Fire Arrow lands.
     *
     * <p>Only the block half lives here, and only it has a switch ({@code light_fires}). A burning
     * arrow already ignites entities and lights TNT, campfires and candles on its own, and
     * duplicating any of that would double the effect.
     *
     * @param event the impact about to be processed
     */
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!getConfig().isLightFiresValue()) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)
                || !arrow.getPickupItemStackOrigin().is(FIRE_ARROW.get())
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        Level level = arrow.level();
        if (level.isClientSide || !arrow.isOnFire()) {
            return;
        }

        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        if (!BaseFireBlock.canBePlacedAt(level, pos, hit.getDirection())) {
            return;
        }
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        Entity shooter = arrow.getOwner();
        level.gameEvent(shooter, GameEvent.BLOCK_PLACE, pos);
    }

    /**
     * Injects the Fire Arrow recipe. Datapack JSON does not load reliably in this mod, so recipes
     * are built in code and re-applied on every reload.
     *
     * @param event the server resource reload event
     */
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new FireArrowRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /** One arrow plus one fire charge, in any arrangement → one Fire Arrow. */
    private void applyFireArrowRecipe(RecipeManager recipeManager) {
        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.EQUIPMENT,
                new ItemStack(FIRE_ARROW.get()),
                NonNullList.of(Ingredient.EMPTY,
                        Ingredient.of(Items.ARROW), Ingredient.of(Items.FIRE_CHARGE)));
        RecipeHolder<ShapelessRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "fire_arrow"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    /** Re-adds the recipe after every server resource reload, once the manager has been rebuilt. */
    private final class FireArrowRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private FireArrowRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,
                                              ResourceManager resourceManager,
                                              ProfilerFiller preparationProfiler,
                                              ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor,
                                              Executor gameExecutor) {
            return CompletableFuture.completedFuture(Unit.INSTANCE)
                    .thenCompose(barrier::wait)
                    .thenRunAsync(() -> applyFireArrowRecipe(recipeManager), gameExecutor);
        }
    }
}
