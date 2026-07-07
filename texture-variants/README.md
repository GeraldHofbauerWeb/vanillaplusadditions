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

## Technik-Notizen
- Die Varianten-Stationsmodelle referenzieren `<block>_trim.png` — diese Datei
  ist NEU; das `original`-Set braucht sie nicht (altes Modell). Übrig gebliebene
  Trim-PNGs stören nicht.
- `*_feeding_station_filled.json` ist ungenutzt (Blockstate zeigt für
  filled=true auf das normale Modell) und bleibt unangetastet.
- Generator-Skripte: Session-Scratchpad `texgen/` (gen_stations.py, gen_armor.py,
  gen_armor_icons.py, render_*.py, iso.py) — bei Bedarf wieder herholbar.
