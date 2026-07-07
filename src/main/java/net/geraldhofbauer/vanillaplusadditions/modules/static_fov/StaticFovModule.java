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

    public StaticFovModule() {
        super("static_fov",
                "Static FOV",
                "Stops the FOV from widening when the player moves faster (sprint, speed, flight).",
                AbstractModuleConfig::createDefault);
    }

    @Override
    protected void onInitialize() {
        // Client-only: StaticFovClientEvents registers itself via @EventBusSubscriber(Dist.CLIENT).
    }
}
