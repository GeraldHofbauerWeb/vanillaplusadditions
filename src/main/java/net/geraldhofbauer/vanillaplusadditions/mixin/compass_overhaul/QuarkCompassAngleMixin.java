package net.geraldhofbauer.vanillaplusadditions.mixin.compass_overhaul;

import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.compat.SableGate;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.compat.SableOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalDouble;

/**
 * Repairs the compass needle that Quark takes over.
 *
 * <p>Quark's <em>Compasses Work Everywhere</em> replaces the needle wholesale —
 * {@code ItemProperties.register(Items.COMPASS, "angle", new CompassAnglePropertyFunction())} — so
 * for an ordinary compass neither vanilla's {@code CompassItemPropertyFunction} nor anything built
 * on top of it is reached any more. Three things are wrong in the replacement:
 *
 * <ul>
 *   <li>It aims at the raw block position instead of the block's centre. The raw position is the
 *       corner with the smallest X and Z — that is, the <strong>north-west</strong> corner — so the
 *       needle sits half a block off in both axes, plainly visible from close up.</li>
 *   <li>It knows nothing about Sable sub-levels, so aboard an airship the needle points at "ship
 *       north" (this is the reason our own sub-level fix on the vanilla function never fires for an
 *       ordinary compass in this pack).</li>
 *   <li>Its item-frame rotation drops the frame's eight rotation steps, so a turned frame shows a
 *       needle turned by the same amount.</li>
 * </ul>
 *
 * <p>Quark is not a compile dependency of this module, hence the string target; without Quark, Mixin
 * simply disables this mixin (the mixin configs are {@code "required": false}). Note that Quark's
 * method works in <em>radians</em>, unlike vanilla's, which uses turns.
 */
@Mixin(targets = "org.violetmoon.quark.content.tweaks.client.item.CompassAnglePropertyFunction",
        remap = false)
public class QuarkCompassAngleMixin {

    private static final double FULL_TURN = Math.PI * 2.0;

    @Inject(method = "getAngleToPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void vpaAimAtBlockCentre(Entity entity, BlockPos target,
                                     CallbackInfoReturnable<Double> cir) {
        if (!CompassOverhaulModule.fixesQuarkCompass()) {
            return;
        }
        if (SableGate.isLoaded()) {
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            OptionalDouble bearing = SableOrientation.bearingInSubLevel(entity, target, partialTick);
            if (bearing.isPresent()) {
                cir.setReturnValue(bearing.getAsDouble() * FULL_TURN);
                return;
            }
        }
        Vec3 viewer = entity.position();
        Vec3 centre = Vec3.atCenterOf(target);
        cir.setReturnValue(Math.atan2(centre.z - viewer.z, centre.x - viewer.x));
    }

    @Inject(method = "getFrameRotation", at = @At("HEAD"), cancellable = true, remap = false)
    private void vpaIncludeFrameRotationSteps(ItemFrame frame, CallbackInfoReturnable<Double> cir) {
        if (!CompassOverhaulModule.fixesQuarkCompass()) {
            return;
        }
        // Vanilla folds the frame's eight rotation steps (and the fudge for floor and ceiling
        // frames) into this value — exactly the amount the frame renderer turns the item by.
        cir.setReturnValue((double) frame.getVisualRotationYInDegrees());
    }
}
