package net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.StationaryChunkLoaderModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class StationaryChunkLoaderConfig
        extends AbstractModuleConfig<StationaryChunkLoaderModule, StationaryChunkLoaderConfig> {

    private ModConfigSpec.IntValue chunkLoadRadius;
    private ModConfigSpec.BooleanValue onlyWhilePlayersOnline;

    public StationaryChunkLoaderConfig(StationaryChunkLoaderModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        chunkLoadRadius = builder
                .comment("Chunk radius (Chebyshev) force-loaded around a Chunk Anchor.",
                        "0 = only the anchor's own chunk; 1 = a 3x3 area; 2 = a 5x5 area.",
                        "Higher = more chunks kept loaded and ticking (more server load).")
                .defineInRange("chunk_load_radius", 0, 0, 8);

        onlyWhilePlayersOnline = builder
                .comment("Only keep anchor chunks loaded while at least one player is online.",
                        "When the last player leaves, anchors pause; on server start / first join the",
                        "anchored chunks are reloaded.",
                        "false = keep loading even with nobody online.")
                .define("only_while_players_online", true);
    }

    public int getChunkLoadRadius() {
        return chunkLoadRadius != null ? chunkLoadRadius.get() : 0;
    }

    public boolean isOnlyWhilePlayersOnline() {
        return onlyWhilePlayersOnline == null || onlyWhilePlayersOnline.get();
    }
}
