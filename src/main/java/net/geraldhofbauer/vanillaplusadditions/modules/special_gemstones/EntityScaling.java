package net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.mixin.special_gemstones.CreeperExplosionRadiusAccessor;
import net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.config.SpecialGemstonesConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides and applies one size step on a living creature.
 *
 * <p>A creature is in exactly one of three states — <strong>shrunk</strong>, <strong>natural</strong>
 * and <strong>grown</strong> — and a gemstone moves it one state, never further. That cap is the
 * whole design: without it the gemstones would stack into arbitrarily huge mobs, and the oversized
 * wolves that make a dungeon find special would stop being special.
 *
 * <p>The state is not stored anywhere of our own. It <em>is</em> the pair of attribute modifiers:
 * a creature carrying {@link #GROWN_ID} is grown, one carrying {@link #SHRUNK_ID} is shrunk, and one
 * carrying neither is at its natural size. Permanent modifiers are saved in the entity's
 * {@code "attributes"} NBT and are synced to every client tracking it, so the state survives saving,
 * chunk unload and a restart, and needs no data attachment (which would not reach the client anyway).
 *
 * <p>Modifiers also make the change <strong>exactly</strong> reversible. Multiplying base values
 * would leave rounding behind on every round trip, and worse, would erase the creature's own numbers:
 * a horse's speed, jump strength and health are rolled per animal, and those are the "hidden stats"
 * that have to survive being grown and shrunk again. Removing a modifier restores them bit for bit.
 */
public final class EntityScaling {

    /** Marks a grown creature. Applied to the size attribute and to every scaled stat. */
    public static final ResourceLocation GROWN_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "gemstone_grown");

    /** Marks a shrunk creature. */
    public static final ResourceLocation SHRUNK_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "gemstone_shrunk");

    /**
     * Vanilla's own bounds for {@code generic.scale}, from the {@code RangedAttribute} in
     * {@code Attributes}. Nothing the config asks for may leave them.
     */
    public static final double VANILLA_MIN_SCALE = 0.0625D;
    public static final double VANILLA_MAX_SCALE = 16.0D;

    private EntityScaling() {
    }

    /** The three states a creature can be in. */
    public enum Step {
        SHRUNK,
        NATURAL,
        GROWN
    }

    /** Why a gemstone did or did not work. Only {@link #CHANGED} consumes the item. */
    public enum Outcome {
        CHANGED,
        AT_LIMIT,
        NO_SPACE,
        DENIED,
        NOT_OWNED,
        OUT_OF_REACH,
        NO_SCALE_ATTRIBUTE
    }

    /**
     * The outcome plus the resulting size as a multiple of the creature's OWN natural size — 0.5, 1
     * or 2 with the defaults, never the absolute attribute value. That is what the player is told,
     * and it stays true for a creature whose natural size is not 1.0 to begin with.
     */
    public record Result(Outcome outcome, double relativeScale) {
    }

    /** Which of the three states a creature is in right now. */
    public static Step currentStep(LivingEntity target) {
        AttributeInstance scale = target.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return Step.NATURAL;
        }
        if (scale.hasModifier(GROWN_ID)) {
            return Step.GROWN;
        }
        return scale.hasModifier(SHRUNK_ID) ? Step.SHRUNK : Step.NATURAL;
    }

    /**
     * Moves the creature one step.
     *
     * @param target  the creature that was right-clicked
     * @param player  the player holding the gemstone
     * @param grow    {@code true} for the Growth Gemstone, {@code false} for the Shrinking one
     * @param config  the module settings
     * @param denied  entity-type ids the gemstones refuse, already parsed
     * @return what happened, and the resulting size
     */
    public static Result apply(LivingEntity target, Player player, boolean grow,
                               SpecialGemstonesConfig config, Set<String> denied) {
        AttributeInstance scale = target.getAttribute(Attributes.SCALE);
        if (scale == null) {
            // A third-party creature whose attribute supplier was hand-rolled without SCALE. Every
            // vanilla LivingEntity has it via createLivingAttributes().
            return new Result(Outcome.NO_SCALE_ATTRIBUTE, 1.0D);
        }

        // The base value is the creature's OWN size: our modifiers never touch it.
        double natural = scale.getBaseValue();
        double current = target.getScale();
        double relative = natural > 0.0D ? current / natural : 1.0D;

        if (target instanceof Player && !config.arePlayersAllowed()) {
            return new Result(Outcome.DENIED, relative);
        }
        if (denied.contains(EntityType.getKey(target.getType()).toString())) {
            return new Result(Outcome.DENIED, relative);
        }
        if (config.isTamedRequired() && !isOwnedBy(target, player)) {
            return new Result(Outcome.NOT_OWNED, relative);
        }
        if (natural < config.getMinNaturalScale() || natural > config.getMaxNaturalScale()) {
            // Already unnaturally large or small — a dungeon wolf, Sif, a boss-sized mob from another
            // mod. Out of the gemstones' reach on purpose, so what makes such a find special stays so.
            return new Result(Outcome.OUT_OF_REACH, relative);
        }

        Step from = currentStep(target);
        Step next = nextStep(from, grow);
        if (next == null) {
            return new Result(Outcome.AT_LIMIT, relative);
        }

        double scaleFactor = config.getScaleFactor();
        double targetScale = natural * switch (next) {
            case GROWN -> scaleFactor;
            case SHRUNK -> 1.0D / scaleFactor;
            case NATURAL -> 1.0D;
        };
        if (targetScale > current && config.isSpaceChecked() && !fitsAt(target, targetScale / current)) {
            return new Result(Outcome.NO_SPACE, relative);
        }

        applyStep(target, next, config);
        if (config.isCreeperBlastScaled()) {
            scaleCreeperBlast(target, next.ordinal() > from.ordinal(), config.getStatFactor());
        }
        return new Result(Outcome.CHANGED, natural > 0.0D ? target.getScale() / natural : 1.0D);
    }

    /** One step in the asked direction, or {@code null} when there is no room left. */
    private static Step nextStep(Step step, boolean grow) {
        if (grow) {
            return switch (step) {
                case SHRUNK -> Step.NATURAL;
                case NATURAL -> Step.GROWN;
                case GROWN -> null;
            };
        }
        return switch (step) {
            case GROWN -> Step.NATURAL;
            case NATURAL -> Step.SHRUNK;
            case SHRUNK -> null;
        };
    }

    /**
     * Writes the new state: both modifiers off every affected attribute, then the one the new state
     * calls for. The health bar is carried over as a fraction, so a creature at half health is still
     * at half health afterwards instead of suddenly wounded (grown) or dying (shrunk).
     */
    private static void applyStep(LivingEntity target, Step step, SpecialGemstonesConfig config) {
        float healthFraction = target.getMaxHealth() > 0.0F
                ? target.getHealth() / target.getMaxHealth()
                : 1.0F;

        double scaleAmount = amountFor(step, config.getScaleFactor());
        double statAmount = amountFor(step, config.getStatFactor());

        setModifier(target.getAttribute(Attributes.SCALE), step, scaleAmount);
        for (Holder<Attribute> attribute : scaledAttributes(config)) {
            setModifier(target.getAttribute(attribute), step, statAmount);
        }

        target.setHealth(healthFraction * target.getMaxHealth());
    }

    /**
     * Carries the creeper's blast along with it.
     *
     * <p>The blast is the one "stat" that is not an attribute — {@code Creeper.explodeCreeper} reads a
     * plain {@code int} field — so there is no modifier to add and remove, and the number has to be
     * walked up and down instead. Rounding is chosen so the walk comes back exactly where it started:
     * growing floors, shrinking rounds, and 3 → 4 → 3 and 3 → 2 → 3 both hold at the default factor.
     *
     * <p>The blast follows {@code stat_factor}, not {@code scale_factor}: at the default that makes a
     * grown creeper a 4, one step short of a charged creeper's 6, rather than equal to it. A giant
     * creeper should be worse news than an ordinary one, not as bad as a lightning strike made it.
     */
    private static void scaleCreeperBlast(LivingEntity target, boolean up, double factor) {
        if (!(target instanceof Creeper creeper)) {
            return;
        }
        CreeperExplosionRadiusAccessor accessor = (CreeperExplosionRadiusAccessor) creeper;
        int current = accessor.getExplosionRadius();
        int next = up
                ? (int) Math.floor(current * factor)
                : (int) Math.round(current / factor);
        accessor.setExplosionRadius(Math.max(1, next));
    }

    /** The modifier amount for {@code ADD_MULTIPLIED_TOTAL}: ×factor grown, ÷factor shrunk. */
    private static double amountFor(Step step, double factor) {
        return switch (step) {
            case GROWN -> factor - 1.0D;
            case SHRUNK -> 1.0D / factor - 1.0D;
            case NATURAL -> 0.0D;
        };
    }

    private static void setModifier(AttributeInstance instance, Step step, double amount) {
        if (instance == null) {
            // Not every creature has every attribute — a cow has no attack damage, and only horses
            // and their relatives roll a jump strength worth scaling.
            return;
        }
        instance.removeModifier(GROWN_ID);
        instance.removeModifier(SHRUNK_ID);
        if (step == Step.NATURAL) {
            return;
        }
        ResourceLocation id = step == Step.GROWN ? GROWN_ID : SHRUNK_ID;
        instance.addOrReplacePermanentModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The configured stat attributes that actually exist in the registry. */
    private static List<Holder<Attribute>> scaledAttributes(SpecialGemstonesConfig config) {
        List<Holder<Attribute>> holders = new ArrayList<>();
        for (String raw : config.getScaledAttributes()) {
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id == null) {
                continue;
            }
            Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.getHolder(id);
            holder.ifPresent(holders::add);
        }
        return holders;
    }

    /**
     * Whether the creature would still fit where it stands after growing by {@code ratio}.
     *
     * <p>{@code Entity.refreshDimensions} does try to find a free spot for a mob that grew
     * ({@code fudgePositionAfterSizeChange}), but it gives up when there is none — and then the animal
     * is stuck in the ceiling, taking suffocation damage. Better to refuse the click.
     *
     * <p>The current bounding box is scaled about the creature's feet, which keeps every factor
     * vanilla already baked in (baby animals, pose) instead of rebuilding the box from
     * {@code getDefaultDimensions}, which is protected anyway.
     */
    private static boolean fitsAt(LivingEntity target, double ratio) {
        AABB box = target.getBoundingBox();
        Vec3 feet = target.position();
        AABB grown = new AABB(
                feet.x + (box.minX - feet.x) * ratio,
                feet.y + (box.minY - feet.y) * ratio,
                feet.z + (box.minZ - feet.z) * ratio,
                feet.x + (box.maxX - feet.x) * ratio,
                feet.y + (box.maxY - feet.y) * ratio,
                feet.z + (box.maxZ - feet.z) * ratio);
        return target.level().noCollision(target, grown);
    }

    /** True when the creature is a tamed animal (or any {@link OwnableEntity}) owned by this player. */
    private static boolean isOwnedBy(LivingEntity target, Player player) {
        if (!(target instanceof OwnableEntity ownable)) {
            return false;
        }
        UUID owner = ownable.getOwnerUUID();
        return owner != null && owner.equals(player.getUUID());
    }
}
