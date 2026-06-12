package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CatGuardianConfig extends AbstractModuleConfig<CatGuardianModule, CatGuardianConfig> {

    private ModConfigSpec.DoubleValue associationRadius;
    private ModConfigSpec.IntValue fedDurationTicks;
    private ModConfigSpec.DoubleValue guardRadius;
    private ModConfigSpec.DoubleValue autoAssociateRadius;
    private ModConfigSpec.IntValue glowDurationSeconds;
    private ModConfigSpec.IntValue maxCatsPerStation;

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
                .comment("Radius (blocks) around the associated bowl in which a fed cat scans for and attacks hostile mobs")
                .defineInRange("guard_radius", 64.0D, 2.0D, 128.0D);
        autoAssociateRadius = builder
                .comment("Radius (blocks) within which a cat without a bowl is automatically associated with a nearby bowl")
                .defineInRange("auto_associate_radius", 1.5D, 0.5D, 4.0D);
        glowDurationSeconds = builder
                .comment("How many seconds the Glowing effect lasts on associated cats when looking at their bowl (1–300)")
                .defineInRange("glow_duration_seconds", 30, 1, 300);
        maxCatsPerStation = builder
                .comment("Maximum number of cats that can be associated with a single bowl or station")
                .defineInRange("max_cats_per_station", 8, 1, 64);
    }

    public double getAssociationRadius() {
        return associationRadius != null ? associationRadius.get() : 8.0D;
    }

    public int getFedDurationTicks() {
        return fedDurationTicks != null ? fedDurationTicks.get() : 6000;
    }

    public double getGuardRadius() {
        return guardRadius != null ? guardRadius.get() : 16.0D;
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
}
