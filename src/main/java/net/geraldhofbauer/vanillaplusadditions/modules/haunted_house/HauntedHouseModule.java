package net.geraldhofbauer.vanillaplusadditions.modules.haunted_house;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.Map;

public class HauntedHouseModule extends AbstractModule<
        HauntedHouseModule,
        AbstractModuleConfig.DefaultModuleConfig<HauntedHouseModule>
        > {
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
            EntityType<?> murmurType = BuiltInRegistries.ENTITY_TYPE.get(murmurId);

            if (murmurType == null) {
                getLogger().error("Failed to find Murmur entity type from Alex's Mobs");
                return;
            }

            // Create the Murmur entity
            Entity murmur = murmurType.create(level);
            if (murmur == null) {
                getLogger().error("Failed to create Murmur entity");
                return;
            }

            // Position the Murmur at the same location as the original witch
            murmur.moveTo(originalEntity.getX(), originalEntity.getY(), originalEntity.getZ(),
                    originalEntity.getYRot(), originalEntity.getXRot());

            // Finalize the spawn
            if (murmur instanceof net.minecraft.world.entity.Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(murmur.blockPosition()),
                        spawnType, null);
            }

            // Add the Murmur to the world
            level.addFreshEntity(murmur);

            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Spawned Murmur in Witch Villa at {}", murmur.blockPosition());
            }

        } catch (Exception e) {
            getLogger().error("Failed to spawn Murmur in Witch Villa", e);
        }
    }

}
