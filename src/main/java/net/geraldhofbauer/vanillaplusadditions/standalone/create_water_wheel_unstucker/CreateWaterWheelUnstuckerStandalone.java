package net.geraldhofbauer.vanillaplusadditions.standalone.create_water_wheel_unstucker;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker.CreateWaterWheelUnstuckerModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the create_water_wheel_unstucker module (jar
 * {@code vpa_create_water_wheel_unstucker}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}. Create itself is a soft dependency - without it the module
 * skips initialization (runtime gate, same precedent as {@code vpa_item_vault_viewer}).
 */
@Mod("vpa_create_water_wheel_unstucker")
public final class CreateWaterWheelUnstuckerStandalone {

    /**
     * Boots the module in standalone mode.
     *
     * @param modEventBus  The mod event bus
     * @param modContainer The mod container
     */
    public CreateWaterWheelUnstuckerStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new CreateWaterWheelUnstuckerModule(), modEventBus, modContainer);
    }
}
