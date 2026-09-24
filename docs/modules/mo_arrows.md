# Mo' Arrows

> **TL;DR** — An arrow crafted from an arrow and a fire charge: it flies burning, sets alight whatever it hits, and starts a real fire where it lands — the part even a Flame-enchanted bow has never done.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `mo_arrows` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_mo_arrows.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_mo_arrows.jar) · also needs `vpa_core` |
| **Config section** | `[modules.mo_arrows]` |
| **Since** | `v1.0.0-beta.87` |
<!-- vpa:meta:end -->

## What it does

<img src="../img/items/fire_arrow.png" width="72" align="right">

Adds one arrow so far, and the module is named for the ones that may follow.

| Item | ID | Notes |
|---|---|---|
| **Fire Arrow** | `vanillaplusadditions:fire_arrow` | Flies burning, ignites what it hits, and starts a fire where it lands |

```
Arrow + Fire Charge   (shapeless)   →   1x Fire Arrow
```

A Flame-enchanted bow already sets mobs alight. What it has never done is start a fire on the
ground — and that is the half this arrow adds. Everything else it does is vanilla's own behaviour
for a burning arrow, not a re-implementation:

* it sets what it hits alight for five seconds, Endermen excepted;
* it lights TNT, campfires and candles;
* water puts it out, and so do rain and powder snow; an extinguished Fire Arrow lays no fire — it
  is an ordinary arrow again until it is shot anew.

It works from a bow, a crossbow and a dispenser, and it can be picked up and shot again. The
dispenser had to be taught: vanilla registers a dispense behaviour per arrow item —
`minecraft:arrow`, `tipped_arrow`, `spectral_arrow` — and anything without one is simply ejected as
an item, which is what the Fire Arrow used to be.

## Why it is built this way

The arrow entity is vanilla's `Arrow`. `FireArrowItem` only overrides the two factories `ArrowItem`
already has — `createArrow`, which bows and crossbows go through, and `asProjectile`, the dispenser
factory — and lights the result for a hundred seconds, which is exactly what the Flame enchantment
does. No arrow stays airborne for anything like that long, so it is guaranteed to arrive burning.
The `asProjectile` half is reached because the module registers the behaviour itself: a dispenser
only shoots an item as a projectile if a `ProjectileDispenseBehavior` exists for it in
`DispenserBlock.DISPENSER_REGISTRY`, and everything else falls through to
`DefaultDispenseItemBehavior`, which just ejects the stack. `MoArrowsModule.onModCommonSetup` calls
`DispenserBlock.registerProjectileBehavior(FIRE_ARROW.get())` inside `event.enqueueWork`, because
that registry is a plain map and mod setup runs in parallel. (Vanilla's other caller of
`asProjectile`, the ominous item spawner, only ever holds what a datapack or command puts in it.)

### Where the fire goes

Not simply "in front of the block that was hit". That is the first candidate and it is all a shot
into flat ground needs — the struck face points up, the block above it is air, the ground below
carries the fire. Outdoors it stops being enough almost immediately, so two more rules apply:

* **The arrow's own block is the second candidate.** Shoot sideways into a bush and the arrow stops
  *inside* the foliage, not in front of it; the block off the struck face is then frequently another
  leaf, and vanilla's `canBePlacedAt` refuses on `!isAir()`. The block the arrow itself occupies is
  the air it just flew through — empty by construction and touching what was hit.
* **A replaceable block may give way.** A snow layer is two pixels high: an arrow shot at
  snow-covered ground passes straight through it and strikes the ground underneath, so the block off
  the struck face *is the snow layer*, and the arrow ends up in it as well. Both candidates then hold
  snow rather than air. Since snow is declared `replaceable()` in vanilla, it is now accepted as long
  as a fire would survive there — the layer is replaced by the fire. Tall grass and ferns come along
  for the same ride.

Verified in game on 2026-09-24 across 18 impacts (11 from above, 7 from the side), with the
per-impact diagnostics switched on: no refusals, and the side hits into snow-fronted blocks show
`fire set at …, which held minecraft:snow[layers=1]`.

If neither candidate works, nothing is set alight and — with `debug_logging` on — one line says which
block was in the way. Those lines are INFO rather than DEBUG on purpose: a dedicated server's log
configuration drops DEBUG entirely, so a `.debug()` call would never reach the log of the one machine
where this needs diagnosing.

The fire it lays hangs off `ProjectileImpactEvent` rather than an overridden `onHitBlock`, because
overriding that method would mean registering an entity type, writing a renderer and sending a spawn
packet — for one line of behaviour that cannot desync anyway. The handler recognises our arrow by the
stack it would drop:

```java
arrow.getPickupItemStackOrigin().is(FIRE_ARROW.get())
```

and then places the fire the way a fire charge places it:
`BaseFireBlock.canBePlacedAt` → sound → `setBlockAndUpdate(BaseFireBlock.getState(...))` →
`GameEvent.BLOCK_PLACE`, on the face the arrow struck. The sound is not a fire charge's: it is
flint and steel's `SoundEvents.FLINTANDSTEEL_USE`, pitch formula and all. Two smaller things differ
too — the handler has no equivalent of the fire charge's `getToolModifiedState(FIRESTARTER_LIGHT)`
branch (a burning arrow lights campfires and candles through their own `onProjectileHit` instead),
and it hands `canBePlacedAt` the struck face where the fire charge hands it the user's horizontal
facing — the argument `BaseFireBlock.isPortal` derives a nether portal's axis from.
The event is never cancelled, so the arrow still sticks where it landed.

**The texture is vanilla's arrow, set alight.** `scripts/gen_fire_arrow_texture.py` takes
`arrow.png` from a client jar and does three things; the result is committed, so the build never
needs a client jar. Every colour is sampled from `campfire_fire.png`.

1. **The head is recoloured** — a plain colour table, no positional mask needed, because the head's
   four greys (`#FFFFFF`, `#D8D8D8`, `#969696`, `#444444`) occur *only* there: the fletching has its
   own greys and the shaft is brown. They map in the same brightness order onto `#F9EBAB`,
   `#EFCD56`, `#C96C03`, `#B13F00`.
2. **The shaft behind the head glows** — three wood pixels turn orange and ember.
3. **Seven flame pixels are added** — a tongue over the edge and a trail running back along the
   shaft, in cells vanilla leaves empty.

Twenty-four pixels differ from vanilla in total. The fletching and the rear two-thirds of the shaft
stay byte-identical. The script verifies the source before writing — the head palette must be
present, no unknown colours may appear, and every flame cell must be empty — and aborts rather than
produce a silently wrong texture if a future Minecraft version redraws the arrow.

A first draft reused vanilla's two-layer tipped-arrow model instead and was dropped again. The
reason recorded while the module was written: those layers are not cut the way their names suggest —
`tipped_arrow_base.png` is *only* the shaft and fletching, while `tipped_arrow_head.png` carries the
head plus the scattered potion drips. Neither that split nor how the result looked can be checked
from this repository; no vanilla client jar is vendored here.

**The tag is load-bearing.** Bows accept ammunition by the `minecraft:arrows` item tag, so
`data/minecraft/tags/item/arrows.json` adds the Fire Arrow to it with `replace: false`. Without that
file the item exists but no bow will fire it.

<!-- vpa:config:start -->
## Configuration

Section `[modules.mo_arrows]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_mo_arrows-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `light_fires` | boolean | `true` | — | Lets a Fire Arrow start a fire where it lands, exactly as a thrown fire charge would. With it off the arrow still burns in flight, still sets what it hits alight for five seconds and still lights TNT, campfires and candles — it simply leaves the ground alone. Only the block half is switchable; igniting on hit is vanilla's own behaviour for a burning arrow and cannot be separated from the arrow being lit. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| **It really does start fires** | A Fire Arrow is an arson tool in the hands of anyone who can craft one. `light_fires = false` takes that half away and leaves the rest: the arrow still burns, still ignites what it hits, still lights TNT and campfires. |
| Infinity | Does not apply: vanilla's Infinity only refunds plain `minecraft:arrow`, so Fire Arrows are always consumed. |
| Skeletons | Unaffected. Mobs shoot vanilla arrows; nothing here changes what they fire. |
| Extinguished in flight | `AbstractArrow.tick` clears the arrow's fire in water, in rain that reaches it under open sky, and in powder snow. It then lands as an ordinary arrow — the handler re-checks `isOnFire()` before laying anything — so in a rainstorm a Fire Arrow shot across open ground lays nothing at all. |
| Module disabled at startup (bundle) | `ModuleManager.initializeModules` only calls `initialize()` on modules the config had enabled, so `onInitialize` never runs and `vanillaplusadditions:fire_arrow` is never registered — no item, no recipe, no handler, and Fire Arrows already in inventories or chests meet an unknown item id on the next load. To take only the fires away, use `light_fires = false`. |
| Module disabled at startup (standalone jar) | `StandaloneModuleBootstrap` initialises unconditionally, so in `vpa_mo_arrows` the item stays registered and only the gates bite: the handler returns on its `isModuleEnabled()` check and the recipe is no longer injected. Switching the module off at runtime does nearly the same in the bundle — except that the recipe already injected into the `RecipeManager` survives until the next `/reload`. |

## Under the hood

| File | Part |
|---|---|
| `modules/mo_arrows/MoArrowsModule.java` | item registration, recipe, the fire-laying event handler |
| `modules/mo_arrows/item/FireArrowItem.java` | lights the arrow in both of `ArrowItem`'s factories |
| `scripts/gen_fire_arrow_texture.py` | derives the texture from vanilla's `arrow.png` |
| `assets/vanillaplusadditions/textures/item/fire_arrow.png` | the committed result, 24 pixels off vanilla |
| `data/minecraft/tags/item/arrows.json` | makes it usable as ammunition |

**Recipe in code, not JSON.** Like every recipe in this mod, it is injected through
`AddReloadListenerEvent` and re-applied on every server resource reload — datapack recipe files do
not load reliably here; see [Custom Crafting Recipes](custom_crafting_recipes.md) for the reasoning.

**Testing.** This repository has no unit tests, and the module's fact sheet still marks it
`NOT VERIFIED IN GAME YET (2026-09-20)` — it compiled and passed Checkstyle, the client deploy was
still pending. Check in game: craft one, shoot a grass block and a mob, then shoot through water and
confirm the wet arrow lays nothing.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Custom Crafting Recipes](custom_crafting_recipes.md) — why recipes are built in code here
* [Tipped Arrows](tipped_arrows.md) — the other module that touches arrows
* [All modules](../../README.md#-modules)
