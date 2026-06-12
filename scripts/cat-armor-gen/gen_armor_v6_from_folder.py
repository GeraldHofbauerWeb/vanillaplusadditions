#!/usr/bin/env python3
"""
Cat armor v6 — copies textures from textures/entity/cat_v6/ and strips the
back end cap (SOUTH face x=40-47, BOTTOM face x=40-47) so only the front
body, flanks, and paw area have armor coverage.
"""

from PIL import Image
import os

SRC     = os.path.normpath(os.path.join(os.path.dirname(__file__), "../.."))
TEX_CAT = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat")
TEX_V6  = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat_v6")


def main():
    for tier in ("iron", "gold", "diamond", "netherite"):
        img = Image.open(os.path.join(TEX_V6, f"cat_armor_{tier}_v6.png")).convert("RGBA")
        dst = img.load()

        # Erase SOUTH face (x=40-47, y=6-11) and BOTTOM body face (x=40-47, y=0-5).
        # Lower body (y=12-21) has no armor pixels at x>=40 so this is a no-op there.
        for y in range(32):
            for x in range(40, 64):
                dst[x, y] = (0, 0, 0, 0)

        img.save(os.path.join(TEX_CAT, f"cat_armor_{tier}_v6.png"))
        print(f"Saved {tier}_v6")

        img.resize((64 * 12, 32 * 12), Image.NEAREST).save(
            os.path.join(SRC, f"cat_armor_v6_{tier}_preview.png"))
        print(f"  preview saved")


if __name__ == "__main__":
    main()
