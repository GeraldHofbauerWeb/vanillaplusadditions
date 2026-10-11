# Minecraft Instance Switcher

The Instance Switcher is a script for managing several Minecraft installations and mod development environments. It helps when you work with different Minecraft versions or mod configurations.

## Features

- 🔄 Switch between different Minecraft instances
- 📦 Maintain separate mod configurations
- 🛠️ Support for development and testing environments
- 🔍 Instance management and verification

## Usage

### Basic Command

```bash
./instance-switcher.sh [instance-name]
```

### Available Instances

- `vanilla`: clean Minecraft installation
- `forge`: Forge mod development environment
- `test`: testing environment with specific mod configurations

### Examples

1. Switch to Forge development environment:
   ```bash
   ./instance-switcher.sh forge
   ```

2. Switch to testing environment:
   ```bash
   ./instance-switcher.sh test
   ```

3. Return to vanilla Minecraft:
   ```bash
   ./instance-switcher.sh vanilla
   ```

## Directory Structure

```
📁 .minecraft/
├── 📁 instances/
│   ├── 📁 vanilla/
│   ├── 📁 forge/
│   └── 📁 test/
└── 📄 instance-switcher.sh
```

## Configuration

Each instance directory contains its own:
- Mods folder
- Configuration files
- Saved games
- Resource packs

## Instance Management

### Adding New Instances

1. Create a new directory under `instances/`
2. Copy necessary Minecraft files
3. Add instance-specific mods and configs
4. The instance will be automatically recognized

### Backing Up Instances

Back up your instances regularly:

```bash
# Example backup command
cp -r .minecraft/instances/my-instance .minecraft/backups/my-instance-$(date +%Y%m%d)
```

## Troubleshooting

### Common Issues

1. **Instance not found**
   - Verify the instance directory exists
   - Check directory permissions

2. **Minecraft fails to launch**
   - Verify Minecraft version compatibility
   - Check mod dependencies
   - Ensure enough RAM is allocated

3. **Mods not loading**
   - Verify mod compatibility
   - Check Forge/Fabric version
   - Ensure mods are in correct directory

### Safety Checks

The script performs several safety checks:
- Instance directory existence
- File permissions
- Minecraft version compatibility
- Mod dependencies

## Tips & Tricks

1. **Development Testing**
   - Use separate instances for different mod versions
   - Keep a clean vanilla instance for comparison

2. **Performance**
   - Regularly clean unused instances
   - Monitor disk space usage
   - Remove unnecessary mods

3. **Backup Strategy**
   - Backup before switching instances
   - Keep dated backups
   - Document instance configurations

## Contributing

To contribute an improvement to the instance switcher:
1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License

This script is released under the MIT License. See the LICENSE file for details.