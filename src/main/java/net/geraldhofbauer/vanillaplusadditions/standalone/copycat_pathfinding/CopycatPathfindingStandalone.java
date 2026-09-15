package net.geraldhofbauer.vanillaplusadditions.standalone.copycat_pathfinding;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.CopycatPathfindingModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Copycat Pathfinding module (jar
 * {@code vpa_copycat_pathfinding}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_copycat_pathfinding")
public final class CopycatPathfindingStandalone {

    public CopycatPathfindingStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new CopycatPathfindingModule(), modEventBus, modContainer);
    }
}
