package net.geraldhofbauer.vanillaplusadditions.modules.dispenser_bucket_guard;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.dispenser_bucket_guard.config.DispenserBucketGuardConfig;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dispenser Bucket Guard Module
 * <p>
 * Keeps a dispenser from throwing a bucket on the floor when the bucket has nothing to do. An empty
 * bucket in front of a block that holds no fluid, or a filled bucket in front of a block its
 * contents cannot be placed into, is ejected as an item entity by vanilla — which breaks every
 * automated water door or bucket loop the moment it fires one time too often.
 * <p>
 * With this module the bucket simply stays in the dispenser and the attempt is a no-op (optionally
 * with vanilla's "dispenser failed" click, see {@code play_fail_sound}). Successful dispenses are
 * untouched, so a water door still picks its water up and puts it back exactly as before.
 * <p>
 * Implementation: at common setup every bucket-like entry of the public
 * {@code DispenserBlock.DISPENSER_REGISTRY} is replaced by a {@link BucketDispenseGuard} that wraps
 * the previous behavior. That preserves whatever another mod registered for the same item, and the
 * enabled-check happens per dispense, so toggling the module at runtime takes effect immediately.
 */
public class DispenserBucketGuardModule
        extends AbstractModule<DispenserBucketGuardModule, DispenserBucketGuardConfig> {

    private static DispenserBucketGuardModule instance;

    public DispenserBucketGuardModule() {
        super("dispenser_bucket_guard",
                "Dispenser Bucket Guard",
                "Dispensers keep a bucket instead of throwing it out when there is nothing to pick up or place",
                DispenserBucketGuardConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // The dispenser registry is a plain HashMap that vanilla fills during class init. Writing to
        // it must happen on the main thread, so hook the event directly instead of using
        // onCommonSetup(), which runs on the parallel mod-loading thread.
        getModEventBus().addListener((FMLCommonSetupEvent event) -> event.enqueueWork(this::installGuards));

        getLogger().info("Dispenser Bucket Guard module initialized - buckets stay in the dispenser on a failed attempt");
    }

    /**
     * Whether the guard should intervene at all.
     *
     * @return true if the module is registered and enabled
     */
    public static boolean isActive() {
        DispenserBucketGuardModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * Whether a held-back bucket plays vanilla's "dispenser failed" click.
     *
     * @return true if the click should play (default true)
     */
    public static boolean playFailSound() {
        DispenserBucketGuardModule module = instance;
        return module == null || module.getConfig().isPlayFailSoundValue();
    }

    /**
     * Wraps every bucket-like dispense behavior. Iterates a copy of the entry set because the
     * registry is written while looping, and skips entries that are already wrapped so a second
     * call cannot stack guards.
     */
    private void installGuards() {
        Map<Item, DispenseItemBehavior> registry = DispenserBlock.DISPENSER_REGISTRY;
        List<Map.Entry<Item, DispenseItemBehavior>> entries = new ArrayList<>(registry.entrySet());
        int wrapped = 0;

        for (Map.Entry<Item, DispenseItemBehavior> entry : entries) {
            Item item = entry.getKey();
            DispenseItemBehavior behavior = entry.getValue();
            if (!BucketDispenseGuard.isBucketLike(item) || behavior instanceof BucketDispenseGuard) {
                continue;
            }
            registry.put(item, new BucketDispenseGuard(behavior));
            wrapped++;
        }

        getLogger().info("Dispenser Bucket Guard: guarded {} bucket dispense behaviors", wrapped);
    }
}
