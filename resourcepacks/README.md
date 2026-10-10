# Ressourcenpakete, die der Mod mitbringt

Pakete, die **im Jar mitreisen** und vom Mod selbst in die Paketliste gelegt werden — anders als
`texture-variants/` (wird von Hand nach `src/main/resources` kopiert) und `world_datapacks/` (landet
nie in einem Jar).

Jeder Unterordner hier ist ein vollständiges Ressourcenpaket mit eigener `pack.mcmeta`. Gradle nimmt
diesen Ordner als zusätzliches Ressourcenverzeichnis auf, die Dateien liegen im Jar also unter
`resourcepacks/<name>/…`.

## `vpa_quark_fresh_animations`

Ergänzt **Fresh Animations** um Tiere aus **Quark**, die FA selbst nicht abdeckt. Zurzeit: der
Foxhound.

Das Paket besteht aus CEM-Dateien (`.jem` für die Geometrie, `.jpm` für die Animationsausdrücke),
die **Entity Model Features** liest. Ohne EMF ist es eine Datei, die niemand anschaut; ohne Quark
gibt es das Tier nicht, auf das sie sich bezieht. Der Mod aktiviert es deshalb nur, wenn beides da
ist.

### Ein weiteres Tier hinzufügen

1. Im Spiel EMF öffnen: **Mods → Entity Model Features → Config → models → allmodels**, das Tier
   suchen und **export** drücken. EMF schreibt eine `.jem` mit den **echten Teilenamen**.
2. Die exportierte Datei hierher legen, an den Pfad, den EMF dafür nennt.
3. Animationen ergänzen — Vorlage ist die passende Datei aus Fresh Animations, damit sich das Tier
   neben FAs eigenen nicht fremd anfühlt.

Warum exportieren statt abtippen: In einer `.jem` ist `"part": "head"` der Schlüssel, unter dem EMF
das Originalteil ersetzt. Stimmt der Name nicht, passiert **nichts** — kein Fehler, keine Animation,
keine Meldung. Aus dem Bytecode der fremden Mod abgeschrieben wäre jeder Tippfehler so ein stiller
Ausfall.

## `vpa_create_track`

Blockstate und Modelle der **Chunk Loader Track** (`train_chunk_loading`). Die Modelle laden Creates
OBJ-Meshes (`create:models/block/track/*.obj`). Minecraft lädt beim Start **jede** Modelldatei im
Namespace einer Mod, ob ein Block sie braucht oder nicht — ohne Create stand darum für jedes dieser
Modelle ein roter `Failed to load model …` im Log. Als Paket, das `ChunkLoaderTrackModelPack` nur bei
geladenem Create zuschaltet, werden die Dateien ohne Create gar nicht erst gelesen.

Bedingung ist **nur Create**, nicht der `enabled`-Schalter des Moduls: mit Create ist der Block
registriert und braucht seine Modelle, sonst rendert er als lila-schwarzer Würfel.
