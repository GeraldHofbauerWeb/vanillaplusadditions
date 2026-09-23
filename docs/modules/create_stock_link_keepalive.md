# Create Stock Link Keepalive

> **TL;DR** — Factory Gauges stop re-ordering a fresh batch of something the vault is already full
> of every time the area loads, because they are held back for a moment until the logistics network
> has reported what is actually in storage.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `create_stock_link_keepalive` |
| **Side** | Server only |
| **Requires** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Works with** | — |
| **Download** | [`vpa_create_stock_link_keepalive.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_create_stock_link_keepalive.jar) · also needs `vpa_core` |
| **Config section** | `[modules.create_stock_link_keepalive]` |
| **Since** | `v1.0.0-beta.88` |
<!-- vpa:meta:end -->

## What it does

Every time you log in, the auto-crafter runs a batch of something you already have hundreds of. The
Item Vault is full, the Stock Link is bound correctly, re-binding it changes nothing — and the
surplus just keeps growing.

This module stops that.

## Why it happens

A Factory Gauge orders when its item is below target **and** Create's own guard `waitingForNetwork`
says the network is complete. Those two facts come from two unrelated pieces of bookkeeping, and for
about a second after a chunk load they disagree:

| | Where it comes from | How long it lives |
|---|---|---|
| **the stock figure** | the `LINKS` cache, which each link refreshes from its own `lazyTick()` | expires 20 ticks after the last refresh |
| **the guard** | `totalLinks.size() - loadedLinks.size()`, an entirely separate structure | permanent until the link unloads |

Right after a chunk load no link has reported in yet, so the summary is empty and the panel reads a
stock level of **0**. Create has a guard for exactly this situation — but on a network where
`totalLinks` and `loadedLinks` are the same size, that difference is always zero, so the guard can
never fire. The panel concludes *"storage empty, network complete"* and orders.

### The measurement

A Factory Gauge polled about once a second, on a server sitting at a steady 20.00 TPS:

```
21:52:55  BR 2942 sat=1 | TR  64 sat=1 | TL 65 sat=1 | BL 69 sat=1   healthy
21:52:56  (chunk not loaded)
21:52:58  BR    0 sat=1 | TR   0 sat=0 | TL  0 sat=0 | BL  0 sat=0   all four read 0
          ...with Waiting: 0b and LastUnloadedLinks: 0 in this very sample
21:52:59  BR 2941        | TR  62 sat=0 | TL 65 sat=1 | BL 69 sat=1   stock is back
21:53:01                   TR LastPromised: 1                         ordered
21:53:06                   TR LastPromised: 2                         ordered again
21:53:02..08  BR 2940 -> 2939                                         ingredients consumed
```

The lasting damage shows up as overshoot: 106 barrels against a target of 64, 78 fluid tanks against
64, 71 chests against 64. The planks, slabs and iron sheets those crafts ate then read as *deficits*
further down the wall — which looks exactly like ordinary restocking and is easy to wave away.

## What it actually does about it

A link only counts towards a network's stock while it sits in Create's `LINKS` cache — a
`TickBasedCache(20, true)`, so an entry dies **one second** after its last refresh. The only thing
that refreshes it is the link's own `lazyTick()`, and that needs the link's chunk to be *ticking*.

When a player disconnects, their chunk tickets go with them. The chunks stop ticking **immediately**
while staying loaded for a good while longer. So any absence longer than one second empties the
network summary — and because nothing actually unloaded, Create's `loadedLinks` still lists every
link and the guard stays false. Reconnect, and the gauge reads zero from a full vault.

This module therefore refreshes the links itself, every `keepalive_interval_ticks` (default 5),
from its own `ServerTickEvent` — which keeps running no matter which chunks tick. The cache cannot
run dry, however long you are away. It covers `stock_link`, `stock_ticker`, `packager`, `repackager`
and `redstone_requester`.

On top of that, and for the *other* regime where the chunks really did unload and the links must
register from scratch, the gauges are held after a chunk load: for `grace_ticks`, and then for as
long as the network summary still has no contributing links at all
(`hold_until_network_reports`, bounded by `max_hold_ticks`).

`resetTimer()` is the lever for that hold because `tickRequests()` returns while the timer is above
zero, **before** it reads any stock level, and the method is public. Setting `waitingForNetwork`
directly would not work — `tickStorageMonitor()` recomputes it earlier in the same tick.

### How this was found, including two fixes that missed

Worth recording, because the first two attempts looked convincing and were wrong.

1. **A fixed 60-tick hold after chunk load.** Held on one rejoin, too short on the next. A fixed
   wait is always a guess.
2. **The same hold, extended while `contributingLinks == 0`.** The right condition — but still tied
   to `ChunkEvent.Load`.

Then the server owner produced an exact reproduction: step into the chunk diagonally north-east of
the stock link's chunk, step back, reconnect. The log settled it:

```
20:57:39.697  Gerre01 left the game
20:57:41.132  Gerre01 joined the game     ← 1.4 seconds
```

**No `ChunkEvent.Load` fired at all** — the module logged nothing for the entire run, while stock
moved (fluid tank 81→82, oak chest 74→75, chain drive 108→109). 1.4 s is simply longer than the
cache's 1.0 s. No chunk load is involved anywhere, which is why neither hold could ever have caught
it. Both are kept regardless: 27 releases were logged during the verification run, so the chunk-load
path does real work in its own regime.

**Verified over five consecutive rejoins** with the owner's reproduction: 82 / 75 / 104 / 109
unchanged to the item, `LastPromised: 0` on every panel, zero errors, 20.000 TPS.

<!-- vpa:config:start -->
## Configuration

Section `[modules.create_stock_link_keepalive]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_create_stock_link_keepalive-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `grace_ticks` | integer | `60` | 0-600 | How long factory panels are held after their chunk loads, before the condition below takes over. Covers the case where the chunks really did unload and the links have to register from scratch. |
| `hold_on_chunk_load` | boolean | `true` | — | Whether the chunk-load hold runs at all. The keepalive above is unaffected by this key. |
| `hold_until_network_reports` | boolean | `true` | — | Keep holding a gauge past grace_ticks while its network summary has no contributing links at all (InventorySummary.contributingLinks == 0). A fixed wait is always a guess; this is the fact the guess stood for. Bounded by max_hold_ticks. |
| `keepalive_interval_ticks` | integer | `5` | 1-19 | How often every tracked logistics link is re-stamped in Create's LINKS cache. That cache expires 20 ticks - one second - after a link last refreshed it, and only the link's own lazyTick() does that, which needs a TICKING chunk. Must stay below 20; 5 leaves a margin of four. |
| `max_hold_ticks` | integer | `600` | 60-6000 | Ceiling on how long one gauge may be held, counted from its chunk load. Only reached when a network genuinely has no contributing links - measured on five gauges whose network has none at all. They are then released and Create decides for itself again. |
<!-- vpa:config:end -->

## Limits

This is a **workaround, not a cure.** The race is still there; the module only keeps anyone from
acting during it. The real repair belongs in Create and is reported upstream as
[issue #10774](https://github.com/Creators-of-Create/Create/issues/10774) — if Create fixes the
guard, this module becomes unnecessary.

Cost of the grace window: after a chunk load, a genuinely needed order is delayed by `grace_ticks`
plus at most one Create `factoryGaugeTimer` interval. Nothing else changes.
