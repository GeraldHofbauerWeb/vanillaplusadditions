# Overpacked Extensions

Bundles quality-of-life features for the [Overpacked](https://modrinth.com/mod/overpacked)
giant-backpack mod into a single module. Each has its own config toggle, so you can enable them
independently even though they share the module's `enabled` flag. (Sorting + searching inside the
backpack are handled by Quark via a one-line config whitelist — see the section below — rather than
reimplemented here.)

## Requirements
- The **slowdown override** works with **Overpacked** installed (nothing to override otherwise).
- The **backpack keybinds** and **sort button** additionally need Overpacked's dependency
  **Curios** (`curios`) — and, on Overpacked 2.x, its new dependency **Bobo Lib** (`bobo_lib`, shipped
  alongside it). Without Overpacked/Curios those two features are inert (the keybinds report
  "unavailable"; no sort button is shown).
- **Both Overpacked 1.x and 2.x are supported.** The bridge picks its restore path from the installed
  major version: 2.x goes through `GiantBackpack.Load` + `SetName`, 1.x (which has neither method) is
  restored by hand. Compiled against 2.x — safe on 1.x because a method reference resolves when it
  first *executes*, not at class verification, and the 2.x branch is never reached there.

> **Side compartments must be unlocked first (Overpacked 2.x):** the right and left compartments are
> `overpacked:backpack_pocket` upgrades. Pressing their keybind on a backpack that has not bought them
> reports "That backpack compartment isn't unlocked yet." rather than opening — the helper entity
> builds all three containers regardless of the unlock, so opening would hand out the upgrade for free.
> Overpacked 1.x has no such concept; there every compartment always exists.
>
> The unlock is encoded as the **presence** of `RightCell`/`LeftCell` in the item's `CUSTOM_DATA`; the
> stored value is always `0` and means nothing. Check with `contains()`, never by reading the value.

> **Keybind clash:** Overpacked 2.x added its own `key.overpacked.take_off_backpack` bound to **B** —
> the same default as this module's "open main compartment". A fresh profile fires both. Rebind one of
> them in Controls (ours is under *Vanilla Plus Additions*).

## Configuration

| Key                     | Default | Range    | Description                                                    |
|-------------------------|---------|----------|----------------------------------------------------------------|
| `slowdown_multiplier`   | `0.0`   | 0.0–10.0 | Multiplier on Overpacked's full-backpack movement penalty       |
| `backpack_keys_enabled` | `true`  | bool     | Enable the compartment-open keybinds                            |

`slowdown_multiplier`: `0.0` removes the slowdown entirely, `0.5` = half, `1.0` = unchanged vanilla
Overpacked behaviour, `2.0` = double.

## Feature 1 — Slowdown override
Overpacked computes its slowdown from backpack item count (`27–53` items → `0.1`, `54–80` → `0.2`,
`81+` → `0.3`, stacked multiplicatively across multiple backpacks). This feature re-runs that same
calculation right after Overpacked's tick handler (event priority `LOW`) and re-applies the
`overpacked:speed` attribute modifier scaled by `slowdown_multiplier`. Server-side only. It reads the
vanilla `CUSTOM_DATA` `Count` NBT and references no Overpacked classes.

## Feature 2 — Backpack keybinds
Open the compartments of the giant backpack you **wear** (Curios `back` slot; any color variant,
matched via the `#overpacked:giant_backpacks` item tag), reusing Overpacked's **own** GUI.

| Action | Compartment | Default |
|--------|-------------|---------|
| Open Backpack (main compartment)  | center, 55 slots | `B` |
| Open Backpack (right compartment) | right, 28 slots  | unbound |
| Open Backpack (left compartment)  | left, 28 slots   | unbound |

Overpacked's GUI (`GiantBackpackMenu` / `GiantBackpackScreen`) is bound to a *placed backpack
entity* — there is no built-in "open the worn backpack" path. So on keypress the module (server-side):
1. finds the worn backpack via Curios,
2. spawns a **transient, non-colliding `GiantBackpack` entity** loaded from the item's NBT (exactly as
   `GiantBackpackItem.use()` does),
3. opens Overpacked's own `GiantBackpackMenu` on it at the requested compartment (deferred one tick so
   the entity syncs to the client first), and
4. on close (and on logout) copies the entity's `getPickResult()` NBT back into the worn item via
   Curios and discards the entity.

## Sorting & searching — use Quark, don't reimplement
Quark already provides a container **sort button** (its `InventoryButtonHandler`) and a **search bar**
that dims non-matching items (its `ChestSearching` module). Both check the same allow-list —
`QuarkGeneralConfig.isScreenAllowed(screen)` — which is a **whitelist**: vanilla screens plus a
hardcoded set of modded screens are allowed, and any other modded screen only gets the buttons if its
class name is in Quark's user-editable `"Allowed Screens"` list (in `config/quark-common.toml`, with
`"Use Screen List Blacklist" = false`).

Overpacked's backpack screen isn't in that list by default, which is exactly why Quark's buttons don't
appear on it. To get Quark's own sort button **and** search bar on the backpack — 1:1, maintained by
Quark, no reimplementation — add the screen class to the whitelist:

```toml
# config/quark-common.toml
"Allowed Screens" = ["net.nycto_team.overpacked.screen.GiantBackpackScreen"]
```

This is purely additive (the hardcoded defaults are separate `static final` lists checked first, so
nothing else is affected). Because both placed and worn backpacks use the same `GiantBackpackScreen`,
Quark's buttons show up in both; the keybinds' close-write-back persists any sort back into the worn
item. Ship this line in the modpack's config so every client gets it (it's a client-side feature).

## Implementation notes
- All Overpacked/Curios references are isolated in `modules/overpacked_extensions/compat/` and only
  reached when both mods are present (`OverpackedCompat.isAvailable()` gate). The sort screen hook
  is registered manually on the client only when Overpacked is present, so its `GiantBackpackMenu`
  reference never links otherwise.
- **The gate must live in its own Overpacked-free class** (`OverpackedCompat`), never on
  `OverpackedGuiBridge` itself. Calling a static method resolves — and therefore links and verifies —
  its declaring class, and the JVM verifier eagerly loads the Overpacked types used in that class's
  method bodies. With the gate on the bridge, a pack without Overpacked crashed mod construction with
  `NoClassDefFoundError: net/nycto_team/overpacked/menu/GiantBackpackMenu` (beta.70). Same rule as
  `bluemap_signs`: gate in the module/helper, optional-mod code in an isolated class.
- The transient entity is initialized exactly like Overpacked's own `Utils.PlaceBackpack()`:
  `SetColor` from the item, then `GiantBackpack.Load(customData)` and `SetName` from `CUSTOM_NAME`.
  Going through `Load` is not optional — since 2.x the item's `CUSTOM_DATA` also carries the
  `RightCell` / `LeftCell` side-pocket unlocks (bought with `overpacked:backpack_pocket`), and the
  write-back stores `getPickResult()`'s `CUSTOM_DATA` on the worn item. An entity that only got
  `Items` + `SleepingBagColor` would persist both pockets as locked and destroy the upgrade.
- Because the GUI is entity-bound, a helper backpack entity exists at the player's position while the
  worn-backpack GUI is open; it is non-colliding and removed on close. Other players may briefly see it.
- A hard crash while the GUI is open can leave edited items on the transient entity (recoverable
  in-world) rather than in the worn item.

## See also
- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
