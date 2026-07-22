package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.client;

import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.EndConduitModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side setup for the End Conduit: registers the block entity renderer. Auto-discovered by
 * NeoForge's {@code @EventBusSubscriber} scan (client dist, mod bus).
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EndConduitClientSetup {

    private EndConduitClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EndConduitModule.END_CONDUIT_BE.get(), EndConduitBER::new);
    }
}
