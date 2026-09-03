package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Modifier keybind that must be held while right-clicking a rideable wolf in order to mount it.
 *
 * <p>Default: left Ctrl — the same gesture the cat and axolotl guardian inventories use. Without
 * the modifier a right-click falls through to vanilla, so feeding, dyeing the collar, equipping
 * armor, shearing it off, repairing it and the sit/stand toggle all keep working unchanged.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WolfMountKeybinds {

    public static final KeyMapping MOUNT_MODIFIER = new KeyMapping(
            "key.vanillaplusadditions.wolf_mount.mount_modifier",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.vanillaplusadditions");

    private WolfMountKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MOUNT_MODIFIER);
    }

    /**
     * Whether the bound modifier key is currently physically held. Reads the raw window key/mouse
     * state rather than {@link KeyMapping#isDown()} because this is a "held while clicking" check,
     * not a discrete press.
     *
     * @return true if the modifier is bound and currently down
     */
    public static boolean isModifierDown() {
        InputConstants.Key key = MOUNT_MODIFIER.getKey();
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
