package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Compass Overhaul module.
 * Each part — lodestone binding, sub-level needle, Quark's replacement needle and the world
 * compass item — can be switched off on its own.
 */
public class CompassOverhaulConfig
        extends AbstractModuleConfig<CompassOverhaulModule, CompassOverhaulConfig> {

    private ModConfigSpec.BooleanValue guardLodestoneBinding;
    private ModConfigSpec.BooleanValue fixSubLevelNeedle;
    private ModConfigSpec.BooleanValue fixQuarkCompass;
    private ModConfigSpec.BooleanValue worldCompassEnabled;

    public CompassOverhaulConfig(CompassOverhaulModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        guardLodestoneBinding = builder
                .comment("Keep a lodestone compass bound unless the lodestone is provably gone. "
                        + "Vanilla drops the binding whenever its POI lookup comes back empty, which also "
                        + "happens for an unloaded chunk or an unreadable POI file — the coordinates are then "
                        + "lost for good. With this on, the binding only clears when the target chunk is "
                        + "loaded and the block there really is no longer a lodestone.")
                .define("guard_lodestone_binding", true);

        fixSubLevelNeedle = builder
                .comment("Correct the compass needle inside Sable sub-levels (Create Aeronautics airships). "
                        + "Sable already rotates the needle, but reads the ship's pose from the previous tick "
                        + "(the needle jitters while turning) and only finds the ship through the viewer's chunk "
                        + "position. Turn this off if another mod fights us over the same method.")
                .define("fix_sublevel_needle", true);

        fixQuarkCompass = builder
                .comment("Repair the compass needle where Quark's 'Compasses Work Everywhere' takes it over. "
                        + "That replacement aims at the raw block position — the corner with the smallest X and Z, "
                        + "i.e. the north-west one — instead of the block's centre, ignores Sable sub-levels, and "
                        + "drops an item frame's rotation steps. Without Quark this does nothing.")
                .define("fix_quark_compass", true);

        worldCompassEnabled = builder
                .comment("Add the World Compass item and its recipe. The World Compass always points north "
                        + "in world space — in the Nether and the End as well, and aboard a turning airship.")
                .define("world_compass_enabled", true);
    }

    /**
     * Whether a lodestone binding is kept unless the lodestone is provably gone.
     *
     * @return true if the binding should be guarded (default true)
     */
    public boolean isGuardLodestoneBindingValue() {
        return guardLodestoneBinding != null ? guardLodestoneBinding.get() : true;
    }

    /**
     * Whether the sub-level needle correction is applied.
     *
     * @return true if the needle should be corrected inside sub-levels (default true)
     */
    public boolean isFixSubLevelNeedleValue() {
        return fixSubLevelNeedle != null ? fixSubLevelNeedle.get() : true;
    }

    /**
     * Whether Quark's replacement compass needle is repaired.
     *
     * @return true if Quark's needle should be corrected (default true)
     */
    public boolean isFixQuarkCompassValue() {
        return fixQuarkCompass != null ? fixQuarkCompass.get() : true;
    }

    /**
     * Whether the World Compass item and its recipe are available.
     *
     * @return true if the World Compass is enabled (default true)
     */
    public boolean isWorldCompassEnabledValue() {
        return worldCompassEnabled != null ? worldCompassEnabled.get() : true;
    }
}
