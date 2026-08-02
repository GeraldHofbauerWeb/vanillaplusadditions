package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.AbstractMobCartBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.compat.CreateTrainAccess;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.compat.CreateTrainAccess.TrainSeat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Captures a mob from the adjacent pen (input side) and holds it inside; releases it into a parked,
 * empty rideable minecart on the output side once that side is unobstructed. If the output side is a
 * Create track instead, the mob is seated in the nearest free seat of a standing train carriage.
 */
public class MobLoaderBlockEntity extends AbstractMobCartBlockEntity {

    public MobLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(MobCartLoaderModule.MOB_LOADER_BE.get(), pos, state);
    }

    @Override
    protected void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        if (!AbstractMobCartBlock.isActive(state)) {
            return;
        }
        if (!hasStored()) {
            // Suck in a pen mob and hold it (removed from the world → it can no longer escape).
            Mob pen = findPenMob(level, inputPos(state));
            if (pen != null) {
                storeMob(pen);
            }
            return;
        }
        // Holding a mob → load it into a parked, empty cart when the output side is clear.
        BlockPos out = outputPos(state);
        if (outputBlocked(level, out)) {
            return;
        }
        Minecart cart = findParkedEmptyMinecart(level, out);
        if (cart != null) {
            Entity mob = takeStoredEntity(level, cart.getX(), cart.getY(), cart.getZ());
            if (mob != null) {
                level.addFreshEntity(mob);
                mob.startRiding(cart, true);
            }
            return;
        }
        // No cart — if the output points at a Create track, board a standing train instead.
        BlockPos track = findTrackTarget(level, out, facing(state).getOpposite());
        if (track == null) {
            return;
        }
        TrainSeat seat = CreateTrainAccess.findFreeSeat(level, track,
                MobCartLoaderModule.getTrainSeatSearchRadius());
        if (seat == null) {
            return;
        }
        Entity mob = takeStoredEntity(level, seat.worldPos().x, seat.worldPos().y, seat.worldPos().z);
        if (mob != null) {
            level.addFreshEntity(mob);
            CreateTrainAccess.seat(seat, mob);
        }
    }

    /** Nearest parked, empty, rideable minecart at the cart position. */
    @Nullable
    private Minecart findParkedEmptyMinecart(ServerLevel level, BlockPos cartPos) {
        AABB box = new AABB(cartPos).inflate(0.4);
        List<Minecart> carts = level.getEntitiesOfClass(Minecart.class, box,
                c -> c.getPassengers().isEmpty() && isParked(c));
        return nearest(carts, cartPos);
    }
}
