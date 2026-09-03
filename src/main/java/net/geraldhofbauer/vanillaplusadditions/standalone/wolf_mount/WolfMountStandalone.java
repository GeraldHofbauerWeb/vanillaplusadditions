package net.geraldhofbauer.vanillaplusadditions.standalone.wolf_mount;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.WolfMountModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the wolf_mount module (jar {@code vpa_wolf_mount}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_wolf_mount")
public final class WolfMountStandalone {

    public WolfMountStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new WolfMountModule(), modEventBus, modContainer);
    }
}
