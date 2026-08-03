package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.compat;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.TrainChunkLoadingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Marks Chunk Loader Tracks active while a train carriage is over them. Scans the carriage's
 * whole AABB footprint (not just the entity anchor) so long/rotated carriages and ascending
 * tracks are covered. Create-typed, so only instantiated when the module initialized.
 */
public final class TrainChunkLoadingEvents {

    /** Scan cadence in ticks, staggered per entity id so carriages don't all scan the same tick. */
    private static final int SCAN_INTERVAL = 10;

    private final TrainChunkLoadingModule module;

    public TrainChunkLoadingEvents(TrainChunkLoadingModule module) {
        this.module = module;
    }

    @SubscribeEvent
    public void onCarriageTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof CarriageContraptionEntity carriage)) {
            return;
        }
        if (!(carriage.level() instanceof ServerLevel level)) {
            return;
        }
        if (!module.isModuleEnabled()) {
            return;
        }
        if ((carriage.tickCount + (carriage.getId() & 7)) % SCAN_INTERVAL != 0) {
            return;
        }

        AABB box = carriage.getBoundingBox();
        int baseY = Mth.floor(box.minY);
        Block track = TrainChunkLoadingModule.CHUNK_LOADER_TRACK.get();
        long now = level.getGameTime();
        // y span: 2 below (bogey/wheel offset, ascending exits) to 1 above the carriage bottom.
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), baseY - 2, Mth.floor(box.minZ),
                Mth.floor(box.maxX), baseY + 1, Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(track)) {
                module.getManager().markActive(level, pos.immutable(), now);
            }
        }
    }
}
