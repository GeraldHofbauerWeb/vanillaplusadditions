# VanillaPlusAdditions

![Banner](docs/github_banner.png)

A Minecraft NeoForge mod (1.21.1) that enhances vanilla gameplay with useful additions while maintaining the original feel.

> 🤖 **AI Collaboration Notice**: This project was developed in collaboration with AI coding assistants. The AI helped with code implementation, documentation, and project structure. While the core ideas and direction came from human creativity, the AI's assistance made this project more robust and feature-complete. We believe in transparency about AI usage while celebrating the potential of human-AI collaboration in software development.

## 🎯 Features

VanillaPlusAdditions is **modular**: every feature below is a self-contained module that can be
enabled/disabled and configured independently (`config/vanillaplusadditions-common.toml`, hot-reloaded
on save). Runtime toggling is also possible with `/vpa module enable|disable <id>`. All integrations
with other mods are **optional** — modules detect them at runtime and degrade gracefully.

---

### 🐾 Companions & Guardians

#### 🐱 Cat Guardian
Turns tamed cats into active base defenders, with food bowls and an automatable feeding station.
- **Cat Bowls & Feeding Station**: Associate tamed cats with a bowl (shift-right-click). Fed cats (fish) actively guard the area and attack hostile mobs within the guard radius (default 32 blocks XZ / 16 Y, configurable).
- **Cat Armor**: Iron, Gold, Diamond and Netherite — increase attack damage and absorb incoming damage; repairable at the anvil (scute or the tier's ingot).
- **Loot & XP Collection**: Cats gather drops from kills into an internal inventory; XP from their kills is buffered and, at a feeding station, converted into Bottles o' Enchanting (hopper/Create-automatable).
- **Smart Guard AI**: returns to base after combat, low-health cats flee home to heal, dives after underwater mobs with water breathing, climbs ledges/fences, one-shots point-blank creepers (no explosion), never teleports while on guard duty.
- **Engineering Goggles overlay** (Create): hold the cat keybind (default Left Ctrl) and look at a guardian cat to peek its stats popup (HP / armor / XP / owner); 3D boxes (outlines, guard radius, path) on a separate toggle (default Numpad +).
- **Cat Inventory GUI** (modifier + right-click): equip armor, view food/XP/armor bars. Station skins selectable via a deco slot. Fully localized (EN, DE, DE-AT, ES, FR, CS).

#### 🐸 Axolotl Guardian
The underwater counterpart to Cat Guardian: axolotl food bowls and feeding stations. Tamed, fed axolotls actively guard your underwater base against hostile mobs. Axolotls can be scooped and placed via a bucket round-trip, and stations support decorative skins. Owner is mirrored to the client for overlays.

#### 🐺 Battle Dogs
Adds Iron, Gold, Diamond and Netherite **wolf armor**, rendered by the vanilla wolf-armor layer. Each tier increases the wolf's attack damage. Equip by right-click, remove with shears.

---

### 👹 Mobs & Spawning

#### 🔥 Hostile Zombified Piglins
Makes zombified piglins always aggressive towards players in the Nether for a more challenging experience. Configurable detection range and anger duration, with smart targeting and player switching.

#### 💀 Wither Skeleton Enforcer
Prevents normal skeletons from spawning in the Nether and (optionally) replaces them with Wither Skeletons. Broadcasts messages about blocked spawns in debug mode.

#### 🐦‍🔥 Better Mobs
Enhances mob variety and challenge: mobs can spawn with customizable armor and potion effects. Configurable spawn chances and equipment tiers, with different settings per Y-level or Nether/End dimension.

#### ✨ Mob Glow (command)
`/mobglow` makes all mobs of a specified type glow (configurable duration, including infinite) for easier tracking. Clear by type or all at once — handy for server administration and debugging.

#### 👻 Haunted House
Creates an atmospheric, spooky experience in configured structures (default: Witch Villas).
- **Witch Spawn Boosting** in target structures, then **invisible entity replacement** (Alex's Mobs Murmurs) that stay invisible until a player looks directly at them (raycast line-of-sight).
- **Atmospheric fog** (darkness effect) inside the structure, with configurable intensity, dissipating on exit.
- **Disabled by default**; auto-enables when Alex's Mobs (`alexsmobs`) and Dungeons and Taverns (`mr_dungeons_andtaverns`) are both present.

---

### 🧱 Blocks, Rails & Create Companions

#### 🚃 Mob Cart Loader
Two directional blocks that automate moving mobs in and out of minecarts on the adjacent rail — and, with Create, in and out of train carriages:
- **Mob Loader** boards a mob standing in the adjacent pen into a parked, empty rideable minecart.
- **Mob Unloader** ejects a mob riding a parked minecart into the adjacent pen.
- **Create trains** (optional, on by default): point the loader's **output** face — or the unloader's **input** face — at a **Create track** instead of a rail, and the same blocks serve **carriage seats**. The block doesn't have to sit in the track bed: it scans up to 3 blocks along its facing direction (configurable), so it can stand off to the side next to the carriage body — but the scan stops at the first solid block, so it never reaches through a wall. The mob goes into (or comes out of) the seat nearest to that track, within a configurable radius (default 4 blocks). Only **standing** trains are served, so a train passing through is never touched. Unlike Create's own seats this deliberately ignores Create's `seatHostileMobs` restriction — hostile mobs can be shipped by rail, and the comparator still tells you what's aboard.
- **Inverse redstone**: active by default, a redstone signal disables the block. 6-way directional with a distinct **input** and **output** face (flow chevrons on the glass sides point input → output). Never touches players.
- **Comparator output** on the stored mob: `0` = empty, `1` = a hostile mob (MONSTER category), `2` = a friendly/neutral mob — so redstone can react to *what* is being moved.
- The affected mob spins as a **live model inside the glass block**; with Create's Engineering Goggles a stats panel shows the mob type and (while sneaking) its health.
- Craft with a glass frame + a minecart, a saddle and a hopper (loader) / dropper (unloader).

#### 🛤️ Minecart Chunk Loading
Adds a **Chunk Loader Rail** that keeps chunks loaded around traveling minecarts, so long-distance rail networks don't stall at chunk borders. Chunks are forced only while a cart is active and released after a timeout.

#### 🚄 Train Chunk Loading
Adds a **Chunk Loader Track** — a real, connectable **Create train track** variant (curves, slopes, girders — everything a normal track does) that keeps chunks loaded around trains passing over it. Create itself only *simulates* trains through unloaded chunks: they keep moving, but onboard drills, deployers, hoppers and portable storage interfaces stop working. Over Chunk Loader Tracks they keep running. Same mechanics as the Chunk Loader Rail: chunks are forced while a carriage is over the track and released after a timeout; state survives restarts. Craft it like the rail: 8 train tracks around an ender pearl → 8 tracks. **Placement tip:** a track in an *unloaded* chunk can't see the train — space loader tracks closer than `chunk_load_radius × 16` blocks along the line so the loaded corridor rolls along with the train. A **Ponder entry** (hold **W** on the item) walks through the placement rules in-game.

#### ⚓ Stationary Chunk Loader
A **Chunk Anchor** block that force-loads its chunk (plus a configurable radius) while redstone-powered — for redstone clocks and Create contraptions that must keep running in unloaded chunks.

#### 💧 Create Water Wheel Unstucker
Detects Create water wheels that stalled after a chunk reload (a known kinetic/flow desync) and can kick them back into rotation. Ships with the `/vpaunstuck` command to re-initialise stalled wheels on demand; auto-fix is opt-in.

#### 🔱 Conduit Attack Range
Makes vanilla conduits **attack hostile mobs at every active tier** (not just at full size), within half the Conduit Power radius, and fixes the client-side attack beam so the animation shows correctly.

#### 🌌 End Conduit
An **End-only conduit upgrade**: a distinct craftable item that renders like a vanilla conduit but activates only in the End, needs **no water**, and forms its frame from Glowstone / End Stone / End Stone Bricks / Sea Lantern. It grants Conduit Power (and, together with End Oxygen, effectively unlimited air) on dry End land. Crafted from chorus fruit, eyes of ender and a vanilla conduit.

---

### 🛠️ Items & Crafting

#### 🐟 Flying Fish
A new aquatic mob with spawn egg, bucket and cooked food variant, woven into vanilla fishing. **Flying Fish Boots** let you skim faster across the water surface and gain short leaps while sprinting on water.

#### 📦 Stackables
Makes normally-unstackable items stackable and raises stack sizes for configured items:
- Potions / splash / lingering (default 16); stews & soups (default 64).
- Auto-detects Tough As Nails items (juices, water bottles, ice cream, empty canteens, …).
- Note: filled canteens with durability can't be stacked (Minecraft limitation).

#### 🧰 Custom Crafting Recipes
Adds configurable **shaped and shapeless** crafting recipes straight from the module config — including the fair rail upgrades (plain rails → powered/detector/activator). The place to add your own vanilla/cross-mod recipes without a datapack.

#### 🔨 Free Anvil Repair
Pure anvil repairs cost **no XP levels** — only plain repairing is free; combining enchanted items, applying books and renaming keep vanilla costs.
- Material repair and same-type combine repair (unenchanted sacrifice), even for gear past the "Too Expensive!" cap.
- **Extra repair materials** (`extra_repair_materials`, Quark-style `item=material`): netherite gear repairs with diamonds and Create's diving gear with its base material out of the box; add your own combos.

#### 💎 Waystone Amethyst Repair
Repair the Waystones **Warp Stone** with amethyst in an anvil (free while Free Anvil Repair is enabled). Inactive without the Waystones mod.

---

### 🌍 World & Environment

#### 🫧 End Oxygen
Removes breathable oxygen from the End, so players must hold their breath or use gear (Create backtanks, Conduit Power via the End Conduit) to survive. Configurable.

#### 🍎 Food Effects
Enhances food items with additional potion effects and thirst restoration.
- Add any potion effect to any item via config, with an optional probability per effect; configured items become **always edible**.
- **Tough As Nails** support (optional): thirst restoration and heating/cooling tooltips.
- Ships with extensive defaults for Vanilla, Create and Tough As Nails items.

#### 🌙 Idle Gamerule Pause
Pauses day / weather / season cycles while the server is empty and resumes them on the first join — the world doesn't drift while nobody is online.

#### 🗺️ Chunk Reset (command)
Provides a command to delete and regenerate chunks from world generation — useful for resetting explored areas to pick up new world-gen.

---

### 🥽 Overlays, HUD & Quality-of-Life

#### 🧪 Debug Overlay (framework)
The shared **Engineering-Goggles debug-overlay** framework other modules plug into: a global toggle plus chunk borders, cat stats, and more. Uses Create's goggles when present, falling back to the `vanillaplusadditions:arm_goggles` item tag.

#### 🦾 Arm Target Overlay
While wearing Engineering Goggles, shows a Create **Mechanical Arm's** input/output target positions in the world — makes configuring arms much easier.

#### 📦 Item Vault Viewer
Lets players view the aggregated contents of a Create **Item Vault**: wear Engineering Goggles and **Ctrl+right-click** the vault (modifier key rebindable). Works on placed vaults **and on vaults mounted on moving Create contraptions** — carts, trains, elevators — where Create itself deliberately opens nothing.

#### 🎥 Static FOV
Stops the field-of-view from widening when the player moves faster (sprinting, Speed, elytra/flight) — a steadier view.

#### ⚙️ VPA Options (backup/restore)
Backup & restore of client options (`options.txt`) including **all keybinds** — manual snapshots via `/vpaoptions` or an Options-screen button, plus automatic rotating backups whenever settings change. Great when a modpack update scrambles your controls.

#### 🎒 Overpacked Extensions
Quality-of-life features for **Overpacked** giant backpacks (each individually toggleable):
- **Slowdown override** — rescale Overpacked's full-backpack movement penalty with a configurable multiplier (up to and including no slowdown). Needs Overpacked.
- **Backpack keybinds** — open the compartments of a worn giant backpack (main compartment on `B` by default; right/left unbound) without taking it off. Needs Overpacked + Curios.

*Sorting & searching inside the backpack aren't reimplemented — Quark already does both. Whitelist Overpacked's screen in Quark's config to get Quark's own sort button + search bar on the backpack (see [docs](docs/overpacked_extensions.md)).*

#### 🪦 Death Coordinates Announcer
Announces player death coordinates in chat; operators can click the message to teleport to the death location.

---

### 🔌 Integrations & Utility

#### 🗺️ BlueMap Signs
Turns `[bm]` signs into curated **BlueMap** markers; manage them with `/bmsigns`. Server-side; inert without BlueMap.

#### 🚫 Texture Kill
Replaces configured textures with a fully transparent one — handy for hiding cosmetic textures from other mods (e.g. Create contraption hats). Format: `namespace:textures/category/name.png`.

## 🔧 Configuration

Each module has its own configuration options. See our detailed guides:
- [Module Configuration Guide](docs/MODULE_CONFIG_GUIDE.md)
- [Debug Logging Configuration](docs/DEBUG_LOGGING_CONFIG.md)
- [MobGlow Command Guide](docs/MOBGLOW_MODULE_GUIDE.md)

## 🚀 Installation

1. Download the latest version from [Releases](https://github.com/Gerry3010/vanillaplusadditions/releases)
2. Install NeoForge for Minecraft 1.21.1
3. Place the jar file in your mods folder
4. Start Minecraft and enjoy!

## 🧩 Module dependencies (other mods)

**None of these mods are required to run VanillaPlusAdditions** — since v1.0.0-beta.25 all of
them are optional dependencies. Modules that integrate with another mod detect it at runtime
and degrade gracefully when it is missing. All modules not listed here are pure vanilla.

| Module | Integrates with | Without that mod |
|---|---|---|
| `arm_target_overlay` | [Create](https://modrinth.com/mod/create) | Overlay inactive (it visualizes Create's Mechanical Arm targets) |
| `item_vault_viewer` | Create | Module skips initialization entirely (it views Create's Item Vaults) |
| `train_chunk_loading` | Create | Module skips initialization entirely (it adds a Create track variant) |
| `create_water_wheel_unstucker` | Create | Module skips initialization (needs Create water wheels) |
| `mob_cart_loader` | Create *(optional)* | Minecart loading/unloading fully functional — train-carriage seats and the goggle stats panel need Create |
| `end_oxygen` | Create *(optional)* | Fully functional — Create backtanks just can't supply air in the End |
| `debug_overlay` | Create *(optional)* | Goggles check falls back to the `vanillaplusadditions:arm_goggles` item tag |
| `overpacked_extensions` | [Overpacked](https://modrinth.com/mod/overpacked) + [Curios](https://modrinth.com/mod/curios) | Slowdown override still works with Overpacked alone; backpack keybinds + sort button are inert without a worn giant backpack |
| `waystone_amethyst_repair` | [Waystones](https://modrinth.com/mod/waystones) | Module inactive (needs the Warp Stone) |
| `cat_guardian` | [Sable](https://modrinth.com/mod/sable) *(optional)* | Cat bowl / feeding station use plain block variants (no ship-assembly awareness) |
| `axolotl_guardian` | Sable *(optional)* | Axolotl bowl / feeding station use plain block variants |
| `block_glow` | Sable *(optional)* | No difference — the integration only additionally highlights blocks *inside* Sable sub-levels (ships), which don't exist without Sable |
| `food_effects` | [Tough As Nails](https://modrinth.com/mod/tough-as-nails) *(optional)* | Thirst-related food effects are skipped |
| `stackables` | Tough As Nails *(optional)* | Only vanilla items are made stackable |
| `bluemap_signs` | [BlueMap](https://modrinth.com/plugin/bluemap) (server) | Module stays inert (`[bm]` signs do nothing) |
| `haunted_house` | [Alex's Mobs](https://modrinth.com/mod/alexs-mobs) + [Dungeons and Taverns](https://modrinth.com/datapack/dungeons-and-taverns) | Module skips initialization (needs the Murmur entity + witch villa structure) |

**Standalone module jars** (`vpa_<module>.jar` from the releases) additionally require
`vpa_core.jar`; `vpa_cat_guardian` also needs `vpa_debug_overlay` + `vpa_flying_fish`, and the
two chunk loaders (`vpa_minecart_chunk_loading`, `vpa_stationary_chunk_loader`) need
`vpa_debug_overlay`. The all-in-one bundle jar has no such requirements (never install bundle
and standalone jars together).

## 🔨 Development

### Prerequisites
- JDK 21
- Gradle 8.4+
- Git

### Setup
```bash
# Clone the repository
git clone https://github.com/Gerry3010/vanillaplusadditions.git
cd vanillaplusadditions

# Setup development environment
./gradlew build
```

### Test Environments
The project includes test server and client setups:
```bash
# Test server
cd test-server
./build-and-test.sh

# Test client
cd test-client
./launch-client.sh
```

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) first.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🌟 Credits

- **Developer**: Gerald Hofbauer
- **Framework**: [NeoForge](https://neoforged.net/)

## 📚 Documentation

- [Module System Overview](docs/MODULE_SYSTEM.md)
- [Configuration System](docs/CONFIGURATION_SYSTEM_SUMMARY.md)
- [Testing Guide](docs/TESTING.md)
- [Companion Armor & Cat Guardian Systems](docs/COMPANION_ARMOR.md)
- [Cat Guardian](docs/cat_guardian.md)
- [Arm Target Overlay](docs/arm_target_overlay.md)
- [Block Glow](docs/block_glow.md)
- [Chunk Reset Command](docs/chunk_reset.md)
- [Custom Crafting Recipes](docs/custom_crafting_recipes.md)
- [End Oxygen](docs/end_oxygen.md)
- [Mob Drops](docs/mob_drops.md)
- [Overpacked Extensions](docs/overpacked_extensions.md)
- [Texture Kill](docs/texture_kill.md)

## 🐛 Debug Logging

VanillaPlusAdditions includes a sophisticated debug logging system:
- Global and per-module control
- Detailed log messages for troubleshooting
- See [Debug Logging Guide](docs/DEBUG_LOGGING_CONFIG.md)

## 🔗 Links

- [GitHub Repository](https://github.com/Gerry3010/vanillaplusadditions)
- [Issue Tracker](https://github.com/Gerry3010/vanillaplusadditions/issues)
- [NeoForge](https://neoforged.net/)
