package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CatInventoryScreen extends AbstractContainerScreen<CatInventoryMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VanillaPlusAdditions.MODID, "textures/gui/cat_inventory.png");

    // Bar colors (ARGB)
    private static final int BAR_BG      = 0xFF333333;
    private static final int BAR_GREEN   = 0xFF00AA00;
    private static final int BAR_YELLOW  = 0xFFDDAA00;
    private static final int BAR_RED     = 0xFFAA2222;

    // Food bar position
    private static final int BAR_X       = 35;
    private static final int BAR_Y       = 36;
    private static final int BAR_WIDTH   = 90;  // spans the 5 loot slots (5×18)
    private static final int BAR_HEIGHT  = 4;

    // Armor durability bar — sits below the armor slot (slot at gui x=8)
    private static final int ARMOR_BAR_X      = 9;
    private static final int ARMOR_BAR_Y      = 36;
    private static final int ARMOR_BAR_WIDTH  = 16;
    private static final int ARMOR_BAR_HEIGHT = 4;

    public CatInventoryScreen(CatInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 135;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = 43;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderFoodBar(guiGraphics);
        renderArmorDurabilityBar(guiGraphics);
    }

    private void renderFoodBar(GuiGraphics guiGraphics) {
        int maxTicks = menu.getMaxFedTicks();
        int curTicks = menu.getFedTicks();
        int bx = leftPos + BAR_X;
        int by = topPos  + BAR_Y;

        guiGraphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, BAR_BG);

        if (maxTicks <= 0) {
            return;
        }
        int filled = (int) ((float) curTicks / maxTicks * BAR_WIDTH);
        if (filled <= 0) {
            return;
        }
        float ratio = (float) curTicks / maxTicks;
        int barColor = ratio > 0.5f ? BAR_GREEN : ratio > 0.25f ? BAR_YELLOW : BAR_RED;
        guiGraphics.fill(bx, by, bx + filled, by + BAR_HEIGHT, barColor);
    }

    private void renderArmorDurabilityBar(GuiGraphics guiGraphics) {
        net.minecraft.world.item.ItemStack armor = menu.getSlot(0).getItem();
        if (armor.isEmpty() || !armor.isDamaged()) {
            return;
        }
        int bx = leftPos + ARMOR_BAR_X;
        int by = topPos  + ARMOR_BAR_Y;

        guiGraphics.fill(bx, by, bx + ARMOR_BAR_WIDTH, by + ARMOR_BAR_HEIGHT, BAR_BG);

        float ratio = 1f - (float) armor.getDamageValue() / armor.getMaxDamage();
        int filled = (int) (ratio * ARMOR_BAR_WIDTH);
        if (filled <= 0) {
            return;
        }
        int barColor = ratio > 0.5f ? BAR_GREEN : ratio > 0.25f ? BAR_YELLOW : BAR_RED;
        guiGraphics.fill(bx, by, bx + filled, by + ARMOR_BAR_HEIGHT, barColor);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
