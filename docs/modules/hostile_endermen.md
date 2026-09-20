# Hostile Endermen

> **TL;DR** — In the End every enderman within 16 blocks comes after you unprovoked, no staring
> required; a carved pumpkin still protects you, and the hunt ends the second you are out of range.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `hostile_endermen` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | [Enderman Overhaul](https://modrinth.com/mod/enderman-overhaul) <sub>tested 2.0.3</sub>, [Enhanced AI](https://modrinth.com/mod/enhanced-ai) <sub>tested 4.2.2.1</sub> |
| **Download** | [`vpa_hostile_endermen.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_hostile_endermen.jar) · also needs `vpa_core` |
| **Config section** | `[modules.hostile_endermen]` |
| **Since** | `v1.0.0-beta.68` |
<!-- vpa:meta:end -->

## What it does

Endermen in the **End** stop being neutral. Each one picks the nearest player inside
`detection_range` — 16 blocks by default — and attacks on its own. Walk far enough away and it
forgets you within a second. Endermen in the Overworld and the Nether are not touched at all.

A **carved pumpkin** still protects you, and so does anything else a mod hangs off vanilla's
ender-mask hook. Creative and spectator players are ignored.

The fight itself is unchanged. The module writes one field on the enderman and lets vanilla's own
enderman AI do the hunting, so an enderman still freezes while you stare at it, still blinks away
when you look at it from close up, and still teleports after you when you run.

It also carries two switches for other mods. Permanent aggression makes two of their abilities
unbearable — Enderman Overhaul's teleport-on-hit and EnhancedAI's teleport anti-cheese both throw
you around for a fight you never picked — so both are capped until you hit back.

## In detail

### One field, and vanilla does the rest

Angering an enderman is three lines:

```java
enderman.setPersistentAngerTarget(player.getUUID());
enderman.startPersistentAngerTimer();
applyAngerTime(enderman);
```

The UUID is the whole trick. Vanilla's `EndermanLookForPlayerGoal` builds its target predicate from

```java
this.isAngerInducing = p -> (enderman.isLookingAtMe((Player)p) || enderman.isAngryAt(p))
        && !enderman.hasIndirectPassenger(p);
```

and `NeutralMob.isAngryAt` is a UUID comparison:

```java
return p.getType() == EntityType.PLAYER && this.isAngryAtAllPlayers(p.level())
    ? true
    : p.getUUID().equals(this.getPersistentAngerTarget());
```

So the pathing, the teleporting closer and the melee are vanilla's, unchanged. No AI goal is added
and no mixin touches the enderman.

**The order of those three calls matters.** `startPersistentAngerTimer()` is not a flag, it rolls a
duration:

```java
private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

@Override
public void startPersistentAngerTimer() {
    this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
}
```

Called after `applyAngerTime` instead of before it, that random 400–799 ticks would overwrite the
configured `anger_duration` every single time.

### When the module looks

Two handlers, both instance methods on the NeoForge game bus, both dropping anything that is not a
server-side enderman in `Level.END`:

| Event | When | What it does |
|---|---|---|
| `EntityJoinLevelEvent` | an enderman spawns, or its chunk loads | angers it at the nearest valid player, if there is one |
| `EntityTickEvent.Pre` | `enderman.tickCount % 20 == 0` | re-evaluates hostility |

The `% 20` filter runs *before* the enabled and dimension checks, on each enderman's own
`tickCount`, so the work spreads itself over the second instead of landing on one tick.

That once-per-second pass has three outcomes:

| Situation | Result |
|---|---|
| Current anger target still valid and inside `detection_range` | timer topped up; the victim keeps their hunter |
| No usable current target, but a valid player is in range | angered at the nearest one |
| No valid player within `detection_range` | calms down |

Keeping the current victim is deliberate — an enderman does not swap to whoever happens to be
closest this second.

### Who counts as a target

```java
if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
    return false;
}
if (!getConfig().respectCarvedPumpkin()) {
    return true;
}
return !CommonHooks.shouldSuppressEnderManAnger(enderman, player,
        player.getItemBySlot(EquipmentSlot.HEAD));
```

That hook is the one vanilla's own `isLookingAtMe` calls:

```java
return mask.isEnderMask(player, enderMan)
    || NeoForge.EVENT_BUS.post(new EnderManAngerEvent(enderMan, player)).isCanceled();
```

which is why a carved pumpkin, a modded ender mask and any mod cancelling `EnderManAngerEvent` all
keep working as protection without a list of items anywhere in this module.

### Calming down

Once no valid player is inside `detection_range`, everything goes:

```java
enderman.setRemainingPersistentAngerTime(0);
enderman.setPersistentAngerTarget(null);
if (enderman.getLastHurtByMob() instanceof Player) {
    enderman.setLastHurtByMob(null);
}
if (chasingPlayer) {
    enderman.setTarget(null);
}
```

The revenge memory has to go with it. `HurtByTargetGoal` sits at target-selector priority 2 and
works off the enderman's 64-block follow range, not our 16 — leave `lastHurtByMob` standing and
walking out of `detection_range` would end nothing.

Two consequences follow from that line:

* **Both compat hooks below read exactly that field** to decide whether you hit back. After an
  enderman has calmed down, the memory that you fought it is gone and the suppressions apply again
  from scratch.
* **An endermite hunt is left alone.** `calmDown` returns early unless the enderman is chasing a
  player, is angry, or remembers a player as its last attacker.

### What `anger_duration` actually governs

Less than it looks. Vanilla decrements the timer in `EnderMan.aiStep` via
`updatePersistentAnger(level, true)`, and that second argument switches the countdown off entirely
while the current target is a player:

```java
if (this.getRemainingPersistentAngerTime() > 0
        && (livingentity == null || livingentity.getType() != EntityType.PLAYER || !p_21668_)) {
    this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
```

On top of that the module rewrites the value every 20 ticks while you are in range, and zeroes it
outright the moment you leave. So while the module is maintaining it, any `anger_duration` of 21 or
more behaves identically. The value only decides how long an already-angry enderman keeps going
once nothing tops it up any more — after `/vpa` switches the module off, for instance. `-1` is
written as `Integer.MAX_VALUE` ticks, roughly 3.4 years of server time, not as a never-expiring
flag.

### Detection range is not attack range

`detection_range` decides who gets angered and when they calm down. Everything after that belongs to
the vanilla goal, which measures differently:

| Vanilla behaviour | Bound |
|---|---|
| Acquiring you | `TargetingConditions.forCombat().range(getFollowDistance())` — the `FOLLOW_RANGE` attribute, 64 blocks — **and line of sight**, which `forCombat()` leaves switched on |
| Keeping you | `continueAggroTargetConditions` is built with `.ignoreLineOfSight()`, so once it has you, walls stop mattering |
| Blinking closer | only past 16 blocks (`distanceToSqr > 256.0`), at most once per `adjustedTickDelay(30)` = 15 ticks, landing about 16 blocks nearer |
| Freezing when stared at | `EndermanFreezeWhenLookedAt` needs `distanceToSqr <= 256.0`, i.e. within 16 blocks |
| Blinking away | you look at it from closer than 4 blocks (`distanceToSqr < 16.0`) |

An angry enderman that has never had line of sight to you simply stands there. That is the most
visible consequence of leaving the hunting to vanilla, and it is intentional.

### Enderman Overhaul: the teleport on hit

Enderman Overhaul's endermen extend vanilla's `EnderMan`, so this module already makes them hostile
with no extra work. Its **End Enderman** has one ability that does not survive permanent
aggression — it blinks its victim away on hit:

```java
// EndEnderman.doHurtTarget, endermanoverhaul-neoforge-1.21.1-2.0.3
if (random.nextFloat() < EndermanOverhaulConfig.endEndermanTeleportChance) {  // ships as 0.5f
    ModUtils.teleportTarget(level(), living, 24);
}
```

`teleportTarget` offsets the victim by `(random − 0.5) × range` in X and Z, so range 24 is a random
blink of up to ±12 blocks, plus `random.nextInt(24) − 8` in Y. Half of all hits, for a fight you
never started.

The mixin wraps that one call:

```java
@WrapWithCondition(method = "doHurtTarget",
        at = @At(value = "INVOKE", target = "…ModUtils;teleportTarget(…)V", remap = false),
        remap = false)
private boolean vpaAllowTeleportAttack(Level level, LivingEntity victim, int range) {
    return !HostileEndermenModule.suppressTeleportAttack(self, victim);
}
```

It is skipped when all of these hold: module enabled, `suppress_teleport_attack` on, the victim is a
player, the enderman is in the End, and `enderman.getLastHurtByMob() != victim`. Vanilla drops
`lastHurtByMob` 100 ticks after the last hit, so "hit back" means "within the last five seconds" —
in a real duel the End Enderman keeps its full moveset, and someone who only runs is no longer
displaced.

Left alone on purpose: the End Islands Enderman's Ender Bullet and the Corrupted Shield/Blade
teleports, and Enderman Overhaul's own `endEndermanTeleportChance`, which stays wherever its owner
put it.

### EnhancedAI: the anti-cheese drag

EnhancedAI hands a `TeleportAntiCheeseGoal` to everything in
`#enhancedai:mobs/teleport_anti_cheese/can_use`, which ships as exactly `minecraft:enderman`. The
goal exists so players cannot plink at an enderman from a spot it cannot reach:

```java
// TeleportAntiCheeseGoal.canUse, enhancedai-4.2.2.1
target = mob.getTarget();
if (target == null || target.getType().is(CANT_BE_TELEPORTED) || !mob.getNavigation().isDone()) {
    return false;
}
if (target.distanceToSqr(mob) < 2.0 && mob.hasLineOfSight(target)) {
    awayFromTargetTick = 0;
} else {
    awayFromTargetTick++;
}
return awayFromTargetTick > adjustedTickDelay(50);   // 25 ticks
```

`start()` then calls `target.randomTeleport(…)` within ±4 blocks of the enderman, with the
chorus-fruit sound. Anything further than 1.4 blocks counts as "away", and the navigation is "done"
the moment the enderman has no path — so with permanent aggression, walking away or dropping off a
ledge yanks you back after about 1.3 seconds. The CHANGELOG entry for `v1.0.0-beta.68` records the
measurement that led here: **29 of 29** unwanted teleports in a diagnosed session came from this
goal, and Enderman Overhaul's melee teleport never fired once.

The mixin vetoes `canUse` at HEAD rather than skipping the teleport itself, so the goal never
starts — no teleport, no sound, no goal bookkeeping. The same self-defence rule applies: only for an
enderman in the End whose target is a player who has not hit it back. That restores the feature's
original purpose. Shooting an enderman from an unreachable perch still drags you in; walking away no
longer does. Overworld and Nether endermen, other mobs in the tag and EnhancedAI's own config are
untouched.

It stays silent even under `debug_teleport_tracking` — `canUse` runs every tick per enderman, and
the source comment puts a single session at 20 000+ log lines.

### Teleport diagnostics

`debug_teleport_tracking` (off by default) is how the goal above was identified. One hook:

```java
@Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
```

`ServerPlayer#teleportTo(double, double, double)` is where every in-dimension displacement of a
player ends up, `LivingEntity.randomTeleport` included — chorus fruit, ender pearls and both mods
above all route through it. The log line carries the distance, the from and to coordinates and the
dimension, followed by up to 14 caller frames with the module's own class and everything under
`net.geraldhofbauer.vanillaplusadditions.mixin` filtered out, so the first frame printed is the
foreign caller.

Two things to know about it. It only sees teleports *within* a dimension — the cross-dimension
`teleportTo(ServerLevel, …)` is a different method and is not hooked. And it ignores the module
switch: unlike the two suppression hooks it never asks `isModuleEnabled()`.

```java
public static void logPlayerTeleport(ServerPlayer player, double x, double y, double z) {
    HostileEndermenModule module = instance;
    if (module == null || !module.getConfig().debugTeleportTracking()) {
        return;
    }
```

<!-- vpa:config:start -->
## Configuration

Section `[modules.hostile_endermen]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_hostile_endermen-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `anger_duration` | int | `600 (DEFAULT_ANGER_DURATION)` | -1 ~ 2147483647 (INDEFINITE_ANGER ~ Integer.MAX_VALUE) | How long endermen stay angry in ticks (-1 for indefinite, applied as Integer.MAX_VALUE ticks), refreshed every second while a player stays within detection_range. |
| `debug_teleport_tracking` | boolean | `false` | — | Diagnostics: logs every player teleport together with up to 14 caller frames (VPA's own frames filtered out), which identifies the mod/feature that moved the player. Fires even while the module is disabled. |
| `detection_range` | int | `16 (DEFAULT_DETECTION_RANGE)` | 1 ~ 128 | Range in blocks in which endermen in the End automatically become hostile, and beyond which they calm down again; the hard ceiling is the vanilla enderman follow range of 64, so larger values are capped by vanilla targeting anyway. |
| `respect_carved_pumpkin` | boolean | `true` | — | Keeps the vanilla carved pumpkin protection: players wearing a carved pumpkin (or a modded ender mask) are not attacked automatically; checked via NeoForge's CommonHooks.shouldSuppressEnderManAnger, so EnderManAngerEvent cancels also count. |
| `suppress_anticheese_teleport` | boolean | `true` | — | EnhancedAI compat: stops its "Teleport anti-cheese" goal from dragging players over to an enderman in the End as long as the player has not hit that enderman back (5 s window); no effect without EnhancedAI installed. |
| `suppress_teleport_attack` | boolean | `true` | — | Enderman Overhaul compat: stops its End Enderman from teleporting the player away on hit as long as the player has not hit that enderman back (5 s window); no effect without Enderman Overhaul installed. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Overworld and Nether | Untouched by design. `appliesTo` tests `Level.END` literally; there is no per-dimension list. |
| An enderman with no line of sight | Stands still, however angry. The vanilla goal has to see you once to acquire you; only after that does `ignoreLineOfSight` take over. |
| Carved pumpkin after you struck first | A pumpkin-wearer is not a valid target, so the once-per-second pass calms the enderman — and `calmDown` clears `lastHurtByMob` too. An enderman you attacked in the End therefore forgets you within a second while you wear one, where vanilla would have kept it hunting. Read off the source; not reproduced in this repository. |
| `detection_range` above 64 | Accepted (the range goes to 128) and pointless: the vanilla goal is bounded by the enderman's 64-block follow range. Nothing in the code clamps it; only the config comment says so. |
| Sneaking or invisible at a large `detection_range` | Vanilla multiplies its own range by `getVisibilityPercent` — 0.8 while crouching — before comparing, while the module's range check does not. Only bites if `detection_range` is pushed near 64. |
| Disabling the module at runtime | Stops new anger, calms nothing down. Both handlers gate per event, so already-angry endermen keep hunting until vanilla's own timer expires — with `anger_duration = -1` that is effectively never. |
| `debug_teleport_tracking` | Ignores the module switch, and its mixin targets a vanilla class, so it is always applied. Leave the flag off in normal play. |
| Endermite hunts | `calmDown` spares them, but the once-per-second pass still re-angers the enderman at any valid player in range, and vanilla's player goal outranks the endermite goal (target-selector priority 1 against 3). |
| Enderman Overhaul or EnhancedAI absent | Both compat mixins use string targets; Mixin disables a mixin whose target class is missing, because the mixin config is `"required": false`. The two switches then do nothing. |
| Mod ids | This module names only the packages `tech.alexnijjar.endermanoverhaul` and `insane96mcp.enhancedai`. There is no `ModList.isLoaded` check and no `neoforge.mods.toml` dependency on either mod, so nothing here ties those packages to a mod id. |
| Bundle and standalone jar side by side | `instance` is a plain static field assigned in the constructor, so the last module constructed wins for all three static hooks. Not guarded. |

## Under the hood

| File | Role |
|---|---|
| `modules/hostile_endermen/HostileEndermenModule.java` | both event handlers and the three static hooks |
| `modules/hostile_endermen/config/HostileEndermenConfig.java` | the six keys |
| `mixin/hostile_endermen/EndEndermanTeleportMixin.java` | Enderman Overhaul, `@WrapWithCondition` |
| `mixin/hostile_endermen/TeleportAntiCheeseMixin.java` | EnhancedAI, `@Shadow @Final Mob mob` plus a cancellable `@Inject` at HEAD of `canUse` |
| `mixin/hostile_endermen/PlayerTeleportDebugMixin.java` | the diagnostics |
| `standalone/hostile_endermen/HostileEndermenStandalone.java` | `@Mod("vpa_hostile_endermen")`, booted through `StandaloneModuleBootstrap` |

**No content surface.** No items, blocks, entities, commands, keybinds, recipes or loot; no `data/`
or `assets/` files and not one lang key. The only switch in game is core's `/vpa`.

**Lifecycle.** `onInitialize()` does one thing, `NeoForge.EVENT_BUS.register(this)`; `onCommonSetup()`
only debug-logs. Both handlers therefore live on the instance registered to the game bus, and the
three mixins reach it through a static `instance` field assigned in the constructor. All three
static hooks null-check it.

**Side.** Server. `appliesTo` rejects `isClientSide` in both handlers, the only mixin against a
vanilla class targets `ServerPlayer`, and nothing in the module's six files touches a client class.

**Standalone jar.** `vpa_hostile_endermen` ships the three mixins and no data files, and needs
`vpa_core` like every module jar. Its generated mixin config is `"required": false` as well, which is
the entire "safe without those mods" guarantee — there is no runtime mod check anywhere.

**Asymmetry in `onConfigLoad`.** The debug block null-checks `detection_range`, `anger_duration` and
`respect_carved_pumpkin`, then also dereferences `suppress_teleport_attack`;
`suppress_anticheese_teleport` and `debug_teleport_tracking` are never logged. Harmless in practice —
every value is built in the same call.

**Testing.** This repository has no unit tests. The only measurement it records is the 29-of-29
teleport count in the `v1.0.0-beta.68` CHANGELOG entry. Exactly one commit has ever touched this
module — `4943bd2`, which is that release commit itself; nothing has changed since.

## See also

* [Hostile Zombified Piglins](hostile_zombified_piglins.md) — the same idea for the Nether, built
  around a UUID map instead of statelessly
* [Enhanced AI Leader Loot](enhanced_ai_leader_loot.md) — the other module that plays alongside
  EnhancedAI
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
