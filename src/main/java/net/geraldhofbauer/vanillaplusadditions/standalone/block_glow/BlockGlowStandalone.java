package net.geraldhofbauer.vanillaplusadditions.standalone.block_glow;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the block_glow module (jar {@code vpa_block_glow}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_block_glow")
public final class BlockGlowStandalone {

    public BlockGlowStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new BlockGlowModule(), modEventBus, modContainer);
    }
}
