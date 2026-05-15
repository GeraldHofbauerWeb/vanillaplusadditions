#!/bin/bash

# VanillaPlusAdditions Test Client Launcher
# This script provides instructions for testing your mod

CLIENT_DIR="/home/gerry/Sync-Projekte/Minecraft/VanillaPlusAdditions/test-client"
MINECRAFT_DIR="/home/gerry/.minecraft"

# Find the latest mod jar dynamically
MOD_JAR_NAME=$(ls -t "$CLIENT_DIR/mods"/vanillaplusadditions-*.jar 2>/dev/null | head -1 | xargs -r basename)
if [ -z "$MOD_JAR_NAME" ]; then
    MOD_JAR_NAME="vanillaplusadditions-<version>.jar"
fi

echo "=== VanillaPlusAdditions Test Client Setup ==="
echo
echo "Your test client environment has been set up successfully!"
echo "Client Directory: $CLIENT_DIR"
echo "Your mod is installed in: $CLIENT_DIR/mods/"
echo
echo "To test your mod:"
echo "1. Open the Minecraft launcher"
echo "2. Look for the 'NeoForge 21.0.167' profile (it should be available)"
echo "3. Select that profile and launch the game"
echo "4. Create a new world or join your test server at localhost:25565"
echo
echo "Alternative: You can also copy your mod JAR to your regular .minecraft/mods folder:"
echo "  cp '$CLIENT_DIR/mods/$MOD_JAR_NAME' '$MINECRAFT_DIR/mods/'"
echo
echo "Your mod includes:"
echo "  • HostileZombifiedPiglinsModule - Makes zombified piglins always hostile to players"
echo "  • Module system for easy expansion"
echo
echo "Check the game logs for mod loading confirmation and any errors."
echo
echo "Happy testing!"
