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
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
