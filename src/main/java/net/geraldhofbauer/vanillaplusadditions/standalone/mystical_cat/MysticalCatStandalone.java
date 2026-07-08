package net.geraldhofbauer.vanillaplusadditions.standalone.mystical_cat;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the mystical_cat module (jar {@code vpa_mystical_cat}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_mystical_cat")
public final class MysticalCatStandalone {

    public MysticalCatStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new MysticalCatModule(), modEventBus, modContainer);
    }
}
