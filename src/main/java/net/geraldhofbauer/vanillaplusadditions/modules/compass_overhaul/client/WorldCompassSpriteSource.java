package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.slf4j.Logger;

/**
 * Erzeugt die 32 Weltkompass-Sprites zur Ladezeit aus den Kompass-Frames des Spielers.
 *
 * <p><b>Warum ueberhaupt:</b> bis beta.91 lagen 32 fertig umgefaerbte PNGs im Jar. Das waren
 * abgeleitete Mojang-Assets — fuer ein privates Modpack ueblich, fuer eine Veroeffentlichung
 * heikel. Diese Quelle liest die Frames stattdessen aus dem Ressourcen-Stapel des Spielers, der
 * sie ohnehin besitzt, und faerbt sie in Java um. Im Jar liegt damit kein einziges fremdes Pixel
 * mehr, nur noch Code und eine Atlas-JSON.
 *
 * <p><b>Die Atlas-JSON liegt unter {@code assets/minecraft/atlases/blocks.json}</b>, nicht unter
 * unserem Namespace: {@code SpriteSourceList.load} baut den Dateinamen aus der ID des Atlas
 * ({@code minecraft:blocks}) und liest diesen einen Pfad aus jedem Pack. Eine Datei im eigenen
 * Namespace wird schlicht nie geoeffnet - ohne Fehlermeldung, nur mit fehlenden Sprites.
 *
 * <p><b>Der Nebeneffekt ist der bessere Teil:</b> weil die Frames aus dem {@link ResourceManager}
 * kommen, folgt der Weltkompass endlich dem Resource Pack des Spielers. Vorher war er auf Vanilla
 * eingefroren — wer ein Pack benutzte, sah einen umgestalteten normalen Kompass und daneben
 * unseren im alten Stil.
 *
 * <p><b>Warum kein {@code minecraft:paletted_permutations}:</b> Vanillas eingebaute Umfaerbe-Quelle
 * kaeme ohne Code aus, bildet aber Farbe auf Farbe ab. Zwei Grautoene des Kompasses haben doppelte
 * Rollen (innen Zifferblatt, aussen Gehaeuse), eine globale Tabelle wuerde das Gehaeuse mitfaerben.
 * Die noetige raeumliche Maske kann nur Code liefern — siehe {@link WorldCompassRecolour}.
 *
 * <p><b>Rueckfall in drei Stufen</b>, weil ein Resource Pack alles Moegliche mitbringen darf:
 * fehlt ein Frame, wird er uebersprungen und einmal gewarnt; findet die Flutfuellung kein
 * Zifferblatt, wird der Frame <em>unveraendert</em> ausgegeben (ein normal aussehender Kompass
 * statt eines fehlenden Sprites); schlaegt das Laden selbst fehl, liefert
 * {@code loadSprite} wie ueberall in Vanilla {@code null} und das Sprite entfaellt.
 */
public class WorldCompassSpriteSource implements SpriteSource {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Der registrierte Typ. Wird einmalig aus {@code RegisterSpriteSourceTypesEvent} gesetzt —
     * das Event feuert nur beim Bau der {@code Minecraft}-Instanz, deshalb muss das Ergebnis hier
     * liegen bleiben, damit {@link #type()} es spaeter noch zurueckgeben kann.
     */
    @Nullable
    private static SpriteSourceType registeredType;

    /** Es reicht, pro Sitzung einmal zu warnen; das Umfaerben laeuft fuer 32 Frames parallel. */
    private static final AtomicBoolean RECOLOUR_WARNED = new AtomicBoolean();

    public static final MapCodec<WorldCompassSpriteSource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("source").forGetter(s -> s.source),
                            ResourceLocation.CODEC.fieldOf("sprite").forGetter(s -> s.sprite),
                            Codec.intRange(1, 256).fieldOf("frames").forGetter(s -> s.frames))
                    .apply(instance, WorldCompassSpriteSource::new));

    /** Basis-ID der Quell-Frames; je Frame wird {@code _00} bis {@code _NN} angehaengt. */
    private final ResourceLocation source;
    /** Basis-ID der erzeugten Sprites, mit demselben Suffix-Schema. */
    private final ResourceLocation sprite;
    /** Wie viele Frames es gibt — beim Vanilla-Kompass 32. */
    private final int frames;

    public WorldCompassSpriteSource(ResourceLocation source, ResourceLocation sprite, int frames) {
        this.source = source;
        this.sprite = sprite;
        this.frames = frames;
    }

    /** Merkt sich den Typ, den NeoForge beim Registrieren zurueckgibt. */
    public static void setRegisteredType(SpriteSourceType type) {
        registeredType = type;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        int missing = 0;
        for (int index = 0; index < frames; index++) {
            String suffix = String.format(Locale.ROOT, "_%02d", index);
            ResourceLocation file = TEXTURE_ID_CONVERTER.idToFile(source.withSuffix(suffix));
            Optional<Resource> resource = resourceManager.getResource(file);
            if (resource.isEmpty()) {
                missing++;
                continue;
            }
            ResourceLocation spriteId = sprite.withSuffix(suffix);
            Resource frame = resource.get();
            // loadSprite kuemmert sich um Metadaten, Bildgroesse und Fehlerbehandlung; wir haengen
            // uns nur in den Konstruktor ein und faerben das fertig gelesene Bild um.
            output.add(spriteId, loader -> loader.loadSprite(spriteId, frame, WorldCompassSpriteSource::recolour));
        }
        if (missing > 0) {
            LOGGER.warn("World Compass: {} of {} compass frames are missing below {} - those sprites stay empty.",
                    missing, frames, TEXTURE_ID_CONVERTER.idToFile(source));
        }
    }

    /**
     * Faerbt den frisch gelesenen Frame um und baut daraus das Sprite.
     *
     * <p>Schlaegt das Umfaerben fehl, wird der Frame trotzdem ausgeliefert — dann sieht der
     * Weltkompass eben aus wie ein normaler Kompass. Das ist deutlich besser als ein fehlendes
     * Sprite, und der Spieler erkennt das Item weiterhin an Name und Tooltip.
     */
    @Nullable
    private static SpriteContents recolour(ResourceLocation id, FrameSize frameSize, NativeImage image,
                                           ResourceMetadata metadata) {
        if (!WorldCompassRecolour.apply(image) && RECOLOUR_WARNED.compareAndSet(false, true)) {
            LOGGER.warn("World Compass: no dial found in {} - a resource pack has redrawn the compass. "
                    + "Leaving the frames as they are.", id);
        }
        return new SpriteContents(id, frameSize, image, metadata);
    }

    @Override
    public SpriteSourceType type() {
        if (registeredType == null) {
            throw new IllegalStateException("World Compass sprite source type was never registered");
        }
        return registeredType;
    }
}
