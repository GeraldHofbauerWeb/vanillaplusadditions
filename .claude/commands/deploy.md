---
description: Build VPA and deploy to games2 server + local client (flags: --server / --client / --no-restart / --no-build)
allowed-tools: Bash(./scripts/deploy.sh:*), Bash(bash scripts/deploy.sh:*)
---

Run the project deploy script, passing through any flags the user gave in `$ARGUMENTS`:

```
bash scripts/deploy.sh $ARGUMENTS
```

The script handles everything correctly and deterministically — do NOT re-implement
its steps inline (the running-client check must run from the file, or a `pgrep`
pattern self-matches and falsely reports "game running").

Flags (combine as needed):
- (no flag)      build + deploy to **server (with restart) AND local client**
- `--server`     server only
- `--client`     local client only
- `--no-restart` (server) push the jar but do **not** restart the container
- `--no-build`   skip the gradle build, reuse the existing jar

After it runs, report to the user: which version, server status (restarted / pushed-only),
and — if the client got a new jar — remind them Minecraft must be started fresh
(never hot-swap a running client). If the client swap was skipped because the game
was running, tell them to close MC and re-run `/deploy --client`.
