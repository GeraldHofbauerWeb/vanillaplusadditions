package net.geraldhofbauer.vanillaplusadditions;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModulesConfig;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.better_mobs.BetterMobsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule;
import net.geraldhofbauer.vanillaplusadditions.modules.death_coordinates.DeathCoordinatesModule;
import net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes.CustomCraftingRecipesModule;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.EndOxygenModule;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.FlyingFishModule;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.FoodEffectsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.HauntedHouseModule;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_zombified_piglins.HostileZombifiedPiglinsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.idle_gamerules.IdleGamerulesModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_glow.MobGlowModule;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_slowdown.OverpackedSlowdownModule;
import net.geraldhofbauer.vanillaplusadditions.modules.stackables.StackablesModule;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.ArmTargetOverlayModule;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.chunk_reset.ChunkResetModule;
import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.DebugOverlayModule;
import net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading.MinecartChunkLoadingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader.StationaryChunkLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.TextureKillModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.WitherSkeletonModule;
import net.geraldhofbauer.vanillaplusadditions.util.WorldgenDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.client.ThirstClientTooltip;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.client.ThirstTooltipData;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import java.util.Comparator;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(VanillaPlusAdditions.MODID)
public class VanillaPlusAdditions {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "vanillaplusadditions";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final SuggestionProvider<CommandSourceStack> MODULE_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    ModuleManager.getInstance().getAllModules().stream()
                            .map(Module::getModuleId)
                            .sorted(),
                    builder
            );

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public VanillaPlusAdditions(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing VanillaPlusAdditions with module system");

        VanillaPlusCreativeTabs.register(modEventBus);

        // Register modules first
        registerModules();

        // Initialize the module system
        ModuleManager.getInstance().initializeModules(modEventBus, modContainer);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::loadComplete);

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

        // Register all available modules.
        // Debug overlay framework first so other modules can plug renderers into it.
        moduleManager.registerModule(new DebugOverlayModule());
        moduleManager.registerModule(new MinecartChunkLoadingModule());
        moduleManager.registerModule(new StationaryChunkLoaderModule());
        moduleManager.registerModule(new HostileZombifiedPiglinsModule());
        moduleManager.registerModule(new WitherSkeletonModule());
        moduleManager.registerModule(new MobGlowModule());
        moduleManager.registerModule(new BlockGlowModule());
        moduleManager.registerModule(new BetterMobsModule());
        moduleManager.registerModule(new DeathCoordinatesModule());
        moduleManager.registerModule(new EndOxygenModule());
        moduleManager.registerModule(new FlyingFishModule());
        moduleManager.registerModule(new StackablesModule());
        moduleManager.registerModule(new HauntedHouseModule());
        moduleManager.registerModule(new FoodEffectsModule());
        moduleManager.registerModule(new OverpackedSlowdownModule());
        moduleManager.registerModule(new CustomCraftingRecipesModule());
        moduleManager.registerModule(new ArmTargetOverlayModule());
        moduleManager.registerModule(new TextureKillModule());
        moduleManager.registerModule(new CatGuardianModule());
        moduleManager.registerModule(new ItemVaultViewerModule());
        moduleManager.registerModule(new BattleDogsModule());
        moduleManager.registerModule(new ChunkResetModule());
        moduleManager.registerModule(new IdleGamerulesModule());

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

    private void loadComplete(FMLLoadCompleteEvent event) {
        // Run module load complete
        ModuleManager.getInstance().loadComplete();

        LOGGER.info("VanillaPlusAdditions load complete");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Run worldgen diagnostics if guard is enabled
        if (ModulesConfig.isWorldgenCrashGuardEnabled()) {
            WorldgenDiagnostics.diagnoseAndReport();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        registerGlobalModuleCommand(dispatcher);
        registerHauntedHouseDebugCommand(dispatcher);
    }

    private void registerGlobalModuleCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vpa")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("module")
                                .then(Commands.literal("status")
                                        .executes(this::executeModuleStatusList)
                                        .then(Commands.argument("module_id", StringArgumentType.word())
                                                .suggests(MODULE_ID_SUGGESTIONS)
                                                .executes(this::executeModuleStatusSingle)
                                        )
                                )
                                .then(Commands.literal("enable")
                                        .then(Commands.argument("module_id", StringArgumentType.word())
                                                .suggests(MODULE_ID_SUGGESTIONS)
                                                .executes(context -> executeSetModuleRuntimeEnabled(context, true))
                                        )
                                )
                                .then(Commands.literal("disable")
                                        .then(Commands.argument("module_id", StringArgumentType.word())
                                                .suggests(MODULE_ID_SUGGESTIONS)
                                                .executes(context -> executeSetModuleRuntimeEnabled(context, false))
                                        )
                                )
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("module_id", StringArgumentType.word())
                                                .suggests(MODULE_ID_SUGGESTIONS)
                                                .executes(this::executeClearModuleRuntimeOverride)
                                        )
                                )
                        )
        );
    }

    private void registerHauntedHouseDebugCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("hauntedhouse")
                        .then(Commands.literal("whereami")
                                .executes(this::executeHauntedHouseWhereAmI)
                        )
        );
    }

    private int executeModuleStatusList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ModuleManager moduleManager = ModuleManager.getInstance();

        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════════════")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> Component.literal("VanillaPlusAdditions Module Status")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════════════")
                .withStyle(ChatFormatting.DARK_GRAY), false);

        moduleManager.getAllModules().stream()
                .sorted(Comparator.comparing(Module::getModuleId))
                .forEach(module -> {
                    String moduleId = module.getModuleId();
                    String displayName = module.getDisplayName();
                    boolean configEnabled = module.getConfig().isEnabled();
                    Boolean runtimeOverride = moduleManager.getRuntimeModuleOverride(moduleId);
                    boolean effectiveEnabled = moduleManager.isModuleEnabled(moduleId);

                    MutableComponent moduleLine = Component.literal("▸ ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal(displayName).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" (" + moduleId + ")").withStyle(ChatFormatting.GRAY));

                    ChatFormatting effectiveColor = effectiveEnabled ? ChatFormatting.GREEN : ChatFormatting.RED;
                    moduleLine.append(Component.literal("\n  └─ Status: ")
                            .withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(effectiveEnabled ? "✓ ENABLED" : "✗ DISABLED")
                                    .withStyle(effectiveColor, ChatFormatting.BOLD));

                    if (configEnabled != effectiveEnabled) {
                        moduleLine.append(Component.literal(" (override)")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
                    }

                    if (runtimeOverride != null) {
                        moduleLine.append(Component.literal("\n  └─ Runtime: ")
                                .withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal(runtimeOverride ? "▲ ON" : "▼ OFF")
                                        .withStyle(runtimeOverride ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
                    }

                    source.sendSuccess(() -> moduleLine, false);
                });

        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════════════")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private int executeModuleStatusSingle(CommandContext<CommandSourceStack> context) {
        String moduleId = StringArgumentType.getString(context, "module_id");
        CommandSourceStack source = context.getSource();
        ModuleManager moduleManager = ModuleManager.getInstance();
        Module module = moduleManager.getModule(moduleId);

        if (module == null) {
            source.sendFailure(Component.literal("❌ Unknown module: " + moduleId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean configEnabled = module.getConfig().isEnabled();
        Boolean runtimeOverride = moduleManager.getRuntimeModuleOverride(moduleId);
        boolean effectiveEnabled = moduleManager.isModuleEnabled(moduleId);

        MutableComponent header = Component.literal("╔════════════════════════════════════╗")
                .withStyle(ChatFormatting.DARK_GRAY);
        source.sendSuccess(() -> header, false);

        MutableComponent title = Component.literal("║ ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(module.getDisplayName()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" ║").withStyle(ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> title, false);

        MutableComponent divider = Component.literal("╠════════════════════════════════════╣")
                .withStyle(ChatFormatting.DARK_GRAY);
        source.sendSuccess(() -> divider, false);

        ChatFormatting effectiveColor = effectiveEnabled ? ChatFormatting.GREEN : ChatFormatting.RED;
        MutableComponent statusLine = Component.literal("║ Status: ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(effectiveEnabled ? "✓ ENABLED" : "✗ DISABLED")
                        .withStyle(effectiveColor, ChatFormatting.BOLD))
                .append(Component.literal(" ║").withStyle(ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> statusLine, false);

        MutableComponent configLine = Component.literal("║ Config: ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(configEnabled ? "ON" : "OFF")
                        .withStyle(configEnabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(" ║").withStyle(ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> configLine, false);

        if (runtimeOverride != null) {
            MutableComponent runtimeLine = Component.literal("║ Runtime: ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(runtimeOverride ? "▲ ON" : "▼ OFF")
                            .withStyle(runtimeOverride ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY))
                    .append(Component.literal(" ║").withStyle(ChatFormatting.DARK_GRAY));
            source.sendSuccess(() -> runtimeLine, false);
        }

        MutableComponent footer = Component.literal("╚════════════════════════════════════╝")
                .withStyle(ChatFormatting.DARK_GRAY);
        source.sendSuccess(() -> footer, false);

        return 1;
    }

    private int executeSetModuleRuntimeEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String moduleId = StringArgumentType.getString(context, "module_id");
        CommandSourceStack source = context.getSource();
        ModuleManager moduleManager = ModuleManager.getInstance();

        if (!moduleManager.setRuntimeModuleEnabled(moduleId, enabled)) {
            source.sendFailure(Component.literal("Unknown module: " + moduleId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format(
                "Runtime override set: %s = %s (effective=%s)",
                moduleId,
                enabled,
                moduleManager.isModuleEnabled(moduleId)
        )).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int executeClearModuleRuntimeOverride(CommandContext<CommandSourceStack> context) {
        String moduleId = StringArgumentType.getString(context, "module_id");
        CommandSourceStack source = context.getSource();
        ModuleManager moduleManager = ModuleManager.getInstance();

        if (moduleManager.getModule(moduleId) == null) {
            source.sendFailure(Component.literal("Unknown module: " + moduleId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean cleared = moduleManager.clearRuntimeModuleOverride(moduleId);
        source.sendSuccess(() -> Component.literal(String.format(
                "Runtime override %s for %s. Effective=%s",
                cleared ? "cleared" : "was not set",
                moduleId,
                moduleManager.isModuleEnabled(moduleId)
        )).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private int executeHauntedHouseWhereAmI(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Module module = ModuleManager.getInstance().getModule("haunted_house");

        if (!(module instanceof HauntedHouseModule hauntedHouseModule)) {
            source.sendFailure(Component.literal("Haunted House module is not registered")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("This command can only be used by a player")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        HauntedHouseModule.PlayerLocationState state = hauntedHouseModule.getPlayerLocationState(player);
        String stateText = switch (state) {
            case INSIDE -> "inside";
            case OUTSIDE_IN_STRUCTURE -> "outside (inside structure area)";
            case OUTSIDE_STRUCTURE -> "outside structure";
        };

        source.sendSuccess(() -> Component.literal("Haunted House location state: " + stateText)
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
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

        @SubscribeEvent
        static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ThirstTooltipData.class, ThirstClientTooltip::new);
        }
    }
}
