#!/usr/bin/env bash
#
# deploy-win.sh — Windows / Git-Bash deploy.
#
# Two local targets, never the server. games2 is a live server and is deployed
# exclusively from Gerry's Linux box via scripts/deploy.sh --server. Nothing in
# this file touches ssh, scp or docker.
#
#   (no flag)   copy the jar into the modpack project:
#               ~/ClaudeProjekte/MinecraftModpack/mods/
#               Just a staging copy — no game reads that folder, so no
#               running-game check is needed.
#
#   --client    swap the jar in the live client mods dir (.minecraft etc.),
#               guarded by the running-game check below.
#
# Why a separate script instead of patching deploy.sh:
#   deploy.sh encodes Gerry's Linux box (Prism-style instance dir, pgrep) and
#   drives the live server, so we keep Windows branches out of it. This file
#   mirrors its client half with two platform fixes:
#
#   1. CLIENT MODS DIR is auto-detected instead of hardcoded to
#      ~/.minecraft-instances/sebsmodpack4/mods (that layout does not exist on
#      Windows). Override anytime with:  VPA_CLIENT_MODS=/c/path/to/mods
#      The modpack target likewise honours VPA_MODPACK_MODS.
#
#   2. RUNNING-GAME CHECK uses PowerShell (Win32_Process), because `pgrep` does
#      not exist in Git Bash. This matters more than it looks: in deploy.sh the
#      check is `if pgrep -f ... >/dev/null 2>&1`, so a MISSING pgrep is a
#      "command not found" swallowed by 2>&1 → condition false → the script
#      happily swaps the jar as if the game were closed. Here the check FAILS
#      LOUD: if we cannot determine the game state, we abort instead of guessing.
#
# NEVER swap the jar in a RUNNING client — it corrupts lazy zip reads
# (ZipException "invalid stored block lengths" → missing textures).
#
# Usage:
#   scripts/deploy-win.sh                   # build + copy into MinecraftModpack/mods
#   scripts/deploy-win.sh --no-build        # same, using the existing jar
#   scripts/deploy-win.sh --client          # build + deploy to the live client
#   scripts/deploy-win.sh --client --no-build
#   scripts/deploy-win.sh --client --force  # skip the running-game check (last resort)
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODPACK_MODS_DEFAULT="$HOME/ClaudeProjekte/MinecraftModpack/mods"

DO_BUILD=1; DO_CLIENT=0; FORCE=0
for arg in "$@"; do
  case "$arg" in
    --client)     DO_CLIENT=1 ;;
    --no-build)   DO_BUILD=0 ;;
    --force)      FORCE=1 ;;
    --server|--no-restart)
      echo "!! $arg is not supported here — this script deploys locally only." >&2
      echo "!! Server deploys run from Gerry's box: scripts/deploy.sh --server" >&2
      exit 2 ;;
    -h|--help)
      sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//; /^set -euo/d'
      exit 0 ;;
    *) echo "unknown arg: $arg" >&2; exit 2 ;;
  esac
done

cd "$REPO_ROOT"
VERSION="$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)"
JAR="build/libs/vanillaplusadditions-${VERSION}.jar"
if [[ "$DO_CLIENT" == 1 ]]; then
  echo "==> VanillaPlusAdditions ${VERSION} (windows/git-bash, target: live client)"
else
  echo "==> VanillaPlusAdditions ${VERSION} (windows/git-bash, target: modpack project)"
fi

# ---- build ------------------------------------------------------------------
if [[ "$DO_BUILD" == 1 ]]; then
  echo "==> Building (./gradlew build) ..."
  ./gradlew build -q
fi
[[ -f "$JAR" ]] || { echo "!! jar not found: $JAR" >&2; exit 1; }
echo "    jar: $JAR ($(du -h "$JAR" | cut -f1))"

# ---- helper: is the Minecraft client running? -------------------------------
# Prints RUNNING, STOPPED, or a failure token. Anything that is not a definitive
# STOPPED makes the caller abort — we never swap a jar on a guess.
client_state() {
  local ps_exe ps_script out
  ps_exe="$(command -v powershell.exe || command -v pwsh.exe || true)"
  [[ -n "$ps_exe" ]] || { echo "NO_POWERSHELL"; return 0; }

  # Markers only a running game carries; gradle daemons and VS Code java
  # language servers do not have them.
  ps_script=$(cat <<'PSEOF'
$ErrorActionPreference = 'Stop'
$pat = '--launchTarget\s+\S*client|net\.minecraft\.client\.main\.Main|--gameDir'
$procs = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'"
$hit = $procs | Where-Object { $_.CommandLine -and $_.CommandLine -match $pat }
if ($hit) { 'RUNNING' } else { 'STOPPED' }
PSEOF
)
  out="$("$ps_exe" -NoProfile -NonInteractive -Command "$ps_script" 2>/dev/null | tr -d '\r' | tail -1)" || out="QUERY_FAILED"
  echo "${out:-QUERY_FAILED}"
}

# ---- helper: locate the live client mods dir --------------------------------
resolve_client_mods() {
  if [[ -n "${VPA_CLIENT_MODS:-}" ]]; then
    echo "$VPA_CLIENT_MODS"; return 0
  fi
  local appdata="$HOME/AppData/Roaming"
  if [[ -n "${APPDATA:-}" ]] && command -v cygpath >/dev/null 2>&1; then
    appdata="$(cygpath -u "$APPDATA")"
  fi
  local c
  for c in "$appdata/.minecraft/mods" \
           "$appdata/PrismLauncher/instances/sebsmodpack4/.minecraft/mods" \
           "$HOME/curseforge/minecraft/Instances/sebsmodpack4/mods" \
           "$HOME/.minecraft-instances/sebsmodpack4/mods"; do
    [[ -d "$c" ]] && { echo "$c"; return 0; }
  done
  return 1
}

# ---- helper: drop the jar in, replacing older VPA jars ----------------------
install_jar() {
  local dest="$1"
  rm -f "$dest"/vanillaplusadditions-*.jar
  cp "$JAR" "$dest/"
  echo "    mods now:"
  ls -la "$dest/" | grep -i vanillaplus || true
}

# ---- target: live client ----------------------------------------------------
if [[ "$DO_CLIENT" == 1 ]]; then
  echo "==> Deploying to local client ..."

  CLIENT_MODS="$(resolve_client_mods)" || {
    echo "!! No client mods dir found." >&2
    echo "!! Set it explicitly, e.g.:" >&2
    echo "!!   VPA_CLIENT_MODS=\"\$APPDATA/.minecraft/mods\" scripts/deploy-win.sh --client --no-build" >&2
    exit 1
  }
  [[ -d "$CLIENT_MODS" ]] || { echo "!! not a directory: $CLIENT_MODS" >&2; exit 1; }
  echo "    mods dir: $CLIENT_MODS"

  STATE="$(client_state)"
  case "$STATE" in
    STOPPED)
      ;;
    RUNNING)
      echo "!! Minecraft client is RUNNING — refusing to swap the jar (would corrupt it)." >&2
      echo "!! Close the game fully, then re-run: scripts/deploy-win.sh --client --no-build" >&2
      exit 1 ;;
    NO_POWERSHELL)
      echo "!! Cannot verify whether Minecraft is running: no powershell.exe/pwsh.exe found." >&2
      echo "!! Refusing to swap the jar blindly. Re-run with --force only if you are" >&2
      echo "!! certain the game is closed." >&2
      [[ "$FORCE" == 1 ]] || exit 1
      echo "    --force given, continuing without the running-game check." ;;
    *)
      echo "!! Running-game check failed (got: '$STATE'). Refusing to swap the jar." >&2
      [[ "$FORCE" == 1 ]] || exit 1
      echo "    --force given, continuing without the running-game check." ;;
  esac

  install_jar "$CLIENT_MODS"
  echo "    client OK — start Minecraft FRESH (never hot-swap into a running game)."
  echo "==> Done: ${VERSION} deployed to client."
  exit 0
fi

# ---- target: modpack project (default) --------------------------------------
echo "==> Copying into the modpack project ..."

MODPACK_MODS="${VPA_MODPACK_MODS:-$MODPACK_MODS_DEFAULT}"
if [[ ! -d "$MODPACK_MODS" ]]; then
  parent="$(dirname "$MODPACK_MODS")"
  if [[ -d "$parent" ]]; then
    mkdir -p "$MODPACK_MODS"
  else
    echo "!! Modpack project not found: $parent" >&2
    echo "!! Set the target explicitly, e.g.:" >&2
    echo "!!   VPA_MODPACK_MODS=/c/path/to/mods scripts/deploy-win.sh --no-build" >&2
    exit 1
  fi
fi
echo "    mods dir: $MODPACK_MODS"

install_jar "$MODPACK_MODS"
echo "    copy OK — nothing was touched in the live client (use --client for that)."
echo "==> Done: ${VERSION} copied to the modpack project."
