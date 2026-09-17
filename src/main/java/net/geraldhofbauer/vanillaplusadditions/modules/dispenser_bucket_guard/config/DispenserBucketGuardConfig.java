package net.geraldhofbauer.vanillaplusadditions.modules.dispenser_bucket_guard.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.dispenser_bucket_guard.DispenserBucketGuardModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Dispenser Bucket Guard module.
 * Controls whether a held-back bucket still plays vanilla's "dispenser failed" click.
 */
public class DispenserBucketGuardConfig
        extends AbstractModuleConfig<DispenserBucketGuardModule, DispenserBucketGuardConfig> {

    private ModConfigSpec.BooleanValue playFailSound;

    public DispenserBucketGuardConfig(DispenserBucketGuardModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        playFailSound = builder
                .comment("Play vanilla's \"dispenser failed\" click when a bucket is held back instead of thrown. "
                        + "true (default) gives the same feedback as an empty dispenser; "
                        + "false keeps a repeatedly triggered dispenser completely silent.")
                .define("play_fail_sound", true);
    }

    /**
     * Whether a held-back bucket plays vanilla's "dispenser failed" click.
     *
     * @return true if the click should play (default true)
     */
    public boolean isPlayFailSoundValue() {
        return playFailSound == null || playFailSound.get();
    }
}
