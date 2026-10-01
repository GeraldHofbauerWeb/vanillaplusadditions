package net.geraldhofbauer.vanillaplusadditions.modules.kill_items.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.kill_items.KillItemsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Kill Items module.
 */
public class KillItemsConfig extends AbstractModuleConfig<KillItemsModule, KillItemsConfig> {

    private ModConfigSpec.IntValue defaultRadius;
    private ModConfigSpec.IntValue maxRadius;
    private ModConfigSpec.IntValue permissionLevel;
    private ModConfigSpec.BooleanValue spherical;
    private ModConfigSpec.BooleanValue feedbackDetails;

    public KillItemsConfig(KillItemsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        defaultRadius = builder
                .comment("Radius in blocks used when the command is given no radius argument.")
                .defineInRange("default_radius", 16, 1, 512);

        maxRadius = builder
                .comment("Largest radius the command accepts. Checked while the command runs, not "
                        + "while the command tree is built - a limit baked into the argument type "
                        + "would freeze at server start and ignore later edits to this file.")
                .defineInRange("max_radius", 256, 1, 2048);

        permissionLevel = builder
                .comment("Permission level required to run the command: 0 lets every player use it, "
                        + "2 is the usual operator level, 4 is a full server operator. Read live, so "
                        + "a change takes effect without a restart.")
                .defineInRange("permission_level", 2, 0, 4);

        spherical = builder
                .comment("Measure the radius as a true sphere. Off means the box Minecraft searches "
                        + "is used as-is, which is a cube - a corner of it reaches about 1.7 times "
                        + "the radius you typed.")
                .define("spherical", true);

        feedbackDetails = builder
                .comment("List the most common item types in the answer, so you can see what was "
                        + "swept up (or would be, on a dry run).")
                .define("feedback_details", true);
    }

    /**
     * Radius used when the command gets no radius argument.
     *
     * @return the default radius in blocks (default 16)
     */
    public int getDefaultRadius() {
        return defaultRadius == null ? 16 : defaultRadius.get();
    }

    /**
     * Largest radius the command accepts.
     *
     * @return the maximum radius in blocks (default 256)
     */
    public int getMaxRadius() {
        return maxRadius == null ? 256 : maxRadius.get();
    }

    /**
     * Permission level required to run the command.
     *
     * @return the required level, 0 to 4 (default 2)
     */
    public int getPermissionLevel() {
        return permissionLevel == null ? 2 : permissionLevel.get();
    }

    /**
     * Whether the radius is measured as a sphere rather than a cube.
     *
     * @return true for a true sphere (default true)
     */
    public boolean isSpherical() {
        return spherical == null || spherical.get();
    }

    /**
     * Whether the answer lists the most common item types.
     *
     * @return true if details should be listed (default true)
     */
    public boolean showsFeedbackDetails() {
        return feedbackDetails == null || feedbackDetails.get();
    }
}
