package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class AxolotlGuardianConfig extends AbstractModuleConfig<AxolotlGuardianModule, AxolotlGuardianConfig> {

    private ModConfigSpec.DoubleValue associationRadius;
    private ModConfigSpec.IntValue fedDurationTicks;
    private ModConfigSpec.DoubleValue guardRadius;
    private ModConfigSpec.DoubleValue guardRadiusY;
    private ModConfigSpec.DoubleValue autoAssociateRadius;
    private ModConfigSpec.IntValue glowDurationSeconds;
    private ModConfigSpec.IntValue maxAxolotlsPerStation;
    private ModConfigSpec.IntValue axolotlXpCapacity;
    private ModConfigSpec.IntValue stationXpCapacity;
    private ModConfigSpec.IntValue xpPerBottle;
    private ModConfigSpec.IntValue playDeadMinHealth;
    private ModConfigSpec.DoubleValue healReturnThreshold;
    private ModConfigSpec.DoubleValue healRecoveryTarget;
    private ModConfigSpec.IntValue defaultUnbreakingLevel;
    private ModConfigSpec.IntValue defaultSharpnessLevel;
    private ModConfigSpec.IntValue defaultThornsLevel;
    private ModConfigSpec.DoubleValue thornsReflectFraction;

    public AxolotlGuardianConfig(AxolotlGuardianModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        associationRadius = builder
                .comment("Radius (blocks) in which owned axolotls are associated when shift-clicking a bowl")
                .defineInRange("association_radius", 64.0D, 1.0D, 128.0D);
        fedDurationTicks = builder
                .comment("How many ticks a single tropical fish keeps an axolotl in the 'fed' (guarding) state (20 ticks = 1 second)")
                .defineInRange("fed_duration_ticks", 6000, 20, 144000);
        guardRadius = builder
                .comment("Horizontal radius (blocks, XZ) around the associated bowl in which a fed axolotl scans for hostile mobs in water")
                .defineInRange("guard_radius", 32.0D, 2.0D, 128.0D);
        guardRadiusY = builder
                .comment("Vertical radius (blocks, Y) around the associated bowl — axolotls ignore mobs further away vertically")
                .defineInRange("guard_radius_y", 16.0D, 2.0D, 128.0D);
        autoAssociateRadius = builder
                .comment("Radius (blocks) within which an axolotl without a bowl is automatically associated with a nearby bowl")
                .defineInRange("auto_associate_radius", 1.5D, 0.5D, 4.0D);
        glowDurationSeconds = builder
                .comment("How many seconds your own associated axolotls glow (client-side only) when looking at their bowl (1–300)")
                .defineInRange("glow_duration_seconds", 30, 1, 300);
        maxAxolotlsPerStation = builder
                .comment("Maximum number of axolotls that can be associated with a single bowl or station")
                .defineInRange("max_axolotls_per_station", 8, 1, 64);
        axolotlXpCapacity = builder
                .comment("Maximum XP points a single axolotl can hold before XP drops normally")
                .defineInRange("axolotl_xp_capacity", 500, 0, 10000);
        stationXpCapacity = builder
                .comment("Maximum XP points a feeding station can hold before overflow stays on axolotls")
                .defineInRange("station_xp_capacity", 5000, 0, 100000);
        xpPerBottle = builder
                .comment("XP points consumed per Bottle o' Enchanting produced at the station")
                .defineInRange("xp_per_bottle", 8, 1, 64);
        playDeadMinHealth = builder
                .comment("An ARMORED guardian axolotl may only play dead once its health drops to "
                        + "this value or below (absolute HP; axolotl max health is 14). Keeps an "
                        + "armored guardian from flopping out of the fight — and from getting the "
                        + "play-dead self-Regeneration — while healthy. In practice this only kicks "
                        + "in after its armor breaks (armor absorbs 100% of damage). Unarmored "
                        + "axolotls keep vanilla play-dead.")
                .defineInRange("play_dead_min_health", 4, 1, 14);
        healReturnThreshold = builder
                .comment("A fed, out-of-combat guardian axolotl whose health drops below this "
                        + "fraction of its max HP proactively swims back to its bowl/station to "
                        + "heal. Sits above the hardcoded emergency-flee threshold (0.20).")
                .defineInRange("heal_return_threshold", 0.60D, 0.0D, 1.0D);
        healRecoveryTarget = builder
                .comment("While at/near its home block, a guardian axolotl keeps regenerating "
                        + "until its health reaches this fraction of its max HP.")
                .defineInRange("heal_recovery_target", 1.0D, 0.0D, 1.0D);
        defaultUnbreakingLevel = builder
                .comment("Default Unbreaking level baked onto freshly crafted axolotl armor "
                        + "(0 = none). Unbreaking works natively and extends armor durability.")
                .defineInRange("default_unbreaking_level", 3, 0, 10);
        defaultSharpnessLevel = builder
                .comment("Default Sharpness level baked onto freshly crafted axolotl armor "
                        + "(0 = none). Adds bonus outgoing damage when the axolotl attacks a mob.")
                .defineInRange("default_sharpness_level", 2, 0, 10);
        defaultThornsLevel = builder
                .comment("Default Thorns level baked onto freshly crafted axolotl armor "
                        + "(0 = none). Reflects a share of incoming damage back to the attacker.")
                .defineInRange("default_thorns_level", 2, 0, 10);
        thornsReflectFraction = builder
                .comment("Base fraction of absorbed damage reflected back to the attacker, scaled "
                        + "by the armor's Thorns level (0.0 = none, 1.0 = full).")
                .defineInRange("thorns_reflect_fraction", 0.33D, 0.0D, 1.0D);
    }

    public double getAssociationRadius() {
        return associationRadius != null ? associationRadius.get() : 64.0D;
    }

    public int getFedDurationTicks() {
        return fedDurationTicks != null ? fedDurationTicks.get() : 6000;
    }

    public double getGuardRadius() {
        return guardRadius != null ? guardRadius.get() : 32.0D;
    }

    public double getGuardRadiusY() {
        return guardRadiusY != null ? guardRadiusY.get() : 16.0D;
    }

    public double getAutoAssociateRadius() {
        return autoAssociateRadius != null ? autoAssociateRadius.get() : 1.5D;
    }

    public int getGlowDurationTicks() {
        return glowDurationSeconds != null ? glowDurationSeconds.get() * 20 : 600;
    }

    public int getMaxAxolotlsPerStation() {
        return maxAxolotlsPerStation != null ? maxAxolotlsPerStation.get() : 8;
    }

    public int getAxolotlXpCapacity() {
        return axolotlXpCapacity != null ? axolotlXpCapacity.get() : 500;
    }

    public int getStationXpCapacity() {
        return stationXpCapacity != null ? stationXpCapacity.get() : 5000;
    }

    public int getXpPerBottle() {
        return xpPerBottle != null ? xpPerBottle.get() : 8;
    }

    public int getPlayDeadMinHealth() {
        return playDeadMinHealth != null ? playDeadMinHealth.get() : 4;
    }

    public double getHealReturnThreshold() {
        return healReturnThreshold != null ? healReturnThreshold.get() : 0.60D;
    }

    public double getHealRecoveryTarget() {
        return healRecoveryTarget != null ? healRecoveryTarget.get() : 1.0D;
    }

    public int getDefaultUnbreakingLevel() {
        return defaultUnbreakingLevel != null ? defaultUnbreakingLevel.get() : 3;
    }

    public int getDefaultSharpnessLevel() {
        return defaultSharpnessLevel != null ? defaultSharpnessLevel.get() : 2;
    }

    public int getDefaultThornsLevel() {
        return defaultThornsLevel != null ? defaultThornsLevel.get() : 2;
    }

    public double getThornsReflectFraction() {
        return thornsReflectFraction != null ? thornsReflectFraction.get() : 0.33D;
    }
}
