package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

/**
 * Central master toggle for all debug overlays (chunk borders, cat stats, future renderers).
 * Off by default — this is an on-demand inspection view, flipped via the shared keybind.
 */
public final class DebugOverlayState {

    private static boolean enabled = false;

    private DebugOverlayState() { }

    /** Flips the master toggle and returns the new state. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
