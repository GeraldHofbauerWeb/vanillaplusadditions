package net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.MinecartChunkLoadingModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class MinecartChunkLoadingConfig
        extends AbstractModuleConfig<MinecartChunkLoadingModule, MinecartChunkLoadingConfig> {

    private ModConfigSpec.IntValue chunkLoadRadius;
    private ModConfigSpec.IntValue activeTimeoutSeconds;
    private ModConfigSpec.BooleanValue onlyWhilePlayersOnline;
    private ModConfigSpec.IntValue chunkBorderScanRadius;
    private ModConfigSpec.IntValue chunkBorderVerticalSpan;

    public MinecartChunkLoadingConfig(MinecartChunkLoadingModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        chunkLoadRadius = builder
                .comment("Chunk radius (Chebyshev) force-loaded around an active loader rail.",
                        "0 = only the rail's own chunk; 1 = a 3x3 area; 2 = a 5x5 area.",
                        "Higher = more chunks ticking but more lookahead (rails can be spaced further).")
                .defineInRange("chunk_load_radius", 1, 0, 8);

        activeTimeoutSeconds = builder
                .comment("How long a loader rail stays active (keeps chunks loaded) after the last",
                        "minecart passed over it, in seconds.")
                .defineInRange("active_timeout_seconds", 15, 1, 300);

        onlyWhilePlayersOnline = builder
                .comment("Only force-load chunks while at least one player is online.",
                        "When the last player leaves, loading pauses; on server start / first join the",
                        "rail chunks that had carts are reloaded so stuck carts continue moving.",
                        "false = keep loading even with nobody online (e.g. perpetual loops).")
                .define("only_while_players_online", true);

        builder.push("overlay");
        chunkBorderScanRadius = builder
                .comment("Debug overlay: how many chunks around the player are scanned for loader",
                        "rails to draw permanent chunk borders for.")
                .defineInRange("chunk_border_scan_radius", 8, 1, 16);

        chunkBorderVerticalSpan = builder
                .comment("Debug overlay: vertical extent (blocks above/below the rail) of the",
                        "rendered chunk border band.")
                .defineInRange("chunk_border_vertical_span", 24, 4, 256);
        builder.pop();
    }

    public int getChunkLoadRadius() {
        return chunkLoadRadius != null ? chunkLoadRadius.get() : 1;
    }

    public int getActiveTimeoutSeconds() {
        return activeTimeoutSeconds != null ? activeTimeoutSeconds.get() : 15;
    }

    public boolean isOnlyWhilePlayersOnline() {
        return onlyWhilePlayersOnline == null || onlyWhilePlayersOnline.get();
    }

    public int getChunkBorderScanRadius() {
        return chunkBorderScanRadius != null ? chunkBorderScanRadius.get() : 8;
    }

    public int getChunkBorderVerticalSpan() {
        return chunkBorderVerticalSpan != null ? chunkBorderVerticalSpan.get() : 24;
    }
}
