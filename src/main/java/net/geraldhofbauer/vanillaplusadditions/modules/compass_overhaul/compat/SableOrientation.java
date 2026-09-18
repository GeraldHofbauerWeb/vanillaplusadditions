package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalDouble;

/**
 * Computes a compass bearing inside a Sable sub-level.
 *
 * <p>A sub-level is a plot of chunks parked far away in the parent level, drawn at the ship's real
 * position through a pose. An entity standing on a ship therefore has plot coordinates and a
 * plot-local yaw, while the compass target is a position in the parent level — so the target has to
 * be brought into the plot's space before the two can be compared.
 *
 * <p><strong>Every reference to a Sable class in this module lives in this file</strong>, and
 * nothing calls into it without {@link SableGate#isLoaded()} being true first.
 */
public final class SableOrientation {

    private static final double FULL_TURN = Math.PI * 2.0;

    private SableOrientation() {
    }

    /**
     * The bearing from the viewer to a target, measured inside the viewer's sub-level.
     *
     * <p>This is the same job Sable does for the ordinary compass, but from the interpolated render
     * pose instead of the pose from the previous tick, so the needle no longer jitters while the ship
     * turns.
     *
     * <p><strong>Only a viewer standing inside the plot counts.</strong> Transforming the target into
     * the ship's space is only correct while the yaw the caller folds in afterwards lives in that same
     * space, and that holds exactly when the viewer is in the plot. Someone standing on a hull drawn
     * out in the world keeps world coordinates and a world yaw; mixing a ship-space bearing into that
     * puts the needle off by the ship's entire rotation — half a turn for a ship facing backwards.
     *
     * @param viewer      the entity the needle is drawn for
     * @param target      the compass target, in parent-level coordinates
     * @param partialTick render partial tick
     * @return the bearing in turns, or empty when the viewer does not stand inside a sub-level
     */
    public static OptionalDouble bearingInSubLevel(Entity viewer, BlockPos target, float partialTick) {
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(viewer);
        if (subLevel == null) {
            return OptionalDouble.empty();
        }

        // Position and target both in the plot's space; the viewer's yaw, which the caller folds in
        // afterwards, is plot-local as well because the viewer stands inside the plot.
        Pose3dc pose = subLevel.renderPose(partialTick);
        Vec3 viewerPos = viewer.position();
        Vec3 localTarget = pose.transformPositionInverse(Vec3.atCenterOf(target));
        return OptionalDouble.of(Math.atan2(localTarget.z() - viewerPos.z, localTarget.x() - viewerPos.x) / FULL_TURN);
    }
}
