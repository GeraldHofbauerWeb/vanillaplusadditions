#!/usr/bin/env bash
#
# deploy.sh — Build VPA and deploy to the games2 server + local client.
#
# Two-target deploy in one shot:
#   1. games2 server (AMP/Docker, container AMP_SebsModpackv401) — docker cp + restart
#   2. local client (~/.minecraft-instances/sebsmodpack4/mods/) — jar swap
#
# Why a script and not inline commands:
#   The running-game check MUST live in a file. When the same pgrep pattern is
#   run inline via `bash -c`, the pattern string ends up in the shell's own
#   /proc/<pid>/cmdline and `pgrep -f` matches ITSELF → false "game running".
#   In a script file the pattern lives in the file, the process cmdline is just
#   `bash deploy.sh`, so there is no self-match. (This bit us repeatedly.)
#
# NEVER swap the jar in a RUNNING client — it corrupts lazy zip reads
# (ZipException "invalid stored block lengths" → missing textures). We hard-abort
# the local swap if the game is up; the server side is always safe to restart.
#
# Usage:
#   scripts/deploy.sh                    # build + deploy to server AND local client
#   scripts/deploy.sh --server           # build + deploy to server only
#   scripts/deploy.sh --client           # build + deploy to local client only
#   scripts/deploy.sh --server --no-restart  # push jar to server, do NOT restart
#   scripts/deploy.sh --no-build         # skip gradle build, use existing jar
#
set -euo pipefail

# ---- config -----------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_SSH="gerry@82.165.95.152"
CONTAINER="AMP_SebsModpackv401"
SERVER_MODS="/AMP/Minecraft/mods"
SERVER_OWNER="amp:amp"
CLIENT_DIR="$HOME/.minecraft-instances/sebsmodpack4"
CLIENT_MODS="$CLIENT_DIR/mods"

DO_SERVER=1; DO_CLIENT=1; DO_BUILD=1; DO_RESTART=1
for arg in "$@"; do
  case "$arg" in
    --server)     DO_SERVER=1; DO_CLIENT=0 ;;
    --client)     DO_SERVER=0; DO_CLIENT=1 ;;
    --no-build)   DO_BUILD=0 ;;
    --no-restart) DO_RESTART=0 ;;
    -h|--help)
      sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//; /^set -euo/d'
      exit 0 ;;
    *) echo "unknown arg: $arg" >&2; exit 2 ;;
  esac
done

cd "$REPO_ROOT"
VERSION="$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)"
JAR="build/libs/vanillaplusadditions-${VERSION}.jar"
echo "==> VanillaPlusAdditions ${VERSION}"

# ---- build ------------------------------------------------------------------
if [[ "$DO_BUILD" == 1 ]]; then
  echo "==> Building (./gradlew build) ..."
  ./gradlew build -q
fi
[[ -f "$JAR" ]] || { echo "!! jar not found: $JAR" >&2; exit 1; }
echo "    jar: $JAR ($(du -h "$JAR" | cut -f1))"

# ---- server -----------------------------------------------------------------
if [[ "$DO_SERVER" == 1 ]]; then
  echo "==> Deploying to server ($CONTAINER on ${SERVER_SSH#*@}) ..."
  scp -q "$JAR" "$SERVER_SSH:/tmp/"
  ssh "$SERVER_SSH" "
    set -e
    docker exec $CONTAINER sh -c 'rm -f $SERVER_MODS/vanillaplusadditions-*.jar'
    docker cp /tmp/$(basename "$JAR") $CONTAINER:$SERVER_MODS/
    docker exec $CONTAINER chown $SERVER_OWNER $SERVER_MODS/$(basename "$JAR")
    rm -f /tmp/$(basename "$JAR")
    echo '    server mods now:'; docker exec $CONTAINER ls -la $SERVER_MODS/ | grep -i vanillaplus
  "
  if [[ "$DO_RESTART" == 1 ]]; then
    ssh "$SERVER_SSH" "echo '    restarting container ...'; docker restart $CONTAINER >/dev/null"
    echo "==> Waiting for server to come back up ..."
    ssh "$SERVER_SSH" '
      for i in $(seq 1 40); do
        if docker exec '"$CONTAINER"' sh -c "grep -q \"Done (\" /AMP/Minecraft/logs/latest.log 2>/dev/null"; then break; fi
        sleep 5
      done
      docker exec '"$CONTAINER"' sh -c "grep -iE \"vanillaplusadditions.*->|Dedicated server took|caught exception|Fatal\" /AMP/Minecraft/logs/latest.log | tail -5"
    '
    echo "    server OK (restarted)"
  else
    echo "    jar pushed, NOT restarted (--no-restart) — restart later to load it."
  fi
fi

# ---- local client -----------------------------------------------------------
if [[ "$DO_CLIENT" == 1 ]]; then
  echo "==> Deploying to local client ..."
  # Match ONLY the running game java process for this instance. --launchTarget
  # forgeclient is unique to the game (the CEF launcher does not have it).
  # Pattern lives in this file, so pgrep cannot self-match (see header).
  if pgrep -f "sebsmodpack4.*--launchTarget forgeclient" >/dev/null 2>&1; then
    echo "!! Minecraft client is RUNNING — refusing to swap the jar (would corrupt it)."
    echo "!! Close the game fully, then re-run: scripts/deploy.sh --client"
    exit 1
  fi
  rm -f "$CLIENT_MODS"/vanillaplusadditions-*.jar
  cp "$JAR" "$CLIENT_MODS/"
  echo "    client mods now:"
  ls -la "$CLIENT_MODS/" | grep -i vanillaplus || true
  echo "    client OK — start Minecraft and connect."
fi

echo "==> Done: ${VERSION} deployed."
