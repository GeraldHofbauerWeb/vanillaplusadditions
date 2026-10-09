package net.geraldhofbauer.vanillaplusadditions.mixin.battle_dogs;

import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets Quark's Foxhound bite targets that do not burn.
 *
 * <p>{@code Foxhound.doHurtTarget} returns {@code false} <em>before</em> dealing any damage when the
 * target is {@code fireImmune()} (zombified piglins, piglins in netherite, wither skeletons, blazes,
 * magma cubes, striders...), has Fire Resistance, or has a {@code BURNING_TIME} attribute of zero or
 * less. The hound still chases and "attacks" such a target, but every bite is silently dropped, so a
 * tamed foxhound in the Nether is useless in exactly the place it is native to.
 *
 * <p>Only a <b>tamed</b> foxhound is affected. A wild one keeps Quark's behaviour on purpose: there
 * Fire Resistance is the intended counter against being bitten, and this must not take it away.
 *
 * <p>The three checks are neutralised here, so the method falls through to its normal path: it
 * ignites the target for five seconds (harmless to anything fire-immune) and calls
 * {@code Wolf.doHurtTarget}, i.e. the regular melee bite with the hound's attack damage. The Peaceful
 * guard against players and the shield check are untouched.
 *
 * <p>Quark is not a compile dependency of the mixin, hence the string target; without Quark, Mixin
 * simply skips this class (the mixin config is {@code "required": false}).
 */
@Mixin(targets = "org.violetmoon.quark.content.mobs.entity.Foxhound", remap = false)
public abstract class FoxhoundFireImmuneBiteMixin {

    @Redirect(method = "doHurtTarget", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/entity/Entity;fireImmune()Z"))
    private boolean vpaIgnoreFireImmunity(Entity target) {
        return !vpaAppliesToThisHound() && target.fireImmune();
    }

    @Redirect(method = "doHurtTarget", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean vpaIgnoreFireResistance(LivingEntity target, Holder<MobEffect> effect) {
        return !vpaAppliesToThisHound() && target.hasEffect(effect);
    }

    @Redirect(method = "doHurtTarget", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/entity/LivingEntity;getAttribute(Lnet/minecraft/core/Holder;)"
                            + "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;"))
    private AttributeInstance vpaIgnoreBurningTime(LivingEntity target, Holder<Attribute> attribute) {
        // A null instance makes Quark skip its BURNING_TIME <= 0 test entirely.
        return vpaAppliesToThisHound() ? null : target.getAttribute(attribute);
    }

    private boolean vpaAppliesToThisHound() {
        return BattleDogsModule.foxhoundsBiteFireImmune() && ((TamableAnimal) (Object) this).isTame();
    }
}
