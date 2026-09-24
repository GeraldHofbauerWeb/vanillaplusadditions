#!/usr/bin/env python3
"""Render the VanillaPlusAdditions item models to PNGs, the way the inventory draws them.

Flat items (``minecraft:item/generated``) become a nearest-neighbour upscale of their texture;
block items get the real isometric inventory view.  Output goes to ``docs/img/items/`` and
``docs/img/blocks/`` and is committed, so neither the Gradle build nor CI ever needs a client jar.

Requires only Pillow.  See ``README.md`` next to this file for the everyday commands.

=================================================================================================
THE MATHS, AND WHERE EVERY CONSTANT COMES FROM
=================================================================================================

Everything below was transcribed from the decompiled 1.21.1 / NeoForge client, not from memory.
The class names in brackets are the source of record; ``--selftest`` re-derives the two tables
that are easy to get backwards and proves they still agree.

Model space
-----------
A model JSON describes geometry in a 0..16 cube.  x = east, y = up, z = south, so

    up = +Y   down = -Y   south = +Z   north = -Z   east = +X   west = -X

Each element is an axis-aligned cuboid ``from``..``to``.  An element may carry a ``rotation``
(origin / axis / angle / rescale) which tilts the whole cuboid [FaceBakery#applyElementRotation],
and each face may carry a ``rotation`` of 0/90/180/270 which turns the UV rectangle on that face
[BlockFaceUV#getShiftedIndex].  Both are handled generically, neither is special-cased.

The GUI transform
-----------------
``minecraft:block/block`` declares

    display.gui = { rotation: [30, 225, 0], translation: [0, 0, 0], scale: 0.625 }

and the client builds the inventory pose like this [GuiGraphics#renderItem, ItemRenderer#render,
ItemTransform#apply]:

    T(slotX + 8, slotY + 8, 150)    centre of the 16x16 slot
  * S(16, -16, 16)                  1 block == 16 GUI px; GUI y grows downwards
  * T(display.translation)          stored in 1/16 block units
  * R                               display.rotation
  * S(display.scale)
  * T(-0.5, -0.5, -0.5)             the model is 0..1, this centres it on the origin

``R`` is JOML's ``rotationXYZ(rx, ry, rz)`` = ``Rx * Ry * Rz``: a vector is spun around Z, then Y,
then X.  With [30, 225, 0] that is ``Rx(30) * Ry(225)``.  Feeding a model-space point p through
the chain and dropping the constant slot offset gives, for an S-pixel image:

    c  = p/16 - (0.5, 0.5, 0.5)                centred, block units
    q  = Rx(30) * Ry(225) * c * display.scale
    screen_x = S/2 + S*q.x + S/16 * tx         depth = S*q.z + S/16 * tz
    screen_y = S/2 - S*q.y - S/16 * ty

so the scale is exactly ``S * display.scale / 16`` output pixels per model unit - 10 px/unit at
S=256 with the standard 0.625.  It is a *constant*: that is what makes every render share one
scale, and anchoring the model-space centre (8,8,8) on the image centre is what makes a 6-high
bowl sit low in its frame instead of floating, exactly as a real slot shows it.  ``--frame`` and
``--anchor model`` exist to override both for doc thumbnails; the defaults stay vanilla.

The projection is orthographic, so x and y of the rotated point *are* the screen position and z is
only a depth.  Larger depth = nearer the viewer.  A face is back-facing when its outward normal has
depth component <= 0; with this matrix a full cube shows up (top), north (right) and east (left).

Which corner carries which UV
-----------------------------
Two tables decide this, and a drift between them is the single most likely way to end up with a
mirrored face that still looks plausible.  So there is exactly one of each here, both transcribed
from the game, and ``--selftest`` asserts they reproduce the identity mapping:

  * ``FACE_VERTEX_INFO`` is [FaceInfo] verbatim - per face, the four quad corners as
    (end, axis) pairs, end 0 = ``from``, end 1 = ``to``.
  * ``duv_at()`` is [BlockElement#uvsByFace] rewritten as a point function; ``default_uv()`` is
    *derived* from it by evaluating it at corners 0 and 2, so a default UV rect can never
    disagree with the corner order.

[BlockFaceUV#getU/getV] put (u1,v1) on corner 0, (u1,v2) on 1, (u2,v2) on 2 and (u2,v1) on 3, and
a face ``rotation`` of r shifts corner i onto UV corner (i + r/90) % 4.  A declared rect with
u1 > u2 (or v1 > v2) mirrors the texture, which several models rely on.

Rasterisation
-------------
An orthographic projection of an axis-aligned rectangle is a parallelogram, so UVs and depth are
both linear in the screen position.  Each face is described by ``P(s,t) = O + s*Es + t*Et`` with
s,t in [0,1]; inverting [Es|Et] turns a pixel centre into (s,t) and a scanline walks x with
constant ds/dx, dt/dx.  The exact s,t range per scanline is solved algebraically, so no pixel
outside the quad is ever touched and no 1-px texture padding is needed to hide overshoot.

Hidden surfaces are a painter's sort *backed by a real depth buffer*:

  * pass 1 draws every fully opaque texel with a depth test and a depth write - order independent
    and exact, an occluded face can never bleed through;
  * pass 2 walks the same faces back to front and blends the partially transparent texels (the
    ``*_glass.png`` of the feeding stations), testing the depth buffer but not writing it.

``--selftest`` checks that against an independently written per-pixel fragment-list reference.

Colour is accumulated premultiplied so the supersampled buffer box-filters down without dark
fringes, and is un-premultiplied at the very end.  Texture sampling is nearest-neighbour, so the
pixel art stays crisp; only the silhouette is smoothed.

Known deviation from the real inventory
---------------------------------------
Face shading here is Minecraft's flat block-face multipliers (up 1.0, down 0.5, north/south 0.8,
east/west 0.6) [FaceBakery].  The inventory actually lights items with the two directional lights
of [Lighting#setupFor3DItems] plus the shader's diffuse mix, which is *not* the same thing - real
screenshots have relatively darker side faces than these renders.  The flat multipliers are what
this project asked for and they are stable and easy to reason about, so they stay; see README.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import os
import sys
import time
import zipfile
from typing import Dict, List, Optional, Sequence, Tuple

try:
    from PIL import Image
except ImportError:                      # only ever hit where nothing is rendered, e.g. on CI
    Image = None

# Bump when the pixels this script produces change: --check then flags every PNG as stale, which
# is exactly right, because the committed PNGs really are out of date at that point.
RENDERER_VERSION = "1.0.0"

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(os.path.dirname(_HERE))

DEFAULT_ASSETS = os.path.join(_REPO, "src/main/resources/assets/vanillaplusadditions")
DEFAULT_OUT = os.path.join(_REPO, "docs/img")
DEFAULT_JAR = os.environ.get("VPA_CLIENT_JAR", os.path.join(_HERE, "client-1.21.1.jar"))

MODID = "vanillaplusadditions"
MANIFEST_NAME = "render-manifest.json"
ITEM_DIR, BLOCK_DIR = "items", "blocks"

FACES: Tuple[str, ...] = ("down", "up", "north", "south", "west", "east")

# Flat block-face shading [FaceBakery].  See "Known deviation" in the module docstring.
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}

# Outward normals in model space.
NORMALS = {"down": (0.0, -1.0, 0.0), "up": (0.0, 1.0, 0.0), "north": (0.0, 0.0, -1.0),
           "south": (0.0, 0.0, 1.0), "west": (-1.0, 0.0, 0.0), "east": (1.0, 0.0, 0.0)}

# [FaceInfo] verbatim: per face the four quad corners, each as (end, axis) with end 0 = `from`,
# end 1 = `to` and axis 0/1/2 = x/y/z.  This is THE corner table; nothing else defines one.
_MINX, _MINY, _MINZ = (0, 0), (0, 1), (0, 2)
_MAXX, _MAXY, _MAXZ = (1, 0), (1, 1), (1, 2)
FACE_VERTEX_INFO: Dict[str, Tuple[Tuple[Tuple[int, int], ...], ...]] = {
    "down": ((_MINX, _MINY, _MAXZ), (_MINX, _MINY, _MINZ), (_MAXX, _MINY, _MINZ), (_MAXX, _MINY, _MAXZ)),
    "up": ((_MINX, _MAXY, _MINZ), (_MINX, _MAXY, _MAXZ), (_MAXX, _MAXY, _MAXZ), (_MAXX, _MAXY, _MINZ)),
    "north": ((_MAXX, _MAXY, _MINZ), (_MAXX, _MINY, _MINZ), (_MINX, _MINY, _MINZ), (_MINX, _MAXY, _MINZ)),
    "south": ((_MINX, _MAXY, _MAXZ), (_MINX, _MINY, _MAXZ), (_MAXX, _MINY, _MAXZ), (_MAXX, _MAXY, _MAXZ)),
    "west": ((_MINX, _MAXY, _MINZ), (_MINX, _MINY, _MINZ), (_MINX, _MINY, _MAXZ), (_MINX, _MAXY, _MAXZ)),
    "east": ((_MAXX, _MAXY, _MAXZ), (_MAXX, _MINY, _MAXZ), (_MAXX, _MINY, _MINZ), (_MAXX, _MAXY, _MINZ)),
}

# Tints that live in Java rather than in the model JSON.  A model that inherits a template vanilla
# tints from code, but has no entry here, is a hard error - an untinted spawn egg is silently wrong.
TINT_FROM_JAVA_PARENTS = {"minecraft:item/template_spawn_egg"}
ITEM_TINTS: Dict[str, Tuple[int, ...]] = {
    # FlyingFishModule.java:104 - new DeferredSpawnEggItem(FLYING_FISH, 0x4F8AA6, 0xE8F2F7, ...)
    "flying_fish_spawn_egg": (0x4F8AA6, 0xE8F2F7),
}

# Entries we deliberately do not render, with a reason that ends up in the summary table.
UNRENDERABLE: Dict[str, str] = {
    "end_conduit": ("parent builtin/entity - vanilla draws conduits with a BlockEntityRenderer "
                    "from geometry hard-coded in Java; neither this mod's model nor vanilla's "
                    "block/conduit.json holds any elements, so there is nothing to derive"),
}

ALPHA_CUTOUT = 26          # vanilla discards alpha < 0.1
MIN_COVERAGE = 0.01        # below this the render is almost certainly broken
THIN_COVERAGE = 0.05       # below this it is worth a look: flat geometry seen nearly edge on
VALID_ROT_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


class ModelError(Exception):
    """The assets are wrong.  Always names the offending file."""


class NotRenderable(ModelError):
    """The model is intact but structurally out of reach (.obj loader, foreign namespace, ...)."""


# -------------------------------------------------------------------------------------------
# logging
# -------------------------------------------------------------------------------------------

class Log:
    def __init__(self, verbose: bool = False, quiet: bool = False) -> None:
        self.verbose, self.quiet = verbose, quiet
        self.warnings: List[str] = []
        self.errors: List[str] = []

    def debug(self, msg: str) -> None:
        if self.verbose:
            print("  [debug] " + msg, file=sys.stderr)

    def info(self, msg: str = "") -> None:
        if not self.quiet:
            print(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)
        print("WARNING: " + msg, file=sys.stderr)

    def error(self, msg: str) -> None:
        self.errors.append(msg)
        print("ERROR: " + msg, file=sys.stderr)


# -------------------------------------------------------------------------------------------
# assets: mod files on disk plus the five vanilla textures (and parents) from the client jar.
# Every read is hashed and attributed to the entry being rendered, which is what --check needs.
# -------------------------------------------------------------------------------------------

_COMPASS_GEN = None


def _compass_gen():
    """Laedt scripts/gen_world_compass_textures.py als Modul - die Umfaerbe-Logik steht dort."""
    global _COMPASS_GEN
    if _COMPASS_GEN is None:
        import importlib.util

        path = os.path.join(_REPO, "scripts", "gen_world_compass_textures.py")
        spec = importlib.util.spec_from_file_location("vpa_compass_gen", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _COMPASS_GEN = module
    return _COMPASS_GEN


def _world_compass_frame(path: str):
    """Welcher Kompass-Frame steckt hinter dieser Textur-ID - oder None, wenn keiner."""
    if path == "item/world_compass":
        return _compass_gen().BASE_FRAME
    prefix = "item/world_compass_"
    if path.startswith(prefix) and path[len(prefix):].isdigit():
        return int(path[len(prefix):])
    return None


class Assets:
    def __init__(self, root: str, jar_path: str, log: Log) -> None:
        self.root = os.path.abspath(root)
        self.log = log
        if not os.path.isdir(self.root):
            raise ModelError("assets directory not found: %s" % self.root)
        if os.path.basename(self.root) != MODID:
            log.warn("--assets points at %r, expected a directory called %r; namespace resolution "
                     "may be wrong" % (os.path.basename(self.root), MODID))
        self.jar_path = os.path.abspath(jar_path)
        self.jar = zipfile.ZipFile(self.jar_path) if os.path.isfile(self.jar_path) else None
        self.hashes: Dict[str, str] = {}
        self._touched: Optional[set] = None

    # -- source tracking ---------------------------------------------------------------------
    def begin_entry(self) -> None:
        self._touched = set()

    def end_entry(self) -> List[Tuple[str, str]]:
        touched = sorted(self._touched or ())
        self._touched = None
        return [(self.portable(s), self.hashes[s]) for s in touched]

    def portable(self, source: str) -> str:
        """Machine-independent source id, so the manifest survives a different checkout path."""
        kind, _, ident = source.partition(":")
        return "file:" + os.path.relpath(ident, self.root) if kind == "file" else source

    # -- reading -----------------------------------------------------------------------------
    def locate(self, ref: str, kind: str, ext: str) -> str:
        namespace, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
        if not path:
            namespace, path = "minecraft", namespace
        if namespace == MODID:
            local = os.path.join(self.root, kind, path + ext)
            # Der Weltkompass liefert seine Texturen seit beta.92 nicht mehr als Dateien aus - sie
            # entstehen im Spiel aus den Kompass-Frames des Spielers (WorldCompassSpriteSource).
            # Fuer das Rendern der Doku-Bilder machen wir hier dasselbe: Vanilla-Frame aus dem
            # Client-Jar holen und mit derselben Logik umfaerben.
            if kind == "textures" and not os.path.isfile(local):
                frame = _world_compass_frame(path)
                if frame is not None:
                    return "compass:%02d" % frame
            return "file:" + local
        if namespace == "minecraft":
            return "jar:assets/minecraft/%s/%s%s" % (kind, path, ext)
        raise NotRenderable("reference %r lives in the %r namespace: that asset ships with another "
                            "mod and is not in this repository" % (ref, namespace))

    def exists(self, source: str) -> bool:
        kind, _, ident = source.partition(":")
        if kind == "file":
            return os.path.isfile(ident)
        if kind == "compass":
            return self.exists("jar:assets/minecraft/textures/item/compass_%s.png" % ident)
        if self.jar is None:
            return False
        try:
            self.jar.getinfo(ident)
            return True
        except KeyError:
            return False

    def read(self, source: str) -> bytes:
        kind, _, ident = source.partition(":")
        try:
            if kind == "file":
                with open(ident, "rb") as handle:
                    data = handle.read()
            elif kind == "compass":
                # Der Hash haengt bewusst am VANILLA-Frame, nicht an den erzeugten Bytes: sonst
                # wuerde ein Pillow-Update jedes Bild als "stale" melden, obwohl sich kein Pixel
                # geaendert hat.
                data = self._recoloured_compass(ident)
            elif kind == "jar":
                if self.jar is None:
                    raise ModelError(
                        "need %s from the vanilla client jar, but none was found at %s.\n"
                        "  Only six vanilla textures are ever read from it (block/lodestone_side, "
                        "block/lodestone_top, item/cod, item/tropical_fish and the two "
                        "item/spawn_egg layers) plus a handful of parent models such as "
                        "block/block and item/generated.  Pass --client-jar or set VPA_CLIENT_JAR."
                        % (ident, self.jar_path))
                data = self.jar.read(ident)
            else:
                raise ModelError("internal: bad source id %r" % source)
        except (OSError, KeyError) as exc:
            raise ModelError("cannot read %s: %s" % (source, exc)) from exc
        self.hashes[source] = hashlib.sha256(data).hexdigest()
        if kind == "compass":
            # Siehe _recoloured_compass: massgeblich ist der Vanilla-Frame, aus dem wir faerben.
            self.hashes[source] = self.hashes["jar:assets/minecraft/textures/item/compass_%s.png" % ident]
        if self._touched is not None:
            self._touched.add(source)
        return data

    def _recoloured_compass(self, index: str) -> bytes:
        """Faerbt einen Vanilla-Kompass-Frame um, genau wie WorldCompassRecolour es im Spiel tut."""
        import io as _io

        from PIL import Image as _Image

        source = "jar:assets/minecraft/textures/item/compass_%s.png" % index
        raw = self.read(source)          # setzt den Hash auf den Vanilla-Frame
        frame = _Image.open(_io.BytesIO(raw)).convert("RGBA")
        dial = _compass_gen().dial_mask(frame)
        edge = _compass_gen().edge_mask(frame, dial)
        out = _io.BytesIO()
        _compass_gen().recolour(frame, dial, edge).save(out, format="PNG")
        return out.getvalue()

    def read_json(self, source: str, what: str) -> dict:
        raw = self.read(source)
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, ValueError) as exc:
            raise ModelError("%s is not valid JSON (%s): %s" % (what, source, exc)) from exc


# -------------------------------------------------------------------------------------------
# model resolution
# -------------------------------------------------------------------------------------------

class Model:
    __slots__ = ("ref", "chain", "textures", "elements", "display", "render_type", "gui_light",
                 "builtin", "loader", "overrides")

    def __init__(self, ref: str) -> None:
        self.ref = ref
        self.chain: List[str] = []
        self.textures: Dict[str, str] = {}
        self.elements: Optional[List[dict]] = None
        self.display: Dict[str, dict] = {}
        self.render_type: Optional[str] = None
        self.gui_light: Optional[str] = None
        self.builtin: Optional[str] = None
        self.loader: Optional[str] = None
        self.overrides: List[dict] = []


def normalise_ref(ref: str) -> str:
    return ref if ":" in ref else "minecraft:" + ref


def resolve_model(assets: Assets, ref: str, max_depth: int = 16) -> Model:
    """Walk the parent chain.  The child wins everywhere; ``elements`` comes from the nearest
    ancestor that declares any, because vanilla does not merge element lists."""
    ref = normalise_ref(ref)
    out = Model(ref)
    seen = set()
    current: Optional[str] = ref
    while current:
        if current in seen:
            raise ModelError("parent cycle: %s -> %s" % (" -> ".join(out.chain), current))
        seen.add(current)
        out.chain.append(current)
        if len(out.chain) > max_depth:
            raise ModelError("parent chain deeper than %d: %s" % (max_depth, " -> ".join(out.chain)))

        _, _, path = current.partition(":")
        if path.startswith("builtin/"):
            out.builtin = path.split("/", 1)[1]
            break

        source = assets.locate(current, "models", ".json")
        if not assets.exists(source):
            parent_of = out.chain[-2] if len(out.chain) > 1 else ref
            raise ModelError("model %r referenced from %r does not exist (looked for %s)"
                             % (current, parent_of, source))
        data = assets.read_json(source, "model " + current)
        if len(out.chain) == 1:
            out.overrides = list(data.get("overrides") or [])
        for key, value in (data.get("textures") or {}).items():
            out.textures.setdefault(key, value)
        if out.elements is None and "elements" in data:
            if not isinstance(data["elements"], list):
                raise ModelError("model %s: 'elements' must be a list" % current)
            out.elements = data["elements"]
        for slot, transform in (data.get("display") or {}).items():
            merged = dict(transform)
            merged.update(out.display.get(slot, {}))       # the child's values already present win
            out.display[slot] = merged
        if out.render_type is None:
            out.render_type = data.get("render_type")
        if out.gui_light is None:
            out.gui_light = data.get("gui_light")
        if out.loader is None:
            out.loader = data.get("loader")
        parent = data.get("parent")
        current = normalise_ref(parent) if parent else None
    return out


def resolve_texture_ref(model: Model, key: str, where: str, max_depth: int = 8) -> str:
    """Chase ``#var`` indirection down to a concrete ``ns:path``."""
    seen: List[str] = []
    current = key
    for _ in range(max_depth):
        if current.startswith("#"):
            current = current[1:]
        if current in seen:
            raise ModelError("%s: texture variable cycle %s -> #%s in %s"
                             % (where, " -> ".join(seen), current, model.ref))
        seen.append(current)
        if current not in model.textures:
            known = ", ".join("#" + k for k in sorted(model.textures)) or "(none)"
            raise ModelError("%s: model %s uses #%s but never defines it (chain %s; defined: %s)"
                             % (where, model.ref, current, " -> ".join(model.chain), known))
        value = model.textures[current]
        if not value.startswith("#"):
            return normalise_ref(value)
        current = value
    raise ModelError("%s: texture variable nested deeper than %d in %s" % (where, max_depth, model.ref))


# -------------------------------------------------------------------------------------------
# textures
# -------------------------------------------------------------------------------------------

class Texture:
    """An RGBA sprite flattened to bytes, plus a per-shade premultiplied colour table."""

    __slots__ = ("ref", "w", "h", "data", "has_partial", "_shaded")

    def __init__(self, image: Image.Image, cutout: bool, ref: str) -> None:
        img = image.convert("RGBA")
        self.ref = ref
        self.w, self.h = img.size
        data = bytearray(img.tobytes())
        self.has_partial = False
        if cutout:
            for i in range(3, len(data), 4):            # vanilla's alpha test
                data[i] = 255 if data[i] >= ALPHA_CUTOUT else 0
        else:
            for i in range(3, len(data), 4):
                if 0 < data[i] < 255:
                    self.has_partial = True
                    break
        self.data = bytes(data)
        self._shaded: Dict[float, bytes] = {}

    def shaded(self, shade: float) -> bytes:
        """RGB pre-multiplied by the face shading, so the inner loop does no arithmetic."""
        table = self._shaded.get(shade)
        if table is None:
            if shade >= 0.999:
                table = self.data
            else:
                out = bytearray(self.data)
                lut = bytes(min(255, int(v * shade + 0.5)) for v in range(256))
                for i in range(0, len(out), 4):
                    out[i] = lut[out[i]]
                    out[i + 1] = lut[out[i + 1]]
                    out[i + 2] = lut[out[i + 2]]
                table = bytes(out)
            self._shaded[shade] = table
        return table


class TextureCache:
    def __init__(self, assets: Assets, log: Log) -> None:
        self.assets, self.log = assets, log
        self._cache: Dict[Tuple[str, bool], Texture] = {}
        self._sources: Dict[Tuple[str, bool], List[str]] = {}

    def get(self, ref: str, cutout: bool) -> Texture:
        ref = normalise_ref(ref)
        key = (ref, cutout)
        if key in self._cache:
            for source in self._sources[key]:           # keep --check hashes honest on cache hits
                self.assets.read(source)
            return self._cache[key]

        source = self.assets.locate(ref, "textures", ".png")
        if not self.assets.exists(source):
            raise ModelError("texture %r does not exist (looked for %s)" % (ref, source))
        used = [source]
        raw = self.assets.read(source)
        try:
            image = Image.open(io.BytesIO(raw))
            image.load()
        except Exception as exc:                        # noqa: BLE001 - want the file name shown
            raise ModelError("texture %r cannot be opened (%s): %s" % (ref, source, exc)) from exc

        meta_source = source + ".mcmeta"
        if self.assets.exists(meta_source):
            used.append(meta_source)
            meta = self.assets.read_json(meta_source, "texture metadata for " + ref)
            image = self._first_frame(ref, image, meta.get("animation"))
        elif image.height > image.width > 0 and image.height % image.width == 0:
            # an animation strip whose .mcmeta we cannot see (vanilla's sea_lantern): frame 0
            self.log.debug("%s: %dx%d strip without visible mcmeta, using frame 0"
                           % (ref, image.width, image.height))
            image = image.crop((0, 0, image.width, image.width))

        texture = Texture(image, cutout, ref)
        self._cache[key] = texture
        self._sources[key] = used
        return texture

    def _first_frame(self, ref: str, image: Image.Image, anim: Optional[dict]) -> Image.Image:
        if not anim:
            return image
        fw = int(anim.get("width", image.width))
        fh = int(anim.get("height", fw))
        if fw <= 0 or fh <= 0 or fw > image.width or fh > image.height:
            raise ModelError("texture %r: animation frame %dx%d does not fit the %dx%d strip"
                             % (ref, fw, fh, image.width, image.height))
        frames = anim.get("frames")
        index = 0
        if frames:
            first = frames[0]
            index = int(first.get("index", 0)) if isinstance(first, dict) else int(first)
        per_row = max(1, image.width // fw)
        col, row = index % per_row, index // per_row
        box = (col * fw, row * fh, col * fw + fw, row * fh + fh)
        if box[2] > image.width or box[3] > image.height:
            raise ModelError("texture %r: animation frame %d is outside the strip" % (ref, index))
        self.log.debug("%s: animated, using frame %d (%dx%d)" % (ref, index, fw, fh))
        return image.crop(box)


# -------------------------------------------------------------------------------------------
# geometry.  ONE corner table (FACE_VERTEX_INFO) and ONE default-UV function (duv_at); the rect
# form default_uv() is derived from them, so the two can never drift apart.  --selftest proves it.
# -------------------------------------------------------------------------------------------

def face_corners(face: str, box: Sequence[float]) -> List[Tuple[float, float, float]]:
    """Model-space corners of ``face`` in UV-corner order [(u1,v1), (u1,v2), (u2,v2), (u2,v1)].

    Read straight off [FaceInfo]: ``box`` is (x1, y1, z1, x2, y2, z2) and a table entry (end, axis)
    picks ``box[axis + 3*end]``.
    """
    try:
        info = FACE_VERTEX_INFO[face]
    except KeyError:
        raise ModelError("unknown face %r (expected one of %s)" % (face, ", ".join(FACES))) from None
    return [tuple(box[axis + 3 * end] for end, axis in vertex) for vertex in info]  # type: ignore


def duv_at(face: str, point: Sequence[float]) -> Tuple[float, float]:
    """[BlockElement#uvsByFace] written as a map from a model-space point to (u, v)."""
    x, y, z = point
    if face == "down":
        return x, 16.0 - z
    if face == "up":
        return x, z
    if face == "north":
        return 16.0 - x, 16.0 - y
    if face == "south":
        return x, 16.0 - y
    if face == "west":
        return z, 16.0 - y
    if face == "east":
        return 16.0 - z, 16.0 - y
    raise ModelError("unknown face %r" % face)


def default_uv(face: str, box: Sequence[float]) -> List[float]:
    """The ``uv`` rect vanilla fills in for a face that omits one - derived, never written twice.

    Corner 0 carries (u1, v1) and corner 2 carries (u2, v2) [BlockFaceUV#getU/getV], so evaluating
    duv_at() at those two corners *is* the rect.
    """
    corners = face_corners(face, box)
    u1, v1 = duv_at(face, corners[0])
    u2, v2 = duv_at(face, corners[2])
    return [u1, v1, u2, v2]


def assert_uv_tables_agree() -> None:
    """The startup guard: for every face the corner table and duv_at must reproduce the identity
    mapping [(u1,v1), (u1,v2), (u2,v2), (u2,v1)].  A drift between exactly these two is what
    produces a silently mirrored face."""
    box = (1.0, 2.0, 3.0, 11.0, 13.0, 15.0)            # deliberately asymmetric on all three axes
    for face in FACES:
        corners = face_corners(face, box)
        u1, v1, u2, v2 = default_uv(face, box)
        want = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
        got = [duv_at(face, corner) for corner in corners]
        for index, (a, b) in enumerate(zip(got, want)):
            if abs(a[0] - b[0]) > 1e-9 or abs(a[1] - b[1]) > 1e-9:
                raise ModelError("UV table drift on face %r corner %d: duv_at gives %s, the "
                                 "default rect wants %s" % (face, index, a, b))


def element_rotation_fn(rot: Optional[dict], where: str):
    """[FaceBakery#applyElementRotation]: move the origin to zero, rotate about the axis, scale the
    two perpendicular axes by 1/|cos(angle)| when ``rescale`` is set, move back."""
    if not rot:
        return None
    try:
        origin = [float(v) for v in rot["origin"]]
        axis = str(rot["axis"]).lower()
        angle = float(rot["angle"])
    except (KeyError, TypeError, ValueError) as exc:
        raise ModelError("%s: malformed element rotation %r (%s)" % (where, rot, exc)) from exc
    if axis not in ("x", "y", "z"):
        raise ModelError("%s: rotation axis must be x, y or z, got %r" % (where, axis))
    if not any(abs(angle - a) < 1e-6 for a in VALID_ROT_ANGLES):
        raise ModelError("%s: rotation angle %s is not one of %s"
                         % (where, angle, ", ".join(str(a) for a in VALID_ROT_ANGLES)))
    rad = math.radians(angle)
    cos_a, sin_a = math.cos(rad), math.sin(rad)
    factor = 1.0
    if rot.get("rescale") and abs(cos_a) > 1e-6:
        factor = 1.0 / abs(cos_a)
    sx, sy, sz = {"x": (1.0, factor, factor), "y": (factor, 1.0, factor),
                  "z": (factor, factor, 1.0)}[axis]
    ox, oy, oz = origin

    def apply(point: Tuple[float, float, float]) -> Tuple[float, float, float]:
        px, py, pz = point[0] - ox, point[1] - oy, point[2] - oz
        if axis == "x":
            py, pz = py * cos_a - pz * sin_a, py * sin_a + pz * cos_a
        elif axis == "y":
            px, pz = px * cos_a + pz * sin_a, -px * sin_a + pz * cos_a
        else:
            px, py = px * cos_a - py * sin_a, px * sin_a + py * cos_a
        return (px * sx + ox, py * sy + oy, pz * sz + oz)

    return apply


class Quad:
    """One textured rectangle in model space, ready to project."""

    __slots__ = ("pts", "uv", "tex", "shade", "normal", "depth", "face")

    def __init__(self, pts, uv, tex, shade, normal, face) -> None:
        self.pts = pts            # four model-space corners, in UV-corner order
        self.uv = uv              # [u1, v1, u2, v2] in the 0..16 face space
        self.tex = tex
        self.shade = shade
        self.normal = normal
        self.face = face
        self.depth = 0.0


def quad_normal(pts) -> Tuple[float, float, float]:
    ax = tuple(pts[1][i] - pts[0][i] for i in range(3))
    bx = tuple(pts[2][i] - pts[1][i] for i in range(3))
    nx = ax[1] * bx[2] - ax[2] * bx[1]
    ny = ax[2] * bx[0] - ax[0] * bx[2]
    nz = ax[0] * bx[1] - ax[1] * bx[0]
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    return (0.0, 0.0, 0.0) if length < 1e-9 else (nx / length, ny / length, nz / length)


def nearest_face(normal) -> str:
    best, best_dot = "up", -2.0
    for name, vec in NORMALS.items():
        dot = normal[0] * vec[0] + normal[1] * vec[1] + normal[2] * vec[2]
        if dot > best_dot:
            best, best_dot = name, dot
    return best


def build_quads(model: Model, textures: TextureCache, log: Log, name: str) -> Tuple[List[Quad], str]:
    if model.loader:
        raise NotRenderable("model %s uses the custom loader %r - the geometry lives outside the "
                            "JSON (an .obj shipped by another mod), not in vanilla elements"
                            % (model.ref, model.loader))
    if not model.elements:
        raise NotRenderable("model %s has no elements anywhere in its parent chain (%s); it is a "
                            "template other models inherit from, not geometry"
                            % (model.ref, " -> ".join(model.chain)))

    mode = (model.render_type or "minecraft:solid").split(":")[-1]
    if mode not in ("solid", "cutout", "cutout_mipped", "translucent"):
        log.warn("%s: unknown render_type %r, treating it as translucent" % (name, model.render_type))
        mode = "translucent"
    cutout = mode != "translucent"

    quads: List[Quad] = []
    for index, element in enumerate(model.elements):
        where = "%s: %s element %d" % (name, model.ref, index)
        if not isinstance(element, dict) or "from" not in element or "to" not in element:
            raise ModelError("%s: element needs 'from' and 'to'" % where)
        frm = [float(v) for v in element["from"]]
        to = [float(v) for v in element["to"]]
        if len(frm) != 3 or len(to) != 3:
            raise ModelError("%s: 'from'/'to' must be three numbers each" % where)
        box = (min(frm[0], to[0]), min(frm[1], to[1]), min(frm[2], to[2]),
               max(frm[0], to[0]), max(frm[1], to[1]), max(frm[2], to[2]))
        rotate = element_rotation_fn(element.get("rotation"), where)
        shaded = bool(element.get("shade", True))
        faces = element.get("faces") or {}
        if not isinstance(faces, dict) or not faces:
            log.warn("%s: element has no faces and is therefore invisible" % where)
            continue

        for face, spec in faces.items():
            if face not in FACES:
                raise ModelError("%s: unknown face %r" % (where, face))
            if not isinstance(spec, dict) or "texture" not in spec:
                raise ModelError("%s: face %r needs a 'texture'" % (where, face))
            # cullface is ignored on purpose: it only hides a face when a neighbouring block
            # covers it, and an item floating in a slot has no neighbours.
            tex_ref = resolve_texture_ref(model, spec["texture"], "%s face %r" % (where, face))
            tex = textures.get(tex_ref, cutout)
            if "tintindex" in spec:
                raise ModelError("%s: face %r has tintindex %s, but this renderer has no block "
                                 "tint source; it would be drawn in the wrong colour"
                                 % (where, face, spec["tintindex"]))

            pts = face_corners(face, box)
            if rotate:
                pts = [rotate(p) for p in pts]
            uv = [float(v) for v in spec.get("uv", default_uv(face, box))]
            if len(uv) != 4:
                raise ModelError("%s: face %r uv needs four numbers, got %r" % (where, face, uv))
            steps = int(spec.get("rotation", 0) or 0)
            if steps % 90:
                raise ModelError("%s: face %r rotation %s is not a multiple of 90"
                                 % (where, face, steps))
            steps = (steps // 90) % 4
            if steps:
                # [BlockFaceUV#getShiftedIndex]: quad corner i receives UV corner (i+steps)%4, so
                # the slot that carries UV corner j holds the position of corner (j-steps)%4.
                pts = [pts[(j - steps) % 4] for j in range(4)]
            normal = quad_normal(pts)
            if max(abs(v) for v in normal) < 1e-9:
                log.debug("%s: face %r is degenerate, skipped" % (where, face))
                continue
            quads.append(Quad(pts, uv, tex, SHADE[nearest_face(normal)] if shaded else 1.0,
                              normal, face))
    if not quads:
        raise ModelError("%s: model %s produced no drawable faces" % (name, model.ref))
    return quads, mode


# -------------------------------------------------------------------------------------------
# projection
# -------------------------------------------------------------------------------------------

def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rotation_matrix(rx_deg: float, ry_deg: float, rz_deg: float) -> List[List[float]]:
    """JOML ``rotationXYZ`` = Rx * Ry * Rz: a vector is spun around z, then y, then x."""
    rx, ry, rz = math.radians(rx_deg), math.radians(ry_deg), math.radians(rz_deg)
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(mx, mat_mul(my, mz))


class Projection:
    """Model space (0..16) -> screen pixels + depth.  Orthographic, hence affine."""

    def __init__(self, size: int, rotation, translation, scale: float, frame: Optional[float]) -> None:
        self.mat = rotation_matrix(*rotation)
        self.size = size
        if frame is None:
            # vanilla: size * display.scale / 16 output pixels per model unit.
            self.unit = size * scale / 16.0
        else:
            # "a full 16^3 cube fills this fraction of the frame, measured on its height".
            cube_height = sum(abs(v) for v in self.mat[1])
            self.unit = frame * size / (16.0 * cube_height)
        self.off_x = size / 2.0 + translation[0] * size / 16.0
        self.off_y = size / 2.0 - translation[1] * size / 16.0
        self.off_d = translation[2] * size / 16.0

    def project(self, point):
        cx, cy, cz = point[0] - 8.0, point[1] - 8.0, point[2] - 8.0
        mat, unit = self.mat, self.unit
        rx = mat[0][0] * cx + mat[0][1] * cy + mat[0][2] * cz
        ry = mat[1][0] * cx + mat[1][1] * cy + mat[1][2] * cz
        rz = mat[2][0] * cx + mat[2][1] * cy + mat[2][2] * cz
        return (self.off_x + rx * unit, self.off_y - ry * unit, self.off_d + rz * unit)

    def depth_of_normal(self, normal) -> float:
        mat = self.mat
        return mat[2][0] * normal[0] + mat[2][1] * normal[1] + mat[2][2] * normal[2]


# -------------------------------------------------------------------------------------------
# rasteriser
# -------------------------------------------------------------------------------------------

class Canvas:
    """Supersampled premultiplied RGBA buffer with a depth buffer."""

    def __init__(self, size: int) -> None:
        self.size = size
        count = size * size
        self.r = [0.0] * count
        self.g = [0.0] * count
        self.b = [0.0] * count
        self.a = [0.0] * count
        self.z = [-1e30] * count

    def draw(self, corners, uv, tex: Texture, shade: float, translucent_pass: bool) -> None:
        """``corners``: the four projected (x, y, depth) tuples in UV-corner order."""
        ox, oy, od = corners[0]
        # Es points at corner 3 = (u2,v1); Et points at corner 1 = (u1,v2).
        esx, esy, esd = corners[3][0] - ox, corners[3][1] - oy, corners[3][2] - od
        etx, ety, etd = corners[1][0] - ox, corners[1][1] - oy, corners[1][2] - od
        det = esx * ety - etx * esy
        if abs(det) < 1e-9:
            return                                     # edge-on or zero area on screen
        inv = 1.0 / det

        size = self.size
        ys = [c[1] for c in corners]
        xs = [c[0] for c in corners]
        y_lo = max(0, int(math.floor(min(ys) - 0.5)) + 1)
        y_hi = min(size - 1, int(math.ceil(max(ys) - 0.5)))
        x_lo_all = max(0, int(math.floor(min(xs) - 0.5)) + 1)
        x_hi_all = min(size - 1, int(math.ceil(max(xs) - 0.5)))
        if y_lo > y_hi or x_lo_all > x_hi_all:
            return

        ds_dx, ds_dy = ety * inv, -etx * inv
        dt_dx, dt_dy = -esy * inv, esx * inv

        tw, th = tex.w, tex.h
        u1, v1, u2, v2 = uv
        tu0, tdu = u1 * tw / 16.0, (u2 - u1) * tw / 16.0
        tv0, tdv = v1 * th / 16.0, (v2 - v1) * th / 16.0
        data = tex.shaded(shade)
        raw_alpha = tex.data
        buf_r, buf_g, buf_b, buf_a, buf_z = self.r, self.g, self.b, self.a, self.z
        max_i, max_j = tw - 1, th - 1

        for py in range(y_lo, y_hi + 1):
            dy = (py + 0.5) - oy
            s_at0 = ds_dy * dy - ds_dx * ox
            t_at0 = dt_dy * dy - dt_dx * ox
            # s(x) = ds_dx*(x+0.5) + s_at0; solve 0 <= s <= 1 and 0 <= t <= 1 for x exactly, so
            # the loop below never touches a pixel outside the quad.
            x_lo, x_hi = float(x_lo_all), float(x_hi_all)
            outside = False
            for grad, base in ((ds_dx, s_at0), (dt_dx, t_at0)):
                if abs(grad) < 1e-12:
                    if base < -1e-9 or base > 1.0 + 1e-9:
                        outside = True
                        break
                    continue
                lim_a = (0.0 - base) / grad - 0.5
                lim_b = (1.0 - base) / grad - 0.5
                if lim_a > lim_b:
                    lim_a, lim_b = lim_b, lim_a
                x_lo = max(x_lo, math.ceil(lim_a - 1e-9))
                x_hi = min(x_hi, math.floor(lim_b + 1e-9))
            if outside:
                continue
            px_lo, px_hi = int(x_lo), int(x_hi)
            if px_lo > px_hi:
                continue

            s = ds_dx * (px_lo + 0.5) + s_at0
            t = dt_dx * (px_lo + 0.5) + t_at0
            tu, tv = tu0 + s * tdu, tv0 + t * tdv
            dtu, dtv = ds_dx * tdu, dt_dx * tdv
            depth = od + s * esd + t * etd
            ddepth = ds_dx * esd + dt_dx * etd
            idx = py * size + px_lo

            for _ in range(px_hi - px_lo + 1):
                tex_i = int(tu)
                tex_j = int(tv)
                if tex_i < 0:
                    tex_i = 0
                elif tex_i > max_i:
                    tex_i = max_i
                if tex_j < 0:
                    tex_j = 0
                elif tex_j > max_j:
                    tex_j = max_j
                off = (tex_j * tw + tex_i) << 2
                alpha = raw_alpha[off + 3]
                if alpha:
                    if translucent_pass:
                        if alpha < 255 and depth > buf_z[idx]:
                            src = alpha / 255.0
                            keep = 1.0 - src
                            buf_r[idx] = buf_r[idx] * keep + data[off] * src
                            buf_g[idx] = buf_g[idx] * keep + data[off + 1] * src
                            buf_b[idx] = buf_b[idx] * keep + data[off + 2] * src
                            buf_a[idx] = buf_a[idx] * keep + src
                    elif alpha == 255 and depth > buf_z[idx]:
                        buf_z[idx] = depth
                        buf_r[idx] = data[off]
                        buf_g[idx] = data[off + 1]
                        buf_b[idx] = data[off + 2]
                        buf_a[idx] = 1.0
                idx += 1
                tu += dtu
                tv += dtv
                depth += ddepth

    def to_image(self, target: int) -> Image.Image:
        """Box-filter the premultiplied buffer down to ``target`` and un-premultiply.

        The average is done here in floating point rather than through Image.resize: Pillow's BOX
        filter works on 8 bit channels, and dividing that rounding error back out by a small alpha
        turns a 1/255 slip into a visible colour shift on an antialiased edge.
        """
        size = self.size
        if size % target:
            raise ModelError("internal: supersampled size %d is not a multiple of %d" % (size, target))
        step = size // target
        r, g, b, a = self.r, self.g, self.b, self.a
        buf = bytearray(target * target * 4)
        inv_cells = 1.0 / (step * step)
        for oy in range(target):
            row_base = oy * step
            for ox in range(target):
                sr = sg = sb = sa = 0.0
                col_base = ox * step
                for dy in range(step):
                    base = (row_base + dy) * size + col_base
                    for dx in range(step):
                        i = base + dx
                        alpha = a[i]
                        if alpha:
                            sr += r[i]
                            sg += g[i]
                            sb += b[i]
                            sa += alpha
                if sa <= 0.0:
                    continue
                out = (oy * target + ox) << 2
                scale = 1.0 / sa                       # un-premultiply in floating point
                buf[out] = min(255, int(sr * scale + 0.5))
                buf[out + 1] = min(255, int(sg * scale + 0.5))
                buf[out + 2] = min(255, int(sb * scale + 0.5))
                alpha = sa * inv_cells
                buf[out + 3] = 255 if alpha >= 1.0 else int(alpha * 255.0 + 0.5)
        return Image.frombytes("RGBA", (target, target), bytes(buf))


class RenderConfig:
    __slots__ = ("size", "ssaa", "frame", "anchor", "cull")

    def __init__(self, size: int, ssaa: int, frame: Optional[float], anchor: str,
                 cull: bool = True) -> None:
        self.size, self.ssaa, self.frame, self.anchor, self.cull = size, ssaa, frame, anchor, cull

    def fingerprint(self) -> str:
        return ("renderer=%s size=%d ssaa=%d frame=%s anchor=%s cull=%s shade=flat"
                % (RENDERER_VERSION, self.size, self.ssaa,
                   "vanilla" if self.frame is None else ("%.6g" % self.frame), self.anchor, self.cull))


def project_quads(model: Model, quads: Sequence[Quad], cfg: RenderConfig, log: Log, name: str):
    """Project, cull and sort.  Returns (projection, [(quad, screen corners)], big canvas size)."""
    gui = model.display.get("gui") or {}
    rotation = gui.get("rotation", [30, 225, 0])
    translation = gui.get("translation", [0, 0, 0])
    scale = gui.get("scale", 0.625)
    scale = float(scale[0]) if isinstance(scale, (list, tuple)) else float(scale)

    big = cfg.size * cfg.ssaa
    proj = Projection(big, rotation, translation, scale, cfg.frame)

    visible = []
    for quad in quads:
        if cfg.cull and proj.depth_of_normal(quad.normal) <= 1e-6:
            continue
        pts = [proj.project(p) for p in quad.pts]
        quad.depth = sum(p[2] for p in pts) / 4.0
        visible.append((quad, pts))
    if not visible:
        raise ModelError("%s: every face of %s points away from the camera - nothing would be "
                         "drawn (try --no-cull to see what is going on)" % (name, model.ref))
    visible.sort(key=lambda item: item[0].depth)       # painter's order: farthest first

    xs = [p[0] for _, pts in visible for p in pts]
    ys = [p[1] for _, pts in visible for p in pts]
    if cfg.anchor == "model":
        # Re-centre on this model's own silhouette.  Nicer for a doc thumbnail, but it breaks the
        # shared ground plane, so it is never the default.
        dx = big / 2.0 - (min(xs) + max(xs)) / 2.0
        dy = big / 2.0 - (min(ys) + max(ys)) / 2.0
        visible = [(q, [(p[0] + dx, p[1] + dy, p[2]) for p in pts]) for q, pts in visible]
        xs = [x + dx for x in xs]
        ys = [y + dy for y in ys]
    if min(xs) < -0.5 or min(ys) < -0.5 or max(xs) > big + 0.5 or max(ys) > big + 0.5:
        log.warn("%s: geometry reaches outside the frame (x %.1f..%.1f, y %.1f..%.1f of 0..%d) and "
                 "will be clipped" % (name, min(xs) / cfg.ssaa, max(xs) / cfg.ssaa,
                                      min(ys) / cfg.ssaa, max(ys) / cfg.ssaa, cfg.size))
    return proj, visible, big


def render_3d(name: str, model: Model, textures: TextureCache, cfg: RenderConfig,
              log: Log) -> Image.Image:
    quads, mode = build_quads(model, textures, log, name)
    _, visible, big = project_quads(model, quads, cfg, log, name)
    canvas = Canvas(big)
    for quad, pts in visible:
        canvas.draw(pts, quad.uv, quad.tex, quad.shade, False)
    if mode == "translucent":
        for quad, pts in visible:
            if quad.tex.has_partial:
                canvas.draw(pts, quad.uv, quad.tex, quad.shade, True)
    log.debug("%s: %d faces, %d front-facing, render_type=%s" % (name, len(quads), len(visible), mode))
    return canvas.to_image(cfg.size)


# -------------------------------------------------------------------------------------------
# flat items
# -------------------------------------------------------------------------------------------

def tint_image(img: Image.Image, colour: int) -> Image.Image:
    """Vanilla tinting: the vertex colour multiplies the texel, alpha untouched."""
    red, green, blue = (colour >> 16) & 255, (colour >> 8) & 255, colour & 255
    bands = list(img.convert("RGBA").split())
    for index, multiplier in enumerate((red, green, blue)):
        lut = [min(255, int(v * multiplier / 255.0 + 0.5)) for v in range(256)]
        bands[index] = bands[index].point(lut)
    return Image.merge("RGBA", bands)


def render_flat(name: str, model: Model, textures: TextureCache, cfg: RenderConfig,
                log: Log) -> Image.Image:
    layers = sorted((key for key in model.textures if key.startswith("layer")
                     and key[5:].isdigit()), key=lambda key: int(key[5:]))
    if not layers:
        raise ModelError("%s: model %s inherits item/generated but defines no layer0 (defined: %s)"
                         % (name, model.ref, sorted(model.textures) or "nothing"))

    tints = ITEM_TINTS.get(name)
    if tints is None and any(ref in TINT_FROM_JAVA_PARENTS for ref in model.chain):
        raise ModelError(
            "%s: its parent chain contains a template that vanilla tints from Java (e.g. "
            "item/template_spawn_egg, where DeferredSpawnEggItem carries the two colours). "
            "Rendering it untinted would be wrong - add the colours to ITEM_TINTS in this script; "
            "grep the Java source for the item's registration." % name)
    if tints is not None and len(tints) < len(layers):
        log.warn("%s: %d layers but only %d tint colours; the rest is drawn untinted"
                 % (name, len(layers), len(tints)))

    loaded = [textures.get(resolve_texture_ref(model, key, "%s %s" % (name, key)), False)
              for key in layers]
    width = max(tex.w for tex in loaded)
    height = max(tex.h for tex in loaded)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for index, tex in enumerate(loaded):
        layer = Image.frombytes("RGBA", (tex.w, tex.h), tex.data)
        if (tex.w, tex.h) != (width, height):
            log.warn("%s: layer%d is %dx%d but the widest layer is %dx%d; scaling it up "
                     "nearest-neighbour" % (name, index, tex.w, tex.h, width, height))
            layer = layer.resize((width, height), Image.NEAREST)
        if tints is not None and index < len(tints):
            layer = tint_image(layer, tints[index])
        canvas.alpha_composite(layer)
    if cfg.size % width or cfg.size % height:
        log.warn("%s: %dx%d does not divide %dpx evenly, so the upscale cannot keep every texel "
                 "the same size" % (name, width, height, cfg.size))
    return canvas.resize((cfg.size, cfg.size), Image.NEAREST)


# -------------------------------------------------------------------------------------------
# what to render.  Entries are discovered from the assets, never hard-coded.
# -------------------------------------------------------------------------------------------

class Entry:
    __slots__ = ("name", "model_ref", "subdir", "required")

    def __init__(self, name: str, model_ref: str, subdir: str, required: bool = True) -> None:
        self.name, self.model_ref, self.subdir, self.required = name, model_ref, subdir, required

    @property
    def rel_path(self) -> str:
        return "%s/%s.png" % (self.subdir, self.name)


def discover_entries(assets: Assets, want_variants: bool, log: Log) -> List[Entry]:
    """Every item model, minus the ones another item model only reaches through ``overrides``.

    That is what removes the 31 ``world_compass_NN`` needle frames - structurally, from the data,
    rather than through a name pattern that rots the day a new predicate model appears.  A model
    that lists *itself* (world_compass does, for angle 0.0) of course stays.
    """
    item_dir = os.path.join(assets.root, "models", "item")
    if not os.path.isdir(item_dir):
        raise ModelError("no item models directory at %s" % item_dir)
    stems = sorted(f[:-5] for f in os.listdir(item_dir) if f.endswith(".json"))

    override_targets: Dict[str, str] = {}
    for stem in stems:
        data = assets.read_json(assets.locate("%s:item/%s" % (MODID, stem), "models", ".json"),
                                "item model " + stem)
        for override in data.get("overrides") or []:
            target = str(override.get("model", "")).rpartition("/")[2]
            if target and target != stem:
                override_targets.setdefault(target, stem)

    entries: List[Entry] = []
    block_models_used = set()
    for stem in stems:
        if stem in override_targets:
            continue
        ref = "%s:item/%s" % (MODID, stem)
        subdir = BLOCK_DIR
        try:
            model = resolve_model(assets, ref)
            if model.builtin == "generated" or (model.elements is None and model.textures.get("layer0")):
                subdir = ITEM_DIR
            for parent in model.chain:
                if parent.startswith("%s:block/" % MODID):
                    block_models_used.add(parent.partition(":block/")[2])
        except ModelError:
            pass                                       # the render will report it properly
        entries.append(Entry(stem, ref, subdir))
    log.debug("%d item models, %d only reachable through overrides -> %d entries"
              % (len(stems), len(stems) - len(entries), len(entries)))

    if want_variants:
        taken = {entry.rel_path for entry in entries}
        block_dir = os.path.join(assets.root, "models", "block")
        extra: List[Entry] = []
        for root, _, files in os.walk(block_dir):
            rel = os.path.relpath(root, block_dir)
            for filename in sorted(files):
                if not filename.endswith(".json"):
                    continue
                stem = filename[:-5]
                path = stem if rel == "." else "%s/%s" % (rel.replace(os.sep, "/"), stem)
                if path in block_models_used:
                    continue                           # an item already renders this one
                candidate = Entry(path.replace("/", "__"), "%s:block/%s" % (MODID, path),
                                  BLOCK_DIR, required=False)
                if candidate.rel_path in taken:
                    # e.g. block/end_conduit next to the item of the same name: one output path,
                    # one entry.  The item wins, because its skip reason is the informative one.
                    log.debug("%s is already produced by an item entry, not queued again"
                              % candidate.rel_path)
                    continue
                taken.add(candidate.rel_path)
                extra.append(candidate)
        extra.sort(key=lambda entry: entry.name)
        entries.extend(extra)
        log.debug("%d block models no item points at" % len(extra))
    return entries


def render_entry(entry: Entry, assets: Assets, textures: TextureCache, cfg: RenderConfig,
                 log: Log, dry: bool = False):
    """Returns (image, [(source, sha256)], skip reason).  The image is None when skipped or dry."""
    assets.begin_entry()
    try:
        reason = UNRENDERABLE.get(entry.name)
        if reason:
            return None, assets.end_entry(), reason
        model = resolve_model(assets, entry.model_ref)
        if model.builtin and model.builtin != "generated":
            raise NotRenderable("model %s inherits builtin/%s: it is drawn from Java code, no "
                                "JSON anywhere holds its geometry" % (model.ref, model.builtin))
        if entry.subdir == ITEM_DIR:
            image = render_flat(entry.name, model, textures, cfg, log)
        elif dry:
            build_quads(model, textures, log, entry.name)      # validate without paying for pixels
            image = None
        else:
            image = render_3d(entry.name, model, textures, cfg, log)
        return image, assets.end_entry(), None
    except NotRenderable as exc:
        if entry.required and entry.name not in UNRENDERABLE:
            raise
        return None, assets.end_entry(), str(exc)
    finally:
        if assets._touched is not None:                # a raise must not leak the tracking state
            assets.end_entry()


# -------------------------------------------------------------------------------------------
# manifest and --check
# -------------------------------------------------------------------------------------------

def digest_of(sources: Sequence[Tuple[str, str]], cfg: RenderConfig) -> str:
    sha = hashlib.sha256()
    sha.update(cfg.fingerprint().encode())
    for source, value in sources:
        sha.update(b"\0" + source.encode() + b"\0" + value.encode())
    return sha.hexdigest()


def file_sha(path: str) -> Optional[str]:
    try:
        with open(path, "rb") as handle:
            return hashlib.sha256(handle.read()).hexdigest()
    except OSError:
        return None


def coverage_of(image: Image.Image) -> float:
    histogram = image.getchannel("A").histogram()
    return sum(histogram[1:]) / float(image.width * image.height)


def load_manifest(out_dir: str) -> dict:
    path = os.path.join(out_dir, MANIFEST_NAME)
    if not os.path.isfile(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {}


def save_manifest(out_dir: str, manifest: dict) -> None:
    with open(os.path.join(out_dir, MANIFEST_NAME), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=1, sort_keys=True)
        handle.write("\n")


def print_skips(skips: List[Tuple[str, str]], log: Log) -> None:
    if not skips:
        return
    log.info("")
    log.info("NOT RENDERED (%d) - each one with the reason it cannot be derived:" % len(skips))
    width = max(len(name) for name, _ in skips)
    for name, reason in skips:
        first = True
        words, line = reason.split(), ""
        for word in words:
            if len(line) + len(word) + 1 > 92:
                log.info("  %-*s  %s" % (width, name if first else "", line))
                first, line = False, word
            else:
                line = (line + " " + word).strip()
        log.info("  %-*s  %s" % (width, name if first else "", line))


def run_check(entries: Sequence[Entry], assets: Assets, textures: TextureCache, cfg: RenderConfig,
              out_dir: str, log: Log, full_scan: bool) -> int:
    manifest = load_manifest(out_dir)
    records = manifest.get("entries", {})
    if manifest.get("config") != cfg.fingerprint():
        log.info("the manifest was written with a different configuration (%r) - everything "
                 "counts as stale" % manifest.get("config", "none at all"))
    state: Dict[str, List[str]] = {key: [] for key in
                                   ("ok", "stale", "missing", "modified", "unknown", "skipped",
                                    "failed", "orphan")}
    expected = {MANIFEST_NAME}
    skips: List[Tuple[str, str]] = []

    for entry in entries:
        try:
            _, sources, skipped = render_entry(entry, assets, textures, cfg, log, dry=True)
        except ModelError as exc:
            log.error(str(exc))
            state["failed"].append(entry.rel_path)
            continue
        if skipped:
            state["skipped"].append(entry.rel_path)
            skips.append((entry.rel_path, skipped))
            continue
        expected.add(entry.rel_path)
        png_path = os.path.join(out_dir, entry.rel_path)
        record = records.get(entry.rel_path)
        if record is None:
            state["unknown"].append(entry.rel_path)
        elif not os.path.isfile(png_path):
            state["missing"].append(entry.rel_path)
        elif record.get("inputs") != digest_of(sources, cfg):
            state["stale"].append(entry.rel_path)
        elif record.get("png") != file_sha(png_path):
            state["modified"].append(entry.rel_path)
        else:
            state["ok"].append(entry.rel_path)

    if full_scan:
        # only inside the two directories this script owns - --out may hold hand-made images
        for subdir in (ITEM_DIR, BLOCK_DIR):
            for root, _, files in os.walk(os.path.join(out_dir, subdir)):
                for filename in files:
                    rel = os.path.relpath(os.path.join(root, filename), out_dir).replace(os.sep, "/")
                    if rel not in expected:
                        state["orphan"].append(rel)
    else:
        log.info("(a partial run, so orphaned files are not reported)")

    log.info("")
    log.info("check: %d up to date, %d stale, %d missing, %d hand-modified, %d not in the "
             "manifest, %d orphaned, %d skipped, %d broken"
             % tuple(len(state[key]) for key in ("ok", "stale", "missing", "modified", "unknown",
                                                 "orphan", "skipped", "failed")))
    explain = [("stale", "a model or texture changed since the PNG was rendered"),
               ("missing", "in the manifest but the PNG is gone"),
               ("modified", "the PNG on disk is not the one this script wrote"),
               ("unknown", "no manifest record - never rendered"),
               ("orphan", "a file in the output tree that nothing here produces"),
               ("failed", "the model no longer resolves (see the errors above)")]
    for key, why in explain:
        for name in sorted(state[key]):
            log.info("  %-9s %s  (%s)" % (key.upper(), name, why))
    print_skips(skips, log)
    if any(state[key] for key, _ in explain):
        log.info("")
        log.info("the PNGs are NOT up to date - re-run without --check to regenerate them.")
        return 1
    log.info("every committed PNG is up to date with the models.")
    return 0


def run_render(entries: Sequence[Entry], assets: Assets, textures: TextureCache, cfg: RenderConfig,
               out_dir: str, log: Log, fail_fast: bool) -> int:
    manifest = load_manifest(out_dir)
    records: dict = manifest.get("entries", {}) if manifest.get("config") == cfg.fingerprint() else {}
    rendered = failed = 0
    skips: List[Tuple[str, str]] = []
    started = time.time()

    for entry in entries:
        path = os.path.join(out_dir, entry.rel_path)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        try:
            image, sources, skipped = render_entry(entry, assets, textures, cfg, log)
        except ModelError as exc:
            log.error(str(exc))
            records.pop(entry.rel_path, None)
            failed += 1
            if fail_fast:
                break
            continue
        if skipped is not None:
            records[entry.rel_path] = {"skipped": True, "reason": skipped}
            skips.append((entry.rel_path, skipped))
            continue
        if image is None or image.size != (cfg.size, cfg.size) or image.mode != "RGBA":
            log.error("%s: internal error, the renderer produced %s" % (entry.rel_path, image))
            failed += 1
            continue
        coverage = coverage_of(image)
        if coverage < MIN_COVERAGE:
            log.warn("%s: only %.2f%% of the image is opaque - the model is empty, fully "
                     "transparent or off frame; do not trust this PNG without looking at it"
                     % (entry.rel_path, coverage * 100))
        elif coverage < THIN_COVERAGE:
            log.warn("%s: covers only %.1f%% of the frame - flat geometry seen nearly edge on "
                     "renders as a correct but nearly useless sliver; consider a second angle for "
                     "documentation" % (entry.rel_path, coverage * 100))
        image.save(path)
        records[entry.rel_path] = {"inputs": digest_of(sources, cfg), "png": file_sha(path),
                                   "model": entry.model_ref,
                                   "sources": [source for source, _ in sources]}
        rendered += 1
        log.info("  %-46s %5.1f%% coverage" % (entry.rel_path, coverage * 100))

    save_manifest(out_dir, {"renderer": RENDERER_VERSION, "config": cfg.fingerprint(),
                            "entries": records})
    print_skips(skips, log)
    log.info("")
    log.info("rendered %d PNG(s), skipped %d, failed %d in %.1fs -> %s"
             % (rendered, len(skips), failed, time.time() - started, out_dir))
    return 2 if failed else 0


# -------------------------------------------------------------------------------------------
# --selftest: prove the two easy-to-break tables, and the compositing, on every run
# -------------------------------------------------------------------------------------------

def _reference_render(model: Model, textures: TextureCache, cfg: RenderConfig, log: Log,
                      name: str) -> Image.Image:
    """A second, deliberately slow renderer written from the Java sources a second time.

    It re-derives the UV corner assignment straight from [BlockFaceUV#getU/getV] and, instead of a
    depth buffer plus a painter's sort, collects every fragment per pixel and sorts them by depth -
    the only strictly correct compositing order.  Diffing the two therefore proves the two-pass
    blend, which is the part the fast path only claims is "exact for these models".

    It does NOT prove the corner table, because it consumes the same quads: a mirrored face would
    look identical to both.  That is exactly how a renderer with its own slow reference can still
    ship mirrored east faces.  Check 2 in run_selftest() is the instrument for that, and it works
    from [FaceInfo]'s facings rather than from the table under test.
    """
    quads, mode = build_quads(model, textures, log, name)
    _, visible, big = project_quads(model, quads, cfg, log, name)
    frags: List[Optional[List[Tuple[float, int, int, int, int]]]] = [None] * (big * big)

    for quad, pts in visible:
        # [BlockFaceUV#getU/getV] again, from scratch: corner i takes uvs[i in (0,1) ? 0 : 2] and
        # uvs[i in (0,3) ? 1 : 3].  The face rotation is already folded into `pts`.
        u1, v1, u2, v2 = quad.uv
        uvs = [(u1 if i in (0, 1) else u2, v1 if i in (0, 3) else v2) for i in range(4)]
        tex = quad.tex
        data = tex.shaded(quad.shade)
        raw = tex.data
        ox, oy, od = pts[0]
        esx, esy, esd = pts[3][0] - ox, pts[3][1] - oy, pts[3][2] - od
        etx, ety, etd = pts[1][0] - ox, pts[1][1] - oy, pts[1][2] - od
        det = esx * ety - etx * esy
        if abs(det) < 1e-9:
            continue
        inv = 1.0 / det
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        for py in range(max(0, int(min(ys))), min(big, int(max(ys)) + 2)):
            for px in range(max(0, int(min(xs))), min(big, int(max(xs)) + 2)):
                dx, dy = px + 0.5 - ox, py + 0.5 - oy
                s = (dx * ety - dy * etx) * inv
                t = (esx * dy - esy * dx) * inv
                if not (0.0 <= s <= 1.0 and 0.0 <= t <= 1.0):
                    continue
                u = uvs[0][0] + s * (uvs[3][0] - uvs[0][0]) + t * (uvs[1][0] - uvs[0][0])
                v = uvs[0][1] + s * (uvs[3][1] - uvs[0][1]) + t * (uvs[1][1] - uvs[0][1])
                i = min(tex.w - 1, max(0, int(u * tex.w / 16.0)))
                j = min(tex.h - 1, max(0, int(v * tex.h / 16.0)))
                off = (j * tex.w + i) << 2
                alpha = raw[off + 3]
                if not alpha:
                    continue
                if mode != "translucent":
                    alpha = 255
                cell = frags[py * big + px]
                value = (od + s * esd + t * etd, data[off], data[off + 1], data[off + 2], alpha)
                if cell is None:
                    frags[py * big + px] = [value]
                else:
                    cell.append(value)

    canvas = Canvas(big)
    for index, cell in enumerate(frags):
        if not cell:
            continue
        cell.sort(key=lambda frag: frag[0])            # far to near
        red = green = blue = acc = 0.0
        for _, tr, tg, tb, ta in cell:
            src = ta / 255.0
            keep = 1.0 - src
            red, green, blue = tr * src + red * keep, tg * src + green * keep, tb * src + blue * keep
            acc = src + acc * keep
        canvas.r[index], canvas.g[index], canvas.b[index], canvas.a[index] = red, green, blue, acc
    return canvas.to_image(cfg.size)


def _marker_model(rotation: int) -> Model:
    """A one-element cube whose south face carries a marker in a known texture corner."""
    model = Model("selftest:marker")
    model.chain = ["selftest:marker"]
    model.elements = [{"from": [0, 0, 0], "to": [16, 16, 16],
                       "faces": {face: {"texture": "#m", "rotation": rotation} for face in FACES}}]
    model.textures = {"m": "selftest:marker"}
    return model


def run_selftest(textures: TextureCache, assets: Assets, cfg: RenderConfig, log: Log) -> int:
    failures: List[str] = []

    # 1. the tables agree (this also runs at import time of every render)
    try:
        assert_uv_tables_agree()
        log.info("  ok   UV corner table and default-UV function reproduce the identity mapping")
    except ModelError as exc:
        failures.append(str(exc))

    # 2. the corner table really is [FaceInfo]: the four corners must lie on the face's own plane
    #    and wind so the cross product points outwards.
    box = (2.0, 3.0, 4.0, 12.0, 13.0, 14.0)
    for face in FACES:
        corners = face_corners(face, box)
        normal = quad_normal(corners)
        want = NORMALS[face]
        if sum(a * b for a, b in zip(normal, want)) < 0.999:
            failures.append("face %s: corner winding gives normal %s, expected %s"
                            % (face, normal, want))
    if not any(f.startswith("face ") for f in failures):
        log.info("  ok   all six faces wind outwards (normals match [FaceInfo] facings)")

    # 3. a synthetic marker lands where the projection maths predicts, for every UV rotation.
    marker = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    marker.putpixel((0, 0), (255, 0, 0, 255))          # (u,v) = (0,0), i.e. UV corner 1 of the rect
    textures._cache[("selftest:marker", True)] = Texture(marker, True, "selftest:marker")
    textures._sources[("selftest:marker", True)] = []
    for rotation in (0, 90, 180, 270):
        model = _marker_model(rotation)
        quads, _ = build_quads(model, textures, log, "selftest")
        proj, visible, big = project_quads(model, quads, cfg, log, "selftest")
        for quad, pts in visible:
            if quad.face != "north":                   # the right-hand face of the cube on screen
                continue
            # texel (0,0) is the corner that carries (u1,v1), which is quad corner 0 by definition.
            want_x, want_y, _ = pts[0]
            image = render_3d("selftest", model, textures, cfg, log)
            found = [(x, y) for y in range(image.height) for x in range(image.width)
                     if image.getpixel((x, y))[:3] == (204, 0, 0)]   # 255 * 0.8 north shading
            if not found:
                failures.append("rotation %d: the marker texel is not visible at all" % rotation)
                break
            cx = sum(p[0] for p in found) / len(found)
            cy = sum(p[1] for p in found) / len(found)
            # the marker covers one texel: its centroid sits half a texel in from the corner
            step = big / cfg.size
            err = math.hypot(cx - want_x / step, cy - want_y / step)
            if err > 12.0:
                failures.append("rotation %d: marker centroid at (%.1f, %.1f) is %.1f px from the "
                                "predicted UV corner (%.1f, %.1f)"
                                % (rotation, cx, cy, err, want_x / step, want_y / step))
            break
    if not any(f.startswith("rotation ") for f in failures):
        log.info("  ok   the (u1,v1) texel lands on quad corner 0 for rotations 0/90/180/270")

    # 4. the fast path agrees with the independent fragment-list reference.
    small = RenderConfig(64, 2, cfg.frame, cfg.anchor, cfg.cull)
    probes = ["cat_feeding_station", "mob_loader", "axolotl_bowl", "chunk_anchor"]
    for name in probes:
        try:
            model = resolve_model(assets, "%s:item/%s" % (MODID, name))
            fast = render_3d(name, model, textures, small, log)
            slow = _reference_render(model, textures, small, log, name)
        except ModelError as exc:
            failures.append("%s: %s" % (name, exc))
            continue
        fast_px, slow_px = fast.tobytes(), slow.tobytes()
        bad = sum(1 for i in range(0, len(fast_px), 4)
                  if max(abs(fast_px[i + c] - slow_px[i + c]) for c in range(4)) > 16)
        share = bad / float(small.size * small.size)
        if share > 0.005:
            failures.append("%s: %.2f%% of pixels differ from the independent reference renderer"
                            % (name, share * 100))
        else:
            log.info("  ok   %-22s matches the fragment-list reference (%.2f%% off)"
                     % (name, share * 100))

    if failures:
        for line in failures:
            log.error("selftest: " + line)
        log.info("")
        log.info("SELFTEST FAILED (%d problem(s))" % len(failures))
        return 1
    log.info("")
    log.info("selftest passed.")
    return 0


# -------------------------------------------------------------------------------------------
# driver
# -------------------------------------------------------------------------------------------

def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Render VanillaPlusAdditions item models to PNGs for the docs.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("--assets", default=DEFAULT_ASSETS, help="assets/<modid> directory")
    parser.add_argument("--client-jar", default=DEFAULT_JAR,
                        help="vanilla 1.21.1 client jar (only five textures are read from it)")
    parser.add_argument("--out", default=DEFAULT_OUT, help="output directory (items/ and blocks/)")
    parser.add_argument("--size", type=int, default=256, help="output edge length in pixels")
    parser.add_argument("--ssaa", type=int, default=4, help="supersampling factor")
    parser.add_argument("--frame", type=float, default=None,
                        help="fraction of the frame a full 16^3 cube fills; the default is "
                             "vanilla's own scale (which works out at 0.98 tall, 0.88 wide)")
    parser.add_argument("--anchor", choices=("block", "model"), default="block",
                        help="'block' keeps vanilla's shared origin so a bowl sits low in the "
                             "frame like a real slot; 'model' centres each silhouette")
    parser.add_argument("--only", default=None,
                        help="comma separated EXACT entry names (not a substring match)")
    parser.add_argument("--variants", dest="variants", action="store_true", default=True,
                        help="also render block models no item points at (skins, filled, rails)")
    parser.add_argument("--no-variants", dest="variants", action="store_false")
    parser.add_argument("--no-cull", dest="cull", action="store_false", default=True,
                        help="also draw faces pointing away from the camera (debugging)")
    parser.add_argument("--check", action="store_true",
                        help="verify the committed PNGs are in sync instead of rendering")
    parser.add_argument("--selftest", action="store_true",
                        help="prove the UV tables and the compositing, then exit")
    parser.add_argument("--list", action="store_true", help="list the entries and exit")
    parser.add_argument("--fail-fast", action="store_true", help="stop at the first bad model")
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("-q", "--quiet", action="store_true")
    args = parser.parse_args(argv)

    log = Log(verbose=args.verbose, quiet=args.quiet)
    if args.size <= 0 or args.ssaa <= 0:
        log.error("--size and --ssaa must be positive")
        return 2
    if args.frame is not None and not 0.05 <= args.frame <= 1.0:
        log.error("--frame must be within 0.05..1.0")
        return 2
    cfg = RenderConfig(args.size, args.ssaa, args.frame, args.anchor, args.cull)

    if Image is None:
        # The committed PNGs are what CI checks against, and comparing them needs no imaging
        # library at all - only regenerating does. A runner without Pillow is the normal case.
        message = ("Pillow is not installed - it is only needed to REGENERATE images "
                   "(pip install Pillow); the PNGs are committed")
        if args.check:
            log.info("skipping the image check: " + message)
            return 0
        log.error("cannot render: " + message)
        return 2

    if not os.path.exists(args.client_jar):
        # Five vanilla textures and a handful of vanilla parent models live in the client jar.
        # The rendered PNGs are committed, so a checkout without the jar is the normal case for
        # CI and for anyone who only builds the mod - that must not be an error.
        message = ("no vanilla client jar at %s - pass --client-jar or set VPA_CLIENT_JAR "
                   "(only needed to REGENERATE images; the PNGs are committed)" % args.client_jar)
        if args.check:
            log.info("skipping the image check: " + message)
            return 0
        log.error("cannot render: " + message)
        return 2

    try:
        assert_uv_tables_agree()                       # cheap, runs before anything is written
        assets = Assets(args.assets, args.client_jar, log)
        textures = TextureCache(assets, log)
        if args.selftest:
            log.info("selftest (%s)" % cfg.fingerprint())
            return run_selftest(textures, assets, cfg, log)
        entries = discover_entries(assets, args.variants, log)
    except ModelError as exc:
        log.error(str(exc))
        return 2

    if args.only:
        wanted = {n.strip() for n in args.only.split(",") if n.strip()}
        entries = [entry for entry in entries if entry.name in wanted]
        missing = wanted - {entry.name for entry in entries}
        if missing:
            log.error("--only: no such entry: %s (names must match exactly; try --list)"
                      % ", ".join(sorted(missing)))
            return 2
    if args.list:
        for entry in entries:
            log.info("%-48s %s" % (entry.rel_path, entry.model_ref))
        return 0

    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)
    log.info("%s %d entries (%s)" % ("checking" if args.check else "rendering", len(entries),
                                     cfg.fingerprint()))
    try:
        if args.check:
            code = run_check(entries, assets, textures, cfg, out_dir, log,
                             full_scan=args.variants and not args.only)
        else:
            code = run_render(entries, assets, textures, cfg, out_dir, log, args.fail_fast)
    except ModelError as exc:
        log.error(str(exc))
        return 2
    if log.warnings and not args.quiet:
        log.info("%d warning(s) - see stderr" % len(log.warnings))
    return code


if __name__ == "__main__":
    sys.exit(main())
