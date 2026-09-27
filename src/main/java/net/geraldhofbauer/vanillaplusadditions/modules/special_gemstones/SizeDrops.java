package net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones;

import net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.config.SpecialGemstonesConfig;
import net.geraldhofbauer.vanillaplusadditions.util.SizeScaling;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A bigger creature leaves more behind, a smaller one less.
 *
 * <p>Same ladder as everything else about size ({@link SizeScaling}), so a creature at twice the
 * usual size drops half again as much at the default factor, and one at three times drops 2.25×.
 *
 * <p>The interesting part is the fraction. Most mob drops are one or two items, so "×1.5" has no
 * honest whole-number answer for a single bone: rounding up makes every kill a bonus kill, rounding
 * down makes the bonus vanish entirely. The remainder is therefore settled by a die roll — one bone
 * becomes one bone plus a coin flip for a second — so ten kills really do average fifteen bones
 * instead of ten or twenty.
 */
public final class SizeDrops {

    private SizeDrops() {
    }

    /** The loot multiplier this creature has earned, or 1.0 when nothing should change. */
    public static double multiplierFor(LivingEntity entity, SpecialGemstonesConfig config) {
        if (entity instanceof Player) {
            // A player's drops are their own inventory. Handing out copies of it would be a dupe.
            return 1.0D;
        }
        return SizeScaling.multiplier(
                entity.getAttributeValue(Attributes.SCALE), config.getDropFactor(), config.getStatCurve());
    }

    /**
     * Scales a stack size, settling the fraction with a die roll.
     *
     * @param count      the number of items vanilla decided on
     * @param multiplier the loot multiplier
     * @param random     the source to settle the remainder with
     * @return the new count, never below 1 for something that dropped at all
     */
    public static int scaleCount(int count, double multiplier, RandomSource random) {
        double wanted = count * multiplier;
        int whole = (int) Math.floor(wanted);
        if (random.nextDouble() < wanted - whole) {
            whole++;
        }
        return Math.max(count > 0 ? 1 : 0, whole);
    }

    /**
     * Grows or shrinks everything a creature dropped.
     *
     * <p>Overflow past a stack's limit becomes further stacks rather than being quietly clamped: a
     * giant creature carrying 60 arrows would otherwise lose the difference to the 64 ceiling.
     *
     * @param entity     the creature that died, for position and random source
     * @param drops      the event's mutable drop collection
     * @param multiplier the loot multiplier
     */
    public static void scaleDrops(LivingEntity entity, Collection<ItemEntity> drops, double multiplier) {
        RandomSource random = entity.getRandom();
        List<ItemEntity> extraStacks = new ArrayList<>();

        for (ItemEntity drop : List.copyOf(drops)) {
            ItemStack stack = drop.getItem();
            int base = stack.getCount();
            int extra = scaleCount(base, multiplier, random) - base;
            if (extra == 0) {
                continue;
            }
            if (extra < 0) {
                stack.setCount(Math.max(1, base + extra));
                drop.setItem(stack);
                continue;
            }

            int room = Math.min(extra, stack.getMaxStackSize() - base);
            if (room > 0) {
                stack.grow(room);
                drop.setItem(stack);
                extra -= room;
            }
            while (extra > 0) {
                int chunk = Math.min(extra, stack.getMaxStackSize());
                extraStacks.add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                        stack.copyWithCount(chunk)));
                extra -= chunk;
            }
        }

        drops.addAll(extraStacks);
    }
}
