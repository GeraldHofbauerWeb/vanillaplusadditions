package net.geraldhofbauer.vanillaplusadditions.modules.kill_items;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.kill_items.config.KillItemsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds {@code /kill-items}: removes dropped item entities around a point, with an optional radius.
 *
 * <p>The point is why this is worth a command of its own. {@code /kill @e[type=item]} clears the
 * whole dimension, and {@code /kill @e[type=item,distance=..16]} measures from whoever runs it and
 * cannot be given a default. This one defaults to a radius from the config, reads its origin from
 * the command source - so {@code /execute positioned ...} and {@code /execute at <player>} work -
 * and can tell you what it would remove before it does.</p>
 *
 * <p>Only loose item entities are touched. Items sitting inside a hopper, a chest, a Create belt or
 * a funnel are item stacks in a block inventory, not entities, so the search never sees them.</p>
 */
public class KillItemsModule extends AbstractModule<KillItemsModule, KillItemsConfig> {

    /** Language key prefix for this command's chat output. */
    private static final String LANG_PREFIX = "command.vpa.killitems.";

    /** How many distinct item types the detailed answer names before it says "and more". */
    private static final int MAX_LISTED_TYPES = 3;

    private static KillItemsModule instance;

    public KillItemsModule() {
        super("kill_items",
                "Kill Items",
                "Command that removes dropped item entities within a radius",
                KillItemsConfig::new);
        instance = this;
    }

    /**
     * The module instance, or {@code null} before {@code onInitialize} has run. Module-local on
     * purpose: {@code ModuleManager} is only populated by the all-in-one bundle, so a lookup there
     * returns null inside a standalone {@code vpa_kill_items} jar.
     *
     * @return the live module instance, or null if it has not been initialized (yet)
     */
    public static KillItemsModule getInstance() {
        return instance;
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Kill Items module initialized - /kill-items registered");
    }

    /**
     * Registers the command under both its asked-for name and the house-style alias.
     *
     * <p>Two roots rather than a {@code redirect()}: a redirect rewrites the context and makes the
     * parse errors talk about the other name.</p>
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.getDispatcher().register(buildCommand("kill-items"));
        event.getDispatcher().register(buildCommand("vpakillitems"));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(getConfig().getPermissionLevel()))
                .executes(context -> execute(context, getConfig().getDefaultRadius(), false))
                .then(Commands.literal("dry-run")
                        .executes(context -> execute(context, getConfig().getDefaultRadius(), true)))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(context -> execute(context, IntegerArgumentType.getInteger(context, "radius"), false))
                        .then(Commands.literal("dry-run")
                                .executes(context ->
                                        execute(context, IntegerArgumentType.getInteger(context, "radius"), true))));
    }

    private int execute(CommandContext<CommandSourceStack> context, int radius, boolean dryRun) {
        CommandSourceStack source = context.getSource();
        if (!isModuleEnabled()) {
            source.sendFailure(Component.translatable(LANG_PREFIX + "disabled"));
            return 0;
        }

        int maxRadius = getConfig().getMaxRadius();
        if (radius > maxRadius) {
            source.sendFailure(Component.translatable(LANG_PREFIX + "radius_too_large", radius, maxRadius));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        List<ItemEntity> found = findItems(level, origin, radius);

        String where = formatOrigin(origin);
        String dimension = level.dimension().location().toString();
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(LANG_PREFIX + "none", radius, where, dimension)
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        Component details = getConfig().showsFeedbackDetails()
                ? Component.literal(" ").append(Component.translatable(LANG_PREFIX + "details", summarize(found)))
                : Component.empty();

        int count = found.size();
        if (dryRun) {
            source.sendSuccess(() -> Component.translatable(LANG_PREFIX + "dry_run", count, radius, where, dimension)
                    .withStyle(ChatFormatting.YELLOW).append(details), false);
            return count;
        }

        for (ItemEntity item : found) {
            // discard(), not kill(): for an ItemEntity the two removal reasons behave identically,
            // but DISCARDED is what vanilla itself uses for "removed administratively".
            item.discard();
        }
        getLogger().info("[kill_items] {} removed {} item entities within {} blocks of {} in {}",
                source.getTextName(), count, radius, where, dimension);
        source.sendSuccess(() -> Component.translatable(LANG_PREFIX + "removed", count, radius, where, dimension)
                .withStyle(ChatFormatting.GREEN).append(details), true);
        return count == 0 ? Command.SINGLE_SUCCESS : count;
    }

    /**
     * Collects the loose item entities inside the radius.
     *
     * <p>Only loaded chunks of this one dimension: {@code getEntitiesOfClass} asks the level's own
     * entity lookup, which holds nothing else and loads no chunk to answer. The box handed to it is
     * a cube, so the sphere has to be cut out with an explicit distance check.</p>
     *
     * @param level  the level to search
     * @param origin centre of the search
     * @param radius radius in blocks
     * @return every matching item entity, possibly empty
     */
    private List<ItemEntity> findItems(ServerLevel level, Vec3 origin, int radius) {
        double size = radius * 2.0D;
        double radiusSqr = (double) radius * (double) radius;
        boolean spherical = getConfig().isSpherical();
        return level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(origin, size, size, size),
                item -> !item.isRemoved() && (!spherical || item.distanceToSqr(origin) <= radiusSqr));
    }

    private static String formatOrigin(Vec3 origin) {
        return String.format("%.1f, %.1f, %.1f", origin.x, origin.y, origin.z);
    }

    /**
     * Builds a short "12x Cobblestone, 3x Stick" summary of what was found.
     *
     * @param items the item entities to summarize
     * @return a human-readable summary, never empty for a non-empty list
     */
    private static String summarize(List<ItemEntity> items) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (ItemEntity entity : items) {
            counts.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
        }
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry<Item, Integer>::getValue).reversed());

        List<String> parts = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : sorted.subList(0, Math.min(MAX_LISTED_TYPES, sorted.size()))) {
            parts.add(entry.getValue() + "x " + entry.getKey().getDescription().getString());
        }
        String summary = String.join(", ", parts);
        int remaining = sorted.size() - parts.size();
        return remaining > 0 ? summary + ", +" + remaining : summary;
    }
}
