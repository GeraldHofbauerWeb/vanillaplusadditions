package net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.config.HostileEndermenConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Hostile Endermen Module
 * <p>
 * Makes endermen in the End attack players on their own — no staring required. Endermen in the
 * Overworld and the Nether stay vanilla-neutral.
 * <p>
 * Implementation note: the module only sets the enderman's <em>persistent anger target</em>.
 * Vanilla's {@code EndermanLookForPlayerGoal} picks targets via
 * {@code isLookingAtMe(player) || isAngryAt(player)}, so an angry enderman is found by the vanilla
 * goal itself — including its teleport-towards-the-player behaviour. No mixin, no custom AI goals.
 */
public class HostileEndermenModule extends AbstractModule<HostileEndermenModule, HostileEndermenConfig> {

    /** Hostility is re-evaluated once per second per enderman. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /** How many caller frames the teleport diagnostics print. */
    private static final int TELEPORT_DEBUG_FRAMES = 14;

    /** Static handle for the Enderman Overhaul compat mixin, which has no module instance. */
    private static HostileEndermenModule instance;

    public HostileEndermenModule() {
        super("hostile_endermen",
                "Hostile Endermen",
                "Makes endermen in the End attack players without being stared at first "
                        + "(a carved pumpkin still protects)",
                HostileEndermenConfig::new
        );
        instance = this;
    }

    /**
     * Decides whether Enderman Overhaul's End Enderman may teleport its victim away on hit.
     * The ability is suppressed while the player is only being attacked because this module
     * angered the enderman — once the player hits back (vanilla tracks that for 100 ticks via
     * {@code lastHurtByMob}), the enderman keeps its full moveset.
     *
     * @param enderman the attacking enderman
     * @param victim   the entity that was hit
     * @return true if the teleport should be skipped
     */
    public static boolean suppressTeleportAttack(LivingEntity enderman, LivingEntity victim) {
        HostileEndermenModule module = instance;
        if (module == null || !module.isModuleEnabled() || !module.getConfig().suppressTeleportAttack()) {
            return false;
        }
        if (!(victim instanceof Player) || enderman.level().dimension() != Level.END) {
            return false;
        }

        boolean suppressed = enderman.getLastHurtByMob() != victim;
        if (module.getConfig().debugTeleportTracking()) {
            module.getLogger().info("[teleport-debug] Enderman Overhaul teleport attack: {} -> {} ({}), "
                            + "lastHurtByMob={}",
                    enderman.getType().getDescriptionId(), victim.getName().getString(),
                    suppressed ? "SUPPRESSED" : "allowed",
                    enderman.getLastHurtByMob() == null ? "none" : enderman.getLastHurtByMob().getName().getString());
        }
        return suppressed;
    }

    /**
     * Decides whether EnhancedAI's "Teleport anti-cheese" goal may drag a player over to an
     * enderman. That feature exists to stop players from bullying endermen out of reach — with this
     * module every enderman in the End attacks unprovoked, so walking away would constantly pull
     * the player back into the fight.
     *
     * @param mob the mob the goal belongs to
     * @return true if the goal should be vetoed
     */
    public static boolean suppressAntiCheeseTeleport(Mob mob) {
        HostileEndermenModule module = instance;
        if (module == null || !module.isModuleEnabled() || !module.getConfig().suppressAntiCheeseTeleport()) {
            return false;
        }
        if (!(mob instanceof EnderMan) || mob.level().dimension() != Level.END) {
            return false;
        }
        if (!(mob.getTarget() instanceof Player player)) {
            return false;
        }

        // Same self-defence rule as the Enderman Overhaul teleport: a player who hits the enderman
        // may be dragged in again — that is exactly the cheese the feature is meant to punish.
        // Deliberately not logged: canUse() runs every tick per enderman, so even a debug line here
        // floods the log (20k+ entries in a single session).
        return mob.getLastHurtByMob() != player;
    }

    /**
     * Diagnostic hook for {@code PlayerTeleportDebugMixin}: logs every in-dimension teleport of a
     * player together with the caller stack, so the mod/feature responsible can be identified.
     * Does nothing unless {@code debug_teleport_tracking} is enabled.
     *
     * @param player the teleported player
     * @param x      target X
     * @param y      target Y
     * @param z      target Z
     */
    public static void logPlayerTeleport(ServerPlayer player, double x, double y, double z) {
        HostileEndermenModule module = instance;
        if (module == null || !module.getConfig().debugTeleportTracking()) {
            return;
        }

        double distance = Math.sqrt(player.distanceToSqr(x, y, z));
        StringBuilder message = new StringBuilder(String.format(
                "[teleport-debug] %s moved %.1f blocks: (%.1f, %.1f, %.1f) -> (%.1f, %.1f, %.1f) in %s",
                player.getGameProfile().getName(), distance,
                player.getX(), player.getY(), player.getZ(), x, y, z,
                player.level().dimension().location()));

        StackTraceElement[] frames = new Throwable().getStackTrace();
        int printed = 0;
        for (StackTraceElement frame : frames) {
            String className = frame.getClassName();
            if (className.startsWith(HostileEndermenModule.class.getName())
                    || className.startsWith("net.geraldhofbauer.vanillaplusadditions.mixin")) {
                continue;
            }
            message.append("\n    at ").append(frame);
            if (++printed >= TELEPORT_DEBUG_FRAMES) {
                break;
            }
        }

        module.getLogger().info(message.toString());
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Hostile Endermen module initialized - the End just got less relaxing!");
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Hostile Endermen module common setup complete");
        }
    }

    /**
     * Angers endermen the moment they spawn into (or get loaded in) the End.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        if (event.getEntity() instanceof EnderMan enderman && appliesTo(enderman)) {
            Player nearest = findNearestTarget(enderman);
            if (nearest != null) {
                angerAt(enderman, nearest);

                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Made enderman {} hostile at spawn (target {})",
                            enderman.getUUID(), nearest.getGameProfile().getName());
                }
            }
        }
    }

    /**
     * Keeps endermen in the End angry (and re-targets them) once per second.
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof EnderMan enderman)) {
            return;
        }
        if (enderman.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        if (!isModuleEnabled() || !appliesTo(enderman)) {
            return;
        }

        maintainHostility(enderman);
    }

    /**
     * Only server-side endermen that are currently in the End are touched at all.
     */
    private boolean appliesTo(EnderMan enderman) {
        return !enderman.level().isClientSide && enderman.level().dimension() == Level.END;
    }

    private void maintainHostility(EnderMan enderman) {
        Player current = resolveAngerTarget(enderman);
        if (current != null && isValidTarget(enderman, current) && isInRange(enderman, current)) {
            // Keep the current victim (natural stickiness) and just top the anger timer up again.
            applyAngerTime(enderman);
            return;
        }

        Player nearest = findNearestTarget(enderman);
        if (nearest == null) {
            // Nobody within detection_range: calm down immediately and drop the chase, so leaving
            // the area actually ends the fight instead of dragging an aggro train along.
            calmDown(enderman);
            return;
        }

        angerAt(enderman, nearest);

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Enderman {} angered at player {}",
                    enderman.getUUID(), nearest.getGameProfile().getName());
        }
    }

    /**
     * Resolves the enderman's current persistent anger target, if that player is still around.
     */
    private @Nullable Player resolveAngerTarget(EnderMan enderman) {
        UUID targetId = enderman.getPersistentAngerTarget();
        return targetId == null ? null : enderman.level().getPlayerByUUID(targetId);
    }

    private @Nullable Player findNearestTarget(EnderMan enderman) {
        double range = getConfig().getDetectionRangeValue();
        return enderman.level().getNearestPlayer(
                enderman.getX(), enderman.getEyeY(), enderman.getZ(), range,
                entity -> entity instanceof Player player && isValidTarget(enderman, player));
    }

    private boolean isInRange(EnderMan enderman, Player player) {
        double range = getConfig().getDetectionRangeValue();
        return player.distanceToSqr(enderman) <= range * range;
    }

    /**
     * A player is a valid auto-aggression target unless they are creative/spectator, dead, or
     * protected by a carved pumpkin (or any modded ender mask) while that protection is enabled.
     */
    private boolean isValidTarget(EnderMan enderman, Player player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!getConfig().respectCarvedPumpkin()) {
            return true;
        }
        // Same hook vanilla's EnderMan#isLookingAtMe uses, so ender masks and other mods'
        // EnderManAngerEvent cancels keep working as protection.
        return !CommonHooks.shouldSuppressEnderManAnger(enderman, player,
                player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private void angerAt(EnderMan enderman, Player player) {
        enderman.setPersistentAngerTarget(player.getUUID());
        // startPersistentAngerTimer() rolls a random 20–39 s duration, so the configured value has
        // to be written afterwards — not before.
        enderman.startPersistentAngerTimer();
        applyAngerTime(enderman);
    }

    private void applyAngerTime(EnderMan enderman) {
        int angerDuration = getConfig().getAngerDurationValue();
        enderman.setRemainingPersistentAngerTime(
                angerDuration == HostileEndermenConfig.INDEFINITE_ANGER ? Integer.MAX_VALUE : angerDuration);
    }

    /**
     * Ends the hunt once no player is within {@code detection_range}: anger, the current player
     * target and the revenge memory all go, otherwise the vanilla goals would immediately pick the
     * player up again (they work off the 64-block follow range, not our detection range).
     * Non-player targets — endermites — are left alone.
     */
    private void calmDown(EnderMan enderman) {
        boolean chasingPlayer = enderman.getTarget() instanceof Player;
        boolean angry = enderman.getPersistentAngerTarget() != null
                || enderman.getRemainingPersistentAngerTime() > 0;
        if (!chasingPlayer && !angry && !(enderman.getLastHurtByMob() instanceof Player)) {
            return;
        }

        enderman.setRemainingPersistentAngerTime(0);
        enderman.setPersistentAngerTarget(null);
        if (enderman.getLastHurtByMob() instanceof Player) {
            enderman.setLastHurtByMob(null);
        }
        if (chasingPlayer) {
            enderman.setTarget(null);
        }

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Enderman {} calmed down (no players within detection range)", enderman.getUUID());
        }
    }
}
