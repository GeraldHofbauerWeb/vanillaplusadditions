package net.geraldhofbauer.vanillaplusadditions.modules.haunted_house;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
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
        AbstractModuleConfig.DefaultModuleConfig<HauntedHouseModule>
        > {
    
    private static final double REPLACEMENT_RATE = 0.10; // 10% chance to replace witch with murmur
    private static final Random RANDOM = new Random();
    
    // Track murmurs that should be invisible and whether they've been spotted
    private final HashMap<UUID, Boolean> invisibleMurmurs = new HashMap<>();
    
    public HauntedHouseModule() {
        super("haunted_house",
                "Haunted House",
                "Spawns Murmurs from Alex's Mobs in Dungeons and Taverns' Witch Villas",
                AbstractModuleConfig::createDefault
        );
    }

    @Override
    protected boolean shouldInitialize() {
        // Check for required mods: Alex's Mobs and Dungeons and Taverns
        final boolean alexMobsLoaded = ModList.get().isLoaded("alexsmobs");
        final boolean dungeonsAndTavernsLoaded = ModList.get().isLoaded("mr_dungeons_andtaverns");

        final boolean modsFound = alexMobsLoaded && dungeonsAndTavernsLoaded;

        if (!modsFound) {
            StringBuilder missingMods = new StringBuilder();
            if (!alexMobsLoaded) {
                missingMods.append("Alex's Mobs (alexsmobs)");
            }
            if (!dungeonsAndTavernsLoaded) {
                if (missingMods.length() > 0) {
                    missingMods.append(" and ");
                }
                missingMods.append("Dungeons and Taverns (mr_dungeons_andtaverns)");
            }
            getLogger().warn("Haunted House module not initialized - Missing required mods: {}", missingMods.toString());
        }

        return modsFound;
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Haunted House module initialized - Murmurs may now spawn in Witch Villas!");
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
        return false;
    }

    /**
     * Event handler that replaces witch spawns with murmurs in witch villa structures.
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

        // Check if the entity is a witch
        if (!event.getEntity().getType().equals(EntityType.WITCH)) {
            return;
        }

        // Cast to ServerLevel to access structure manager
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Check if the witch is spawning inside a Witch Villa structure
        BlockPos spawnPos = event.getEntity().blockPosition();
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(spawnPos);

        if (allStructures.isEmpty()) {
            return;
        }

        // Check if any of the structures is a Witch Villa from nova_structures mod
        boolean insideWitchVilla = false;
        for (Structure structure : allStructures.keySet()) {
            ResourceLocation structureLocation = serverLevel.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(structure);

            if (structureLocation != null && structureLocation.toString().contains("witch_villa")) {
                insideWitchVilla = true;
                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Detected witch spawn inside Witch Villa at {}", spawnPos);
                }
                break;
            }
        }

        if (!insideWitchVilla) {
            return;
        }
        
        // Only replace 10% of witch spawns with murmurs
        if (RANDOM.nextDouble() >= REPLACEMENT_RATE) {
            return;
        }
        
        // Cancel the witch spawn and replace it with a Murmur
        event.setSpawnCancelled(true);

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Cancelled witch spawn in Witch Villa at {}", spawnPos);
        }

        // Spawn a Murmur in place of the witch
        spawnMurmur(serverLevel, event.getEntity(), event.getSpawnType());
    }

    /**
     * Spawns a Murmur entity from Alex's Mobs at the position of the cancelled witch spawn.
     */
    private void spawnMurmur(ServerLevel level, Entity originalEntity, MobSpawnType spawnType) {
        try {
            // Get the Murmur entity type from Alex's Mobs
            ResourceLocation murmurId = ResourceLocation.fromNamespaceAndPath("alexsmobs", "murmur");
            Optional<EntityType<?>> murmurType = BuiltInRegistries.ENTITY_TYPE.getOptional(murmurId);

            if (murmurType.isEmpty()) {
                getLogger().error("Failed to find Murmur entity type from Alex's Mobs");
                return;
            }

            // Create the Murmur entity
            Entity murmur = murmurType.get().create(level);
            if (murmur == null) {
                getLogger().error("Failed to create Murmur entity");
                return;
            }

            // Position the Murmur at the same location as the original witch
            murmur.moveTo(originalEntity.getX(), originalEntity.getY(), originalEntity.getZ(),
                    originalEntity.getYRot(), originalEntity.getXRot());

            // Make the Murmur invisible by default
            if (murmur instanceof Mob mob) {
                mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                invisibleMurmurs.put(murmur.getUUID(), false); // false = not yet spotted
            }
            
            // Add the Murmur to the world
            level.addFreshEntity(murmur);
            
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Spawned invisible Murmur in Witch Villa at {}", murmur.blockPosition());
            }

        } catch (Exception e) {
            getLogger().error("Failed to spawn Murmur in Witch Villa", e);
        }
    }
    
    /**
     * Event handler that checks if players are looking at invisible murmurs and makes them visible.
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
        
        // Check if this is a murmur we're tracking
        if (!(event.getEntity() instanceof Mob murmur)) {
            return;
        }
        
        UUID murmurId = murmur.getUUID();
        if (!invisibleMurmurs.containsKey(murmurId)) {
            return;
        }
        
        // If already spotted, no need to check further
        if (invisibleMurmurs.get(murmurId)) {
            return;
        }
        
        // Only process on server side
        if (murmur.level().isClientSide) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) murmur.level();
        Vec3 murmurPos = murmur.getEyePosition();
        
        // Check if any non-spectator player is looking at the murmur
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isSpectator()) {
                continue;
            }
            
            // Check if player is within reasonable distance (32 blocks)
            if (player.distanceToSqr(murmur) > 32 * 32) {
                continue;
            }
            
            // Get player's look vector
            Vec3 playerEyePos = player.getEyePosition();
            Vec3 playerLookVec = player.getLookAngle();
            
            // Calculate vector from player to murmur
            Vec3 toMurmur = murmurPos.subtract(playerEyePos).normalize();
            
            // Check if player is looking roughly in the direction of the murmur
            // (dot product > 0.95 means within about 18 degrees)
            double dotProduct = playerLookVec.dot(toMurmur);
            if (dotProduct < 0.95) {
                continue;
            }
            
            // Perform raycast to check if player has line of sight to murmur
            ClipContext clipContext = new ClipContext(
                    playerEyePos,
                    murmurPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            );
            HitResult hitResult = serverLevel.clip(clipContext);
            
            // If raycast hits something before reaching the murmur, continue
            if (hitResult.getType() != HitResult.Type.MISS) {
                double distanceToHit = hitResult.getLocation().distanceToSqr(playerEyePos);
                double distanceToMurmur = murmurPos.distanceToSqr(playerEyePos);
                if (distanceToHit < distanceToMurmur - 0.5) { // Small tolerance
                    continue;
                }
            }
            
            // Player is looking at the murmur! Make it visible
            murmur.removeEffect(MobEffects.INVISIBILITY);
            invisibleMurmurs.put(murmurId, true); // Mark as spotted
            
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Player {} spotted Murmur at {}", player.getName().getString(), murmur.blockPosition());
            }
            
            break; // No need to check other players
        }
    }

}
