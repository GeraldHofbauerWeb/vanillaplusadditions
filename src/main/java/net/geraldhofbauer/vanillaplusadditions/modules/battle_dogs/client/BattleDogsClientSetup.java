package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.client;

import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.compat.quark.FoxhoundArmorLayers;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.compat.quark.QuarkFoxhoundCompat;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BattleDogsClientSetup {

    private BattleDogsClientSetup() { }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<?> renderer = event.getRenderer(EntityType.WOLF);
        if (renderer instanceof LivingEntityRenderer<?, ?> lr) {
            @SuppressWarnings("unchecked")
            LivingEntityRenderer<Wolf, WolfModel<Wolf>> wolfRenderer =
                    (LivingEntityRenderer<Wolf, WolfModel<Wolf>>) lr;
            wolfRenderer.addLayer(new BattleDogsArmorLayer(wolfRenderer, event.getEntityModels()));
        }

        // Quark's Foxhound extends Wolf but has a renderer of its own, so the layer above never
        // reaches it - and Quark's own armour layer only fires for the vanilla wolf_armor item.
        // Without this a foxhound wore our armour invisibly.
        if (QuarkFoxhoundCompat.isAvailable()) {
            FoxhoundArmorLayers.addTo(event);
        }
    }
}
