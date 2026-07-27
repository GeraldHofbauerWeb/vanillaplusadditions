package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block;

import com.mojang.serialization.MapCodec;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.AbstractMobCartBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.MobLoaderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a mob standing in the adjacent pen into a parked, empty rideable minecart on the block's
 * facing side. Shows the pen candidate as the spinning mini-mob.
 */
public class MobLoaderBlock extends AbstractMobCartBlock {

    public static final MapCodec<MobLoaderBlock> CODEC = simpleCodec(MobLoaderBlock::new);

    public MobLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<MobLoaderBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AbstractMobCartBlockEntity> getBlockEntityType() {
        return MobCartLoaderModule.MOB_LOADER_BE.get();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobLoaderBlockEntity(pos, state);
    }
}
