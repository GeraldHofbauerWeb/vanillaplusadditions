# Mob Glow Command

> **TL;DR** — `/mobglow minecraft:creeper` outlines every creeper in the loaded chunks of the
> dimension you are standing in, visible through walls, until the time runs out or you clear it
> again.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `mob_glow` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_mob_glow.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_mob_glow.jar) · also needs `vpa_core` |
| **Config section** | `[modules.mob_glow]` |
| **Since** | `v0.1.0` |
<!-- vpa:meta:end -->

## What it does

`/mobglow` makes all mobs of a specified type glow (configurable duration, including infinite) for
easier tracking. Clear by type or all at once — handy for server administration and debugging.

The outline itself is stock vanilla: the Glowing effect, drawn through walls, in the mob's team
colour (`Entity.getTeamColor()` — white for anything not on a scoreboard team). A vanilla client
sees it without installing anything.

| Command | What it does |
|---|---|
| `/mobglow <entity_type>` | Glow with the default duration — normally "indefinitely" |
| `/mobglow <entity_type> <seconds>` | Glow for that many seconds |
| `/mobglow <entity_type> infinite` | The same as the bare form |
| `/mobglow <entity_type> clear` | Stop that type glowing |
| `/mobglow all clear` | Stop everything this module is still tracking |

`<entity_type>` is a resource location, so a bare `creeper` becomes `minecraft:creeper`. Tab
completion offers every entity type in the registry except `minecraft:player`, and applying to
players is refused a second time when the command runs: *Cannot apply glow effect to players*.

Permission level 2 by default; see [Permissions](#permissions).

## In detail

### Durations

The duration is a `StringArgumentType.word()` parsed with a plain `Integer.parseInt`. Two forms
work, and only two:

| Typed | Result |
|---|---|
| `infinite` (any case) | The `default_duration` path — see below |
| A bare integer, e.g. `30` | That many seconds |
| Anything else, e.g. `30s`, `5m`, `1h` | *Invalid duration: 30s. Use a number or 'infinite'* |

Time units have never worked, although an older version of this page documented them.

`max_duration` (3600 s) is checked on the numeric path only, and it is an upper bound and nothing
else:

```java
int maxDuration = getConfig().getMaxDurationValue();
if (maxDuration > 0 && durationSeconds > maxDuration) {
    source.sendFailure(Component.literal("Duration cannot exceed " + maxDuration + " seconds") …);
    return 0;
}
```

Zero and negative values pass it. `word()` accepts a leading `-`, `Integer.parseInt` accepts it, and
`/mobglow minecraft:zombie -5` answers *Applied glow effect to N minecraft:zombie entities for -5
seconds*. Nothing glows: the effect instance carries −100 ticks, and

```java
private boolean hasRemainingDuration() {
    return this.isInfiniteDuration() || this.duration > 0;
}
```

makes `MobEffectInstance.tick` return false on the mob's very next tick, so the effect is removed
before it is ever drawn. `/mobglow <type> 0` behaves the same way.

### "infinite" is 3.4 years, not vanilla's infinite

With the default `default_duration = -1`:

```java
if (configDefault == -1) {
    durationTicks = Integer.MAX_VALUE;
    isInfinite = true;
} else {
    durationTicks = configDefault * 20; // Convert seconds to ticks
}
```

`Integer.MAX_VALUE` ticks is 107,374,182 seconds — about 3.4 years at 20 tps. Vanilla's real
infinite marker is `MobEffectInstance.INFINITE_DURATION = -1`, which this module never uses, so
`isInfiniteDuration()` stays false and the effect really does tick down towards an expiry, where a
vanilla-infinite one never expires. Nobody ever reads that number off the effects HUD: players
cannot be targeted at all, and a mob's remaining duration never reaches anyone's HUD — the effect
packet `LivingEntity.sendEffectToPassengers` sends to a rider is addressed to the *mob's* entity
id, not the player's. The finite count shows up only in server-side data, e.g. `/data get entity
<uuid> active_effects`. Long enough in practice; not the same thing.

Set `default_duration` to anything else and the word `infinite` stops meaning infinite: the branch
falls through to `configDefault * 20`, `isInfinite` stays false, and chat reports "for N seconds".
`max_duration` is not consulted on that path, so `default_duration = 99999` with
`max_duration = 3600` applies 99999 seconds unchallenged.

Both multiplications are unguarded `int` arithmetic, so a large `default_duration`, or a large typed
duration with `max_duration = 0` ("no limit"), wraps around — and where it lands depends on the
number. `/mobglow minecraft:zombie 999999999` with the limit off works out to −1,474,836,500 ticks,
the "nothing glows" case above. `default_duration = 214748365` wraps the other way, to +4 ticks: a
fifth of a second of glow, announced as "for 0 seconds".

### Re-applying never shortens a glow

`LivingEntity.addEffect` hands an existing instance to `MobEffectInstance.update`, and at equal
amplifier only one branch can assign a new duration:

```java
} else if (this.isShorterDurationThan(p_19559_)) {
    if (p_19559_.amplifier == this.amplifier) {
        this.duration = p_19559_.duration;
```

This module always uses amplifier 0, so the new duration wins only when it is the **longer** one.
After `/mobglow minecraft:zombie` the zombies carry `Integer.MAX_VALUE` ticks, and a following
`/mobglow minecraft:zombie 10` changes nothing at all: chat says *All 37 minecraft:zombie entities
already have glow effect* and the command returns 0. To shorten a glow, clear it and apply again.

### One dimension, loaded chunks

`serverLevel.getAllEntities()` is `getEntities().getAll()` — the level's own entity lookup, a map of
the entities in loaded chunks. Mobs in unloaded chunks are never reached, and a mob that wanders in
afterwards does not start glowing; the command is a one-shot sweep, not a rule.

Both the apply and the clear path work on `source.getLevel()` alone, so standing in the Overworld and
typing `/mobglow minecraft:zombie` does nothing to the Nether. `/execute in minecraft:the_nether run
mobglow minecraft:zombie` does.

### What the number in chat means

Every matching living entity gets the effect and goes into the tracking map. Only the *reported*
number is filtered:

```java
livingEntity.addEffect(glowEffect);

// Track this mob
trackedGlowingMobs.put(entity.getUUID(), entityType);
totalWithEffectCount.incrementAndGet();

// Count as changed if it didn't have the effect before or we're within the limit
if (!hadGlowEffect && (maxMobs == 0 || changedCount.get() < maxMobs)) {
    changedCount.incrementAndGet();
```

`max_mobs_per_command` caps `changedCount`, never the effect. With the default of 100 and 300 zombies
loaded, all 300 glow and chat says *Applied glow effect to 100 new minecraft:zombie entities
indefinitely (300 total now glowing)*. The config comment ("Maximum number of mobs that can be
affected per command") promises a cap the code does not apply. `changedCount` is also the command's
Brigadier return value, so `execute store result` and a command block's success count are capped at
100 as well.

| Situation | Chat |
|---|---|
| No matching living entity in this level | No `<type>` entities found to apply glow effect |
| All of them already glowed | All N `<type>` entities already have glow effect |
| All of them are new, and under the cap | Applied glow effect to N `<type>` entities *(duration)* |
| Otherwise | Applied glow effect to C new `<type>` entities *(duration)* (T total now glowing) |

Non-living entity types parse fine — the registry key exists — but are filtered out by
`entity instanceof LivingEntity`, so `/mobglow minecraft:item` answers *No minecraft:item entities
found to apply glow effect*. Armour stands are living entities and are a valid target.

### Clearing, and what it forgets

Both clear commands walk `trackedGlowingMobs`, a plain `HashMap<UUID, EntityType<?>>` field on the
module instance. They can only ever clear what this module applied **in this session**: a glow from a
spectral arrow or from `/effect` is never touched. The converse holds too — for a mob that *is*
tracked, the clear calls `removeEffect(GLOWING)` unconditionally, so it also strips a glow that came
from somewhere else.

The map is never written to disk. After a server restart the mobs given a practically infinite glow
keep glowing while `/mobglow all clear` reports *No glowing entities found to clear*. The way out is
vanilla's own `/effect clear @e[type=zombie] minecraft:glowing`.

Worse, a clear untracks more than it clears:

```java
Entity entity = serverLevel.getEntity(mobUUID);
if (entity instanceof LivingEntity livingEntity && livingEntity.hasEffect(MobEffects.GLOWING)) {
    livingEntity.removeEffect(MobEffects.GLOWING);
    …
}

// Remove from tracking regardless of whether entity was found
// (entity might have despawned or died)
trackedGlowingMobs.remove(mobUUID);
```

That `remove` sits outside the found branch, and `cleanupDeadReferences(serverLevel)` then runs at
the end of *every* clear and drops every tracked UUID that `serverLevel.getEntity(...)` cannot
resolve. Seen from one level, a mob in another dimension and a mob in an unloaded chunk look exactly
like a dead one. So a type-scoped `/mobglow minecraft:zombie clear` in the Overworld also forgets the
Nether creepers that were glowing, and no `/mobglow` command can clear those afterwards.

### Where the parser surprises you

`all` is a literal at the command root, and `CommandNode.getRelevantNodes` returns
`Collections.singleton(literal)` on an exact literal match without ever trying the argument children.
An exact word therefore shadows the `<entity_type>` argument rather than competing with it:

| Typed | What happens |
|---|---|
| `/mobglow all` | Only the `all` literal is considered, and it has no `executes` → *Unknown or incomplete command* |
| `/mobglow all 30` | Same shadowing; `all` has only the `clear` child → parse error |
| `/mobglow clear` | `clear` is **not** a literal at the root, so it parses as the entity type `minecraft:clear` → *Unknown entity type: minecraft:clear* |
| `/mobglow <type> clear` | `clear` **is** a literal under `<entity_type>`, so it can never be read as a duration |

### Permissions

One `.requires` on the command root, and nothing else:

```java
.requires(source -> !getConfig().getRequireOpValue() || source.hasPermission(2))
```

It is read live, so flipping `require_op` takes effect without a restart. It also gates every
subcommand at once: `require_op = false` hands `/mobglow all clear` to every player on the server.
Two TODO comments in the source acknowledge the missing per-subcommand check. Note that the command
tree reaches a player again only through `Commands.sendCommands`, which has exactly two callers.
`PlayerList.sendPlayerPermissionLevel` covers join, respawn, a dimension change, an op change and —
via a NeoForge patch in `MinecraftServer.reloadResources` — `/reload`; the other is
`IntegratedServer.publishServer`, in singleplayer, when the world is opened to LAN. So tab
completion can lag a live change of `require_op` even though typing the command already works.

<!-- vpa:config:start -->
## Configuration

Section `[modules.mob_glow]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_mob_glow-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_duration` | int | `-1` | -1 ~ 2147483647 | Seconds the glow lasts when no duration is typed (the implicit "infinite"). -1 means Integer.MAX_VALUE ticks (~3.4 years, reported as "indefinitely"); any other value is used as seconds and is NOT checked against max_duration. |
| `max_duration` | int | `3600` | 0 ~ 2147483647 | Upper bound in seconds for an explicitly typed duration; 0 disables the check. It does not bound the default_duration path, and it does not reject zero or negative durations. |
| `max_mobs_per_command` | int | `100` | 0 ~ 2147483647 | Caps only the number reported in chat and the command's Brigadier return value; 0 means uncapped. It does NOT limit how many mobs actually receive the glow - despite the config comment claiming it does. |
| `require_op` | boolean | `true` | — | Whether /mobglow needs permission level 2. Read live in the command's requires predicate, so it takes effect without a restart. false opens every subcommand, including "all clear", to every player. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Vanilla clients | Nothing to install. `RegisterCommandsEvent` fires server-side and the outline is vanilla's own Glowing effect. |
| Other dimensions | Apply and clear both work on `source.getLevel()` only. Reach another one with `/execute in <dimension> run mobglow …`. |
| Unloaded chunks | `getAllEntities()` sees loaded chunks only, and the command does not repeat itself, so a mob that loads in later never glows. |
| Server restart | The tracking map is RAM-only. Glow applied before the restart survives it; the clear commands no longer know about it. Fall back to `/effect clear @e[type=…] minecraft:glowing`. |
| Clearing from the wrong dimension | Untracks the other dimension's mobs as well, permanently — `/mobglow` can never clear them again. |
| Glow from another source | Not cleared unless the mob happens to be tracked — and for a tracked mob it *is* cleared, even though this module did not apply it. |
| Shortening a glow | Impossible by re-applying: `MobEffectInstance.update` keeps the longer duration. Clear first, then apply. |
| `require_op = false` | Opens every subcommand, `all clear` included, to every player. |
| `max_mobs_per_command` | Caps the reported number and the command's return value, not how many mobs are affected. |
| Module disabled at runtime | Nothing on the execute path checks the module, so `/mobglow` keeps working until the command tree is rebuilt (`/reload` or a restart). |
| Module disabled at startup, bundle | `ModuleManager.initializeModules` only calls `initialize()` on enabled modules, so the listener is never registered and the command is absent. `/vpa module enable mob_glow` cannot bring it back without a restart. |
| Module disabled at startup, standalone jar | `StandaloneModuleBootstrap.boot` always initialises the module, so the only gate is the `isModuleEnabled()` check at the top of the handler — there, a config change plus `/reload` does add and remove the command. |
| Another mod vetoing the effect | Glowing has no vanilla immunity (`LivingEntity.canBeAffected` only special-cases Infested, Oozing and Poison/Regeneration), but NeoForge's `MobEffectEvent.Applicable` lets another mod refuse it. |
| Translations | All chat output is hardcoded English `Component.literal` — see below. |

## Under the hood

| File | Role |
|---|---|
| `modules/mob_glow/MobGlowModule.java` | Everything: the command tree, apply, clear, the tracking map |
| `modules/mob_glow/config/MobGlowConfig.java` | The four keys |
| `standalone/mob_glow/MobGlowStandalone.java` | `@Mod("vpa_mob_glow")` entry point |

No mixins, no items, no blocks, no entities, no keybinds, no recipes, no models, no textures. One
event, on the game bus:

| Event | Bus | Purpose |
|---|---|---|
| `RegisterCommandsEvent` | game | Builds the `/mobglow` tree; returns early when the module is disabled |

`NeoForge.EVENT_BUS.register(this)` happens in `onInitialize`, which makes enabling and disabling a
**registration-time** decision in the bundle — see the two rows in the table above.

**The effect instance** is `new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0, false, true)`:
amplifier 0, `ambient = false`, `visible = true`, and the five-argument constructor passes `visible`
straight on as `showIcon`. Affected mobs therefore also trail the usual effect particles
(`updateSynchronizedMobEffectParticles` filters on `isVisible`), which on a few hundred zombies is
conspicuous in its own right.

**Fourteen dead lang keys.** Every `command.vpa.mobglow.*` key is unreferenced — `grep -rn
"command.vpa.mobglow" src/main/java/` returns nothing — because all output is hardcoded
`Component.literal`. The keys survive in all six lang files (`cs_cz`, `de_at`, `de_de`, `en_us`,
`es_es`, `fr_fr`), 84 entries in total, and they are this module's only resources. The history is
deliberate rather than accidental: commit `ae2bca3` (released as 0.10.3) replaced the hardcoded
strings with translatable components, and commit `dbc4787` (released as 0.12.0) switched the module
back to direct text output. Only the cleanup was forgotten.

## See also

* [Block Glow](block_glow.md) — the same idea for blocks, client-side and with a radius
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
