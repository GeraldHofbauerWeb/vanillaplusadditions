package net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.config.ArmTargetOverlayConfig;

public class ArmTargetOverlayModule extends AbstractModule<ArmTargetOverlayModule, ArmTargetOverlayConfig> {

    private static ArmTargetOverlayModule instance;

    public ArmTargetOverlayModule() {
        super(
                "arm_target_overlay",
                "Arm Target Overlay",
                "Shows Mechanical Arm input/output positions when wearing Engineering Goggles",
                ArmTargetOverlayConfig::new
        );
        instance = this;
    }

    /**
     * The module instance, or {@code null} before construction.
     *
     * <p>Module-local lookup: the ModuleManager singleton is only filled by the all-in-one
     * bundle, so {@code ModuleManager.getModule("arm_target_overlay")} is null inside a
     * standalone module jar.</p>
     *
     * @return the module instance, or null if it has not been constructed yet
     */
    public static ArmTargetOverlayModule getInstance() {
        return instance;
    }

    @Override
    protected void onInitialize() {
        getLogger().info("Arm Target Overlay module initialized");
    }
}
