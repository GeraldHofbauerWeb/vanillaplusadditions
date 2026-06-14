package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the vanilla owner-teleport for guardian cats at the source. Both teleport call sites
 * ({@code FollowOwnerGoal.tick} and {@code TamableAnimal.TamableAnimalPanicGoal.tick}) gate the
 * teleport on {@code shouldTryTeleportToOwner()}; returning false here prevents a bowl-assigned
 * cat from ever teleporting to a distant owner, regardless of goal timing.
 */
@Mixin(TamableAnimal.class)
public class CatGuardianTeleportMixin {

    @Inject(method = "shouldTryTeleportToOwner", at = @At("HEAD"), cancellable = true)
    private void vpaNoGuardianTeleport(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Cat cat && CatGuardianModule.isGuardianCat(cat)) {
            cir.setReturnValue(false);
        }
    }
}
