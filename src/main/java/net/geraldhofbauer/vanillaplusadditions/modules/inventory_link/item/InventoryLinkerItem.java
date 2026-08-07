package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Inventory Linker tool. All behaviour lives in the module's interaction handlers; the item
 * itself only carries the pending-selection data component and its tooltip.
 */
public class InventoryLinkerItem extends Item {

    public InventoryLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.vanillaplusadditions.inventory_linker.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vanillaplusadditions.inventory_linker.line2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
