package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.config.TextureKillConfig;

public class TextureKillModule extends AbstractModule<TextureKillModule, TextureKillConfig> {
    public TextureKillModule() {
        super(
            "texture_kill",
            "Texture Kill",
            "Replaces configured textures with a fully transparent texture. "
            + "Useful to hide cosmetic textures from other mods (e.g., Create contraption hats).",
            TextureKillConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        // Client events are registered via @EventBusSubscriber on TextureKillClientEvents
    }
}
