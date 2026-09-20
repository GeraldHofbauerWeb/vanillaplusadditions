package net.geraldhofbauer.vanillaplusadditions.standalone.pathfinder_quills;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.PathfinderQuillsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the pathfinder_quills module (jar
 * {@code vpa_pathfinder_quills}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}. Quark is a soft dependency - without it the module skips
 * initialization (runtime gate, same precedent as {@code vpa_create_water_wheel_unstucker}), so
 * the jar loads and stays inert instead of crashing: the only Quark type in the module sits in a
 * method body of {@code PathfinderQuillRecipe}, never in a field, signature or superclass, and
 * that class is reached only behind the gate. Since the module does nothing else, this jar is
 * only worth installing alongside Quark.
 */
@Mod("vpa_pathfinder_quills")
public final class PathfinderQuillsStandalone {

    /**
     * Boots the module in standalone mode.
     *
     * @param modEventBus  The mod event bus
     * @param modContainer The mod container
     */
    public PathfinderQuillsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new PathfinderQuillsModule(), modEventBus, modContainer);
    }
}
