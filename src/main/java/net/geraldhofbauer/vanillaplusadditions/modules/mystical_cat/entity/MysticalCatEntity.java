package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity;

import net.geraldhofbauer.vanillaplusadditions.mixin.mystical_cat.CatRelaxStateInvoker;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The Mystical Cat — a special, immovable cat that sleeps at generated "cat spots" and starts a
 * random mini-game when right-clicked. It is a {@link Cat} subclass (so Fresh Animations / EMF pick
 * up its cat geometry) but with its own {@link EntityType}, so the Cat Guardian module — which gates
 * on {@code getType() == EntityType.CAT} — never treats it as a guardable pet.
 *
 * <p>The cat is invulnerable to everything except {@code /kill} (which bypasses invulnerability),
 * never wanders, never despawns, cannot be pushed, leashed, tamed, bred or fed. The only AI goal it
 * keeps is looking at the nearest player. All interaction is dispatched to
 * {@link MysticalCatModule#handleCatInteract}.
 */
public class MysticalCatEntity extends Cat {

    private static final String TAG_COOLDOWN_UNTIL = "CooldownUntil";
    private static final String TAG_SPOT_CENTER = "SpotCenter";

    /** Game time (level day-time ticks) until which this cat refuses to start a new game. */
    private long cooldownUntil;
    /** The center of the cat spot this cat belongs to (worldgen/command spots), or null. */
    @Nullable
    private BlockPos spotCenter;

    /**
     * When true this cat is a temporary "ghost" escort (Ghost Escort mini-game): it is allowed to
     * navigate along a path instead of resting in place. Not persisted — ghosts never survive a
     * reload (their game is aborted on load).
     */
    private boolean ghost;

    public MysticalCatEntity(EntityType<? extends MysticalCatEntity> entityType, Level level) {
        super(entityType, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // Deliberately NOT calling super: a Mystical Cat must never stroll, follow, flee, be
        // tempted, breed or sit-on-blocks. The only behaviour it keeps is turning its head toward
        // the nearest player, which reads as the cat "watching" whoever approaches.
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F));
    }

    @Override
    protected void reassessTameGoals() {
        // No-op: the vanilla implementation would add an "avoid players" flee goal while untamed.
        // The Mystical Cat is never tamed and must stay put, so it gets no such goal.
    }

    // ---- Immovability / immortality ----

    @Override
    public boolean isPushable() {
        // Entity.push() checks isPushable() on both sides, so returning false blocks both being
        // shoved by others and shoving others (piston shove is handled by the fixed position).
        return false;
    }

    @Override
    protected void pushEntities() {
        // Skip the per-tick neighbour scan entirely — this cat never pushes anything.
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- Pose helpers (Fresh Animations / vanilla read these flags) ----

    /** Curled-up sleeping pose (lying + relaxed), as a cat resting on its owner's bed. */
    public void setSleepingPose() {
        this.setOrderedToSit(false);
        this.setInSittingPose(false);
        this.setLying(true);
        relaxStateInvoker().callSetRelaxStateOne(true);
    }

    /** Upright sitting pose, adopted when a game starts. */
    public void setSittingPose() {
        this.setLying(false);
        relaxStateInvoker().callSetRelaxStateOne(false);
        this.setInSittingPose(true);
    }

    private CatRelaxStateInvoker relaxStateInvoker() {
        return (CatRelaxStateInvoker) (Object) this;
    }

    // ---- Interaction: hand everything to the module (game start / trade / cooldown message) ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // No super call: skip all vanilla cat interaction (feeding, taming, dyeing, sit toggle).
        return MysticalCatModule.handleCatInteract(this, player, hand);
    }

    // ---- State accessors ----

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    @Nullable
    public BlockPos getSpotCenter() {
        return spotCenter;
    }

    public void setSpotCenter(@Nullable BlockPos spotCenter) {
        this.spotCenter = spotCenter;
    }

    public boolean isGhost() {
        return ghost;
    }

    public void setGhost(boolean ghost) {
        this.ghost = ghost;
    }

    // ---- Persistence ----

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putLong(TAG_COOLDOWN_UNTIL, cooldownUntil);
        if (spotCenter != null) {
            compound.putLong(TAG_SPOT_CENTER, spotCenter.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        cooldownUntil = compound.getLong(TAG_COOLDOWN_UNTIL);
        if (compound.contains(TAG_SPOT_CENTER)) {
            spotCenter = BlockPos.of(compound.getLong(TAG_SPOT_CENTER));
        }
    }
}
