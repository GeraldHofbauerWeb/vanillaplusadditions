package net.geraldhofbauer.vanillaplusadditions.core;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Namespace-neutral constants shared by the framework <b>without</b> depending on the root
 * {@code @Mod} class {@link net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions}.
 *
 * <p>This lets {@code vpa_core} (and the per-module standalone jars) load without pulling in the
 * bundle's {@code @Mod} entrypoint, which would otherwise register a second {@code vanillaplusadditions}
 * mod. The registry/asset namespace stays {@code "vanillaplusadditions"} regardless of the loading
 * jar's modId, so no registry or resource renaming is needed.
 */
public final class Vpa {
    /** The shared Minecraft resource / registry namespace used by every module. */
    public static final String NAMESPACE = "vanillaplusadditions";

    /** Shared logger for framework-level classes. */
    public static final Logger LOGGER = LogUtils.getLogger();

    private Vpa() {
    }
}
