package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.core.ModulesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Intercepts the CompletableFuture.handle() callback in GenerationChunkHolder.applyStep
 * to suppress known worldgen exceptions BEFORE they get wrapped in
 * ReportedException and crash the server.
 *
 * <p>Both exception types end up in the .handle() at line 67:
 * - ArrayIndexOutOfBoundsException from Aquifer (lithostitched/sable conflict)
 * - IllegalStateException "Parent chunk missing" (chunks outside world border)
 */
@Mixin(targets = "net.minecraft.server.level.GenerationChunkHolder")
public abstract class GenerationChunkHolderMixin {

    @Unique
    private static final Logger VPA_LOGGER = LoggerFactory.getLogger(GenerationChunkHolderMixin.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
            method = "applyStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;handle"
                            + "(Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletableFuture;",
                    ordinal = 0
            )
    )
    private CompletableFuture guardApplyStepHandle(
            CompletableFuture future,
            BiFunction originalHandler
    ) {
        if (!ModulesConfig.isWorldgenCrashGuardEnabled()) {
            return future.handle(originalHandler);
        }

        return future.handle((result, rawThrowable) -> {
            Throwable throwable = (rawThrowable instanceof Throwable t) ? t : null;
            if (throwable != null) {
                Throwable cause = throwable;
                while (cause != null) {
                    if (isKnownChunkGenException(cause)) {
                        VPA_LOGGER.error(
                                "[Worldgen Guard] Suppressed {} during async chunk generation: {} — "
                                        + "skipping chunk to prevent server crash (incompatible worldgen mods)",
                                cause.getClass().getSimpleName(),
                                cause.getMessage() != null ? cause.getMessage() : "(no message)"
                        );
                        // Return null to mark chunk as skipped instead of crashing server
                        return null;
                    }
                    cause = cause.getCause();
                }
            }
            // For unknown exceptions or no exception: delegate to original handler
            return originalHandler.apply(result, rawThrowable);
        });
    }

    @Unique
    private static boolean isKnownChunkGenException(Throwable t) {
        if (t instanceof ArrayIndexOutOfBoundsException) {
            return true;
        }
        if (t instanceof IllegalStateException
                && t.getMessage() != null
                && t.getMessage().contains("Parent chunk missing")) {
            return true;
        }
        return false;
    }
}

