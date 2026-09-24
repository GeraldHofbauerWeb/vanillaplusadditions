package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.item;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.util.MobArmorTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class WolfArmorItem extends AnimalArmorItem {

    public enum Tier {
        IRON(200, 1.0f),
        GOLD(100, 2.0f),
        DIAMOND(400, 3.0f),
        NETHERITE(600, 4.0f);

        private final int maxDurability;
        private final float attackBonus;

        Tier(int maxDurability, float attackBonus) {
            this.maxDurability = maxDurability;
            this.attackBonus = attackBonus;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        public float getAttackBonus() {
            return attackBonus;
        }
    }

    private final Tier tier;
    private final ResourceLocation texture;
    private final ResourceLocation foxhoundTexture;

    public WolfArmorItem(Tier tier, Properties properties) {
        super(ArmorMaterials.ARMADILLO, BodyType.CANINE, false,
              properties.durability(tier.getMaxDurability()));
        this.tier = tier;
        String suffix = tier.name().toLowerCase() + ".png";
        this.texture = ResourceLocation.fromNamespaceAndPath(
                VanillaPlusAdditions.MODID, "textures/entity/wolf/wolf_armor_" + suffix);
        this.foxhoundTexture = ResourceLocation.fromNamespaceAndPath(
                VanillaPlusAdditions.MODID, "textures/entity/foxhound/foxhound_armor_" + suffix);
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public ResourceLocation getTexture() {
        return texture;
    }

    /**
     * The same armour on Quark's Foxhound.
     *
     * <p>A second texture is needed because Quark's Foxhound brings its own armour model with a
     * 64x64 UV layout that has nothing to do with the 64x32 vanilla wolf armour. Same tier, same
     * palette, different sheet - see {@code scripts/gen_foxhound_armor_textures.py}.</p>
     *
     * @return the Foxhound armour texture for this tier
     */
    public ResourceLocation getFoxhoundTexture() {
        return foxhoundTexture;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MobArmorTooltip.append(tooltipComponents, tier.getAttackBonus());
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
