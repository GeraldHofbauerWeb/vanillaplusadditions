package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.config.WolfMountConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.network.MountWolfPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
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
     * Periodic upkeep: ends a ride whose preconditions no longer hold, and heals a leaked reach
     * modifier (logout while mounted, dimension change, death, a crash mid-ride).
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
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
     * Points the mount at a new target, keeping vanilla's own exclusions intact and never yanking
     * it off a still-living target it is already busy with.
     */
    private void retarget(Wolf wolf, Player rider, LivingEntity target) {
        if (target == rider || target == wolf || !target.isAlive()) {
            return;
        }
        // Wolf.wantsToAttack keeps creepers, ghasts and the owner's other pets off the list.
        if (!wolf.wantsToAttack(target, rider)) {
            return;
        }
        double radius = getConfig().getDefendRiderRadius();
        if (wolf.distanceToSqr(target) > radius * radius) {
            return;
        }
        LivingEntity current = wolf.getTarget();
        if (current == null || !current.isAlive()) {
            wolf.setTarget(target);
        }
    }
}
