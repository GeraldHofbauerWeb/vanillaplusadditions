# VPA Options

> **TL;DR** — Keeps named snapshots of your client settings — the whole `options.txt` or just the
> keybinds — so you can put your controls back exactly as they were after a modpack update or a
> misclick wrecks them.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `options` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_options.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_options.jar) · also needs `vpa_core` |
| **Config section** | `[modules.options]` |
| **Since** | `v1.0.0-beta.41` |
<!-- vpa:meta:end -->

## What it does

Minecraft keeps one `options.txt` and no history of it. Take a modpack update that hands your keys
to a new mod, or walk out of the Controls screen after a misclick, and the only way back is
remembering what it used to say.

This module snapshots that file and puts it back. Three ways in:

* **`/vpaoptions export <name>`** — a named snapshot, whenever you want one.
* **The "Backups…" button**, top right on the vanilla Options *and* Controls screens: a name box,
  the list of snapshots, Restore and Delete.
* **Automatically, once per game start** — if the current settings differ from the newest automatic
  snapshot, a fresh `auto_<timestamp>` is written and the oldest surplus is deleted. Ten are kept.

Snapshots are plain UTF-8 text in `options.txt` format under
`config/vanillaplusadditions/options_backups/`, one file per snapshot. You can read them, edit them,
copy them between instances or keep one next to your modpack.

By default a snapshot covers the whole file — video, sound, chat, keybinds. Set
`full_options_backup = false` and both backups and restores shrink to the `key_*` lines, so putting
the controls back does not drag a year-old render distance along with them.

All of it is client-side. The module is registered on a dedicated server as well, but every piece of
it hangs off client-only events, so there it does nothing at all.

## In detail

### The commands

| Command | What it does |
|---|---|
| `/vpaoptions export <name>` | Saves the current options as `<name>.txt`, at the scope `full_options_backup` sets. No name suggestions — you are naming something new. |
| `/vpaoptions restore <name>` | Writes a safety snapshot, then applies `<name>`. Existing names are suggested, `auto_` ones included. |
| `/vpaoptions delete <name>` | Deletes `<name>.txt`. Suggested the same way, `auto_` ones included. |
| `/vpaoptions list` | Manual snapshots first, white and alphabetical; automatic ones after them, grey and newest-first. |
| `/vpaoptions gui` | Opens the management screen. |

All five are **client** commands (`RegisterClientCommandsEvent`) and carry no `.requires()` gate:
they work for any player on any server, including servers that do not have the mod. The bare
`/vpaoptions` has no `.executes()`, so a subcommand is always required.

A manual name must match `[A-Za-z0-9._-]+` and must not start with `auto_`, which is reserved for
the rotating backups. Brigadier's `word()` argument is a shade more permissive — it also accepts
`+` — so such a name parses and is then refused by the module's own check with the *"Invalid backup
name"* message rather than by the parser. The GUI trims whitespace around the name and caps the box
at 64 characters.

That validation applies to **export only**. `restore` and `delete` take any name that exists on
disk, which is deliberate: the snapshot you most often want back is an automatic one.

### Automatic backups

The check runs on the **first** `ClientTickEvent.Post` of the session — at the title screen, before
any world is loaded — and never again; a static flag makes it one-shot. There is no watcher while
you play, so a setting you change during a session is captured at the *next* start, not when you
change it.

1. The live options are flushed to disk (`Options.save()`), so the comparison sees what you
   actually have rather than what was last written.
2. The scoped lines are compared line by line with the newest automatic snapshot — newest by file
   modification time.
3. Identical, and nothing happens. Different, and `auto_<yyyyMMdd-HHmmss>.txt` is written, after
   which everything past `auto_backup_keep` (10) is deleted, oldest first.

The one-shot flag is set **before** the module and config check, so switching the module or
`auto_backup` on mid-session buys nothing until the next restart.

### Restoring

Four steps, in this order:

| # | Step |
|---|---|
| 1 | The snapshot is read into memory. |
| 2 | A safety snapshot `auto_<stamp>-prerestore` of the current state is written — always at **full** scope, even with `full_options_backup = false`. |
| 3 | The automatic backups are pruned, because that safety snapshot carries the `auto_` prefix and counts against `auto_backup_keep`. |
| 4 | The snapshot is applied. |

Step 3 has a consequence worth knowing: restoring the *oldest* automatic snapshot while the budget
is already full deletes that very file as part of the restore. The restore still works — the content
was read in step 1, before the prune — but the snapshot is gone afterwards.

**Which path applies is decided by the snapshot's content, not only by the config.** The full path
needs `full_options_backup = true` *and* a snapshot that has at least one non-blank line which is
not a `key_` line. A keybind-only snapshot restored at full scope is therefore still applied as
keybinds only — there is nothing else in it to apply.

| Path | What it does |
|---|---|
| Full | Overwrites `options.txt` with the snapshot, then `Options.load()`, `KeyMapping.resetMapping()` and `Options.save()`. The chat line warns that some settings (language, resource packs) may need a restart. |
| Keybinds only | Parses `key_<mapping>:<value>`, walks the **live** key mappings and sets each one whose stored value differs from its current `saveString()`, then `resetMapping()` and `save()`. |

Two things follow from "walks the live mappings". The number in *"(N keybinds applied)"* counts the
keys that actually **changed**, so restoring a snapshot you already match reports 0. And a mapping
that is in the snapshot but not in the current install — a mod removed since — is never looked at:
no warning, no contribution to the count.

One oddity if you run with `full_options_backup = false`: the `-prerestore` file is full scope, so
it is the newest automatic snapshot and the next start's keybind-only comparison cannot match it.
That start writes one extra automatic backup. The extra one is keybind-scoped and now the newest, so
it settles after exactly one — it does not repeat every start. Toggling `full_options_backup` does
the same thing, once.

### What the scope covers

| | `full_options_backup = true` | `full_options_backup = false` |
|---|---|---|
| `export`, GUI *Create* | whole `options.txt` | `key_*` lines only |
| automatic backup at game start | whole file | `key_*` lines only |
| restore | full swap, unless the snapshot holds nothing but `key_*` lines | keybinds only, whatever the snapshot holds |
| `-prerestore` safety snapshot | whole file | **whole file** |

### The screen

The "Backups…" button is added by `ScreenEvent.Init.Post` at `screen.width - 66, 6`, 60 × 20 — the
top-right corner of the Options screen and of the Controls screen. `/vpaoptions gui` opens the same
screen from anywhere.

A 204-wide name box (64 characters) with *Create* beside it, a scrollable list below it, and
*Restore* / *Delete* / *Done* along the bottom. Each row carries the name — white for manual, grey
for automatic — and the file's modification time right-aligned as `yyyy-MM-dd HH:mm`.

Restore and Delete stay inactive until you pick a row. **There is no confirmation dialogue**:
clicking Restore acts at once, and the `-prerestore` snapshot is the only safety net. Feedback is a
single grey line above the buttons, red on failure; it survives a window resize, but it is gone the
moment the screen closes.

*Done* hands you back to the screen you came from. Opened from the command there is none — the
screen is built with a `null` parent and closes outright.

<!-- vpa:config:start -->
## Configuration

Section `[modules.options]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_options-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `auto_backup` | boolean | `true` | — | Automatically create a rotating backup at game start whenever the current options differ from the newest automatic snapshot. The check runs exactly once per launch (first client tick), not while playing. |
| `auto_backup_keep` | int | `10` | 1 ~ 100 | How many automatic backups (auto_* snapshots) to keep before the oldest ones are deleted. The auto_<stamp>-prerestore safety snapshots count against this budget too. |
| `full_options_backup` | boolean | `true` | — | Back up / restore the full options.txt (video, sound, chat, keybinds, ...); set to false to limit both backups and restores to keybinds (key_* lines) only. Note: the automatic pre-restore safety snapshot is always taken at full scope regardless of this setting. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Dedicated server | The module is registered, but every class that does anything is `@OnlyIn(Dist.CLIENT)` or behind a `Dist.CLIENT` event subscriber, so it does nothing. Nothing to install server-side. |
| Enabling the module mid-session | The command is registered once per `RegisterClientCommandsEvent`, and only if the module is enabled at that moment, so `/vpaoptions` stays absent until commands are registered again — rejoining a world or reconnecting. The screen button re-checks on every screen init and appears straight away. |
| Enabling `auto_backup` mid-session | No effect until the next restart: the one-shot flag is already set. |
| Overwriting a snapshot | `export` onto an existing name truncates it without a question, from the command and from the GUI alike. |
| Hand-edited snapshot | A `key_` line whose value is not a key name vanilla knows makes `InputConstants.getKey` throw — an unchecked exception, which neither call site catches because both catch `IOException` only. You get a stack trace rather than the friendly error line. Files this module writes always hold valid values, so it takes a manual edit to reach. |
| Unreadable backup directory | `listBackups` swallows the `IOException` and returns an empty list, so a broken or permission-denied directory looks exactly like "no backups yet", never like an error. |
| Two restores inside the same second | The safety snapshot's name has one-second resolution (`auto_<yyyyMMdd-HHmmss>-prerestore`), so the second one overwrites the first. |
| `debug_logging` | No effect here — nothing in the module reads it. The little logging there is (automatic backup created; an operation failed) goes to the ordinary log at info and warn. |
| Language and resource packs | A full restore reloads the options in place, but those two only take full effect after a restart. The in-game message says so. |

## Under the hood

No mixins, no registries, no keybinds of its own, no network payloads, no `data/` files.
`OptionsModule` sits in the common package and is registered unconditionally — in the bundle and in
the standalone jar alike — but its `onInitialize()` is an empty stub. Everything hangs off
`OptionsClientEvents`, an `@EventBusSubscriber(value = Dist.CLIENT, bus = GAME)` class that FML
picks up by itself.

| Event | Purpose |
|---|---|
| `RegisterClientCommandsEvent` | Registers `/vpaoptions`, if the module is enabled at that moment |
| `ClientTickEvent.Post` | The one-shot automatic-backup check; the guard flag is set first, the module and config check second |
| `ScreenEvent.Init.Post` | `addListener` for the "Backups…" button on `OptionsScreen` and `ControlsScreen`, re-checked on every init |

| Class | Role |
|---|---|
| `modules/options/OptionsModule` | Module descriptor; `onInitialize()` empty |
| `modules/options/config/OptionsConfig` | The three config values |
| `modules/options/client/OptionsClientEvents` | Command, button injection, automatic-backup trigger |
| `modules/options/client/OptionsBackupManager` | All file work: export, restore, list, delete, prune |
| `modules/options/client/OptionsBackupScreen` | The management screen and its list |
| `standalone/options/OptionsStandalone` | `@Mod("vpa_options")` entry point |

**The sort order comes out of one comparator.** `listBackups` compares on three keys: automatic
after manual (`comparing(BackupInfo::auto)`, false before true), then `-lastModified` for automatic
entries and a constant `0L` for manual ones, then the name. That middle key is what gives the two
groups different orders — newest-first inside the automatic block, no effect at all inside the
manual one, which therefore falls through to alphabetical.

**The screen open is deferred.** `/vpaoptions gui` does not call `setScreen` directly, it hands it
to `mc.tell(...)`. Per the comment in the source, the chat screen closes right after a command runs
and would override a synchronous `setScreen`.

**The list keeps its selection.** `refresh` remembers the selected entry, clears the list — which
clears the selection — and re-selects by name if that entry is still there. After a delete or a
prune the selection is simply gone and the two buttons go inactive, so no row can point at a file
that no longer exists.

**Standalone jar.** `vpa_options` is declared in `build.gradle` with no mixins, no data globs and no
module dependencies, and packs only `modules/options/**` plus `standalone/options/**`. The GUI and
chat strings live in `assets/vanillaplusadditions/`, which ships in `vpa_core` — required by every
module jar — so they still resolve in a standalone setup.

**Testing.** This repository has no unit tests, and nothing here is covered by an automated check;
everything above is read off the source.

## See also

* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
