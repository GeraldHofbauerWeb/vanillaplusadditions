# Configuration Reference

Every setting of every module — 248 module-specific keys across 50 modules,
generated from the source. For what the file is and where it lives, see the
[Configuration Guide](../guides/configuration.md).

Two keys exist in every module and are not repeated below:

| Key | Type | Default | Effect |
|---|---|---|---|
| `enabled` | boolean | varies | Whether the module is active. |
| `debug_logging` | enum | `AUTO` | `AUTO`, `ON` or `OFF`. |

## `arm_target_overlay` — Arm Target Overlay

Hold Left Ctrl while wearing goggles and looking at a Create Mechanical Arm to see glowing boxes around every block it takes from (orange) and deposits into (teal) - even through walls. · [full page](../modules/arm_target_overlay.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `input_color.alpha` | double | `0.8` | 0.0 ~ 1.0 | Alpha component for input (TAKE) position outlines. |
| `input_color.blue` | double | `0.1` | 0.0 ~ 1.0 | Blue component for input (TAKE) position outlines. |
| `input_color.green` | double | `0.6` | 0.0 ~ 1.0 | Green component for input (TAKE) position outlines. |
| `input_color.red` | double | `1.0` | 0.0 ~ 1.0 | Red component for input (TAKE) position outlines. |
| `output_color.alpha` | double | `0.8` | 0.0 ~ 1.0 | Alpha component for output (DEPOSIT) position outlines. |
| `output_color.blue` | double | `0.7` | 0.0 ~ 1.0 | Blue component for output (DEPOSIT) position outlines. |
| `output_color.green` | double | `0.9` | 0.0 ~ 1.0 | Green component for output (DEPOSIT) position outlines. |
| `output_color.red` | double | `0.1` | 0.0 ~ 1.0 | Red component for output (DEPOSIT) position outlines. |

## `axolotl_guardian` — Axolotl Guardian

Tame axolotls with raw fish, put a bowl or feeding station in your underwater base, and the fed axolotls patrol a configurable radius around it, killing hostile mobs in the water and hauling their loot and XP back to the station. · [full page](../modules/axolotl_guardian.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `association_radius` | double | `64.0` | 1.0 ~ 128.0 | Radius (blocks) in which owned axolotls are associated when shift-clicking a bowl. |
| `auto_associate_radius` | double | `1.5` | 0.5 ~ 4.0 | Radius (blocks) within which an axolotl without a bowl is automatically associated with a nearby bowl. |
| `axolotl_xp_capacity` | int | `500` | 0 ~ 10000 | Maximum XP points a single axolotl can hold before XP drops normally. |
| `fed_duration_ticks` | int | `6000` | 20 ~ 144000 | How many ticks a single tropical fish keeps an axolotl in the 'fed' (guarding) state (20 ticks = 1 second). |
| `glow_duration_seconds` | int | `30` | 1 ~ 300 | How many seconds your own associated axolotls glow (client-side only) when looking at their bowl. The getter multiplies by 20, so the code works in ticks (default 600). |
| `guard_radius` | double | `32.0` | 2.0 ~ 128.0 | Horizontal radius (blocks, XZ) around the associated bowl in which a fed axolotl scans for hostile mobs in water. |
| `guard_radius_y` | double | `16.0` | 2.0 ~ 128.0 | Vertical radius (blocks, Y) around the associated bowl - axolotls ignore mobs further away vertically. |
| `heal_recovery_target` | double | `1.0` | 0.0 ~ 1.0 | While at/near its home block (distance squared <= 16), a guardian axolotl keeps regenerating until its health reaches this fraction of max HP. |
| `heal_return_threshold` | double | `0.60` | 0.0 ~ 1.0 | A fed, out-of-combat guardian whose health drops below this fraction of max HP proactively swims back to its bowl/station to heal. |
| `max_axolotls_per_station` | int | `8` | 1 ~ 64 | Maximum number of axolotls that can be associated with a single bowl or station. Also the divisor of the feeding station's comparator output. |
| `play_dead_min_health` | int | `4` | 1 ~ 14 | An armored guardian axolotl may only play dead once its health drops to this absolute HP value or below (axolotl max health is 14). Unarmored axolotls keep vanilla play-dead untouched. |
| `station_xp_capacity` | int | `5000` | 0 ~ 100000 | Maximum XP points a feeding station can hold before overflow stays on axolotls. |
| `thorns_reflect_fraction` | double | `0.33` | 0.0 ~ 1.0 | Base fraction of absorbed damage reflected back to the attacker, scaled by the armor's Thorns level. |
| `xp_per_bottle` | int | `8` | 1 ~ 64 | XP points consumed per Bottle o' Enchanting produced at the station. |

## `battle_dogs` — Battle Dogs

Tamed wolves can wear iron, gold, diamond or netherite body armor that soaks up every point of incoming damage until it breaks, makes the wolf hit noticeably harder, and finally gives a biting wolf a visible head-snap and lunge instead of vanilla's motionless attack. · [full page](../modules/battle_dogs.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `bite_animation.enabled` | boolean | `true` | — | Snap the wolf's head down when it lands a bite. Also gates the lunge and the server-side bite-direction packet; it does NOT gate the swing-timer mixin. |
| `bite_animation.only_when_ridden` | boolean | `false` | — | Restrict the animation to a wolf that is being ridden (off by default, because a dog that only bites visibly while carrying someone looks stranger than one that always does). |
| `bite_animation.strength` | double | `1.0` | 0.0 ~ 2.0 | Scales how far the head swings and how far the lunge travels; 1.0 is about 50 degrees of head pitch (BITE_PITCH = 0.9 rad). |
| `thorns_reflect_fraction` | double | `0.33` | 0.0 ~ 1.0 | Base fraction of absorbed damage reflected back to the attacker, scaled by the armor's Thorns level (0.0 = none, 1.0 = full). Total reflect is capped at 1.0 (min(1.0, fraction * thornsLevel)). |

## `better_mobs` — Better Mobs

Hostile mobs spawn kitted out - battle-worn armour, permanent potion effects and, for skeletons, a random enchanted weapon - with the loot table getting nastier below Y=0 and nastiest of all in the Nether and the End. · [full page](../modules/better_mobs.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `above_zero` | list | `GEAR_TYPES;gold;10, GEAR_TYPES;chainmail;10, GEAR_TYPES;leather;20, ARMOR_CHANCES;helmet;60, ARMOR_CHANCES;chestplate;50, ARMOR_CHANCES;leggings;45, ARMOR_CHANCES;boots;40, HELMET_ENCHANTMENTS;protection;5, HELMET_ENCHANTMENTS;fire_protection;2, HELMET_ENCHANTMENTS;blast_protection;3, HELMET_ENCHANTMENTS;projectile_protection;3, HELMET_ENCHANTMENTS;thorns;4, WEAPON_ENCHANTMENTS;sharpness;5, WEAPON_ENCHANTMENTS;fire_aspect;2, WEAPON_ENCHANTMENTS;knockback;3, WEAPON_ENCHANTMENTS;unbreaking;5, WEAPON_ENCHANTMENTS;power;5, WEAPON_ENCHANTMENTS;punch;2, WEAPON_ENCHANTMENTS;flame;2, CHESTPLATE_ENCHANTMENTS;protection;5, CHESTPLATE_ENCHANTMENTS;fire_protection;2, CHESTPLATE_ENCHANTMENTS;blast_protection;3, CHESTPLATE_ENCHANTMENTS;projectile_protection;3, CHESTPLATE_ENCHANTMENTS;thorns;4, LEGGINGS_ENCHANTMENTS;protection;5, LEGGINGS_ENCHANTMENTS;fire_protection;2, LEGGINGS_ENCHANTMENTS;blast_protection;3, LEGGINGS_ENCHANTMENTS;projectile_protection;3, LEGGINGS_ENCHANTMENTS;thorns;4, BOOTS_ENCHANTMENTS;protection;5, BOOTS_ENCHANTMENTS;fire_protection;2, BOOTS_ENCHANTMENTS;blast_protection;3, BOOTS_ENCHANTMENTS;projectile_protection;3, BOOTS_ENCHANTMENTS;feather_falling;2, BOOTS_ENCHANTMENTS;thorns;4, BOOTS_ENCHANTMENTS;frost_walker;5, ENCHANTMENT_LEVELS;min_level;1, ENCHANTMENT_LEVELS;max_level;3, ARMOR_DURABILITY;min_percent;5, ARMOR_DURABILITY;max_percent;20, POTION_EFFECTS;speed;10, POTION_EFFECTS;strength;5, POTION_EFFECTS;haste;5, POTION_EFFECTS;fire_resistance;5, WEAPON_TYPES;wood;65, WEAPON_TYPES;stone;35, WEAPON_RANDOMIZER;minecraft:skeleton;sword;35, WEAPON_RANDOMIZER;minecraft:skeleton;axe;25` | no validator - plain builder.define(...) with a List<String> default, NOT defineList(), so there is no per-element type check and no range. Entry grammar: <CONFIG_KEY>;<property>;<chance-or-value>, except WEAPON_RANDOMIZER which is 4 parts: WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>. Valid CONFIG_KEYs (BetterMobsConfigKey.java): GEAR_TYPES, HELMET_ENCHANTMENTS, CHESTPLATE_ENCHANTMENTS, LEGGINGS_ENCHANTMENTS, BOOTS_ENCHANTMENTS, ENCHANTMENT_LEVELS, ARMOR_DURABILITY, POTION_EFFECTS, ARMOR_CHANCES, WEAPON_RANDOMIZER, WEAPON_TYPES, WEAPON_ENCHANTMENTS. Unknown keys are silently skipped (BetterMobsConfig.java:530-532); a non-numeric chance throws (see notes). | Config comment: "Configuration for mobs spawned above Y=0". Used in every dimension except the Nether and the End, for mobs whose spawn Y >= 0. |
| `below_zero` | list | `GEAR_TYPES;gold;10, GEAR_TYPES;iron;30, GEAR_TYPES;leather;10, GEAR_TYPES;diamond;30, ARMOR_CHANCES;helmet;80, ARMOR_CHANCES;chestplate;70, ARMOR_CHANCES;leggings;65, ARMOR_CHANCES;boots;60, HELMET_ENCHANTMENTS;protection;30, HELMET_ENCHANTMENTS;fire_protection;15, HELMET_ENCHANTMENTS;blast_protection;15, HELMET_ENCHANTMENTS;projectile_protection;15, HELMET_ENCHANTMENTS;respiration;20, HELMET_ENCHANTMENTS;aqua_affinity;15, HELMET_ENCHANTMENTS;thorns;10, WEAPON_ENCHANTMENTS;sharpness;25, WEAPON_ENCHANTMENTS;fire_aspect;10, WEAPON_ENCHANTMENTS;knockback;10, WEAPON_ENCHANTMENTS;unbreaking;20, WEAPON_ENCHANTMENTS;power;20, WEAPON_ENCHANTMENTS;punch;10, WEAPON_ENCHANTMENTS;flame;10, CHESTPLATE_ENCHANTMENTS;protection;30, CHESTPLATE_ENCHANTMENTS;fire_protection;15, CHESTPLATE_ENCHANTMENTS;blast_protection;15, CHESTPLATE_ENCHANTMENTS;projectile_protection;15, CHESTPLATE_ENCHANTMENTS;thorns;10, LEGGINGS_ENCHANTMENTS;protection;30, LEGGINGS_ENCHANTMENTS;fire_protection;15, LEGGINGS_ENCHANTMENTS;blast_protection;15, LEGGINGS_ENCHANTMENTS;projectile_protection;15, LEGGINGS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;protection;30, BOOTS_ENCHANTMENTS;fire_protection;15, BOOTS_ENCHANTMENTS;blast_protection;15, BOOTS_ENCHANTMENTS;projectile_protection;15, BOOTS_ENCHANTMENTS;feather_falling;20, BOOTS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;depth_strider;10, BOOTS_ENCHANTMENTS;frost_walker;10, ENCHANTMENT_LEVELS;min_level;2, ENCHANTMENT_LEVELS;max_level;4, ARMOR_DURABILITY;min_percent;5, ARMOR_DURABILITY;max_percent;20, POTION_EFFECTS;speed;10, POTION_EFFECTS;strength;10, POTION_EFFECTS;haste;10, POTION_EFFECTS;fire_resistance;10, WEAPON_TYPES;iron;50, WEAPON_TYPES;gold;20, WEAPON_TYPES;diamond;30, WEAPON_RANDOMIZER;minecraft:skeleton;sword;40, WEAPON_RANDOMIZER;minecraft:skeleton;axe;30` | no validator - plain builder.define(...) with a List<String> default, NOT defineList(), so there is no per-element type check and no range. Entry grammar: <CONFIG_KEY>;<property>;<chance-or-value>, except WEAPON_RANDOMIZER which is 4 parts: WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>. Valid CONFIG_KEYs (BetterMobsConfigKey.java): GEAR_TYPES, HELMET_ENCHANTMENTS, CHESTPLATE_ENCHANTMENTS, LEGGINGS_ENCHANTMENTS, BOOTS_ENCHANTMENTS, ENCHANTMENT_LEVELS, ARMOR_DURABILITY, POTION_EFFECTS, ARMOR_CHANCES, WEAPON_RANDOMIZER, WEAPON_TYPES, WEAPON_ENCHANTMENTS. Unknown keys are silently skipped (BetterMobsConfig.java:530-532); a non-numeric chance throws (see notes). | Config comment: "Configuration for mobs spawned below Y=0 or in the Nether/End". The Nether/End half of that comment is WRONG: getDimensionConfigEntries (BetterMobsConfig.java:697-704) routes Level.NETHER and Level.END to nether_end first, so below_zero only ever applies to Y < 0 in all other dimensions. |
| `drop_chance` | int | `10` | 0 ~ 100 | Chance (in percentage) for mobs to drop their enhanced gear upon death (0-100). Written per filled equipment slot via Mob#setDropChance(slot, value/100f); slots the module does not fill keep their vanilla drop chance. |
| `enabled_mobs` | list | `minecraft:zombie, minecraft:skeleton, minecraft:husk, minecraft:stray, minecraft:drowned, minecraft:spider, minecraft:cave_spider, minecraft:witch, minecraft:pillager, minecraft:vindicator, minecraft:evoker, minecraft:illusioner, minecraft:blaze, minecraft:wither_skeleton, minecraft:guardian, minecraft:elder_guardian, minecraft:shulker, minecraft:warden, minecraft:piglin, minecraft:zombified_piglin` | defineList with element validator o instanceof String; no id validation, so a typo or an unloaded mod's entity id is simply never matched. Only entities that extend net.minecraft.world.entity.monster.Monster are ever processed, whatever is listed here. | List of mob entity IDs that should receive random equipment. minecraft:shulker in the default list is a permanent no-op (Shulker extends AbstractGolem, not Monster). |
| `enabled_mobs_with_armor` | list | `minecraft:zombie, minecraft:skeleton, minecraft:husk, minecraft:wither_skeleton` | defineList with element validator o instanceof String; no id validation. | List of mob entity IDs that can receive armor. A second gate applied on top of enabled_mobs (BetterMobsModule.java:171) - a mob missing here still gets weapons and potion effects, but no armor. |
| `max_durability` | int | `100` | 1 ~ 100 | Config comment: "Maximum durability for enhanced gear as percentage (1-100%)". Actually the REMAINING durability in percent (100 = pristine, 1 = nearly broken) and it is applied ONLY to the main-hand weapon (BetterMobsModule.java:141-145), never to armor - armor uses the per-zone ARMOR_DURABILITY rolls instead. |
| `nether_end` | list | `GEAR_TYPES;netherite;30, ARMOR_CHANCES;helmet;80, ARMOR_CHANCES;chestplate;70, ARMOR_CHANCES;leggings;65, ARMOR_CHANCES;boots;60, HELMET_ENCHANTMENTS;protection;30, HELMET_ENCHANTMENTS;fire_protection;15, HELMET_ENCHANTMENTS;blast_protection;15, HELMET_ENCHANTMENTS;projectile_protection;15, HELMET_ENCHANTMENTS;thorns;10, WEAPON_ENCHANTMENTS;sharpness;30, WEAPON_ENCHANTMENTS;fire_aspect;15, WEAPON_ENCHANTMENTS;knockback;15, WEAPON_ENCHANTMENTS;unbreaking;25, WEAPON_ENCHANTMENTS;power;25, WEAPON_ENCHANTMENTS;punch;15, WEAPON_ENCHANTMENTS;flame;15, CHESTPLATE_ENCHANTMENTS;protection;30, CHESTPLATE_ENCHANTMENTS;fire_protection;15, CHESTPLATE_ENCHANTMENTS;blast_protection;15, CHESTPLATE_ENCHANTMENTS;projectile_protection;15, CHESTPLATE_ENCHANTMENTS;thorns;10, LEGGINGS_ENCHANTMENTS;protection;30, LEGGINGS_ENCHANTMENTS;fire_protection;15, LEGGINGS_ENCHANTMENTS;blast_protection;15, LEGGINGS_ENCHANTMENTS;projectile_protection;15, LEGGINGS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;protection;30, BOOTS_ENCHANTMENTS;fire_protection;15, BOOTS_ENCHANTMENTS;blast_protection;15, BOOTS_ENCHANTMENTS;projectile_protection;15, BOOTS_ENCHANTMENTS;feather_falling;20, BOOTS_ENCHANTMENTS;thorns;10, BOOTS_ENCHANTMENTS;depth_strider;10, BOOTS_ENCHANTMENTS;frost_walker;10, ENCHANTMENT_LEVELS;min_level;2, ENCHANTMENT_LEVELS;max_level;4, ARMOR_DURABILITY;min_percent;5, ARMOR_DURABILITY;max_percent;20, POTION_EFFECTS;speed;20, POTION_EFFECTS;strength;10, POTION_EFFECTS;haste;10, POTION_EFFECTS;fire_resistance;10, WEAPON_TYPES;gold;65, WEAPON_TYPES;netherite;35, WEAPON_RANDOMIZER;minecraft:skeleton;sword;45, WEAPON_RANDOMIZER;minecraft:skeleton;axe;35` | no validator - plain builder.define(...) with a List<String> default, NOT defineList(), so there is no per-element type check and no range. Entry grammar: <CONFIG_KEY>;<property>;<chance-or-value>, except WEAPON_RANDOMIZER which is 4 parts: WEAPON_RANDOMIZER;<mob_id>;<weapon_type>;<chance>. Valid CONFIG_KEYs (BetterMobsConfigKey.java): GEAR_TYPES, HELMET_ENCHANTMENTS, CHESTPLATE_ENCHANTMENTS, LEGGINGS_ENCHANTMENTS, BOOTS_ENCHANTMENTS, ENCHANTMENT_LEVELS, ARMOR_DURABILITY, POTION_EFFECTS, ARMOR_CHANCES, WEAPON_RANDOMIZER, WEAPON_TYPES, WEAPON_ENCHANTMENTS. Unknown keys are silently skipped (BetterMobsConfig.java:530-532); a non-numeric chance throws (see notes). | Config comment: "Configuration for mobs spawned in the Nether or End dimension". Applies at every Y in those two dimensions, and it is ONE shared block for both - there is no way to configure Nether and End separately (see the TODO at BetterMobsConfig.java:287). |

## `block_glow` — Block Glow

Type /blockglow iron_ore and every matching block within a set radius is outlined in glowing cyan through walls, until a timer runs out or you clear it. · [full page](../modules/block_glow.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_duration_seconds` | int | `60` | 0 ~ 2147483647 (Integer.MAX_VALUE) | Default glow duration in seconds (0 = infinite). |
| `default_radius` | int | `24` | 1 ~ 128 | Default search radius in blocks for /blockglow. |
| `max_highlights_per_frame` | int | `512` | 16 ~ 8192 | Maximum number of block outlines rendered per frame. Caps how many boxes are DRAWN, not how many blocks are scanned. |
| `max_radius` | int | `64` | 1 ~ 256 | Maximum allowed radius in blocks for /blockglow. |
| `outline_color.alpha` | double | `1.0` | 0.0 ~ 1.0 | Alpha component of the outline color. |
| `outline_color.blue` | double | `1.0` | 0.0 ~ 1.0 | Blue component of the outline color. |
| `outline_color.green` | double | `1.0` | 0.0 ~ 1.0 | Green component of the outline color. |
| `outline_color.red` | double | `0.0` | 0.0 ~ 1.0 | Red component of the outline color. |
| `selection_mode` | string | `"nearest"` | free-form string, no validator (define, not defineInList) — only "scan_order" (after trim+lowercase) is honored, every other value silently falls back to "nearest" | Selection mode for which blocks are highlighted: nearest or scan_order. |

## `bluemap_signs` — BlueMap Signs

Put `[bm]` on the first line of any sign and that spot shows up as a labelled, icon-picked pin on your server's BlueMap web map — plus `/bmsigns` to place and edit pins that have no sign at all. · [full page](../modules/bluemap_signs.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_hidden` | boolean | `false` | — | Whether the marker layer starts hidden (players must enable it). |
| `marker_set_name` | string | `"Map Signs"` | — | Display name of the marker layer in the BlueMap web UI. |
| `max_distance` | int | `10000` | 1 ~ 10000000 | Max camera distance in blocks at which markers stay visible; a large value means always. |
| `prefix` | string | `"[bm]"` | — | Trigger text on line 1 of a sign that turns it into a BlueMap marker (case-insensitive, trimmed). |
| `toggleable` | boolean | `true` | — | Whether players can toggle the marker layer on/off in BlueMap. |

## `cat_guardian` — Cat Guardian

Tamed cats you hook up to a fish bowl or feeding station become base guards: they patrol, kill hostile mobs in a configurable radius, haul the loot and XP back to the station, and can wear their own armor. · [full page](../modules/cat_guardian.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `association_radius` | double | `64.0` | 1.0 ~ 128.0 | Radius (blocks) in which tamed cats are associated when shift-clicking a bowl. |
| `auto_associate_radius` | double | `1.5` | 0.5 ~ 4.0 | Radius (blocks) within which a cat without a bowl is automatically associated with a nearby bowl. |
| `cat_xp_capacity` | int | `500` | 0 ~ 10000 | Maximum XP points a single cat can hold before XP drops normally. |
| `fed_duration_ticks` | int | `6000` | 20 ~ 144000 | How many ticks a single fish feeding keeps a cat in the 'fed' (guarding) state (20 ticks = 1 second). |
| `glow_duration_seconds` | int | `30` | 1 ~ 300 | How many seconds the (client-side) glow lasts on associated cats when looking at their bowl. Consumed as ticks via getGlowDurationTicks() = value * 20. |
| `guard_radius` | double | `32.0` | 2.0 ~ 128.0 | Horizontal radius (blocks, XZ) around the associated bowl in which a fed cat scans for and attacks hostile mobs. |
| `guard_radius_y` | double | `16.0` | 2.0 ~ 128.0 | Vertical radius (blocks, Y) around the associated bowl - cats ignore mobs further away vertically. |
| `max_cats_per_station` | int | `8` | 1 ~ 64 | Maximum number of cats that can be associated with a single bowl or station. Also the divisor of the feeding station's comparator output. |
| `station_xp_capacity` | int | `5000` | 0 ~ 100000 | Maximum XP points a feeding station can hold before overflow stays on cats. |
| `thorns_reflect_fraction` | double | `0.33` | 0.0 ~ 1.0 | Base fraction of absorbed damage reflected back to the attacker, scaled by the armor's Thorns level (0.0 = none, 1.0 = full). |
| `xp_per_bottle` | int | `8` | 1 ~ 64 | XP points consumed per Bottle o' Enchanting produced at the station. |

## `chunk_reset` — Chunk Reset Command

An operator-only command that permanently deletes the chunk you're standing in — or up to an 11x11 square around you — so the world generator builds that terrain again from scratch, wiping every block, entity and structure in it. · [full page](../modules/chunk_reset.md)

No settings of its own.

## `compass_overhaul` — Compass Overhaul

Your lodestone compass stops forgetting where it was bound, the needle stays steady aboard a Create Aeronautics airship, and a craftable World Compass always points true north — in the Nether and the End too. · [full page](../modules/compass_overhaul.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `fix_quark_compass` | boolean | `true` | — | Repairs the needle where Quark's 'Compasses Work Everywhere' takes it over (aim at block centre instead of the north-west corner, honour Sable sub-levels, keep an item frame's rotation steps); without Quark it does nothing. |
| `fix_sublevel_needle` | boolean | `true` | — | Corrects the compass needle inside Sable sub-levels (Create Aeronautics airships), using the interpolated render pose instead of Sable's previous-tick pose; turn it off if another mod fights over the same method. |
| `guard_lodestone_binding` | boolean | `true` | — | Keeps a lodestone compass bound unless the lodestone is provably gone, instead of dropping the binding whenever vanilla's POI lookup comes back empty (which also happens for an unloaded chunk or an unreadable POI file). |
| `world_compass_enabled` | boolean | `true` | — | Adds the World Compass recipe — a compass that always points north in world space, including the Nether, the End and aboard a turning airship. NOTE: only the recipe is actually gated; the item itself is registered regardless (see notes). |

## `conduit_attack_range` — Conduit Attack Range

Your conduit shoots hostile mobs as soon as it is active (16 frame blocks) instead of only at the full 42-block frame, and it reaches out to half its Conduit Power radius instead of a flat 8 blocks. · [full page](../modules/conduit_attack_range.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `min_frames` | int | `16` | 1 ~ 96 (effective 16 ~ 42) | Minimum frame-block count at which a conduit starts attacking hostiles, replacing vanilla's hardcoded 42. Values below 16 do nothing (vanilla only runs the attack tick on an ACTIVE conduit, which needs 16 frames) and values above 42 switch attacking off entirely (a vanilla frame caps at 42). Note the conduit's angry open-eye texture still needs 42 frames - the mixin does not touch updateHunting. |
| `radius_divisor` | int | `2` | 1 ~ 16 | Hostile-damage radius = Conduit Power radius (frames / 7 * 16) / this divisor, floored at 1 block. 2 (default) = half the effect radius: 16 blocks at 16 frames, 48 at 42. 1 = full effect radius (32 / 96). The module additionally clamps the value with Math.max(1, ...). |

## `copycat_pathfinding` — Copycat Pathfinding

Mobs use a passage whose wall, ceiling or floor is lined with Create Copycat Panels again — walking along and under the 3-pixel plate, standing on a supported floor panel, swimming through a waterlogged one — while a panel standing squarely across the way still stops them. · [full page](../modules/copycat_pathfinding.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `direction_aware` | boolean | `true` | — | Blocks only those movements that actually cross a panel's plate; false makes wall and ceiling panels passable from every side, which lets mobs walk into a panel wall and get stuck against it. |
| `floor_panels_walkable` | boolean | `true` | — | Treats a floor panel (FACING=UP) resting on something solid as walkable ground instead of a wall, fixing passages only one block high, while a floor panel with air below stays as it is so mobs keep crossing free-standing panel bridges. |
| `water_pathfinding` | boolean | `true` | — | Lets waterlogged panels and steps count as water for swimming mobs, like vanilla slabs do, where Create blocks them outright. |

## `create_redstone_link_rebinder` — Create Redstone Link Rebinder

Redstone Links that quietly stopped reaching their receivers after a chunk reload are found and reconnected, so buttons and levers on a frequency keep working instead of needing the transmitter block replaced by hand. · [full page](../modules/create_redstone_link_rebinder.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `auto_rebind_during_sweep` | boolean | `true` | — | Whether the periodic sweep may re-register missing links. This is the one that does the work in practice - turning it off effectively disables the module's automatic repair. |
| `auto_rebind_on_chunk_load` | boolean | `true` | — | Whether the post-load check may re-register a missing link itself. Turning it off leaves only /vparelink. |
| `check_interval_ticks` | integer | `100` | 20-1200 | How often all tracked links are swept. Only remembered positions in loaded chunks are read - never a world scan. This is the value that matters: every one of the ten links repaired after the cold load was found here. Lowering it shortens how long a door stays dead; raising it costs only reaction time. |
| `post_load_delay_ticks` | integer | `20` | 0-600 | Ticks between a chunk with redstone links loading and the targeted check of those links. This is the early net, for links that never register at all. Measured on games2 on 2026-09-23: after a cold load ten links were missing from their network and NOT ONE was caught here - at 20 ticks they all still looked correct and dropped out afterwards. Do not tune this hoping to catch that case; the sweep does. |

## `create_stock_link_keepalive` — Create Stock Link Keepalive

Factory Gauges stop ordering a fresh batch of something the vault is already full of after you rejoin, because the logistics links are kept reporting their contents even while nobody is nearby to make their chunks tick. · [full page](../modules/create_stock_link_keepalive.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `grace_ticks` | integer | `60` | 0-600 | How long factory panels are held after their chunk loads, before the condition below takes over. Covers the case where the chunks really did unload and the links have to register from scratch. |
| `hold_on_chunk_load` | boolean | `true` | — | Whether the chunk-load hold runs at all. The keepalive above is unaffected by this key. |
| `hold_until_network_reports` | boolean | `true` | — | Keep holding a gauge past grace_ticks while its network summary has no contributing links at all (InventorySummary.contributingLinks == 0). A fixed wait is always a guess; this is the fact the guess stood for. Bounded by max_hold_ticks. |
| `keepalive_interval_ticks` | integer | `5` | 1-19 | How often every tracked logistics link is re-stamped in Create's LINKS cache. That cache expires 20 ticks - one second - after a link last refreshed it, and only the link's own lazyTick() does that, which needs a TICKING chunk. Must stay below 20; 5 leaves a margin of four. |
| `max_hold_ticks` | integer | `600` | 60-6000 | Ceiling on how long one gauge may be held, counted from its chunk load. Only reached when a network genuinely has no contributing links - measured on five gauges whose network has none at all. They are then released and Create decides for itself again. |

## `create_water_wheel_unstucker` — Create Water Wheel Unstucker

Create water wheels that quietly stopped turning after their chunk was unloaded and reloaded get spotted and started again - automatically only where the repair is provably harmless (Create's own stress bookkeeping), and on demand with /vpaunstuck. · [full page](../modules/create_water_wheel_unstucker.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `auto_clear_phantom_stress` | boolean | `true` | — | Whether the automatic path may also drop an unloaded-member stress tally, not just a self-contradictory one. Subordinate to `clear_phantom_stress`. It never fires immediately: the tally must have been unchanged for ten seconds while the network stayed overstressed. That wait is what makes it safe - Create seeds the tally with the whole network when a world loads and only counts it down as members load, so a tally that is still shrinking means members are still arriving. Every such clear is logged as a warning with its numbers. |
| `auto_fix` | boolean | `false` | — | Whether the PERIODIC SWEEP may re-initialise a stalled wheel. false (default) = the sweep only detects and settles stress bookkeeping, never touching a block. This no longer governs the chunk-load path, which has its own key `auto_unstick_on_chunk_load` (default true) - the reload stall is what the module is for, while the sweep is a safety net over wheels that stalled for some other reason. |
| `auto_unstick_on_chunk_load` | boolean | `true` | — | Whether the targeted check that runs `post_load_delay_ticks` after a chunk with wheels loads may apply the full cure, re-initialising the wheel if nothing cheaper worked - the same thing `/vpaunstuck` does by hand. This is the situation the module exists for: a wheel loses its flow score across a chunk reload. A wheel a player has just placed uses a separate trigger and is never re-initialised. Independent of `auto_fix`, which governs only the periodic sweep. |
| `check_interval_ticks` | int | `100` | 20 ~ 1200 | How often (in ticks) all tracked water wheels are swept for stalls; only remembered wheel positions whose footprint chunks are already loaded are checked, never a global scan. The sweep is driven by the global server tick counter (server.getTickCount() % interval == 0), so every dimension is swept in the same tick. |
| `clear_phantom_stress` | boolean | `true` | — | Allows dropping a stale unloaded-member stress tally to cure a phantom 'Overstressed' network - automatically in the sweep when the tally is provably orphaned (stress or capacity charged while unloadedMembers == 0), and in the judgement case (real unloaded members, loaded members alone would fit) only via /vpaunstuck. false disables both paths. It does not gate the preceding network recompute, which always runs for an overstressed stalled wheel. Master switch: with it off, no path clears a tally at all. With it on, the automatic path is additionally gated by `auto_clear_phantom_stress`. |
| `hard_kick` | boolean | `true` | — | **Currently inert — nothing reads this key.** It was meant to allow a cheaper escalation step (detach and re-attach the wheel's kinetic network, the equivalent of wrenching it out and back in) before the break-and-replace re-init. That step was never wired up: `WaterWheelKinetics.softKick` and `hardKick` exist but have no callers anywhere in the module. The key is kept rather than removed so an existing config file does not silently change meaning. |
| `max_fix_attempts` | int | `3` | 1 ~ 10 | Consecutive re-init attempts per wheel before that wheel backs off for ~5 minutes (STALL_BACKOFF_TICKS = 6000) and the attempt counter resets; the first exhaustion also logs one WARN per wheel. Applies to every automatic trigger that may re-initialise - the chunk-load path as well as the sweep. On top of it sits a hard floor of one automatic re-init per wheel per minute (MIN_REINIT_INTERVAL_TICKS = 1200). `/vpaunstuck` ignores both, clears any pending backoff and does not count towards either. Every attempt is the same break-and-replace re-init; there is no cheaper first step. |
| `post_load_delay_ticks` | int | `60` | 0 ~ 600 | Delay in ticks between a chunk with water wheels loading - or a wheel being placed - and the targeted stall check for those wheels, so Create can finish its own init first. |
| `reinit_flood_ticks` | int | `6` | 1 ~ 40 | Ticks the wheel stays removed during a re-init so adjacent water can flood the gap and re-establish active flow before the wheel is placed back. |
| `reinit_large_wheels` | boolean | `false` | — | Whether large (multiblock) water wheels may be re-initialised. Off by default because Create rebuilds the multiblock through `LargeWaterWheelBlock.tick`, which calls `destroyBlock(center, false)` - without drops - so a re-init that goes wrong there costs the whole structure rather than one block. Large wheels are still detected and logged. |

## `custom_crafting_recipes` — Custom Crafting Recipes

Lets you write extra crafting recipes straight into the config file instead of a datapack - and ships a few by default: plain rails upgrade into powered/detector/activator rails, leather plus string makes a bundle, and (with Create installed) the Netherite Ingot is crafted from scrap and Powdered Obsidian with just one gold ingot left in the middle. · [full page](../modules/custom_crafting_recipes.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `recipes` | list | `List.of(SAMPLE_RECIPE, POWERED_RAIL_FROM_RAILS, DETECTOR_RAIL_FROM_RAILS, ACTIVATOR_RAIL_FROM_RAILS, NETHERITE_INGOT_FROM_POWDERED_OBSIDIAN) = ["vanillaplusadditions:giant_backpack;overpacked:giant_backpack;1;CDC\|ABA\|AAA;A=minecraft:leather,B=create:item_vault,C=minecraft:string,D=create:andesite_alloy", "vanillaplusadditions:powered_rail_from_rails;minecraft:powered_rail;6;R R\|RGR\|RDR;R=minecraft:rail,G=minecraft:gold_ingot,D=minecraft:redstone", "vanillaplusadditions:detector_rail_from_rails;minecraft:detector_rail;6;R R\|RPR\|RDR;R=minecraft:rail,P=minecraft:stone_pressure_plate,D=minecraft:redstone", "vanillaplusadditions:activator_rail_from_rails;minecraft:activator_rail;6;RSR\|RTR\|RSR;R=minecraft:rail,S=minecraft:stick,T=minecraft:redstone_torch", "minecraft:netherite_ingot;minecraft:netherite_ingot;1;PSP\|SGS\|PSP;P=create:powdered_obsidian,S=minecraft:netherite_scrap,G=minecraft:gold_ingot"]` | no spec range; per-entry validator requires 5 semicolon-separated parts, two parsable ResourceLocations and result_count 1-64 | List of custom SHAPED recipes in the format recipe_id;result_item;result_count;pattern;keys (pattern as AAA\|BBB\|AAA or "AAA" "BBB" "AAA"; keys as A=minecraft:green_wool,B=minecraft:chest; ingredients may be tags with a leading #). A recipe_id that matches an existing recipe replaces it. |
| `shapeless_recipes` | list | `List.of(SAMPLE_SHAPELESS_RECIPE) = ["minecraft:leather,minecraft:string->minecraft:bundle;1"]` | no spec range; per-entry validator requires an -> arrow, a non-empty ingredient part, a parsable result ResourceLocation and, if given, result_count 1-64 and a parsable recipe_id | List of custom SHAPELESS recipes in the format ingredient1,ingredient2,...->result_item[;result_count[;recipe_id]], with item IDs or #tags as ingredients. Omitting recipe_id auto-generates minecraft:shapeless_<result path>. |

## `death_coordinates` — Death Coordinates Announcer

When any player dies, everyone on the server gets a chat line naming the player, the exact X/Y/Z of the death and the dimension — and if the player who died was an operator, that line is clickable and teleports whoever clicks it to the spot, provided they themselves may run commands. · [full page](../modules/death_coordinates.md)

No settings of its own.

## `debug_overlay` — Debug Overlay

Wear Engineer's Goggles and press numpad + to switch on the mod's shared debug view - chunk-loader borders, Chunk Anchor areas and the guardian pets' extended stat boxes all appear and disappear together with this one key. · [full page](../modules/debug_overlay.md)

No settings of its own.

## `dispenser_bucket_guard` — Dispenser Bucket Guard

A dispenser that can't use its bucket — empty bucket with no fluid in front of it, or a water bucket aimed at a solid block — keeps the bucket instead of throwing it on the floor, so an automated water door survives a misfire. · [full page](../modules/dispenser_bucket_guard.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `play_fail_sound` | boolean | `true` | — | Play vanilla's "dispenser failed" click when a bucket is held back instead of thrown; false keeps a repeatedly triggered dispenser completely silent. |

## `end_conduit` — End Conduit

A craftable violet conduit for the End: surround it with 16 Glowstone, End Stone, End Stone Bricks or Sea Lanterns - no water needed - and it grants Conduit Power on dry End ground, which (with the End Oxygen module enabled) means you can breathe there indefinitely. · [full page](../modules/end_conduit.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `effect_radius_divisor` | int | `1` | 1 ~ 16 | Conduit Power radius = (actual frame count / 7 * 16) / this divisor, floored at 1 block (all integer division). 1 (default) = full vanilla Conduit Power radius: 32 blocks at a 16-block frame, 96 at a full 42-block frame. The module additionally clamps the value with Math.max(1, ...). |
| `min_frames` | int | `16` | 1 ~ 42 | Minimum frame-block count (Glowstone / End Stone / End Stone Bricks / Sea Lantern) required to activate the End Conduit. Vanilla conduit uses 16; 42 is the maximum the ring geometry can hold. Read live on both sides, so it also decides what the client renders as active. |

## `end_oxygen` — End Oxygen

The End has no breathable air: your air bubbles drain while you are there and you start taking damage once they run out, unless you carry a Create backtank (by default a diving helmet is required alongside it) or stand in Conduit Power, e.g. from an End Conduit. · [full page](../modules/end_oxygen.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `air_consumption_interval` | int | `2` | 1 ~ 300 | The number of ticks between each air depletion (1 = standard, higher = slower). |
| `backtank.backtank_depletion_rate` | int | `20` | 0 ~ 1000 | The number of ticks between each backtank air depletion (0 to disable depletion). |
| `backtank.requires_full_set` | boolean | `true` | — | Whether a diving helmet is required along with the backtank to breathe. |
| `conduit_power_grants_air` | boolean | `true` | — | Whether the Conduit Power effect (e.g. from an End Conduit) lets the player breathe freely in the End, refilling air each tick. Default true. |
| `damage_tick` | int | `20` | 1 ~ 200 | The interval (in ticks) at which damage is applied when out of air. |
| `out_of_air_damage` | double | `2.0` | 0.5 ~ 20.0 | The amount of damage to apply when the player is out of air in the End. |
| `water_breathing_effect_interval_bonus` | int | `4` | 0 ~ 100 | Additional ticks added to the consumption interval per level of Water Breathing effect. |

## `enhanced_ai_leader_loot` — Enhanced AI Leader Loot

Every banner-carrying "leader" mob from the Enhanced AI mod drops a handful of golden carrots (6-12) or golden apples (3-9) when you kill it - and, at a 1% chance, a single enchanted golden apple - on top of its normal loot; exactly one of those entries drops per leader, and the whole table is a single weighted config list you can rewrite. · [full page](../modules/enhanced_ai_leader_loot.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `leader_bonus_loot` | list | `DEFAULT_LEADER_BONUS_LOOT = ["minecraft:golden_carrot;50;6;12", "minecraft:golden_apple;49;3;9", "minecraft:enchanted_golden_apple;1;1;1"] (EnhancedAiLeaderLootConfig.java:14-18)` | no spec range; per-entry validator (EnhancedAiLeaderLootConfig.java:35-52) requires exactly 4 semicolon-separated parts, a parsable ResourceLocation, weight >= 1, min_count >= 1 and max_count >= min_count. It does NOT check that the item exists - an unknown id passes validation and is dropped later by the parser (EnhancedAiLeaderLootModule.java:79-85). | Bonus loot rolled in addition to the mob's normal death loot when an Enhanced AI "leader" mob dies. Format item_id;weight;min_count;max_count. It is one loot pool with exactly one roll, so precisely one entry is picked per leader kill, weighted; weights are relative and need not sum to 100 (the defaults happen to sum to 100, which is why the doc's 50/49/1 percentages read as literal chances). Count is a uniform range between min_count and max_count. |

## `flying_fish` — Flying Fish

Adds flying fish that leap out of warm oceans, plus Flying Fish Boots that let you sprint across the water surface, hop out of the water and glide back down. · [full page](../modules/flying_fish.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `boots_horizontal_boost` | double | `0.08` | 0.0 ~ 1.0 | Horizontal speed boost applied while sprinting over or through water with Flying Fish Boots. Added to the delta movement each tick along the horizontal look vector, then clamped to the hardcoded MAX_SURFACE_SPEED of 0.95. |
| `boots_vertical_boost` | double | `0.42` | 0.0 ~ 2.0 | Vertical launch strength applied when the boots trigger a flying-fish leap (used as a floor via Math.max on the current upward motion). |
| `leap_cooldown_ticks` | int | `14` | 1 ~ 200 | Cooldown between automatic flying-fish leaps from the water surface. ARCADE uses half of it (minimum 1). |
| `leap_mode` | enum | `LeapMode.DEFAULT` | DEFAULT \| ARCADE \| REALISTIC (LeapMode.java) | Auto-hop behaviour of the Flying Fish Boots: DEFAULT = automatic leaps when sprinting near the water surface (governed by leap_cooldown_ticks), ARCADE = leaps trigger any time the player touches water while sprinting, with the cooldown halved, REALISTIC = no automatic leaps at all, so only the horizontal water-skim boost remains (and with it no glide, because the glide grace is only set on a leap). |
| `max_glide_fall_speed` | double | `0.08` | 0.01 ~ 1.0 | Maximum downward speed while gliding after a water leap; lower values glide longer. Applies only during the 16-tick glide grace after a leap and only while out of water. |

## `food_effects` — Food Effects

Eating a configured item hands out the potion effects listed for it - and with Tough As Nails installed, the configured drinks and stews also refill the thirst bar; every listed item additionally becomes edible even on a full hunger bar. · [full page](../modules/food_effects.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `food_effects` | list | `["minecraft:cookie;minecraft:speed;160;1", "minecraft:rabbit_stew;toughasnails:internal_warmth;24000;0", "minecraft:mushroom_stew;toughasnails:internal_warmth;12000;0", "minecraft:beetroot_soup;toughasnails:internal_warmth;12000;0", "rottencreatures:magma_rotten_flesh;toughasnails:internal_warmth;6000;0", "toughasnails:sweet_berry_juice;toughasnails:internal_warmth;3600;0", "rottencreatures:frozen_rotten_flesh;toughasnails:internal_chill;6000;0", "toughasnails:cactus_juice;toughasnails:internal_chill;3600;0", "minecraft:golden_apple;toughasnails:thirst;600;0;0.25", "minecraft:enchanted_golden_apple;toughasnails:thirst;600;0;0.25", "minecraft:golden_carrot;toughasnails:thirst;600;0;0.25", "minecraft:glow_berries;minecraft:glowing;60;0", "toughasnails:melon_juice;minecraft:regeneration;60;0", "toughasnails:glow_berry_juice;minecraft:glowing;120;0", "toughasnails:chorus_fruit_juice;minecraft:jump_boost;240;1", "minecraft:enchanted_golden_apple;toughasnails:climate_clemency;6000;0;1.0", "create:sweet_roll;minecraft:speed;120;0", "create:bar_of_chocolate;minecraft:speed;600;0", "create:bar_of_chocolate;minecraft:jump_boost;600;0", "create:chocolate_glazed_berries;minecraft:speed;60;0", "create:chocolate_glazed_berries;minecraft:jump_boost;60;0"] - 21 entries covering 18 distinct items` | no spec range; defineList with a per-entry validator: 3 to 5 semicolon-separated parts, parts[0] and parts[1] must parse as ResourceLocations, duration_in_ticks >= 0, amplifier >= 0 (optional, default 0), chance between 0.0 and 1.0 (optional, default 1.0). The "new entry" template offered by the config UI is minecraft:apple;minecraft:speed;200;0;1.0. | Item-to-potion-effect table applied when a living entity finishes eating the item. Format: item_id;effect_id;duration_in_ticks;amplifier;chance. Several lines may target the same item (all of them are applied, each rolled separately). Entries whose item or effect is not registered are dropped silently at cache load. Every listed item is additionally made always-edible via the FOOD data component. |
| `thirst_effects` | list | `["minecraft:beetroot_soup;6;", "minecraft:mushroom_stew;2;", "minecraft:rabbit_stew;2;", "minecraft:melon_slice;2;"] - note the trailing semicolons, which split drops, so chance defaults to 1.0` | no spec range; defineList with a per-entry validator: 2 or 3 semicolon-separated parts, parts[0] must parse as a ResourceLocation, thirst_amount >= 0, chance between 0.0 and 1.0 (optional, default 1.0). Template: minecraft:beetroot_soup;6;1.0. | Item-to-thirst table. Format: item_id;thirst_amount;chance. Only ever read when Tough As Nails is loaded (loadThirstEffects returns immediately otherwise, FoodEffectsModule.java:121-124) and only applied to Players; the restored value is capped at 20. One entry per item (a HashMap, so a second line for the same item overwrites the first). The items ARE made always-edible even without Tough As Nails. |

## `free_anvil_repair` — Free Anvil Repair

Repairing a damaged tool, weapon or piece of armour in an anvil - with its material or by combining it with an unenchanted second copy - costs zero XP levels, works even on gear vanilla calls "Too Expensive!", restores 50% more durability per material unit by default, and accepts extra materials such as diamonds for netherite gear, prismarine shards for a trident or sticks for a bow. · [full page](../modules/free_anvil_repair.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `extra_repair_materials` | list | `minecraft:netherite_sword=minecraft:diamond, minecraft:netherite_pickaxe=minecraft:diamond, minecraft:netherite_axe=minecraft:diamond, minecraft:netherite_shovel=minecraft:diamond, minecraft:netherite_hoe=minecraft:diamond, minecraft:netherite_helmet=minecraft:diamond, minecraft:netherite_chestplate=minecraft:diamond, minecraft:netherite_leggings=minecraft:diamond, minecraft:netherite_boots=minecraft:diamond, create:netherite_diving_helmet=minecraft:diamond, create:netherite_diving_boots=minecraft:diamond, create:copper_diving_helmet=minecraft:copper_ingot, create:copper_diving_boots=minecraft:copper_ingot, minecraft:trident=minecraft:prismarine_shard, minecraft:bow=minecraft:stick` | — | Additional anvil repair materials (Quark-style, format item=material, one item may appear in several entries). These repairs are computed by this module and are free like regular material repairs (and follow repair_boost_percent). Entries whose item or material is not installed are skipped silently; the config validator additionally rejects entries with no '=', with '=' at either end, or with a second '='. |
| `free_combine_repair` | boolean | `true` | — | Combining two items of the same type costs no XP levels, as long as the sacrifice item is damageable and carries neither enchantments nor stored enchantments (pure durability merge). The kept item may be enchanted. |
| `free_material_repair` | boolean | `true` | — | Repairing with the item's repair material (e.g. diamonds) costs no XP levels. Also the master switch for extra_repair_materials: with false, those non-vanilla combinations stop working altogether instead of just costing XP. |
| `increase_prior_work_penalty` | boolean | `false` | — | Whether free repairs still double the hidden prior-work penalty (REPAIR_COST component via AnvilMenu.calculateIncreasedRepairCost) like vanilla does; default false so repairing does not make later enchant operations more expensive. |
| `repair_boost_percent` | int | `50` | 0 ~ 500 | Extra durability restored per material unit on every module-handled MATERIAL repair, vanilla material and extra_repair_materials alike: perUnit = max(1, maxDamage/4 * (100+value)/100). 0 = vanilla 25%/unit; the default 50 = 37.5%/unit, so 3 units fully repair an item instead of 4. Does NOT apply to combining two of the same item, and the JEI display ignores it. |

## `freecam_sublevel_noclip` — Freecam Sub-Level Noclip

The Freecam camera flies through an airship the way it flies through a mountain, instead of stopping dead at the hull while the terrain underneath stays passable. · [full page](../modules/freecam_sublevel_noclip.md)

No settings of its own.

## `glider_water_repair` — Glider Water Repair

A paraglider from the Gliders mod that a lightning strike left broken can be thrown into water - as a dropped item, never one sitting in a slot: it hisses, steams and glides again, though it stays as battered as the strike left it. · [full page](../modules/glider_water_repair.md)

No settings of its own.

## `haunted_house` — Haunted House

Inside Witch Villas, part of the mob spawning is taken over: spawns are swapped for an invisible stalker that only fades into view once you look straight at it, while a creeping darkness follows you from room to room. · [full page](../modules/haunted_house.md)

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

## `hostile_endermen` — Hostile Endermen

In the End, every enderman within 16 blocks attacks you on its own without having to be stared at first — a carved pumpkin still protects you, and they drop the hunt the moment you get out of range. · [full page](../modules/hostile_endermen.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `anger_duration` | int | `600 (DEFAULT_ANGER_DURATION)` | -1 ~ 2147483647 (INDEFINITE_ANGER ~ Integer.MAX_VALUE) | How long endermen stay angry in ticks (-1 for indefinite, applied as Integer.MAX_VALUE ticks), refreshed every second while a player stays within detection_range. |
| `debug_teleport_tracking` | boolean | `false` | — | Diagnostics: logs every player teleport together with up to 14 caller frames (VPA's own frames filtered out), which identifies the mod/feature that moved the player. `logPlayerTeleport` checks only the config flag, not `isModuleEnabled()` (HostileEndermenModule.java:123), so it keeps logging after a runtime `/vpa module disable hostile_endermen`. A module disabled at startup is a different case: `ModuleManager.initializeModules` initialises enabled modules only (ModuleManager.java:104-106), so `instance` is never set and the hook returns immediately. |
| `detection_range` | int | `16 (DEFAULT_DETECTION_RANGE)` | 1 ~ 128 | Range in blocks in which endermen in the End automatically become hostile, and beyond which they calm down again; the hard ceiling is the vanilla enderman follow range of 64, so larger values are capped by vanilla targeting anyway. |
| `respect_carved_pumpkin` | boolean | `true` | — | Keeps the vanilla carved pumpkin protection: players wearing a carved pumpkin (or a modded ender mask) are not attacked automatically; checked via NeoForge's CommonHooks.shouldSuppressEnderManAnger, so EnderManAngerEvent cancels also count. |
| `suppress_anticheese_teleport` | boolean | `true` | — | EnhancedAI compat: stops its "Teleport anti-cheese" goal from dragging players over to an enderman in the End as long as the player has not hit that enderman back (5 s window); no effect without EnhancedAI installed. |
| `suppress_teleport_attack` | boolean | `true` | — | Enderman Overhaul compat: stops its End Enderman from teleporting the player away on hit as long as the player has not hit that enderman back (5 s window); no effect without Enderman Overhaul installed. |

## `hostile_zombified_piglins` — Hostile Zombified Piglins

Zombified piglins come after you unprovoked - the module pins a grudge on them the moment they spawn and keeps renewing it for as long as you stay inside the detection range, so you never get the vanilla grace period; step far enough away and they forget you instantly. · [full page](../modules/hostile_zombified_piglins.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `anger_duration` | int | `200` | -1 ~ 2147483647 (Integer.MAX_VALUE) | Config comment: "How long zombified piglins stay angry in ticks (-1 for indefinite)". Effectively a two-state switch, NOT a duration. Any value >= 0 is written at HostileZombifiedPiglinsModule.java:146 and then immediately overwritten at L149 by startPersistentAngerTimer(), which in vanilla 1.21.1 rolls a fresh random 400-780 ticks (ZombifiedPiglin.java:176-177) - so 200 behaves exactly like 5 or like 2000000000. Only -1 is observably different, and it works through a separate path: maintainHostility re-sets the timer to Integer.MAX_VALUE once per second while a player is in range (L244-248). In all cases the anger is zeroed the moment no eligible player is inside detection_range. |
| `detection_range` | int | `32` | 1 ~ 128 | Config comment: "Range in blocks to detect players and become hostile". Radius of the axis-aligned box (getBoundingBox().inflate(range), HostileZombifiedPiglinsModule.java:164-166) in which the module looks for a player to pin the piglin's grudge on, and outside of which it wipes that grudge again. It is NOT attack range: the actual chase is vanilla's NearestAttackableTargetGoal, capped at the FOLLOW_RANGE attribute (35 blocks for a zombified piglin) and requiring line of sight, so values above ~35 only extend the zone in which a grudge is kept alive, not the distance from which piglins come at you. Creative and spectator players are never detected. |
| `target_switch_threshold` | double | `5.0` | 0.0 ~ 1.7976931348623157E308 (Double.MAX_VALUE) | Config comment: "Time in seconds before a zombified piglin can switch to a new nearest player target". Wall-clock seconds (`System.currentTimeMillis()`, converted to ms by `getTargetSwitchThresholdValue(true)`), so it is unaffected by TPS, lag or a paused single-player world. It does NOT measure how long the grudge has been held: the holder's timestamp is refreshed on every once-per-second pass in which it is still first in the query (HostileZombifiedPiglinsModule.java:213-218), so the clock only starts running once somebody else takes the front of the list. An old grudge is therefore no easier to displace than a fresh one. The "nearest" player is simply the first the level query returns - the list is never sorted - and on an actual switch the timestamp is not carried over fresh (:200-212), so the new holder settles only on the following pass. |

## `idle_gamerules` — Idle Gamerule Pause

While nobody is online, the server stops advancing day/night, weather and seasons, and starts them again the moment the first player logs in - so you don't come back to a world that drifted through three nights and a thunderstorm without you. · [full page](../modules/idle_gamerules.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `gamerules` | list | `List.of("doDaylightCycle", "doWeatherCycle", "doSeasonCycle") = ["doDaylightCycle", "doWeatherCycle", "doSeasonCycle"] (IdleGamerulesConfig.java:13-14, 29-32; generated in test-server/config/vanillaplusadditions-common.toml:561)` | no spec range; the per-entry validator requires a non-blank String. Whether the name is a real gamerule is settled at apply time, not here - an unknown one is named in the log rather than silently ignored. The new-element supplier for config editors is "doDaylightCycle". | Gamerules that are set to FALSE while no player is online and back to TRUE as soon as the first player joins, listed by their /gamerule name so modded rules work too (e.g. Serene Seasons' doSeasonCycle). Each name is looked up among the server's boolean gamerules and written directly; a name that is not one is skipped and reported in a WARN line, and the INFO line lists only what was really applied. The same rules are restored to TRUE when a server that is currently empty shuts down, so a paused world is not left frozen in level.dat. |

## `item_vault_viewer` — Item Vault Viewer

Hold Ctrl and right-click a Create Item Vault while wearing Engineering Goggles to see everything stored inside it in a searchable, sortable read-only grid — including vaults mounted on moving contraptions, where Create itself opens nothing. · [full page](../modules/item_vault_viewer.md)

No settings of its own.

## `minecart_chunk_loading` — Minecart Chunk Loading

A craftable rail that keeps the chunks around a traveling minecart loaded, so long-distance cart lines keep running instead of stalling at the edge of the loaded world. · [full page](../modules/minecart_chunk_loading.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `active_timeout_seconds` | int | `15` | 1 ~ 300 | How long a loader rail stays active (keeps chunks loaded) after the last minecart passed over it, in seconds; multiplied by 20 into ticks at MinecartChunkLoadingModule.java:154. MinecartChunkLoadingConfig.java:29-32. |
| `chunk_load_radius` | int | `2` | 0 ~ 8 | Chebyshev chunk radius force-loaded around an active loader rail (0 = only the rail's own chunk, 1 = 3x3, 2 = 5x5); this is the rolling-load lookahead. MinecartChunkLoadingConfig.java:22-27. |
| `only_while_players_online` | boolean | `true` | — | Only force-load chunks while at least one player is online; false keeps loading with nobody online (e.g. perpetual loops). Evaluated every server tick at MinecartChunkLoadingModule.java:168-181. MinecartChunkLoadingConfig.java:34-39. |
| `overlay.chunk_border_scan_radius` | int | `8` | 1 ~ 16 | Debug overlay: how many chunks around the player are scanned for loader rails to draw permanent chunk borders for. In a sub-section pushed as "overlay" (MinecartChunkLoadingConfig.java:41-45); the renderer clamps the value to 1..16 again at ChunkLoaderBorderRenderer.java:133. |
| `overlay.chunk_border_vertical_span` | int | `24` | 4 ~ 256 | Debug overlay: vertical extent in blocks above/below the rail of the rendered chunk border band (drawn as centerY-span .. centerY+span, ChunkLoaderBorderRenderer.java:77,103-110). MinecartChunkLoadingConfig.java:47-50. |

## `mo_arrows` — Mo' Arrows

An arrow crafted from an arrow and a fire charge: it flies burning, sets alight whatever it hits, and starts a real fire where it lands — the part even a Flame-enchanted bow has never done. · [full page](../modules/mo_arrows.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `light_fires` | boolean | `true` | — | Lets a Fire Arrow start a fire where it lands, exactly as a thrown fire charge would. With it off the arrow still burns in flight, still sets what it hits alight for five seconds and still lights TNT, campfires and candles — it simply leaves the ground alone. Only the block half is switchable; igniting on hit is vanilla's own behaviour for a burning arrow and cannot be separated from the arrow being lit. |

## `mob_cart_loader` — Mob Cart Loader

Two translucent machine blocks that capture a mob out of a pen and drop it into a parked minecart (or the seat of a standing Create train) - and back out again - with the captured mob spinning visibly inside and a comparator telling you whether it is hostile or friendly. · [full page](../modules/mob_cart_loader.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `check_interval_ticks` | int | `5` | 1 ~ 40 | How often (in ticks) each loader/unloader block scans the adjacent rail and pen; lower = more responsive, higher = cheaper. The check is global-phase (serverLevel.getGameTime() % interval), not per block, and the value is additionally clamped with Math.max(1, ...). |
| `create_trains.enabled` | boolean | `true` | — | Also load/unload mobs into the seats of Create train carriages (inert without Create; minecart handling is unaffected either way). Checked inside findTrackTarget, so turning it off makes the blocks minecart-only. |
| `create_trains.seat_search_radius` | double | `4.0` | 1.0 ~ 16.0 | How far (in blocks) a carriage seat may be from the found track block to still count; measured from the track block's centre, and the nearest matching seat wins. The same radius also inflates the AABB used to collect candidate carriages. |
| `create_trains.track_search_distance` | int | `3` | 1 ~ 8 | How many positions along the relevant direction the block scans for a Create track, starting at the block directly in front of the output (loader) / input (unloader) face - so 3 means that neighbour plus two more. The scan stops at the first block with a non-empty collision shape, so it never reaches through a wall. |

## `mob_drops` — Mob Drops

Gives any mob extra drops on death on top of its normal loot, configured as one line per drop - by default a wither skeleton drops its skull 12.5% of the time, a golden apple 40% and netherite scrap 10%, and a warden always drops 1-3 enchanted golden apples. · [full page](../modules/mob_drops.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `mob_drops` | list | `MobDropsConfig.DEFAULT_MOB_DROPS (MobDropsConfig.java:20-25) = List.of("minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.125", "minecraft:wither_skeleton;minecraft:golden_apple;0.4", "minecraft:wither_skeleton;minecraft:netherite_scrap;0.1", "minecraft:warden;minecraft:enchanted_golden_apple;1;3")` | — | List of additional mob drops. Format: mob_id;item_id;chance[;max_drops] - Example: minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.5;2 |

## `mob_glow` — Mob Glow Command

An operator command that puts a glowing outline on every mob of one chosen type in the dimension you are standing in - every creeper, every zombie in the loaded chunks - visible through walls until the time runs out or you clear it again. · [full page](../modules/mob_glow.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_duration` | int | `-1` | -1 ~ 2147483647 | Seconds the glow lasts when no duration is typed (the implicit "infinite"). -1 means Integer.MAX_VALUE ticks (~3.4 years, reported as "indefinitely"); any other value is used as seconds and is NOT checked against max_duration. |
| `max_duration` | int | `3600` | 0 ~ 2147483647 | Upper bound in seconds for an explicitly typed duration; 0 disables the check. It does not bound the default_duration path, and it does not reject zero or negative durations. |
| `max_mobs_per_command` | int | `100` | 0 ~ 2147483647 | Caps only the number reported in chat and the command's Brigadier return value; 0 means uncapped. It does NOT limit how many mobs actually receive the glow - despite the config comment claiming it does. |
| `require_op` | boolean | `true` | — | Whether /mobglow needs permission level 2. Read live in the command's requires predicate, so it takes effect without a restart. false opens every subcommand, including "all clear", to every player. |

## `mob_spawn_overlay` — Mob Spawn Overlay

Hold F3 and press M to light up every block around you where hostile mobs can spawn — red fields spawn them right now, yellow ones as soon as it gets dark, and a violet outline means a spider fits there too. · [full page](../modules/mob_spawn_overlay.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `color_spawn_at_night.alpha` | double | `0.45` | 0.0 ~ 1.0 | Alpha component for positions that only spawn mobs in the dark. |
| `color_spawn_at_night.blue` | double | `0.2` | 0.0 ~ 1.0 | Blue component for positions that only spawn mobs in the dark. |
| `color_spawn_at_night.green` | double | `0.85` | 0.0 ~ 1.0 | Green component for positions that only spawn mobs in the dark. |
| `color_spawn_at_night.red` | double | `1.0` | 0.0 ~ 1.0 | Red component for positions that only spawn mobs in the dark. |
| `color_spawn_now.alpha` | double | `0.55` | 0.0 ~ 1.0 | Alpha component for positions where mobs spawn right now. |
| `color_spawn_now.blue` | double | `0.15` | 0.0 ~ 1.0 | Blue component for positions where mobs spawn right now. |
| `color_spawn_now.green` | double | `0.15` | 0.0 ~ 1.0 | Green component for positions where mobs spawn right now. |
| `color_spawn_now.red` | double | `1.0` | 0.0 ~ 1.0 | Red component for positions where mobs spawn right now. |
| `color_spider_outline.alpha` | double | `0.9` | 0.0 ~ 1.0 | Alpha component of the outline drawn around spider-sized spots. |
| `color_spider_outline.blue` | double | `1.0` | 0.0 ~ 1.0 | Blue component of the outline drawn around spider-sized spots. |
| `color_spider_outline.green` | double | `0.3` | 0.0 ~ 1.0 | Green component of the outline drawn around spider-sized spots. |
| `color_spider_outline.red` | double | `0.75` | 0.0 ~ 1.0 | Red component of the outline drawn around spider-sized spots. |
| `display.scroll_speed` | double | `0.35` | 0.0 ~ 5.0 | How fast the stripes travel (blocks per second). |
| `display.see_through_blocks` | boolean | `false` | — | Draw markers through terrain (x-ray) instead of hiding them behind blocks. |
| `display.shimmer_strength` | double | `0.35` | 0.0 ~ 1.0 | Strength of the additive enchantment-style shimmer; 0 skips the whole shimmer pass. |
| `display.stripe_scale` | double | `0.5` | 0.05 ~ 4.0 | Width of one diagonal stripe in blocks — smaller means denser stripes. |
| `scan.horizontal_radius` | int | `16` | 4 ~ 48 | How far around the player spawn positions are scanned (blocks). |
| `scan.mark_spider_spots` | boolean | `true` | — | Additionally outline positions with enough room (2x2) for a spider to spawn. |
| `scan.max_markers` | int | `6000` | 100 ~ 60000 | Safety cap on how many markers a single scan may collect. |
| `scan.rescan_interval_ticks` | int | `10` | 1 ~ 100 | Ticks between rescans (20 = one second); lower reacts faster but costs more. |
| `scan.vertical_radius` | int | `8` | 2 ~ 32 | How far above/below the player spawn positions are scanned (blocks). |
| `toggle_key` | int | `77` | 32 ~ 348 | GLFW key code pressed together with F3 to toggle the overlay (77 = M); change it if another mod claims the same F3 combo. |

## `options` — VPA Options

Keeps named snapshots of your client settings — the whole options.txt or just the keybinds — so you can put your controls back exactly as they were after a modpack update or a misclick wrecks them. · [full page](../modules/options.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `auto_backup` | boolean | `true` | — | Automatically create a rotating backup at game start whenever the current options differ from the newest automatic snapshot. The check runs exactly once per launch (first client tick), not while playing. |
| `auto_backup_keep` | int | `10` | 1 ~ 100 | How many automatic backups (auto_* snapshots) to keep before the oldest ones are deleted. The auto_<stamp>-prerestore safety snapshots count against this budget too. |
| `full_options_backup` | boolean | `true` | — | Back up / restore the full options.txt (video, sound, chat, keybinds, ...); set to false to limit both backups and restores to keybinds (key_* lines) only. Note: the automatic pre-restore safety snapshot is always taken at full scope regardless of this setting. |

## `overpacked_extensions` — Overpacked Extensions

Open the compartments of the giant backpack you are wearing with a keypress instead of taking it off, and dial the full-backpack movement slowdown down to anything you like — including off entirely. · [full page](../modules/overpacked_extensions.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `backpack_keys_enabled` | boolean | `true` | — | Enable the keybinds that open the compartments of a worn giant backpack (main compartment on B by default; right/left unbound). define("backpack_keys_enabled", true) — OverpackedExtensionsConfig.java:47. Checked only server-side, in the packet handler. |
| `slowdown_multiplier` | double | `0.0` | 0.0 ~ 10.0 | Multiplier applied to the Overpacked slowdown effect (0.0 = no slowdown at all, 0.5 = half, 1.0 = original, 2.0 = double). defineInRange("slowdown_multiplier", 0.0, 0.0, 10.0) — OverpackedExtensionsConfig.java:42. |

## `pathfinder_quills` — Pathfinder Quills

Lets you craft Quark's Pathfinder's Quill yourself - one feather, one eye of ender and one block that matches the biome you want to find (sand for desert, podzol for old growth pine taiga, cherry leaves for cherry grove) - instead of having to trade a cartographer or wandering trader for it. · [full page](../modules/pathfinder_quills.md)

No settings of its own.

## `pet_potions` — Pet Potions

Splash a potion of Healing or Regeneration onto someone else's wolf, cat or horse that you accidentally hit, and it forgives you instead of hunting you down until you die. · [full page](../modules/pet_potions.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `allow_throwing_at_pets` | boolean | `true` | — | Let beneficial splash/lingering potions be thrown while aiming at a tamed animal (vanilla swallows that right-click - the client reports CONSUME for any tamed wolf - so without this the potion never leaves your hand). Gates ONLY the pass-through half (PetPotionsModule.java:140): calming still works when this is false, you just cannot aim at the animal itself. |
| `calm_feedback` | boolean | `true` | — | Play 4 heart particles above the animal and an amethyst chime (NEUTRAL, vol 0.7, pitch 1.5) when it is calmed, so it is visible that it worked. Fires only when anger towards the thrower was actually cleared. |
| `calming_effects` | list | `List.of("minecraft:instant_health", "minecraft:regeneration")` | elements must be String (defineList validator o -> o instanceof String); new entries default to "minecraft:instant_health" | Effect ids (namespace:path) that make an angry owned animal forgive the player who threw the potion; works for splash and lingering potions alike. An empty list switches calming off entirely (isCalming returns false immediately) without affecting the throw-at-pets fix. |
| `peace_duration_ticks` | int | `200` | 0 ~ 24000 | How long (in ticks) a calmed animal refuses to re-target the thrower; 0 disables the grace period - the anger is still cleared once, it may just come straight back. Only one window per pet exists, so a second player's calm overwrites the first player's protection. |

## `stackables` — Stackables

Raises the maximum stack size of items that vanilla keeps small or unstackable - potions, splash and lingering potions and tipped arrows stack to 64 by default, and so do stews, soups, ender pearls, eggs and a configurable list of items from other mods (eleven Tough As Nails entries plus Create's Builder's Tea ship in the default list). · [full page](../modules/stackables.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_potion_stack_size` | int | `64` | 1 ~ 64 | Default stack size for potions, splash potions, lingering potions and tipped arrows. Effective value is max(2, this) - setting 1 still yields 2, so vanilla's unstackable potions cannot be restored through this key (StackablesModule.java:73). The default was 16 until v0.9.3 (commit 73e2ffe). |
| `stackable_items` | list | `StackablesConfig.DEFAULT_STACKABLES (18 entries) = minecraft:mushroom_stew:64, minecraft:rabbit_stew:64, minecraft:beetroot_soup:64, minecraft:suspicious_stew:64, minecraft:ender_pearl:64, minecraft:egg:64, toughasnails:dirty_water_bottle:64, toughasnails:purified_water_bottle:64, toughasnails:apple_juice:64, toughasnails:cactus_juice:64, toughasnails:chorus_fruit_juice:64, toughasnails:glow_berry_juice:64, toughasnails:melon_juice:64, toughasnails:pumpkin_juice:64, toughasnails:sweet_berry_juice:64, toughasnails:ice_cream:64, toughasnails:charc_os:64, create:builders_tea:64` | per entry: at least two colons (the part before the last colon must itself contain a colon) and a stack part parsing to 1 ~ 64 | List of item entries to make stackable, each in the format namespace:id:stack (e.g. minecraft:mushroom_stew:64); the last part is the desired max stack size. Ids that are not present in the item registry are skipped (WARN only with debug_logging on), malformed entries are skipped without any log. The runtime parser is looser than the config validator (see notes 10). |

## `static_fov` — Static FOV

Your view stays at the same zoom level when you sprint, drink a Speed potion or fly in creative, instead of the camera pulling back every time you speed up — while zoom-in effects like drawing a bow still work normally. · [full page](../modules/static_fov.md)

No settings of its own.

## `stationary_chunk_loader` — Stationary Chunk Loader

A craftable Chunk Anchor block that, while it receives a redstone signal, keeps its own chunk (and optionally a ring of chunks around it) loaded and ticking with no player nearby - so farms, redstone clocks and Create contraptions keep running while you are away. · [full page](../modules/stationary_chunk_loader.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `chunk_load_radius` | int | `0` | 0 ~ 8 | Chunk radius (Chebyshev) force-loaded around a Chunk Anchor; 0 = only the anchor's own chunk, 1 = a 3x3 area, 2 = a 5x5 area, higher = more chunks kept loaded and ticking (more server load). Read when an anchor is forced and on resume (StationaryChunkLoaderModule.java:140,168; StationaryChunkLoaderManager.java:56-70). StationaryChunkLoaderConfig.java:19-23. |
| `only_while_players_online` | boolean | `true` | — | Only keep anchor chunks loaded while at least one player is online; anchors pause when the last player leaves and are re-forced on server start / first join (false = keep loading with nobody online). Evaluated every server tick at StationaryChunkLoaderModule.java:130-150. StationaryChunkLoaderConfig.java:25-30. |

## `texture_kill` — Texture Kill

Makes textures you list in the config invisible — either the whole image (the shipped default hides Create's contraption hats) or just a rectangle of it (the shipped default erases hat/accessory areas from zombie skins) — without editing a single file on disk. · [full page](../modules/texture_kill.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `erase_regions` | list | `62 entries covering 38 distinct texture files (TextureKillConfig.DEFAULT_ERASED_REGIONS, lines 23-90), e.g. "minecraft:textures/entity/zombie/drowned.png@32:0-64:16"` | — | List of rectangular texture regions to erase (make transparent) inside an otherwise untouched texture (format: namespace:textures/path.png@x1:y1-x2:y2, end coordinates exclusive; repeat the same path with different @-suffixes for multiple regions — getErasedRegions() groups them per ResourceLocation). Validator only requires the string to contain ":" and "@". |
| `killed_textures` | list | `List.of("create:textures/entity/train_hat.png", "create:textures/entity/logistics_hat.png") — 2 entries (TextureKillConfig.DEFAULT_KILLED_TEXTURES, lines 18-21)` | — | List of texture ResourceLocations to replace with a fully transparent 1x1 PNG (format: namespace:textures/category/name.png). Entries are accepted by the config validator if they merely contain ":"; entries that ResourceLocation.tryParse cannot parse are silently dropped at read time. |

## `tipped_arrows` — Tipped Arrows from Potions

Tipped arrows can be crafted with an ordinary potion in the middle of eight arrows, so you no longer need Dragon's Breath and a brewing detour just to tip a stack. · [full page](../modules/tipped_arrows.md)

No settings of its own.

## `train_chunk_loading` — Train Chunk Loading

Adds a blue Create train track that force-loads the chunks around any train rolling over it, so the drills, deployers and item transfers on board keep running even when no player is nearby. · [full page](../modules/train_chunk_loading.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `active_timeout_seconds` | int | `15` | 1 ~ 300 | How long a loader track stays active and keeps chunks loaded after the last train carriage passed over it. Converted to ticks as value * 20. |
| `chunk_load_radius` | int | `2` | 0 ~ 8 | Chebyshev chunk radius force-loaded around an active loader track (0 = only its own chunk, 1 = 3x3, 2 = 5x5); acts as the rolling-load lookahead for a moving train. |
| `only_while_players_online` | boolean | `true` | — | Only force-load chunks while at least one player is online; false keeps loading with nobody online (e.g. perpetual loops). |
| `overlay.chunk_border_scan_radius` | int | `8` | 1 ~ 16 | Debug overlay: how many chunks around the player are scanned for loader tracks to draw chunk borders for. |
| `overlay.chunk_border_vertical_span` | int | `24` | 4 ~ 256 | Debug overlay: vertical extent in blocks above and below the track of the rendered chunk border band. |

## `waystone_amethyst_repair` — Waystone Amethyst Repair

Amethyst shards repair the Waystones Warp Stone in an anvil - 25% of its maximum durability per shard, costing one XP level per shard, or nothing at all while the Free Anvil Repair module is enabled. · [full page](../modules/waystone_amethyst_repair.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `repair_materials` | list | `List.of("minecraft:amethyst_shard")` | no spec range; per-entry validator requires a String that ResourceLocation.tryParse accepts (WaystoneAmethystRepairConfig.java:50-52) | Item ids accepted as repair material in the anvil (amethyst shards by default). Entries are trimmed and resolved into a Set<Item>; entries whose item is not installed are skipped with a debug-only log line. Any listed material repairs at the same rate - there is no per-material weighting. |
| `repair_percent_per_unit` | int | `25` | 1 ~ 100 | How much of the item's maximum durability a single material unit restores, in percent (vanilla material repairs use 25). Applied as perUnit = max(1, maxDamage * percent / 100) with integer division, and units are consumed one at a time until the item is undamaged or the material stack runs out; the number of consumed units is both the material cost and (unless free_anvil_repair is enabled) the XP level cost. |
| `target_item` | string | `"waystones:warp_stone"` | no spec range; validator requires a String that ResourceLocation.tryParse accepts (WaystoneAmethystRepairConfig.java:44-45) | The damageable item made repairable by this module (Waystones Warp Stone by default). Resolved lazily by registry id and re-resolved whenever the string changes, so it takes effect without a restart; if the id is not installed (or unparsable) the module stays inert instead of erroring. |

## `wither_skeleton` — Wither Skeleton Enforcer

In chunks that belong to a Nether fortress (vanilla or Better Fortresses), a plain skeleton about to spawn is blocked and a wither skeleton takes its place - the rest of the Nether is left completely alone, despite what the README says. · [full page](../modules/wither_skeleton.md)

No settings of its own.

## `wolf_mount` — Wolf Mount

Hold Ctrl and right-click a huge, armored, tamed wolf to ride it like a horse — steer it, charge up big jumps, swim on it, fight from its back while it bites the nearest monster for you, and stop killing it by accident with your own sword swings. · [full page](../modules/wolf_mount.md)

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `combat.attack_creepers` | boolean | `true` | — | Let the ridden mount attack creepers, short-circuiting Wolf.wantsToAttack's blanket refusal (covers modded creepers that extend vanilla Creeper). Only reachable while a player is riding the wolf, so loose pets keep vanilla's caution. |
| `combat.defend_rider` | boolean | `true` | — | Master switch for the whole combat half: the mount attacks whatever attacks its rider, takes over the rider's own target when the rider strikes something, and (together with target_nearest) runs the periodic nearest-threat sweep. Off means none of the other combat.* keys do anything except attack_creepers, which is only consulted from those same paths and so goes dead too. |
| `combat.defend_rider_radius` | double | `32.0` | 0.0 ~ 128.0 | Maximum distance between mount and attacker for the defend behaviour to trigger, and the reach for "aggressors" (mobs already targeting rider or mount) in the sweep. Vanilla's own OwnerHurtByTargetGoal is capped at follow range (16), which is why this exists. |
| `combat.hostile_scan_radius` | double | `16.0` | 0.0 ~ 64.0 | How far the sweep looks for Enemy mobs that have NOT attacked yet; 0 makes the mount purely retaliatory, and bosses (Warden, Wither, Ender Dragon) are never picked up unprovoked. Dead unless defend_rider AND target_nearest are both on — it is only ever read from retargetNearest/isThreatTo. |
| `combat.retarget_margin` | double | `3.0` | 0.0 ~ 32.0 | How much closer (in blocks) a new threat has to be before the mount switches to it — pure hysteresis against flip-flopping. Only consulted when target_nearest is on. |
| `combat.rider_reach_bonus` | double | `2.0` | 0.0 ~ 8.0 | Extra ENTITY_INTERACTION_RANGE while mounted (transient ADD_VALUE modifier vanillaplusadditions:wolf_mount_reach), so a rider sitting ~2.7 blocks up can still reach the ground. Applied to any player riding any Wolf, eligible or not; 0 removes the modifier. |
| `combat.target_nearest` | boolean | `true` | — | Keep the mount on the NEAREST threat instead of the first one it locked onto. Also the second gate on the periodic sweep: retargetNearest only runs when defend_rider AND target_nearest are both true. |
| `combat.target_recheck_ticks` | int | `10` | 1 ~ 100 | How often (in ticks, measured on the rider's tickCount) the mount re-picks the nearest threat while ridden. Dead unless defend_rider AND target_nearest are both on. |
| `dismount_when_armor_removed` | boolean | `true` | — | Eject the rider the moment the mount's body armor is removed or breaks. Silently requires eligibility.require_body_armor to be on as well. |
| `eligibility.min_scale` | double | `2.0` | 1.0 ~ 64.0 | Minimum generic.scale a wolf needs to be rideable (vanilla wolves are 1.0; the "Sif" wolf from Grim Kingdoms is 3.25). Ignored when require_large_scale is off. |
| `eligibility.require_body_armor` | boolean | `true` | — | Only a wolf wearing canine body armor (vanilla minecraft:wolf_armor or Battle Dogs armor — anything that is an AnimalArmorItem with BodyType.CANINE) can be ridden. Switching it OFF silently disables dismount_when_armor_removed as well. |
| `eligibility.require_large_scale` | boolean | `true` | — | Only wolves scaled up via the vanilla generic.scale attribute can be ridden. Switching it OFF also widens protection.owner_damage_immunity to every tamed wolf you own, because that handler shares the same isLargeEnough() gate. |
| `eligibility.require_tamed_owner` | boolean | `true` | — | Only a wolf that is tamed and belongs to the rider can be ridden. |
| `eligibility_recheck_ticks` | int | `20` | 1 ~ 200 | How often (in ticks, on the rider's tickCount) an ongoing ride is re-checked against the eligibility rules; the same tick also re-applies or heals the reach modifier. |
| `hud.armor_warning_threshold` | double | `0.25` | 0.0 ~ 1.0 | Below this fraction of remaining armor durability the bar pulses red and the rider gets a one-off action bar warning (message.vanillaplusadditions.wolf_mount.armor_low); 0 disables it. The latch re-arms when the armor is healthy again or the ride ends. Client-only read. |
| `hud.compact_mount_health` | boolean | `true` | — | Cancel vanilla's multi-row VEHICLE_HEALTH layer and draw a single ten-heart row showing health as a fraction (vanilla would spend three rows on a 350 HP wolf). Client-only read. |
| `hud.show_armor_bar` | boolean | `true` | — | Show the mount's body armor durability while riding — the number that actually matters with battle_dogs armor, since it absorbs the damage. Draws nothing (and consumes no HUD row) when the armor is absent or undamageable. Client-only read; may differ between client and server. |
| `movement.backward_multiplier` | double | `0.25` | 0.0 ~ 1.0 | Backwards input scaling (vanilla horses use 0.25); applied whenever the rider's forward input is <= 0. |
| `movement.float_in_water` | boolean | `true` | — | Keep the mount at the surface instead of sinking, by pulsing the jump flag from tickRidden (vanilla's FloatGoal only runs server-side, while a ridden entity is driven by the client). Undocumented in the config comment: the same code also fires in LAVA (isInLava()), not only water. |
| `movement.instant_jump` | boolean | `false` | — | Jump at full power on tap instead of charging the meter by holding the key; min_jump_charge is then ignored. |
| `movement.jump_strength` | double | `1.6` | 0.0 ~ 8.0 | Multiplies getJumpPower(charge); a wolf's generic.jump_strength is only 0.42, and the default puts a full charge at roughly 2.5 blocks. |
| `movement.min_jump_charge` | double | `0.15` | 0.0 ~ 1.0 | Jump scale at the shortest possible tap, as a fraction of a full charge; the client's 0..90 charge is mapped onto min_jump_charge..1.0 (vanilla horses effectively use 0.4). |
| `movement.speed_multiplier` | double | `1.0` | 0.1 ~ 3.0 | Multiplies the wolf's movement speed attribute while ridden; MUST match between client and server or the server's "moved too quickly" check rubber-bands the rider. |
| `movement.strafe_multiplier` | double | `0.5` | 0.0 ~ 1.0 | Sideways input scaling (vanilla horses use 0.5). Read client-side from a non-synced COMMON config — keep it identical on both sides. |
| `protection.owner_damage_immunity` | boolean | `true` | — | A tamed wolf that passes the scale gate takes no damage at all from the player that owns it, sweeping-edge splash included. Cancelled at LivingIncomingDamageEvent/HIGHEST, so no i-frames are burned, lastHurtByMob is never set, the sword and the wolf armor take no durability, and Thorns cannot reflect. NOTE: with the defaults this is NOT limited to mounts — see notes. |
| `protection.owner_immunity_only_when_ridden` | boolean | `false` | — | Narrow the owner immunity to a wolf whose controlling passenger is that same owner — i.e. the wolf the damaging player is riding right now, not just any ridden wolf. |
| `protection.owner_immunity_requires_armor` | boolean | `false` | — | Narrow the owner immunity to wolves that actually wear canine body armor. |
| `protection.suppress_ridden_knockback` | boolean | `true` | — | Cancel LivingKnockBackEvent for any wolf whose controlling passenger is a Player — from every source, not just the owner — because vanilla applies sweep knockback before the damage, so cancelling the damage cannot undo the shove. |
