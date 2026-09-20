package net.geraldhofbauer.vanillaplusadditions.standalone.waystone_amethyst_repair;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.waystone_amethyst_repair.WaystoneAmethystRepairModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Waystone Amethyst Repair module (jar
 * {@code vpa_waystone_amethyst_repair}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 *
 * <p>No Waystones dependency: the target item and the repair materials are plain registry ids resolved at
 * runtime, so the jar loads without Waystones and the module is simply inert until {@code target_item}
 * names an installed damageable item.
 *
 * <p>The zero-cost path is bundle-only. It asks {@link net.geraldhofbauer.vanillaplusadditions.core.ModuleManager}
 * whether {@code free_anvil_repair} is enabled, and that map is filled by the bundle's module registration,
 * which {@link StandaloneModuleBootstrap} deliberately skips — so a standalone repair always costs its XP
 * levels, even alongside {@code vpa_free_anvil_repair}. That is the harmless direction: a cost-0 anvil result
 * is un-takeable without Free Anvil Repair's own mixin.
 */
@Mod("vpa_waystone_amethyst_repair")
public final class WaystoneAmethystRepairStandalone {

    public WaystoneAmethystRepairStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new WaystoneAmethystRepairModule(), modEventBus, modContainer);
    }
}
