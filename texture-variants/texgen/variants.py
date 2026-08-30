"""Die Ruestungsvarianten fuer Katze und Axolotl, im Formvokabular der Wolfsruestung.

Aus der Wolfstextur ausgelesene Grammatik (siehe wolfswatch.py):
  1. Mail-Sattel ueber dem Ruecken, voll gedeckt
  2. Flanken bis rund zwei Drittel der Hoehe herunter, Bauch offen
  3. genau EIN Bauchgurt, der einmal komplett herumlaeuft
  4. Helmdecke oben auf dem Kopf + schmales Stirnband; Gesicht und Schnauze frei
  5. kleine Manschette am Bein, sonst Bein frei
  6. Schwanz und Ohren komplett frei

Vier Deckungsstufen. Jede Stufe enthaelt die vorherige.
"""

import fa_reserved
import paint
import wolfswatch as ws

LEVELS = {
    1: "W1-riemen",
    2: "W2-sattel",
    3: "W3-sattel-kragen",
    4: "W4-ritter",
}

DESCRIPTIONS = {
    "W1-riemen": "Nur der Bauchgurt, der einmal um den Koerper laeuft — plus Schnalle. "
                 "Das Tier bleibt maximal sichtbar.",
    "W2-sattel": "Die direkte Wolf-Uebersetzung: Mail-Sattel ueber dem Ruecken, Flanken "
                 "zwei Drittel herunter, ein Bauchgurt. Kopf, Beine und Schwanz frei.",
    "W3-sattel-kragen": "Wie W2, dazu Brustkragen und die kleinen Beinmanschetten des Wolfs.",
    "W4-ritter": "Wie W3, dazu Helmdecke und Stirnband. Gesicht, Ohren bzw. Kiemen und "
                 "Augen bleiben ausgespart.",
}


def _mail_into(face, x0, y0, x1, y1, swatch=None, mirror=False):
    """Bereich [x0,x1) x [y0,y1) mit dem Wolf-Mail fuellen.

    mirror nur dort einschalten, wo die x-Achse der Flaeche quer ueber die
    Wirbelsaeule laeuft (Sattel- und Bauchflaechen). Auf Flanken ist x die Hoehe
    bzw. die Koerperlaenge — dort wuerde Spiegeln die Form zerstoeren.
    """
    grid = ws.tile(swatch or ws.mail(), x1 - x0, y1 - y0, mirror=mirror)
    ws.stamp(face, grid, x0, y0)


def _leading_edge(face, along_x, at=0):
    """Dunkle Abschlusskante, wie der Wolf sie an der Vorderkante des Sattels hat."""
    if along_x:
        for y in range(face.h):
            if face.filled(at, y):
                face.set(at, y, paint.RIM)
    else:
        for x in range(face.w):
            if face.filled(x, at):
                face.set(x, at, paint.RIM)


def _girth_row(face, y, horizontal=True):
    """Eine Zeile (bzw. Spalte) im Muster des Wolf-Bauchgurts."""
    g = ws.girth()
    if horizontal:
        for x in range(face.w):
            face.set(x, y, g[x % len(g)])
    else:
        for yy in range(face.h):
            face.set(y, yy, g[yy % len(g)])


# --------------------------------------------------------------------------- Katze
# Koerperbox 4x16x6, um PI/2 gedreht: Flaeche "back" ist visuell oben (Sattel),
# "front" ist der Bauch, "left"/"right" sind die Flanken (u = Hoehe, 0 = oben;
# v = Koerperlaenge), "top" ist die Brustkappe, "bottom" das Heck.

CAT_GIRTH_V = 5          # Position des Bauchgurts entlang der Koerperlaenge (von 16)
CAT_FLANK_DEPTH = 4      # wie weit die Flanke herunterreicht (von 6) — wie beim Wolf


def paint_cat(sheet, level):
    # --- Sattel (Ruecken)
    if level >= 2:
        f = sheet.face("body", "back")
        _mail_into(f, 0, 0, f.w, f.h, mirror=True)
        _leading_edge(f, along_x=False, at=0)
        sheet.blit("body", "back", f)
    else:
        f = sheet.face("body", "back")
        _girth_row(f, CAT_GIRTH_V)
        _girth_row(f, CAT_GIRTH_V + 1)
        sheet.blit("body", "back", f)

    # --- Flanken
    for side in ("left", "right"):
        f = sheet.face("body", side)
        if level >= 2:
            _mail_into(f, 0, 0, CAT_FLANK_DEPTH, f.h, ws.flank())
            _leading_edge(f, along_x=True, at=0)     # Oberkante, wo der Sattel ansetzt
            _leading_edge(f, along_x=False, at=0)    # Vorderkante
        # Bauchgurt laeuft ueber die volle Flankenhoehe
        for x in range(f.w):
            g = ws.girth()
            f.set(x, CAT_GIRTH_V, g[x % len(g)])
            if level == 1:
                f.set(x, CAT_GIRTH_V + 1, g[(x + 1) % len(g)])
        sheet.blit("body", side, f)

    # --- Bauch: nur der Gurt
    f = sheet.face("body", "front")
    _girth_row(f, CAT_GIRTH_V)
    if level == 1:
        _girth_row(f, CAT_GIRTH_V + 1)
    paint.rivet(f, f.w // 2, CAT_GIRTH_V)
    sheet.blit("body", "front", f)

    # --- Brustkappe (Kragen)
    if level >= 3:
        f = sheet.face("body", "top")
        _mail_into(f, 0, 0, f.w, f.h, ws.mane(), mirror=True)
        paint.rim(f)
        sheet.blit("body", "top", f)

    # --- Beinmanschetten
    if level >= 3:
        cuff = ws.cuff()
        for part, box, row in (("left_front_leg", "leg", 4), ("right_front_leg", "leg", 4),
                               ("left_hind_leg", "leg", 2), ("right_hind_leg", "leg", 2)):
            for face_name in ("front", "back", "left", "right"):
                f = sheet.face(part, face_name, box_name=box)
                ws.stamp(f, ws.tile(cuff, f.w, len(cuff)), 0, row)
                sheet.blit(part, face_name, f, box_name=box)

    # --- Helm
    if level >= 4:
        f = sheet.face("head", "top", box_name="main")
        ws.stamp(f, ws.tile(ws.helm_top(), f.w, f.h), 0, 0)
        paint.rim(f)
        sheet.blit("head", "top", f, box_name="main")
        for side in ("left", "right"):
            f = sheet.face("head", side, box_name="main")
            ws.stamp(f, ws.tile(ws.helm_side(), f.w, 2), 0, 0)
            sheet.blit("head", side, f, box_name="main")
        f = sheet.face("head", "back", box_name="main")
        ws.stamp(f, ws.tile(ws.helm_top(), f.w, 2), 0, 0)
        sheet.blit("head", "back", f, box_name="main")
        # Stirnband — nur die oberste Zeile, Gesicht bleibt frei
        f = sheet.face("head", "front", box_name="main")
        ws.stamp(f, ws.tile(ws.browband(), f.w, 1), 0, 0)
        sheet.blit("head", "front", f, box_name="main")


# ------------------------------------------------------------------------ Axolotl
# Koerperbox 8x4x10, ungedreht: "top" ist der Ruecken (u = Breite, v = Laenge),
# "bottom" der Bauch, "left"/"right" die Flanken (u = Laenge, v = Hoehe, 0 = oben).

AXO_GIRTH_V = 3
AXO_FLANK_DEPTH = 3      # von 4 Hoeheneinheiten


def paint_axolotl(sheet, level):
    # --- Sattel (Ruecken)
    f = sheet.face("body", "top")
    if level >= 2:
        _mail_into(f, 0, 0, f.w, f.h, mirror=True)
        _leading_edge(f, along_x=False, at=0)
    else:
        _girth_row(f, AXO_GIRTH_V)
        _girth_row(f, AXO_GIRTH_V + 1)
    sheet.blit("body", "top", f)

    # --- Flanken (u = Laenge, v = Hoehe)
    for side in ("left", "right"):
        f = sheet.face("body", side)
        if level >= 2:
            _mail_into(f, 0, 0, f.w, AXO_FLANK_DEPTH, ws.flank())
            _leading_edge(f, along_x=False, at=0)    # Oberkante der Flanke
            _leading_edge(f, along_x=True, at=0)     # Vorderkante
        g = ws.girth()
        for y in range(f.h):
            f.set(AXO_GIRTH_V, y, g[y % len(g)])
            if level == 1:
                f.set(AXO_GIRTH_V + 1, y, g[(y + 1) % len(g)])
        sheet.blit("body", side, f)

    # --- Bauch: nur der Gurt (Laengsachse ist hier v)
    f = sheet.face("body", "bottom")
    _girth_row(f, AXO_GIRTH_V)
    if level == 1:
        _girth_row(f, AXO_GIRTH_V + 1)
    paint.rivet(f, f.w // 2, AXO_GIRTH_V)
    sheet.blit("body", "bottom", f)

    # --- Kragen am Kopfende des Koerpers
    if level >= 3:
        f = sheet.face("body", "front")
        _mail_into(f, 0, 0, f.w, f.h, ws.mane(), mirror=True)
        paint.rim(f)
        sheet.blit("body", "front", f)

    # --- Beinmanschetten (Beine sind flache Quads: nur front/back tragen Pixel)
    if level >= 3:
        cuff = ws.cuff()
        for part in ("left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"):
            for face_name in ("front", "back"):
                f = sheet.face(part, face_name, box_name="leg")
                ws.stamp(f, ws.tile(cuff, f.w, len(cuff)), 0, 2)
                sheet.blit(part, face_name, f, box_name="leg")

    # --- Helm; Kiemen bleiben unangetastet (eigene Boxen, werden nie bemalt)
    if level >= 4:
        f = sheet.face("head", "top")
        ws.stamp(f, ws.tile(ws.helm_top(), f.w, f.h), 0, 0)
        paint.rim(f)
        sheet.blit("head", "top", f)
        for side in ("left", "right"):
            f = sheet.face("head", side)
            ws.stamp(f, ws.tile(ws.helm_side(), f.w, 2), 0, 0)
            sheet.blit("head", side, f)
        f = sheet.face("head", "back")
        ws.stamp(f, ws.tile(ws.helm_top(), f.w, 2), 0, 0)
        sheet.blit("head", "back", f)
        f = sheet.face("head", "front")
        ws.stamp(f, ws.tile(ws.browband(), f.w, 1), 0, 0)
        sheet.blit("head", "front", f)


PAINTERS = {"cat": paint_cat, "axolotl": paint_axolotl}


def build(species, level):
    """Index-Sheet einer Variante. FA-Sperrpixel werden am Ende hart geraeumt."""
    import geometry
    sheet = paint.Sheet(geometry.get(species))
    PAINTERS[species](sheet, level)
    for (x, y) in fa_reserved.RESERVED.get(species, ()):
        sheet.cells[y][x] = None
    return sheet
