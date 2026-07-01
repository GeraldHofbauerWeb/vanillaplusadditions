package net.geraldhofbauer.vanillaplusadditions.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * Boots a <b>single</b> {@link Module} as its own standalone NeoForge mod (modId {@code vpa_<module>}),
 * sharing the framework and assets provided by the required {@code vpa_core} jar.
 *
 * <p>It deliberately does <b>not</b> route through {@link ModuleManager}'s one-shot lifecycle
 * ({@code initializeModules}/{@code commonSetup}/…): that singleton is shared across every mod in the
 * JVM and is designed for the bundle's single construction pass. If two standalone module mods both used
 * it, the second {@code registerModule} would throw ("already initialized") and lifecycle callbacks would
 * fire once per loaded module. Instead this drives exactly one module's lifecycle off <b>this</b> mod's
 * event bus, so any combination of {@code vpa_*} module jars coexists cleanly.
 *
 * <p>Module enable/disable still works: {@link AbstractModule#isModuleEnabled()} resolves via
 * {@link ModuleManager#resolveModuleEnabled(String, boolean)} which only consults runtime overrides (none
 * in standalone) and the module's own config value — no manager registration required.
 */
public final class StandaloneModuleBootstrap {

    private StandaloneModuleBootstrap() {
    }

    /**
     * Wires a standalone module into its own mod: registers a single-module config, initializes the
     * module, and forwards the FML lifecycle + config-load events to it.
     *
     * @param module       the module instance shipped by this jar
     * @param modEventBus  this mod's event bus (passed to the {@code @Mod} constructor)
     * @param modContainer this mod's container (passed to the {@code @Mod} constructor)
     */
    public static void boot(Module module, IEventBus modEventBus, ModContainer modContainer) {
        Vpa.LOGGER.info("Booting standalone module '{}' ({})", module.getModuleId(), module.getDisplayName());

        // Single-module config → file vpa_<module>-common.toml (globals + this module's [modules.<id>]).
        ModConfigSpec spec = ModulesConfig.buildStandaloneSpec(module);
        modContainer.registerConfig(ModConfig.Type.COMMON, spec);

        // Deliver onConfigLoad to the module (ModulesConfig's @EventBusSubscriber is bound to the
        // bundle modId "vanillaplusadditions" and never fires here, so wire it manually).
        modEventBus.addListener((ModConfigEvent event) -> {
            ModuleConfig config = module.getConfig();
            if (config != null && event.getConfig().getModId().equals(modContainer.getModId())) {
                try {
                    config.onConfigLoad(spec);
                } catch (Exception e) {
                    Vpa.LOGGER.error("Error loading config for standalone module {}: {}",
                            module.getModuleId(), e.getMessage());
                }
            }
        });

        // Drive this one module's lifecycle off this mod's bus.
        module.initialize(modEventBus, modContainer);
        modEventBus.addListener((FMLCommonSetupEvent event) -> module.commonSetup());
        modEventBus.addListener((FMLLoadCompleteEvent event) -> module.loadComplete());
        modEventBus.addListener((FMLClientSetupEvent event) -> module.clientSetup());
    }
}
