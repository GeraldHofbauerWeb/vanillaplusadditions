package net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations.config.QuarkFreshAnimationsConfig;

/**
 * Ships a resource pack that teaches Fresh Animations about Quark's animals.
 *
 * <p>Fresh Animations covers vanilla mobs only, so Quark's foxhound moves like stock Minecraft while
 * every wolf beside it does not. That gap became hard to ignore once the foxhound turned into a
 * mount and started standing in front of the player all day. The pack fills it with CEM files that
 * <b>Entity Model Features</b> reads — geometry in {@code .jem}, expressions in {@code .jpm}.
 *
 * <p>The module itself owns no blocks, items or events of its own; all it does is put the pack in
 * front of the pack repository, and only when the two mods it depends on are actually there. See
 * {@code modules.quark_fresh_animations.client.QuarkFreshAnimationsPack} for the conditions.
 */
public class QuarkFreshAnimationsModule
        extends AbstractModule<QuarkFreshAnimationsModule, QuarkFreshAnimationsConfig> {

    private static QuarkFreshAnimationsModule instance;

    public QuarkFreshAnimationsModule() {
        super(
            "quark_fresh_animations",
            "Quark Fresh Animations",
            "Ships a built-in resource pack that extends Fresh Animations to Quark's animals "
            + "(currently the foxhound) and enables it automatically when Quark and a Fresh "
            + "Animations pack are both present.",
            QuarkFreshAnimationsConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // Nothing to wire up: the pack is added from QuarkFreshAnimationsPack, a client-only
        // @EventBusSubscriber that NeoForge discovers on its own.
    }

    /**
     * The module instance, or {@code null} before construction. Module-local on purpose: standalone
     * module jars boot past {@code ModuleManager}, so its registry is empty there.
     *
     * @return the module instance, or null if it has not been constructed yet
     */
    public static QuarkFreshAnimationsModule getInstance() {
        return instance;
    }
}
