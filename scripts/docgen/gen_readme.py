#!/usr/bin/env python3
"""Generate the module table and the tested-combination block in README.md.

The README is the front door: one table, every module, what it needs and where to get it. With
46 modules that table cannot be maintained by hand — it would be wrong within a week. So it is
generated from the same checked fact sheets everything else uses, and written between markers::

    <!-- vpa:table:start -->  ...  <!-- vpa:table:end -->
    <!-- vpa:tested:start --> ...  <!-- vpa:tested:end -->

Everything else in the README is hand-written and never touched.

A module missing from ``data/categories.json`` is an error, not a silent omission — that is how a
new module is stopped from quietly staying out of the overview.

Usage:
    python3 scripts/docgen/gen_readme.py [--check]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DATA = REPO / "scripts" / "docgen" / "data"
README = REPO / "README.md"
RELEASE_BASE = "https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download"

TABLE_START, TABLE_END = "<!-- vpa:table:start -->", "<!-- vpa:table:end -->"
TESTED_START, TESTED_END = "<!-- vpa:tested:start -->", "<!-- vpa:tested:end -->"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_meta import MOD_LINKS, cell  # noqa: E402  (same data, one definition)


def load() -> tuple[dict, dict, dict, dict]:
    sheets = {p.stem: json.loads(p.read_text()) for p in sorted((DATA / "modules").glob("*.json"))}
    categories = json.loads((DATA / "categories.json").read_text())
    versions = json.loads((DATA / "tested-versions.json").read_text())
    floors = json.loads((DATA / "mod-versions.json").read_text())["mods"]
    return sheets, categories, versions, floors


def mod_name(mod_id: str | None, fallback: str) -> str:
    if not mod_id:
        return fallback or "an unidentified mod"
    name, url = MOD_LINKS.get(mod_id, (mod_id, None))
    return f"[{name}]({url})" if url else name


def requires_cell(facts: dict, floors: dict) -> str:
    """What this module needs, and what merely makes it better — with the floor we can prove."""
    parts = []
    for entry in facts["requiredMods"]:
        mod_id = entry.get("modId")
        label = mod_name(mod_id, entry.get("displayName", ""))
        floor = floors.get(mod_id, {}).get("min") if mod_id else None
        parts.append(f"**{label}**" + (f" {floor}+" if floor else ""))
    required = ", ".join(parts) if parts else "—"

    optional = [mod_name(e.get("modId"), e.get("displayName", "")) for e in facts["optionalMods"]]
    if optional:
        required += f"<br><sub>better with: {', '.join(optional)}</sub>"
    return required


def download_cell(module_id: str, facts: dict) -> str:
    if not facts["hasStandaloneJar"]:
        return "*bundle only*"
    jar = f"vpa_{module_id}.jar"
    extra = facts.get("moduleDeps", [])
    suffix = f"<br><sub>+ {', '.join('`vpa_' + d + '`' for d in extra)}</sub>" if extra else ""
    return f"[`{jar}`]({RELEASE_BASE}/{jar})" + suffix


def build_table(sheets: dict, categories: dict, floors: dict) -> tuple[str, list[str]]:
    problems = []
    assigned = categories["modules"]
    for module_id in sheets:
        if module_id not in assigned:
            problems.append(f"{module_id} has no category in data/categories.json")

    lines = [
        "| Module | Docs | Download | Requires |",
        "|---|---|---|---|",
        "| **Vanilla Plus Additions** — every module in one jar |"
        " [All modules](#-modules) |"
        f" [`vanillaplusadditions.jar`]({RELEASE_BASE}/vanillaplusadditions.jar) |"
        " NeoForge 21.1+ · Minecraft 1.21.1 |",
    ]
    for category in categories["order"]:
        members = sorted(m for m, c in assigned.items() if c == category and m in sheets)
        if not members:
            continue
        lines.append(f"| **{category}** | | | |")
        for module_id in members:
            facts = sheets[module_id]
            lines.append(
                f"| **{cell(facts['displayName'])}**<br><sub>`{module_id}`</sub> "
                f"| [Docs](docs/modules/{module_id}.md) "
                f"| {download_cell(module_id, facts)} "
                f"| {requires_cell(facts, floors)} |")
    return "\n".join(lines), problems


def build_tested(versions: dict) -> str:
    platform = versions["platform"]
    mods = versions["mods"]
    viewer = versions["recipeViewer"]
    lines = [
        "<details>",
        "<summary><b>The exact combination this is played on</b> — "
        "“compatible” means tested, and this is what was tested</summary>",
        "",
        f"Minecraft **{platform['minecraft']}** · NeoForge **{platform['neoforge']}** · "
        f"Java **{platform['javaRuntime']}**",
        "",
        "| Mod | Version |",
        "|---|---|",
    ]
    for mod_id in sorted(mods):
        name, url = MOD_LINKS.get(mod_id, (mod_id, None))
        lines.append(f"| {f'[{name}]({url})' if url else name} | `{mods[mod_id]}` |")
    lines += [
        "",
        f"Recipe viewer: **EMI {viewer['emi']}** with "
        f"**TooManyRecipeViewers {viewer['toomanyrecipeviewers']}**. There is no JEI in this pack — "
        "the JEI integrations are compiled against the JEI API and reach the screen through that "
        "bridge, so JEI itself is supported but untested here.",
        "",
        "Anything older than the floors in the table above is simply unknown, not known to be "
        "broken. If you run a different combination and it works, say so in an issue and it goes "
        "in this list.",
        "</details>",
    ]
    return "\n".join(lines)


def replace(text: str, start: str, end: str, body: str) -> tuple[str, bool]:
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.S)
    if not pattern.search(text):
        return text, False
    return pattern.sub(lambda _: f"{start}\n{body}\n{end}", text, count=1), True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    sheets, categories, versions, floors = load()
    table, problems = build_table(sheets, categories, floors)
    tested = build_tested(versions)

    text = original = README.read_text()
    # The module count appears twice in prose; both must follow the data, not a memory of it.
    count = len(sheets)
    text = re.sub(r"(modules-)\d+(-blue)", rf"\g<1>{count}\g<2>", text)
    text = re.sub(r"\*\*\d+ small fixes", f"**{count} small fixes", text)
    text, had_table = replace(text, TABLE_START, TABLE_END, table)
    text, had_tested = replace(text, TESTED_START, TESTED_END, tested)
    if not had_table:
        problems.append(f"README.md has no {TABLE_START} block")
    if not had_tested:
        problems.append(f"README.md has no {TESTED_START} block")

    stale = text != original
    if stale and not args.check:
        README.write_text(text)

    print(f"{len(sheets)} modules in {len(categories['order'])} categories; "
          f"README table {'stale' if stale else 'up to date'}")
    for problem in problems:
        print(f"  ! {problem}")
    return 1 if problems or (args.check and stale) else 0


if __name__ == "__main__":
    sys.exit(main())
