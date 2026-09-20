package net.geraldhofbauer.vanillaplusadditions.standalone.mob_cart_loader;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Mob Cart Loader module (jar
 * {@code vpa_mob_cart_loader}), depending on {@code vpa_core} and {@code vpa_debug_overlay}
 * (the goggle panel reads {@code GogglesUtil}). All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_mob_cart_loader")
public final class MobCartLoaderStandalone {

    public MobCartLoaderStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MobCartLoaderModule(), modEventBus, modContainer);
    }
}
