package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import java.util.Locale;

/**
 * Built-in icon keys usable on line 3 of a {@code [bm]} sign or as the {@code icon} argument of
 * {@code /bmsigns}. Each key maps to a bundled PNG under
 * {@code assets/vanillaplusadditions/bluemap_icons/<key>.png} and a translation key used by the
 * {@code /bmsigns help} listing. Unknown/empty keys resolve to {@link #DEFAULT}.
 */
public enum IconKey {
    BASE,
    HOME,
    SHOP,
    FARM,
    PORTAL,
    WARP,
    MINE,
    NETHER,
    END,
    SPAWN,
    DANGER,
    STATION,
    STORAGE,
    REDSTONE,
    DEKO,
    TOWER,
    FACTORY,
    VILLAGE,
    CASTLE,
    ENCHANT,
    ANVIL,
    BREW,
    TREASURE,
    GRAVE,
    ARENA,
    MOUNTAIN,
    CAVE,
    DOCK,
    BRIDGE,
    WOODS,
    BANK,
    CHURCH,
    AIRPORT,
    DEFAULT;

    /** Lower-case key as written on signs / in commands (e.g. {@code base}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String fileName() {
        return key() + ".png";
    }

    public String langKey() {
        return "command.vpa.bmsigns.icon." + key();
    }

    /** Resolves a user-supplied key (case-insensitive); blank or unknown returns {@link #DEFAULT}. */
    public static IconKey resolve(String raw) {
        if (raw != null && !raw.isBlank()) {
            String norm = raw.trim().toLowerCase(Locale.ROOT);
            for (IconKey k : values()) {
                if (k.key().equals(norm)) {
                    return k;
                }
            }
        }
        return DEFAULT;
    }
}
