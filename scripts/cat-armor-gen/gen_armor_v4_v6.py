#!/usr/bin/env python3
"""
Cat armor v4 and v6.

UV body face layout (OcelotModel / CatModel, 64×32 texture):
  EAST  face: x=20-25  (6px — right flank, head-to-tail on the body's right side)
  NORTH face: x=26-33  (8px — front end cap, where head/chest connects)
  WEST  face: x=34-39  (6px — left flank, head-to-tail on the body's left side)
  SOUTH face: x=40-47  (8px — back end cap, where tail connects)
  Side face row:  y=6-11   (6px tall)
  Lower body:     y=12-21, x=20-39  (10px tall — belly extension; SOUTH has no lower body)
  Top face:       y=0-5,  x=26-33
  Bottom face:    y=0-5,  x=40-47

v4: staggered vertical stripes running the full body height (y=6-21).
    3 stripes at x_base = 24, 31, 38.
    Upper half (y=6-13):  stripe at x_base
    Lower half (y=14-21): stripe at x_base+1
    Top face (y=0-5) is excluded — never erased.

v6: small circular cutouts (r=2) at the leg-attachment seams.
    Front legs emerge from the bottom corners of the NORTH face (front end cap):
      (26, 11)  and  (33, 11)
    Back legs emerge from the bottom corners of the SOUTH face (back end cap):
      (40, 11)  and  (47, 11)
    Both versions also remove the ear-tip pixels.
"""

import math
from PIL import Image
import os

SRC     = os.path.join(os.path.dirname(__file__), "../..")
SRC     = os.path.normpath(SRC)
UVMAP   = os.path.join(SRC, "cat_armor_uvmap_64x32.png")
TEX_DIR = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat")

EAR_TIP_PIXELS = frozenset({(3,10),(3,11),(8,10),(9,10),(8,11),(9,11)})

# v4: staggered vertical stripes spanning full body height y=6-21
_V4_STRIPES = []
for x_base in (24, 31, 38):
    for y in range(6, 14):    # upper half y=6-13 at x_base
        _V4_STRIPES.append((x_base, y))
    for y in range(14, 22):   # lower half y=14-21 at x_base+1
        _V4_STRIPES.append((x_base + 1, y))
V4_STRIPE_PIXELS = frozenset(_V4_STRIPES)

# v6: circular cutouts at the 4 leg-attachment corners
V6_LIMB_CIRCLES = [
    (26, 11, 2),   # front-left  (NORTH face left  corner, front paw)
    (33, 11, 2),   # front-right (NORTH face right corner, front paw)
    (40, 11, 2),   # back-left   (SOUTH face left  corner, back  paw)
    (47, 11, 2),   # back-right  (SOUTH face right corner, back  paw)
]


def is_armor_pixel(a: int) -> bool:
    return a == 220


def in_limb_circle(x: int, y: int) -> bool:
    for cx, cy, r in V6_LIMB_CIRCLES:
        if math.sqrt((x - cx)**2 + (y - cy)**2) <= r:
            return True
    return False


def generate(version: int, tier: str, uv_img: Image.Image) -> Image.Image:
    v1  = Image.open(os.path.join(TEX_DIR, f"cat_armor_{tier}.png")).convert("RGBA")
    out = v1.copy()
    uv  = uv_img.load()
    dst = out.load()

    for y in range(32):
        for x in range(64):
            _, _, _, a = uv[x, y]
            if not is_armor_pixel(a):
                continue

            erase = False

            if (x, y) in EAR_TIP_PIXELS:
                erase = True

            if version == 4 and (x, y) in V4_STRIPE_PIXELS:
                erase = True

            if version == 6 and in_limb_circle(x, y):
                erase = True

            if erase:
                dst[x, y] = (0, 0, 0, 0)

    return out


def main():
    uv_img = Image.open(UVMAP).convert("RGBA")

    for ver in (4, 6):
        for tier in ("iron", "gold", "diamond", "netherite"):
            img = generate(ver, tier, uv_img)
            img.save(os.path.join(TEX_DIR, f"cat_armor_{tier}_v{ver}.png"))
            print(f"Saved {tier}_v{ver}")
            img.resize((64*12, 32*12), Image.NEAREST).save(
                os.path.join(SRC, f"cat_armor_v{ver}_{tier}_preview.png"))
            print(f"  preview saved")


if __name__ == "__main__":
    main()
