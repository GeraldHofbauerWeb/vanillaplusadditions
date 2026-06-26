package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Single source of truth for "is the player wearing goggles" across all debug overlays.
 * Mirrors the dual check used by {@code arm_target_overlay}: Create's Engineer's Goggles
 * (helmet slot or any registered Curios slot) OR any item in the {@code arm_goggles} tag
 * (e.g. Create: Aeronautics aviator's goggles).
 */
public final class GogglesUtil {

    private static final TagKey<Item> ARM_GOGGLES_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "arm_goggles"));

    private GogglesUtil() { }

    public static boolean isWearingGoggles(Player player) {
        if (player == null) {
            return false;
        }
        if (GogglesItem.isWearingGoggles(player)) {
            return true;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ARM_GOGGLES_TAG);
    }
}
