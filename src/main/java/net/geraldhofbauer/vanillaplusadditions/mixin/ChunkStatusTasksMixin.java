package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.core.ModulesConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Emergency crash guard for structure-start worldgen.
 *
 * <p>This catches IndexOutOfBoundsException from structure start generation only when
 * enabled in config, so affected servers can keep running while root-cause mods are isolated.
 */
@Mixin(ChunkStatusTasks.class)
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
        } catch (IndexOutOfBoundsException exception) {
            if (!ModulesConfig.isWorldgenCrashGuardEnabled()) {
                throw exception;
            }

            ChunkPos chunkPos = chunk.getPos();
            String message = String.format(
                    "[Worldgen Crash Guard] Suppressed IndexOutOfBoundsException at chunk %d,%d",
                    chunkPos.x,
                    chunkPos.z
            );

            VPA_LOGGER.error(
                    "{} (temporary workaround while isolating conflicting worldgen mods)",
                    message,
                    exception
            );

            if (ModulesConfig.isGlobalDebugLoggingEnabled()) {
                VPA_LOGGER.warn("{} - Debug enabled, investigate conflicting worldgen mods", message);
            }
        }
    }
}

