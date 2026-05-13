package net.geraldhofbauer.vanillaplusadditions.mixin;
import net.geraldhofbauer.vanillaplusadditions.core.ModulesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
/**
 * Intercepts the CompletableFuture.handle() callback in GenerationChunkHolder.applyStep
 * to suppress known worldgen exceptions BEFORE they get wrapped in
 * ReportedException and crash the server.
 *
 * <p>Both exception types end up in the .handle() at line 67:
 * - ArrayIndexOutOfBoundsException from Aquifer (lithostitched/sable conflict)
 * - IllegalStateException "Parent chunk missing" (chunks outside world border)
 *
 * <p>Returns null to skip the problematic chunk instead of crashing.
 * Logs are rate-limited to prevent spam filling disk/causing lag.
 */
@Mixin(targets = "net.minecraft.server.level.GenerationChunkHolder")
public abstract class GenerationChunkHolderMixin {
    @Unique
    private static final Logger VPA_LOGGER = LoggerFactory.getLogger(GenerationChunkHolderMixin.class);
    @Unique
    private static final long LOG_COOLDOWN_MS = 5_000L;
    @Unique
    private static final AtomicLong LAST_LOG_MS = new AtomicLong(0L);
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
                        long now = System.currentTimeMillis();
                        long last = LAST_LOG_MS.get();
                        if (now - last >= LOG_COOLDOWN_MS && LAST_LOG_MS.compareAndSet(last, now)) {
                            VPA_LOGGER.warn(
                                    "[Worldgen Guard] Suppressed {} during async chunk generation"
                                            + " (incompatible worldgen mods — see earlier logs for chunk coords)",
                                    cause.getClass().getSimpleName()
                            );
                        }
                        // Return null: chunk skipped instead of crashing the server
                        return null;
                    }
                    cause = cause.getCause();
                }
            }
            // Unknown exception or no exception: delegate to original handler
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
