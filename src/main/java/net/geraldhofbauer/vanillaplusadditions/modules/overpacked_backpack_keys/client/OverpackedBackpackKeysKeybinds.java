package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds that open the compartments of the worn Overpacked giant backpack.
 *
 * <p>Main (center) compartment defaults to {@code K}; the right and left compartments are
 * <em>unbound</em> by default (players can bind them in the Controls menu). All three share the
 * shared {@code key.categories.vanillaplusadditions} category.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OverpackedBackpackKeysKeybinds {

    private static final String CATEGORY = "key.categories.vanillaplusadditions";

    public static final KeyMapping OPEN_MAIN = new KeyMapping(
            "key.vanillaplusadditions.overpacked_backpack_keys.open_main",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    public static final KeyMapping OPEN_RIGHT = new KeyMapping(
            "key.vanillaplusadditions.overpacked_backpack_keys.open_right",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    public static final KeyMapping OPEN_LEFT = new KeyMapping(
            "key.vanillaplusadditions.overpacked_backpack_keys.open_left",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private OverpackedBackpackKeysKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAIN);
        event.register(OPEN_RIGHT);
        event.register(OPEN_LEFT);
    }
}
