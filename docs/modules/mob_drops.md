# Mob Drops

> **TL;DR** — One line of config per rule gives any mob an extra item when it dies, on top of its
> normal loot: by default a wither skeleton drops its own skull 12.5 % of the time, and a warden
> always leaves 1–3 enchanted golden apples.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `mob_drops` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_mob_drops.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_mob_drops.jar) · also needs `vpa_core` |
| **Config section** | `[modules.mob_drops]` |
| **Since** | the next release |
<!-- vpa:meta:end -->

## What it does

A rule is one line, three fields or four:

```
mob_id;item_id;chance[;max_drops]
```

| Field | Meaning |
|---|---|
| `mob_id` | Entity type id — `minecraft:wither_skeleton`. A bare `wither_skeleton` also works; `ResourceLocation.parse` fills in the `minecraft` namespace when there is no colon. |
| `item_id` | The item to drop — `minecraft:netherite_scrap`. |
| `chance` | Probability per death, `0.0`–`1.0`. `1` means every time. |
| `max_drops` | Optional. The count is rolled evenly between 1 and this number; left out, it is 1. |

Four rules ship as the default list:

| Mob | Item | Chance | Count |
|---|---|---|---|
| Wither Skeleton | Wither Skeleton Skull | 12.5 % | 1 |
| Wither Skeleton | Golden Apple | 40 % | 1 |
| Wither Skeleton | Netherite Scrap | 10 % | 1 |
| Warden | Enchanted Golden Apple | every time | 1–3 |

Nothing is replaced. The mob's own loot table has already run by the time these rules are rolled and
the extra items join the same pile, so a wither skeleton can still produce vanilla's own skull as
well as ours. A mob may carry as many rules as you like and each one is rolled separately.

Edit the list, save the file, and the next mob that dies uses the new rules — the cache is rebuilt on
every config load. No restart.

## In detail

### Rolling a drop

The whole of the runtime is one `LivingDropsEvent` handler. For each rule registered against the
dying mob's exact entity type:

```java
if (entity.getRandom().nextFloat() < dropInfo.chance) {
    int amount = dropInfo.maxDrops > 1
            ? entity.getRandom().nextInt(dropInfo.maxDrops) + 1
            : 1;
```

* `nextFloat()` returns `[0, 1)`, so `chance = 1` always fires and `chance = 0` never does. A rule
  set to `0` is a silent off switch, not an error — nothing is logged.
* The count is uniform over 1…`max_drops`, not a fixed stack. The warden default `…;1;3` therefore
  reads "always, one to three apples", averaging two.
* `DropInfo`'s constructor clamps with `Math.max(1, maxDrops)`, so `0` or a negative value degrades
  to a single item.
* The randomness is the dying mob's own `RandomSource`. Nothing here goes through a loot table's
  random sequence, so none of it is reproducible from a world seed.

The item entity is then built directly rather than through `Entity.spawnAtLocation`:

```java
event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
        entity.level(), entity.getX(), entity.getY(), entity.getZ(),
        new ItemStack(dropInfo.item, amount)));
```

Position and scatter match a vanilla loot drop exactly: `dropFromLootTable` hands its items to
`this::spawnAtLocation`, which uses an offset of `0.0F`, and the five-argument `ItemEntity`
constructor supplies the same ±0.1 horizontal, 0.2 upward nudge either way. One thing differs.
`spawnAtLocation` calls `setDefaultPickUpDelay()` — 10 ticks — and this does not, so these items can
be collected half a second before the rest of the loot lands.

### What the roll ignores

| | |
|---|---|
| Looting | No enchantment is read anywhere. A Looting III sword changes the vanilla table and leaves these rules alone. |
| The killer | The damage source is never inspected. A creeper, a cactus, drowning and `/kill` all drop the same — `LivingEntity.kill()` routes through `hurt(genericKill, MAX_VALUE)`, so a command kill is an ordinary death here. |
| `doMobLoot` | See below: the rules fire even with the gamerule off. |
| Baby mobs | `LivingEntity.shouldDropLoot()` is `!this.isBaby()` and guards the loot table only. A calf drops nothing of its own and still drops whatever rule names `minecraft:cow`. (`Monster` overrides it to `true`, so hostile babies were never affected either way.) |
| Subtypes | A rule matches one exact entity type. `minecraft:zombie` is not a husk, not a drowned, not a zombie villager, and there is no tag syntax. |

The gamerule case is worth showing, because it is the same line that decides several of the others.
In the patched `LivingEntity.dropAllDeathLoot` the event is fired **after** the gamerule block:

```java
if (this.shouldDropLoot() && p_level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
    this.dropFromLootTable(damageSource, flag);
    this.dropCustomDeathLoot(p_level, damageSource, flag);
}

this.dropEquipment();
this.dropExperience(damageSource.getEntity());

Collection<ItemEntity> drops = captureDrops(null);
if (!net.neoforged.neoforge.common.CommonHooks.onLivingDrops(this, damageSource, drops, lastHurtByPlayerTime > 0))
    drops.forEach(e -> level().addFreshEntity(e));
```

With `doMobLoot false` a mob drops nothing of its own and still drops every rule that names it. The
last two lines also say what happens when another mod cancels the event: *nothing* is spawned, ours
included.

A `minecraft:player` rule works as well. `ServerPlayer.die` calls `dropAllDeathLoot` for any
non-spectator, and it does so exactly once — it overrides `die` outright rather than calling
`super.die`, so the event does not fire twice for a player.

### A typo names a pig

```java
EntityType<?> mobType = BuiltInRegistries.ENTITY_TYPE.get(mobRl);
Item item = BuiltInRegistries.ITEM.get(itemRl);

if (mobType == null) {                       // ← unreachable
```

`ENTITY_TYPE` is a `DefaultedRegistry`, and its default is the pig:

```java
public static final DefaultedRegistry<EntityType<?>> ENTITY_TYPE = registerDefaultedWithIntrusiveHolders(
    Registries.ENTITY_TYPE, "pig", p_259175_ -> EntityType.PIG
);
```

`DefaultedMappedRegistry.get(ResourceLocation)` is `@Nonnull` and hands back `defaultValue` instead
of null. The null branch can never be taken, its "Mob not found" warning can never print, and
`minecraft:wither_skelton` quietly attaches its rule to `minecraft:pig`. If a rule stops firing, or
pigs start dropping something odd, that is the first thing to check.

The item half is right by accident. `BuiltInRegistries.ITEM` is defaulted too — to `air` — and the
code happens to test `item == Items.AIR` rather than `null`, so a misspelt item id really is caught
and skipped. The price is that a deliberate `minecraft:air` drop is discarded as though it were a
typo. The sibling `mob_glow` module does the check properly, with
`BuiltInRegistries.ENTITY_TYPE.containsKey(id)` before `get(id)`.

### Two different ideas of a valid rule

Every line passes two separate checks, and they do not agree.

The **spec validator** in `MobDropsConfig` runs when NeoForge reads the file, and rejects an entry
with fewer than 3 or more than 4 `;`-parts, a `chance` that is not a number or falls outside
`0.0`–`1.0`, or a `max_drops` that is not an integer. Rejection is not cosmetic: NeoForge's
`defineList` correction **removes the offending entries from the list**, and if that leaves the list
empty it restores the whole four-line default instead. A single mistyped chance therefore disappears
from your config file rather than being ignored.

The **runtime parser** in `reloadMobDropsCache()` is looser on the very same line. It accepts
`parts.length >= 3`, ignores anything past the fourth field, applies no range check to `chance` at
all, and only warns about an unparsable `max_drops` before keeping 1. Neither path is ever fatal: a
line that cannot be parsed is logged and skipped, and the rest of the list still loads.

### The drops that went missing

The three wither-skeleton defaults are not new. They were `wither_skeleton`'s own until v0.12.0
(2026-05-10), where the changelog moved them here:

> WitherSkeletonModule: Zusätzliche Drop-Logik entfernt; Drop-Konfiguration in das neue
> `mob_drops`-Modul verschoben.

Same items, same numbers — the old `additional_drops` list read `minecraft:wither_skeleton_skull;0.125`,
`minecraft:golden_apple;0.4`, `minecraft:netherite_scrap;0.1` — and the handler here is the old one
generalised from `instanceof WitherSkeleton` to a lookup by entity type. The warden rule is the only
one that was genuinely new.

The move went half way. `WitherSkeletonModule` lost its `LivingDropsEvent` handler and is a Nether
spawn blocker today and nothing else, but `new MobDropsModule()` never reached `registerModules()`:
`git log --all -S"new MobDropsModule"` finds no commit that ever added it, and both `build.gradle`
and `AGENTS.md` carried a note saying the module was deliberately absent. An unregistered module is
never initialised, never subscribes to the event bus, and gets no config section at all, because
`ModulesConfig` builds the spec from the registered modules only.

So from v0.12.0 until this release, wither skeletons dropped no skull, no golden apple and no
netherite scrap beyond vanilla's own, and the warden rule never fired once. Registering the module
is what put them back.

One consequence for anyone upgrading: an existing `vanillaplusadditions-common.toml` has no
`[modules.mob_drops]` section. It gains one, carrying the four default rules, the first time the
server starts with a build that includes the module.

<!-- vpa:config:start -->
## Configuration

Section `[modules.mob_drops]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_mob_drops-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `mob_drops` | list | `MobDropsConfig.DEFAULT_MOB_DROPS (MobDropsConfig.java:20-25) = List.of("minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.125", "minecraft:wither_skeleton;minecraft:golden_apple;0.4", "minecraft:wither_skeleton;minecraft:netherite_scrap;0.1", "minecraft:warden;minecraft:enchanted_golden_apple;1;3")` | — | List of additional mob drops. Format: mob_id;item_id;chance[;max_drops] - Example: minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.5;2 |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Unknown or misspelt `mob_id` | Silently becomes `minecraft:pig`. No warning is possible — see above. |
| `minecraft:air` as `item_id` | Skipped, with the same message an unknown item gets, and only when debug logging is on. |
| One invalid entry | Deleted from the list by NeoForge's config correction. If every entry is invalid the four defaults come back. |
| `chance = 0.0` | Passes validation and never fires. Nothing is logged; it looks exactly like a rule that is not working. |
| Duplicate rules | Never merged or de-duplicated. The same mob and item listed twice is rolled twice, and both may drop. |
| `max_drops` above the item's stack size | Not clamped. `ItemStack(item, count)` takes the count as given, so a rule like `…;minecraft:totem_of_undying;1;5` produces one oversized stack. |
| Another mod cancelling `LivingDropsEvent` | Our drops vanish with everything else — the cancel is checked after every handler has run. |
| Modded mobs and items | Work, by id. The cache is rebuilt at common setup, which runs after the registries are populated, so a modded id resolves there even if the config was read earlier. |
| Module disabled at startup | `reloadMobDropsCache()` clears the cache and returns early, so re-enabling it with `/vpa module enable mob_drops` flips the per-drop gate but leaves the cache empty. Touch the config file (or restart) to rebuild it. The other direction — disabling a running module — takes effect immediately. |
| Nothing is shown in game | No lang keys, no commands, no chat feedback. The only visible effect is the item on the floor; everything else is in the log. |

## Under the hood

Two files, 254 lines, no mixins, no commands, no keybinds, no network packets, no items, blocks or
entities, and not a single data or resource file.

| Class | Role |
|---|---|
| `modules/mob_drops/MobDropsModule` | The cache, the parser and the `LivingDropsEvent` handler |
| `modules/mob_drops/config/MobDropsConfig` | The `mob_drops` list, its defaults and the spec validator |
| `standalone/mob_drops/MobDropsStandalone` | `@Mod("vpa_mob_drops")` entry point for the standalone jar |

`onInitialize` does one thing — `NeoForge.EVENT_BUS.register(this)`. Note that `AbstractModule`
gates that on `shouldInitialize()`, which this module leaves at the default `true` and which is not
tied to the config flag, so the handler is always subscribed. The real gate is the
`isModuleEnabled()` check at the top of `onEntityDrop`, which is also what makes
`/vpa module disable mob_drops` take effect on the next death.

**Cache lifecycle.** `reloadMobDropsCache()` runs from `onCommonSetup()` and from
`MobDropsConfig.onConfigLoad()`, which is wired to `ModConfigEvent` — loading *and* reloading. That
is why editing the file on a running server is enough. The cache itself is a
`Map<EntityType<?>, List<DropInfo>>`; the predecessor in `wither_skeleton` used a `Map<Item, Float>`,
which is where the one-chance-per-item behaviour came from, and the list is what allows the same item
to appear twice for one mob today.

**Logging.** With `debug_logging = ON` the module logs each rule it accepted with its chance and
maximum, the number of mob types in the finished cache, and every drop it awards with the item,
count, mob type and block position. Both registry-lookup warnings sit behind the same flag, so a
misspelt item id is invisible at default log levels; a malformed line, by contrast, always warns, and
an exception during parsing is always logged as an error.

**Side.** Server. `LivingDropsEvent` only ever fires from `LivingEntity.die` on a `ServerLevel`, and
the module references nothing client-side at all.

## See also

* [Wither Skeleton Enforcer](wither_skeleton.md) — where the three wither-skeleton rules came from
* [Mob Glow](mob_glow.md) — the sibling that validates an entity id the right way
* [Enhanced AI Leader Loot](enhanced_ai_leader_loot.md) — replacing another mod's loot table in code
* [Debug Logging](../guides/debug-logging.md) — how to switch the per-drop logging on
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
