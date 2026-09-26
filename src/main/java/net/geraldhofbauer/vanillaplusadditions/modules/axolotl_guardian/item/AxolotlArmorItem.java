package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.item;

import net.geraldhofbauer.vanillaplusadditions.util.PetArmor;
import net.geraldhofbauer.vanillaplusadditions.util.MobArmorTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class AxolotlArmorItem extends Item implements PetArmor {

    public enum Tier {
        IRON(800, 1.0f),
        GOLD(400, 2.0f),
        DIAMOND(1600, 3.0f),
        NETHERITE(2400, 4.0f);

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
     * Turtle scutes (the recipe ingredient) repair axolotl armor on the anvil. The netherite tier
     * additionally accepts diamonds: a netherite ingot is far too dear to burn on a repair, and
     * diamond is the material the tier is built up from anyway. With the free_anvil_repair module
     * enabled that repair costs 0 levels automatically.
     */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.TURTLE_SCUTE)
                || (tier == Tier.NETHERITE && repairCandidate.is(Items.DIAMOND));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MobArmorTooltip.append(tooltipComponents, tier.getAttackBonus());
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Netherite only — see {@link PetArmor#resistsHeat()}.</p>
     */
    @Override
    public boolean resistsHeat() {
        return tier == Tier.NETHERITE;
    }
}
