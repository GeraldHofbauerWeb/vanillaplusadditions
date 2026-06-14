package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CatGuardianConfig extends AbstractModuleConfig<CatGuardianModule, CatGuardianConfig> {

    private ModConfigSpec.DoubleValue associationRadius;
    private ModConfigSpec.IntValue fedDurationTicks;
    private ModConfigSpec.DoubleValue guardRadius;
    private ModConfigSpec.DoubleValue guardRadiusY;
    private ModConfigSpec.DoubleValue autoAssociateRadius;
    private ModConfigSpec.IntValue glowDurationSeconds;
    private ModConfigSpec.IntValue maxCatsPerStation;
    private ModConfigSpec.IntValue catXpCapacity;
    private ModConfigSpec.IntValue stationXpCapacity;
    private ModConfigSpec.IntValue xpPerBottle;

    public CatGuardianConfig(CatGuardianModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        associationRadius = builder
                .comment("Radius (blocks) in which tamed cats are associated when shift-clicking a bowl")
                .defineInRange("association_radius", 64.0D, 1.0D, 128.0D);
        fedDurationTicks = builder
                .comment("How many ticks a single fish feeding keeps a cat in the 'fed' (guarding) state (20 ticks = 1 second)")
                .defineInRange("fed_duration_ticks", 6000, 20, 144000);
        guardRadius = builder
                .comment("Horizontal radius (blocks, XZ) around the associated bowl in which a fed cat scans for and attacks hostile mobs")
                .defineInRange("guard_radius", 32.0D, 2.0D, 128.0D);
        guardRadiusY = builder
                .comment("Vertical radius (blocks, Y) around the associated bowl — cats ignore mobs further away vertically")
                .defineInRange("guard_radius_y", 16.0D, 2.0D, 128.0D);
        autoAssociateRadius = builder
                .comment("Radius (blocks) within which a cat without a bowl is automatically associated with a nearby bowl")
                .defineInRange("auto_associate_radius", 1.5D, 0.5D, 4.0D);
        glowDurationSeconds = builder
                .comment("How many seconds the Glowing effect lasts on associated cats when looking at their bowl (1–300)")
                .defineInRange("glow_duration_seconds", 30, 1, 300);
        maxCatsPerStation = builder
                .comment("Maximum number of cats that can be associated with a single bowl or station")
                .defineInRange("max_cats_per_station", 8, 1, 64);
        catXpCapacity = builder
                .comment("Maximum XP points a single cat can hold before XP drops normally")
                .defineInRange("cat_xp_capacity", 500, 0, 10000);
        stationXpCapacity = builder
                .comment("Maximum XP points a feeding station can hold before overflow stays on cats")
                .defineInRange("station_xp_capacity", 5000, 0, 100000);
        xpPerBottle = builder
                .comment("XP points consumed per Bottle o' Enchanting produced at the station")
                .defineInRange("xp_per_bottle", 8, 1, 64);
    }

    public double getAssociationRadius() {
        return associationRadius != null ? associationRadius.get() : 8.0D;
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

    public int getMaxCatsPerStation() {
        return maxCatsPerStation != null ? maxCatsPerStation.get() : 8;
    }

    public int getCatXpCapacity() {
        return catXpCapacity != null ? catXpCapacity.get() : 500;
    }

    public int getStationXpCapacity() {
        return stationXpCapacity != null ? stationXpCapacity.get() : 5000;
    }

    public int getXpPerBottle() {
        return xpPerBottle != null ? xpPerBottle.get() : 8;
    }

}
