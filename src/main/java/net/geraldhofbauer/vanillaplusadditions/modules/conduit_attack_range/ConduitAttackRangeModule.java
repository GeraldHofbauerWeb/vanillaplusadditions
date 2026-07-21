package net.geraldhofbauer.vanillaplusadditions.modules.conduit_attack_range;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.conduit_attack_range.config.ConduitAttackRangeConfig;

/**
 * Conduit Attack Range Module
 * <p>
 * Vanilla conduits only attack hostile mobs once the frame reaches the maximum 42 blocks
 * ({@code MIN_KILL_SIZE}), and then only within a fixed 8-block radius — much smaller than the
 * Conduit Power (beneficial) radius, which scales with frame size
 * ({@code frames / 7 * 16}: 32 blocks at 16 frames up to 96 at 42).
 * <p>
 * This module makes every <em>active</em> conduit (16+ frames) attack hostiles, within a radius
 * equal to the Conduit Power radius divided by {@code radius_divisor} (default 2). Damage amount
 * and interval stay vanilla (4 magic damage every 2 seconds). All behavior lives in
 * {@code ConduitBlockEntityMixin}, which delegates back to the static helpers here for
 * enabled-gating and radius math.
 */
public class ConduitAttackRangeModule
        extends AbstractModule<ConduitAttackRangeModule, ConduitAttackRangeConfig> {

    private static ConduitAttackRangeModule instance;

    public ConduitAttackRangeModule() {
        super("conduit_attack_range",
                "Conduit Attack Range",
                "Conduits attack hostile mobs at every active tier, within half the Conduit Power radius",
                ConduitAttackRangeConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        getLogger().info("Conduit Attack Range module initialized - conduits now hunt at every tier");
    }

    /**
     * Mixin gate: whether the conduit-attack behavior should be altered at all.
     *
     * @return true if the module is registered and enabled
     */
    public static boolean isActive() {
        ConduitAttackRangeModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * Minimum frame-block count at which a conduit starts attacking hostiles. Replaces vanilla's
     * hardcoded 42 ({@code MIN_KILL_SIZE}); default 16 = every active conduit.
     *
     * @return the configured minimum frame count
     */
    public static int minFrames() {
        ConduitAttackRangeModule module = instance;
        return module != null ? module.getConfig().getMinFramesValue() : 16;
    }

    /**
     * Hostile-damage radius for a conduit with the given frame-block count: the Conduit Power
     * radius ({@code frameCount / 7 * 16}) divided by the configured {@code radius_divisor}.
     *
     * @param frameCount number of active frame blocks (i.e. {@code positions.size()})
     * @return the hostile-damage radius in blocks (at least 1)
     */
    public static int hostileRadius(int frameCount) {
        ConduitAttackRangeModule module = instance;
        int divisor = module != null ? Math.max(1, module.getConfig().getRadiusDivisorValue()) : 2;
        int effectRadius = frameCount / 7 * 16;
        return Math.max(1, effectRadius / divisor);
    }
}
