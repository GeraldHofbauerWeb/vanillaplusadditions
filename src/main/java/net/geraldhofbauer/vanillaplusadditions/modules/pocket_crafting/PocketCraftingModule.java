package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.config.PocketCraftingConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu.PocketCraftingMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network.OpenPocketCraftingPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Opens a 3x3 crafting grid straight from the survival inventory: right-click a crafting table in
 * your own inventory and the stock crafting screen comes up, Escape takes you back to the inventory.
 *
 * <p>The table is never consumed and never placed. What the grid gives you is the one thing a
 * crafting table in a backpack cannot do on its own - the 3x3 recipes - without asking you to put a
 * block down, use it and mine it again.</p>
 *
 * <p>Nothing of the interface belongs to us. {@link PocketCraftingMenu} reports
 * {@code MenuType.CRAFTING}, so every client draws the crafting screen it already ships: no texture,
 * no screen class, no menu type, no translation of ours. See that class for why that works and where
 * the trap is.</p>
 */
public class PocketCraftingModule extends AbstractModule<PocketCraftingModule, PocketCraftingConfig> {

    /**
     * Title of the pocket grid, and the marker the client uses to tell it apart from a real table.
     * A real crafting table opens under {@code container.crafting}, so a distinct key is enough.
     */
    public static final String MENU_TITLE_KEY = "container.vanillaplusadditions.pocket_crafting";

    private static PocketCraftingModule instance;

    /** Mod id of an installed mod that claims the same click, or null. */
    private String conflictingMod;

    public PocketCraftingModule() {
        super("pocket_crafting",
                "Pocket Crafting",
                "Right-click a crafting table in your inventory to open the 3x3 crafting grid",
                PocketCraftingConfig::new);
        instance = this;
    }

    /**
     * The module instance, or {@code null} before {@code onInitialize} has run. Module-local on
     * purpose: {@code ModuleManager} is only populated by the all-in-one bundle, so a lookup there
     * returns null inside a standalone {@code vpa_pocket_crafting} jar.
     *
     * @return the live module instance, or null if it has not been initialized (yet)
     */
    public static PocketCraftingModule getInstance() {
        return instance;
    }

    @Override
    protected void onInitialize() {
        getModEventBus().addListener(this::onRegisterPayloadHandlers);
        getLogger().info("Pocket Crafting module initialized");
    }

    @Override
    protected void onCommonSetup() {
        for (String modId : getConfig().getConflictingMods()) {
            if (ModList.get().isLoaded(modId)) {
                conflictingMod = modId;
                getLogger().info("Pocket Crafting: inventory trigger off, '{}' is installed and claims the same click",
                        modId);
                return;
            }
        }
    }

    /**
     * Whether the inventory click should open the grid at all.
     *
     * @return true if the trigger is armed
     */
    public boolean isTriggerActive() {
        return isModuleEnabled() && getConfig().isInventoryClickEnabled() && conflictingMod == null;
    }

    /**
     * Registers the open request as an <b>optional</b> channel.
     *
     * <p>Deliberate: {@code NetworkComponentNegotiator} fails the whole handshake when one side has
     * a non-optional channel the other lacks, and the client is then disconnected as incompatible.
     * A server running only the standalone {@code vpa_pocket_crafting} jar would throw out every
     * vanilla player over a convenience window. Optional means a client without the mod simply has
     * no trigger - which is exactly the state it was in before.</p>
     *
     * @param event the payload registration event
     */
    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(
                OpenPocketCraftingPacket.TYPE,
                OpenPocketCraftingPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isTriggerActive()) {
                        return;
                    }
                    if (!(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    openGrid(player, packet.slot());
                }));
    }

    /**
     * Opens the grid after re-checking the client's claim against the server's own inventory.
     *
     * @param player    the requesting player
     * @param slotIndex slot index the client says it clicked
     */
    private void openGrid(ServerPlayer player, int slotIndex) {
        if (player.containerMenu != player.inventoryMenu) {
            // Only from the plain inventory. Anything else means the client is out of sync or lying.
            return;
        }
        if (slotIndex < 0 || slotIndex >= player.inventoryMenu.slots.size()) {
            return;
        }
        Slot slot = player.inventoryMenu.getSlot(slotIndex);
        if (!(slot.container instanceof Inventory)) {
            // The inventory menu's own 2x2 grid and its result slot are backed by a transient
            // container, not by Inventory, so this rules those two out. What is left is the player's
            // real inventory - main, hotbar, armour and offhand all share that one container. The
            // armour slots cannot hold a crafting table anyway; the offhand can, and opening the
            // grid from there is fine, it is still the player's own table.
            return;
        }
        if (!slot.getItem().is(Items.CRAFTING_TABLE)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new PocketCraftingMenu(id, inventory),
                Component.translatable(MENU_TITLE_KEY)));
    }
}
