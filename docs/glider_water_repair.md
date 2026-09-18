# Glider Water Repair

## Overview

A broken Gliders paraglider thrown into water becomes usable again. Only the broken flag clears —
the durability it lost stays lost.

---

## What the Gliders mod does

Thunderstorms are supposed to be dangerous. Glide through rain for more than 200 ticks and
`GliderUtil.lightningLogic` spawns a real bolt straight onto the player:

```java
if (player.level().random.nextInt(24) == 0 && lightningTimer > 200 && !GliderItem.hasBeenStruck(glider)) {
    LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
    lightningBolt.setPos(player.getX(), player.getY(), player.getZ());
    lightningBolt.setVisualOnly(false);
    level.addFreshEntity(lightningBolt);
}
```

`GliderEventsNeoForge.onEntityStruckByLightning` then decides what it costs. With the copper upgrade,
nothing. Without it, `GliderItem.setBroken(chestItem, true)`.

**That part is deliberately left alone.** The strike is the point of the mechanic.

---

## What this module changes

The way back. The broken flag is the `vc_gliders:broken` data component, and it does three things at
once: the model switches to `damaged_glider`, `isGlidingEnabled` returns false, and the only cure the
mod offers is an anvil plus the **Reinforced Paper of that glider's exact tier**:

| Tier | Repair material | What that costs |
|---|---|---|
| Wood | `reinforced_paper` | 5 leather + 4 paper |
| Iron | `reinforced_paper_iron` | the above + 8 iron ingots |
| Gold | `reinforced_paper_gold` | the above + 8 gold ingots |
| Diamond | `reinforced_paper_diamond` | the above + 8 diamonds |
| Netherite | `reinforced_paper_netherite` | the above + 8 netherite scrap |

Not an iron ingot on its own, not a diamond — the tier's paper, which nobody carries on a flight.
Meanwhile the broken glider wears a charred sprite and looks exactly like it is on fire, so the first
thing anyone tries is to dunk it.

Here that works. A broken glider lying in water as a dropped item loses its broken flag, with
vanilla's extinguishing hiss and a puff of steam. The durability stays where the strike left it, so:

* the strike still costs something;
* the Reinforced Paper repair keeps its purpose — it is still the only way to get the bar back;
* you are not locked out of flying until you have mined eight iron.

---

## Implementation notes

The check rides on `EntityTickEvent.Post`, filtered to `ItemEntity` first and then throttled to once
a second per item (`tickCount % 20`). The item has to sink and bob anyway, and a second of delay is
the beat the hiss wants.

Nothing is compiled against the Gliders mod:

```java
DataComponentType<?> broken = BuiltInRegistries.DATA_COMPONENT_TYPE
        .get(ResourceLocation.fromNamespaceAndPath("vc_gliders", "broken"));
```

Gliders are recognised by their registry namespace, and the component is resolved lazily the first
time it is needed, so mod load order does not matter. `shouldInitialize()` checks for `vc_gliders`,
so the module is inert without it. Should the Gliders mod ever rename the component, the module logs
a warning once and does nothing.

---

## Configuration

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | The module does one thing; this is the only switch it needs. |
