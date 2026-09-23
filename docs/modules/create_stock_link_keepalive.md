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

`tickRequests()` returns early while the panel's timer is above zero — **before** it reads any stock
level at all. So for a short window after a chunk load (`grace_ticks`, default 60) the module simply
calls the public `resetTimer()` on every panel of every tracked gauge, once per tick.

Nothing in Create's own bookkeeping is written to. No state is faked. When the window closes the
panel carries on exactly as designed — by which time the links have long since reported in.

Setting `waitingForNetwork` directly would *not* work, incidentally: `tickStorageMonitor()`
recomputes it earlier in the same tick, so the value would be overwritten before it is read.

**Verified at the default of 60 ticks:** on a cold load all 14 gauges of the test base logged their
window closing with real stock and *satisfied* on every panel, and nothing was re-ordered.

<!-- vpa:config:start -->
## Configuration

Section `[modules.create_stock_link_keepalive]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_create_stock_link_keepalive-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `grace_ticks` | integer | `60` | 0-600 | How long factory panels are kept from placing orders after their chunk loads. The blind window measured on the live server is about 20 ticks; 60 covers it with room to spare. A larger value delays a genuinely needed order by that much (plus up to one Create factoryGaugeTimer interval) after a chunk load, and nothing else. |
| `hold_on_chunk_load` | boolean | `true` | — | Whether the panels are held at all. This is the module's only effect; the key exists so the behaviour can be compared without editing the module list. |
<!-- vpa:config:end -->

## Limits

This is a **workaround, not a cure.** The race is still there; the module only keeps anyone from
acting during it. The real repair belongs in Create and is reported upstream as
[issue #10774](https://github.com/Creators-of-Create/Create/issues/10774) — if Create fixes the
guard, this module becomes unnecessary.

Cost of the grace window: after a chunk load, a genuinely needed order is delayed by `grace_ticks`
plus at most one Create `factoryGaugeTimer` interval. Nothing else changes.
