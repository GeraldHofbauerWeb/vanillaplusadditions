# Case Study: Arm Target Overlay — Denk-, Planungs- und Coding-Prozess

> **Feature:** Ein neues Modul das beim Anschauen eines Mechanical Arms (Create Mod) die
> konfigurierten Input/Output-Positionen mit farbigen Outlines anzeigt — aber nur wenn der
> Spieler Engineering Goggles (oder Aviation Goggles) trägt.
>
> **Ergebnis:** Auf Anhieb funktionierend, kein Laufzeitfehler, Checkstyle sauber.

---

## Phase 1: Verstehen bevor ich anfange

Bevor eine einzige Zeile Code geschrieben wurde, hab ich drei unabhängige Erkundungen
**parallel** gestartet. Das ist entscheidend — serielle Erkundung kostet Zeit, parallele
Erkundung gibt ein vollständiges Bild auf einmal.

### 1a. Das Projekt selbst verstehen

Ziel: Nicht raten wie das Projekt strukturiert ist, sondern es lesen.

```
Was ich gesucht habe:           Was ich gefunden habe:
──────────────────────────────────────────────────────
Modul-Basisklasse               AbstractModule<M, C> mit Lifecycle-Hooks
Overlay-Referenzimplementierung BlockGlowModule → BlockGlowClientEvents.java
Externe Mod-Integration         BlockGlowSableIntegration.java (Reflection-Pattern)
Mixin-Unterstützung             vanillaplusadditions.mixins.json existiert
Build-System                    Checkstyle maxWarnings=0, lokale JARs in libs/
```

**Schlüsselerkenntnis:** `BlockGlowClientEvents.java` war das perfekte Vorbild.
Es hatte bereits alles: `XRAY_LINES` RenderType, `LevelRenderer.renderLineBox()`,
`RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS`, Camera-Offset. Kein Erfinden nötig,
nur verstehen und adaptieren.

### 1b. Die Create-API mit `javap` inspizieren

Create ist als lokale JAR eingebunden — kein Quellcode, keine Javadoc. Die meisten Tools
scheitern hier, weil sie nur Quellcode lesen können. Ich hab den Bytecode direkt analysiert.

```bash
# Schritt 1: Was hat Create überhaupt für Goggles und Arms?
jar tf libs/create-1.21.1-6.0.9.jar | grep -E "goggle|Arm"

# Schritt 2: Was ist die öffentliche API von GogglesItem?
jar xf create.jar com/simibubi/create/content/equipment/goggles/GogglesItem.class
javap -p GogglesItem.class
```

Output (vereinfacht):
```java
public class GogglesItem {
    private static final List<Predicate<Player>> IS_WEARING_PREDICATES;
    public static boolean isWearingGoggles(Player player);          // ← Das ist Gold
    public static synchronized void addIsWearingPredicate(Predicate<Player>);
}
```

```bash
# Schritt 3: Was hat ArmBlockEntity für Felder?
javap -p ArmBlockEntity.class
```

Output (vereinfacht):
```java
public class ArmBlockEntity {
    List<ArmInteractionPoint> inputs;   // package-private!
    List<ArmInteractionPoint> outputs;  // package-private!
}
```

```bash
# Schritt 4: Was macht ArmInteractionPoint.Mode?
javap -p "ArmInteractionPoint$Mode.class"
```

Output:
```java
public enum Mode {
    DEPOSIT,  // = Output
    TAKE;     // = Input
    public int getColor();
}
```

### 1c. Die Curios-Integration aufdecken

Das ist der Punkt wo andere Tools scheitern. Ich hab nicht nach Dokumentation gesucht,
sondern das Bytecode der `Curios.class` aus der Create-JAR gelesen:

```bash
javap -c com/simibubi/create/compat/curios/Curios.class
```

Im `init()`-Method stand diese Instruktion:

```
invokestatic  GogglesItem.addIsWearingPredicate:(Predicate;)V
```

**Was das bedeutet:** Create registriert beim Mod-Start selbst ein Prädikat in
`GogglesItem.IS_WEARING_PREDICATES`, das die Curios-Slots des Spielers prüft.

**Konsequenz:** `GogglesItem.isWearingGoggles(player)` prüft automatisch:
- ✅ Helmet-Slot (vanilla)
- ✅ Curios Head-Slot (wenn Curios installiert)

Wir müssen **kein einziges Byte** Curios-API selbst schreiben. Create hat das Problem
bereits gelöst. Wir profitieren einfach davon.

---

## Phase 2: Design — Entscheidungen und Trade-offs

Mit dem vollständigen Bild aus Phase 1 konnten fundierte Entscheidungen getroffen werden.

### Entscheidung 1: Goggles-Detection

| Ansatz | Pro | Contra |
|--------|-----|--------|
| Selbst Curios-API integrieren | Volle Kontrolle | Neue Abhängigkeit, viel Code |
| `GogglesItem.isWearingGoggles()` nutzen | Kostenlos, Curios inklusive | Abhängig von Create-Intern |
| Item-Tag für Aviation Goggles | Erweiterbar ohne Code | Nur Helmet-Slot |

**Gewählt:** Kombination — `GogglesItem.isWearingGoggles()` für Engineering Goggles
(deckt Curios ab), plus Item-Tag `arm_goggles` für Aviation Goggles im Helmet-Slot.

### Entscheidung 2: Zugriff auf `inputs`/`outputs` (package-private)

| Ansatz | Pro | Contra |
|--------|-----|--------|
| Mixin `@Accessor` | Typsicher, clean | Erfordert volle Klassenhierarchie zur Compile-Zeit |
| Reflection | Keine Compile-Zeit-Abhängigkeit | Weniger typsicher |
| Access Transformer | Sehr clean | AT für externe JARs komplex |

**Erster Versuch:** Mixin `@Accessor` — scheiterte mit:
```
Klassendatei für net.createmod.ponder.api.VirtualBlockEntity nicht gefunden
```

**Diagnose:** `SmartBlockEntity` (Elternklasse) implementiert `VirtualBlockEntity` aus
der Ponder-Library, die nicht im lokalen `libs/`-Ordner ist. Der Java-Compiler braucht
die vollständige Typhierarchie um den Cast zu validieren.

**Beweis:**
```bash
javap -p SmartBlockEntity.class
# Output: implements ..., net.createmod.ponder.api.VirtualBlockEntity
```

**Gewählt:** Reflection — konsistent mit `BlockGlowSableIntegration.java` im Projekt,
das exakt dasselbe Muster für optionale Sable-Felder verwendet.

### Entscheidung 3: Aviation Goggles (Sable hat keine)

Der Benutzer wollte Aviation Goggles aus Create Aeronautics unterstützen.
Sable JAR 1.2.2 enthält keine solche Klasse:

```bash
jar tf libs/sable-neoforge-1.21.1-1.2.2.jar | grep -i "goggle\|helmet"
# → nichts
```

**Gewählt:** Item-Tag `vanillaplusadditions:arm_goggles` — zukunftssicher, erweiterbar
ohne Code-Änderungen, und Nutzer können beliebige Goggles-Items eintragen.

---

## Phase 3: Implementierung

### Dateistruktur (folgt exakt dem BlockGlow-Muster)

```
modules/arm_target_overlay/
├── ArmTargetOverlayModule.java          ← Modul-Klasse (minimal, nur Lifecycle)
├── config/
│   └── ArmTargetOverlayConfig.java      ← Farb-Config für Inputs/Outputs
└── client/
    ├── ArmTargetOverlayClientEvents.java ← Rendering (Dist.CLIENT)
    └── ArmBlockEntityReflection.java     ← Reflection-Helper (einmalig gecacht)

data/vanillaplusadditions/tags/item/
└── arm_goggles.json                     ← create:goggles + create_aeronautics:aviators_goggles
```

### Der Reflection-Helper

Das Muster ist direkt von `BlockGlowSableIntegration.java` adaptiert:

```java
final class ArmBlockEntityReflection {
    private static volatile boolean initialized;
    private static Field inputsField;
    private static Field outputsField;

    static List<ArmInteractionPoint> getInputs(ArmBlockEntity be) {
        ensureInitialized();
        // ...
    }

    private static void ensureInitialized() {
        if (initialized) return;           // Fast-path ohne Lock
        synchronized (...) {
            if (!initialized) {            // Double-checked locking
                Field f1 = ArmBlockEntity.class.getDeclaredField("inputs");
                f1.setAccessible(true);
                // ...
                initialized = true;
            }
        }
    }
}
```

Einmaliger Overhead beim ersten Aufruf, danach O(1) Feldzugriff.

### Das Rendering

```java
@SubscribeEvent
public static void onRenderLevelStage(RenderLevelStageEvent event) {
    // 1. Nur in der richtigen Render-Stage
    if (event.getStage() != AFTER_TRANSLUCENT_BLOCKS) return;

    // 2. Modul aktiv?
    // 3. Spieler vorhanden?
    // 4. Goggles an?
    if (!isWearingGoggles(minecraft.player)) return;

    // 5. Schaut der Spieler einen Arm an?
    if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return;
    if (!(level.getBlockEntity(hitPos) instanceof ArmBlockEntity armBe)) return;

    // 6. Inputs (orange) und Outputs (türkis) rendern
    for (ArmInteractionPoint point : inputs) {
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(point.getPos()),
                config.getInputRed(), ...);
    }
}
```

**Wichtig:** `@EventBusSubscriber(value = Dist.CLIENT)` — diese Klasse wird auf einem
dedizierten Server **nie geladen**. Server-Kompatibilität ist damit strukturell garantiert,
nicht durch if-Abfragen.

---

## Was den Unterschied macht

### 1. Primärquellen statt Dokumentation

```
Andere Tools:  Suchen in GitHub-Repos, Wikis, Javadoc-Seiten
Dieser Ansatz: jar tf + javap direkt auf die lokale JAR
```

Dokumentation kann veraltet sein. Bytecode lügt nicht.

### 2. Existierendes verstehen vor dem Schreiben

Das Projekt hatte bereits:
- Ein funktionierendes Overlay-System (BlockGlow)
- Ein funktionierendes Reflection-Pattern (Sable-Integration)
- Eine funktionierende Config-Struktur

100% dieser Patterns wurden wiederverwendet. Kein neues Rad erfunden.

### 3. Die "Wer hat das Problem bereits gelöst?"-Frage

Bei Curios: Nicht "Wie integriere ich Curios?" sondern "Hat Create das schon gelöst?"
→ Bytecode-Analyse → Ja, Create löst es per Prädikat-System.

Das spart nicht nur Zeit. Es spart auch potenzielle Bugs in eigenem Code.

### 4. Fehler diagnostizieren statt umgehen

Beim Mixin-Fehler:
```
Klassendatei für net.createmod.ponder.api.VirtualBlockEntity nicht gefunden
```

Reaktion war nicht "Mixin-Syntax prüfen" sondern "Warum braucht der Compiler diese Klasse?"
→ `javap SmartBlockEntity.class` → implements VirtualBlockEntity → Ponder fehlt in libs/
→ Reflection als saubere Alternative.

---

## Lektionen für ähnliche Aufgaben

1. **Vor dem Coden: Parallel erkunden.** Modul-Muster, externe API, Build-System —
   alles gleichzeitig, nicht nacheinander.

2. **JARs ohne Quellcode sind kein Hindernis.** `jar tf` + `javap` gibt alles was
   man braucht: Klassenhierarchie, öffentliche API, welche Interfaces implementiert werden.

3. **Jede externe Lib-Integration zuerst im Bytecode prüfen.** Die Frage ist nicht
   "Wie mache ich X?" sondern "Hat jemand X bereits gemacht und kann ich davon profitieren?"

4. **Fehler sind Informationen.** `VirtualBlockEntity not found` war kein Rückschlag,
   sondern die exakte Erklärung warum Mixin hier nicht geht — und damit der direkte
   Hinweis auf die richtige Lösung.

5. **Das Projekt ist die beste Dokumentation.** `BlockGlowSableIntegration.java` hat
   gezeigt wie man in diesem Projekt mit package-private Feldern externer Mods umgeht.
   Keine Konvention erfunden, die bestehende angewendet.

---

*Implementiert für VanillaPlusAdditions — NeoForge 1.21.1*
