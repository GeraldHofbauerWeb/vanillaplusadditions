package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Shared rule for the three pet armors (wolf, cat, axolotl): which damage they absorb
 * <em>without paying durability for it</em>.
 *
 * <p>All three armors already absorb 100 % of incoming damage; the only currency is wear, one point
 * of durability per point of damage. That is fine against a mob, which hits once and stops, and
 * ruinous against terrain: a cactus, a sweet berry bush or a stalagmite deals damage on a timer for
 * as long as the animal stands in it, so a pet grazing a hedge chewed through its armor faster than
 * any fight ever did (Gerry, 2026-09-26). Damage listed in this tag is still absorbed completely —
 * nothing reaches the animal — it simply costs no durability.</p>
 *
 * <p>The tag is the switch. There is no config flag on purpose: a datapack can add another mod's
 * hazard with three lines, and one with {@code "replace": true} and an empty list turns the whole
 * behaviour off. The shipped list covers contact hazards only; fire, fall, drowning and starvation
 * are deliberately left out, so the armor still wears from the animal's own mistakes.</p>
 */
public final class MobArmorDamage {

    /** Damage types every pet armor absorbs without losing durability. */
    public static final TagKey<DamageType> PET_ARMOR_NO_WEAR = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("vanillaplusadditions", "pet_armor_no_wear"));

    /**
     * Heat hazards that are free only for armor whose {@link PetArmor#resistsHeat()} holds.
     *
     * <p>Separate from the tag above because standing on a magma block should not be a free ride at
     * every tier (Gerry, 2026-09-26). Netherite shrugging it off matches the material not burning up
     * in vanilla; iron, gold and diamond still pay.</p>
     */
    public static final TagKey<DamageType> PET_ARMOR_NO_WEAR_HEATPROOF = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("vanillaplusadditions", "pet_armor_no_wear_heatproof"));

    private MobArmorDamage() {
    }

    /**
     * Whether this damage should be absorbed free of charge.
     *
     * @param source the incoming damage source
     * @param armor  the armor that absorbed it, for the heat-resistance question
     * @return true when the armor must not lose durability over it
     */
    public static boolean absorbsWithoutWear(DamageSource source, ItemStack armor) {
        if (source.is(PET_ARMOR_NO_WEAR)) {
            return true;
        }
        return source.is(PET_ARMOR_NO_WEAR_HEATPROOF)
                && armor.getItem() instanceof PetArmor petArmor && petArmor.resistsHeat();
    }

    /**
     * Charges the armor for damage it just absorbed — unless the source is exempt.
     *
     * <p>One point of durability per point of damage, at least one, which is what all three pet
     * modules did inline before this method existed. Keeping it here is not only about the tag
     * check: the rounding and the floor of 1 have to stay identical across wolf, cat and axolotl,
     * and three copies of the same expression is how they drift apart.</p>
     *
     * @param armor    the armor stack that absorbed the hit
     * @param pet      the wearer, for the break animation and the equipment slot
     * @param slot     the slot the armor sits in ({@code BODY} for wolves, {@code CHEST} otherwise)
     * @param absorbed how much damage was absorbed
     * @param source   the damage source, tested against both no-wear tags
     */
    public static void wearArmor(ItemStack armor, LivingEntity pet, EquipmentSlot slot,
                                 float absorbed, DamageSource source) {
        if (absorbsWithoutWear(source, armor)) {
            return;
        }
        armor.hurtAndBreak(Math.max(1, (int) Math.ceil(absorbed)), pet, slot);
    }
}
