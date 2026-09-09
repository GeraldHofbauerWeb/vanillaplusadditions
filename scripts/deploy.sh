#!/usr/bin/env bash
#
# deploy.sh — Build VPA and deploy to the games2 server + local client.
#
# Three-target deploy in one shot:
#   1. games2 server (AMP/Docker, container AMP_SebsModpackv401) — docker cp + restart
#   2. local client (the instance ~/.minecraft points at) — jar swap
#   3. the public modpack archive on geraldhofbauer.net — rebuild the zip and
#      replace the Craft asset behind /files/sebs-modpack-v5
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
#   scripts/deploy.sh                    # build + server + client + modpack archive
#   scripts/deploy.sh --server           # server only
#   scripts/deploy.sh --client           # local client only
#   scripts/deploy.sh --modpack          # modpack archive only (zip + homepage)
#   scripts/deploy.sh --server --client  # combine freely; naming any target
#                                        #   deselects the ones you did not name
#   scripts/deploy.sh --server --no-restart  # push jar to server, do NOT restart
#   scripts/deploy.sh --no-build         # skip gradle build, use existing jar
#
# The modpack step REFUSES to publish a version that has no matching git tag.
# The archive is public and people install from it; two different jars carrying
# one version number is exactly the confusion that costs an evening to untangle.
# Override with --modpack-untagged when you really mean it.
#
set -euo pipefail

# ---- config -----------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_SSH="gerry@82.165.95.152"
CONTAINER="AMP_SebsModpackv401"
SERVER_MODS="/AMP/Minecraft/mods"
SERVER_OWNER="amp:amp"

# Modpack archive. The web root and the game server share one box.
MODPACK_DIR="$HOME/.minecraft/meincraft"
MODPACK_ZIP="$HOME/Downloads/sebsmodpack-v5.zip"
WEB_TMP="/var/www/geraldhofbauer.net/storage"
WEB_FILE="sebsmodpack-v5.zip"
CRAFT_MCP="https://geraldhofbauer.net/mcp/"
CRAFT_ENTRY=1317          # staticFiles entry "Sebs Modpack v5"
CRAFT_FOLDER=4            # staticFiles volume root folder
PUBLIC_URL="https://geraldhofbauer.net/static-files/$WEB_FILE"
# Follow the ~/.minecraft symlink — the instance manager flips it when switching
# instances, so hardcoding a name silently deploys into an inactive instance
# (bit us on the v4 -> v5 switch). Fall back to the symlink target only if it
# actually resolves into the instances dir.
if [ -L "$HOME/.minecraft" ] && CLIENT_DIR="$(readlink -f "$HOME/.minecraft")" \
   && [ -d "$CLIENT_DIR/mods" ]; then
  :
else
  echo "!! ~/.minecraft is not a symlink to a usable instance" >&2
  exit 1
fi
CLIENT_MODS="$CLIENT_DIR/mods"

# Empty means "not named". Naming any target switches the unnamed ones off, so
# --server --client works as a combination instead of the last flag winning.
DO_SERVER=; DO_CLIENT=; DO_MODPACK=; DO_BUILD=1; DO_RESTART=1; MODPACK_UNTAGGED=0
for arg in "$@"; do
  case "$arg" in
    --server)     DO_SERVER=1 ;;
    --client)     DO_CLIENT=1 ;;
    --modpack)    DO_MODPACK=1 ;;
    --modpack-untagged) DO_MODPACK=1; MODPACK_UNTAGGED=1 ;;
    --no-build)   DO_BUILD=0 ;;
    --no-restart) DO_RESTART=0 ;;
    -h|--help)
      sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//; /^set -euo/d'
      exit 0 ;;
    *) echo "unknown arg: $arg" >&2; exit 2 ;;
  esac
done
if [[ -z "${DO_SERVER}${DO_CLIENT}${DO_MODPACK}" ]]; then
  DO_SERVER=1; DO_CLIENT=1; DO_MODPACK=1
fi
DO_SERVER=${DO_SERVER:-0}; DO_CLIENT=${DO_CLIENT:-0}; DO_MODPACK=${DO_MODPACK:-0}

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

# ---- modpack archive --------------------------------------------------------
# Rebuilds ~/Downloads/sebsmodpack-v5.zip from the distribution folder and swaps
# the asset behind the Craft entry. The asset is DELETED and re-uploaded under
# the identical filename on purpose: uploading alongside would make Craft mint
# "sebsmodpack-v5_1.zip" and every link already handed out would rot.
if [[ "$DO_MODPACK" == 1 ]]; then
  echo "==> Publishing modpack archive ..."
  [[ -d "$MODPACK_DIR/mods" ]] || { echo "!! no modpack dir: $MODPACK_DIR" >&2; exit 1; }

  if [[ "$MODPACK_UNTAGGED" != 1 ]] && ! git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/v${VERSION}" >/dev/null; then
    echo "!! ${VERSION} has no git tag — refusing to publish it to a public download." >&2
    echo "!! Tag and release first, or re-run with --modpack-untagged." >&2
    exit 1
  fi

  # Craft's MCP endpoint, spoken directly. The bearer token lives in the Claude
  # Code settings; never echo it.
  mcp() {
    local token
    token="$(python3 -c "import json;a=json.load(open('$HOME/.claude/settings.json'))['mcpServers']['craft-cms']['args'];print([x for x in a if x.startswith('Authorization:')][0].split()[-1])")"
    curl -s -X POST "$CRAFT_MCP" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"$1\",\"arguments\":$2}}" \
      | sed 's/^data: //' | grep -v '^$'
  }

  echo "    syncing jar into $MODPACK_DIR/mods ..."
  rm -f "$MODPACK_DIR"/mods/vanillaplusadditions-*.jar
  cp "$JAR" "$MODPACK_DIR/mods/"

  echo "    zipping ..."
  rm -f "$MODPACK_ZIP"
  # -x drops the config backups the options module leaves behind; they are dead
  # weight in a public download.
  (cd "$MODPACK_DIR" && zip -1 -r -q "$MODPACK_ZIP" mods config resourcepacks options.txt \
      -x '*.toml.bak' '*.toml.bak-*' '*/options_backups/*')
  ZIP_SIZE="$(stat -c%s "$MODPACK_ZIP")"
  echo "    $(basename "$MODPACK_ZIP") — $(du -h "$MODPACK_ZIP" | cut -f1), $(unzip -l "$MODPACK_ZIP" | tail -1 | awk '{print $2}') files"

  echo "    uploading ..."
  scp -q "$MODPACK_ZIP" "$SERVER_SSH:$WEB_TMP/$WEB_FILE"
  ssh "$SERVER_SSH" "chmod 666 $WEB_TMP/$WEB_FILE"

  OLD_ASSET="$(mcp get_entry "{\"id\":$CRAFT_ENTRY}" | python3 -c "
import sys, json
e = json.loads(json.load(sys.stdin)['result']['content'][0]['text'])
f = e['fields']['staticFile']
sys.stderr.write(e['fields']['fileDescription'])
print(f[0]['id'] if f else 0)
" 2>"$MODPACK_ZIP.desc")"
  [[ -s "$MODPACK_ZIP.desc" ]] || { echo "!! could not read entry $CRAFT_ENTRY" >&2; exit 1; }

  if [[ "$OLD_ASSET" != 0 ]]; then
    mcp delete_asset "{\"id\":$OLD_ASSET}" >/dev/null
  fi
  NEW_ASSET="$(mcp upload_asset "{\"folderId\":$CRAFT_FOLDER,\"filename\":\"$WEB_FILE\",\"tempFilePath\":\"$WEB_TMP/$WEB_FILE\"}" \
    | python3 -c "import sys,json;print(json.loads(json.load(sys.stdin)['result']['content'][0]['text'])['id'])")"
  echo "    asset $OLD_ASSET -> $NEW_ASSET (same filename, so the public URL is unchanged)"

  # Keep the version named in the description honest.
  python3 - "$MODPACK_ZIP.desc" "$VERSION" "$CRAFT_ENTRY" "$NEW_ASSET" > "$MODPACK_ZIP.update" <<'PYEOF'
import io, json, re, sys
desc = io.open(sys.argv[1], encoding="utf-8").read()
short = re.sub(r'^1\.0\.0-', '', sys.argv[2])
desc = re.sub(r'\(beta\.\d+\)', '(%s)' % short, desc)
print(json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {
    "name": "update_entry",
    "arguments": {"id": int(sys.argv[3]),
                  "fields": {"staticFile": [int(sys.argv[4])], "fileDescription": desc}}}}))
PYEOF
  TOKEN="$(python3 -c "import json;a=json.load(open('$HOME/.claude/settings.json'))['mcpServers']['craft-cms']['args'];print([x for x in a if x.startswith('Authorization:')][0].split()[-1])")"
  curl -s -X POST "$CRAFT_MCP" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
    --data-binary "@$MODPACK_ZIP.update" >/dev/null
  rm -f "$MODPACK_ZIP.desc" "$MODPACK_ZIP.update"

  LIVE_SIZE="$(curl -sI "$PUBLIC_URL" | awk 'tolower($1)=="content-length:"{print $2+0}')"
  if [[ "$LIVE_SIZE" == "$ZIP_SIZE" ]]; then
    echo "    live OK — $PUBLIC_URL ($LIVE_SIZE bytes)"
  else
    echo "!! live size $LIVE_SIZE != local $ZIP_SIZE — check the entry by hand" >&2
    exit 1
  fi
fi

echo "==> Done: ${VERSION} deployed."
