package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatAmphibiousNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects into Mob.createNavigation (which Cat inherits, not overrides) and returns
 * CatAmphibiousNavigation for all cats. Targeting Mob instead of Cat is required
 * because Mixin cannot inject into an inherited method on a subclass.
 *
 * CatAmphibiousNavigation extends GroundPathNavigation so FollowOwnerGoal still
 * accepts it, but overrides getTempMobPos/getGroundY/isStableDestination to allow
 * proper underwater path planning and following.
 */
@Mixin(Mob.class)
public class CatGuardianNavMixin {

    @Inject(method = "createNavigation", at = @At("HEAD"), cancellable = true)
    private void replaceCatNavigation(Level level, CallbackInfoReturnable<PathNavigation> cir) {
        if ((Object) this instanceof Cat) {
            cir.setReturnValue(new CatAmphibiousNavigation((Mob) (Object) this, level));
        }
    }
}
