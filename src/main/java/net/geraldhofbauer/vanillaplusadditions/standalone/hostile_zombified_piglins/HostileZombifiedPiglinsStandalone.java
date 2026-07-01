package net.geraldhofbauer.vanillaplusadditions.standalone.hostile_zombified_piglins;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_zombified_piglins.HostileZombifiedPiglinsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the hostile_zombified_piglins module (jar {@code vpa_hostile_zombified_piglins}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_hostile_zombified_piglins")
public final class HostileZombifiedPiglinsStandalone {

    public HostileZombifiedPiglinsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new HostileZombifiedPiglinsModule(), modEventBus, modContainer);
    }
}
