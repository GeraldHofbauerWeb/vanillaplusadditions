"""Vanilla-Grundhaeute fuer die Vorschau-Renders.

Werden aus dem Minecraft-Client-Jar im Gradle-Cache gezogen (stdlib zipfile) und in
texgen/_cache/ abgelegt, damit die Renders nicht von einem Netzzugriff abhaengen.
"""

import glob
import os
import zipfile

import png

_HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(_HERE, "_cache")

SKINS = {
    "cat": "assets/minecraft/textures/entity/cat/tabby.png",
    "axolotl": "assets/minecraft/textures/entity/axolotl/axolotl_lucy.png",
    "wolf": "assets/minecraft/textures/entity/wolf/wolf_tame.png",
}


def _client_jars():
    pats = [
        os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/*/client-extra.jar"),
        os.path.expanduser("~/.gradle/caches/**/client-extra*.jar"),
    ]
    out = []
    for p in pats:
        out.extend(glob.glob(p, recursive=True))
    return out


def base(species):
    os.makedirs(CACHE, exist_ok=True)
    dst = os.path.join(CACHE, "%s_base.png" % species)
    if os.path.exists(dst):
        return png.load(dst)
    entry = SKINS[species]
    for jar in _client_jars():
        try:
            with zipfile.ZipFile(jar) as z:
                if entry in z.namelist():
                    with open(dst, "wb") as fh:
                        fh.write(z.read(entry))
                    return png.load(dst)
        except (zipfile.BadZipFile, OSError):
            continue
    raise RuntimeError("Grundhaut %s nicht gefunden. Client-Jar im Gradle-Cache?" % entry)
