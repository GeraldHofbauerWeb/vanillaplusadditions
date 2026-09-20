# Haunted House

> **TL;DR** — Inside Witch Villas, part of the mob spawning is taken over: spawns are swapped for an
> invisible stalker that only fades into view once you look straight at it, while a creeping darkness
> follows you from room to room.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `haunted_house` |
| **Side** | Server only |
| **Requires** | [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) <sub>tested 4.4.4</sub> |
| **Works with** | [Alex's Mobs](https://modrinth.com/mod/alexs-mobs), `nova_structures` |
| **Download** | [`vpa_haunted_house.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_haunted_house.jar) · also needs `vpa_core` |
| **Config section** | `[modules.haunted_house]` |
| **Since** | `v0.5.0` |
<!-- vpa:meta:end -->

## What it does

Name a structure — out of the box a Witch Villa — and the module takes over part of what happens
inside it. Three things, in this order:

* **More witches.** A mob that is about to spawn in the villa and is *not* a witch is cancelled and a
  witch put in its place instead, 78 % of the time.
* **An invisible tenant.** A share of the witches — 10 % by default — never appears. In their place
  comes the configured replacement entity, carrying permanent Invisibility. It hunts, it hurts, and
  it stays unseen until a player looks straight at it from within 32 blocks with a clear line of
  sight. Then the effect is removed, for good.
* **The dark.** While you are inside, you are kept under Darkness. Indoors it thickens the longer you
  stay, in the courtyard it thins out again, and it trails behind you for a few seconds after you
  leave.

On top of that the module keeps its own list of usable spots in the rooms you walk through and spawns
into it directly, past the vanilla spawn machinery — so the house does not fall quiet when the mob
cap elsewhere is full or the light level says no.

Two things the README used to claim and the code does not do:

* **The default replacement entity is `minecraft:witch`**, not Alex's Mobs' Murmur. An invisible witch
  is what you get out of the box; `alexsmobs:murmur` is a suggestion in a config comment, and setting
  it is a config edit like any other.
* **The module is off by default and does not switch itself on.** It needs
  [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) (`mr_dungeons_andtaverns`) to
  wire up at all, and that check decides only whether the module is *built*, never whether it is
  *enabled*. Set `enabled = true` in the config, or `/vpa module enable haunted_house` for a runtime
  override.

## In detail

### Where "inside the house" ends

Every path in the module starts with the same question, and it is asked at chunk granularity.
`StructureManager.getAllStructuresAt(pos)` does not test a bounding box; it hands back the structure
*references* of the chunk the position sits in:

```java
public Map<Structure, LongSet> getAllStructuresAt(BlockPos pos) {
    SectionPos sectionpos = SectionPos.of(pos);
    return this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
}
```

So "in the villa" means "in a chunk that some piece of the villa touches", full height, right out to
the chunk border. That is deliberately coarse and it is why everything downstream leans on block
material instead — the module works out what a room looks like rather than trusting the structure
box.

The structure id is then matched as a **substring**, not for equality:

```java
for (String targetStructure : targetStructures.get()) {
    if (structureId.contains(targetStructure)) {
        return true;
    }
}
```

`dungeons_and_taverns:witch_villa` therefore also matches a hypothetical `…:witch_villa_annex`, and a
deliberately short entry such as `dungeons_and_taverns:witch` would match every structure in that
namespace whose id starts with `witch`. A one-word entry is not possible: the list validator
`isValidStructureEntry` insists on exactly two colon-separated parts, logs *Invalid structure entry
format* for anything else and lets the entry be corrected back to the default — a bare `witch` never
reaches the matcher. One shorter form does slip through: `:witch` still splits into two parts, and
unlike the entity-id validator this one does not reject blank halves, so it matches that path prefix
in *every* namespace.

The fog handler goes one step further and adds a vertical test, because a villa spans several chunks
and its start is rarely under your feet: for every start-chunk key in the reference set it looks the
start up at the centre of that chunk at *your* Y, and requires your Y to be inside the start's overall
bounding box. Horizontally it is still the chunk test.

### Cave, house or garden

Three heuristics decide whether a position is a plausible haunted spot. The two material tests count
blocks in a box around it (`material_scan_horizontal_radius` 2, `material_scan_vertical_radius` 1 — a
5 × 3 × 5 box, 75 samples per pass); the roof test reads no box at all and walks a single column
upwards.

| Test | What it counts | Passes when |
|---|---|---|
| Structure material | `structure_material_blocks` **plus** the tags `LOGS`, `PLANKS`, `WOODEN_STAIRS`, `WOODEN_SLABS`, `FENCES`, `FENCE_GATES` | ≥ `structure_material_threshold` (4) |
| Cave material | `underground_material_blocks` (stone, deepslate, cobbled deepslate, tuff and the andesite/diorite/granite trio) | ≥ `cave_material_threshold` (8) |
| Roof | first non-air block with a sturdy downward face, 1–6 blocks overhead | found |

The six block tags are added unconditionally, so emptying `structure_material_blocks` does not switch
wood off.

`isLikelyUndergroundCave` puts them in order: a structure-material hit always wins, then a cave-material
hit, and failing both it falls back to depth — deeper than `cave_depth_tolerance` (8 under the default
preset) below `max(MOTION_BLOCKING_NO_LEAVES, WORLD_SURFACE)` counts as a cave. A cellar dressed in
stone bricks therefore stays part of the house; a natural cavern under the villa does not.

`isNearStructureGarden` is the same structure count with a wider net: radius `sky_access_near_building_radius`
(7) horizontally, 2 vertically — a 15 × 5 × 15 box, 1125 samples — against **twice** the threshold, so 8
hits. That is the "there is a building next to me" test used for courtyards.

Put together, `isBlockedHauntedSpawnLocation` reads:

| Position | Result |
|---|---|
| Looks like a cave | blocked |
| No structure materials, no roof, no building nearby | blocked |
| Not under open sky (`canSeeSky` false) | allowed |
| Open sky, no building nearby | blocked |
| Open sky next to a building | allowed with probability `sky_access_around_building_chance` (6 %) |

The 6 % is what keeps the villa's garden from turning into a spawner while still letting the
occasional figure stand in the courtyard.

### The three ways something spawns

| | Trigger | Runs at | What it produces |
|---|---|---|---|
| **Witch boost** | `FinalizeSpawnEvent` for a **non-witch** | `HIGHEST` | cancels the spawn, puts a witch there — or, with the witch's own replacement rate, the invisible replacement |
| **Replacement** | `FinalizeSpawnEvent` for a mob listed in `target_mobs` | `HIGH` | cancels the spawn, puts the invisible replacement there |
| **Direct area spawn** | the player tick, from the module's own cache | — | 85 % the invisible replacement, 15 % a plain, visible witch |

**Witch boost.** Only fires when `witch_spawn_boost_chance` is above zero, skips `minecraft:witch`
itself, and needs the spawn to be inside a target structure at an unblocked position with a usable
distributed position. Then it rolls: 78 % under the default preset. Of those boosted witches, the rate
configured for `minecraft:witch` in `target_mobs` (10 %) turns into the invisible replacement straight
away instead of a visible witch.

**Replacement.** `target_mobs` carries entries of the form `namespace:mob_id:percentage` —
`minecraft:witch:10` out of the box, and nothing else. An entry is only accepted with exactly three
colon-separated parts and a rate between 0 and 100. The handler runs the same structure, blocked-spot
and distribution checks, rolls against the rate, cancels the spawn and puts the replacement there.

**Direct area spawning** does not sit on an event at all. It rides the per-player tick, fires when
`tickCount % direct_spawn_interval_ticks == 0` (40 ticks, so every two seconds), rolls
`direct_spawn_attempt_chance` (35 %), and then draws up to `direct_spawn_candidate_samples` (10) picks
from the cached spots in the chunks around you. A pick has to survive re-validation, sit between 8 and
36 blocks away, and either have no sky access or stand next to a building. The first one that does gets
a mob, and the attempt ends — at most one spawn every two seconds per player.

This path bypasses the vanilla spawn pipeline completely: no mob cap, no light level, no difficulty
check, and no `FinalizeSpawnEvent` for other mods to see. Note also that the 15 % branch calls
`spawnWitch`, which does **not** apply invisibility — only `spawnReplacementEntity` does.

### Picking the actual block

Both event paths refuse to spawn where the game wanted to and look for somewhere better, so that a
villa does not end up with three figures in one corner. `findDistributedSpawnPos` makes
`distribution_attempts` (16) tries with a radius that grows from `distribution_radius` (8) by one block
per attempt up to a hard ceiling of three times the base — 8 to 23 blocks. Each try:

1. random X/Z offset, then `alignToWalkablePosition` — from two blocks above the original Y down to two
   below, the first spot with two blocks of air over a sturdy face;
2. still inside a target-structure chunk, before and after the alignment;
3. not a blocked haunted spot;
4. no living non-player entity within `min_distance_to_other_mobs` (8) — a 17 × 17 × 17 box.

The first candidate that passes all four wins. A candidate that fails only on step 4 is remembered, and
if all 16 attempts fail that crowded fallback is used after all. If even that is empty, nothing spawns
and the original mob is left alone.

### Being seen

The replacement carries `MobEffectInstance(INVISIBILITY, Integer.MAX_VALUE, 0, false, false)` — not
ambient, not visible, so no particles and no effect icon — and its UUID goes into a set of entities
still waiting to be spotted.

The check runs on the entity's own tick, every ten ticks, and only for entities in that set. It looks
for non-spectator players within 32 blocks; with none nearby it backs off for 40 ticks instead of 10.
For each player in range:

```java
Vec3 toReplacementEntity = replacementEntityPos.subtract(playerEyePos).normalize();
double dotProduct = playerLookVec.dot(toReplacementEntity);
if (dotProduct < 0.95) {
    continue;
}
if (!player.hasLineOfSight(replacementEntity)) {
    continue;
}
```

Eye position to eye position, a dot product of 0.95 — about 18.2° off your crosshair — and an
unobstructed line. Aiming near it is not enough; neither is standing next to it with your back turned.
Once it trips, the effect is removed and the UUID leaves the set. There is no way back: nothing in the
module ever re-applies invisibility to an entity that has been seen.

### The dark

The fog handler runs once a second per player (`tickCount % 20`), server-side. For **creative and
spectator players** the structure detection is skipped, so they always read as being outside: no fog
build-up, no cache refresh, no direct spawns. The handler itself keeps running and drops them into its
leave branch, which drains whatever fog trail they still carry. Testing the module in creative therefore
looks exactly like a broken module.

Inside, the zone is read from the module's own spot cache: every cached spot within
`fog_cache_proximity_radius` (3 blocks) votes, sky-access spots for *garden*, the rest for *indoor*, and
indoor wins ties. With no cached spots nearby it falls back to `canSeeSky`.

| Zone | Trail | Darkness duration |
|---|---|---|
| Indoor | +50 per second, capped at `fog_trail_max_ticks` (160) | `fog_indoor_base_duration_ticks` (120) + trail |
| Garden | −`fog_trail_decay_ticks` (20) per second, floor 0 | `fog_garden_base_duration_ticks` (50) + trail |
| Outside | −20 per second | `min(40, trail)` |

So a first step into a room gives 170 ticks of Darkness, and each further second 220, 270 and then a
steady 280 — roughly fourteen seconds' worth, re-applied every second. It is applied with
`ambient = false, visible = false`, so there is no icon in the HUD telling you where it comes from.

Leaving is gradual rather than abrupt, and mostly by accident: vanilla only lets a re-applied effect
*extend* an existing one (`MobEffectInstance.update` keeps the longer duration at equal amplifier), so
the 40-tick top-up outside is swallowed while the last indoor instance is still running. What you see
is the tail of that instance — up to fourteen seconds of darkness walking out of the door with you —
while the trail counter drains at 20 ticks per second underneath. The top-up only bites when you step
out of a short garden instance.

### The spot cache

Everything the direct spawning and the fog zones need comes from one cache per dimension, built from
where players walk. Once a second, for a player inside the villa, `updateCacheFromPlayerMovement` looks
at the step since the last pass. Standing still or moving one block scans around the current position;
a longer jump is interpolated into at most `movement_interpolation_max_steps` (10) steps — eleven
sample positions, both ends included — each of which can trigger its own scan, so a teleport costs up to
eleven scans in one tick.

A scan walks a grid of `area_scan_radius` (8) at `cache_scan_step` (3) — six offsets per axis — over five
Y-levels (−2 to +2): 180 candidate positions. Known spots have their expiry pushed out; new ones are
validated (`isBlockedDirectSpawnSpot` plus the nearby-mob test) and stored with a flag for whether they
see sky. Validations are themselves cached for `direct_spot_validation_interval_ticks` (60), which is
what keeps the 75- and 1125-block material scans from running on every candidate every second.

Entries live for `cache_ttl_seconds` (180) and the per-dimension cache is capped at
`max_cached_spawn_spots_per_level` (600); above that the entries closest to expiry are evicted first.

### Spawn presets

`spawn_preset` decides seven of the tuning values, and **only `custom` reads what you wrote in the
config file**. Every other preset hardcodes them in the getter, so on a default install the effective
witch boost is 78 %, not the 50.0 sitting in the file. An unrecognised value is not swallowed silently:
the validator logs *Invalid spawn preset '…'. Expected: custom, balanced, structure_focused, courtyard*
and the key is corrected back to its default, `structure_focused`; the getter falls back to the same
preset if it ever sees one anyway.

| Key | `custom` | `balanced` | `structure_focused` (default) | `courtyard` |
|---|---|---|---|---|
| `witch_spawn_boost_chance` | your value | 55 % | **78 %** | 60 % |
| `cave_depth_tolerance` | your value | 6 | **8** | 5 |
| `sky_access_near_building_radius` | your value | 5 | **7** | 8 |
| `sky_access_around_building_chance` | your value | 15 % | **6 %** | 28 % |
| `distribution_attempts` | your value | 8 | **16** | 12 |
| `distribution_radius` | your value | 4 | **8** | 6 |
| `min_distance_to_other_mobs` | your value | 6 | **8** | 6 |

Read along the rows: `balanced` repeats the config file's own defaults apart from the boost (55 %
against the file's 50), `structure_focused` pulls everything indoors and spreads the spawns wider, and
`courtyard` opens the garden up — 28 % instead of 6 %, and a building counted from 8 blocks away
instead of 7 — while being stricter about depth.

<!-- vpa:config:start -->
## Configuration

Section `[modules.haunted_house]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_haunted_house-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `area_scan_radius` | int | `8` | 2 ~ 48 | Horizontal scan radius around players used to discover and cache indoor/garden spawn spots (scanned 5 Y-levels deep, dy -2..+2). |
| `cache_query_chunk_radius` | int | `1` | 0 ~ 4 | Chunk radius used when querying cached spots around players. |
| `cache_refresh_interval_ticks` | int | `20` | 1 ~ 400 | Minimum ticks between movement-driven cache refresh passes per player. Values below 20 change nothing because the player tick handler itself is throttled to every 20 ticks. |
| `cache_scan_step` | int | `3` | 1 ~ 8 | Step size for cache area scans; higher means less CPU and less precision. |
| `cache_ttl_seconds` | int | `180` | 30 ~ 1800 | How long cached spawn spots stay valid before being dropped. |
| `cave_depth_tolerance` | int | `6` | 0 ~ 64 | How far below the terrain surface (max of MOTION_BLOCKING_NO_LEAVES and WORLD_SURFACE) a spawn is treated as cave-like and blocked. IGNORED unless spawn_preset = custom (default preset forces 8). |
| `cave_material_threshold` | int | `8` | 1 ~ 128 | Minimum number of cave-like blocks near a position to treat it as a likely cave. A structure-material hit at or above structure_material_threshold always wins over this. |
| `direct_spawn_attempt_chance` | double | `35.0` | 0.0 ~ 100.0 | Chance per interval to attempt a direct haunted spawn for a player standing in a haunted area. |
| `direct_spawn_candidate_samples` | int | `10` | 1 ~ 64 | How many cached spawn spots are sampled when picking a direct haunted spawn (at most one spawn per attempt). |
| `direct_spawn_interval_ticks` | int | `40` | 20 ~ 1200 | Tick interval between direct spawn attempts (20 ticks = 1 second). Values below 20 are clamped to 20 (`Math.max(20, ...)`, HauntedHouseModule.java:928). Two gates intersect rather than cancel: the player tick handler runs only on multiples of 20 (:1213) and the attempt additionally requires `tickCount % interval == 0` (:929), so attempts land on the multiples of lcm(20, interval) - with 30 that is every 60 ticks. No value silently stops the spawner. |
| `direct_spawn_max_player_distance` | int | `36` | 4 ~ 128 | Maximum distance to the player for direct haunted spawns (forced to at least min + 2). |
| `direct_spawn_min_player_distance` | int | `8` | 0 ~ 64 | Minimum distance to the player for direct haunted spawns. |
| `direct_spawn_replacement_chance` | double | `85.0` | 0.0 ~ 100.0 | Chance that a direct haunted spawn becomes the configured replacement entity (invisible) instead of a plain, fully visible witch. |
| `direct_spot_validation_interval_ticks` | int | `60` | 1 ~ 1200 | Ticks a direct-spot validation is reused before the expensive cave/material/mob checks run again. |
| `distribution_attempts` | int | `8` | 1 ~ 64 | How many attempts are made to spread haunted spawns away from clusters. IGNORED unless spawn_preset = custom (default preset forces 16). |
| `distribution_radius` | int | `4` | 0 ~ 24 | Horizontal offset radius in blocks used when distributing haunted spawns (grows adaptively up to 3x over the attempts). IGNORED unless spawn_preset = custom (default preset forces 8). Note the code clamps it to at least 1, so 0 behaves like 1. |
| `enable_direct_area_spawning` | boolean | `true` | — | Enable direct haunted spawns from cached indoor/garden areas, independent of the vanilla spawn generator. Only runs while enable_fog_effect is also true. |
| `enable_fog_effect` | boolean | `true` | — | Enable the fog (Darkness) effect for players inside target structures. Careful: this also gates the whole per-player tick pipeline - setting it to false disables the spawn-spot cache refresh AND direct area spawning too (HauntedHouseModule.java:1218 returns before both). |
| `fog_cache_proximity_radius` | int | `3` | 1 ~ 16 | Radius used to inspect cached spots around the player for indoor-vs-garden fog zone detection. |
| `fog_effect_amplifier` | int | `0` | 0 ~ 5 | Amplifier for the Darkness effect (0 = light fog, 1 = medium, 2+ = heavy). |
| `fog_garden_base_duration_ticks` | int | `50` | 10 ~ 600 | Base Darkness duration in ticks for garden/open haunted zones. |
| `fog_indoor_base_duration_ticks` | int | `120` | 20 ~ 1200 | Base Darkness duration in ticks for indoor haunted zones. |
| `fog_trail_decay_ticks` | int | `20` | 1 ~ 1200 | How many trail ticks are lost per update while the player is outside an indoor zone. |
| `fog_trail_max_ticks` | int | `160` | 20 ~ 2400 | Maximum lingering fog trail duration in ticks built up by indoor exposure (+50 per indoor second). |
| `material_scan_horizontal_radius` | int | `2` | 1 ~ 8 | Horizontal scan radius for the block-material based cave/structure detection. |
| `material_scan_vertical_radius` | int | `1` | 0 ~ 4 | Vertical scan radius for the block-material based cave/structure detection. |
| `max_cached_spawn_spots_per_level` | int | `600` | 100 ~ 10000 | Maximum number of cached haunted spawn spots kept per dimension; the oldest-expiring entries are evicted above this. |
| `min_distance_to_other_mobs` | int | `6` | 0 ~ 64 | Minimum distance to other living mobs when selecting a distributed spawn position. IGNORED unless spawn_preset = custom (default preset forces 8). |
| `movement_interpolation_max_steps` | int | `10` | 1 ~ 64 | Maximum interpolation steps for movement-based cache updates (clamps cost on teleports and lag spikes). Each step can trigger its own full area scan, so this multiplies the scan cost. |
| `replacement_entity_id` | string | `"minecraft:witch"` | — | Entity ID spawned as invisible replacement in target structures (e.g. 'alexsmobs:murmur' or 'minecraft:witch'). Only LivingEntity types get the invisibility effect; anything else spawns visible and logs a warning. |
| `sky_access_around_building_chance` | double | `15.0` | 0.0 ~ 100.0 | Chance to allow haunted spawns in open-sky spots next to a covered area. IGNORED unless spawn_preset = custom (default preset forces 6%). |
| `sky_access_near_building_radius` | int | `5` | 1 ~ 24 | Horizontal search radius in blocks used by isNearStructureGarden() to detect nearby covered building areas (vertical radius is hardcoded 2, threshold is structure_material_threshold * 2). IGNORED unless spawn_preset = custom (default preset forces 7). |
| `spawn_preset` | string | `"structure_focused"` | custom \| balanced \| structure_focused \| courtyard | Spawn tuning preset for haunted structures. Only 'custom' makes the seven tuning values below take effect; every other preset hardcodes them (see notes). An unparsable value falls back to structure_focused. |
| `structure_material_blocks` | list | `List.of("minecraft:cobblestone_stairs", "minecraft:cobblestone_slab", "minecraft:mossy_cobblestone_stairs", "minecraft:mossy_cobblestone_slab", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab", "minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_stairs", "minecraft:mossy_stone_brick_slab", "minecraft:mossy_stone_brick_wall", "minecraft:grass_block", "minecraft:dirt_path", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:moss_block")` | — | Explicit structure material blocks used for house/garden detection. On top of this list the code always counts the block tags LOGS, PLANKS, WOODEN_STAIRS, WOODEN_SLABS, FENCES and FENCE_GATES (HauntedHouseModule.java:669-677), so removing entries here cannot switch wood off. Entries are validated against BuiltInRegistries.BLOCK and unknown ids are rejected. |
| `structure_material_threshold` | int | `4` | 1 ~ 128 | Minimum number of structure-like blocks near a position to treat it as a house/garden area. |
| `target_mobs` | list | `List.of("minecraft:witch:10")` | — | List of mobs to replace with the replacement entity in the format 'namespace:mob_id:replacement_rate' (e.g. 'minecraft:witch:10' = 10% of witches get replaced). Entries with a rate outside 0-100 or without exactly three colon-separated parts are rejected by the validator. |
| `target_structures` | list | `List.of("nova_structures:witch_villa", "dungeons_and_taverns:witch_villa")` | — | List of structure IDs where mob replacements should occur. Matching is a substring test, not equality (see notes). |
| `underground_material_blocks` | list | `List.of("minecraft:stone", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff")` | — | Explicit underground/cave material blocks used for cave detection. Validated against BuiltInRegistries.BLOCK. |
| `witch_spawn_boost_chance` | double | `50.0` | 0.0 ~ 100.0 | Chance that a non-witch mob spawn in target structures is replaced with a witch, so more witches exist to be replaced; 0 disables the boost. IGNORED unless spawn_preset = custom (default preset structure_focused forces 78%). |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| **Invisibility survives a restart, the bookkeeping does not** | The effect is ordinary entity NBT; the set of entities waiting to be spotted is a plain `HashSet` on the module instance. After a server restart every replacement that had not yet been seen is **permanently invisible** — it is no longer in the set, so looking at it does nothing. Nothing in the module cleans that up. |
| Dungeons and Taverns missing | `shouldInitialize()` returns false, nothing is registered on the event bus, and the module is inert. It logs a warning at startup. The standalone `vpa_haunted_house.jar` declares no hard dependency on the mod, so it loads either way — it just does nothing. |
| Alex's Mobs | Not integrated in code. `ModList` is only ever asked about `mr_dungeons_andtaverns`. Murmurs work as `replacement_entity_id` because they are an entity id, like any other. |
| `nova_structures:witch_villa` | Ships as the first default target, but nothing checks whether that namespace exists. Without the mod the entry simply never matches. |
| Mod id vs namespace | The required **mod** is `mr_dungeons_andtaverns`; its **structures** live under `dungeons_and_taverns`. Both spellings are correct in their own place. |
| `replacement_entity_id` unknown | `BuiltInRegistries.ENTITY_TYPE.getOptional()` comes back empty, the module logs *Failed to find configured replacement entity type* — and the spot stays empty, because the original spawn was already cancelled. |
| `replacement_entity_id` not a `LivingEntity` | Spawns fully visible and logs a warning; invisibility cannot be applied. |
| `enable_fog_effect = false` | Turns off far more than the fog. The player tick returns before the cache refresh **and** before direct area spawning, so the spot cache is never filled and direct spawns stop with it. |
| Creative and spectator | Structure detection is skipped for them, so no fog, no cache refresh and no direct spawns. Testing in creative looks exactly like a broken module. |
| Non-witch entries in `target_mobs` | `setSpawnCancelled` does not cancel the event — it only calls `getEntity().setSpawnCancelled(cancel)` — so the `HIGH` handler still runs after the `HIGHEST` one and does not test `isSpawnCancelled()`. With the shipped defaults this cannot bite, because the boost skips witches and witches are the only target. Add e.g. `minecraft:zombie:50` and one cancelled zombie can yield a boosted witch **and** a replacement entity. |
| `direct_spawn_interval_ticks` not a multiple of 20 | Stretched, not disabled. The two throttles intersect: the player tick handler runs at `tickCount % 20 == 0` and the attempt additionally demands `tickCount % interval == 0`, so attempts land on the multiples of `lcm(20, interval)` — 30 fires every 60 ticks, 50 every 100, 21 every 420. No value in the 20–1200 range ever stops firing altogether. The same 20-tick throttle does make `cache_refresh_interval_ticks` below 20 meaningless. |
| `debug_logging` on a live server | `MessageBroadcaster` pushes a grey italic `[DEBUG]` chat line to **every player on the server**, and *Step 1: Detected mob spawn* fires for every mob spawn in the world, not only in the villa. Leave it off outside a test world. |
| `/hauntedhouse whereami` | Registered by the bundle only, and with no permission gate — any player can run it. It does not exist in the standalone jar. |
| Untracked entities | The pending set is pruned only when the entity itself ticks. An entity that unloads while still invisible leaves its UUID behind for the rest of the server's uptime, as do the per-player fog and position maps of players who log out. Bounded and harmless, but never zero. |

## Under the hood

| Class | Role |
|---|---|
| `modules/haunted_house/HauntedHouseModule` | all four handlers, the spot cache, the heuristics — 1368 lines |
| `modules/haunted_house/config/HauntedHouseConfig` | 39 keys, the four presets, the validators |
| `standalone/haunted_house/HauntedHouseStandalone` | `@Mod("vpa_haunted_house")` entry point |
| `VanillaPlusAdditions` (bundle class) | registers `/hauntedhouse whereami` |

No mixins, no items, no blocks, no recipes, no data files, no lang keys — every string the module shows
is an English literal in the Java source.

**Events.** All four handlers are registered on the NeoForge game bus from `onInitialize()` via
`NeoForge.EVENT_BUS.register(this)`, which is only reached when `shouldInitialize()` found Dungeons and
Taverns. Each one re-checks `isModuleEnabled()` first, so the module can be switched on and off at
runtime.

| Handler | Event | Priority |
|---|---|---|
| `onBoostWitchSpawns` | `FinalizeSpawnEvent` | `HIGHEST` |
| `onShouldSpawnHauntedEntity` | `FinalizeSpawnEvent` | `HIGH` |
| `onEntityTick` | `EntityTickEvent.Pre` | default |
| `onPlayerTick` | `EntityTickEvent.Pre` | default |

**Two handlers on every entity tick.** `EntityTickEvent.Pre` fires for every entity in the world, and
both handlers are on it. `onEntityTick` bails after a `tickCount % 10`, an `instanceof` and a `HashSet`
lookup; `onPlayerTick` after an `instanceof` and a `tickCount % 20`. The real cost is not there but in
the block scans behind them: `countNearbyMatchingBlocks` is a triple loop, the garden test alone reads
1125 block states, and a cache refresh pass validates up to 180 candidate positions. The validation
cache and the 20-tick throttle are what keep that to roughly one pass per second per player standing
inside a villa.

**State.** Everything lives in plain `HashMap`s and `HashSet`s on the module instance: the pending
reveal set and its next-check ticks, the players currently in a structure, their last positions,
their last refresh tick, their fog trail, and three maps of per-dimension caches. None of it is
written to disk, and none of it survives a restart.

**Command.** `/hauntedhouse whereami` reports `getPlayerLocationState()`, which is the fog logic
without the fog:

| Reply | Meaning |
|---|---|
| `inside` | in a target-structure chunk, not a blocked fog area, and either roofed or without sky access |
| `outside (inside structure area)` | in a target-structure chunk, but the position reads as cave, open ground or courtyard |
| `outside structure` | no target structure referenced by this chunk |

**Testing.** This repository has no unit tests. The test notes in
[docs/guides/testing.md](../guides/testing.md) ask for a manual pass: find or place a Witch Villa,
check that witches spawn, that the replacement is invisible until looked at, and that the fog appears
indoors.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Debug Logging](../guides/debug-logging.md) — and why you do not want it on here
* [Module System](../guides/module-system.md) — `enabled`, runtime overrides and `/vpa module`
* [All modules](../../README.md#-modules)
