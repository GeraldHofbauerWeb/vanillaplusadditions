package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.client;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side setup for the mob loader/unloader: registers the spinning mini-mob block entity
 * renderer for both block entity types. Auto-discovered by NeoForge's {@code @EventBusSubscriber}
 * scan (client dist, mod bus).
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MobCartLoaderClientSetup {

    private MobCartLoaderClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MobCartLoaderModule.MOB_LOADER_BE.get(), MobCartBER::new);
        event.registerBlockEntityRenderer(MobCartLoaderModule.MOB_UNLOADER_BE.get(), MobCartBER::new);
    }
}
