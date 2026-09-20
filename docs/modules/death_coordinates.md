# Death Coordinates Announcer

> **TL;DR** — When any player dies, everyone on the server gets a chat line naming the player, the
> exact X/Y/Z of the death and the dimension — and if the player who died was an operator, that line
> is clickable and teleports whoever clicks it to the spot, provided they themselves may run
> commands.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `death_coordinates` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_death_coordinates.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_death_coordinates.jar) · also needs `vpa_core` |
| **Config section** | `[modules.death_coordinates]` |
| **Since** | `v0.4.0` |
<!-- vpa:meta:end -->

## What it does

Every player death produces one extra chat line, sent to everyone online and to the server console:

```
Player Gerry died at coordinates: X=112, Y=63, Z=-408 in the Overworld
```

The name is bold gold, the coordinates aqua, the dimension light purple. Nothing has to be typed and
nothing has to be remembered: the line stays in the chat history for as long as any other message,
which is usually enough to walk back to the items.

If the player who died was an **operator**, the whole line also carries a hover ("Click to teleport
to death location") and a click that runs a teleport to the spot. That is a convenience for staff
cleaning up after an accident, not a general respawn shortcut — see [the click](#the-click) for who
actually gets it and who it works for.

## In detail

### The message

It is built from literals in `onPlayerDeath`, piece by piece:

| Piece | Text | Style |
|---|---|---|
| Prefix | `Player ` | plain |
| Name | `player.getName().getString()` | **bold**, gold |
| Middle | ` died at coordinates: ` | plain |
| Position | `X=%d, Y=%d, Z=%d` | aqua |
| Middle | ` in ` | plain |
| Dimension | see below | light purple |

The position is `player.blockPosition()` — the block the player's feet occupied, so the values are
whole numbers and never the fractional death position.

The finished component is sent once per online player with `ServerPlayer.sendSystemMessage`, then a
copy goes to `MinecraftServer.sendSystemMessage`, which puts the same line in the console and the
server log. There is no receiver-side filter of any kind: everyone online sees every death, in every
dimension, at any distance.

**It is a second line, not a replacement.** Nothing here touches vanilla's own death message, and
nothing reads the `showDeathMessages` gamerule. Both lines therefore normally appear, and the
coordinate line still appears when `showDeathMessages` is off. The order is fixed: NeoForge fires
`LivingDeathEvent` from the first statements of `ServerPlayer.die`, before the gamerule is read and
before vanilla broadcasts anything —

```java
public void die(DamageSource p_9035_) {
    this.gameEvent(GameEvent.ENTITY_DIE);
    if (net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, p_9035_)) return;
    boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
```

— so the coordinates come first and "Gerry fell from a high place" second.

### The click

The hover and the click event are attached to the message *once*, gated on the player who just
**died**:

```java
if (player.hasPermissions(2)) {
    deathMessage = deathMessage.withStyle(style -> style
            .withHoverEvent(...)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/execute in %s as @s run tp @s %d %d %d".formatted(...))));
}
```

That single component is then broadcast unchanged to everybody. There is no per-recipient check, so
the outcome is a grid rather than a rule:

| Who died | Who clicks | Result |
|---|---|---|
| Operator | may run `/tp` | teleported to the death spot |
| Operator | may not run commands | the command is rejected for the clicker |
| Ordinary player | anyone, operator included | no hover, no click — plain text |

The command runs with the **clicker's** permissions, not the dead player's, which is what the second
row comes down to. The gate on the dying player is a known open point rather than a settled design;
the source says so at the `if`:

```java
// TODO: Make the permission level configurable aka make it a config option to enable for spectators
//  (and/or ops) or all players
```

Because the style is applied to the root component, the click covers the whole line, not just the
coordinates — the children carry their own colours but inherit the click and hover.

`/execute in <dimension>` is what makes the teleport cross dimensions; `/tp` alone cannot. The
dimension in the command is the raw id (`minecraft:the_nether`), deliberately, because that is what
`/execute in` expects — the readable name is display only. The coordinates go in raw as well, so the
teleport lands on the block corner and puts you wherever the death was, lava and void included.

The `@s` in the `tp` is load-bearing. It was `@p` until `v0.13.4`, which meant clicking teleported
whichever player happened to be nearest to the clicker instead of the clicker themselves.

### Dimension names

Vanilla has no display name for a dimension — `DimensionType` carries mechanical settings only, and
1.21.1 ships no `dimension.*` translation keys at all. `dimensionName()` therefore asks for the
widely used modded convention `dimension.<namespace>.<path>` through
`Component.translatableWithFallback` and supplies its own fallback:

| Dimension | Key asked for | Shown if no mod provides the key |
|---|---|---|
| `minecraft:overworld` | `dimension.minecraft.overworld` | the Overworld |
| `minecraft:the_nether` | `dimension.minecraft.the_nether` | the Nether |
| `minecraft:the_end` | `dimension.minecraft.the_end` | the End |
| anything else | `dimension.<namespace>.<path>` | the raw id, e.g. `othermod:mining` |

A dimension mod that ships that key gets a localised name for free; everything else at least stays
actionable. This is the one part of the line that is translatable at all — the rest is English
literals (see the limits below).

### What does not produce a line

| Case | Why |
|---|---|
| A mob dies | The handler returns unless the entity is `instanceof Player`. |
| The death is cancelled | `LivingDeathEvent` is cancellable and `@SubscribeEvent` defaults to `receiveCanceled = false`, so a totem-style save by another mod announces nothing. |
| Client side | `level.isClientSide()` returns early; the module is server-side throughout. |
| Module switched off | `isModuleEnabled()` is checked inside the handler, so toggling it takes effect on the next death without a restart. |

<!-- vpa:config:start -->
## Configuration

Section `[modules.death_coordinates]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_death_coordinates-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| English only | The message is built from `Component.literal`, so client language does not matter. The lang key `message.vpa.death_coords` exists in all six lang files but is referenced nowhere in `src/` — `0.10.3` moved these strings to translatable components and `0.12.0` moved them back to literals, leaving the translations behind. |
| Everyone sees every death | There is no distance, team or dimension filter. On a PvP server the line tells the whole server where a player's items are lying. |
| `showDeathMessages` off | Does not silence this module; the gamerule is never read. Two lines become one. |
| Non-op deaths are never clickable | Even an operator receives plain text when an ordinary player dies, because the gate sits on the dying player. |
| Op deaths are clickable for everyone | A player without command permission still gets a clickable line and only finds out on the click, when the command is refused. |
| Teleport target is not made safe | `tp` goes to the recorded block position. A void death in the End teleports the clicker back into the void; a lava death teleports them into lava. |
| Fake players | Any `Player` subclass counts, including the fake players some mods use for machines. There is no UUID or fake-player check. |
| Client mods | None needed. Nothing is registered on the client and no packet of our own is sent; it is ordinary system chat. |

## Under the hood

The whole module is one file, 142 lines:
`modules/death_coordinates/DeathCoordinatesModule.java`. No mixins, no commands, no items, blocks or
entities, no data files, no config subclass — it passes `AbstractModuleConfig::createDefault`, which
is why the configuration above has only the two universal keys.

| Event | Bus | Handler |
|---|---|---|
| `LivingDeathEvent` | game (`NeoForge.EVENT_BUS.register(this)` in `onInitialize`) | `onPlayerDeath` — the entire module |

The explicit `priority = EventPriority.NORMAL` on the subscription is the annotation default and
changes nothing.

**Logging.** `onInitialize` writes one INFO line at startup regardless of the `debug_logging`
setting. The per-death log is gated on `getConfig().shouldDebugLog()` but then calls
`getLogger().info(...)`, not `.debug(...)` — so turning debug logging on for this module adds an INFO
line per player death rather than a DEBUG one. `onCommonSetup` uses `.debug()` correctly.

**Standalone jar.** `vpa_death_coordinates` is `vpa_core` plus this one class: no mixins, no data
globs, no module dependencies. Entry point
`standalone/death_coordinates/DeathCoordinatesStandalone`, `@Mod("vpa_death_coordinates")`. In the
bundle it is registered at `VanillaPlusAdditions.java:156`; nothing else in `src/` references it.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
