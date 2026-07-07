package net.geraldhofbauer.vanillaplusadditions.mixin.cat_guardian;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the vanilla owner-teleport for all tame cats when the cat_guardian module is active.
 * The only legitimate cat-teleport mechanism is Sable Contraptions assembly
 * ({@code SableBowlAssemblyHandler}), which bypasses this check entirely.
 * Both vanilla call sites ({@code FollowOwnerGoal.tick} and
 * {@code TamableAnimal.TamableAnimalPanicGoal.tick}) gate on {@code shouldTryTeleportToOwner()}.
 */
@Mixin(TamableAnimal.class)
public class CatGuardianTeleportMixin {

    @Inject(method = "shouldTryTeleportToOwner", at = @At("HEAD"), cancellable = true)
    private void vpaNoTeleportToPlayer(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Cat cat && cat.isTame() && CatGuardianModule.isModuleActive()) {
            cir.setReturnValue(false);
        }
    }
}
