#!/usr/bin/env python3
"""
Cat armor v3 — v1 design with:
  - 3 horizontal transparent breathing stripes across the body
    (body UV rows y=9, 13, 17 — evenly spaced through the body height y=6-21)
  - Ear tips transparent
    (the 6 UV pixels that form the top face of ear1 and ear2 boxes: y=10-11, x∈{3,8,9})
"""

from PIL import Image
import os

SRC     = os.path.normpath(os.path.join(os.path.dirname(__file__), "../.."))  # noqa
UVMAP   = os.path.join(SRC, "cat_armor_uvmap_64x32.png")
TEX_DIR = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat")

# Ear tip UV pixels (top face of each ear box, identified from UV map)
EAR_TIP_PIXELS = frozenset({(3,10), (3,11), (8,10), (9,10), (8,11), (9,11)})

# Body breathing stripe rows — blue (body) UV pixels at these y values become transparent
BODY_STRIPE_Y = frozenset({9, 13, 17})


def is_armor_pixel(a: int) -> bool:
    return a == 220


def is_body_pixel(r: int, g: int, b: int) -> bool:
    """Blue-dominant UV pixels = body region."""
    return b > r and b > g


def is_ear_tip(x: int, y: int) -> bool:
    return (x, y) in EAR_TIP_PIXELS


def generate_v3(tier_name: str, uv_img: Image.Image) -> Image.Image:
    v1_path = os.path.join(TEX_DIR, f"cat_armor_{tier_name}.png")
    v1      = Image.open(v1_path).convert("RGBA")
    out     = v1.copy()

    uv_data  = uv_img.load()
    out_data = out.load()

    for y in range(32):
        for x in range(64):
            r, g, b, a = uv_data[x, y]
            if not is_armor_pixel(a):
                continue

            erase = False

            # Ear tip: top face of each ear box → transparent
            if is_ear_tip(x, y):
                erase = True

            # Body breathing stripe: full horizontal row of body UV → transparent
            if is_body_pixel(r, g, b) and y in BODY_STRIPE_Y:
                erase = True

            if erase:
                out_data[x, y] = (0, 0, 0, 0)

    return out


def main():
    uv_img = Image.open(UVMAP).convert("RGBA")

    for tier in ("iron", "gold", "diamond", "netherite"):
        img = generate_v3(tier, uv_img)

        out_path = os.path.join(TEX_DIR, f"cat_armor_{tier}_v3.png")
        img.save(out_path)
        print(f"Saved {out_path}")

        preview = img.resize((64 * 12, 32 * 12), Image.NEAREST)
        preview.save(os.path.join(SRC, f"cat_armor_v3_{tier}_preview.png"))
        print(f"  preview saved")


if __name__ == "__main__":
    main()
