#!/usr/bin/env python3
"""Erzeugt Texturen und Modelle für den Weltkompass aus den Vanilla-Kompass-Frames.

Der Weltkompass übernimmt die Geometrie des Vanilla-Kompasses Pixel für Pixel — jeder
Nachbau scheitert am beleuchteten Metallrahmen, der das ganze Icon trägt. Umgefärbt wird
ausschließlich das Zifferblatt: Nordnadel in Amethyst, Fläche in Enderperlen-Türkis,
Südzeiger in Ender-Auge-Grün, dazu die zehn dunklen Eckpixel rundherum in einem tieferen
Grün, damit die Fläche nicht flach wirkt. Das Gehäuse bleibt unangetastet.

Zwei Masken statt einer Farbtabelle, weil zwei Grautöne doppelte Rollen haben:
``#4f4d4d`` ist innen der Südzeiger und außen eine dunkle Stelle am Gehäuse, ``#353535``
ist der Innenrand und der obere Gehäusebogen. Beide Masken werden hier aus dem Bild
berechnet, nicht hartkodiert — und gegen alle 32 Frames geprüft.

Aufruf (Client-Jar wird sonst im Gradle-Cache gesucht)::

    python3 scripts/gen_world_compass_textures.py [pfad/zum/client.jar]

Braucht Pillow.
"""

import json
import sys
import zipfile
from collections import deque
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - reine Bedienerhilfe
    sys.exit("Pillow fehlt: pip install --user Pillow")

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "src/main/resources/assets/vanillaplusadditions"
TEXTURES = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"

NAMESPACE = "vanillaplusadditions"
ITEM = "world_compass"
BASE_FRAME = 16  # Frame 16 ist die Basistextur; die Nadel zeigt dort nach oben

# --- Rollen im Original ----------------------------------------------------------------
FACE = (0x2F, 0x2F, 0x2F)  # Zifferblattfläche
SOUTH = (0x4F, 0x4D, 0x4D)  # Südzeiger (innen) bzw. dunkle Gehäusestelle (außen)
PIVOT = (0x64, 0x64, 0x64)  # Drehpunkt
INNER_EDGE = (0x35, 0x35, 0x35)  # Innenrand (innen) bzw. oberer Gehäusebogen (außen)
NEEDLE = [(0xFF, 0x14, 0x14), (0xCB, 0x1A, 0x1A), (0xBE, 0x15, 0x15)]

# --- Zielfarben, alle aus echten Vanilla-Texturen entnommen ----------------------------
NEW_NEEDLE = (0xA8, 0x55, 0xF7)  # Amethyst
NEW_NEEDLE_STEPS = [1.00, 0.80, 0.74]  # Helligkeitsstufen wie im Original
NEW_FACE = (0x10, 0x5E, 0x51)  # ender_pearl.png
NEW_SOUTH = (0x71, 0xAC, 0x49)  # ender_eye.png
NEW_INNER_EDGE = (0x0B, 0x4D, 0x42)  # ender_pearl.png, dunkler
PIVOT_LIFT = 0.30  # Drehpunkt = Fläche, so weit Richtung Weiß aufgehellt


def lighten(color, amount):
    """Hellt eine Farbe anteilig Richtung Weiß auf."""
    return tuple(round(c + (255 - c) * amount) for c in color)


def scale(color, factor):
    """Skaliert eine Farbe in der Helligkeit, wie es das Original bei der Nadel tut."""
    return tuple(min(255, round(c * factor)) for c in color)


def load_frames(jar_path):
    """Liest die 32 Kompass-Frames und das Basismodell aus einem Client-Jar."""
    frames = []
    with zipfile.ZipFile(jar_path) as jar:
        for index in range(32):
            name = f"assets/minecraft/textures/item/compass_{index:02d}.png"
            with jar.open(name) as handle:
                frames.append(Image.open(handle).convert("RGBA").copy())
        with jar.open("assets/minecraft/models/item/compass.json") as handle:
            model = json.load(handle)
    return frames, model


def dial_mask(frame):
    """Das Zifferblatt: Flutfüllung vom Mittelpunkt über alles, was nicht Gehäuse ist."""
    interior = {FACE, SOUTH, PIVOT, *NEEDLE}
    pixels = frame.load()
    mask = [[False] * 16 for _ in range(16)]
    queue = deque([(8, 7)])
    while queue:
        x, y = queue.popleft()
        if not (0 <= x < 16 and 0 <= y < 16) or mask[y][x]:
            continue
        r, g, b, a = pixels[x, y]
        if not a or (r, g, b) not in interior:
            continue
        mask[y][x] = True
        queue.extend([(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)])
    return mask


def edge_mask(frame, dial):
    """Die dunklen Eckpixel, die das Zifferblatt unmittelbar einfassen."""
    pixels = frame.load()
    mask = [[False] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if dial[y][x] or not pixels[x, y][3] or pixels[x, y][:3] != INNER_EDGE:
                continue
            neighbours = ((x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if any(0 <= nx < 16 and 0 <= ny < 16 and dial[ny][nx] for nx, ny in neighbours):
                mask[y][x] = True
    return mask


def recolour(frame, dial, edge):
    """Wendet die Palette an: außerhalb der Masken bleibt jedes Pixel, wie es ist."""
    inside = {
        FACE: NEW_FACE,
        SOUTH: NEW_SOUTH,
        PIVOT: lighten(NEW_FACE, PIVOT_LIFT),
    }
    for original, factor in zip(NEEDLE, NEW_NEEDLE_STEPS):
        inside[original] = scale(NEW_NEEDLE, factor)

    out = frame.copy()
    source = frame.load()
    target = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = source[x, y]
            if not a:
                continue
            if dial[y][x]:
                target[x, y] = (*inside.get((r, g, b), (r, g, b)), 255)
            elif edge[y][x]:
                target[x, y] = (*NEW_INNER_EDGE, 255)
    return out


def rename_model(reference):
    """Biegt eine Modellreferenz aus dem Vanilla-Kompass auf unser Item um."""
    suffix = reference.rsplit("/", 1)[-1]
    if suffix == "compass":
        return f"{NAMESPACE}:item/{ITEM}"
    return f"{NAMESPACE}:item/{ITEM}_{suffix.rsplit('_', 1)[-1]}"


def write_models(vanilla_model):
    """Schreibt das Basismodell samt Override-Tabelle und die 31 Einzelframes."""
    MODELS.mkdir(parents=True, exist_ok=True)
    base = {
        "parent": vanilla_model["parent"],
        "textures": {"layer0": f"{NAMESPACE}:item/{ITEM}_{BASE_FRAME:02d}"},
        "overrides": [
            {"predicate": dict(entry["predicate"]), "model": rename_model(entry["model"])}
            for entry in vanilla_model["overrides"]
        ],
    }
    (MODELS / f"{ITEM}.json").write_text(json.dumps(base, indent=2) + "\n")

    written = 1
    for index in range(32):
        if index == BASE_FRAME:
            continue  # Frame 16 ist das Basismodell selbst, genau wie in Vanilla
        leaf = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NAMESPACE}:item/{ITEM}_{index:02d}"},
        }
        (MODELS / f"{ITEM}_{index:02d}.json").write_text(json.dumps(leaf, indent=2) + "\n")
        written += 1
    return written


def find_client_jar():
    """Sucht ein 1.21.1-Client-Jar im Gradle-Cache des Projekts."""
    candidates = sorted(Path.home().glob(".gradle/caches/minecraft/versions/1.21.1/*.jar"))
    candidates += sorted((REPO / ".gradle").glob("**/1.21.1/*.jar"))
    for candidate in candidates:
        with zipfile.ZipFile(candidate) as jar:
            if "assets/minecraft/textures/item/compass_00.png" in jar.namelist():
                return candidate
    return None


def main():
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else find_client_jar()
    if not jar_path or not jar_path.exists():
        sys.exit("Kein Client-Jar gefunden — Pfad als Argument angeben.")

    frames, vanilla_model = load_frames(jar_path)
    dial = dial_mask(frames[BASE_FRAME])
    edge = edge_mask(frames[BASE_FRAME], dial)

    # Gegenprobe: außerhalb des Zifferblatts darf sich über die 32 Frames nichts ändern,
    # sonst gälten die aus Frame 16 gewonnenen Masken nicht für alle Frames.
    base_pixels = frames[BASE_FRAME].load()
    for index, frame in enumerate(frames):
        pixels = frame.load()
        for y in range(16):
            for x in range(16):
                if not dial[y][x] and pixels[x, y] != base_pixels[x, y]:
                    sys.exit(f"Frame {index}: Pixel ({x},{y}) außerhalb des Zifferblatts weicht ab.")

    TEXTURES.mkdir(parents=True, exist_ok=True)
    for index, frame in enumerate(frames):
        recolour(frame, dial, edge).save(TEXTURES / f"{ITEM}_{index:02d}.png")

    models = write_models(vanilla_model)
    dial_pixels = sum(map(sum, dial))
    edge_pixels = sum(map(sum, edge))
    print(f"Quelle: {jar_path}")
    print(f"Zifferblatt {dial_pixels} Pixel, Innenrand {edge_pixels} Pixel")
    print(f"32 Texturen -> {TEXTURES.relative_to(REPO)}")
    print(f"{models} Modelle -> {MODELS.relative_to(REPO)}")


if __name__ == "__main__":
    main()
