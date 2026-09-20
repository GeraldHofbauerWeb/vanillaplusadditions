# End Oxygen

> **TL;DR** — The End has no breathable air: your bubbles drain the whole time you are there and you
> start taking damage once they run out, unless you carry a Create backtank or stand in Conduit
> Power.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `end_oxygen` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Download** | [`vpa_end_oxygen.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_end_oxygen.jar) · also needs `vpa_core` |
| **Config section** | `[modules.end_oxygen]` |
| **Since** | `v0.9.0` |
<!-- vpa:meta:end -->

## What it does

Step through the portal and the air bar appears. It does not fill back up. Your air drains whatever
you are standing on, and once the bar is empty you take damage every second until you leave, find
breathing gear or die — the death message reads *"&lt;name&gt; ran out of oxygen"*.

With the defaults that is one air unit every 2 ticks, so a full bar of 300 buys about **30 seconds**
after you arrive, and then 2 damage (one heart before armour) per second.

Four things change that:

| | Effect |
|---|---|
| **Water Breathing** | Slows the drain rather than stopping it: 90 seconds at level I, 2½ minutes at level II. |
| **Create backtank** | Real breathing. The bar refills while the tank drains, roughly 15 minutes out of a full one. By default it also wants a diving helmet — see [below](#the-diving-helmet-no-tag-defines), that gate does not currently work. |
| **Conduit Power** | Full air every tick, no damage at all. The [End Conduit](end_conduit.md) is the source built for this; any Conduit Power does it. |
| **Creative or Spectator** | Skipped entirely. |

Only players are affected. Endermen, shulkers and everything else in the End breathe as they always
did.

There is no icon on this page: the module adds no item and no block.

## In detail

Everything lives in one handler on NeoForge's `LivingBreatheEvent`, which fires once per tick for
every living entity from `LivingEntity.baseTick`. The event decides what happens to the air bar:

```java
if (breatheEvent.canBreathe()) {
    entity.setAirSupply(Math.min(entity.getAirSupply() + breatheEvent.getRefillAirAmount(), entity.getMaxAirSupply()));
} else {
    entity.setAirSupply(entity.getAirSupply() - breatheEvent.getConsumeAirAmount());
}
```

In the End on dry land NeoForge hands us `canBreathe = true` and a refill of up to 4, because the
player's eye is in air. The module's whole job is to turn that answer around.

### One handler, in order

The handler leaves immediately unless the entity is a `Player`, the dimension is `minecraft:the_end`
and the player is in neither creative nor spectator mode. After that it runs in a fixed order, and
the order is what produces most of the edge cases below:

| # | Branch | What it sets |
|---|---|---|
| 1 | Conduit Power (and `conduit_power_grants_air`) | `canBreathe = true`, refill `getMaxAirSupply()` — then **returns**, so the damage block never runs |
| 2 | Floor | `air < 1` is lifted back to 1 |
| 3 | Backtank with air (and the helmet gate) | `canBreathe = true`, refill 1, and one air unit out of the tank every `backtank_depletion_rate` ticks |
| 4 | Otherwise | `canBreathe = false`, consume 1 — but only on the ticks the interval allows |
| 5 | Damage | `air <= 1` and `tickCount % damage_tick == 0` → `vanillaplusadditions:out_of_oxygen` |

Step 5 is **not** inside an `else`. It runs on the backtank path too.

### How long a lungful lasts

A player's maximum air is vanilla's 300, and the module writes the consume amount itself:

```java
int totalInterval = baseInterval + effectBonus;
if (player.tickCount % totalInterval == 0) {
    event.setConsumeAirAmount(1);
} else {
    event.setConsumeAirAmount(0);
}
```

`effectBonus` is `(amplifier + 1) × water_breathing_effect_interval_bonus`, so a Water Breathing
potion widens the gap between depletion steps instead of stopping it:

| Water Breathing | Interval | Ticks from full to empty | Real time |
|---|---|---|---|
| none | 2 | 598 | ~30 s |
| I (amplifier 0) | 6 | 1 794 | ~90 s |
| II (amplifier 1) | 10 | 2 990 | ~2:30 |

Because the module *assigns* the consume amount, it also overwrites what NeoForge put there.
Vanilla passes in `airSupply - decreaseAirSupply(airSupply)`, which is 0 whenever the
`OXYGEN_BONUS` attribute — Respiration — wins its roll. That value is thrown away here, so
**Respiration does nothing in the End**. Water Breathing is the only enchantment-or-potion lever,
and only through the config key above.

### Why you never actually drown

Vanilla's drowning damage is never reached, and that is deliberate. The floor keeps the bar at 1
before the event's own arithmetic runs, and the module's consume amount is at most 1, so the bar
bottoms out at exactly 0:

```java
if (player.getAirSupply() < 1) {
    player.setAirSupply(1);
}
```

NeoForge does post a `LivingDrownEvent` once air reaches 0, but its vanilla-populated constructor is
`this(entity, entity.getAirSupply() <= -20, 2.0F, 8)` — at 0 the entity is not "actively drowning",
so there are no bubble particles and no `minecraft:drown` damage. Every point of damage you take in
the End comes from this module, at this module's rate.

### The damage

```java
DamageSource outOfOxygen = player.level().damageSources().source(OUT_OF_OXYGEN);
player.hurt(outOfOxygen, getConfig().getOutOfAirDamage());
```

`data/vanillaplusadditions/damage_type/out_of_oxygen.json` declares `exhaustion` 0.1, the message id
`vanillaplusadditions.out_of_oxygen` and `when_caused_by_living_non_player` scaling. What it does
*not* declare is any tag, and the repository ships no damage-type tag files at all — so this is an
ordinary damage source where vanilla drowning is a special one:

| | `minecraft:drown` | `vanillaplusadditions:out_of_oxygen` |
|---|---|---|
| In `#minecraft:bypasses_armor` | yes | **no** — armour points reduce it |
| In `#minecraft:no_impact`, `#no_knockback` | yes | no |
| `effects` | `drowning`, the gurgle | unset, so the plain hurt sound |
| Exhaustion per hit | 0.0 | 0.1 |

Invulnerability frames apply as well: `LivingEntity.hurt` drops a hit of equal strength while
`invulnerableTime > 10`, and a hit sets that to 20. `damage_tick` below 11 therefore does not
actually raise the damage rate, it just gets swallowed.

The other consequence of step 5 living outside the `else`: walk into the End with an empty bar and a
working backtank and you can still be hit **once**, on the arrival tick, if that tick happens to be a
multiple of `damage_tick`. From the next tick the refill has lifted the bar to 2 and the condition is
false.

### Breathing from a backtank

The module never looks at the item itself; it asks Create:

```java
List<ItemStack> backtanks = CreateCompat.isLoaded()
        ? CreateBacktankCompat.getBacktanksWithAir(player)
        : List.<ItemStack>of();
```

`BacktankUtil.getAllWithAir` walks the four armour slots for anything in
`create:pressurized_air_sources` (plus whatever other suppliers Create has been given through
`addBacktankSupplier`), keeps the stacks with air left, and **sorts them ascending by air**. The
module always takes `backtanks.get(0)`, so with two tanks on you it drains the emptier one first and
only moves to the fuller one once the first is spent. The HUD reads the same slot, so the timer is
the emptier tank's, not the total.

While a tank supplies air the bar refills at 1 per tick — a bar emptied before you found the tank
takes 300 ticks, 15 seconds, to fill again — and one air unit leaves the tank every
`backtank_depletion_rate` ticks. Against Create's own defaults (`airInBacktank` 900, plus 300 per
level of Capacity) that works out as:

| Tank | Air | At `backtank_depletion_rate = 20` |
|---|---|---|
| Plain backtank | 900 | 900 s = 15:00 |
| Capacity III (Create's maximum) | 1 800 | 1 800 s = 30:00 |
| `backtank_depletion_rate = 0` | — | never depletes; an infinite tank |

When the tank does run dry it drops out of `getAllWithAir`, the handler falls through to step 4, and
the bar starts from wherever it was — full, in practice, so you get the ordinary 30 seconds of grace
to get out.

### The diving helmet no tag defines

With `backtank.requires_full_set` at its default `true`, the backtank branch also wants a helmet:

```java
boolean hasDivingHelmet = player.getItemBySlot(EquipmentSlot.HEAD).is(DIVING_HELMETS);

if (!backtanks.isEmpty() && (!getConfig().requiresFullSet() || hasDivingHelmet)) {
```

`DIVING_HELMETS` is the item tag `vanillaplusadditions:diving_helmets` — and **nothing in this
repository fills it**. The only tag file under `data/vanillaplusadditions/tags/item/` is
`arm_goggles.json`, there is no datagen in the project, and a repo-wide search finds the string
`diving_helmets` in exactly one place: the `TagKey` declaration itself. An empty tag matches no
item, so on a server with no datapack of its own the condition can never be true and backtanks
supply no air at all.

Two ways out, both provable from the source: set `backtank.requires_full_set = false`, or add a
datapack that gives the tag some values (`create:copper_diving_helmet`,
`create:netherite_diving_helmet`).

<!-- TODO: confirm in game. Read off the source only — nothing in this repository records a test of
     the backtank path with the default requires_full_set = true. -->

### Conduit Power

```java
if (getConfig().conduitPowerGrantsAir() && player.hasEffect(MobEffects.CONDUIT_POWER)) {
    event.setCanBreathe(true);
    event.setRefillAirAmount(player.getMaxAirSupply());
    return;
}
```

A refill of 300 is clamped against the maximum by NeoForge, so the bar is simply full again every
tick. The `return` is the important part: this branch skips the damage block entirely, so Conduit
Power is the one state in which the module cannot hurt you even for a single tick.

The [End Conduit](end_conduit.md) exists for this — it grants `CONDUIT_POWER` for 260 ticks to every
player in range on dry End ground. But the branch only asks for the effect, so a vanilla conduit in a
pool of water you hauled into the End, or an `/effect` command, opens it just the same.

### The air bar and the backtank timer

Vanilla only draws the air bar when there is a reason to:

```java
if (player.isEyeInFluid(FluidTags.WATER) || j3 < i3) {
```

In the End neither half holds while your air is full, which is exactly when the module most wants you
to see the bar. So the client hook lies to it by a single unit, in `RenderGuiLayerEvent.Pre` on the
`minecraft:air_level` layer:

```java
if (player.getAirSupply() >= player.getMaxAirSupply()) {
    player.setAirSupply(player.getMaxAirSupply() - 1);
}
```

299 of 300 still rounds to ten full bubbles, so the bar looks untouched — and the server resyncs the
real value on its next entity-data update anyway. It is a display trick and nothing else.

The `Post` hook on the same layer draws the backtank overlay, and only if Create is loaded and a tank
with air is worn: the item itself at `guiWidth/2 + 90`, `guiHeight - 53` — the right-hand end of the
status rows — with `MM:SS` printed 16 pixels to its right. A stack carrying
`DataComponents.FIRE_RESISTANT`, i.e. Create's netherite backtank, is drawn 9 pixels lower.

| Detail | Behaviour |
|---|---|
| Time shown | `max(0, air - 1) × backtank_depletion_rate ÷ tickrate`, in seconds. At default tick rate and depletion rate that is one second per air unit. |
| `backtank_depletion_rate = 0` | Hardcoded to 5999 s, so it reads a permanent `99:59`. |
| Cap | Minutes above 99 clamp to `99:59`. |
| Colour | White, flipping to red while `air < 60 && air % 2 == 0` — with the defaults that is an alternating flash once a second over the last minute. |

The position is fixed, not stacked onto vanilla's `rightHeight`, so a crowded HUD can overlap it.

<!-- vpa:config:start -->
## Configuration

Section `[modules.end_oxygen]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_end_oxygen-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `air_consumption_interval` | int | `2` | 1 ~ 300 | The number of ticks between each air depletion (1 = standard, higher = slower). |
| `backtank.backtank_depletion_rate` | int | `20` | 0 ~ 1000 | The number of ticks between each backtank air depletion (0 to disable depletion). |
| `backtank.requires_full_set` | boolean | `true` | — | Whether a diving helmet is required along with the backtank to breathe. |
| `conduit_power_grants_air` | boolean | `true` | — | Whether the Conduit Power effect (e.g. from an End Conduit) lets the player breathe freely in the End, refilling air each tick. Default true. |
| `damage_tick` | int | `20` | 1 ~ 200 | The interval (in ticks) at which damage is applied when out of air. |
| `out_of_air_damage` | double | `2.0` | 0.5 ~ 20.0 | The amount of damage to apply when the player is out of air in the End. |
| `water_breathing_effect_interval_bonus` | int | `4` | 0 ~ 100 | Additional ticks added to the consumption interval per level of Water Breathing effect. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| `requires_full_set = true` with no datapack | The default. `vanillaplusadditions:diving_helmets` is empty in this repository, so the backtank branch is unreachable and a backtank gives you nothing. Set it to `false` or fill the tag. |
| No Create installed | `CreateCompat.isLoaded()` is false, the backtank branch and the whole HUD overlay are skipped, and the module degrades to plain breath-holding plus Water Breathing and Conduit Power. The changelog records the bundle booting and the End breathing working on a bare NeoForge server without the library mods (`v1.0.0-beta.25`). |
| Only `minecraft:the_end` | The check is `player.level().dimension() == Level.END`, a literal dimension key. A modded End-like dimension, or a second End from a datapack, is not covered. |
| Only players | `LivingBreatheEvent` fires for every living entity; the handler filters to `Player`. Mobs in the End are untouched. |
| Respiration | Overwritten, see above. It has no effect on the drain in the End. |
| Water Breathing | Never grants breathing here — it only widens the interval. Being submerged in water in the End changes nothing either: the module overrides NeoForge's answer whichever way it came out. |
| Armour | Reduces `out_of_oxygen` damage, unlike vanilla drowning. A player in full netherite takes noticeably longer to die of suffocation than one in nothing. |
| Another mod on `LivingBreatheEvent` | The handler runs at default priority and sets the values rather than cancelling, so a later handler wins. Nothing here guards against that. |
| Module disabled at runtime | Works in both directions: the handler is registered unconditionally at startup (`shouldInitialize()` is not overridden) and re-reads `isModuleEnabled()` on every event. The HUD class is annotation-registered and independent of module initialisation, and checks the same flag. |
| Client-side air mutation | The `Pre` hook writes to the local player's air supply every frame the bar would otherwise be hidden. Harmless because the server owns the value, but it does mean the client's air is briefly a fabrication. |

## Under the hood

No mixins, no commands, no keybinds, no network payloads, no registered content. The module is one
event handler and one HUD class.

| Event | Bus | Side | Purpose |
|---|---|---|---|
| `LivingBreatheEvent` | game, `NeoForge.EVENT_BUS.register(this)` in `onInitialize` | both | Everything above |
| `RenderGuiLayerEvent.Pre` (`minecraft:air_level`) | game | client | Forces the bubble bar to render |
| `RenderGuiLayerEvent.Post` (`minecraft:air_level`) | game | client | Backtank item and timer |

The breathing handler is an instance handler on the **common** bus, so it runs on both physical
sides; `player.hurt` is a no-op on the client (`LivingEntity.hurt` returns false there), so only the
server ever deals the damage. Only the HUD class is `Dist.CLIENT`.

| File | Role |
|---|---|
| `modules/end_oxygen/EndOxygenModule.java` | The handler, the damage-type key, the two tag keys |
| `modules/end_oxygen/config/EndOxygenConfig.java` | The seven config values |
| `modules/end_oxygen/compat/CreateCompat.java` | "Is Create there?" — names no Create type |
| `modules/end_oxygen/compat/CreateBacktankCompat.java` | Every `BacktankUtil` call, vanilla types in its signatures |
| `modules/end_oxygen/client/EndOxygenClientEvents.java` | The air-bar trick and the backtank overlay |
| `standalone/end_oxygen/EndOxygenStandalone.java` | `@Mod("vpa_end_oxygen")` entry point |
| `data/vanillaplusadditions/damage_type/out_of_oxygen.json` | The damage type |

**The Create split is not cosmetic.** `CreateBacktankCompat` imports `BacktankUtil`, so asking *that*
class whether Create is installed would resolve, link and verify it, and the JVM verifier may load
the Create types out of its method bodies. The question therefore lives in `CreateCompat`, which
names no Create type and caches `ModList.get().isLoaded("create")` once. `v1.0.0-beta.72` split it
out for exactly this reason, after the same trap had taken down `overpacked_extensions` — the
changelog calls it hardening rather than a live crash, because Create happened to be present on both
distribution paths at the time.

**Timing is keyed off `player.tickCount`,** not a countdown that starts when you enter the End. Both
the consumption interval and `damage_tick` are `tickCount % n == 0`, so the phase is whatever the
player entity's age happens to be — it makes no difference to how long you survive, but it does mean
the first depletion step after a portal can land anywhere in the interval.

**Loose ends in the module,** all harmless and all worth knowing before editing it:

* `EndOxygenModule.BACKTANKS`, the `TagKey` `vanillaplusadditions:backtanks`, is `public static
  final` and read by nothing. Backtank detection runs entirely through Create. The page that used to
  live here claimed the tag "controls which items count"; it never did.
* `message.action_bar.low_oxygen_alert` and `message.action_bar.remaining_oxygen` sit in `en_us.json`
  with no reference anywhere in Java — leftovers from an earlier HUD, not features.
* `EndOxygenConfig.conduitPowerGrantsAir()` is written as `conduitPowerGrantsAir == null ||
  conduitPowerGrantsAir.get()`, so it answers `true` before the spec is built. The other six getters
  have no such guard.

**Standalone jar.** `vpa_end_oxygen` ships `modules/end_oxygen/**` — the HUD class included — plus
the entry point and `out_of_oxygen.json`; the death-message lang string travels in `vpa_core`, which
every module jar requires. The generated per-module `neoforge.mods.toml` declares only `vpa_core`,
`neoforge`, `minecraft` and an `incompatible` against the bundle; it does **not** carry the optional
`create` dependency the bundle's own toml has, which only affects load ordering, since
`ModList.isLoaded` is answerable either way.

**Testing.** This repository has no unit tests, and nothing in it records an in-game test of the
default backtank path (see the TODO above). The Create-free boot is the one claim the changelog
backs.

## See also

* [End Conduit](end_conduit.md) — the block that makes Conduit Power available on dry End ground
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
