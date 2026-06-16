package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses FloatGoal for guardian cats that are actively navigating.
 * FloatGoal (priority 0) calls jumpControl.jump() every tick, pushing the cat
 * to the surface and overriding boostWaterNavigation's downward impulse.
 * When a guardian cat has a live path (pursuing target or returning to base),
 * FloatGoal must not fire so the cat can actually dive.
 */
@Mixin(FloatGoal.class)
public class CatGuardianFloatMixin {

    @Shadow @Final private Mob mob;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void suppressForNavigatingGuardian(CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof Cat cat && CatGuardianModule.isGuardianCat(cat)) {
            Path path = cat.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                cir.setReturnValue(false);
            }
        }
    }
}
