package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.AbstractMobCartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Loads a pen mob into a parked, empty rideable minecart. Shows the pen candidate as the mini-mob.
 */
public class MobLoaderBlockEntity extends AbstractMobCartBlockEntity {

    public MobLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(MobCartLoaderModule.MOB_LOADER_BE.get(), pos, state);
    }

    @Override
    protected void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        // Loader: mob comes from the input (pen) side, goes into a cart on the output side.
        Mob penMob = findPenMob(level, inputPos(state));
        // Display the pen candidate even before (or without) a cart present.
        updateDisplayFrom(penMob);

        if (!AbstractMobCartBlock.isActive(state) || penMob == null) {
            return;
        }
        Minecart cart = findParkedEmptyMinecart(level, outputPos(state));
        if (cart == null) {
            return;
        }
        penMob.startRiding(cart, true);
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
