"""Material-Swatches direkt aus der Wolfsruestung.

Gerry: der Wolf soll die Basis der anderen Ruestungen sein. Statt seinen Look
nachzuerfinden, werden hier seine tatsaechlichen Pixelmuster als Index-Raster
ausgelesen und auf die Flaechen von Katze und Axolotl gekachelt. Das Material ist
dadurch buchstaeblich dasselbe.

Ausgelesene Muster (Indizes in die 11-stufige Rampe, 0 = hellste Stufe):
  MAIL      body/back   6x9  — die Kettengeflecht-Sattelflaeche
  MANE      mane/back   8x6  — zweites Mail-Muster, etwas grober
  HELM_TOP  head/top    6x4  — Helmdecke
  BROWBAND  head/front  Zeilen 0-1 — Stirnband, Gesicht bleibt frei
  CUFF      leg/front   Zeilen 4-5 — die kleine Beinmanschette
  GIRTH     body/front  Zeile 3 — der eine Bauchgurt
"""

import geometry
import wolfref

_cache = {}


def _grid(part, box, face, tier="iron"):
    key = (part, box, face, tier)
    if key in _cache:
        return _cache[key]
    img = wolfref.wolf_texture(tier)
    ramp = wolfref.ramp(tier)
    idx = {c: i for i, c in enumerate(ramp)}
    model = geometry.get("wolf")
    b = [x for x in model.part(part).boxes if x.name == box][0]
    fx, fy, fw, fh = b.rects()[face]
    out = []
    for y in range(fh):
        row = []
        for x in range(fw):
            c = img.get(fx + x, fy + y)
            row.append(None if c[3] == 0 else idx[(c[0], c[1], c[2])])
        out.append(row)
    _cache[key] = out
    return out


def mail():
    return _grid("body", "body", "back")


def mane():
    return _grid("upper_body", "mane", "back")


def flank():
    return _grid("body", "body", "left")


def helm_top():
    return _grid("real_head", "head", "top")


def browband():
    return _grid("real_head", "head", "front")[:2]


def helm_side():
    return _grid("real_head", "head", "left")


def cuff():
    return [r for r in _grid("right_front_leg", "leg", "front") if any(v is not None for v in r)]


def girth():
    """Die eine volle Zeile des Bauchgurts."""
    for row in _grid("body", "body", "front"):
        if any(v is not None for v in row):
            return row
    return []


def tile(swatch, w, h, mirror=True):
    """Swatch auf w x h kacheln.

    Zwei Feinheiten, die beide aus dem Aussehen der Wolfstextur folgen:

    * Die Zeilen werden je Zeile um eins versetzt gesampelt. Ohne diesen Versatz
      wiederholt sich auf einer schmalen Flaeche (Katzensattel: nur 4 Pixel breit)
      immer dieselbe kurze Zeile, und das Kettengeflecht kippt optisch in
      Querstreifen um. Der Versatz ist genau das, was echtes Mail auch tut.
    * mirror spiegelt anschliessend die linke Haelfte auf die rechte, damit die
      Ruestung — wie beim Wolf — symmetrisch zur Wirbelsaeule bleibt. Nur auf
      Flaechen einschalten, deren x-Achse quer ueber den Ruecken laeuft.
    """
    sh = len(swatch)
    sw = len(swatch[0]) if sh else 0
    if not sh or not sw:
        return [[None] * w for _ in range(h)]
    out = []
    for y in range(h):
        row = [swatch[y % sh][(x + y) % sw] for x in range(w)]
        if mirror:
            for x in range((w + 1) // 2, w):
                row[x] = row[w - 1 - x]
        out.append(row)
    return out


def stamp(face, grid, x0=0, y0=0, skip_none=True):
    """Ein Index-Raster in eine Face schreiben."""
    for y, row in enumerate(grid):
        for x, v in enumerate(row):
            if v is None and skip_none:
                continue
            face.set(x0 + x, y0 + y, v)
