# Ressourcenpakete, die der Mod mitbringt

Pakete, die **im Jar mitreisen** und vom Mod selbst in die Paketliste gelegt werden — anders als
`texture-variants/` (wird von Hand nach `src/main/resources` kopiert) und `world_datapacks/` (landet
nie in einem Jar).

Jeder Unterordner hier ist ein vollständiges Ressourcenpaket mit eigener `pack.mcmeta`. Gradle nimmt
diesen Ordner als zusätzliches Ressourcenverzeichnis auf, die Dateien liegen im Jar also unter
`resourcepacks/<name>/…`.

## `vpa_quark_fresh_animations`

Ergänzt **Fresh Animations** um Tiere aus **Quark**, die FA selbst nicht abdeckt. Zurzeit:
Foxhound, Crab, Stoneling, Wraith und Shiba. Der Forgotten braucht nichts, Quark zeichnet ihn mit
dem Vanilla-Skelettmodell. Beim Shiba bewegt Quark Kopf, Ohren, Schwanz und Zunge auf allen Achsen
selbst; das Paket gibt ihm nur Augen wie dem Foxhound und schaltet die Sichtbarkeit der Zunge.

**Lizenz: CC BY-NC-SA 3.0, nicht MIT.** Die `.jem`-Dateien sind Quarks Modellgeometrie, per EMF
re-exportiert, und Quark steht unter CC BY-NC-SA 3.0 — die Share-Alike-Klausel zieht die Ableitung
mit. Darum liegt in diesem Ordner (und in `vpa_quark_toretoise`) eine eigene `LICENSE`, die über
`processResources` mit ins Jar und über `quarkFreshAnimationsPack` in das Standalone-Zip wandert;
der Java-Code des Moduls bleibt MIT. `vpa_create_track` ist davon nicht betroffen.

**Nie einen Teil lesen, den die `.jpm` selbst schreibt.** `teil.rx = teil.rx + …` liefert in EMF
den *eigenen* Wert aus dem letzten Frame zurück, nicht Quarks frischen – die Bewegung summiert sich
jedes Frame auf (der Wraith hat sich so um die eigene Achse gedreht). Also nur Achsen bewegen, die
Quark nicht setzt, und zwar absolut. Welche das sind, steht in Quarks `setupAnim`
(`javap -c -p … <Tier>Model`, nach `putfield …xRot/yRot/zRot` suchen).

**Absolut heißt: ab Quarks Grundpose.** Wer eine Achse schreibt, ersetzt auch die Ausgangsdrehung,
die Quark in `createBodyLayer` per `PartPose.offsetAndRotation(…)` vergibt. Bei der Krabbe sind die
Beine dort aufgefächert, die Klauen nach innen gedreht und die Augen geneigt – ohne diese Werte
klappen die Beine zusammen und die Klauen stehen gerade nach vorn. Also immer
`<Grundwert> + Bewegung`, den Grundwert aus dem Bytecode.

**Der Toretoise braucht den Mod.** Sobald es für ihn irgendeine `.jem` gibt, baut EMF das ganze
Modell um und schiebt alle Würfel in Unterteile. Quark bewegt die Beine beim Zeichnen selbst und
holt sich per `getRandomCube` eine Würfelecke als Drehpunkt für den Schritt – aus einer jetzt leeren
Liste, das Spiel stürzt ab. Der Mixin `ToretoiseRandomCubeMixin` reicht Quark den Würfel aus dem
Unterteil weiter, so behält der Schritt seinen echten Drehpunkt. Weil das eigenständige ZIP
keinen Mixin hat, liegt der Toretoise im eigenen Paket `vpa_quark_toretoise`, das nur der Mod lädt
(auch dann, wenn das ZIP installiert ist). Vor jedem neuen Tier prüfen, ob dessen Modellklasse auf
die eigenen Teile zugreift (`javap -c … | grep getRandomCube`).

Jeder Build schreibt das Paket zusätzlich als eigenständiges ZIP nach
`build/resourcepacks/Quark-x-Fresh-Animations-<version>.zip`; `scripts/deploy.sh --pack` legt es
auch bei laufendem Spiel in die Client-Instanz, danach lädt F3+T es neu. Liegt das ZIP im
`resourcepacks`-Ordner, lässt der Mod seine eingebaute Kopie weg.

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
