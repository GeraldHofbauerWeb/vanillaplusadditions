package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderIndex;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.TrainChunkLoadingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Ponder entry ("W" on the Chunk Loader Track item) explaining what the track does and — most
 * importantly — how far apart loader tracks may be placed. Client-only; registered from the
 * module's {@code onClientSetup} (Create present, same loading window Create uses for its own
 * plugin).
 *
 * <p>NOTE: outside Ponder's editing mode all scene text is resolved through lang keys
 * ({@code vanillaplusadditions.ponder.chunk_loader_track_spacing.header/text_N}) — the literals
 * below are fallbacks for editing mode/datagen only. Keep the lang files in sync with the order
 * of {@code text()} calls.</p>
 */
public final class ChunkLoaderTrackPonder {

    private ChunkLoaderTrackPonder() {
    }

    public static void register() {
        PonderIndex.addPlugin(new Plugin());
    }

    private static final class Plugin implements PonderPlugin {
        @Override
        public String getModId() {
            return VanillaPlusAdditions.MODID;
        }

        @Override
        public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
            helper.forComponents(TrainChunkLoadingModule.CHUNK_LOADER_TRACK_ITEM.getId())
                    .addStoryBoard("chunk_loader_track/spacing", ChunkLoaderTrackPonder::spacingScene);
        }
    }

    private static void spacingScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("chunk_loader_track_spacing", "Placing Chunk Loader Tracks");
        scene.configureBasePlate(8, 0, 9);
        scene.scaleSceneView(0.65f);
        scene.removeShadow();
        scene.setSceneOffsetY(-1.0f);
        scene.showBasePlate();
        scene.idle(5);
        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        scene.idle(15);

        BlockPos leftLoader = util.grid().at(2, 1, 4);
        BlockPos rightLoader = util.grid().at(22, 1, 4);

        scene.overlay().showText(120)
                .text("Chunk Loader Tracks keep the area around passing trains loaded. Without "
                        + "them, Create merely simulates distant trains — onboard drills, deployers "
                        + "and item transfers stop working.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(12, 1, 4))
                .placeNearTarget();
        scene.idleSeconds(7);

        scene.overlay().showOutline(PonderPalette.BLUE, "loader_left",
                util.select().position(leftLoader), 200);
        scene.overlay().showOutline(PonderPalette.BLUE, "loader_right",
                util.select().position(rightLoader), 200);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.MEDIUM, "band_left",
                new AABB(leftLoader.getX() - 4.5, 0.0, -0.5,
                        leftLoader.getX() + 5.5, 3.0, 9.5), 200);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.MEDIUM, "band_right",
                new AABB(rightLoader.getX() - 4.5, 0.0, -0.5,
                        rightLoader.getX() + 5.5, 3.0, 9.5), 200);
        scene.idle(10);
        scene.overlay().showText(120)
                .text("While a carriage is above a loader track, a square of chunks around that "
                        + "track stays force-loaded (chunk_load_radius, default 2 = a 5x5 area).")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(leftLoader))
                .placeNearTarget();
        scene.idleSeconds(8);

        scene.overlay().showBigLine(PonderPalette.GREEN,
                util.vector().topOf(leftLoader).add(0, 1.5, 0),
                util.vector().topOf(rightLoader).add(0, 1.5, 0), 160);
        scene.overlay().showText(160)
                .text("Rule of thumb: place a loader track at most chunk_load_radius x 16 blocks "
                        + "apart along the line — 32 blocks with default settings. The loaded "
                        + "corridor then rolls along with the train and never loses it.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(12, 1, 4))
                .placeNearTarget();
        scene.idleSeconds(9);

        scene.overlay().showText(140)
                .text("A track in an already-unloaded chunk cannot see the simulated train. "
                        + "Loaded areas are released after a timeout and restored automatically "
                        + "after restarts or when players return.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(rightLoader))
                .placeNearTarget();
        scene.idleSeconds(8);

        scene.markAsFinished();
    }
}
