package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.WolfMountModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Wolf Mount module.
 *
 * <p>All getters are null-safe and fall back to the literal default, because config values are
 * read on paths that can run before the spec is loaded.
 *
 * <p><b>Multiplayer note:</b> this is a COMMON config and NeoForge does not sync it. {@code enabled}
 * and the movement multipliers must match on client and server — see {@code docs/wolf_mount.md}.
 */
public class WolfMountConfig extends AbstractModuleConfig<WolfMountModule, WolfMountConfig> {

    private static final double DEFAULT_MIN_SCALE = 2.0D;
    private static final double DEFAULT_SPEED_MULTIPLIER = 1.0D;
    private static final double DEFAULT_STRAFE_MULTIPLIER = 0.5D;
    private static final double DEFAULT_BACKWARD_MULTIPLIER = 0.25D;
    private static final double DEFAULT_JUMP_STRENGTH = 1.6D;
    private static final double DEFAULT_MIN_JUMP_CHARGE = 0.15D;
    private static final double DEFAULT_DEFEND_RADIUS = 32.0D;
    private static final double DEFAULT_REACH_BONUS = 2.0D;
    private static final double DEFAULT_RETARGET_MARGIN = 3.0D;
    private static final int DEFAULT_TARGET_RECHECK_TICKS = 10;
    private static final double DEFAULT_HOSTILE_SCAN_RADIUS = 16.0D;
    private static final double DEFAULT_ARMOR_WARNING_THRESHOLD = 0.25D;
    private static final int DEFAULT_RECHECK_TICKS = 20;

    private ModConfigSpec.BooleanValue requireLargeScale;
    private ModConfigSpec.DoubleValue minScale;
    private ModConfigSpec.BooleanValue requireTamedOwner;
    private ModConfigSpec.BooleanValue requireBodyArmor;

    private ModConfigSpec.DoubleValue speedMultiplier;
    private ModConfigSpec.DoubleValue strafeMultiplier;
    private ModConfigSpec.DoubleValue backwardMultiplier;
    private ModConfigSpec.DoubleValue jumpStrength;
    private ModConfigSpec.DoubleValue minJumpCharge;
    private ModConfigSpec.BooleanValue instantJump;
    private ModConfigSpec.BooleanValue floatInWater;

    private ModConfigSpec.BooleanValue ownerDamageImmunity;
    private ModConfigSpec.BooleanValue ownerImmunityRequiresArmor;
    private ModConfigSpec.BooleanValue ownerImmunityOnlyWhenRidden;
    private ModConfigSpec.BooleanValue suppressRiddenKnockback;

    private ModConfigSpec.BooleanValue defendRider;
    private ModConfigSpec.DoubleValue defendRiderRadius;
    private ModConfigSpec.BooleanValue targetNearest;
    private ModConfigSpec.DoubleValue retargetMargin;
    private ModConfigSpec.IntValue targetRecheckTicks;
    private ModConfigSpec.DoubleValue hostileScanRadius;
    private ModConfigSpec.BooleanValue attackCreepers;

    private ModConfigSpec.BooleanValue showArmorBar;
    private ModConfigSpec.BooleanValue compactMountHealth;
    private ModConfigSpec.DoubleValue armorWarningThreshold;

    private ModConfigSpec.DoubleValue riderReachBonus;
    private ModConfigSpec.BooleanValue dismountWhenArmorRemoved;
    private ModConfigSpec.IntValue eligibilityRecheckTicks;

    public WolfMountConfig(WolfMountModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        builder.comment("Which wolves can be ridden at all.").push("eligibility");
        requireLargeScale = builder
                .comment("Only wolves scaled up via the vanilla generic.scale attribute can be ridden.")
                .define("require_large_scale", true);
        minScale = builder
                .comment("Minimum generic.scale a wolf needs to be rideable. Vanilla wolves are 1.0;",
                        "the \"Sif\" wolf from Grim Kingdoms is 3.25.")
                .defineInRange("min_scale", DEFAULT_MIN_SCALE, 1.0D, 64.0D);
        requireTamedOwner = builder
                .comment("Only a wolf that is tamed and belongs to the rider can be ridden.")
                .define("require_tamed_owner", true);
        requireBodyArmor = builder
                .comment("Only a wolf wearing body armor can be ridden. Both vanilla minecraft:wolf_armor",
                        "and this mod's Battle Dogs armor count (anything registered as canine body armor).")
                .define("require_body_armor", true);
        builder.pop();

        builder.comment("How the mount handles.").push("movement");
        speedMultiplier = builder
                .comment("Multiplies the wolf's movement speed while ridden.",
                        "MUST match between client and server — the client is authoritative for a",
                        "ridden entity, so a mismatch makes the server's anti-cheat rubber-band you.")
                .defineInRange("speed_multiplier", DEFAULT_SPEED_MULTIPLIER, 0.1D, 3.0D);
        strafeMultiplier = builder
                .comment("Sideways input scaling (vanilla horses use 0.5).")
                .defineInRange("strafe_multiplier", DEFAULT_STRAFE_MULTIPLIER, 0.0D, 1.0D);
        backwardMultiplier = builder
                .comment("Backwards input scaling (vanilla horses use 0.25).")
                .defineInRange("backward_multiplier", DEFAULT_BACKWARD_MULTIPLIER, 0.0D, 1.0D);
        jumpStrength = builder
                .comment("Multiplies the jump power.",
                        "A wolf's generic.jump_strength is 0.42 — the same as a player's — so an",
                        "unmultiplied jump is a one-block hop, which looks ridiculous under a mount",
                        "three times the size of a horse. The default puts a full charge at roughly",
                        "2.5 blocks — a solid mount jump without the catapult feel of a maxed horse.")
                .defineInRange("jump_strength", DEFAULT_JUMP_STRENGTH, 0.0D, 8.0D);
        minJumpCharge = builder
                .comment("Jump scale at the shortest possible tap, as a fraction of a full charge.",
                        "Vanilla horses use 0.4, which makes the charge meter nearly pointless;",
                        "lower values give the meter a much wider, more noticeable range.")
                .defineInRange("min_jump_charge", DEFAULT_MIN_JUMP_CHARGE, 0.0D, 1.0D);
        instantJump = builder
                .comment("Jump at full power on tap instead of charging the meter by holding the key.")
                .define("instant_jump", false);
        floatInWater = builder
                .comment("Keep the mount swimming at the surface instead of sinking.",
                        "Needed because the vanilla FloatGoal that makes a loose wolf swim only runs",
                        "server-side, while a ridden entity is driven by the client.")
                .define("float_in_water", true);
        builder.pop();

        builder.comment("Protecting the mount from its own owner.").push("protection");
        ownerDamageImmunity = builder
                .comment("A rideable wolf takes no damage from the player that owns it.",
                        "This also covers sweeping-edge splash damage, which is the usual way to",
                        "accidentally kill your own wolf.")
                .define("owner_damage_immunity", true);
        ownerImmunityRequiresArmor = builder
                .comment("Narrow the immunity to wolves that actually wear body armor.")
                .define("owner_immunity_requires_armor", false);
        ownerImmunityOnlyWhenRidden = builder
                .comment("Narrow the immunity to the wolf currently being ridden.")
                .define("owner_immunity_only_when_ridden", false);
        suppressRiddenKnockback = builder
                .comment("Suppress knockback on a wolf that is being ridden. Without this a sweep",
                        "attack still shoves the mount around: vanilla applies the knockback before",
                        "the damage, so cancelling the damage cannot undo it.")
                .define("suppress_ridden_knockback", true);
        builder.pop();

        builder.comment("Fighting from the saddle.").push("combat");
        defendRider = builder
                .comment("The mount attacks whatever attacks its rider, and takes over the rider's",
                        "own target when the rider strikes something.")
                .define("defend_rider", true);
        defendRiderRadius = builder
                .comment("Maximum distance between mount and attacker for that to trigger.",
                        "Vanilla's own owner-defence goal is capped at follow range (16), which is",
                        "why a separate radius exists here.")
                .defineInRange("defend_rider_radius", DEFAULT_DEFEND_RADIUS, 0.0D, 128.0D);
        targetNearest = builder
                .comment("Keep the mount on the NEAREST threat instead of the first one it locked",
                        "onto. Without this the mount stays on a distant archer while a zombie is",
                        "chewing on the rider's leg, because vanilla never re-picks a live target.",
                        "Only mobs that are already hostile towards rider or mount count as threats,",
                        "so the mount still never picks a fight with the local cows.")
                .define("target_nearest", true);
        retargetMargin = builder
                .comment("How much closer (in blocks) a new threat has to be before the mount",
                        "switches to it. Pure hysteresis: without a margin two mobs at nearly the",
                        "same distance would make it flip-flop every recheck and bite neither.")
                .defineInRange("retarget_margin", DEFAULT_RETARGET_MARGIN, 0.0D, 32.0D);
        targetRecheckTicks = builder
                .comment("How often (in ticks) the mount re-picks the nearest threat while ridden.")
                .defineInRange("target_recheck_ticks", DEFAULT_TARGET_RECHECK_TICKS, 1, 100);
        hostileScanRadius = builder
                .comment("How far the mount looks for hostile mobs that have NOT attacked yet.",
                        "A tamed vanilla wolf only ever picks fights with skeletons, so without this",
                        "the mount ignores the zombie standing in its face. Aggressors that already",
                        "target rider or mount are picked up out to defend_rider_radius instead.",
                        "Set to 0 to only ever retaliate. Bosses are never attacked unprovoked.")
                .defineInRange("hostile_scan_radius", DEFAULT_HOSTILE_SCAN_RADIUS, 0.0D, 64.0D);
        attackCreepers = builder
                .comment("Let the mount attack creepers. Vanilla's Wolf.wantsToAttack refuses them",
                        "outright -- sensible for a pet that dies to the blast, silly for an armored",
                        "mount that one-shots them. Covers modded creepers too (Creeper Overhaul's",
                        "all extend vanilla Creeper). Applies only to a wolf being ridden; loose pets",
                        "keep vanilla's caution. Turn off if you would rather not risk the blast.")
                .define("attack_creepers", true);
        riderReachBonus = builder
                .comment("Extra entity interaction range while mounted. Sitting on a scale-3.25 wolf",
                        "raises your eyes about 2.7 blocks, so the vanilla 3-block reach no longer",
                        "gets you to the ground.")
                .defineInRange("rider_reach_bonus", DEFAULT_REACH_BONUS, 0.0D, 8.0D);
        builder.pop();

        builder.comment("What the rider sees on screen. Read client-side only, so this section",
                        "may safely differ between client and server.").push("hud");
        showArmorBar = builder
                .comment("Show the mount's body armor durability while riding. This is the number",
                        "that actually matters: the armor absorbs 100% of the damage, so the wolf's",
                        "health does not move at all until the armor breaks -- and when it does, the",
                        "rider is thrown off (see dismount_when_armor_removed).")
                .define("show_armor_bar", true);
        compactMountHealth = builder
                .comment("Replace vanilla's mount health bar with a single ten-heart row showing the",
                        "mount's health as a fraction. Vanilla draws one heart per 2 HP capped at 30",
                        "hearts, so a 350 HP wolf fills three rows that never visibly move.")
                .define("compact_mount_health", true);
        armorWarningThreshold = builder
                .comment("Below this fraction of remaining armor durability the bar pulses red and",
                        "the rider gets a one-off action bar warning. 0 disables the warning.")
                .defineInRange("armor_warning_threshold", DEFAULT_ARMOR_WARNING_THRESHOLD, 0.0D, 1.0D);
        builder.pop();

        dismountWhenArmorRemoved = builder
                .comment("Eject the rider the moment the mount's body armor is removed or breaks.")
                .define("dismount_when_armor_removed", true);
        eligibilityRecheckTicks = builder
                .comment("How often (in ticks) an ongoing ride is re-checked against the rules above.")
                .defineInRange("eligibility_recheck_ticks", DEFAULT_RECHECK_TICKS, 1, 200);
    }

    public boolean isRequireLargeScale() {
        return requireLargeScale == null || requireLargeScale.get();
    }

    public double getMinScale() {
        return minScale != null ? minScale.get() : DEFAULT_MIN_SCALE;
    }

    public boolean isRequireTamedOwner() {
        return requireTamedOwner == null || requireTamedOwner.get();
    }

    public boolean isRequireBodyArmor() {
        return requireBodyArmor == null || requireBodyArmor.get();
    }

    public double getSpeedMultiplier() {
        return speedMultiplier != null ? speedMultiplier.get() : DEFAULT_SPEED_MULTIPLIER;
    }

    public double getStrafeMultiplier() {
        return strafeMultiplier != null ? strafeMultiplier.get() : DEFAULT_STRAFE_MULTIPLIER;
    }

    public double getBackwardMultiplier() {
        return backwardMultiplier != null ? backwardMultiplier.get() : DEFAULT_BACKWARD_MULTIPLIER;
    }

    public double getJumpStrength() {
        return jumpStrength != null ? jumpStrength.get() : DEFAULT_JUMP_STRENGTH;
    }

    public double getMinJumpCharge() {
        return minJumpCharge != null ? minJumpCharge.get() : DEFAULT_MIN_JUMP_CHARGE;
    }

    public boolean isInstantJump() {
        return instantJump != null && instantJump.get();
    }

    public boolean isFloatInWater() {
        return floatInWater == null || floatInWater.get();
    }

    public boolean isOwnerDamageImmunity() {
        return ownerDamageImmunity == null || ownerDamageImmunity.get();
    }

    public boolean isOwnerImmunityRequiresArmor() {
        return ownerImmunityRequiresArmor != null && ownerImmunityRequiresArmor.get();
    }

    public boolean isOwnerImmunityOnlyWhenRidden() {
        return ownerImmunityOnlyWhenRidden != null && ownerImmunityOnlyWhenRidden.get();
    }

    public boolean isSuppressRiddenKnockback() {
        return suppressRiddenKnockback == null || suppressRiddenKnockback.get();
    }

    public boolean isDefendRider() {
        return defendRider == null || defendRider.get();
    }

    public double getDefendRiderRadius() {
        return defendRiderRadius != null ? defendRiderRadius.get() : DEFAULT_DEFEND_RADIUS;
    }

    public boolean isTargetNearest() {
        return targetNearest == null || targetNearest.get();
    }

    public double getRetargetMargin() {
        return retargetMargin != null ? retargetMargin.get() : DEFAULT_RETARGET_MARGIN;
    }

    public boolean isAttackCreepers() {
        return attackCreepers == null || attackCreepers.get();
    }

    public double getHostileScanRadius() {
        return hostileScanRadius != null ? hostileScanRadius.get() : DEFAULT_HOSTILE_SCAN_RADIUS;
    }

    public int getTargetRecheckTicks() {
        return targetRecheckTicks != null ? targetRecheckTicks.get() : DEFAULT_TARGET_RECHECK_TICKS;
    }

    public boolean isShowArmorBar() {
        return showArmorBar == null || showArmorBar.get();
    }

    public boolean isCompactMountHealth() {
        return compactMountHealth == null || compactMountHealth.get();
    }

    public double getArmorWarningThreshold() {
        return armorWarningThreshold != null ? armorWarningThreshold.get() : DEFAULT_ARMOR_WARNING_THRESHOLD;
    }

    public double getRiderReachBonus() {
        return riderReachBonus != null ? riderReachBonus.get() : DEFAULT_REACH_BONUS;
    }

    public boolean isDismountWhenArmorRemoved() {
        return dismountWhenArmorRemoved == null || dismountWhenArmorRemoved.get();
    }

    public int getEligibilityRecheckTicks() {
        return eligibilityRecheckTicks != null ? eligibilityRecheckTicks.get() : DEFAULT_RECHECK_TICKS;
    }
}
