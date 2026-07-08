package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.client;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class MysticalCatClientHooks {
    private MysticalCatClientHooks() {
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!MysticalCatModule.isContentRegistered()) {
            return;
        }
        event.registerEntityRenderer(MysticalCatModule.MYSTICAL_CAT.get(), MysticalCatRenderer::new);
    }
}
