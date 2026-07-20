package net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.air_blocks.AirBlocksModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Air Blocks module.
 *
 * <p>Adds a client-side reveal radius (how far the reveal tool searches for hidden Air Blocks) and
 * a toggle for the in-code crafting recipe.</p>
 */
public class AirBlocksConfig extends AbstractModuleConfig<AirBlocksModule, AirBlocksConfig> {

    private ModConfigSpec.IntValue revealRadius;
    private ModConfigSpec.BooleanValue enableCrafting;

    public AirBlocksConfig(AirBlocksModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        revealRadius = builder
                .comment("Chunk radius (Chebyshev) around the player scanned by the Air Block Revealer.",
                        "Higher = Air Blocks are found from farther away (slightly more client work).")
                .defineInRange("reveal_radius", 6, 1, 16);

        enableCrafting = builder
                .comment("Whether the in-code crafting recipe for the Air Block is registered.",
                        "false = the Air Block is only obtainable via the creative tab / commands.")
                .define("enable_crafting", true);
    }

    public int getRevealRadius() {
        return revealRadius != null ? revealRadius.get() : 6;
    }

    public boolean isCraftingEnabled() {
        return enableCrafting == null || enableCrafting.get();
    }
}
