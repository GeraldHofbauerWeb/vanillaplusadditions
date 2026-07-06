package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.OverpackedBackpackKeysModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Overpacked Backpack Keybinds module.
 *
 * <p>No module-specific options beyond the standard {@code enabled} / {@code debug_logging}
 * provided by {@link AbstractModuleConfig}. The three keybinds are configured in the vanilla
 * Controls screen, not here.
 */
public class OverpackedBackpackKeysConfig
        extends AbstractModuleConfig<OverpackedBackpackKeysModule, OverpackedBackpackKeysConfig> {

    public OverpackedBackpackKeysConfig(OverpackedBackpackKeysModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        // No extra options.
    }
}
