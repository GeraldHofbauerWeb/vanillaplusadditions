# Quark Fresh Animations

> **TL;DR** — Fresh Animations only covers vanilla mobs, so Quark's foxhound moves like stock Minecraft while every wolf beside it does not - this module carries a CEM pack that fills the gap and enables it automatically when Quark and a Fresh Animations pack are both installed.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `quark_fresh_animations` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | [Quark](https://modrinth.com/mod/quark) <sub>tested 4.1-482</sub>, `entity_model_features` |
| **Download** | [`vpa_quark_fresh_animations.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_quark_fresh_animations.jar) · also needs `vpa_core` |
| **Config section** | `[modules.quark_fresh_animations]` |
| **Since** | the next release |
<!-- vpa:meta:end -->

## What it does

[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) replaces the stiff vanilla
mob animations with something alive — ears that twitch, heads that track you, tails that wag. It
covers **vanilla mobs only**. Quark's foxhound is not one, so it kept moving like stock Minecraft
while every wolf standing next to it did not. That is easy to overlook until the foxhound becomes a
mount and spends the day in front of your face.

This module carries a small resource pack that fills the gap, and switches it on by itself when the
two things it needs are present. Nothing to download, nothing to tick.

What the foxhound gained:

| | |
|---|---|
| **Ears** | Twitch on their own, lean back while running, each ear on its own rhythm |
| **Snout** | Sniffs in short bursts when standing still |
| **Tail** | Wags in phases — roughly 1.5 s every 7 s — instead of never stopping |
| **Legs** | Shortened and moved inward so they no longer swing through the head; the sitting pose is left to Quark |
| **Eyes** | Lid, iris and a moving pupil that follows the head, plus a rare glance and a short blink |

Two animals share the pack: the plain foxhound and the armoured one. Quark renders armour as a
separate baked model, so it needs its own geometry file — both point at the same expression file,
which is what keeps animal and armour from drifting apart when one of them is edited.

## Why it exists

The pack is not a fork of Fresh Animations and does not overwrite any of its files. It only adds
entries under Quark's own namespace, which is why a Fresh Animations update cannot break it.

The interesting part is the automatic activation, and it has two halves that are not equally sure of
themselves:

* **Quark** is a mod, so this is an exact question with an exact answer: `ModList.get().isLoaded`.
  Without Quark the file describes an animal that does not exist.
* **Fresh Animations** is a *resource pack*. There is no id to ask for, no registry to look in — the
  file name in the `resourcepacks` folder is the only handle there is. That makes it a **heuristic**,
  and it is documented as one rather than presented as a check. `require_fresh_animations` turns it
  off for anyone whose Fresh Animations lives somewhere this cannot see.

Deliberately **not** part of the gate is Entity Model Features, even though it is the mod that
actually reads the pack. Without EMF the files are read by nobody — inert, not broken — so a check
for it could only ever refuse wrongly.

## In detail

### Where the pack lives

`resourcepacks/vpa_quark_fresh_animations/` in the repository, copied into the jar at the same path
by a `processResources` block. That is the path `AddPackFindersEvent.addPackFinders` resolves
against, so source layout and jar layout are the same thing — an important property, because it means
the pack you edit is the pack that ships.

The developer README next to it is excluded from the jar.

### Which jar owns it

`addPackFinders` reads its argument's namespace as a mod id, and that id is `vanillaplusadditions`
in the bundle but `vpa_quark_fresh_animations` in the standalone jar. Instead of keeping a list of
names in step with the build, the module asks every loaded mod file which one actually carries the
folder, and uses that mod's id. One less thing that can silently fall out of sync.

### Why the pack cannot be unticked

It is registered with `alwaysActive = true`. A resource pack added through this event is **off by
default** — NeoForge says so in its own javadoc — and a compatibility pack nobody switches on does
nothing at all. The off switch is this module's `enabled` key instead of the pack list.

<!-- vpa:config:start -->
## Configuration

Section `[modules.quark_fresh_animations]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_quark_fresh_animations-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `require_fresh_animations` | boolean | `true` | — | Only enable the built-in pack when a Fresh Animations pack is found in the game's resourcepacks folder. Unlike the Quark check this is a heuristic - Fresh Animations is a resource pack, not a mod, so there is no id to ask for and the file name is the only handle. The match strips everything but letters and lower-cases the rest, so FreshAnimations_v1.10.4.zip and fresh-animations both count. Turn it off when Fresh Animations lives somewhere the check cannot see (a modpack overlay, a renamed file), and the pack then enables itself on the Quark check alone. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| No Entity Model Features | The pack is added and stays inert — nothing reads `.jem`/`.jpm`, nothing breaks. |
| No Quark | The pack is never added. No Quark type is named anywhere in the module, so nothing is loaded that could fail verification. |
| Fresh Animations under an unusual name | The name check strips everything but letters, so `FreshAnimations_v1.10.4.zip` and `fresh-animations` both count — but a pack inside a modpack overlay or renamed past recognition does not. Set `require_fresh_animations = false`. |
| Other Quark animals | Not covered yet. Crab, Stoneling, Wraith and the rest are files, not code: export the model from EMF, drop the `.jem` in beside the foxhound's. |
| Editing the `.jpm` | `tx`/`ty`/`tz` **replace** a part's position rather than offsetting it, and `invertAxis: "xy"` negates the translate's x and y when the model loads — so `.jem` and `.jpm` speak opposite signs for the same part. Lines are evaluated top to bottom, so a variable must be written before it is read. |
| Frequencies above 10 Hz | The game ticks at 20 per second, so anything faster aliases into jitter. An early version of the sniffing ran at 23 Hz and simply looked broken. |
| A wrong part name in a `.jem` | Fails **silently** — no error, no animation, no message. Always export the geometry from EMF instead of typing it out. |
| Server | Irrelevant. Client-only, and nothing is sent anywhere. |

## Under the hood

Three classes and three data files. The module itself registers nothing: it exists to own a config
section and to give the pack finder something to ask.

The expression file compensates for one Quark quirk that is worth knowing about. Quark's
`prepareMobModel` writes the interest angle — the head tilt when you hold food — onto the head's
**yaw** axis, where vanilla puts it on the **roll** axis, and `setupAnim` then adds the look
direction on top. The head therefore turns up to 27° *past* the player while nominally looking at
them. `var.head_off = head.ry - torad(head_yaw)` is exactly that surplus, and the pupils roll back by
it, which is why the foxhound gives you the sideways stare instead of looking through you.

### Getting the foxhound to lie down

Not this module's doing, but the question comes up while testing it: Quark's
`FoxhoundPlaceToRestGoal.canUse()` requires `isTame() && !isOrderedToSit() && !isResting()`. The dog
has to be **standing** — telling it to sit is what prevents it from lying down. It then walks to a
`LIT_FURNACE`, a `FURNACE` or any `GLOWING` block (anything emitting light that is not a fluid,
campfires included) with air above it, and settles there on its own.

## See also

* [Configuration Guide](../guides/configuration.md)
* [All modules](../../README.md#modules)
