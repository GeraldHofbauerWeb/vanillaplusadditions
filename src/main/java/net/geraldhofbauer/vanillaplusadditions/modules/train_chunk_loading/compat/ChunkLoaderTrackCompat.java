package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.compat;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackMaterialFactory;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.TrainChunkLoadingModule;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.block.ChunkLoaderTrackBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * All Create-typed pieces of the train_chunk_loading module: the custom {@link TrackMaterial}
 * (self-registers into {@code TrackMaterial.ALL} on construction — no registry event exists),
 * block/item factories, and the {@code validBlocks} extension of Create's track block entity type.
 *
 * <p>Only classloaded once the module's {@code shouldInitialize()} confirmed Create is present
 * (isolation pattern like {@code mob_cart_loader}'s {@code CreateTrainAccess}). Compiles against
 * Create's jar-in-jar libs (Registrate/Flywheel) extracted into {@code libs/create-nested/} —
 * compile-only, the runtime classes come from Create's own jarjar.</p>
 */
public final class ChunkLoaderTrackCompat {

    /**
     * Built when this class is first loaded, i.e. during block registration — before world load
     * and before client model baking. {@code customModels} is wrapped in Create's
     * {@code executeOnClientOnly}, so the Flywheel PartialModel holder class is never touched on
     * a dedicated server.
     */
    public static final TrackMaterial MATERIAL = TrackMaterialFactory
            .make(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "chunk_loader"))
            .lang("Chunk Loader")
            .block(() -> () -> (TrackBlock) TrainChunkLoadingModule.CHUNK_LOADER_TRACK.get())
            .particle(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "block/chunk_loader_track"))
            // Sleeper/rails are TrackMaterial metadata (used by Create's datagen, not by us) —
            // the actual crafting recipe is code-injected in TrainChunkLoadingModule.
            .sleeper(Ingredient.of(Items.ENDER_PEARL))
            .rails(Ingredient.of(Items.IRON_NUGGET))
            .trackType(TrackMaterial.TrackType.STANDARD)
            .customModels(
                    () -> () -> net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading
                            .client.ChunkLoaderTrackModels.TIE,
                    () -> () -> net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading
                            .client.ChunkLoaderTrackModels.SEGMENT_LEFT,
                    () -> () -> net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading
                            .client.ChunkLoaderTrackModels.SEGMENT_RIGHT)
            .build();

    private ChunkLoaderTrackCompat() {
    }

    public static Block createBlock() {
        // Mirrors Create's own track properties (AllBlocks.TRACK).
        return new ChunkLoaderTrackBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(0.8F)
                .sound(SoundType.METAL)
                .noOcclusion(),
                MATERIAL);
    }

    public static BlockItem createItem() {
        // Create's TrackBlockItem (public ctor) provides the full curve/bezier placement UX.
        return new com.simibubi.create.content.trains.track.TrackBlockItem(
                TrainChunkLoadingModule.CHUNK_LOADER_TRACK.get(), new Item.Properties());
    }

    /**
     * Adds our block to Create's track {@link BlockEntityType} valid-blocks set so the block
     * entities carrying curved-connection data survive chunk reloads. NeoForge runs official
     * (Mojang) mappings at runtime, so the field name is stable. Graceful degradation: if this
     * fails, the track still fully works — only curved connections vanish on chunk reload.
     */
    public static void extendTrackBlockEntityType(Block block, Logger logger) {
        try {
            BlockEntityType<?> type = AllBlockEntityTypes.TRACK.get();
            Field field = BlockEntityType.class.getDeclaredField("validBlocks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Block> valid = (Set<Block>) field.get(type);
            Set<Block> extended = new HashSet<>(valid);
            extended.add(block);
            field.set(type, extended);
        } catch (ReflectiveOperationException | ClassCastException e) {
            logger.warn("Could not extend Create's track block entity type; curved connections on "
                    + "Chunk Loader Tracks will not persist across chunk reloads.", e);
        }
    }
}
