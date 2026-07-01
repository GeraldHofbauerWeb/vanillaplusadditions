package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The single keybind that toggles the whole debug overlay (chunk borders, cat stats, ...).
 * Default: numpad +, matching the former cat-only keybind it replaces.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DebugOverlayKeybinds {

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.vanillaplusadditions.debug_overlay.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD,
            "key.categories.vanillaplusadditions");

    private DebugOverlayKeybinds() { }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }
}
