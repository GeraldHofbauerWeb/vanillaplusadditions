"""Zeichen-Primitive im Modellraum, im Formvokabular der Wolfsruestung.

Gemalt wird nicht in Farben, sondern in *Rampen-Indizes* 0..10 (0 = hellste Stufe,
10 = dunkelster Rim). Erst beim Materialisieren wird je Tier die passende
11-Stufen-Rampe der Wolfsruestung eingesetzt. Damit ist die Wolf-Konformitaet
strukturell garantiert: alle vier Tiers einer Variante haben zwangslaeufig identische
Pixelzahlen je Rampenstufe, genau wie der Wolf.

Gezeichnet wird immer flaechenweise (Box + Flaeche). Das haelt jeden Strich
automatisch innerhalb seines UV-Rechtecks — ein Rim kann nicht in die Nachbarflaeche
bluten, was im Atlas sonst leicht passiert.
"""

import geometry

# Rollen in der 11-stufigen Rampe (Indizes in die nach Luminanz sortierte Wolf-Rampe)
HILIGHT = 0     # Nieten, Glanzkante
LIGHT = 2       # obere Plattenflaeche
BASE = 5        # Grundflaeche
MID = 6
SHADE = 8       # untere Plattenflaeche
DARK = 9        # Fugen
RIM = 10        # Aussenkante


class Face:
    """Index-Raster einer einzelnen Modellflaeche."""

    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.cells = [[None] * w for _ in range(h)]

    def set(self, x, y, idx):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.cells[y][x] = idx

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.cells[y][x]
        return None

    def filled(self, x, y):
        return self.get(x, y) is not None

    def any_filled(self):
        return any(c is not None for row in self.cells for c in row)


def band(face, y0, y1, x0=0, x1=None):
    """Waagerechtes Band mit Wertverlauf hell -> dunkel (die Wolf-Grundform)."""
    x1 = face.w if x1 is None else x1
    rows = max(1, y1 - y0)
    for y in range(max(0, y0), min(y1, face.h)):
        t = (y - y0) / rows
        idx = LIGHT if t < 0.25 else (BASE if t < 0.55 else (MID if t < 0.8 else SHADE))
        for x in range(max(0, x0), min(x1, face.w)):
            face.set(x, y, idx)


def seam(face, y, x0=0, x1=None):
    """Plattenfuge — eine dunkle Pixelzeile, nur wo schon Rüstung liegt."""
    x1 = face.w if x1 is None else x1
    for x in range(max(0, x0), min(x1, face.w)):
        if face.filled(x, y):
            face.set(x, y, DARK)


def vseam(face, x, y0=0, y1=None):
    y1 = face.h if y1 is None else y1
    for y in range(max(0, y0), min(y1, face.h)):
        if face.filled(x, y):
            face.set(x, y, DARK)


def rivet(face, x, y):
    if face.filled(x, y):
        face.set(x, y, HILIGHT)


def strap(face, y, thickness=1, x0=0, x1=None):
    """Riemen — schmales Band in mittlerem Wert, ohne Verlauf."""
    x1 = face.w if x1 is None else x1
    for yy in range(max(0, y), min(y + thickness, face.h)):
        for x in range(max(0, x0), min(x1, face.w)):
            face.set(x, yy, MID)


def edge_light(face):
    """1px Glanzkante auf der obersten gefuellten Zeile jeder Spalte."""
    for x in range(face.w):
        for y in range(face.h):
            if face.filled(x, y):
                face.set(x, y, LIGHT if face.get(x, y) == BASE else face.get(x, y))
                face.set(x, y, HILIGHT if y == 0 else LIGHT)
                break


def rim(face):
    """1px dunkle Aussenkante rund um die gefuellte Flaeche, innen liegend.

    Wird auf die Randpixel der Fuellung gesetzt, nicht daneben — die Silhouette
    bleibt dadurch exakt gleich und die Kante trennt die Ruestung sichtbar vom Fell.
    """
    edge = []
    for y in range(face.h):
        for x in range(face.w):
            if not face.filled(x, y):
                continue
            if (x == 0 or y == 0 or x == face.w - 1 or y == face.h - 1
                    or not face.filled(x - 1, y) or not face.filled(x + 1, y)
                    or not face.filled(x, y - 1) or not face.filled(x, y + 1)):
                edge.append((x, y))
    for x, y in edge:
        face.set(x, y, RIM)


class Sheet:
    """Index-Atlas eines ganzen Modells, flaechenweise befuellt."""

    def __init__(self, model):
        self.model = model
        self.w = model.tex_w
        self.h = model.tex_h
        self.cells = [[None] * self.w for _ in range(self.h)]

    def _box(self, part_name, box_name=None):
        part = self.model.part(part_name)
        for b in part.boxes:
            if box_name is None or b.name == box_name:
                return b
        raise KeyError("Box %s/%s nicht gefunden" % (part_name, box_name))

    def face(self, part_name, face_name, box_name=None):
        """Leeres Index-Raster fuer eine Modellflaeche."""
        b = self._box(part_name, box_name)
        _x, _y, fw, fh = b.rects()[face_name]
        return Face(fw, fh)

    def blit(self, part_name, face_name, face, box_name=None):
        b = self._box(part_name, box_name)
        fx, fy, fw, fh = b.rects()[face_name]
        for y in range(min(fh, face.h)):
            for x in range(min(fw, face.w)):
                v = face.cells[y][x]
                if v is not None and 0 <= fy + y < self.h and 0 <= fx + x < self.w:
                    self.cells[fy + y][fx + x] = v

    def materialize(self, ramp):
        """Index-Atlas -> RGBA-Bild mit der Rampe eines Tiers."""
        import png as pngmod
        img = pngmod.Image(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                v = self.cells[y][x]
                if v is None:
                    continue
                r, g, b = ramp[min(v, len(ramp) - 1)]
                img.set(x, y, (r, g, b, 255))
        return img

    def index_histogram(self):
        counts = {}
        for row in self.cells:
            for v in row:
                if v is not None:
                    counts[v] = counts.get(v, 0) + 1
        return counts

    def opaque_count(self):
        return sum(1 for row in self.cells for v in row if v is not None)
