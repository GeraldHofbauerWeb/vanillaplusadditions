# Hostile Zombified Piglins

> **TL;DR** — Zombified piglins come after you unprovoked: the module pins a grudge on every one of
> them and renews it for as long as you stay inside the detection range, so you never get the vanilla
> truce.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `hostile_zombified_piglins` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_hostile_zombified_piglins.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_hostile_zombified_piglins.jar) · also needs `vpa_core` |
| **Config section** | `[modules.hostile_zombified_piglins]` |
| **Since** | `v0.1.0` |
<!-- vpa:meta:end -->

## What it does

Zombified piglins stop being neutral. Every one that spawns — or whose chunk loads — is marked as
angry at a player straight away, and once a second that mark is renewed for as long as a player is
inside `detection_range`, 32 blocks by default. The crowd standing in the bastion does not wait for
somebody to swing first any more.

They are audibly angry with it. Vanilla keys three things off the anger flag, and the module simply
holds that flag down:

* `getAmbientSound()` returns `ZOMBIFIED_PIGLIN_ANGRY` instead of the ordinary grunt;
* an adult picks up vanilla's `SPEED_MODIFIER_ATTACKING`, so it moves at 0.28 instead of 0.23
  (babies are excluded, as in vanilla);
* the mob counts as *recently hurt by a player*, which decides its loot flag and its XP — see
  [Anger has side effects that outlive it](#anger-has-side-effects-that-outlive-it).

Leaving ends it: as soon as no player is inside the range, the grudge is wiped outright. That is
harsher than vanilla, which would hold it for another 20–39 seconds. A chase that is already under
way is the one case where it does not end instantly — see below.

Players in **creative** or **spectator** are not seen at all.

Despite the mob, **nothing here is gated to the Nether**. There is no dimension check anywhere in the
module: a pig struck by lightning in the Overworld, a piglin that zombified after walking through a
portal, one that wandered into the End — all covered. The Nether is only where they normally live.
Ordinary piglins, piglin brutes and hoglins are untouched; the module tests `instanceof
ZombifiedPiglin` and nothing else.

## In detail

### Two handlers, and nothing else

| Event | When | What it does |
|---|---|---|
| `EntityJoinLevelEvent` | a piglin spawns, or its chunk loads | flags it at the first player in range |
| `EntityTickEvent.Pre` | `piglin.tickCount % 20 == 0` | re-evaluates that flag, once a second |

Both are instance methods on the NeoForge game bus. The `% 20` runs on each piglin's own `tickCount`,
so the work spreads itself across the second instead of landing on one tick, and it is checked
*before* the `instanceof` — a disabled module costs one `isModuleEnabled()` call per ticking entity
and nothing else. Everything that matters happens server-side: the player lookup returns `null` on a
client level, which stops the tick handler dead. The join handler is not gated that way, and the
entry it writes on the client is the single-player wrinkle in the limits table below.

The join handler is only a head start. A piglin that spawns with nobody nearby, or one the event
missed, is picked up by the next once-per-second pass anyway.

### One field, and vanilla does the hunting

The module never calls `setTarget`. It writes the persistent anger target:

```java
zombifiedPiglin.setPersistentAngerTarget(targetPlayer.getUUID());
```

and vanilla's own target goal does the rest:

```java
this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
```

`isAngryAt` is a UUID comparison against exactly the field the module wrote. So the pathing, the
melee and the pack alerting are vanilla's, unchanged — no AI goal is added, and there is no mixin
anywhere in this module.

### Detection range is not attack range

`detection_range` decides who gets flagged and when the flag is dropped. Everything after that
belongs to the vanilla goal, which measures differently:

| Bound | Value |
|---|---|
| The module's search | `getBoundingBox().inflate(detection_range)` — an axis-aligned **cube**. The default 32 is therefore a box about 65 blocks on a side: a player 32 blocks straight up counts, and so does one 32 out along X *and* 32 along Z, which is 45 blocks away |
| Acquiring you | `TargetingConditions.forCombat().range(getFollowDistance())` — `FOLLOW_RANGE` is the zombie's inherited 35 (`ZombifiedPiglin.createAttributes` overrides reinforcements, speed and damage, not range), measured as a true sphere — **and line of sight**, which `forCombat()` leaves switched on |
| An invisible player | that 35 is multiplied by `getVisibilityPercent`, with a floor of 2 blocks |
| Keeping you | the same 35 blocks; line of sight may lapse for `unseenMemoryTicks` 60, halved to 30 by `reducedTickDelay` and only counted on the ticks the goal selector runs — about three seconds in practice |
| Choosing between several | `Level.getNearestPlayer` picks the genuinely nearest player passing those conditions — but only among the players the piglin is angry at, and it is only ever angry at one |

Raising `detection_range` past ~35 therefore does not make piglins come at you from further away.
What it changes is the zone in which the grudge is *kept alive*, and that is the value's real job.

### `anger_duration` is a switch, not a duration

The configured value is thrown away one line after it is written:

```java
int angerDuration = getConfig().getAngerDurationValue();
if (angerDuration == -1) {
    zombifiedPiglin.setRemainingPersistentAngerTime(Integer.MAX_VALUE);
} else {
    zombifiedPiglin.setRemainingPersistentAngerTime(angerDuration);
}

zombifiedPiglin.startPersistentAngerTimer();
```

`startPersistentAngerTimer` is not "start the timer I just set". It rolls a new one:

```java
private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

@Override
public void startPersistentAngerTimer() {
    this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
}
```

So every value of 0 or more produces the same thing: a fresh random 400–780 ticks. `1` behaves like
the default `200`, which behaves like `2000000000`.

`-1` really is different, but it works down a different path — the tail of the once-per-second pass,
which never calls `startPersistentAngerTimer`:

```java
if (configuredDuration == -1
        && zombifiedPiglin.getRemainingPersistentAngerTime() < Integer.MAX_VALUE / 2) {
    zombifiedPiglin.setRemainingPersistentAngerTime(Integer.MAX_VALUE);
}
```

| `anger_duration` | What you actually get |
|---|---|
| `-1` | topped back up to `Integer.MAX_VALUE` every second while you are in range |
| anything `>= 0` | vanilla's own 20–39 seconds, re-rolled whenever the module re-angers the piglin |

In play the two are hard to tell apart, because a value of 0 or more never runs out while you are
around either — for the two reasons below.

### The timer does not run while it is chasing you

Vanilla's own countdown switches itself off when the current target is a player:

```java
if (this.getRemainingPersistentAngerTime() > 0
        && (livingentity == null || livingentity.getType() != EntityType.PLAYER || !updateAnger)) {
    this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
```

`ZombifiedPiglin.customServerAiStep` passes `updateAnger = true`, so the clock only runs in the gaps
where the piglin has no target. And where it does run, the module's pass catches it well before the
end:

```java
if (!zombifiedPiglin.isAngryAt(newTarget)
        || zombifiedPiglin.getRemainingPersistentAngerTime() < 100) {
```

Under five seconds left, and it is re-angered. A piglin that can see you never calms down on its own.

### Walking away, and the one case where it does not work

When the pass finds nobody in the cube it clears everything (the same three lines appear twice in
the file):

```java
angryPiglins.remove(zombifiedPiglin.getUUID());
zombifiedPiglin.setRemainingPersistentAngerTime(0);
zombifiedPiglin.setPersistentAngerTarget(null);
```

For a piglin that has not actually acquired you, that is the end of it. **A piglin already chasing
you re-angers itself in the same tick.** The module never touches `setTarget`, and vanilla rebuilds
the grudge from whatever live target the mob has:

```java
if (livingentity != null && !Objects.equals(uuid, livingentity.getUUID())) {
    this.setPersistentAngerTarget(livingentity.getUUID());
    this.startPersistentAngerTimer();
}
```

`EntityTickEvent.Pre` fires before the entity's tick, so the wipe and the rebuild happen within the
same tick, in that order — and the rebuild rolls a fresh 400–780 ticks while it is at it.

The chase therefore ends where vanilla ends it: once you are past 35 blocks, or once it has been
unable to see you for about three seconds, `TargetGoal.canContinueToUse` returns false, `stop()`
clears the target, and only then does the next pass find nothing to rebuild from. Worth knowing if
you set `detection_range` below 35 — you can be well outside the module's cube and still inside the
chase.

### Several players

The variable is called `nearestPlayer`, but nothing sorts the list:

```java
Player nearestPlayer = nearbyPlayers.isEmpty() ? null : nearbyPlayers.getFirst();
```

`getEntitiesOfClass` returns the entity-section query in its own order, so on a busy server the
grudge lands on whichever player came back first, not the closest one. Since a piglin only ever
carries one grudge, and vanilla's goal only considers players it is angry at, a piglin can walk past
someone standing next to it to get at a player 30 blocks away.

`target_switch_threshold` (5.0 s) is how long the current grudge must have been held before another
player can take it over. It is measured in **wall-clock** time through `System.currentTimeMillis()`,
so lag, a low TPS or a paused single-player world do not slow it down.

One wrinkle in the bookkeeping: on an actual switch the *previous* target's timestamp is carried
over, because the fresh timestamp is only written in the "same player as last time" branch. The new
target therefore starts out already past its threshold and could be replaced again on the very next
pass; it settles a second later, once the same player comes back first in the query.

### Anger has side effects that outlive it

`ZombifiedPiglin.customServerAiStep` contains:

```java
if (this.isAngry()) {
    this.lastHurtByPlayerTime = this.tickCount;
}
```

`lastHurtByPlayerTime > 0` is the flag `dropAllDeathLoot` passes to the loot table as *killed by a
player*, and it is also what `dropExperience` requires. While the module holds a piglin angry, that
field is rewritten every tick to the mob's current age, and afterwards it counts down by one per
tick — so a piglin that has been alive ten minutes keeps the credit for roughly ten minutes after it
calms down.

In practice: an angry zombified piglin that dies to lava, a fall or another mob still drops its XP,
and — once vanilla's goal has actually targeted a player, which is what sets `lastHurtByPlayer` —
still drops its player-kill loot. The module does not write any of those fields itself; it only
keeps the mob in the state vanilla attaches them to.

### Pack alerting is vanilla's

The class javadoc lists "maintains pack behavior where attacking one angers nearby ones" as a
feature. The module neither adds nor touches it: `maybeAlertOthers` is vanilla's, it only runs while
the piglin has a target and can see it, and it recruits piglins within `FOLLOW_RANGE` that have **no
target of their own**. The module does make it fire more often, because piglins that keep a grudge
hold a target far more of the time.

<!-- vpa:config:start -->
## Configuration

Section `[modules.hostile_zombified_piglins]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_hostile_zombified_piglins-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `anger_duration` | int | `200` | -1 ~ 2147483647 (Integer.MAX_VALUE) | Config comment: "How long zombified piglins stay angry in ticks (-1 for indefinite)". Effectively a two-state switch, NOT a duration. Any value >= 0 is written at HostileZombifiedPiglinsModule.java:146 and then immediately overwritten at L149 by startPersistentAngerTimer(), which in vanilla 1.21.1 rolls a fresh random 400-780 ticks (ZombifiedPiglin.java:176-177) - so 200 behaves exactly like 5 or like 2000000000. Only -1 is observably different, and it works through a separate path: maintainHostility re-sets the timer to Integer.MAX_VALUE once per second while a player is in range (L244-248). In all cases the anger is zeroed the moment no eligible player is inside detection_range. |
| `detection_range` | int | `32` | 1 ~ 128 | Config comment: "Range in blocks to detect players and become hostile". Radius of the axis-aligned box (getBoundingBox().inflate(range), HostileZombifiedPiglinsModule.java:164-166) in which the module looks for a player to pin the piglin's grudge on, and outside of which it wipes that grudge again. It is NOT attack range: the actual chase is vanilla's NearestAttackableTargetGoal, capped at the FOLLOW_RANGE attribute (35 blocks for a zombified piglin) and requiring line of sight, so values above ~35 only extend the zone in which a grudge is kept alive, not the distance from which piglins come at you. Creative and spectator players are never detected. |
| `target_switch_threshold` | double | `5.0` | 0.0 ~ 1.7976931348623157E308 (Double.MAX_VALUE) | Config comment: "Time in seconds before a zombified piglin can switch to a new nearest player target". Seconds of WALL-CLOCK time (System.currentTimeMillis(), converted to ms by getTargetSwitchThresholdValue(true)) that the current grudge must have been held before the module will re-point it at a different player - so it is unaffected by TPS, lag or a paused single-player world. The "new nearest player" it switches to is really just the first player the level query returns (the list is never sorted), and on an actual switch the timestamp is not reset, so the new target starts out already past its threshold and only settles one second later. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No dimension gate | Every zombified piglin in every dimension is affected, not only the ones in the Nether. There is no per-dimension option. |
| `detection_range` above ~35 | Buys no extra reach. The vanilla goal is capped at `FOLLOW_RANGE` 35 and needs line of sight; the larger cube only keeps the grudge alive further out. |
| `anger_duration` of 0 or more | Indistinguishable in play — the value is overwritten by vanilla's 20–39 second roll on every re-anger. Only `-1` behaves differently. |
| Universal anger gamerule | `isAngryAtAllPlayers` requires `getPersistentAngerTarget() == null`, and the module always writes a target, so the gamerule's "angry at everyone" clause never applies to a piglin this module is holding. |
| Sleeping | `isPreventingPlayerRest` returns `isAngryAt(player)`, and vanilla checks a 16 × 10 × 16 box around the bed. Where a bed works at all — so not the Nether — a piglin holding a grudge against you blocks sleep. |
| Single player / LAN | `EntityJoinLevelEvent` fires on the client too, and the tracking map is a plain `HashMap` on one module instance shared by the render thread and the integrated server thread, with no synchronisation. The client handler stores a `null` entry under the same piglin UUID, which wipes the server's switch bookkeeping for that piglin at spawn. A dedicated server has no client in the JVM and neither problem. |
| Map entries | `angryPiglins.remove` only ever runs on the "nobody in range" path. A piglin that dies, despawns or unloads while a player is still nearby leaves its entry behind, and the entry holds a strong `Player` reference. The map is transient, never saved and rebuilt from nothing after a restart. |
| Module disabled at startup (bundle) | `ModuleManager` only calls `initialize()` for modules enabled in the config, so the handlers are never subscribed and `/vpa module enable` cannot bring them back until a restart. Disabling at runtime does work, because both handlers check `isModuleEnabled()` on every call. |
| Module disabled at startup (standalone jar) | `StandaloneModuleBootstrap` initialises unconditionally, so in `vpa_hostile_zombified_piglins` both directions work at runtime. |
| Piglins' own anger | Vanilla saves `AngerTime` / `AngryAt` on the entity, independently of this module. Switch the module off and whatever grudge a piglin happened to be holding is still on it, and now expires normally. |

## Under the hood

Three files, 381 lines, no mixins, no registry entries, no assets, no data files and no lang keys —
the module produces no player-visible text at all, only log lines under `debug_logging`.

| File | Role |
|---|---|
| `modules/hostile_zombified_piglins/HostileZombifiedPiglinsModule.java` | both handlers, the grudge, the tracking map |
| `modules/hostile_zombified_piglins/config/HostileZombifiedPiglinsConfig.java` | the three config values |
| `modules/hostile_zombified_piglins/models/NearestPlayerTime.java` | `record NearestPlayerTime(Player player, long timeStamp)` |
| `standalone/hostile_zombified_piglins/HostileZombifiedPiglinsStandalone.java` | `@Mod("vpa_hostile_zombified_piglins")`, boots through `StandaloneModuleBootstrap` |

`onInitialize` does one thing, `NeoForge.EVENT_BUS.register(this)`, and the enabled check lives
inside the two handlers rather than around the subscription — which is where the difference between
the bundle and the standalone jar in the table above comes from. The bundle registers the module
unconditionally in `VanillaPlusAdditions`; `build.gradle` declares the standalone jar as a bare
one-liner with no mixins and no data globs.

**State.** `HashMap<UUID, NearestPlayerTime> angryPiglins` maps a piglin to the player it is angry at
and the wall-clock millisecond at which that grudge was last confirmed. Nothing else in the mod
reads it, and it is never persisted.

**If you edit this file**, four things are worth knowing before you trust what you read:

* the inline comment `// 10 seconds threshold` next to the switch check is stale — the configured
  default is 5.0 seconds, and the code is right;
* the commented-out `angryPiglins.put(...)` after a successful re-anger would have written the fresh
  timestamp that the switch path is missing;
* `getDetectionRange()`, `getAngerDuration()`, `getTargetSwitchThreshold()` (the raw `ModConfigSpec`
  accessors) and the no-argument `getTargetSwitchThresholdValue()` have no callers — everything goes
  through the `*Value()` variants, and the threshold is always fetched with `convertToMillis = true`;
* `NearestPlayerTime` implements `Comparable`, but nothing ever sorts or compares one.

## See also

* [Hostile Endermen](hostile_endermen.md) — the same trick for endermen, gated to the End
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
