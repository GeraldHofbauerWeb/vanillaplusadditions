package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.config.TextureKillConfig;

public class TextureKillModule extends AbstractModule<TextureKillModule, TextureKillConfig> {

    private static TextureKillModule instance;

    public TextureKillModule() {
        super(
            "texture_kill",
            "Texture Kill",
            "Replaces configured textures with a fully transparent texture. "
            + "Useful to hide cosmetic textures from other mods (e.g., Create contraption hats).",
            TextureKillConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // Client events are registered via @EventBusSubscriber on TextureKillClientEvents
    }

    /**
     * The module instance, or {@code null} before construction. Module-local on purpose: standalone
     * module jars boot past {@code ModuleManager}, so its registry is empty there.
     *
     * @return the module instance, or null if it has not been constructed yet
     */
    public static TextureKillModule getInstance() {
        return instance;
    }
}
