package net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.MoArrowsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Mo' Arrows module.
 *
 * <p>The Fire Arrow does two things, and they are worth separating: igniting what it hits is
 * ordinary combat, while laying a fire block is a building — or burning — tool that any player who
 * can craft one carries around. Only the second one has a switch, because the first is vanilla's own
 * behaviour for a burning arrow and cannot be taken away without taking the arrow's fire away too.
 */
public class MoArrowsConfig extends AbstractModuleConfig<MoArrowsModule, MoArrowsConfig> {

    private ModConfigSpec.BooleanValue lightFires;

    public MoArrowsConfig(MoArrowsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        lightFires = builder
                .comment("Let a Fire Arrow start a fire where it lands, exactly as a thrown fire charge "
                        + "would. Turn this off on a server that would rather not hand every archer a "
                        + "ranged tinderbox: the arrow then still burns in flight, still sets what it hits "
                        + "alight for five seconds and still lights TNT, campfires and candles — it simply "
                        + "leaves the ground alone.")
                .define("light_fires", true);
    }

    /**
     * Whether a Fire Arrow places a fire block where it lands.
     *
     * @return true if the arrow should start fires (default true)
     */
    public boolean isLightFiresValue() {
        return lightFires != null ? lightFires.get() : true;
    }
}
