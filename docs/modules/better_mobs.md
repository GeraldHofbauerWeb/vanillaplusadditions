# Better Mobs

> **TL;DR** — Hostile mobs spawn kitted out: battle-worn armour, a permanent potion effect and, for
> skeletons, a sword or an axe in place of the bow — and the kit gets nastier below Y=0, nastiest of
> all in the Nether and the End.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `better_mobs` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_better_mobs.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_better_mobs.jar) · also needs `vpa_core` |
| **Config section** | `[modules.better_mobs]` |
| **Since** | `v0.2.0` |
<!-- vpa:meta:end -->

## What it does

Every hostile mob that joins a server level is rolled against one of three equipment lists, chosen
by where it is standing:

| Where it is | List |
|---|---|
| The Nether or the End, at any height | `nether_end` |
| Any other dimension, Y ≥ 0 | `above_zero` |
| Any other dimension, Y < 0 | `below_zero` |

The surface gives you leather, chainmail and the odd gold helmet. Deep caves give iron and diamond.
The Nether and the End give netherite or nothing at all.

Three things can land on a mob, and each is gated separately:

* **Armour** — only for the four mobs in `enabled_mobs_with_armor`: zombie, skeleton, husk and
  wither skeleton. Each of the four slots is rolled on its own, so half-kitted mobs are the norm.
* **A weapon** — only for mobs that have a `WEAPON_RANDOMIZER` entry, which by default is the
  **skeleton and nobody else**. A skeleton that draws one loses its bow and charges you with a sword
  or an axe instead.
* **A potion effect** — speed, strength or fire resistance, level I, for the rest of the mob's life.
  You can see them coming by the particle trail.

That leaves fifteen of the twenty mobs in the default `enabled_mobs` list — stray, drowned, spider,
cave spider, witch, pillager, vindicator, evoker, illusioner, blaze, guardian, elder guardian,
warden, piglin and zombified piglin — able to receive **nothing but potion effects**. A shulker in
that list receives nothing at all; see [Compatibility](#compatibility-and-known-limits).

What a mob can actually end up with, on the shipped defaults:

| Mob | Armour | Weapon | Effects |
|---|---|---|---|
| Skeleton | yes | sword or axe | yes |
| Zombie, husk, wither skeleton | yes | — | yes |
| The other fifteen | — | — | yes |
| Shulker | — | — | — |

## In detail

### The gear-type roll is the master switch

Before anything else, the mob draws a **gear type** — the material its armour will be made of. The
selection walks the `GEAR_TYPES` entries in list order and takes the first one whose own roll
succeeds:

```java
for (String[] gearType : gearTypes) {
    if (random.nextInt(100) < Integer.parseInt(gearType[2])) {
        equipment.put(BetterMobsConfigKey.GEAR_TYPES, List.of(gearType[1]));
        break;          // first hit wins
    }
}
```

These are **independent rolls, not a weighted table**. Three entries at 10, 10 and 20 do not add up
to 40 %; they miss together 64.8 % of the time. And when they all miss, the module gives up on that
mob entirely:

```java
var materials = setup.get(BetterMobsConfigKey.GEAR_TYPES);
if (materials.isEmpty()) {
    return;             // before the weapon block, before armour, before the effects
}
```

So the `GEAR_TYPES` percentages govern the whole module, not only the armour material. A mob that
fails them gets no weapon and no potion effect either.

| Zone | `GEAR_TYPES` entries | Outcome |
|---|---|---|
| `above_zero` | gold 10, chainmail 10, leather 20 | gold 10 %, chainmail 9 %, leather 16.2 % — **nothing 64.8 %** |
| `below_zero` | gold 10, iron 30, leather 10, diamond 30 | gold 10 %, iron 27 %, leather 6.3 %, diamond 17.0 % — **nothing 39.7 %** |
| `nether_end` | netherite 30 | netherite 30 % — **nothing 70 %** |

Order matters, because of the `break`: moving `leather;20` to the front of the `above_zero` list
would make leather the most common material by a wide margin and gold nearly extinct.

### Armour

Each slot is rolled separately against `ARMOR_CHANCES`, then built from the gear material. Slots
whose roll fails stay empty; the mob keeps whatever vanilla gave it.

| Slot | `above_zero` | `below_zero` / `nether_end` | Net chance above Y=0 | Net below Y=0 | Net in Nether/End |
|---|---|---|---|---|---|
| Helmet | 60 % | 80 % | 21 % | 48 % | 24 % |
| Chestplate | 50 % | 70 % | 18 % | 42 % | 21 % |
| Leggings | 45 % | 65 % | 16 % | 39 % | 20 % |
| Boots | 40 % | 60 % | 14 % | 36 % | 18 % |

The three right-hand columns fold in the gear-type roll above, which is the figure that matters in
play. Recognised armour materials are `gold`, `iron`, `chainmail`, `leather`, `diamond` and
`netherite`; anything else yields an empty stack and the piece is silently skipped.

### Weapons

A weapon is offered only to mobs named in a `WEAPON_RANDOMIZER` entry. The roll is **cumulative**,
not per-entry:

```java
int randVal = new Random(uuid.getLeastSignificantBits() ^ 0x5EED).nextInt(100);
for (String entry : weaponRandomizers) {
    if (parts.length == 3 && parts[0].equals(mobId)) {
        cumulativeChance += Integer.parseInt(parts[2]);
        if (randVal < cumulativeChance) { selectedWeapon = parts[1]; break; }
    }
}
```

`sword;35` followed by `axe;25` therefore means sword on [0,35), axe on [35,60), nothing on the
remaining 40 — the entries share one 0–99 roll rather than each getting their own.

| Zone | Sword | Axe | No weapon | Net sword / axe after the gear-type roll |
|---|---|---|---|---|
| `above_zero` | 35 % | 25 % | 40 % | 12 % / 9 % |
| `below_zero` | 40 % | 30 % | 30 % | 24 % / 18 % |
| `nether_end` | 45 % | 35 % | 20 % | 14 % / 11 % |

**Swords and axes use their own material.** `WEAPON_TYPES` is a genuinely weighted pick — the
weights are summed and normalised, so as long as the list is non-empty it always returns a material.
That is the opposite of `GEAR_TYPES`, and only the shipped weights summing to 100 in every zone
hides the difference: halve them all and the shares stay exactly the same.

| Zone | Weapon material |
|---|---|
| `above_zero` | wood 65 %, stone 35 % |
| `below_zero` | iron 50 %, gold 20 %, diamond 30 % |
| `nether_end` | gold 65 %, netherite 35 % |

So a Nether skeleton wears netherite and swings a golden sword about two thirds of the time. For
`sword` and `axe` the recognised materials are `gold`, `iron`, `wood`/`wooden`, `chainmail`,
`leather`, `stone`, `diamond` and `netherite`, where **`chainmail` and `leather` both map to the
wooden** sword or axe. `bow`, `crossbow` and `trident` are recognised too and ignore the material
entirely. If the weapon material produces nothing, the code retries once with the gear material
before giving up.

**A skeleton handed a sword stops being an archer.** Vanilla gives every skeleton a bow in
`populateDefaultEquipmentSlots`; setting the main hand replaces it, and the module then calls
`reassessWeaponGoal()`, which swaps the bow goal for the melee goal because the hand no longer holds
`Items.BOW`. That is the single most noticeable thing this module does.

### Enchantments

Two candidate levels are rolled once per mob from `ENCHANTMENT_LEVELS` (`min_level`–`max_level`:
1–3 above Y=0, 2–4 elsewhere). Every enchantment that lands then picks one of those two at random
and is clamped to what the enchantment actually supports:

```java
stack.enchant(optHolder.get(), MobArmorEnchantments.clampLevel(optHolder.get(), rolledLevel));
```

Without that clamp `ItemStack#enchant` writes whatever number it is given — nothing in vanilla
validates it — and the `max_level` of 4 produces Unbreaking IV, Thorns IV and Frost Walker IV.
`better_mobs` is the only caller of `clampLevel` in the repository.

An armour piece's enchantments are only rolled **if that piece spawned**. Weapon enchantments are
rolled unconditionally, so a mob that receives no weapon still burns those rolls; that shifts the
shared random stream but has no visible effect.

Two silent filters sit on top:

* 37 vanilla enchantment names are recognised by name. Anything else — a modded enchantment, a typo
  — is dropped without a warning.
* `Enchantment#canEnchant` and `EnchantmentHelper#isEnchantmentCompatible` are both checked. The
  default `WEAPON_ENCHANTMENTS` lists include `power`, `punch` and `flame`, which are bow
  enchantments; on a sword or an axe they are skipped, again without a warning.

### Potion effects

Each `POTION_EFFECTS` entry is rolled on its own, so a mob can pick up several. Then:

```java
final int level = new Random(uuid.getLeastSignificantBits()).nextInt(1, 2);
final int duration = Integer.MAX_VALUE;
```

`nextInt(1, 2)` has an exclusive upper bound, so the level is **always 1** and the amplifier always
0 — the randomness is dead, and the debug line that prints `(Level 1)` as though it varied is
printing a constant. The duration is `Integer.MAX_VALUE` ticks, about 3.4 years of game time, with
vanilla's default of visible particles: an affected mob trails particles until something kills it.

| Zone | Effects rolled | Net chance per effect |
|---|---|---|
| `above_zero` | speed 10, strength 5, fire_resistance 5 (plus `haste`, which does nothing) | 3.5 % / 1.8 % / 1.8 % |
| `below_zero` | speed 10, strength 10, fire_resistance 10 | 6.0 % each |
| `nether_end` | speed 20, strength 10, fire_resistance 10 | 6.0 % / 3.0 % / 3.0 % |

Thirteen effect names are recognised: `speed`, `strength`, `regeneration`, `fire_resistance`,
`invisibility`, `water_breathing`, `night_vision`, `jump_boost`, `weakness`, `slowness`,
`mining_fatigue`, `poison`, `wither`. **`haste` is not one of them**, although it appears in all
three shipped defaults — it falls into `case null, default -> {}` and does nothing at all.

### "Battle-worn" is real in the config and invisible in play

The armour durability is rolled four times, once per slot, from `ARMOR_DURABILITY`
(`min_percent`/`max_percent`, 5–20 % remaining in every shipped zone), clamped to 1–100 and applied
so a piece is never handed out already broken. The weapon takes its remaining durability from
`max_durability` instead — which despite its name and its config comment is not a maximum but the
remaining durability in percent, and is read **only** inside the weapon branch. The shipped default
of 100 means the weapon spawns pristine.

Neither number is observable in ordinary play, and this is worth knowing before tuning them:

* **Mobs never wear their gear down.** `LivingEntity#hurtArmor` and `#hurtHelmet` are empty bodies;
  only `Player` overrides them, and `Mob#doHurtTarget` never damages the held item. Armour value
  does not depend on damage either, so a helmet at 5 % protects exactly as well as a new one.
* **Vanilla re-rolls the damage when the piece drops.** In `Mob#dropCustomDeathLoot`:

  ```java
  if (!flag && itemstack.isDamageableItem()) {
      itemstack.setDamageValue(
          itemstack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemstack.getMaxDamage() - 3, 1)))
      );
  }
  ```

  `flag` is `dropChance > 1.0F`, and `drop_chance` is capped at 100, i.e. exactly `1.0F` — so `flag`
  is always false here and the branch always runs. Whatever you loot carries vanilla's own
  heavily-damaged roll, not the module's.

Read off vanilla's code, not reproduced in game.

### Drop chances

`drop_chance` is written per filled slot as `setDropChance(slot, value / 100f)`; slots the module
does not touch keep vanilla's `DEFAULT_EQUIPMENT_DROP_CHANCE` of `0.085F`. The shipped default of 10
is therefore barely a change from vanilla's 8.5 %. Vanilla still requires the mob to have been hurt
by a player recently before any of it drops.

### The handler re-runs, and that has consequences

`EntityJoinLevelEvent` is not only fired for a freshly spawned mob. NeoForge also fires it from
`PersistentEntitySectionManager#addEntity`, which is what `processPendingLoads` calls for every
entity read back off disk — the event even carries a `loadedFromDisk` flag, which this module does
not look at. Every chunk load therefore re-runs the whole block on mobs that were equipped long ago:

* **Position is read fresh.** The zone comes from `mob.blockPosition().getY()` at the moment of the
  event, not from where the mob first spawned. A zombie that spawns at Y=40 in leather and wanders
  down into a cave below Y=0 is re-equipped from `below_zero` after the next chunk reload — and can
  walk back up in diamond.
* **Picked-up gear is destroyed.** When a mob picks an item off the ground, vanilla marks the slot
  with `setGuaranteedDrop` (drop chance `2.0F`) so the item is never lost. The next chunk reload
  overwrites that slot with the module's own roll and resets the drop chance to `drop_chance / 100`.
* **Everything else is identical**, because every roll is seeded from the mob's UUID (see below), so
  the re-application produces the same kit it produced the first time.

### Determinism

Every roll in the module is seeded from `uuid.getLeastSignificantBits()` — the setup rolls, the
material pick, the enchantment levels, the effect level, and the weapon roll from the same value
XORed with `0x5EED`. A given mob therefore always produces exactly the same kit, and two mobs that
happen to share their low UUID bits are twins. There is no per-spawn variation beyond the UUID.

One exception. The four armour-enchantment groups are iterated out of a `Map.of(...)`, whose
iteration order is explicitly unspecified and which OpenJDK randomises once per JVM start — four
different orders showed up across eight runs of a four-entry map on the JDK 21 that builds this
project. All four groups draw from the same stream, so **which rolls land on the helmet and which on
the boots can change when the server restarts**. The number of draws is the same either way, so the
levels, durability, effects and weapon enchantments that follow are unaffected.

### Writing a zone list

Every entry in `above_zero`, `below_zero` and `nether_end` is one semicolon-separated string:

```
<CONFIG_KEY>;<property>;<number>
WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>      # the one four-part form
```

| `CONFIG_KEY` | `property` | `number` means |
|---|---|---|
| `GEAR_TYPES` | material | % chance, first hit wins (see above) |
| `ARMOR_CHANCES` | `helmet` `chestplate` `leggings` `boots` | % chance for that slot |
| `HELMET_ENCHANTMENTS` … `BOOTS_ENCHANTMENTS` | enchantment name | % chance, rolled only if the piece spawned |
| `WEAPON_ENCHANTMENTS` | enchantment name | % chance, rolled always |
| `ENCHANTMENT_LEVELS` | `min_level` / `max_level` | the level, before clamping |
| `ARMOR_DURABILITY` | `min_percent` / `max_percent` | remaining durability in percent |
| `POTION_EFFECTS` | effect name | % chance |
| `WEAPON_TYPES` | material | relative weight, normalised over the list |
| `WEAPON_RANDOMIZER` | *(four parts)* mob id, then weapon type | % chance, cumulative per mob |

> **The separator is `;`, and every number must be an integer.** An entry that contains `:` but no
> `;` is rejected on purpose — it is the old delimiter — and neither that rejection nor the
> `Integer.parseInt` calls around it are caught anywhere on the path from the event handler:
>
> ```java
> if (!entry.contains(";") && entry.contains(":")) {
>     throw new IllegalArgumentException(
>         "Better Mobs config entry uses ':' as delimiter. Use ';' instead: " + entry);
> }
> ```
>
> One malformed entry therefore throws out of `onEntityJoinLevel` **on every single mob spawn** in
> that zone. An unknown `CONFIG_KEY`, by contrast, is skipped without a word.

### Debug mode broadcasts to everyone

With `debug_logging = ON` — or `AUTO` while `globalDebugLogging` is true — every equipped spawn does
more than write a log line: it sends a chat message to **all players on the server**, carrying a
hover tooltip with the full gear list and a click action that runs `/tp @s <x> <y> <z>`. On a
populated server this is a flood, not a diagnostic. The message itself is German, and it is the only
player-visible string the module has; there are no lang entries. The `/tp` runs as the clicking
player, so it needs their own permission for the command.

<!-- vpa:config:start -->
## Configuration

Section `[modules.better_mobs]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_better_mobs-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `above_zero` | list | `GEAR_TYPES;gold;10, GEAR_TYPES;chainmail;10, GEAR_TYPES;leather;20, ARMOR_CHANCES;helmet;60, ARMOR_CHANCES;chestplate;50, ARMOR_CHANCES;leggings;45, ARMOR_CHANCES;boots;40, HELMET_ENCHANTMENTS;protection;5, HELMET_ENCHANTMENTS;fire_protection;2, HELMET_ENCHANTMENTS;blast_protection;3, HELMET_ENCHANTMENTS;projectile_protection;3, HELMET_ENCHANTMENTS;thorns;4, WEAPON_ENCHANTMENTS;sharpness;5, WEAPON_ENCHANTMENTS;fire_aspect;2, WEAPON_ENCHANTMENTS;knockback;3, WEAPON_ENCHANTMENTS;unbreaking;5, WEAPON_ENCHANTMENTS;power;5, WEAPON_ENCHANTMENTS;punch;2, WEAPON_ENCHANTMENTS;flame;2, CHESTPLATE_ENCHANTMENTS;protection;5, CHESTPLATE_ENCHANTMENTS;fire_protection;2, CHESTPLATE_ENCHANTMENTS;blast_protection;3, CHESTPLATE_ENCHANTMENTS;projectile_protection;3, CHESTPLATE_ENCHANTMENTS;thorns;4, LEGGINGS_ENCHANTMENTS;protection;5, LEGGINGS_ENCHANTMENTS;fire_protection;2, LEGGINGS_ENCHANTMENTS;blast_protection;3, LEGGINGS_ENCHANTMENTS;projectile_protection;3, LEGGINGS_ENCHANTMENTS;thorns;4, BOOTS_ENCHANTMENTS;protection;5, BOOTS_ENCHANTMENTS;fire_protection;2, BOOTS_ENCHANTMENTS;blast_protection;3, BOOTS_ENCHANTMENTS;projectile_protection;3, BOOTS_ENCHANTMENTS;feather_falling;2, BOOTS_ENCHANTMENTS;thorns;4, BOOTS_ENCHANTMENTS;frost_walker;5, ENCHANTMENT_LEVELS;min_level;1, ENCHANTMENT_LEVELS;max_level;3, ARMOR_DURABILITY;min_percent;5, ARMOR_DURABILITY;max_percent;20, POTION_EFFECTS;speed;10, POTION_EFFECTS;strength;5, POTION_EFFECTS;haste;5, POTION_EFFECTS;fire_resistance;5, WEAPON_TYPES;wood;65, WEAPON_TYPES;stone;35, WEAPON_RANDOMIZER;minecraft:skeleton;sword;35, WEAPON_RANDOMIZER;minecraft:skeleton;axe;25` | no validator - plain builder.define(...) with a List<String> default, NOT defineList(), so there is no per-element type check and no range. Entry grammar: <CONFIG_KEY>;<property>;<chance-or-value>, except WEAPON_RANDOMIZER which is 4 parts: WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>. Valid CONFIG_KEYs (BetterMobsConfigKey.java): GEAR_TYPES, HELMET_ENCHANTMENTS, CHESTPLATE_ENCHANTMENTS, LEGGINGS_ENCHANTMENTS, BOOTS_ENCHANTMENTS, ENCHANTMENT_LEVELS, ARMOR_DURABILITY, POTION_EFFECTS, ARMOR_CHANCES, WEAPON_RANDOMIZER, WEAPON_TYPES, WEAPON_ENCHANTMENTS. Unknown keys are silently skipped (BetterMobsConfig.java:530-532); a non-numeric chance throws (see notes). | Config comment: "Configuration for mobs spawned above Y=0". Used in every dimension except the Nether and the End, for mobs whose spawn Y >= 0. |
| `below_zero` | list | `GEAR_TYPES;gold;10, GEAR_TYPES;iron;30, GEAR_TYPES;leather;10, GEAR_TYPES;diamond;30, ARMOR_CHANCES;helmet;80, ARMOR_CHANCES;chestplate;70, ARMOR_CHANCES;leggings;65, ARMOR_CHANCES;boots;60, HELMET_ENCHANTMENTS;protection;30, HELMET_ENCHANTMENTS;fire_protection;15, HELMET_ENCHANTMENTS;blast_protection;15, HELMET_ENCHANTMENTS;projectile_protection;15, HELMET_ENCHANTMENTS;respiration;20, HELMET_ENCHANTMENTS;aqua_affinity;15, HELMET_ENCHANTMENTS;thorns;10, WEAPON_ENCHANTMENTS;sharpness;25, WEAPON_ENCHANTMENTS;fire_aspect;10, WEAPON_ENCHANTMENTS;knockback;10, WEAPON_ENCHANTMENTS;unbreaking;20, WEAPON_ENCHANTMENTS;power;20, WEAPON_ENCHANTMENTS;punch;10, WEAPON_ENCHANTMENTS;flame;10, CHESTPLATE_ENCHANTMENTS;protection;30, CHESTPLATE_ENCHANTMENTS;fire_protection;15, CHESTPLATE_ENCHANTMENTS;blast_protection;15, CHESTPLATE_ENCHANTMENTS;projectile_protection;15, CHESTPLATE_ENCHANTMENTS;thorns;10, LEGGINGS_ENCHANTMENTS;protection;30, LEGGINGS_ENCHANTMENTS;fire_protection;15, LEGGINGS_ENCHANTMENTS;blast_protection;15, LEGGINGS_ENCHANTMENTS;projectile_protection;15, LEGGINGS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;protection;30, BOOTS_ENCHANTMENTS;fire_protection;15, BOOTS_ENCHANTMENTS;blast_protection;15, BOOTS_ENCHANTMENTS;projectile_protection;15, BOOTS_ENCHANTMENTS;feather_falling;20, BOOTS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;depth_strider;10, BOOTS_ENCHANTMENTS;frost_walker;10, ENCHANTMENT_LEVELS;min_level;2, ENCHANTMENT_LEVELS;max_level;4, ARMOR_DURABILITY;min_percent;5, ARMOR_DURABILITY;max_percent;20, POTION_EFFECTS;speed;10, POTION_EFFECTS;strength;10, POTION_EFFECTS;haste;10, POTION_EFFECTS;fire_resistance;10, WEAPON_TYPES;iron;50, WEAPON_TYPES;gold;20, WEAPON_TYPES;diamond;30, WEAPON_RANDOMIZER;minecraft:skeleton;sword;40, WEAPON_RANDOMIZER;minecraft:skeleton;axe;30` | no validator - plain builder.define(...) with a List<String> default, NOT defineList(), so there is no per-element type check and no range. Entry grammar: <CONFIG_KEY>;<property>;<chance-or-value>, except WEAPON_RANDOMIZER which is 4 parts: WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>. Valid CONFIG_KEYs (BetterMobsConfigKey.java): GEAR_TYPES, HELMET_ENCHANTMENTS, CHESTPLATE_ENCHANTMENTS, LEGGINGS_ENCHANTMENTS, BOOTS_ENCHANTMENTS, ENCHANTMENT_LEVELS, ARMOR_DURABILITY, POTION_EFFECTS, ARMOR_CHANCES, WEAPON_RANDOMIZER, WEAPON_TYPES, WEAPON_ENCHANTMENTS. Unknown keys are silently skipped (BetterMobsConfig.java:530-532); a non-numeric chance throws (see notes). | Config comment: "Configuration for mobs spawned below Y=0 or in the Nether/End". The Nether/End half of that comment is WRONG: getDimensionConfigEntries (BetterMobsConfig.java:697-704) routes Level.NETHER and Level.END to nether_end first, so below_zero only ever applies to Y < 0 in all other dimensions. |
| `drop_chance` | int | `10` | 0 ~ 100 | Chance (in percentage) for mobs to drop their enhanced gear upon death (0-100). Written per filled equipment slot via Mob#setDropChance(slot, value/100f); slots the module does not fill keep their vanilla drop chance. |
| `enabled_mobs` | list | `minecraft:zombie, minecraft:skeleton, minecraft:husk, minecraft:stray, minecraft:drowned, minecraft:spider, minecraft:cave_spider, minecraft:witch, minecraft:pillager, minecraft:vindicator, minecraft:evoker, minecraft:illusioner, minecraft:blaze, minecraft:wither_skeleton, minecraft:guardian, minecraft:elder_guardian, minecraft:shulker, minecraft:warden, minecraft:piglin, minecraft:zombified_piglin` | defineList with element validator o instanceof String; no id validation, so a typo or an unloaded mod's entity id is simply never matched. Only entities that extend net.minecraft.world.entity.monster.Monster are ever processed, whatever is listed here. | List of mob entity IDs that should receive random equipment. minecraft:shulker in the default list is a permanent no-op (Shulker extends AbstractGolem, not Monster). |
| `enabled_mobs_with_armor` | list | `minecraft:zombie, minecraft:skeleton, minecraft:husk, minecraft:wither_skeleton` | defineList with element validator o instanceof String; no id validation. | List of mob entity IDs that can receive armor. A second gate applied on top of enabled_mobs (BetterMobsModule.java:171) - a mob missing here still gets weapons and potion effects, but no armor. |
| `max_durability` | int | `100` | 1 ~ 100 | Config comment: "Maximum durability for enhanced gear as percentage (1-100%)". Actually the REMAINING durability in percent (100 = pristine, 1 = nearly broken) and it is applied ONLY to the main-hand weapon (BetterMobsModule.java:141-145), never to armor - armor uses the per-zone ARMOR_DURABILITY rolls instead. |
| `nether_end` | list | `GEAR_TYPES;netherite;30, ARMOR_CHANCES;helmet;80, ARMOR_CHANCES;chestplate;70, ARMOR_CHANCES;leggings;65, ARMOR_CHANCES;boots;60, HELMET_ENCHANTMENTS;protection;30, HELMET_ENCHANTMENTS;fire_protection;15, HELMET_ENCHANTMENTS;blast_protection;15, HELMET_ENCHANTMENTS;projectile_protection;15, HELMET_ENCHANTMENTS;thorns;10, WEAPON_ENCHANTMENTS;sharpness;30, WEAPON_ENCHANTMENTS;fire_aspect;15, WEAPON_ENCHANTMENTS;knockback;15, WEAPON_ENCHANTMENTS;unbreaking;25, WEAPON_ENCHANTMENTS;power;25, WEAPON_ENCHANTMENTS;punch;15, WEAPON_ENCHANTMENTS;flame;15, CHESTPLATE_ENCHANTMENTS;protection;30, CHESTPLATE_ENCHANTMENTS;fire_protection;15, CHESTPLATE_ENCHANTMENTS;blast_protection;15, CHESTPLATE_ENCHANTMENTS;projectile_protection;15, CHESTPLATE_ENCHANTMENTS;thorns;10, LEGGINGS_ENCHANTMENTS;protection;30, LEGGINGS_ENCHANTMENTS;fire_protection;15, LEGGINGS_ENCHANTMENTS;blast_protection;15, LEGGINGS_ENCHANTMENTS;projectile_protection;15, LEGGINGS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;protection;30, BOOTS_ENCHANTMENTS;fire_protection;15, BOOTS_ENCHANTMENTS;blast_protection;15, BOOTS_ENCHANTMENTS;projectile_protection;15, BOOTS_ENCHANTMENTS;feather_falling;20, BOOTS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;depth_strider;10, BOOTS_ENCHANTMENTS;frost_walker;10, ENCHANTMENT_LEVELS;min_level;2, ENCHANTMENT_LEVELS;max_level;4, ARMOR_DURABILITY;min_percent;5, ARMOR_DURABILITY;max_percent;20, POTION_EFFECTS;speed;20, POTION_EFFECTS;strength;10, POTION_EFFECTS;haste;10, POTION_EFFECTS;fire_resistance;10, WEAPON_TYPES;gold;65, WEAPON_TYPES;netherite;35, WEAPON_RANDOMIZER;minecraft:skeleton;sword;45, WEAPON_RANDOMIZER;minecraft:skeleton;axe;35` | no validator - plain builder.define(...) with a List<String> default, NOT defineList(), so there is no per-element type check and no range. Entry grammar: <CONFIG_KEY>;<property>;<chance-or-value>, except WEAPON_RANDOMIZER which is 4 parts: WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>. Valid CONFIG_KEYs (BetterMobsConfigKey.java): GEAR_TYPES, HELMET_ENCHANTMENTS, CHESTPLATE_ENCHANTMENTS, LEGGINGS_ENCHANTMENTS, BOOTS_ENCHANTMENTS, ENCHANTMENT_LEVELS, ARMOR_DURABILITY, POTION_EFFECTS, ARMOR_CHANCES, WEAPON_RANDOMIZER, WEAPON_TYPES, WEAPON_ENCHANTMENTS. Unknown keys are silently skipped (BetterMobsConfig.java:530-532); a non-numeric chance throws (see notes). | Config comment: "Configuration for mobs spawned in the Nether or End dimension". Applies at every Y in those two dimensions, and it is ONE shared block for both - there is no way to configure Nether and End separately (see the TODO at BetterMobsConfig.java:287). |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| `minecraft:shulker` in the default list | A permanent no-op. Only `Monster` subclasses pass the type check, and `Shulker extends AbstractGolem` — it merely *implements* `Enemy`. Verified against the 1.21.1 class files. |
| Modded and other vanilla mobs | Listing an id does nothing unless that entity extends `net.minecraft.world.entity.monster.Monster`. Animals, golems and most bosses never qualify. Ids are not validated, so a typo or an unloaded mod's id is silently never matched. |
| `POTION_EFFECTS;haste` | Not a recognised name; it is in all three shipped defaults and does nothing. |
| Effect strength | Always level I, always `Integer.MAX_VALUE` ticks, always with particles. Neither is configurable. |
| A bow or crossbow on a non-skeleton | `reassessWeaponGoal()` is only called on `AbstractSkeleton`. Any other mob given a ranged weapon keeps its melee AI and holds the bow without using it. |
| Armour and weapon durability | Not observable in play — mobs do not wear gear down, and vanilla re-rolls the damage value when the piece drops. See above. |
| `max_durability = 1` | `setDamageValue(max - max * percent / 100)` has no clamp and uses integer division. On a wooden sword (59 uses) that is damage 59 — zero remaining. Only the armour path clamps. |
| Nether and End share one list | `nether_end` covers both, at every height. There is no way to separate them; the TODO in `BetterMobsConfig` says so. Modded dimensions fall into the `above_zero`/`below_zero` split by Y. |
| `below_zero`'s own config comment | Reads "Configuration for mobs spawned below Y=0 or in the Nether/End". The Nether/End half is wrong — both are routed to `nether_end` first and never reach `below_zero`. |
| `enabled_mobs`' config comment | Lists the mobs only as far as `minecraft:warden` and omits `minecraft:piglin` and `minecraft:zombified_piglin`, which are in the actual twenty-entry default. Cosmetic, but it is what you read in the generated `.toml`. |
| Malformed zone entry | Throws out of the event handler on every mob spawn in that zone. See the warning above. |
| Turning the module off | The handler returns immediately, so no new mob is equipped — but mobs that were already kitted out keep everything they have. |
| Tests | There is no `src/test` in this repository. Nothing here has automated coverage; verification is in-game only. |

## Under the hood

Three files plus a standalone entry point, no mixins, no registries, no commands, no lang keys, no
data files:

| Class | Role |
|---|---|
| `modules/better_mobs/BetterMobsModule` | The event handler and everything it applies |
| `modules/better_mobs/config/BetterMobsConfig` | The seven config values, the parser and every roll |
| `modules/better_mobs/config/BetterMobsConfigKey` | The twelve entry keys, as an enum |
| `standalone/better_mobs/BetterMobsStandalone` | `@Mod("vpa_better_mobs")` entry point |

**One handler.** `NeoForge.EVENT_BUS.register(this)` in `onInitialize`, one `@SubscribeEvent` on
`EntityJoinLevelEvent`. The gates run in this order: module enabled → entity is a `Monster` **and**
the level is a `ServerLevel` → the mob's id is in `enabled_mobs`. Then gear material, weapon,
armour (second gate: `enabled_mobs_with_armor`), potion effects, debug broadcast.

**Where the rolls live.** `BetterMobsConfig#getRandomEquipmentSetupForMob` does all the drawing and
hands back a `Map<BetterMobsConfigKey, List<String>>` of *decided* values: `GEAR_TYPES` is a list of
either one material or none, `WEAPON_TYPES` one material, `ARMOR_CHANCES` the slots that won,
`ARMOR_DURABILITY` four numbers, `ENCHANTMENT_LEVELS` two. The module only reads that map. Two
leftovers from an earlier shape survive it: the module picks the material with
`new Random(...).nextInt(materials.size())` over a list that can only have one element, and
`getRandomEquipmentSetupForMob` can never return `null`, so the `if (setup == null)` guard and its
debug line are unreachable.

**`applyArmorEnchantments` is used for the weapon too**, despite the name, and creates a fresh
`Random` seeded with the same UUID bits on every call — so the *n*-th enchantment on the helmet and
the *n*-th on the boots always pick the same one of the two candidate levels.

**Dead code.** `getAboveZeroConfig()`, `getBelowZeroConfig()`, `getNetherEndConfig()`,
`convertToKeyMap()`, `isEntityEnabled()` and `canEntityWearArmor()` have no callers anywhere under
`src/`; the module reads `getEnabledMobs()` and `getEnabledMobsWithArmor()` directly. `convertToKeyMap`
carries the same unguarded parse as the live path, which is why it is easy to mistake for the real
one.

**Packaging.** The only cross-module coupling is `util/MobArmorEnchantments#clampLevel`, which is
packed into `vpa_core`; every standalone module jar depends on `vpa_core` anyway, so
`vpa_better_mobs` declares no module dependency of its own. The jar carries
`modules/better_mobs/**` and `standalone/better_mobs/**` and nothing else.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives and how the `;`
  format fits the rest of the mod
* [Debug Logging Guide](../guides/debug-logging.md) — how to switch this module's broadcast off
  again while keeping global logging on
* [Mob Drops](mob_drops.md) — the other half of mob loot
* [All modules](../../README.md#-modules)
