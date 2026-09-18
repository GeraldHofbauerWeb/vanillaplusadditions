# Glider Lightning Guard

## Overview

A lightning strike costs the Gliders paraglider **durability** instead of wrecking it outright.

---

## What happens without this module

The Gliders mod makes thunderstorms dangerous on purpose. Glide through rain for more than 200
ticks and `GliderUtil.lightningLogic` spawns a real bolt straight onto the player:

```java
if (player.level().random.nextInt(24) == 0 && lightningTimer > 200 && !GliderItem.hasBeenStruck(glider)) {
    LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
    lightningBolt.setPos(player.getX(), player.getY(), player.getZ());
    lightningBolt.setVisualOnly(false);
    level.addFreshEntity(lightningBolt);
}
```

`GliderEventsNeoForge.onEntityStruckByLightning` then decides what the strike does to the glider:

* **with** the copper upgrade — nothing (it only sets a "has been struck" flag);
* **without** it — `GliderItem.setBroken(chestItem, true)`.

That broken flag is the `vc_gliders:broken` data component. It does three things at once: the item
model switches to `damaged_glider` (the charred sprite that looks like the glider is on fire),
`isGlidingEnabled` returns false, and the only way back is an anvil with the **Reinforced Paper of
that glider's tier** — `reinforced_paper`, `…_iron`, `…_gold`, `…_diamond`, `…_netherite`. Not an
iron ingot, not a diamond. Most players do not carry one, which is why the glider reads as "ruined
for good".

---

## What this module does

Two handlers on `EntityStruckByLightningEvent`:

| Priority | Job |
|---|---|
| `HIGHEST` | Note which of the player's gliders were **already** broken before the bolt |
| `LOWEST` | Take the broken flag back off any glider that was intact a moment ago, and charge the strike to the durability bar instead |

The snapshot matters: without it, a spare broken glider in a Curios slot would be repaired for free
by every lightning strike.

The durability charge is a quarter of the bar by default (`durability_cost`), and it always stops
one point short of breaking, so there is always something left to repair.

The bolt itself is left completely alone — the damage, the fire, the fright and the sound all stay.
Only the total loss goes away.

---

## Where the glider is looked for

The Gliders mod checks the chest armor slot first and the Curios back/cape slots after that, so this
module covers both. Curios access lives in `compat/GliderCuriosAccess`, behind `CuriosGate`.

## No compile-time dependency

Nothing here is compiled against the Gliders mod:

```java
DataComponentType<?> broken = BuiltInRegistries.DATA_COMPONENT_TYPE
        .get(ResourceLocation.fromNamespaceAndPath("vc_gliders", "broken"));
```

Gliders are recognised by their registry namespace, and the component is resolved lazily the first
time it is needed, so mod load order does not matter. `shouldInitialize()` checks for `vc_gliders`,
so the module is simply inert without it. Should the Gliders mod ever rename the component, the
module logs a warning once and does nothing.

---

## Configuration

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | |
| `durability_cost` | `0.25` | Share of maximum durability a strike costs. `0` makes strikes free. |
