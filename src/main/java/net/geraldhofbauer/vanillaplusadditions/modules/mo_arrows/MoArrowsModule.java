package net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.config.MoArrowsConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.item.FireArrowItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
        getModEventBus().addListener(this::onModCommonSetup);
        VanillaPlusCreativeTabs.addToMainTab(FIRE_ARROW);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Mo' Arrows module initialized - Fire Arrow registered");
    }

    /**
     * Teaches dispensers to shoot the Fire Arrow.
     *
     * <p>A dispenser only fires what has an entry in {@code DispenserBlock.DISPENSER_REGISTRY};
     * vanilla registers exactly three arrows there. Without this the Fire Arrow was simply dropped
     * on the ground, and {@code FireArrowItem.asProjectile} - which lights the arrow on the
     * dispenser path - could never run.</p>
     *
     * <p>Through {@code enqueueWork} because that registry is a plain map and mod setup runs in
     * parallel.</p>
     *
     * @param event the common setup event
     */
    private void onModCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerProjectileBehavior(FIRE_ARROW.get()));
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
        if (level.isClientSide) {
            return;
        }
        if (!arrow.isOnFire()) {
            // These three lines are INFO rather than DEBUG on purpose. NeoForge routes DEBUG into
            // logs/debug.log while logs/latest.log keeps only INFO and above, and latest.log is what
            // gets looked at first when a hit report comes in. The debug_logging gate keeps them quiet.
            if (getConfig().shouldDebugLog()) {
                getLogger().info("Fire Arrow hit {} on its {} face but was no longer burning - "
                                + "nothing set alight.",
                        hit.getBlockPos().toShortString(), hit.getDirection());
            }
            return;
        }

        BlockPos pos = firePosition(level, hit, arrow);
        if (pos == null) {
            if (getConfig().shouldDebugLog()) {
                BlockPos offFace = hit.getBlockPos().relative(hit.getDirection());
                getLogger().info("Fire Arrow hit {} on its {} face, no fire placed: {} holds {}, "
                                + "the arrow's own spot {} holds {}",
                        hit.getBlockPos().toShortString(), hit.getDirection(),
                        offFace.toShortString(), level.getBlockState(offFace),
                        arrow.blockPosition().toShortString(),
                        level.getBlockState(arrow.blockPosition()));
            }
            return;
        }
        BlockState previous = level.getBlockState(pos);
        // Flint and steel, not a fire charge: what happens here is a fire being PLACED on a face,
        // which is flint and steel's sound and pitch formula. A fire charge's FIRECHARGE_USE is the
        // whoosh of the charge itself bursting, and there is no charge in flight here.
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        Entity shooter = arrow.getOwner();
        level.gameEvent(shooter, GameEvent.BLOCK_PLACE, pos);
        if (getConfig().shouldDebugLog()) {
            getLogger().info("Fire Arrow hit {} ({}) on its {} face; fire set at {}, which held {}. "
                            + "Arrow sits at {}.",
                    hit.getBlockPos().toShortString(), level.getBlockState(hit.getBlockPos()),
                    hit.getDirection(), pos.toShortString(), previous,
                    arrow.blockPosition().toShortString());
        }
    }

    /**
     * Where the fire goes, or null if neither candidate can hold one.
     *
     * <p>Two candidates, in this order:</p>
     * <ol>
     *   <li><b>Off the struck face.</b> The natural choice and the one that always works for a shot
     *       into the ground: the block above solid ground is air and the ground carries the fire.</li>
     *   <li><b>Where the arrow itself is.</b> Needed for anything that is not a flat surface. Shoot
     *       into a bush and the block off the struck face is frequently another leaf - foliage is
     *       several blocks deep and the arrow stops inside it, not in front of it - so
     *       {@code canBePlacedAt} refuses on {@code !isAir()} and nothing catches. The arrow's own
     *       block is the air it just flew through, so it is empty by construction and touches what
     *       was hit.</li>
     * </ol>
     *
     * @param level the level the arrow struck in
     * @param hit   the block hit result
     * @param arrow the arrow, for its own position
     * @return the position to set alight, or null if neither candidate can take a fire
     */
    private static BlockPos firePosition(Level level, BlockHitResult hit, AbstractArrow arrow) {
        BlockPos offFace = hit.getBlockPos().relative(hit.getDirection());
        if (canTakeFire(level, offFace, hit.getDirection())) {
            return offFace;
        }
        BlockPos atArrow = arrow.blockPosition();
        if (!atArrow.equals(offFace) && canTakeFire(level, atArrow, hit.getDirection())) {
            return atArrow;
        }
        return null;
    }

    /**
     * Whether a fire can be put at this position.
     *
     * <p>{@link BaseFireBlock#canBePlacedAt} first, which is vanilla's own rule - but it insists on
     * <em>air</em>, and outdoors that is often not what is there. A snow layer is the case that
     * showed it: an arrow shot at snow-covered ground goes through the two-pixel layer and strikes
     * the ground underneath, so the block off the struck face is the snow layer itself, and the
     * arrow ends up in it too. Both candidates then hold snow, not air, and nothing caught fire.</p>
     *
     * <p>So a replaceable block is accepted as well, as long as it is not a fluid and a fire would
     * survive there. That is what flint and steel does - right-click snow-covered ground and the
     * layer is replaced by the fire - and the same now goes for tall grass and ferns.</p>
     *
     * @param level the level
     * @param pos   the candidate position
     * @param face  the struck face, for vanilla's portal check
     * @return true if a fire may be set here
     */
    private static boolean canTakeFire(Level level, BlockPos pos, Direction face) {
        if (BaseFireBlock.canBePlacedAt(level, pos, face)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced()
                && state.getFluidState().isEmpty()
                && BaseFireBlock.getState(level, pos).canSurvive(level, pos);
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

    /**
     * Acht Pfeile um eine Feuerkugel herum → acht Feuerpfeile.
     *
     * <p>Dieselbe Form, die Vanilla fuer getippte Pfeile um einen verweilenden Trank benutzt: eine
     * Feuerkugel je acht Pfeile. Die vorherige Fassung war formlos, ein Pfeil plus eine ganze
     * Feuerkugel, und damit um den Faktor acht teurer als ihr Vanilla-Vorbild.
     */
    private void applyFireArrowRecipe(RecipeManager recipeManager) {
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.EQUIPMENT,
                ShapedRecipePattern.of(
                        Map.of('A', Ingredient.of(Items.ARROW), 'F', Ingredient.of(Items.FIRE_CHARGE)),
                        "AAA", "AFA", "AAA"),
                new ItemStack(FIRE_ARROW.get(), 8));
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(
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
