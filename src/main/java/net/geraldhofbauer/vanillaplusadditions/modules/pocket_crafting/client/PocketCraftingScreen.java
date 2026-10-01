package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.client;

import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network.ReturnFromPocketCraftingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The stock crafting screen, with one addition: closing it goes back to where it was opened from.
 *
 * <p>Everything visible is inherited - the same texture, the same recipe book, the same layout.
 * The only reason this class exists is {@link #onClose()}.</p>
 */
@OnlyIn(Dist.CLIENT)
public class PocketCraftingScreen extends CraftingScreen {

    /** Whether the server will reopen a container for us, rather than us opening the inventory. */
    private final boolean returnToContainer;

    public PocketCraftingScreen(CraftingMenu menu, Inventory playerInventory, Component title,
                                boolean returnToContainer) {
        super(menu, playerInventory, title);
        this.returnToContainer = returnToContainer;
    }

    /**
     * Closes the grid and goes back.
     *
     * <p>Order matters. {@code super.onClose()} sends the close packet and resets the client's
     * {@code containerMenu} back to {@code inventoryMenu} before clearing the screen. From the
     * survival inventory, the new {@link InventoryScreen} binds exactly that menu, so there is
     * nothing to race. From a container, the server reopens it: the return request travels on the
     * same ordered connection as the close packet, so the server always sees the grid closed first.
     * The player reference is taken beforehand because the field is cleared along the way.</p>
     *
     * <p>Only covers closing it yourself. A close forced by the server arrives as a packet and never
     * runs through here, so that case still lands in the world - which is the honest outcome, since
     * the server has already thrown the menu away.</p>
     */
    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        super.onClose();
        if (player == null) {
            return;
        }

        ClientPacketListener connection = minecraft.getConnection();
        if (returnToContainer && connection != null && connection.hasChannel(ReturnFromPocketCraftingPacket.TYPE)) {
            PacketDistributor.sendToServer(ReturnFromPocketCraftingPacket.INSTANCE);
            return;
        }

        PocketCraftingModule module = PocketCraftingModule.getInstance();
        boolean returnToInventory = module == null || module.getConfig().returnsToInventoryOnClose();
        if (returnToInventory) {
            minecraft.setScreen(new InventoryScreen(player));
        }
    }
}
