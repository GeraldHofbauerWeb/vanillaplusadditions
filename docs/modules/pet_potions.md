# Pet Potions

> **TL;DR** — Splash a potion of Healing or Regeneration onto someone else's wolf that you hit by
> accident, and it forgives you instead of hunting you down until you die.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `pet_potions` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_pet_potions.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_pet_potions.jar) · also needs `vpa_core` |
| **Config section** | `[modules.pet_potions]` |
| **Since** | `v1.0.0-beta.65` |
<!-- vpa:meta:end -->

## What it does

Hit somebody else's tamed animal by mistake and it turns on you, and vanilla offers no way back: the
only appeasement it knows is your own death. Here a thrown potion does the job instead. Splash or lob
a potion of Healing or Regeneration at the animal and the anger towards you is cleared, with four
heart particles and an amethyst chime to show that it worked. For the next ten seconds it will not
take you as its target again.

The pardon is aimed. Only the anger directed at the thrower is undone — an animal that is
simultaneously hunting somebody else stays angry at them. You buy off your own mistake rather than
pacifying a stranger's guard animal.

The module also fixes the reason that throw usually fails. Aiming a splash potion at a tamed animal
normally does nothing at all: the potion never leaves your hand. Beneficial splash and lingering
potions now pass through the animal and are thrown as they should be. Drinkable potions are
deliberately left alone, so holding an ordinary healing potion still lets you tell your own wolf to
sit.

## Why it exists

### Vanilla's only way out is dying

`TamableAnimal` exempts exactly one player from being targeted, and it is not you:

```java
public boolean canAttack(LivingEntity target) {
    return this.isOwnedBy(target) ? false : super.canAttack(target);
}
```

For everybody else the ordinary target goals apply. A wolf carries both the retaliation goal and one
that re-acquires whoever it is persistently angry at:

```java
this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
```

The one appeasement vanilla ships is `NeutralMob#playerDied`:

```java
default void playerDied(Player player) {
    if (player.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
        if (player.getUUID().equals(this.getPersistentAngerTarget())) {
            this.stopBeingAngry();
        }
    }
}
```

You have to die, and `forgiveDeadPlayers` has to be on.

### The client eats the throw before the server hears about it

On the client, `Wolf#mobInteract` never reaches any of its real branches and ends here:

```java
boolean flag = this.isOwnedBy(player) || this.isTame() || itemstack.is(Items.BONE) && !this.isTame() && !this.isAngry();
return flag ? InteractionResult.CONSUME : InteractionResult.PASS;
```

The check is `isTame()`, not `isOwnedBy(player)` — *any* tamed wolf answers `CONSUME`, whoever owns
it and whatever you are holding. `Minecraft#startUseItem` takes that answer at face value:

```java
InteractionResult interactionresult = this.gameMode.interactAt(this.player, entity, entityhitresult, interactionhand);
if (!interactionresult.consumesAction()) {
    interactionresult = this.gameMode.interact(this.player, entity, interactionhand);
}

if (interactionresult.consumesAction()) {
    ...
    return;            // ← never reaches this.gameMode.useItem(...)
}
```

`CONSUME` consumes the action, the method returns, and the use-item packet is never sent. The server
would have let the throw through — it is never asked. That is why this half of the module has no
side guard: it has to take effect on the client, or nothing happens at all.

Both interaction events are hooked, because `startUseItem` asks the positional one first
(`interactAt` → `EntityInteractSpecific`) and the plain one second (`interact` → `EntityInteract`).
Cancelling them with `InteractionResult.PASS` is enough: `Player#interactOn` hands the cancellation
result straight back, nothing consumes the action, and the code above falls through to `useItem`.

The wolf is the case that is provable in vanilla's source; other tamed animals have their own
`mobInteract`. The pass-through applies to every owned animal regardless.

## In detail

### "Beneficial" and "calming" are two different questions

The module asks two independent things about the potion, and it is easy to assume they are one:

| Test | Code | Decides |
|---|---|---|
| **Beneficial** | no effect of category `HARMFUL` | whether the right-click is passed through, so you can aim at the animal |
| **Calming** | carries at least one effect id from `calming_effects` | whether the anger is actually cleared |

They cross in all four combinations:

| Potion | Can be aimed at the pet | Calms |
|---|---|---|
| Healing, Regeneration | yes | yes |
| Swiftness, Fire Resistance, a splash water bottle | yes | no |
| A brew mixing Healing with a harmful effect | no — throw it past the animal instead | yes, if it lands in range |
| Harming, Poison | no | no |

`getAllEffects()` covers the base potion and any custom effects on the stack, so an empty potion — a
water bottle, a mundane potion — counts as beneficial and passes through without calming anything.

### What counts as a pet

`isOwnedPet` tests `OwnableEntity` with a non-null owner UUID, deliberately not `TamableAnimal`:

```java
return entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
```

In vanilla that interface is implemented by `TamableAnimal` (wolf, cat, parrot) and by
`AbstractHorse` — horse, donkey, mule, llama, camel and the two undead horses — the latter of which
are not tamables and so do not even get vanilla's owner exemption. Modded pets come along for free.

Which of them can be angry at you at all is a narrower set. Only the wolf and the llama carry a
`HurtByTargetGoal` (`Llama.LlamaHurtByTargetGoal`), and only the wolf is a `NeutralMob`. Cats,
parrots and horses never target a player in vanilla, so for them calming has nothing to clear —
which is exactly what happens: nothing, silently. They are covered for the sake of AI mods and
modded pets that do make them fight back.

### What calming does

`calm(LivingEntity, Player)` runs three checks against the thrower and does only what applies:

| Condition | Action |
|---|---|
| Pet is a `NeutralMob` and its persistent anger target is the thrower's UUID | `stopBeingAngry()` — which itself clears last-hurt-by, anger target, current target and the anger timer |
| Current target is the thrower | `setTarget(null)` |
| Last-hurt-by is the thrower | `setLastHurtByMob(null)` — otherwise `HurtByTargetGoal` picks it straight back up |

If none of the three matched, the method returns before everything else: no grace period, no
particles, no chime. A potion thrown at an animal that was never angry at you produces no feedback,
which is the honest answer — there was nothing to forgive. The potion's own effects apply as usual
either way; calming is added on top and replaces nothing.

There is no check that the thrower owns the animal. Anyone the pet is currently angry at can buy
themselves off, owner or stranger.

### Splash potions

The hook is `ProjectileImpactEvent`, which NeoForge fires in `ThrowableProjectile#tick` immediately
before the hit is resolved and before the projectile is moved on:

```java
HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
if (hitresult.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hitresult)) {
    this.hitTargetOrDeflectSelf(hitresult);
}
```

The potion therefore still sits exactly where vanilla's own `applySplash` is about to read it, and
the module copies that geometry by hand: the bounding box inflated by `(4.0, 2.0, 4.0)`, then
`distanceToSqr(living) < 16.0`. The event is observed only — never cancelled.

Two deliberate differences from vanilla's own splash: there is no `isAffectedByPotions()` filter and
no distance falloff. Calming does not care whether the potion could do anything to the animal, only
whether it was in range.

Lingering potions return early here; they are handled through the cloud they leave behind.

### Lingering clouds

A lingering potion's cloud lasts 600 ticks (30 seconds) and starts at radius 3, so a pet that only
wanders in later must be caught too. The cloud is picked up in `EntityJoinLevelEvent` when its owner
is a `Player` and its contents are calming — read through the accessor mixin, because vanilla ships
only a setter for `potionContents` — and stored as dimension plus thrower UUID.

Every 10 ticks the sweep resolves the cloud again (`ServerLevel#getEntity(UUID)`) and calms
everything owned inside it, mirroring `AreaEffectCloud#tick`'s own cylindrical test against the
cloud's *current* radius, so the area shrinks as the cloud does:

```java
double dx = living.getX() - cloud.getX();
double dz = living.getZ() - cloud.getZ();
if (dx * dx + dz * dz <= radius * radius) {
```

The candidates come from the cloud's bounding box, which vanilla scales as
`EntityDimensions.scalable(radius * 2, 0.5)` — a flat disc half a block high, not a sphere.

The sweep is coarser than vanilla's effect application in four ways, all of them in the player's
favour: vanilla applies effects only every 5 ticks, only after the 10-tick wait time, only to
entities outside its `victims` re-application map, and only to those `isAffectedByPotions()`. The
sweep ignores all four, so an animal standing in a fresh cloud is calmed during the waiting phase.

| Situation | What the sweep does |
|---|---|
| Thrower not in that level (offline, elsewhere) | Skips the cloud for this tick and keeps tracking it |
| Cloud gone, dead, or its dimension unloaded | Drops it from the map |
| Cloud leaves the level | `EntityLeaveLevelEvent` drops it at once |
| Cloud with no player owner (dispenser, command, witch) | Never tracked in the first place |

### The grace period

Clearing the anger once is not enough. `HurtByTargetGoal.canUse` re-fires whenever
`getLastHurtByMobTimestamp()` differs from the value it last stored, and AI-overhaul mods set targets
past vanilla's goals entirely. The module therefore hooks `LivingChangeTargetEvent`, which NeoForge
fires from `Mob#setTarget` itself:

```java
public void setTarget(@Nullable LivingEntity target) {
    LivingChangeTargetEvent changeTargetEvent = CommonHooks.onLivingChangeTarget(this, target, LivingTargetType.MOB_TARGET);
    if (!changeTargetEvent.isCanceled()) {
         this.target = changeTargetEvent.getNewAboutToBeSetTarget();
    }
}
```

The handler cancels only when the entity has a live `PeaceEntry` and the new target is precisely that
player. Everything else — a different player, a mob, `null` — passes. The window is 200 ticks (10
seconds) by default and is deliberately finite: attack the animal again after it lapses and it
reacts normally.

Entries expire in two places: lazily in the handler, when a cancel is considered after `expiresAt`,
and in the same 10-tick sweep that walks the clouds. The sweep measures against
`server.overworld().getGameTime()` while `calm` stamps the expiry from `mob.level().getGameTime()`.
Those are the same clock in vanilla — every non-overworld `ServerLevel` is built with a
`DerivedLevelData` whose `getGameTime()` delegates to the primary level data — but a mod that hands a
custom dimension its own level data could pull them apart.

### Feedback

When, and only when, anger was actually cleared and `calm_feedback` is on: four `HEART` particles at
0.75 × the animal's height with a spread of 0.3 / 0.2 / 0.3 (server level only), plus
`AMETHYST_BLOCK_CHIME` on `SoundSource.NEUTRAL` at volume 0.7, pitch 1.5.

### Which half runs where

| Half | Runs on | Gated by |
|---|---|---|
| Pass-through (`EntityInteract`, `EntityInteractSpecific`) | Client **and** server — no side guard, by design | `enabled` + `allow_throwing_at_pets` |
| Calming, cloud tracking, grace period | Server only — explicit `isClientSide()` returns | `enabled` only |

The asymmetry matters when reading the configuration: `allow_throwing_at_pets = false` takes away
only the convenience of aiming at the animal. A potion thrown past it, or any lingering cloud, still
calms.

<!-- vpa:config:start -->
## Configuration

Section `[modules.pet_potions]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_pet_potions-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `allow_throwing_at_pets` | boolean | `true` | — | Let beneficial splash/lingering potions be thrown while aiming at a tamed animal (vanilla swallows that right-click - the client reports CONSUME for any tamed wolf - so without this the potion never leaves your hand). Gates ONLY the pass-through half (PetPotionsModule.java:140): calming still works when this is false, you just cannot aim at the animal itself. |
| `calm_feedback` | boolean | `true` | — | Play 4 heart particles above the animal and an amethyst chime (NEUTRAL, vol 0.7, pitch 1.5) when it is calmed, so it is visible that it worked. Fires only when anger towards the thrower was actually cleared. |
| `calming_effects` | list | `List.of("minecraft:instant_health", "minecraft:regeneration")` | elements must be String (defineList validator o -> o instanceof String); new entries default to "minecraft:instant_health" | Effect ids (namespace:path) that make an angry owned animal forgive the player who threw the potion; works for splash and lingering potions alike. An empty list switches calming off entirely (isCalming returns false immediately) without affecting the throw-at-pets fix. |
| `peace_duration_ticks` | int | `200` | 0 ~ 24000 | How long (in ticks) a calmed animal refuses to re-target the thrower; 0 disables the grace period - the anger is still cleared once, it may just come straight back. Only one window per pet exists, so a second player's calm overwrites the first player's protection. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| One grace period per pet | `peaceWindows` maps the pet's UUID to a single player. If a second player calms the same animal, their window overwrites the first player's — two people cannot hold protection on the same wolf at once. |
| Nothing is persisted | Both maps are plain in-memory `HashMap`s, cleared on `ServerStoppedEvent`. A restart drops every grace period. Whether a lingering cloud that outlives the restart is picked up again depends on `EntityJoinLevelEvent` firing for a deserialised cloud and its owner resolving to a player at that moment — not verified here. |
| The pack is alerted separately | A wolf's `HurtByTargetGoal` has `setAlertOthers()`, which calls `setTarget(you)` on every wolf of the same owner within follow range. Those outside the splash were never calmed and hold no window of their own, so they still come for you. |
| No owner check on the thrower | Whoever the animal is angry at can calm it, including a player who is not its owner. |
| Brain-driven mobs | The grace period hooks `Mob#setTarget`. A mob that keeps its target in `MemoryModuleType.ATTACK_TARGET` and overrides `getTarget()` with `getTargetFromBrain()` — no vanilla pet does, but modded ones might — bypasses both the `getTarget()` check in `calm` and the cancel. |
| Potions with no player owner | A dispenser-fired or command-spawned potion has no owner (`new ThrownPotion(level, x, y, z)`), and a witch's has a witch. Neither calms anything, and their clouds are never tracked. |
| A client without the module | The pass-through half lives on the client. On a server where a player's own client does not run this module, their right-click is still swallowed by `Wolf#mobInteract`; calming from a potion thrown elsewhere is unaffected. |
| Copied vanilla geometry | The splash box `(4.0, 2.0, 4.0)` / `< 16.0` and the cloud's cylinder are re-implemented by hand. Should Mojang change those numbers, the calmed area silently drifts away from the real splash. |
| Module disabled at runtime | Every handler that matters is gated on `isModuleEnabled()`, so switching it off takes effect immediately. `tickCounter` stops advancing with it, so the 10-tick sweep comes back on a shifted phase — cosmetic. |
| No in-game test on record | This repository has no test sources and nothing here records a play-test of this module. Everything above is read from the code. |

## Under the hood

No items, blocks, entities, commands, keybinds, recipes, loot tables or lang keys — the module is
event logic plus one accessor mixin.

| Event | Bus | Purpose |
|---|---|---|
| `PlayerInteractEvent.EntityInteract` | game | Cancels with `PASS` so the throw happens (no side guard) |
| `PlayerInteractEvent.EntityInteractSpecific` | game | The same for the positional right-click |
| `ProjectileImpactEvent` | game | Splash calming; observed, never cancelled |
| `EntityJoinLevelEvent` | game | Starts tracking a calming lingering cloud |
| `EntityLeaveLevelEvent` | game | Drops a tracked cloud |
| `ServerTickEvent.Post` | game | Every 10th tick: expire grace periods, re-sweep clouds |
| `LivingChangeTargetEvent` | game | Cancels a re-target of the protected player |
| `ServerStoppedEvent` | game | Clears both maps |

`EntityLeaveLevelEvent` and `ServerStoppedEvent` are not gated on `isModuleEnabled()`; harmless,
because both maps stay empty while the module is off.

**The mixin.** `AreaEffectCloudAccessor` is a single read-only `@Accessor("potionContents")` on
`net.minecraft.world.entity.AreaEffectCloud` — vanilla exposes only the setter, so there is no other
way to ask a cloud what it carries. It is listed in the common `mixins` block, not the client one,
and has exactly one call site. Nothing writes through it.

**Classes.**

| Class | Role |
|---|---|
| `modules/pet_potions/PetPotionsModule` | All eight handlers, the calming logic and the two maps |
| `modules/pet_potions/config/PetPotionsConfig` | The four keys; every accessor falls back to its default when the spec is not built yet |
| `modules/pet_potions/models/PeaceEntry` | `record (UUID player, long expiresAt)` |
| `modules/pet_potions/models/CalmingCloud` | `record (ResourceKey<Level> dimension, UUID thrower)` |
| `mixin/pet_potions/AreaEffectCloudAccessor` | The potion-contents accessor |
| `standalone/pet_potions/PetPotionsStandalone` | `@Mod("vpa_pet_potions")` entry point |

**Configuration side.** The spec is registered as `ModConfig.Type.COMMON`, in the bundle and in
`StandaloneModuleBootstrap` alike, so the client has the values the pass-through half reads there.

**Standalone jar.** `vpa_pet_potions` ships the one mixin and no data files of its own, and depends
only on `vpa_core`.

**Dead code.** `getProtectedPets()` is documented as being for debugging and tests and has no callers
anywhere in the repository; there is no `src/test` at all.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
