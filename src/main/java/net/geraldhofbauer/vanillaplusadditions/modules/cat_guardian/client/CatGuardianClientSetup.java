package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.client.model.CatModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CatGuardianClientSetup {

    private CatGuardianClientSetup() { }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<Cat> renderer = event.getRenderer(EntityType.CAT);
        if (renderer instanceof LivingEntityRenderer<?, ?> lr) {
            @SuppressWarnings("unchecked")
            LivingEntityRenderer<Cat, CatModel<Cat>> catRenderer =
                    (LivingEntityRenderer<Cat, CatModel<Cat>>) lr;
            catRenderer.addLayer(new CatArmorLayer(catRenderer));
        }
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CatGuardianModule.CAT_FEEDING_STATION_MENU.get(), CatFeedingStationScreen::new);
    }
}
