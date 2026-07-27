package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block;

import com.mojang.serialization.MapCodec;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.AbstractMobCartBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.MobUnloaderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Ejects a mob riding a parked minecart on the block's facing side into the adjacent pen. Shows the
 * cart passenger as the spinning mini-mob.
 */
public class MobUnloaderBlock extends AbstractMobCartBlock {

    public static final MapCodec<MobUnloaderBlock> CODEC = simpleCodec(MobUnloaderBlock::new);

    public MobUnloaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<MobUnloaderBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AbstractMobCartBlockEntity> getBlockEntityType() {
        return MobCartLoaderModule.MOB_UNLOADER_BE.get();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobUnloaderBlockEntity(pos, state);
    }
}
