package net.geraldhofbauer.vanillaplusadditions.modules.block_glow;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.config.BlockGlowConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side block highlight module driven by /blockglow.
 */
public class BlockGlowModule extends AbstractModule<BlockGlowModule, BlockGlowConfig> {
    private static BlockGlowModule instance;

    private BlockGlowState clientState = BlockGlowState.disabled();

    public BlockGlowModule() {
        super("block_glow",
                "Block Glow",
                "Highlights selected block types with render outlines via /blockglow",
                BlockGlowConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        getLogger().info("Block Glow module initialized");
    }

    /**
     * The module instance, or {@code null} before construction.
     *
     * <p>Module-local lookup: it works both in the all-in-one bundle and in the standalone
     * {@code vpa_block_glow} jar, whose bootstrap deliberately bypasses {@code ModuleManager}.</p>
     *
     * @return the module instance, or {@code null} if it has not been constructed yet
     */
    public static BlockGlowModule getInstance() {
        return instance;
    }

    public synchronized void setClientState(ResourceLocation blockId, int radius, int durationSeconds, long currentGameTime) {
        long expiresAtGameTime = durationSeconds <= 0
                ? 0L
                : Math.min(Long.MAX_VALUE, currentGameTime + (long) durationSeconds * 20L);
        this.clientState = BlockGlowState.enabled(blockId, radius, expiresAtGameTime);
    }

    public synchronized void clearClientState() {
        this.clientState = BlockGlowState.disabled();
    }

    public synchronized BlockGlowState getClientState() {
        return clientState;
    }

    public record BlockGlowState(boolean enabled, ResourceLocation blockId, int radius, long expiresAtGameTime) {
        public static BlockGlowState disabled() {
            return new BlockGlowState(false, ResourceLocation.withDefaultNamespace("air"), 0, 0L);
        }

        public static BlockGlowState enabled(ResourceLocation blockId, int radius, long expiresAtGameTime) {
            return new BlockGlowState(true, blockId, radius, expiresAtGameTime);
        }

        public boolean isExpired(long gameTime) {
            return enabled && expiresAtGameTime > 0L && gameTime > expiresAtGameTime;
        }
    }
}

