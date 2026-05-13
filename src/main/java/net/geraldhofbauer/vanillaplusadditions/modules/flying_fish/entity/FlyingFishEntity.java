package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.entity;

import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.FlyingFishModule;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FlyingFishEntity extends Cod {
    private static final float LEAP_CHANCE = 0.12F;
    private static final double MIN_GLIDE_FALL_SPEED = -0.08D;

    private int leapCooldown;

    public FlyingFishEntity(EntityType<? extends FlyingFishEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void aiStep() {
        if (leapCooldown > 0) {
            leapCooldown--;
        }

        if (this.isInWater() && canLeapFromSurface()) {
            launchOutOfWater();
        }

        super.aiStep();

        if (!this.isInWater() && !this.onGround()) {
            applyAirGlide();
        }
    }

    private boolean canLeapFromSurface() {
        if (leapCooldown > 0 || this.getTarget() != null || this.random.nextFloat() >= LEAP_CHANCE) {
            return false;
        }

        BlockPos position = this.blockPosition();
        return this.level().getFluidState(position).is(FluidTags.WATER)
                && !this.level().getFluidState(position.above()).is(FluidTags.WATER);
    }

    private void launchOutOfWater() {
        Vec3 direction = getHorizontalDirection();
        double speed = 0.45D + this.random.nextDouble() * 0.22D;
        double upwardBoost = 0.56D + this.random.nextDouble() * 0.16D;

        this.setDeltaMovement(direction.x * speed, upwardBoost, direction.z * speed);
        this.hasImpulse = true;
        this.leapCooldown = 16 + this.random.nextInt(24);
    }

    private Vec3 getHorizontalDirection() {
        Vec3 movement = this.getDeltaMovement();
        if (movement.horizontalDistanceSqr() > 1.0E-4D) {
            return new Vec3(movement.x, 0.0D, movement.z).normalize();
        }

        float yawRadians = (this.getYRot() + (this.random.nextFloat() * 50.0F - 25.0F)) * ((float) Math.PI / 180.0F);
        return new Vec3(-Mth.sin(yawRadians), 0.0D, Mth.cos(yawRadians));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(FlyingFishModule.FLYING_FISH_BUCKET.get());
    }

    private void applyAirGlide() {
        Vec3 deltaMovement = this.getDeltaMovement();
        double clampedFallSpeed = Math.max(deltaMovement.y, MIN_GLIDE_FALL_SPEED);
        this.setDeltaMovement(deltaMovement.x * 1.01D, clampedFallSpeed, deltaMovement.z * 1.01D);
    }
}

