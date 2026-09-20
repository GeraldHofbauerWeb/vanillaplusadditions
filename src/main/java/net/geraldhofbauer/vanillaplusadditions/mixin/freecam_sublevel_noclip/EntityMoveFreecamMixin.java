package net.geraldhofbauer.vanillaplusadditions.mixin.freecam_sublevel_noclip;

import net.geraldhofbauer.vanillaplusadditions.modules.freecam_sublevel_noclip.FreecamSublevelNoclipModule;
import net.geraldhofbauer.vanillaplusadditions.modules.freecam_sublevel_noclip.compat.FreecamAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns off collision for Freecam's camera the instant before it moves.
 *
 * <p>The flag has to be written here and nowhere else. {@code Player.tick} opens with
 * {@code this.noPhysics = this.isSpectator();} — and Freecam's camera is a {@link
 * net.minecraft.client.player.LocalPlayer}, not a spectator — so anything set earlier in the tick,
 * from a client-tick handler for instance, is wiped again before the camera ever moves. The first
 * thing {@code Entity.move} does is read that same flag:
 *
 * <pre>
 * public void move(MoverType type, Vec3 movement) {
 *     if (this.noPhysics) { this.setPos(...); }      // ← world and sub-level both skipped
 *     else { ... Vec3 vec3 = this.collide(movement); ... }   // ← Sable's redirect lives here
 * </pre>
 *
 * <p>Setting it at HEAD therefore takes out vanilla's collision and Sable's sub-level collision in
 * one step, without naming a single Sable class.
 *
 * <p><strong>Cost.</strong> {@code Entity.move} runs for every entity every tick, so the first test
 * is a reference comparison against the camera entity — everything else is only reached by the one
 * entity that is the camera. The mixin is client-only; on a dedicated server it is never applied,
 * which also keeps {@code Minecraft.getInstance()} off the server's class path.
 */
@Mixin(Entity.class)
public class EntityMoveFreecamMixin {

    @Inject(method = "move", at = @At("HEAD"))
    private void vpaFreecamNoclip(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self != Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        FreecamSublevelNoclipModule module = FreecamSublevelNoclipModule.getInstance();
        if (module == null || !module.isModuleEnabled() || !FreecamAccess.isLoaded()) {
            return;
        }
        if (!FreecamAccess.isCamera(self) || !FreecamAccess.ignoresAllBlocks()) {
            return;
        }
        self.noPhysics = true;
    }
}
