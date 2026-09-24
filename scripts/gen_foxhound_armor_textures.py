#!/usr/bin/env python3
"""Färbt Quarks Foxhound-Rüstungstextur in unsere vier Werkstoff-Stufen um.

Warum umfärben und nicht unsere Wolfstextur nehmen: Quarks Foxhound bringt ein eigenes
Rüstungsmodell mit (``ModelHandler.foxhound_armor``, 64×64), dessen UV-Belegung nichts mit
der Vanilla-Wolfsrüstung (64×32) zu tun hat. Unsere Textur direkt daraufzulegen ergibt
Streifensalat. Was sich übertragen lässt, ist die **Farbe**, nicht die Fläche.

Das geht hier auf, weil beide Seiten exakt **elf** undurchsichtige Farbtöne benutzen:
Quarks Original elf rötliche (acht Panzer, drei Lederriemen), jede unserer vier Stufen elf
Töne einer Metallrampe. Zugeordnet wird nach **Helligkeit**: der dunkelste Ton des Originals
wird zum dunkelsten der Stufe, der zweitdunkelste zum zweitdunkelsten und so fort. Damit
bleibt die Schattierung Pixel für Pixel erhalten und nur der Farbton wandert.

Der Lederriemen wird dabei bewusst mitgefärbt. Unsere Wolfsrüstungen sind durchgehend aus
einem Metall — ein brauner Riemen an der Foxhound-Rüstung, den es am Wolf nicht gibt, wäre
der Ausreißer, nicht die Treue zum Original.

Aufruf (Quark-Jar wird sonst in ``libs/`` gesucht)::

    python3 scripts/gen_foxhound_armor_textures.py [pfad/zur/Quark.jar]

Die Ergebnisse werden mitcommittet, damit weder Build noch CI das Quark-Jar brauchen.
Braucht Pillow.
"""

import io
import sys
import zipfile
from collections import Counter
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - reine Bedienerhilfe
    sys.exit("Pillow fehlt: pip install --user Pillow")

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "src/main/resources/assets/vanillaplusadditions"
WOLF_TEXTURES = ASSETS / "textures/entity/wolf"
OUT_DIR = ASSETS / "textures/entity/foxhound"

QUARK_ENTRY = "assets/quark/textures/model/entity/foxhound/foxhound_armor.png"
TIERS = ("iron", "gold", "diamond", "netherite")

#: Beide Paletten müssen genau so viele Töne haben, sonst ist die Zuordnung geraten.
PALETTE_SIZE = 11


def luminance(colour):
    """Wahrgenommene Helligkeit, nach der beide Paletten sortiert werden."""
    red, green, blue = colour[:3]
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def palette(image, label):
    """Die undurchsichtigen Farbtöne eines Bildes, von dunkel nach hell."""
    counts = Counter(pixel for pixel in image.get_flattened_data() if pixel[3] > 0)
    if len(counts) != PALETTE_SIZE:
        sys.exit(f"{label}: {len(counts)} Farbtöne statt {PALETTE_SIZE} - die Zuordnung nach "
                 f"Helligkeit wäre nicht mehr eindeutig. Bitte das Skript anpassen, nicht raten.")
    return sorted(counts, key=luminance)


def find_quark_jar(argv):
    if len(argv) > 1:
        jar = Path(argv[1])
        if not jar.is_file():
            sys.exit(f"Kein Jar unter {jar}")
        return jar
    candidates = sorted((REPO / "libs").glob("Quark-*.jar"))
    if not candidates:
        sys.exit("Kein Quark-Jar in libs/ gefunden - Pfad als Argument angeben.")
    return candidates[-1]


def main(argv):
    jar_path = find_quark_jar(argv)
    with zipfile.ZipFile(jar_path) as jar:
        try:
            raw = jar.read(QUARK_ENTRY)
        except KeyError:
            sys.exit(f"{jar_path.name} enthält {QUARK_ENTRY} nicht.")
    source = Image.open(io.BytesIO(raw)).convert("RGBA")
    source_palette = palette(source, f"{jar_path.name}:{QUARK_ENTRY}")
    print(f"Vorlage: {jar_path.name} -> {source.size[0]}x{source.size[1]}, "
          f"{PALETTE_SIZE} Farbtöne")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    pixels = list(source.get_flattened_data())

    for tier in TIERS:
        wolf_path = WOLF_TEXTURES / f"wolf_armor_{tier}.png"
        if not wolf_path.is_file():
            sys.exit(f"Fehlt: {wolf_path.relative_to(REPO)}")
        tier_palette = palette(Image.open(wolf_path).convert("RGBA"), wolf_path.name)
        mapping = dict(zip(source_palette, tier_palette))

        out = Image.new("RGBA", source.size)
        out.putdata([mapping[p] if p[3] > 0 else p for p in pixels])
        target = OUT_DIR / f"foxhound_armor_{tier}.png"
        out.save(target)
        print(f"  {target.relative_to(REPO)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
