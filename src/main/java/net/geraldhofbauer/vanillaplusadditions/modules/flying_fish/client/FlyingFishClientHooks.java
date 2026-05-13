package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.client;

import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.FlyingFishModule;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class FlyingFishClientHooks {
    private FlyingFishClientHooks() {
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!FlyingFishModule.isContentRegistered()) {
            return;
        }

        event.registerEntityRenderer(FlyingFishModule.FLYING_FISH.get(), FlyingFishRenderer::new);
    }
}

