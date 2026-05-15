package net.geraldhofbauer.vanillaplusadditions.modules.block_glow.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Block Glow module.
 */
public class BlockGlowConfig extends AbstractModuleConfig<BlockGlowModule, BlockGlowConfig> {
    private ModConfigSpec.IntValue defaultRadius;
    private ModConfigSpec.IntValue maxRadius;
    private ModConfigSpec.IntValue defaultDurationSeconds;
    private ModConfigSpec.IntValue maxHighlightsPerFrame;
    private ModConfigSpec.ConfigValue<String> selectionMode;
    private ModConfigSpec.DoubleValue outlineRed;
    private ModConfigSpec.DoubleValue outlineGreen;
    private ModConfigSpec.DoubleValue outlineBlue;
    private ModConfigSpec.DoubleValue outlineAlpha;

    public BlockGlowConfig(BlockGlowModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        defaultRadius = builder
                .comment("Default search radius in blocks for /blockglow")
                .defineInRange("default_radius", 24, 1, 128);

        maxRadius = builder
                .comment("Maximum allowed radius in blocks for /blockglow")
                .defineInRange("max_radius", 64, 1, 256);

        defaultDurationSeconds = builder
                .comment("Default glow duration in seconds (0 = infinite)")
                .defineInRange("default_duration_seconds", 60, 0, Integer.MAX_VALUE);

        maxHighlightsPerFrame = builder
                .comment("Maximum number of block outlines rendered per frame")
                .defineInRange("max_highlights_per_frame", 512, 16, 8192);

        selectionMode = builder
                .comment("Selection mode for which blocks are highlighted: nearest or scan_order")
                .define("selection_mode", "nearest");

        builder.push("outline_color");
        outlineRed = builder
                .comment("Red component of the outline color")
                .defineInRange("red", 0.0D, 0.0D, 1.0D);

        outlineGreen = builder
                .comment("Green component of the outline color")
                .defineInRange("green", 1.0D, 0.0D, 1.0D);

        outlineBlue = builder
                .comment("Blue component of the outline color")
                .defineInRange("blue", 1.0D, 0.0D, 1.0D);

        outlineAlpha = builder
                .comment("Alpha component of the outline color")
                .defineInRange("alpha", 1.0D, 0.0D, 1.0D);
        builder.pop();
    }

    public int getDefaultRadius() {
        return defaultRadius.get();
    }

    public int getMaxRadius() {
        return maxRadius.get();
    }

    public int getDefaultDurationSeconds() {
        return defaultDurationSeconds.get();
    }

    public int getMaxHighlightsPerFrame() {
        return maxHighlightsPerFrame.get();
    }

    public String getSelectionMode() {
        String value = selectionMode.get();
        if (value == null) {
            return "nearest";
        }

        String normalized = value.trim().toLowerCase();
        if (!"scan_order".equals(normalized)) {
            return "nearest";
        }

        return normalized;
    }

    public float getOutlineRed() {
        return outlineRed.get().floatValue();
    }

    public float getOutlineGreen() {
        return outlineGreen.get().floatValue();
    }

    public float getOutlineBlue() {
        return outlineBlue.get().floatValue();
    }

    public float getOutlineAlpha() {
        return outlineAlpha.get().floatValue();
    }
}

