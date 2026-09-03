package net.geraldhofbauer.vanillaplusadditions.mixin.wolf_mount;

import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.WolfMountModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes {@link Wolf} rideable and steerable.
 *
 * <p><b>Why merged overrides and not {@code @Inject}:</b> every hook this needs
 * ({@code getControllingPassenger}, {@code tickRidden}, {@code getRiddenInput},
 * {@code getRiddenSpeed}, {@code positionRider}, {@code getDismountLocationForPassenger}) is
 * declared on {@code Mob}/{@code LivingEntity}/{@code Entity} and <em>not</em> on {@code Wolf}
 * itself. {@code @Inject} needs the target method to exist in the target class, so the only
 * workable style here is declaring the overrides in the mixin and letting Mixin merge them into
 * {@code Wolf}. {@code @Overwrite} is never appropriate — it would clobber vanilla bodies we want
 * to keep.
 *
 * <p><b>Why no AI suppression:</b> vanilla's ridden pipeline already discards the AI's output.
 * {@code Mob.serverAiStep} runs the goals and writes {@code xxa}/{@code zza} via the move control
 * and {@code yHeadRot} via the look control, but {@code LivingEntity.aiStep} then routes into
 * {@code travelRidden}, whose first act is {@code getRiddenInput(...)} — our override ignores the
 * argument entirely — and {@code tickRidden} overwrites the rotations right after. The only goal
 * that moves the entity directly, {@code LeapAtTargetGoal}, already disables itself while a
 * passenger is in control. That is also why the target-selecting goals keep working, which is what
 * makes the mount fight alongside its rider.
 *
 * <p><b>Why {@code extends TamableAnimal}:</b> Mixin requires the declared superclass to be in the
 * target's hierarchy for {@code super.} calls to resolve; {@code TamableAnimal} is {@code Wolf}'s
 * direct superclass. The constructor exists only to satisfy javac and is never invoked.
 *
 * <p>This mixin must be applied on <b>both</b> sides. {@code LocalPlayer.jumpableVehicle()} does an
 * {@code instanceof PlayerRideableJumping} check on the client, and the server needs
 * {@code getControllingPassenger} to agree — so it belongs in the {@code "mixins"} array of
 * {@code vanillaplusadditions.mixins.json}, never in {@code "client"}.
 */
@Mixin(Wolf.class)
public abstract class WolfMountMixin extends TamableAnimal implements PlayerRideableJumping {

    @Unique
    private float vpaJumpPendingScale;

    @Unique
    private boolean vpaIsJumping;

    private WolfMountMixin(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    /**
     * Hands control to a riding player, which is what switches vanilla over to
     * {@code travelRidden}.
     *
     * <p>Gated on nothing but "module on" and "first passenger is a player" — deliberately. This
     * method feeds {@code Entity.isControlledByLocalInstance()}, so if the client and server
     * disagreed here the client would stop sending vehicle movement and the mount would freeze.
     * Eligibility is enforced server-side at mount time instead.
     *
     * <p>The {@code Mob} branch replicates {@code Mob.getControllingPassenger} rather than calling
     * {@code super}, because {@code TamableAnimal} does not declare the method.
     */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity first = this.getFirstPassenger();
        if (WolfMountModule.isModuleActive() && first instanceof Player player) {
            return player;
        }
        if (!this.isNoAi() && first instanceof Mob mob && mob.canControlVehicle()) {
            return mob;
        }
        return null;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 travelVector) {
        super.tickRidden(rider, travelVector);
        Vec2 rotation = new Vec2(rider.getXRot() * 0.5F, rider.getYRot());
        this.setRot(rotation.y, rotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isControlledByLocalInstance() && this.onGround()) {
            if (this.vpaJumpPendingScale > 0.0F && !this.vpaIsJumping) {
                this.vpaExecuteRidersJump(this.vpaJumpPendingScale, travelVector);
            } else {
                // Only clear the "airborne" latch once we are grounded *without* a pending jump,
                // so a charge released on the same tick we land cannot fire twice.
                this.vpaIsJumping = false;
            }
            this.vpaJumpPendingScale = 0.0F;
        }
        if (!this.level().isClientSide() && this.isOrderedToSit()) {
            // A stray sit order (another mod, a command, vanilla's own right-click racing our
            // mount packet) must not make the mount squat mid-gallop.
            this.setOrderedToSit(false);
            this.setInSittingPose(false);
        }
        this.vpaKeepAfloat();
    }

    /**
     * Keeps the mount swimming instead of sinking.
     *
     * <p>An unridden wolf floats because {@code FloatGoal} pulses its jump control — but goals only
     * tick on the server, and while a player rides, the <em>client</em> is authoritative for
     * movement. Nothing was setting the jump flag there, so the wolf walked along the bottom. This
     * re-creates the goal's effect on whichever side is actually driving.
     *
     * <p>{@code LivingEntity.aiStep} evaluates the jump flag in the "jump" phase, one phase before
     * the "travel" phase this runs in, so the impulse lands on the next tick — invisible at 20 Hz.
     *
     * <p><b>Clearing the flag again is not optional.</b> The only thing that normally sets
     * {@code jumping} back to false on a mob is {@code JumpControl.tick()}, which runs inside
     * {@code serverAiStep} — server-side only. On the client, which drives a ridden entity, the
     * flag would stay true forever after the first swim, and {@code LivingEntity.aiStep} answers a
     * raised jump flag on solid ground with {@code jumpFromGround()} — the mount would hop
     * endlessly the moment it left the water.
     */
    @Unique
    private void vpaKeepAfloat() {
        if (!WolfMountModule.isFloatInWater()) {
            return;
        }
        boolean swimming = this.isInLava()
                || this.isInWater() && this.getFluidHeight(FluidTags.WATER) > this.getFluidJumpThreshold();
        this.setJumping(swimming);
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travelVector) {
        float strafe = (float) (rider.xxa * WolfMountModule.getStrafeMultiplier());
        float forward = rider.zza;
        if (forward <= 0.0F) {
            forward *= (float) WolfMountModule.getBackwardMultiplier();
        }
        return new Vec3(strafe, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) (this.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                * WolfMountModule.getSpeedMultiplier());
    }

    /**
     * Keeps the rider's torso aligned with the mount instead of letting it swivel on its own.
     * Mirrors {@code AbstractHorse.positionRider}.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = this.yBodyRot;
        }
    }

    /**
     * Puts the rider down beside the wolf instead of on top of it.
     *
     * <p>{@code Entity}'s default answer is "the top of my bounding box" — on a scale-3.25 wolf
     * that is a 2.8-block drop every single dismount. This mirrors what
     * {@code AbstractHorse.getDismountLocationForPassenger} does: try the rider's dominant side
     * first, then the other, then give up and drop them on the wolf's own position.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float preferred = this.getYRot()
                + (passenger.getMainArm() == HumanoidArm.RIGHT ? 90.0F : -90.0F);
        Vec3 side = getCollisionHorizontalEscapeVector(
                this.getBbWidth(), passenger.getBbWidth(), preferred);
        Vec3 found = this.vpaDismountInDirection(side, passenger);
        if (found != null) {
            return found;
        }
        Vec3 otherSide = getCollisionHorizontalEscapeVector(
                this.getBbWidth(), passenger.getBbWidth(), preferred - 180.0F);
        found = this.vpaDismountInDirection(otherSide, passenger);
        return found != null ? found : this.position();
    }

    /**
     * Scans upwards from the wolf's feet at the given horizontal offset for a spot the rider
     * actually fits in. Reimplemented here because {@code AbstractHorse}'s equivalent is private.
     */
    @Unique
    @Nullable
    private Vec3 vpaDismountInDirection(Vec3 offset, LivingEntity passenger) {
        double x = this.getX() + offset.x;
        double z = this.getZ() + offset.z;
        double ceiling = this.getBoundingBox().maxY + 0.75;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Pose pose : passenger.getDismountPoses()) {
            cursor.set(x, this.getBoundingBox().minY, z);
            do {
                double floor = this.level().getBlockFloorHeight(cursor);
                if (cursor.getY() + floor > ceiling) {
                    break;
                }
                if (DismountHelper.isBlockFloorValid(floor)) {
                    AABB bounds = passenger.getLocalBoundsForPose(pose);
                    Vec3 candidate = new Vec3(x, cursor.getY() + floor, z);
                    if (DismountHelper.canDismountTo(this.level(), passenger, bounds.move(candidate))) {
                        passenger.setPose(pose);
                        return candidate;
                    }
                }
                cursor.move(Direction.UP);
            } while (cursor.getY() < ceiling);
        }
        return null;
    }

    /**
     * Launches the mount.
     *
     * <p>The multiplier is not cosmetic: a wolf's {@code generic.jump_strength} is 0.42, the same
     * as a player's, so {@code getJumpPower} alone produces a hop of barely one block — absurd
     * under a mount three times the size of a horse. A vanilla horse reaches 1.0 at full charge,
     * which is the number the default multiplier is tuned to hit.
     */
    @Unique
    private void vpaExecuteRidersJump(float chargeScale, Vec3 travelVector) {
        double power = this.getJumpPower(chargeScale) * WolfMountModule.getJumpStrength();
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x, power, movement.z);
        this.vpaIsJumping = true;
        this.hasImpulse = true;
        if (travelVector.z > 0.0) {
            float sin = Mth.sin(this.getYRot() * ((float) Math.PI / 180F));
            float cos = Mth.cos(this.getYRot() * ((float) Math.PI / 180F));
            this.setDeltaMovement(this.getDeltaMovement()
                    .add(-0.4F * sin * chargeScale, 0.0, 0.4F * cos * chargeScale));
        }
    }

    /**
     * Turns the client's charge meter into a jump scale.
     *
     * <p>The charge arrives as roughly 10..100. Vanilla's horse maps that onto 0.4..1.0, which
     * makes a tap already 40 % as strong as a full charge — barely distinguishable. This maps onto
     * {@link WolfMountModule#getMinJumpCharge()}..1.0 (default 0.15) so holding the key actually
     * pays off.
     */
    @Override
    public void onPlayerJump(int charge) {
        if (!WolfMountModule.isModuleActive()) {
            return;
        }
        if (WolfMountModule.isInstantJump()) {
            this.vpaJumpPendingScale = 1.0F;
            return;
        }
        float floor = (float) WolfMountModule.getMinJumpCharge();
        float normalized = Math.min(Math.max(charge, 0), 90) / 90.0F;
        this.vpaJumpPendingScale = floor + (1.0F - floor) * normalized;
    }

    /**
     * Polled every frame by the HUD to decide whether to draw the jump meter, so this stays cheap
     * and must not touch anything heavy.
     */
    @Override
    public boolean canJump() {
        return WolfMountModule.isModuleActive() && this.getControllingPassenger() instanceof Player;
    }

    @Override
    public void handleStartJump(int charge) {
        this.playSound(net.minecraft.sounds.SoundEvents.WOLF_PANT, 0.4F, 1.0F);
    }

    @Override
    public void handleStopJump() {
        // Nothing to wind down — the jump itself is executed from tickRidden.
    }
}
