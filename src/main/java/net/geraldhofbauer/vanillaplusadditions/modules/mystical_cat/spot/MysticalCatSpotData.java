package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.spot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-{@link ServerLevel} registry of Mystical Cat spot centers, used to enforce the minimum
 * distance between naturally generated spots and to list them via the command.
 */
public class MysticalCatSpotData extends SavedData {

    private static final String NAME = "vanillaplusadditions_mystical_cat_spots";
    private static final String KEY_SPOTS = "spots";

    private final Set<Long> spots = new HashSet<>();

    public MysticalCatSpotData() {
    }

    public static MysticalCatSpotData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MysticalCatSpotData::new, MysticalCatSpotData::load), NAME);
    }

    public static MysticalCatSpotData load(CompoundTag tag, HolderLookup.Provider registries) {
        MysticalCatSpotData data = new MysticalCatSpotData();
        for (long packed : tag.getLongArray(KEY_SPOTS)) {
            data.spots.add(packed);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(KEY_SPOTS, spots.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public void addSpot(BlockPos pos) {
        if (spots.add(pos.asLong())) {
            setDirty();
        }
    }

    public void removeSpot(BlockPos pos) {
        if (spots.remove(pos.asLong())) {
            setDirty();
        }
    }

    public Set<Long> spots() {
        return spots;
    }

    /** True if any existing spot lies within {@code distance} blocks (3D) of {@code pos}. */
    public boolean hasSpotWithin(BlockPos pos, double distance) {
        double sq = distance * distance;
        for (long packed : spots) {
            if (BlockPos.of(packed).distSqr(pos) <= sq) {
                return true;
            }
        }
        return false;
    }
}
