package net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules.config.IdleGamerulesConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Pauses time/weather/season progression while the server is empty and resumes it as soon as the
 * first player joins. Each configured gamerule is set to {@code false} when the last player leaves
 * and back to {@code true} on the next join.
 *
 * <p>Rules are applied by their {@code /gamerule} name, so vanilla rules (doDaylightCycle,
 * doWeatherCycle) and modded ones (Serene Seasons' doSeasonCycle) are handled uniformly. The state
 * is driven off the live player count via a transition check, so it self-corrects on server start.
 */
public class IdleGamerulesModule extends AbstractModule<IdleGamerulesModule, IdleGamerulesConfig> {

    /** Last observed "players online" state; null until the first tick so we apply the initial state. */
    private Boolean lastPlayersOnline = null;

    public IdleGamerulesModule() {
        super("idle_gamerules",
                "Idle Gamerule Pause",
                "Pauses day/weather/season cycles while the server is empty, resumes on first join.",
                IdleGamerulesConfig::new);
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        boolean playersOnline = server.getPlayerList().getPlayerCount() > 0;

        if (lastPlayersOnline != null && lastPlayersOnline == playersOnline) {
            return; // no transition
        }
        lastPlayersOnline = playersOnline;
        applyGamerules(server, playersOnline);
    }

    /** Sets every configured gamerule to {@code enabled} via the server command source (perm level 4). */
    private void applyGamerules(MinecraftServer server, boolean enabled) {
        String value = Boolean.toString(enabled);
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        for (String rule : getConfig().getGamerules()) {
            server.getCommands().performPrefixedCommand(source, "gamerule " + rule + " " + value);
        }
        getLogger().info("Server is now {} -> set {} to {}",
                enabled ? "occupied" : "empty", getConfig().getGamerules(), value);
    }
}
