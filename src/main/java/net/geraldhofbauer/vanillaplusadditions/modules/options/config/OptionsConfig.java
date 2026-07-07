package net.geraldhofbauer.vanillaplusadditions.modules.options.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.options.OptionsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the VPA Options module (options backup &amp; restore).
 */
public class OptionsConfig extends AbstractModuleConfig<OptionsModule, OptionsConfig> {

    private ModConfigSpec.BooleanValue fullOptionsBackup;
    private ModConfigSpec.BooleanValue autoBackup;
    private ModConfigSpec.IntValue autoBackupKeep;

    /**
     * Creates a new OptionsConfig.
     *
     * @param module The module this configuration belongs to
     */
    public OptionsConfig(OptionsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        fullOptionsBackup = builder
                .comment("Back up / restore the full options.txt (video, sound, chat, keybinds, ...). "
                        + "Set to false to limit both backups and restores to keybinds (key_* lines) only.")
                .define("full_options_backup", true);

        autoBackup = builder
                .comment("Automatically create a rotating backup at game start whenever the current "
                        + "options differ from the newest automatic snapshot.")
                .define("auto_backup", true);

        autoBackupKeep = builder
                .comment("How many automatic backups (auto_* snapshots) to keep before the oldest "
                        + "ones are deleted.")
                .defineInRange("auto_backup_keep", 10, 1, 100);
    }

    public boolean isFullOptionsBackup() {
        return fullOptionsBackup == null || fullOptionsBackup.get();
    }

    public boolean isAutoBackupEnabled() {
        return autoBackup == null || autoBackup.get();
    }

    public int getAutoBackupKeep() {
        return autoBackupKeep != null ? autoBackupKeep.get() : 10;
    }
}
