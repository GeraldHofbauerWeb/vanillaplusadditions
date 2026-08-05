package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.MobSpawnOverlayModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Mob Spawn Overlay module: which F3 combo toggles it, how far it scans,
 * and how the markers look.
 */
public class MobSpawnOverlayConfig
        extends AbstractModuleConfig<MobSpawnOverlayModule, MobSpawnOverlayConfig> {

    /** GLFW key code of {@code M} — the default second key of the F3 combo. */
    public static final int DEFAULT_TOGGLE_KEY = 77;

    private ModConfigSpec.IntValue toggleKey;
    private ModConfigSpec.IntValue horizontalRadius;
    private ModConfigSpec.IntValue verticalRadius;
    private ModConfigSpec.IntValue rescanIntervalTicks;
    private ModConfigSpec.IntValue maxMarkers;
    private ModConfigSpec.BooleanValue seeThroughBlocks;
    private ModConfigSpec.BooleanValue markSpiderSpots;

    private ModConfigSpec.DoubleValue stripeScale;
    private ModConfigSpec.DoubleValue scrollSpeed;
    private ModConfigSpec.DoubleValue shimmerStrength;

    private ModConfigSpec.DoubleValue nowRed;
    private ModConfigSpec.DoubleValue nowGreen;
    private ModConfigSpec.DoubleValue nowBlue;
    private ModConfigSpec.DoubleValue nowAlpha;

    private ModConfigSpec.DoubleValue nightRed;
    private ModConfigSpec.DoubleValue nightGreen;
    private ModConfigSpec.DoubleValue nightBlue;
    private ModConfigSpec.DoubleValue nightAlpha;

    private ModConfigSpec.DoubleValue spiderRed;
    private ModConfigSpec.DoubleValue spiderGreen;
    private ModConfigSpec.DoubleValue spiderBlue;
    private ModConfigSpec.DoubleValue spiderAlpha;

    public MobSpawnOverlayConfig(MobSpawnOverlayModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        toggleKey = builder.comment(
                        "GLFW key code pressed together with F3 to toggle the overlay.",
                        "Default 77 = M. Change this if another mod claims the same F3 combo.",
                        "Codes: A=65 ... Z=90, F1=290 ... F12=301 (see GLFW key constants).")
                .defineInRange("toggle_key", DEFAULT_TOGGLE_KEY, 32, 348);

        builder.push("scan");
        horizontalRadius = builder.comment("How far around the player spawn positions are scanned (blocks)")
                .defineInRange("horizontal_radius", 16, 4, 48);
        verticalRadius = builder.comment("How far above/below the player spawn positions are scanned (blocks)")
                .defineInRange("vertical_radius", 8, 2, 32);
        rescanIntervalTicks = builder.comment("Ticks between rescans (20 = one second). Lower reacts faster, costs more")
                .defineInRange("rescan_interval_ticks", 10, 1, 100);
        maxMarkers = builder.comment("Safety cap on how many markers a single scan may collect")
                .defineInRange("max_markers", 6000, 100, 60000);
        markSpiderSpots = builder.comment(
                        "Additionally outline positions with enough room (2x2) for a spider to spawn")
                .define("mark_spider_spots", true);
        builder.pop();

        builder.push("display");
        seeThroughBlocks = builder.comment("Draw markers through terrain (x-ray) instead of hiding them behind blocks")
                .define("see_through_blocks", false);
        stripeScale = builder.comment("Width of one diagonal stripe in blocks — smaller means denser stripes")
                .defineInRange("stripe_scale", 0.5D, 0.05D, 4.0D);
        scrollSpeed = builder.comment("How fast the stripes travel (blocks per second)")
                .defineInRange("scroll_speed", 0.35D, 0.0D, 5.0D);
        shimmerStrength = builder.comment("Strength of the additive enchantment-style shimmer (0 disables it)")
                .defineInRange("shimmer_strength", 0.35D, 0.0D, 1.0D);
        builder.pop();

        builder.push("color_spawn_now");
        nowRed = builder.comment("Red component for positions where mobs spawn right now")
                .defineInRange("red", 1.0D, 0.0D, 1.0D);
        nowGreen = builder.comment("Green component for positions where mobs spawn right now")
                .defineInRange("green", 0.15D, 0.0D, 1.0D);
        nowBlue = builder.comment("Blue component for positions where mobs spawn right now")
                .defineInRange("blue", 0.15D, 0.0D, 1.0D);
        nowAlpha = builder.comment("Alpha component for positions where mobs spawn right now")
                .defineInRange("alpha", 0.55D, 0.0D, 1.0D);
        builder.pop();

        builder.push("color_spawn_at_night");
        nightRed = builder.comment("Red component for positions that only spawn mobs in the dark")
                .defineInRange("red", 1.0D, 0.0D, 1.0D);
        nightGreen = builder.comment("Green component for positions that only spawn mobs in the dark")
                .defineInRange("green", 0.85D, 0.0D, 1.0D);
        nightBlue = builder.comment("Blue component for positions that only spawn mobs in the dark")
                .defineInRange("blue", 0.2D, 0.0D, 1.0D);
        nightAlpha = builder.comment("Alpha component for positions that only spawn mobs in the dark")
                .defineInRange("alpha", 0.45D, 0.0D, 1.0D);
        builder.pop();

        builder.push("color_spider_outline");
        spiderRed = builder.comment("Red component of the outline drawn around spider-sized spots")
                .defineInRange("red", 0.75D, 0.0D, 1.0D);
        spiderGreen = builder.comment("Green component of the outline drawn around spider-sized spots")
                .defineInRange("green", 0.3D, 0.0D, 1.0D);
        spiderBlue = builder.comment("Blue component of the outline drawn around spider-sized spots")
                .defineInRange("blue", 1.0D, 0.0D, 1.0D);
        spiderAlpha = builder.comment("Alpha component of the outline drawn around spider-sized spots")
                .defineInRange("alpha", 0.9D, 0.0D, 1.0D);
        builder.pop();
    }

    public int getToggleKey() {
        return toggleKey == null ? DEFAULT_TOGGLE_KEY : toggleKey.get();
    }

    public int getHorizontalRadius() {
        return horizontalRadius.get();
    }

    public int getVerticalRadius() {
        return verticalRadius.get();
    }

    public int getRescanIntervalTicks() {
        return rescanIntervalTicks.get();
    }

    public int getMaxMarkers() {
        return maxMarkers.get();
    }

    public boolean shouldMarkSpiderSpots() {
        return markSpiderSpots.get();
    }

    public boolean isSeeThroughBlocks() {
        return seeThroughBlocks.get();
    }

    public float getStripeScale() {
        return stripeScale.get().floatValue();
    }

    public float getScrollSpeed() {
        return scrollSpeed.get().floatValue();
    }

    public float getShimmerStrength() {
        return shimmerStrength.get().floatValue();
    }

    /** RGBA of the "spawns right now" fill, as a float array of length 4. */
    public float[] getSpawnNowColor() {
        return rgba(nowRed, nowGreen, nowBlue, nowAlpha);
    }

    /** RGBA of the "spawns once it gets dark" fill, as a float array of length 4. */
    public float[] getSpawnAtNightColor() {
        return rgba(nightRed, nightGreen, nightBlue, nightAlpha);
    }

    /** RGBA of the spider-spot outline, as a float array of length 4. */
    public float[] getSpiderOutlineColor() {
        return rgba(spiderRed, spiderGreen, spiderBlue, spiderAlpha);
    }

    private static float[] rgba(ModConfigSpec.DoubleValue r, ModConfigSpec.DoubleValue g,
                                ModConfigSpec.DoubleValue b, ModConfigSpec.DoubleValue a) {
        return new float[] {
                r.get().floatValue(), g.get().floatValue(), b.get().floatValue(), a.get().floatValue()
        };
    }
}
