package net.geraldhofbauer.vanillaplusadditions.modules.pet_potions;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.mixin.pet_potions.AreaEffectCloudAccessor;
import net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.config.PetPotionsConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.models.CalmingCloud;
import net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.models.PeaceEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pet Potions Module
 *
 * <p>Lets a thrown healing potion serve as an apology. If you hit someone else's tamed animal it
 * turns on you, and vanilla offers no way back: {@code TamableAnimal#canAttack} only exempts the
 * animal's <em>own</em> owner, so for everybody else {@code HurtByTargetGoal} applies normally. The
 * only vanilla appeasement is {@code NeutralMob#playerDied} — you have to die. This module adds the
 * missing option: splash or lob a healing/regeneration potion at the animal and it forgives you.
 *
 * <p>It also fixes the reason that throw usually fails. {@code Wolf#mobInteract} returns
 * {@code CONSUME} on the client for <em>any</em> tamed wolf (the check is {@code isTame()}, not
 * {@code isOwnedBy(player)}), which makes {@code Minecraft#startUseItem} bail out before the
 * use-item packet is ever sent — the potion never leaves your hand even though the server would
 * have let it through.
 */
public class PetPotionsModule extends AbstractModule<PetPotionsModule, PetPotionsConfig> {

    /** How often the lingering-cloud and grace-period bookkeeping runs, in ticks. */
    private static final int SWEEP_INTERVAL_TICKS = 10;

    /** Splash radius box, mirroring {@code ThrownPotion#applySplash}. */
    private static final double SPLASH_INFLATE_HORIZONTAL = 4.0D;

    /** Splash radius box height, mirroring {@code ThrownPotion#applySplash}. */
    private static final double SPLASH_INFLATE_VERTICAL = 2.0D;

    /** Squared splash cut-off distance, mirroring {@code ThrownPotion#applySplash}. */
    private static final double SPLASH_RANGE_SQR = 16.0D;

    /** Pets currently protected from re-targeting a specific player, keyed by the pet's UUID. */
    private final Map<UUID, PeaceEntry> peaceWindows = new HashMap<>();

    /** Lingering clouds that carry a calming effect, keyed by the cloud's UUID. */
    private final Map<UUID, CalmingCloud> calmingClouds = new HashMap<>();

    private int tickCounter;

    /**
     * Creates the Pet Potions module.
     */
    public PetPotionsModule() {
        super("pet_potions",
                "Pet Potions",
                "Throw a healing or regeneration potion at an angry tamed animal to make it forgive you, "
                        + "and let beneficial thrown potions actually be aimed at pets",
                PetPotionsConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Pet Potions module initialized - healing potions now count as an apology");
    }

    // ------------------------------------------------------------------------------------------
    // Throwing beneficial potions at pets
    // ------------------------------------------------------------------------------------------

    /**
     * Lets a beneficial thrown potion pass through the pet interaction (generic right-click).
     *
     * @param event the interaction event
     */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (shouldPassThrough(event.getTarget(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.PASS);
            event.setCanceled(true);
        }
    }

    /**
     * Lets a beneficial thrown potion pass through the pet interaction (positional right-click).
     *
     * @param event the interaction event
     */
    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (shouldPassThrough(event.getTarget(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.PASS);
            event.setCanceled(true);
        }
    }

    /**
     * Decides whether a right-click on an entity should be handed back to the normal item use.
     *
     * <p>Only splash and lingering potions qualify — drinkable potions are deliberately left alone so
     * that holding an ordinary healing potion does not stop you from telling your wolf to sit.
     */
    private boolean shouldPassThrough(Entity target, ItemStack stack) {
        if (!isModuleEnabled() || !getConfig().isThrowingAtPetsAllowed()) {
            return false;
        }
        if (!isOwnedPet(target)) {
            return false;
        }
        if (!(stack.getItem() instanceof SplashPotionItem) && !(stack.getItem() instanceof LingeringPotionItem)) {
            return false;
        }
        return isBeneficial(potionContentsOf(stack));
    }

    // ------------------------------------------------------------------------------------------
    // Calming
    // ------------------------------------------------------------------------------------------

    /**
     * Calms owned animals caught in the splash of a thrown potion.
     *
     * <p>Fired from {@code ThrowableProjectile#tick} immediately before {@code onHit}, so the potion
     * still sits at the exact position vanilla's own {@code applySplash} will use.
     *
     * @param event the projectile impact event (never cancelled here, only observed)
     */
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getProjectile() instanceof ThrownPotion potion) || potion.level().isClientSide()) {
            return;
        }
        // Lingering potions are handled through the cloud they spawn, see onEntityJoinLevel.
        if (potion.getItem().getItem() instanceof LingeringPotionItem) {
            return;
        }
        if (!(potion.getOwner() instanceof Player thrower)) {
            return;
        }
        if (!isCalming(potionContentsOf(potion.getItem()))) {
            return;
        }

        AABB box = potion.getBoundingBox().inflate(SPLASH_INFLATE_HORIZONTAL, SPLASH_INFLATE_VERTICAL,
                SPLASH_INFLATE_HORIZONTAL);
        for (LivingEntity living : potion.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (potion.distanceToSqr(living) < SPLASH_RANGE_SQR) {
                calm(living, thrower);
            }
        }
    }

    /**
     * Starts tracking lingering clouds that carry a calming effect.
     *
     * @param event the entity join event
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isModuleEnabled() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof AreaEffectCloud cloud)) {
            return;
        }
        if (!(cloud.getOwner() instanceof Player thrower)) {
            return;
        }
        if (!isCalming(((AreaEffectCloudAccessor) cloud).getPotionContents())) {
            return;
        }
        calmingClouds.put(cloud.getUUID(), new CalmingCloud(event.getLevel().dimension(), thrower.getUUID()));
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Tracking calming lingering cloud {} thrown by {}", cloud.getUUID(), thrower.getName().getString());
        }
    }

    /**
     * Drops a tracked cloud as soon as it leaves the level.
     *
     * @param event the entity leave event
     */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (calmingClouds.isEmpty() || !(event.getEntity() instanceof AreaEffectCloud cloud)) {
            return;
        }
        calmingClouds.remove(cloud.getUUID());
    }

    /**
     * Periodically re-applies calming inside tracked lingering clouds and expires grace periods.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (++tickCounter % SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        expirePeaceWindows(server.overworld().getGameTime());
        sweepCalmingClouds(server);
    }

    private void expirePeaceWindows(long now) {
        if (peaceWindows.isEmpty()) {
            return;
        }
        peaceWindows.values().removeIf(entry -> entry.expiresAt() <= now);
    }

    private void sweepCalmingClouds(MinecraftServer server) {
        if (calmingClouds.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, CalmingCloud>> iterator = calmingClouds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CalmingCloud> entry = iterator.next();
            CalmingCloud tracked = entry.getValue();
            ServerLevel level = server.getLevel(tracked.dimension());
            if (level == null || !(level.getEntity(entry.getKey()) instanceof AreaEffectCloud cloud) || !cloud.isAlive()) {
                iterator.remove();
                continue;
            }
            Player thrower = level.getPlayerByUUID(tracked.thrower());
            if (thrower == null) {
                continue;
            }
            calmInsideCloud(level, cloud, thrower);
        }
    }

    /**
     * Calms owned animals standing inside a lingering cloud, mirroring {@code AreaEffectCloud#tick}'s
     * own cylindrical range check.
     */
    private void calmInsideCloud(ServerLevel level, AreaEffectCloud cloud, Player thrower) {
        float radius = cloud.getRadius();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox())) {
            double dx = living.getX() - cloud.getX();
            double dz = living.getZ() - cloud.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                calm(living, thrower);
            }
        }
    }

    /**
     * Makes a single animal forgive the thrower, if it is an owned pet that is currently angry at
     * exactly that player.
     *
     * <p>Deliberately targeted: anger towards third parties is left untouched, so you buy off your own
     * mistake rather than pacifying somebody else's guard animal wholesale.
     */
    private void calm(LivingEntity living, Player thrower) {
        if (!(living instanceof Mob mob) || !isOwnedPet(mob)) {
            return;
        }

        boolean calmed = false;
        if (mob instanceof NeutralMob neutral && thrower.getUUID().equals(neutral.getPersistentAngerTarget())) {
            neutral.stopBeingAngry();
            calmed = true;
        }
        if (mob.getTarget() == thrower) {
            mob.setTarget(null);
            calmed = true;
        }
        // Left over from the hit that started this; HurtByTargetGoal would pick it straight back up.
        if (mob.getLastHurtByMob() == thrower) {
            mob.setLastHurtByMob(null);
            calmed = true;
        }
        if (!calmed) {
            return;
        }

        int duration = getConfig().getPeaceDurationTicksValue();
        if (duration > 0) {
            peaceWindows.put(mob.getUUID(), new PeaceEntry(thrower.getUUID(), mob.level().getGameTime() + duration));
        }
        if (getConfig().isCalmFeedbackEnabled()) {
            playCalmFeedback(mob);
        }
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Calmed {} ({}) towards {}", mob.getName().getString(), mob.getUUID(),
                    thrower.getName().getString());
        }
    }

    private void playCalmFeedback(Mob mob) {
        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() * 0.75D,
                    mob.getZ(), 4, 0.3D, 0.2D, 0.3D, 0.0D);
        }
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 0.7f, 1.5f);
    }

    // ------------------------------------------------------------------------------------------
    // Grace period
    // ------------------------------------------------------------------------------------------

    /**
     * Blocks a freshly calmed pet from taking the thrower as its target again for a short while.
     *
     * <p>Clearing the anger once is not enough on its own: a goal can re-fire off a stale
     * {@code lastHurtByMobTimestamp}, and AI overhaul mods set targets past vanilla's goals entirely.
     * Cancelling here catches all of them, because it hooks {@code Mob#setTarget} itself.
     *
     * @param event the target change event
     */
    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (!isModuleEnabled() || peaceWindows.isEmpty()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player)) {
            return;
        }
        PeaceEntry peace = peaceWindows.get(entity.getUUID());
        if (peace == null || !peace.player().equals(player.getUUID())) {
            return;
        }
        if (entity.level().getGameTime() >= peace.expiresAt()) {
            peaceWindows.remove(entity.getUUID());
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Drops all bookkeeping when a world is left, so nothing carries over into the next one.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        peaceWindows.clear();
        calmingClouds.clear();
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Whether an entity is a tamed/owned animal. Uses {@link OwnableEntity} rather than
     * {@code TamableAnimal} so horses, donkeys, mules and llamas — which are not tamables but do carry
     * an owner — and modded pets are covered as well.
     */
    private static boolean isOwnedPet(@Nullable Entity entity) {
        return entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }

    private static PotionContents potionContentsOf(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    /** A potion counts as beneficial as long as none of its effects is harmful. */
    private static boolean isBeneficial(PotionContents contents) {
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                return false;
            }
        }
        return true;
    }

    /** Whether the potion carries at least one of the configured calming effects. */
    private boolean isCalming(PotionContents contents) {
        Set<String> calmingIds = getConfig().getCalmingEffectIds();
        if (calmingIds.isEmpty()) {
            return false;
        }
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (instance.getEffect().unwrapKey()
                    .map(key -> calmingIds.contains(key.location().toString()))
                    .orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exposes the currently tracked grace periods, for debugging and tests.
     *
     * @return an unmodifiable snapshot of the protected pets
     */
    public List<UUID> getProtectedPets() {
        return List.copyOf(peaceWindows.keySet());
    }
}
