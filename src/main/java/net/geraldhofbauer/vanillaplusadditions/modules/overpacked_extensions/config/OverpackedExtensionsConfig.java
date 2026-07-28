package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.OverpackedExtensionsModule;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the combined Overpacked Extensions module.
 *
 * <p>Because the sub-features (slowdown override, backpack keybinds) share this module's single
 * {@code enabled} flag, each has its own toggle so they can be turned off individually:
 * <ul>
 *   <li>{@code slowdown_multiplier} — multiplier on Overpacked's movement penalty (0.0 = removed).</li>
 *   <li>{@code backpack_keys_enabled} — the compartment-open keybinds.</li>
 * </ul>
 *
 * <p>(Sorting/searching inside the backpack come from Quark, not this module — see
 * {@code docs/overpacked_extensions.md}.)
 */
public class OverpackedExtensionsConfig
        extends AbstractModuleConfig<OverpackedExtensionsModule, OverpackedExtensionsConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverpackedExtensionsConfig.class);

    private ModConfigSpec.DoubleValue slowdownMultiplier;
    private ModConfigSpec.BooleanValue backpackKeysEnabled;

    public OverpackedExtensionsConfig(OverpackedExtensionsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        slowdownMultiplier = builder
                .comment("Multiplier applied to the Overpacked slowdown effect.",
                        "0.0 = no slowdown (completely removes the effect),",
                        "0.5 = half the original slowdown,",
                        "1.0 = original slowdown (no change),",
                        "2.0 = double the slowdown.")
                .defineInRange("slowdown_multiplier", 0.0, 0.0, 10.0);

        backpackKeysEnabled = builder
                .comment("Enable the keybinds that open the compartments of a worn giant backpack",
                        "(main compartment on B by default; right/left unbound).")
                .define("backpack_keys_enabled", true);

        LOGGER.debug("Built module-specific configuration for Overpacked Extensions module");
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        if (shouldDebugLog()) {
            LOGGER.debug("Overpacked Extensions config loaded — slowdown x{}, keys={}",
                    getSlowdownMultiplierValue(), isBackpackKeysEnabled());
        }
    }

    /** @return the slowdown multiplier, or the default (0.0) before the spec is loaded. */
    public double getSlowdownMultiplierValue() {
        return slowdownMultiplier != null ? slowdownMultiplier.get() : 0.0;
    }

    /** @return whether the compartment-open keybinds are active. */
    public boolean isBackpackKeysEnabled() {
        return backpackKeysEnabled == null || backpackKeysEnabled.get();
    }
}
