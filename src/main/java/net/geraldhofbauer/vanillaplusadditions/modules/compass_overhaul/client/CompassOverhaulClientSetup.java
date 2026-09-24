package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/**
 * Client-side setup for the Compass Overhaul: binds the needle function to the World Compass.
 *
 * <p>The predicate is named {@code minecraft:angle} on purpose — the per-item property map is keyed
 * by item first, so reusing vanilla's key collides with nothing and lets the model file say plain
 * {@code "angle"}, exactly like the vanilla compass model it was generated from.
 *
 * <p>{@code ItemProperties.PROPERTIES} is a plain {@code HashMap} and client setup runs in parallel,
 * so the registration has to go through {@code enqueueWork}.
 *
 * <p>Also registers the sprite source that builds the 32 dial sprites out of the player's own
 * compass frames at atlas-stitch time — see {@link WorldCompassSpriteSource} for why the textures
 * are no longer shipped as files.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CompassOverhaulClientSetup {

    /**
     * The id the atlas JSON refers to. It is spelled out here rather than derived, because
     * {@code assets/minecraft/atlases/blocks.json} has to name the very same string. That file
     * lives in the MINECRAFT namespace on purpose: the atlas is looked up by its own id
     * ({@code minecraft:blocks}), so a copy under our namespace is never read.
     */
    private static final String SPRITE_SOURCE_ID = "world_compass";

    private CompassOverhaulClientSetup() {
    }

    /**
     * Fires once while the {@code Minecraft} instance is being built, so the returned type has to
     * be handed to the source class immediately — there is no second chance to look it up.
     */
    @SubscribeEvent
    public static void onRegisterSpriteSourceTypes(RegisterSpriteSourceTypesEvent event) {
        WorldCompassSpriteSource.setRegisteredType(event.register(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, SPRITE_SOURCE_ID),
                WorldCompassSpriteSource.CODEC));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                CompassOverhaulModule.WORLD_COMPASS.get(),
                ResourceLocation.withDefaultNamespace("angle"),
                WorldCompassAngle.create()));
    }
}
