package net.geraldhofbauer.vanillaplusadditions.modules.food_effects.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side renderer for the thirst tooltip.
 */
public class ThirstClientTooltip implements ClientTooltipComponent {
    private static final ResourceLocation TAN_ICONS = ResourceLocation.fromNamespaceAndPath("toughasnails", "textures/gui/icons.png");
    private final ThirstTooltipData data;

    public ThirstClientTooltip(ThirstTooltipData data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return 9;
    }

    @Override
    public int getWidth(Font font) {
        int icons = (int) Math.ceil(data.amount() / 2.0);
        int width = icons * 8;
        if (data.chance() < 1.0f) {
            String chanceText = " (" + (int) (data.chance() * 100) + "%)";
            width += font.width(chanceText) + 2;
        }
        return width;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        int amount = data.amount();
        int fullIcons = amount / 2;
        boolean halfIcon = amount % 2 != 0;

        int currentX = x;
        for (int i = 0; i < fullIcons; i++) {
            // Full droplet (U:0, V:41)
            guiGraphics.blit(TAN_ICONS, currentX, y, 0, 41, 8, 8, 256, 256);
            currentX += 8;
        }

        if (halfIcon) {
            // Half droplet (U:8, V:41)
            guiGraphics.blit(TAN_ICONS, currentX, y, 8, 41, 8, 8, 256, 256);
            currentX += 8;
        }

        if (data.chance() < 1.0f) {
            String chanceText = " (" + (int) (data.chance() * 100) + "%)";
            guiGraphics.drawString(font, chanceText, currentX + 2, y + 1, 0xAAAAAA);
        }
    }
}
