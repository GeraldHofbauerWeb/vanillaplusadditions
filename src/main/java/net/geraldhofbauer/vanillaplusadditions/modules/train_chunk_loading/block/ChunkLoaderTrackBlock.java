package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.block;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * The Chunk Loader Track block — a full Create track (connectable, curvable, drivable) whose
 * presence under a passing train carriage marks it active in the module's ChunkLoaderManager.
 * All behavior is inherited from {@link TrackBlock}; the chunk-loading logic lives in the
 * module's event handlers, keyed on this block type.
 */
public class ChunkLoaderTrackBlock extends TrackBlock {

    public ChunkLoaderTrackBlock(Properties properties, TrackMaterial material) {
        super(properties, material);
    }

    // Loot tables via JSON don't load reliably in this mod (see CLAUDE.md) — drop in code.
    // Also covers Create's wrench pickup, which goes through Block.getDrops as well.
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
