package net.geraldhofbauer.vanillaplusadditions.modules.options.client;

import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.options.OptionsModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Snapshot management screen for the VPA Options module: create named backups, restore or
 * delete existing ones (manual and automatic). Opened via the "Backups…" button on the vanilla
 * Options/Controls screens or {@code /vpaoptions gui}.
 */
@OnlyIn(Dist.CLIENT)
public class OptionsBackupScreen extends Screen {

    private static final int LIST_TOP = 58;
    private static final int FOOTER_HEIGHT = 60;

    private final Screen parent;

    private EditBox nameBox;
    private BackupList list;
    private Button restoreButton;
    private Button deleteButton;
    private Component status = CommonComponents.EMPTY;

    public OptionsBackupScreen(Screen parent) {
        super(Component.translatable("gui.vanillaplusadditions.options.screen_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        nameBox = new EditBox(this.font, center - 154, 30, 204, 20,
                Component.translatable("gui.vanillaplusadditions.options.name_hint"));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.translatable("gui.vanillaplusadditions.options.name_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.vanillaplusadditions.options.create"),
                        button -> createBackup())
                .bounds(center + 54, 30, 100, 20)
                .build());

        list = new BackupList(Minecraft.getInstance(), this.width,
                this.height - LIST_TOP - FOOTER_HEIGHT, LIST_TOP, 20);
        addRenderableWidget(list);

        int footerY = this.height - 28;
        restoreButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.vanillaplusadditions.options.restore"),
                        button -> restoreSelected())
                .bounds(center - 154, footerY, 100, 20)
                .build());
        deleteButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.vanillaplusadditions.options.delete"),
                        button -> deleteSelected())
                .bounds(center - 50, footerY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(center + 54, footerY, 100, 20)
                .build());

        refreshList();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, status, this.width / 2, this.height - 46, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshList() {
        list.refresh(OptionsBackupManager.listBackups());
        boolean hasSelection = list.getSelected() != null;
        restoreButton.active = hasSelection;
        deleteButton.active = hasSelection;
    }

    private void createBackup() {
        OptionsModule module = getModule();
        String name = nameBox.getValue().trim();
        if (module == null) {
            return;
        }
        if (!OptionsBackupManager.isValidName(name)) {
            status = Component.translatable("message.vanillaplusadditions.options.invalid_name", name)
                    .withStyle(ChatFormatting.RED);
            return;
        }
        try {
            boolean full = module.getConfig().isFullOptionsBackup();
            OptionsBackupManager.export(name, full);
            status = Component.translatable("message.vanillaplusadditions.options.exported", name,
                    Component.translatable("message.vanillaplusadditions.options."
                            + (full ? "scope_full" : "scope_keys")));
            nameBox.setValue("");
            refreshList();
        } catch (IOException e) {
            status = Component.translatable("message.vanillaplusadditions.options.error", e.getMessage())
                    .withStyle(ChatFormatting.RED);
        }
    }

    private void restoreSelected() {
        OptionsModule module = getModule();
        BackupList.Entry selected = list.getSelected();
        if (module == null || selected == null) {
            return;
        }
        try {
            OptionsBackupManager.RestoreResult result = OptionsBackupManager.restore(
                    selected.info.name(), module.getConfig().isFullOptionsBackup(),
                    module.getConfig().getAutoBackupKeep());
            status = result.fullRestore()
                    ? Component.translatable("message.vanillaplusadditions.options.restored_full",
                            selected.info.name())
                    : Component.translatable("message.vanillaplusadditions.options.restored_keys",
                            selected.info.name(), result.keysApplied());
            refreshList();
        } catch (IOException e) {
            status = Component.translatable("message.vanillaplusadditions.options.error", e.getMessage())
                    .withStyle(ChatFormatting.RED);
        }
    }

    private void deleteSelected() {
        BackupList.Entry selected = list.getSelected();
        if (selected == null) {
            return;
        }
        try {
            OptionsBackupManager.delete(selected.info.name());
            status = Component.translatable("message.vanillaplusadditions.options.deleted",
                    selected.info.name());
            refreshList();
        } catch (IOException e) {
            status = Component.translatable("message.vanillaplusadditions.options.error", e.getMessage())
                    .withStyle(ChatFormatting.RED);
        }
    }

    private static OptionsModule getModule() {
        Module module = ModuleManager.getInstance().getModule("options");
        return module instanceof OptionsModule m ? m : null;
    }

    /** Scrollable snapshot list; selecting an entry enables the Restore/Delete buttons. */
    private class BackupList extends ObjectSelectionList<BackupList.Entry> {

        BackupList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void refresh(List<OptionsBackupManager.BackupInfo> backups) {
            Entry previous = getSelected();
            clearEntries();
            for (OptionsBackupManager.BackupInfo info : backups) {
                Entry entry = new Entry(info);
                addEntry(entry);
                if (previous != null && previous.info.name().equals(info.name())) {
                    setSelected(entry);
                }
            }
        }

        @Override
        public int getRowWidth() {
            return 308;
        }

        /** One snapshot row: name (gray for automatic backups) plus modification timestamp. */
        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final OptionsBackupManager.BackupInfo info;

            Entry(OptionsBackupManager.BackupInfo info) {
                this.info = info;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width,
                               int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                int color = info.auto() ? 0xA0A0A0 : 0xFFFFFF;
                guiGraphics.drawString(OptionsBackupScreen.this.font, info.name(), left + 4, top + 5, color);
                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(info.lastModified()));
                guiGraphics.drawString(OptionsBackupScreen.this.font, stamp,
                        left + width - OptionsBackupScreen.this.font.width(stamp) - 6, top + 5, 0x808080);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                BackupList.this.setSelected(this);
                restoreButton.active = true;
                deleteButton.active = true;
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(info.name());
            }
        }
    }
}
