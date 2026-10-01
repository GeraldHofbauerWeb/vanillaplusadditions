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
| **Since** | `v1.0.0-beta.101` |
<!-- vpa:meta:end -->

## What it does

Right-click a crafting table you are carrying and the ordinary crafting window opens: the full 3×3
grid and the recipe book. That works from your inventory **and** from any container you have open — a
chest, a barrel, a shulker box, a furnace — as long as the table sits in *your* half of the screen.
Press Escape or your inventory key and you are back where you came from: in the inventory, or in that
chest.

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
cursor**, in **survival or adventure** mode.

Any container screen counts: the survival inventory, a chest, a barrel, an ender chest, a furnace and
so on. The one exception is a crafting screen itself — a second grid on top of a grid is pointless.

Each of those conditions earns its place:

* *nothing on the cursor* — with an item on the mouse a right-click means "place one here", and that
  is also the only situation in which Mouse Tweaks arms its own right-click behaviour. Keeping the
  trigger to an empty cursor keeps the two from ever meeting. Set `require_empty_carried = false` if
  you disagree.
* *a slot of your own inventory* — a table lying in the chest half of the screen does not respond,
  and neither do the inventory's own 2×2 grid and its output slot. Main inventory, hotbar, armour and
  offhand all count; of those only the armour slots could never hold a crafting table anyway, and
  opening the grid from your offhand is allowed on purpose.
* *survival or adventure* — in creative, `E` opens the creative menu, whose slots behave nothing like
  a survival inventory, and creative players have the recipe book's crafting at their fingertips
  anyway.
* **Shift is left alone.** Shift-right-click stays vanilla's quick-move.

Set `inventory_click_enabled = false` to keep the module loaded but take the click away.

### The hint on the tooltip

A crafting table in your inventory carries a yellow **Right-click to open** line at the bottom of its
tooltip, so the feature is discoverable without reading this page.

It appears exactly where the click works, and only there: on a table in your own inventory, in any
container screen, in survival or adventure. Hover a table that sits in the chest, or look at one in the
creative menu or a recipe viewer, and the line is absent — a hint that shows up where it does not work
is worse than no hint. The tooltip and the click share one check, so the two cannot drift apart.
`show_tooltip = false` removes it.

### Escape goes back to where you were

Closing the grid yourself takes you back instead of dropping you into the world. Items left in the
grid come back to your inventory, exactly as they do when you walk away from a real table — nothing is
ever destroyed on close.

* **Opened from the inventory** — the inventory reopens.
* **Opened from a chest, barrel, shulker box, ender chest, dispenser, hopper, furnace or brewing
  stand** — that container reopens.
* **Opened from anything else** — a modded container, a horse, a villager — the inventory reopens.

That last rule is deliberate. A player only ever has one container open, so opening the grid closes
the chest, and going back means the server opens it a *second* time with whatever opened it the first
time. For vanilla storage that is harmless. A mod is not obliged to make that safe, though, and one
that is not ends with the player being disconnected — so going back into a container happens only
for the types listed in `return_menu_types`. Add a modded container there once you have checked it
reopens cleanly.

Because the chest really is closed and reopened, you will hear its lid shut when the grid opens and
open again when you come back.

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
| `inventory_click_enabled` | boolean | `true` | — | Whether a right-click on a crafting table in the player's own inventory opens the grid - from the survival inventory and from any container screen except a crafting screen, in survival and adventure only. Turning it off leaves the module and its server side loaded but removes the trigger, which is the setting to reach for when another mod claims the same click. Read live by the client handler, so it takes effect without a restart. |
| `require_empty_carried` | boolean | `true` | — | Only fire while the mouse cursor carries nothing. Also what keeps Mouse Tweaks out of the way: its right-click drag only arms when the cursor is holding something, so an empty cursor is the one branch where the two cannot collide. |
| `require_table_while_open` | boolean | `false` | — | Make stillValid() check that a crafting table is still in the main inventory or the offhand, closing the grid when it is not. Off by default because the 3x3 grid is a TransientCraftingContainer and not part of Inventory: a player who legitimately puts the table into the grid as an ingredient would read as having none and the menu would close mid-craft. Nothing is lost either way - removed() always empties the grid back into the inventory. |
| `return_menu_types` | list<string> | `generic_9x1..9x6, generic_3x3, shulker_box, hopper, furnace, blast_furnace, smoker, brewing_stand (all minecraft:)` | — | Menu types the grid may send the player back into after Escape; anything else returns to the survival inventory. An allow-list on purpose: going back means replaying the recorded MenuProvider a second time, and a provider is not obliged to survive that - one that built a temporary entity to back its screen (Overpacked's backpack bridge) would point at a discarded entity, and its client factory dereferences that without a null check, which NeoForge answers by disconnecting the client. The defaults are vanilla containers backed by a block entity, an entity or the ender chest inventory, none of which reads extra data on the client. |
| `return_to_inventory_on_close` | boolean | `true` | — | Escape or the inventory key goes back to where the grid was opened from: the survival inventory, or the container that was open if its menu type is listed in return_menu_types (anything else falls back to the inventory). Off means Escape behaves like a real table and lands in the world. Only covers closing it yourself: a close forced by the server arrives as a packet and never runs through Screen.onClose(). |
| `show_tooltip` | boolean | `true` | — | Add a "Right-click to open" line to a crafting table's tooltip. Drawn exactly where the click works - a table in the player's own inventory, in any eligible container screen, in survival or adventure - because the tooltip and the trigger share one eligibility check. A table in the chest half, the creative menu or a recipe viewer gets no line. |
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
screen factory, no texture, and two language keys (the window title, in two variants — see below).

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
under a key of its own — two, in fact. Which of the two the server picks also tells the client where
Escape leads: back to the inventory, or back into a container. Both read "Crafting" on screen. Putting
that decision into the title rather than into a separate packet means it travels inside the very
packet that opens the window, so the client can never see one without the other. Deliberately not a type check — Visual Workbench registers a crafting menu
type of its own for the real table, so a type comparison would pass here by coincidence and break in
a pack without it. The weak spot to know about: a mod that rewrites screen titles would defeat the
marker.

**Escape is not a race, and that is provable from the packet order.** Closing sends the close packet
and resets the client's active menu back to the inventory menu *before* the screen is cleared — and
the inventory screen that opens next binds exactly that menu. Clicks in it address the inventory
container, and the close packet went out earlier on the same ordered connection.

**The server does not trust the click.** The request carries only a slot index — without one it would
be a free "open me a crafting grid anywhere" for any client. Before opening, the server checks that the
player is not in creative or spectator, that the index is in range of the menu they actually have
open, that the slot belongs to the player's own inventory rather than the chest half or the 2×2 grid,
and that the item in it is in fact a crafting table.

**Going back into a chest needs one mixin.** An open menu does not know what built it, so a mixin on
`ServerPlayer` records, for every menu a player opens, the provider and NeoForge's extra-data writer
it was opened with. One hook covers everything: NeoForge funnels vanilla's `openMenu(provider)` and its
own `openMenu(provider, pos)` into the same method. When the grid is opened from a container, the
server checks that the recorded menu is still the one open and that its type is on the allow-list, and
keeps it. After Escape the client asks to go back, and the server reopens it.

That return request is guarded, because "reopen a container" is exactly what a modified client would
like to abuse. It carries nothing, so it can only ever reach the container the server itself recorded.
It is honoured once, within two seconds of the grid closing, and only from the plain inventory. And the
reopened menu is checked for reach immediately — on the server thread, before any click from the client
can be processed — so a player who walked away from the chest does not get even one tick of access to
it.

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
