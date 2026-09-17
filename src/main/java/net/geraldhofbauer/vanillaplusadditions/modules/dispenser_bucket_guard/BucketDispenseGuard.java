package net.geraldhofbauer.vanillaplusadditions.modules.dispenser_bucket_guard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/**
 * Wraps the vanilla dispense behavior of a bucket-like item and swallows the attempt when it would
 * fail, instead of letting vanilla throw the bucket onto the floor.
 * <p>
 * Vanilla's two bucket behaviors both end in {@code DefaultDispenseItemBehavior} — i.e. "eject the
 * item as an entity" — when their real job cannot be done: the empty bucket when
 * {@code BucketPickup.pickupBlock} comes back empty, every filled bucket when
 * {@code DispensibleContainerItem.emptyContents} returns false. For an automated water door that
 * means the bucket leaves the dispenser and the machine is dead until someone picks it up again.
 * <p>
 * This wrapper predicts both outcomes <em>without</em> touching the world (vanilla's own checks all
 * mutate on success) and, on a predicted failure, returns the stack unchanged so it stays in the
 * dispenser. Whenever the outcome cannot be predicted the call is delegated untouched, so anything
 * this class does not understand keeps behaving exactly like vanilla.
 */
public final class BucketDispenseGuard implements DispenseItemBehavior {

    /** Vanilla level event for the "dispenser failed" click ({@code LevelEvent.SOUND_DISPENSER_FAIL}). */
    private static final int FAIL_SOUND_EVENT = 1001;

    private final DispenseItemBehavior delegate;

    BucketDispenseGuard(DispenseItemBehavior delegate) {
        this.delegate = delegate;
    }

    /**
     * Whether the given item is one this guard knows how to predict, i.e. whether wrapping its
     * dispense behavior can do anything useful.
     *
     * @param item the item registered in {@code DispenserBlock.DISPENSER_REGISTRY}
     * @return true for empty, filled and solid ("powder snow") buckets
     */
    static boolean isBucketLike(Item item) {
        return item instanceof BucketItem || item instanceof SolidBucketItem;
    }

    @Override
    public ItemStack dispense(BlockSource source, ItemStack stack) {
        if (DispenserBucketGuardModule.isActive() && !wouldSucceed(source, stack)) {
            if (DispenserBucketGuardModule.playFailSound()) {
                source.level().levelEvent(FAIL_SOUND_EVENT, source.pos(), 0);
            }
            return stack;
        }
        return delegate.dispense(source, stack);
    }

    /**
     * Predicts whether vanilla's behavior would do its actual job rather than fall through to
     * ejecting the item. Returns true whenever the outcome is unclear, which keeps vanilla's
     * behavior for everything this guard does not model.
     */
    private static boolean wouldSucceed(BlockSource source, ItemStack stack) {
        ServerLevel level = source.level();
        BlockPos front = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        BlockState state = level.getBlockState(front);
        Item item = stack.getItem();

        if (item instanceof BucketItem bucket) {
            // Items.BUCKET carries Fluids.EMPTY, which is not a FlowingFluid — that is the pickup case.
            return bucket.content instanceof FlowingFluid
                    ? canPlaceFluid(level, front, state, bucket.content)
                    : canPickUp(state);
        }
        if (item instanceof SolidBucketItem) {
            return level.isInWorldBounds(front) && level.isEmptyBlock(front);
        }
        return true;
    }

    /**
     * Mirrors what {@code BucketPickup.pickupBlock} would return for the block in front, without
     * calling it — the vanilla method drains the block as a side effect of succeeding.
     * <p>
     * Vanilla's implementations hand out a filled bucket exactly when a fluid source sits there
     * ({@code LiquidBlock} requires {@code LEVEL == 0}, a waterloggable block requires
     * {@code WATERLOGGED == true}); powder snow is the one block that always yields.
     */
    private static boolean canPickUp(BlockState state) {
        if (!(state.getBlock() instanceof BucketPickup)) {
            return false;
        }
        return state.getBlock() instanceof PowderSnowBlock || state.getFluidState().isSource();
    }

    /**
     * Mirrors the placement check at the top of {@code BucketItem.emptyContents}. Because the
     * dispenser passes no {@code BlockHitResult}, a failed check there has no neighbour fallback
     * and goes straight to ejecting the bucket.
     */
    private static boolean canPlaceFluid(ServerLevel level, BlockPos pos, BlockState state, Fluid content) {
        if (state.isAir() || state.canBeReplaced(content)) {
            return true;
        }
        return state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(null, level, pos, state, content);
    }
}
