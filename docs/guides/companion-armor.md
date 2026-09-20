# Companion Armor & Cat Guardian Systems

This document describes the durability and attack bonuses for wolf and cat armor, as well as the unique systems of the Cat Guardian module.

## Armor Tiers & Stats

Both Wolf and Cat armor share the same tier stats.

| Material | Durability | Attack Bonus |
| :--- | :--- | :--- |
| Iron | 200 | +1.0 |
| Gold | 100 | +2.0 |
| Diamond | 400 | +3.0 |
| Netherite | 600 | +4.0 |

*Note: Netherite armor is fire-resistant.*

## Wolf Armor (Battle Dogs)

The Battle Dogs module provides tiered armor for tamed wolves.

- **Damage Absorption**: Similar to vanilla armadillo wolf armor, all tiered wolf armor in this mod absorbs **100%** of incoming damage to the wolf, losing 1 durability point for every point of damage absorbed. This makes wolves invincible until the armor breaks.
- **Attack Bonus**: Equipping armor increases the wolf's attack damage based on the tier (see table above).
- **Bite Animation**: Vanilla wolves have **no** attack animation at all — `WolfModel` never reads `attackAnim`, so an attacking wolf deals its damage without moving a pixel. Battle Dogs adds one: the head snaps down on every landed bite, with a small shoulder lean behind it. Barely noticeable on a pet trotting behind you, very noticeable while riding one (`wolf_mount`), where the head fills the lower half of the screen.
  - **The swing timer has to be wound up first.** `LivingEntity.updateSwingTime()` is called from exactly three places in vanilla — `Player`, `RemotePlayer` and `Monster.aiStep`. A wolf is a `TamableAnimal`, so none applies: its `swingTime` never advances and `attackAnim` is pinned at 0 forever. That is the real reason vanilla ships no wolf attack animation. Worse, `swinging` is cleared *only* inside `updateSwingTime()` while `swing()` refuses to fire again — and therefore to broadcast its `ClientboundAnimatePacket` — while the flag is up, so untended the first bite of a wolf's life latches it and every later bite is swallowed. `mixin/battle_dogs/WolfSwingTimeMixin` mirrors what `Monster` does, on **both** sides: the client to drive the animation, the server to keep the packets coming.
  - **Two animations, because one is not enough.** `WolfBiteAnimationMixin` snaps the head in `WolfModel.setupAnim`; `client/WolfBiteLunge` shoves the whole wolf forward and down on the render pose via `RenderLivingEvent`. The head snap is the nicer motion but dies in any pack with **Entity Model Features + Fresh Animations**: EMF re-applies its own part rotations from the CEM `wolf.jem` *after* `setupAnim` returns, so the snap never reaches the screen. The lunge runs outside the model, where EMF has nothing to say. It is a translation, not a rotation, because at that point in the pipeline `setupRotations` has not run yet — a rotation would land in world space and tip the wrong way on half the compass.
  - **The lunge aims at the victim, and needs the server to say where that is.** `getTarget()` is server-side AI state and is never synced, so it is always null on the client. Head yaw is a decent fallback for a loose wolf and useless for a ridden one, because `tickRidden` overwrites head *and* body rotation from the rider's look every tick. So the server sends `WolfBiteDirectionPacket` (wolf id + yaw) to tracking players per landed bite; `network/BiteDirections` holds it for 500 ms, comfortably longer than the six-tick swing, and entries expire on their own so nothing needs cleaning up. The channel is registered **optional** — `NetworkComponentNegotiator` fails the whole handshake over a non-optional channel only one side has, and locking a player out of a server over a cosmetic hint would be absurd. Mismatched, the wolf simply lunges along its head yaw.
  - Config under `[modules.battle_dogs.bite_animation]`: `enabled` (default `true`), `only_when_ridden` (default `false` — a dog that only bites visibly while carrying someone looks stranger than one that always does), `strength` (default `1.0`; a double, not an integer — `1.3` is as valid as `1.0`, and `2.0` is visibly too much).

## Cat Guardian Systems

The Cat Guardian module transforms tamed cats into station-based defenders.

### Feeding & Guarding
- **Cat Bowls / Feeding Stations**: Cats can be associated with a Cat Bowl or Feeding Station. They will automatically stay near it (within a guard radius).
- **Feeding**: Cats need to be fed fish via the bowl. A fed cat will stay at its post and guard the area. 
- **Capacity**: By default, up to **8 cats** can be associated with a single bowl or station. This limit is configurable via the module settings. Since each cat consumes one "unit" of fish to stay fed, more cats will drain the station's food supply faster.
- **Monitoring**: 
    - The **Feeding Station inventory** displays the current number of associated cats (e.g., "Associated Cats: 3/8").
    - Wearing **Engineering Goggles** (from Create) or **Aviation Goggles** allows you to see the association count as a hover tooltip when looking at any bowl or station in the world.
- **Guard Behavior**: Fed cats will prioritize attacking hostile mobs. If their health drops below 40%, they will temporarily retreat to the bowl to recover.
- **Auto-Association**: Tamed cats near a bowl will automatically associate with it if they aren't already guarding another station.

### Armor & Combat
- **Damage Absorption**: Cat armor is highly effective. It absorbs **100%** of incoming damage to the cat, losing 1 durability point for every point of damage absorbed.
- **Attack Bonus**: Equipping armor increases the cat's attack damage based on the tier (see table above).
- **No Enchantments**: Cat armor cannot be enchanted.

### Loot Collection
- **Automatic Looting**: When a guarding cat kills a mob, it automatically collects the loot.
- **Feeding Station Integration**: If the cat is associated with a **Feeding Station** (not just a basic bowl), it will automatically transfer its collected loot into the station's inventory whenever it returns to eat.
- **Automation (Hopper/Create)**: The Feeding Station is designed for easy automation:
    - **Loot Extraction**: Place a **Hopper** (or any extraction device) **underneath** the station to automatically pull out the loot collected by your cats.
    - **Food Supply**: Place a **Hopper** (or any insertion device) on the **top or sides** of the station to automatically refill it with fish. Extraction from these sides is also possible but will pull from the food inventory.
    - *Note: The bottom side is dedicated exclusively to the loot inventory, ensuring that your automation never accidentally pulls out the cat food.*
