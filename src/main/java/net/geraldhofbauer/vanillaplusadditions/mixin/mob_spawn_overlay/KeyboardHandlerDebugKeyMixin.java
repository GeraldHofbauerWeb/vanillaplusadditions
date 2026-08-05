package net.geraldhofbauer.vanillaplusadditions.mixin.mob_spawn_overlay;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.MobSpawnOverlayModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client.MobSpawnOverlayClientEvents;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds {@code F3 + M} (rebindable via config) as a debug key that toggles the mob spawn overlay.
 *
 * <p>Hooking vanilla's own debug-key dispatch rather than {@code InputEvent.Key} is deliberate:
 * NeoForge fires the key event only after {@code handledDebugKey |= flag5}, and it cannot be
 * cancelled — so releasing F3 would additionally open the debug screen. Returning {@code true}
 * here sets that flag, exactly as vanilla's own F3+G does.</p>
 *
 * <p>Client-only mixin: {@code KeyboardHandler} does not exist on a dedicated server, so this
 * class is registered in the {@code "client"} block of the mixin config.</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerDebugKeyMixin {

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void vpaToggleSpawnOverlay(int key, CallbackInfoReturnable<Boolean> cir) {
        if (!MobSpawnOverlayModule.isActiveClientSide() || key != MobSpawnOverlayModule.getToggleKey()) {
            return;
        }
        MobSpawnOverlayClientEvents.toggleFromDebugKey();
        cir.setReturnValue(true);
    }
}
