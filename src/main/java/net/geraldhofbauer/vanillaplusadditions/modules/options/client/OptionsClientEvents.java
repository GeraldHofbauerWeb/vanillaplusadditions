package net.geraldhofbauer.vanillaplusadditions.modules.options.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.options.OptionsModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Client wiring for the VPA Options module: the {@code /vpaoptions} client command, the
 * "Backups…" button injected into the vanilla Options/Controls screens, and the one-shot
 * automatic backup check on game start.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class OptionsClientEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptionsClientEvents.class);
    private static final String LANG_PREFIX = "message.vanillaplusadditions.options.";

    private static final SuggestionProvider<CommandSourceStack> BACKUP_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    OptionsBackupManager.listBackups().stream()
                            .map(OptionsBackupManager.BackupInfo::name), builder);

    private static boolean autoBackupChecked = false;

    private OptionsClientEvents() { }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        OptionsModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        event.getDispatcher().register(
                Commands.literal("vpaoptions")
                        .then(Commands.literal("export")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(OptionsClientEvents::executeExport)))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(BACKUP_SUGGESTIONS)
                                        .executes(OptionsClientEvents::executeRestore)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(BACKUP_SUGGESTIONS)
                                        .executes(OptionsClientEvents::executeDelete)))
                        .then(Commands.literal("list")
                                .executes(OptionsClientEvents::executeList))
                        .then(Commands.literal("gui")
                                .executes(OptionsClientEvents::executeGui))
        );
    }

    /** One-shot on game start: create a rotating auto backup if the options changed. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (autoBackupChecked) {
            return;
        }
        autoBackupChecked = true;

        OptionsModule module = getModule();
        if (module == null || !module.isModuleEnabled() || !module.getConfig().isAutoBackupEnabled()) {
            return;
        }
        try {
            Optional<String> created = OptionsBackupManager.autoBackupIfChanged(
                    module.getConfig().isFullOptionsBackup(), module.getConfig().getAutoBackupKeep());
            created.ifPresent(name -> LOGGER.info("VPA Options: options changed — automatic backup '{}' created",
                    name));
        } catch (IOException e) {
            LOGGER.warn("VPA Options: automatic backup failed", e);
        }
    }

    /** Injects the "Backups…" button into the vanilla Options and Controls screens. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof OptionsScreen) && !(screen instanceof ControlsScreen)) {
            return;
        }
        OptionsModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }
        event.addListener(Button.builder(
                        Component.translatable("gui.vanillaplusadditions.options.backups_button"),
                        button -> Minecraft.getInstance().setScreen(new OptionsBackupScreen(screen)))
                .bounds(screen.width - 66, 6, 60, 20)
                .build());
    }

    private static int executeExport(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        OptionsModule module = getModule();
        if (module == null) {
            return 0;
        }
        if (!OptionsBackupManager.isValidName(name)) {
            context.getSource().sendFailure(Component.translatable(LANG_PREFIX + "invalid_name", name));
            return 0;
        }
        try {
            boolean full = module.getConfig().isFullOptionsBackup();
            OptionsBackupManager.export(name, full);
            context.getSource().sendSuccess(() -> Component.translatable(LANG_PREFIX + "exported", name,
                    Component.translatable(LANG_PREFIX + (full ? "scope_full" : "scope_keys"))), false);
            return 1;
        } catch (IOException e) {
            LOGGER.warn("VPA Options: export '{}' failed", name, e);
            context.getSource().sendFailure(Component.translatable(LANG_PREFIX + "error", e.getMessage()));
            return 0;
        }
    }

    private static int executeRestore(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        OptionsModule module = getModule();
        if (module == null) {
            return 0;
        }
        if (!OptionsBackupManager.exists(name)) {
            context.getSource().sendFailure(Component.translatable(LANG_PREFIX + "not_found", name));
            return 0;
        }
        try {
            OptionsBackupManager.RestoreResult result = OptionsBackupManager.restore(name,
                    module.getConfig().isFullOptionsBackup(), module.getConfig().getAutoBackupKeep());
            if (result.fullRestore()) {
                context.getSource().sendSuccess(
                        () -> Component.translatable(LANG_PREFIX + "restored_full", name), false);
            } else {
                context.getSource().sendSuccess(() -> Component.translatable(LANG_PREFIX + "restored_keys",
                        name, result.keysApplied()), false);
            }
            return 1;
        } catch (IOException e) {
            LOGGER.warn("VPA Options: restore '{}' failed", name, e);
            context.getSource().sendFailure(Component.translatable(LANG_PREFIX + "error", e.getMessage()));
            return 0;
        }
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!OptionsBackupManager.exists(name)) {
            context.getSource().sendFailure(Component.translatable(LANG_PREFIX + "not_found", name));
            return 0;
        }
        try {
            OptionsBackupManager.delete(name);
            context.getSource().sendSuccess(
                    () -> Component.translatable(LANG_PREFIX + "deleted", name), false);
            return 1;
        } catch (IOException e) {
            LOGGER.warn("VPA Options: delete '{}' failed", name, e);
            context.getSource().sendFailure(Component.translatable(LANG_PREFIX + "error", e.getMessage()));
            return 0;
        }
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        var backups = OptionsBackupManager.listBackups();
        if (backups.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable(LANG_PREFIX + "list_empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable(LANG_PREFIX + "list_header", backups.size()), false);
        for (OptionsBackupManager.BackupInfo backup : backups) {
            context.getSource().sendSuccess(() -> Component.literal("  " + backup.name())
                    .withStyle(backup.auto() ? ChatFormatting.GRAY : ChatFormatting.WHITE), false);
        }
        return backups.size();
    }

    private static int executeGui(CommandContext<CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        // Deferred: the chat screen closes right after command execution and would override
        // setScreen if called synchronously here.
        mc.tell(() -> mc.setScreen(new OptionsBackupScreen(null)));
        return 1;
    }

    private static OptionsModule getModule() {
        Module module = ModuleManager.getInstance().getModule("options");
        return module instanceof OptionsModule m ? m : null;
    }
}
