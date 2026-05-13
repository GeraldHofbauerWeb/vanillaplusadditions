package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.core.ModulesConfig;
import net.geraldhofbauer.vanillaplusadditions.util.WorldgenGuardService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Emergency crash guard for worldgen collision issues.
 *
 * <p>This catches IndexOutOfBoundsException and other exceptions from structure start and surface
 * generation only when enabled in config, so affected servers can keep running while root-cause
 * mods with conflicting worldgen transformations are isolated.
 *
 * <p>Known conflicts: lithostitched, mowziesmobs, yungsapi, mr_dungeons_andtaverns cause
 * negative Aquifer indices when their structure generation interferes with Noise-based
 * chunk generation.
 *
 * NOTE: This mixin targets net.minecraft.world.level.chunk.status.ChunkStatusTasks
 * At runtime, the target class is correctly resolved through Mixin's bytecode transformation.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.status.ChunkStatusTasks")
public abstract class ChunkStatusTasksMixin {
    @Unique
    private static final Logger VPA_LOGGER = LoggerFactory.getLogger(ChunkStatusTasksMixin.class);

    @Redirect(
            method = "generateStructureStarts",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;createStructures"
                            + "(Lnet/minecraft/core/RegistryAccess;"
                            + "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"
                            + "Lnet/minecraft/world/level/StructureManager;"
                            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;)V"
            )
    )
    private static void guardCreateStructures(
            ChunkGenerator generator,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager
    ) {
        try {
            generator.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
        } catch (Exception exception) {
            handleWorldgenException(exception, chunk, "structure generation");
        }
    }

    @Unique
    private static void handleWorldgenExceptionInner(Exception exception, ChunkAccess chunk, String phase) {
        // Only catch IndexOutOfBoundsException or IllegalStateException in critical phases
        if (!(exception instanceof IndexOutOfBoundsException)
                && !(exception instanceof IllegalStateException)) {
            throw new RuntimeException(exception);
        }

        if (!ModulesConfig.isWorldgenCrashGuardEnabled()) {
            throw new RuntimeException(exception);
        }

        ChunkPos chunkPos = chunk.getPos();
        String message = String.format(
                "[Worldgen Crash Guard] Suppressed %s at chunk %d,%d during %s",
                exception.getClass().getSimpleName(),
                chunkPos.x,
                chunkPos.z,
                phase
        );

        VPA_LOGGER.error(
                "{} (temporary workaround while isolating conflicting worldgen mods)",
                message,
                exception
        );

        // Auto-delete corrupted region files and broadcast warning
        try {
            Object level = chunk.getLevel();
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && !serverLevel.isClientSide()) {
                WorldgenGuardService.deleteCorruptedRegionAndBroadcast(chunkPos, serverLevel);
            }
        } catch (Exception e) {
            VPA_LOGGER.warn("Failed to delete corrupted region files: {}", e.getMessage());
        }

        if (ModulesConfig.isGlobalDebugLoggingEnabled()) {
            VPA_LOGGER.warn("{} - Debug enabled, investigate conflicting worldgen mods", message);
        }
    }

    private static void handleWorldgenException(Exception exception, ChunkAccess chunk, String phase) {
        handleWorldgenExceptionInner(exception, chunk, phase);
    }
}

