package net.geraldhofbauer.vanillaplusadditions.modules.train_chunk_loading.client;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds the Chunk Loader Track's blockstate and models as a built-in pack, and only when Create is
 * loaded.
 *
 * <p><b>Why a pack and not plain assets:</b> the models load Create's OBJ meshes
 * ({@code create:models/block/track/*.obj}). Minecraft loads <em>every</em> model file in a mod's
 * namespace at startup, whether a block uses it or not, so without Create each of them failed with
 * a red {@code Failed to load model ...} line - eleven of them, in a log that is otherwise clean.
 * Harmless, but exactly what makes a stranger (or a Modrinth moderator) think the mod is broken.
 * In a pack that is only added alongside Create, the files are simply never read without it.
 *
 * <p>The gate is Create alone, deliberately not this module's {@code enabled} key: whenever Create
 * is present the block is registered, and a registered block without models would render as the
 * purple-black cube. Same pattern as {@code QuarkFreshAnimationsPack}; no Create type is named here,
 * so this class is safe to load without Create.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChunkLoaderTrackModelPack {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkLoaderTrackModelPack.class);

    /** Path inside the owning mod jar, and the folder this repo keeps the pack in. */
    private static final String PACK_PATH = "resourcepacks/vpa_create_track";

    private ChunkLoaderTrackModelPack() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded("create")) {
            return;
        }
        ResourceLocation location = locatePack();
        if (location == null) {
            LOGGER.warn("{} is not in any loaded mod jar - the Chunk Loader Track will have no models", PACK_PATH);
            return;
        }
        // alwaysActive: the block exists, so its models must too; there is nothing to opt into.
        event.addPackFinders(
                location,
                PackType.CLIENT_RESOURCES,
                Component.literal("VPA Chunk Loader Track"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }

    /**
     * Finds the jar that carries the pack: the bundle and the standalone module jar have different
     * mod ids, and {@code addPackFinders} resolves the namespace as a mod id.
     *
     * @return the pack's location, or null when no loaded jar carries it
     */
    private static ResourceLocation locatePack() {
        for (IModInfo info : ModList.get().getMods()) {
            try {
                Path candidate = info.getOwningFile().getFile().findResource(PACK_PATH);
                if (Files.exists(candidate)) {
                    return ResourceLocation.fromNamespaceAndPath(info.getModId(), PACK_PATH);
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Could not look inside {}: {}", info.getModId(), e.toString());
            }
        }
        return null;
    }
}
