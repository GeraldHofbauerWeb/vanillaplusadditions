# Overpacked Extensions

Bundles quality-of-life features for the [Overpacked](https://modrinth.com/mod/overpacked)
giant-backpack mod into a single module. Each has its own config toggle, so you can enable them
independently even though they share the module's `enabled` flag. (Sorting + searching inside the
backpack are handled by Quark via a one-line config whitelist — see the section below — rather than
reimplemented here.)

## Requirements
- The **slowdown override** works with **Overpacked** installed (nothing to override otherwise).
- The **backpack keybinds** and **sort button** additionally need Overpacked's dependency
  **Curios** (`curios`). Without Overpacked/Curios those two features are inert (the keybinds report
  "unavailable"; no sort button is shown).

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
  reached when both mods are present (`OverpackedGuiBridge.isAvailable()` gate). The sort screen hook
  is registered manually on the client only when Overpacked is present, so its `GiantBackpackMenu`
  reference never links otherwise.
- Because the GUI is entity-bound, a helper backpack entity exists at the player's position while the
  worn-backpack GUI is open; it is non-colliding and removed on close. Other players may briefly see it.
- A hard crash while the GUI is open can leave edited items on the transient entity (recoverable
  in-world) rather than in the worn item.

## See also
- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
