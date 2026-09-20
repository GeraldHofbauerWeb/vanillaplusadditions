# Wolf Mount

> **TL;DR** — Hold Ctrl and right-click a big, armoured, tamed wolf to ride it like a horse: steer
> it, charge a jump, swim on it, fight from its back — and stop killing it by accident with your own
> sword.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `wolf_mount` |
| **Side** | Client + Server |
| **Requires** | — |
| **Works with** | Grim Kingdoms: structures & ruins, Creeper Overhaul |
| **Download** | [`vpa_wolf_mount.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_wolf_mount.jar) · also needs `vpa_core` |
| **Config section** | `[modules.wolf_mount]` |
| **Since** | `v1.0.0-beta.74` |
<!-- vpa:meta:end -->

## What it does

Hold the mount modifier — **left Ctrl** by default, rebindable in Controls as *Mount Wolf* — and
right-click a wolf that is big enough, tamed and wearing body armour. WASD steers, the mouse turns
the mount, space charges a jump on the same meter horses use, and sneaking gets you off again.

Without the modifier a right-click falls through to vanilla, so feeding, dyeing the collar,
equipping and shearing armour, repairing it with an armadillo scute, taming, breeding and the
sit/stand toggle all keep working unchanged.

While you are up there:

* the mount **fights alongside you** — it bites whatever bites you, takes over the target you strike,
  and keeps switching to the nearest threat instead of chasing the first one it saw;
* it **cannot be hurt by you**, sweeping-edge splash included, and it is not shoved around while you
  steer it;
* it **swims** instead of walking along the bottom;
* the HUD shows its armour durability and collapses its health into one row.

With the defaults **no naturally occurring wolf qualifies**: the scale gate wants `generic.scale`
≥ 2.0 and a vanilla wolf is 1.0. The module was built around **Sif**, the giant wolf from *Grim
Kingdoms: structures & ruins* — who turned out not to be a mod entity at all, but a plain
`minecraft:wolf` on a spawn egg with `generic.scale = 3.25`, 350 max health, 25 attack damage,
armour 12 and permanent Resistance IV. There is no code integration with that mod and no dependency
on it; any sufficiently large wolf works, and [*Getting a wolf that
qualifies*](#getting-a-wolf-that-qualifies) below summons one from a command.

## Why it exists

Riding a wolf is the new part. The four vanilla behaviours below are the ones that had to be
corrected before the ride was worth having.

### Your own sweep kills your own wolf

`Player.attack` hits every living thing in the swept box that is not you, not the main target and
not on your team — a tamed wolf is none of those:

```java
for (LivingEntity livingentity2 : this.level()
        .getEntitiesOfClass(LivingEntity.class, p_36347_.getBoundingBox().inflate(1.0, 0.25, 1.0))) {
    if (livingentity2 != this && livingentity2 != p_36347_ && !this.isAlliedTo(livingentity2) && …) {
        float f5 = this.getEnchantedDamage(livingentity2, f7, damagesource) * f2;
        livingentity2.knockback(0.4F, …);      // ← knockback first
        livingentity2.hurt(damagesource, f5);  // ← damage second
    }
}
```

Two things follow from that order. Cancelling the damage cannot undo the shove, which is why
knockback is suppressed separately. And the same `damagesource` instance is reused for the direct
hit and for every sweep victim, so one attacker check covers both cases.

### A ridden wolf cannot swim

Goals tick in `Mob.serverAiStep`, which is **server-side only** — and `serverAiStep` is `final`, so
it could not have been overridden anyway. A ridden entity is driven by the other side:

```java
private void travelRidden(Player p_278244_, Vec3 p_278231_) {
    Vec3 vec3 = this.getRiddenInput(p_278244_, p_278231_);
    this.tickRidden(p_278244_, vec3);
    if (this.isControlledByLocalInstance()) {   // ← the client, while a player rides
        this.setSpeed(this.getRiddenSpeed(p_278244_));
        this.travel(vec3);
```

`FloatGoal` is what keeps a loose wolf at the surface by pulsing its jump control. Nobody pulses it
on the client, so a ridden wolf walks along the bottom of the lake.

### Vanilla refuses to let a wolf touch a creeper

```java
public boolean wantsToAttack(LivingEntity p_30389_, LivingEntity p_30390_) {
    if (p_30389_ instanceof Creeper || p_30389_ instanceof Ghast || p_30389_ instanceof ArmorStand) {
        return false;
```

Sensible for a pet that dies to the blast. Silly for an armoured mount that can eat one — and the
creeper is walking at the rider either way, so ignoring it does not avoid the explosion, it
guarantees it. The blanket `instanceof` is also why **modded** creepers were ignored: Creeper
Overhaul's whole family extends vanilla `Creeper`.

### A wolf never re-picks a live target

`OwnerHurtByTargetGoal` sits at priority 1 in the wolf's target selector, and a running goal is only
displaced by a goal with a *better* priority number:

```java
public boolean canBeReplacedBy(WrappedGoal p_26003_) {
    return this.isInterruptable() && p_26003_.getPriority() < this.getPriority();
}
```

So the first mob that hits the rider owns the mount for the rest of the fight: it keeps snapping at
an archer 25 blocks away while a zombie stands in its face. The other half of the problem is what a
tamed wolf attacks unprompted at all — goal 7, `NearestAttackableTargetGoal<>(this,
AbstractSkeleton.class, false)`, and nothing else.

## In detail

### Mounting

The modifier is client-only state, so the gesture starts on the client — an `EntityInteract` event,
main hand only — and travels to the server as `MountWolfPacket`. Everything in that packet is
untrusted: the handler re-checks the module state, that the id really names a `Wolf`, the distance,
that the player is neither spectating nor already a passenger, and the full eligibility gate.

Cancelling the client event does **not** stop vanilla's own interaction:
`MultiPlayerGameMode.interact` sends `ServerboundInteractPacket` one line *before* it fires the
event. The server therefore still performs the vanilla right-click, which on a tamed wolf toggles
sit/stand; the mount packet travels the same connection immediately after and is guaranteed to be
processed second, so the handler can undo it. Mounting clears the sit order and the sitting pose,
stops the navigation, clears the wolf's current target, snaps the rider's yaw and pitch to the
wolf's, and plays a wolf ambient sound at 0.4 volume. `tickRidden` re-clears the sit order every
tick server-side as a backstop.

The sit clear is load-bearing twice over: a sitting wolf renders haunched under its rider, and both
`OwnerHurtByTargetGoal` and `OwnerHurtTargetGoal` bail out on `isOrderedToSit()` — which would
silently disable the whole fight-alongside behaviour.

### Who may be ridden

| Gate | Default | Config key |
|---|---|---|
| Scaled up via `generic.scale` | ≥ 2.0 | `eligibility.require_large_scale`, `eligibility.min_scale` |
| Tamed and owned by the rider | on | `eligibility.require_tamed_owner` |
| Wearing canine body armour | on | `eligibility.require_body_armor` |
| Alive, grown, nobody else aboard | always | — |

An ongoing ride is re-checked every `eligibility_recheck_ticks` (20, measured on the rider's
`tickCount`) against the same rules minus the "nobody else aboard" one; a ride that stops qualifying
ends there and then. Armour coming off is handled faster than that — see below.

**Both kinds of armour count.** The test is structural — *is this an `AnimalArmorItem` with
`BodyType.CANINE`* — rather than a check against a specific item class, so vanilla
`minecraft:wolf_armor` and all four `battle_dogs` tiers pass it without this module importing a
single `battle_dogs` class, and third-party canine armour is picked up for free.

<table>
<tr>
<td width="80" align="center"><img src="../img/items/wolf_armor_iron.png" width="48"></td>
<td width="80" align="center"><img src="../img/items/wolf_armor_gold.png" width="48"></td>
<td width="80" align="center"><img src="../img/items/wolf_armor_diamond.png" width="48"></td>
<td width="80" align="center"><img src="../img/items/wolf_armor_netherite.png" width="48"></td>
</tr>
<tr>
<td align="center">Iron</td>
<td align="center">Gold</td>
<td align="center">Diamond</td>
<td align="center">Netherite</td>
</tr>
</table>

The four [Battle Dogs](battle_dogs.md) tiers. Vanilla wolf armour satisfies the gate exactly the
same way. This cannot use `Wolf.hasArmor()`, which is hardcoded to `Items.WOLF_ARMOR` and would
reject our own armour.

### Steering

| | This module | Vanilla horse |
|---|---|---|
| Forward | rider's `zza`, unscaled | same |
| Backwards | × `backward_multiplier` (0.25), whenever forward input ≤ 0 | 0.25 |
| Sideways | × `strafe_multiplier` (0.5) | 0.5 |
| Speed | `generic.movement_speed` × `speed_multiplier` (1.0) | `generic.movement_speed` |
| Rotation | rider's yaw, half the rider's pitch | same |

A wolf's `generic.movement_speed` is **0.3**; a horse rolls between 0.1125 and 0.3375 (0.225 on
average) through the same `getRiddenSpeed` path. An unmultiplied wolf is therefore already a good
horse. Step-up height needs no help either: `LivingEntity.maxUpStep` returns
`getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f`, so the mount walks up full
blocks by itself.

### Jumping

Space charges the same meter a horse uses — while mounted it replaces the XP bar, which is vanilla
HUD behaviour for anything implementing `PlayerRideableJumping`, not something this module draws.
Releasing it fires on the next tick the mount is on the ground and locally controlled.

The client sends the charge as `floor(jumpRidingScale · 100)`, so 0–100 while you hold the key.
Anything from 90 up counts as a full charge and the rest is mapped **linearly** onto
`min_jump_charge`..1.0. Vanilla's horse maps the same number onto `0.4 + 0.4 · charge/90` and reaches
1.0 only at 90, which makes the shortest tap already 40 % as strong as a full hold. At the default
floor of 0.15 the meter is worth watching.

The launch itself mirrors `AbstractHorse.executeRidersJump` — vertical velocity set to the jump
power, `hasImpulse`, and a forward nudge of `0.4 · charge` if the rider is holding forward — with one
addition:

| | Value |
|---|---|
| Jump power | `getJumpPower(charge) × jump_strength` = `0.42 × charge × 1.6` at the defaults |
| Shortest tap | `0.42 × 0.15 × 1.6` = 0.1008 blocks/tick — a nudge |
| Full charge | 0.672 blocks/tick, against a player's 0.42 |
| `instant_jump` | a tap is a full charge; `min_jump_charge` is then ignored |

The multiplier is not cosmetic: a wolf's `generic.jump_strength` is **0.42**, exactly a player's,
because the wolf never overrides the attribute's default. Unmultiplied that is a one-block hop under
a mount three times the size of a horse. The config comment puts a full charge at roughly 2.5
blocks; nothing in this repository measures it.
<!-- TODO: measure the actual apex in game — a plain ballistic integration from 0.672 comes out
     nearer 2.9 blocks, so the 2.5 in the config comment may be conservative. -->

A latch (`vpaIsJumping`) is cleared only on a grounded tick *without* a pending charge, so a charge
released on the same tick the mount lands cannot fire twice.

### Swimming

`float_in_water` re-creates `FloatGoal`'s effect on whichever side is actually driving, straight
from `tickRidden`:

```java
boolean swimming = this.isInLava()
        || this.isInWater() && this.getFluidHeight(FluidTags.WATER) > this.getFluidJumpThreshold();
this.setJumping(swimming);
```

Note the `isInLava()`: the mount bobs in lava as well as in water. The config comment does not
mention that.

**Clearing the flag again is not optional.** The only thing that normally lowers `jumping` on a mob
is `JumpControl.tick()`, which is also server-only. Left raised on the client, `LivingEntity.aiStep`
answers it with `jumpFromGround()` on every grounded tick — the mount would hop nonstop after its
first swim.

### Protection from its own owner

The damage is cancelled at `LivingIncomingDamageEvent` with `EventPriority.HIGHEST`, which fires
right after the invulnerability checks and before everything else. `hurt()` therefore returns false
outright, and none of the follow-on effects happen:

| Not consumed | Why it matters |
|---|---|
| Invulnerability frames | The mount keeps them for a real threat in the same tick |
| `lastHurtByMob` | The wolf never registers its owner as an attacker, so it cannot turn on you |
| Weapon durability | The hit never happened |
| Armour durability | Same |
| Thorns | Enchanted wolf armour cannot reflect your own damage back at you |

**By default this is not limited to the mount.** With `owner_immunity_only_when_ridden` and
`owner_immunity_requires_armor` both off, every tamed wolf you own that passes the scale gate is
immune to you — armoured or not, ridden or not, standing three chunks away or under your sword. And
`isLargeEnough()` returns true unconditionally when `require_large_scale` is off, so switching that
gate off makes *all* of your tamed wolves immune to you.

| `owner_damage_immunity` | `…only_when_ridden` | `…requires_armor` | Who is immune to you |
|---|---|---|---|
| `true` (default) | `false` | `false` | every tamed wolf you own at or above `min_scale` |
| `true` | `true` | `false` | only the wolf you are riding right now |
| `true` | `false` | `true` | every owned wolf at or above `min_scale` that wears canine armour |
| `false` | — | — | nobody; vanilla behaviour |

Knockback is a separate switch because of the ordering above. `suppress_ridden_knockback` cancels
`LivingKnockBackEvent` for **any** wolf whose controlling passenger is a `Player` — matched
structurally, because the event carries no damage source, and therefore from every source, not only
from the rider. A mount under a rider's control is not shoved around at all any more.

### Fighting from the saddle

You fight with your own weapon; riding changes nothing about your attacks except the reach. Sitting
on a scale-3.25 wolf raises your eyes about 2.7 blocks, so a mounted player gets a transient
`ENTITY_INTERACTION_RANGE` modifier (`rider_reach_bonus`, +2.0) and can still reach the ground.

The mount picks up a target from three places:

| Trigger | Event | New target |
|---|---|---|
| Something damages the rider | `LivingIncomingDamageEvent` | the attacker |
| The rider strikes something | `AttackEntityEvent` | whatever the rider hit |
| Periodic sweep, every `target_recheck_ticks` (10) | `PlayerTickEvent.Post` | the nearest threat |

All three go through the same attackable test: alive, neither the rider nor the wolf, and accepted
by `Wolf.wantsToAttack` — except that a `Creeper` is decided by `attack_creepers` (on) instead.
Ghasts, armour stands, tamed animals including your other pets and tamed horses therefore stay off
the list; **creepers are the one vanilla exclusion this module overrides**, and because Creeper
Overhaul's family extends vanilla `Creeper`, that override covers those too.

A single incoming hit or a strike of your own only re-points the mount within `defend_rider_radius`
(32). If it already holds a live, still-attackable target, the new one has to be `retarget_margin`
(3.0) blocks closer to win — pure hysteresis, so two mobs at roughly the same distance cannot make
it flip-flop and bite neither.

**The sweep** is the half vanilla cannot do. It scans out to `max(defend_rider_radius,
hostile_scan_radius)`, drops a target that has died, become off-limits or left that radius, and
picks the nearest of two kinds of threat, each with its own reach:

| Kind | Test | Reach |
|---|---|---|
| Aggressors | a `Mob` already targeting rider or mount | `defend_rider_radius` (32) |
| Hostiles that have not done anything yet | `instanceof Enemy`, not a boss | `hostile_scan_radius` (16) |

Passive and neutral bystanders are never picked up, however close they stand — the local cows are
safe. Warden, Wither and Ender Dragon are never picked up unprovoked; once one of them attacks it
becomes an ordinary aggressor, because at that point the fight is happening anyway. Setting
`hostile_scan_radius` to 0 makes the mount purely retaliatory.

The biting itself is vanilla `MeleeAttackGoal` throughout — only the target is ever set here. One
pleasant consequence: `battle_dogs` still applies its Sharpness bonus (+0.5 + 0.5 per level) to
bites made from under a rider.

### What the rider sees

Two rows on the right, above the hotbar.

**The armour bar is the important one.** With `battle_dogs` armour the wolf's *health does not move
at all*: that module absorbs 100 % of the damage and drains one durability point per damage point,
so durability is the real health pool — and when it runs out the armour breaks,
`dismount_when_armor_removed` fires and the rider is dropped mid-fight. Ten icons, tinted by tier
(an explicit dye wins; otherwise the tier is read off the item id, so third-party canine armour gets
a sane colour for free). Below `armor_warning_threshold` (0.25) the row pulses red on a 900 ms sine
and the rider gets one action-bar line — once per damage run, not once per frame; the latch re-arms
when the armour is healthy again or the ride ends. Armour that is absent or undamageable draws
nothing and consumes no row.

**The health row is collapsed.** `Gui.getVehicleMaxHearts` caps at 30 hearts, so a 350 HP wolf fills
three rows that never visibly move — 30 px of screen spent on a constant. `compact_mount_health`
cancels vanilla's layer and draws a single ten-heart row showing health as a fraction. Turn it off
and vanilla's rows come back with the armour bar stacked on top of them.

The armour row fills **left to right**, the heart row above it right to left. That mismatch is
vanilla's: `Gui.renderVehicleHealth` counts `l - l1 * 8 - 9` while the armour bar counts `x + i * 8`,
and the shared `armor_half` sprite is filled on its *left* half. Drawing armour with the heart
geometry points the half icon's filled side away from the full icons beside it, which reads as a hole
in the bar.

No packet is involved: body armour reaches the client as a complete `ItemStack` through
`ClientboundSetEquipmentPacket`, damage component included.

### Getting a wolf that qualifies

Sif is a plain `minecraft:wolf` with tuned attributes, so he can be rebuilt from a command — handy
for trying the module out without hunting down a Grim Kingdoms ruin. Swap the owner for your own name
or UUID; a plain `Owner:"PlayerName"` works on a server that has seen that player, because
`OldUsersConverter` resolves it against the profile cache.

```mcfunction
summon minecraft:wolf ~ ~ ~ {CustomName:'{"text":"Sif"}',CustomNameVisible:1b,PersistenceRequired:1b,Owner:"Gerre01",Sitting:0b,CollarColor:14b,Health:350f,attributes:[{id:"minecraft:generic.scale",base:3.25},{id:"minecraft:generic.max_health",base:350},{id:"minecraft:generic.attack_damage",base:25},{id:"minecraft:generic.armor",base:12}],active_effects:[{id:"minecraft:resistance",amplifier:3,duration:-1,show_particles:0b,ambient:1b}],body_armor_item:{id:"vanillaplusadditions:wolf_armor_netherite",count:1}}
```

Three things are load-bearing:

* **`Owner` alone tames him.** `TamableAnimal.readAdditionalSaveData` calls `setTame(true, false)`,
  and that `false` skips `applyTamingSideEffects()`, which would otherwise set max health back to 40
  and heal him to it.
* **`Health:350f` has to be set explicitly.** The `max_health` attribute raises the ceiling, not the
  current value; without it he spawns at a wolf's default health.
* **The `body_armor_item` above is a Battle Dogs item.** With that module absent use
  `minecraft:wolf_armor` instead — the mount gate accepts either.

He is 3.25 blocks of wolf; summon him outdoors, not in a two-block corridor. For a portable version,
put the same data on a spawn egg — which is exactly how Grim Kingdoms ships him, in an invisible item
frame:

```mcfunction
give @s minecraft:wolf_spawn_egg[minecraft:entity_data={id:"minecraft:wolf",CustomName:'{"text":"Sif"}',PersistenceRequired:1b,Owner:"Gerre01",Health:350f,attributes:[{id:"minecraft:generic.scale",base:3.25},{id:"minecraft:generic.max_health",base:350},{id:"minecraft:generic.attack_damage",base:25},{id:"minecraft:generic.armor",base:12}],body_armor_item:{id:"vanillaplusadditions:wolf_armor_netherite",count:1}}]
```

<!-- vpa:config:start -->
## Configuration

Section `[modules.wolf_mount]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_wolf_mount-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

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
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| The config is a NeoForge **COMMON** config | NeoForge does not sync it, and the client is authoritative for a ridden entity. `enabled` and every `movement.*` key must be identical on client and server: a mismatch either freezes the mount or trips the server's "moved too quickly" check. The `hud` section is the documented exception — it is read client-side only and may differ. |
| `dismount_when_armor_removed` with `require_body_armor` off | Does nothing. The handler returns early unless **both** are on, whatever `dismount_when_armor_removed` says, and `stillEligible` stops testing for armour as well. The config comment does not mention it. |
| `hostile_scan_radius`, `target_recheck_ticks`, `attack_creepers` with `defend_rider` or `target_nearest` off | Dead. The sweep runs only when `defend_rider` **and** `target_nearest` are both true, and `attack_creepers` is only ever consulted from the defend and sweep paths. |
| `defend_rider_radius` above 16 | An upper bound, not a promise. A wolf's `generic.follow_range` is 16, and while any vanilla target goal is running, `TargetGoal.canContinueToUse` clears a target further away than that on its next tick. Read off vanilla's code, not measured. |
| Owner immunity with the defaults | Covers every tamed wolf you own at or above `min_scale`, not only the one you are riding — and every tamed wolf you own at all if `require_large_scale` is off. |
| An **unridden** owned wolf and your sweep | Still receives the harmless 0.4-strength nudge. It takes no damage. Removing the shove would mean mixing into `Player.attack` itself, which is far more invasive than the symptom warrants. |
| Knockback suppression | Applies to any wolf whose controlling passenger is a player, from **every** source — an arrow, an explosion, another player. Not just from the owner. |
| `rider_reach_bonus` | Applied by `EntityMountEvent` to any player mounting any wolf, eligible or not. Setting it to 0 removes the modifier instead of adding one. |
| Module disabled | The keybind is registered regardless, so *Mount Wolf* still appears in the Controls screen; and the mixin is still applied, so every wolf in the game answers `instanceof PlayerRideableJumping`. Nothing else runs: `ModuleManager` never initialises a disabled module, the static instance stays null, and `isModuleActive()` is false. |
| Enabling the module at runtime | Needs a restart: a module that was off at startup was never initialised. Disabling at runtime takes effect immediately instead — every handler and the mixin re-read the config, so a ride in progress stops responding to its rider. |
| Vanilla wolf armour and the armour bar | The bar shows durability, which is the health pool only for `battle_dogs` armour. Vanilla `minecraft:wolf_armor` goes through vanilla's own absorption, so the wolf's health moves as well. |
| Grim Kingdoms | Not required and not integrated. It only supplies a conveniently large wolf; with the default `min_scale` of 2.0 you otherwise have to scale one up yourself. |
| Testing | This repository has no unit tests, and none of the numbers above were measured in game. Everything on this page is read off the source. |

## Under the hood

| File | Role |
|---|---|
| `modules/wolf_mount/WolfMountModule.java` | Everything that plain events can do: mounting, upkeep, immunity, combat |
| `modules/wolf_mount/WolfMountRules.java` | The eligibility predicates, shared by the module and the mixin |
| `modules/wolf_mount/network/MountWolfPacket.java` | Client → server mount request |
| `modules/wolf_mount/client/WolfMountKeybinds.java` | The modifier key |
| `modules/wolf_mount/client/WolfMountClientEvents.java` | The Ctrl + right-click gesture |
| `modules/wolf_mount/client/WolfMountArmorHud.java` | Armour row, compact health row, low-armour warning |
| `mixin/wolf_mount/WolfMountMixin.java` | Steering, jumping, floating, seating, dismounting |
| `standalone/wolf_mount/WolfMountStandalone.java` | `@Mod("vpa_wolf_mount")` entry point |

**The AI needed no suppression at all** — the single most useful finding while building this.
`Mob.serverAiStep` runs the goals and writes `xxa`/`zza` through the move control and `yHeadRot`
through the look control, but `LivingEntity.aiStep` then routes into `travelRidden`, whose first act
is `getRiddenInput(...)` — our override ignores its argument entirely — and `tickRidden` overwrites
the rotations right after. The one goal that moves the entity directly, `LeapAtTargetGoal`, already
returns false from `canUse()` while `hasControllingPassenger()`. Because the *target*-selecting goals
keep running, the mount fights alongside its rider with no extra machinery; overriding `isImmobile()`
to stand the AI down would have broken exactly that, since both goal selectors sit behind the same
`isEffectiveAi()` branch.

**Mixin style.** One class, targeting `Wolf`, with no `@Inject` and no `@Overwrite` anywhere. Every
hook it needs (`getControllingPassenger`, `tickRidden`, `getRiddenInput`, `getRiddenSpeed`,
`positionRider`, `getDismountLocationForPassenger`) is declared on `Mob`/`LivingEntity`/`Entity` and
not on `Wolf` itself, and `@Inject` requires the target method to exist in the target class — so the
overrides are declared in the mixin and merged. It declares `extends TamableAnimal` because Mixin
needs the declared superclass to be in the target's hierarchy for `super.` calls to resolve. It is
listed in the `"mixins"` array of `vanillaplusadditions.mixins.json`, **never** `"client"`:
`LocalPlayer.jumpableVehicle()` does an `instanceof PlayerRideableJumping` check client-side and the
server needs `getControllingPassenger` to agree.

**`getControllingPassenger` consults `isModuleActive()` and nothing else** — deliberately. It feeds
`Entity.isControlledByLocalInstance()`, which decides which side owns the vehicle's movement. Reading
gameplay config there would let a client/server config mismatch freeze the mount, so scale, owner and
armour are enforced at mount time and by the periodic recheck instead.

**Seat position is free.** `EntityType.WOLF` already declares `passengerAttachments(new Vec3(0.0,
0.81875, -0.0625))`, and the attachment scales with `generic.scale`. At 3.25 the seat lands at
y ≈ 2.66 against a 2.76-block hitbox — just below the withers — and a live scale change tracks
automatically. Only the *dismount* location is overridden: `Entity`'s default answer is "the top of my
bounding box", which is a 2.8-block drop every time. The override mirrors `AbstractHorse`: the
rider's dominant side first, then the other, then the wolf's own position, scanning upwards from the
wolf's feet for a pose that fits.

**Events.**

| Event | Bus | Purpose |
|---|---|---|
| `RegisterPayloadHandlersEvent` | mod | Registers `MountWolfPacket` play-to-server on registrar `"1"` |
| `RegisterKeyMappingsEvent` | mod, client | The mount modifier — registered unconditionally |
| `EntityMountEvent` | game | Applies and removes the reach modifier |
| `PlayerTickEvent.Post` | game | Target sweep, eligibility recheck, reach-modifier self-heal |
| `LivingEquipmentChangeEvent` | game | Ejects the rider when `BODY` armour comes off |
| `LivingIncomingDamageEvent` | game | Two handlers: owner immunity at `HIGHEST`, rider defence at default priority |
| `LivingKnockBackEvent` | game | Suppresses knockback on a ridden wolf |
| `AttackEntityEvent` | game | The mount takes over the rider's target |
| `PlayerInteractEvent.EntityInteract` | game, client | The Ctrl + right-click gesture |
| `RenderGuiLayerEvent.Pre` / `.Post` | game, client | Compact health row, armour row |

**Why the damage hook is `LivingIncomingDamageEvent`.** `battle_dogs` uses `LivingDamageEvent.Pre`,
which fires far later — past the i-frame bookkeeping, past `setLastHurtByMob`, past armour
absorption. The two modules compose cleanly precisely because they hook different points of the same
call: when the immunity cancels, the `battle_dogs` handler is never reached.

**The reach modifier** is transient, so it is never written to disk; the worst case for a leaked
modifier is a single session, and the recheck tick heals it anyway (logout while mounted, dimension
change, death, a crash mid-ride).

**The keybind reads raw window state** (`InputConstants.isKeyDown`, or `glfwGetMouseButton` if it is
bound to a mouse button) rather than `KeyMapping.isDown()`, because this is a "held while clicking"
check and not a discrete press.

**The server-side distance guard is 64 blocks, not 8.** `MAX_MOUNT_DISTANCE_SQR` is 64.0 and
documented as "squared", but the check squares it again:

```java
if (player.distanceToSqr(wolf) > MAX_MOUNT_DISTANCE_SQR * MAX_MOUNT_DISTANCE_SQR) {
```

Harmless, because the gesture needs a real right-click on the entity to happen at all, but the
constant does not mean what its name says.

**Standalone jar.** `vpa_wolf_mount` ships the one mixin in the both-sides array and no data files of
its own; the keybind label and the low-armour message live in `vpa_core`, which every module jar
requires. Both strings exist in `en_us`, `de_de` and `es_es`; `cs_cz`, `de_at` and `fr_fr` fall back
to English.

**Porting checklist.** The module rests on four `protected` hooks with no NeoForge event coverage.
Any port past 1.21.1 must re-verify the `travelRidden` branch of `LivingEntity.aiStep`,
`travelRidden` itself, and the signatures of `tickRidden`, `getRiddenInput` and `getRiddenSpeed`.

## See also

* [Battle Dogs](battle_dogs.md) — the canine armour this pairs with, and the 100 % absorption the
  armour bar exists for
* [Companion Armor](../guides/companion-armor.md) — how that armour is enchanted and repaired
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
