# Pocket Crafting

> **TL;DR** — Right-click a crafting table sitting in your own inventory and the ordinary crafting window opens on the spot - the full 3x3 grid and the recipe book, with Escape taking you back to the inventory instead of dropping you into the world.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `pocket_crafting` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_pocket_crafting.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_pocket_crafting.jar) · also needs `vpa_core` |
| **Config section** | `[modules.pocket_crafting]` |
| **Since** | the next release |
<!-- vpa:meta:end -->

## What it does

Open your inventory, right-click a crafting table sitting in it, and the ordinary crafting window
opens: the full 3×3 grid and the recipe book. Press Escape or your inventory key and you are back in
the inventory you came from.

The table is **not** consumed and **not** placed. It just has to be somewhere in your inventory —
which, if you carry one at all, it already is.

## Why it exists

A crafting table in your backpack is oddly useless. Away from base, a 3×3 recipe means putting the
block down, using it, mining it again and picking it back up — four actions for something you are
carrying the tool for. The 2×2 grid in the inventory exists precisely because that round trip is
annoying, and then stops one row short of most recipes.

So: if you are carrying a crafting table, you can craft with it.

None of the interface is ours. The window you get is Minecraft's own crafting screen, drawn from the
game's own texture — this module ships **no** images and no screen of its own. See
[Under the hood](#under-the-hood) for how that works; it is the reason the recipe book and every
recipe viewer in the pack behave exactly as they do at a real table.

## In detail

### The trigger

A **right-click**, on a crafting table, in a slot of your own inventory, with **nothing on the
cursor**.

Each of those conditions earns its place:

* *nothing on the cursor* — with an item on the mouse a right-click means "place one here", and that
  is also the only situation in which Mouse Tweaks arms its own right-click behaviour. Keeping the
  trigger to an empty cursor keeps the two from ever meeting. Set `require_empty_carried = false` if
  you disagree.
* *a slot of your own inventory* — the inventory's own 2×2 grid and its output slot never respond.
  Main inventory, hotbar, armour and offhand all count; of those only the armour slots could never
  hold a crafting table anyway, and opening the grid from your offhand is allowed on purpose.
* **Shift is left alone.** Shift-right-click stays vanilla's quick-move.

Set `inventory_click_enabled = false` to keep the module loaded but take the click away.

### The hint on the tooltip

A crafting table in your inventory carries a yellow **Right-click to open** line at the bottom of its
tooltip, so the feature is discoverable without reading this page.

It only appears while the survival inventory is the open screen. Tooltips are drawn in plenty of other
places — a chest, the creative menu, a recipe viewer, an item in your hand — and the click does nothing
in any of them. A hint that shows up where it does not work is worse than no hint, so it is tied to the
one screen where it is true. `show_tooltip = false` removes it.

### Escape goes back to the inventory

Closing the grid yourself reopens the inventory, rather than dropping you into the world. Items left
in the grid come back to your inventory, exactly as they do when you walk away from a real table —
nothing is ever destroyed on close.

One honest limit: this covers **you** closing the window. If the *server* closes it — which is what
`require_table_while_open` does when the table leaves your inventory — that arrives as a packet and
never runs through the screen, so you end up in the world. Set
`return_to_inventory_on_close = false` if you would rather Escape behaved like a real table
throughout.

### Does the table have to stay?

By default, no: once the grid is open it stays open. `require_table_while_open = true` makes it close
as soon as no crafting table is left in your inventory or offhand.

That is off by default for a specific reason. The 3×3 grid is **not** part of your inventory, so
putting the table into the grid — as an ingredient, which is a perfectly ordinary thing to do —
would read as "the table is gone" and close the window mid-craft. Either way nothing is lost: the
grid is always emptied back into your inventory.

<!-- vpa:config:start -->
## Configuration

Section `[modules.pocket_crafting]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_pocket_crafting-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `conflicting_mods` | list<string> | `[] (empty)` | — | Mod ids that switch the trigger off when installed, checked once in common setup via ModList.isLoaded. Empty on purpose - nothing in the current pack collides. The known candidate is 'iteminteractions', the library behind Easy Shulker Boxes, which hooks the same ScreenEvent.MouseButtonPressed.Pre on container screens. A list rather than a hard-coded set so a collision can be defused without a release. |
| `inventory_click_enabled` | boolean | `true` | — | Whether a right-click on a crafting table inside the inventory opens the grid. Turning it off leaves the module and its server side loaded but removes the trigger, which is the setting to reach for when another mod claims the same click. Read live by the client handler, so it takes effect without a restart. |
| `require_empty_carried` | boolean | `true` | — | Only fire while the mouse cursor carries nothing. Also what keeps Mouse Tweaks out of the way: its right-click drag only arms when the cursor is holding something, so an empty cursor is the one branch where the two cannot collide. |
| `require_table_while_open` | boolean | `false` | — | Make stillValid() check that a crafting table is still in the main inventory or the offhand, closing the grid when it is not. Off by default because the 3x3 grid is a TransientCraftingContainer and not part of Inventory: a player who legitimately puts the table into the grid as an ingredient would read as having none and the menu would close mid-craft. Nothing is lost either way - removed() always empties the grid back into the inventory. |
| `return_to_inventory_on_close` | boolean | `true` | — | Escape or the inventory key reopens the inventory screen instead of returning to the game. Only covers closing it yourself: a close forced by the server arrives as a packet and never runs through Screen.onClose(), so that case still lands in the world. |
| `show_tooltip` | boolean | `true` | — | Add a "Right-click to open" line to a crafting table's tooltip. Only drawn while the survival inventory is the open screen - the trigger does nothing in a chest, in the creative menu or in a recipe viewer, and a hint shown where it does not work is worse than none. |
<!-- vpa:config:end -->

## Compatibility and known limits

* **Nothing in the current pack collides.** Easy Magic, Easy Anvils and Visual Workbench are all
  block-bound — they change the enchanting table, the anvil and the crafting table *in the world* —
  and never touch the inventory screen.
* **Mouse Tweaks coexists.** It listens on the same mouse event but never cancels it, and its
  right-click drag only arms while the cursor is carrying something. The empty-cursor condition puts
  this module in the one branch where Mouse Tweaks does nothing at all.
* **The one known candidate for a real conflict** is `iteminteractions`, the library behind Easy
  Shulker Boxes, which hooks the same event on container screens to open its own in-inventory views.
  It is not installed here. If it ever is, add its id to `conflicting_mods` and the trigger switches
  itself off with a line in the log. That key is a list rather than something hard-coded so a
  collision can be defused without waiting for a release.
* **Vanilla clients are not kicked and not served.** The network channel is registered as optional,
  so a player without this mod connects normally and simply has no trigger.
* **Recipe viewers work** — the window is a real crafting screen, so EMI and the recipe book transfer
  into it like they do at a table.
* Cosmetic: there is a single frame with no window open while Escape hands over from the grid to the
  inventory. It comes from the order vanilla closes a container in and cannot be avoided from here.

## Under the hood

**No menu type, no screen, no assets — and that is the whole design.** The menu is a subclass of
vanilla's `CraftingMenu`. A menu's type is assigned once in the base constructor and only read back
afterwards, so the subclass still reports itself as the vanilla crafting menu — which is what the
server puts into the open-screen packet. Every client therefore builds the stock crafting screen on
its own, because that is what it already maps that menu type to. The result: no registry entry, no
screen factory, no texture, no mixin, and one single language key (the window title).

**The trap that makes or breaks it.** `CraftingMenu`'s two-argument constructor uses a null level
access, and that access never invokes the function handed to it. Two things in the superclass then
break silently:

* the grid never recomputes its result — you can fill it and nothing appears in the output slot;
* closing the menu never empties the grid back into your inventory — the nine items are **destroyed**.

So a real level access is required. Only the level is ever used by the superclass; the position it
also carries is read exclusively by the inherited validity check, which this module overrides —
without that override the menu would look for a crafting table block at the player's feet, not find
one, and be closed by the server on the next tick.

**How the client knows the window was ours.** It cannot tell by menu type, since the menu reports the
vanilla one. The **title** is the marker: a real table opens under `container.crafting`, this grid
under a key of its own. Deliberately not a type check — Visual Workbench registers a crafting menu
type of its own for the real table, so a type comparison would pass here by coincidence and break in
a pack without it. The weak spot to know about: a mod that rewrites screen titles would defeat the
marker.

**Escape is not a race, and that is provable from the packet order.** Closing sends the close packet
and resets the client's active menu back to the inventory menu *before* the screen is cleared — and
the inventory screen that opens next binds exactly that menu. Clicks in it address the inventory
container, and the close packet went out earlier on the same ordered connection.

**The server does not trust the click.** The request carries only a slot index — without one it would
be a free "open me a crafting grid anywhere" for any client. Before opening, the server checks that
the player really has just their inventory open, that the index is in range, that the slot belongs to
the player inventory rather than the 2×2 grid or its output, and that the item in it is in fact a
crafting table.

**Not modelled on Easy Shulker Boxes, despite being the starting point.** That mod opens no second
window at all: it renders the container's contents as a *tooltip* inside the existing inventory
screen and routes clicks through vanilla's own slot-click path, which is why it never had a "get back
to the previous screen" problem to solve. A crafting grid needs real recipe resolution and an output
slot, so the trick does not carry over. What was taken from it is the event to listen on and the
reading of the hovered slot.

## See also

* [Custom Crafting Recipes](custom_crafting_recipes.md) — the recipes this grid can make
* [Configuration Guide](../guides/configuration.md)
* [All modules](../../README.md#modules)
