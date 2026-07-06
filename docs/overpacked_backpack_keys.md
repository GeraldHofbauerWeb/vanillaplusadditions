# Overpacked Backpack Keybinds

Adds keybinds that open the compartments of the **giant backpack** you wear (from the
[Overpacked](https://modrinth.com/mod/overpacked) mod), reusing Overpacked's **own** backpack GUI.

## Requirements
- **Overpacked** (`overpacked`) and its dependency **Curios** (`curios`) must be installed.
  Without them the module loads but does nothing (the keybinds simply report "unavailable").

## Keybinds
Rebindable under *Options → Controls → Vanilla+ Additions*:

| Action | Compartment | Default |
|--------|-------------|---------|
| Open Backpack (main compartment) | center, 55 slots | `K` |
| Open Backpack (right compartment) | right, 28 slots | unbound |
| Open Backpack (left compartment) | left, 28 slots | unbound |

The backpack must be **worn** (Curios `back` slot). Any color variant works
(matched via the `#overpacked:giant_backpacks` item tag).

## How it works
Overpacked's backpack inventory lives in the item's `CUSTOM_DATA` NBT
(`Items` → `0`/`1`/`2`, plus `Count`, `SleepingBagColor`), but its GUI
(`GiantBackpackMenu` / `GiantBackpackScreen`) is bound to a *placed backpack entity* — there is no
built-in "open the worn backpack" path.

So, on keypress, the module (server-side):
1. finds the worn backpack via Curios,
2. spawns a **transient, non-colliding `GiantBackpack` entity** loaded from the item's NBT
   (exactly as `GiantBackpackItem.use()` does),
3. opens Overpacked's own `GiantBackpackMenu` on it at the requested compartment
   (deferred one tick so the entity syncs to the client first), and
4. on close, copies the entity's `getPickResult()` NBT back into the worn item (via Curios) and
   discards the entity.

All Overpacked/Curios references are isolated in
`modules/overpacked_backpack_keys/compat/` and only reached when both mods are present.

## Notes / caveats
- Because the GUI is entity-bound, a helper backpack entity exists at the player's position while
  the GUI is open; it is non-colliding and removed on close. Other players may briefly see it.
- Contents are written back on GUI close and on logout. A hard crash while the GUI is open can leave
  the edited items on the transient entity (recoverable in-world) rather than in the worn item.
- The `overpacked_slowdown` module reads the same `Count` NBT; write-back keeps it consistent.
