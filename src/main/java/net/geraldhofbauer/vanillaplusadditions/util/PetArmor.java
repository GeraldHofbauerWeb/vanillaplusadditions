package net.geraldhofbauer.vanillaplusadditions.util;

/**
 * The one thing the three pet armors (wolf, cat, axolotl) have to agree on across modules.
 *
 * <p>Each of them carries its own {@code Tier} enum, in its own module, and {@code util} lives in
 * {@code vpa_core} — so core cannot ask any of them what tier a piece is without depending on all
 * three. This interface is that common denominator, and it is an interface rather than another item
 * tag on purpose: a tag nothing fills is silently empty and the rule just never fires, which is
 * exactly how the End backtanks were broken for months (see {@code end_oxygen}, beta.97). A missing
 * implementation here is a compile error.</p>
 */
public interface PetArmor {

    /**
     * Whether this piece shrugs off heat — true for the netherite tier only.
     *
     * <p>Used by {@link MobArmorDamage#wearArmor} to decide whether the heat hazards in
     * {@code #vanillaplusadditions:pet_armor_no_wear_heatproof} cost durability. Iron, gold and
     * diamond pay for standing on a magma block; netherite does not, which matches the material's
     * vanilla behaviour of not burning up.</p>
     *
     * @return true when the armor is of the netherite tier
     */
    boolean resistsHeat();
}
