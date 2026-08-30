"""Fresh-Animations-Sperrpixel.

Der Pack faehrt FreshAnimations_v1.10.4 + Entity Model Features + Entity Texture
Features. FA ersetzt die Entity-Modelle per OptiFine-CEM und behaelt dabei das
Vanilla-UV-Layout bei (textureSize und alle textureOffset-Werte decken sich mit den
dekompilierten Vanilla-Modellen) — eine Texturueberarbeitung ist also grundsaetzlich
FA-sicher.

Die eine Ausnahme: FA fuegt eigene Augen-Quads mit explizitem uvNorth/uvEast/uvWest
hinzu, die in Rechtecke sampeln, die das Vanilla-Modell dort nicht benutzt. Weil
CatArmorLayer/AxolotlArmorLayer das komplette Parent-Modell mit der Ruestungstextur
rendern (coloredCutoutModelCopyLayerRender(getParentModel(), getParentModel(), ...)),
wuerde alles, was dort deckend ist, dem Tier als Ruestungspixel auf dem Auge landen.

Die Pixel unten sind aus assets/minecraft/optifine/cem/{cat,axolotl}.jem ausgelesen
(alle uv*-Rechtecke der Augen-/Pupillen-Submodelle, Rechteck-Enden exklusiv).

Stand der Live-Texturen: sauber — cat_armor_* und axolotl_armor_* treffen in allen
vier Tiers 0 dieser Pixel. Das ist also keine Reparatur, sondern eine Regel, die
weiter eingehalten werden muss.
"""

# Exakt die Pixel, die FAs Augen-Quads sampeln.
RESERVED = {
    "cat": frozenset({(0, 4), (1, 3), (1, 4), (3, 3), (3, 4), (4, 4)}),
    "axolotl": frozenset({(0, 4), (1, 4), (3, 4), (4, 4)}),
    "wolf": frozenset(),
}


def violations(species, img):
    """Deckende Pixel auf FA-Sperrpixeln. Leer = sauber."""
    return sorted(p for p in RESERVED.get(species, ()) if img.get(*p)[3] > 0)


def clear(species, img):
    """Sperrpixel hart auf transparent setzen."""
    for (x, y) in RESERVED.get(species, ()):
        img.set(x, y, (0, 0, 0, 0))
    return img
