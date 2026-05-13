package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.FlyingFishModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class FlyingFishConfig extends AbstractModuleConfig<FlyingFishModule, FlyingFishConfig> {

    private ModConfigSpec.DoubleValue bootsHorizontalBoost;
    private ModConfigSpec.DoubleValue bootsVerticalBoost;
    private ModConfigSpec.DoubleValue maxGlideFallSpeed;
    private ModConfigSpec.IntValue leapCooldownTicks;
    private ModConfigSpec.EnumValue<LeapMode> leapMode;

    public FlyingFishConfig(FlyingFishModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        leapMode = builder
                .comment(
                        "Auto-hop behaviour of the Flying Fish Boots.\n"
                        + "  DEFAULT  - Automatic leaps when sprinting near the water surface (governed by leap_cooldown_ticks).\n"
                        + "  ARCADE   - Leaps trigger any time the player touches water while sprinting; "
                        + "cooldown is halved for a fast, bouncy dolphin feel.\n"
                        + "  REALISTIC - No automatic leaps; only the horizontal water-skim boost is applied."
                )
                .defineEnum("leap_mode", LeapMode.DEFAULT);

        bootsHorizontalBoost = builder
                .comment("Horizontal speed boost applied while sprinting over or through water with Flying Fish Boots.")
                .defineInRange("boots_horizontal_boost", 0.08D, 0.0D, 1.0D);

        bootsVerticalBoost = builder
                .comment("Vertical launch strength applied when the boots trigger a flying-fish leap.")
                .defineInRange("boots_vertical_boost", 0.42D, 0.0D, 2.0D);

        maxGlideFallSpeed = builder
                .comment("Maximum downward speed while gliding after a water leap. Lower values glide longer.")
                .defineInRange("max_glide_fall_speed", 0.08D, 0.01D, 1.0D);

        leapCooldownTicks = builder
                .comment("Cooldown between automatic flying-fish leaps from the water surface.")
                .defineInRange("leap_cooldown_ticks", 14, 1, 200);
    }

    public LeapMode getLeapMode() {
        return leapMode != null ? leapMode.get() : LeapMode.DEFAULT;
    }

    public double getBootsHorizontalBoost() {
        return bootsHorizontalBoost != null ? bootsHorizontalBoost.get() : 0.08D;
    }

    public double getBootsVerticalBoost() {
        return bootsVerticalBoost != null ? bootsVerticalBoost.get() : 0.42D;
    }

    public double getMaxGlideFallSpeed() {
        return maxGlideFallSpeed != null ? maxGlideFallSpeed.get() : 0.08D;
    }

    public int getLeapCooldownTicks() {
        return leapCooldownTicks != null ? leapCooldownTicks.get() : 14;
    }
}

