# Enhanced AI Leader Loot

> **TL;DR** — Killing one of Enhanced AI's banner-carrying leader mobs now pays: a handful of golden
> carrots, a handful of golden apples, or — once in a hundred — an enchanted golden apple, on top of
> its normal loot.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `enhanced_ai_leader_loot` |
| **Side** | Server only |
| **Requires** | [Enhanced AI](https://modrinth.com/mod/enhanced-ai) <sub>tested 4.2.2.1</sub> |
| **Works with** | — |
| **Download** | bundle only — no standalone jar |
| **Config section** | `[modules.enhanced_ai_leader_loot]` |
| **Since** | `v1.0.0-beta.73` |
<!-- vpa:meta:end -->

## What it does

Enhanced AI's *Leaders* feature promotes random Zombie- and Skeleton-family mobs to banner-carrying
leaders. It also rolls a second, separate loot table for them when they die — and ships that table
empty, so that extra roll yields nothing at all. A leader is a harder fight for the same handful of
rotten flesh.

This module fills that table. Every leader kill yields **exactly one** of these, in addition to the
mob's ordinary death loot:

| Item | Weight | Count | Chance |
|---|---|---|---|
| Golden Carrot | 50 | 6–12 | 50 % |
| Golden Apple | 49 | 3–9 | 49 % |
| Enchanted Golden Apple | 1 | 1 | 1 % |

There is no "nothing" outcome — a leader is always worth walking over to. The whole table is one
config list, so the items, the weights and the counts are yours to rewrite.

The counts above are the current ones. `v1.0.0-beta.73` shipped 3–9 golden carrots and 1–3 golden
apples; `v1.0.0-beta.74` raised them to 6–12 and 3–9. The enchanted golden apple has always been a
single one at weight 1.

Without Enhanced AI installed the module is inert.

## In detail

### Where the hook is

Enhanced AI ships no public sources. The mechanism below was read out of the decompiled bytecode of
`Leaders.class` (`javap -c` on `enhancedai-4.2.2.1.jar`, the version this mod is played against) and
is therefore the one thing on this page that cannot be checked against this repository:

* Leaders are drawn from two tags — `#enhancedai:overworld_zombies` (Zombie, Drowned, Giant, Husk,
  Zombie Villager) and `#enhancedai:overworld_skeletons` (Skeleton, Stray, Bogged).
* `Leaders.onDeath(LivingDeathEvent)` checks the dying mob's `EAIData LEADER` flag, builds a standard
  `LootParams` (Entity, Origin, Damage Source, Attacker …) for `LootContextParamSets.ENTITY` and
  rolls a **separate** loot table through
  `server.reloadableRegistries().getLootTable(LEADER_LOOT_TABLE)` — *in addition to* the mob's own
  death loot, not instead of it.
* That table is `enhancedai:leader_mob` (`data/enhancedai/loot_table/leader_mob.json` inside the
  jar), and it ships as `{"type": "minecraft:empty"}` — deliberately empty, an extension point
  waiting for exactly this kind of addition.

### The table we put there

`buildLeaderLootTable()` assembles one pool with one roll and no conditions:

```java
LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));
```

Four consequences worth knowing:

* **Always exactly one entry.** The pool carries no `when(...)`, so it cannot decline to drop, and
  the roll count is constant. One leader, one bonus item stack.
* **Counts are uniform and inclusive.** `SetItemCountFunction.setCount(UniformGenerator.between(min,
  max))`, and `UniformGenerator.getInt` is `Mth.nextInt(random, min, max)` — with 6–12 each of the
  seven counts is equally likely, 9 on average.
* **Weights are relative, not percentages.** The defaults read as 50 / 49 / 1 % only because they
  happen to sum to 100. Add a fourth entry at weight 50 and the carrot is down to a third.
* **Luck and Looting do not move the odds.** An entry's effective weight is
  `floor(weight + quality × luck)` and nothing here ever calls `setQuality`, so quality stays 0. No
  `ApplyBonusCount` function is applied either, so a Looting sword changes the mob's normal drops
  and nothing about the bonus.

The table is built with `LootContextParamSets.ENTITY`, matching the param set Enhanced AI rolls it
with.

### Writing your own table

One line per entry, `item_id;weight;min_count;max_count`. The namespace may be dropped —
`ResourceLocation.parse` fills in `minecraft`, so `golden_carrot;50;6;12` is the same entry as the
default. Every field is required: `String.split(";")` discards trailing empty fields, so
`minecraft:golden_carrot;50;6;` parses as three parts and is rejected.

A line passes two gates, and they check different things:

| Gate | What it checks | What a failure does |
|---|---|---|
| The config validator, `EnhancedAiLeaderLootConfig` | exactly four `;`-separated parts, a parsable id, `weight ≥ 1`, `min_count ≥ 1`, `max_count ≥ min_count` | NeoForge drops the line when it corrects the file against the spec — it disappears from your config file. If that leaves the list empty, the whole key reverts to the three defaults, because `defineList` demands a non-empty list. |
| The parser, `buildLeaderLootTable()` | that the item actually exists | The entry is skipped and the rest of the table is built as usual. With [debug logging](../guides/debug-logging.md) on you get `Leader bonus loot: item not found: …`; otherwise nothing is said. |

The second gate is the one that will bite, because the validator never asks the registry:

```java
Item item = BuiltInRegistries.ITEM.get(itemId);
if (item == Items.AIR) {
    if (getConfig().shouldDebugLog()) {
        getLogger().warn("Leader bonus loot: item not found: {}", parts[0]);
    }
    continue;
}
```

`BuiltInRegistries.ITEM.get` answers `minecraft:air` for an id it does not know, and the code reads
air as "not found". A typo, or an id belonging to a mod that is not installed, therefore sails
through validation and then quietly vanishes from the pool.

Two things follow from that sentinel:

* **`minecraft:air` can never be configured as a drop.** It is indistinguishable from a typo.
* **There is no way to express "no bonus this time".** The pool always rolls once, and air is ruled
  out, so the only switch that gives a leader nothing is `enabled = false`.

If *every* surviving entry names an item that does not exist, the pool ends up with no entries at
all. That is harmless rather than fatal: `LootPool.addRandomItem` guards the pick with
`if (mutableint.intValue() != 0 && i != 0)`, so the table produces nothing and throws nothing, and
leaders drop only their normal loot. (Read off the 1.21.1 code this mod builds against, not off this
repository.)

### When a change takes effect

The table is built inside the `LootTableLoadEvent` handler, and that event only fires when the
server loads or reloads its resources. Editing `leader_bonus_loot`, flipping `enabled` or running
`/vpa module disable enhanced_ai_leader_loot` therefore changes nothing on its own — the table
already in memory stays as it is until `/reload`, a world rejoin or a restart.

<!-- vpa:config:start -->
## Configuration

Section `[modules.enhanced_ai_leader_loot]` in `config/vanillaplusadditions-common.toml`.

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `leader_bonus_loot` | list | `DEFAULT_LEADER_BONUS_LOOT = ["minecraft:golden_carrot;50;6;12", "minecraft:golden_apple;49;3;9", "minecraft:enchanted_golden_apple;1;1;1"] (EnhancedAiLeaderLootConfig.java:14-18)` | no spec range; per-entry validator (EnhancedAiLeaderLootConfig.java:35-52) requires exactly 4 semicolon-separated parts, a parsable ResourceLocation, weight >= 1, min_count >= 1 and max_count >= min_count. It does NOT check that the item exists - an unknown id passes validation and is dropped later by the parser (EnhancedAiLeaderLootModule.java:79-85). | Bonus loot rolled in addition to the mob's normal death loot when an Enhanced AI "leader" mob dies. Format item_id;weight;min_count;max_count. It is one loot pool with exactly one roll, so precisely one entry is picked per leader kill, weighted; weights are relative and need not sum to 100 (the defaults happen to sum to 100, which is why the doc's 50/49/1 percentages read as literal chances). Count is a uniform range between min_count and max_count. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Enhanced AI not installed | `shouldInitialize()` is false, so `onInitialize()` never runs and the handler is never registered — the module is completely inert. It is still registered, still writes its config section and still appears in `/vpa module status`, and nothing warns you: `enhancedai` is not declared in `neoforge.mods.toml`, the gate is runtime only. |
| Leaders switched off in Enhanced AI's own config | No leaders, no rolls. This module supplies the contents of a table; it never decides who becomes a leader or when the table is rolled. |
| Enhanced AI renaming or dropping `enhancedai:leader_mob` | The match is a hardcoded `ResourceLocation` comparison, so the handler simply stops firing. No crash and no warning — the bonus loot would just be gone. |
| A datapack carrying its own `enhancedai/leader_mob.json` | Overwritten. `event.setTable(...)` replaces whatever was loaded under that name, datapack contents included. |
| Another mod handling the same event | Last handler on the bus wins. This one does not merge with an existing table, it replaces it. |
| Config changed at runtime | Takes effect at the next resource reload only — see above. |
| `max_count` above the item's stack size | Accepted. The validator only requires `max_count ≥ min_count`, and `SetItemCountFunction` sets the count outright instead of capping it at the stack limit. <!-- TODO: whether an oversize stack is then split into several depends on which getRandomItems overload Enhanced AI calls; not verified for 4.2.2.1. --> |
| Ordinary Enhanced AI mobs | Untouched. Only the leader table is replaced. |
| Standalone jars | There is none. This module ships in the bundle jar only. |

## Under the hood

Two files, 158 lines together. No mixin, no client code, no assets, no data files, no registry
entries, no command, no keybind and no lang key — nothing user-visible beyond the config comments.

| File | Role |
|---|---|
| `modules/enhanced_ai_leader_loot/EnhancedAiLeaderLootModule.java` | the mod gate, the event handler, the table builder |
| `modules/enhanced_ai_leader_loot/config/EnhancedAiLeaderLootConfig.java` | `leader_bonus_loot` and its validator |

`onInitialize()` does exactly one thing, `NeoForge.EVENT_BUS.register(this)`, and it is only reached
when `shouldInitialize()` finds `enhancedai` in the `ModList`. Everything else hangs off one
handler:

```java
@SubscribeEvent
public void onLootTableLoad(LootTableLoadEvent event) {
    if (!isModuleEnabled() || !event.getName().equals(LEADER_MOB_LOOT_TABLE)) {
        return;
    }
    event.setTable(buildLeaderLootTable());
}
```

**Server side, although the listener is registered on both.** `LootTableLoadEvent` is fired "on the
main Forge event bus, only on the logical server", per its own javadoc, and "whenever server
resources are loaded or reloaded".

**No compile-time dependency on Enhanced AI.** Not one of its classes is named anywhere in the
module; the only thing that crosses the boundary is the string `enhancedai:leader_mob` and the
`ModList` lookup. This is the fourth of the four recipe-and-loot cases in `CLAUDE.md` — a foreign
mod's own extension point, taken over from code rather than by shipping a loot-table JSON — and this
module is the template named there for it.

**Two dead safety nets, deliberately kept.** A line with the wrong number of parts is skipped
without a log, and a `catch (Exception)` around the parse logs at ERROR. Neither can be reached by a
value that came out of the config, because the spec's validator rejects such lines first. What they
still cover is a mistake in `DEFAULT_LEADER_BONUS_LOOT` itself: `getLeaderBonusLoot()` hands those
hardcoded strings out unvalidated whenever the config value does not exist yet. Worth remembering
when editing the defaults in code.

**Nothing is cached.** The table is rebuilt from the config on every resource reload, which is why a
`/reload` is all it takes to pick up an edit, and why nothing needs invalidating when the module is
toggled at runtime.

## See also

* [Mob Drops](mob_drops.md) — the other loot list, with one independent chance roll per rule
* [Custom Crafting Recipes](custom_crafting_recipes.md) — the same "content as a config list" idea, for recipes
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Debug Logging](../guides/debug-logging.md) — how to see the skipped-item warning
* [All modules](../../README.md#-modules)
