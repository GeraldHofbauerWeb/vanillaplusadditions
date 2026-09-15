package net.geraldhofbauer.vanillaplusadditions.mixin.copycat_pathfinding;

import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.CopycatPathfindingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.compat.CopycatBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Undoes Create's blanket "not pathfindable" for copycat panels.
 *
 * <p>{@code CopycatPanelBlock.isPathfindable} returns a hardcoded {@code false} for every
 * {@link PathComputationType}, so vanilla marks the block {@code PathType.BLOCKED} and mobs treat a
 * 3-pixel plate like a solid wall. This is the single choke point for that decision: both
 * {@code WalkNodeEvaluator} (land) and {@code SwimNodeEvaluator} (water) ask right here, so one
 * injection covers cats and axolotls alike.
 *
 * <p>Restored selectively, not wholesale:
 * <ul>
 *   <li>wall and ceiling panels get vanilla's answer for {@code LAND} — the direction-aware part
 *       then lives in {@code WalkNodeEvaluatorCopycatMixin};</li>
 *   <li>a floor panel keeps Create's {@code false}. That is what makes the cell above it walkable,
 *       which is how mobs walk across panel floors today; upgrading it is
 *       {@code WalkNodeEvaluatorCopycatMixin}'s job, and only where the panel has support;</li>
 *   <li>{@code copycat_step} keeps {@code false} too — that is exactly what {@code SlabBlock} does,
 *       so there is nothing to repair;</li>
 *   <li>{@code WATER} gets vanilla's answer for both blocks, because Create locks swimming mobs out
 *       of waterlogged copycats where a vanilla slab would let them through;</li>
 *   <li>{@code AIR} is left alone — no evaluator in 1.21.1 asks for it.</li>
 * </ul>
 *
 * <p>The block-identity test comes first on purpose. This method runs for every block of every path
 * type calculation (and for spawn placement, dolphins, striders...), so the hot path must be two
 * reference comparisons that fail immediately; without Create both fields are null and nothing else
 * is ever touched.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseCopycatMixin {

    @Inject(method = "isPathfindable", at = @At("HEAD"), cancellable = true)
    private void vpaCopycatPathfindable(PathComputationType pathComputationType,
                                        CallbackInfoReturnable<Boolean> cir) {
        BlockState state = (BlockState) (Object) this;
        Block block = state.getBlock();
        boolean isPanel = block == CopycatBlocks.panel();
        boolean isStep = !isPanel && block == CopycatBlocks.step();
        if (!isPanel && !isStep) {
            return;
        }
        if (!CopycatPathfindingModule.isActive()) {
            return;
        }

        if (pathComputationType == PathComputationType.WATER) {
            if (CopycatPathfindingModule.waterPathfinding()) {
                cir.setReturnValue(state.getFluidState().is(FluidTags.WATER));
            }
            return;
        }
        if (pathComputationType != PathComputationType.LAND || isStep) {
            return;
        }
        // The plate sticks to FACING.getOpposite(); DOWN means a floor panel, handled elsewhere.
        if (state.getValue(BlockStateProperties.FACING).getOpposite() == Direction.DOWN) {
            return;
        }
        cir.setReturnValue(!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }
}
