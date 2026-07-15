package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.item;

import net.geraldhofbauer.vanillaplusadditions.util.MobArmorTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class AxolotlArmorItem extends Item {

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

    public AxolotlArmorItem(Tier tier, Properties properties) {
        super(properties.durability(tier.getMaxDurability()));
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    /**
     * Turtle scutes (the recipe ingredient) repair axolotl armor on the anvil. With the
     * free_anvil_repair module enabled that repair costs 0 levels automatically.
     */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.TURTLE_SCUTE);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MobArmorTooltip.append(tooltipComponents, tier.getAttackBonus());
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
