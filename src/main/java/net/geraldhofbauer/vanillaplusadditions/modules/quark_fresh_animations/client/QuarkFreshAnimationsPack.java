package net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations.client;

import net.geraldhofbauer.vanillaplusadditions.modules.quark_fresh_animations.QuarkFreshAnimationsModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Puts the bundled Fresh-Animations-for-Quark pack in front of the pack repository, and only when it
 * has something to do.
 *
 * <p>Two conditions, for two different reasons:
 *
 * <ul>
 *   <li><b>Quark</b> — a mod, so this is an exact {@link ModList} check. Without it the animals the
 *       pack describes do not exist and the files would describe nothing.</li>
 *   <li><b>Fresh Animations</b> — a resource pack, not a mod, so there is no id to ask for and the
 *       file name is the only handle there is. That makes it a heuristic, which is why it is
 *       documented as one and can be switched off in the config.</li>
 * </ul>
 *
 * <p>Deliberately absent is a check for Entity Model Features. Without EMF the pack's files are read
 * by nobody — inert, not broken — so gating on it would only add a way to wrongly refuse.
 *
 * <p>No Quark type is named anywhere in this class. A class is verified when it is loaded, and a
 * signature mentioning a class from an absent mod brings the whole thing down with a
 * {@code NoClassDefFoundError} — the trap that cost a crash in beta.70.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class QuarkFreshAnimationsPack {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuarkFreshAnimationsPack.class);

    /** Path inside the owning mod jar, and the folder this repo keeps the pack in. */
    private static final String PACK_PATH = "resourcepacks/vpa_quark_fresh_animations";

    /**
     * The Toretoise in a pack of its own. Its model only renders safely with
     * {@code ToretoiseRandomCubeMixin}, so it must never travel in the standalone zip - and it is
     * added even while that zip is installed, because the zip does not carry it.
     */
    private static final String TORETOISE_PACK_PATH = "resourcepacks/vpa_quark_toretoise";

    /** A pack file name reduced to bare letters has to contain this for Fresh Animations to count. */
    private static final String FRESH_ANIMATIONS_MARKER = "freshanimations";

    /**
     * Letters-only name of the standalone copy that {@code ./gradlew quarkFreshAnimationsPack} builds
     * and Modrinth ships as "Quark x Fresh Animations" ({@code Quark-x-Fresh-Animations-<version>.zip}).
     */
    private static final String STANDALONE_PACK_MARKER = "quarkxfreshanimations";

    /** The name {@code scripts/deploy.sh --pack} gives the same zip in the dev instance. */
    private static final String STANDALONE_DEV_PACK_MARKER = "vpaquarkfreshanimations";

    /** Letters-only name of the dev-only Toretoise zip ({@code ./gradlew quarkToretoiseDevPack}). */
    private static final String TORETOISE_DEV_PACK_MARKER = "vpaquarktoretoisedev";

    private QuarkFreshAnimationsPack() { }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        QuarkFreshAnimationsModule module = QuarkFreshAnimationsModule.getInstance();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }
        if (!ModList.get().isLoaded("quark")) {
            return;
        }
        if (module.getConfig().requiresFreshAnimations() && !resourcePackFolderContains(FRESH_ANIMATIONS_MARKER)) {
            LOGGER.debug("No Fresh Animations pack found, leaving the Quark companion pack out");
            return;
        }

        // A dev copy in the resourcepacks folder wins, so F3+T picks up edits without a restart.
        if (resourcePackFolderContains(TORETOISE_DEV_PACK_MARKER)) {
            LOGGER.info("Dev Toretoise pack found, leaving the built-in copy out");
        } else {
            addPack(event, TORETOISE_PACK_PATH, "VPA Fresh Animations for Quark: Toretoise");
        }

        // The standalone zip carries the same files. Both active would leave it to the pack order
        // which copy wins, and the built-in one sits on top - edits to the standalone pack would
        // then never show. Whoever installed the zip wants that one.
        if (resourcePackFolderContains(STANDALONE_PACK_MARKER) || resourcePackFolderContains(STANDALONE_DEV_PACK_MARKER)) {
            LOGGER.info("Standalone VPA Quark Fresh Animations pack found, leaving the built-in copy out");
            return;
        }
        addPack(event, PACK_PATH, "VPA Fresh Animations for Quark");
    }

    private static void addPack(AddPackFindersEvent event, String path, String title) {
        ResourceLocation location = locatePack(path);
        if (location == null) {
            LOGGER.warn("{} is not in any loaded mod jar - the companion pack stays out", path);
            return;
        }

        // alwaysActive: this is the whole point. A resource pack added this way is off by default,
        // and a compatibility pack nobody switches on compatibly does nothing. The off switch is
        // this module's own enabled key, not the pack list.
        event.addPackFinders(
            location,
            PackType.CLIENT_RESOURCES,
            Component.literal(title),
            PackSource.BUILT_IN,
            true,
            Pack.Position.TOP
        );
    }

    /**
     * Finds the jar that carries the pack, without assuming which one that is.
     *
     * <p>{@code addPackFinders} resolves its argument's namespace as a mod id, and that id differs
     * between the bundle ({@code vanillaplusadditions}) and the standalone module jar. Asking every
     * loaded mod file which one actually holds the folder is shorter than keeping a list of names in
     * sync with the build.
     *
     * @param path the pack folder inside the jar
     * @return the pack's location, or null when no loaded jar carries it
     */
    private static ResourceLocation locatePack(String path) {
        for (IModInfo info : ModList.get().getMods()) {
            try {
                Path candidate = info.getOwningFile().getFile().findResource(path);
                if (Files.exists(candidate)) {
                    return ResourceLocation.fromNamespaceAndPath(info.getModId(), path);
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Could not look inside {}: {}", info.getModId(), e.toString());
            }
        }
        return null;
    }

    /**
     * Whether something in the game's resourcepacks folder carries the given name.
     *
     * <p>Name matching, because a resource pack has no id. Letters only and lower-cased, so
     * {@code FreshAnimations_v1.10.4.zip} and {@code fresh-animations} both count.
     *
     * @param marker letters-only, lower-case name fragment
     * @return true when a matching file or folder is there
     */
    private static boolean resourcePackFolderContains(String marker) {
        Path directory = FMLPaths.GAMEDIR.get().resolve("resourcepacks");
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.anyMatch(entry -> letters(entry.getFileName().toString())
                    .contains(marker));
        } catch (IOException e) {
            LOGGER.debug("Could not read {}: {}", directory, e.toString());
            return false;
        }
    }

    private static String letters(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
