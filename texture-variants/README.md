# Textur-/Modell-Varianten (Cat + Axolotl Guardian)

Tauschbare Design-Sets für Feeding Stations, Bowls und Tier-Rüstungen.
Jedes Set ist **vollständig** (Texturen + ggf. Modelle) — anwenden mit:

```bash
cd texture-variants
./apply.sh                # listet alles auf
./apply.sh cat-station A-gemuetliches-holz
./apply.sh axolotl-armor C-schuppen
./apply.sh cat-station original    # zurück zum alten Stand
```

Danach `./gradlew build` und Jar deployen. Vorschau-Renders liegen in `preview/`.

## Gruppen & Varianten

### cat-station / axolotl-station
Enthält: Station-Textur (Boden), **neue** `_trim`-Textur (Rahmen/Wände),
Glas-Textur, Bowl-Textur + `_trim`, sowie **überarbeitete Modelle**
(Aquarium-Rahmen mit Eckpfosten + oberem Rahmen; Bowl-Wände nutzen die
Trim-Textur statt Streifen aus der Bodentextur).

| Variante | Cat | Axolotl |
|---|---|---|
| `A-…` | Gemütliches Holz: Fichte + rotes Kissen, Eichen-Rahmen | Prismarin-Aquarium: Prismarinziegel + dunkler Prismarin-Rahmen |
| `B-…` | Stein-Bistro: Glattstein + Eisen-Rahmen | Kupfer-Labor: oxidiertes Kupfer + Kupfer-Rahmen |
| `C-…` | Kirschholz-Café: Kirschholz mit Pfotenabdruck | Korallenriff: Hirnkoralle + Orgelkoralle, Seelaternen-Akzent |
| `D-…` | Edle Dunkeleiche: Dunkeleiche + Gold-Rahmen | Wirrwald: Warped Planks + dunkler Prismarin |
| `E-…` | Bambus-Lounge: Bambus + Mosaik-Rahmen | Amethyst-Grotte: Calcit + Amethyst |
| `F-…` | Deepslate Modern: Tiefenschiefer + Kupfer | Froglight-Strand: Sand + Perlmutt-Froglight |
| `original` | bisheriger Stand (Snapshot) | bisheriger Stand (Snapshot) |

### cat-armor / axolotl-armor
Entity-Texturen (Vanilla-UV-Layout), je 4 Tiers (iron/gold/diamond/netherite):

| Variante | Look |
|---|---|
| `A-vollplatte` | Volle Platte: Body-Panzerung + Helmkappe (+ Stirn-Gem), bei der Katze zusätzlich Beinschienen vorn |
| `B-harness` | Geschirr: 2 Gurte um den Körper + Rückenplatte, Halsband |
| `C-schuppen` | Schuppenpanzer: Schuppendecke über Rücken/Flanken, Bauchgurte, bei der Katze Schwanzringe |
| `C2-schuppen-kappe` (nur Axolotl) | Wie C, Helm nur als Kappe oben — nichts im Gesicht |
| `C3-schuppen-ohne-helm` (nur Axolotl) | Nur Körper-Schuppendecke, Kopf frei |
| `original-fixed` (nur Katze, **live**) | Original-Look mit korrigierten Pixelfehlern (Ohr-Deckfläche, Vorderbein-Innenseite) |
| `D-helm` | Nur Kopf: Kappe + Seitenschutz (+ Ohren bei der Katze), Gem |
| `E-pfoten` | Nur Stiefel/Füßchen (untere Beinreihen + Sohlen) |
| `F-schienbeinschoner` / `F-beinschienen` | Beinschienen (Katze: Pfoten frei; Axolotl: Beinchen komplett) |
| `G-bauchpanzer` | Bauch-/Brustplatte + untere Flankenkante (Rücken frei) |
| `H-rueckendecke` / `H-rueckenplatte` | Nur Rücken (Katze: wie Pferdedecke inkl. Heck; Axolotl: Platte mit Rückgrat-Grat) |
| `I-kragen` | Ring um den vorderen Körper/Hals + Brustplatte |
| `J-schwanzpanzer` | Geschuppter Schwanz bzw. Schwanzflosse + Hinterteil |
| `K-volle-montur` | Alles zusammen (Körper, Helm, Beine, Schwanz, Gem) |
| `W1-riemen` (**neu**) | Nur der Bauchgurt mit Schnalle — Ruecken, Kopf, Beine, Schwanz frei |
| `W2-sattel` (**neu**) | Die direkte Wolf-Uebersetzung: Mail-Sattel, Flanken 2/3 herunter, ein Bauchgurt |
| `W3-sattel-kragen` (**neu**) | Wie W2 + Brustkragen + die kleinen Beinmanschetten des Wolfs |
| `W4-ritter` (**neu**) | Wie W3 + Helmdecke und Stirnband; Gesicht, Ohren, Kiemen, Augen ausgespart |
| `original` | bisheriger Stand (Snapshot) |

### cat-armor-icons / axolotl-armor-icons
Item-Icons (`textures/item/<sp>_armor_<tier>.png`, 16px-Pixelart 8× skaliert), je 4 Tiers.
Vorschau: `preview/armor-icons.png`.

| Variante | Cat | Axolotl |
|---|---|---|
| `V1-…` | Gesicht: Brustpanzer mit Ohrenschutz + Nase | Kiemenpanzer: Kopf-/Körperpanzer mit rosa Kiemenästen (**live**) |
| `V2-…` | Helm: Katzenhelm mit Augenschlitzen | Kiemenhelm: Kopfkappe mit Kiemen, Augen + Visier |
| `V3-…` | Schuppen: Brustplatte mit Schuppenreihen | Flossenpanzer: Platte mit rosa Rückenkamm + Schwanzflosse |
| `original` | bisheriger Stand (live) | bisheriger alter Stand (war fälschlich der Cat-Look) |

## Der Wolf ist die Referenz (2026-08-30)
Die Wolfsruestung (`textures/entity/wolf/wolf_armor_*.png`) wird **nicht angefasst** —
sie ist die Design-Basis fuer Katze und Axolotl. Ausgemessen ergibt sie ein sehr
diszipliniertes System:
- **11 Farbstufen**, und alle vier Tiers haben *identische* Pixelzahlen je Stufe
  (7/43/53/10/34/51/49/11/33/40/37) — eine Zeichnung, vier ausgetauschte Rampen.
- **17 % Deckung.** Schwanz, Ohren und Schnauze komplett frei; Beine nur eine kleine
  Manschette (12 von 64 Pixeln); Rueckenplatte voll; Flanken zu zwei Dritteln; Bauch
  offen bis auf **genau einen** Gurt.

Die `W*`-Varianten uebernehmen dieses Vokabular. Sie malen in *Rampen-Indizes* statt in
Farben — dadurch ist strukturell garantiert, dass alle vier Tiers dieselben
Stufenhistogramme haben wie der Wolf.

## Fresh-Animations-Regeln
Der Pack faehrt FreshAnimations + Entity Model Features + Entity Texture Features. FA
behaelt das Vanilla-UV-Layout bei (alle `textureOffset` decken sich), eine
Textur-Ueberarbeitung ist also FA-sicher. **Aber:** FA fuegt eigene Augen-Quads mit
eigenen `uv*`-Rechtecken hinzu, und weil `CatArmorLayer`/`AxolotlArmorLayer` das
komplette Parent-Modell mit der Ruestungstextur rendern, wuerden deckende Pixel dort
dem Tier auf dem Auge landen. Diese Pixel muessen transparent bleiben:
- Katze: (0,4) (1,3) (1,4) (3,3) (3,4) (4,4)
- Axolotl: (0,4) (1,4) (3,4) (4,4)

`texgen/fa_reserved.py` raeumt sie und prueft jeden Lauf. Die alten Live-Texturen
halten die Regel bereits ein (0 Treffer in allen Tiers).

Aus demselben Grund **kein** eigener Model-Layer mit `CubeDeformation` fuer Cat/Axolotl:
der waere Vanilla-Geometrie, die EMF nicht ersetzt — die Ruestung wuerde als starre
Huelle um ein FA-animiertes Tier haengen. Beim Wolf geht das nur, weil FA selbst ein
`wolf_armor.jem` mitliefert.

## Technik-Notizen
- Die Varianten-Stationsmodelle referenzieren `<block>_trim.png` — diese Datei
  ist NEU; das `original`-Set braucht sie nicht (altes Modell). Übrig gebliebene
  Trim-PNGs stören nicht.
- `*_feeding_station_filled.json` ist ungenutzt (Blockstate zeigt für
  filled=true auf das normale Modell) und bleibt unangetastet.
- **Generator liegt jetzt im Repo:** `texture-variants/texgen/` — reine
  Python-Standardbibliothek (kein Pillow, kein venv, kein Setup):

      python3 texture-variants/texgen/build_variants.py

  Module: `png.py` (PNG-IO), `geometry.py` (Modellboxen + Vanilla-UV-Konvention),
  `wolfref.py`/`wolfswatch.py` (Wolf als Referenz auslesen), `paint.py` (Zeichnen in
  Rampen-Indizes), `variants.py` (die W*-Varianten), `iso.py` (isometrischer
  Renderer), `icons.py`, `fa_reserved.py`, `baseskins.py`.
  Der Lauf prueft selbst: FA-Sperrpixel frei, nur Wolf-Rampenfarben, Alpha sauber 0/255.
- Die alten Generatoren der Stations-Sets lagen nur im Session-Scratchpad und sind weg.
- `scripts/cat-armor-gen/` (SVG-basiert, v1-v7) ist der ueberholte Vorgaenger und die
  Ursache dafuer, dass die alte Cat-Ruestung 179 Farben hat (Antialiasing statt Pixelart).
