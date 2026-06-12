package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CatArmorItem extends Item {

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

    public CatArmorItem(Tier tier, Properties properties) {
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
}
