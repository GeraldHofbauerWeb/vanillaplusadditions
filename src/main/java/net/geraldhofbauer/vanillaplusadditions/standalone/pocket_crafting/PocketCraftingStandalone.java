package net.geraldhofbauer.vanillaplusadditions.standalone.pocket_crafting;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Pocket Crafting module (jar
 * {@code vpa_pocket_crafting}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_pocket_crafting")
public final class PocketCraftingStandalone {

    public PocketCraftingStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new PocketCraftingModule(), modEventBus, modContainer);
    }
}
