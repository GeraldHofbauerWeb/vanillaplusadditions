#!/usr/bin/env python3
"""Erzeugt die Fire-Arrow-Textur aus Vanillas ``arrow.png``.

Drei Schritte, alle in Farben aus ``campfire_fire.png``:

1. **Die Spitze** wird umgefaerbt. Sie besteht aus vier Grautoenen, die **nur dort** vorkommen —
   die Federn tragen eigene Werte (``#E0E0E0``, ``#C6C6C6``, ``#3F3F3F``), der Schaft braune.
   Deshalb genuegt eine Farbtabelle, es braucht keine Ortsmaske.
2. **Der Schaftansatz** direkt hinter der Spitze glueht: drei Holzpixel werden zu Orange/Glut.
3. **Sieben Flammenpixel** kommen dazu — eine Zunge ueber der Schneide und eine Fahne, die am
   Schaft nach hinten zieht. Sie liegen in Zellen, die bei Vanilla leer sind; das Skript prueft das.

Die Federn und der hintere Schaft bleiben Pixel fuer Pixel unveraendert.

Das Skript prueft die Quelle, bevor es etwas schreibt: stimmen Groesse oder Palette nicht, bricht
es ab, statt eine stillschweigend falsche Textur abzulegen.

    python3 scripts/gen_fire_arrow_texture.py [--jar PFAD] [--check]

``--check`` schreibt nichts und meldet nur, ob die abgelegte Datei aktuell ist (fuer CI).
Die erzeugte PNG ist eingecheckt, der Build braucht also nie ein Client-Jar.
"""
import argparse, hashlib, io, os, sys, zipfile
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src/main/resources/assets/vanillaplusadditions/textures/item/fire_arrow.png")
DEFAULT_JAR = os.environ.get("VPA_CLIENT_JAR",
                             os.path.expanduser("~/.config/instant-launcher/shared/versions/1.21.1/1.21.1.jar"))
SRC = "assets/minecraft/textures/item/arrow.png"

# Spitze -> Lagerfeuer. Links die vier Grautoene der Feuersteinspitze (Luminanz 255/216/150/68),
# rechts Toene aus campfire_fire.png in derselben Reihenfolge (232/202/124/90).
TIP = {
    (0xFF, 0xFF, 0xFF): (0xF9, 0xEB, 0xAB),   # weissglueh  -> blasses Gelb
    (0xD8, 0xD8, 0xD8): (0xEF, 0xCD, 0x56),   # hell        -> Feuergelb
    (0x96, 0x96, 0x96): (0xC9, 0x6C, 0x03),   # mittel      -> Orange
    (0x44, 0x44, 0x44): (0xB1, 0x3F, 0x00),   # dunkel      -> Glutrot
}
# Was unveraendert bleiben MUSS - taucht die Spitzenfarbe hier auf, ist die Quelle nicht die,
# die wir kennen, und die Farbtabelle waere nicht mehr eindeutig.
KEEP = {(0xE0, 0xE0, 0xE0), (0xC6, 0xC6, 0xC6), (0x3F, 0x3F, 0x3F),   # Federn
        (0x89, 0x67, 0x27), (0x28, 0x1E, 0x0B)}                        # Schaft

# Schaftansatz hinter der Spitze: dieselben zwei Holztoene, aber nur in diesen Zellen.
GLOW_CELLS = [(11, 4), (10, 5), (11, 5)]
GLOW = {(0x89, 0x67, 0x27): (0xC9, 0x6C, 0x03), (0x28, 0x1E, 0x0B): (0xB1, 0x3F, 0x00)}

# Flammen in Zellen, die bei Vanilla leer sind: Zunge ueber der Schneide, Fahne nach achtern.
FLAMES = [
    (13, 1, (0xEF, 0xCD, 0x56)), (12, 1, (0xC9, 0x6C, 0x03)), (13, 0, (0xB1, 0x3F, 0x00)),
    (9, 3, (0xC9, 0x6C, 0x03)), (9, 4, (0xEF, 0xCD, 0x56)),
    (8, 4, (0xC9, 0x6C, 0x03)), (8, 5, (0xB1, 0x3F, 0x00)),
]


def build(jar_path):
    with zipfile.ZipFile(jar_path) as jar:
        src = Image.open(io.BytesIO(jar.read(SRC))).convert("RGBA")
    if src.size != (16, 16):
        sys.exit(f"!! {SRC} ist {src.size}, erwartet 16x16 - Vanilla hat die Textur geaendert")
    sp = src.load()
    seen = {sp[x, y][:3] for y in range(16) for x in range(16) if sp[x, y][3]}
    missing = set(TIP) - seen
    if missing:
        sys.exit("!! Spitzenfarben fehlen in der Quelle: "
                 + ", ".join(f"#{r:02X}{g:02X}{b:02X}" for r, g, b in sorted(missing)))
    if seen - set(TIP) - KEEP:
        sys.exit("!! unbekannte Farben in arrow.png: "
                 + ", ".join(f"#{r:02X}{g:02X}{b:02X}" for r, g, b in sorted(seen - set(TIP) - KEEP)))

    for x, y, _ in FLAMES:
        if sp[x, y][3]:
            sys.exit(f"!! Flammenpixel ({x},{y}) ist in arrow.png nicht leer - Vanilla hat die "
                     f"Textur geaendert, der Entwurf muesste neu gezeichnet werden")

    out = src.copy()
    px = out.load()
    changed = 0
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in TIP:
                px[x, y] = TIP[(r, g, b)] + (a,)
                changed += 1
    for x, y in GLOW_CELLS:
        r, g, b, a = px[x, y]
        if a and (r, g, b) in GLOW:
            px[x, y] = GLOW[(r, g, b)] + (a,)
            changed += 1
    for x, y, col in FLAMES:
        px[x, y] = col + (255,)
        changed += 1
    buf = io.BytesIO()
    out.save(buf, format="PNG", optimize=True)
    return buf.getvalue(), changed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=DEFAULT_JAR)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if not os.path.exists(args.jar):
        sys.exit(f"!! Client-Jar nicht gefunden: {args.jar}  (--jar oder VPA_CLIENT_JAR setzen)")
    data, changed = build(args.jar)
    have = open(OUT, "rb").read() if os.path.exists(OUT) else None
    if args.check:
        ok = have == data
        print(("aktuell" if ok else "VERALTET") + f": {os.path.relpath(OUT, REPO)}")
        sys.exit(0 if ok else 1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(data)
    print(f"geschrieben: {os.path.relpath(OUT, REPO)}  ({changed} Pixel umgefaerbt, "
          f"sha256 {hashlib.sha256(data).hexdigest()[:12]})")


main()
