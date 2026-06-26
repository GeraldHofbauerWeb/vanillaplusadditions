package net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * A rail that behaves like a vanilla flat/curved rail (carts ride it normally) but is recognised
 * by the Minecart Chunk Loading module to force-load chunks around traveling minecarts.
 *
 * <p>Extends {@link BaseRailBlock} directly (rather than {@code RailBlock}) so the block can own a
 * properly-typed {@link MapCodec}; rail behaviour (state updates, placement) comes from the base.</p>
 */
public class ChunkLoaderRailBlock extends BaseRailBlock {

    public static final MapCodec<ChunkLoaderRailBlock> CODEC = simpleCodec(ChunkLoaderRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;

    public ChunkLoaderRailBlock(BlockBehaviour.Properties properties) {
        super(false, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    public MapCodec<ChunkLoaderRailBlock> codec() {
        return CODEC;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED);
    }

    /**
     * Drops itself when broken. Done in code instead of a loot-table JSON because JSON datapack
     * data does not load reliably in this mod (see CLAUDE.md: recipes/loot via code).
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
