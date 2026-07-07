package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlFeedingStationBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.menu.AxolotlFeedingStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AxolotlFeedingStationScreen extends AbstractContainerScreen<AxolotlFeedingStationMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VanillaPlusAdditions.MODID, "textures/gui/axolotl_feeding_station.png");

    // XP bar — dedicated 16px header strip between the title and the first slot row (y=34)
    private static final int XP_BAR_X      = 8;
    private static final int XP_BAR_Y      = 20;
    private static final int XP_BAR_WIDTH  = 160;
    private static final int XP_BAR_HEIGHT = 6;
    private static final int BAR_BORDER    = 0xFF000000;
    private static final int BAR_BG        = 0xFF2B2B2B;
    private static final int BAR_XP        = 0xFF7BE018;
    private static final int LABEL_COLOR   = 0x404040;

    public AxolotlFeedingStationScreen(AxolotlFeedingStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
        this.inventoryLabelY = 96;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        AxolotlFeedingStationBlockEntity be = menu.getBlockEntity();
        if (be != null) {
            int count = be.getAssociatedAxolotls().size();
            int max = AxolotlGuardianModule.getMaxAxolotlsPerStation();
            // Just "count/max", right-aligned — the full "Axolotls: X/8" next to the (long)
            // station title overflows the 176px panel.
            Component axolotls = Component.translatable(
                    "gui.vanillaplusadditions.axolotl_guardian.axolotls_short", count, max);
            int x = this.imageWidth - 8 - this.font.width(axolotls);
            guiGraphics.drawString(this.font, axolotls, x, this.titleLabelY, LABEL_COLOR, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        AxolotlFeedingStationBlockEntity be = menu.getBlockEntity();
        if (be == null) {
            return;
        }
        int cap = AxolotlGuardianModule.getStationXpCapacity();
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

        // XP bar tooltip
        int x = leftPos + XP_BAR_X;
        int y = topPos + XP_BAR_Y;
        if (mouseX >= x - 1 && mouseX <= x + XP_BAR_WIDTH + 1
                && mouseY >= y - 1 && mouseY <= y + XP_BAR_HEIGHT + 1) {
            AxolotlFeedingStationBlockEntity be = menu.getBlockEntity();
            if (be != null) {
                guiGraphics.renderTooltip(this.font, Component.translatable(
                        "gui.vanillaplusadditions.axolotl_guardian.stored_xp",
                        be.getStoredXp(), AxolotlGuardianModule.getStationXpCapacity()), mouseX, mouseY);
            }
        }
    }
}
