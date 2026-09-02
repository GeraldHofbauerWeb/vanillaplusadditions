# Enhanced AI Leader Loot

## Overview

Enhanced AI (insane96mcp) has a "Leaders" module: random Zombie-/Skeleton-family mobs get
promoted to banner-carrying leaders. This module adds bonus loot — golden carrots, golden
apples, or a rare enchanted golden apple — when a leader dies, on top of its normal mob loot.

Inactive without Enhanced AI installed (`ModList.isLoaded("enhancedai")`).

## Where the data comes from

Enhanced AI ships no public sources. The mechanism below was read out of the decompiled bytecode
of `Leaders.class` (`javap -c` on `enhancedai-4.2.2.1.jar`):

- Which mobs can become leaders: `#enhancedai:overworld_zombies` (Zombie, Drowned, Giant, Husk,
  Zombie Villager) and `#enhancedai:overworld_skeletons` (Skeleton, Stray, Bogged).
- `Leaders.onDeath(LivingDeathEvent)` checks the dying mob's `EAIData LEADER` flag, builds a
  standard `LootParams` (Entity, Origin, Damage Source, Attacker, …) via
  `LootContextParamSets.ENTITY`, and rolls a **separate** loot table —
  `server.reloadableRegistries().getLootTable(LEADER_LOOT_TABLE)`,
  `LootTable.getRandomItems(...)` — **in addition to** the mob's own vanilla death loot, not
  instead of it.
- That table, `enhancedai:leader_mob` (`data/enhancedai/loot_table/leader_mob.json` inside the
  jar), ships as `{"type": "minecraft:empty"}` — deliberately empty, clearly meant as an
  extension point for exactly this kind of addition.

## How this module hooks it

Per CLAUDE.md, this mod never ships loot-table JSON (doesn't load reliably here) — everything
goes through code. `LootTableLoadEvent` (`net.neoforged.neoforge.event.LootTableLoadEvent`) fires
for every loot table on load/reload, vanilla and mod tables alike; when its name matches
`enhancedai:leader_mob`, `EnhancedAiLeaderLootModule` replaces it with a `LootTable` built from
the module config via `event.setTable(...)`. No Enhanced AI class is referenced directly — the
match is purely on the loot table's `ResourceLocation` — so this module needs **no compile-time
dependency** on Enhanced AI, only the runtime `ModList.isLoaded("enhancedai")` gate.

## Configuration

`leader_bonus_loot` — one weighted pool, one entry rolled per kill (weights are relative, not
required to sum to 100):

| Item | Weight | Count | Effective chance (default) |
|------|--------|-------|------------------------------|
| Golden Carrot | 50 | 6–12 | 50% |
| Golden Apple | 49 | 3–9 | 49% |
| Enchanted Golden Apple | 1 | 1 | 1% |

Format: `item_id;weight;min_count;max_count`. Invalid entries are skipped and logged, not fatal
(same convention as `custom_crafting_recipes`/`food_effects`).

## Implementation

- `EnhancedAiLeaderLootModule.java` — the `LootTableLoadEvent` handler and `LootTable` builder.
- `config/EnhancedAiLeaderLootConfig.java` — the `leader_bonus_loot` list + validation.
