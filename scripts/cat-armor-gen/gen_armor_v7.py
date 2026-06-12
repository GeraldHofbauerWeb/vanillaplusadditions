#!/usr/bin/env python3
"""
Generate cat armor v7 — SVG-first approach, one SVG per tier.

UV face layout (64×32 texture, verified from UV mask):
  HEAD top face:      x= 5– 9,  y=0–4
  HEAD bottom face:   x=10–14,  y=0–4
  HEAD side faces:    x= 0– 4 / 5– 9 / 10–14 / 15–19,  y=5–8
  Ear 1 area:         x= 0– 5,  y=9–11
  Ear 2 area:         x= 6–11,  y=9–11
  Head cheek/jaw:     x= 0–11,  y=12
  Front leg A UV:     x= 8–15,  y=16–20   (blue-dominant)
  Front leg B UV:     x= 0– 7,  y=18–23   (other)
  Snout/chin:         x= 0– 9,  y=24–27
  BODY top face:      x=26–33,  y=0–5
  BODY bottom face:   x=38–45,  y=2–5  (+ x=42–45, y=0–1)
  BODY EAST flank:    x=20–25,  y=6–11
  BODY NORTH front:   x=26–33,  y=6–11
  BODY WEST flank:    x=34–39,  y=6–11
  BODY SOUTH back:    x=40–47,  y=6–11
  BODY lower belly:   x=20–39,  y=12–21
"""

import os
import subprocess
from PIL import Image

SRC     = os.path.normpath(os.path.join(os.path.dirname(__file__), "../.."))
SVG_DIR = os.path.join(os.path.dirname(__file__), "svg_v7")
TEX_DIR = os.path.join(SRC, "src/main/resources/assets/vanillaplusadditions/textures/entity/cat")

os.makedirs(SVG_DIR, exist_ok=True)

# Palettes: colours matched to v1 texture analysis
PALETTES = {
    "iron": dict(
        dark      = "#3D3D3F",   # (61,61,63)
        mid       = "#4D4D4F",   # (77,77,79)
        light     = "#777779",   # (119,119,121)
        bright    = "#939395",   # (147,147,149)
        highlight = "#9C9CA0",   # (156,156,160)
        top       = "#696970",   # (105,105,112)
        rim       = "#2C2C2D",   # (44,44,45)
    ),
    "gold": dict(
        dark      = "#564200",   # (86,66,0)
        mid       = "#6D5300",   # (109,83,0)
        light     = "#9D7700",   # (157,119,0)
        bright    = "#C79700",   # (199,151,0)
        highlight = "#F7E200",   # (247,226,0)
        top       = "#967200",   # (150,114,0)
        rim       = "#3F3000",   # (63,48,0)
    ),
    "diamond": dict(
        dark      = "#145258",   # (20,82,88)
        mid       = "#1A6870",   # (26,104,112)
        light     = "#259696",   # (37,150,150)
        bright    = "#2FBDCB",   # (47,189,203)
        highlight = "#46F5F9",   # (70,245,249)
        top       = "#238F99",   # (35,143,153)
        rim       = "#0F3C41",   # (15,60,65)
    ),
    "netherite": dict(
        dark      = "#151215",   # (21,18,21)
        mid       = "#1B161B",   # (27,22,27)
        light     = "#252025",   # (37,32,37)
        bright    = "#312941",   # (49,41,65)
        highlight = "#493E49",   # (73,62,73)
        top       = "#251F25",   # (37,31,37)
        rim       = "#0F0D0F",   # (15,13,15)
    ),
}


def make_svg(tier: str) -> str:
    p = PALETTES[tier]
    d  = p["dark"]
    m  = p["mid"]
    li = p["light"]
    br = p["bright"]
    hi = p["highlight"]
    tp = p["top"]
    ri = p["rim"]

    return f"""\
<svg xmlns="http://www.w3.org/2000/svg"
     viewBox="0 0 64 32" width="64" height="32"
     shape-rendering="crispEdges">
  <defs>
    <!-- Body face gradients (vertical: top→bottom) -->
    <linearGradient id="gSide"  x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="{li}"/>
      <stop offset="100%" stop-color="{d}"/>
    </linearGradient>
    <linearGradient id="gFront" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="{br}"/>
      <stop offset="100%" stop-color="{m}"/>
    </linearGradient>
    <linearGradient id="gBack"  x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="{br}"/>
      <stop offset="100%" stop-color="{d}"/>
    </linearGradient>
    <linearGradient id="gLower" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="{m}"/>
      <stop offset="100%" stop-color="{d}"/>
    </linearGradient>
    <!-- Body face gradients (horizontal) -->
    <linearGradient id="gTop"   x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%"   stop-color="{tp}"/>
      <stop offset="100%" stop-color="{ri}"/>
    </linearGradient>
    <linearGradient id="gBot"   x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%"   stop-color="{hi}"/>
      <stop offset="100%" stop-color="{m}"/>
    </linearGradient>
    <!-- Head gradient -->
    <linearGradient id="gHead"  x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="{li}"/>
      <stop offset="50%"  stop-color="{m}"/>
      <stop offset="100%" stop-color="{d}"/>
    </linearGradient>
    <linearGradient id="gLeg"   x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="{m}"/>
      <stop offset="100%" stop-color="{d}"/>
    </linearGradient>
  </defs>

  <!-- ── HEAD (x=0–19) ──────────────────────────────────────────── -->

  <!-- Head top face (x=5–9, y=0–4) -->
  <rect x="5"  y="0" width="5" height="5" fill="{tp}"/>
  <!-- Head bottom face (x=10–14, y=0–4) -->
  <rect x="10" y="0" width="5" height="5" fill="{m}"/>

  <!-- Head side faces (4 faces × 5px wide, y=5–8) -->
  <rect x="0"  y="5" width="5" height="4" fill="url(#gHead)"/>
  <rect x="5"  y="5" width="5" height="4" fill="url(#gHead)"/>
  <rect x="10" y="5" width="5" height="4" fill="url(#gHead)"/>
  <rect x="15" y="5" width="5" height="4" fill="url(#gHead)"/>

  <!-- Ear 1 (texOffset 0,10 — W=2 H=2 D=1) -->
  <rect x="0"  y="9"  width="6" height="3" fill="{m}"/>
  <!-- Ear 2 (texOffset 6,10) -->
  <rect x="6"  y="9"  width="6" height="3" fill="{m}"/>

  <!-- Head cheek/jaw row -->
  <rect x="0"  y="12" width="12" height="1" fill="{m}"/>

  <!-- Front leg UV regions (y=13–23, two interlocked sets) -->
  <rect x="8"  y="13" width="8" height="8"  fill="url(#gLeg)"/>
  <rect x="0"  y="15" width="8" height="9"  fill="url(#gLeg)"/>

  <!-- Snout / chin lower (y=24–27) -->
  <rect x="2"  y="24" width="6" height="2" fill="{m}"/>
  <rect x="0"  y="26" width="10" height="2" fill="{m}"/>

  <!-- ── BODY ───────────────────────────────────────────────────── -->

  <!-- Body TOP face (x=26–33, y=0–5) -->
  <rect x="26" y="0" width="8" height="6" fill="url(#gTop)"/>

  <!-- Body BOTTOM face (x=38–45 y=2–5; notch x=42–45 y=0–1) -->
  <rect x="38" y="2" width="8" height="4" fill="url(#gBot)"/>
  <rect x="42" y="0" width="4" height="2" fill="{hi}"/>

  <!-- Body EAST flank (right side) -->
  <rect x="20" y="6" width="6" height="6" fill="url(#gSide)"/>
  <!-- Body NORTH front face -->
  <rect x="26" y="6" width="8" height="6" fill="url(#gFront)"/>
  <!-- Body WEST flank (left side) -->
  <rect x="34" y="6" width="6" height="6" fill="url(#gSide)"/>
  <!-- Body SOUTH back face -->
  <rect x="40" y="6" width="8" height="6" fill="url(#gBack)"/>

  <!-- Lower body / belly (x=20–39, y=12–21) -->
  <rect x="20" y="12" width="20" height="10" fill="url(#gLower)"/>

</svg>
"""


def main():
    for tier in ("iron", "gold", "diamond", "netherite"):
        # Write SVG
        svg_path = os.path.join(SVG_DIR, f"cat_armor_{tier}_v7.svg")
        with open(svg_path, "w") as f:
            f.write(make_svg(tier))
        print(f"Saved SVG: {svg_path}")

        # Convert SVG → 64×32 PNG
        out_png = os.path.join(TEX_DIR, f"cat_armor_{tier}_v7.png")
        subprocess.run(
            ["rsvg-convert", "-w", "64", "-h", "32", svg_path, "-o", out_png],
            check=True,
        )
        print(f"  → PNG: {out_png}")

        # 12× preview
        preview = Image.open(out_png).resize((64 * 12, 32 * 12), Image.NEAREST)
        preview.save(os.path.join(SRC, f"cat_armor_v7_{tier}_preview.png"))
        print(f"  → preview saved")


if __name__ == "__main__":
    main()
