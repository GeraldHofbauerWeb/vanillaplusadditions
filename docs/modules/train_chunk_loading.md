# Train Chunk Loading

> **TL;DR** — A blue Create train track that force-loads the chunks around any train rolling over
> it, so the drills, deployers and item transfers on board keep running with no player anywhere
> near.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `train_chunk_loading` |
| **Side** | Client + Server |
| **Requires** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Works with** | — |
| **Download** | [`vpa_train_chunk_loading.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_train_chunk_loading.jar) · also needs `vpa_core`, `vpa_debug_overlay` |
| **Config section** | `[modules.train_chunk_loading]` |
| **Since** | `v1.0.0-beta.58` |
<!-- vpa:meta:end -->

## What it does

Create keeps a train moving whether or not anyone is watching. What it does not keep doing is run
the machinery bolted to it: outside loaded chunks the carriage's contraption is not there to tick,
so a mounted drill cuts nothing, a deployer places nothing, and a Portable Storage Interface moves
nothing. The train reaches the far station on time and empty.

The **Chunk Loader Track** is a real Create track — same placement, same curves, slopes, girders and
crossings, blue instead of brown. While a carriage is over one, a square of chunks around it stays
force-loaded *and ticking*, and is released again a quarter of a minute after the last carriage has
gone. Lay a few along a line and the loaded corridor rolls with the train.

<img src="../img/items/chunk_loader_track.png" width="64" alt="Chunk Loader Track">

One rule decides whether a line works, and it is the one thing worth reading twice: **a loader track
that is itself in an unloaded chunk cannot see the train.** Tracks must therefore be spaced no
further apart than `chunk_load_radius × 16` blocks — 32 blocks at the defaults — so that each one is
already inside the square the previous one is holding open. A Ponder entry (hold **W** on the item)
walks through this in game.

## Why it exists

The carriage entity is created and destroyed with the chunk under it. Read out of Create 6.0.9's
shipped bytecode — Create is here only as a binary jar
(`implementation files("libs/create-1.21.1-6.0.9.jar")` in `build.gradle`), not as a source
dependency:

```
com.simibubi.create.content.trains.entity.Carriage
  public void manageEntities(Level level)     ← one call per carriage, out of Train.tick(Level)
      no entity yet:
          CarriageEntityHandler.isActiveChunk(level, positionAnchor)
              true → dimensional.createEntity(level, …)
      entity present:
          CarriageEntityHandler.validateCarriageEntity(entity)
              !isActiveChunk(level, entity.blockPosition())  →  entity.leftTickingChunks = true
          !isAlive() || leftTickingChunks || discard
              → dimensional.removeAndSaveEntity(entity, …)
```

`Train.tick` calls `Carriage.travel(…)` in the same pass, so the *travelling* never stops — the
train keeps advancing along the track graph, arrives, brakes, departs again. Only the
`CarriageContraptionEntity`, and with it every block entity mounted on the carriage, blinks out of
existence the moment `isActiveChunk` says no. That is the whole gap this module closes, and it is
also why the module's own detection is entity-based: where the carriage entity exists, the
contraption already works; a track only has to notice it and hold the neighbourhood open long enough
for the next track to take over.

## In detail

### Spacing

An active track forces a Chebyshev square of `(2R + 1)²` chunks centred on its own chunk — 25 chunks,
80 × 80 blocks, at the default radius of 2. What the square guarantees is not 80 blocks in every
direction but `R × 16`: in the worst case the track sits flush against the edge of its own chunk, and
then only `R` whole chunks lie ahead of it. Hence the rule of thumb, and hence its exact form:

| `chunk_load_radius` | Forced square | Maximum spacing |
|---|---|---|
| 0 | 1 chunk | — (holds its own chunk and nothing ahead) |
| 1 | 3 × 3 | 16 blocks |
| 2 (default) | 5 × 5 | 32 blocks |
| 4 | 9 × 9 | 64 blocks |
| 8 | 17 × 17 | 128 blocks |

Radius 0 is a corner case rather than a setting: the square never reaches past the track's own chunk,
so nothing ahead of the train is ever opened and the chain cannot advance on its own.

### Marking a track active

`EntityTickEvent.Post` in `TrainChunkLoadingEvents` filters to `CarriageContraptionEntity` on a
`ServerLevel` and then throttles itself:

```java
if ((carriage.tickCount + (carriage.getId() & 7)) % SCAN_INTERVAL != 0) {
    return;
}
```

Each carriage scans on one tick in ten, and the low three bits of the entity id spread the carriages
of one train across eight of those ten phases rather than having them all scan together.

The scan walks the carriage's whole bounding box footprint — `floor(minX)…floor(maxX)` by
`floor(minZ)…floor(maxZ)` — over a four-block-tall band from **two blocks below** the box's floor to
**one above** it. Two below is what catches the bogey offset and the block a carriage is climbing off
on an ascending section; the footprint rather than the entity anchor is what catches long and
diagonally rotated carriages. Every Chunk Loader Track in that band is stamped with the current game
time.

### Forcing and releasing

`LevelTickEvent.Post` reconciles each server level once every ten game ticks (`gameTime % 10 == 0`):

| Per active track | Action |
|---|---|
| Last seen ≤ `active_timeout_seconds × 20` ticks ago, not yet forced | Force its `(2R+1)²` chunks, record the position in the level's saved data |
| Last seen ≤ timeout, already forced | Nothing |
| Last seen **>** timeout | Release its chunks, drop it from the saved data, forget it |

The chunks go in as **ticking** tickets — `controller.forceChunk(level, railPos, x, z, true, true)`
— because a merely loaded chunk would not run the machines, which is the entire point. The ticket
owner is the track's own `BlockPos`, so two tracks whose squares overlap hold their tickets
independently and neither can release the other's.

Worst case from carriage to loaded chunk is nine ticks waiting for the next scan plus nine waiting
for the next reconcile: **18 ticks, a little under a second**. A train has to cross a whole chunk
in less than that to outrun the corridor, which is why the spacing rule and not the latency is the
thing that bites.

The timeout comparison is strict (`now - lastSeen > timeoutTicks`), so the default 15 seconds is
300 ticks of grace after the last carriage left.

### Players offline, and restarts

`ServerTickEvent.Post` holds one server-wide flag, `forcingEnabled`, and only its *transitions* do
any work:

* **off → on** (server start, or the first player joining when `only_while_players_online` is true):
  `resume()` re-forces every track position in the level's saved data and stamps it active, so a
  train parked mid-line starts moving again instead of waiting for a player to walk out to it.
* **on → off** (the last player leaving): `releaseAll()` drops every ticket this module holds and
  clears the in-memory active set, but **keeps** the persisted positions.

The asymmetry is deliberate. A track that simply timed out went through the release path, which
*does* remove it from the persisted set — so only tracks that genuinely had a train standing on them
when the server emptied are resumed. `resume()` stamps them with the current game time, so if the
train has since gone the entry lives one more timeout and then clears itself.

The persisted set is a `SavedData` named `vanillaplusadditions_train_chunk_loader`, holding a single
long array of packed block positions under the key `active_rails`. On world load the ticket
controller's validation callback drops every ticket the module owned in the previous session; the
active set is rebuilt from the saved data and from train movement, never from stale tickets.

### The debug overlay

`ChunkLoaderTrackBorderRenderer` plugs into the mod's [shared debug overlay](debug_overlay.md), so it
appears only while that overlay is switched on (numpad **+**) *and* the player is wearing goggles —
Create's Engineer's Goggles in the helmet or a Curios slot, or anything in the
`vanillaplusadditions:arm_goggles` tag. Every chunk within `overlay.chunk_border_scan_radius` that
contains a loader track is outlined over `overlay.chunk_border_vertical_span` blocks above and below
the track: **blue** for idle, **red** for currently loaded. Only the outer boundary of each
same-state region gets a translucent fill; internal walls between neighbouring chunks of the same
colour are culled, or the fills would stack into fog.

Two honest caveats. The red state is computed client-side from the carriages the client can see, with
no networking — it is a good approximation of the server's forced set, not a readout of it. And the
scan behind it is brute force: every ten client ticks it walks `(2r+1)²` chunks × all non-empty
sections × 16³ blocks, up to 33 × 33 chunks at the maximum radius of 16. It runs only while the
overlay is live, so it costs nothing passively, but raising the scan radius is expensive.

## Items, blocks and recipes

| | Block / item | Details |
|---|---|---|
| <img src="../img/items/chunk_loader_track.png" width="40"> | **Chunk Loader Track** | `vanillaplusadditions:chunk_loader_track`. A `TrackBlock` carrying the four properties Create's own track sets (map colour `METAL`, strength 0.8, metal sound, no occlusion), but built from a bare `BlockBehaviour.Properties.of()` rather than Create's andesite base and without Create's `forceSolidOn()`. On a custom `TrackMaterial` `vanillaplusadditions:chunk_loader`, track type `STANDARD`. The item is Create's `TrackBlockItem`, so placement — curves, bezier connections, the drag-to-connect preview — behaves exactly like the normal track. |

**Recipe** — shaped, category *misc*, eight tracks in and eight out:

```
 T T T        T = Create Train Track
 T E T        E = Ender Pearl
 T T T
```

Per the project convention it is registered in code rather than as a datapack JSON: a reload listener
added in `AddReloadListenerEvent` merges `vanillaplusadditions:chunk_loader_track` into the
`RecipeManager` on every datapack reload, gated on the module being enabled. If `create:track`
resolves to `minecraft:air` the recipe is skipped and a warning is logged rather than a broken
recipe registered.

**Drops** — `getDrops` is overridden to return exactly one of the block, ignoring the loot
parameters entirely. That covers breaking it by hand, by explosion, and Create's wrench pickup, which
goes through `Block.getDrops` as well. There is no loot table JSON, by the same convention.

**Tags** — two small datapack files add the block to Create's own `#create:tracks` and
`#create:girdable_tracks`, both with `"replace": false` and the entry marked `"required": false` so
they are inert without Create. Create's own copies of both tags contain exactly `create:track`.

### Models and textures

Nothing here is new geometry. The 76 blockstate variants (19 shapes × turn × waterlogged) resolve to
12 models of ours plus `minecraft:block/air` for `shape=none`. Four of the twelve simply inherit from
a `create:block/track/…` parent and swap the textures (`x_ortho`, `z_ortho`, `cross_ortho`,
`teleport`); the other eight load one of Create's OBJ meshes through the NeoForge OBJ loader — the
ascending piece, five crossings and two diagonals. Curved bezier connections add three more meshes of
the same kind, wrapped as Flywheel `PartialModel`s: `tie.obj`, `segment_left.obj` and
`segment_right.obj`.

What is ours is three 32 × 32 textures, and those are Create's `standard_track`, `standard_track_mip`
and `standard_track_crossing` with every opaque pixel hue-rotated into the blue. Brightness survives
the rotation intact: across all 648 opaque pixels of the main texture the brightest channel keeps its
exact value, and in ours that channel is always blue. The wooden sleepers therefore go
`#59473E → #073859` and `#70574C → #054570`, while the metal rails keep their grey and pick up only a
faint cast (`#6C6E77 → #657077`). That is why the block reads as "a track, but blue" at a glance and
still tiles cleanly against ordinary Create track.

<!-- vpa:config:start -->
## Configuration

Section `[modules.train_chunk_loading]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_train_chunk_loading-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `active_timeout_seconds` | int | `15` | 1 ~ 300 | How long a loader track stays active and keeps chunks loaded after the last train carriage passed over it. Converted to ticks as value * 20. |
| `chunk_load_radius` | int | `2` | 0 ~ 8 | Chebyshev chunk radius force-loaded around an active loader track (0 = only its own chunk, 1 = 3x3, 2 = 5x5); acts as the rolling-load lookahead for a moving train. |
| `only_while_players_online` | boolean | `true` | — | Only force-load chunks while at least one player is online; false keeps loading with nobody online (e.g. perpetual loops). |
| `overlay.chunk_border_scan_radius` | int | `8` | 1 ~ 16 | Debug overlay: how many chunks around the player are scanned for loader tracks to draw chunk borders for. |
| `overlay.chunk_border_vertical_span` | int | `24` | 4 ~ 256 | Debug overlay: vertical extent in blocks above and below the track of the rendered chunk border band. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Create absent | Nothing is registered: `onInitialize()` sits behind `shouldInitialize()`, which is `ModList.get().isLoaded("create")`. No block, no item, no handlers, no recipe. `onCommonSetup()` and `onClientSetup()` are *not* behind that gate — `ModuleManager` dispatches them to every config-*enabled* module — so they still run. `onCommonSetup()` dies on its own argument: `CHUNK_LOADER_TRACK.get()` on a register that was never bound to the bus throws `NullPointerException: Trying to access unbound value` before `ChunkLoaderTrackCompat` is entered. In the bundle `ModuleManager` wraps both calls in `catch (Exception …)`, which does swallow that unbound-holder NPE. `onClientSetup()` then runs on, and its two statements fare differently: registering `ChunkLoaderTrackBorderRenderer` with the overlay survives, because the renderer's only Create reference (`CarriageContraptionEntity`) sits inside `clientTick`, not in a field or the constructor — and `clientTick` is only dispatched while the overlay is switched on. The call after it, `ChunkLoaderTrackPonder.register()` → `PonderIndex.addPlugin`, resolves the `PonderPlugin` interface; those are `net.createmod.ponder` types, which ship inside Create's jar-in-jar, so without Create that is a `NoClassDefFoundError` — an `Error`, not an `Exception`, which the guard does not catch. The standalone jar has no guard at all (next row). |
| Standalone jar without Create | `vpa_train_chunk_loading` declares required dependencies on `vpa_core` and `vpa_debug_overlay` and **no dependency on `create`**, so it will happily load into a pack that has no Create. Registration is skipped as above, but `StandaloneModuleBootstrap` forwards `FMLCommonSetupEvent` and `FMLClientSetupEvent` to the module with no `try`/`catch` at all, so the same `PonderIndex.addPlugin` call ends client setup — and had it got past that, the overlay's first tick would resolve `CarriageContraptionEntity`. Read off the source, not reproduced: install Create if you install this jar. |
| Loader track in an unloaded chunk | Cannot see the train and never activates. This is the spacing rule, not a bug — Create's simulated train has no entity for anything to notice. |
| Module disabled while the server runs | The carriage scan, the reconcile, the player gate and the recipe listener all return early on `isModuleEnabled()`, including the reconcile that would release chunks. Tickets already held stay held until the level unloads (the validation callback then drops them on the next world load). Disable it with nothing running, or restart. |
| Create updated, `validBlocks` reflection fails | Logged as a warning at common setup. The track still places, connects and loads chunks; only the block entities carrying **curved** connection data stop surviving a chunk reload, so bezier curves come back straight. That warning line is the symptom to look for after a Create update. |
| Overlay colours | Mirrored client-side from visible carriages with no networking. Red means "this client thinks a carriage is on a track within the radius", not "the server has this chunk forced". |
| Track broken while its position is persisted | `resume()` re-forces from the saved positions without checking that the block is still there. A stale entry holds its square for one timeout and is then cleaned up. |
| Recipe injection | The listener copies the whole recipe map, adds one entry and calls `RecipeManager.replaceRecipes`, once per datapack reload. Several modules stack this same pattern. |
| Localisation | The block name and the five Ponder strings exist in `en_us` and `de_de` only; `cs_cz`, `de_at`, `es_es` and `fr_fr` fall back to English. |

## Under the hood

No mixins, no commands, no keybinds of its own. The numpad **+** that reveals the chunk borders is
`key.vanillaplusadditions.debug_overlay.toggle` and belongs to the
[Debug Overlay](debug_overlay.md) module.

| Event | Bus | Purpose |
|---|---|---|
| `RegisterTicketControllersEvent` | mod | Registers the `vanillaplusadditions:train_chunk_loading` controller and its load-time ticket purge |
| `EntityTickEvent.Post` | game | The carriage footprint scan |
| `LevelTickEvent.Post` | game | Reconcile: force and release |
| `ServerTickEvent.Post` | game | The `only_while_players_online` gate, `resume()` / `releaseAll()` |
| `LevelEvent.Unload` | game | Drops in-memory tracking for the level |
| `AddReloadListenerEvent` | game | Adds the recipe listener |

| Class | Role |
|---|---|
| `modules/train_chunk_loading/TrainChunkLoadingModule` | Registration, the four handlers, the recipe, static config accessors for the renderer |
| `modules/train_chunk_loading/block/ChunkLoaderTrackBlock` | `extends TrackBlock`; the only addition is the `getDrops` override |
| `modules/train_chunk_loading/compat/ChunkLoaderTrackCompat` | The `TrackMaterial`, the block and item factories, the `validBlocks` reflection |
| `modules/train_chunk_loading/compat/TrainChunkLoadingEvents` | The carriage scan |
| `modules/train_chunk_loading/client/ChunkLoaderTrackBorderRenderer` | The chunk-border overlay |
| `modules/train_chunk_loading/client/ChunkLoaderTrackModels` | Three Flywheel `PartialModel`s for bezier curves |
| `modules/train_chunk_loading/client/ChunkLoaderTrackPonder` | The Ponder scene |
| `util/chunkload/ChunkLoaderManager`, `ChunkLoaderData` | **Shared** with the minecart module |
| `standalone/train_chunk_loading/TrainChunkLoadingStandalone` | `@Mod("vpa_train_chunk_loading")` |

**The shared bookkeeping.** `ChunkLoaderManager` and `ChunkLoaderData` are the same classes the
[Chunk Loader Rail](minecart_chunk_loading.md) uses; this module differs only in the `SavedData` name
it passes in and in the ticket controller it registers. The two modules' forced chunks and persisted
sets therefore never touch each other, and a change to the loading semantics of one changes both.

**Keeping Create out of the class loader.** `CHUNK_LOADER_TRACK` and `CHUNK_LOADER_TRACK_ITEM` are
declared as plain `DeferredBlock<Block>` / `DeferredItem<BlockItem>`, and only their *supplier*
bodies (`ChunkLoaderTrackCompat::createBlock`, `::createItem`) name a Create type. The registers are
bound to the mod bus inside `onInitialize`, which only runs once `shouldInitialize()` has confirmed
Create is loaded — so on a Create-less start no Create class is ever resolved from this module's
registration path. The same pattern keeps `ChunkLoaderTrackModels` (a Flywheel type) off a dedicated
server: it is reached only through the lazy suppliers Create evaluates inside its own
`executeOnClientOnly`.

**The `TrackMaterial`.** Create has no registry event for track materials — `TrackMaterialFactory
.build()` self-registers into `TrackMaterial.ALL`. The material is therefore constructed in
`ChunkLoaderTrackCompat`'s static initialiser, which runs during block registration: early enough for
model baking, late enough that Create is known to be present. Its `.sleeper(…)` and `.rails(…)`
ingredients look like a recipe and are not one — they are metadata for Create's own datagen, which
this repository does not run. The real ingredients are the eight tracks and the ender pearl above.

**The reflection.** `extendTrackBlockEntityType` takes Create's track `BlockEntityType`, reads the
private `validBlocks` set, copies it, adds our block and writes it back. NeoForge runs official
Mojang mappings at runtime so the field name is stable, and `ReflectiveOperationException` and
`ClassCastException` are both caught and downgraded to a warning — the failure mode is lost curve
persistence, never a crash.

**Ponder.** `PonderIndex.addPlugin` at client setup, one scene `chunk_loader_track/spacing` attached
to the item, structure NBT at `assets/vanillaplusadditions/ponder/chunk_loader_track/spacing.nbt`.
The literal strings in the Java scene builder are fallbacks for Ponder's editing mode only; the
shipped game resolves `vanillaplusadditions.ponder.chunk_loader_track_spacing.header` and `.text_1`
… `.text_4`, and the lang file's order has to match the order of the `text()` calls.

**Standalone jar.** `vpa_train_chunk_loading` ships only this module's packages plus the two Create
tag files, and is declared incompatible with the all-in-one bundle. Models, textures, lang entries
and the Ponder NBT ride along in `vpa_core`, which carries every module's assets.

**Testing.** This repository has no unit tests, and nothing in it records a runtime test of this
module. Everything above is read from the source and from Create 6.0.9's bytecode.

## See also

* [Minecart Chunk Loading](minecart_chunk_loading.md) — the same mechanics on an ordinary rail, and
  the other user of the shared `ChunkLoaderManager`
* [Stationary Chunk Loader](stationary_chunk_loader.md) — a redstone-powered anchor for a fixed spot
* [Debug Overlay](debug_overlay.md) — the toggle and the goggles requirement for the chunk borders
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
