package net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.config.ArmTargetOverlayConfig;

public class ArmTargetOverlayModule extends AbstractModule<ArmTargetOverlayModule, ArmTargetOverlayConfig> {
    public ArmTargetOverlayModule() {
        super(
                "arm_target_overlay",
                "Arm Target Overlay",
                "Shows Mechanical Arm input/output positions when wearing Engineering Goggles",
                ArmTargetOverlayConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        getLogger().info("Arm Target Overlay module initialized");
    }
}
