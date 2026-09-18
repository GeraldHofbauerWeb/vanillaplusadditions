package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.client;

import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side setup for the Compass Overhaul: binds the needle function to the World Compass.
 *
 * <p>The predicate is named {@code minecraft:angle} on purpose — the per-item property map is keyed
 * by item first, so reusing vanilla's key collides with nothing and lets the model file say plain
 * {@code "angle"}, exactly like the vanilla compass model it was generated from.
 *
 * <p>{@code ItemProperties.PROPERTIES} is a plain {@code HashMap} and client setup runs in parallel,
 * so the registration has to go through {@code enqueueWork}.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CompassOverhaulClientSetup {

    private CompassOverhaulClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                CompassOverhaulModule.WORLD_COMPASS.get(),
                ResourceLocation.withDefaultNamespace("angle"),
                WorldCompassAngle.create()));
    }
}
