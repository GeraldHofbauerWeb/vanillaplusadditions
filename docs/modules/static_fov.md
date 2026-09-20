# Static FOV

> **TL;DR** — Your view stays at the zoom level you set when you sprint, drink a Speed potion or fly
> in creative, instead of the camera pulling back every time you get faster — while zoom-in effects
> like drawing a bow still work.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `static_fov` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_static_fov.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_static_fov.jar) · also needs `vpa_core` |
| **Config section** | `[modules.static_fov]` |
| **Since** | `v1.0.0-beta.37` |
<!-- vpa:meta:end -->

## What it does

Vanilla widens the field of view whenever you move faster than walking pace. Start sprinting and the
camera pulls back; a Speed potion pushes it further; step onto soul sand in Soul Speed boots and it
lurches again. At the default 70° setting a plain sprint is already 80.5° of view, and the number
changes every time you start or stop running.

This module removes the widening and nothing else. Anything that would push the view past your own
FOV setting is cut back to exactly that setting, so the view you chose is the view you get, standing
still or at a dead sprint. Everything that *narrows* the view is untouched: drawing a bow still
zooms in, the spyglass still works, Slowness still closes the view down.

Client-side only, with no effect on the world. It works on any server — including a vanilla one —
because nothing about it is sent anywhere.

## Why it exists

The widening is arithmetic in `AbstractClientPlayer.getFieldOfViewModifier()`, one factor for
creative flight and one for the movement speed attribute:

```java
float f = 1.0F;
if (this.getAbilities().flying) {
    f *= 1.1F;
}

f *= ((float)this.getAttributeValue(Attributes.MOVEMENT_SPEED) / this.getAbilities().getWalkingSpeed() + 1.0F) / 2.0F;
```

A player's `movement_speed` base and `Abilities.walkingSpeed` are both `0.1F`, so the second factor
is "half of one plus however many times faster than a walk you currently are". Sprinting adds a
transient `+30 %` modifier, the Speed effect `+20 %` per level, Soul Speed an `ADD_VALUE` bonus of
`+0.0405` at level I and `+0.0105` for every level above that — `+0.0510` at II, `+0.0615` at III —
while you stand on a block in `#minecraft:soul_speed_blocks` (soul sand or soul soil). Every one of
them widens the view.

Vanilla does have a switch for this: the **FOV Effects** slider in Accessibility,
`fovEffectScale`, default 100 %. But it is applied as a lerp toward 1.0 over the *finished*
modifier — in NeoForge that line sits in the event constructor this module listens to:

```java
this.setNewFovModifier((float) Mth.lerp(Minecraft.getInstance().options.fovEffectScale().get(), 1.0F, fovModifier));
```

`Mth.lerp` does not care which side of 1.0 the value is on. Turning the slider down to 0 % kills the
sprint widening and the bow-draw zoom with it, because the zoom is the same modifier below 1.0. The
slider is symmetric; the complaint is not. This module clamps one side.

## In detail

### What the numbers actually are

`movement_speed` modifiers of the `ADD_MULTIPLIED_TOTAL` kind (sprinting, Speed) multiply; Soul
Speed is an `ADD_VALUE` modifier and lands on the base first. FOV in degrees is your FOV setting
times the modifier, so at the default 70°:

| Situation | `movement_speed` | Modifier | Vanilla FOV | With this module |
|---|---|---|---|---|
| Standing, walking | 0.1 | 1.0 | 70° | 70° |
| Sprinting | 0.13 | 1.15 | 80.5° | 70° |
| Speed I | 0.12 | 1.10 | 77° | 70° |
| Sprinting + Speed I | 0.156 | 1.28 | 89.6° | 70° |
| Sprinting + Speed II | 0.182 | 1.41 | 98.7° | 70° |
| Creative flight | 0.1 | 1.10 | 77° | 70° |
| Creative flight, sprinting | 0.13 | 1.265 | 88.6° | 70° |
| Soul Speed I on soul sand, sprinting | 0.18265 | 1.413 | 98.9° | 70° |
| Soul Speed III on soul sand, sprinting | 0.20995 | 1.55 → **1.5** | 105° | 70° |
| Slowness I | 0.085 | 0.925 | 64.8° | 64.8° |
| Bow at full draw | — | ×0.85 | 59.5° | 59.5° |

The last two rows are the point of the clamp being one-sided. The `1.55 → 1.5` row is vanilla's own
ceiling, from `GameRenderer.tickFov`, and 105° is as wide as the view can ever get at a 70° setting.

Everything in the table assumes FOV Effects at 100 %. At 50 % the vanilla column moves halfway back
toward 70° — in both directions; only the Soul Speed III row is an exception, because the slider
halves the raw 1.55 (to 1.275 → 89.3°) before `tickFov`'s 1.5 ceiling ever applies. Every row the
module clamps still reads 70°, because whatever the slider leaves above 1.0 the clamp takes; the two
narrowing rows move with the slider exactly as they do in vanilla, to 67.4° for Slowness I and
64.8° at full bow draw.

### Where the clamp sits

```
GameRenderer.tickFov()                             once per client tick
 └─ AbstractClientPlayer.getFieldOfViewModifier()  flight ×1.1, speed ratio, bow draw ×0.85…
     └─ ClientHooks.getFieldOfViewModifier()       posts ComputeFovModifierEvent
         └─ the event constructor                  lerp by the FOV Effects slider
             └─ StaticFovClientEvents              ← anything above 1.0 becomes 1.0
 ── back in tickFov                                fov += (result − fov) × 0.5, clamp 0.1…1.5
GameRenderer.getFov()                              degrees = FOV setting (30–110) × that value
```

The modifier is therefore computed once per client tick, not per frame; `getFov` interpolates
between the last two values when it draws.

Two consequences of arriving that late:

* The value the module sees has already been scaled by the FOV Effects slider, so the two settings
  do not fight. Whatever the slider leaves above 1.0, the clamp takes.
* `tickFov` halves the remaining distance each tick rather than jumping, so switching the module off
  in game does not snap the camera — the widening eases back in over about five ticks.

### What it deliberately does not touch

**Values below 1.0 pass through.** The whole handler is a one-sided comparison, so the bow-draw zoom
(`f *= 1.0F - f1 * 0.15F`, up to −15 % over a 20-tick draw) and the narrowing from Slowness or any
other speed *reduction* survive intact.

**The spyglass never gets here.** In first person, scoping returns before NeoForge is asked at all:

```java
} else if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && this.isScoping()) {
    return 0.1F;
}
```

That early `return` skips the event, so the spyglass bypasses this module *and* the FOV Effects
slider. In third person the same spyglass falls through to the speed-derived value instead, and
there the clamp applies like anywhere else.

**FOV changes made elsewhere are not FOV modifiers.** The underwater and lava squeeze
(`×0.857`, itself scaled by the FOV Effects slider) and the death-animation zoom are computed in
`GameRenderer.getFov`, past the event. They are unaffected, which is why diving still narrows the
view with the module on.

**The ceiling is hardcoded.** `1.0f` is written into the comparison and the assignment; there is no
key for a different ceiling, and none for keeping a fraction of the widening. Wanting "half as much"
is what the vanilla FOV Effects slider is for — at the cost of halving the bow zoom too.

<!-- vpa:config:start -->
## Configuration

Section `[modules.static_fov]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_static_fov-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| The client decides, always | The clamp runs in the client's own JVM on the client's own camera. A server neither knows nor cares whether you use it. |
| `/vpa module disable static_fov` against a dedicated server | Flips the *server's* `ModuleManager` only, which no camera consults — the view keeps behaving as the client's config says. In singleplayer client and integrated server share one JVM, so there the command works at once, without a restart or world reload. |
| Spyglass | Bypasses the event entirely in first person (see above). Unchanged with the module on or off. |
| Anything outside `ComputeFovModifierEvent` | Untouched. The fluid and death-animation factors in `GameRenderer.getFov`, and any mod that widens the view through `ViewportEvent.ComputeFov` or its own renderer, are past this hook. |
| A mod listening at a lower priority | The handler runs at the default `NORMAL` priority. A listener at `LOW` or `LOWEST` can raise the modifier again afterwards, and the clamp will not see it. |
| Elytra | Vanilla's expression has no elytra term: gliding is the shared entity flag that `LivingEntity.isFallFlying()` reads (`getSharedFlag(7)`), not `abilities.flying` — that one comes from creative/spectator flight and the server's abilities packet, never from a glide — and it adds no `movement_speed` modifier. So the FOV does not widen during a glide and there is nothing to clamp. (Read off the 1.21.1 source; not re-checked in game.) |
| `debug_logging` | Inert for this module. None of its files contains a logger call, so `ON` produces no output. The only lines that ever name the module are `AbstractModule`'s own lifecycle messages — plus, in the standalone jar, the boot line in `StandaloneModuleBootstrap`. |
| Standalone jar | `static_fov` is listed in `build.gradle`'s `standaloneModules`, so the module also builds as `vpa_static_fov` (entrypoint `StaticFovStandalone`, needs `vpa_core`) next to the bundle. |
| Module registered but not enabled | The event handler is attached regardless (see below) and asks per event. The cost of a disabled module is a static-field read, a config-value read and one map lookup (the runtime-override map) per client tick. |

## Under the hood

Three files, 94 lines together, and the entire behaviour is three lines of it:

```java
if (event.getNewFovModifier() > 1.0f) {
    event.setNewFovModifier(1.0f);
}
```

| Class | Role |
|---|---|
| `modules/static_fov/StaticFovModule` | Id, display name, description, `AbstractModuleConfig::createDefault`. `onInitialize()` is empty on purpose. |
| `modules/static_fov/client/StaticFovClientEvents` | The listener, the clamp, and the null check around the module lookup. |
| `standalone/static_fov/StaticFovStandalone` | `@Mod("vpa_static_fov")` entrypoint of the standalone jar; hands the module to `StandaloneModuleBootstrap`. |

**No mixin, no access transformer.** NeoForge already fires `ComputeFovModifierEvent` at exactly the
right place, so there is nothing to patch — `grep fov` over `vanillaplusadditions.mixins.json` and
`mixin/` finds nothing. There are also no items, blocks, entities, commands, keybinds, lang keys,
assets or data files, and no `DeferredRegister` anywhere in the module.

**The handler is not wired by the module.** It attaches itself through

```java
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
```

which NeoForge's annotation scan honours at mod load, bypassing the module lifecycle entirely. That
is why `onInitialize()` can be empty, and it has two effects worth knowing: the listener is present
on every client even while the module is switched off, and because it re-reads the switch on every
FOV computation, flipping `enabled` takes effect on the next tick instead of the next start.

**The enabled check.** `isModuleEnabled()` goes through
`ModuleManager.resolveModuleEnabled(moduleId, config.isEnabled())`, which consults the runtime
override map from `/vpa module enable|disable|clear` before the config value — the same chain every
other module uses, just read from the client side here.

**A missing module is inert, not fatal.** `StaticFovModule.getInstance()` hands back the static
reference the module's constructor stores, or `null` if the module has never been constructed; the
handler then returns without touching the event. In a build that ships these classes without ever
constructing the module — not registered in the bundle, no standalone entrypoint — the client code
does nothing at all rather than throwing on every client tick. The lookup is deliberately module-local
rather than `ModuleManager.getModule("static_fov")`: the standalone jar boots through
`StandaloneModuleBootstrap`, which leaves the manager's registry empty, so an id lookup would report
the module as absent while it is running.

## See also

* [VPA Options](options.md) — backs up `options.txt`, where your FOV and FOV Effects settings live
* [Module System](../guides/module-system.md) — the runtime `/vpa module` toggles
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
