package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.client;

import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network.OpenPocketCraftingPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of Pocket Crafting: the trigger in the inventory, and the screen swap that gives the
 * grid its way back to the inventory.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class PocketCraftingClientEvents {

    /** Language key of the hint line added to a crafting table's tooltip. */
    private static final String TOOLTIP_KEY = "tooltip.vanillaplusadditions.pocket_crafting.open";

    private PocketCraftingClientEvents() {
    }

    /**
     * Tells the player a crafting table in the inventory can be opened.
     *
     * <p>Only while the survival inventory is on screen. The click does nothing anywhere else - not
     * in a chest, not in the creative menu, not in a recipe viewer - and a hint that shows up where
     * it does not work is worse than no hint at all.</p>
     *
     * @param event the tooltip being assembled
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(Items.CRAFTING_TABLE)) {
            return;
        }
        PocketCraftingModule module = PocketCraftingModule.getInstance();
        if (module == null || !module.isTriggerActive() || !module.getConfig().showsTooltip()) {
            return;
        }
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) {
            return;
        }
        event.getToolTip().add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Turns a right-click on a crafting table inside the inventory into an open request.
     *
     * <p>{@code ScreenEvent.MouseButtonPressed.Pre} is the same hook the Easy Shulker Boxes family
     * uses for its in-inventory interactions. Mouse Tweaks listens here too but never cancels, and
     * it only arms its right-click drag while the cursor carries something - so the empty-cursor
     * condition below keeps the two apart. Shift is left alone on purpose: shift-right-click is
     * vanilla's quick-move and stays that way.</p>
     *
     * @param event the mouse press about to be handled by the screen
     */
    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        PocketCraftingModule module = PocketCraftingModule.getInstance();
        if (module == null || !module.isTriggerActive()) {
            return;
        }
        if (Screen.hasShiftDown()) {
            return;
        }
        if (module.getConfig().requiresEmptyCarried() && !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.getItem().is(Items.CRAFTING_TABLE)) {
            return;
        }
        if (!(slot.container instanceof Inventory)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null
                || !minecraft.getConnection().hasChannel(OpenPocketCraftingPacket.TYPE)) {
            // The channel is optional, so a server without this module simply does not have it.
            // Sending anyway would be refused by NetworkRegistry.checkPacket and throw.
            return;
        }

        PacketDistributor.sendToServer(new OpenPocketCraftingPacket(slot.index));
        event.setCanceled(true);
    }

    /**
     * Replaces the stock crafting screen with ours when the server opened <em>our</em> grid.
     *
     * <p>Because the menu reports {@code MenuType.CRAFTING}, the client builds a plain
     * {@code CraftingScreen} and cannot tell where the menu came from. The menu title is the marker:
     * a real crafting table opens under {@code container.crafting}, the pocket grid under a key of
     * ours. Not the menu type - Visual Workbench registers a crafting menu type of its own for the
     * real table, so a type check would pass here by accident and break in another pack.</p>
     *
     * @param event the screen about to be opened
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof CraftingScreen craftingScreen)
                || craftingScreen instanceof PocketCraftingScreen) {
            return;
        }
        if (!(craftingScreen.getTitle().getContents() instanceof TranslatableContents contents)
                || !PocketCraftingModule.MENU_TITLE_KEY.equals(contents.getKey())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        event.setNewScreen(new PocketCraftingScreen(
                craftingScreen.getMenu(), minecraft.player.getInventory(), craftingScreen.getTitle()));
    }
}
