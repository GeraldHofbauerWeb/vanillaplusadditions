package net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Modifier keybind that must be held while looking at a Create Mechanical Arm to reveal its
 * input/output target overlay (hold-to-peek).
 *
 * <p>Default: left Ctrl. When it is <em>not</em> held, the overlay stays hidden even with goggles
 * on. The mapping is rebindable in the Controls menu; players can move it to any key (or a mouse
 * button).
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ArmTargetOverlayKeybinds {

    public static final KeyMapping PEEK_MODIFIER = new KeyMapping(
            "key.vanillaplusadditions.arm_target_overlay.peek_modifier",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.vanillaplusadditions");

    private ArmTargetOverlayKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PEEK_MODIFIER);
    }

    /**
     * Whether the bound modifier key is currently physically held. Reads the raw window key/mouse
     * state (not {@link KeyMapping#isDown()}) because we need a "held while looking" check, not a
     * discrete press event.
     *
     * @return true if the modifier is down and bound to a real key/button
     */
    public static boolean isModifierDown() {
        InputConstants.Key key = PEEK_MODIFIER.getKey();
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
