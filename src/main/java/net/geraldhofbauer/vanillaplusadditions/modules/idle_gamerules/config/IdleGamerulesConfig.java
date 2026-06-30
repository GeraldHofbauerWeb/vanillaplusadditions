package net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules.IdleGamerulesModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class IdleGamerulesConfig
        extends AbstractModuleConfig<IdleGamerulesModule, IdleGamerulesConfig> {

    /** Gamerules paused while the server is empty (vanilla + modded, set by name via /gamerule). */
    private static final List<String> DEFAULT_GAMERULES =
            List.of("doDaylightCycle", "doWeatherCycle", "doSeasonCycle");

    private ModConfigSpec.ConfigValue<List<? extends String>> gamerules;

    public IdleGamerulesConfig(IdleGamerulesModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        gamerules = builder
                .comment("Gamerules that are set to FALSE while no player is online and back to TRUE",
                        "as soon as the first player joins. Listed by their /gamerule name, so modded",
                        "rules work too (e.g. Serene Seasons' doSeasonCycle).",
                        "Default: doDaylightCycle, doWeatherCycle, doSeasonCycle.")
                .defineList("gamerules",
                        DEFAULT_GAMERULES,
                        () -> "doDaylightCycle",
                        o -> o instanceof String);
    }

    public List<? extends String> getGamerules() {
        return gamerules != null ? gamerules.get() : DEFAULT_GAMERULES;
    }
}
