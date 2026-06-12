#!/usr/bin/env python3
"""
Cat armor v2 — v1 gradient colors + large overlapping scale-mail mask.

Scale geometry (6×6 pitch, alternating +3 offset every 2 rows):
  Within each 6×6 tile the opaque "scale" occupies an oval ~14px out of 36,
  leaving wide transparent gaps so the cat fur is clearly visible.

  Tile mask (sx 0-5, sy 0-5):
    sy=0: transparent (gap top)
    sy=1: opaque at sx 1,2,3,4  (4px wide, narrowed)
    sy=2: opaque at sx 0,1,2,3,4,5  (full width)
    sy=3: opaque at sx 0,1,2,3,4,5  (full width)
    sy=4: opaque at sx 1,2,3,4  (narrowed)
    sy=5: transparent (gap bottom)

  Coverage ≈ (0+4+6+6+4+0) / 36 = 20/36 = 56 %
  With the 3-pixel alternating offset, adjacent rows interlock like fish scales.

Colors reproduce v1's per-face gradient: each scale goes bright (top) → dark (bottom).
"""

from PIL import Image
import os

SRC    = os.path.normpath(os.path.join(os.path.dirname(__file__), "../.."))  # noqa
UVMAP  = os.path.join(SRC, "cat_armor_uvmap_64x32.png")
OUT    = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat")

# v1-inspired palettes (bright top, dark bottom)
TIERS = {
    "iron": {
        "hi":  (210, 212, 220),   # bright silver
        "mid": (140, 142, 152),
        "lo":  ( 58,  60,  70),   # dark steel
        "accent": None,
    },
    "gold": {
        "hi":  (255, 220,  40),   # bright gold
        "mid": (210, 140,   0),
        "lo":  ( 90,  52,   0),   # dark amber
        "accent": None,
    },
    "diamond": {
        "hi":  ( 80, 248, 255),   # bright cyan
        "mid": (  0, 172, 195),
        "lo":  (  0,  80, 110),   # dark teal
        "accent": None,
    },
    "netherite": {
        "hi":  ( 95,  90, 105),   # light charcoal
        "mid": ( 52,  48,  60),
        "lo":  ( 18,  14,  24),   # near black
        "accent": (190, 65, 5),   # lava orange
    },
}

# Scale mask: which (sx, sy) offsets within a 6×6 tile are opaque
_SCALE_OPAQUE = set()
for _sy, _xs in [
    (1, (1, 2, 3, 4)),
    (2, (0, 1, 2, 3, 4, 5)),
    (3, (0, 1, 2, 3, 4, 5)),
    (4, (1, 2, 3, 4)),
]:
    for _sx in _xs:
        _SCALE_OPAQUE.add((_sx, _sy))

PITCH = 6
STAGGER = 3  # alternating x offset every two scale rows


def is_scale(x: int, y: int) -> bool:
    scale_row = y // PITCH
    x_off = STAGGER if (scale_row % 2) else 0
    sx = (x + x_off) % PITCH
    sy = y % PITCH
    return (sx, sy) in _SCALE_OPAQUE


def lerp(a, b, t):
    return int(a + (b - a) * t)


def scale_color(x: int, y: int, palette: dict) -> tuple:
    """Gradient based on y-position within the 4 opaque rows (sy 1-4)."""
    scale_row = y // PITCH
    sy = y % PITCH
    # Map sy 1-4 → t 0.0 (bright) … 1.0 (dark)
    t = (sy - 1) / 3.0  # sy=1 → 0, sy=4 → 1

    hi, mid, lo = palette["hi"], palette["mid"], palette["lo"]
    if t < 0.5:
        t2 = t * 2
        r = lerp(hi[0], mid[0], t2)
        g = lerp(hi[1], mid[1], t2)
        b = lerp(hi[2], mid[2], t2)
    else:
        t2 = (t - 0.5) * 2
        r = lerp(mid[0], lo[0], t2)
        g = lerp(mid[1], lo[1], t2)
        b = lerp(mid[2], lo[2], t2)

    # Netherite: orange lava patches on ~1/3 of scales (deterministic per scale tile)
    accent = palette.get("accent")
    if accent:
        x_off = STAGGER if (scale_row % 2) else 0
        tile_col = (x + x_off) // PITCH
        blob = (tile_col * 13 + scale_row * 29) % 9
        if blob < 3:  # ~33% of scale tiles have orange
            ot = 0.5 + 0.4 * t   # stronger toward bottom of scale
            r = int(r * (1 - ot) + accent[0] * ot)
            g = int(g * (1 - ot) + accent[1] * ot)
            b = int(b * (1 - ot) + accent[2] * ot)

    return (r, g, b, 255)


def generate(tier_name: str, palette: dict, uv_img: Image.Image) -> Image.Image:
    out     = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    uv_data = uv_img.load()
    dst     = out.load()

    for y in range(32):
        for x in range(64):
            _, _, _, a = uv_data[x, y]
            if a != 220:          # not an armor UV pixel
                continue
            if not is_scale(x, y):
                continue
            dst[x, y] = scale_color(x, y, palette)

    return out


def main():
    uv_img = Image.open(UVMAP).convert("RGBA")

    for tier, palette in TIERS.items():
        img = generate(tier, palette, uv_img)
        img.save(os.path.join(OUT, f"cat_armor_{tier}_v2.png"))
        print(f"Saved {tier}_v2")

        preview = img.resize((64 * 12, 32 * 12), Image.NEAREST)
        preview.save(os.path.join(SRC, f"cat_armor_v2_{tier}_preview.png"))


if __name__ == "__main__":
    main()
