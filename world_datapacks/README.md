# World datapacks

Datapacks that live in a **world folder**, not in the mod jar. They are not built by Gradle and
not shipped with any release — they are kept here so the live server is not the only copy.

Both are in use on games2, under `/AMP/Minecraft/survival_world/datapacks/`.

Install: copy the folder into `<world>/datapacks/`. New folders are picked up and enabled
automatically — `/reload` is enough for tags, worldgen needs a server restart and then only
affects chunks generated from that point on.

---

## `vpa_higher_mountains`

Raises terrain far from spawn, so mountains out in the unexplored land break through the cloud
layer while everything the players have already built stays untouched.

Wraps `minecraft:overworld_large_biomes/offset` through Lithostitched. **The `large_biomes`
variant is the one that matters** — games2 runs `level-type=minecraft:large_biomes`, and wrapping
plain `minecraft:overworld/offset` would do nothing at all.

- Amplification ramps in from 10,000 to 18,000 blocks from origin (`amplify_ramp.json`); the
  explored radius at the time was 8,397, so the inhabited area is outside the ramp entirely.
- The amplified value is a **locally blurred** copy of the offset (5-point cross at 384 blocks,
  `blur_offset.json`). Amplifying the raw offset multiplies the horizontal gradient by the same
  factor and produces vertical walls; blurring first keeps the peaks and loses the cliffs.
- `minecraft:clamp` rejects bounds beyond ±1,000,000. A larger value makes the whole pack fail to
  load and the world refuses to start — loudly, which is the one merciful part.

Measured against an identical seed without the pack: summit y 241 vs 156, maximum slope 77 vs 100,
chunk generation +10 %.

## `vpa_movable_docking_connector`

Lets a Create contraption carry a `simulated:docking_connector` (Create Aeronautics / Simulated).

Simulated 1.3.1 added the connector to the block tag `create:non_movable`, which Create checks in
`BlockMovementChecksImpl.isMovementAllowedFallback` **before** any config — so there is no switch,
only the tag. The pack overrides it with `"replace": true` and re-lists everything except the
connector itself.

`simulated:paired_docking_connector` deliberately stays non-movable: it only exists while the
connector is extended, and leaving it blocked turns "moving while extended" into a clear assembly
error instead of an orphaned block.

All entries carry `"required": false`, so the pack can never break world loading if one of those
mods is removed.

After a change, verify with `/reload` and then, standing next to a connector:

```
/execute if block <x> <y> <z> #create:non_movable run say STILL BLOCKED
```

Silence means the override took.

## `vpa_vanilla_mushroom_fields`

Gibt den Mushroom Fields ihre Vanilla-Generierung zurueck — und zwar **nur** diesem einen Biom.
Alles andere bleibt bei William Wythers' Overhauled Overworld (`wwoo`).

WWOO liefert seine Worldgen als eingebauten Datapack `wwoo:resources/wwoo_main` (ueber Cristel Lib)
und ueberschreibt darin rund 50 Vanilla-Biome, unter anderem
`minecraft:worldgen/biome/mushroom_fields`. Aus der Pilzinsel wird dort ein „fungales" Biom:
Crimson-Forest-Musik und -Ambiente, Myzel-Partikel, Basaltklippen, Sculk-Infektion, Blood Woods,
Feuerkorallen, Twisted Kelp. Die echten Riesenpilze (`minecraft:mushroom_island_vegetation`),
Zuckerrohr und Kuerbisse fallen dabei raus.

Der Pack legt die **unveraenderte Vanilla-Definition** wieder darueber. Sie stammt nicht aus
Handarbeit, sondern aus Mojangs eigenem Datengenerator:

```bash
java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --server --output srvout
# -> srvout/data/minecraft/worldgen/biome/mushroom_fields.json
```

`--reports` und `--dev` liefern das **nicht** (der alte Worldgen-Dump ist dort verschwunden) —
`--server` exportiert die Datapack-Registries vollstaendig.

### Warum das ueberhaupt greift

WWOO fasst die Biom-**Platzierung** nicht an: im Jar gibt es weder `multi_noise`- noch
`noise_settings`- oder `dimension`-Overrides, und eigene Biome bringt es auch keine mit. Es tauscht
ausschliesslich Definitionen. Wer die Definition zurueckdreht, bekommt darum wirklich vanilla
Pilzinseln an vanilla Stellen.

Cristel Lib haengt seine Packs mit `Pack.Position.TOP` ein — und das ist **kein** kosmetisches
Detail, sondern entscheidet, wer gewinnt. Die tatsaechliche Reihenfolge steht in `level.dat` unter
`Data.DataPacks.Enabled`, spaeter = hoehere Prioritaet. Sie faellt je nach Weg unterschiedlich aus:

**Pack in eine BESTEHENDE Welt gelegt** — er ist neu, wird hinten angehaengt und gewinnt.
Auf games2 gemessen:

```
13  wwoo:resources/wwoo_main
...
19  file/vpa_higher_mountains
20  file/vpa_movable_docking_connector
```

Gleiches Bild in der lokalen Testwelt `berge-test-4`.

**Pack lag schon beim ANLEGEN der Welt im Ordner** — dann sind beide neu, und Cristel Libs
`Position.TOP` schiebt WWOO dahinter:

```
vanilla, mod_data, file/vpa_mushroom_island_plateau, wwoo:resources/wwoo_main, wwoo:…/wwoo_remove_ores
```

WWOO gewinnt, unser Pack ist wirkungslos — **lautlos**, ohne Fehlermeldung. Beim Bau der
Varianten A-D am 2026-09-24 sind genau so vier identische Testwelten entstanden, bis der
Blockvergleich es auffliegen liess (Basalt 239k in allen vier, auch in der reinen Vanilla-Variante).

**Konsequenz:** Welt erst ohne den Pack anlegen und ihn danach hineinkopieren — oder
`Data.DataPacks.Enabled` in `level.dat` nachtraeglich so umsortieren, dass die `file/`-Eintraege
zuletzt stehen. Fuer games2 ist der Fall unkritisch, weil `survival_world` laengst existiert.

### Was der Pack bewusst NICHT anfasst

- **Erze.** Die wiederhergestellte Feature-Liste ruft `minecraft:ore_andesite_upper` und Co. auf,
  und die sind global durch das aktivierte Add-on `wwoo_remove_ores` veraendert. Das bleibt so —
  alles andere waere eine weltweite Aenderung, nur um ein Biom zu bedienen. Einzige Abweichung
  nach oben: Vanilla kennt `ore_diamond_medium`, WWOOs Liste nicht.
- **`spring_lava`** ist ebenfalls global von WWOO ueberschrieben und bleibt es.
- **Mob-Ergaenzungen.** Mushroom Creeper (Creeper Overhaul) und Mushroom-Fields-Enderman
  (Enderman Overhaul) kommen ueber `neoforge:add_spawns`-Biome-Modifier, die **nach** der Biom-JSON
  greifen. Sie ueberleben die Wiederherstellung.
- **Den Block-Tag `mushroom_grow_block`.** WWOO ergaenzt dort additiv `mushroom_stem`; das ist
  weltweit und harmlos.

### Grenzen

Worldgen wirkt nur auf **neu generierte Chunks**. Bereits erkundete Pilzinseln behalten ihre
Basaltklippen und Blood Woods. Was sofort und ueberall umschlaegt, sind die Biom-*Effects* —
Himmel-, Nebel- und Wasserfarbe, Ambiente, Musik, Partikel — sowie die Spawn-Listen, weil die live
aus der Biom-Definition gelesen werden.

Ein `/reload` reicht nicht: Worldgen-Registries werden beim Weltladen aufgebaut. Der Server muss
neu starten, im Client muss die Welt bis zum Titelbildschirm verlassen und neu betreten werden.

## `vpa_mushroom_fields_plus`

Der Stand, auf den die Runde vom 2026-09-24/25 hinausgelaufen ist, und der einzige Pilz-Pack, den
man einsetzen will. Vanilla-Basis wie oben, plus:

| Ebene | Inhalt |
|---|---|
| Stufe 0 | `wythers:terrain/local/mushroom_plateau_cliffs` — Felskanten an den Inselraendern |
| Stufe 4 | `wythers:terrain/local/mushroom_island_caves` — hoehlt das Inselinnere aus (~9.500 Bloecke) |
| Stufe 6 | sieben eigene `vpa:extra_ore_*` — Erze auf rund **140 %** von Vanilla |
| Stufe 9 | fuenf eigene Pilzformen, Bodenteppich mit dem Leuchtpilz des Moduls |
| Stufe 10 | Baumpilze, dazu `vpa:mushroom_lichen` |
| Effects | Lavendelhimmel, Tuerkiswasser, Warped-Forest-Ambiente, Grove-Musik, Sporenflug |

Die fuenf Pilzformen sind alle `minecraft:tree`-Features mit Pilzbloecken statt Holz und Laub:

- **Spitzpilz** — `pine_foliage_placer`, der oben bei Radius 0 anfaengt und sich nach unten
  monoton aufweitet. Das ergibt einen echten Kegel. `spruce_foliage_placer` waere falsch: der
  staffelt die Scheiben in Etagen und sieht nach Fichte aus.
- **Riesenpilz breit** — `giant_trunk_placer` (2x2-Stamm) mit `blob_foliage_placer`, Radius 4.
- **Verzweigt rot / braun** — `cherry_trunk_placer` + `cherry_foliage_placer`: ein Stamm, mehrere
  Kappen, wie die Kirschbaeume im Cherry Grove.
- **Leuchtpilz** — dieselben zwei grossen Formen, Hut aber zu 100 % `shroomlight`.

### Kein Pilz im Wasser, kein Pilz auf einem Pilz

Bis zum 2026-09-25 standen Pilze im Meer und wuchsen uebereinander (Sebi und Gerry per Screenshot).
Zwei Ursachen, beide in der Platzierung:

**`MOTION_BLOCKING` ist der falsche Heightmap.** Er zaehlt Wasser mit (`blocksMotion() ||
!getFluidState().isEmpty()`), landet im Meer also auf der **Wasseroberflaeche** — und er zaehlt
Pilzbloecke mit, landet in der Naehe eines fertigen Pilzes also auf dessen **Hut**. Vanilla benutzt
denselben Heightmap fuer `mushroom_island_vegetation` und kommt damit durch, weil dort
`HugeMushroomFeature` selbst prueft, ob unter dem Ursprung ein `#mushroom_grow_block` liegt. Unsere
Formen sind `minecraft:tree`-Features, und `TreeFeature.validTreePos` erlaubt alles, was Luft oder
`#replaceable_by_trees` ist — es prueft den Boden gar nicht. Jetzt steht ueberall `OCEAN_FLOOR_WG`
(Fluessigkeiten ignoriert, `Usage.WORLDGEN` — nicht `OCEAN_FLOOR`, das ist `Usage.LIVE_WORLD`).

**Der Bodentest muss aus dem Placed Feature kommen.** Jede der sechs Pilzformen und der Bodenteppich
tragen jetzt hinter dem Heightmap denselben Filter:

```json
{ "type": "minecraft:block_predicate_filter",
  "predicate": { "type": "minecraft:all_of", "predicates": [
    { "type": "minecraft:matching_blocks", "blocks": "minecraft:air" },
    { "type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": "#minecraft:dirt" } ] } }
```

Der Ursprung selbst muss Luft sein — damit faellt alles im Wasser raus. Darunter muss `#minecraft:dirt`
liegen (Mycel, Podsol, Erde, Grasblock …) — damit faellt jeder Pilzhut, jeder Stiel, Honig,
Shroomlight, Stein und Sand raus. Genau das macht Vanilla bei Baeumen auch, nur ueber
`BlockPredicate.wouldSurvive(<Setzling>)`.

**Bei den kleinen Pilzen reicht `canSurvive()` nicht.** `SimpleBlockFeature` prueft es zwar, aber
`MushroomBlock.canSurvive` laesst **jeden** soliden Block zu, sobald `getRawBrightness < 13` ist — und
waehrend der Weltgenerierung ist das Licht noch gar nicht berechnet, steht also immer auf 0. Darum
sassen Leuchtpilze auf Riesenpilzhuetten. Der Filter oben liegt deshalb zusaetzlich in der **inneren**
Platzierung von `dense_mushroom_patch`, pro Einzelposition.

Alles das wirkt nur auf **neu generierte** Chunks. Was schon dasteht, bleibt stehen.

### Flechten: `multiface_growth`, nicht Dekorator

Glow Lichen sitzt **nicht** ueber einen `attached_to_leaves`-Dekorator am Pilz. Der reiht die
Bloecke an den Hutkanten auf und erzeugt gerade Linien und rechte Winkel — unorganisch. Stattdessen
laeuft ein eigenes `minecraft:multiface_growth`-Feature (dasselbe, mit dem Vanilla seine
Hoehlenflechten setzt) auf `mushroom_stem` und die beiden Pilzblock-Sorten, mit
`can_place_on_wall: true`, sodass die Flechten auch **am Stiel** wachsen.

Achtung bei der Menge: Das Feature wuerfelt eine Hoehe und sucht dann im `search_range` eine
Oberflaeche. Die meisten Versuche landen in der Luft. Mit `count` 4-8 kamen **43** zusaetzliche
Flechten heraus, mit 50-70 und einem auf die Pilzzone eingeengten Hoehenband rund **1.200**.

### Haengende Flechten gehen nicht

Glow Lichen ist ein Multiface-Block und braucht eine Blockflaeche. `/setblock` platziert ihn zwar
frei schwebend (der Befehl ueberspringt die Pruefung), aber generieren laesst sich das nicht: Ein
Dekorator setzt genau einen Block pro Ansatzpunkt, fuer eine Kette nach unten gibt es kein
Werkzeug. Wer haengende Straenge will, braucht einen anderen Block — Ranken, Weeping Vines oder
Cave Vines.

### Haengt am Modul `glow_mushroom`

Der Bodenteppich nennt `vanillaplusadditions:glow_mushroom`. Ohne das Modul **laedt die Welt
nicht** ("Errors in the currently selected data pack(s)"). Umgekehrt ist das Modul ohne den Pack
harmlos — der Pilz fehlt dann nur in der Weltgenerierung.

### Verworfene Varianten

Vier Zwischenstaende sind auf dem Weg dorthin entstanden und wurden **nicht** eingecheckt. Was sie
gekostet und gezeigt haben, steht hier, damit der Weg nicht nochmal gegangen wird:

| Pack | Warum verworfen |
|---|---|
| Nur Felskanten + Hoehlen | Praktisch nicht von Vanilla zu unterscheiden — Myzel 81.235 gegen 82.956 |
| Rosinen aus WWOO | Dessen Kuesten-Features feuerten **gar nicht** (0 Sea Pickles, 0 Kelp, 0 Seegras) — sie brauchen WWOOs eigenes Ufer-Terrain |
| Erster Eigenbau | 7.129 Shroomlight-Bloecke — viel zu grell |
| Nur Effects | Unter Shadern wirkungslos, weil die Himmel und Nebel selbst berechnen |

Eine fuenfte Variante mit WWOOs totem `mushroom_island_plateau`: Sie schuettete
**1.008.175 Basaltbloecke** ueber die Insel und liess das Myzel von 82.956 auf 10.154 schrumpfen,
womit kein einziger Riesenpilz mehr wuchs. Jetzt ist klar, warum der Autor sie nie angeschlossen
hat.
