# Texture Kill

> **TL;DR** — Makes textures you name in the config invisible: either the whole image, or just a
> rectangle of it. Nothing on disk is touched.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `texture_kill` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_texture_kill.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_texture_kill.jar) · also needs `vpa_core` |
| **Config section** | `[modules.texture_kill]` |
| **Since** | `v0.18.0` |
<!-- vpa:meta:end -->

## What it does

Some textures you would rather not see. Create draws a train driver's hat on anything riding a
contraption; a zombie resource pack puts a bowler on every second husk. Neither is worth unpacking
a mod jar, editing a PNG and re-zipping it — and an edit like that is lost with the next update of
whatever shipped the file.

This module does it from the config file instead, in two ways:

* **`killed_textures`** replaces a whole texture with a fully transparent image. The shipped
  default hides Create's two contraption hats, `create:textures/entity/train_hat.png` and
  `create:textures/entity/logistics_hat.png`.
* **`erase_regions`** blanks a rectangle inside a texture and leaves the rest alone — for a skin
  sheet where the hat occupies a known corner and the face does not. The shipped default covers
  62 such rectangles across 38 zombie, drowned and husk skins.

Both work by handing Minecraft a modified copy at load time. A killed texture's original is never
read again. An erased texture's original *is* re-read, from the packs below, on every resource
reload — the eraser needs an unmodified image to blank the rectangles on. Neither file is ever
written to, and both stay intact for anything else that wants them.

The module is client-side. A server neither needs it nor notices it.

## In detail

### One virtual resource pack, two mechanisms

Everything is served by a single generated pack, `vanillaplusadditions:texture_kill`, registered in
`AddPackFindersEvent` with `PackSource.BUILT_IN`, pack format 34 (the 1.21.1 client format) and
`Pack.Position.TOP`, so it is inserted at the high-priority end of the pack list — above vanilla's
own pack and above the mod packs already in the list, but below a server-supplied resource pack,
which vanilla pins there with `fixedPosition = true`. Nothing keeps it in that place afterwards —
see the Compatibility table. It has no files: every lookup is answered from memory, in this order.

| Lookup | Answer |
|---|---|
| Path is in `killed_textures` | A 1×1 fully transparent PNG, generated once at class init |
| Path is in the eraser's cache | The re-encoded PNG with the rectangles blanked |
| Anything else | `null` — the request falls through to the packs below |

`getResource`, `listResources` and `getNamespaces` all return nothing for `PackType.SERVER_DATA`;
`getRootResource` always returns `null`, so the pack has no icon and no `pack.png`. Its display name
is a `Component.literal("VPA Texture Kill")` and is therefore the same in all six shipped languages.

The pack reads the config live on every single lookup rather than caching it, which is why editing
`killed_textures` and pressing F3+T is enough to see the change — see the two limits about
restarts below.

### Killing a whole texture

A killed texture is replaced by a **1×1** transparent PNG, not by a transparent image of the
original size. For a standalone entity or overlay texture — what the shipped defaults are — that is
invisible either way. For an atlas sprite or an animated texture with an `.mcmeta` beside it, the
changed dimensions matter, and nothing in this repository exercises that case.

### Erasing a rectangle

`TextureRegionEraser` is a `PreparableReloadListener` registered through
`RegisterClientReloadListenersEvent`. On every resource reload its `prepare()` step runs off-thread
and, for each texture with at least one region:

1. asks the `ResourceManager` for the original — `resource.isEmpty()` is skipped without a word;
2. decodes it with `ImageIO`, and copies it into a `TYPE_INT_ARGB` image through `Graphics2D`, so
   that writing a zero pixel really produces transparency rather than black;
3. sets every pixel of each rectangle to `0x00000000`;
4. re-encodes to PNG and keeps the bytes.

`apply()` then runs on the game thread and puts those bytes in the static `CACHE` the pack reads
from. The original dimensions are preserved, which is the practical difference from killing the
whole file.

**The clear-first trick.** `prepare()` begins with `CACHE.clear()`. Without it the eraser would read
its own output from the previous reload — the pack sits near the top of the stack, above the packs
the originals come from, so it is asked before them — and each F3+T would erase the rectangles again
on an already-erased image. Clearing first makes the pack fall through to the packs below, so the
eraser always works from an unmodified original. The reasoning is written into the code at
`TextureRegionEraser` lines 28–31 and 50–52.

**The second registration.** `apply()` also decodes each result into a `NativeImage`, wraps it in a
`DynamicTexture` and registers it with Minecraft's `TextureManager` under the same
`ResourceLocation`. The comment above it calls this belt-and-suspenders and names ETF — presumably
the Entity Texture Features mod — as the reason: a mod that caches entity textures outside
`ResourceManager` would otherwise keep serving the unerased image.

It is load-bearing without any such mod too, on the session's **first** resource load. A pack only
joins a namespace's lookup chain if `getNamespaces()` named that namespace when the
`ResourceManager` was built, and `getNamespaces()` derives the erase-side namespaces from `CACHE` —
which is still empty at that moment, because it is filled afterwards, in `apply()`. The
killed-texture half of `getNamespaces()` is not affected — it reads the config directly — but the
shipped `killed_textures` are both `create:`, so nothing else puts `minecraft` into the pack's
namespace set. All 62 shipped erase entries are `minecraft:`, so on that first load none of them can
be served through `ResourceManager`. For the one path a vanilla renderer binds directly,
`minecraft:textures/entity/zombie/drowned.png` — `DrownedRenderer`'s `DROWNED_LOCATION` — the
`TextureManager` registration is what puts the erased image on screen. The other 61 entries — 54
OptiFine-style `optifine/mob/zombie/` paths plus the seven `drowned_necromancer.png` and
`zombie_ollie.png` entries, which are resource-pack additions no vanilla renderer binds — reach a
screen only through a mod like ETF, and the registration helps there only as far as that mod itself
reads from `TextureManager`. From the next reload on the `ResourceManager` path works as well,
because `CACHE` is still populated when the following manager is built.

### Writing an entry

| Key | Format | Example |
|---|---|---|
| `killed_textures` | `namespace:textures/category/name.png` | `create:textures/entity/train_hat.png` |
| `erase_regions` | `namespace:textures/path.png@x1:y1-x2:y2` | `minecraft:textures/entity/zombie/drowned.png@32:0-64:16` |

Coordinates are in pixels and the **end is exclusive**: `@32:0-64:16` clears x ∈ [32, 64) and
y ∈ [0, 16). Several rectangles on one texture are written as several entries with the same path
and different `@` suffixes; `getErasedRegions()` groups them per `ResourceLocation` before the
eraser runs.

Both lists are validated very loosely — `killed_textures` accepts any string containing `:`,
`erase_regions` any string containing `:` and `@`. Everything stricter happens at read time and
**fails silently**: an entry that `ResourceLocation.tryParse` rejects, or a range that
`parseRegion` cannot read, is skipped with no log line at all. A typo therefore looks exactly like
"the module does not work".

Three consequences of how `parseRegion` splits its input are worth knowing:

| Input | Result |
|---|---|
| Negative start coordinate, e.g. `@-8:0-16:16` | Cannot be expressed. The parser splits on the **first** `-`, which leaves an empty start half, so the entry is dropped. |
| Negative or inverted end, e.g. `@16:16-8:8` | Parses fine and erases nothing — the loops are `x1 < x2`, `y1 < y2`. |
| Rectangle larger than the image | Harmless. The loops clamp against `img.getWidth()` and `img.getHeight()`; only the upper edge needs it, because a negative start is unreachable. |

### What the shipped defaults cover

`killed_textures` ships the two Create hats. `erase_regions` ships 62 entries across 38 distinct
files, in four rectangles:

| Rectangle | Entries | What the source comment says it is |
|---|---|---|
| `@32:0-64:16` | 32 | The hat region — the outer head layer at texture offset [32, 0] of a 128×64 skin |
| `@86:28-128:60` | 16 | Hat brim and side panels, JEM headwear offsets [86, 46], [100, 30], [112, 28], [112, 36] |
| `@64:0-82:9` | 12 | A sword or similar object through the head, JEM offset [64, 0] |
| `@70:20-102:36` | 2 | An accessory above the head, JEM offset [70, 20] |

All 62 sit in the `minecraft:` namespace: 54 under `minecraft:optifine/mob/zombie/…`, the other 8
across `drowned.png`, `drowned_necromancer.png` and `zombie_ollie.png` under
`minecraft:textures/entity/zombie/`. The `optifine/` paths are OptiFine-style random-entity and CEM
resources, which means the defaults target a **resource pack's** zombie skins, not a mod — which is
why the module needs no mod dependency of any kind. The comment in the source attributes them to
*AL's Zombies Revamped*.

<!-- vpa:config:start -->
## Configuration

Section `[modules.texture_kill]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_texture_kill-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `erase_regions` | list | `62 entries covering 38 distinct texture files (TextureKillConfig.DEFAULT_ERASED_REGIONS, lines 23-90), e.g. "minecraft:textures/entity/zombie/drowned.png@32:0-64:16"` | — | List of rectangular texture regions to erase (make transparent) inside an otherwise untouched texture (format: namespace:textures/path.png@x1:y1-x2:y2, end coordinates exclusive; repeat the same path with different @-suffixes for multiple regions — getErasedRegions() groups them per ResourceLocation). Validator only requires the string to contain ":" and "@". |
| `killed_textures` | list | `List.of("create:textures/entity/train_hat.png", "create:textures/entity/logistics_hat.png") — 2 entries (TextureKillConfig.DEFAULT_KILLED_TEXTURES, lines 18-21)` | — | List of texture ResourceLocations to replace with a fully transparent 1x1 PNG (format: namespace:textures/category/name.png). Entries are accepted by the config validator if they merely contain ":"; entries that ResourceLocation.tryParse cannot parse are silently dropped at read time. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Toggling `enabled` at runtime | Does nothing until the game restarts. Both handlers check `isModuleEnabled()` when their startup event fires; nothing checks it afterwards. A module disabled at boot never gets its pack or its reload listener, and F3+T will not arm it; a module enabled at boot keeps working even if you switch it off. |
| Editing `killed_textures` or `erase_regions` at runtime | Takes effect on the next resource reload, no restart needed. The pack reads the config per lookup and `prepare()` re-reads it per reload. |
| A killed texture on an atlas or with an `.mcmeta` | The replacement is 1×1, so the sprite changes size. Only whole-file entity and overlay textures are exercised by the shipped defaults. |
| A typo in either list | Silently dropped. There is no warning for an unparsable `ResourceLocation` or an unparsable range. |
| A texture `ResourceManager` cannot resolve | Region erasing skips it silently. A file that resolves but will not decode logs `[TextureKill] Could not decode image`. |
| `debug_logging` | Does not gate this module's messages. Both the warnings and the "Prepared N region-erased textures" line go through the global `Vpa.LOGGER`, not through the module's own helper. |
| Same path in both lists | `killed_textures` wins — `getResource` checks it first. The eraser still runs, but during the reload its "original" is the 1×1 stand-in the pack serves, so it caches a 1×1 copy that is never read. |
| Dedicated server | The config section `[modules.texture_kill]` appears there, because the spec is registered as `ModConfig.Type.COMMON`. Every line of behaviour is behind `@EventBusSubscriber(value = Dist.CLIENT)`, so it is inert. |
| Another pack above ours | Possible. The three arguments of `new PackSelectionConfig(true, Pack.Position.TOP, false)` are `required`, `defaultPosition` and `fixedPosition`. What `required = true` buys is that the pack cannot be switched off — `PackRepository.rebuildSelected` forces a required pack back into the list and `PackSelectionModel.canUnselect` refuses it. Its *place* in the list is not pinned, because `fixedPosition = false`: a server-supplied resource pack, which vanilla creates with `fixedPosition = true`, is inserted above ours, and in the resource-pack screen the player can move another pack past it. The effect differs per mechanism: a pack above ours answers the lookup first — `FallbackResourceManager.getResource` walks its pack list backwards — so a `killed_textures` entry that pack also contains silently stops working; an `erase_regions` entry does not break, because `prepare()` reads whatever image is topmost through the `ResourceManager` and blanks the rectangles on that, and the `TextureManager` registration in `apply()` puts the result on screen regardless. |

## Under the hood

No mixins, no items, blocks or entities, no commands, no keybinds, no lang keys, and not one
resource file — a grep over `src/main/resources` for `texture_kill` finds nothing. The standalone
jar spec in `build.gradle` lists no data globs, no mixins and no module dependencies either.

| Event | Bus | Purpose |
|---|---|---|
| `AddPackFindersEvent` | mod, `Dist.CLIENT` | Builds the pack with `Pack.readMetaAndCreate` and adds it as a repository source. A `null` return is handled — the pack is simply not added. |
| `RegisterClientReloadListenersEvent` | mod, `Dist.CLIENT` | Registers one `TextureRegionEraser`, holding the module's config instance. |

| Class | Role |
|---|---|
| `modules/texture_kill/TextureKillModule` | The module shell. `onInitialize()` is deliberately empty; the annotation does the wiring. |
| `modules/texture_kill/client/TextureKillClientEvents` | The two handlers above, each re-checking `isModuleEnabled()`. |
| `modules/texture_kill/client/TransparentTexturePack` | The virtual pack, serving both mechanisms, plus its `Pack.ResourcesSupplier`. |
| `modules/texture_kill/client/TextureRegionEraser` | The reload listener and the static `ConcurrentHashMap` cache. |
| `modules/texture_kill/config/TextureKillConfig` | The two lists, the defaults, and `parseRegion`. |
| `standalone/texture_kill/TextureKillStandalone` | `@Mod("vpa_texture_kill")` entry point for the standalone jar. |

**Cost per lookup.** `getKilledTextures()` builds a fresh `HashSet` and runs `ResourceLocation.tryParse`
over every configured string, and the pack calls it on every resource lookup that reaches it — the
same for `getNamespaces()` and `listResources()`. With the shipped two-entry default that is
nothing; a list of several hundred entries would be paying for the parse repeatedly.

**Create is not a dependency.** It appears only as text inside the two shipped default strings.
There is no `ModList.isLoaded` check, no `compileOnly` dependency and no compat class anywhere in the
module; without Create those two entries simply never match a lookup. The same holds for ETF,
which appears in two comment lines and nowhere else — not even as a `neoforge.mods.toml` entry or a mod id.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Debug Logging](../guides/debug-logging.md) — the `debug_logging` key this module does not use
* [All modules](../../README.md#-modules)
