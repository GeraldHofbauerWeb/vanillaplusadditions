package net.geraldhofbauer.vanillaplusadditions.modules.static_fov;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;

/**
 * Keeps the field of view constant when the player gets faster.
 *
 * <p>Vanilla widens the FOV with the speed-derived modifier (sprinting, Speed effects,
 * creative flight, Soul Speed, …). This module clamps the modifier so it can never
 * exceed 1.0 — FOV-narrowing effects like drawing a bow keep working.</p>
 *
 * <p>Purely client-side; the handler lives in
 * {@link net.geraldhofbauer.vanillaplusadditions.modules.static_fov.client.StaticFovClientEvents}
 * and is only loaded on {@code Dist.CLIENT}.</p>
 */
public class StaticFovModule
        extends AbstractModule<StaticFovModule, AbstractModuleConfig.DefaultModuleConfig<StaticFovModule>> {

    private static StaticFovModule instance;

    public StaticFovModule() {
        super("static_fov",
                "Static FOV",
                "Stops the FOV from widening when the player moves faster (sprint, speed, flight).",
                AbstractModuleConfig::createDefault);
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // Client-only: StaticFovClientEvents registers itself via @EventBusSubscriber(Dist.CLIENT).
    }

    /**
     * The module instance, or {@code null} before construction. Module-local lookup, because
     * {@code ModuleManager} only knows the modules registered by the all-in-one bundle and would
     * return {@code null} inside the standalone {@code vpa_static_fov} jar.
     *
     * @return the module instance, or {@code null} if it has not been constructed yet
     */
    public static StaticFovModule getInstance() {
        return instance;
    }
}
