package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * The eligibility predicates that decide which wolf may be ridden, and by whom.
 *
 * <p>Kept in its own class (and free of any mixin or event types) because both the module's
 * server-side handlers and the {@code Wolf} mixin need the same answers, and because the armor
 * test is the one piece of {@code battle_dogs} integration in this module.
 */
public final class WolfMountRules {

    private WolfMountRules() {
    }

    /**
     * Whether a stack is body armor a canine can wear.
     *
     * <p>This is deliberately a <em>structural</em> test rather than
     * {@code instanceof WolfArmorItem}: vanilla's {@code minecraft:wolf_armor} and this mod's four
     * {@code battle_dogs} tiers are both {@link AnimalArmorItem}s with
     * {@link AnimalArmorItem.BodyType#CANINE}, so one check covers both without importing a single
     * {@code battle_dogs} class. That keeps {@code vpa_wolf_mount} loadable with battle_dogs
     * disabled or absent, and it picks up third-party canine armor for free.
     *
     * <p>Note this cannot use {@link Wolf#hasArmor()} — that is hardcoded to
     * {@code Items.WOLF_ARMOR} and would reject our own armor.
     *
     * @param stack the stack to test
     * @return true if the stack is canine body armor
     */
    public static boolean isCanineBodyArmor(ItemStack stack) {
        return stack.getItem() instanceof AnimalArmorItem armor
                && armor.getBodyType() == AnimalArmorItem.BodyType.CANINE;
    }

    /**
     * Whether the wolf currently wears armor that satisfies the mount gate.
     *
     * @param wolf the wolf
     * @return true if armor is present, or if the armor requirement is switched off
     */
    public static boolean hasMountArmor(Wolf wolf) {
        if (!WolfMountModule.isRequireBodyArmor()) {
            return true;
        }
        return isCanineBodyArmor(wolf.getBodyArmorItem());
    }

    /**
     * Whether the wolf is big enough to carry a rider, measured on the vanilla
     * {@code generic.scale} attribute.
     *
     * @param wolf the wolf
     * @return true if large enough, or if the scale requirement is switched off
     */
    public static boolean isLargeEnough(Wolf wolf) {
        if (!WolfMountModule.isRequireLargeScale()) {
            return true;
        }
        return wolf.getAttributeValue(Attributes.SCALE) >= WolfMountModule.getMinScale();
    }

    /**
     * Whether the wolf is tamed and belongs to this player.
     *
     * @param wolf   the wolf
     * @param player the prospective rider
     * @return true if owned, or if the ownership requirement is switched off
     */
    public static boolean isOwnedBy(Wolf wolf, Player player) {
        if (!WolfMountModule.isRequireTamedOwner()) {
            return true;
        }
        return wolf.isTame() && wolf.isOwnedBy(player);
    }

    /**
     * Full mount gate: every configured requirement plus the structural ones (alive, grown, not
     * already carrying someone).
     *
     * @param wolf   the wolf
     * @param player the prospective rider
     * @return true if this player may mount this wolf right now
     */
    public static boolean canMount(Wolf wolf, Player player) {
        return wolf.isAlive()
                && !wolf.isBaby()
                && !wolf.isVehicle()
                && isLargeEnough(wolf)
                && isOwnedBy(wolf, player)
                && hasMountArmor(wolf);
    }

    /**
     * Whether an <em>ongoing</em> ride is still legitimate. Same as {@link #canMount} minus the
     * "not already a vehicle" check, which is by definition false while being ridden.
     *
     * @param wolf  the wolf being ridden
     * @param rider the current rider
     * @return true if the ride may continue
     */
    public static boolean stillEligible(Wolf wolf, Player rider) {
        return wolf.isAlive()
                && !wolf.isBaby()
                && isLargeEnough(wolf)
                && isOwnedBy(wolf, rider)
                && hasMountArmor(wolf);
    }

    /**
     * Whether this wolf is currently being ridden by the player that owns it.
     *
     * @param wolf the wolf
     * @return true if the controlling passenger is this wolf's owner
     */
    public static boolean isRiddenByOwner(Wolf wolf) {
        LivingEntity controller = wolf.getControllingPassenger();
        return controller instanceof Player player && wolf.isTame() && wolf.isOwnedBy(player);
    }
}
