"""Die Wolfsruestung als Referenz auslesen.

Gerry: "Wolf Ruestungen passen so, die sollen nicht angepasst werden, sondern als
Basis der anderen Armors dienen." Dieses Modul liest sie darum ausschliesslich —
es schreibt nie in textures/entity/wolf/.

Zwei Dinge werden extrahiert:
  * die 11-stufige Farbrampe je Tier (hell -> dunkel nach Luminanz)
  * die Flaechendeckung je Modellflaeche, als Vorlage fuer die Formgrammatik
"""

import os

import geometry
import png

TIERS = ("iron", "gold", "diamond", "netherite")

_REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
WOLF_DIR = os.path.join(_REPO, "src/main/resources/assets/vanillaplusadditions"
                               "/textures/entity/wolf")


def _lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def wolf_texture(tier):
    return png.load(os.path.join(WOLF_DIR, "wolf_armor_%s.png" % tier))


def ramp(tier):
    """Farbrampe des Tiers, hell -> dunkel. 11 Stufen."""
    img = wolf_texture(tier)
    counts = {}
    for i in range(0, len(img.px), 4):
        if img.px[i + 3] > 0:
            c = (img.px[i], img.px[i + 1], img.px[i + 2])
            counts[c] = counts.get(c, 0) + 1
    return [c for c, _ in sorted(counts.items(), key=lambda kv: -_lum(kv[0]))]


def ramps():
    return {t: ramp(t) for t in TIERS}


def ramp_histogram(tier):
    """Pixelzahl je Rampenstufe — beim Wolf ueber alle Tiers identisch."""
    img = wolf_texture(tier)
    counts = {}
    for i in range(0, len(img.px), 4):
        if img.px[i + 3] > 0:
            c = (img.px[i], img.px[i + 1], img.px[i + 2])
            counts[c] = counts.get(c, 0) + 1
    return [counts[c] for c in ramp(tier)]


def coverage(model_name, img):
    """Deckung je (Box, Flaeche): (deckende Pixel, Gesamtpixel)."""
    model = geometry.get(model_name)
    out = {}
    for part in model.parts:
        for box in part.boxes:
            for face, (fx, fy, fw, fh) in box.rects().items():
                if fw == 0 or fh == 0:
                    continue
                cov = tot = 0
                for y in range(fy, min(fy + fh, img.h)):
                    for x in range(fx, min(fx + fw, img.w)):
                        tot += 1
                        if img.get(x, y)[3] > 0:
                            cov += 1
                if tot:
                    out[(part.name, box.name, face)] = (cov, tot)
    return out
