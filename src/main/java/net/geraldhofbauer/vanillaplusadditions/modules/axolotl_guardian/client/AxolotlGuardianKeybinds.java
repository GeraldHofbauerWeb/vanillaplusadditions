package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Modifier keybind that must be held while right-clicking an owned axolotl to open its inventory
 * GUI.
 *
 * <p>Default: left Ctrl (same physical default as the cat module — both are hold-modifiers, so
 * sharing the key is harmless). When it is <em>not</em> held, right-clicking falls through to
 * vanilla (bucket pickup etc.). Rebindable in the Controls menu.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AxolotlGuardianKeybinds {

    public static final KeyMapping OPEN_INVENTORY_MODIFIER = new KeyMapping(
            "key.vanillaplusadditions.axolotl_guardian.open_inventory_modifier",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.vanillaplusadditions");

    private AxolotlGuardianKeybinds() { }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_INVENTORY_MODIFIER);
    }

    /**
     * Whether the bound modifier key is currently physically held. Reads the raw window key/mouse
     * state (not {@link KeyMapping#isDown()}) because we need a "held while clicking" check, not a
     * discrete press event.
     *
     * @return true if the modifier is down and bound to a real key/button
     */
    public static boolean isModifierDown() {
        InputConstants.Key key = OPEN_INVENTORY_MODIFIER.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int value = key.getValue();
            return value != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, value);
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
