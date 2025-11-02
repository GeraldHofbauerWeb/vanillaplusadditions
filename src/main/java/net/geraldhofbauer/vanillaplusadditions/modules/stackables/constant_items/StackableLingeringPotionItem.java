package net.geraldhofbauer.vanillaplusadditions.modules.stackables.constant_items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import javax.annotation.Nonnull;

public class StackableLingeringPotionItem extends LingeringPotionItem {
    public StackableLingeringPotionItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack itemstack = super.getDefaultInstance();
        itemstack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        return itemstack;
    }

    @Override
    public int getMaxStackSize(@Nonnull ItemStack stack) {
        return this.getDefaultMaxStackSize();
    }
}
