package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.AbstractMobCartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ejects mob passengers of a parked minecart into the adjacent pen. Shows the cart passenger as the
 * mini-mob.
 */
public class MobUnloaderBlockEntity extends AbstractMobCartBlockEntity {

    public MobUnloaderBlockEntity(BlockPos pos, BlockState state) {
        super(MobCartLoaderModule.MOB_UNLOADER_BE.get(), pos, state);
    }

    @Override
    protected void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        // Unloader: mob comes from a cart on the input side, is ejected to the output (pen) side.
        AbstractMinecart cart = findCart(level, inputPos(state));
        Mob cartMob = cart != null ? firstMobPassenger(cart) : null;
        // Display the cart passenger.
        updateDisplayFrom(cartMob);

        if (!AbstractMobCartBlock.isActive(state) || cart == null || cartMob == null) {
            return;
        }
        if (!isParked(cart)) {
            return;
        }

        BlockPos penPos = outputPos(state);
        double px = penPos.getX() + 0.5;
        double py = penPos.getY();
        double pz = penPos.getZ() + 0.5;

        // Eject every mob passenger (never players) into the pen. Copy first — dismounting mutates
        // the passenger list.
        List<Mob> toEject = new ArrayList<>();
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Mob mob) {
                toEject.add(mob);
            }
        }
        for (Mob mob : toEject) {
            mob.stopRiding();
            mob.teleportTo(px, py, pz);
        }
    }
}
