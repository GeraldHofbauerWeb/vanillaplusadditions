package net.geraldhofbauer.vanillaplusadditions.standalone.create_stock_link_keepalive;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive.CreateStockLinkKeepaliveModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the create_stock_link_keepalive module (jar
 * {@code vpa_create_stock_link_keepalive}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}. Create itself is a soft dependency - without it the module
 * skips initialization (runtime gate, same precedent as {@code vpa_create_water_wheel_unstucker}).
 */
@Mod("vpa_create_stock_link_keepalive")
public final class CreateStockLinkKeepaliveStandalone {

    /**
     * Boots the module in standalone mode.
     *
     * @param modEventBus  The mod event bus
     * @param modContainer The mod container
     */
    public CreateStockLinkKeepaliveStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new CreateStockLinkKeepaliveModule(), modEventBus, modContainer);
    }
}
