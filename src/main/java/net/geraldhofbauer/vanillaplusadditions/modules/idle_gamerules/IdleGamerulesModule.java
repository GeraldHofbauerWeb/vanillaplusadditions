package net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules.config.IdleGamerulesConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** Boolean gamerules by their {@code /gamerule} name, built once per server run. */
    private Map<String, GameRules.Key<GameRules.BooleanValue>> booleanRulesByName;

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

    /**
     * Restores the paused gamerules when a server that is currently empty shuts down.
     *
     * <p>Gamerules live in {@code level.dat}. Without this, a server stopped (or crashed, or with
     * the module switched off) while empty keeps doDaylightCycle=false and friends, and nothing is
     * left that would ever put them back. Restoring costs nothing: if the server comes back up
     * empty, the first tick pauses them again.</p>
     *
     * @param event the shutdown event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (Boolean.FALSE.equals(lastPlayersOnline)) {
            getLogger().info("Server is stopping while empty - restoring the paused gamerules "
                    + "so they are not left switched off in level.dat");
            applyGamerules(event.getServer(), true);
        }
        lastPlayersOnline = null;
        booleanRulesByName = null;
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

    /**
     * Sets every configured gamerule to {@code enabled}.
     *
     * <p>The rules are looked up by name and written directly, rather than run through
     * {@code /gamerule}. A command would be the shorter route but a silent one: its failures go to
     * {@code sendFailure} on a suppressed-output source and are dropped, so a typo or a rule from a
     * mod that is not installed used to be logged as a success. Here an unknown name is named.</p>
     *
     * @param server  the server whose gamerules are written
     * @param enabled the value to set every configured rule to
     */
    private void applyGamerules(MinecraftServer server, boolean enabled) {
        Map<String, GameRules.Key<GameRules.BooleanValue>> known = booleanRules();
        GameRules rules = server.getGameRules();
        List<String> applied = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (String rule : getConfig().getGamerules()) {
            GameRules.Key<GameRules.BooleanValue> key = known.get(rule);
            if (key == null) {
                unknown.add(rule);
                continue;
            }
            rules.getRule(key).set(enabled, server);
            applied.add(rule);
        }

        if (!applied.isEmpty()) {
            getLogger().info("Server is now {} -> set {} to {}",
                    enabled ? "occupied" : "empty", applied, enabled);
        }
        if (!unknown.isEmpty()) {
            getLogger().warn("Ignoring {} configured entries that are not boolean gamerules on this "
                    + "server: {}. Check the spelling, or whether the mod that owns them is installed.",
                    unknown.size(), unknown);
        }
    }

    /**
     * All boolean gamerules known to this server, by their {@code /gamerule} name.
     *
     * <p>Collected lazily and cached for the server run: registration is static and complete long
     * before the first tick, and it covers modded rules too (Serene Seasons' doSeasonCycle among
     * them) because they register the same way.</p>
     *
     * @return name to key, for every boolean rule
     */
    private Map<String, GameRules.Key<GameRules.BooleanValue>> booleanRules() {
        if (booleanRulesByName == null) {
            Map<String, GameRules.Key<GameRules.BooleanValue>> byName = new HashMap<>();
            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key,
                                         GameRules.Type<GameRules.BooleanValue> type) {
                    byName.put(key.getId(), key);
                }
            });
            booleanRulesByName = byName;
        }
        return booleanRulesByName;
    }
}
