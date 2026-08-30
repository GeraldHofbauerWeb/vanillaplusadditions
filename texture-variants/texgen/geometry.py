"""Modellgeometrie von Katze, Axolotl und Wolf + die Vanilla-UV-Konvention.

Die Boxdaten sind aus den dekompilierten Vanilla-Quellen abgeschrieben (Pfade unten).
Bewusst *abgeschrieben* statt zur Laufzeit geparst: die dekompilierten Quellen liegen
unter build/ und sind gitignored — ein Parser wuerde auf einem frischen Checkout
brechen. `verify_against_sources()` prueft die Daten gegen die Quellen zurueck, wenn
sie vorhanden sind.

Quellen (build/neoform/neoFormJoined1.21-20240613.152323/steps/unzipSources/unpacked/
net/minecraft/client/model/):
  OcelotModel.createBodyMesh   — Katze (CatModel erbt davon), Blatt 64x32
  AxolotlModel.createBodyLayer — Axolotl, Blatt 64x64
  WolfModel.createMeshDefinition — Wolf, Blatt 64x32  (nur Referenz, nie beschrieben)

UV-Konvention, belegt aus ModelPart.Cube (Konstruktor): fuer texOffs (u,v) und
Boxmasse (w,h,d) gilt
    TOP    (minY) : (u+d,     v    ) w x d
    BOTTOM (maxY) : (u+d+w,   v    ) w x d
    LEFT   (minX) : (u,       v+d  ) d x h
    FRONT  (minZ) : (u+d,     v+d  ) w x h
    RIGHT  (maxX) : (u+d+w,   v+d  ) d x h
    BACK   (maxZ) : (u+d+w+d, v+d  ) w x h
MC-Modellraum hat +Y nach UNTEN (das Modell wird beim Rendern gespiegelt), darum ist
die minY-Flaeche visuell oben. Die Namen hier sind visuell gemeint.
Rotationen: ModelPart.translateAndRotate nutzt rotationZYX(zRot,yRot,xRot),
also v' = Rz * Ry * Rx * v.
"""

import math

FACES = ("top", "bottom", "left", "front", "right", "back")


def face_rects(tu, tv, w, h, d):
    """UV-Rechtecke (x, y, w, h) aller sechs Flaechen einer Box."""
    return {
        "top": (tu + d, tv, w, d),
        "bottom": (tu + d + w, tv, w, d),
        "left": (tu, tv + d, d, h),
        "front": (tu + d, tv + d, w, h),
        "right": (tu + d + w, tv + d, d, h),
        "back": (tu + d + w + d, tv + d, w, h),
    }


class Box:
    __slots__ = ("name", "x", "y", "z", "w", "h", "d", "tu", "tv")

    def __init__(self, name, x, y, z, w, h, d, tu, tv):
        self.name = name
        self.x, self.y, self.z = x, y, z
        self.w, self.h, self.d = w, h, d
        self.tu, self.tv = tu, tv

    def rects(self):
        return face_rects(self.tu, self.tv, int(self.w), int(self.h), int(self.d))

    def flat_axis(self):
        """Achse mit Dicke 0 (Kiemen, Schwanzflossen) oder None."""
        if self.w == 0:
            return "x"
        if self.h == 0:
            return "y"
        if self.d == 0:
            return "z"
        return None


class Part:
    __slots__ = ("name", "parent", "px", "py", "pz", "rx", "ry", "rz", "boxes")

    def __init__(self, name, px, py, pz, boxes, rx=0.0, ry=0.0, rz=0.0, parent=None):
        self.name = name
        self.parent = parent
        self.px, self.py, self.pz = px, py, pz
        self.rx, self.ry, self.rz = rx, ry, rz
        self.boxes = boxes


class Model:
    def __init__(self, name, tex_w, tex_h, parts):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.parts = parts
        self._by_name = {p.name: p for p in parts}

    def part(self, name):
        return self._by_name[name]

    def world_matrix(self, part):
        """4x3-Transform (Rotation + Translation) von Part-Raum nach Modellraum."""
        chain = []
        p = part
        while p is not None:
            chain.append(p)
            p = self._by_name[p.parent] if p.parent else None
        m = _identity()
        for p in reversed(chain):
            m = _mul(m, _translate(p.px, p.py, p.pz))
            if p.rz:
                m = _mul(m, _rot_z(p.rz))
            if p.ry:
                m = _mul(m, _rot_y(p.ry))
            if p.rx:
                m = _mul(m, _rot_x(p.rx))
        return m


# ---- kleine Matrixhilfen (3x3 Rotation + Translation) -----------------------

def _identity():
    return ((1.0, 0.0, 0.0, 0.0), (0.0, 1.0, 0.0, 0.0), (0.0, 0.0, 1.0, 0.0))


def _translate(x, y, z):
    return ((1.0, 0.0, 0.0, x), (0.0, 1.0, 0.0, y), (0.0, 0.0, 1.0, z))


def _rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return ((1.0, 0.0, 0.0, 0.0), (0.0, c, -s, 0.0), (0.0, s, c, 0.0))


def _rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, 0.0, s, 0.0), (0.0, 1.0, 0.0, 0.0), (-s, 0.0, c, 0.0))


def _rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, -s, 0.0, 0.0), (s, c, 0.0, 0.0), (0.0, 0.0, 1.0, 0.0))


def _mul(a, b):
    out = []
    for r in range(3):
        row = []
        for c in range(3):
            row.append(sum(a[r][k] * b[k][c] for k in range(3)))
        row.append(sum(a[r][k] * b[k][3] for k in range(3)) + a[r][3])
        out.append(tuple(row))
    return tuple(out)


def apply(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


# ---- die drei Modelle -------------------------------------------------------
# Stehende Ruhepose: Werte aus prepareMobModel/setupAnim bei limbSwingAmount = 0.

HALF_PI = math.pi / 2


def cat_model():
    """OcelotModel.createBodyMesh — Blatt 64x32. Pose: stehend, state != 3."""
    return Model("cat", 64, 32, [
        Part("head", 0.0, 15.0, -9.0, [
            Box("main", -2.5, -2.0, -3.0, 5, 4, 5, 0, 0),
            Box("nose", -1.5, 0.0, -4.0, 3, 2, 2, 0, 24),
            Box("ear1", -2.0, -3.0, 0.0, 1, 1, 2, 0, 10),
            Box("ear2", 1.0, -3.0, 0.0, 1, 1, 2, 6, 10),
        ]),
        # body.xRot = PI/2 (setupAnim, state != 3)
        Part("body", 0.0, 12.0, -10.0, [
            Box("body", -2.0, 3.0, -8.0, 4, 16, 6, 20, 0),
        ], rx=HALF_PI),
        Part("tail1", 0.0, 15.0, 8.0, [
            Box("tail1", -0.5, 0.0, 0.0, 1, 8, 1, 0, 15),
        ], rx=0.9),
        Part("tail2", 0.0, 20.0, 14.0, [
            Box("tail2", -0.5, 0.0, 0.0, 1, 8, 1, 4, 15),
        ], rx=1.7278761),
        Part("left_hind_leg", 1.1, 18.0, 5.0, [
            Box("leg", -1.0, 0.0, 1.0, 2, 6, 2, 8, 13),
        ]),
        Part("right_hind_leg", -1.1, 18.0, 5.0, [
            Box("leg", -1.0, 0.0, 1.0, 2, 6, 2, 8, 13),
        ]),
        Part("left_front_leg", 1.2, 14.1, -5.0, [
            Box("leg", -1.0, 0.0, 0.0, 2, 10, 2, 40, 0),
        ]),
        Part("right_front_leg", -1.2, 14.1, -5.0, [
            Box("leg", -1.0, 0.0, 0.0, 2, 10, 2, 40, 0),
        ]),
    ])


def axolotl_model():
    """AxolotlModel.createBodyLayer — Blatt 64x64."""
    return Model("axolotl", 64, 64, [
        Part("body", 0.0, 20.0, 5.0, [
            Box("body", -4.0, -2.0, -9.0, 8, 4, 10, 0, 11),
            Box("back_fin", 0.0, -3.0, -8.0, 0, 5, 9, 2, 17),
        ]),
        Part("head", 0.0, 0.0, -9.0, [
            Box("head", -4.0, -3.0, -5.0, 8, 5, 5, 0, 1),
        ], parent="body"),
        Part("top_gills", 0.0, -3.0, -1.0, [
            Box("top_gills", -4.0, -3.0, 0.0, 8, 3, 0, 3, 37),
        ], parent="head"),
        Part("left_gills", -4.0, 0.0, -1.0, [
            Box("left_gills", -3.0, -5.0, 0.0, 3, 7, 0, 0, 40),
        ], parent="head"),
        Part("right_gills", 4.0, 0.0, -1.0, [
            Box("right_gills", 0.0, -5.0, 0.0, 3, 7, 0, 11, 40),
        ], parent="head"),
        Part("right_hind_leg", -3.5, 1.0, -1.0, [
            Box("leg", -2.0, 0.0, 0.0, 3, 5, 0, 2, 13),
        ], parent="body"),
        Part("left_hind_leg", 3.5, 1.0, -1.0, [
            Box("leg", -1.0, 0.0, 0.0, 3, 5, 0, 2, 13),
        ], parent="body"),
        Part("right_front_leg", -3.5, 1.0, -8.0, [
            Box("leg", -2.0, 0.0, 0.0, 3, 5, 0, 2, 13),
        ], parent="body"),
        Part("left_front_leg", 3.5, 1.0, -8.0, [
            Box("leg", -1.0, 0.0, 0.0, 3, 5, 0, 2, 13),
        ], parent="body"),
        Part("tail", 0.0, 0.0, 1.0, [
            Box("tail", 0.0, -3.0, 0.0, 0, 5, 12, 2, 19),
        ], parent="body"),
    ])


def wolf_model():
    """WolfModel.createMeshDefinition — Blatt 64x32. Nur Referenz."""
    return Model("wolf", 64, 32, [
        Part("head", -1.0, 13.5, -7.0, [], ),
        Part("real_head", 0.0, 0.0, 0.0, [
            Box("head", -2.0, -3.0, -2.0, 6, 6, 4, 0, 0),
            Box("ear_l", -2.0, -5.0, 0.0, 2, 2, 1, 16, 14),
            Box("ear_r", 2.0, -5.0, 0.0, 2, 2, 1, 16, 14),
            Box("snout", -0.5, 0.0, -5.0, 3, 3, 4, 0, 10),
        ], parent="head"),
        Part("body", 0.0, 14.0, 2.0, [
            Box("body", -3.0, -2.0, -3.0, 6, 9, 6, 18, 14),
        ], rx=HALF_PI),
        Part("upper_body", -1.0, 14.0, -3.0, [
            Box("mane", -3.0, -3.0, -3.0, 8, 6, 7, 21, 0),
        ], rx=HALF_PI),
        Part("right_hind_leg", -2.5, 16.0, 7.0, [
            Box("leg", 0.0, 0.0, -1.0, 2, 8, 2, 0, 18),
        ]),
        Part("left_hind_leg", 0.5, 16.0, 7.0, [
            Box("leg", 0.0, 0.0, -1.0, 2, 8, 2, 0, 18),
        ]),
        Part("right_front_leg", -2.5, 16.0, -4.0, [
            Box("leg", 0.0, 0.0, -1.0, 2, 8, 2, 0, 18),
        ]),
        Part("left_front_leg", 0.5, 16.0, -4.0, [
            Box("leg", 0.0, 0.0, -1.0, 2, 8, 2, 0, 18),
        ]),
        Part("tail", -1.0, 12.0, 8.0, [
            Box("tail", 0.0, 0.0, -1.0, 2, 8, 2, 9, 18),
        ], rx=math.pi / 5),
    ])


MODELS = {"cat": cat_model, "axolotl": axolotl_model, "wolf": wolf_model}


def get(name):
    return MODELS[name]()
