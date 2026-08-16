package net.geraldhofbauer.vanillaplusadditions.mixin.hostile_endermen;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.HostileEndermenModule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Compat mixin for Enderman Overhaul: its End Enderman randomly teleports its victim away on hit
 * ({@code doHurtTarget} → {@code ModUtils.teleportTarget}). Since the Hostile Endermen module makes
 * every enderman in the End attack unprovoked, that ability would constantly fling players around
 * for a fight they never picked — so it is skipped while the player has not hit the enderman back
 * (see {@link HostileEndermenModule#suppressTeleportAttack}).
 * <p>
 * Enderman Overhaul is not a compile dependency, hence the string target. If the mod is absent the
 * target class is missing and Mixin simply disables this mixin — both mixin configs are
 * {@code "required": false}.
 */
@Mixin(targets = "tech.alexnijjar.endermanoverhaul.common.entities.EndEnderman", remap = false)
public class EndEndermanTeleportMixin {

    @WrapWithCondition(
            method = "doHurtTarget",
            at = @At(value = "INVOKE",
                    target = "Ltech/alexnijjar/endermanoverhaul/common/utils/ModUtils;"
                            + "teleportTarget(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/entity/LivingEntity;I)V",
                    remap = false),
            remap = false)
    private boolean vpaAllowTeleportAttack(Level level, LivingEntity victim, int range) {
        LivingEntity self = (LivingEntity) (Object) this;
        return !HostileEndermenModule.suppressTeleportAttack(self, victim);
    }
}
