# Dispenser Bucket Guard

## Overview

A dispenser that tries to use a bucket and cannot **throws the bucket on the floor**. An empty
bucket in front of a block that holds no fluid, a water bucket in front of a block the water
cannot go into — in both cases vanilla gives up and ejects the item as an entity.

For a hand-operated dispenser that is a shrug. For an automated water door it is fatal: the loop
fires one time too often, the bucket is gone, and the machine is dead until someone swims down and
picks it up.

This module keeps the bucket in the dispenser instead. A failed attempt becomes a no-op, with
vanilla's "dispenser failed" click as feedback. Successful dispenses are untouched.

---

## Why vanilla does it

Both bucket behaviours in `DispenseItemBehavior.bootStrap()` end in the same fallback — the plain
`DefaultDispenseItemBehavior`, i.e. "spit the item out" — whenever their real job fails:

```java
// empty bucket
ItemStack picked = bucketPickup.pickupBlock(null, level, pos, state);
if (picked.isEmpty()) {
    return super.execute(source, stack);          // ← ejects the bucket
}

// every filled bucket
if (containerItem.emptyContents(null, level, pos, null, stack)) {
    ...
} else {
    return this.defaultDispenseItemBehavior.dispense(source, stack);   // ← ejects the bucket
}
```

That fallback is the right answer for a *dropper-style* item with no special action. For a bucket
it silently destroys the machine that uses it.

---

## Behaviour

| Situation | Vanilla | With this module |
|---|---|---|
| Empty bucket, fluid source in front | picks it up | unchanged |
| Empty bucket, nothing to pick up | **bucket ejected** | bucket stays, click |
| Empty bucket, non-fluid block in front | **bucket ejected** | bucket stays, click |
| Filled bucket, target replaceable | places contents | unchanged |
| Filled bucket, target blocked | **bucket ejected** | bucket stays, click |
| Anything that is not a bucket | thrown / used | unchanged |

Verified on a headless 1.21.1 server, guard off vs. guard on, same command script. The three
ejecting cases flip to "kept"; the two working cases produce a byte-identical dispenser inventory
(`water_bucket` after a pickup, `bucket` after a placement), and a snowball still leaves the
dispenser.

---

## Implementation

`DispenserBlock.DISPENSER_REGISTRY` is public, so no mixin and no access transformer is needed. At
`FMLCommonSetupEvent` — enqueued onto the main thread, because the registry is a plain map filled
during class init — every entry whose item is a `BucketItem` or `SolidBucketItem` is replaced by a
`BucketDispenseGuard` wrapping the previous behaviour. Wrapping rather than replacing keeps
whatever another mod registered for the same item, and the guard skips entries it has already
wrapped, so it can never stack.

The enabled check happens per dispense, so toggling the module in the config takes effect
immediately — no restart. (Disabling it *before* startup means the wrapper is never installed at
all, same as every other VPA module.)

### Predicting the failure

Vanilla's own checks cannot be reused: `pickupBlock` drains the block as a side effect of
succeeding, and `emptyContents` places the fluid. The guard therefore mirrors them read-only.

**Empty bucket** — vanilla's implementations hand out a filled bucket exactly when a fluid *source*
sits there (`LiquidBlock` wants `LEVEL == 0`, a waterloggable block wants `WATERLOGGED == true`);
powder snow is the one block that always yields:

```java
if (!(state.getBlock() instanceof BucketPickup)) {
    return false;
}
return state.getBlock() instanceof PowderSnowBlock || state.getFluidState().isSource();
```

**Filled bucket** — the placement test from the top of `BucketItem.emptyContents`. Because the
dispenser passes no `BlockHitResult`, a failure there has no neighbour fallback and goes straight
to the ejection:

```java
state.isAir()
    || state.canBeReplaced(content)
    || (state.getBlock() instanceof LiquidBlockContainer c && c.canPlaceLiquid(null, level, pos, state, content));
```

`Items.BUCKET` is itself a `BucketItem`, carrying `Fluids.EMPTY` — which is not a `FlowingFluid`.
That single check routes the empty bucket to the pickup branch and every filled one to the
placement branch, modded buckets included.

**Everything unknown delegates.** A `SolidBucketItem` needs plain air (`isEmptyBlock`); any other
item the wrapper cannot model returns "would succeed" and is handed to the original behaviour
untouched. The guard can therefore only ever *prevent* an ejection, never cause one.

---

## Known limits

| Limit | Effect |
|---|---|
| A modded `BucketPickup` that yields without a fluid source | predicted as "nothing there", so the bucket is held back although vanilla would have picked something up. Disable the module if a mod does this. |
| `emptyContents` failing *after* its placement check (`setBlock` refused) | not predicted — vanilla ejects as before. |
| Milk bucket | has no dispense behaviour at all in vanilla; it is thrown like any other item, unchanged. |
| Module disabled at startup | the wrapper is never installed, so enabling it later needs a restart. |

---

## Config

`[modules.dispenser_bucket_guard]`

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | Module on/off (takes effect per dispense while the server runs) |
| `play_fail_sound` | `true` | Play vanilla's "dispenser failed" click when a bucket is held back. `false` keeps a repeatedly triggered dispenser silent. |
