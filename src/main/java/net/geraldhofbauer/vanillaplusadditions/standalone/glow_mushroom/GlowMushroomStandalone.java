package net.geraldhofbauer.vanillaplusadditions.standalone.glow_mushroom;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom.GlowMushroomModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the glow_mushroom module (jar {@code vpa_glow_mushroom}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 *
 * <p>Block and item are registered unconditionally; the huge variant grown by bone meal comes from
 * the {@code vpa_mushroom_fields_plus} world datapack and is simply absent without it.</p>
 */
@Mod("vpa_glow_mushroom")
public final class GlowMushroomStandalone {

    /**
     * Boots the module in standalone mode.
     *
     * @param modEventBus  The mod event bus
     * @param modContainer The mod container
     */
    public GlowMushroomStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new GlowMushroomModule(), modEventBus, modContainer);
    }
}
