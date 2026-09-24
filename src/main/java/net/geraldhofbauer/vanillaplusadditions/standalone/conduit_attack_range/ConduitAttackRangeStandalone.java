package net.geraldhofbauer.vanillaplusadditions.standalone.conduit_attack_range;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.conduit_attack_range.ConduitAttackRangeModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Conduit Attack Range module (jar
 * {@code vpa_conduit_attack_range}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_conduit_attack_range")
public final class ConduitAttackRangeStandalone {

    public ConduitAttackRangeStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new ConduitAttackRangeModule(), modEventBus, modContainer);
    }
}
