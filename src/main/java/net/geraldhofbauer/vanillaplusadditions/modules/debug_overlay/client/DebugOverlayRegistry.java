package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of {@link DebugOverlayRenderer}s. Modules register their renderers during client
 * setup; the framework's event handlers dispatch to every registered renderer.
 */
public final class DebugOverlayRegistry {

    private static final List<DebugOverlayRenderer> RENDERERS = new ArrayList<>();

    private DebugOverlayRegistry() { }

    public static void register(DebugOverlayRenderer renderer) {
        if (renderer != null && !RENDERERS.contains(renderer)) {
            RENDERERS.add(renderer);
        }
    }

    public static List<DebugOverlayRenderer> renderers() {
        return RENDERERS;
    }
}
