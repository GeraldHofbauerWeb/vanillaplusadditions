package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.client;

import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.EndConduitModule;
import net.minecraft.client.particle.FlyTowardsPositionParticle;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-side setup for the End Conduit: registers the block entity renderer and the item's custom
 * 3D shell renderer. Auto-discovered by NeoForge's {@code @EventBusSubscriber} scan (client dist,
 * mod bus).
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EndConduitClientSetup {

    private EndConduitClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EndConduitModule.END_CONDUIT_BE.get(), EndConduitBER::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        // Reuse the vanilla nautilus particle behaviour, bound to our tinted sprite.
        event.registerSpriteSet(EndConduitModule.END_NAUTILUS.get(),
                FlyTowardsPositionParticle.NautilusProvider::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private EndConduitItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new EndConduitItemRenderer();
                }
                return this.renderer;
            }
        }, EndConduitModule.END_CONDUIT_ITEM.get());
    }
}
