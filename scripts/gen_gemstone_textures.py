#!/usr/bin/env python3
"""Generates the two Special Gemstones item icons.

The art is drawn from scratch here — no vanilla texture is read, recoloured or copied. That is
deliberate: `docs/internal/BACKLOG.md` ("Abgeleitete Vanilla-Texturen aufloesen") tracks the
textures in this mod that are still derived from Mojang's, and a recoloured `emerald.png` would
have gone straight onto that list. Everything below is geometry plus two colour ramps, so the
result shares no pixels with any Mojang asset.

The two gems use ONE silhouette and ONE facet layout, exactly the way the four `wolf_armor_*.png`
icons share a shape and differ only in their metal ramp. They are told apart twice over: by hue and
by an arrow cut into the pavilion — pointing up on the Growth Gemstone, down on the Shrinking one. Colour alone would be useless to a colour-blind player looking at two gems in a
hotbar. The hues follow the recipe rather than being picked freely: crimson for the gem cut around
nether wart, warped teal for the one cut around warped wart, which is the same red-means-bigger,
blue-means-smaller logic the recipe itself carries.

Usage:
    python3 scripts/gen_gemstone_textures.py             # write the committed PNGs
    python3 scripts/gen_gemstone_textures.py --check      # fail if they are out of date (CI)
    python3 scripts/gen_gemstone_textures.py --preview    # also write an 16x zoom to /tmp
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the error message IS the handling
    sys.exit("Pillow is required: pip install --user Pillow")

SIZE = 16
REPO_ROOT = Path(__file__).resolve().parent.parent
TEXTURE_DIR = REPO_ROOT / "src/main/resources/assets/vanillaplusadditions/textures/item"

# Half-widths of the gem per row, as (first_col, last_col) inclusive, symmetric about x = 7.5.
# Wide shoulders near the top, tapering to a point: a kite cut seen face-on.
ROW_SPANS = {
    2: (6, 9),
    3: (4, 11),
    4: (3, 12),
    5: (2, 13),
    6: (2, 13),
    7: (2, 13),
    8: (3, 12),
    9: (3, 12),
    10: (4, 11),
    11: (5, 10),
    12: (6, 9),
    13: (7, 8),
}

# The glyph that names the direction, as a set of pixels engraved into the pavilion: an arrow up on
# the Growth Gemstone, an arrow down on the Shrinking one. Both are built from a two-pixel stem and
# a head widening 2 -> 4 -> 6, centred on the gem's own axis between columns 7 and 8, and one is the
# other mirrored top to bottom — so the pair is symmetric the way the two effects are.
_ARROW_UP = {
    4: (7, 8),
    5: (6, 7, 8, 9),
    6: (5, 6, 7, 8, 9, 10),
    7: (4, 5, 6, 7, 8, 9, 10, 11),
    8: (7, 8),
    9: (7, 8),
    10: (7, 8),
    11: (7, 8),
}
GLYPH_ARROW_UP = {(x, y) for y, columns in _ARROW_UP.items() for x in columns}
GLYPH_ARROW_DOWN = {(x, 15 - y) for x, y in GLYPH_ARROW_UP}

PALETTES = {
    # Crimson, after the nether wart block in its recipe: in Sebi's colour logic red means bigger.
    "growth_gemstone": {
        "outline": (43, 5, 7),
        "deep": (74, 10, 16),
        "shade": (107, 16, 24),
        "mid": (143, 26, 35),
        "light": (166, 38, 44),
        "crown": (190, 52, 52),
        "highlight": (228, 116, 100),
        "accent": (255, 227, 214),
    },
    # Warped, after the warped wart block: blue means smaller.
    "shrinking_gemstone": {
        "outline": (4, 42, 43),
        "deep": (7, 63, 66),
        "shade": (11, 88, 92),
        "mid": (17, 116, 120),
        "light": (24, 144, 148),
        "crown": (34, 172, 176),
        "highlight": (127, 224, 226),
        "accent": (223, 251, 251),
    },
}


# The gem's own ramp, dark to light. A cut is measured AGAINST it rather than in absolute colours:
# the arrow crosses the bright table and the dark tip, and a groove has to look equally deep on both.
RAMP = ("outline", "deep", "shade", "mid", "light", "crown", "highlight")


def shift(palette: dict, key: str, delta: int) -> tuple[int, int, int]:
    """The colour `delta` steps along the ramp from `key`, clamped at both ends."""
    if key not in RAMP:
        return palette[key]
    return palette[RAMP[max(0, min(len(RAMP) - 1, RAMP.index(key) + delta))]]


def shape() -> set[tuple[int, int]]:
    """Every pixel the gem covers."""
    return {(x, y) for y, (first, last) in ROW_SPANS.items() for x in range(first, last + 1)}


def is_outline(pixel: tuple[int, int], filled: set[tuple[int, int]]) -> bool:
    """A pixel on the silhouette's edge — any of its four neighbours is outside the gem."""
    x, y = pixel
    return any(neighbour not in filled
               for neighbour in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))


def facet(pixel: tuple[int, int]) -> str:
    """Which facet an interior pixel belongs to. The light falls in from the upper left."""
    x, y = pixel
    if y <= 4:
        # Table — the flat top of the cut, the brightest face.
        return "crown"
    if y <= 6:
        return "light" if x < 8 else "mid"
    if y >= 11:
        # The tip sits in its own shadow.
        return "deep"
    return "mid" if x < 8 else "shade"


def render(name: str, glyph: set[tuple[int, int]]) -> Image.Image:
    palette = PALETTES[name]
    filled = shape()
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()

    for pixel in sorted(filled):
        key = "outline" if is_outline(pixel, filled) else facet(pixel)
        pixels[pixel] = palette[key] + (255,)

    # Two highlight pixels on the table, where the light hits.
    for pixel in ((5, 3), (6, 3), (4, 4)):
        if pixel in filled and not is_outline(pixel, filled):
            pixels[pixel] = palette["highlight"] + (255,)

    # The direction glyph, cut into the stone and then filled.
    #
    # An engraving is a relief with the light reversed, and that is the whole trick: the light falls
    # in from the upper left, so a GROOVE lies in shadow along its top and left edge and catches the
    # light along its bottom and right. Doing it the other way round — which is what a bright shape
    # with a shadow under it does — reads as a symbol painted on, never as one cut in.
    #
    # The shadowed rim is taken four steps down the ramp from the facet it sits on, not from a fixed
    # colour, so the cut is as deep on the bright table as it is on the dark tip. The filled core
    # stays bright: a true groove loses contrast as it deepens, and at 16 pixels there is none to
    # spare.
    for pixel in sorted(glyph):
        if pixel not in filled or is_outline(pixel, filled):
            continue
        x, y = pixel
        if (x, y - 1) not in glyph or (x - 1, y) not in glyph:
            pixels[pixel] = shift(palette, facet(pixel), -4) + (255,)
        elif (x, y + 1) not in glyph or (x + 1, y) not in glyph:
            pixels[pixel] = palette["highlight"] + (255,)
        else:
            pixels[pixel] = palette["accent"] + (255,)

    return image


def build() -> dict[str, Image.Image]:
    return {
        "growth_gemstone": render("growth_gemstone", GLYPH_ARROW_UP),
        "shrinking_gemstone": render("shrinking_gemstone", GLYPH_ARROW_DOWN),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero when the committed PNGs differ from what this generates")
    parser.add_argument("--preview", action="store_true",
                        help="also write a 16x nearest-neighbour zoom next to the PNGs in /tmp")
    parser.add_argument("--out-dir", type=Path, default=TEXTURE_DIR,
                        help="where the PNGs live (default: the mod's item texture folder)")
    args = parser.parse_args()

    images = build()
    failures = []

    for name, image in images.items():
        target = args.out_dir / f"{name}.png"
        if args.check:
            if not target.exists():
                failures.append(f"{target} is missing")
                continue
            existing = Image.open(target).convert("RGBA")
            if existing.size != image.size or existing.tobytes() != image.tobytes():
                failures.append(f"{target} is out of date")
            continue

        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target)
        print(f"wrote {target.relative_to(REPO_ROOT)}")

        if args.preview:
            zoom = image.resize((SIZE * 16, SIZE * 16), Image.NEAREST)
            preview = Path("/tmp") / f"{name}_zoom.png"
            zoom.save(preview)
            print(f"wrote {preview}")

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        print("Run: python3 scripts/gen_gemstone_textures.py", file=sys.stderr)
        return 1

    if args.check:
        print("gemstone textures are up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
