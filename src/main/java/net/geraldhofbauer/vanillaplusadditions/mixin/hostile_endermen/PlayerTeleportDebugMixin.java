package net.geraldhofbauer.vanillaplusadditions.mixin.hostile_endermen;

import net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.HostileEndermenModule;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic hook: logs who teleported a player. Every mod-side player displacement inside a
 * dimension ends up in {@code ServerPlayer#teleportTo(double, double, double)} — vanilla's
 * {@code LivingEntity#randomTeleport} (chorus fruit, Enderman Overhaul, EnhancedAI's teleport
 * anti-cheese, …) routes through it as well — so a single hook plus the caller stack identifies
 * the source. Disabled unless {@code debug_teleport_tracking} is on.
 */
@Mixin(ServerPlayer.class)
public class PlayerTeleportDebugMixin {

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void vpaLogTeleport(double x, double y, double z, CallbackInfo ci) {
        HostileEndermenModule.logPlayerTeleport((ServerPlayer) (Object) this, x, y, z);
    }
}
