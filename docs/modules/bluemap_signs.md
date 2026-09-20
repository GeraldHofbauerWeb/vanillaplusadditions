# BlueMap Signs

> **TL;DR** — Put `[bm]` on the first line of any sign and that spot shows up as a labelled,
> icon-picked pin on your server's BlueMap web map — plus `/bmsigns` to place and edit pins that
> have no sign at all.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `bluemap_signs` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | [BlueMap](https://modrinth.com/plugin/bluemap) <sub>tested 5.7</sub> |
| **Download** | [`vpa_bluemap_signs.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_bluemap_signs.jar) · also needs `vpa_core` |
| **Config section** | `[modules.bluemap_signs]` |
| **Since** | `v1.0.0-beta.22` |
<!-- vpa:meta:end -->

## What it does

BlueMap renders the world in a browser, but it knows nothing about what is *in* it. This module
makes the world label itself: the sign you already put at the door of a place becomes the pin for
that place on the web map.

Write four lines on any sign — standing, wall or hanging:

```
[bm]              line 1  the trigger, and the only mandatory line
Hub Station       line 2  the title shown on the map
station           line 3  an icon key (optional)
North line, 3 platforms   line 4  popup text (optional)
```

Finish the edit and the pin is on the map immediately: no reload, no restart, no command. Change
the sign and the pin follows; break it, or take `[bm]` off line 1, and the pin is gone. Nobody
needs a client mod — everything happens on the server, and players see the result in BlueMap's web
UI.

Thirty-four icons ship with the module, from `base` and `shop` to `grave` and `airport`. Line 3
picks one; anything unknown or empty falls back to a plain pin.

Not every place has somewhere to nail a sign to. `/bmsigns add "Kraken Bay" danger Do not swim here`
drops a pin at your feet with no block involved, `/bmsigns addat <x y z> …` puts one anywhere, and
`/bmsigns edit` moves or relabels it afterwards. Both kinds of pin land in the same map layer.

`/bmsigns help` prints the sign format and the full icon list in chat, so nobody has to come back
to this page in the middle of a build.

## In detail

### What makes a sign a marker

Line 1 is compared with the configured `prefix` (default `[bm]`) after both have been trimmed, and
the comparison is case-insensitive: `[BM]` and ` [bm] ` both work. The text is taken through
`Component.getString()`, which strips formatting, so a coloured or glowing sign is read the same as
a plain one — and it is the *raw* line (`getMessage(i, false)`), not the profanity-filtered one.

Lines 2, 3 and 4 become label, icon key and popup detail; all three are trimmed, and the icon key is
lower-cased. Only line 1 must be there. A `[bm]` sign with nothing else on it produces a default pin
labelled `(unnamed)`.

**Both faces are read, and the front wins.** `SignReader.readMarker` tries the front text first and
only falls back to the back. Two different `[bm]` markers on the two faces of one sign are therefore
not two pins — the back one is simply never used, because a sign is one block position and one
marker id.

### When the module looks at a sign

Three entry points, and they cover different things:

| Trigger | Fires on | Effect |
|---|---|---|
| `SignBlockEntity.setText` (mixin, at RETURN) | a player finishing an edit in the sign screen | Re-reads the sign; adds, updates or drops its marker |
| `ChunkEvent.Load` | every server chunk load | Reconciles every sign in that chunk against the stored markers |
| `BlockEvent.BreakEvent` | a **player** breaking a sign block | Drops the marker at that position |

The mixin is the fast path and the one you notice. It hangs on `setText`, which is what
`updateSignText` calls when the edit screen is confirmed — **not** on NBT load. A sign that arrives
with its text already set, from `/setblock`, a structure, a schematic paste or another mod writing
the block entity directly, never passes through it and waits for the chunk pass instead.

`reconcileChunk` is the safety net that makes the module work on a world that already exists. It
walks every block entity of the freshly loaded chunk, collects the `[bm]` signs it finds, upserts
the ones whose data differs from what is stored, and then deletes every stored **sign** marker whose
position falls inside that `ChunkPos` and has no matching sign any more. Command markers are never
touched by it. Switch the module on for the first time and your old signs appear one chunk at a
time, as people walk the world.

`BlockEvent.BreakEvent` exists so that a sign broken by hand disappears from the map instantly
rather than at the next chunk load. It only fires for player breaks — an explosion, a piston, water,
`/fill` or a mod removing the block leaves the pin standing until that chunk reloads.

### What the map gets

Every marker becomes a BlueMap `POIMarker` in a `MarkerSet` with the id `vpa_map_signs`, created on
**every map of every world** that corresponds to the dimension the marker lives in:

| Property | Value |
|---|---|
| Position | block centre — `x + 0.5`, `y + 0.5`, `z + 0.5` |
| Label | line 2, or the literal `(unnamed)` when it is blank |
| Icon | the resolved icon PNG, anchored bottom-centre at (16, 42) for the 32 × 42 images |
| Detail | line 4, HTML-escaped (`&`, `<`, `>`), and attached only when it is not blank |
| `maxDistance` | `max_distance` from the config, 10000 by default |
| Layer name / toggle / hidden | `marker_set_name`, `toggleable`, `default_hidden` |

The key inside the marker set is `<dimension>/<record id>`, e.g.
`minecraft:overworld/s/27762667782208`, so the Overworld, the Nether and the End never collide even
though each dimension keeps its own storage.

The label is passed through as written; only the detail is escaped. Anything HTML-ish on line 2 of a
sign reaches the web UI unaltered.

### Icons

Line 3 (or the `icon` argument) is matched case-insensitively against the 34 keys below. Blank and
unknown keys resolve to `default` rather than failing, so a typo costs you the icon, not the pin.

| Key | Meaning | Key | Meaning |
|---|---|---|---|
| `base` | Main base | `village` | Village |
| `home` | Home | `castle` | Castle |
| `shop` | Shop | `enchant` | Enchanting |
| `farm` | Farm | `anvil` | Smithy / anvil |
| `portal` | Portal | `brew` | Brewery |
| `warp` | Warp point | `treasure` | Treasure / loot |
| `mine` | Mine | `grave` | Grave / death |
| `nether` | Nether | `arena` | Arena / PvP |
| `end` | End | `mountain` | Mountain |
| `spawn` | Spawn | `cave` | Cave |
| `danger` | Danger | `dock` | Harbor / dock |
| `station` | Rail station | `bridge` | Bridge |
| `storage` | Storage | `woods` | Woods / forest |
| `redstone` | Redstone | `bank` | Bank / gold vault |
| `deko` | Decoration | `church` | Church / temple |
| `tower` | Tower | `airport` | Airport / Elytra launch |
| `factory` | Factory / Create machines | `default` | Default pin |

`deko` is the one key that is not English; it is the key as shipped and renaming it would orphan
every sign already using it.

The PNGs are handed to BlueMap once per API enable, through `WebApp.createImage` under
`vanillaplusadditions/icons/<key>`. An icon that cannot be loaded is not an error: that marker gets
`POIMarker.defaultIcon()` and nothing is logged beyond a warning for the upload itself.

### The command

| Command | Level | What it does |
|---|---|---|
| `/bmsigns`, `/bmsigns help` | everyone | Sign format, all 34 icon keys with their descriptions, the command list |
| `/bmsigns list [dimension]` | everyone | Every marker of that dimension, sorted by id |
| `/bmsigns add <label> [icon] [detail]` | 2 | New pin at the block you stand in |
| `/bmsigns addat <x y z> <label> [icon] [detail]` | 2 | New pin at the given position |
| `/bmsigns remove <id>` | 2 | Deletes a command pin |
| `/bmsigns edit <id> label\|icon\|detail <value>` | 2 | Rewrites one field |
| `/bmsigns edit <id> pos here\|<x y z>` | 2 | Moves the pin |

`help` is the only subcommand that works without BlueMap installed; every other one answers
*"BlueMap is not installed on this server."* and does nothing — including `list`, which would
otherwise only read our own storage.

The two `label` arguments are not spelled the same way. `add` and `addat` take a Brigadier *string*,
so a title with spaces needs quotes (`/bmsigns add "Hub Station"`), while `edit <id> label` takes
the rest of the line greedily and must not be quoted. `detail` is greedy in all three.

Every line of `list` carries the id, the label, the icon key in brackets, the source (`SIGN` or
`COMMAND`) and the coordinates. The coordinates are clickable and run `/tp @s <x y z>` as the person
who clicked, so they teleport whoever is allowed to use `/tp` and refuse for everyone else.

**Sign markers cannot be touched from the command.** `remove` and `edit` return
`SIGN_IMMUTABLE` for anything with source `SIGN` and answer *"change or break the sign (line 1) to
edit/remove it."* The tab-completion for `<id>` filters sign markers out entirely, so their ids can
only be reached by typing them in full.

### Persistence and ids

The source of truth is `MapSignData`, a `SavedData` named `vanillaplusadditions_map_signs` — one
file per dimension, next to the other level data. BlueMap's marker set is only a mirror of it:
whenever BlueMap enables, `rebuildAllFromStorage` walks every level and rebuilds the whole set from
storage. A BlueMap restart, a map reload or a fresh server start therefore all end with the map
agreeing with the world, without anyone touching a sign.

Ids are stable and never reused:

| Source | Id | Built from |
|---|---|---|
| Sign | `s/<packed pos>` | `Long.toUnsignedString(BlockPos.asLong())` — a sign at 100/64/-200 is `s/27762667782208` |
| Command | `c<n>` | A counter in the saved data, incremented on every `add` |

Because the sign id *is* the position, a sign marker cannot be moved; it can only vanish and reappear
elsewhere. Command markers keep their `c<n>` id across `edit … pos`, which is what makes them worth
having for anything that moves.

### Without BlueMap

The module still loads and still registers `/bmsigns`; it does not skip initialization. What it does
skip is everything that would produce a marker: `bluemapPresent` stays false, so the sign hook, the
chunk reconcile and the break handler all return at their first line, and **nothing is written to the
saved data**. `/bmsigns help` still prints the full listing, plus a red line saying BlueMap is
missing.

Install BlueMap later and every `[bm]` sign is picked up chunk by chunk as the world is walked —
nothing was lost, because nothing was ever stored.

<!-- vpa:config:start -->
## Configuration

Section `[modules.bluemap_signs]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_bluemap_signs-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_hidden` | boolean | `false` | — | Whether the marker layer starts hidden (players must enable it). |
| `marker_set_name` | string | `"Map Signs"` | — | Display name of the marker layer in the BlueMap web UI. |
| `max_distance` | int | `10000` | 1 ~ 10000000 | Max camera distance in blocks at which markers stay visible; a large value means always. |
| `prefix` | string | `"[bm]"` | — | Trigger text on line 1 of a sign that turns it into a BlueMap marker (case-insensitive, trimmed). |
| `toggleable` | boolean | `true` | — | Whether players can toggle the marker layer on/off in BlueMap. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| BlueMap not installed | Only `/bmsigns help` works. Signs do nothing and nothing is persisted; the module is otherwise inert. |
| Sign removed by anything but a player | Explosion, piston, water, `/fill`, `/setblock`, another mod: the pin stays on the map until that chunk is loaded again and the reconcile pass drops it. |
| Sign created by anything but the edit screen | `/setblock` with sign NBT, structures, schematics: no `setText`, so the pin appears only at the next chunk load of that position. |
| A break that is cancelled afterwards | `BlockEvent.BreakEvent` is handled without checking `isCanceled()`, so a protection mod cancelling the break still costs the marker until the chunk reloads. Read from the source; not reproduced. |
| Editing across dimensions | Only `list` takes a dimension. `add`, `addat`, `remove`, `edit` and the id suggestions all use the level you are standing in, and a correct id from another dimension fails with the same "no marker with id" message as a typo. |
| Module disabled while the server runs | The three event handlers stop, but the command was registered at startup and is never gated: `/bmsigns add|edit|remove` keeps writing to the saved data and keeps pushing to BlueMap. Disabling only takes full effect after a restart, where the module is never initialized and the command never registered. |
| Config changed while the server runs | `marker_set_name`, `toggleable` and `default_hidden` are read when a marker set is built, `max_distance` when a marker is built. Existing ones keep the old values until the next rebuild — a BlueMap re-enable, a restart, or the next edit of that marker. |
| `prefix` changed | Markers created under the old prefix are not dropped retroactively. Each one goes when its chunk next loads and the reconcile pass finds no match. |
| HTML in a label | Only `detail` is escaped. Line 2 reaches the web UI as written. |
| Chunk-load cost | While the module is active and BlueMap is present, every server chunk load iterates that chunk's block entities. Cheap per chunk, but it is not free on a world being explored fast. |
| Standalone jar, custom icons | `BlueMapBridge` loads the PNGs via `getResourceAsStream("/assets/vanillaplusadditions/bluemap_icons/…")`, but the module jar ships only its own classes — all assets live in `vpa_core.jar`. Whether that lookup crosses NeoForge's module layer could not be settled from the repository; if it does not, every marker silently falls back to the default pin. The all-in-one bundle is unaffected. <!-- TODO: verify in game with vpa_bluemap_signs.jar + vpa_core.jar; the fallback is silent, so "plain pins everywhere" is the symptom to look for --> |
| Translations | The 48 `bmsigns` strings exist in `en_us` and `de_de` only. `de_at`, `fr_fr`, `es_es` and `cs_cz` fall back. |

## Under the hood

| File | Role |
|---|---|
| `modules/bluemap_signs/BluemapSignsModule.java` | The four event handlers, the `ModList` gate, the static mixin hook |
| `modules/bluemap_signs/SignReader.java` | Sign text → `Optional<MapSignMarker>` |
| `modules/bluemap_signs/MapSignManager.java` | BlueMap-free orchestration: storage plus mirror pushes |
| `modules/bluemap_signs/MapSignData.java` | Per-level `SavedData`, the source of truth |
| `modules/bluemap_signs/MapSignMarker.java` | The record, and the `SIGN` / `COMMAND` distinction |
| `modules/bluemap_signs/MarkerBackend.java` | The isolation seam (`upsert` / `remove` / `rebuildAll`) |
| `modules/bluemap_signs/IconKey.java` | The 34 keys, their file names and lang keys |
| `modules/bluemap_signs/compat/BlueMapBridge.java` | The only class that imports `de.bluecolored.*` |
| `modules/bluemap_signs/command/BluemapSignsCommands.java` | `/bmsigns` |
| `mixin/bluemap_signs/SignBlockEntityTextMixin.java` | The edit hook |
| `standalone/bluemap_signs/BluemapSignsStandalone.java` | `@Mod("vpa_bluemap_signs")` |

**The optional-mod gate.** This module is the repository's reference implementation of the pattern,
and [Overpacked Extensions](overpacked_extensions.md) cites it as such. `MarkerBackend` is a plain
interface in module code; `BlueMapBridge` is its only implementation and the only class in the whole
module that names a BlueMap type. It is constructed *inside* the `ModList.get().isLoaded("bluemap")`
branch of `onInitialize`, so in a pack without BlueMap the class is never loaded, never verified and
never linked. The manager talks to the interface and nothing else. Getting this wrong — putting the
gate on the class that also holds the optional types — is what crashed mod construction in beta.70
for a neighbouring module.

**The mixin** injects at `RETURN` of `setText(SignText, boolean)` and does nothing but call
`BluemapSignsModule.onSignChanged`. All guarding lives in that static method: module instance,
BlueMap present, module enabled, and `sign.getLevel() instanceof ServerLevel`. The mixin sits in the
common (`"mixins"`) block of `vanillaplusadditions.mixins.json`, so it is applied on both sides, but
the level check means the client half returns immediately.

**No registry content at all.** No items, blocks, entities, recipes, keybinds or network payloads —
events, a command, a `SavedData` and the bridge. That is why the module is server-only: there is not
one `Dist.CLIENT` reference in it, and the only client-visible artefacts are the chat strings.

**Thread safety.** `BlueMapAPI.onEnable` may run on a BlueMap thread, so the rebuild is dispatched
with `server.execute(…)`. `api` and `live` are `volatile`, and every push checks `isLive()`; while
BlueMap is disabled the pushes are dropped and the next enable rebuilds from storage. Everything
else (mixin hook, chunk and break events, commands) is already on the server thread.

**Build dependencies.** `libs/bluemap-5.7-neoforge.jar` is `compileOnly` plus `localRuntime`, and
`libs/flow-math-1.0.4-SNAPSHOT.jar` is `compileOnly` on its own because BlueMap's marker API exposes
flow's vector types from a jar-in-jar. Unlike the four JARs the project's `CLAUDE.md` calls out as
missing, both of these are committed to the repository, so a fresh checkout compiles this module
without downloading anything.

**Standalone jar.** `vpa_bluemap_signs` carries the module classes, the standalone entry point and
the mixin, with a generated `vpa_bluemap_signs.mixins.json`. Its generated `neoforge.mods.toml` lists
only `vpa_core`, NeoForge, Minecraft and the incompatibility with the bundle — the optional BlueMap
dependency that the bundle declares (`ordering="AFTER"`, `side="SERVER"`) is **not** reproduced
there. `BlueMapAPI.onEnable` is a callback, so load order should not matter, but the asymmetry is
real and has not been tested.

**Testing.** This repository has no unit tests, and nothing here has been verified in game as part
of writing this page.

## See also

* [Overpacked Extensions](overpacked_extensions.md) — the same optional-mod gate, and what happens
  when it is put on the wrong class
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
