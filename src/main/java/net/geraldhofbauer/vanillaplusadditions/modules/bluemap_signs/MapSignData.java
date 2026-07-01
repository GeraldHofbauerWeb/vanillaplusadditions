package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-{@link ServerLevel} store of {@link MapSignMarker}s (both SIGN- and COMMAND-sourced).
 * This is the source of truth; the BlueMap MarkerSet is rebuilt from here on enable/reload. Mirrors
 * the {@code ChunkAnchorData} SavedData pattern.
 */
public class MapSignData extends SavedData {

    private static final String NAME = "vanillaplusadditions_map_signs";
    private static final String KEY_MARKERS = "markers";
    private static final String KEY_COUNTER = "command_counter";

    private final Map<String, MapSignMarker> markers = new HashMap<>();
    private int commandCounter = 0;

    public MapSignData() {
    }

    public static MapSignData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MapSignData::new, MapSignData::load), NAME);
    }

    public static MapSignData load(CompoundTag tag, HolderLookup.Provider registries) {
        MapSignData data = new MapSignData();
        data.commandCounter = tag.getInt(KEY_COUNTER);
        ListTag list = tag.getList(KEY_MARKERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            MapSignMarker.Source source;
            try {
                source = MapSignMarker.Source.valueOf(m.getString("source"));
            } catch (IllegalArgumentException e) {
                source = MapSignMarker.Source.COMMAND;
            }
            MapSignMarker marker = new MapSignMarker(
                    m.getString("id"), source, BlockPos.of(m.getLong("pos")),
                    m.getString("label"), m.getString("icon"), m.getString("detail"));
            data.markers.put(marker.id(), marker);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_COUNTER, commandCounter);
        ListTag list = new ListTag();
        for (MapSignMarker marker : markers.values()) {
            CompoundTag m = new CompoundTag();
            m.putString("id", marker.id());
            m.putString("source", marker.source().name());
            m.putLong("pos", marker.pos().asLong());
            m.putString("label", marker.label());
            m.putString("icon", marker.iconKey());
            m.putString("detail", marker.detail());
            list.add(m);
        }
        tag.put(KEY_MARKERS, list);
        return tag;
    }

    public Map<String, MapSignMarker> markers() {
        return markers;
    }

    public MapSignMarker get(String id) {
        return markers.get(id);
    }

    public void put(MapSignMarker marker) {
        markers.put(marker.id(), marker);
        setDirty();
    }

    public boolean remove(String id) {
        if (markers.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Allocates the next stable command-marker id (e.g. {@code c0}, {@code c1}, ...). */
    public String nextCommandId() {
        String id = "c" + commandCounter++;
        setDirty();
        return id;
    }
}
