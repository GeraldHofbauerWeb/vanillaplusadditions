package net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent per-level record of the loader-rail positions that currently have an active cart
 * (encoded as {@code BlockPos.asLong()}). Survives "no players online" pauses and server
 * restarts, so the chunks can be force-loaded again on resume and stuck carts continue moving.
 */
public class ChunkLoaderData extends SavedData {

    private static final String NAME = "vanillaplusadditions_chunk_loader";
    private static final String KEY = "active_rails";

    private final Set<Long> activeRails = new HashSet<>();

    public ChunkLoaderData() {
    }

    public static ChunkLoaderData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChunkLoaderData::new, ChunkLoaderData::load), NAME);
    }

    public static ChunkLoaderData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkLoaderData data = new ChunkLoaderData();
        for (long packed : tag.getLongArray(KEY)) {
            data.activeRails.add(packed);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(KEY, activeRails.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public Set<Long> rails() {
        return activeRails;
    }

    public void add(long railPos) {
        if (activeRails.add(railPos)) {
            setDirty();
        }
    }

    public void remove(long railPos) {
        if (activeRails.remove(railPos)) {
            setDirty();
        }
    }
}
