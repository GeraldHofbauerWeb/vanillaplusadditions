package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatFeedingStationBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatFeedingStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CatFeedingStationScreen extends AbstractContainerScreen<CatFeedingStationMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VanillaPlusAdditions.MODID, "textures/gui/cat_feeding_station.png");

    // XP bar — header area between title (ends ~y=13) and first slot row (y=18)
    private static final int XP_BAR_X      = 8;
    private static final int XP_BAR_Y      = 14;
    private static final int XP_BAR_WIDTH  = 160;
    private static final int XP_BAR_HEIGHT = 4;
    private static final int XP_BAR_BG     = 0xFF333333;
    private static final int XP_BAR_COLOR  = 0xFF70D000;

    public CatFeedingStationScreen(CatFeedingStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 172;
        this.inventoryLabelY = 80;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        CatFeedingStationBlockEntity be = menu.getBlockEntity();
        if (be != null) {
            int count = be.getAssociatedCats().size();
            int max = CatGuardianModule.getMaxCatsPerStation();
            Component text = Component.translatable("gui.vanillaplusadditions.cat_guardian.associated_cats", count, max);
            guiGraphics.drawString(this.font, text, 8, 70, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderXpBar(guiGraphics);
    }

    private void renderXpBar(GuiGraphics guiGraphics) {
        CatFeedingStationBlockEntity be = menu.getBlockEntity();
        if (be == null) {
            return;
        }
        int cap = CatGuardianModule.getStationXpCapacity();
        int cur = be.getStoredXp();
        int bx = leftPos + XP_BAR_X;
        int by = topPos  + XP_BAR_Y;
        guiGraphics.fill(bx, by, bx + XP_BAR_WIDTH, by + XP_BAR_HEIGHT, XP_BAR_BG);
        if (cap <= 0 || cur <= 0) {
            return;
        }
        int filled = (int) ((float) cur / cap * XP_BAR_WIDTH);
        if (filled <= 0) {
            return;
        }
        guiGraphics.fill(bx, by, bx + filled, by + XP_BAR_HEIGHT, XP_BAR_COLOR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        // XP bar tooltip
        int bx = leftPos + XP_BAR_X;
        int by = topPos  + XP_BAR_Y;
        if (mouseX >= bx && mouseX <= bx + XP_BAR_WIDTH
                && mouseY >= by && mouseY <= by + XP_BAR_HEIGHT) {
            CatFeedingStationBlockEntity be = menu.getBlockEntity();
            if (be != null) {
                int cap = CatGuardianModule.getStationXpCapacity();
                guiGraphics.renderTooltip(this.font,
                        Component.literal(be.getStoredXp() + " / " + cap + " XP"),
                        mouseX, mouseY);
            }
        }
    }
}
