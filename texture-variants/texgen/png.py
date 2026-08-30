"""Minimales PNG-Lesen/Schreiben auf reiner Standardbibliothek.

Bewusst kein Pillow: die letzte Generation dieser Toolchain ging verloren, weil sie
in einem Scratchpad lag und ein Setup brauchte. Alle Ein- und Ausgabedateien dieses
Projekts sind 8-Bit-RGBA ohne Interlace (geprueft: nur IHDR/IDAT/IEND), also reicht
zlib + struct.
"""

import struct
import zlib


class Image:
    """8-Bit-RGBA-Bild. px ist ein flaches bytearray der Laenge w*h*4."""

    __slots__ = ("w", "h", "px")

    def __init__(self, w, h, px=None):
        self.w = w
        self.h = h
        self.px = px if px is not None else bytearray(w * h * 4)

    def copy(self):
        return Image(self.w, self.h, bytearray(self.px))

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x, y):
        o = (y * self.w + x) * 4
        return (self.px[o], self.px[o + 1], self.px[o + 2], self.px[o + 3])

    def set(self, x, y, rgba):
        if not self.inside(x, y):
            return
        o = (y * self.w + x) * 4
        self.px[o] = rgba[0]
        self.px[o + 1] = rgba[1]
        self.px[o + 2] = rgba[2]
        self.px[o + 3] = rgba[3]

    def blend(self, x, y, rgba):
        """Ueber den Untergrund legen (Straight-Alpha, Source-over)."""
        if not self.inside(x, y):
            return
        sa = rgba[3]
        if sa == 0:
            return
        if sa == 255:
            self.set(x, y, rgba)
            return
        o = (y * self.w + x) * 4
        da = self.px[o + 3]
        out_a = sa + da * (255 - sa) // 255
        if out_a == 0:
            self.px[o:o + 4] = b"\0\0\0\0"
            return
        for i in range(3):
            s = rgba[i] * sa
            d = self.px[o + i] * da * (255 - sa) // 255
            self.px[o + i] = (s + d) // out_a
        self.px[o + 3] = out_a

    def scaled(self, factor):
        """Nearest-Neighbour-Vergroesserung — Pixelart bleibt Pixelart."""
        out = Image(self.w * factor, self.h * factor)
        for y in range(out.h):
            sy = y // factor
            for x in range(out.w):
                o = (y * out.w + x) * 4
                s = ((sy * self.w) + x // factor) * 4
                out.px[o:o + 4] = self.px[s:s + 4]
        return out

    def colours(self, opaque_only=True):
        """Menge der vorkommenden RGB-Werte."""
        seen = set()
        for i in range(0, len(self.px), 4):
            if not opaque_only or self.px[i + 3] > 0:
                seen.add((self.px[i], self.px[i + 1], self.px[i + 2]))
        return seen

    def opaque_count(self):
        return sum(1 for i in range(3, len(self.px), 4) if self.px[i] > 0)


def _unfilter(raw, w, h):
    bpp = 4
    stride = w * bpp
    out = bytearray()
    prev = bytearray(stride)
    k = 0
    for _ in range(h):
        f = raw[k]
        k += 1
        line = bytearray(raw[k:k + stride])
        k += stride
        if f == 1:
            for x in range(bpp, stride):
                line[x] = (line[x] + line[x - bpp]) & 255
        elif f == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        elif f != 0:
            raise ValueError("unbekannter PNG-Filter %d" % f)
        out += line
        prev = line
    return out


def load(path):
    with open(path, "rb") as fh:
        d = fh.read()
    if d[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("kein PNG: %s" % path)
    w, h, bd, ct, _cm, _fl, il = struct.unpack(">IIBBBBB", d[16:29])
    if il != 0:
        raise ValueError("interlaced PNG wird nicht unterstuetzt: %s" % path)
    if bd != 8 or ct not in (2, 6):
        raise ValueError("nur 8-Bit RGB/RGBA unterstuetzt (%s: bd=%d ct=%d)" % (path, bd, ct))
    idat = b""
    i = 8
    while i < len(d):
        ln, typ = struct.unpack(">I4s", d[i:i + 8])
        if typ == b"IDAT":
            idat += d[i + 8:i + 8 + ln]
        i += 12 + ln
    raw = zlib.decompress(idat)
    if ct == 6:
        return Image(w, h, bytearray(_unfilter(raw, w, h)))
    # RGB ohne Alpha -> auf RGBA aufblasen
    bpp, stride = 3, w * 3
    out = bytearray()
    prev = bytearray(stride)
    k = 0
    for _ in range(h):
        f = raw[k]
        k += 1
        line = bytearray(raw[k:k + stride])
        k += stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out += line
        prev = line
    img = Image(w, h)
    for j in range(w * h):
        img.px[j * 4:j * 4 + 3] = out[j * 3:j * 3 + 3]
        img.px[j * 4 + 3] = 255
    return img


def save(path, img):
    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data
                + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF))

    raw = bytearray()
    stride = img.w * 4
    for y in range(img.h):
        raw.append(0)  # Filter "None" — die Bilder sind winzig, Groesse ist egal
        raw += img.px[y * stride:(y + 1) * stride]
    body = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", img.w, img.h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    with open(path, "wb") as fh:
        fh.write(body)
