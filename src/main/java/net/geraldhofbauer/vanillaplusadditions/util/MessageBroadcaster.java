package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Utility class for broadcasting messages to all players on a server.
 */
public final class MessageBroadcaster {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private MessageBroadcaster() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Broadcasts a simple message to all players on the server.
     *
     * @param level  The server level
     * @param message The message to broadcast
     */
    public static void broadcast(ServerLevel level, String message) {
        Component component = Component.literal(message);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    /**
     * Broadcasts a styled message to all players on the server.
     *
     * @param level      The server level
     * @param message    The message to broadcast
     * @param formatting ChatFormatting styles to apply
     */
    public static void broadcast(ServerLevel level, String message, ChatFormatting... formatting) {
        MutableComponent component = Component.literal(message);
        if (formatting != null && formatting.length > 0) {
            component = component.withStyle(formatting);
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    /**
     * Broadcasts a message with location information that can be clicked to teleport.
     *
     * @param level       The server level
     * @param mainMessage The main message text
     * @param position    The position to include in the message
     */
    public static void broadcastWithLocation(ServerLevel level, String mainMessage, BlockPos position) {
        broadcastWithLocation(level, mainMessage, position, (ChatFormatting[]) null);
    }

    /**
     * Broadcasts a message with location information that can be clicked to teleport.
     *
     * @param level          The server level
     * @param mainMessage    The main message text
     * @param position       The position to include in the message
     * @param mainFormatting ChatFormatting styles to apply to the main message
     */
    public static void broadcastWithLocation(ServerLevel level, String mainMessage, BlockPos position, 
                                            ChatFormatting... mainFormatting) {
        MutableComponent message = Component.literal(mainMessage);
        
        if (mainFormatting != null && mainFormatting.length > 0) {
            message = message.withStyle(mainFormatting);
        }
        
        MutableComponent locationComponent = Component
                .literal("\nLocation: %d, %d, %d".formatted(position.getX(), position.getY(), position.getZ()))
                .withStyle(ChatFormatting.YELLOW)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/tp @p %d %d %d".formatted(position.getX(), position.getY(), position.getZ())
                        )
                ));
        
        message.append(locationComponent);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * Broadcasts a debug message to all players if debug logging is enabled.
     *
     * @param level          The server level
     * @param shouldDebug    Whether debug logging is enabled
     * @param message        The message to broadcast
     * @param logger         The logger to also log the message to
     */
    public static void broadcastDebug(ServerLevel level, boolean shouldDebug, String message, Logger logger) {
        if (!shouldDebug) {
            return;
        }
        
        broadcast(level, "[DEBUG] " + message, ChatFormatting.GRAY, ChatFormatting.ITALIC);
        
        if (logger != null) {
            logger.debug(message);
        }
    }

    /**
     * Broadcasts a debug message with location to all players if debug logging is enabled.
     *
     * @param level          The server level
     * @param shouldDebug    Whether debug logging is enabled
     * @param message        The message to broadcast
     * @param position       The position to include
     * @param logger         The logger to also log the message to
     */
    public static void broadcastDebugWithLocation(ServerLevel level, boolean shouldDebug, String message, 
                                                  BlockPos position, Logger logger) {
        if (!shouldDebug) {
            return;
        }
        
        broadcastWithLocation(level, "[DEBUG] " + message, position, ChatFormatting.GRAY, ChatFormatting.ITALIC);
        
        if (logger != null) {
            logger.debug("{} at {}", message, position);
        }
    }
}
