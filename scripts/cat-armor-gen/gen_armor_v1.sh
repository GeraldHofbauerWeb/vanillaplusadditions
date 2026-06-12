#!/usr/bin/env bash
# Generate v1 cat armor textures from SVG via rsvg-convert.
# Run from the project root.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/../.."
TEX_DIR="$SRC/src/main/resources/assets/vanillaplusadditions/textures/entity/cat"

for tier in iron gold diamond netherite; do
    rsvg-convert -w 64 -h 32 "$SRC/docs/cat_armor_${tier}.svg" \
        -o "$TEX_DIR/cat_armor_${tier}.png"
    echo "Saved ${tier} v1"
done
