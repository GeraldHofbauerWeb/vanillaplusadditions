package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CatInventoryScreen extends AbstractContainerScreen<CatInventoryMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VanillaPlusAdditions.MODID, "textures/gui/cat_inventory.png");

    // Bar colors (ARGB)
    private static final int BAR_BORDER  = 0xFF000000;
    private static final int BAR_BG      = 0xFF2B2B2B;
    private static final int BAR_GREEN   = 0xFF3FC23F;
    private static final int BAR_YELLOW  = 0xFFE6C00A;
    private static final int BAR_RED     = 0xFFC23B3B;
    private static final int BAR_XP      = 0xFF7BE018;

    // Food + XP bar geometry. They sit above/below the right-aligned 5-slot loot grid
    // (x=80, width 90 = 5×18), so their right edge is flush with the player inventory.
    private static final int BAR_X       = 80;
    private static final int BAR_Y       = 36;
    private static final int BAR_WIDTH   = 90; // spans the 5 loot slots (5×18)
    private static final int BAR_HEIGHT  = 5;

    // Armor durability bar — sits below the armor slot (slot at gui x=8)
    private static final int ARMOR_BAR_X      = 9;
    private static final int ARMOR_BAR_Y      = 36;
    private static final int ARMOR_BAR_WIDTH  = 16;
    private static final int ARMOR_BAR_HEIGHT = 4;

    // XP bar — top header, to the right of the "Cat" title (clear of the slot row)
    private static final int XP_BAR_X      = BAR_X;
    private static final int XP_BAR_Y      = 7;
    private static final int XP_BAR_WIDTH  = BAR_WIDTH;
    private static final int XP_BAR_HEIGHT = 5;

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

    /** Draws a bar with a 1px black frame, a dark track, and a coloured fill for the given ratio. */
    private static void drawBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int fill) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BAR_BORDER);
        g.fill(x, y, x + w, y + h, BAR_BG);
        int filled = Math.max(0, Math.min(w, Math.round(ratio * w)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, fill);
        }
    }

    private static int ratioColor(float ratio) {
        return ratio > 0.5f ? BAR_GREEN : ratio > 0.25f ? BAR_YELLOW : BAR_RED;
    }

    private static boolean isHover(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x - 1 && mouseX <= x + w + 1 && mouseY >= y - 1 && mouseY <= y + h + 1;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // XP bar
        int cap = menu.getCatXpCap();
        int cur = menu.getCatXp();
        float xpRatio = cap > 0 ? (float) cur / cap : 0f;
        drawBar(guiGraphics, leftPos + XP_BAR_X, topPos + XP_BAR_Y, XP_BAR_WIDTH, XP_BAR_HEIGHT, xpRatio, BAR_XP);

        // Food (fed) bar
        int maxTicks = menu.getMaxFedTicks();
        int curTicks = menu.getFedTicks();
        float foodRatio = maxTicks > 0 ? (float) curTicks / maxTicks : 0f;
        drawBar(guiGraphics, leftPos + BAR_X, topPos + BAR_Y, BAR_WIDTH, BAR_HEIGHT, foodRatio, ratioColor(foodRatio));

        // Armor durability bar (only when armor present)
        ItemStack armor = menu.getSlot(0).getItem();
        if (!armor.isEmpty() && armor.isDamaged()) {
            float armorRatio = 1f - (float) armor.getDamageValue() / armor.getMaxDamage();
            drawBar(guiGraphics, leftPos + ARMOR_BAR_X, topPos + ARMOR_BAR_Y,
                    ARMOR_BAR_WIDTH, ARMOR_BAR_HEIGHT, armorRatio, ratioColor(armorRatio));
        }

        // Health text — centered in the horizontal gap between armor bar (ends X=25) and food bar (starts X=80)
        Cat cat = menu.getCat();
        if (cat != null) {
            int hp = Math.round(cat.getHealth());
            int maxHp = Math.round(cat.getMaxHealth());
            String healthText = hp + "/" + maxHp + " ♥";
            int tw = this.font.width(healthText);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(leftPos + 52f, topPos + 36f, 0f);
            guiGraphics.pose().scale(0.75f, 0.75f, 1f);
            guiGraphics.drawString(this.font, healthText, -(int) (tw * 0.5f), 0, 0xFFFF4444, true);
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        // XP bar tooltip
        if (isHover(mouseX, mouseY, leftPos + XP_BAR_X, topPos + XP_BAR_Y, XP_BAR_WIDTH, XP_BAR_HEIGHT)) {
            guiGraphics.renderTooltip(this.font, Component.translatable(
                    "gui.vanillaplusadditions.cat_guardian.xp", menu.getCatXp(), menu.getCatXpCap()), mouseX, mouseY);
        }

        // Food bar tooltip
        if (isHover(mouseX, mouseY, leftPos + BAR_X, topPos + BAR_Y, BAR_WIDTH, BAR_HEIGHT)) {
            int maxTicks = menu.getMaxFedTicks();
            int pct = maxTicks > 0 ? Math.round((float) menu.getFedTicks() / maxTicks * 100f) : 0;
            guiGraphics.renderTooltip(this.font, Component.translatable(
                    "gui.vanillaplusadditions.cat_guardian.food", pct), mouseX, mouseY);
        }

        // Armor bar tooltip
        ItemStack armor = menu.getSlot(0).getItem();
        if (!armor.isEmpty() && armor.isDamaged()
                && isHover(mouseX, mouseY, leftPos + ARMOR_BAR_X, topPos + ARMOR_BAR_Y,
                        ARMOR_BAR_WIDTH, ARMOR_BAR_HEIGHT)) {
            int left = armor.getMaxDamage() - armor.getDamageValue();
            guiGraphics.renderTooltip(this.font, Component.translatable(
                    "gui.vanillaplusadditions.cat_guardian.armor", left, armor.getMaxDamage()), mouseX, mouseY);
        }
    }
}
