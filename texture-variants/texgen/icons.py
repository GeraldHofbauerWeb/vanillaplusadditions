"""Item-Icons aus der jeweiligen Variante erzeugen.

Statt ein Icon frei zu zeichnen, wird die Ruestung selbst gerendert — ohne Tier,
nur die Panzerteile — und auf 16px Pixelart heruntergerechnet. Damit passt das Icon
immer zur gewaehlten Variante, zum Tier und zum Tier-Metall, ganz ohne zweite
Zeichenquelle, die auseinanderlaufen koennte.

Ausgabegroesse folgt dem, was im Repo schon liegt: Katze und Axolotl 128x128
(16px-Art, 8x nearest hochskaliert), damit apply.sh die Dateien 1:1 ersetzen kann.
"""

import iso
import png

NATIVE = 16


def _downsample(img, factor):
    """Blockweise auf die haeufigste deckende Farbe reduzieren (kein Weichzeichnen)."""
    ow, oh = img.w // factor, img.h // factor
    out = png.Image(ow, oh)
    for y in range(oh):
        for x in range(ow):
            counts = {}
            clear = 0
            for dy in range(factor):
                for dx in range(factor):
                    c = img.get(x * factor + dx, y * factor + dy)
                    if c[3] == 0:
                        clear += 1
                    else:
                        counts[c] = counts.get(c, 0) + 1
            if not counts or clear > (factor * factor) * 0.62:
                continue
            out.set(x, y, max(counts.items(), key=lambda kv: kv[1])[0])
    return out


def _fit(img, size):
    """Bild mittig in ein quadratisches Feld setzen."""
    out = png.Image(size, size)
    ox = (size - img.w) // 2
    oy = (size - img.h) // 2
    for y in range(img.h):
        for x in range(img.w):
            c = img.get(x, y)
            if c[3]:
                out.set(x + ox, y + oy, c)
    return out


def build(species, armor_tex, out_size=128):
    """Icon einer Variante: nur die Ruestung, isometrisch, auf 16px reduziert."""
    empty = png.Image(armor_tex.w, armor_tex.h)          # kein Tier, nur Ruestung
    big = iso.render(species, empty, armor_tex,
                     iso.View(-32.0, 26.0, 6.0, NATIVE * 8, NATIVE * 8))
    big = iso.trim(big, pad=0)
    # auf ein Vielfaches von 8 bringen, damit das Downsampling sauber aufgeht
    side = max(big.w, big.h)
    side = ((side + 7) // 8) * 8
    side = max(side, NATIVE * 4)
    big = _fit(big, side)
    small = _downsample(big, side // NATIVE)
    small = _fit(small, NATIVE)
    return small.scaled(out_size // NATIVE) if out_size != NATIVE else small
