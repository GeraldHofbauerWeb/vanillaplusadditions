#!/bin/bash
# VanillaPlusAdditions Test Server Startup Script

echo "=== VanillaPlusAdditions Test Server ==="
echo "Starting Minecraft 1.21.1 NeoForge test server..."
echo "(Mod JAR is copied automatically by Gradle's copyJarToTestEnvironments task after each build)"

echo "Starting server..."
echo "==============================================="

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Please install Java 21."
    exit 1
fi

# Start the server using the generated run.sh
if [ -f "run.sh" ]; then
    chmod +x run.sh
    ./run.sh
else
    echo "Error: run.sh not found. Please run the NeoForge installer first."
    exit 1
fi