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

    // XP bar — dedicated 16px header strip between the title and the first slot row (y=34)
    private static final int XP_BAR_X      = 8;
    private static final int XP_BAR_Y      = 20;
    private static final int XP_BAR_WIDTH  = 160;
    private static final int XP_BAR_HEIGHT = 6;
    private static final int BAR_BORDER    = 0xFF000000;
    private static final int BAR_BG        = 0xFF2B2B2B;
    private static final int BAR_XP        = 0xFF7BE018;
    private static final int LABEL_COLOR   = 0x404040;

    public CatFeedingStationScreen(CatFeedingStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
        this.inventoryLabelY = 96;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        CatFeedingStationBlockEntity be = menu.getBlockEntity();
        if (be != null) {
            int count = be.getAssociatedCats().size();
            int max = CatGuardianModule.getMaxCatsPerStation();
            Component cats = Component.translatable(
                    "gui.vanillaplusadditions.cat_guardian.cats_short", count, max);
            // Place the count compactly to the right of the title (e.g. "Cat Feeding Station  Cats: 2/8")
            int x = this.titleLabelX + this.font.width(this.title) + 8;
            guiGraphics.drawString(this.font, cats, x, this.titleLabelY, LABEL_COLOR, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        CatFeedingStationBlockEntity be = menu.getBlockEntity();
        if (be == null) {
            return;
        }
        int cap = CatGuardianModule.getStationXpCapacity();
        int cur = be.getStoredXp();
        float ratio = cap > 0 ? (float) cur / cap : 0f;

        int x = leftPos + XP_BAR_X;
        int y = topPos + XP_BAR_Y;
        guiGraphics.fill(x - 1, y - 1, x + XP_BAR_WIDTH + 1, y + XP_BAR_HEIGHT + 1, BAR_BORDER);
        guiGraphics.fill(x, y, x + XP_BAR_WIDTH, y + XP_BAR_HEIGHT, BAR_BG);
        int filled = Math.max(0, Math.min(XP_BAR_WIDTH, Math.round(ratio * XP_BAR_WIDTH)));
        if (filled > 0) {
            guiGraphics.fill(x, y, x + filled, y + XP_BAR_HEIGHT, BAR_XP);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        // Skin slot hint (only while empty) — short title + compact material lines instead of
        // one over-long single-line tooltip.
        if (this.hoveredSlot != null && this.hoveredSlot == menu.getSlot(CatFeedingStationMenu.SKIN_SLOT)
                && !this.hoveredSlot.hasItem()) {
            guiGraphics.renderComponentTooltip(this.font, java.util.List.of(
                    Component.translatable("gui.vanillaplusadditions.cat_guardian.skin_slot"),
                    Component.translatable("gui.vanillaplusadditions.cat_guardian.skin_slot.hint")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.vanillaplusadditions.cat_guardian.skin_slot.materials1")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                    Component.translatable("gui.vanillaplusadditions.cat_guardian.skin_slot.materials2")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                    Component.translatable("gui.vanillaplusadditions.cat_guardian.skin_slot.materials3")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        }

        // XP bar tooltip
        int x = leftPos + XP_BAR_X;
        int y = topPos + XP_BAR_Y;
        if (mouseX >= x - 1 && mouseX <= x + XP_BAR_WIDTH + 1
                && mouseY >= y - 1 && mouseY <= y + XP_BAR_HEIGHT + 1) {
            CatFeedingStationBlockEntity be = menu.getBlockEntity();
            if (be != null) {
                guiGraphics.renderTooltip(this.font, Component.translatable(
                        "gui.vanillaplusadditions.cat_guardian.stored_xp",
                        be.getStoredXp(), CatGuardianModule.getStationXpCapacity()), mouseX, mouseY);
            }
        }
    }
}
