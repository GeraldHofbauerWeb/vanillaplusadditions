package net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding;

import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.compat.CopycatBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import org.jetbrains.annotations.Nullable;

/**
 * Where a copycat panel's 3-pixel plate sits, and which movements it therefore blocks.
 *
 * <p>A panel occupies one face of its block; everything else in that block is air. Create marks the
 * whole block unwalkable, which is why mobs refuse to use a passage that merely has a panel lining
 * its wall or ceiling. This class supplies the finer answer: a move is blocked only when it would
 * cross the plate's plane.
 *
 * <p><b>The plate sticks to {@code FACING.getOpposite()}, not to {@code FACING}.</b> Create builds
 * the shape from {@code AllShapes.CASING_3PX}, a floor plate anchored at {@link Direction#UP}, and
 * {@code getStateForPlacement} sets {@code FACING = getNearestLookingDirection().getOpposite()} —
 * looking down places a floor panel with {@code FACING = UP}. Dropping the {@code getOpposite()}
 * would invert the whole module: it would free exactly the moves it means to block.
 * {@link CopycatBlocks} verifies that convention once at resolve time.
 */
public final class PanelGeometry {

    private PanelGeometry() {
    }

    /**
     * The block face a copycat panel's plate is attached to.
     *
     * @param level   the (pre-fetched) region the path finder is working on
     * @param scratch scratch position, reused to avoid allocating per lookup
     * @param x       block x
     * @param y       block y
     * @param z       block z
     * @return the face the plate sticks to, or null if there is no copycat panel at that position
     */
    @Nullable
    public static Direction plateFace(BlockGetter level, BlockPos.MutableBlockPos scratch, int x, int y, int z) {
        Block panel = CopycatBlocks.panel();
        if (panel == null) {
            return null;
        }
        BlockState state = level.getBlockState(scratch.set(x, y, z));
        if (state.getBlock() != panel) {
            return null;
        }
        return state.getValue(BlockStateProperties.FACING).getOpposite();
    }

    /**
     * Whether moving from one block to another would cross a panel plate — either leaving the
     * source through its plate, or entering the target through its plate.
     *
     * <p>Costs exactly two block lookups regardless of how many axes change.
     *
     * @param level   the region the path finder is working on
     * @param scratch scratch position, reused to avoid allocating per lookup
     * @param fx      source x
     * @param fy      source y
     * @param fz      source z
     * @param tx      target x
     * @param ty      target y
     * @param tz      target z
     * @return true if the movement crosses a plate and must be rejected
     */
    public static boolean blocksMove(BlockGetter level, BlockPos.MutableBlockPos scratch,
                                     int fx, int fy, int fz, int tx, int ty, int tz) {
        Direction fromPlate = plateFace(level, scratch, fx, fy, fz);
        Direction toPlate = plateFace(level, scratch, tx, ty, tz);
        if (fromPlate == null && toPlate == null) {
            return false;
        }
        return crosses(fromPlate, toPlate, Direction.Axis.X, tx - fx)
                || crosses(fromPlate, toPlate, Direction.Axis.Y, ty - fy)
                || crosses(fromPlate, toPlate, Direction.Axis.Z, tz - fz);
    }

    /**
     * Diagonal variant. Vanilla reaches the diagonal cell via the two straight neighbours, so all
     * three legs are checked: root to x-neighbour, root to z-neighbour, and root to the diagonal
     * cell itself. The diagonal cell is {@code (xNode.x, root.y, zNode.z)} — {@code findAcceptedNode}
     * only ever shifts a node in y, never in x or z.
     *
     * @param level   the region the path finder is working on
     * @param scratch scratch position, reused to avoid allocating per lookup
     * @param root    the node the mob moves away from
     * @param xNode   the straight neighbour on one axis
     * @param zNode   the straight neighbour on the other axis
     * @return true if any leg of the diagonal crosses a plate
     */
    public static boolean blocksDiagonal(BlockGetter level, BlockPos.MutableBlockPos scratch,
                                         Node root, Node xNode, Node zNode) {
        return blocksMove(level, scratch, root.x, root.y, root.z, xNode.x, xNode.y, xNode.z)
                || blocksMove(level, scratch, root.x, root.y, root.z, zNode.x, zNode.y, zNode.z)
                || blocksMove(level, scratch, root.x, root.y, root.z, xNode.x, root.y, zNode.z);
    }

    /**
     * Does a step along one axis cross either plate?
     *
     * <p>Leaving the source through its own plate, or entering the target through the plate on the
     * face that is being passed through, both count.
     */
    private static boolean crosses(@Nullable Direction fromPlate, @Nullable Direction toPlate,
                                   Direction.Axis axis, int delta) {
        if (delta == 0) {
            return false;
        }
        Direction step = Direction.fromAxisAndDirection(axis,
                delta > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        return fromPlate == step || toPlate == step.getOpposite();
    }
}
