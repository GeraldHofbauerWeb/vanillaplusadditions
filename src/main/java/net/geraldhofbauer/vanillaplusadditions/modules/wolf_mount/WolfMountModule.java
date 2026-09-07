package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.config.WolfMountConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.network.MountWolfPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Lets a big, armored, tamed wolf be ridden like a horse.
 *
 * <p>The steering itself lives in {@code mixin/wolf_mount/WolfMountMixin}: vanilla's whole
 * ridden-movement pipeline is generic on {@code LivingEntity}, so overriding four hooks on
 * {@code Wolf} is enough and no AI has to be removed. This class owns everything that can be done
 * with plain events: mounting, keeping the ride legitimate, protecting the mount from its owner,
 * and having it fight alongside its rider.
 */
public class WolfMountModule extends AbstractModule<WolfMountModule, WolfMountConfig> {

    /**
     * Extra reach while mounted. Transient, so it is never written to disk — the worst case for a
     * leaked modifier is a single session, and {@link #onPlayerTick} heals it anyway.
     */
    private static final ResourceLocation REACH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "wolf_mount_reach");

    /** Server-side interaction range guard for the mount packet, squared. */
    private static final double MAX_MOUNT_DISTANCE_SQR = 64.0D;

    private static WolfMountModule instance;

    public WolfMountModule() {
        super("wolf_mount",
                "Wolf Mount",
                "Ride a big, armored, tamed wolf like a horse — steer it, jump with it, "
                        + "fight from its back, and stop hurting it by accident.",
                WolfMountConfig::new);
    }

    @Override
    protected void onInitialize() {
        instance = this;
        getModEventBus().addListener(this::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Wolf Mount module initialized - big armored wolves can be ridden");
    }

    // ---- Static accessors for the mixin -------------------------------------------------------
    // The mixin is merged into net.minecraft.world.entity.animal.Wolf and has no other way to
    // reach module state.

    /**
     * Whether the module is loaded and enabled.
     *
     * <p>Deliberately the <em>only</em> thing the mixin's {@code getControllingPassenger} consults:
     * that method feeds {@code Entity.isControlledByLocalInstance()}, which decides whether the
     * client is authoritative for the vehicle. Reading gameplay config there would let a
     * client/server config mismatch freeze the mount, so scale/owner/armor are enforced at mount
     * time and by the periodic recheck instead.
     *
     * @return true if the module is active
     */
    public static boolean isModuleActive() {
        return instance != null && instance.isModuleEnabled();
    }

    static boolean isRequireLargeScale() {
        return instance == null || instance.getConfig().isRequireLargeScale();
    }

    static double getMinScale() {
        return instance != null ? instance.getConfig().getMinScale() : 2.0D;
    }

    static boolean isRequireTamedOwner() {
        return instance == null || instance.getConfig().isRequireTamedOwner();
    }

    static boolean isRequireBodyArmor() {
        return instance == null || instance.getConfig().isRequireBodyArmor();
    }

    /**
     * Speed scaling applied on top of the wolf's movement speed attribute while ridden.
     *
     * @return the configured multiplier
     */
    public static double getSpeedMultiplier() {
        return instance != null ? instance.getConfig().getSpeedMultiplier() : 1.0D;
    }

    /**
     * Sideways input scaling while ridden.
     *
     * @return the configured multiplier
     */
    public static double getStrafeMultiplier() {
        return instance != null ? instance.getConfig().getStrafeMultiplier() : 0.5D;
    }

    /**
     * Backwards input scaling while ridden.
     *
     * @return the configured multiplier
     */
    public static double getBackwardMultiplier() {
        return instance != null ? instance.getConfig().getBackwardMultiplier() : 0.25D;
    }

    /**
     * Jump power scaling while ridden.
     *
     * @return the configured multiplier
     */
    public static double getJumpStrength() {
        return instance != null ? instance.getConfig().getJumpStrength() : 1.6D;
    }

    /**
     * Whether a tap should jump at full power instead of charging the meter.
     *
     * @return true if charging is skipped
     */
    public static boolean isInstantJump() {
        return instance != null && instance.getConfig().isInstantJump();
    }

    /**
     * Jump scale produced by the shortest possible tap, as a fraction of a full charge.
     *
     * @return the configured floor
     */
    public static double getMinJumpCharge() {
        return instance != null ? instance.getConfig().getMinJumpCharge() : 0.15D;
    }

    /**
     * Whether the mount should stay at the water surface instead of sinking.
     *
     * @return true if buoyancy is enabled
     */
    public static boolean isFloatInWater() {
        return instance == null || instance.getConfig().isFloatInWater();
    }

    /**
     * Whether the rider's HUD should show the mount's body armor durability.
     *
     * @return true if the armor bar is enabled
     */
    public static boolean isShowArmorBar() {
        return instance == null || instance.getConfig().isShowArmorBar();
    }

    /**
     * Whether vanilla's multi-row mount health bar should be collapsed into a single row.
     *
     * @return true if the compact bar is enabled
     */
    public static boolean isCompactMountHealth() {
        return instance == null || instance.getConfig().isCompactMountHealth();
    }

    /**
     * Remaining-durability fraction below which the armor bar warns the rider.
     *
     * @return the configured threshold, 0 to disable
     */
    public static double getArmorWarningThreshold() {
        return instance != null ? instance.getConfig().getArmorWarningThreshold() : 0.25D;
    }

    // ---- Networking --------------------------------------------------------------------------

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                MountWolfPacket.TYPE,
                MountWolfPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled()) {
                        return;
                    }
                    if (!(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    Entity target = player.level().getEntity(packet.wolfId());
                    if (!(target instanceof Wolf wolf)) {
                        return;
                    }
                    if (player.distanceToSqr(wolf) > MAX_MOUNT_DISTANCE_SQR * MAX_MOUNT_DISTANCE_SQR) {
                        return;
                    }
                    if (player.isSpectator() || player.isPassenger()) {
                        return;
                    }
                    if (!WolfMountRules.canMount(wolf, player)) {
                        return;
                    }
                    mount(wolf, player);
                }));
    }

    /**
     * Puts the player on the wolf and clears everything that would fight the rider.
     *
     * <p>The sit clear is load-bearing twice over: a sitting wolf renders haunched under its rider,
     * and both {@code OwnerHurtByTargetGoal} and {@code OwnerHurtTargetGoal} bail out on
     * {@code isOrderedToSit()} — which would silently disable the fight-alongside behaviour. It
     * also undoes the sit toggle that vanilla's own right-click just performed, see
     * {@link MountWolfPacket}.
     */
    private void mount(Wolf wolf, ServerPlayer player) {
        wolf.setOrderedToSit(false);
        wolf.setInSittingPose(false);
        wolf.getNavigation().stop();
        wolf.setTarget(null);
        player.setYRot(wolf.getYRot());
        player.setXRot(wolf.getXRot());
        if (player.startRiding(wolf)) {
            wolf.level().playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(),
                    SoundEvents.WOLF_AMBIENT, wolf.getSoundSource(), 0.4F, 1.0F);
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("{} mounted wolf {} (scale {})",
                        player.getName().getString(), wolf.getId(),
                        wolf.getAttributeValue(Attributes.SCALE));
            }
        }
    }

    // ---- Keeping the ride legitimate ----------------------------------------------------------

    @SubscribeEvent
    public void onEntityMount(EntityMountEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntityMounting() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getEntityBeingMounted() instanceof Wolf)) {
            return;
        }
        if (event.isMounting()) {
            applyReachModifier(player);
        } else {
            removeReachModifier(player);
        }
    }

    /**
     * Periodic upkeep: keeps the mount on the nearest threat, ends a ride whose preconditions no
     * longer hold, and heals a leaked reach modifier (logout while mounted, dimension change,
     * death, a crash mid-ride).
     *
     * <p>The target sweep runs on its own, faster interval than the eligibility recheck: a fight
     * changes shape far quicker than a ride becomes illegitimate.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getVehicle() instanceof Wolf mount
                && mount.getControllingPassenger() == player
                && getConfig().isDefendRider()
                && getConfig().isTargetNearest()
                && player.tickCount % getConfig().getTargetRecheckTicks() == 0) {
            retargetNearest(mount, player);
        }
        if (player.tickCount % getConfig().getEligibilityRecheckTicks() != 0) {
            return;
        }
        if (!(player.getVehicle() instanceof Wolf wolf)) {
            removeReachModifier(player);
            return;
        }
        if (!WolfMountRules.stillEligible(wolf, player)) {
            player.stopRiding();
            removeReachModifier(player);
            return;
        }
        applyReachModifier(player);
    }

    /**
     * Eject the rider when the mount's body armor comes off or breaks.
     */
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!isModuleEnabled() || !getConfig().isDismountWhenArmorRemoved()) {
            return;
        }
        if (!getConfig().isRequireBodyArmor()) {
            return;
        }
        if (event.getSlot() != EquipmentSlot.BODY) {
            return;
        }
        if (!(event.getEntity() instanceof Wolf wolf) || wolf.level().isClientSide()) {
            return;
        }
        if (WolfMountRules.isCanineBodyArmor(event.getTo())) {
            return;
        }
        if (wolf.getControllingPassenger() instanceof ServerPlayer rider) {
            rider.stopRiding();
            removeReachModifier(rider);
        }
    }

    private void applyReachModifier(ServerPlayer player) {
        double bonus = getConfig().getRiderReachBonus();
        AttributeInstance attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute == null) {
            return;
        }
        if (bonus <= 0.0D) {
            attribute.removeModifier(REACH_MODIFIER_ID);
            return;
        }
        if (attribute.getModifier(REACH_MODIFIER_ID) != null) {
            return;
        }
        attribute.addTransientModifier(new AttributeModifier(
                REACH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
    }

    private void removeReachModifier(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute != null) {
            attribute.removeModifier(REACH_MODIFIER_ID);
        }
    }

    // ---- Owner damage immunity ----------------------------------------------------------------

    /**
     * Makes a rideable wolf immune to its own owner, sweeping-edge splash included.
     *
     * <p>This deliberately hooks {@code LivingIncomingDamageEvent} and not
     * {@code LivingDamageEvent.Pre} (which {@code BattleDogsModule} uses). This event fires right
     * after the invulnerability checks and before everything else, so cancelling here makes
     * {@code hurt()} return false, which means: no invulnerability frames are consumed, the wolf's
     * {@code lastHurtByMob} is never set (so it does not turn on its owner), the owner's sword
     * takes no durability for the hit, the wolf's armor takes none either, and Thorns never
     * reflects the owner's own damage back at them.
     *
     * <p>Sweeping edge needs no separate handling: {@code Player.attack} builds one
     * {@code playerAttack} damage source and reuses that same instance for the direct hit and for
     * every sweep victim, so one attacker check covers both.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWolfIncomingDamage(LivingIncomingDamageEvent event) {
        if (!isModuleEnabled() || !getConfig().isOwnerDamageImmunity()) {
            return;
        }
        if (!(event.getEntity() instanceof Wolf wolf) || wolf.level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (!wolf.isTame() || !wolf.isOwnedBy(player)) {
            return;
        }
        if (getConfig().isOwnerImmunityOnlyWhenRidden() && wolf.getControllingPassenger() != player) {
            return;
        }
        if (getConfig().isOwnerImmunityRequiresArmor() && !WolfMountRules.isCanineBodyArmor(wolf.getBodyArmorItem())) {
            return;
        }
        if (!WolfMountRules.isLargeEnough(wolf)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Suppress knockback on a wolf that is being ridden.
     *
     * <p>Needed because vanilla's sweep applies knockback <em>before</em> the damage, so cancelling
     * the damage cannot undo the shove. Matched structurally (is it being ridden?) rather than by
     * damage source, because {@code LivingKnockBackEvent} carries none. It is also simply correct
     * on its own terms: a mount should not be shoved around while its rider is steering it.
     */
    @SubscribeEvent
    public void onRiddenKnockback(LivingKnockBackEvent event) {
        if (!isModuleEnabled() || !getConfig().isSuppressRiddenKnockback()) {
            return;
        }
        if (event.getEntity() instanceof Wolf wolf && wolf.getControllingPassenger() instanceof Player) {
            event.setCanceled(true);
        }
    }

    // ---- Fighting alongside the rider ---------------------------------------------------------

    /**
     * The mount attacks whatever attacks its rider.
     *
     * <p>Vanilla's {@code OwnerHurtByTargetGoal} already does most of this, but it is capped at
     * follow range (16 blocks), so an archer picking the rider off from further away is never
     * answered. The actual biting stays vanilla {@code MeleeAttackGoal} — only the target is set
     * here.
     */
    @SubscribeEvent
    public void onRiderIncomingDamage(LivingIncomingDamageEvent event) {
        if (!isModuleEnabled() || !getConfig().isDefendRider()) {
            return;
        }
        if (!(event.getEntity() instanceof Player rider) || rider.level().isClientSide()) {
            return;
        }
        if (!(rider.getVehicle() instanceof Wolf wolf) || wolf.getControllingPassenger() != rider) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            retarget(wolf, rider, attacker);
        }
    }

    /**
     * The mount takes over the rider's target when the rider strikes something.
     */
    @SubscribeEvent
    public void onRiderAttack(AttackEntityEvent event) {
        if (!isModuleEnabled() || !getConfig().isDefendRider()) {
            return;
        }
        Player rider = event.getEntity();
        if (rider.level().isClientSide()) {
            return;
        }
        if (!(rider.getVehicle() instanceof Wolf wolf) || wolf.getControllingPassenger() != rider) {
            return;
        }
        if (event.getTarget() instanceof LivingEntity target) {
            retarget(wolf, rider, target);
        }
    }

    /**
     * Points the mount at a new target, keeping vanilla's own exclusions intact.
     *
     * <p>A live target is no longer sacred: with {@code target_nearest} on, a closer threat takes
     * over, because the mount can only bite what is actually within its reach. The switch needs
     * {@code retarget_margin} blocks of daylight between the two so that two mobs at roughly the
     * same distance cannot make it flip-flop.
     */
    private void retarget(Wolf wolf, Player rider, LivingEntity target) {
        if (!isAttackable(wolf, rider, target)) {
            return;
        }
        double radius = getConfig().getDefendRiderRadius();
        if (wolf.distanceToSqr(target) > radius * radius) {
            return;
        }
        LivingEntity current = wolf.getTarget();
        if (current == null || !isAttackable(wolf, rider, current)) {
            wolf.setTarget(target);
            return;
        }
        if (getConfig().isTargetNearest()
                && wolf.distanceTo(target) < wolf.distanceTo(current) - getConfig().getRetargetMargin()) {
            wolf.setTarget(target);
        }
    }

    /**
     * Re-picks the nearest threat around the mount, and drops a target that has died, become
     * off-limits or wandered out of the defend radius.
     *
     * <p>This is the half vanilla cannot do. Its target goals only ever fire on an <em>empty</em>
     * target slot, so the first mob to hit the rider owns the mount for the rest of the fight —
     * which is exactly the "bites at the archer 25 blocks away while a zombie stands in its face"
     * complaint. Candidates are restricted to mobs already hostile towards rider or mount, so the
     * sweep never starts a fight with the local wildlife.
     */
    private void retargetNearest(Wolf wolf, Player rider) {
        double scanRadius = Math.max(getConfig().getDefendRiderRadius(), getConfig().getHostileScanRadius());
        if (scanRadius <= 0.0D) {
            return;
        }
        LivingEntity current = wolf.getTarget();
        if (current != null
                && (!isAttackable(wolf, rider, current) || wolf.distanceToSqr(current) > scanRadius * scanRadius)) {
            wolf.setTarget(null);
            current = null;
        }
        double margin = getConfig().getRetargetMargin();
        double best = current != null ? wolf.distanceTo(current) - margin : Double.MAX_VALUE;
        LivingEntity nearest = null;
        for (LivingEntity candidate : wolf.level().getEntitiesOfClass(LivingEntity.class,
                wolf.getBoundingBox().inflate(scanRadius), e -> isThreatTo(wolf, rider, e))) {
            double distance = wolf.distanceTo(candidate);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        if (nearest != null && nearest != current) {
            wolf.setTarget(nearest);
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Mount {} switched to nearest threat {} at {} blocks",
                        wolf.getId(), nearest.getName().getString(), String.format("%.1f", best));
            }
        }
    }

    /**
     * Whether the mount may hold this entity as a target at all.
     *
     * <p>{@code Wolf.wantsToAttack} carries vanilla's own exclusions: creepers, ghasts and the
     * owner's other pets stay off the list.
     *
     * <p>Creepers are the one exclusion worth overriding, because vanilla's reasoning does not
     * survive contact with an armored mount. A pet wolf dies to the blast; a ridden one has the
     * armor pool to eat it and the damage to one-shot the creeper first — and the creeper is
     * walking at the rider anyway, so ignoring it does not avoid the explosion, it only guarantees
     * it. This covers modded creepers for free: Creeper Overhaul's whole family extends vanilla
     * {@code Creeper}, which is exactly why {@code wantsToAttack} rejected them.
     */
    private boolean isAttackable(Wolf wolf, Player rider, LivingEntity candidate) {
        if (candidate == rider || candidate == wolf || !candidate.isAlive()) {
            return false;
        }
        if (candidate instanceof Creeper) {
            return getConfig().isAttackCreepers();
        }
        return wolf.wantsToAttack(candidate, rider);
    }

    /**
     * Whether this entity counts as a threat worth sweeping for.
     *
     * <p>Two ways in, each with its own reach:
     * <ul>
     *   <li><b>Aggressors</b> — anything already targeting rider or mount — out to
     *       {@code defend_rider_radius}. That covers neutrals the rider picked a fight with.</li>
     *   <li><b>Hostiles that have not done anything yet</b>, out to {@code hostile_scan_radius}.
     *       This is the half that matters in practice: a tamed vanilla wolf only ever attacks
     *       skeletons unprompted (goal 7 of its target selector), so without this the mount stares
     *       right past the zombie standing in its face.</li>
     * </ul>
     *
     * <p>Passive and neutral bystanders are ignored no matter how close they stand, and bosses are
     * never picked up unprovoked — starting a fight with a Warden because it was the nearest thing
     * around is not a favour to the rider.
     */
    private boolean isThreatTo(Wolf wolf, Player rider, LivingEntity candidate) {
        if (!isAttackable(wolf, rider, candidate)) {
            return false;
        }
        if (candidate instanceof Mob mob && (mob.getTarget() == rider || mob.getTarget() == wolf)) {
            double defendRadius = getConfig().getDefendRiderRadius();
            return wolf.distanceToSqr(candidate) <= defendRadius * defendRadius;
        }
        if (!(candidate instanceof Enemy) || isBoss(candidate)) {
            return false;
        }
        double hostileRadius = getConfig().getHostileScanRadius();
        return hostileRadius > 0.0D && wolf.distanceToSqr(candidate) <= hostileRadius * hostileRadius;
    }

    /**
     * Bosses the mount must not pick a fight with on its own. They stay attackable once they have
     * attacked first — at that point the fight is happening either way.
     */
    private boolean isBoss(LivingEntity candidate) {
        EntityType<?> type = candidate.getType();
        return type == EntityType.WARDEN || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
    }
}
