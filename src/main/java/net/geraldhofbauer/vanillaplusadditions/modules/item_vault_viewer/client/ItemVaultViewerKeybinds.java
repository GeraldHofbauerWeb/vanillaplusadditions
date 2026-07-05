package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Modifier keybind that must be held while right-clicking a Create Item Vault to open the viewer GUI.
 *
 * <p>Default: left Ctrl. When it is <em>not</em> held, right-clicking the vault falls through to
 * plain vanilla behaviour — so the viewer no longer hijacks every click. The mapping is rebindable
 * in the Controls menu; players can move it to any key (or a mouse button).
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ItemVaultViewerKeybinds {

    public static final KeyMapping OPEN_MODIFIER = new KeyMapping(
            "key.vanillaplusadditions.item_vault_viewer.open_modifier",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.vanillaplusadditions");

    private ItemVaultViewerKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MODIFIER);
    }

    /**
     * Whether the bound modifier key is currently physically held. Reads the raw window key/mouse
     * state (not {@link KeyMapping#isDown()}) because we need a "held while clicking" check, not a
     * discrete press event.
     *
     * @return true if the modifier is down and bound to a real key/button
     */
    public static boolean isModifierDown() {
        InputConstants.Key key = OPEN_MODIFIER.getKey();
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
