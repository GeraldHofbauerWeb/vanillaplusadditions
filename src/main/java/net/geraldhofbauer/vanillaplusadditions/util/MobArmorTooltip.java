package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared item tooltip for the mob-armor items (wolf / cat / axolotl body armor). All three share the
 * exact same mechanic — they absorb 100% of incoming damage (draining durability instead) and grant a
 * flat attack-damage bonus to the wearing mob — so they share one tooltip and its translation keys.
 * Lives in {@code util} (→ {@code vpa_core}) so every standalone module jar can reach it.
 */
public final class MobArmorTooltip {

    private MobArmorTooltip() {
    }

    /**
     * Appends the shared mob-armor stat lines: the attack-damage bonus granted to the mob and the
     * full damage absorption. Vanilla already renders the durability bar, so it is not repeated here.
     */
    public static void append(List<Component> tooltip, float attackBonus) {
        tooltip.add(Component.translatable(
                        "tooltip.vanillaplusadditions.mob_armor.attack", format(attackBonus))
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.vanillaplusadditions.mob_armor.absorb")
                .withStyle(ChatFormatting.GOLD));
    }

    private static String format(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : Float.toString(value);
    }
}
