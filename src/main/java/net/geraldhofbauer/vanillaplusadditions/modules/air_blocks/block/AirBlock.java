package net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * An invisible, solid-for-fluids "air" block. It occupies its cell like a normal block — so water,
 * lava and modded fluids cannot flow into or through it — but has an <b>empty collision shape</b>, so
 * every entity (players, mobs, items, projectiles) passes straight through it.
 *
 * <p>It renders invisibly ({@link RenderShape#INVISIBLE}) like a Barrier, yet keeps a full outline
 * shape so it can still be targeted, highlighted and mined normally. Because JSON loot tables do not
 * load reliably in this mod (see CLAUDE.md), it drops its own item via {@link #getDrops}.</p>
 */
public class AirBlock extends Block {

    public AirBlock(Properties properties) {
        super(properties);
    }

    /** Empty collision → all entities pass through. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    /** Full outline → the block can still be targeted and mined. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Shapes.block();
    }

    /** Full occlusion/visual shape kept empty so it never affects neighbour culling or light. */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * Drops itself when broken. Done in code instead of a loot-table JSON because JSON datapack data
     * does not load reliably in this mod (see CLAUDE.md).
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
