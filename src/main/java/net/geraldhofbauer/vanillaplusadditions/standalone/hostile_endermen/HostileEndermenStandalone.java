package net.geraldhofbauer.vanillaplusadditions.standalone.hostile_endermen;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.HostileEndermenModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the hostile_endermen module (jar {@code vpa_hostile_endermen}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_hostile_endermen")
public final class HostileEndermenStandalone {

    public HostileEndermenStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new HostileEndermenModule(), modEventBus, modContainer);
    }
}
