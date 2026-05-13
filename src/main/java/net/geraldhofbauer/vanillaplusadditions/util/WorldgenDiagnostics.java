package net.geraldhofbauer.vanillaplusadditions.util;

import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostics utility for worldgen crash guard.
 * Identifies known problematic mod combinations and provides guidance.
 */
public final class WorldgenDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldgenDiagnostics.class);

    // Known problematic mods and their typical issues
    private static final String[] PROBLEMATIC_MODS = {
            "lithostitched",        // Aquifer corruption
            "sable",                // Plot system + Aquifer conflicts
            "yungsapi",             // Structure API conflicts
            "mr_dungeons_andtaverns" // Structure gen conflicts
    };

    private WorldgenDiagnostics() {
        // Utility class
    }

    /**
     * Diagnose worldgen issues and provide recommendations.
     */
    public static void diagnoseAndReport() {
        LOGGER.info("=== Worldgen Guard Diagnostics ===");
        LOGGER.info("If you experience 'ArrayIndexOutOfBoundsException' or 'Parent chunk missing' errors,");
        LOGGER.info("this is likely caused by incompatible worldgen mod combinations.");
        LOGGER.info("");

        boolean hasProblematicMods = false;
        ModList modList = ModList.get();

        LOGGER.info("Checking for known problematic mods:");
        for (String modId : PROBLEMATIC_MODS) {
            if (modList != null && modList.isLoaded(modId)) {
                LOGGER.warn("  ⚠ {} is loaded (known to cause worldgen conflicts)", modId);
                hasProblematicMods = true;
            } else {
                LOGGER.info("  ✓ {} not loaded", modId);
            }
        }

        LOGGER.info("");
        if (hasProblematicMods) {
            LOGGER.warn("RECOMMENDATION: Disable one or more of the problematic mods above.");
            LOGGER.warn("Known safe combinations:");
            LOGGER.warn("  - Create + Vanilla worldgen (without mods above)");
            LOGGER.warn("  - Vanilla + Tough As Nails");
            LOGGER.warn("  - Vanilla curated single mod additions only");
        } else {
            LOGGER.info("No known problematic mods detected. Your mod setup should be safe.");
        }

        LOGGER.info("Enable 'worldgenCrashGuardEnabled' in config if crashes still occur (temporary workaround only).");
        LOGGER.info("=====================================");
    }
}

