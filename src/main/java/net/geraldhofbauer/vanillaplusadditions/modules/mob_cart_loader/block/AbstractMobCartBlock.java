package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.AbstractMobCartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared base for the mob loader/unloader blocks: a full-block frame with an open window on its
 * input and output faces (the two ends of the 6-way {@link #FACING} axis) so the mini-mob inside is
 * visible; controlled by <b>inverse</b> redstone via {@link #POWERED} — active by default (no
 * signal), disabled while powered.
 *
 * <p>{@link #FACING} stores the <b>input</b> direction (the face pointing toward the player when
 * placed, observer-style); the output is the opposite face. Which physical side is the cart vs the
 * pen depends on the subclass. Block-entity logic (cart/mob detection, loading/unloading, mob
 * display sync) lives in {@link AbstractMobCartBlockEntity}.</p>
 */
public abstract class AbstractMobCartBlock extends BaseEntityBlock {

    /** 6-way facing: stores the input direction (toward the player at placement); output is opposite. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Redstone state. Inverse semantics: powered = inactive, unpowered = active (see {@link #isActive}). */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected AbstractMobCartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE));
    }

    /** The block entity type this block ticks, used for the server ticker. */
    protected abstract BlockEntityType<? extends AbstractMobCartBlockEntity> getBlockEntityType();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Input face (FACING) points toward the player (observer-style); output is the opposite side.
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Render the normal block model; the BER draws the spinning mob on top.
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, getBlockEntityType(),
                AbstractMobCartBlockEntity::serverTick);
    }

    /**
     * Drops itself when broken (code, not loot JSON — see CLAUDE.md: loot/recipes via code).
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    /**
     * Whether the block is active (inverse redstone: active unless powered).
     *
     * @param state the block state
     * @return true if the block should load/unload
     */
    public static boolean isActive(BlockState state) {
        return !state.getValue(POWERED);
    }
}
