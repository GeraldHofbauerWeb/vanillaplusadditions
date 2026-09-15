package net.geraldhofbauer.vanillaplusadditions.mixin.copycat_pathfinding;

import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link NodeEvaluator}'s {@code currentContext} field.
 *
 * <p>The neighbour checks in {@code WalkNodeEvaluatorCopycatMixin} need the region the path finder
 * is currently working on, and vanilla offers no getter. An accessor on the declaring class is the
 * clean way in — a {@code @Shadow protected} field would trip Checkstyle's {@code VisibilityModifier},
 * and Mixin forbids narrowing a shadowed field to private. Read-only.
 */
@Mixin(NodeEvaluator.class)
public interface NodeEvaluatorContextAccessor {

    /**
     * {@return the pathfinding context the evaluator is currently prepared for}
     */
    @Accessor("currentContext")
    PathfindingContext vpaCurrentContext();
}
