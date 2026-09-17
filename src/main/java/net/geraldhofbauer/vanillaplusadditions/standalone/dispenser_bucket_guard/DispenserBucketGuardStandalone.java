package net.geraldhofbauer.vanillaplusadditions.standalone.dispenser_bucket_guard;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.dispenser_bucket_guard.DispenserBucketGuardModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Dispenser Bucket Guard module (jar
 * {@code vpa_dispenser_bucket_guard}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_dispenser_bucket_guard")
public final class DispenserBucketGuardStandalone {

    public DispenserBucketGuardStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new DispenserBucketGuardModule(), modEventBus, modContainer);
    }
}
