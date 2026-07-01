package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.BluemapSignsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.IconKey;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.MapSignManager;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.MapSignMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Registers and handles the player-accessible {@code /bmsigns} command (help + marker management). */
public final class BluemapSignsCommands {

    private BluemapSignsCommands() {
    }

    private enum EditField {
        LABEL,
        ICON,
        DETAIL
    }

    private static final SuggestionProvider<CommandSourceStack> ICON_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(IconKey.values()).map(IconKey::key), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, BluemapSignsModule module) {
        SuggestionProvider<CommandSourceStack> commandIds = (ctx, builder) ->
                SharedSuggestionProvider.suggest(module.getManager().list(ctx.getSource().getLevel()).stream()
                        .filter(m -> !m.isSign()).map(MapSignMarker::id), builder);

        dispatcher.register(Commands.literal("bmsigns")
                .requires(source -> true)
                .executes(ctx -> help(ctx, module))
                .then(Commands.literal("help").executes(ctx -> help(ctx, module)))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, module, ctx.getSource().getLevel()))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> list(ctx, module,
                                        DimensionArgument.getDimension(ctx, "dimension")))))
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("label", StringArgumentType.string())
                                .executes(ctx -> add(ctx, module, "", ""))
                                .then(Commands.argument("icon", StringArgumentType.word())
                                        .suggests(ICON_SUGGESTIONS)
                                        .executes(ctx -> add(ctx, module,
                                                StringArgumentType.getString(ctx, "icon"), ""))
                                        .then(Commands.argument("detail", StringArgumentType.greedyString())
                                                .executes(ctx -> add(ctx, module,
                                                        StringArgumentType.getString(ctx, "icon"),
                                                        StringArgumentType.getString(ctx, "detail")))))))
                .then(Commands.literal("addat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("label", StringArgumentType.string())
                                        .executes(ctx -> addAt(ctx, module, "", ""))
                                        .then(Commands.argument("icon", StringArgumentType.word())
                                                .suggests(ICON_SUGGESTIONS)
                                                .executes(ctx -> addAt(ctx, module,
                                                        StringArgumentType.getString(ctx, "icon"), ""))
                                                .then(Commands.argument("detail", StringArgumentType.greedyString())
                                                        .executes(ctx -> addAt(ctx, module,
                                                                StringArgumentType.getString(ctx, "icon"),
                                                                StringArgumentType.getString(ctx, "detail"))))))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(commandIds)
                                .executes(ctx -> remove(ctx, module))))
                .then(Commands.literal("edit")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(commandIds)
                                .then(Commands.literal("label")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> editField(ctx, module, EditField.LABEL))))
                                .then(Commands.literal("icon")
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .suggests(ICON_SUGGESTIONS)
                                                .executes(ctx -> editField(ctx, module, EditField.ICON))))
                                .then(Commands.literal("detail")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> editField(ctx, module, EditField.DETAIL))))
                                .then(Commands.literal("pos")
                                        .then(Commands.literal("here")
                                                .executes(ctx -> editPos(ctx, module,
                                                        BlockPos.containing(ctx.getSource().getPosition()))))
                                        .then(Commands.argument("value", BlockPosArgument.blockPos())
                                                .executes(ctx -> editPos(ctx, module,
                                                        BlockPosArgument.getBlockPos(ctx, "value"))))))));
    }

    // ---- handlers ----

    private static int help(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module) {
        CommandSourceStack source = ctx.getSource();
        String prefix = module.getConfig().getPrefix();
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.format", prefix)
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.icons_header")
                .withStyle(ChatFormatting.YELLOW), false);
        for (IconKey key : IconKey.values()) {
            source.sendSuccess(() -> Component.literal("  " + key.key() + " ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable(key.langKey()).withStyle(ChatFormatting.GRAY)), false);
        }
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.commands_header")
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.commands")
                .withStyle(ChatFormatting.GRAY), false);
        if (!module.isBluemapPresent()) {
            source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.no_bluemap")
                    .withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module, ServerLevel level) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBluemap(source, module)) {
            return 0;
        }
        List<MapSignMarker> markers = module.getManager().list(level);
        markers.sort(Comparator.comparing(MapSignMarker::id));
        String dim = level.dimension().location().toString();
        if (markers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.list_empty", dim)
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.list_header", dim, markers.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (MapSignMarker marker : markers) {
            source.sendSuccess(() -> listEntry(marker), false);
        }
        return 1;
    }

    private static MutableComponent listEntry(MapSignMarker marker) {
        BlockPos pos = marker.pos();
        String label = marker.label().isBlank() ? "(unnamed)" : marker.label();
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(marker.id()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" " + label).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" [" + marker.iconKey() + "]").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" " + marker.source())
                        .withStyle(marker.isSign() ? ChatFormatting.DARK_AQUA : ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" @" + coords).withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @s " + coords))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("command.vpa.bmsigns.tp_hover")))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module,
                           String icon, String detail) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBluemap(source, module)) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        return doAdd(source, module, source.getLevel(), pos, StringArgumentType.getString(ctx, "label"),
                icon, detail);
    }

    private static int addAt(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module,
                             String icon, String detail) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBluemap(source, module)) {
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        return doAdd(source, module, source.getLevel(), pos, StringArgumentType.getString(ctx, "label"),
                icon, detail);
    }

    private static int doAdd(CommandSourceStack source, BluemapSignsModule module, ServerLevel level,
                             BlockPos pos, String label, String icon, String detail) {
        MapSignMarker marker = module.getManager().addCommandMarker(level, pos, label, icon, detail);
        source.sendSuccess(() -> Component.translatable("command.vpa.bmsigns.added", marker.label(), marker.id())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBluemap(source, module)) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        MapSignManager.OpResult result = module.getManager().removeCommandMarker(source.getLevel(), id);
        return feedback(source, result, "command.vpa.bmsigns.removed", id);
    }

    private static int editField(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module,
                                 EditField field) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBluemap(source, module)) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        String value = StringArgumentType.getString(ctx, "value");
        MapSignManager.OpResult result = module.getManager().editCommandMarker(source.getLevel(), id,
                existing -> switch (field) {
                    case LABEL -> new MapSignMarker(existing.id(), existing.source(), existing.pos(),
                            value, existing.iconKey(), existing.detail());
                    case ICON -> new MapSignMarker(existing.id(), existing.source(), existing.pos(),
                            existing.label(), value, existing.detail());
                    case DETAIL -> new MapSignMarker(existing.id(), existing.source(), existing.pos(),
                            existing.label(), existing.iconKey(), value);
                });
        return feedback(source, result, "command.vpa.bmsigns.edited", id);
    }

    private static int editPos(CommandContext<CommandSourceStack> ctx, BluemapSignsModule module, BlockPos pos) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBluemap(source, module)) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        MapSignManager.OpResult result = module.getManager().editCommandMarker(source.getLevel(), id,
                existing -> new MapSignMarker(existing.id(), existing.source(), pos.immutable(),
                        existing.label(), existing.iconKey(), existing.detail()));
        return feedback(source, result, "command.vpa.bmsigns.edited", id);
    }

    // ---- helpers ----

    private static boolean requireBluemap(CommandSourceStack source, BluemapSignsModule module) {
        if (!module.isBluemapPresent()) {
            source.sendFailure(Component.translatable("command.vpa.bmsigns.no_bluemap"));
            return false;
        }
        return true;
    }

    private static int feedback(CommandSourceStack source, MapSignManager.OpResult result,
                                String okKey, Object arg) {
        switch (result) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable(okKey, arg).withStyle(ChatFormatting.GREEN), false);
                return 1;
            }
            case NOT_FOUND -> source.sendFailure(
                    Component.translatable("command.vpa.bmsigns.not_found", arg));
            case SIGN_IMMUTABLE -> source.sendFailure(
                    Component.translatable("command.vpa.bmsigns.sign_immutable"));
            default -> {
                return 0;
            }
        }
        return 0;
    }
}
