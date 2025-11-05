# VanillaPlusAdditions

A Minecraft NeoForge mod that enhances vanilla gameplay with useful additions while maintaining the original feel.

> 🤖 **AI Collaboration Notice**: This project was developed in collaboration with the Warp AI assistant (powered by Claude 3.5 Sonnet). The AI helped with code implementation, documentation, and project structure. While the core ideas and direction came from human creativity, the AI's assistance made this project more robust and feature-complete. We believe in transparency about AI usage while celebrating the potential of human-AI collaboration in software development.

## 🎯 Features

### 🔥 Hostile Zombified Piglins
- Makes zombified piglins always hostile in the Nether
- Configurable detection range and anger duration
- Smart targeting system with player switching

### 💀 Wither Skeleton Enforcer
- Prevents normal skeletons from spawning in Nether fortresses
- Replaces them with Wither Skeletons if enabled
- Server-wide broadcast messages for blocked spawns (in debug mode)
- TODO - Config to enable it only in Nether fortresses or the entire Nether

### ✨ MobGlow Command
- Make specific mob types glow for easier tracking
- Configurable duration (including infinite)
- Clear glow effects by type or all at once
- Perfect for server administration and debugging

### 🐦‍🔥 Better Mobs

- Enhances mob variety and challenge
- Mobs can now spawn with customizable armor and potion effects
- Configurable spawn chances and equipment tiers
- Different settings based on Y-Levels or Nether/End dimensions
- TODO - Better levels configuration (for twilight forest, etc.)

### 🪦 Death Coords Logger

- Logs player death coordinates to
    - all players
    - TODO - only the deceased player
    - TODO - the server console
- Operators can teleport to death locations by clicking the message

### 📦 Stackables

- Makes non-stackable items stackable (e.g., stews, potions)
- Increases stack sizes for modded items (Tough as Nails support included)
- Configurable stack sizes for vanilla items:
  - Potions, splash potions, lingering potions (default: 16)
  - Mushroom stew, rabbit stew, beetroot soup, suspicious stew (default: 64)
- Auto-detection for Tough as Nails items:
  - All juice types (apple, melon, cactus, sweet berry, chorus fruit, glow berry, pumpkin)
  - Water bottles (dirty, purified)
  - Ice cream and Charc-Os
  - Empty canteens (all types)
- **Note**: Filled canteens with durability cannot be made stackable due to Minecraft limitations.

### 👻 Haunted House **[On Hold]**

Creates an atmospheric and spooky experience in configured structures (default: Witch Villas).

#### Features:
- 🧙 **Witch Spawn Boosting**: Increases witch population in target structures
  - Default 50% chance to replace mob spawns with witches
  - Ensures sufficient witches for replacement mechanic
  - Configurable via `witch_spawn_boost_chance`

- 👻 **Invisible Entity Replacement**: Replaces witches with invisible Murmurs (currently zombies for testing)
  - Default 10% of witches become invisible entities
  - Entities remain invisible until a player looks directly at them
  - Advanced line-of-sight detection with raycast verification
  - Combined effect: ~5% of all mob spawns become invisible entities
  - Configurable via `target_mobs` list

- 🌫️ **Atmospheric Fog**: Creates spooky ambiance inside structures
  - Applies darkness effect (natural cave-like fog)
  - Configurable on/off via `enable_fog_effect` (default: true)
  - Adjustable intensity (0-5) via `fog_effect_amplifier` (default: 0)
  - Automatically dissipates when leaving structure

- ⚙️ **Fully Configurable**:
  - Target structures list (default: `nova_structures:witch_villa`)
  - Mob replacement rates per entity type
  - Witch spawn boost percentage
  - Fog effect toggle and intensity
  - Comprehensive debug logging

#### Requirements:
- Alex's Mobs mod (alexsmobs) - for Murmur entity
- Dungeons and Taverns mod (mr_dungeons_andtaverns) - for Witch Villa structure

#### Status:
**Disabled by default** - Currently uses zombies for testing while waiting for Alex's Mobs to be updated to Minecraft 1.21.x. 
When Alex's Mobs is available, the module will spawn actual Murmurs instead of zombies.

#### Configuration Example:
```toml
[haunted_house]
    enabled = true
    debug_logging = true
    witch_spawn_boost_chance = 50.0
    enable_fog_effect = true
    fog_effect_amplifier = 0
    target_mobs = ["minecraft:witch:10"]
    target_structures = ["nova_structures:witch_villa"]
```

## 🔧 Configuration

Each module has its own configuration options. See our detailed guides:
TODO - Create these guides
- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
- [Debug Logging Configuration](DEBUG_LOGGING_CONFIG.md)
- [MobGlow Command Guide](MOBGLOW_MODULE_GUIDE.md)

## 🚀 Installation

1. Download the latest version from [Releases](https://github.com/Gerry3010/vanillaplusadditions/releases)
2. Install NeoForge for Minecraft 1.21
3. Place the jar file in your mods folder
4. Start Minecraft and enjoy!

## 🔨 Development

### Prerequisites
- JDK 21
- Gradle 8.4+
- Git

### Setup
```bash
# Clone the repository
git clone https://github.com/Gerry3010/vanillaplusadditions.git
cd vanillaplusadditions

# Setup development environment
./gradlew build
```

### Test Environments
The project includes test server and client setups:
```bash
# Test server
cd test-server
./build-and-test.sh

# Test client
cd test-client
./launch-client.sh
```

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) first.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🌟 Credits

- **Developer**: Gerald Hofbauer
- **AI Assistant**: Warp AI (Claude 3.5 Sonnet)
- **Framework**: [NeoForge](https://neoforged.net/)

## 📚 Documentation

- [Module System Overview](MODULE_SYSTEM.md)
- [Configuration System](CONFIGURATION_SYSTEM_SUMMARY.md)
- [Testing Guide](TESTING.md)

## 🐛 Debug Logging

VanillaPlusAdditions includes a sophisticated debug logging system:
- Global and per-module control
- Detailed log messages for troubleshooting
- See [Debug Logging Guide](DEBUG_LOGGING_CONFIG.md)

## 🔗 Links

- [GitHub Repository](https://github.com/Gerry3010/vanillaplusadditions)
- [Issue Tracker](https://github.com/Gerry3010/vanillaplusadditions/issues)
- [NeoForge](https://neoforged.net/)

## 💬 About AI Assistance

This project demonstrates the potential of human-AI collaboration in software development. The AI assistant helped with:

- Code implementation
- Documentation writing
- Project structure
- CI/CD setup
- Testing frameworks
- Bug fixes

While the AI provided technical assistance, all creative decisions, feature ideas, and project direction came from human input. We believe this transparency about AI usage is important for the open-source community.