package net.geraldhofbauer.vanillaplusadditions.mixin.copycat_pathfinding;

import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.CopycatPathfindingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.PanelGeometry;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.compat.CopycatBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The direction-aware half of the copycat fix, plus the floor-panel upgrade.
 *
 * <p>Once {@code BlockStateBaseCopycatMixin} lets wall and ceiling panels through, the path finder
 * would happily route a mob straight through the plate — which physics then stops, leaving the mob
 * bumping against it. The two neighbour checks here put that back: a move is rejected exactly when
 * it crosses a plate's plane, so walking along a panel-lined wall works while walking through it
 * does not.
 *
 * <p>Axolotls come along for free: {@code AmphibiousNodeEvaluator} extends this class, calls
 * {@code super.getNeighbors} and routes its vertical neighbours through {@code isNeighborValid} as
 * well, so diving through a floor or ceiling plate is covered too.
 */
@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorCopycatMixin {

    /** Reused per evaluator instance, like vanilla's own {@code reusableNeighbors} field. */
    @Unique
    private final BlockPos.MutableBlockPos vpaScratch = new BlockPos.MutableBlockPos();

    @Inject(method = "isNeighborValid", at = @At("RETURN"), cancellable = true)
    private void vpaBlockPanelCrossing(@Nullable Node neighbor, Node node,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || neighbor == null) {
            return;
        }
        PathfindingContext context = vpaContext();
        if (context == null) {
            return;
        }
        if (PanelGeometry.blocksMove(context.level(), vpaScratch,
                node.x, node.y, node.z, neighbor.x, neighbor.y, neighbor.z)
                && CopycatPathfindingModule.isActive()
                && CopycatPathfindingModule.isDirectionAware()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isDiagonalValid(Lnet/minecraft/world/level/pathfinder/Node;"
            + "Lnet/minecraft/world/level/pathfinder/Node;"
            + "Lnet/minecraft/world/level/pathfinder/Node;)Z",
            at = @At("RETURN"), cancellable = true)
    private void vpaBlockDiagonalPanelCrossing(Node root, @Nullable Node xNode, @Nullable Node zNode,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || xNode == null || zNode == null) {
            return;
        }
        PathfindingContext context = vpaContext();
        if (context == null) {
            return;
        }
        if (PanelGeometry.blocksDiagonal(context.level(), vpaScratch, root, xNode, zNode)
                && CopycatPathfindingModule.isActive()
                && CopycatPathfindingModule.isDirectionAware()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Promotes a floor panel that rests on something solid from {@code BLOCKED} to walkable ground.
     *
     * <p>A floor panel deliberately keeps Create's "not pathfindable" answer, because that is what
     * makes the cell <em>above</em> it walkable — how mobs cross panel floors and free-standing
     * panel bridges today. The cost is a passage only one block high: there is no cell above to
     * walk in, and the mob gives up. This applies vanilla's own rule from the same switch — a cell
     * is walkable when the cell below offers footing — to the panel cell itself, which leaves the
     * free-standing case untouched.
     */
    @Inject(method = "getPathTypeStatic(Lnet/minecraft/world/level/pathfinder/PathfindingContext;"
            + "Lnet/minecraft/core/BlockPos$MutableBlockPos;)"
            + "Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("RETURN"), cancellable = true)
    private static void vpaFloorPanelWalkable(PathfindingContext context, BlockPos.MutableBlockPos pos,
                                              CallbackInfoReturnable<PathType> cir) {
        if (cir.getReturnValue() != PathType.BLOCKED) {
            return;
        }
        Block panel = CopycatBlocks.panel();
        if (panel == null) {
            return;
        }
        BlockState state = context.level().getBlockState(pos);
        if (state.getBlock() != panel
                || state.getValue(BlockStateProperties.FACING) != Direction.UP) {
            return;
        }
        if (!CopycatPathfindingModule.isActive() || !CopycatPathfindingModule.floorPanelsWalkable()) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // Plain comparisons instead of a switch: an enum switch inside a mixin would emit a
        // synthetic switch-map inner class that Mixin then has to relocate into the target.
        PathType below = context.getPathTypeFromState(x, y - 1, z);
        if (below == PathType.OPEN || below == PathType.WATER
                || below == PathType.LAVA || below == PathType.WALKABLE) {
            // Nothing to stand on - leave it blocked so mobs keep walking across the plate.
            return;
        }
        cir.setReturnValue(WalkNodeEvaluator.checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE));
    }

    @Unique
    @Nullable
    private PathfindingContext vpaContext() {
        if (CopycatBlocks.panel() == null) {
            return null;
        }
        return ((NodeEvaluatorContextAccessor) (Object) this).vpaCurrentContext();
    }
}
