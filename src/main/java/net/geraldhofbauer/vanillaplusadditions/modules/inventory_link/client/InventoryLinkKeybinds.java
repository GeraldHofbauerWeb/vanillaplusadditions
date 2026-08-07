package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Modifier held while right-clicking a linked block to open its settings screen instead of
 * starting a new link. Default: left Ctrl.
 *
 * <p>The modifier has to be evaluated on the client: the vanilla interaction packet carries no
 * modifier state, so the server can never tell a plain click from a Ctrl click.</p>
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class InventoryLinkKeybinds {

    public static final KeyMapping SETTINGS_MODIFIER = new KeyMapping(
            "key.vanillaplusadditions.inventory_link.settings_modifier",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.vanillaplusadditions");

    private InventoryLinkKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SETTINGS_MODIFIER);
    }

    /**
     * Whether the bound modifier is physically held right now. Reads raw window state rather than
     * {@link KeyMapping#isDown()}, because this is a "held while clicking" check, not a press.
     *
     * @return true if the modifier key or mouse button is currently down
     */
    public static boolean isModifierDown() {
        InputConstants.Key key = SETTINGS_MODIFIER.getKey();
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
