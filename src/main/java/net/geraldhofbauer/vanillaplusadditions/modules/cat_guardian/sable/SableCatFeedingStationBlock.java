package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatFeedingStationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class SableCatFeedingStationBlock extends CatFeedingStationBlock implements BlockSubLevelAssemblyListener {

    public SableCatFeedingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void afterMove(ServerLevel worldFrom, ServerLevel worldTo, BlockState state,
                          BlockPos fromPos, BlockPos toPos) {
        SableBowlAssemblyHandler.afterMove(worldFrom, worldTo, fromPos, toPos);
    }
}
