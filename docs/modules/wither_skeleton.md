# Wither Skeleton Enforcer

> **TL;DR** — In a chunk that belongs to a Nether fortress a plain skeleton never gets to spawn: it
> is turned away and a wither skeleton takes its place. The rest of the Nether is left exactly as it
> is.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `wither_skeleton` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | [YUNG's Better Nether Fortresses](https://modrinth.com/mod/yungs-better-nether-fortresses) |
| **Download** | [`vpa_wither_skeleton.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_wither_skeleton.jar) · also needs `vpa_core` |
| **Config section** | `[modules.wither_skeleton]` |
| **Since** | `v0.1.0` |
<!-- vpa:meta:end -->

## What it does

In the Nether, in a chunk that belongs to a fortress, a plain skeleton that is about to spawn is
blocked, and a wither skeleton is put in its place — same position, same facing, and finalised like
a natural spawn, so it arrives with its stone sword and its 4.0 base attack damage, and it withers
whoever it hits like any other of its kind.

Nothing else about the Nether changes. Skeletons in the open wastes and in the soul sand valleys
spawn as they always did; the module only ever looks at fortress chunks. Blazes, magma cubes and
zombified piglins are untouched, and so are strays, boggeds and wither skeletons themselves — the
check is `instanceof Skeleton`, and in 1.21.1 those three all extend `AbstractSkeleton` directly,
so none of them match.

The swap is silent by default. There is a chat broadcast, but it only fires with
[debug logging](../guides/debug-logging.md) switched on; out of the box a fortress just stops
producing ordinary skeletons.

## Why it exists

A vanilla fortress spawns plain skeletons, because they are on its spawn list:

```java
// NetherFortressStructure
public static final WeightedRandomList<MobSpawnSettings.SpawnerData> FORTRESS_ENEMIES = WeightedRandomList.create(
    new MobSpawnSettings.SpawnerData(EntityType.BLAZE, 10, 2, 3),
    new MobSpawnSettings.SpawnerData(EntityType.ZOMBIFIED_PIGLIN, 5, 4, 4),
    new MobSpawnSettings.SpawnerData(EntityType.WITHER_SKELETON, 8, 5, 5),
    new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 2, 5, 5),
    new MobSpawnSettings.SpawnerData(EntityType.MAGMA_CUBE, 3, 4, 4)
);
```

A weight of two out of twenty-eight, so about one fortress spawn roll in fourteen picks the plain
skeleton — and that entry asks for five of them at once. Weighted by the group sizes in the list
above, roughly one fortress monster in eleven is an ordinary bow skeleton standing between the
blazes, worth a bone and two arrows. That is what this module removes, by turning exactly those
spawns into the mob the group next to them already is.

## In detail

### What counts as "in a fortress"

The gate is the chunk's structure references, not the fortress walls:

```java
Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(
        event.getEntity().blockPosition());
if (allStructures.isEmpty()) {
    return;
}
```

```java
for (Structure structure : allStructures.keySet()) {
    if (structure instanceof NetherFortressStructure
            || ResourceLocation.fromNamespaceAndPath("betterfortresses", "fortress")
            .equals(structureRegistry.getKey(structure))) {
```

`getAllStructuresAt` reads the references of the chunk the position sits in, and nothing finer:

```java
// StructureManager
public Map<Structure, LongSet> getAllStructuresAt(BlockPos pos) {
    SectionPos sectionpos = SectionPos.of(pos);
    return this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
}
```

Those references are written during world generation to every chunk whose 16 × 16 footprint
overlaps the structure's bounding box (`ChunkGenerator.createReferences`, the test is
`structurestart.getBoundingBox().intersects(minX, minZ, minX + 15, minZ + 15)`). The Y coordinate
is only used to pick the chunk, so the gate covers the **whole column**: a skeleton in the lava sea
below a fortress, or up on the roof above it, is in the same chunk and is treated the same.

That is wider than the region vanilla itself calls a fortress spawn. Vanilla only swaps in
`FORTRESS_ENEMIES` when all three of these hold:

```java
// NaturalSpawner.isInNetherFortressBounds
if (category == MobCategory.MONSTER && level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) {
    Structure structure = structureManager.registryAccess()
            .registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.FORTRESS);
    return structure == null ? false : structureManager.getStructureAt(pos, structure).isValid();
}
```

So a skeleton that rolled out of the ordinary biome list while standing on netherrack — never a
fortress spawn as far as vanilla is concerned — is blocked and converted too, as long as its chunk
carries a fortress reference.

### What is affected, and what is not

| Spawn | Result |
|---|---|
| Plain skeleton, fortress chunk, Nether | blocked, wither skeleton instead |
| Plain skeleton, anywhere else in the Nether | untouched |
| Skeleton in the Overworld or the End | untouched — `serverLevel.dimension() != Level.NETHER` returns early |
| Stray, bogged, wither skeleton | untouched — only `Skeleton` matches |
| Skeleton from a spawner, a spawn egg or `/summon`, fortress chunk | converted as well; the module applies no spawn-type filter |
| Spawns during world generation | exempt — they run against a `WorldGenRegion`, and the handler needs a `ServerLevel` |

There is no `MobSpawnType` check anywhere in the module, so every path that fires
`FinalizeSpawnEvent` against a real level is covered. Two of those have side effects worth knowing:

* **Spawners.** `BaseSpawner` aborts its batch when the entity cannot be added
  (`if (!level.tryAddFreshEntityWithPassengers(entity)) { this.delay(...); return; }`), so a
  skeleton spawner in a fortress chunk produces wither skeletons — one per activation, instead of
  the usual group.
* **Spawn eggs.** `SpawnEggItem` only shrinks the stack when `spawn(...)` returns non-null, and a
  cancelled spawn returns null. The module has already added its wither skeleton by then, so a
  skeleton spawn egg used in a fortress chunk yields a wither skeleton **and keeps the egg**.
* **`/summon`.** `/summon minecraft:skeleton` is converted; `/summon minecraft:skeleton ~ ~ ~ {}`
  is not, because vanilla skips `finalizeSpawn` when an NBT argument is given, and the event never
  fires.

### The broadcast

`broadcastSkeletonBlockedMessage` returns immediately unless the module's `debug_logging` resolves
to on — `AUTO` by default, which falls through to `globalDebugLogging`, itself `false`. With it
switched on, every player on the server gets this, once per blocked spawn:

```java
Component message = Component
        .literal("🔥 A normal skeleton tried to spawn in a Fortress but was blocked! 🔥")
        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        .append(Component
                .literal("\nLocation: %d, %d, %d".formatted(position.getX(), position.getY(), position.getZ()))
```

The coordinate line carries a `RUN_COMMAND` click event for `/tp @p x y z`, which needs permission
level 2 — a normal player clicking it gets an error. The text is a hardcoded literal; there is no
lang key for it, so it cannot be translated. Sending it also writes one unconditional `INFO` line to
the log per blocked spawn.

### The code and the labels disagree

The module only ever acts **inside** fortress chunks, but most of the prose around it says the
opposite:

| Where | What it says | What happens |
|---|---|---|
| Startup log, `onInitialize` | "Normal skeletons are now banned from the Nether!" | They are banned from fortress chunks only |
| Debug line before the block | "Allowed normal skeleton spawn inside Nether Fortress at {}" | Eighteen lines later that same spawn is cancelled |
| README, class javadoc | "optionally replaces them with Wither Skeletons" | The replacement is unconditional; no switch exists |

The one piece of text that matches the code is the chat message itself: *"A normal skeleton tried to
spawn in a Fortress but was blocked."*

<!-- TODO: fortress-only is what the code does and what this page documents; whether the inverted
     wording in the log lines, the javadoc and the README is the accident, or the scope is, needs a
     decision from Gerry. -->

<!-- vpa:config:start -->
## Configuration

Section `[modules.wither_skeleton]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_wither_skeleton-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| YUNG's Better Nether Fortresses | `betterfortresses:fortress` is accepted next to the vanilla structure, matched by registry key alone — no compile dependency, no `ModList.isLoaded`, and the module works without the mod. Whether those fortresses use vanilla's fortress spawn list at all depends on the structure's own `spawn_overrides`; the module does not care either way. |
| The replacement bypasses every other spawn hook | It is built by hand and `finalizeSpawn` is called directly instead of `EventHooks.finalizeMobSpawn`, so no other mod's `FinalizeSpawnEvent` handler sees the wither skeleton, no spawn-placement rule is consulted, and it is not counted against the mob cap. |
| Mob-cap bookkeeping is off by one, briefly | In a natural spawn pass the skeleton that never appeared is still counted — vanilla increments its pack counters and runs `afterSpawn` after the refused add — while the wither skeleton the module adds is counted nowhere. Both wash out on the next pass, which recounts from the entities actually present. |
| A failed replacement is near-silent | `EntityType.WITHER_SKELETON.create` returning null logs a warning, anything thrown is caught and logged as an error — in both cases the skeleton stays blocked and nothing takes its place. |
| Another mod could clear the flag | The handler runs at `EventPriority.HIGH` and does not cancel the event, so later handlers still run. One calling `setSpawnCancelled(false)` would give you the skeleton *and* the wither skeleton; the module never re-checks. |
| Debug logging is loud | With it on, a busy fortress messages every player on the server and writes an `INFO` line per blocked spawn. |
| Module disabled at startup | `ModuleManager.initializeModules` only calls `initialize()` for modules that were enabled when the config was read, and only `onInitialize` registers the handler, so enabling it later needs a restart. Turning it **off** works at once — `/vpa module disable wither_skeleton`, a command the bundle registers — because `isModuleEnabled()` is re-checked on every event. In the standalone jar the bootstrap always boots the module, so both directions work live. |

## Under the hood

One handler, one file, no mixins and no assets. `onInitialize` registers the module on
`NeoForge.EVENT_BUS`; everything else hangs off `FinalizeSpawnEvent` at `EventPriority.HIGH`:

| Step | Line of defence |
|---|---|
| 1 | `isModuleEnabled()` |
| 2 | `event.getLevel().isClientSide()` — dead code, the event is only fired on the logical server |
| 3 | `event.getEntity() instanceof Skeleton` |
| 4 | `event.getLevel() instanceof ServerLevel`, and its `dimension()` must be `Level.NETHER` |
| 5 | the fortress reference lookup described above |

**How the block is enforced.** The module calls `event.setSpawnCancelled(true)` and leaves the
event itself uncancelled. That flag is honoured late — NeoForge watches for it on the entity's way
into the world:

```java
// NeoForgeEventHandler
@SubscribeEvent(priority = EventPriority.HIGHEST)
public void builtinMobSpawnBlocker(EntityJoinLevelEvent event) {
    if (event.getEntity() instanceof Mob mob && mob.isSpawnCancelled()) {
        event.setCanceled(true);
    }
}
```

Because the event was not cancelled, `EventHooks.finalizeMobSpawn` still calls the skeleton's own
`finalizeSpawn` afterwards. The doomed skeleton therefore gets fully rolled — equipment,
enchantments, group data — and is only dropped when it tries to join the level.

**How the replacement is made.** `replaceWithWitherSkeleton` creates the mob with
`EntityType.WITHER_SKELETON.create(level)`, copies position and rotation from the skeleton with
`moveTo`, calls `finalizeSpawn` with the local difficulty and the *original* `MobSpawnType`, and
adds it with `level.addFreshEntity`. That `finalizeSpawn` call is what hands it the stone sword and
sets `ATTACK_DAMAGE` to 4.0. The spawn type it is handed changes nothing about persistence or
despawning: `Mob.finalizeSpawn` only records it on the mob — readable as `Mob#getSpawnType`, saved
as the `neoforge:spawn_type` NBT tag — and nothing in the wither skeleton's `finalizeSpawn` chain
reads it or sets `persistenceRequired`. The replacement despawns like any other non-persistent mob.
The whole block sits in a `try`/`catch (Exception)`.

| File | Contents |
|---|---|
| `modules/wither_skeleton/WitherSkeletonModule.java` | The handler, the broadcast and the replacement — 203 lines, the whole module |
| `modules/wither_skeleton/config/WitherSkeletonConfig.java` | `buildModuleSpecificConfig` is empty; the module has only the framework's own keys |
| `standalone/wither_skeleton/WitherSkeletonStandalone.java` | `@Mod("vpa_wither_skeleton")`, boots through `StandaloneModuleBootstrap` |

Wither skeleton **drops** are not part of this module. They lived here until 0.12.0 and now belong
to [Mob Drops](mob_drops.md).

## See also

* [Mob Drops](mob_drops.md) — the skull, golden apple and netherite scrap rolls
* [Debug Logging](../guides/debug-logging.md) — how to switch the broadcast on
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
