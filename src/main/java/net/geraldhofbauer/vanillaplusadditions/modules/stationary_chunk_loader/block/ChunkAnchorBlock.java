package net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.block;

import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.StationaryChunkLoaderModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A full block that permanently force-loads the chunk it stands in (plus a configurable radius)
 * <b>while it is redstone-powered</b> and force-loading is enabled (a player online, by default).
 *
 * <p>Redstone toggle: any vanilla redstone signal (lever, comparator, or e.g. a Create Redstone
 * Link receiver) enables loading via {@link #POWERED}; cutting the signal releases the chunk. The
 * active state shows a red glowing centre. Registration/release is delegated to
 * {@link StationaryChunkLoaderModule}; persistence + tickets live there so a powered anchor survives
 * restarts. Unlike the loader rail, no minecart is needed.</p>
 */
public class ChunkAnchorBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ChunkAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(POWERED, powered);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel
                && state.getValue(POWERED)) {
            StationaryChunkLoaderModule.onAnchorActive(serverLevel, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
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
            if (level instanceof ServerLevel serverLevel) {
                if (powered) {
                    StationaryChunkLoaderModule.onAnchorActive(serverLevel, pos);
                } else {
                    StationaryChunkLoaderModule.onAnchorInactive(serverLevel, pos);
                }
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            StationaryChunkLoaderModule.onAnchorInactive(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Drops itself when broken. Done in code instead of a loot-table JSON because JSON datapack data
     * does not load reliably in this mod (see CLAUDE.md: recipes/loot via code).
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
