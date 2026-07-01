package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

/**
 * Isolation seam between the (BlueMap-free) manager and the actual BlueMap integration. The only
 * implementation is {@code compat.BlueMapBridge}, which is the single class allowed to import
 * {@code de.bluecolored.*}. The manager talks exclusively to this interface, so nothing links
 * BlueMap types when the mod is absent.
 */
public interface MarkerBackend {

    /** Hook the backend into BlueMap's lifecycle (e.g. {@code BlueMapAPI.onEnable}). */
    default void register() {
    }

    /** Whether BlueMap is currently enabled and marker pushes will take effect. */
    boolean isLive();

    /** Add or replace a single marker on every map of the given level's world. */
    void upsert(ServerLevel level, MapSignMarker marker);

    /** Remove a marker (by its record id) from every map of the given level's world. */
    void remove(ServerLevel level, String markerId);

    /** Rebuild the whole marker set for a level from the given markers (used on enable/reload). */
    void rebuildAll(ServerLevel level, Collection<MapSignMarker> markers);
}
