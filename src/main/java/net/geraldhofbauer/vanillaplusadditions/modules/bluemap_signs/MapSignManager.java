package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * BlueMap-free orchestration: keeps {@link MapSignData} (source of truth) in sync and, when a live
 * {@link MarkerBackend} is present, mirrors every change onto the BlueMap MarkerSet. All methods run
 * on the server thread (sign mixin hook, chunk/break events, commands).
 */
public class MapSignManager {

    public enum OpResult {
        OK,
        NOT_FOUND,
        SIGN_IMMUTABLE
    }

    private MarkerBackend backend;
    private MinecraftServer server;

    public void setBackend(MarkerBackend backend) {
        this.backend = backend;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public static String signId(BlockPos pos) {
        return "s/" + Long.toUnsignedString(pos.asLong());
    }

    // ---- sign-driven (SIGN markers) ----

    /** A sign at {@code pos} was edited: upsert if it is now a {@code [bm]} marker, else remove. */
    public void handleSignEdit(ServerLevel level, BlockPos pos, Optional<MapSignMarker> marker) {
        MapSignData data = MapSignData.get(level);
        if (marker.isPresent()) {
            MapSignMarker m = marker.get();
            if (!m.equals(data.get(m.id()))) {
                data.put(m);
                pushUpsert(level, m);
            }
        } else {
            removeSignAt(level, pos);
        }
    }

    /** Drop the SIGN marker at {@code pos} (block broken / line 1 no longer matches). */
    public void removeSignAt(ServerLevel level, BlockPos pos) {
        String id = signId(pos);
        MapSignData data = MapSignData.get(level);
        if (data.get(id) != null && data.remove(id)) {
            pushRemove(level, id);
        }
    }

    /** Reconcile all SIGN markers in a freshly loaded chunk against persistence. */
    public void reconcileChunk(ServerLevel level, LevelChunk chunk, String prefix) {
        MapSignData data = MapSignData.get(level);
        ChunkPos cp = chunk.getPos();

        Map<String, MapSignMarker> current = new HashMap<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof SignBlockEntity sign) {
                SignReader.readMarker(sign, prefix).ifPresent(m -> current.put(m.id(), m));
            }
        }

        // Add or update markers found in the chunk.
        for (MapSignMarker m : current.values()) {
            if (!m.equals(data.get(m.id()))) {
                data.put(m);
                pushUpsert(level, m);
            }
        }

        // Drop SIGN markers persisted in this chunk that no longer have a matching sign.
        List<String> stale = new ArrayList<>();
        for (MapSignMarker m : data.markers().values()) {
            if (m.isSign() && cp.equals(new ChunkPos(m.pos())) && !current.containsKey(m.id())) {
                stale.add(m.id());
            }
        }
        for (String id : stale) {
            if (data.remove(id)) {
                pushRemove(level, id);
            }
        }
    }

    // ---- command-driven (COMMAND markers) ----

    public MapSignMarker addCommandMarker(ServerLevel level, BlockPos pos, String label, String iconKey,
                                          String detail) {
        MapSignData data = MapSignData.get(level);
        MapSignMarker marker = new MapSignMarker(data.nextCommandId(), MapSignMarker.Source.COMMAND,
                pos.immutable(), label, iconKey, detail);
        data.put(marker);
        pushUpsert(level, marker);
        return marker;
    }

    public OpResult removeCommandMarker(ServerLevel level, String id) {
        MapSignData data = MapSignData.get(level);
        MapSignMarker existing = data.get(id);
        if (existing == null) {
            return OpResult.NOT_FOUND;
        }
        if (existing.isSign()) {
            return OpResult.SIGN_IMMUTABLE;
        }
        data.remove(id);
        pushRemove(level, id);
        return OpResult.OK;
    }

    /** Transform a COMMAND marker via {@code editor}; rejects SIGN markers and unknown ids. */
    public OpResult editCommandMarker(ServerLevel level, String id, UnaryOperator<MapSignMarker> editor) {
        MapSignData data = MapSignData.get(level);
        MapSignMarker existing = data.get(id);
        if (existing == null) {
            return OpResult.NOT_FOUND;
        }
        if (existing.isSign()) {
            return OpResult.SIGN_IMMUTABLE;
        }
        MapSignMarker updated = editor.apply(existing);
        data.put(updated);
        pushUpsert(level, updated);
        return OpResult.OK;
    }

    public MapSignMarker get(ServerLevel level, String id) {
        return MapSignData.get(level).get(id);
    }

    public List<MapSignMarker> list(ServerLevel level) {
        return new ArrayList<>(MapSignData.get(level).markers().values());
    }

    // ---- lifecycle ----

    /** Rebuild every level's MarkerSet from persistence (called on BlueMap enable / reload). */
    public void rebuildAllFromStorage(MinecraftServer server) {
        if (backend == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            backend.rebuildAll(level, new ArrayList<>(MapSignData.get(level).markers().values()));
        }
    }

    private void pushUpsert(ServerLevel level, MapSignMarker marker) {
        if (backend != null && backend.isLive()) {
            backend.upsert(level, marker);
        }
    }

    private void pushRemove(ServerLevel level, String id) {
        if (backend != null && backend.isLive()) {
            backend.remove(level, id);
        }
    }
}
