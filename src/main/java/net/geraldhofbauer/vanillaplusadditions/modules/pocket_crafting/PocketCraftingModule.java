package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.config.PocketCraftingConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu.MenuMemory;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu.PocketCraftingMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu.RememberedMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network.OpenPocketCraftingPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network.ReturnFromPocketCraftingPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Opens a 3x3 crafting grid straight from an inventory: right-click a crafting table you are
 * carrying - in the survival inventory or in the inventory half of any container screen - and the
 * stock crafting screen comes up. Escape takes you back to where you were.
 *
 * <p>The table is never consumed and never placed. What the grid gives you is the one thing a
 * crafting table in a backpack cannot do on its own - the 3x3 recipes - without asking you to put a
 * block down, use it and mine it again.</p>
 *
 * <p>Nothing of the interface belongs to us. {@link PocketCraftingMenu} reports
 * {@code MenuType.CRAFTING}, so every client draws the crafting screen it already ships: no texture,
 * no screen class, no menu type, no translation of ours. See that class for why that works and where
 * the trap is.</p>
 *
 * <p><b>Going back into a chest.</b> A player has exactly one open menu, so opening the grid from a
 * chest closes the chest on the server. Returning means opening it again, and an open menu does not
 * know what built it - so a mixin records the provider of every menu as it is opened
 * ({@code ServerPlayerMenuMemoryMixin}). Replaying that provider is only done for menu types on an
 * allow-list: a provider is not obliged to survive being called twice, and one that is not ends in a
 * disconnected client. Everything else returns to the survival inventory.</p>
 */
public class PocketCraftingModule extends AbstractModule<PocketCraftingModule, PocketCraftingConfig> {

    /**
     * Title of a pocket grid that returns to the survival inventory, and the marker the client uses
     * to tell it apart from a real table. A real crafting table opens under {@code container.crafting}.
     */
    public static final String MENU_TITLE_KEY = "container.vanillaplusadditions.pocket_crafting";

    /**
     * Title of a pocket grid that returns to the container it was opened from. A second key rather
     * than a packet: the server decides where Escape leads at the moment it opens the grid, and the
     * title travels in the very same open-screen packet, so the client cannot see one without the other.
     */
    public static final String MENU_TITLE_RETURN_KEY = MENU_TITLE_KEY + ".return";

    /**
     * How long after the grid closed the server still honours a request to go back. Generous enough
     * for a laggy connection, short enough that a recorded chest cannot be reopened at leisure.
     */
    private static final int RETURN_WINDOW_TICKS = 40;

    private static PocketCraftingModule instance;

    /** Mod id of an installed mod that claims the same click, or null. */
    private String conflictingMod;

    /** Per player: the container a grid that is open (or just closed) should return into. */
    private final Map<UUID, PendingReturn> pendingReturns = new HashMap<>();

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
        NeoForge.EVENT_BUS.register(this);
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

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingReturns.remove(event.getEntity().getUUID());
    }

    /**
     * Registers both requests on an <b>optional</b> channel.
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
        event.registrar("1").optional()
                .playToServer(
                        OpenPocketCraftingPacket.TYPE,
                        OpenPocketCraftingPacket.STREAM_CODEC,
                        (packet, ctx) -> ctx.enqueueWork(() -> {
                            if (isTriggerActive() && ctx.player() instanceof ServerPlayer player) {
                                openGrid(player, packet.slot());
                            }
                        }))
                .playToServer(
                        ReturnFromPocketCraftingPacket.TYPE,
                        ReturnFromPocketCraftingPacket.STREAM_CODEC,
                        (packet, ctx) -> ctx.enqueueWork(() -> {
                            if (isModuleEnabled() && ctx.player() instanceof ServerPlayer player) {
                                returnToContainer(player);
                            }
                        }));
    }

    /**
     * Opens the grid after re-checking the client's claim against the server's own menu.
     *
     * @param player    the requesting player
     * @param slotIndex menu slot index the client says it clicked
     */
    private void openGrid(ServerPlayer player, int slotIndex) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        AbstractContainerMenu current = player.containerMenu;
        if (slotIndex < 0 || slotIndex >= current.slots.size()) {
            return;
        }
        Slot slot = current.getSlot(slotIndex);
        if (slot.container != player.getInventory()) {
            // Only a table the player is carrying. That rules out the chest half of a container
            // screen as well as the inventory menu's own 2x2 grid, whose slots are backed by other
            // containers. Main inventory, hotbar, armour and offhand all share this one object;
            // armour slots cannot hold a table anyway, the offhand can and is fine.
            return;
        }
        if (!slot.getItem().is(Items.CRAFTING_TABLE)) {
            return;
        }

        RememberedMenu back = current == player.inventoryMenu ? null : resolveReturnTarget(player, current);
        pendingReturns.remove(player.getUUID());
        if (back != null) {
            pendingReturns.put(player.getUUID(), new PendingReturn(back, -1L));
        }
        String title = back != null ? MENU_TITLE_RETURN_KEY : MENU_TITLE_KEY;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new PocketCraftingMenu(id, inventory),
                Component.translatable(title)));
    }

    /**
     * Works out whether the container the player has open may be reopened later.
     *
     * @param player  the player
     * @param current the menu the player has open right now
     * @return how to reopen it, or null if the grid should return to the survival inventory instead
     */
    @Nullable
    private RememberedMenu resolveReturnTarget(ServerPlayer player, AbstractContainerMenu current) {
        if (!getConfig().returnsToInventoryOnClose() || !(player instanceof MenuMemory memory)) {
            return null;
        }
        RememberedMenu last = memory.vpaGetLastMenu();
        if (last == null || last.containerId() != current.containerId) {
            // The open menu did not come through openMenu (a horse's inventory is opened by a
            // separate path), or something else was opened since. Either way: unknown provenance.
            return null;
        }
        ResourceLocation type;
        try {
            type = BuiltInRegistries.MENU.getKey(current.getType());
        } catch (UnsupportedOperationException e) {
            // Menus constructed without a type throw here rather than returning null.
            return null;
        }
        if (type == null || !getConfig().getReturnMenuTypes().contains(type.toString())) {
            return null;
        }
        return last;
    }

    /**
     * Called by the server-side grid when it closes, for whatever reason.
     *
     * @param player the player whose grid closed
     */
    public void onGridClosed(ServerPlayer player) {
        pendingReturns.computeIfPresent(player.getUUID(), (id, pending) -> pending.closedAt() < 0
                ? new PendingReturn(pending.target(), player.server.getTickCount())
                : pending);
    }

    /**
     * Reopens the container the grid was opened from.
     *
     * <p>Each condition closes a door a modified client could otherwise walk through. The request
     * carries nothing, so it can only ever reach the container the server itself recorded; it is
     * honoured once, only shortly after the grid closed, and only from the plain inventory. And the
     * reopened menu is checked for reach before anything else happens: this runs on the server
     * thread, so no click from the client can reach the menu before that check - without it, a
     * player who had walked away could get one tick of access to a chest out of reach.</p>
     *
     * @param player the player asking to go back
     */
    private void returnToContainer(ServerPlayer player) {
        PendingReturn pending = pendingReturns.remove(player.getUUID());
        if (pending == null || pending.closedAt() < 0) {
            return;
        }
        if (player.server.getTickCount() - pending.closedAt() > RETURN_WINDOW_TICKS) {
            return;
        }
        if (player.containerMenu != player.inventoryMenu || player.isCreative() || player.isSpectator()) {
            return;
        }
        RememberedMenu target = pending.target();
        player.openMenu(target.provider(), target.extraData());
        if (player.containerMenu != player.inventoryMenu && !player.containerMenu.stillValid(player)) {
            player.closeContainer();
        }
    }

    /**
     * A container to return into.
     *
     * @param target   how to reopen it
     * @param closedAt server tick the grid closed at, or -1 while it is still open
     */
    private record PendingReturn(RememberedMenu target, long closedAt) {
    }
}
