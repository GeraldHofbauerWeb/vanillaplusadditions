#!/usr/bin/env python3
"""Generate the Ponder scene structure for the Inventory Linker.

Ponder loads its scenes from vanilla structure templates. This writes the one used by
``InventoryLinkerPonder``: a 9x9 grass plate with a 2x2x2 Create Item Vault on the left and a
plain barrel on the right — the two inventories the scene links together.

Usage:
    python3 -m venv venv && ./venv/bin/pip install nbtlib
    ./venv/bin/python scripts/generate_inventory_linker_ponder.py

Writes: src/main/resources/assets/vanillaplusadditions/ponder/inventory_linker/linking.nbt
The tag layout mirrors the committed chunk_loader_track/spacing.nbt, which is known to load.
"""

import os

import nbtlib
from nbtlib.tag import Compound, Int, List, String

DATA_VERSION = 3955  # Minecraft 1.21.1, same as the existing ponder structure

SIZE_X, SIZE_Y, SIZE_Z = 9, 3, 9

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_PATH = os.path.join(
    REPO_ROOT,
    "src/main/resources/assets/vanillaplusadditions/ponder/inventory_linker/linking.nbt",
)

PALETTE = [
    Compound({"Name": String("minecraft:grass_block"),
              "Properties": Compound({"snowy": String("false")})}),
    Compound({"Name": String("create:item_vault"),
              "Properties": Compound({"axis": String("z"), "large": String("true")})}),
    Compound({"Name": String("minecraft:barrel"),
              "Properties": Compound({"facing": String("up"), "open": String("false")})}),
]

GRASS, VAULT, BARREL = 0, 1, 2


def block(x, y, z, state):
    return Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(state)})


def main():
    blocks = []

    # Ground plate.
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            blocks.append(block(x, 0, z, GRASS))

    # 2x2x2 item vault on the left (axis z => the 2x2 cross-section is x/y, hence large=true).
    for x in (1, 2):
        for y in (1, 2):
            for z in (3, 4):
                blocks.append(block(x, y, z, VAULT))

    # A single barrel on the right.
    blocks.append(block(6, 1, 4, BARREL))

    structure = nbtlib.File({
        "size": List[Int]([Int(SIZE_X), Int(SIZE_Y), Int(SIZE_Z)]),
        "entities": List[Compound]([]),
        "blocks": List[Compound](blocks),
        "palette": List[Compound](PALETTE),
        "DataVersion": Int(DATA_VERSION),
    })
    structure.gzipped = True

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    structure.save(OUT_PATH)
    print(f"wrote {OUT_PATH} ({len(blocks)} blocks)")


if __name__ == "__main__":
    main()
