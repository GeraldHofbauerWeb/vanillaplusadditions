package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Shared helper for the mob-armor combat enchantments (Unbreaking, Sharpness, Thorns) used by the
 * Axolotl Guardian, Cat Guardian and Battle Dogs modules.
 *
 * <p>The armor items carry real vanilla enchantment components: Unbreaking works natively through
 * {@code hurtAndBreak}, while Sharpness and Thorns are read back off the stack and applied manually
 * in each module's combat handlers (the armor bypasses vanilla weapon/wear mechanics).
 */
public final class MobArmorEnchantments {

    private MobArmorEnchantments() {
    }

    /**
     * Stamps the configured default Unbreaking/Sharpness/Thorns levels onto a freshly crafted armor
     * stack. Levels {@code <= 0} are skipped. A {@code null} registry (no world yet) is ignored.
     *
     * @param stack          the crafted result stack to enchant in place
     * @param registryAccess registry access used to resolve the enchantment holders
     * @param unbreaking     Unbreaking level to bake (skipped when {@code <= 0})
     * @param sharpness      Sharpness level to bake (skipped when {@code <= 0})
     * @param thorns         Thorns level to bake (skipped when {@code <= 0})
     */
    public static void applyDefaults(ItemStack stack, RegistryAccess registryAccess,
                                     int unbreaking, int sharpness, int thorns) {
        if (stack == null || stack.isEmpty() || registryAccess == null) {
            return;
        }
        enchant(stack, registryAccess, Enchantments.UNBREAKING, unbreaking);
        enchant(stack, registryAccess, Enchantments.SHARPNESS, sharpness);
        enchant(stack, registryAccess, Enchantments.THORNS, thorns);
    }

    /**
     * Resolves an enchantment holder from the registry and applies it to the stack.
     *
     * @param stack          the stack to enchant in place
     * @param registryAccess registry access used to resolve the enchantment holder
     * @param key            the enchantment resource key
     * @param level          the level to apply (skipped when {@code <= 0})
     */
    public static void enchant(ItemStack stack, RegistryAccess registryAccess,
                               ResourceKey<Enchantment> key, int level) {
        if (stack == null || stack.isEmpty() || registryAccess == null || level <= 0) {
            return;
        }
        registryAccess.registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(key)
                .ifPresent(holder -> stack.enchant(holder, level));
    }

    /**
     * Reads the level of a given enchantment directly off the stack's enchantment component,
     * without needing registry access — matching by resource key.
     *
     * @param stack the stack to inspect
     * @param key   the enchantment resource key
     * @return the enchantment level, or {@code 0} if absent
     */
    public static int getLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchantments = stack.getEnchantments();
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(key)) {
                return enchantments.getLevel(holder);
            }
        }
        return 0;
    }

    /**
     * Convenience accessor for the Sharpness level baked onto a stack.
     *
     * @param stack the armor stack
     * @return the Sharpness level, or {@code 0} if absent
     */
    public static int getSharpnessLevel(ItemStack stack) {
        return getLevel(stack, Enchantments.SHARPNESS);
    }

    /**
     * Convenience accessor for the Thorns level baked onto a stack.
     *
     * @param stack the armor stack
     * @return the Thorns level, or {@code 0} if absent
     */
    public static int getThornsLevel(ItemStack stack) {
        return getLevel(stack, Enchantments.THORNS);
    }

    /**
     * Reflects a share of absorbed damage back to a living attacker (manual Thorns), used after a
     * mob-armor guardian absorbs incoming damage. No-ops on the client, when there is no living
     * attacker, when the attacker is the guardian itself, or when nothing would be reflected.
     *
     * @param guardian        the armored guardian that was hit
     * @param source          the incoming damage source (its entity is the reflect target)
     * @param absorbed        the amount of damage the armor absorbed
     * @param thornsLevel     the armor's Thorns level
     * @param reflectFraction the per-level reflect fraction (total capped at 1.0)
     */
    public static void reflectThorns(LivingEntity guardian, DamageSource source, float absorbed,
                                     int thornsLevel, double reflectFraction) {
        if (guardian == null || thornsLevel <= 0 || absorbed <= 0f || guardian.level().isClientSide()) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == guardian || !attacker.isAlive()) {
            return;
        }
        double fraction = Math.min(1.0, reflectFraction * thornsLevel);
        float reflect = (float) (absorbed * fraction);
        if (reflect <= 0f) {
            return;
        }
        attacker.hurt(guardian.level().damageSources().thorns(guardian), reflect);
    }
}
