package net.geraldhofbauer.vanillaplusadditions.modules.special_gemstones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One of the two size gemstones. The item itself carries no behaviour — the size change hangs off
 * {@code PlayerInteractEvent.EntityInteract} in the module, because a tamed animal consumes a plain
 * right-click for its own sit/stand toggle long before an item hook would ever run.
 *
 * <p>The direction lives here rather than in two subclasses so the tooltip, the sound pitch and the
 * scaling all read the same flag.
 */
public class GemstoneItem extends Item {

    private final boolean grows;

    public GemstoneItem(Properties properties, boolean grows) {
        super(properties);
        this.grows = grows;
    }

    /** {@code true} for the Growth Gemstone, {@code false} for the Shrinking Gemstone. */
    public boolean grows() {
        return grows;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        String prefix = grows
                ? "item.vanillaplusadditions.growth_gemstone."
                : "item.vanillaplusadditions.shrinking_gemstone.";
        tooltipComponents.add(Component.translatable(prefix + "desc_1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(prefix + "desc_2").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
