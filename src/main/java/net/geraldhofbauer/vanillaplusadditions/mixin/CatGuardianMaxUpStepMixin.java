package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the auto-step height for guardian cats so they can walk over stairs,
 * half-slabs, and similar partial-height blocks without getting stuck / spinning.
 */
@Mixin(Cat.class)
public class CatGuardianMaxUpStepMixin {

    @Inject(method = "maxUpStep", at = @At("HEAD"), cancellable = true)
    private void vanillaplusGuardianMaxUpStep(CallbackInfoReturnable<Float> cir) {
        if (CatGuardianModule.isGuardianCat((Cat) (Object) this)) {
            cir.setReturnValue(1.5f);
        }
    }
}
