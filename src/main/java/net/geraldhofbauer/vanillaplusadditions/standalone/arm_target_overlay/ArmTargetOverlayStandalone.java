package net.geraldhofbauer.vanillaplusadditions.standalone.arm_target_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.ArmTargetOverlayModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the arm_target_overlay module (jar {@code vpa_arm_target_overlay}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_arm_target_overlay")
public final class ArmTargetOverlayStandalone {

    public ArmTargetOverlayStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new ArmTargetOverlayModule(), modEventBus, modContainer);
    }
}
