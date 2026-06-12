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

## Cat Guardian Systems

The Cat Guardian module transforms tamed cats into station-based defenders.

### Feeding & Guarding
- **Cat Bowls / Feeding Stations**: Cats can be associated with a Cat Bowl or Feeding Station. They will automatically stay near it (within a guard radius).
- **Feeding**: Cats need to be fed fish via the bowl. A fed cat will stay at its post and guard the area. 
- **Capacity**: There is **no hard limit** on the number of cats that can be associated with a single bowl or station. However, since each cat consumes one "unit" of fish to stay fed, more cats will drain the station's food supply significantly faster.
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
