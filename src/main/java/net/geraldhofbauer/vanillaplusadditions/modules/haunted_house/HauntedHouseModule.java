package net.geraldhofbauer.vanillaplusadditions.modules.haunted_house;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.config.HauntedHouseConfig;
import net.geraldhofbauer.vanillaplusadditions.util.MessageBroadcaster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.*;

public class HauntedHouseModule extends AbstractModule<
        HauntedHouseModule,
        HauntedHouseConfig
        > {
    
    private static final Random RANDOM = new Random();
    
    // Track murmurs that should be invisible and whether they've been spotted
    private final HashMap<UUID, Boolean> invisibleMurmurs = new HashMap<>();
    
    // Track players inside target structures for fog effect
    private final HashMap<UUID, Long> playersInStructure = new HashMap<>();
    
    public HauntedHouseModule() {
        super("haunted_house",
                "Haunted House",
                "Spawns Murmurs from Alex's Mobs in Dungeons and Taverns' Witch Villas",
                HauntedHouseConfig::new
        );
    }

    @Override
    protected boolean shouldInitialize() {
        // TODO: Temporarily disabled for testing with zombies instead of murmurs
        // Check for required mods: Alex's Mobs and Dungeons and Taverns
        // final boolean alexMobsLoaded = ModList.get().isLoaded("alexsmobs");
         final boolean dungeonsAndTavernsLoaded = ModList.get().isLoaded("mr_dungeons_andtaverns");

        // final boolean modsFound = alexMobsLoaded && dungeonsAndTavernsLoaded;

        // if (!modsFound) {
        //     StringBuilder missingMods = new StringBuilder();
        //     if (!alexMobsLoaded) {
        //         missingMods.append("Alex's Mobs (alexsmobs)");
        //     }
        //     if (!dungeonsAndTavernsLoaded) {
        //         if (missingMods.length() > 0) {
        //             missingMods.append(" and ");
        //         }
        //         missingMods.append("Dungeons and Taverns (mr_dungeons_andtaverns)");
        //     }
        //     getLogger().warn("Haunted House module not initialized - Missing required mods: {}", missingMods.toString());
        // }

        // return modsFound;
        return dungeonsAndTavernsLoaded;
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Haunted House module initialized - Testing with zombies instead of murmurs!");
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Haunted House module common setup complete");
        }
    }

    // TODO: Re-enable when Alex's Mobs becomes available for Minecraft 1.21.x
    // Currently disabled by default because Alex's Mobs is not yet available for 1.21.x
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    /**
     * Event handler that boosts witch spawn rates in target structures.
     * Uses HIGHEST priority to run before the replacement handler.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBoostWitchSpawns(FinalizeSpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Cast to ServerLevel
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Check if witch spawn boost is enabled
        double boostChance = getConfig().getWitchSpawnBoostChance();
        if (boostChance <= 0) {
            return;
        }

        // Get the entity type
        EntityType<?> entityType = event.getEntity().getType();
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        
        if (entityTypeId == null) {
            return;
        }

        String mobId = entityTypeId.toString();
        
        // Don't boost witches themselves
        if (mobId.equals("minecraft:witch")) {
            return;
        }

        // Check if we're in a target structure
        BlockPos spawnPos = event.getEntity().blockPosition();
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(spawnPos);

        if (allStructures.isEmpty()) {
            return;
        }

        // Check if any structure is a target
        boolean insideTargetStructure = false;
        for (Structure structure : allStructures.keySet()) {
            ResourceLocation structureLocation = serverLevel.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(structure);

            if (structureLocation != null && getConfig().isTargetStructure(structureLocation.toString())) {
                insideTargetStructure = true;
                break;
            }
        }

        if (!insideTargetStructure) {
            return;
        }

        // Roll for witch spawn boost
        double randomValue = RANDOM.nextDouble();
        if (randomValue < boostChance) {
            // Cancel current spawn and replace with witch
            event.setSpawnCancelled(true);

            MessageBroadcaster.broadcastDebugWithLocation(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "🧙 Boosted witch spawn: Replaced " + mobId + " with witch (roll: " + 
                            String.format("%.2f", randomValue) + " < " + String.format("%.2f", boostChance) + ")",
                    spawnPos,
                    getLogger()
            );

            spawnWitch(serverLevel, event.getEntity(), event.getSpawnType());
        }
    }

    /**
     * Spawns a witch at the given location.
     */
    private void spawnWitch(ServerLevel level, Entity originalEntity, MobSpawnType spawnType) {
        try {
            EntityType<?> witchType = EntityType.WITCH;
            Entity witch = witchType.create(level);
            
            if (witch == null) {
                getLogger().error("Failed to create witch entity");
                return;
            }

            // Position the witch at the same location as the original mob
            witch.moveTo(originalEntity.getX(), originalEntity.getY(), originalEntity.getZ(),
                    originalEntity.getYRot(), originalEntity.getXRot());

            // Add the witch to the world
            level.addFreshEntity(witch);

        } catch (Exception e) {
            getLogger().error("Failed to spawn witch", e);
        }
    }

    /**
     * Event handler that replaces mob spawns with zombies (for testing) in configured structures.
     * Uses HIGH priority to ensure we can cancel the spawn before other mods process it.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onShouldSpawnHauntedEntity(FinalizeSpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Cast to ServerLevel for broadcasting
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Get the entity type as a ResourceLocation
        EntityType<?> entityType = event.getEntity().getType();
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        
        if (entityTypeId == null) {
            return;
        }
        
        // Check if this mob type should be replaced according to configuration
        String mobId = entityTypeId.toString();
        
        MessageBroadcaster.broadcastDebug(
                serverLevel,
                getConfig().shouldDebugLog(),
                "🔍 Step 1: Detected mob spawn: " + mobId,
                getLogger()
        );
        
        if (!getConfig().shouldReplaceMob(mobId)) {
            return;
        }
        
        MessageBroadcaster.broadcastDebug(
                serverLevel,
                getConfig().shouldDebugLog(),
                "✅ Step 2: Mob " + mobId + " is in replacement list",
                getLogger()
        );

        // Check if the mob is spawning inside a structure
        BlockPos spawnPos = event.getEntity().blockPosition();
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(spawnPos);

        if (allStructures.isEmpty()) {
            MessageBroadcaster.broadcastDebugWithLocation(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "❌ Step 3: No structures found at spawn location",
                    spawnPos,
                    getLogger()
            );
            return;
        }
        
        MessageBroadcaster.broadcastDebugWithLocation(
                serverLevel,
                getConfig().shouldDebugLog(),
                "✅ Step 3: Found " + allStructures.size() + " structure(s) at location",
                spawnPos,
                getLogger()
        );

        // Check if any of the structures is in the target structure list
        boolean insideTargetStructure = false;
        StringBuilder structureList = new StringBuilder();
        
        for (Structure structure : allStructures.keySet()) {
            ResourceLocation structureLocation = serverLevel.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(structure);

            if (structureLocation != null) {
                if (structureList.length() > 0) {
                    structureList.append(", ");
                }
                structureList.append(structureLocation.toString());
                
                if (getConfig().isTargetStructure(structureLocation.toString())) {
                    insideTargetStructure = true;
                    MessageBroadcaster.broadcastDebug(
                            serverLevel,
                            getConfig().shouldDebugLog(),
                            "✅ Step 4: Found target structure: " + structureLocation,
                            getLogger()
                    );
                    break;
                }
            }
        }

        if (!insideTargetStructure) {
            MessageBroadcaster.broadcastDebug(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "❌ Step 4: None of the structures match target list. Found: " + structureList,
                    getLogger()
            );
            return;
        }
        
        // Check if this spawn should be replaced based on configured replacement rate
        double replacementRate = getConfig().getReplacementRate(mobId);
        double randomValue = RANDOM.nextDouble();
        
        MessageBroadcaster.broadcastDebug(
                serverLevel,
                getConfig().shouldDebugLog(),
                String.format("🎲 Step 5: Replacement roll: %.2f < %.2f = %s", 
                        randomValue, replacementRate, randomValue < replacementRate),
                getLogger()
        );
        
        if (randomValue >= replacementRate) {
            return;
        }
        
        // Cancel the mob spawn and replace it with a zombie (for testing)
        event.setSpawnCancelled(true);

        // Broadcast debug message about cancelled spawn
        MessageBroadcaster.broadcastDebugWithLocation(
                serverLevel,
                getConfig().shouldDebugLog(),
                "❌ Cancelled " + mobId + " spawn in target structure",
                spawnPos,
                getLogger()
        );

        // Spawn a zombie in place of the cancelled mob (for testing)
        spawnReplacementMob(serverLevel, event.getEntity(), event.getSpawnType());
    }

    /**
     * Spawns a replacement mob at the position of the cancelled spawn.
     * TODO: For testing, spawns zombies. Will spawn murmurs when Alex's Mobs is available.
     */
    private void spawnReplacementMob(ServerLevel level, Entity originalEntity, MobSpawnType spawnType) {
        try {
            // TODO: Temporarily using zombies for testing instead of murmurs
            // Get the Zombie entity type (for testing)
            // ResourceLocation murmurId = ResourceLocation.fromNamespaceAndPath("alexsmobs", "murmur");
            // Optional<EntityType<?>> murmurType = BuiltInRegistries.ENTITY_TYPE.getOptional(murmurId);
            
            ResourceLocation zombieId = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");
            Optional<EntityType<?>> zombieType = BuiltInRegistries.ENTITY_TYPE.getOptional(zombieId);

            if (zombieType.isEmpty()) {
                getLogger().error("Failed to find Zombie entity type");
                return;
            }

            // Create the Zombie entity
            Entity replacementMob = zombieType.get().create(level);
            if (replacementMob == null) {
                getLogger().error("Failed to create replacement mob");
                return;
            }

            // Position the replacement mob at the same location as the original
            replacementMob.moveTo(originalEntity.getX(), originalEntity.getY(), originalEntity.getZ(),
                    originalEntity.getYRot(), originalEntity.getXRot());

            // Make the replacement mob invisible by default
            if (replacementMob instanceof Mob mob) {
                mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                invisibleMurmurs.put(replacementMob.getUUID(), false); // false = not yet spotted
            }
            
            // Add the replacement mob to the world
            level.addFreshEntity(replacementMob);
            
            // Broadcast debug message to all players if debug logging is enabled
            MessageBroadcaster.broadcastDebugWithLocation(
                    level,
                    getConfig().shouldDebugLog(),
                    "👻 Spawned invisible replacement mob (zombie for testing)",
                    replacementMob.blockPosition(),
                    getLogger()
            );

        } catch (Exception e) {
            getLogger().error("Failed to spawn replacement mob", e);
        }
    }
    
    /**
     * Event handler that checks if players are looking at invisible replacement mobs and makes them visible.
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        
        // Only check every 10 ticks (0.5 seconds) for performance
        if (event.getEntity().tickCount % 10 != 0) {
            return;
        }
        
        // Check if this is a replacement mob we're tracking
        if (!(event.getEntity() instanceof Mob replacementMob)) {
            return;
        }
        
        UUID mobId = replacementMob.getUUID();
        if (!invisibleMurmurs.containsKey(mobId)) {
            return;
        }
        
        // If already spotted, no need to check further
        if (invisibleMurmurs.get(mobId)) {
            return;
        }
        
        // Only process on server side
        if (replacementMob.level().isClientSide) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) replacementMob.level();
        Vec3 mobPos = replacementMob.getEyePosition();
        
        // Check if any non-spectator player is looking at the replacement mob
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isSpectator()) {
                continue;
            }
            
            // Check if player is within reasonable distance (32 blocks)
            if (player.distanceToSqr(replacementMob) > 32 * 32) {
                continue;
            }
            
            // Get player's look vector
            Vec3 playerEyePos = player.getEyePosition();
            Vec3 playerLookVec = player.getLookAngle();
            
            // Calculate vector from player to replacement mob
            Vec3 toMob = mobPos.subtract(playerEyePos).normalize();
            
            // Check if player is looking roughly in the direction of the replacement mob
            // (dot product > 0.95 means within about 18 degrees)
            double dotProduct = playerLookVec.dot(toMob);
            if (dotProduct < 0.95) {
                continue;
            }
            
            // Perform raycast to check if player has line of sight to replacement mob
            ClipContext clipContext = new ClipContext(
                    playerEyePos,
                    mobPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            );
            HitResult hitResult = serverLevel.clip(clipContext);
            
            // If raycast hits something before reaching the replacement mob, continue
            if (hitResult.getType() != HitResult.Type.MISS) {
                double distanceToHit = hitResult.getLocation().distanceToSqr(playerEyePos);
                double distanceToMob = mobPos.distanceToSqr(playerEyePos);
                if (distanceToHit < distanceToMob - 0.5) { // Small tolerance
                    continue;
                }
            }
            
            // Player is looking at the replacement mob! Make it visible
            replacementMob.removeEffect(MobEffects.INVISIBILITY);
            invisibleMurmurs.put(mobId, true); // Mark as spotted
            
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Player {} spotted replacement mob at {}", player.getName().getString(), replacementMob.blockPosition());
            }
            
            break; // No need to check other players
        }
    }
    
    /**
     * Event handler that applies fog effect to players inside target structures.
     */
    @SubscribeEvent
    public void onPlayerTick(EntityTickEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }
        
        // Only check players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        // Only process on server side
        if (player.level().isClientSide) {
            return;
        }
        
        // Only check every 20 ticks (1 second) for performance
        if (player.tickCount % 20 != 0) {
            return;
        }
        
        // Check if fog effect is enabled
        if (!getConfig().isFogEffectEnabled()) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();
        
        // Check if player is in a target structure
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(playerPos);
        
        boolean insideTargetStructure = false;
        if (!allStructures.isEmpty()) {
            for (Structure structure : allStructures.keySet()) {
                ResourceLocation structureLocation = serverLevel.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .getKey(structure);
                
                if (structureLocation != null && getConfig().isTargetStructure(structureLocation.toString())) {
                    insideTargetStructure = true;
                    break;
                }
            }
        }
        
        UUID playerId = player.getUUID();
        
        if (insideTargetStructure) {
            // Player is inside - apply fog effect
            int amplifier = getConfig().getFogEffectAmplifier();
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, amplifier, false, false));
            
            // Track first entry for debug message
            if (!playersInStructure.containsKey(playerId)) {
                playersInStructure.put(playerId, System.currentTimeMillis());
                
                MessageBroadcaster.broadcastDebug(
                        serverLevel,
                        getConfig().shouldDebugLog(),
                        "🌫️ Player " + player.getName().getString() + " entered haunted structure - applying fog effect",
                        getLogger()
                );
            }
        } else {
            // Player left structure - remove from tracking
            if (playersInStructure.remove(playerId) != null) {
                MessageBroadcaster.broadcastDebug(
                        serverLevel,
                        getConfig().shouldDebugLog(),
                        "☀️ Player " + player.getName().getString() + " left haunted structure - fog will dissipate",
                        getLogger()
                );
            }
        }
    }

}
