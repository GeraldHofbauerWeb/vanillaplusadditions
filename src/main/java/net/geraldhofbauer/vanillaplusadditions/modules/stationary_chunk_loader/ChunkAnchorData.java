package net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent per-level record of stationary Chunk Anchor positions (encoded as
 * {@code BlockPos.asLong()}). Unlike the minecart loader rails, anchors never time out: an anchor
 * keeps its chunk loaded until the block is broken. The set survives "no players online" pauses and
 * server restarts so the chunks can be force-loaded again on resume.
 */
public class ChunkAnchorData extends SavedData {

    private static final String NAME = "vanillaplusadditions_chunk_anchor";
    private static final String KEY = "anchors";

    private final Set<Long> anchors = new HashSet<>();

    public ChunkAnchorData() {
    }

    public static ChunkAnchorData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChunkAnchorData::new, ChunkAnchorData::load), NAME);
    }

    public static ChunkAnchorData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkAnchorData data = new ChunkAnchorData();
        for (long packed : tag.getLongArray(KEY)) {
            data.anchors.add(packed);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(KEY, anchors.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public Set<Long> anchors() {
        return anchors;
    }

    public void add(long anchorPos) {
        if (anchors.add(anchorPos)) {
            setDirty();
        }
    }

    public void remove(long anchorPos) {
        if (anchors.remove(anchorPos)) {
            setDirty();
        }
    }
}
