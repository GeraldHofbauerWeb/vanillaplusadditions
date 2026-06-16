package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/**
 * Navigation for guardian cats that allows underwater pathfinding while remaining
 * a GroundPathNavigation subclass (required by FollowOwnerGoal's type check).
 *
 * Key overrides vs vanilla GroundPathNavigation:
 *  - AmphibiousNodeEvaluator: plans paths through water blocks (sets PathType.WATER malus=0)
 *  - getTempMobPos: uses actual Y, not surface Y (GroundPathNavigation returns surface when floating)
 *  - getGroundY: returns actual Y so MoveControl targets underwater path nodes correctly
 *  - isStableDestination: accepts underwater positions (block below must not be air)
 *  - canUpdatePath: always true so path follows even when not on ground
 *  - setCanFloat: no-op prevents FloatGoal from overriding navigation behaviour in water
 */
public class CatAmphibiousNavigation extends GroundPathNavigation {

    public CatAmphibiousNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new AmphibiousNodeEvaluator(false);
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.mob.getY(0.5), this.mob.getZ());
    }

    @Override
    protected double getGroundY(Vec3 vec) {
        return vec.y;
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        return !this.level.getBlockState(pos.below()).isAir();
    }

    @Override
    public void setCanFloat(boolean canFloat) {
        // no-op: FloatGoal must not change navigation behaviour while cat dives
    }
}
