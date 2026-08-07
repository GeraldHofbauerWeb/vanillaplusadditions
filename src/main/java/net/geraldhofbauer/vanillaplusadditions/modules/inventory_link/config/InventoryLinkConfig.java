package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.InventoryLinkModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class InventoryLinkConfig extends AbstractModuleConfig<InventoryLinkModule, InventoryLinkConfig> {

    private ModConfigSpec.IntValue maxLinkRange;
    private ModConfigSpec.IntValue transferIntervalTicks;
    private ModConfigSpec.IntValue itemsPerTransfer;
    private ModConfigSpec.IntValue maxLinksPerBlock;
    private ModConfigSpec.DoubleValue outlineRed;
    private ModConfigSpec.DoubleValue outlineGreen;
    private ModConfigSpec.DoubleValue outlineBlue;
    private ModConfigSpec.DoubleValue outlineAlpha;

    public InventoryLinkConfig(InventoryLinkModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        maxLinkRange = builder
                .comment("Maximum distance (in blocks) between the two endpoints of a link.",
                        "Both endpoints must be in the same dimension. Physics platform blocks count",
                        "with their plot coordinates, so a link onto a moving contraption is measured",
                        "inside the platform's own coordinate space.")
                .defineInRange("max_link_range", 32, 4, 128);

        transferIntervalTicks = builder
                .comment("Ticks between transfer passes. 8 = 2.5 passes per second.")
                .defineInRange("transfer_interval_ticks", 8, 1, 200);

        itemsPerTransfer = builder
                .comment("Maximum number of items moved per link per pass.",
                        "Defaults (16 items / 8 ticks) are roughly twice hopper speed.")
                .defineInRange("items_per_transfer", 16, 1, 256);

        maxLinksPerBlock = builder
                .comment("How many links a single block may take part in.")
                .defineInRange("max_links_per_block", 8, 1, 64);

        builder.push("overlay");
        outlineRed = builder
                .comment("Outline colour (red channel) for bidirectional link endpoints.")
                .defineInRange("outline_red", 0.3, 0.0, 1.0);
        outlineGreen = builder
                .comment("Outline colour (green channel) for bidirectional link endpoints.")
                .defineInRange("outline_green", 0.9, 0.0, 1.0);
        outlineBlue = builder
                .comment("Outline colour (blue channel) for bidirectional link endpoints.")
                .defineInRange("outline_blue", 1.0, 0.0, 1.0);
        outlineAlpha = builder
                .comment("Outline opacity.")
                .defineInRange("outline_alpha", 0.9, 0.0, 1.0);
        builder.pop();
    }

    public int getMaxLinkRange() {
        return maxLinkRange != null ? maxLinkRange.get() : 32;
    }

    public int getTransferIntervalTicks() {
        return transferIntervalTicks != null ? transferIntervalTicks.get() : 8;
    }

    public int getItemsPerTransfer() {
        return itemsPerTransfer != null ? itemsPerTransfer.get() : 16;
    }

    public int getMaxLinksPerBlock() {
        return maxLinksPerBlock != null ? maxLinksPerBlock.get() : 8;
    }

    public float getOutlineRed() {
        return outlineRed != null ? outlineRed.get().floatValue() : 0.3f;
    }

    public float getOutlineGreen() {
        return outlineGreen != null ? outlineGreen.get().floatValue() : 0.9f;
    }

    public float getOutlineBlue() {
        return outlineBlue != null ? outlineBlue.get().floatValue() : 1.0f;
    }

    public float getOutlineAlpha() {
        return outlineAlpha != null ? outlineAlpha.get().floatValue() : 0.9f;
    }
}
