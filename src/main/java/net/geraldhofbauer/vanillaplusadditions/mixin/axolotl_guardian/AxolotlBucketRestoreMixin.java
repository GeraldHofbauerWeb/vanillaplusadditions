package net.geraldhofbauer.vanillaplusadditions.mixin.axolotl_guardian;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the guardian payload (owner, bowl, fed state, XP, armor) whenever an axolotl bucket is
 * emptied — no matter who empties it.
 *
 * <p>The module used to pre-capture the payload in {@code PlayerInteractEvent.RightClickBlock},
 * which only fires for player-driven placements. A dispenser calls
 * {@code MobBucketItem.checkExtraContent} directly, so the payload was silently dropped and the
 * spawned axolotl came out unowned — which in turn made feeding stations ignore it, since
 * auto-association is owner-gated.</p>
 *
 * <p>{@code loadFromBucketTag} is the single choke point every placement path shares (player,
 * dispenser, Create deployer, any mod), and it receives the bucket's full BUCKET_ENTITY_DATA tag
 * including our sub-tag. Injecting at the tail leaves vanilla's Variant/Age/HuntingCooldown
 * handling untouched.</p>
 */
@Mixin(Axolotl.class)
public abstract class AxolotlBucketRestoreMixin {

    @Inject(method = "loadFromBucketTag", at = @At("TAIL"))
    private void restoreGuardianPayload(CompoundTag tag, CallbackInfo ci) {
        AxolotlGuardianModule.restoreFromBucketTag((Axolotl) (Object) this, tag);
    }
}
