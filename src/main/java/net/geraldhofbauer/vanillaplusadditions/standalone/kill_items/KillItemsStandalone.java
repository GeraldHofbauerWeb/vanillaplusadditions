package net.geraldhofbauer.vanillaplusadditions.standalone.kill_items;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.kill_items.KillItemsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Kill Items module (jar {@code vpa_kill_items}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_kill_items")
public final class KillItemsStandalone {

    public KillItemsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new KillItemsModule(), modEventBus, modContainer);
    }
}
