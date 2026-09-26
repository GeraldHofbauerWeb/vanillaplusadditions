# Glow Mushroom

> **TL;DR** — A third kind of small mushroom that lights the ground it stands on. Dig it up, replant
> it, eat it for a brief glow, or boil it into a stew that makes you glow for half a minute.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `glow_mushroom` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_glow_mushroom.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_glow_mushroom.jar) · also needs `vpa_core` |
| **Config section** | `[modules.glow_mushroom]` |
| **Since** | `v1.0.0-beta.93` |
<!-- vpa:meta:end -->

## What it does

Mushroom islands have red mushrooms and brown mushrooms. This adds a third: a small orange one with
a shroomlight-coloured cap that gives off light.

It behaves like the vanilla two in every way that matters. It grows on the same ground, breaks
instantly, drops itself, and can be replanted anywhere a mushroom would survive. What it adds is
light level 12 — enough to keep a cave corner lit — and, unlike the vanilla mushrooms, it is edible.

| | Effect | Hunger |
|---|---|---|
| **Glow Mushroom** (raw) | Glowing, 10 s | 1 |
| **Glow Mushroom Stew** | Glowing, 30 s · Internal Warmth, 10 min | 6 |

The stew is the vanilla recipe with the red mushroom swapped out: **bowl + glow mushroom + brown
mushroom**, shapeless. The bowl comes back when you finish it.

The warmth is not this module's doing. The stew is listed in
[`food_effects`](food_effects.md)' default table as
`vanillaplusadditions:glow_mushroom_stew;toughasnails:internal_warmth;12000;0`, exactly like vanilla's
mushroom stew — so it needs Tough As Nails, it follows whatever that config says, and being on that
list is also what makes it **always edible**, on a full hunger bar. Neither applies if `food_effects`
is disabled. A config that already exists keeps its own list: the new line only reaches a server whose
`vanillaplusadditions-common.toml` is regenerated or edited by hand.

## Where it comes from

The block is registered by the mod, but it only *grows in the world* if the
`vpa_mushroom_fields_plus` world datapack is installed — that pack seeds it into the ground cover of
mushroom fields at a weight of 1 against 3 red and 3 brown. Bone meal likewise grows the big
branched variant from that pack.

**This makes the pack depend on the mod, not the other way round.** A world that has the pack but
not the module refuses to load with a datapack error, because the pack names a block that does not
exist. The module on its own is harmless: the mushroom is simply absent from worldgen and has to be
crafted or spawned in.

## Two details worth knowing

**The orientation is decided by the position, not by chance.** The block ships two models — one
straight, one turned 45° — and Minecraft picks between blockstate variants by block coordinates. Put
a glow mushroom where a red one stood and it will face the same way the red one did. That is vanilla
behaviour, not something this module does.

The 45° turn has to live in the model's elements. A blockstate's own `y` key only accepts
0/90/180/270, and turning a square cap by 90° leaves it looking identical — which is exactly the
trap this module fell into on the first attempt.

**Glowing and lighting are two separate things.** `lightLevel(12)` makes the mushroom light its
surroundings. What makes the mushroom itself look lit is NeoForge's per-element `neoforge_data`,
which sets the cap to `block_light: 15` and the stem to 8. Remove the second and you get a mushroom
that brightens the floor while looking dull itself.

## What the big mushrooms drop

The giant glow mushrooms that the world datapack grows are built from **vanilla** blocks: a
`mushroom_stem` trunk and a cap of roughly 85 % `honey_block` to 15 % `shroomlight`. Left alone that
would make every one of them a free honey and shroomlight farm.

Their loot tables cannot simply be edited, because they are global — a Nether shroomlight has to
keep dropping shroomlight, and honey blocks are load-bearing in redstone. The throttle therefore
runs in `BlockDropsEvent` and only fires when a `mushroom_stem` stands within five blocks sideways,
eight below or two above. Inside a mushroom that is always true; anywhere else essentially never.

| Breaking a cap block | Yield |
|---|---|
| 5 % | the block itself (honey or shroomlight) |
| 35 % | a glow mushroom |
| 60 % | nothing |

**Silk Touch bypasses the rule entirely** and returns the block. The stem is untouched and drops
`mushroom_stem` as usual.

## The stew ships no texture

Its model points `layer0` at `minecraft:item/mushroom_stew`, so the game draws the bowl it already
has. This is deliberate and it is the safer way round: copying Mojang's PNG into the mod would mean
redistributing their asset, while referencing it does not. The permanent sparkle is the
`enchantment_glint_override` component — no enchantment involved.

Both effect durations are read when the items are registered, so a change needs a restart to take
hold.

<!-- vpa:config:start -->
## Configuration

Section `[modules.glow_mushroom]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_glow_mushroom-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `mushroom_glow_ticks` | int | `200` | 20 ~ 6000 | Duration of the Glowing effect after eating a raw glow mushroom, in ticks (20 = one second). Read when the item is registered, so a change needs a restart. |
| `stew_glow_ticks` | int | `600` | 20 ~ 24000 | Duration of the Glowing effect after eating glow mushroom stew, in ticks. Same restart caveat. |
<!-- vpa:config:end -->
