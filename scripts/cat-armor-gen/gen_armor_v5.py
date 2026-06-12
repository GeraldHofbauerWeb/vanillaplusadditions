#!/usr/bin/env python3
"""
Cat armor v5 — v1 design + semicircular cutouts straddling the top/side face seam.

  Circle set A at NORTH/TOP boundary (y=5.5, x≈29): half in TOP face, half in NORTH side face
  Circle set B at SOUTH/BOTTOM boundary (y=5.5, x≈43): half in BOTTOM face, half in SOUTH side face
  Each set has 2 circles (r=2) within the 8px-wide face column.
  Ear tips also transparent.
"""

import math
from PIL import Image
import os

SRC     = os.path.normpath(os.path.join(os.path.dirname(__file__), "../.."))
UVMAP   = os.path.join(SRC, "cat_armor_uvmap_64x32.png")
TEX_DIR = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat")

EAR_TIP_PIXELS = frozenset({(3,10),(3,11),(8,10),(9,10),(8,11),(9,11)})

# v5 circle cutout centers (cx, cy) and radius
# cy=5.5 = seam between the top-face rows (y=0-5) and the side-face rows (y=6-11)
# Set A: NORTH face (x=26-33) ↔ TOP face boundary
# Set B: SOUTH face (x=40-47) ↔ BOTTOM face boundary
V5_CIRCLES = [
    (28, 5.5, 2),   # A-left  (NORTH/TOP)
    (31, 5.5, 2),   # A-right (NORTH/TOP)
    (41, 5.5, 2),   # B-left  (SOUTH/BOTTOM — "back of body")
    (44, 5.5, 2),   # B-right (SOUTH/BOTTOM)
]


def is_armor_pixel(a: int) -> bool:
    return a == 220


def in_v5_circle(x: int, y: int) -> bool:
    for cx, cy, r in V5_CIRCLES:
        if math.sqrt((x - cx) ** 2 + (y - cy) ** 2) <= r:
            return True
    return False


def generate(tier: str, uv_img: Image.Image) -> Image.Image:
    v1  = Image.open(os.path.join(TEX_DIR, f"cat_armor_{tier}.png")).convert("RGBA")
    out = v1.copy()
    uv  = uv_img.load()
    dst = out.load()

    for y in range(32):
        for x in range(64):
            _, _, _, a = uv[x, y]
            if not is_armor_pixel(a):
                continue

            erase = (x, y) in EAR_TIP_PIXELS or in_v5_circle(x, y)
            if erase:
                dst[x, y] = (0, 0, 0, 0)

    return out


def main():
    uv_img = Image.open(UVMAP).convert("RGBA")

    for tier in ("iron", "gold", "diamond", "netherite"):
        img = generate(tier, uv_img)
        img.save(os.path.join(TEX_DIR, f"cat_armor_{tier}_v5.png"))
        print(f"Saved {tier}_v5")
        img.resize((64*12, 32*12), Image.NEAREST).save(
            os.path.join(SRC, f"cat_armor_v5_{tier}_preview.png"))


if __name__ == "__main__":
    main()
