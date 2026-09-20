# Item Vault Viewer

> **TL;DR** — Hold Ctrl and right-click a Create Item Vault while wearing Engineering Goggles to see
> everything stored inside it in a searchable, sortable read-only grid — including vaults mounted on
> moving contraptions, where Create itself opens nothing.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `item_vault_viewer` |
| **Side** | Client + Server |
| **Requires** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Works with** | — |
| **Download** | [`vpa_item_vault_viewer.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_item_vault_viewer.jar) · also needs `vpa_core` |
| **Config section** | `[modules.item_vault_viewer]` |
| **Since** | `v1.0.0-beta.13` |
<!-- vpa:meta:end -->

## What it does

Wear Engineering Goggles, hold the modifier key (Left Ctrl by default) and right-click an Item
Vault. A panel opens showing the aggregated contents of the whole multiblock: one cell per item
type, with the total count across every slot of every block. Type in the search field at the bottom
to filter, use the button beside it to flip the sort order, scroll with the wheel when there are
more than six rows. A slim bar under the title says how full the vault is.

The same click works on a vault **mounted on an assembled contraption** — a cart contraption, a
train carriage, an elevator — and works while the thing is moving.

Nothing can be taken out. Without the modifier held, the right-click falls through to whatever it
would normally do, so placing a block against a vault still works.

## Why it exists

Create's Item Vault has no screen. `ItemVaultBlock` overrides no use method at all, and
`ItemVaultBlockEntity.addBehaviours` is empty — not even a goggles overlay:

```java
public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
}
```

The block's one readout is its comparator signal (`hasAnalogOutputSignal` → `true`), which gives you
a single number between 0 and 15 for a store of up to 1620 slots.

On a contraption Create is explicit about it. Every mounted storage inherits
`MountedItemStorage.handleInteraction`, which builds a menu over the mounted handler, plays the
opening sound and opens it — that is why a chest on a train opens when you click it. The vault opts
out in one line:

```java
// ItemVaultMountedStorage
public boolean handleInteraction(ServerPlayer player, Contraption contraption, StructureBlockInfo info) {
    return false;
}
```

That is a defensible decision for a container you could otherwise empty at speed. It is a bad one
for the question players actually have in front of a nine-block vault on a train: *is there anything
in it?*

## In detail

### Opening it

Two entirely separate click paths, because a contraption block never fires
`PlayerInteractEvent.RightClickBlock` — Create routes contraption clicks through
`InputEvent.InteractionKeyMappingTriggered` instead.

| | Placed vault | Contraption vault |
|---|---|---|
| Client event | `PlayerInteractEvent.RightClickBlock` | `InputEvent.InteractionKeyMappingTriggered`, `EventPriority.HIGH` |
| Finding the block | the event's own `BlockPos` | Create's own ray trace, mirrored (see below) |
| Packet | `vanillaplusadditions:open_item_vault_viewer` (`BlockPos`) | `vanillaplusadditions:open_contraption_vault_viewer` (entity id + contraption-local `BlockPos`) |
| Cancels | the interaction, result `SUCCESS` | the input event, `setSwingHand(false)` |

Both client handlers require: Create loaded, main hand, not sneaking, the modifier physically held,
Engineering Goggles worn, the module enabled, and the hit block a vault. The contraption handler
additionally skips spectators.

The server trusts none of it and re-checks what it can see — module enabled, Create loaded, goggles
worn (Create's own `GogglesItem.isWearingGoggles`, so any slot Create counts as "worn" counts here),
not sneaking, the target really a vault, and a 64-block range. The held modifier, the hand and the
spectator state are the three things it cannot see, so those stay client-side.

**The contraption ray trace** is Create's, step for step: `ContraptionHandlerClient.getRayInputs`
for origin and target (the target is already shortened to the nearest world block, so you cannot
reach through a wall), `ContraptionHandler.loadedContraptions` for the candidates rather than an
entity search — contraption entities are `isPickable() == false` and a plain entity lookup drops
them — a bounding-box prefilter inflated by 16, then `ContraptionHandlerClient.rayTraceContraption`
per candidate and the nearest vault hit wins. Anything that is not a vault falls through to Create's
own handler untouched, which is the point of running at `HIGH` and cancelling only on a hit.

### The modifier key

`key.vanillaplusadditions.item_vault_viewer.open_modifier`, default `GLFW_KEY_LEFT_CONTROL`,
rebindable in the Controls screen like any other mapping — to a mouse button as well.

It is read as raw window state, not as a key mapping:

```java
InputConstants.Key key = OPEN_MODIFIER.getKey();
long window = Minecraft.getInstance().getWindow().getWindow();
if (key.getType() == InputConstants.Type.KEYSYM) {
    int value = key.getValue();
    return value != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, value);
}
if (key.getType() == InputConstants.Type.MOUSE) {
    return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
}
```

`KeyMapping.isDown()` answers "was this pressed since the last poll", which is the wrong question
for "is it being held while I click". Reading GLFW directly is also what makes a mouse-button
binding work at all.

### Reading a placed vault

The clicked block entity is asked for its controller (`getControllerBE`), and the controller for its
item capability. Create builds that capability on the controller as a
`VersionedInventoryWrapper` around a `SameSizeCombinedInvWrapper` over every member block's handler,
so a placed multiblock already adds up in one handler and the viewer does no walking at all.

Both hops go through reflection — `getControllerBE()` by name, `itemCapability` as a declared field,
because it is `protected`. A failure to find the controller means nothing opens at all. A failure on
the capability falls back to the public `getInventoryOfBlock()`, which returns **one block's**
handler, so a hypothetical rename of `itemCapability` would quietly reduce a 3×3×9 vault to 20 slots
rather than break outright. Both swallow the `ReflectiveOperationException` without a log line.

### Reading a contraption vault

This is the fragile half, and it needs its own code because a multiblock vault has no shared
inventory: every block keeps its own handler (`vaultCapacity`, Create's server config, 20 slots by
default), the controller merely wraps them at runtime, and Create's assembly mounts **each block
separately** as its own `MountedItemStorage`. Reading one storage showed one block's 20 slots, often
an empty one — which is exactly what shipped in `v1.0.0-beta.58` and was fixed in `v1.0.0-beta.59`.

`ContraptionVaultAccess` rebuilds the controller's combined view by hand:

| Step | What it does | If it fails |
|---|---|---|
| 1 | Reads the `Controller` stamp Create writes into every captured member's block-entity NBT | step 2 |
| 2 | Reflects the protected field `Contraption.capturedMultiblocks` and searches it for the clicked position | step 3, and warns once |
| 3 | Treats the clicked block as its own controller | — |
| 4 | Collects every vault block in the contraption whose owner is that controller | falls back to the controller alone |
| 5 | Sorts the members in Create's own member order, then maps each to its mounted storage | — |
| 6 | If that list is empty: the nearest mounted storage within Chebyshev radius 3 | nothing opens |

The member order mirrors `ItemVaultBlockEntity.initCapability`, which walks the vault along its
horizontal axis first: `Z` → sort by z, x, y; `Y` → y, x, z; otherwise x, y, z. That keeps the
slot order identical to what the placed vault's combined handler would produce.

Step 6 is a last resort and it is not exact. It searches *all* mounted item storages, not just
vaults, so on a densely packed contraption it can hand back a neighbouring container's contents
instead of the vault you clicked. It only ever runs when both controller lookups failed.

### What the grid shows

Slots are merged across every handler that was read:

```java
for (ItemStack existing : stacks) {
    if (ItemStack.isSameItemSameComponents(existing, stack)) {
        existing.grow(stack.getCount());
        merged = true;
        break;
    }
}
```

`grow` is not clamped to the item's maximum stack size, so one cell can legitimately read 3200
cobblestone. The count is drawn by hand rather than by `renderItemDecorations` (which is called with
an empty count string) so that it can shrink to fit: full size below 100, 0.85× from 100, 0.72× from
1000.

| | |
|---|---|
| Grid | 9 columns; `ceil(distinct types / 9)` rows, of which at most 6 are visible. The window height is fixed when the menu opens, so filtering down to three items leaves a six-row panel |
| Scrolling | one row per wheel notch; the scroll bar only appears when there is something to scroll |
| Search | matches the lowercased hover name **or** the registry id as a substring; 64 characters max; resets the scroll on every keystroke |
| Sort | count, then hover name case-insensitively; the button toggles `Desc` (the default) and `Asc` |
| Empty | "No matches" with a search term in the box, "Empty" without one |

Descending reverses the whole comparator, name included, so items with equal counts read Z→A in
`Desc` and A→Z in `Asc`.

### The fill bar

The bar and the percentage in the header are computed server-side over every slot of every block, so
they do not move when you filter or scroll. They count **stack units**, not slots:

```java
int limit = Math.max(1, Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize()));
usedStackUnits += Math.min(1.0f, stack.getCount() / (float) limit);
```

The vault's handler reports the default slot limit of 64, so `limit` is in practice the item's own
maximum stack size. A slot with 32 cobblestone therefore counts as half full and a slot with a single
shulker box as completely full — closer to "how much still fits in there" than a slot count is. The
slot count is reported separately and lives in the bar's hover tooltip, together with the total
number of items.

| Fill | Colour |
|---|---|
| below 70 % | green `#63B25A` |
| from 70 % | yellow `#D6B24A` |
| from 90 % | red `#D65C4A` |

The header percentage uses the same colour with the alpha byte stripped. Rounding is deliberate at
both ends: 0 % is printed only for a genuinely empty vault and 100 % only for a genuinely full one,
otherwise the value rounds to 1 % or 99 %. A fill above zero always keeps at least one pixel of bar
visible for the same reason.

### Read-only, and a snapshot

`ItemVaultViewerMenu` registers **zero** `Slot`s — not the vault's, not even the player's own
inventory — and `quickMoveStack` returns `ItemStack.EMPTY`. There is nothing to pick up, drop or
shift-click; the cells are rectangles drawn by the screen, not slots.

The contents are read once, server-side, at the moment the menu opens, and written into the
open-menu payload. There is no tick handler and no refresh path: a vault being filled by a machine
while you watch will look unchanged until you close the panel and open it again.

What *is* re-checked every tick is whether the panel may stay open at all, through `stillValid`:

| Anchor | Closes when |
|---|---|
| `BlockAnchor` (controller position) | the block is no longer a vault, or you are more than 64 blocks away |
| `ContraptionAnchor` (entity id + local position) | the contraption entity is gone or dead, the local position is no longer a vault, or its current world position is more than 64 blocks away |

So the panel closes by itself when you walk off, when someone breaks the vault, and when the
contraption disassembles under it.

<!-- vpa:config:start -->
## Configuration

Section `[modules.item_vault_viewer]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_item_vault_viewer-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Create not installed | `shouldInitialize()` is false, so the menu type and the two payloads are never registered, and both client handlers return at their first line. The keybind still appears in the Controls screen — it is registered unconditionally, and harmlessly, since nothing reads it. |
| The view does not update | It is a snapshot from the moment of opening. Nothing refreshes it. |
| All GUI text is hardcoded English | The only lang key this module owns is the keybind name. The window title, "Search", "Desc"/"Asc", "No matches", "Empty", "Vault N% full", "N / M slots used" and "N items" are literals and cannot be translated. |
| `debug_logging` does nothing here | The module never consults it. Both the server-side traces and the client-side `[IVV/contraption]` diagnostics are plain SLF4J `debug` calls on their class loggers, so only a log4j level change turns them on. |
| Mixed versions | The open-menu payload changed in `v1.0.0-beta.69` (anchor → fill numbers → stacks). Client and server must run the same version. |
| Create renames `itemCapability` | Placed vaults quietly degrade to the controller block's own 20 slots via the `getInventoryOfBlock()` fallback. A rename of `getControllerBE` stops the viewer opening at all. Neither logs anything. |
| Both contraption controller lookups fail | The nearest-storage fallback searches every mounted item storage within Chebyshev radius 3, vault or not, and can show a neighbouring container. The reflection failure itself is warned about once per launch. |
| Spectators | The contraption path skips them explicitly; the placed-block client handler does not, and neither does the server. The view is read-only either way. |
| Standalone jar | `vpa_item_vault_viewer` declares no dependency on Create, not even an optional one — it simply does nothing without it. The bundle does declare `create` as optional, `[6.0,)`, ordering AFTER, side BOTH. |
| Building it | Create is an `implementation` dependency on `libs/create-1.21.1-6.0.9.jar`, which is committed to the repository — unlike the four gitignored jars the build otherwise needs. |

## Under the hood

Registered content is one menu type, `vanillaplusadditions:item_vault_viewer`, and two
client-to-server payloads on registrar `"1"`. No items, no blocks, no entities, no mixins, no
commands, no recipes.

| Event | Bus | Purpose |
|---|---|---|
| `RegisterPayloadHandlersEvent` | mod | Both C2S packets, added inside `onInitialize` |
| `RegisterMenuScreensEvent` | mod, client | Binds `ItemVaultViewerScreen` to the menu type |
| `RegisterKeyMappingsEvent` | mod, client | The hold modifier |
| `PlayerInteractEvent.RightClickBlock` | game, client | Placed vaults |
| `InputEvent.InteractionKeyMappingTriggered` | game, client | Contraption vaults, `EventPriority.HIGH` |

**The module gate is Create, not the config.** `shouldInitialize()` returns `isCreateLoaded()`, so
with Create present the registration happens regardless of `enabled` and every packet handler and
every client handler re-checks `isModuleEnabled()` per use. The upshot is that the module can be
switched on and off at runtime; without Create it never registers anything in the first place.

| Class | Role |
|---|---|
| `modules/item_vault_viewer/ItemVaultViewerModule` | Menu type, both packet handlers, reading and merging the contents, the placed-vault reflection |
| `modules/item_vault_viewer/menu/ItemVaultViewerMenu` | The slot-less menu, the two anchors, the wire format |
| `modules/item_vault_viewer/client/ItemVaultViewerScreen` | The whole GUI: grid, counts, scroll bar, search, sort, fill bar |
| `modules/item_vault_viewer/client/ItemVaultViewerClientEvents` | Both click paths and the ray trace |
| `modules/item_vault_viewer/client/ItemVaultViewerKeybinds` | The hold modifier and its raw GLFW read |
| `modules/item_vault_viewer/client/ItemVaultViewerClientSetup` | Menu type → screen |
| `modules/item_vault_viewer/compat/ContraptionVaultAccess` | Resolving a contraption vault's mounted storages |
| `modules/item_vault_viewer/network/OpenItemVaultViewerPacket`, `OpenContraptionVaultViewerPacket` | The two payloads |
| `modules/item_vault_viewer/config/ItemVaultViewerConfig` | Ten lines; no keys of its own |
| `standalone/item_vault_viewer/ItemVaultViewerStandalone` | `@Mod("vpa_item_vault_viewer")` |

**Coupling to Create internals.** Three of them are not API: the protected field `itemCapability`,
the protected field `Contraption.capturedMultiblocks`, and the member order that mirrors the private
`ItemVaultBlockEntity.initCapability`. All three exist in the vendored `create-1.21.1-6.0.9.jar`
(checked with `javap -p`), all three are reflection or behavioural copies rather than compile
dependencies, and all three degrade quietly rather than crash. The ray trace is a fourth, softer
one: it calls public Create methods, but it depends on those methods keeping their current meaning.

**A note on the optional-mod gate.** The project rule is that the "is the mod installed?" gate must
live in a class that names no types from that mod, because the bytecode verifier can resolve them
before the check ever runs — an `isLoaded()` call inside such a class comes too late. Here
`isCreateLoaded()` sits in `ItemVaultViewerModule` itself, whose imports and method bodies do
reference `ItemVaultBlock`, `GogglesItem` and `AbstractContraptionEntity`, and the module is
constructed unconditionally in `VanillaPlusAdditions`. The `CHANGELOG` for the `beta.25`/`beta.30`
isolation work records a verified boot of the bundle on a bare NeoForge server with none of the
library mods present, so in practice it holds — but the arrangement contradicts the written rule and
is worth knowing before anyone refactors it.

## See also

* [Arm Target Overlay](arm_target_overlay.md) — the other Create-and-goggles peek, on the same
  hold-to-peek pattern
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
