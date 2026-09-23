package net.geraldhofbauer.vanillaplusadditions.standalone.create_redstone_link_rebinder;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.create_redstone_link_rebinder.CreateRedstoneLinkRebinderModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the create_redstone_link_rebinder module (jar
 * {@code vpa_create_redstone_link_rebinder}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}. Create itself is a soft dependency - without it the module
 * skips initialization (runtime gate, same precedent as {@code vpa_create_water_wheel_unstucker}).
 */
@Mod("vpa_create_redstone_link_rebinder")
public final class CreateRedstoneLinkRebinderStandalone {

    /**
     * Boots the module in standalone mode.
     *
     * @param modEventBus  The mod event bus
     * @param modContainer The mod container
     */
    public CreateRedstoneLinkRebinderStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new CreateRedstoneLinkRebinderModule(), modEventBus, modContainer);
    }
}
