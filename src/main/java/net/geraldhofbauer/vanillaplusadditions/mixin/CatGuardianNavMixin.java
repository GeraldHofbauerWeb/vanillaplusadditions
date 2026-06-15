package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the vanilla GroundPathNavigation with AmphibiousPathNavigation for all cats.
 * AmphibiousNodeEvaluator creates path nodes inside water blocks (not just at the surface),
 * allowing guardian cats to plan and follow dive paths to submerged targets.
 * Non-guardian cats are unaffected in practice: they have no underwater goals and their
 * stroll/follow behaviour keeps them away from water as before.
 */
@Mixin(Cat.class)
public class CatGuardianNavMixin {

    @Inject(method = "createNavigation", at = @At("HEAD"), cancellable = true)
    private void useAmphibiousNavigation(Level level, CallbackInfoReturnable<PathNavigation> cir) {
        cir.setReturnValue(new AmphibiousPathNavigation((Cat) (Object) this, level));
    }
}
