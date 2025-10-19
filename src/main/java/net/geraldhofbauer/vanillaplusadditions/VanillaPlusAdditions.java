package net.geraldhofbauer.vanillaplusadditions;

import com.mojang.logging.LogUtils;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.core.ModulesConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.better_mobs.BetterMobsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.death_coordinates.DeathCoordinatesModule;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_zombified_piglins.HostileZombifiedPiglinsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_glow.MobGlowModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.WitherSkeletonModule;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(VanillaPlusAdditions.MODID)
public class VanillaPlusAdditions {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "vanillaplusadditions";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public VanillaPlusAdditions(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing VanillaPlusAdditions with module system");

        // Register modules first
        registerModules();

        // Initialize the module system
        ModuleManager.getInstance().initializeModules(modEventBus, modContainer);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // All item/block registration is now handled by individual modules

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (VanillaPlusAdditions)
        // to respond directly to events. Do not add this line if there are no @SubscribeEvent-annotated
        // functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Creative tab handling is now managed by individual modules

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, ModulesConfig.getSpec());

        LOGGER.info("VanillaPlusAdditions initialization complete. {}",
                ModuleManager.getInstance().getModuleStats());
    }

    /**
     * Registers all available modules with the ModuleManager.
     * Add new modules here to include them in the mod.
     */
    private void registerModules() {
        ModuleManager moduleManager = ModuleManager.getInstance();

        // Register all available modules
        moduleManager.registerModule(new HostileZombifiedPiglinsModule());
        moduleManager.registerModule(new WitherSkeletonModule());
        moduleManager.registerModule(new MobGlowModule());
        moduleManager.registerModule(new BetterMobsModule());
        moduleManager.registerModule(new DeathCoordinatesModule());

        LOGGER.info("Registered {} modules", moduleManager.getAllModules().size());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Run module common setup
        ModuleManager.getInstance().commonSetup();

        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        // Log module configuration status
        LOGGER.info("Module configuration loaded:");
        for (var module : ModuleManager.getInstance().getAllModules()) {
            boolean enabled = ModulesConfig.isModuleEnabled(module);
            LOGGER.info("  - {}: {}", module.getDisplayName(), enabled ? "ENABLED" : "DISABLED");
        }

        LOGGER.info("VanillaPlusAdditions common setup complete with {} enabled modules",
                    ModuleManager.getInstance().getEnabledModules().size());
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class
    // annotated with @SubscribeEvent
    @EventBusSubscriber(modid = VanillaPlusAdditions.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            // Run module client setup
            ModuleManager.getInstance().clientSetup();

            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            LOGGER.info("VanillaPlusAdditions client setup complete with {} enabled modules",
                        ModuleManager.getInstance().getEnabledModules().size());
        }
    }
}
