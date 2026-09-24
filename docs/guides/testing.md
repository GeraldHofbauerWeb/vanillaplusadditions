# Testing Guide

VanillaPlusAdditions provides several ways to verify and test mod features during development.

## Automated Testing
**There is no test suite.** The `test` task is wired up but there is no `src/test` directory, so

```bash
./gradlew test
```

is a no-op that always succeeds. Do not read a green `test` task as evidence of anything.

What the build *does* check is style and static analysis, and that check is strict — one Checkstyle
warning fails it:

```bash
./gradlew build                      # includes Checkstyle and SpotBugs
./gradlew checkstyleMain spotbugsMain   # the same gate, faster
```

Verification of behaviour is therefore manual, in the game, using the environments below.

## Manual Testing Environments
We provide pre-configured environments for manual testing on both the server and client.

### Test Server
Located in the `test-server/` directory. This is ideal for testing server-side logic, networking, and multiplayer compatibility.

**Quick Start:**
```bash
cd test-server
./build-and-test.sh
```
This script builds the mod and starts the server automatically.

See [test-server/README.md](../../test-server/README.md) for more details.

### Test Client
Located in the `test-client/` directory. Use this to test client-side features, rendering, and UI.

**Quick Start:**
```bash
cd test-client
./launch-client.sh
```

See [test-client/README.md](../../test-client/README.md) for more details.

## Module-Specific Testing

### MobGlow Command
- Use `/mobglow <entity_type>` to verify glowing effects.
- Check server logs for debug output if `debug_logging` is enabled.

### Food Effects
- Use `/give @p <item>` to obtain food items.
- Consume items and verify potion effects and (if Tough As Nails is installed) thirst restoration.

### Haunted House
- Locate or spawn a Witch Villa structure.
- Verify witch spawning and invisible mob replacement.
- Check for atmospheric fog effects inside the structure.

## Debug Logging
Enable debug logging in `vanillaplusadditions-server.toml` to get detailed information about module behavior. See [Debug Logging Guide](debug-logging.md) for details.
