# Quark Fresh Animations

> **TL;DR** — Fresh Animations stops at vanilla mobs, so Quark's animals kept moving like stock Minecraft next to it. This module ships a CEM pack for the foxhound, crab, stoneling, wraith, shiba and toretoise in the same style and switches it on by itself when Quark and Fresh Animations are both installed.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `quark_fresh_animations` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | [Quark](https://modrinth.com/mod/quark) <sub>tested 4.1-482</sub>, `entity_model_features` |
| **Download** | [`vpa_quark_fresh_animations.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_quark_fresh_animations.jar) · also needs `vpa_core` |
| **Config section** | `[modules.quark_fresh_animations]` |
| **Since** | `v1.0.0-beta.101` |
<!-- vpa:meta:end -->

## What it does

[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) gives vanilla mobs ears
that twitch, heads that track you and tails that wag. It stops at vanilla, though. Quark's animals
are not on its list, so a foxhound kept moving like stock Minecraft while every wolf next to it did
not, and once the foxhound is your mount it spends the whole day right in front of your face.

This module ships a small resource pack that covers Quark's animals in the same style and switches
it on by itself when Quark and Fresh Animations are both installed. There is nothing to download
and nothing to tick.

| Mob | What moves |
|---|---|
| **Foxhound** | Ears that twitch on their own and fold back at a run, a snout that sniffs in short bursts while it stands, a tail that wags in phases (about 1.5 s every 7 s) instead of never stopping, and eyes with a lid, an iris and a pupil that follows the head, plus a rare glance and a short blink. The legs are shorter and sit further in, so they no longer swing through the head; the sitting pose stays Quark's. |
| **Crab** | Claws that open and close now and then, eye stalks that look around on their own, legs that shuffle while it stands. |
| **Stoneling** | Heavy, slow steps with the arms swinging along, and drowsy eyes: lids that hang half shut and a slow, heavy blink. |
| **Wraith** | The chain between its wrists is a solid 3D chain that swings, glowing pupils drift through the eye hollows, and body and arms drift like a ghost's. |
| **Shiba** | Eyes that blink and look at you, and a real 3D tongue instead of Quark's flat one; it slides out now and then, sitting or running, and pants. Quark already moves the head, ears, tail and tongue on every axis itself, so the pack only adds the eyes and switches the tongue's visibility. |
| **Toretoise** | A heavy, rowing walk that lifts its feet in diagonal pairs, side eyes under heavy lids that blink slowly, and slow chewing. It needs a mixin, so it lives in a built-in pack of its own (see the limits below). |
| **Forgotten** | Nothing to do. Quark draws it with the vanilla skeleton model, which Fresh Animations already covers. |

Everything runs on top of Quark's own animation. Quark still does the walking, sitting and
attacking; the pack only moves axes Quark leaves alone and starts from Quark's rest pose.

The foxhound and its armour are two models in Quark (the armour is a separate baked model), so the
pack has a geometry file for each. Both point at the same expression file, which keeps animal and
armour in step when one of them is edited.

## Why it exists

The pack does not touch Fresh Animations' own files. It only adds entries under Quark's namespace,
so a Fresh Animations update cannot break it.

The automatic activation checks two things, and the two checks are not equally reliable:

* Quark is a mod, so this is an exact question with an exact answer: `ModList.get().isLoaded`.
  Without Quark the files describe animals that do not exist.
* Fresh Animations is a resource pack. It has no id and no registry entry; the file name in the
  `resourcepacks` folder is all there is to go on, which makes this check a **heuristic**, and the
  config says so. `require_fresh_animations` turns it off for anyone whose Fresh Animations lives
  somewhere the check cannot see.

Entity Model Features is deliberately **not** part of the check, although it is the mod that reads the
pack. Without EMF nobody reads the files, so they sit there and do nothing, and a check for EMF
could only ever refuse wrongly.

## In detail

### Where the pack lives

`resourcepacks/vpa_quark_fresh_animations/` in the repository, copied into the jar at the same path
by a `processResources` block. That is the path `AddPackFindersEvent.addPackFinders` resolves
against, so the pack you edit is the pack that ships. The developer README next to it stays out of
the jar. The Toretoise has its own folder beside it, `resourcepacks/vpa_quark_toretoise/`, which
only the mod adds.

### Which jar owns it

`addPackFinders` reads its argument's namespace as a mod id, and that id is `vanillaplusadditions`
in the bundle but `vpa_quark_fresh_animations` in the standalone jar. The module keeps no list of
those names. It asks every loaded mod file which one carries the folder and uses that mod's id.

### Why the pack cannot be unticked

It is registered with `alwaysActive = true`. NeoForge's own javadoc says a pack added through this
event is **off by default**, and a compatibility pack nobody switches on does nothing at all. So the
off switch is this module's `enabled` key; the pack list will not let you untick it.

<!-- vpa:config:start -->
## Configuration

Section `[modules.quark_fresh_animations]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_quark_fresh_animations-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `require_fresh_animations` | boolean | `true` | — | Only enable the built-in pack when a Fresh Animations pack is found in the game's resourcepacks folder. Unlike the Quark check this is a heuristic: Fresh Animations is a resource pack, not a mod, so there is no id to ask for and the file name is all there is to go on. The match strips everything but letters and lower-cases the rest, so FreshAnimations_v1.10.4.zip and fresh-animations both count. Turn it off when Fresh Animations lives somewhere the check cannot see (a modpack overlay, a renamed file); the pack then enables itself on the Quark check alone. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Entity Model Features | The pack is added, but nothing reads `.jem`/`.jpm`, so nothing happens and nothing breaks. |
| No Quark | The pack is never added. No Quark type is named anywhere in the module, so nothing is loaded that could fail verification. |
| Fresh Animations under an unusual name | The name check strips everything but letters, so `FreshAnimations_v1.10.4.zip` and `fresh-animations` both count. A pack inside a modpack overlay, or renamed past recognition, does not. Set `require_fresh_animations = false`. |
| Reading a part you also write | `part.rx = part.rx + …` reads back EMF's own value from the previous frame, not Quark's fresh one, so the offset adds up every frame and the part spins. Only animate axes Quark leaves alone, with absolute values starting from Quark's rest pose (`PartPose` in `createBodyLayer`), because writing an axis replaces that too. |
| Toretoise | Any `.jem` for it makes EMF rebuild the whole model, and Quark's renderer then picks a random cube from an empty leg part (its pivot for the step) and crashes. A client mixin (`ToretoiseRandomCubeMixin`) hands it the cube EMF moved into the leg's child part. The Toretoise model therefore lives in its own built-in pack, never in the standalone zip. |
| Standalone pack | Every build also writes `Quark-x-Fresh-Animations-<version>.zip`. While it sits in the `resourcepacks` folder, the mod leaves its built-in copy out. |
| Editing the `.jpm` | `tx`/`ty`/`tz` **replace** a part's position rather than offsetting it, and `invertAxis: "xy"` negates the translate's x and y when the model loads, so `.jem` and `.jpm` speak opposite signs for the same part. Lines are evaluated top to bottom, so a variable must be written before it is read. |
| Frequencies above 10 Hz | The game ticks at 20 per second, so anything faster aliases into jitter. An early version of the sniffing ran at 23 Hz and simply looked broken. |
| A wrong part name in a `.jem` | Fails **silently**: no error, no animation, no message. Export the geometry from EMF instead of typing it out. |
| Server | Irrelevant. Client-only, and nothing is sent anywhere. |

## Under the hood

Three classes, one mixin and a folder of data files. The module itself registers nothing: it
exists to own a config section and to give the pack finder something to ask.

The foxhound's expression file compensates for one Quark quirk. Quark's `prepareMobModel` writes
the interest angle (the head tilt when you hold food) onto the head's yaw axis, where vanilla puts
it on the roll axis, and `setupAnim` then adds the look direction on top. The head therefore turns
up to 27° past the player while nominally looking at them. `var.head_off = head.ry - torad(head_yaw)`
is exactly that surplus, and the pupils roll back by it, which is why the foxhound gives you a
sideways stare instead of looking through you.

### Getting the foxhound to lie down

Not this module's doing, but the question comes up while testing it. Quark's
`FoxhoundPlaceToRestGoal.canUse()` requires `isTame() && !isOrderedToSit() && !isResting()`, so
the dog has to be **standing**; telling it to sit is exactly what stops it from lying down. It then
walks to a `LIT_FURNACE`, a `FURNACE` or any `GLOWING` block (anything emitting light that is not a
fluid, campfires included) with air above it, and settles there on its own.

## License

Quark is licensed CC BY-NC-SA 3.0, and the pack's `.jem` files are Quark's entity models
re-exported through EMF, so the ShareAlike clause applies: both pack folders
(`resourcepacks/vpa_quark_fresh_animations`, `resourcepacks/vpa_quark_toretoise`) are
**CC BY-NC-SA 3.0** and carry their own `LICENSE`, which travels inside the jar and the standalone
zip. The module's Java code, like the rest of the mod, stays MIT. No Quark or Fresh Animations
file is shipped; Quark's textures are read from the installed mod at runtime.

## See also

* [Configuration Guide](../guides/configuration.md)
* [All modules](../../README.md#modules)
