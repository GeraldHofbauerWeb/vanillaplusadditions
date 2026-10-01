package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.client;

import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network.OpenPocketCraftingPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Client side of Pocket Crafting: the trigger, the tooltip hint, and the screen swap that gives the
 * grid its way back.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class PocketCraftingClientEvents {

    /** Language key of the hint line added to a crafting table's tooltip. */
    private static final String TOOLTIP_KEY = "tooltip.vanillaplusadditions.pocket_crafting.open";

    private PocketCraftingClientEvents() {
    }

    /**
     * The slot under the mouse if it holds a crafting table the player can open the grid from, or
     * null. Shared by the trigger and the tooltip so the hint appears exactly where the click works.
     *
     * <p>Any container screen counts - the survival inventory, a chest, a barrel, a furnace - except
     * a crafting screen, where opening a second grid makes no sense. The table has to sit in the
     * player's own inventory, not in the container half. Survival and adventure only: in creative,
     * {@code E} opens the creative menu, whose slots behave nothing like a survival inventory.</p>
     *
     * @param screen the screen currently open
     * @return the eligible slot, or null
     */
    @Nullable
    private static Slot eligibleSlot(@Nullable Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || screen instanceof CraftingScreen) {
            return null;
        }
        PocketCraftingModule module = PocketCraftingModule.getInstance();
        if (module == null || !module.isTriggerActive()) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return null;
        }
        Slot slot = containerScreen.getSlotUnderMouse();
        if (slot == null || slot.container != player.getInventory() || !slot.getItem().is(Items.CRAFTING_TABLE)) {
            return null;
        }
        return slot;
    }

    /**
     * Tells the player a crafting table can be opened, wherever the click would actually work.
     *
     * @param event the tooltip being assembled
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(Items.CRAFTING_TABLE)) {
            return;
        }
        PocketCraftingModule module = PocketCraftingModule.getInstance();
        if (module == null || !module.getConfig().showsTooltip()) {
            return;
        }
        if (eligibleSlot(Minecraft.getInstance().screen) == null) {
            return;
        }
        event.getToolTip().add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Turns a right-click on a crafting table in the inventory into an open request.
     *
     * <p>{@code ScreenEvent.MouseButtonPressed.Pre} is the same hook the Easy Shulker Boxes family
     * uses for its in-inventory interactions. Mouse Tweaks listens here too but never cancels, and
     * it only arms its right-click drag while the cursor carries something - so the empty-cursor
     * condition keeps the two apart. Shift is left alone on purpose: shift-right-click is vanilla's
     * quick-move and stays that way.</p>
     *
     * @param event the mouse press about to be handled by the screen
     */
    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || Screen.hasShiftDown()) {
            return;
        }
        Slot slot = eligibleSlot(event.getScreen());
        if (slot == null) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        PocketCraftingModule module = PocketCraftingModule.getInstance();
        if (module.getConfig().requiresEmptyCarried() && !screen.getMenu().getCarried().isEmpty()) {
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
     * a real crafting table opens under {@code container.crafting}, the pocket grid under one of two
     * keys of ours - and which of the two also says where Escape leads. Not the menu type - Visual
     * Workbench registers a crafting menu type of its own for the real table, so a type check would
     * pass here by accident and break in another pack.</p>
     *
     * @param event the screen about to be opened
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof CraftingScreen craftingScreen)
                || craftingScreen instanceof PocketCraftingScreen) {
            return;
        }
        if (!(craftingScreen.getTitle().getContents() instanceof TranslatableContents contents)) {
            return;
        }
        boolean returnToContainer = PocketCraftingModule.MENU_TITLE_RETURN_KEY.equals(contents.getKey());
        if (!returnToContainer && !PocketCraftingModule.MENU_TITLE_KEY.equals(contents.getKey())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        event.setNewScreen(new PocketCraftingScreen(craftingScreen.getMenu(), minecraft.player.getInventory(),
                craftingScreen.getTitle(), returnToContainer));
    }
}
