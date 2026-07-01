package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.compat;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.WebApp;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.BluemapSignsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.IconKey;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.MapSignManager;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.MapSignMarker;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.MarkerBackend;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * THE single class that imports {@code de.bluecolored.*}. Instantiated only when BlueMap is loaded
 * (guarded by {@code ModList.isLoaded("bluemap")} in the module), so BlueMap types are never linked
 * when the mod is absent. Bridges {@link MapSignManager} mutations onto BlueMap MarkerSets.
 */
public final class BlueMapBridge implements MarkerBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlueMapBridge.class);
    private static final String MARKER_SET_ID = "vpa_map_signs";
    private static final String ICON_BASE_PATH = "/assets/vanillaplusadditions/bluemap_icons/";

    private final BluemapSignsModule module;
    private final MapSignManager manager;

    private volatile BlueMapAPI api;
    private volatile boolean live;
    private final Map<String, String> iconAddresses = new HashMap<>();
    private final Map<String, int[]> iconAnchors = new HashMap<>();

    public BlueMapBridge(BluemapSignsModule module, MapSignManager manager) {
        this.module = module;
        this.manager = manager;
    }

    @Override
    public void register() {
        BlueMapAPI.onEnable(this::onEnable);
        BlueMapAPI.onDisable(this::onDisable);
    }

    private void onEnable(BlueMapAPI enabledApi) {
        this.api = enabledApi;
        this.live = true;
        try {
            setupIcons(enabledApi.getWebApp());
        } catch (Exception e) {
            LOGGER.warn("[bluemap_signs] Failed to register marker icons", e);
        }
        MinecraftServer server = manager.getServer();
        if (server != null) {
            // onEnable may run on a BlueMap thread -> mutate markers on the server thread.
            server.execute(() -> manager.rebuildAllFromStorage(server));
        }
        LOGGER.info("[bluemap_signs] BlueMap integration enabled (BlueMap {})", enabledApi.getBlueMapVersion());
    }

    private void onDisable(BlueMapAPI disabledApi) {
        this.live = false;
        this.api = null;
    }

    @Override
    public boolean isLive() {
        return live;
    }

    private void setupIcons(WebApp webApp) {
        iconAddresses.clear();
        iconAnchors.clear();
        for (IconKey key : IconKey.values()) {
            BufferedImage img = loadImage(key.fileName());
            if (img == null) {
                continue;
            }
            try {
                String address = webApp.createImage(img, "vanillaplusadditions/icons/" + key.key());
                iconAddresses.put(key.key(), address);
                iconAnchors.put(key.key(), new int[]{img.getWidth() / 2, img.getHeight()});
            } catch (IOException e) {
                LOGGER.warn("[bluemap_signs] Could not register icon '{}'", key.key(), e);
            }
        }
    }

    private BufferedImage loadImage(String fileName) {
        try (InputStream in = BlueMapBridge.class.getResourceAsStream(ICON_BASE_PATH + fileName)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void rebuildAll(ServerLevel level, Collection<MapSignMarker> markers) {
        BlueMapAPI current = this.api;
        if (current == null) {
            return;
        }
        current.getWorld(level).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                MarkerSet set = new MarkerSet(module.getConfig().getMarkerSetName(),
                        module.getConfig().isToggleable(), module.getConfig().isDefaultHidden());
                for (MapSignMarker marker : markers) {
                    set.getMarkers().put(markerId(level, marker.id()), buildPoi(marker));
                }
                map.getMarkerSets().put(MARKER_SET_ID, set);
            }
        });
    }

    @Override
    public void upsert(ServerLevel level, MapSignMarker marker) {
        BlueMapAPI current = this.api;
        if (current == null) {
            return;
        }
        current.getWorld(level).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                markerSetOf(map).getMarkers().put(markerId(level, marker.id()), buildPoi(marker));
            }
        });
    }

    @Override
    public void remove(ServerLevel level, String markerId) {
        BlueMapAPI current = this.api;
        if (current == null) {
            return;
        }
        current.getWorld(level).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set != null) {
                    set.getMarkers().remove(markerId(level, markerId));
                }
            }
        });
    }

    private MarkerSet markerSetOf(BlueMapMap map) {
        return map.getMarkerSets().computeIfAbsent(MARKER_SET_ID, k -> new MarkerSet(
                module.getConfig().getMarkerSetName(),
                module.getConfig().isToggleable(), module.getConfig().isDefaultHidden()));
    }

    private POIMarker buildPoi(MapSignMarker marker) {
        String label = marker.label().isBlank() ? "(unnamed)" : marker.label();
        POIMarker.Builder builder = POIMarker.builder()
                .label(label)
                .position(marker.pos().getX() + 0.5, marker.pos().getY() + 0.5, marker.pos().getZ() + 0.5)
                .maxDistance(module.getConfig().getMaxDistance());

        String iconAddress = iconAddresses.get(IconKey.resolve(marker.iconKey()).key());
        int[] anchor = iconAnchors.get(IconKey.resolve(marker.iconKey()).key());
        if (iconAddress != null && anchor != null) {
            builder.icon(iconAddress, anchor[0], anchor[1]);
        } else {
            builder.defaultIcon();
        }

        if (!marker.detail().isBlank()) {
            builder.detail(escapeHtml(marker.detail()));
        }
        return builder.build();
    }

    private String markerId(ServerLevel level, String recordId) {
        return level.dimension().location() + "/" + recordId;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
