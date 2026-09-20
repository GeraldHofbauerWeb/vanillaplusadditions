package net.geraldhofbauer.vanillaplusadditions.mixin.compass_overhaul;

import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.compat.SableGate;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.compat.SableOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalDouble;

/**
 * Steadies the ordinary compass needle aboard a Sable sub-level.
 *
 * <p>Sable already handles this case — it overwrites the same method, rotates the target into the
 * ship's space and covers both the viewer sitting in the plot and the viewer riding something that
 * does. Its weak spot is the pose it reads: {@code lastPose()}, the pose from the previous tick, so
 * the needle jitters and lags while the ship turns. This injection does the same arithmetic from
 * {@code renderPose(partialTick)} instead. It also matters for a second reason — with Quark
 * installed the ordinary compass never reaches this method at all, and
 * {@code QuarkCompassAngleMixin} has nothing to fall back on but the shared helper this calls.
 *
 * <p>Injecting at HEAD and returning our own value leaves Sable's body in place but unreached.
 * Two mods writing to one method is inherently fragile, hence {@code require = 0} (a missed binding
 * must not crash the game) and a priority above Sable's default, so this is applied <em>after</em>
 * its overwrite rather than being replaced by it. The config switch {@code fix_sublevel_needle}
 * turns it off should the two ever fall out properly.
 */
@Mixin(value = CompassItemPropertyFunction.class, priority = 1500)
public class CompassAngleSubLevelMixin {

    @Inject(method = "getAngleFromEntityToPos", at = @At("HEAD"), cancellable = true, require = 0)
    private void useInterpolatedSubLevelPose(Entity entity, BlockPos pos,
                                             CallbackInfoReturnable<Double> cir) {
        if (!CompassOverhaulModule.fixesSubLevelNeedle() || !SableGate.isLoaded()) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        OptionalDouble bearing = SableOrientation.bearingInSubLevel(entity, pos, partialTick);
        if (bearing.isPresent()) {
            cir.setReturnValue(bearing.getAsDouble());
        }
    }
}
