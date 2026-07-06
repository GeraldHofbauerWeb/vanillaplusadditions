package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.sable;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlFeedingStationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class SableAxolotlFeedingStationBlock extends AxolotlFeedingStationBlock implements BlockSubLevelAssemblyListener {

    public SableAxolotlFeedingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void afterMove(ServerLevel worldFrom, ServerLevel worldTo, BlockState state,
                          BlockPos fromPos, BlockPos toPos) {
        SableAxolotlBowlAssemblyHandler.afterMove(worldFrom, worldTo, fromPos, toPos);
    }
}
