package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.client;

import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.menu.ItemVaultViewerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class ItemVaultViewerScreen extends AbstractContainerScreen<ItemVaultViewerMenu> {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COLUMNS = 9;
    private static final int PANEL_TOP = 24;
    private static final int PANEL_LEFT = 8;
    private static final int PANEL_MARGIN_BOTTOM = 8;
    private static final int PANEL_MARGIN_TOP = 8;
    private static final int SCROLL_BAR_WIDTH = 6;
    private static final int SORT_BUTTON_WIDTH = 32;
    private static final int SORT_BUTTON_HEIGHT = 14;

    private final List<Integer> filteredIndices = new ArrayList<>();

    private int scrollRow;
    private boolean sortAscending = false;
    private EditBox searchBox;

    public ItemVaultViewerScreen(ItemVaultViewerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_LEFT + SLOT_COLUMNS * SLOT_SIZE + 28;
        this.imageHeight = PANEL_TOP + menu.getVisibleRows() * SLOT_SIZE + PANEL_MARGIN_BOTTOM + PANEL_MARGIN_TOP;
    }

    @Override
    protected void init() {
        super.init();
        this.scrollRow = 0;
        this.titleLabelX = PANEL_LEFT;
        this.titleLabelY = 6;

        int footerY = this.topPos + this.imageHeight - 14;
        int searchX = this.leftPos + 8;
        int searchWidth = this.imageWidth - 16 - SORT_BUTTON_WIDTH - 8;
        this.searchBox = new EditBox(this.font, searchX, footerY, searchWidth, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.literal("Search"));
        this.searchBox.setResponder(value -> {
            rebuildFilteredIndices();
            this.scrollRow = 0;
        });
        this.addRenderableWidget(this.searchBox);

        int sortButtonX = this.leftPos + this.imageWidth - 8 - SORT_BUTTON_WIDTH;
        int sortY = footerY - 1;
        this.addRenderableWidget(Button.builder(Component.literal("Desc"), button -> {
            sortAscending = !sortAscending;
            button.setMessage(Component.literal(sortAscending ? "Asc" : "Desc"));
            rebuildFilteredIndices();
        }).bounds(sortButtonX, sortY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT).build());

        rebuildFilteredIndices();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int visibleRows = this.menu.getVisibleRows();
        int totalRows = getDisplayedTotalRows();

        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xFF202020);
        guiGraphics.fill(left + 1, top + 1, left + this.imageWidth - 1, top + this.imageHeight - 1, 0xFF2E2E2E);
        guiGraphics.fill(left, top, left + this.imageWidth, top + 1, 0xFF000000);
        guiGraphics.fill(left, top + this.imageHeight - 1, left + this.imageWidth, top + this.imageHeight, 0xFF000000);
        guiGraphics.fill(left, top, left + 1, top + this.imageHeight, 0xFF000000);
        guiGraphics.fill(left + this.imageWidth - 1, top, left + this.imageWidth, top + this.imageHeight, 0xFF000000);

        int startIndex = this.scrollRow * 9;
        int endIndex = Math.min(filteredIndices.size(), startIndex + visibleRows * 9);
        for (int displayIndex = startIndex; displayIndex < endIndex; displayIndex++) {
            int itemIndex = filteredIndices.get(displayIndex);
            ItemStack stack = this.menu.getStacks().get(itemIndex);
            int relative = displayIndex - startIndex;
            int row = relative / 9;
            int col = relative % 9;
            int slotX = left + PANEL_LEFT + col * SLOT_SIZE;
            int slotY = top + PANEL_TOP + row * SLOT_SIZE;

            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF4A4A4A);
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 1, 0xFF6A6A6A);
            guiGraphics.fill(slotX, slotY, slotX + 1, slotY + 16, 0xFF6A6A6A);
            guiGraphics.fill(slotX + 15, slotY, slotX + 16, slotY + 16, 0xFF2B2B2B);
            guiGraphics.fill(slotX, slotY + 15, slotX + 16, slotY + 16, 0xFF2B2B2B);
            guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
            guiGraphics.renderItemDecorations(this.font, stack, slotX + 1, slotY + 1, "");
            renderScaledCount(guiGraphics, stack, slotX + 1, slotY + 1);
        }

        if (filteredIndices.isEmpty()) {
            Component emptyLabel = searchBox != null && !searchBox.getValue().isBlank()
                    ? Component.literal("No matches")
                    : Component.literal("Empty");
            guiGraphics.drawCenteredString(this.font, emptyLabel, left + this.imageWidth / 2,
                    top + PANEL_TOP + 12, 0xA0A0A0);
        }

        if (totalRows > visibleRows) {
            int trackTop = top + PANEL_TOP;
            int trackHeight = visibleRows * SLOT_SIZE - 2;
            int thumbHeight = Math.max(10, (int) ((visibleRows / (float) totalRows) * trackHeight));
            int maxScroll = getMaxScroll();
            int thumbTop = trackTop + (int) (this.scrollRow / (float) maxScroll * (trackHeight - thumbHeight));
            int scrollBarX = left + PANEL_LEFT + SLOT_COLUMNS * SLOT_SIZE + 4;
            guiGraphics.fill(scrollBarX, trackTop, scrollBarX + SCROLL_BAR_WIDTH, trackTop + trackHeight, 0xFF3A3A3A);
            guiGraphics.fill(scrollBarX + 1, thumbTop, scrollBarX + SCROLL_BAR_WIDTH - 1, thumbTop + thumbHeight, 0xFFB0B0B0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (scrollY > 0.0) {
            this.scrollRow = Mth.clamp(this.scrollRow - 1, 0, maxScroll);
            return true;
        }
        if (scrollY < 0.0) {
            this.scrollRow = Mth.clamp(this.scrollRow + 1, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE0E0E0, false);
        int totalRows = getDisplayedTotalRows();
        if (totalRows > this.menu.getVisibleRows()) {
            int startRow = this.scrollRow + 1;
            int endRow = Math.min(totalRows, this.scrollRow + this.menu.getVisibleRows());
            Component rangeLabel = Component.literal(startRow + "-" + endRow + " / " + totalRows);
            int rangeLabelX = this.imageWidth - 8 - this.font.width(rangeLabel);
            guiGraphics.drawString(this.font, rangeLabel, rangeLabelX, this.titleLabelY, 0x909090, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int startIndex = this.scrollRow * 9;
        int endIndex = Math.min(filteredIndices.size(), startIndex + this.menu.getVisibleRows() * 9);
        for (int displayIndex = startIndex; displayIndex < endIndex; displayIndex++) {
            int itemIndex = filteredIndices.get(displayIndex);
            int relative = displayIndex - startIndex;
            int row = relative / 9;
            int col = relative % 9;
            int slotX = this.leftPos + PANEL_LEFT + col * SLOT_SIZE;
            int slotY = this.topPos + PANEL_TOP + row * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                guiGraphics.renderTooltip(this.font, this.menu.getStacks().get(itemIndex), mouseX, mouseY);
                return;
            }
        }
    }

    private void rebuildFilteredIndices() {
        filteredIndices.clear();

        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (int index = 0; index < this.menu.getStacks().size(); index++) {
            ItemStack stack = this.menu.getStacks().get(index);
            if (matchesQuery(stack, query)) {
                filteredIndices.add(index);
            }
        }

        Comparator<Integer> byCount = Comparator.comparingInt(i -> this.menu.getStacks().get(i).getCount());
        Comparator<Integer> byName = Comparator.comparing(i ->
                this.menu.getStacks().get(i).getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<Integer> comparator = byCount.thenComparing(byName);
        if (!sortAscending) {
            comparator = comparator.reversed();
        }
        filteredIndices.sort(comparator);

        this.scrollRow = Mth.clamp(this.scrollRow, 0, getMaxScroll());
    }

    private boolean matchesQuery(ItemStack stack, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String itemName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (itemName.contains(query)) {
            return true;
        }
        String itemId = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
        return itemId.contains(query);
    }

    private int getDisplayedTotalRows() {
        return Math.max(1, (filteredIndices.size() + 8) / 9);
    }

    private int getMaxScroll() {
        return Math.max(0, getDisplayedTotalRows() - this.menu.getVisibleRows());
    }

    private void renderScaledCount(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }

        String text = Integer.toString(stack.getCount());
        float scale = stack.getCount() >= 1000 ? 0.72f : stack.getCount() >= 100 ? 0.85f : 1.0f;
        int color = 0xFFFFFF;
        var pose = guiGraphics.pose();

        if (scale == 1.0f) {
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 200.0F);
            int drawX = x + 17 - this.font.width(text);
            int drawY = y + 9;
            guiGraphics.drawString(this.font, text, drawX, drawY, color, true);
            pose.popPose();
            return;
        }

        pose.pushPose();
        pose.translate(0.0F, 0.0F, 200.0F);
        pose.scale(scale, scale, 1.0f);
        float scaledWidth = this.font.width(text) * scale;
        float drawX = (x + 17 - scaledWidth) / scale;
        float drawY = (y + 9) / scale;
        guiGraphics.drawString(this.font, text, (int) drawX, (int) drawY, color, true);
        pose.popPose();
    }
}
