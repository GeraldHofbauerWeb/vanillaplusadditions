# AGENTS.md

## Project snapshot
- NeoForge mod for Minecraft 1.21.1 on Java 21 (`gradle.properties`, `build.gradle`, `src/main/resources/META-INF/neoforge.mods.toml`).
- Main entrypoint is `src/main/java/net/geraldhofbauer/vanillaplusadditions/VanillaPlusAdditions.java`; it registers modules first, then hands lifecycle control to the singleton `ModuleManager`.
- Only modules added in `registerModules()` are live. `src/main/java/net/geraldhofbauer/vanillaplusadditions/modules/mob_drops/` exists, but is not currently registered.

## Architecture
- Feature code lives in `src/main/java/net/geraldhofbauer/vanillaplusadditions/modules/<module_id>/`.
- Standard pattern: module class extends `AbstractModule`, config extends `AbstractModuleConfig`, and module-specific settings usually live in a `config/` subpackage.
- Lifecycle order is `initialize(...)` → `commonSetup()` → `clientSetup()` → `loadComplete()`; `ModuleManager` only fans these phases out to enabled modules.
- Config is built dynamically from registered modules in `ModulesConfig`; every module gets `enabled` + `debug_logging`, then its own extra keys (example: `FoodEffectsConfig`).
- Most modules attach runtime events in `onInitialize()` via `NeoForge.EVENT_BUS.register(this)`; mod-bus hooks go through `getModEventBus().addListener(...)` (`FoodEffectsModule` registers `ModifyDefaultComponentsEvent` this way).
- For player-visible diagnostics, modules often use `util/MessageBroadcaster.java` so debug output appears both in chat and in logs.
- Data-heavy features are config-driven and cache registry lookups after load; see `FoodEffectsModule.reloadEffectCache()` for the item/effect/thirst parsing pattern.

## Working efficiently
```bash
./gradlew build
./gradlew test
./gradlew checkstyleMain spotbugsMain
```
- `build` automatically runs `copyJarToTestEnvironments`, which copies the built jar into `test-server/mods/` and `test-client/mods/`.
- Practical verification is mostly manual: `test-server/build-and-test.sh` builds and starts a local NeoForge server; `test-client/launch-client.sh` prints launcher-based client instructions.
- There are currently no checked-in `*Test*.java` files, so `./gradlew test` is wired up but manual in-game testing is the real verification path.
- SpotBugs is advisory (`ignoreFailures = true`); Checkstyle is enforced (`ignoreFailures = false`, `maxWarnings = 0`).

## Release checklist
When tagging and pushing a new release (e.g. `git tag vX.Y.Z && git push --tags`), **always** update these files **before** committing/tagging:
1. `gradle.properties` → bump `mod_version`
2. `CHANGELOG.md` → add a new `## [X.Y.Z]` section describing all changes
3. `CHANGELOG.md` entry is the single source of truth for release notes. The workflow `.github/workflows/release.yml` reads the matching `## [X.Y.Z]` block automatically — **no manual edits to `release.yml` needed for the body**. The workflow also backfills all existing GitHub releases from `CHANGELOG.md` on every new tag push.
4. Commit everything, then `git tag vX.Y.Z` and `git push && git push --tags`

## Project-specific conventions
- Put `if (!isModuleEnabled()) return;` at the top of event handlers and guard side-specific logic explicitly with `level.isClientSide()` / server-side casts.
- Event priority is part of feature behavior: `HauntedHouseModule` uses `HIGHEST`/`HIGH` on `FinalizeSpawnEvent`; `OverpackedSlowdownModule` uses `LOW` to run after Overpacked’s default handler.
- Optional mod integration is runtime-gated with `ModList.get().isLoaded(...)`; `HauntedHouseModule.shouldInitialize()` hard-disables without `mr_dungeons_andtaverns`, while `FoodEffectsModule` conditionally enables Tough As Nails support.
- Resource IDs and module IDs are the real API surface. Config sections are `[modules.<module_id>]` such as `[modules.food_effects]` or `[modules.mob_glow]`.
- The mod JAR is always copied by Gradle (`copyJarToTestEnvironments`) after `build` — do **not** add manual copy logic in shell scripts, as the version string changes with every release.

## Useful references
- `src/main/java/net/geraldhofbauer/vanillaplusadditions/VanillaPlusAdditions.java`
- `src/main/java/net/geraldhofbauer/vanillaplusadditions/core/{AbstractModule.java,AbstractModuleConfig.java,ModuleManager.java,ModulesConfig.java}`
- `src/main/java/net/geraldhofbauer/vanillaplusadditions/modules/food_effects/FoodEffectsModule.java`
- `src/main/java/net/geraldhofbauer/vanillaplusadditions/modules/haunted_house/HauntedHouseModule.java`
- `docs/{MODULE_SYSTEM.md,CONFIGURATION_SYSTEM_SUMMARY.md,DEBUG_LOGGING_CONFIG.md,TESTING.md}`

