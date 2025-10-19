package net.geraldhofbauer.vanillaplusadditions.modules.mob_glow;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_glow.config.MobGlowConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mob Glow Module
 * <p>
 * Provides a command to make all mobs of a specified type glow for a given duration.
 * This is useful for server administrators and operators to highlight specific mob types
 * for debugging, demonstration, or gameplay purposes.
 * <p>
 * Features:
 * - /mobglow command with entity type and duration parameters
 * - OP-only command (configurable)
 * - Support for infinite duration or specific time limits
 * - Configurable limits on affected mobs and duration
 * - Auto-completion for entity types
 */
public class MobGlowModule extends AbstractModule<MobGlowModule, MobGlowConfig> {

    // Track mobs that have glow effect applied by this command
    private final Map<UUID, EntityType<?>> trackedGlowingMobs = new HashMap<>();
    
    // Suggestion provider for entity types
    private static final SuggestionProvider<CommandSourceStack> ENTITY_TYPE_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggestResource(
                BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                        .filter(resourceLocation -> BuiltInRegistries.ENTITY_TYPE.get(resourceLocation)
                                != EntityType.PLAYER), // Exclude players
                builder
        );
    };

    public MobGlowModule() {
        super("mob_glow",
                "Mob Glow Command",
                "Provides a command to make all mobs of a specified type glow for debugging and visualization",
                MobGlowConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Mob Glow module initialized - /mobglow command ready!");
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Mob Glow module common setup complete");
        }
    }

    /**
     * Register the /mobglow command when commands are being registered
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Registering /mobglow command");
        }

        dispatcher.register(
                Commands.literal("mobglow")
                        .requires(source -> !getConfig().getRequireOpValue()
                                || source.hasPermission(2)) // OP level 2 required by default
                        .then(Commands.literal("all")
                                .then(Commands.literal("clear")
                                        .executes(this::executeClearAllGlow)
                                )
                        )
                        .then(Commands.argument("entity_type", ResourceLocationArgument.id())
                                .suggests(ENTITY_TYPE_SUGGESTIONS)
                                .executes(this::executeMobGlowInfinite) // Default to infinite
                                .then(Commands.literal("clear")
                                        .executes(this::executeClearEntityTypeGlow)
                                )
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(this::executeMobGlowWithDuration)
                                )
                        )
        );

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Successfully registered /mobglow command");
        }
    }

    /**
     * Execute mobglow command with infinite duration (default)
     */
    private int executeMobGlowInfinite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation entityTypeId = ResourceLocationArgument.getId(context, "entity_type");
        return executeMobGlow(context, entityTypeId, "infinite");
    }

    /**
     * Execute mobglow command with specified duration
     */
    private int executeMobGlowWithDuration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation entityTypeId = ResourceLocationArgument.getId(context, "entity_type");
        String durationStr = StringArgumentType.getString(context, "duration");
        return executeMobGlow(context, entityTypeId, durationStr);
    }
    
    /**
     * Execute clear all glow effects command
     */
    private int executeClearAllGlow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        // TODO: Permission check for clearing all (is op for now)

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Executing /mobglow all clear command");
        }
        
        int clearedCount = clearGlowEffects(source, null);
        
        if (clearedCount == 0) {
            source.sendSuccess(() -> Component.literal("No glowing entities found to clear")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            source.sendSuccess(() -> Component.literal("Cleared glow effect from " + clearedCount + " entities")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        
        return clearedCount;
    }
    
    /**
     * Execute clear specific entity type glow effects command
     */
    private int executeClearEntityTypeGlow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation entityTypeId = ResourceLocationArgument.getId(context, "entity_type");
        CommandSourceStack source = context.getSource();
        
        // Validate entity type
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) {
            source.sendFailure(Component.literal("Unknown entity type: " + entityTypeId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Executing /mobglow {} clear command", entityTypeId);
        }
        
        int clearedCount = clearGlowEffects(source, entityType);
        
        if (clearedCount == 0) {
            source.sendSuccess(() -> Component.literal("No glowing " + entityTypeId
                            + " entities found to clear")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            source.sendSuccess(() -> Component.literal("Cleared glow effect from " + clearedCount
                            + " " + entityTypeId + " entities")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        
        return clearedCount;
    }

    /**
     * Main execution logic for the mobglow command
     */
    private int executeMobGlow(CommandContext<CommandSourceStack> context, ResourceLocation entityTypeId,
            String durationStr) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        // TODO: Permission check for making mobs glow (is op for now)

        // Validate entity type
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) {
            source.sendFailure(Component.literal("Unknown entity type: " + entityTypeId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        if (entityType == EntityType.PLAYER) {
            source.sendFailure(Component.literal("Cannot apply glow effect to players")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Parse duration
        int durationTicks;
        boolean isInfinite = false;
        
        if ("infinite".equalsIgnoreCase(durationStr)) {
            int configDefault = getConfig().getDefaultDurationValue();
            if (configDefault == -1) {
                durationTicks = Integer.MAX_VALUE;
                isInfinite = true;
            } else {
                durationTicks = configDefault * 20; // Convert seconds to ticks
            }
        } else {
            try {
                int durationSeconds = Integer.parseInt(durationStr);
                
                // Check maximum duration limit
                int maxDuration = getConfig().getMaxDurationValue();
                if (maxDuration > 0 && durationSeconds > maxDuration) {
                    source.sendFailure(Component.literal("Duration cannot exceed " + maxDuration + " seconds")
                            .withStyle(ChatFormatting.RED));
                    return 0;
                }
                
                durationTicks = durationSeconds * 20; // Convert seconds to ticks
            } catch (NumberFormatException e) {
                source.sendFailure(Component.literal("Invalid duration: " + durationStr
                                + ". Use a number or 'infinite'")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Executing /mobglow command: entity_type={}, duration={} ({}ticks, infinite={})",
                    entityTypeId, durationStr, durationTicks, isInfinite);
        }

        // Apply glow effect to all matching entities
        AtomicInteger changedCount = new AtomicInteger(0);
        AtomicInteger totalWithEffectCount = new AtomicInteger(0);
        int maxMobs = getConfig().getMaxMobsPerCommandValue();
        
        if (source.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getAllEntities().forEach(entity -> {
                if (entity.getType() == entityType && entity instanceof LivingEntity livingEntity) {
                    boolean hadGlowEffect = livingEntity.hasEffect(MobEffects.GLOWING);
                    
                    // Apply glowing effect
                    MobEffectInstance glowEffect = new MobEffectInstance(MobEffects.GLOWING,
                            durationTicks, 0, false, true);
                    livingEntity.addEffect(glowEffect);
                    
                    // Track this mob
                    trackedGlowingMobs.put(entity.getUUID(), entityType);
                    totalWithEffectCount.incrementAndGet();
                    
                    // Count as changed if it didn't have the effect before or we're within the limit
                    if (!hadGlowEffect && (maxMobs == 0 || changedCount.get() < maxMobs)) {
                        changedCount.incrementAndGet();
                        
                        if (getConfig().shouldDebugLog()) {
                            getLogger().debug("Applied glow effect to {} at {}",
                                    entityType.getDescriptionId(), entity.blockPosition());
                        }
                    }
                }
            });
        }

        // Send result message
        if (totalWithEffectCount.get() == 0) {
            source.sendSuccess(() -> Component.literal("No " + entityTypeId
                            + " entities found to apply glow effect")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            String durationText = isInfinite ? "indefinitely" : "for " + (durationTicks / 20) + " seconds";
            if (changedCount.get() == 0) {
                source.sendSuccess(() -> Component.literal("All " + totalWithEffectCount.get() + " "
                                + entityTypeId + " entities already have glow effect")
                        .withStyle(ChatFormatting.YELLOW), true);
            } else {
                String message;
                if (changedCount.get() == totalWithEffectCount.get()) {
                    message = "Applied glow effect to " + changedCount.get() + " " + entityTypeId
                            + " entities " + durationText;
                } else {
                    message = "Applied glow effect to " + changedCount.get() + " new " + entityTypeId
                            + " entities " + durationText + " ("
                            + totalWithEffectCount.get()
                            + " total now glowing)";
                }
                source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
            }
            
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Successfully applied glow effect: {} changed, {} total of type {}",
                        changedCount.get(), totalWithEffectCount.get(), entityTypeId);
            }
        }

        return changedCount.get();
    }
    
    /**
     * Clears glow effects from tracked entities
     * 
     * @param source The command source
     * @param entityType The specific entity type to clear, or null for all types
     * @return The number of entities that had their glow effect removed
     */
    private int clearGlowEffects(CommandSourceStack source, EntityType<?> entityType) {
        AtomicInteger clearedCount = new AtomicInteger(0);
        
        if (source.getLevel() instanceof ServerLevel serverLevel) {
            // Create a copy of the keyset to avoid concurrent modification
            Set<UUID> mobsToCheck = new HashSet<>(trackedGlowingMobs.keySet());
            
            for (UUID mobUUID : mobsToCheck) {
                EntityType<?> trackedType = trackedGlowingMobs.get(mobUUID);
                
                // Skip if we're looking for a specific type and this isn't it
                if (entityType != null && trackedType != entityType) {
                    continue;
                }
                
                // Find the entity in the world
                Entity entity = serverLevel.getEntity(mobUUID);
                if (entity instanceof LivingEntity livingEntity
                        && livingEntity.hasEffect(MobEffects.GLOWING)) {
                    // Remove the glow effect
                    livingEntity.removeEffect(MobEffects.GLOWING);
                    clearedCount.incrementAndGet();
                    
                    if (getConfig().shouldDebugLog()) {
                        getLogger().debug("Removed glow effect from {} at {}",
                                trackedType.getDescriptionId(), entity.blockPosition());
                    }
                }
                
                // Remove from tracking regardless of whether entity was found
                // (entity might have despawned or died)
                trackedGlowingMobs.remove(mobUUID);
            }
            
            // Clean up any dead references (entities that are no longer in the world)
            cleanupDeadReferences(serverLevel);
        }
        
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Cleared glow effects from {} entities", clearedCount.get());
        }
        
        return clearedCount.get();
    }
    
    /**
     * Cleanup tracking for entities that no longer exist
     */
    private void cleanupDeadReferences(ServerLevel serverLevel) {
        Set<UUID> deadMobs = new HashSet<>();
        
        for (UUID mobUUID : trackedGlowingMobs.keySet()) {
            Entity entity = serverLevel.getEntity(mobUUID);
            if (entity == null || !entity.isAlive()) {
                deadMobs.add(mobUUID);
            }
        }
        
        for (UUID deadMob : deadMobs) {
            trackedGlowingMobs.remove(deadMob);
        }
        
        if (getConfig().shouldDebugLog() && !deadMobs.isEmpty()) {
            getLogger().debug("Cleaned up {} dead entity references from tracking", deadMobs.size());
        }
    }
}
