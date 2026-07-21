package net.geraldhofbauer.vanillaplusadditions.mixin.conduit_attack_range;

import net.geraldhofbauer.vanillaplusadditions.modules.conduit_attack_range.ConduitAttackRangeModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Makes conduits attack hostile mobs at every active tier, within the Conduit Power radius divided
 * by {@code radius_divisor} (default 2), instead of vanilla's max-frame-only, fixed-8-block attack.
 * Three surgical edits inside {@code updateDestroyTarget}, all no-ops (vanilla values) when the
 * {@code conduit_attack_range} module is disabled:
 * <ul>
 *     <li>lower the {@code i < 42} ({@code MIN_KILL_SIZE}) gate to {@code min_frames};</li>
 *     <li>replace the fixed {@code getDestroyRangeAABB(pos)} search box with a frame-scaled one;</li>
 *     <li>replace the {@code pos.closerThan(target, 8.0)} retain check with the same scaled radius,
 *         so far-acquired targets aren't dropped the next tick.</li>
 * </ul>
 * {@code findDestroyTarget} (re-acquire by UUID after reload) keeps the vanilla 8-block box — it has
 * no {@code positions} in scope and only re-finds an already-chosen target.
 */
@Mixin(ConduitBlockEntity.class)
public class ConduitBlockEntityMixin {

    @ModifyConstant(method = "updateDestroyTarget", constant = @Constant(intValue = 42))
    private static int vpaMinKillSize(int original) {
        return ConduitAttackRangeModule.isActive() ? ConduitAttackRangeModule.minFrames() : original;
    }

    @Redirect(
            method = "updateDestroyTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;"
                            + "getDestroyRangeAABB(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB vpaDestroyRange(BlockPos rangePos,
                                         Level level, BlockPos pos, BlockState state,
                                         List<BlockPos> positions, ConduitBlockEntity blockEntity) {
        int radius = ConduitAttackRangeModule.isActive()
                ? ConduitAttackRangeModule.hostileRadius(positions.size())
                : 8;
        int x = rangePos.getX();
        int y = rangePos.getY();
        int z = rangePos.getZ();
        return new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(radius);
    }

    @Redirect(
            method = "updateDestroyTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
            )
    )
    private static boolean vpaRetainRange(BlockPos self, Vec3i target, double distance,
                                           Level level, BlockPos pos, BlockState state,
                                           List<BlockPos> positions, ConduitBlockEntity blockEntity) {
        double radius = ConduitAttackRangeModule.isActive()
                ? ConduitAttackRangeModule.hostileRadius(positions.size())
                : distance;
        return self.closerThan(target, radius);
    }
}
