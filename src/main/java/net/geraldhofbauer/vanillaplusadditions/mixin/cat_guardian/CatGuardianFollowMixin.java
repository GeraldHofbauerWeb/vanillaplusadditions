package net.geraldhofbauer.vanillaplusadditions.mixin.cat_guardian;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents all tamed cats from following their owner when CatGuardianModule is active.
 * Belt-and-suspenders alongside the goal-removal in suppressFollowingBehaviors().
 */
@Mixin(FollowOwnerGoal.class)
public class CatGuardianFollowMixin {

    @Shadow
    @Final
    private TamableAnimal tamable;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void suppressCatFollowing(CallbackInfoReturnable<Boolean> cir) {
        if (tamable instanceof Cat && CatGuardianModule.isModuleActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void suppressCatFollowingContinue(CallbackInfoReturnable<Boolean> cir) {
        if (tamable instanceof Cat && CatGuardianModule.isModuleActive()) {
            cir.setReturnValue(false);
        }
    }
}
