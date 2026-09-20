package net.geraldhofbauer.vanillaplusadditions.modules.options;

import net.geraldhofbauer.vanillaplusadditions.modules.options.config.OptionsConfig;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;

/**
 * VPA Options — backup &amp; restore of the client options ({@code options.txt}) including all
 * keybinds (vanilla + modded).
 *
 * <p>Entirely client-side. Features:
 * <ul>
 *   <li>Manual snapshots via {@code /vpaoptions export|restore|list|delete} or the
 *       "Backups…" button injected into the vanilla Options and Controls screens.</li>
 *   <li>Automatic rotating backups: on every game start the current options are compared to the
 *       newest automatic snapshot; if they differ, a new {@code auto_&lt;timestamp&gt;} backup is
 *       created (protects against a mod or misclick trashing the keybinds).</li>
 *   <li>Scope is configurable: the full {@code options.txt} (default) or keybinds only.</li>
 * </ul>
 *
 * <p>Snapshots live in {@code config/vanillaplusadditions/options_backups/}. Restoring a full
 * snapshot rewrites {@code options.txt} and reloads it in place ({@code Options.load()}); a few
 * settings (language, resource packs) may only fully apply after a restart.
 */
public class OptionsModule extends AbstractModule<OptionsModule, OptionsConfig> {

    private static OptionsModule instance;

    public OptionsModule() {
        super(
            "options",
            "VPA Options",
            "Backup & restore of client options (options.txt) incl. all keybinds — manual snapshots "
            + "via command/GUI plus automatic rotating backups on change.",
            OptionsConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // Client events are registered via @EventBusSubscriber on OptionsClientEvents.
    }

    /**
     * The module instance, or {@code null} before construction.
     *
     * <p>Module-local on purpose: {@code ModuleManager} only knows the modules registered by the
     * all-in-one bundle, so looking this module up there returns {@code null} inside a standalone
     * {@code vpa_options} jar. Callers must keep handling {@code null}.
     *
     * @return the module instance, or {@code null} if it has not been constructed yet
     */
    public static OptionsModule getInstance() {
        return instance;
    }
}
