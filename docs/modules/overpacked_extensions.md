# Overpacked Extensions

> **TL;DR** — Open the compartments of the giant backpack you are wearing with a keypress instead of
> taking it off, and dial the full-backpack movement penalty down to anything you like — including
> off entirely.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `overpacked_extensions` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | [Overpacked](https://modrinth.com/mod/overpacked) <sub>tested 2.0.1</sub>, [Curios API](https://modrinth.com/mod/curios) <sub>tested 9.5.1</sub>, [Quark](https://modrinth.com/mod/quark) <sub>tested 4.1-482</sub> |
| **Download** | [`vpa_overpacked_extensions.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_overpacked_extensions.jar) · also needs `vpa_core` |
| **Config section** | `[modules.overpacked_extensions]` |
| **Since** | `v1.0.0-beta.56` |
<!-- vpa:meta:end -->

## What it does

Two quality-of-life features for [Overpacked](https://modrinth.com/mod/overpacked)'s giant backpacks.
They share the module's `enabled` flag and each has its own switch:

| Part | Config key | What it does | Runs on |
|---|---|---|---|
| A | `slowdown_multiplier` | Rescales the movement penalty a loaded backpack costs you | server |
| B | `backpack_keys_enabled` | Keybinds that open the compartments of the backpack you **wear** | client + server |

**Part A defaults to `0.0`, which is a gameplay change out of the box.** Enable the module and change
nothing else and carried backpacks cost no speed at all. `1.0` reproduces Overpacked untouched, `0.5`
halves the penalty, `2.0` doubles it.

Part B is about the backpack on your back. Overpacked has no way to look inside a worn one — you take
it off, place it, open it, pick it up again. Here **B** opens the main compartment; the right and left
compartments are unbound by default and can be bound in Controls under *Vanilla+ Additions*. What
opens is Overpacked's own screen, not a copy of it, so it looks and behaves exactly as it does on a
placed backpack.

Sorting and searching inside the backpack are deliberately **not** part of this module. Quark already
does both, and one config line gets you its buttons — see [below](#sorting-and-searching--quarks-job).

## Why it exists

### The penalty is hardcoded

Overpacked's movement penalty lives in `Events.onPlayerTick` with the thresholds and factors as
literals, and the 2.0.1 jar ships no config class at all — there is nothing to turn it down with.
`SlowdownFeature` recalculates the identical number and re-applies it:

```java
for (ItemStack stack : items) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data != null && data.copyTag().contains("Count")) {
        int count = data.copyTag().getInt("Count");
        if (count >= 27) {
            slowdown += (1.0 - slowdown) * (count < 54 ? 0.1 : (count < 81 ? 0.2 : 0.3));
        }
    }
}
slowdown *= multiplier;
```

Everything above the last line is Overpacked's own arithmetic, instruction for instruction. Only the
multiplier is ours.

### Overpacked's GUI is bound to an entity

There is no worn-item open path to call, because the screen resolves its backpack from an **entity
id** on the client. Decompiled from `GiantBackpackMenu(int, Inventory, FriendlyByteBuf)` in
overpacked-2.0.1:

```java
this(id, inv, (GiantBackpack) inv.player.level().getEntity(buf.readInt()), buf.readByte());
```

A worn backpack is an item in a Curios slot and has no entity, so opening it means giving it one for
as long as the screen is up — which is what this module does, and where most of its complexity sits.

## In detail

### A — the slowdown override

`Count` is not the number of items. Overpacked's `get_stack()` writes it from `get_items_count()`,
which sums each item type across all three compartments and rounds **up to whole stacks**
(`Mth.ceil(total / item.getDefaultMaxStackSize())`) before adding them together. One dirt block costs
as much as 64; 17 ender pearls cost two, because they stack to 16. The tag is only written at all when
that number is above zero.

| `Count` (stacks) | Penalty for that backpack |
|---|---|
| 0–26 | none |
| 27–53 | 10 % |
| 54–80 | 20 % |
| 81+ | 30 % |

Several loaded backpacks compound rather than add — `slowdown += (1 - slowdown) * f` — so two at 30 %
leave you 0.7 × 0.7 = 49 % of your speed, not 40 %. The multiplier is applied once at the end, to the
compounded value, and the result becomes an `ADD_MULTIPLIED_TOTAL` modifier of `-slowdown` on
`minecraft:generic.movement_speed` under the id `overpacked:speed`.

Counted are the 36 main inventory slots (`player.getInventory().items`) plus the offhand. Neither
Overpacked's handler nor ours looks at the item: anything carrying a `CUSTOM_DATA` `Count` of 27 or
more is treated as a loaded backpack.

**How the override wins.** Both handlers sit on `PlayerTickEvent.Pre`. Overpacked's is a plain
`@SubscribeEvent`, so it runs at `NORMAL`; ours declares `EventPriority.LOW` and therefore runs after
it, removes the modifier it just wrote and adds its own. That removal and re-add happen every server
tick for every player, even when the result is zero — the `overpacked:speed` modifier is always
present, frequently with amount 0.

The config value is read per tick, so a config reload takes effect immediately. Switching the module
off at runtime makes the handler return at once and leaves Overpacked's own modifier in place, because
the `NORMAL`-priority handler writes it again on the next tick. On a pack **without** Overpacked
nothing rewrites it: the last amount we wrote stays on the attribute until the player relogs (it is a
transient modifier) — nothing at the `0.0` default, a residual slowdown above it.

**What the override does not cover: the backpack you wear.** That penalty comes from a different
route. `GiantBackpackItem` implements Curios' `ICurioItem` and returns a `MOVEMENT_SPEED` modifier from
`getAttributeModifiers(SlotContext, ResourceLocation, ItemStack)` — same 27/54/81 thresholds, same
−0.1/−0.2/−0.3, but flat instead of compounded and registered under Curios' own per-slot id
(`CuriosApi.getSlotId` builds `curios:back0` from identifier and index), not under `overpacked:speed`.
`SlowdownFeature` only ever touches `overpacked:speed`, so `slowdown_multiplier = 0.0` leaves the worn
backpack's penalty exactly where it was. Read off the overpacked-2.0.1 bytecode; not reproduced in
play.

### B — opening a worn backpack

| Keybind | Compartment | Slots in the GUI | Default |
|---|---|---|---|
| Open Backpack (main compartment) | centre, `inv_id` 0 | 54 | `B` |
| Open Backpack (right compartment) | right, `inv_id` 1 | 27 | unbound |
| Open Backpack (left compartment) | left, `inv_id` 2 | 27 | unbound |

Those are the slots you see, not the container sizes: `GiantBackpackMenu` sets
`rows = inv_id == 0 ? 6 : 3` and adds `rows * 9` `ShulkerBoxSlot`s, while `CreateInventory` allocates
55/28/28. The centre container's last slot is the sleeping bag (`SetSleepingBag` writes
`inv[0].getContainerSize() - 1`), which the backpack screen never shows.

A keypress runs the whole chain server-side:

1. The client tick drains the mapping (`consumeClick`) and sends
   `vanillaplusadditions:open_backpack_compartment`, a single VAR_INT. The client checks nothing —
   not the module state, not whether you wear anything.
2. The server handler, enqueued onto the main thread, drops the packet silently when the module or
   `backpack_keys_enabled` is off or the compartment is outside 0–2, and answers
   *"Overpacked backpack access is unavailable."* when Overpacked or Curios is missing.
3. `CuriosBackpackAccess.findWorn` takes the **first** curio in **any** slot type that matches
   `#overpacked:giant_backpacks` — every colour variant, not only the `back` slot. None →
   *"You aren't wearing a giant backpack."*
4. A locked side pocket (Overpacked 2.x) → *"That backpack compartment isn't unlocked yet."*
5. Otherwise the helper entity is spawned and the screen opened once the client confirms it can
   see it — see *The handshake* below.

All three messages go to the action bar (`displayClientMessage(…, true)`).

**The helper entity.** A real `GiantBackpack`, parked **2.5 blocks below your feet** and re-parked
there every server tick for as long as the session lives. It is spawned with `setNoGravity(true)`,
`noPhysics = true` and `setInvulnerable(true)`, so it neither falls, nor is shoved about, nor can be
destroyed — Overpacked's `GiantBackpack.hurt` adds `amount × 10` to a damage counter that ticks back
down by 1, and once that counter passes 40 it discards the entity and, with `doEntityDrops`, drops the
whole backpack as an item. Since the helper's contents are a *copy* of the worn one, that would be a
duplication bug, and buried in the floor it is within reach of a cactus or a creative player's hit.

Under your feet rather than in front of you, because of one line in Overpacked's `tick()`:

```java
this.level().getEntities(EntityTypeTest.forClass(Player.class), this.getBoundingBox(),
        EntitySelector.pushableBy(this)).forEach(e -> e.push(this));
```

**A giant backpack pushes every player standing inside it, once per tick.** Earlier versions of this
module put the helper 1.5 blocks in front of the player and fell back to spawning it *inside* them
whenever that spot was blocked — a cabin, a corridor, anywhere tight. On a Sable airship that push was
enough to squeeze the player through the hull, because sub-level block collision runs through the
weaker transformed path (see [`freecam_sublevel_noclip`](freecam_sublevel_noclip.md)). The player's box
is 1.8 blocks tall and the backpack's 1.25, so a 2.5-block drop clears it with room to spare, and it
sits out of the crosshair — it cannot steal the pick from an item frame on the wall either.

**Re-parking it every tick is what makes this work on a moving ship.** A helper left at the world
position where it spawned simply stays there while the ship, and the player standing on it, fly on: it
drifts off visibly, and once it is more than 4 blocks away Overpacked's `stillValid` slams the screen
shut. Following the player sidesteps the whole coordinate-space question, because the player is the
reference frame either way.

Its contents are restored the way Overpacked's own place-a-backpack code does it: colour from the
item, then on 2.x `Load(tag)` plus `SetName` for a renamed backpack, on 1.x `SetSleepingBagColor` and
`LoadInventory`. Restoring 2.x by hand is not an option — `Load` is also what carries the side-pocket
unlocks, and the write-back would then persist them as locked and destroy the upgrade.

**The handshake.** Look again at Overpacked's client-side menu factory:

```java
public GiantBackpackMenu(int id, Inventory inv, FriendlyByteBuf buf) {
    this(id, inv, (GiantBackpack)inv.player.level().getEntity(buf.readInt()), buf.readByte());
}
// and then, in the constructor it delegates to:
this.container = backpack.inv[inv_id];
```

There is no null check. If the client cannot resolve that id yet, `backpack` is null, `backpack.inv`
throws, and NeoForge's advanced-open-screen handler answers a throwing menu factory by **disconnecting
the client**:

> Failed to open a screen with advanced data: java.lang.NullPointerException: Cannot read field "inv"
> because …

So the server never opens the screen on a timer. It spawns the helper, sends
`vanillaplusadditions:backpack_helper_ready` with the entity id, and waits. The client polls
`level.getEntity(id)` once per client tick and answers `vanillaplusadditions:confirm_backpack_helper`
the moment it resolves — then, and only then, does `player.openMenu` run. The client gives up after
5 s, the server cleans the helper up after 7 s and says *"The backpack could not be opened — please
try again."* Nothing was edited at that point, so there is nothing to write back.

This replaced a fixed one-tick `TickTask` delay, which is the kind of fix that works until it does not:
it held on a quiet server and broke on a Sable airship, where Sebi was thrown off the server for it
(2026-09-25). A round trip costs one ping before the GUI appears and cannot be wrong.

The client-side watcher deliberately tests `getEntity(id) != null` rather than
`instanceof GiantBackpack` — naming Overpacked's type there would link it into a class that runs on
every client, Overpacked installed or not. Entity ids are unique per connection, so non-null is
already the answer.

While the screen is up the helper is an ordinary entity in the world — other players can see it, if
they look into the ground. Overpacked's own `stillValid` requires
`canInteractWithEntity(backpack, 4.0)`; the helper follows you, so that stays true until you close it.

**The write-back.** `entity.getPickResult()` (Overpacked's `get_stack()`) is the source of truth. Its
`CUSTOM_DATA` is copied onto a copy of the original worn stack, and a **missing** `CUSTOM_DATA` clears
the worn item's rather than being skipped: `get_stack()` returns a bare stack whenever the item count
is zero, the sleeping-bag colour is −1 and neither cell is set, so keeping the stale tag duplicated
the old contents on the next open. If the Curios slot no longer holds a giant backpack — you swapped
it while the screen was open — the stack goes into your inventory, and onto the floor if that is full.
`PlayerLoggedOutEvent` sweeps any session left over for the player as a safety net.

**The side-pocket lock (Overpacked 2.x only).** Right and left are `overpacked:backpack_pocket`
upgrades, and the unlock is encoded as the mere **presence** of `RightCell` / `LeftCell` in the item's
`CUSTOM_DATA`: `Save` writes the key with a hardcoded `0` payload and only when the cell is non-zero,
`Load` answers with `SetRightCell(1)` whenever the key exists. So the check is `contains()`, never the
value — `getByte(key) == 0` is true for every backpack, locked or not, and would reject them all.
Pressing a locked compartment's key reports the message instead of opening, because the helper's
`CreateInventory` builds all three containers (55/28/28) regardless of the unlock, so opening one
would hand out the upgrade for free. On 1.x `isCompartmentLocked` returns false before it ever reads
the tag, so both side compartments always open — that is read off our own gate; no 1.x jar is in
`libs/` to confirm what Overpacked 1.x stores on the item.

### Sorting and searching — Quark's job

Quark already provides a container **sort button** (`InventoryButtonHandler`) and a **search bar** that
dims non-matching items (`ChestSearchingModule`). Both consult the same allow-list,
`QuarkGeneralConfig.isScreenAllowed(screen)`, which answers in four steps: every class named
`net.minecraft.*` is allowed, then a hardcoded `STATIC_ALLOWED_SCREENS` list, then a hardcoded
`STATIC_DENIED_SCREENS` list, and only after those the user-editable `"Allowed Screens"` config list.

Overpacked's screen is not in that hardcoded set, which is exactly why Quark's buttons do not show up
on it. Add it and you get Quark's own sort button **and** search bar, 1:1 and maintained by Quark:

```toml
# config/quark-common.toml
"Allowed Screens" = ["net.nycto_team.overpacked.screen.GiantBackpackScreen"]
```

This is purely additive — the two hardcoded lists are checked first and are untouched by it — and it
needs `"Use Screen List Blacklist" = false`, which is the default (the config list itself starts
empty). Because placed and worn backpacks use the
same `GiantBackpackScreen`, the buttons appear in both, and the close-write-back persists a sort into
the worn item. It is a client-side feature, so ship the line in the modpack's config.

This module contains **no** Quark code, no Quark type and no `ModList` check for Quark. Its own
description string still advertises a "Quark-style sort button"; that is a leftover from a feature
that was dropped in favour of the config line above.

<!-- vpa:config:start -->
## Configuration

Section `[modules.overpacked_extensions]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_overpacked_extensions-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `backpack_keys_enabled` | boolean | `true` | — | Enable the keybinds that open the compartments of a worn giant backpack (main compartment on B by default; right/left unbound). define("backpack_keys_enabled", true) — OverpackedExtensionsConfig.java:47. Checked only server-side, in the packet handler. |
| `slowdown_multiplier` | double | `0.0` | 0.0 ~ 10.0 | Multiplier applied to the Overpacked slowdown effect (0.0 = no slowdown at all, 0.5 = half, 1.0 = original, 2.0 = double). defineInRange("slowdown_multiplier", 0.0, 0.0, 10.0) — OverpackedExtensionsConfig.java:42. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Overpacked or no Curios | The bridge's handlers are never registered and a keypress answers *"Overpacked backpack access is unavailable."* The slowdown handler still ticks, finds nothing with a `Count` and writes amount 0. |
| The backpack you **wear** | Its penalty is a Curios attribute modifier under `curios:<slot><index>`, not `overpacked:speed`, so `slowdown_multiplier` does not touch it. Only carried and offhand backpacks are rescaled. |
| Any item with `CUSTOM_DATA` `Count` ≥ 27 | Counted as a loaded backpack. Overpacked's own handler has the same blind spot, so this only matters on a pack **without** Overpacked, where ours is the sole handler and `slowdown_multiplier > 0`. |
| `slowdown_multiplier` above ~3.3 | The range allows up to 10. An `ADD_MULTIPLIED_TOTAL` of −1.0 cancels the whole base movement speed, so a single fully loaded backpack at a high multiplier pins you in place. |
| Overpacked 1.x | Has its own path: with `isV2()` false the lock check is skipped, so both side compartments always open, and the helper is restored by hand (`SetSleepingBagColor` + `LoadInventory`) instead of through `Load`. Only `overpacked-2.0.1` sits in `libs/`, so that path is not exercised here. |
| Keybind clash, Overpacked 2.x | `key.overpacked.take_off_backpack` also defaults to **B**. Ours uses the four-argument `KeyMapping` constructor, i.e. the `UNIVERSAL` conflict context, so on a fresh profile both fire. Rebind one of them. |
| Keybinds with the module off | `BackpackKeybinds` and `BackpackKeysClientEvents` are plain `@EventBusSubscriber(Dist.CLIENT)` classes with no module gate: all three mappings always appear in Controls and a keypress always sends. The server-side handler is what drops it. |
| Module disabled on only one side | The payload is registered in `onInitialize`, which a disabled module never reaches, and the registrar is not `optional()`. A client and a server that disagree about this module should therefore fail NeoForge's channel negotiation. Read off the source, not reproduced. |
| Someone hits the helper entity | `hurt` adds `amount × 10` to a counter that decays by 1 per tick; once it passes 40 — or at once on a hit from a creative player — the entity is discarded, after spawning `get_stack()` as an item if `doEntityDrops` is on. That is about eight full-strength bare-hand hits: the counter loses 1 per tick and an empty hand needs 5 ticks to recharge, so every swing after the first nets roughly +5. A single swing of anything dealing more than 4 damage (stone sword upwards) does it at once. The close that follows still writes the same contents back into the worn item. The spawn-inside-the-player fallback exists to keep the helper out of the crosshair, but another player can still reach it. Read off the source, not reproduced. |
| Hard crash or restart with the GUI open | The edited items sit on the transient entity in the world rather than in the worn item. Recoverable in-world, but not where you would look for them. The helper itself no longer survives: it carries the entity tag `vpa_backpack_helper`, and `EntityJoinLevelEvent` discards any tagged helper that comes back from disk. **Before `v1.0.0-beta.87` it did survive**, with two consequences worth knowing if an old world still has one: it sits inside the player, where Overpacked's `place_predicate` silently refuses every in-hand backpack placement, and its contents are a copy of the worn backpack's — breaking it duplicates them. Observed in play on 2026-09-20. |
| Upgrade across `v1.0.0-beta.56` | This module was merged out of `overpacked_slowdown` (v0.7.0) and `overpacked_backpack_keys` (v1.0.0-beta.31). The old config sections are not migrated — `slowdown_multiplier` has to be set again in the new section. |

## Under the hood

No mixins, no items, no blocks, no commands, no recipes — one network payload and seven event handlers.

| Class | Role |
|---|---|
| `modules/overpacked_extensions/OverpackedExtensionsModule` | Registration, the payload handler and its gates |
| `modules/overpacked_extensions/SlowdownFeature` | Part A, in full — references no Overpacked type |
| `modules/overpacked_extensions/config/OverpackedExtensionsConfig` | The two config values |
| `modules/overpacked_extensions/network/OpenBackpackCompartmentPacket` | One VAR_INT, client → server |
| `modules/overpacked_extensions/client/BackpackKeybinds` | The three `KeyMapping`s |
| `modules/overpacked_extensions/client/BackpackKeysClientEvents` | Drains them per client tick |
| `modules/overpacked_extensions/compat/OverpackedCompat` | The gate. Names no Overpacked or Curios type |
| `modules/overpacked_extensions/compat/CuriosBackpackAccess` | Find and write back the worn stack (Curios types only) |
| `modules/overpacked_extensions/compat/OverpackedGuiBridge` | The only class that touches Overpacked |
| `standalone/overpacked_extensions/OverpackedExtensionsStandalone` | `@Mod("vpa_overpacked_extensions")` |

| Event | Bus | Registered | Purpose |
|---|---|---|---|
| `PlayerTickEvent.Pre` | game | always, `EventPriority.LOW` | Part A; returns immediately on the client |
| `RegisterPayloadHandlersEvent` | mod | always | `registrar("1").playToServer(…)` |
| `PlayerContainerEvent.Close` | game | only when Overpacked **and** Curios are present | Write back and discard the helper |
| `PlayerEvent.PlayerLoggedOutEvent` | game | same | Sweep leftover sessions |
| `EntityJoinLevelEvent` | game | same | Discard a tagged helper that was loaded from disk |
| `RegisterKeyMappingsEvent` | mod, client | `@EventBusSubscriber(Dist.CLIENT)`, ungated | The three mappings |
| `ClientTickEvent.Post` | game, client | same | Send the packet |

**The gate must not live with the code it guards.** `OverpackedCompat` exists only to answer "are
Overpacked and Curios installed?" and names neither mod's types. Asking `OverpackedGuiBridge` itself
would resolve — and therefore link and verify — that class, and the verifier eagerly loads the
Overpacked types appearing in its method bodies. With the gate on the bridge, a pack without Overpacked
crashed during mod construction with `NoClassDefFoundError:
net/nycto_team/overpacked/menu/GiantBackpackMenu` (beta.70). Same rule as `bluemap_signs`.

**One method, two Overpacked generations.** `OverpackedCompat.isV2()` reads
`getModInfo().getVersion().getMajorVersion() >= 2`, and both restore paths sit in the same method,
compiled against 2.x. That is safe because a method reference is resolved when it first *executes*,
not when the class is verified: the 2.x calls are never reached on 1.x, so they never resolve and
never throw `NoSuchMethodError`.

**Sessions.** `OverpackedGuiBridge.SESSIONS` is a static `HashMap` from the helper's entity id to the
player UUID, the Curios identifier and index, and a copy of the original worn stack. The close handler
looks the entity up there and returns immediately when it finds nothing, which is how a real, placed
backpack is left alone.

**Dependencies are runtime-only.** Neither `neoforge.mods.toml` nor the standalone jar's generated
metadata declares `overpacked` or `curios`; everything is `ModList` gating. Overpacked, Curios and
Bobo Lib are `compileOnly` plus `localRuntime`, and unlike the Sable, ToughAsNails, Quark and Zeta
jars — which `.gitignore` excludes — those three are committed to `libs/`, so this module builds from
a clean clone. Bobo Lib is needed because
Overpacked 2.0.1 lists it as a `required` dependency in its own `neoforge.mods.toml` and does not
bundle it — it is a separate download for anyone running Overpacked 2.x, not something this module
adds.

**Standalone jar.** `vpa_overpacked_extensions` ships no mixins and no data files; it declares only
`vpa_core`, NeoForge and Minecraft, plus the usual `incompatible` entry for the all-in-one bundle.
`vpa_core` carries the keybind and message translations in all six languages.

## See also

* [BlueMap Signs](bluemap_signs.md) — the same optional-mod gate pattern
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
