package net.geraldhofbauer.vanillaplusadditions.modules.freecam_sublevel_noclip;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;

/**
 * Lets the Freecam camera fly through a Sable airship the way it flies through the world.
 *
 * <p>Freecam turns blocks into thin air by cancelling
 * {@code BlockStateBase.getCollisionShape(BlockGetter, BlockPos, CollisionContext)} whenever the
 * {@code CollisionContext} carries its own camera entity. Sable never gives it that chance on a
 * sub-level: {@code SubLevelEntityCollision.getSubLevelEntityCollisionShape} passes a context only
 * for scaffolding and falls back to the two-argument {@code getCollisionShape(level, pos)} for every
 * other block — which serves the cached shape and never asks who is colliding. The camera therefore
 * bumps into the ship's hull while it passes through the terrain underneath it.
 *
 * <p>The repair does not touch Sable. Vanilla's {@code Entity.move} skips collision entirely when
 * {@code noPhysics} is set — and it skips it <em>before</em> the call Sable redirects — so setting
 * that flag on the camera entity takes both collision paths out at once.
 *
 * <p>Purely client-side, and only while Freecam is installed, enabled and configured to ignore all
 * blocks. The work happens in the client mixin {@code
 * mixin.freecam_sublevel_noclip.EntityMoveFreecamMixin}; this class only carries the module's
 * identity and its config section.
 */
public class FreecamSublevelNoclipModule
        extends AbstractModule<FreecamSublevelNoclipModule,
        AbstractModuleConfig.DefaultModuleConfig<FreecamSublevelNoclipModule>> {

    private static FreecamSublevelNoclipModule instance;

    public FreecamSublevelNoclipModule() {
        super("freecam_sublevel_noclip",
                "Freecam Sub-Level Noclip",
                "Lets the Freecam camera pass through Sable airships instead of getting stuck in the hull.",
                AbstractModuleConfig::createDefault);
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // Client-only: FreecamSublevelNoclipClientEvents registers itself via @EventBusSubscriber.
    }

    /**
     * The module instance, or {@code null} before construction. Module-local lookup, because
     * {@code ModuleManager} only knows the modules registered by the all-in-one bundle and would
     * return {@code null} inside the standalone {@code vpa_freecam_sublevel_noclip} jar.
     *
     * @return the module instance, or {@code null} if it has not been constructed yet
     */
    public static FreecamSublevelNoclipModule getInstance() {
        return instance;
    }
}
