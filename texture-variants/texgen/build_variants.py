#!/usr/bin/env python3
"""Alle Varianten + Vorschaubilder erzeugen.

    python3 texture-variants/texgen/build_variants.py [--previews-only]

Schreibt je Variante einen vollstaendigen assets/-Baum unter
texture-variants/<gruppe>/<variante>/, genau so wie apply.sh es erwartet, und die
isometrischen Renders nach texture-variants/preview/.

Die Wolfsruestung wird nur gelesen (sie ist die Referenz) und nie ueberschrieben.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import baseskins          # noqa: E402
import fa_reserved        # noqa: E402
import icons              # noqa: E402
import iso                # noqa: E402
import png                # noqa: E402
import variants           # noqa: E402
import wolfref            # noqa: E402

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
VARDIR = os.path.join(REPO, "texture-variants")
PREVIEW = os.path.join(VARDIR, "preview")

ENTITY_PATH = {
    "cat": "textures/entity/cat_final/cat_armor_%s.png",
    "axolotl": "textures/entity/axolotl_armor/axolotl_armor_%s.png",
}
ITEM_PATH = {
    "cat": "textures/item/cat_armor_%s.png",
    "axolotl": "textures/item/axolotl_armor_%s.png",
}
GROUP = {"cat": "cat-armor", "axolotl": "axolotl-armor"}
ICON_GROUP = {"cat": "cat-armor-icons", "axolotl": "axolotl-armor-icons"}

VIEWS = {
    "": iso.View(-35.0, 28.0, 12.0, 560, 480),          # 3/4 von vorne links
    "_rear": iso.View(145.0, 26.0, 12.0, 560, 480),     # 3/4 von hinten rechts
}


def _write(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    png.save(path, img)


def main():
    previews_only = "--previews-only" in sys.argv
    ramps = wolfref.ramps()
    problems = []

    for species in ("cat", "axolotl"):
        base = baseskins.base(species)
        for level, name in variants.LEVELS.items():
            sheet = variants.build(species, level)
            for tier in wolfref.TIERS:
                tex = sheet.materialize(ramps[tier])

                # --- Pruefungen
                bad = fa_reserved.violations(species, tex)
                if bad:
                    problems.append("%s/%s/%s: FA-Sperrpixel belegt %s"
                                    % (species, name, tier, bad))
                extra = tex.colours() - set(ramps[tier])
                if extra:
                    problems.append("%s/%s/%s: Farben ausserhalb der Wolf-Rampe %s"
                                    % (species, name, tier, extra))
                for i in range(3, len(tex.px), 4):
                    if tex.px[i] not in (0, 255):
                        problems.append("%s/%s/%s: halbtransparente Pixel"
                                        % (species, name, tier))
                        break

                if not previews_only:
                    root = os.path.join(VARDIR, GROUP[species], name,
                                        "assets", "vanillaplusadditions")
                    _write(os.path.join(root, ENTITY_PATH[species] % tier), tex)
                    iroot = os.path.join(VARDIR, ICON_GROUP[species], name,
                                         "assets", "vanillaplusadditions")
                    _write(os.path.join(iroot, ITEM_PATH[species] % tier),
                           icons.build(species, tex, out_size=128))

                for suffix, view in VIEWS.items():
                    img = iso.trim(iso.render(species, base, tex, view))
                    _write(os.path.join(PREVIEW, "%s_%s_%s%s.png"
                                        % (GROUP[species], name, tier, suffix)), img)
                print("  %-8s %-18s %-10s ok" % (species, name, tier), flush=True)

    # Referenzrender der Wolfsruestung (nur Vorschau, Textur bleibt unangetastet)
    wbase = baseskins.base("wolf")
    for tier in wolfref.TIERS:
        wtex = wolfref.wolf_texture(tier)
        for suffix, view in VIEWS.items():
            img = iso.trim(iso.render("wolf", wbase, wtex, view))
            _write(os.path.join(PREVIEW, "wolf-armor_REFERENZ_%s%s.png" % (tier, suffix)), img)
        print("  wolf     REFERENZ           %-10s ok" % tier, flush=True)

    print()
    if problems:
        print("PRUEFUNG FEHLGESCHLAGEN:")
        for p in problems:
            print("  !!", p)
        return 1
    print("Pruefungen ok: keine FA-Sperrpixel belegt, nur Wolf-Rampenfarben, "
          "Alpha sauber 0/255.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
