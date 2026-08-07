package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent per-level store of all inventory links. Sable/Aeronautics physics platforms are
 * plots inside the same {@link ServerLevel} (at ~20.48M block coordinates), so a plain per-level
 * position store covers world blocks and contraption blocks alike.
 */
public class InventoryLinkData extends SavedData {

    private static final String NAME = "vanillaplusadditions_inventory_links";
    private static final String KEY = "links";

    private final List<InventoryLink> links = new ArrayList<>();
    private final Map<BlockPos, List<InventoryLink>> byPos = new HashMap<>();

    public InventoryLinkData() {
    }

    public static InventoryLinkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(InventoryLinkData::new, InventoryLinkData::load), NAME);
    }

    public static InventoryLinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        InventoryLinkData data = new InventoryLinkData();
        for (Tag entry : tag.getList(KEY, Tag.TAG_COMPOUND)) {
            data.index(InventoryLink.load((CompoundTag) entry));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (InventoryLink link : links) {
            list.add(link.save());
        }
        tag.put(KEY, list);
        return tag;
    }

    private void index(InventoryLink link) {
        links.add(link);
        byPos.computeIfAbsent(link.first(), p -> new ArrayList<>()).add(link);
        byPos.computeIfAbsent(link.second(), p -> new ArrayList<>()).add(link);
    }

    private void unindex(InventoryLink link) {
        links.remove(link);
        removeFromPosIndex(link.first(), link);
        removeFromPosIndex(link.second(), link);
    }

    private void removeFromPosIndex(BlockPos pos, InventoryLink link) {
        List<InventoryLink> atPos = byPos.get(pos);
        if (atPos != null) {
            atPos.remove(link);
            if (atPos.isEmpty()) {
                byPos.remove(pos);
            }
        }
    }

    /** Copy — safe to iterate while links are being removed. */
    public List<InventoryLink> all() {
        return new ArrayList<>(links);
    }

    /** Copy — safe to iterate while links are being removed. */
    public List<InventoryLink> linksAt(BlockPos pos) {
        List<InventoryLink> atPos = byPos.get(pos);
        return atPos != null ? new ArrayList<>(atPos) : List.of();
    }

    public int countAt(BlockPos pos) {
        List<InventoryLink> atPos = byPos.get(pos);
        return atPos != null ? atPos.size() : 0;
    }

    public InventoryLink find(BlockPos a, BlockPos b) {
        List<InventoryLink> atPos = byPos.get(a);
        if (atPos == null) {
            return null;
        }
        for (InventoryLink link : atPos) {
            if (link.matches(a, b)) {
                return link;
            }
        }
        return null;
    }

    public boolean has(BlockPos a, BlockPos b) {
        return find(a, b) != null;
    }

    public void add(InventoryLink link) {
        index(link);
        setDirty();
    }

    public boolean remove(BlockPos a, BlockPos b) {
        InventoryLink link = find(a, b);
        if (link == null) {
            return false;
        }
        unindex(link);
        setDirty();
        return true;
    }

    public boolean setModes(BlockPos a, BlockPos b, LinkMode modeA, LinkMode modeB) {
        InventoryLink link = find(a, b);
        if (link == null) {
            return false;
        }
        // The caller passes modes in (a, b) order; store them in the link's (first, second) order.
        if (link.first().equals(a)) {
            link.setModes(modeA, modeB);
        } else {
            link.setModes(modeB, modeA);
        }
        setDirty();
        return true;
    }
}
