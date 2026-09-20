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
 * position through a pose. Sable keeps most entities out of that plot — they live at the projected
 * world position and merely "stick" to the sub-level — but everything in the entity type tag
 * {@code sable:retain_in_sub_level} really does sit inside it: item frames, armour stands,
 * minecarts, {@code create:seat}. Those have plot coordinates and a plot-local yaw, while the
 * compass target is a position in the parent level, so the target has to be brought into the plot's
 * space before the two can be compared.
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
     * <p>Two viewers count, and no others:
     *
     * <ul>
     *   <li><strong>The viewer sits inside the plot</strong> — a retained entity such as an item
     *       frame. Position and yaw are both plot-local, so only the target has to be moved.</li>
     *   <li><strong>The viewer rides a vehicle that sits inside the plot</strong> — a player in a
     *       seat. Sable projects the rider itself out to the parent level but leaves its yaw
     *       plot-local (that is what {@code Entity.calculateViewVector} rotates by the ship's
     *       orientation), so the rider's <em>position</em> has to be moved into the plot as well.
     *       Both go through the same pose, so the ship's translation cancels exactly.</li>
     * </ul>
     *
     * <p>Everyone else keeps world coordinates <em>and</em> a world yaw — a player walking the deck
     * is projected out of the plot in full — and mixing a ship-space bearing into a world-space yaw
     * would put the needle off by the ship's entire rotation: half a turn on a ship facing backwards.
     * Asking Sable for the sub-level an entity merely <em>tracks</em> does exactly that, which is why
     * only the two cases above are answered.
     *
     * @param viewer      the entity the needle is drawn for
     * @param target      the compass target, in parent-level coordinates
     * @param partialTick render partial tick
     * @return the bearing in turns, or empty when neither the viewer nor its vehicle is in a plot
     */
    public static OptionalDouble bearingInSubLevel(Entity viewer, BlockPos target, float partialTick) {
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(viewer);
        boolean rider = false;
        if (subLevel == null) {
            Entity vehicle = viewer.getVehicle();
            if (vehicle == null) {
                return OptionalDouble.empty();
            }
            subLevel = SableCompanion.INSTANCE.getContainingClient(vehicle);
            if (subLevel == null) {
                return OptionalDouble.empty();
            }
            rider = true;
        }

        Pose3dc pose = subLevel.renderPose(partialTick);
        Vec3 viewerPos = rider ? pose.transformPositionInverse(viewer.position()) : viewer.position();
        Vec3 localTarget = pose.transformPositionInverse(Vec3.atCenterOf(target));
        return OptionalDouble.of(Math.atan2(localTarget.z - viewerPos.z, localTarget.x - viewerPos.x) / FULL_TURN);
    }

    /**
     * The viewer's position in parent-level coordinates.
     *
     * <p>For a viewer inside a plot this is the point the ship is actually drawn at; for everyone
     * else it is the viewer's own position, unchanged. Anything that builds a compass target
     * <em>relative to the viewer</em> has to start here, because a plot coordinate is roughly twenty
     * million blocks away from where the ship appears to be — measuring a direction from it would be
     * dominated by that offset rather than by the direction meant.
     *
     * @param viewer      the entity the needle is drawn for
     * @param partialTick render partial tick
     * @return the viewer's position in the parent level
     */
    public static Vec3 parentPosition(Entity viewer, float partialTick) {
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(viewer);
        Vec3 position = viewer.position();
        return subLevel == null ? position : subLevel.renderPose(partialTick).transformPosition(position);
    }
}
