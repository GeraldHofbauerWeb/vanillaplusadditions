# Idle Gamerule Pause

> **TL;DR** — While nobody is online the server stops the clock and the weather, and the first
> player to log in starts them again — so you don't come back to a world that drifted through three
> nights and a thunderstorm without you.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `idle_gamerules` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_idle_gamerules.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_idle_gamerules.jar) · also needs `vpa_core` |
| **Config section** | `[modules.idle_gamerules]` |
| **Since** | `v1.0.0-beta.22` |
<!-- vpa:meta:end -->

## What it does

Pauses day / weather / season cycles while the server is empty and resumes them on the first join.
Three gamerules are switched off the moment the last player disconnects and back on the moment the
next one arrives:

| Gamerule | Belongs to | What stops |
|---|---|---|
| `doDaylightCycle` | vanilla | The sun. The time of day stands still at whatever it was. |
| `doWeatherCycle` | vanilla | The weather **timers**. Rain does not stop — it freezes. |
| `doSeasonCycle` | Serene Seasons | The season clock, if that mod is installed. |

The list is a config key, so any other gamerule with a `true`/`false` value can join it, vanilla or
modded — they are applied by their `/gamerule` name.

Despite the module id, the trigger is an **empty** server, not an idle one. One player standing AFK
in a corner is enough to keep the clock running; the module counts heads
(`getPlayerList().getPlayerCount() > 0`) and nothing else. Spectators count as heads.

## In detail

### The transition check

One handler on `ServerTickEvent.Post`, and on almost every tick it does nothing:

```java
boolean playersOnline = server.getPlayerList().getPlayerCount() > 0;

if (lastPlayersOnline != null && lastPlayersOnline == playersOnline) {
    return; // no transition
}
lastPlayersOnline = playersOnline;
applyGamerules(server, playersOnline);
```

`lastPlayersOnline` is a plain `Boolean` on the module instance, `null` until the first tick and
never saved. That `null` is deliberate: the first tick after a start always counts as a transition,
so the module writes the correct state once at boot instead of trusting whatever is in the save.
The steady-state cost is one enabled check plus one player-count read per tick.

The write itself is one `/gamerule` command per configured rule, through the server's own
command source:

```java
CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
for (String rule : getConfig().getGamerules()) {
    server.getCommands().performPrefixedCommand(source, "gamerule " + rule + " " + value);
}
```

`MinecraftServer.createCommandSourceStack()` hands out permission level 4 named *Server*, so
`/gamerule`'s own level-2 requirement is never in the way. `withSuppressedOutput()` keeps the
`commands.gamerule.set` message out of chat and out of the admin broadcast.

### What the three defaults actually pause

Only the parts of the tick that sit behind those rules. `ServerLevel.tickTime` keeps counting
`gameTime` and only skips `dayTime`:

```java
protected void tickTime() {
    if (this.tickTime) {
        long i = this.levelData.getGameTime() + 1L;
        this.serverLevelData.setGameTime(i);
        this.serverLevelData.getScheduledEvents().tick(this.server, i);
        if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            this.setDayTime(this.levelData.getDayTime() + advanceDaytime());
        }
    }
}
```

So redstone, scheduled block ticks, hoppers and furnaces carry on exactly as before — in whatever
chunks are still ticking, which on an empty vanilla server is the spawn chunks and anything
force-loaded. That is a smaller area than it sounds: the spawn-chunk ticket is a `START` ticket of
radius `spawnChunkRadius` + 1 (3 by default), and `DistanceManager.addRegionTicket` files it at
chunk level `33 - 3 = 30`, with the level climbing by one per chunk of distance from the centre.
Block ticking ends at level 32, so of the 7×7 chunks the ticket loads only the inner 5×5 tick
blocks — the inner 3×3 tick entities on top of that, and the outermost ring is loaded and nothing
more.

Crops and mob spawning are the exception, and not because of anything this module does: they need
more than a ticking chunk. `ServerChunkCache` runs natural spawning and `tickChunk` — the random
ticks — only for a chunk that has a player close enough for spawning **or** a force-*ticking*
ticket:

```java
if ((this.level.isNaturalSpawningAllowed(chunkpos)
            && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkpos))
        || this.distanceManager.shouldForceTicks(chunkpos.toLong())) {
```

With nobody online the player check is false everywhere, and vanilla `/forceload` does not set the
force-ticks flag either — NeoForge's `forceChunk(..., ticking = true)` does, which is what
[Stationary Chunk Loader](stationary_chunk_loader.md) uses. Pair the module with a chunk loader of
that kind and your factory keeps running overnight while the sun does not move.

`doWeatherCycle` is a **freeze, not a clear**. The entire rain/thunder counter block in
`ServerLevel.advanceWeatherCycle` lives inside the rule:

```java
if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
    int i = this.serverLevelData.getClearWeatherTime();
    ...
    this.serverLevelData.setRaining(flag2);
}
```

Nothing calls `setRaining(false)`. If the last player logs out in a thunderstorm, the storm is still
there when the next one logs in, with its remaining timer intact. That is the point — you get the
weather you left, not a fresh roll.

### One write covers every dimension

The module runs the command once, not once per level, and that is enough: gamerules are a single
object for the whole server. `/gamerule` writes to the server's copy —

```java
T t = commandsourcestack.getServer().getGameRules().getRule(gameRule);
```

— `MinecraftServer.getGameRules()` returns `this.overworld().getGameRules()`, and every other
dimension reads through `DerivedLevelData.getGameRules()`, which returns `this.worldData
.getGameRules()`. The Nether and the End see the same flags. (Time only advances in the overworld
anyway: `tickTime` is passed `true` for `Level.OVERWORLD` and `false` for every other level.)

### The paused state is saved to the world

Gamerules live in `level.dat`, so `doDaylightCycle = false` survives a shutdown, a crash and a
backup restore. While the module is installed and enabled that costs nothing — the first join after
the restart sets everything back to `true`.

That is why the module puts them back on the way out. `ServerStopping` checks whether the server
is stopping while empty — the only state in which the rules are currently paused — and restores
them before `level.dat` is written:

```java
if (Boolean.FALSE.equals(lastPlayersOnline)) {
    applyGamerules(event.getServer(), true);
}
```

It costs nothing: a server that comes back up empty pauses them again on the first tick. What it
buys is that removing the jar, or setting `enabled = false`, never leaves a world with a frozen sun,
frozen weather and a frozen season clock and nothing left to undo it.

A crash is the one case this cannot cover — `ServerStopping` does not fire. If a world does come
back frozen, it is one command per rule:

```
/gamerule doDaylightCycle true
/gamerule doWeatherCycle true
/gamerule doSeasonCycle true
```

(No Serene Seasons, no `doSeasonCycle` — that third command just fails, harmlessly.)

### Rule names are checked

The rules are looked up by name and written directly, not run through `/gamerule`:

```java
GameRules.Key<GameRules.BooleanValue> key = known.get(rule);
if (key == null) {
    unknown.add(rule);
    continue;
}
rules.getRule(key).set(enabled, server);
```

`known` is every boolean gamerule the server has, collected once per run through
`GameRules.visitGameRuleTypes` — which covers modded rules as well, because they register the same
way. Anything not in it is named in a WARN line instead of being applied, and the INFO line lists
only what really was applied.

This used to be a command, and a silent one. Vanilla answers a parse error with `sendFailure`, which
a suppressed-output source drops on the floor:

```java
public void sendFailure(Component message) {
    if (this.source.acceptsFailure() && !this.silent) {
```

— and the log line printed the whole configured list regardless. The symptom was "the log says it
set `doSeasonCycle`, but nothing happened". Checking the rule by
hand with `/gamerule doSeasonCycle` is the only feedback there is.

<!-- vpa:config:start -->
## Configuration

Section `[modules.idle_gamerules]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_idle_gamerules-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `gamerules` | list | `List.of("doDaylightCycle", "doWeatherCycle", "doSeasonCycle") = ["doDaylightCycle", "doWeatherCycle", "doSeasonCycle"] (IdleGamerulesConfig.java:13-14, 29-32; generated in test-server/config/vanillaplusadditions-common.toml:561)` | no spec range; the per-entry validator requires a non-blank String. Whether the name is a real gamerule is settled at apply time, not here - an unknown one is named in the log rather than silently ignored. The new-element supplier for config editors is "doDaylightCycle". | Gamerules that are set to FALSE while no player is online and back to TRUE as soon as the first player joins, listed by their /gamerule name so modded rules work too (e.g. Serene Seasons' doSeasonCycle). Each name is looked up among the server's boolean gamerules and written directly; a name that is not one is skipped and reported in a WARN line, and the INFO line lists only what was really applied. The same rules are restored to TRUE when a server that is currently empty shuts down, so a paused world is not left frozen in level.dat. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Module removed or disabled while the server is empty | Handled: the rules are restored on `ServerStopping`. Only a crash can still leave a world paused — set the rules back by hand then. |
| The module owns those rules | It only ever writes, never reads. A rule you deliberately set yourself is overwritten at the next join or the next empty server; take it out of the `gamerules` list instead. |
| Misspelled or unavailable rule name | Skipped and named in a WARN line, with the count. The INFO line lists only the rules that were really applied. |
| No Serene Seasons | `doSeasonCycle` is reported as unknown once per transition and skipped; `doDaylightCycle` and `doWeatherCycle` still pause. There is no `ModList.isLoaded` check, no compile dependency and no compat class — the mod is named in a comment and nowhere else. |
| Single player | The handler is on the game bus, so the integrated server ticks it too. `lastPlayersOnline` starts `null`, so every world load writes the rules once; if the host is not in the player list yet on that tick, it writes `false` and corrects itself to `true` a moment later. Harmless in play, but it does mean every single-player world load touches those three rules. <!-- TODO: whether the very first ticked frame really sees playerCount == 0 is timing, not something the repository proves --> |
| Module disabled at startup (bundle) | `ModuleManager.initializeModules` only calls `initialize()` for modules enabled in the config, so `onInitialize` never runs and the handler is never subscribed. `/vpa module enable idle_gamerules` cannot bring it back before a restart. |
| Disabling at runtime | Works immediately — the handler checks `isModuleEnabled()` on every tick. Re-enabling self-corrects, because `lastPlayersOnline` is not updated while the module is off: the next tick either sees a real transition and re-applies, or correctly sees none. |
| Module disabled at startup (standalone jar) | `StandaloneModuleBootstrap` initialises unconditionally, so in `vpa_idle_gamerules` both directions work at runtime. |
| Grace period | There is none. The rules flip on the tick the last player leaves and on the tick the next one joins. |

## Under the hood

Two files, 62 + 38 lines, plus a 19-line standalone entrypoint. No mixins, no commands, no
keybinds, no registry entries, no assets, no data files and no lang keys — the module brings no
in-game text of its own beyond the display name that the core prints in `/vpa module status` and the
comments it writes into the config file. At runtime it produces one INFO line per transition and
nothing else.

| File | Role |
|---|---|
| `modules/idle_gamerules/IdleGamerulesModule.java` | the tick handler, the transition check, the command loop |
| `modules/idle_gamerules/config/IdleGamerulesConfig.java` | the one config value |
| `standalone/idle_gamerules/IdleGamerulesStandalone.java` | `@Mod("vpa_idle_gamerules")`, boots through `StandaloneModuleBootstrap` |

`onInitialize` does one thing, `NeoForge.EVENT_BUS.register(this)`, and the enabled check lives
inside the handler rather than around the subscription — which is where the difference between the
bundle and the standalone jar in the table above comes from. The bundle registers the module in
`VanillaPlusAdditions`; `build.gradle` declares the standalone jar as a bare one-liner with no
mixins, no data globs and no module dependencies, so `vpa_idle_gamerules.jar` is `vpa_core` plus
these two classes.

**The side is the logic, not the packaging.** Everything here is server-side — no rendering, no
`Minecraft.getInstance()`, no `Dist.CLIENT` — but the generated `neoforge.mods.toml` declares no
per-mod side, so the standalone jar loads on a client as well and does nothing there beyond the
integrated server.

**`getGamerules()` cannot NPE.** It returns `DEFAULT_GAMERULES` while the `ConfigValue` is still
`null`, which is what covers the ticks before the config file is built.

**Two names in the wild.** The module calls itself *Idle Gamerule Pause* (that is what the config
section comment says) while the standalone jar is labelled *Vanilla Plus: Idle Gamerules*. Same
module.

**History.** One commit ever touched the module directory: `1d9a41c`, 2026-06-30. The standalone
entrypoint arrived a day later with the `beta.22` standalone-jar pilot. `CHANGELOG.md` files it
under `1.0.0-beta.21`, which cannot be where it shipped — that tag does not exist in the repository,
and the earliest tag containing the commit is `v1.0.0-beta.22`.

**Testing.** This repository has no unit tests, and nothing in it records a run of this module.

## See also

* [Stationary Chunk Loader](stationary_chunk_loader.md) — keeps a chunk loaded and ticking with
  no player nearby, the other half of "the factory runs, the sun does not"
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Module System](../guides/module-system.md) — enabling and disabling modules
* [All modules](../../README.md#-modules)
