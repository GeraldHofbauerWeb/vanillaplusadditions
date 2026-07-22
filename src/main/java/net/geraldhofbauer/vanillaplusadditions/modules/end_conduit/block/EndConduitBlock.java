package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.block;

import com.mojang.serialization.MapCodec;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.EndConduitModule;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.blockentity.EndConduitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The End Conduit block. Mirrors vanilla {@link net.minecraft.world.level.block.ConduitBlock} — a
 * fully block-entity-rendered ({@code ENTITYBLOCK_ANIMATED}) 6×6×6 core — but without waterlogging
 * (the End has no water). Its {@link EndConduitBlockEntity} handles activation and the Conduit Power
 * effect; rendering is provided by a copy of the vanilla conduit renderer.
 */
public class EndConduitBlock extends BaseEntityBlock {

    public static final MapCodec<EndConduitBlock> CODEC = simpleCodec(EndConduitBlock::new);
    protected static final VoxelShape SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

    public EndConduitBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<EndConduitBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndConduitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, EndConduitModule.END_CONDUIT_BE.get(),
                level.isClientSide ? EndConduitBlockEntity::clientTick : EndConduitBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Drops itself when broken (code, not loot JSON — see CLAUDE.md: loot/recipes via code).
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
