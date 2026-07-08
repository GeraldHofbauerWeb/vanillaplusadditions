package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameManager;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.spot.MysticalCatSpotData;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.spot.SpotLayoutBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /mysticalcat} admin command (permission level 2): spawn a full spot or a lone cat, inspect
 * a player's progress, abort a running game, or list the spots in the current dimension.
 */
public final class MysticalCatCommand {

    private MysticalCatCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mysticalcat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn").executes(MysticalCatCommand::spawnSpot))
                .then(Commands.literal("spawncat").executes(MysticalCatCommand::spawnCat))
                .then(Commands.literal("progress").executes(MysticalCatCommand::progress))
                .then(Commands.literal("abort").executes(MysticalCatCommand::abort))
                .then(Commands.literal("spots").executes(MysticalCatCommand::spots)));
    }

    private static int spawnSpot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        MysticalCatEntity cat = SpotLayoutBuilder.buildSpot(level, at.getX(), at.getZ());
        if (cat == null) {
            ctx.getSource().sendFailure(Component.literal("Could not create the Mystical Cat."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Created a Mystical Cat spot here.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int spawnCat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        MysticalCatEntity cat = MysticalCatModule.MYSTICAL_CAT.get().create(level);
        if (cat == null) {
            ctx.getSource().sendFailure(Component.literal("Could not create the Mystical Cat."));
            return 0;
        }
        BlockPos at = player.blockPosition();
        cat.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
        cat.setSleepingPose();
        level.addFreshEntity(cat);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a lone Mystical Cat.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int progress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int completed = player.getData(MysticalCatModule.GAMES_COMPLETED.get());
        int paws = countPaws(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Trials won: " + completed + "  ·  Mystical Paws: " + paws)
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int abort(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean aborted = GameManager.get().abortPlayer(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                aborted ? "Aborted your active trial." : "You have no active trial."), false);
        return aborted ? 1 : 0;
    }

    private static int spots(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        MysticalCatSpotData data = MysticalCatSpotData.get(level);
        int count = data.spots().size();
        ctx.getSource().sendSuccess(() -> Component.literal(count + " Mystical Cat spot(s) in this dimension.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        int shown = 0;
        for (long packed : data.spots()) {
            if (shown++ >= 10) {
                ctx.getSource().sendSuccess(() -> Component.literal("  …and more."), false);
                break;
            }
            BlockPos pos = BlockPos.of(packed);
            ctx.getSource().sendSuccess(() -> Component.literal("  " + pos.getX() + ", " + pos.getY()
                    + ", " + pos.getZ()), false);
        }
        return count;
    }

    private static int countPaws(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(MysticalCatModule.MYSTICAL_PAW.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
