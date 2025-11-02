package net.geraldhofbauer.vanillaplusadditions.modules.stackables.constant_items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jetbrains.annotations.NotNull;

public class StackableLingeringPotionItem extends LingeringPotionItem {
    public StackableLingeringPotionItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public @NotNull ItemStack getDefaultInstance() {
        ItemStack itemstack = super.getDefaultInstance();
        itemstack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        return itemstack;
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return this.getDefaultMaxStackSize();
    }
}
