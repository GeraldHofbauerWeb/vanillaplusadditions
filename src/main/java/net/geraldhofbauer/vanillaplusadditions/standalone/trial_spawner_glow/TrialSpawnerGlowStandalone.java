package net.geraldhofbauer.vanillaplusadditions.standalone.trial_spawner_glow;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.trial_spawner_glow.TrialSpawnerGlowModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Trial Spawner Glow module (jar
 * {@code vpa_trial_spawner_glow}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_trial_spawner_glow")
public final class TrialSpawnerGlowStandalone {

    public TrialSpawnerGlowStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new TrialSpawnerGlowModule(), modEventBus, modContainer);
    }
}
