package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CatGuardianConfig extends AbstractModuleConfig<CatGuardianModule, CatGuardianConfig> {

    private ModConfigSpec.DoubleValue associationRadius;
    private ModConfigSpec.IntValue fedDurationTicks;
    private ModConfigSpec.DoubleValue guardRadius;
    private ModConfigSpec.DoubleValue autoAssociateRadius;
    private ModConfigSpec.DoubleValue outlineRed;
    private ModConfigSpec.DoubleValue outlineGreen;
    private ModConfigSpec.DoubleValue outlineBlue;
    private ModConfigSpec.DoubleValue outlineAlpha;

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

        builder.push("outline_color");
        outlineRed = builder.comment("Red component of the associated-cat X-ray outline color")
                .defineInRange("red", 1.0D, 0.0D, 1.0D);
        outlineGreen = builder.comment("Green component of the associated-cat X-ray outline color")
                .defineInRange("green", 0.65D, 0.0D, 1.0D);
        outlineBlue = builder.comment("Blue component of the associated-cat X-ray outline color")
                .defineInRange("blue", 0.1D, 0.0D, 1.0D);
        outlineAlpha = builder.comment("Alpha component of the associated-cat X-ray outline color")
                .defineInRange("alpha", 0.85D, 0.0D, 1.0D);
        builder.pop();
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

    public float getOutlineRed() {
        return outlineRed != null ? outlineRed.get().floatValue() : 1.0f;
    }

    public float getOutlineGreen() {
        return outlineGreen != null ? outlineGreen.get().floatValue() : 0.65f;
    }

    public float getOutlineBlue() {
        return outlineBlue != null ? outlineBlue.get().floatValue() : 0.1f;
    }

    public float getOutlineAlpha() {
        return outlineAlpha != null ? outlineAlpha.get().floatValue() : 0.85f;
    }
}
