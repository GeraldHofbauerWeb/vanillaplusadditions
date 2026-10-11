# Chunk Reset Command

> **TL;DR** — An operator-only command that permanently deletes the chunk you are standing in, or
> up to an 11×11 square around you, so the world generator builds that terrain again from scratch.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `chunk_reset` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_chunk_reset.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_chunk_reset.jar) · also needs `vpa_core` |
| **Config section** | `[modules.chunk_reset]` |
| **Since** | `v1.0.0-beta.2` |
<!-- vpa:meta:end -->

## What it does

Stand where the terrain should go away and type `/chunkreset`. Nothing is deleted yet: the command
prints a warning with the chunk coordinates and two clickable buttons. Only a radius greater than 0
gets the chunk count and the `s×s` side length as well. This is what `/chunkreset 1` prints:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠ Chunk Reset Warning
9 chunks (3×3) around [x=12, z=-4] will be PERMANENTLY deleted and regenerated.
All blocks, entities and structures will be LOST!
[✓ Confirm Reset]  [✗ Cancel]
```

Plain `/chunkreset` is the same block with a singular middle line: `Chunk [x=12, z=-4] will be
PERMANENTLY deleted and regenerated.`

Click `[✓ Confirm Reset]` (or type `/chunkreset confirm`; the buttons only run that same command)
and the chunks' entries are cleared from the region file. The next time something loads one of those
positions, the generator builds it again from the current seed and the current datapacks. Use it to
pick up a worldgen change on ground somebody has already explored, to reset a test area, or to hand
a builder's plot back to vanilla terrain.

`/chunkreset <radius>` takes a square around you instead of the single chunk, up to radius 5.

Before using it, know that there is no undo. The old region data is gone, and what comes back is
whatever the generator produces today. The deletion also does not always take: a chunk that is still
in memory with unsaved changes is written straight back into the entry that was just cleared. Both
points are covered in detail below.

## In detail

### The commands

All four need **permission level 4**. The requirement sits on the root literal, so it covers
`confirm` and `cancel` as well.

| Command | Effect |
|---|---|
| `/chunkreset` | Queue the caller's own chunk and print the confirmation prompt |
| `/chunkreset <radius>` | Queue a square of radius 0–5 around the caller |
| `/chunkreset confirm` | Carry out the caller's pending reset |
| `/chunkreset cancel` | Drop the caller's pending reset |

All three handler methods begin with `source.getPlayerOrException()`, so the command needs a real player
behind it: the console, a command block and a `/execute as` with a non-player source all fail with
vanilla's "player required" error. The radius argument is `IntegerArgumentType.integer(0, 5)`, so
brigadier rejects anything outside that range before the module sees it. `MAX_RADIUS` is a private
constant; the ceiling cannot be raised from a config file.

| `radius` | Chunks | Area |
|---|---|---|
| `0` | 1 | 16 × 16 blocks |
| `1` | 9 (3×3) | 48 × 48 |
| `2` | 25 (5×5) | 80 × 80 |
| `3` | 49 (7×7) | 112 × 112 |
| `4` | 81 (9×9) | 144 × 144 |
| `5` | 121 (11×11) | 176 × 176 |

The prompt goes to the caller only (`sendSuccess(…, false)`); the final result is sent with
`allowLogging = true`, so it is also broadcast to the other operators and written to the server log
as an admin command, subject to the usual `sendCommandFeedback` and `logAdminCommands` gamerules.

### What the prompt remembers, and what it does not

A pending reset is one entry in a plain `HashMap<UUID, PendingReset>`, and the record holds three
fields:

```java
private record PendingReset(ChunkPos center, int radius, ResourceKey<Level> dimension) { }
```

Before the first click, note how that plays out:

* **The dimension is part of it** since beta.91 (`81c9867`); before that the record knew only the
  centre and the radius and confirmed into whatever level you stood in. The entry records the level,
  and the confirm refuses outright if you are somewhere else by then, naming both dimensions. Run
  `/chunkreset` in the Nether, walk through a portal and click the button, and nothing happens
  except a red line telling you why.
* **A pending reset dies with the session.** Confirm, cancel or logging out all drop it. It does
  still survive indefinitely while you stay online; clicking a confirm button hours later works.
* **Only the newest one exists.** A second `/chunkreset` replaces the first, and says so: the
  warning block names the square it just displaced.

`cancel` answers either way: "Chunk reset cancelled." when there was something to drop, "No pending
chunk reset to cancel." when there was not.

### What a reset actually does

For every chunk in the square, `resetSingleChunk` does exactly two things:

1. `level.setChunkForced(pos.x, pos.z, false)`, which removes the position from
   `ForcedChunksSavedData` and tells the chunk cache about it.
2. Calls `ChunkStorage.write(ChunkPos, CompoundTag)` on the level's `ChunkMap` with a **null** tag.

`ChunkMap` extends `ChunkStorage` and does not override `write`, so the call ends up on the region
storage for that dimension's `region/` folder, by way of the same `IOWorker` the game itself saves
through. A null tag is vanilla's own delete instruction:

```java
// RegionFileStorage.write
protected void write(ChunkPos chunkPos, @Nullable CompoundTag chunkData) throws IOException {
    RegionFile regionfile = this.getRegionFile(chunkPos);
    if (chunkData == null) {
        regionfile.clear(chunkPos);
    } else {
        …
    }
}
```

The write is asynchronous. `ChunkStorage.write` returns a `CompletableFuture` that the module drops
on the floor, so the per-chunk "succeeded" count means *the deletion was queued*, not *the region
file changed*. A failure inside the worker is logged by vanilla and never reaches chat.

Nothing else is touched. No unload is forced and no flush is requested. The write happens for every
target at once, loaded or not; for a chunk that is still in memory it would not stick on its own,
which is what [taking the write away](#taking-the-write-away) deals with.

### Why a write to a loaded chunk would undo itself

The same `write` is what `ChunkMap.save(ChunkAccess)` calls to persist a chunk:

```java
CompoundTag compoundtag = ChunkSerializer.write(this.level, chunk);
…
this.write(chunkpos, compoundtag).exceptionally(…);
```

A loaded chunk is therefore one autosave away from being written back into the entry that was just
cleared. At 20 tps that is at most 6000 ticks (five minutes), and it happens again when the chunk
unloads and when the server shuts down. The one thing that stops it is the guard at the top of
`save`:

```java
if (!chunk.isUnsaved()) {
    return false;
}
```

So a write would hold for a chunk that has not been modified since its last save, and be silently
reverted for one that has. `LevelChunk.setBlockState` sets that flag, so does a light-section change
and so does a freshly generated chunk. A crop growing, a leaf decaying or a single block placed
between the reset and the unload would be enough.

The chunk you are standing in is loaded by definition, so for a `radius 0` reset this was the
normal path.

### Taking the write away

Every target is cleared the moment the command runs. A chunk that is still in memory is then
**held unsaved** for as long as it stays there:

```java
private static void holdClean(ServerLevel level, ChunkPos pos) {
    LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
    if (chunk != null) {
        chunk.setUnsaved(false);
    }
}
```

It works because of the guard quoted above: `ChunkMap.save` returns `false` for a chunk that is not
unsaved, so a chunk held clean is never written, whether by the autosave, the unload or the
shutdown. The flag is cleared **every tick** because the world sets it again on every block change,
and an autosave landing in a gap between two clears would undo the whole thing.

Anything a player builds in such a chunk while it is held is discarded, which is intended for a
chunk that is being reset.

**Two earlier attempts got this wrong**; the reasoning behind each sounds right until you measure
it:

1. *Write immediately and warn if the chunk is loaded.* The write lands, then the next autosave puts
   the chunk back over it, silently and every time.
2. *Wait for the chunk to unload, then write.* This cannot work.
   `ChunkMap.processUnloads` removes the chunk from the map `getVisibleChunkIfPresent` reads, and
   only afterwards does `scheduleUnload` call `save(chunkaccess)`. So "no longer loaded" arrives
   **before** the write it is supposed to follow. Hooking `ChunkEvent.Unload` and waiting five ticks
   does not save it either: measured on a server with `view-distance=32`, the chunk was back in
   memory **291 ms** after unloading, long before the delay expired.

That unload hook is still in the code, now behind the hold: when a held chunk does leave memory, its
entry is cleared once more five ticks later (`POST_UNLOAD_DELETE_DELAY_TICKS`).

The give-up timer is still there: after **ten minutes** of a chunk refusing to leave memory the hold
is released with a WARN, because holding a chunk unsaved forever would quietly cost every change
made in it.

The queue is **not** gated on the module's `enabled` flag. What is in it was already confirmed by
an operator, so switching the module off stops new orders and lets the accepted ones finish. The
queue does not survive a restart and does not need to: after a restart nothing has loaded the chunks
yet, so the command takes the immediate path.

**Verified in game** on 2026-09-24 against a live 1.21.1 server: `/chunkreset` on a chunk holding a
small stone-brick build, then flying out and back. The region-file header entry for that chunk went
from two sectors to one, with the freshly generated chunk written in place of the build.

### What survives a reset

| Data | Folder | Cleared? |
|---|---|---|
| Blocks, block entities, heightmaps, structure starts | `region/` | Yes; that is the entry the null write clears |
| Persistent entities (mobs, item frames, armour stands, dropped items) | `entities/` | **No**: `ServerLevel` keeps them in a separate `EntityStorage`, which the module never touches |
| Points of interest (beds, workstations, portals) | `poi/` | **No**: they sit in a separate `SectionStorage` as well |

Entities have lived in their own region files since 1.17 and are loaded per `ChunkPos` independently
of the chunk data, so the mobs that were standing in a reset chunk come back with the regenerated
terrain, on top of whatever the generator has since put there. The chat warning saying "All blocks,
entities and structures will be LOST!" is accurate for blocks and wrong for entities. This, too, is
deduced from the source and has not been reproduced in game.

### Chunks that stay loaded anyway

Step 1 above removes a **vanilla `/forceload` ticket and nothing else**. `ServerLevel.setChunkForced`
only touches vanilla's forceload bookkeeping: it drops the position from `ForcedChunksSavedData` and,
when the position really was in there, calls `ServerChunkCache.updateChunkForced`, which removes the
matching `FORCED` ticket from the `DistanceManager`. Every other reason a chunk might be loaded
survives: NeoForge chunk tickets, the spawn chunks, a nearby player, a Create train, and this mod's
own [Stationary Chunk Loader](stationary_chunk_loader.md), [Minecart Chunk
Loading](minecart_chunk_loading.md) and [Train Chunk Loading](train_chunk_loading.md). A chunk held
by one of those never unloads, which puts it squarely in the rewrite case above.

### Getting out of the way

Before deleting anything, every player of that level whose `chunkPosition()` is inside the square is
teleported and told so ("⚠ You were moved out of a chunk being reset."). The destination is one
chunk beyond the **+Z** edge of the square:

```java
double safeX = center.getMiddleBlockX();
double safeZ = ((center.z + radius) * 16.0) + 17.0;
double safeY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        BlockPos.containing(safeX, level.getMinBuildHeight(), safeZ)).getY();
…
nearby.teleportTo(safeX, safeY, safeZ);
```

The height is looked up once for that column, on the `MOTION_BLOCKING_NO_LEAVES` heightmap, so
everyone lands on its surface: a player at y = 12 in a cave comes out on top, one chunk to the
south. There is no collision check beyond that and no dimension change;
`ServerPlayer.teleportTo(double, double, double)` is a plain connection teleport that keeps the
rotation. Nothing stops them from walking straight back in either; the chunks are still loaded.

Only entries of `level.players()` are considered. Players in other dimensions are irrelevant, and
mobs, item frames and dropped items are not moved at all. Per the table above, the persistent ones
among them are not deleted anyway.

<!-- vpa:config:start -->
## Configuration

Section `[modules.chunk_reset]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_chunk_reset-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| A still-loaded chunk | Cleared at once and then held unsaved, so nothing writes it back; see [taking the write away](#taking-the-write-away). The blocks stay visible until the chunk leaves memory once; leave the area and come back and it has regenerated. |
| Anything built in a held chunk | Discarded. The hold works by marking the chunk as already saved, so changes made while it is held are never written. |
| A chunk that never leaves memory | After ten minutes the hold is released with a WARN, and from then on a save can write it back. Run the command again when the area is not loaded. |
| The queue does not survive a restart | Outstanding entries are logged at shutdown and dropped. Run the command again afterwards; nothing has loaded the chunks yet, so it takes the immediate path. |
| Confirming in a different dimension | Refused. The pending reset records its dimension and the confirm compares it against the level you are in. |
| Entities and POIs | Not deleted: they live in `entities/` and `poi/`, which the module never touches, and the chat warning claims otherwise. |
| Mod chunk loaders, spawn chunks, nearby players | Untouched: only a vanilla `/forceload` ticket is removed. Such a chunk cannot unload, so the deletion cannot stick. |
| Failures are reported | A reset where every write threw prints a red line instead of the green ✓, and a partial one adds a red count of what did not make it. The reason stays in the log. |
| The teleport lands on the surface | The destination column's height is looked up (`MOTION_BLOCKING_NO_LEAVES`), so nobody is dropped inside terrain or left in mid-air. Nothing stops a player from walking straight back in, though. |
| A second `/chunkreset` before confirming | Replaces the pending square and names the one it displaced. Only the newest can be confirmed. |
| Pending resets expire with the session | Held per player UUID until confirm, cancel, logout or restart. |
| Console and command blocks | Rejected. All three handlers call `getPlayerOrException()`. |
| Command name | Plain `chunkreset`, no mod namespace, so it can collide with another chunk-management mod's command. |
| No translations | Every message is a hardcoded English `Component.literal`; the module owns no lang keys at all. |
| Disabled in the config at startup (bundle) | The module is never initialised, never subscribes, and `/vpa module enable chunk_reset` cannot bring the command back, because the subscription does not exist. Restart with `enabled = true`. |
| Disabled at runtime (bundle or standalone jar) | `isModuleEnabled()` is only consulted while commands are being registered, so the already-registered `/chunkreset` keeps working until the next command re-registration. |
| Verified in game once | The module dates from `v1.0.0-beta.2` (the standalone entry point followed in `v1.0.0-beta.23`); beta.91 (`81c9867`) added the dimension check, the surface lookup and the hold-clean mechanism. That version was checked in game on 2026-09-24 (see above). This repository has no tests, and everything else on this page, the entity and POI behaviour included, is read off the module and off vanilla's decompiled 1.21.1 sources. |

## Under the hood

One implementation file of 535 lines, `ChunkResetModule.java`, plus the standalone entry point.
There are no mixins, assets, data files, registries or packets, and no config class of its own.

| Event | Bus | Purpose |
|---|---|---|
| `RegisterCommandsEvent` | game | Builds the whole command tree; returns early unless `isModuleEnabled()` |

| Class | Role |
|---|---|
| `modules/chunk_reset/ChunkResetModule` | Everything: the command tree, the pending map, the teleport, the deletion |
| `standalone/chunk_reset/ChunkResetStandalone` | `@Mod("vpa_chunk_reset")`, boots the same module through `StandaloneModuleBootstrap` |

**The enable gate behaves differently in the two jars.** `onInitialize` subscribes to
`NeoForge.EVENT_BUS` unconditionally and logs `Chunk Reset module initialized - /chunkreset command
ready!`; the only `isModuleEnabled()` check is inside `onRegisterCommands`. In the bundle,
`ModuleManager.initializeModules` filters by the config *before* calling `initialize`, so a module
switched off at startup never subscribes and cannot be revived by a runtime override. The standalone
jar's `StandaloneModuleBootstrap.boot` initialises its one module unconditionally, so there the
`isModuleEnabled()` check really is the only gate and both directions work from the next command
re-registration.

**Why the write goes through reflection.** `resolveChunkStorageWriteMethod` walks
`ChunkStorage.class.getDeclaredMethods()` and takes the first method whose parameters are exactly
`(ChunkPos, CompoundTag)`, marks it accessible and caches it in a `static volatile Method`:

```java
for (Method m : ChunkStorage.class.getDeclaredMethods()) {
    Class<?>[] params = m.getParameterTypes();
    if (params.length == 2 && params[0] == ChunkPos.class && params[1] == CompoundTag.class) {
        m.setAccessible(true);
        chunkStorageWriteMethod = m;
        return m;
    }
}
```

The comment in the code gives the reason: the method name is not hardcoded, so a mapping difference
does not break the call. In 1.21.1 `write(ChunkPos, CompoundTag)` is the only declared method with
that shape, which makes "first match" deterministic there. The one field hop on the way,
`ServerChunkCache.chunkMap`, needs no accessor, because the field is `public final` and `ChunkMap`
is a public subclass of `ChunkStorage`; there is no Mixin dependency anywhere in this module. The
cache is written without synchronisation, which is harmless: two threads would resolve the same
`Method`. If no such method is found, the `NoSuchMethodException` is caught per chunk in
`resetSingleChunk`, logged, and counted as a failure; the `Failures are reported` row above says
what of that reaches chat.

**A dead branch.** `executeConfirm` guards with
`if (!(source.getLevel() instanceof ServerLevel serverLevel))`, whose failure message reads "This
command can only be used in a server level." It can never fire: in 1.21.1 `CommandSourceStack` is
declared `public ServerLevel getLevel()`.

## See also

* [Stationary Chunk Loader](stationary_chunk_loader.md): one of the things that keeps a reset chunk
  loaded, and so alive
* [Minecart Chunk Loading](minecart_chunk_loading.md), [Train Chunk Loading](train_chunk_loading.md):
  the same, on rails
* [Configuration Guide](../guides/configuration.md): where the config file lives
* [All modules](../../README.md#-modules)
