package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client;

import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.InventoryLinkModule;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.LinkMode;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.LinkDisplay;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.UpdateLinkPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen for the links of one block: which end may give items away, which may take them,
 * and which links to drop. Opened with Ctrl + right-click while holding the Inventory Linker.
 *
 * <p>Every edit goes to the server immediately and the server answers with the authoritative list,
 * so the screen can never drift from the actual state.</p>
 */
@OnlyIn(Dist.CLIENT)
public class InventoryLinkScreen extends Screen {

    private static final int LIST_TOP = 40;
    private static final int FOOTER_HEIGHT = 62;
    private static final int ROW_HEIGHT = 24;

    private BlockPos anchor;
    private final List<LinkDisplay> links = new ArrayList<>();

    private LinkList list;
    private Button nearModeButton;
    private Button farModeButton;
    private Button unlinkButton;

    public InventoryLinkScreen(BlockPos anchor, List<LinkDisplay> links) {
        super(Component.translatable("gui.vanillaplusadditions.inventory_link.title",
                anchor.getX(), anchor.getY(), anchor.getZ()));
        this.anchor = anchor.immutable();
        this.links.addAll(links);
    }

    /** Heading — rebuilt on every frame so it follows {@link #refresh} onto another block. */
    private Component heading() {
        return Component.translatable("gui.vanillaplusadditions.inventory_link.title",
                anchor.getX(), anchor.getY(), anchor.getZ());
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        list = new LinkList(Minecraft.getInstance(), this.width,
                this.height - LIST_TOP - FOOTER_HEIGHT, LIST_TOP, ROW_HEIGHT);
        addRenderableWidget(list);

        int modeRowY = this.height - 52;
        nearModeButton = addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> cycleNearMode())
                .bounds(center - 154, modeRowY, 150, 20)
                .build());
        farModeButton = addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> cycleFarMode())
                .bounds(center + 4, modeRowY, 150, 20)
                .build());

        int footerY = this.height - 28;
        unlinkButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.vanillaplusadditions.inventory_link.unlink"),
                        button -> removeSelected())
                .bounds(center - 154, footerY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(center + 4, footerY, 150, 20)
                .build());

        rebuild();
    }

    /** Whether this screen is showing the links of the given block. */
    public boolean isShowing(BlockPos pos) {
        return anchor.equals(pos);
    }

    /** Applies a fresh server-side snapshot; called after every edit. */
    public void refresh(BlockPos queried, List<LinkDisplay> updated) {
        this.anchor = queried.immutable();
        this.links.clear();
        this.links.addAll(updated);
        if (this.links.isEmpty()) {
            onClose();
            return;
        }
        rebuild();
    }

    private void rebuild() {
        if (list == null) {
            return;
        }
        list.refresh(links);
        updateButtons();
    }

    private void updateButtons() {
        LinkDisplay selected = selected();
        boolean hasSelection = selected != null;
        nearModeButton.active = hasSelection;
        farModeButton.active = hasSelection;
        unlinkButton.active = hasSelection;

        nearModeButton.setMessage(Component.translatable(
                "gui.vanillaplusadditions.inventory_link.this_end",
                modeLabel(hasSelection ? LinkMode.fromId(selected.nearMode()) : LinkMode.BOTH)));
        farModeButton.setMessage(Component.translatable(
                "gui.vanillaplusadditions.inventory_link.other_end",
                modeLabel(hasSelection ? LinkMode.fromId(selected.farMode()) : LinkMode.BOTH)));
    }

    private LinkDisplay selected() {
        LinkList.Entry entry = list != null ? list.getSelected() : null;
        return entry != null ? entry.link : null;
    }

    private void cycleNearMode() {
        LinkDisplay link = selected();
        if (link == null) {
            return;
        }
        sendModes(link, LinkMode.fromId(link.nearMode()).next(), LinkMode.fromId(link.farMode()));
    }

    private void cycleFarMode() {
        LinkDisplay link = selected();
        if (link == null) {
            return;
        }
        sendModes(link, LinkMode.fromId(link.nearMode()), LinkMode.fromId(link.farMode()).next());
    }

    private void sendModes(LinkDisplay link, LinkMode nearMode, LinkMode farMode) {
        LinkMode firstMode = link.anchorIsFirst() ? nearMode : farMode;
        LinkMode secondMode = link.anchorIsFirst() ? farMode : nearMode;
        PacketDistributor.sendToServer(new UpdateLinkPacket(anchor, link.first(), link.second(),
                UpdateLinkPacket.ACTION_SET_MODES, firstMode.id(), secondMode.id()));
    }

    private void removeSelected() {
        LinkDisplay link = selected();
        if (link == null) {
            return;
        }
        PacketDistributor.sendToServer(new UpdateLinkPacket(anchor, link.first(), link.second(),
                UpdateLinkPacket.ACTION_REMOVE, (byte) 0, (byte) 0));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, heading(), this.width / 2, 14, 0xFFFFFF);
        guiGraphics.renderItem(new ItemStack(InventoryLinkModule.INVENTORY_LINKER.get()), 8, 8);
    }

    private static Component modeLabel(LinkMode mode) {
        return Component.translatable("gui.vanillaplusadditions.inventory_link.mode."
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Arrow showing where items actually flow, mirroring the transfer engine: the endpoint clicked
     * first wins when both ends allow both directions.
     */
    private static String directionGlyph(LinkDisplay link) {
        LinkMode firstMode = LinkMode.fromId(link.firstMode());
        LinkMode secondMode = LinkMode.fromId(link.secondMode());
        boolean forward = firstMode.canOutput() && secondMode.canInput();
        boolean backward = secondMode.canOutput() && firstMode.canInput();
        if (!forward && !backward) {
            return "  x  ";
        }
        boolean nearToFar = forward == link.anchorIsFirst();
        return nearToFar ? "  >  " : "  <  ";
    }

    /** Scrollable list of the links this block takes part in. */
    private class LinkList extends ObjectSelectionList<LinkList.Entry> {

        LinkList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void refresh(List<LinkDisplay> updated) {
            Entry previous = getSelected();
            clearEntries();
            Entry firstEntry = null;
            for (LinkDisplay link : updated) {
                Entry entry = new Entry(link);
                addEntry(entry);
                if (firstEntry == null) {
                    firstEntry = entry;
                }
                if (previous != null && previous.link.first().equals(link.first())
                        && previous.link.second().equals(link.second())) {
                    setSelected(entry);
                }
            }
            if (getSelected() == null) {
                setSelected(firstEntry);
            }
        }

        @Override
        public int getRowWidth() {
            return 308;
        }

        /** One link: the two endpoints with the flow direction, and both modes underneath. */
        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final LinkDisplay link;

            Entry(LinkDisplay link) {
                this.link = link;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width,
                               int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                String coordinates = link.nearPos().toShortString()
                        + directionGlyph(link) + link.farPos().toShortString();
                guiGraphics.drawString(InventoryLinkScreen.this.font, coordinates, left + 4, top + 2, 0xFFFFFF);

                Component modes = Component.translatable("gui.vanillaplusadditions.inventory_link.row_modes",
                                modeLabel(LinkMode.fromId(link.nearMode())),
                                modeLabel(LinkMode.fromId(link.farMode())))
                        .withStyle(ChatFormatting.GRAY);
                guiGraphics.drawString(InventoryLinkScreen.this.font, modes, left + 4, top + 13, 0xA0A0A0);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                LinkList.this.setSelected(this);
                updateButtons();
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(link.nearPos().toShortString() + " to " + link.farPos().toShortString());
            }
        }
    }
}
