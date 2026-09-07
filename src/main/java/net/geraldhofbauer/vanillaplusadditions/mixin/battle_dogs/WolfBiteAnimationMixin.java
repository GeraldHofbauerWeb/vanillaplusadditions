package net.geraldhofbauer.vanillaplusadditions.mixin.battle_dogs;

import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a biting wolf a visible head snap.
 *
 * <p>Vanilla wolves have no attack animation whatsoever — {@code WolfModel} never reads
 * {@code attackAnim}, so an attacking wolf deals its damage without moving a pixel. That is barely
 * noticeable for a pet trotting along behind you and glaring while riding one, where the head fills
 * the lower half of the screen.
 *
 * <p>Nothing has to be sent for this. {@code MeleeAttackGoal} already calls {@code swing()} on every
 * landed hit, the server already broadcasts that as a {@code ClientboundAnimatePacket}, and
 * {@code getAttackAnim} turns it into a 0→1 ramp over the swing duration. This mixin only reads it.
 *
 * <p>Both parts it touches are assigned fresh every frame before the injection point —
 * {@code head.xRot} two lines above in {@code setupAnim}, {@code upperBody.xRot} in
 * {@code prepareMobModel}, which runs first. Adding to them is therefore safe and cannot
 * accumulate across frames, which a translation would.
 */
@Mixin(WolfModel.class)
public abstract class WolfBiteAnimationMixin {

    /** How far the head pitches down at full swing, in radians, at strength 1.0. */
    private static final float BITE_PITCH = 0.9F;
    /** A much smaller forward lean of the shoulders, to stop the head moving on its own. */
    private static final float BITE_LEAN = 0.12F;

    @Shadow
    @Final
    private ModelPart head;

    @Shadow
    @Final
    private ModelPart upperBody;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/Wolf;FFFFF)V", at = @At("TAIL"))
    private void applyBiteAnimation(Wolf entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!BattleDogsModule.isModuleActive() || !BattleDogsModule.isBiteAnimation()) {
            return;
        }
        if (BattleDogsModule.isBiteAnimationOnlyWhenRidden() && !entity.isVehicle()) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float swing = entity.getAttackAnim(partialTick);
        if (swing <= 0.0F) {
            return;
        }
        // attackAnim ramps 0->1 over the swing and snaps back; a sine turns that into down-and-up.
        float curve = Mth.sin(swing * Mth.PI);
        float strength = (float) BattleDogsModule.getBiteAnimationStrength();
        this.head.xRot += curve * BITE_PITCH * strength;
        this.upperBody.xRot += curve * BITE_LEAN * strength;
    }
}
