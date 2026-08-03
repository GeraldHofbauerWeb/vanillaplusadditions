package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.resources.ResourceLocation;

/**
 * Flywheel partial models for the Chunk Loader Track's curved (bezier) connections — retextured
 * wrappers of Create's tie/segment OBJ models. Client-only; referenced solely through the lazy
 * suppliers handed to {@code TrackMaterialFactory.customModels}, which Create evaluates inside
 * {@code executeOnClientOnly}. Must be created before model baking — which holds, since the
 * material is built during block registration.
 */
public final class ChunkLoaderTrackModels {

    public static final PartialModel TIE = partial("tie");
    public static final PartialModel SEGMENT_LEFT = partial("segment_left");
    public static final PartialModel SEGMENT_RIGHT = partial("segment_right");

    private ChunkLoaderTrackModels() {
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                VanillaPlusAdditions.MODID, "block/chunk_loader_track/" + name));
    }
}
