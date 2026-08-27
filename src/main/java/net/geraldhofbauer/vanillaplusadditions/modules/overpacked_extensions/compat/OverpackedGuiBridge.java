package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.compat;

import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.compat.CuriosBackpackAccess.Worn;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    /** Transient backpack entities we spawned, keyed by entity id → the slot to write back to. */
    private static final Map<Integer, Session> SESSIONS = new HashMap<>();

    private record Session(UUID playerUUID, String identifier, int index, ItemStack originalWorn) {
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
        // Place the helper a bit in front of the player along their (horizontal) look direction — not
        // inside the player — and rotate it to face the player (yRot + 180), exactly like Overpacked's
        // own GiantBackpackItem.use() does when you place a backpack on the ground.
        Vec3 look = player.getViewVector(1.0f);
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        double horizontalLen = horizontal.length();
        Vec3 forward = horizontalLen > 1.0e-4
                ? horizontal.scale(1.0 / horizontalLen)
                : Vec3.directionFromRotation(0.0f, player.getYRot());
        double spawnDistance = 1.5;
        entity.moveTo(
                player.getX() + forward.x * spawnDistance,
                player.getY(),
                player.getZ() + forward.z * spawnDistance,
                player.getYRot() + 180.0f,
                0.0f);
        // Would the in-front spot drop the helper into a block (e.g. the wall a mounted item frame
        // hangs on, when the player stands right at the shelf)? Then it would overlap the frame /
        // entity there — stealing the frame's crosshair pick and becoming an accidental hit target
        // whose GiantBackpack.hurt() would dump its contents. Fall back to spawning it inside the
        // player: out of the crosshair, off wall frames. Mirrors the noCollision guard in
        // Overpacked's own GiantBackpackItem.use(). A clear spot in front keeps the 1.5-block offset.
        if (!level.noCollision(entity, entity.getBoundingBox())) {
            entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0f, 0.0f);
        }
        entity.setNoGravity(true);
        entity.noPhysics = true; // transient helper — never blocks or shoves the player
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
        level.addFreshEntity(entity);

        SESSIONS.put(entity.getId(),
                new Session(player.getUUID(), worn.identifier(), worn.index(), worn.stack().copy()));

        // Defer the open by one tick so the entity-spawn packet reaches the client before the
        // open-screen packet — otherwise Overpacked's client menu factory can't resolve the entity.
        MinecraftServer server = player.getServer();
        if (server == null) {
            openMenuNow(player, entity, compartment);
            return;
        }
        server.tell(new TickTask(server.getTickCount() + 1, () -> openMenuNow(player, entity, compartment)));
    }

    private static void openMenuNow(ServerPlayer player, GiantBackpack entity, int compartment) {
        if (!entity.isAlive() || player.hasDisconnected()) {
            // GUI never opened — nothing was edited, just drop the helper entity.
            SESSIONS.remove(entity.getId());
            entity.discard();
            return;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) -> new GiantBackpackMenu(id, inv, entity, compartment),
                        entity.getDisplayName()),
                buf -> {
                    buf.writeInt(entity.getId());
                    buf.writeByte(compartment);
                });
    }

    // ---- Event handlers (registered on NeoForge.EVENT_BUS only when isAvailable()) ----

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
