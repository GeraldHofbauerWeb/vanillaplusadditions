package net.geraldhofbauer.vanillaplusadditions.standalone.core;

import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.core.Vpa;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * {@code @Mod} entrypoint for the {@code vpa_core} standalone jar — the shared framework + assets that
 * every {@code vpa_<module>} standalone jar depends on. It owns the global half of what the bundle's
 * {@link net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions} constructor does: registering the
 * shared creative tab exactly once. Individual standalone modules only append items to that tab via
 * {@link VanillaPlusCreativeTabs#addToMainTab}/{@link VanillaPlusCreativeTabs#addAllToMainTab}.
 *
 * <p>Lives under {@code standalone/**}, which the bundle jar excludes, so the bundle never declares a
 * phantom {@code vpa_core} mod. Loading {@code vpa_core} together with the {@code vanillaplusadditions}
 * bundle is unsupported (duplicate tab/content registration).
 */
@Mod("vpa_core")
public final class VpaCore {

    public VpaCore(IEventBus modEventBus, ModContainer modContainer) {
        Vpa.LOGGER.info("vpa_core framework loaded — providing shared modules framework and assets");
        VanillaPlusCreativeTabs.register(modEventBus);
    }
}
