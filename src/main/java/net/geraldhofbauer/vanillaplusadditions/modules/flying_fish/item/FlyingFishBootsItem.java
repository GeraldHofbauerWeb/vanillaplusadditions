package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FlyingFishBootsItem extends ArmorItem {
    public FlyingFishBootsItem(Item.Properties properties) {
        super(ArmorMaterials.DIAMOND, Type.BOOTS, properties);
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
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

