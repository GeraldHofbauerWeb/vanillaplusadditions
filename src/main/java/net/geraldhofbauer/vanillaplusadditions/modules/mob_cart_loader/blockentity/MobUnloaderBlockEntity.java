package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.AbstractMobCartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Captures a mob riding a parked minecart (input side) and holds it inside; releases it into the pen
 * on the output side once that side is unobstructed. A block in front of the output (e.g. a piston
 * head) keeps the mob buffered, so ejection can be gated with redstone/pistons.
 */
public class MobUnloaderBlockEntity extends AbstractMobCartBlockEntity {

    public MobUnloaderBlockEntity(BlockPos pos, BlockState state) {
        super(MobCartLoaderModule.MOB_UNLOADER_BE.get(), pos, state);
    }

    @Override
    protected void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        if (!AbstractMobCartBlock.isActive(state)) {
            return;
        }
        if (!hasStored()) {
            // Pull a passenger out of a parked cart and hold it inside.
            AbstractMinecart cart = findCart(level, inputPos(state));
            if (cart != null && isParked(cart)) {
                Mob mob = firstMobPassenger(cart);
                if (mob != null) {
                    storeMob(mob);
                }
            }
            return;
        }
        // Holding a mob → eject it into the pen when the output side is clear (into water if aquatic).
        BlockPos out = outputPos(state);
        if (outputBlocked(level, out)) {
            return;
        }
        releaseStoredNear(level, out);
    }
}
