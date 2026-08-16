package net.geraldhofbauer.vanillaplusadditions.mixin.hostile_endermen;

import net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.HostileEndermenModule;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compat mixin for EnhancedAI's "Teleport anti-cheese": when an enderman cannot path to its target
 * it yanks the target over to itself ({@code TeleportAntiCheeseGoal#start} →
 * {@code target.randomTeleport(mobX…, mobY…, mobZ…)}). That exists to stop players from bullying
 * endermen from safe spots — but with the Hostile Endermen module every enderman in the End hunts
 * unprovoked, so simply walking away gets the player dragged back in.
 * <p>
 * The goal is vetoed in {@code canUse} (rather than skipping the teleport itself) so the goal never
 * starts — no teleport sound, no goal bookkeeping. See
 * {@link HostileEndermenModule#suppressAntiCheeseTeleport}.
 * <p>
 * EnhancedAI is not a compile dependency, hence the string target; if the mod is absent Mixin
 * disables this mixin (both mixin configs are {@code "required": false}).
 */
@Mixin(targets = "insane96mcp.enhancedai.module.mobs.teleportanticheese.TeleportAntiCheeseGoal",
        remap = false)
public abstract class TeleportAntiCheeseMixin {

    @Shadow
    @Final
    private Mob mob;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void vpaVetoAntiCheeseTeleport(CallbackInfoReturnable<Boolean> cir) {
        if (HostileEndermenModule.suppressAntiCheeseTeleport(mob)) {
            cir.setReturnValue(false);
        }
    }
}
