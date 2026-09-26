package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.compat;

import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.compat.CuriosBackpackAccess.Worn;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network.BackpackHelperReadyPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nycto_team.overpacked.entity.GiantBackpack;
import net.nycto_team.overpacked.item.GiantBackpackItem;
import net.nycto_team.overpacked.menu.GiantBackpackMenu;
import net.nycto_team.overpacked.registry.ModEntities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The only class that references Overpacked types. It reuses Overpacked's <em>own</em> backpack menu
 * and screen by spawning a short-lived {@link GiantBackpack} entity from the worn backpack item,
 * opening {@link GiantBackpackMenu} on it, and — when the player closes the GUI — writing the edited
 * contents back into the worn item and discarding the entity.
 *
 * <p>Overpacked's GUI is entity-bound (its client menu factory resolves the backpack by entity id),
 * so there is no worn-item-only open path; a transient entity is required. All Overpacked references
 * live inside method bodies and are reached only when {@link OverpackedCompat#isAvailable()} holds —
 * the module never registers this class's event handlers otherwise (mirrors {@code end_oxygen} /
 * Create compat). Note the gate deliberately lives in {@link OverpackedCompat}: merely resolving a
 * static member of <em>this</em> class links it, and the verifier then loads Overpacked's types.
 */
public final class OverpackedGuiBridge {

    /**
     * Markiert die Helfer-Entity im NBT, damit eine liegengebliebene beim Laden erkannt wird.
     * {@link Entity#addTag} landet in den {@code Tags} und ueberlebt Speichern und Neustart.
     */
    private static final String HELPER_TAG = "vpa_backpack_helper";

    /**
     * Transient backpack entities we spawned, keyed by entity id → the slot to write back to.
     *
     * <p>In-memory only, and deliberately so: a session is meaningful just as long as the screen is
     * open. {@link #HELPER_TAG} is what survives a restart, and it exists so a helper entity left
     * behind by a crash can be recognised and removed - not to resume anything.</p>
     */
    private static final Map<Integer, Session> SESSIONS = new HashMap<>();

    /**
     * How far below the player's feet the helper entity is parked, in blocks.
     *
     * <p>Not a cosmetic choice. {@code GiantBackpack.tick()} pushes <b>every player inside its
     * bounding box</b> away from itself, once per tick — and the old code dropped the helper straight
     * into the player whenever the spot in front was occupied. On a Sable airship that shove is enough
     * to squeeze the player through the hull, because sub-level block collision is the weaker,
     * transformed path. Parking it clear of the player's box (which is 1.8 blocks tall, the helper
     * 1.25) makes the push a no-op, keeps it out of the crosshair, and buries it out of sight. It is
     * {@code noPhysics}, so sitting inside the floor costs nothing.</p>
     */
    private static final double HELPER_DROP = 2.5;

    /**
     * How long the server waits for the client's {@code ConfirmBackpackHelperPacket} before cleaning
     * up an unopened helper, in ticks (7 s).
     *
     * <p>Just longer than the client's own 5 s patience: the client always gives up first, so this
     * only ever fires for a client that vanished mid-handshake. Not much longer, because a pending
     * session also blocks the keybind (see {@link #hasPendingHelper}) — a failed attempt should not
     * lock the backpack for half a minute.</p>
     */
    private static final int CONFIRM_TIMEOUT_TICKS = 140;

    /**
     * One open (or pending) helper backpack: which slot to write back to, and how far the handshake
     * with the client has got.
     */
    private static final class Session {
        private final UUID playerUUID;
        private final String identifier;
        private final int index;
        private final ItemStack originalWorn;
        private final int compartment;
        private final long confirmDeadline;
        /** False until the client confirmed the entity and the screen actually opened. */
        private boolean opened;

        private Session(UUID playerUUID, String identifier, int index, ItemStack originalWorn,
                        int compartment, long confirmDeadline) {
            this.playerUUID = playerUUID;
            this.identifier = identifier;
            this.index = index;
            this.originalWorn = originalWorn;
            this.compartment = compartment;
            this.confirmDeadline = confirmDeadline;
        }

        private UUID playerUUID() {
            return playerUUID;
        }

        private String identifier() {
            return identifier;
        }

        private int index() {
            return index;
        }

        private ItemStack originalWorn() {
            return originalWorn;
        }
    }

    private OverpackedGuiBridge() {
    }

    /** Overpacked's inv_id for the two side compartments, and the NBT key holding each unlock. */
    private static final Map<Integer, String> SIDE_CELL_KEYS = Map.of(1, "RightCell", 2, "LeftCell");

    /**
     * True when the requested compartment is a side pocket the backpack has not unlocked.
     *
     * <p>Overpacked 2.x encodes the unlock as the mere <b>presence</b> of {@code RightCell} /
     * {@code LeftCell} in the item's {@code CUSTOM_DATA} — the stored value is meaningless. Its
     * {@code Save} writes the key with a hardcoded {@code 0} payload and only when the cell is
     * non-zero, and its {@code Load} answers with {@code SetRightCell(1)} whenever the key exists.
     * So read the key, never its value: {@code getByte(key) == 0} is true for every backpack, locked
     * or not, and would reject them all.
     *
     * <p>Overpacked 1.x has no such concept — every compartment always exists there — so nothing is
     * ever locked.
     */
    private static boolean isCompartmentLocked(CompoundTag wornTag, int compartment) {
        if (!OverpackedCompat.isV2()) {
            return false;
        }
        String cellKey = SIDE_CELL_KEYS.get(compartment);
        return cellKey != null && !wornTag.contains(cellKey);
    }

    /**
     * Opens the given compartment (0 = center/main, 1 = right, 2 = left) of the player's worn giant
     * backpack, reusing Overpacked's own menu + screen.
     */
    public static void open(ServerPlayer player, int compartment) {
        if (hasPendingHelper(player)) {
            // A helper is already waiting for its confirmation - holding the key down would otherwise
            // spawn one per tick, each carrying a copy of the same inventory.
            return;
        }
        Optional<Worn> wornOpt = CuriosBackpackAccess.findWorn(player);
        if (wornOpt.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.vanillaplusadditions.overpacked_extensions.no_backpack"), true);
            return;
        }
        Worn worn = wornOpt.get();
        CustomData wornData = worn.stack().get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = wornData != null ? wornData.copyTag() : new CompoundTag();
        if (isCompartmentLocked(tag, compartment)) {
            // Overpacked 2.x sells the side compartments as backpack_pocket upgrades. Opening one the
            // player never bought would hand it to them for free — our helper entity builds all three
            // containers regardless of the unlock, so the slots would simply be there and the contents
            // would persist on write-back. Overpacked's own right-click falls back to the center
            // compartment here; a keybind is explicit, so say why nothing opened instead.
            player.displayClientMessage(Component.translatable(
                    "message.vanillaplusadditions.overpacked_extensions.compartment_locked"), true);
            return;
        }
        ServerLevel level = player.serverLevel();

        // Recreate the entity exactly as Overpacked's own place-a-backpack code does: colour from the
        // item, everything else from the item's CUSTOM_DATA. On 2.x that is one GiantBackpack.Load()
        // call plus the custom name (Utils.PlaceBackpack); 1.x has no such helper and is restored by
        // hand. Hand-restoring on 2.x would silently drop whatever Load() covers — see below.
        GiantBackpack entity = new GiantBackpack(ModEntities.giant_backpack.get(), level);
        // Park the helper below the player's feet rather than in front of them — see HELPER_DROP for
        // why that matters, and onServerTick() for why it stays there.
        moveHelperToPlayer(entity, player);
        entity.setNoGravity(true);
        entity.noPhysics = true; // transient helper — never blocks or shoves the player
        // Buried in the floor it can still catch an entityInside damage source (a cactus, a player in
        // creative). GiantBackpack.hurt() answers damage by dropping its whole contents as an item —
        // which here is a copy of the worn backpack, so it would be a duplication bug.
        entity.setInvulnerable(true);
        if (worn.stack().getItem() instanceof GiantBackpackItem backpackItem) {
            entity.SetColor(backpackItem.color);
        }
        if (OverpackedCompat.isV2()) {
            // Load() restores sleeping-bag colour, BOTH side-pocket unlocks (RightCell/LeftCell —
            // bought with Overpacked 2.x's backpack_pocket item) and the inventory. The pocket unlocks
            // matter beyond the GUI: on close we write getPickResult()'s CUSTOM_DATA back onto the
            // worn item, so an entity that never learned about them would persist "locked" and
            // destroy the upgrade.
            entity.Load(tag);
            Component customName = worn.stack().get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                // Keeps the GUI title on a renamed backpack; get_stack() re-attaches it to the
                // written-back stack, matching what a placed-and-picked-up backpack does.
                entity.SetName(customName.getString());
            }
        } else {
            // Overpacked 1.x: no Load()/SetName() and no pocket unlocks on the item, so the hand
            // restore is complete. Never reached on 2.x, and never resolved on 1.x — see
            // OverpackedCompat#OVERPACKED_V2 on why both branches can share this method.
            if (tag.contains("SleepingBagColor")) {
                entity.SetSleepingBagColor(tag.getInt("SleepingBagColor"));
            }
            entity.LoadInventory(tag.getCompound("Items"));
        }
        // Markieren, BEVOR sie in der Welt landet: stuerzt der Server ab oder wird er neu gestartet,
        // waehrend das GUI offen ist, wird die Entity mitgespeichert - SESSIONS ist danach aber leer,
        // und onContainerClose laesst sie deshalb in Ruhe. Sie bliebe als echter, abgestellter
        // Rucksack liegen und ihr Inhalt ist eine Kopie des getragenen, also ein Duplikationsweg.
        // Der Tag macht sie beim Laden auffindbar.
        entity.addTag(HELPER_TAG);
        level.addFreshEntity(entity);

        MinecraftServer server = player.getServer();
        long deadline = (server != null ? server.getTickCount() : 0L) + CONFIRM_TIMEOUT_TICKS;
        SESSIONS.put(entity.getId(), new Session(player.getUUID(), worn.identifier(), worn.index(),
                worn.stack().copy(), compartment, deadline));

        // Now ask the client whether it can see the entity. openMenu() only happens once it says yes
        // (confirm()) — never on a timer. See BackpackHelperReadyPacket.
        PacketDistributor.sendToPlayer(player, new BackpackHelperReadyPacket(entity.getId(), compartment));
    }

    /**
     * True when this player already has a helper waiting for its confirmation.
     *
     * <p>Holding the keybind would otherwise spawn one helper per tick, each with a copy of the same
     * inventory. Sessions that are already open are not affected: re-opening a second compartment
     * while the first screen is up is legitimate and closes the first one.</p>
     */
    private static boolean hasPendingHelper(ServerPlayer player) {
        return SESSIONS.values().stream()
                .anyMatch(session -> !session.opened && session.playerUUID().equals(player.getUUID()));
    }

    /**
     * Handles the client's confirmation that it can resolve the helper entity, and only then opens
     * Overpacked's screen.
     *
     * @param player    the player who sent the confirmation
     * @param entityId  the helper entity id, echoed back by the client
     */
    public static void confirm(ServerPlayer player, int entityId) {
        Session session = SESSIONS.get(entityId);
        if (session == null || session.opened || !session.playerUUID().equals(player.getUUID())) {
            return;
        }
        if (!(player.level().getEntity(entityId) instanceof GiantBackpack entity) || !entity.isAlive()) {
            SESSIONS.remove(entityId);
            return;
        }
        session.opened = true;
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) -> new GiantBackpackMenu(id, inv, entity, session.compartment),
                        entity.getDisplayName()),
                buf -> {
                    buf.writeInt(entity.getId());
                    buf.writeByte(session.compartment);
                });
    }

    /** Parks the helper straight below the player, facing the way the player faces. */
    private static void moveHelperToPlayer(GiantBackpack entity, ServerPlayer player) {
        entity.moveTo(player.getX(), player.getY() - HELPER_DROP, player.getZ(),
                player.getYRot() + 180.0f, 0.0f);
        entity.setDeltaMovement(Vec3.ZERO);
    }

    // ---- Event handlers (registered on NeoForge.EVENT_BUS only when isAvailable()) ----

    /**
     * Keeps every live helper glued to its owner and cleans up helpers whose handshake never finished.
     *
     * <p>The gluing is what makes the feature work on a moving Sable airship. A helper spawned at the
     * player's world position simply stays there while the ship — and the player standing on it — flies
     * on: it visibly drifts off, and once it is more than 4 blocks away Overpacked's
     * {@code stillValid} slams the screen shut. Re-parking it under the player every tick sidesteps
     * the whole coordinate-space question, because the player is the reference frame either way.</p>
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        Iterator<Map.Entry<Integer, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Session> entry = it.next();
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerUUID());
            if (player == null) {
                continue; // onLogout owns that case
            }
            Entity ent = player.serverLevel().getEntity(entry.getKey());
            if (!(ent instanceof GiantBackpack helper) || !helper.isAlive()) {
                it.remove();
                continue;
            }
            if (!session.opened && now > session.confirmDeadline) {
                // The client never answered — nothing was edited, so the helper can just go.
                helper.discard();
                it.remove();
                player.displayClientMessage(Component.translatable(
                        "message.vanillaplusadditions.overpacked_extensions.open_failed"), true);
                continue;
            }
            moveHelperToPlayer(helper, player);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getContainer() instanceof GiantBackpackMenu menu)) {
            return;
        }
        GiantBackpack entity = menu.backpack;
        if (entity == null) {
            return;
        }
        Session session = SESSIONS.remove(entity.getId());
        if (session == null) {
            return; // a real, placed backpack — not one of ours; leave it alone
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            writeBack(player, session, entity);
        }
        entity.discard();
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Nur was von der Platte kommt: frisch gespawnte Helfer tragen den Tag auch, haben aber eine
        // lebende Session. loadedFromDisk() trennt beide Faelle, ohne auf die Reihenfolge von
        // addFreshEntity und SESSIONS.put angewiesen zu sein.
        if (!event.loadedFromDisk() || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof GiantBackpack backpack && backpack.getTags().contains(HELPER_TAG)) {
            backpack.discard();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Safety net: discard (and persist) any lingering sessions for this player.
        Iterator<Map.Entry<Integer, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Session> entry = it.next();
            if (!entry.getValue().playerUUID().equals(player.getUUID())) {
                continue;
            }
            Entity ent = player.serverLevel().getEntity(entry.getKey());
            if (ent instanceof GiantBackpack backpack) {
                writeBack(player, entry.getValue(), backpack);
                backpack.discard();
            }
            it.remove();
        }
    }

    /**
     * Serializes the (edited) transient entity back into the worn backpack item and stores it in the
     * original Curios slot. Falls back to the player's inventory/ground if that slot no longer holds
     * a giant backpack, so items are never lost.
     */
    private static void writeBack(ServerPlayer player, Session session, GiantBackpack entity) {
        ItemStack saved = entity.getPickResult(); // colored backpack with full Items/Count NBT
        CustomData data = saved.get(DataComponents.CUSTOM_DATA);
        ItemStack updated = session.originalWorn().copy();
        // The pick-result IS the source of truth for the edited contents. When the player empties the
        // backpack completely, Overpacked's save_item(0) returns a bare stack with NO CUSTOM_DATA — so
        // we must CLEAR the stale Items on the worn item, not keep them. Otherwise the old contents
        // reappear on the next open (duplication).
        if (data != null) {
            updated.set(DataComponents.CUSTOM_DATA, data);
        } else {
            updated.remove(DataComponents.CUSTOM_DATA);
        }
        if (CuriosBackpackAccess.isGiantBackpackInSlot(player, session.identifier(), session.index())) {
            CuriosBackpackAccess.setWorn(player, session.identifier(), session.index(), updated);
        } else if (!player.getInventory().add(updated)) {
            player.drop(updated, false);
        }
    }
}
