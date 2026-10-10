# Vanilla Plus Additions

**56 small fixes and additions that make Minecraft — and a few of its mods — feel better than intended.**
Nothing here adds a new dimension or a tech tree. Each module scratches one itch: a compass that stops
forgetting its lodestone, cats that actually guard your base, a track that keeps the chunks loaded
while your train is somewhere else.

## ⚠️ Read this first: everything is ON

This mod is a toolbox, and out of the box **every module is enabled**. Some of them change the game
noticeably, so look through the list once and switch off what you don't want. It's one line per
module in `config/vanillaplusadditions-common.toml` (`enabled = false`).

The ones you'll notice first:

- **Harder mobs** — `better_mobs` (armoured, enchanted mobs), `hostile_endermen` (End endermen
  attack on sight), `hostile_zombified_piglins`, `wither_skeleton` (fortresses spawn wither skeletons)
- **The End needs air** — `end_oxygen`: you slowly suffocate in the End without a Create backtank or
  an End Conduit nearby
- **Economy** — `free_anvil_repair` (anvil repairs cost no XP), `mob_drops` (extra drops),
  `stackables` (potions, stews and pearls stack to 64)
- **Server behaviour** — `idle_gamerules` (daylight and weather pause while nobody is online),
  `death_coordinates` (death positions are announced in chat), `static_fov`
- **Commands for operators** — `/chunkreset`, `/kill-items`, `/mobglow`, and `/blockglow` (outlines ore through walls, so it needs operator rights)

Disabling a module stops what it does. Its blocks and items stay registered, so nothing you built
disappears from your world.

## Install

- **NeoForge 1.21.1**, on **client and server** (the mod adds blocks and items).
- **No other mod is required.** Modules for other mods switch themselves on when those are present:
  Create, Quark, Sable, Tough As Nails, BlueMap, Waystones, Overpacked, Enhanced AI and more.
- Want just one or two features? Every module is also available as its own small jar on
  [GitHub](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest).

## A few highlights

- **Compass Overhaul** — lodestone compasses no longer forget their target when a chunk is unloaded,
  and a second compass always points to world north.
- **Cat & Axolotl Guardian** — feed a tamed cat and it patrols your base and hunts hostile mobs;
  axolotls do the same underwater. Both get armour, bowls and feeding stations with dozens of skins.
- **Battle Dogs & Wolf Mount** — four tiers of wolf armour, and big wolves you can ride into a fight.
- **Chunk loading that follows you** — a rail and a Create track that carry loaded chunks along with
  the cart or train, plus an anchor block for a fixed area.
- **Mob Cart Loader** — load a mob into a passing minecart and unload it at the other end, UUID intact.
- **Flying Fish** — a fish that leaps out of the water, and boots that let you run across it.
- **Pocket Crafting** — right-click a crafting table in your inventory to craft on the spot.
- **Create fixes** — revives stalled water wheels, keeps stock links alive, rebinds redstone links.

Every module has its own page with the details:
[all modules](https://github.com/GeraldHofbauerWeb/vanillaplusadditions#-modules) ·
[configuration reference](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/blob/master/docs/reference/config.md)

## Credits

- **Vazkii / Quark** — the Foxhound armour textures are recolours of Quark's own, used with permission.
- **Fresh Animations by FreshLX** — the Quark mob animations are modelled after Fresh Animations'
  style. No Fresh Animations file is included; the add-on only switches on alongside it.
- Several textures are recoloured from vanilla Minecraft. The world compass and the Chunk Loader
  Track ship no pixels at all — they are painted at load time from the textures you already have.

> 🤖 **AI collaboration notice:** this project was built together with AI coding assistants. The ideas
> and direction are human; the AI helped with implementation, documentation and structure.

Code: MIT · [Source & issues](https://github.com/GeraldHofbauerWeb/vanillaplusadditions) ·
[Changelog](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/blob/master/CHANGELOG.md)
