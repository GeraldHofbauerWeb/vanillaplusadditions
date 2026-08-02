package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional Create integration for the Mob Cart Loader: the only class that touches Create's train
 * API, guarded by a cached {@code isLoaded("create")} check. Without Create every lookup returns
 * "nothing found" and the loader/unloader keep working on plain minecarts.
 *
 * <p>A train carriage cannot be ridden like a minecart — it is a {@link CarriageContraptionEntity}
 * whose passengers occupy <b>Create Seat blocks</b> addressed by index inside the contraption
 * ({@link Contraption#getSeats()} / {@link AbstractContraptionEntity#addSittingPassenger}). This
 * class translates between those seat indices and world positions so the block entities can keep
 * thinking in "the thing next to my output face".</p>
 *
 * <p>Only members with vanilla-typed signatures are used: {@code net.createmod.catnip} is not on the
 * compile classpath, so anything referencing it (e.g. {@code Carriage.bogeys}, {@code ITrackBlock})
 * must be avoided. Track detection therefore goes through the {@code create:tracks} block tag rather
 * than {@code ITrackBlock}, which additionally needs no Create classes at all.</p>
 *
 * <p>Note: Create's own {@code SeatBlock.canBePickedUp} is deliberately <b>not</b> applied. It
 * rejects hostile mobs while Create's {@code seatHostileMobs} option is off, which would silently
 * break this module's hostile/friendly comparator output. These blocks are explicit machinery.</p>
 */
public final class CreateTrainAccess {

    private static final boolean CREATE_LOADED = ModList.get().isLoaded("create");

    /** Create's block tag covering every track material (vanilla lookup — no Create classes). */
    private static final TagKey<Block> TRACKS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("create", "tracks"));

    /** A train is considered standing below this absolute speed. */
    private static final double STANDING_EPSILON = 1.0E-3;

    private CreateTrainAccess() {
    }

    /**
     * A seat of a carriage, expressed in vanilla types so callers link without Create.
     *
     * @param carriage  the carriage contraption entity owning the seat
     * @param seatIndex index into the contraption's seat list
     * @param worldPos  the seat's current position in the world
     */
    public record TrainSeat(Entity carriage, int seatIndex, Vec3 worldPos) {
    }

    /**
     * Whether Create is present at all.
     *
     * @return true if the {@code create} mod is loaded
     */
    public static boolean isCreateLoaded() {
        return CREATE_LOADED;
    }

    /**
     * Whether the given block is one of Create's train tracks. Safe (and simply {@code false})
     * without Create, because the tag then does not exist.
     *
     * @param state the block state to test
     * @return true if the block is tagged {@code create:tracks}
     */
    public static boolean isTrack(BlockState state) {
        return state.is(TRACKS);
    }

    /**
     * Finds the free carriage seat closest to the given track block.
     *
     * @param level    the server level
     * @param trackPos the track block the loader points at
     * @param radius   maximum distance (blocks) between the track block and the seat
     * @return the nearest free seat of a standing carriage, or null if there is none
     */
    @Nullable
    public static TrainSeat findFreeSeat(ServerLevel level, BlockPos trackPos, double radius) {
        if (!CREATE_LOADED) {
            return null;
        }
        Vec3 center = Vec3.atCenterOf(trackPos);
        double bestDist = radius * radius;
        TrainSeat best = null;

        for (CarriageContraptionEntity carriage : standingCarriages(level, trackPos, radius)) {
            Contraption contraption = carriage.getContraption();
            if (contraption == null) {
                continue;
            }
            List<BlockPos> seats = contraption.getSeats();
            Collection<Integer> occupied = contraption.getSeatMapping().values();
            for (int i = 0; i < seats.size(); i++) {
                if (occupied.contains(i)) {
                    continue;
                }
                Vec3 world = seatPosition(carriage, seats.get(i));
                double dist = world.distanceToSqr(center);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new TrainSeat(carriage, i, world);
                }
            }
        }
        return best;
    }

    /**
     * Finds the mob sitting in the carriage seat closest to the given track block.
     *
     * @param level    the server level
     * @param trackPos the track block the unloader points at
     * @param radius   maximum distance (blocks) between the track block and the seat
     * @return the mob in the nearest occupied seat of a standing carriage, or null if there is none
     */
    @Nullable
    public static Mob findSeatedMob(ServerLevel level, BlockPos trackPos, double radius) {
        if (!CREATE_LOADED) {
            return null;
        }
        Vec3 center = Vec3.atCenterOf(trackPos);
        double bestDist = radius * radius;
        Mob best = null;

        for (CarriageContraptionEntity carriage : standingCarriages(level, trackPos, radius)) {
            Contraption contraption = carriage.getContraption();
            if (contraption == null) {
                continue;
            }
            Map<UUID, Integer> mapping = contraption.getSeatMapping();
            for (Entity passenger : carriage.getPassengers()) {
                if (!(passenger instanceof Mob mob) || !mapping.containsKey(mob.getUUID())) {
                    continue;
                }
                BlockPos seat = contraption.getSeatOf(mob.getUUID());
                if (seat == null) {
                    continue;
                }
                double dist = seatPosition(carriage, seat).distanceToSqr(center);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = mob;
                }
            }
        }
        return best;
    }

    /**
     * Seats an entity that is already in the world. Create handles the seat mapping and the
     * client-side sync packet itself.
     *
     * @param seat   the target seat
     * @param entity the entity to seat
     * @return true if the entity was seated
     */
    public static boolean seat(TrainSeat seat, Entity entity) {
        if (!CREATE_LOADED || !(seat.carriage() instanceof AbstractContraptionEntity contraption)) {
            return false;
        }
        contraption.addSittingPassenger(entity, seat.seatIndex());
        return true;
    }

    // ---- internals (only reached with Create present) ----

    /** Carriages of standing trains whose entity box is within {@code radius} of the track block. */
    private static List<CarriageContraptionEntity> standingCarriages(ServerLevel level, BlockPos trackPos,
                                                                     double radius) {
        AABB box = new AABB(trackPos).inflate(radius);
        return level.getEntitiesOfClass(CarriageContraptionEntity.class, box, CreateTrainAccess::isStanding);
    }

    /**
     * Whether the carriage's train is currently standing. The carriage's own {@code carriage} field
     * is private, so the train is looked up through the public global railway registry via the
     * carriage's public {@code trainId}.
     */
    private static boolean isStanding(CarriageContraptionEntity carriage) {
        UUID trainId = carriage.trainId;
        if (trainId == null) {
            return false;
        }
        Train train = Create.RAILWAYS.trains.get(trainId);
        return train != null && !train.derailed && Math.abs(train.speed) < STANDING_EPSILON;
    }

    /**
     * World position of a contraption-local seat position — the same transform Create's own
     * {@code AbstractContraptionEntity#getPassengerPosition} applies.
     */
    private static Vec3 seatPosition(AbstractContraptionEntity carriage, BlockPos localSeat) {
        return carriage.toGlobalVector(Vec3.atCenterOf(localSeat), 1.0f);
    }
}
