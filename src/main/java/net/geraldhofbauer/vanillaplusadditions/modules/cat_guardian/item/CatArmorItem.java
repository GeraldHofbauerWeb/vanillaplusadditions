package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item;

import net.minecraft.world.item.Item;

public class CatArmorItem extends Item {

    public enum Tier {
        IRON(200, 0.15f, 1.0f),
        GOLD(100, 0.20f, 2.0f),
        DIAMOND(400, 0.30f, 3.0f),
        NETHERITE(500, 0.40f, 4.0f);

        private final int maxDurability;
        private final float damageReduction;
        private final float attackBonus;

        Tier(int maxDurability, float damageReduction, float attackBonus) {
            this.maxDurability = maxDurability;
            this.damageReduction = damageReduction;
            this.attackBonus = attackBonus;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        public float getDamageReduction() {
            return damageReduction;
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
}
