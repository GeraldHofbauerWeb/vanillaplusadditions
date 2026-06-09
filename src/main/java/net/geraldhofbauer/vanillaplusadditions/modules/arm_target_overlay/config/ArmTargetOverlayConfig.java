package net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.ArmTargetOverlayModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ArmTargetOverlayConfig extends AbstractModuleConfig<ArmTargetOverlayModule, ArmTargetOverlayConfig> {
    private ModConfigSpec.DoubleValue inputRed;
    private ModConfigSpec.DoubleValue inputGreen;
    private ModConfigSpec.DoubleValue inputBlue;
    private ModConfigSpec.DoubleValue inputAlpha;
    private ModConfigSpec.DoubleValue outputRed;
    private ModConfigSpec.DoubleValue outputGreen;
    private ModConfigSpec.DoubleValue outputBlue;
    private ModConfigSpec.DoubleValue outputAlpha;

    public ArmTargetOverlayConfig(ArmTargetOverlayModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        builder.push("input_color");
        inputRed = builder.comment("Red component for input (TAKE) position outlines")
                .defineInRange("red", 1.0D, 0.0D, 1.0D);
        inputGreen = builder.comment("Green component for input (TAKE) position outlines")
                .defineInRange("green", 0.6D, 0.0D, 1.0D);
        inputBlue = builder.comment("Blue component for input (TAKE) position outlines")
                .defineInRange("blue", 0.1D, 0.0D, 1.0D);
        inputAlpha = builder.comment("Alpha component for input (TAKE) position outlines")
                .defineInRange("alpha", 0.8D, 0.0D, 1.0D);
        builder.pop();

        builder.push("output_color");
        outputRed = builder.comment("Red component for output (DEPOSIT) position outlines")
                .defineInRange("red", 0.1D, 0.0D, 1.0D);
        outputGreen = builder.comment("Green component for output (DEPOSIT) position outlines")
                .defineInRange("green", 0.9D, 0.0D, 1.0D);
        outputBlue = builder.comment("Blue component for output (DEPOSIT) position outlines")
                .defineInRange("blue", 0.7D, 0.0D, 1.0D);
        outputAlpha = builder.comment("Alpha component for output (DEPOSIT) position outlines")
                .defineInRange("alpha", 0.8D, 0.0D, 1.0D);
        builder.pop();
    }

    public float getInputRed() {
        return inputRed.get().floatValue();
    }

    public float getInputGreen() {
        return inputGreen.get().floatValue();
    }

    public float getInputBlue() {
        return inputBlue.get().floatValue();
    }

    public float getInputAlpha() {
        return inputAlpha.get().floatValue();
    }

    public float getOutputRed() {
        return outputRed.get().floatValue();
    }

    public float getOutputGreen() {
        return outputGreen.get().floatValue();
    }

    public float getOutputBlue() {
        return outputBlue.get().floatValue();
    }

    public float getOutputAlpha() {
        return outputAlpha.get().floatValue();
    }
}
