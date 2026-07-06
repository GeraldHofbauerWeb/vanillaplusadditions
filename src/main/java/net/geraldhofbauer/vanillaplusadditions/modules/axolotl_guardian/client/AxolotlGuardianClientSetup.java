package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.minecraft.client.model.AxolotlModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AxolotlGuardianClientSetup {

    private AxolotlGuardianClientSetup() { }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<Axolotl> renderer = event.getRenderer(EntityType.AXOLOTL);
        if (renderer instanceof LivingEntityRenderer<?, ?> lr) {
            @SuppressWarnings("unchecked")
            LivingEntityRenderer<Axolotl, AxolotlModel<Axolotl>> axolotlRenderer =
                    (LivingEntityRenderer<Axolotl, AxolotlModel<Axolotl>>) lr;
            axolotlRenderer.addLayer(new AxolotlArmorLayer(axolotlRenderer));
        }
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AxolotlGuardianModule.AXOLOTL_FEEDING_STATION_MENU.get(), AxolotlFeedingStationScreen::new);
        event.register(AxolotlGuardianModule.AXOLOTL_INVENTORY_MENU.get(), AxolotlInventoryScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                AxolotlGuardianModule.AXOLOTL_FEEDING_STATION_BE.get(),
                AxolotlFeedingStationBER::new
        );
    }
}
