package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.compat;

import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.compat.CuriosBackpackAccess.Worn;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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
 * live inside method bodies and are reached only when {@link #isAvailable()} holds — the module never
 * registers this class's event handlers otherwise (mirrors {@code end_oxygen} / Create compat).
 */
public final class OverpackedGuiBridge {

    private static final boolean OVERPACKED_LOADED = ModList.get().isLoaded("overpacked");

    /** Transient backpack entities we spawned, keyed by entity id → the slot to write back to. */
    private static final Map<Integer, Session> SESSIONS = new HashMap<>();

    private record Session(UUID playerUUID, String identifier, int index, ItemStack originalWorn) {
    }

    private OverpackedGuiBridge() {
    }

    /** True when both Overpacked and Curios are present (Overpacked hard-requires Curios). */
    public static boolean isAvailable() {
        return OVERPACKED_LOADED && CuriosBackpackAccess.CURIOS_LOADED;
    }

    /**
     * Opens the given compartment (0 = center/main, 1 = right, 2 = left) of the player's worn giant
     * backpack, reusing Overpacked's own menu + screen.
     */
    public static void open(ServerPlayer player, int compartment) {
        Optional<Worn> wornOpt = CuriosBackpackAccess.findWorn(player);
        if (wornOpt.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.vanillaplusadditions.overpacked_backpack_keys.no_backpack"), true);
            return;
        }
        Worn worn = wornOpt.get();
        ServerLevel level = player.serverLevel();

        // Recreate the entity exactly as GiantBackpackItem.use() does: colour from the item, sleeping
        // bag colour + inventory from the item's CUSTOM_DATA (LoadInventory takes the "Items" subtag).
        GiantBackpack entity = new GiantBackpack(ModEntities.giant_backpack.get(), level);
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        entity.setNoGravity(true);
        entity.noPhysics = true; // transient helper — never blocks or shoves the player
        if (worn.stack().getItem() instanceof GiantBackpackItem backpackItem) {
            entity.SetColor(backpackItem.color);
        }
        CustomData data = worn.stack().get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
        if (tag.contains("SleepingBagColor")) {
            entity.SetSleepingBagColor(tag.getInt("SleepingBagColor"));
        }
        entity.LoadInventory(tag.getCompound("Items"));
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
        if (data != null) {
            updated.set(DataComponents.CUSTOM_DATA, data);
        }
        if (CuriosBackpackAccess.isGiantBackpackInSlot(player, session.identifier(), session.index())) {
            CuriosBackpackAccess.setWorn(player, session.identifier(), session.index(), updated);
        } else if (!player.getInventory().add(updated)) {
            player.drop(updated, false);
        }
    }
}
