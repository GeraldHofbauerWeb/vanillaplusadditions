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

    // Bar position within the image
    private static final int BAR_X       = 35;
    private static final int BAR_Y       = 36;
    private static final int BAR_WIDTH   = 90;  // spans the 5 loot slots (5×18)
    private static final int BAR_HEIGHT  = 4;

    public CatInventoryScreen(CatInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderFoodBar(guiGraphics);
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
