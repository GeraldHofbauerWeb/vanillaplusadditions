package net.geraldhofbauer.vanillaplusadditions.mixin.free_anvil_repair;

import net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.FreeAnvilRepairModule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets players take zero-cost anvil results produced by the Free Anvil Repair module.
 * Vanilla's {@code mayPickup} requires {@code cost > 0}, but never creates a non-empty result
 * with cost 0 itself — so allowing pickup at cost 0 (with a filled result slot) only affects
 * outputs the module computed. The second parameter is the result slot's {@code hasItem()}
 * (see {@code ItemCombinerMenu}'s result slot delegating {@code mayPickup(player, hasItem())}).
 */
@Mixin(AnvilMenu.class)
public class AnvilMenuFreeRepairMixin {

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void vpaAllowFreeRepairPickup(Player player, boolean hasStack,
                                          CallbackInfoReturnable<Boolean> cir) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        if (hasStack && self.getCost() == 0 && FreeAnvilRepairModule.allowFreePickup()) {
            cir.setReturnValue(true);
        }
    }
}
