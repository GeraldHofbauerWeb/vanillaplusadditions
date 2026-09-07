# Wolf Mount

Lets a big, armored, tamed wolf be ridden like a horse — steered, jumped, and fought from.

Built for **Sif**, the giant wolf from *Grim Kingdoms: structures & ruins*. Sif turned out not to
be a mod entity at all: he is a plain `minecraft:wolf` handed out as a spawn egg inside an invisible
item frame, carrying `generic.scale = 3.25`, 350 max health, 25 attack damage, armor 12 and
permanent Resistance IV. This module therefore depends on **no** foreign mod — it works on any
sufficiently large vanilla wolf.

## Requirements

None. Grim Kingdoms is not needed (it only supplies a conveniently large wolf), and
`battle_dogs` is not needed either — see *Armor* below.

> **Multiplayer:** the module config is a NeoForge COMMON config, which is **not** synced. `enabled`
> and the movement multipliers must be identical on client and server. The client is authoritative
> for a ridden entity, so a mismatch either freezes the mount (module off on one side) or triggers
> the server's "moved too quickly" check (different `speed_multiplier`).

## Mounting

Hold the mount modifier — **left Ctrl** by default, rebindable in Controls as
*Mount Wolf* — and right-click the wolf. The same gesture the cat and axolotl guardian
inventories use.

Without the modifier a right-click falls through to vanilla, so **feeding, dyeing the collar,
equipping and shearing armor, repairing it with an armadillo scute, taming, breeding and the
sit/stand toggle all keep working unchanged.** Dismount by sneaking, as with any vehicle.

A wolf is rideable when all of the following hold (each individually switchable):

| Gate | Default | Config key |
|---|---|---|
| Scaled up via `generic.scale` | ≥ 2.0 | `require_large_scale`, `min_scale` |
| Tamed and owned by the rider | on | `require_tamed_owner` |
| Wearing body armor | on | `require_body_armor` |
| Grown, alive, nobody else aboard | always | — |

### Armor: both kinds count

The check is structural — *is this an `AnimalArmorItem` with `BodyType.CANINE`* — rather than a
test against a specific item class. That covers vanilla `minecraft:wolf_armor` **and** all four
`battle_dogs` tiers, without this module importing a single `battle_dogs` class. So
`vpa_wolf_mount` loads and works with `battle_dogs` disabled or absent, and any third-party canine
armor is picked up for free.

Note this cannot use `Wolf.hasArmor()`: that is hardcoded to `Items.WOLF_ARMOR` and would reject
our own armor.

## Steering and jumping

WASD steers, the mouse turns the mount, space jumps with a charge meter (the same one horses use —
it replaces the XP bar while mounted, exactly as vanilla does for a horse).

Sideways and backwards movement are scaled down like a horse's (`strafe_multiplier` 0.5,
`backward_multiplier` 0.25). Step-up height is raised to a full block automatically by vanilla
whenever a player controls an entity, so the mount walks up single blocks without jumping.

Two numbers here are not arbitrary. A wolf's `generic.jump_strength` is **0.42 — exactly a
player's**, so an unmultiplied jump is a one-block hop, which looks absurd under a mount three
times the size of a horse; `jump_strength` defaults to 1.6, which puts a full charge at roughly
2.5 blocks. And vanilla's horse maps the charge meter onto 0.4–1.0, making a tap already 40 %
as strong as a full hold — `min_jump_charge` lowers that floor to 0.15 so the meter is worth
watching.

The mount also **swims** rather than sinking. That needs explicit handling: the vanilla `FloatGoal`
that keeps a loose wolf at the surface only runs server-side, but a ridden entity is driven by the
client, so nothing was setting the jump flag there. `float_in_water` re-creates the goal's effect on
whichever side is actually driving — and, just as importantly, clears the flag again on dry land.
The only thing that normally lowers it is `JumpControl.tick()`, which is *also* server-only, so a
flag left raised makes `LivingEntity.aiStep` answer with `jumpFromGround()` every tick: the mount
would hop nonstop after its first swim.

The wolf's own AI keeps running and does **not** fight the rider — see *Implementation notes*.

## Owner damage immunity

A rideable wolf takes **no** damage from the player that owns it, including **sweeping-edge splash
damage**, which is the usual way to kill your own wolf by accident while fighting next to it.

Because the damage is cancelled at the earliest possible point, none of the follow-on effects
happen either: no invulnerability frames are burned (so the mount keeps them for a real threat in
the same tick), the wolf never registers its owner as an attacker (so it does not turn on you),
your weapon takes no durability for the hit, the wolf's armor takes none, and **Thorns does not
reflect your own damage back at you** — a real footgun with enchanted wolf armor.

Vanilla applies sweep *knockback* before the damage, so cancelling the damage cannot undo the
shove. A ridden mount therefore also has knockback suppressed outright
(`suppress_ridden_knockback`), which is correct on its own terms: a mount should not be pushed
around while its rider is steering it.

> **Known limitation:** an *unridden* owned wolf still receives the harmless 0.4-strength sweep
> nudge. It takes no damage. Removing the nudge would require mixing into `Player.attack` itself,
> which is far more invasive than the symptom warrants.

## Fighting from the saddle

You fight normally with your own weapon — riding changes nothing about your attacks. Since sitting
on a scale-3.25 wolf raises your eyes roughly 2.7 blocks, mounted players get a temporary
`entity_interaction_range` bonus (`rider_reach_bonus`, default +2.0) so the ground is still in
reach. It is a transient modifier, removed on dismount and self-healed every recheck tick.

The mount fights alongside you: it attacks whatever attacks you, and takes over your target when
you strike something. Vanilla's own exclusions stay intact — it still refuses to attack creepers,
ghasts and the owner's other pets, because the decision routes through `Wolf.wantsToAttack`.

**It always goes for the nearest threat** (`combat.target_nearest`, on by default). Vanilla's target
goals only ever fire on an *empty* target slot, so without this the first mob to hit you owns the
mount for the whole fight — it keeps snapping at an archer 25 blocks away while a zombie stands in
its face. Every `combat.target_recheck_ticks` (10) the mount re-picks the closest threat around it
and drops a target that has died, become off-limits or left `combat.defend_rider_radius`. A switch
needs `combat.retarget_margin` (3.0) blocks of daylight between old and new target, otherwise two
mobs at roughly the same range would make it flip-flop every recheck and bite neither.

The sweep looks for two kinds of threat, each with its own reach:

- **Aggressors** — anything already targeting you or the mount — out to `combat.defend_rider_radius`
  (32). That includes neutrals you picked a fight with.
- **Hostiles that have not done anything yet**, out to `combat.hostile_scan_radius` (16). This is
  the half that actually matters: a tamed vanilla wolf attacks *skeletons* unprompted and nothing
  else (goal 7 of its target selector), so without it the mount stares right past the zombie
  standing in its face. Set the radius to 0 to make the mount purely retaliatory.

Passive and neutral bystanders are never picked up, however close they stand — the local cows are
safe. Neither are bosses (Warden, Wither, Ender Dragon) attacked unprovoked; once one of them
attacks first it becomes a normal aggressor, because at that point the fight is happening anyway.

**Creepers are the one vanilla exclusion this module overrides** (`combat.attack_creepers`, on by
default). `Wolf.wantsToAttack` refuses them outright, which is right for a pet that dies to the
blast and wrong for an armored mount that one-shots them — and since the creeper is walking at the
rider regardless, ignoring it does not avoid the explosion, it only guarantees it. This is also why
**modded creepers were being ignored**: Creeper Overhaul's whole family (`BaseCreeper`,
`NeutralCreeper`, `PassiveCreeper`, `WaterCreeper`) extends vanilla `Creeper`, so a single
`instanceof` was rejecting all of them. Ghasts stay excluded — a melee mount cannot reach one.

The biting itself is entirely vanilla `MeleeAttackGoal`. One nice side effect: `battle_dogs` still
applies its Sharpness bonus to bites made from under a rider.

## What the rider sees

Riding shows two bars on the right, above the hotbar.

**The armor bar is the important one.** With `battle_dogs` installed, canine body armor absorbs
**100 %** of incoming damage and drains one durability point per damage point — so the mount's
*health never moves* until the armor breaks. Durability is the real health pool, and when it hits
zero the armor breaks, `dismount_when_armor_removed` fires and the rider is dropped mid-fight. Ten
icons, tinted by tier (an explicit dye wins over the tier colour). Below
`hud.armor_warning_threshold` (25 %) the row pulses red and the rider gets one action bar line —
once per damage run, not once per frame.

The tier colour is read off the **item id**, not an `instanceof WolfArmorItem` test, so the HUD
keeps the module's independence from `battle_dogs` and gives third-party canine armor a sane colour
for free.

The armor row fills **left to right**, the heart row above it right to left — that mismatch is
vanilla's, not ours: `Gui.renderVehicleHealth` counts `l - i * 8 - 9` while `Gui.renderArmor` counts
`x + i * 8`, and the `armor_half` sprite is filled on its *left* half. Give the armor row the heart
geometry and the half icon's filled side points away from the full icons beside it, which reads as a
hole in the bar.

**The health row is collapsed.** Vanilla draws one heart per 2 HP capped at 30 hearts, so a 350 HP
Sif fills three rows that never visibly move — 30 px of screen spent on a constant.
`hud.compact_mount_health` replaces them with a single ten-heart row showing health as a fraction.
Turn it off and vanilla's rows come back with the armor bar stacked on top.

No packet is involved: body armor reaches the client as a complete `ItemStack` through
`ClientboundSetEquipmentPacket`, damage component included. The `hud` config section is read
client-side only, so it may safely differ between client and server.

## Configuration

Section `[modules.wolf_mount]` in `config/vanillaplusadditions-common.toml`.

| Key | Default | Range | Meaning |
|---|---|---|---|
| `enabled` | `true` | — | module on/off |
| `debug_logging` | `AUTO` | AUTO/ON/OFF | per-module log verbosity |
| `eligibility.require_large_scale` | `true` | — | require a scaled-up wolf |
| `eligibility.min_scale` | `2.0` | 1.0–64.0 | `generic.scale` threshold (Sif is 3.25) |
| `eligibility.require_tamed_owner` | `true` | — | require ownership |
| `eligibility.require_body_armor` | `true` | — | require canine body armor |
| `movement.speed_multiplier` | `1.0` | 0.1–3.0 | **must match client and server** |
| `movement.strafe_multiplier` | `0.5` | 0.0–1.0 | sideways input scaling |
| `movement.backward_multiplier` | `0.25` | 0.0–1.0 | backwards input scaling |
| `movement.jump_strength` | `1.6` | 0.0–8.0 | jump power multiplier (see below) |
| `movement.min_jump_charge` | `0.15` | 0.0–1.0 | jump scale at the shortest tap |
| `movement.instant_jump` | `false` | — | skip the charge meter |
| `movement.float_in_water` | `true` | — | swim at the surface instead of sinking |
| `protection.owner_damage_immunity` | `true` | — | immune to the owner |
| `protection.owner_immunity_requires_armor` | `false` | — | narrow it to armored wolves |
| `protection.owner_immunity_only_when_ridden` | `false` | — | narrow it to the active mount |
| `protection.suppress_ridden_knockback` | `true` | — | no knockback while ridden |
| `combat.defend_rider` | `true` | — | mount fights alongside the rider |
| `combat.defend_rider_radius` | `32.0` | 0.0–128.0 | max mount↔attacker distance |
| `combat.target_nearest` | `true` | — | keep the mount on the nearest threat |
| `combat.retarget_margin` | `3.0` | 0.0–32.0 | how much closer a new threat must be to win |
| `combat.target_recheck_ticks` | `10` | 1–100 | how often the nearest threat is re-picked |
| `combat.hostile_scan_radius` | `16.0` | 0.0–64.0 | reach for hostiles that have not attacked yet |
| `combat.attack_creepers` | `true` | — | override vanilla's creeper exclusion for the mount |
| `combat.rider_reach_bonus` | `2.0` | 0.0–8.0 | extra reach while mounted |
| `hud.show_armor_bar` | `true` | — | show the mount's armor durability |
| `hud.compact_mount_health` | `true` | — | one heart row instead of vanilla's three |
| `hud.armor_warning_threshold` | `0.25` | 0.0–1.0 | when the bar pulses and warns |
| `dismount_when_armor_removed` | `true` | — | eject when the armor comes off |
| `eligibility_recheck_ticks` | `20` | 1–200 | how often an ongoing ride is re-validated |

## Summoning a test Sif

Sif is a plain `minecraft:wolf` with tuned attributes, so he can be rebuilt from a command. Handy
for testing the module without hunting down a Grim Kingdoms ruin. Swap the `Owner` array for your
own UUID — a plain `Owner:"PlayerName"` also works on a server that has seen that player, because
`OldUsersConverter` resolves it against the profile cache.

```mcfunction
summon minecraft:wolf ~ ~ ~ {CustomName:'{"text":"Sif"}',CustomNameVisible:1b,PersistenceRequired:1b,Owner:"Gerre01",Sitting:0b,CollarColor:14b,Health:350f,attributes:[{id:"minecraft:generic.scale",base:3.25},{id:"minecraft:generic.max_health",base:350},{id:"minecraft:generic.attack_damage",base:25},{id:"minecraft:generic.armor",base:12}],active_effects:[{id:"minecraft:resistance",amplifier:3,duration:-1,show_particles:0b,ambient:1b}],body_armor_item:{id:"vanillaplusadditions:wolf_armor_netherite",count:1}}
```

Two things are load-bearing:

- **`Owner` alone tames him.** `TamableAnimal.readAdditionalSaveData` calls `setTame(true, false)` —
  the `false` skips vanilla's taming side effects, which would otherwise stomp max health back down
  to 40 and attack damage to 4. The attributes are read before that, so they survive.
- **`Health:350f` has to be set explicitly.** The `max_health` attribute raises the ceiling but not
  the current value; without it Sif spawns at a wolf's default 8 HP.

He is 3.25 blocks of wolf — summon him outdoors, not in a 2-block corridor.

For a portable version, put the same data on a spawn egg:

```mcfunction
give @s minecraft:wolf_spawn_egg[minecraft:entity_data={id:"minecraft:wolf",CustomName:'{"text":"Sif"}',PersistenceRequired:1b,Owner:"Gerre01",Health:350f,attributes:[{id:"minecraft:generic.scale",base:3.25},{id:"minecraft:generic.max_health",base:350},{id:"minecraft:generic.attack_damage",base:25},{id:"minecraft:generic.armor",base:12}],body_armor_item:{id:"vanillaplusadditions:wolf_armor_netherite",count:1}}]
```

That is exactly how Grim Kingdoms ships him: a spawn egg in an invisible item frame.

## Implementation notes

**The AI needed no suppression at all** — the single most useful finding while building this.
Vanilla's ridden pipeline is generic on `LivingEntity` and already discards the AI's output:
`Mob.serverAiStep` runs the goals and writes `xxa`/`zza` through the move control and `yHeadRot`
through the look control, but `LivingEntity.aiStep` then routes into `travelRidden`, whose first
act is `getRiddenInput(...)` — our override ignores its argument entirely — and `tickRidden`
overwrites the rotations immediately after. The one goal that moves the entity directly,
`LeapAtTargetGoal`, already disables itself while a passenger is in control.

This matters twice over: `Mob.serverAiStep` is `final` and could not have been overridden anyway,
and because the *target*-selecting goals keep running, the mount fights alongside its rider with no
extra machinery. Overriding `isImmobile()` to stand the AI down would have broken exactly that —
both goal selectors sit behind the same `isEffectiveAi()` branch.

**Seat position is free.** `EntityType.WOLF` already declares a passenger attachment, and it scales
with `generic.scale` through `EntityDimensions.scale`. At 3.25 the seat lands at y ≈ 2.66 against a
2.76-block hitbox — just below the withers. No `getPassengerAttachmentPoint` override needed, and
live scale changes track automatically.

**Why the mount gesture needs a packet.** The modifier key is client-only state, so the decision
has to start on the client. Cancelling the client-side interact event does *not* stop the vanilla
`ServerboundInteractPacket` — `MultiPlayerGameMode.interact` sends it one line *before* running the
interact event. The server therefore still performs the vanilla right-click, which on a tamed wolf
toggles sit/stand. `MountWolfPacket` travels the same connection immediately after, so it is
guaranteed to be processed second, and the mount handler clears the sit state again. `tickRidden`
re-asserts it every tick as a backstop.

**Why the damage hook is `LivingIncomingDamageEvent`.** It fires right after the invulnerability
checks and before everything else, so cancelling makes `hurt()` return false outright. The
alternative, `LivingDamageEvent.Pre` (which `BattleDogsModule.onWolfHurt` uses), fires roughly 600
lines later — past the i-frame bookkeeping, past `setLastHurtByMob`, past armor absorption. The two
modules compose cleanly precisely because they hook different points of the same call: when the
immunity cancels, battle_dogs' handler is simply never reached.

**Mixin surface.** One class, `mixin/wolf_mount/WolfMountMixin`, targeting `Wolf`, with no
`@Inject` anywhere: every hook it needs is declared on `Mob`/`LivingEntity`/`Entity` and not on
`Wolf` itself, and `@Inject` requires the target method to exist in the target class. The overrides
are declared in the mixin and merged. It must be listed in the `"mixins"` array of
`vanillaplusadditions.mixins.json`, **never** `"client"` — `LocalPlayer.jumpableVehicle()` does an
`instanceof PlayerRideableJumping` check client-side and the server needs `getControllingPassenger`
to agree.

**Porting checklist.** The module rests on four `protected` hooks with no NeoForge event coverage.
Any port past 1.21.1 must re-verify `LivingEntity.aiStep` (the `travelRidden` branch),
`LivingEntity.travelRidden` itself, and the signatures of `tickRidden` / `getRiddenInput` /
`getRiddenSpeed`.

**Side effect worth knowing:** while mounted the XP bar is replaced by the horse jump meter. That is
vanilla HUD behaviour for anything implementing `PlayerRideableJumping`, not something this module
draws.

## See also

- [Companion Armor](COMPANION_ARMOR.md) — the `battle_dogs` wolf armor this pairs with
- [Module System](MODULE_SYSTEM.md)
