package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.client;

import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network.BackpackHelperReadyPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network.ConfirmBackpackHelperPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network.OpenBackpackCompartmentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drains the backpack keybinds each client tick and asks the server to open the matching
 * compartment. The server decides whether the player actually wears a backpack (and whether the
 * feature is enabled), so no client-side state is needed for that part.
 *
 * <p>The one piece of state that <em>is</em> needed lives here: the pending helper entity. See
 * {@link BackpackHelperReadyPacket} — the server does not open the screen on its own initiative, it
 * waits until this class confirms that the entity has arrived.</p>
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class BackpackKeysClientEvents {

    /**
     * How long to wait for the helper entity before giving up, in client ticks (5 s).
     *
     * <p>Generous on purpose: a slow connection must not cost the player their backpack. Giving up
     * merely means no screen opens — the server discards the helper on its own timeout either way.</p>
     */
    private static final int CONFIRM_TIMEOUT_TICKS = 100;

    /** Network id of the helper entity we are waiting for, or -1 when nothing is pending. */
    private static int pendingEntityId = -1;
    private static int pendingCompartment;
    private static int pendingTicksLeft;

    private BackpackKeysClientEvents() {
    }

    /** Handles {@link BackpackHelperReadyPacket}: start watching for the entity. */
    public static void handleHelperReady(BackpackHelperReadyPacket packet) {
        pendingEntityId = packet.entityId();
        pendingCompartment = packet.compartment();
        pendingTicksLeft = CONFIRM_TIMEOUT_TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Compartment ids match Overpacked's inv_id: 0 = center (main), 1 = right, 2 = left.
        while (BackpackKeybinds.OPEN_MAIN.consumeClick()) {
            PacketDistributor.sendToServer(new OpenBackpackCompartmentPacket(0));
        }
        while (BackpackKeybinds.OPEN_RIGHT.consumeClick()) {
            PacketDistributor.sendToServer(new OpenBackpackCompartmentPacket(1));
        }
        while (BackpackKeybinds.OPEN_LEFT.consumeClick()) {
            PacketDistributor.sendToServer(new OpenBackpackCompartmentPacket(2));
        }
        tickPendingHelper();
    }

    /**
     * Confirms the pending helper entity as soon as this client can resolve it.
     *
     * <p>{@code getEntity(id) != null} is the whole test, and it is deliberately not narrowed to
     * Overpacked's entity class: naming that type here would link Overpacked into a class that runs
     * on every client, Overpacked installed or not. Entity ids are unique per connection, so a
     * non-null answer is already the answer we need.</p>
     */
    private static void tickPendingHelper() {
        if (pendingEntityId < 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            pendingEntityId = -1;
            return;
        }
        if (level.getEntity(pendingEntityId) != null) {
            PacketDistributor.sendToServer(new ConfirmBackpackHelperPacket(pendingEntityId, pendingCompartment));
            pendingEntityId = -1;
            return;
        }
        pendingTicksLeft--;
        if (pendingTicksLeft <= 0) {
            pendingEntityId = -1;
        }
    }
}
