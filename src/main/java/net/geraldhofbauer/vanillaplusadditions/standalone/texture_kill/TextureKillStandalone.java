package net.geraldhofbauer.vanillaplusadditions.standalone.texture_kill;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.TextureKillModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the texture_kill module (jar {@code vpa_texture_kill}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_texture_kill")
public final class TextureKillStandalone {

    public TextureKillStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new TextureKillModule(), modEventBus, modContainer);
    }
}
