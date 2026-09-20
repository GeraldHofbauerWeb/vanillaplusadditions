# Arm Target Overlay

> **TL;DR** — Hold Left Ctrl while wearing goggles and looking at a Create Mechanical Arm, and every
> block it takes from is outlined in orange, every block it deposits into in teal — through walls.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `arm_target_overlay` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub>, [Create Aeronautics](https://modrinth.com/mod/create-aeronautics), [Curios API](https://modrinth.com/mod/curios) <sub>tested 9.5.1</sub> |
| **Download** | [`vpa_arm_target_overlay.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_arm_target_overlay.jar) · also needs `vpa_core` |
| **Config section** | `[modules.arm_target_overlay]` |
| **Since** | `v0.17.0` |
<!-- vpa:meta:end -->

## What it does

A Mechanical Arm's targets are a list inside the block. Once the arm is built and the wrench is back
in the chest, nothing in the world tells you which belt it takes from and which depot it feeds.

Put on Engineering Goggles, look at a placed arm, hold **Left Ctrl**, and the list becomes visible:
one glowing box around every block the arm is bound to, orange for the ones it takes from and teal
for the ones it deposits into. The boxes are drawn without a depth test, so a target behind the
machine's casing or on the far side of a wall shows up just the same.

Only the arm under your crosshair is drawn, and only while the key is held. Let go and the world
looks normal again. There is no radius, no toggle and no "show every arm" mode.

## In detail

### What has to be true

The overlay is drawn once per frame, and every one of these has to hold — in this order:

| # | Condition | If it fails |
|---|---|---|
| 1 | Create is loaded | The handler returns on its first statement; nothing else is ever touched |
| 2 | Render stage is `AFTER_TRANSLUCENT_BLOCKS` | Skipped — the other stages of the same frame do nothing |
| 3 | The module is registered and `enabled` | Nothing is drawn |
| 4 | A level and a player exist | Nothing is drawn |
| 5 | You are wearing goggles | Nothing is drawn |
| 6 | The peek key is physically held | Nothing is drawn |
| 7 | Your crosshair is on a block (`minecraft.hitResult` is a `BlockHitResult`) | Nothing is drawn |
| 8 | That block's block entity is an `ArmBlockEntity` | Nothing is drawn |
| 9 | The arm has at least one input or output | Nothing is drawn |

Step 7 is the ordinary crosshair pick, so the arm has to be within your normal reach — the same
distance at which you could break it.

### The peek key

| | |
|---|---|
| Mapping | `key.vanillaplusadditions.arm_target_overlay.peek_modifier` — *Show Mechanical Arm Targets (hold + look at arm)* |
| Default | Left Ctrl (`GLFW_KEY_LEFT_CONTROL`) |
| Category | *Vanilla+ Additions* |
| Type | Hold, not toggle |

It is rebindable to any key **or mouse button** in the Controls screen. The check reads the raw
window state (`InputConstants.isKeyDown`, or `glfwGetMouseButton` for a mouse binding) rather than
`KeyMapping.isDown()`, because what is wanted is "held right now", not a press event. A mapping set
to *None* returns false, which means the overlay can never appear.

**The Controls screen will flag a conflict on a fresh install.** Five Vanilla+ mappings share Left
Ctrl as their default — this one, `item_vault_viewer`, `cat_guardian`, `axolotl_guardian` and
`wolf_mount`. They are separate `KeyMapping` objects and every one of them reads the raw window
state independently, so all five work while Ctrl is held and the orange conflict marker is
cosmetic. Rebind if the marker bothers you; nothing breaks either way.

### Which goggles count

Two separate checks, and the difference matters:

| Item | Helmet slot | Curios head slot |
|---|---|---|
| Create's **Engineering Goggles** | yes | yes, if Curios is installed |
| Anything in `vanillaplusadditions:arm_goggles` — by default Create: Aeronautics' **aviator's goggles** | yes | **no** |

The first row goes through `GogglesItem.isWearingGoggles(player)`, and Create itself registers the
Curios predicate behind it; this module writes no Curios code at all. The second row is a plain
`player.getItemBySlot(EquipmentSlot.HEAD).is(ARM_GOGGLES_TAG)` — hard-wired to the vanilla head
slot. Aviator's goggles in a Curios slot therefore do nothing.

The tag is the extension point. Both of its entries are `"required": false`, so neither mod has to
be present, and a datapack can add any helmet you like:

```json
{
  "values": [
    { "id": "create:goggles", "required": false },
    { "id": "aeronautics:aviators_goggles", "required": false }
  ]
}
```

### What is drawn

One line box per interaction point, over `new AABB(point.getPos())` — exactly one full block, never
a partial hitbox. Create's own two modes map straight onto the two colours:

| Create mode | Called here | Default colour | |
|---|---|---|---|
| `TAKE` | input | `1.0 / 0.6 / 0.1` at alpha `0.8` | orange |
| `DEPOSIT` | output | `0.1 / 0.9 / 0.7` at alpha `0.8` | teal |

All eight channels are config values in the 0.0–1.0 range and are read fresh on every frame, so a
changed value takes effect the moment the config is reloaded — no restart. The **line width is
not** configurable: the render type is built with `OptionalDouble.empty()`, vanilla's default.

Setting an alpha to 0 makes that colour invisible but saves nothing: the arm is still read and the
box is still submitted.

<!-- vpa:config:start -->
## Configuration

Section `[modules.arm_target_overlay]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_arm_target_overlay-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `input_color.alpha` | double | `0.8` | 0.0 ~ 1.0 | Alpha component for input (TAKE) position outlines. |
| `input_color.blue` | double | `0.1` | 0.0 ~ 1.0 | Blue component for input (TAKE) position outlines. |
| `input_color.green` | double | `0.6` | 0.0 ~ 1.0 | Green component for input (TAKE) position outlines. |
| `input_color.red` | double | `1.0` | 0.0 ~ 1.0 | Red component for input (TAKE) position outlines. |
| `output_color.alpha` | double | `0.8` | 0.0 ~ 1.0 | Alpha component for output (DEPOSIT) position outlines. |
| `output_color.blue` | double | `0.7` | 0.0 ~ 1.0 | Blue component for output (DEPOSIT) position outlines. |
| `output_color.green` | double | `0.9` | 0.0 ~ 1.0 | Green component for output (DEPOSIT) position outlines. |
| `output_color.red` | double | `0.1` | 0.0 ~ 1.0 | Red component for output (DEPOSIT) position outlines. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Create installed | The module still loads, still initialises, still has its config section and its keybind — it just never draws. `shouldInitialize()` is deliberately **not** overridden; the render handler returns on the `CREATE_LOADED` check instead, before any Create class is resolved. |
| A future Create renaming `inputs` / `outputs` | Fails soft and silently. `warnOnce()` puts a single line in the log and both getters return an empty list from then on — the overlay simply stops appearing, with nothing said in game. |
| Aviator's goggles in a Curios slot | Not detected. The tag check only ever looks at `EquipmentSlot.HEAD`; only Create's own goggles reach the Curios path. |
| Module disabled in config | The keybind is registered anyway — *Show Mechanical Arm Targets* stays in the Controls screen even with the module off or Create absent, because `ArmTargetOverlayKeybinds` subscribes on the mod bus with no gate at all. |
| Dedicated server | The eight colour keys are registered into the **common** spec although the feature is purely client-side, so a server's `vanillaplusadditions-common.toml` carries them and they do nothing there. |
| Standalone jar and Create | `vpa_arm_target_overlay`'s generated `mods.toml` declares only `vpa_core`, NeoForge and Minecraft, with no Create dependency and therefore no `ordering="AFTER" create`. Only the bundle declares Create as optional with that ordering. |
| Two arms side by side | Only the one under the crosshair is drawn. There is no way to see several at once. |
| Shared with `debug_overlay` | The `arm_goggles` tag is read by [Debug Overlay](debug_overlay.md) as well, through its own copy of the goggles logic. The tag file ships in *this* module's standalone jar only, so a `vpa_debug_overlay` jar installed without `vpa_arm_target_overlay` has no tag content to match — Create's own goggles still work there, tag-based ones do not. |

## Under the hood

No mixins, no server code, no network payloads, no registries, no commands. `ArmTargetOverlayModule.onInitialize()`
logs one line; everything else lives in two `@EventBusSubscriber(Dist.CLIENT)` classes with one
`@SubscribeEvent` method each.

| Event | Bus | Purpose |
|---|---|---|
| `RenderLevelStageEvent` | game, client | The whole overlay — gate chain, reflection read, two loops of `renderLineBox` |
| `RegisterKeyMappingsEvent` | mod, client | Registers `PEEK_MODIFIER`; ungated |

### The render type

A private `RenderType` named `arm_target_overlay_xray_lines`, `POSITION_COLOR_NORMAL` /
`Mode.LINES`, buffer size 1536:

| State | Value | Why |
|---|---|---|
| Depth test | `NO_DEPTH_TEST` | This is what makes targets visible through solid blocks |
| Cull | `NO_CULL` | Box edges stay complete from every angle |
| Transparency | `TRANSLUCENT_TRANSPARENCY` | The two `alpha` keys do something |
| Write mask | `COLOR_WRITE` | The lines write no depth and cannot disturb what is drawn after them |
| Layering | `VIEW_OFFSET_Z_LAYERING` | Nudged towards the viewer so an outline does not fight the block face it sits on |
| Output | `ITEM_ENTITY_TARGET` | The render target vanilla uses for item entities |
| Line width | `OptionalDouble.empty()` | Vanilla default, not configurable |

The pose stack is translated by the negated camera position before the boxes are drawn and the
batch is ended explicitly with `endBatch(XRAY_LINES)`, so the lines are flushed in the same frame.
This is the same construction [Block Glow](block_glow.md) uses for its highlights.

### Reflection, not a mixin

`ArmBlockEntity.inputs` and `.outputs` are package-private in Create. A Mixin `@Accessor` was the
first attempt and it does not compile: `ArmBlockEntity`'s supertype `SmartBlockEntity` implements
`net.createmod.ponder.api.VirtualBlockEntity`, Ponder is not among the jars in `libs/`, and javac
needs the whole supertype hierarchy to validate the cast.

```
Klassendatei für net.createmod.ponder.api.VirtualBlockEntity nicht gefunden
```

`ArmBlockEntityReflection` resolves both fields once inside a `synchronized` block, caches them and
calls `setAccessible(true)`. Any `ReflectiveOperationException` — at resolve time or at read time —
goes through `warnOnce`, which logs a single `Vpa.LOGGER.warn` line and sets a flag; after that the
getters return `Collections.emptyList()` for the rest of the session. The reasoning is written up in
full in the [case study](../internal/arm_target_overlay_case_study.md).

### Files

| File | Role |
|---|---|
| `modules/arm_target_overlay/ArmTargetOverlayModule.java` | Module class; `onInitialize` only logs |
| `modules/arm_target_overlay/client/ArmTargetOverlayClientEvents.java` | Gate chain, goggles check, render type, drawing |
| `modules/arm_target_overlay/client/ArmTargetOverlayKeybinds.java` | `PEEK_MODIFIER` and the raw-state `isModifierDown()` |
| `modules/arm_target_overlay/client/ArmBlockEntityReflection.java` | The cached field access into Create |
| `modules/arm_target_overlay/config/ArmTargetOverlayConfig.java` | The two colour groups |
| `data/vanillaplusadditions/tags/item/arm_goggles.json` | Which helmets unlock the overlay |
| `standalone/arm_target_overlay/ArmTargetOverlayStandalone.java` | `@Mod("vpa_arm_target_overlay")` entry point |

## See also

* [Item Vault Viewer](item_vault_viewer.md) — same goggles, same Left Ctrl modifier, for Create's Item Vault
* [Debug Overlay](debug_overlay.md) — reads the same `arm_goggles` tag
* [Block Glow](block_glow.md) — the x-ray line render type this module copied
* [Arm Target Overlay case study](../internal/arm_target_overlay_case_study.md) — how it was built, in German
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
