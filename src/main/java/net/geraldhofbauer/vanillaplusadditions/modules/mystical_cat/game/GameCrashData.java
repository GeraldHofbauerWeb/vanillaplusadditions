package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-{@link ServerLevel} crash-safety mirror of the temporary blocks a running game has placed. For
 * every active session it stores the <em>original</em> block states that must be restored. On a
 * clean cleanup/win/abort the session removes its own entry; if the server crashes mid-game the
 * entries survive and {@link #restoreInto} puts the world back on the next start.
 */
public class GameCrashData extends SavedData {

    private static final String NAME = "vanillaplusadditions_mystical_cat_games";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_ID = "id";
    private static final String KEY_BLOCKS = "blocks";
    private static final String KEY_POS = "pos";
    private static final String KEY_STATE = "state";

    /** snapshotId → list of (pos, original state) to restore. Insertion-ordered for stable saves. */
    private final Map<String, List<Restore>> sessions = new LinkedHashMap<>();

    private record Restore(BlockPos pos, BlockState state) {
    }

    public GameCrashData() {
    }

    public static GameCrashData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GameCrashData::new, GameCrashData::load), NAME);
    }

    public static GameCrashData load(CompoundTag tag, HolderLookup.Provider registries) {
        GameCrashData data = new GameCrashData();
        HolderLookup.RegistryLookup<net.minecraft.world.level.block.Block> blocks =
                registries.lookupOrThrow(Registries.BLOCK);
        ListTag sessionList = tag.getList(KEY_SESSIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < sessionList.size(); i++) {
            CompoundTag s = sessionList.getCompound(i);
            List<Restore> restores = new ArrayList<>();
            ListTag blockList = s.getList(KEY_BLOCKS, Tag.TAG_COMPOUND);
            for (int j = 0; j < blockList.size(); j++) {
                CompoundTag b = blockList.getCompound(j);
                BlockState state = NbtUtils.readBlockState(blocks, b.getCompound(KEY_STATE));
                restores.add(new Restore(BlockPos.of(b.getLong(KEY_POS)), state));
            }
            data.sessions.put(s.getString(KEY_ID), restores);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag sessionList = new ListTag();
        for (Map.Entry<String, List<Restore>> e : sessions.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putString(KEY_ID, e.getKey());
            ListTag blockList = new ListTag();
            for (Restore r : e.getValue()) {
                CompoundTag b = new CompoundTag();
                b.putLong(KEY_POS, r.pos().asLong());
                b.put(KEY_STATE, NbtUtils.writeBlockState(r.state()));
                blockList.add(b);
            }
            s.put(KEY_BLOCKS, blockList);
            sessionList.add(s);
        }
        tag.put(KEY_SESSIONS, sessionList);
        return tag;
    }

    /** Records the original state at {@code pos} for session {@code id} (before a temp block is set). */
    public void recordBlock(String id, BlockPos pos, BlockState originalState) {
        sessions.computeIfAbsent(id, k -> new ArrayList<>()).add(new Restore(pos.immutable(), originalState));
        setDirty();
    }

    /** Drops a session's mirror once it has been restored/cleaned up normally. */
    public void clearSession(String id) {
        if (sessions.remove(id) != null) {
            setDirty();
        }
    }

    /**
     * Restores every pending session's blocks (crash leftovers) into the level, then clears them.
     * Iterated newest-recorded-last so restores apply in reverse placement order per session.
     */
    public void restoreInto(ServerLevel level) {
        if (sessions.isEmpty()) {
            return;
        }
        for (List<Restore> restores : sessions.values()) {
            // Reverse placement order per session; setBlock force-loads the (rare, few) leftover chunks.
            for (int i = restores.size() - 1; i >= 0; i--) {
                Restore r = restores.get(i);
                level.setBlock(r.pos(), r.state(), 3);
            }
        }
        sessions.clear();
        setDirty();
    }

    /** True if nothing is pending (used to avoid loading chunks needlessly). */
    public boolean isEmpty() {
        return sessions.isEmpty();
    }
}
