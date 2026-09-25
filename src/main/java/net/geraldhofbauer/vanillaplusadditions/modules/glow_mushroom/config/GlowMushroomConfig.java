package net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom.GlowMushroomModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Glow Mushroom module. Only the universal keys plus the two effect
 * durations - the item and block themselves are not configurable, because changing them after the
 * fact would desync what is already placed in the world.
 */
public class GlowMushroomConfig
        extends AbstractModuleConfig<GlowMushroomModule, GlowMushroomConfig> {

    private ModConfigSpec.IntValue mushroomGlowTicks;
    private ModConfigSpec.IntValue stewGlowTicks;

    /**
     * Creates the config for the given module instance.
     *
     * @param module The owning module
     */
    public GlowMushroomConfig(GlowMushroomModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        mushroomGlowTicks = builder
                .comment("How long (in ticks) the Glowing effect lasts after eating a raw glow mushroom.",
                        "20 ticks = one second. NOTE: this is read when the item is registered, so a",
                        "change only takes effect after a restart.")
                .defineInRange("mushroom_glow_ticks", 200, 20, 6000);

        stewGlowTicks = builder
                .comment("How long (in ticks) the Glowing effect lasts after eating glow mushroom stew.",
                        "Same restart caveat as mushroom_glow_ticks.")
                .defineInRange("stew_glow_ticks", 600, 20, 24000);
    }

    /**
     * Gets the Glowing duration granted by the raw mushroom.
     *
     * @return duration in ticks
     */
    public int getMushroomGlowTicks() {
        return mushroomGlowTicks != null ? mushroomGlowTicks.get() : 200;
    }

    /**
     * Gets the Glowing duration granted by the stew.
     *
     * @return duration in ticks
     */
    public int getStewGlowTicks() {
        return stewGlowTicks != null ? stewGlowTicks.get() : 600;
    }
}
