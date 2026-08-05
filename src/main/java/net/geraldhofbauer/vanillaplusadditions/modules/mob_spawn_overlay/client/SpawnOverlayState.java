package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client;

import java.util.List;

/**
 * Toggle plus the most recent scan result. Off by default — this is an on-demand inspection view
 * flipped with {@code F3 + M}.
 */
public final class SpawnOverlayState {

    private static boolean enabled = false;
    private static List<SpawnMarker> markers = List.of();
    private static boolean truncated = false;

    private SpawnOverlayState() { }

    /** Flips the overlay and returns the new state. Clears stale markers when switching off. */
    public static boolean toggle() {
        enabled = !enabled;
        if (!enabled) {
            markers = List.of();
            truncated = false;
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<SpawnMarker> getMarkers() {
        return markers;
    }

    /** Whether the last scan hit the {@code max_markers} cap and dropped positions. */
    public static boolean isTruncated() {
        return truncated;
    }

    static void setResult(List<SpawnMarker> scanned, boolean hitCap) {
        markers = scanned;
        truncated = hitCap;
    }
}
