package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FlyingFishBootsItem extends ArmorItem {
    public FlyingFishBootsItem(Item.Properties properties) {
        super(ArmorMaterials.DIAMOND, Type.BOOTS, properties);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        if (isBlockedEnchantment(enchantment)) {
            return false;
        }

        return super.isPrimaryItemFor(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        ItemEnchantments storedEnchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (storedEnchantments.keySet().stream().anyMatch(FlyingFishBootsItem::isBlockedEnchantment)) {
            return false;
        }

        return super.isBookEnchantable(stack, book);
    }

    private static boolean isBlockedEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.DEPTH_STRIDER) || enchantment.is(Enchantments.FROST_WALKER);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(Component.translatable("item.vanillaplusadditions.flying_fish_boots.desc_1")
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable("item.vanillaplusadditions.flying_fish_boots.desc_2")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.vanillaplusadditions.flying_fish_boots.desc_3")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

