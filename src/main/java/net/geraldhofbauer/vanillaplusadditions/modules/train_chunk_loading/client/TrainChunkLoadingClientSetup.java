package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/**
 * Registers the sprite source that paints the Chunk Loader Track textures.
 *
 * <p>Deliberately unconditional and free of Create types: this class is loaded by the annotation
 * scan whether Create is installed or not, and the type has to exist even without Create - the atlas
 * JSON names it, and an unknown type makes Minecraft discard the whole atlas file (including the
 * world compass entry in the bundle). Without Create the source just finds no textures and adds no
 * sprites. See {@link ChunkLoaderTrackSpriteSource}.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TrainChunkLoadingClientSetup {

    /** Must match the {@code type} in {@code atlases/blocks/train_chunk_loading.json}. */
    private static final String SPRITE_SOURCE_ID = "chunk_loader_track";

    private TrainChunkLoadingClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterSpriteSourceTypes(RegisterSpriteSourceTypesEvent event) {
        ChunkLoaderTrackSpriteSource.setRegisteredType(event.register(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, SPRITE_SOURCE_ID),
                ChunkLoaderTrackSpriteSource.CODEC));
    }
}
