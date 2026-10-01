package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu;

import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A vanilla 3x3 crafting menu that is not tied to a crafting table in the world.
 *
 * <p>Deliberately a subclass of {@link CraftingMenu} rather than a menu type of its own.
 * {@code AbstractContainerMenu.menuType} is assigned once in the protected constructor and
 * {@code getType()} only reads it, so this class still reports {@code MenuType.CRAFTING}. That is
 * what {@code ServerPlayer.openMenu} puts into the open-screen packet, so any client - including a
 * vanilla one - builds the stock {@code CraftingScreen} for it. No menu type to register, no screen
 * factory, no texture and no language key of our own, and every recipe viewer in the pack sees a
 * crafting screen it already knows.</p>
 *
 * <p>The level access matters more than it looks. {@link ContainerLevelAccess#NULL} never invokes
 * the function it is handed, which silently breaks two things in the superclass:
 * {@code slotsChanged} would never compute a result, and {@code removed} would never call
 * {@code clearContainer} - the nine items in the grid would simply vanish when the screen closes.
 * So a real access is required. Only {@code level} is ever used by the superclass
 * ({@code slotChangedCraftingGrid} asks it for the recipe manager, {@code onCraftedBy} takes it);
 * the position is read exclusively by the inherited {@code stillValid}, which is overridden below.</p>
 */
public class PocketCraftingMenu extends CraftingMenu {

    public PocketCraftingMenu(int containerId, Inventory playerInventory) {
        super(containerId, playerInventory, ContainerLevelAccess.create(
                playerInventory.player.level(), playerInventory.player.blockPosition()));
    }

    /**
     * Whether the menu may stay open.
     *
     * <p>The inherited implementation looks for a crafting table block at the access position and
     * would close this menu on the very next tick, because the whole point is that there is no table
     * standing anywhere. With {@code require_table_while_open} the check becomes "is a crafting table
     * still in the inventory" instead - off by default, because a player may legitimately put the
     * table into the grid as an ingredient, and the grid is a {@code TransientCraftingContainer} that
     * is not part of {@code Inventory}: the table would read as gone and the menu would close mid-craft.</p>
     *
     * @param player the player the menu belongs to
     * @return true while the menu may stay open
     */
    @Override
    public boolean stillValid(Player player) {
        PocketCraftingModule module = PocketCraftingModule.getInstance();
        if (module == null || !module.getConfig().requiresTableWhileOpen()) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.CRAFTING_TABLE)) {
                return true;
            }
        }
        return player.getOffhandItem().is(Items.CRAFTING_TABLE);
    }
}
