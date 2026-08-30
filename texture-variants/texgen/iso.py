"""Isometrischer Software-Renderer fuer die Vorschaubilder.

Kein OpenGL, kein Pillow: die Modelle haben ~11 Boxen, die Texturen sind 64x64. Es
reicht, jeden Texel als projiziertes Viereck zu fuellen und alles per Maleralgorithmus
nach Tiefe zu sortieren. Bei ~10px pro Texel bleibt die Pixelart dabei knackig.

Koordinaten: MC-Modellraum hat +X rechts, +Y NACH UNTEN, +Z nach hinten. Beim
Projizieren wird Y gespiegelt, damit oben oben ist.

Die Ruestung wird genau so zusammengesetzt wie im Spiel: dieselbe Geometrie, und je
Texel gewinnt die Ruestungstextur, wenn sie dort deckend ist, sonst die Grundhaut
(Cutout-Overlay ueber dem Parent-Modell).
"""

import math

import geometry
import png

# Flaechenhelligkeit wie in MC: oben am hellsten, unten am dunkelsten.
FACE_SHADE = {
    "top": 1.0,
    "front": 0.80,
    "back": 0.80,
    "left": 0.62,
    "right": 0.62,
    "bottom": 0.48,
}

# Die vier Eckpunkte je Flaeche in Einheitskoordinaten der Box (u laeuft mit der
# Texturbreite, v mit der Texturhoehe), passend zur UV-Konvention in geometry.py.
FACE_CORNERS = {
    #            p(u,v) -> (x, y, z) als Funktion von (u, v) in [0,1]
    "top":    lambda u, v, w, h, d: (u * w, 0.0, v * d),
    "bottom": lambda u, v, w, h, d: (u * w, h, (1.0 - v) * d),
    "front":  lambda u, v, w, h, d: (u * w, v * h, 0.0),
    "back":   lambda u, v, w, h, d: ((1.0 - u) * w, v * h, d),
    "left":   lambda u, v, w, h, d: (0.0, v * h, (1.0 - u) * d),
    "right":  lambda u, v, w, h, d: (w, v * h, u * d),
}

FACE_NORMAL = {
    "top": (0.0, -1.0, 0.0),
    "bottom": (0.0, 1.0, 0.0),
    "front": (0.0, 0.0, -1.0),
    "back": (0.0, 0.0, 1.0),
    "left": (-1.0, 0.0, 0.0),
    "right": (1.0, 0.0, 0.0),
}


class View:
    def __init__(self, yaw_deg, pitch_deg, scale, out_w, out_h):
        self.yaw = math.radians(yaw_deg)
        self.pitch = math.radians(pitch_deg)
        self.scale = scale
        self.out_w = out_w
        self.out_h = out_h

    def project(self, p):
        """Modellraum -> (Bildschirm-x, Bildschirm-y, Tiefe)."""
        x, y, z = p
        y = -y  # MC-Modellraum: +Y ist unten
        cy, sy = math.cos(self.yaw), math.sin(self.yaw)
        x, z = x * cy - z * sy, x * sy + z * cy
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x * self.scale, -y * self.scale, z)


def _fill_quad(buf, w, h, pts, rgba):
    """Konvexes Viereck fuellen (einfacher Scanline-Fill)."""
    ys = [p[1] for p in pts]
    y0 = max(0, int(math.floor(min(ys))))
    y1 = min(h - 1, int(math.ceil(max(ys))))
    n = len(pts)
    for y in range(y0, y1 + 1):
        yc = y + 0.5
        xs = []
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            if (ay <= yc < by) or (by <= yc < ay):
                t = (yc - ay) / (by - ay)
                xs.append(ax + t * (bx - ax))
        if len(xs) < 2:
            continue
        xa, xb = min(xs), max(xs)
        x0 = max(0, int(math.floor(xa + 0.5)))
        x1 = min(w - 1, int(math.ceil(xb - 0.5)))
        for x in range(x0, x1 + 1):
            o = (y * w + x) * 4
            buf[o] = rgba[0]
            buf[o + 1] = rgba[1]
            buf[o + 2] = rgba[2]
            buf[o + 3] = 255


def _shade(rgb, f):
    return (min(255, int(rgb[0] * f)), min(255, int(rgb[1] * f)), min(255, int(rgb[2] * f)))


def render(model_name, base_tex, overlay_tex=None, view=None, hide=()):
    """Ein Modell rendern. base_tex = Grundhaut, overlay_tex = Ruestung (optional)."""
    model = geometry.get(model_name)
    view = view or View(35.0, 28.0, 11.0, 360, 320)

    cells = []
    for part in model.parts:
        if part.name in hide:
            continue
        m = model.world_matrix(part)
        for box in part.boxes:
            w, h, d = float(box.w), float(box.h), float(box.d)
            flat = box.flat_axis()
            rects = box.rects()
            for face, (fx, fy, fw, fh) in rects.items():
                if fw <= 0 or fh <= 0:
                    continue
                # Bei null-dicken Boxen (Kiemen, Schwanzflossen) traegt nur das
                # Flaechenpaar senkrecht zur flachen Achse ueberhaupt Pixel.
                if flat == "z" and face not in ("front", "back"):
                    continue
                if flat == "x" and face not in ("left", "right"):
                    continue
                if flat == "y" and face not in ("top", "bottom"):
                    continue
                corner = FACE_CORNERS[face]
                nx, ny, nz = FACE_NORMAL[face]
                # Normale in den Modellraum drehen (nur Rotationsanteil)
                wn = (m[0][0] * nx + m[0][1] * ny + m[0][2] * nz,
                      m[1][0] * nx + m[1][1] * ny + m[1][2] * nz,
                      m[2][0] * nx + m[2][1] * ny + m[2][2] * nz)
                shade = _shade_for_normal(wn)
                for ty in range(fh):
                    for tx in range(fw):
                        src = None
                        if overlay_tex is not None:
                            c = overlay_tex.get(fx + tx, fy + ty)
                            if c[3] > 0:
                                src = c
                        if src is None:
                            c = base_tex.get(fx + tx, fy + ty)
                            if c[3] == 0:
                                continue
                            src = c
                        pts = []
                        depth = 0.0
                        for (uu, vv) in ((tx, ty), (tx + 1, ty), (tx + 1, ty + 1), (tx, ty + 1)):
                            lp = corner(uu / fw, vv / fh, w, h, d)
                            wp = geometry.apply(m, (box.x + lp[0], box.y + lp[1], box.z + lp[2]))
                            sx, sy, sz = view.project(wp)
                            pts.append((sx, sy))
                            depth += sz
                        cells.append((depth / 4.0, pts, _shade(src[:3], shade)))

    # Nach dem Projizieren waechst z zum Betrachter hin: kleinere z sind weiter
    # weg und muessen zuerst gemalt werden (Maleralgorithmus).
    cells.sort(key=lambda c: c[0])
    buf = bytearray(view.out_w * view.out_h * 4)
    # zentrieren
    allx = [p[0] for c in cells for p in c[1]]
    ally = [p[1] for c in cells for p in c[1]]
    if not allx:
        return png.Image(view.out_w, view.out_h, buf)
    ox = view.out_w / 2.0 - (min(allx) + max(allx)) / 2.0
    oy = view.out_h / 2.0 - (min(ally) + max(ally)) / 2.0
    for _depth, pts, rgb in cells:
        _fill_quad(buf, view.out_w, view.out_h,
                   [(p[0] + ox, p[1] + oy) for p in pts], (rgb[0], rgb[1], rgb[2], 255))
    return png.Image(view.out_w, view.out_h, buf)


def _shade_for_normal(n):
    """Helligkeit aus der gedrehten Flaechennormalen — nach dem Drehen stimmen
    die festen Flaechennamen nicht mehr, die Richtung im Raum aber schon."""
    x, y, z = n
    ln = math.sqrt(x * x + y * y + z * z) or 1.0
    x, y, z = x / ln, y / ln, z / ln
    if y < -0.7:      # zeigt nach oben (MC: -Y ist oben)
        return 1.0
    if y > 0.7:       # nach unten
        return 0.48
    if abs(z) > 0.7:
        return 0.80
    return 0.62


def trim(img, pad=8):
    """Transparenten Rand wegschneiden, definierten Rand wieder anlegen."""
    minx, miny, maxx, maxy = img.w, img.h, -1, -1
    for y in range(img.h):
        for x in range(img.w):
            if img.px[(y * img.w + x) * 4 + 3] > 0:
                if x < minx:
                    minx = x
                if x > maxx:
                    maxx = x
                if y < miny:
                    miny = y
                if y > maxy:
                    maxy = y
    if maxx < 0:
        return img
    nw = maxx - minx + 1 + 2 * pad
    nh = maxy - miny + 1 + 2 * pad
    out = png.Image(nw, nh)
    for y in range(miny, maxy + 1):
        for x in range(minx, maxx + 1):
            out.set(x - minx + pad, y - miny + pad, img.get(x, y))
    return out
