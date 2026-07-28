package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.client;

import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network.OpenBackpackCompartmentPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drains the backpack keybinds each client tick and asks the server to open the matching
 * compartment. The server decides whether the player actually wears a backpack (and whether the
 * feature is enabled), so no client-side state is needed here.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class BackpackKeysClientEvents {

    private BackpackKeysClientEvents() {
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
    }
}
