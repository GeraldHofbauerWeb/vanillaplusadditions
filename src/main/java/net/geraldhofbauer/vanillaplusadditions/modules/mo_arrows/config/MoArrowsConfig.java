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
    private ModConfigSpec.BooleanValue impactEffects;

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

        impactEffects = builder
                .comment("Give the Fire Arrow a bang and a puff of flame where it lands. Purely "
                        + "cosmetic: no explosion, no damage, no block broken — the arrow does exactly "
                        + "what it did before, it just stops landing in silence. The sound is the "
                        + "explosion one, played quietly and pitched up so it reads as a small burst "
                        + "rather than TNT going off nearby.")
                .define("impact_effects", true);
    }

    /**
     * Whether a Fire Arrow places a fire block where it lands.
     *
     * @return true if the arrow should start fires (default true)
     */
    public boolean isLightFiresValue() {
        return lightFires != null ? lightFires.get() : true;
    }

    /**
     * Whether a Fire Arrow makes a bang and a puff of flame where it lands.
     *
     * @return true if the impact should be seen and heard (default true)
     */
    public boolean showsImpactEffects() {
        return impactEffects == null || impactEffects.get();
    }
}
