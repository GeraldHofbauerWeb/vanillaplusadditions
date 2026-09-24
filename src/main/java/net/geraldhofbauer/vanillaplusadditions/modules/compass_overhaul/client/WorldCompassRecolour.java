package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;

/**
 * Faerbt einen Vanilla-Kompass-Frame in das Zifferblatt des Weltkompasses um.
 *
 * <p>Der Weltkompass uebernimmt die Geometrie des Vanilla-Kompasses Pixel fuer Pixel — jeder
 * Nachbau scheitert am beleuchteten Metallrahmen, der das ganze Icon traegt. Umgefaerbt wird
 * ausschliesslich das Zifferblatt: Nordnadel in Amethyst, Flaeche in Enderperlen-Tuerkis,
 * Suedzeiger in Ender-Auge-Gruen, dazu die dunklen Eckpixel rundherum in einem tieferen Gruen.
 * Das Gehaeuse bleibt unangetastet.
 *
 * <p><b>Warum eine Flutfuellung und keine Farbtabelle:</b> zwei Grautoene haben doppelte Rollen.
 * {@code #4f4d4d} ist innen der Suedzeiger und aussen eine dunkle Stelle am Gehaeuse,
 * {@code #353535} ist der Innenrand und der obere Gehaeusebogen. Eine Farbe-auf-Farbe-Abbildung
 * — auch Vanillas {@code paletted_permutations} — wuerde das Gehaeuse mitfaerben. Die Fuellung
 * startet im Mittelpunkt und wird vom Innenrand eingeschlossen, kommt also gar nicht erst nach
 * aussen; die Masken entstehen dabei aus dem Bild statt aus Koordinaten.
 *
 * <p>Deshalb ist der Code auch unabhaengig von der Aufloesung: ein Resource Pack mit 32x32- oder
 * 64x64-Kompass wird genauso umgefaerbt, solange es dieselben fuenf Quellfarben benutzt. Tut es
 * das nicht, findet die Fuellung nichts und {@link #apply(NativeImage)} meldet das dem Aufrufer,
 * statt ein halb umgefaerbtes Bild zurueckzugeben.
 *
 * <p>Portiert aus {@code scripts/gen_world_compass_textures.py}, das bis beta.91 32 fertige PNGs
 * erzeugt hat. Die liegen jetzt nicht mehr im Jar — siehe {@link WorldCompassSpriteSource}.
 */
public final class WorldCompassRecolour {

    /** Zifferblattflaeche. */
    private static final int FACE = 0x2F2F2F;
    /** Suedzeiger (innen) bzw. dunkle Gehaeusestelle (aussen). */
    private static final int SOUTH = 0x4F4D4D;
    /** Drehpunkt in der Mitte. */
    private static final int PIVOT = 0x646464;
    /** Innenrand (innen) bzw. oberer Gehaeusebogen (aussen). */
    private static final int INNER_EDGE = 0x353535;
    /** Die drei Helligkeitsstufen der roten Nordnadel. */
    private static final int[] NEEDLE = {0xFF1414, 0xCB1A1A, 0xBE1515};

    /** Amethyst, aus {@code amethyst_shard.png}. */
    private static final int NEW_NEEDLE = 0xA855F7;
    /** Helligkeitsstufen der neuen Nadel, wie im Original. */
    private static final float[] NEEDLE_STEPS = {1.00F, 0.80F, 0.74F};
    /** Enderperlen-Tuerkis, aus {@code ender_pearl.png}. */
    private static final int NEW_FACE = 0x105E51;
    /** Ender-Auge-Gruen, aus {@code ender_eye.png}. */
    private static final int NEW_SOUTH = 0x71AC49;
    /** Dunkleres Enderperlen-Tuerkis fuer den Innenrand. */
    private static final int NEW_INNER_EDGE = 0x0B4D42;
    /** Der Drehpunkt ist die Flaeche, so weit Richtung Weiss aufgehellt. */
    private static final float PIVOT_LIFT = 0.30F;

    private WorldCompassRecolour() {
    }

    /**
     * Faerbt das Bild an Ort und Stelle um.
     *
     * @param image der Vanilla-Frame; wird veraendert, wenn ein Zifferblatt gefunden wurde
     * @return {@code true}, wenn umgefaerbt wurde; {@code false}, wenn das Bild kein erkennbares
     *         Zifferblatt hat und deshalb unangetastet blieb
     */
    public static boolean apply(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }

        boolean[] dial = dialMask(image, width, height);
        if (dial == null) {
            return false;
        }
        boolean[] edge = edgeMask(image, dial, width, height);
        recolour(image, dial, edge, width, height);
        return true;
    }

    /**
     * Das Zifferblatt: Flutfuellung vom Mittelpunkt ueber alles, was nicht Gehaeuse ist.
     *
     * <p>Markiert wird beim Einreihen, nicht beim Entnehmen — so landet jedes Pixel hoechstens
     * einmal auf dem Stapel und der ist mit {@code width * height} sicher gross genug.
     *
     * @return die Maske, oder {@code null}, wenn schon der Startpunkt kein Zifferblattpixel ist
     */
    private static boolean[] dialMask(NativeImage image, int width, int height) {
        // Derselbe Startpunkt wie im Python-Skript, nur relativ statt auf 16x16 festgenagelt.
        int seedX = width / 2;
        int seedY = height * 7 / 16;
        if (!isInterior(image, seedX, seedY)) {
            return null;
        }

        boolean[] mask = new boolean[width * height];
        int[] stack = new int[width * height];
        int top = 0;
        mask[seedY * width + seedX] = true;
        stack[top++] = seedY * width + seedX;

        while (top > 0) {
            int index = stack[--top];
            int x = index % width;
            int y = index / width;
            if (x + 1 < width) {
                top = visit(image, mask, stack, top, index + 1, x + 1, y);
            }
            if (x > 0) {
                top = visit(image, mask, stack, top, index - 1, x - 1, y);
            }
            if (y + 1 < height) {
                top = visit(image, mask, stack, top, index + width, x, y + 1);
            }
            if (y > 0) {
                top = visit(image, mask, stack, top, index - width, x, y - 1);
            }
        }
        return mask;
    }

    /** Reiht einen Nachbarn ein, wenn er zum Zifferblatt gehoert und noch nicht markiert ist. */
    private static int visit(NativeImage image, boolean[] mask, int[] stack, int top, int index, int x, int y) {
        if (mask[index] || !isInterior(image, x, y)) {
            return top;
        }
        mask[index] = true;
        stack[top] = index;
        return top + 1;
    }

    /** Gehoert das Pixel zu den Farben, die das Zifferblatt ausmachen? */
    private static boolean isInterior(NativeImage image, int x, int y) {
        int packed = image.getPixelRGBA(x, y);
        if (FastColor.ABGR32.alpha(packed) == 0) {
            return false;
        }
        int rgb = rgb(packed);
        if (rgb == FACE || rgb == SOUTH || rgb == PIVOT) {
            return true;
        }
        for (int needle : NEEDLE) {
            if (rgb == needle) {
                return true;
            }
        }
        return false;
    }

    /** Die dunklen Eckpixel, die das Zifferblatt unmittelbar einfassen. */
    private static boolean[] edgeMask(NativeImage image, boolean[] dial, int width, int height) {
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int packed = image.getPixelRGBA(x, y);
                if (dial[index] || FastColor.ABGR32.alpha(packed) == 0 || rgb(packed) != INNER_EDGE) {
                    continue;
                }
                mask[index] = touchesDial(dial, width, height, x, y);
            }
        }
        return mask;
    }

    /** Liegt eines der acht Nachbarpixel im Zifferblatt? */
    private static boolean touchesDial(boolean[] dial, int width, int height, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && dial[ny * width + nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Wendet die Palette an: ausserhalb der beiden Masken bleibt jedes Pixel, wie es ist. */
    private static void recolour(NativeImage image, boolean[] dial, boolean[] edge, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int packed = image.getPixelRGBA(x, y);
                if (FastColor.ABGR32.alpha(packed) == 0) {
                    continue;
                }
                if (dial[index]) {
                    int mapped = mapInterior(rgb(packed));
                    if (mapped >= 0) {
                        image.setPixelRGBA(x, y, opaque(mapped));
                    }
                } else if (edge[index]) {
                    image.setPixelRGBA(x, y, opaque(NEW_INNER_EDGE));
                }
            }
        }
    }

    /** Bildet eine Zifferblattfarbe auf die neue Palette ab, oder {@code -1} fuer "unveraendert". */
    private static int mapInterior(int rgb) {
        if (rgb == FACE) {
            return NEW_FACE;
        }
        if (rgb == SOUTH) {
            return NEW_SOUTH;
        }
        if (rgb == PIVOT) {
            return lighten(NEW_FACE, PIVOT_LIFT);
        }
        for (int i = 0; i < NEEDLE.length; i++) {
            if (rgb == NEEDLE[i]) {
                return scale(NEW_NEEDLE, NEEDLE_STEPS[i]);
            }
        }
        return -1;
    }

    /** Hellt eine Farbe anteilig Richtung Weiss auf. */
    private static int lighten(int rgb, float amount) {
        int r = Math.round(red(rgb) + (255 - red(rgb)) * amount);
        int g = Math.round(green(rgb) + (255 - green(rgb)) * amount);
        int b = Math.round(blue(rgb) + (255 - blue(rgb)) * amount);
        return (r << 16) | (g << 8) | b;
    }

    /** Skaliert eine Farbe in der Helligkeit, wie es das Original bei der Nadel tut. */
    private static int scale(int rgb, float factor) {
        int r = Math.min(255, Math.round(red(rgb) * factor));
        int g = Math.min(255, Math.round(green(rgb) * factor));
        int b = Math.min(255, Math.round(blue(rgb) * factor));
        return (r << 16) | (g << 8) | b;
    }

    /** NativeImage speichert ABGR; hier wird daraus ein handliches 0xRRGGBB. */
    private static int rgb(int packed) {
        return (FastColor.ABGR32.red(packed) << 16)
                | (FastColor.ABGR32.green(packed) << 8)
                | FastColor.ABGR32.blue(packed);
    }

    /** Verpackt 0xRRGGBB wieder als deckendes ABGR, wie NativeImage es erwartet. */
    private static int opaque(int rgb) {
        return FastColor.ABGR32.color(255, blue(rgb), green(rgb), red(rgb));
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }
}
