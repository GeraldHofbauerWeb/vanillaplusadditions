package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for handling worldgen crash guard operations.
 * Automatically deletes corrupted region files and broadcasts messages.
 */
public final class WorldgenGuardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldgenGuardService.class);

    private WorldgenGuardService() {
        // Utility class
    }

    /**
     * Deletes the region file(s) corresponding to the problematic chunk
     * and broadcasts a warning message to all players.
     *
     * @param chunkPos The chunk that caused the worldgen error
     * @param level The server level for broadcasting and path resolution
     */
    public static void deleteCorruptedRegionAndBroadcast(ChunkPos chunkPos, ServerLevel level) {
        // Calculate region coordinates (each region is 32x32 chunks)
        int regionX = chunkPos.x >> 5;
        int regionZ = chunkPos.z >> 5;

        List<String> deletedFiles = new ArrayList<>();

        // Get dimension path and construct region directory
        Path dimensionPath;
        try {
            // For the overworld, this is world/region/
            // For other dimensions, it's world/dimensions/<namespace>:<name>/region/
            String dimensionKey = level.dimension().location().toString();

            Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);

            if ("minecraft:overworld".equals(dimensionKey)) {
                dimensionPath = worldRoot;
            } else {
                // Dimensions are stored in world/dimensions/<namespace>/<name>/
                String[] parts = dimensionKey.split(":");
                String namespace = parts.length > 0 ? parts[0] : "minecraft";
                String name = parts.length > 1 ? parts[1] : "nether";
                dimensionPath = worldRoot.resolve("dimensions").resolve(namespace).resolve(name);
            }
        } catch (Exception e) {
            LOGGER.warn("[Worldgen Guard] Failed to get dimension path: {}", e.getMessage());
            return;
        }

        // Delete mca file (main chunks)
        Path regionPath = dimensionPath.resolve("region").resolve("r." + regionX + "." + regionZ + ".mca");
        if (Files.exists(regionPath)) {
            try {
                Files.delete(regionPath);
                deletedFiles.add("region/r." + regionX + "." + regionZ + ".mca");
                LOGGER.info("[Worldgen Guard] Deleted corrupted region file: {}", regionPath);
            } catch (IOException e) {
                LOGGER.warn("[Worldgen Guard] Failed to delete region file {}: {}", regionPath, e.getMessage());
            }
        }

        // Delete poi file (points of interest)
        Path poiPath = dimensionPath.resolve("poi").resolve("r." + regionX + "." + regionZ + ".mca");
        if (Files.exists(poiPath)) {
            try {
                Files.delete(poiPath);
                deletedFiles.add("poi/r." + regionX + "." + regionZ + ".mca");
                LOGGER.info("[Worldgen Guard] Deleted corrupted POI file: {}", poiPath);
            } catch (IOException e) {
                LOGGER.warn("[Worldgen Guard] Failed to delete POI file {}: {}", poiPath, e.getMessage());
            }
        }

        // Delete entities file if exists
        Path entitiesPath = dimensionPath.resolve("entities").resolve("r." + regionX + "." + regionZ + ".mca");
        if (Files.exists(entitiesPath)) {
            try {
                Files.delete(entitiesPath);
                deletedFiles.add("entities/r." + regionX + "." + regionZ + ".mca");
                LOGGER.info("[Worldgen Guard] Deleted corrupted entities file: {}", entitiesPath);
            } catch (IOException e) {
                LOGGER.warn("[Worldgen Guard] Failed to delete entities file {}: {}", entitiesPath, e.getMessage());
            }
        }

        // Broadcast message to all players
        if (!deletedFiles.isEmpty()) {
            String fileList = String.join(", ", deletedFiles);
            String message = "[Worldgen Guard] Deleted corrupted region files at chunk " + chunkPos.x + "," + chunkPos.z
                    + " in " + level.dimension().location() + ": " + fileList;

            MessageBroadcaster.broadcast(level, message);
            LOGGER.warn("[Worldgen Guard] Broadcast message: {}", message);
        }
    }
}

