package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.client;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

/**
 * The needle of the World Compass: it points at world north, always.
 *
 * <p>Rather than deriving the angle by hand, this hands vanilla's own
 * {@link CompassItemPropertyFunction} a target four million blocks due north of the viewer. The
 * needle is then computed by exactly the code that drives a lodestone compass — same wobble for the
 * holding player, same instant reading in an item frame, same everything — so it cannot disagree
 * with the compass lying next to it in the hotbar.
 *
 * <p>Two things follow from that distance. The target is so far away that the direction to it is
 * north from anywhere in the world to well within a tenth of a degree, which is a fraction of one
 * of the 32 texture frames; and it stays comfortably inside the world border of ±30 million.
 *
 * <p>Because the target carries the viewer's own dimension, the needle also works in the Nether and
 * the End, where an ordinary compass spins — vanilla only rejects a target from a <em>different</em>
 * dimension. And since this goes through the ordinary vanilla path, the sub-level correction from
 * {@code CompassAngleSubLevelMixin} applies here too: aboard a turning airship the needle keeps
 * pointing at world north.
 */
public final class WorldCompassAngle {

    /** Far enough that the bearing is north from anywhere, near enough to stay inside the world. */
    private static final int NORTH_DISTANCE = 4_000_000;

    private WorldCompassAngle() {
    }

    /**
     * Builds the needle function for the World Compass.
     *
     * @return a property function whose needle points at world north
     */
    public static ClampedItemPropertyFunction create() {
        return new CompassItemPropertyFunction((level, stack, entity) -> GlobalPos.of(
                level.dimension(),
                BlockPos.containing(entity.getX(), entity.getY(), entity.getZ() - NORTH_DISTANCE)));
    }
}
