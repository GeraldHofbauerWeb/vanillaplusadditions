package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.client;

import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The stock crafting screen, with one addition: closing it goes back to the inventory.
 *
 * <p>Everything visible is inherited - the same texture, the same recipe book, the same layout.
 * The only reason this class exists is {@link #onClose()}: a crafting screen opened from the
 * inventory should return there, not drop the player back into the world.</p>
 */
@OnlyIn(Dist.CLIENT)
public class PocketCraftingScreen extends CraftingScreen {

    public PocketCraftingScreen(CraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * Closes the grid and reopens the inventory.
     *
     * <p>Order matters. {@code super.onClose()} sends the close packet and resets the client's
     * {@code containerMenu} back to {@code inventoryMenu} before clearing the screen - which is
     * exactly the menu the new {@link InventoryScreen} binds to, so there is nothing to race. The
     * player reference is taken beforehand because the field is cleared along the way.</p>
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

        PocketCraftingModule module = PocketCraftingModule.getInstance();
        boolean returnToInventory = module == null || module.getConfig().returnsToInventoryOnClose();
        if (player != null && returnToInventory) {
            minecraft.setScreen(new InventoryScreen(player));
        }
    }
}
