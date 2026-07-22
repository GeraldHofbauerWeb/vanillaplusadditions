package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item;

import net.geraldhofbauer.vanillaplusadditions.util.MobArmorTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CatArmorItem extends Item {

    public enum Tier {
        IRON(200, 1.0f, Items.IRON_INGOT),
        GOLD(100, 2.0f, Items.GOLD_INGOT),
        DIAMOND(400, 3.0f, Items.DIAMOND),
        NETHERITE(600, 4.0f, Items.NETHERITE_INGOT);

        private final int maxDurability;
        private final float attackBonus;
        private final Item repairMaterial;

        Tier(int maxDurability, float attackBonus, Item repairMaterial) {
            this.maxDurability = maxDurability;
            this.attackBonus = attackBonus;
            this.repairMaterial = repairMaterial;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        public float getAttackBonus() {
            return attackBonus;
        }

        /** The tier's base metal/gem (iron/gold/diamond/netherite), which repairs the armor. */
        public Item getRepairMaterial() {
            return repairMaterial;
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
        // Material-based enchantability so the armor can be enchanted at a table (mirrors the
        // vanilla ArmorMaterials enchantment values). Anvil book enchanting works regardless.
        return switch (tier) {
            case IRON -> 9;
            case GOLD -> 25;
            case DIAMOND -> 10;
            case NETHERITE -> 15;
        };
    }

    /**
     * Armadillo scutes (the recipe ingredient) or the tier's base metal/gem (iron, gold, diamond,
     * netherite) repair cat armor on the anvil — mirroring how vanilla armor repairs with its own
     * material. With the free_anvil_repair module enabled that repair costs 0 levels automatically.
     */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.ARMADILLO_SCUTE)
                || repairCandidate.is(tier.getRepairMaterial());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MobArmorTooltip.append(tooltipComponents, tier.getAttackBonus());
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
