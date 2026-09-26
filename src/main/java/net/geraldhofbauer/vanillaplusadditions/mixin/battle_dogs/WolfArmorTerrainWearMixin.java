package net.geraldhofbauer.vanillaplusadditions.mixin.battle_dogs;

import net.geraldhofbauer.vanillaplusadditions.util.MobArmorDamage;
import net.geraldhofbauer.vanillaplusadditions.util.PetArmor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops terrain from grinding down a wolf's body armor — the one pet where an event handler is not
 * enough.
 *
 * <p>Cat and axolotl armor lives in this mod's own attachment data, so their damage runs the ordinary
 * route and {@code LivingDamageEvent.Pre} fires. A wolf's does not. {@code Wolf.actuallyHurt} forks
 * before ever reaching {@code LivingEntity.actuallyHurt}:</p>
 *
 * <pre>{@code
 * if (!this.canArmorAbsorb(source)) {
 *     super.actuallyHurt(source, amount);          // only here does the event fire
 * } else {
 *     itemstack.hurtAndBreak(Mth.ceil(amount), this, EquipmentSlot.BODY);
 * }
 * }</pre>
 *
 * <p>So for an armored wolf, vanilla absorbs the hit and wears the armor <b>itself</b>, and
 * {@code BattleDogsModule.onWolfHurt} is only reached by damage types in
 * {@code #minecraft:bypasses_wolf_armor}. Magma blocks are not among them, which is why a netherite
 * wolf still lost durability standing on one after {@code v1.0.0-beta.99} (Gerry, 2026-09-26) —
 * the no-wear rule was never asked.</p>
 *
 * <p>Cancelling at the head of {@code actuallyHurt} skips both branches at once: no damage, no wear,
 * exactly what the armor promises. The guard is narrow on purpose — only this mod's armor
 * ({@link PetArmor}), only damage types the armor would have absorbed anyway, and only those the
 * no-wear tags name. Everything else falls through to vanilla untouched.</p>
 */
@Mixin(Wolf.class)
public class WolfArmorTerrainWearMixin {

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void vpaSkipTerrainWear(DamageSource source, float amount, CallbackInfo ci) {
        Wolf wolf = (Wolf) (Object) this;
        ItemStack armor = wolf.getBodyArmorItem();
        if (!(armor.getItem() instanceof PetArmor)) {
            return; // vanilla wolf armor, or none — leave it alone
        }
        if (source.is(DamageTypeTags.BYPASSES_WOLF_ARMOR)) {
            return; // the armor would not have absorbed this; the event handler owns that path
        }
        if (MobArmorDamage.absorbsWithoutWear(source, armor)) {
            ci.cancel();
        }
    }
}
