package net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations.QuarkFreshAnimationsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Quark Fresh Animations module.
 */
public class QuarkFreshAnimationsConfig
        extends AbstractModuleConfig<QuarkFreshAnimationsModule, QuarkFreshAnimationsConfig> {

    private ModConfigSpec.BooleanValue requireFreshAnimations;

    public QuarkFreshAnimationsConfig(QuarkFreshAnimationsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        requireFreshAnimations = builder
                .comment("Only enable the built-in pack when a Fresh Animations pack is found in the",
                        "game's resourcepacks folder. Unlike the Quark check this is a HEURISTIC: Fresh",
                        "Animations is a resource pack, not a mod, so there is nothing to ask by id - the",
                        "file name is all there is to go on. Turn this off if your Fresh Animations lives",
                        "somewhere this cannot see (a modpack overlay, a renamed file, another mod that",
                        "provides it), and the pack will then enable itself on the Quark check alone.")
                .define("require_fresh_animations", true);
    }

    /** Whether a Fresh Animations pack must be present for the built-in pack to be enabled. */
    public boolean requiresFreshAnimations() {
        return requireFreshAnimations == null || requireFreshAnimations.get();
    }
}
