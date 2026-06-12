package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatBowlBlockEntity;
import net.minecraft.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CatBowlBlock extends AbstractCatBowlBlock {

    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 6, 15);

    public CatBowlBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatBowlBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!stack.is(ItemTags.FISHES)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof CatBowlBlockEntity bowl)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide()) {
            ItemStack toInsert = stack.copyWithCount(1);
            boolean inserted = bowl.insertFish(toInsert, false);
            if (inserted && !player.isCreative()) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CatBowlBlockEntity bowl)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            associateCatsWithBowl(level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // Empty hand, not sneaking → eject fish
        if (!level.isClientSide()) {
            ItemStack fish = bowl.takeFish();
            if (!fish.isEmpty()) {
                player.getInventory().placeItemBackInInventory(fish);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof CatBowlBlockEntity bowl) {
            ItemStack fish = bowl.getFishSlot().getStackInSlot(0);
            if (!fish.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fish);
            }
            clearAssociationsOnBreak(level, pos, bowl);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
