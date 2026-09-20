package net.geraldhofbauer.vanillaplusadditions.standalone.enhanced_ai_leader_loot;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.enhanced_ai_leader_loot.EnhancedAiLeaderLootModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Enhanced AI Leader Loot module (jar
 * {@code vpa_enhanced_ai_leader_loot}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 *
 * <p>Enhanced AI is a runtime gate only: the module names no class of it, so without the mod
 * installed the jar loads, writes its config and stays inert.
 */
@Mod("vpa_enhanced_ai_leader_loot")
public final class EnhancedAiLeaderLootStandalone {

    public EnhancedAiLeaderLootStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new EnhancedAiLeaderLootModule(), modEventBus, modContainer);
    }
}
