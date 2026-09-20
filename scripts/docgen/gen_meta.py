#!/usr/bin/env python3
"""Fill the generated blocks of the module documentation pages.

Every page in ``docs/modules/`` carries two machine-owned regions::

    <!-- vpa:meta:start -->   ... the fact box ...     <!-- vpa:meta:end -->
    <!-- vpa:config:start --> ... the settings ...     <!-- vpa:config:end -->

Everything outside those markers is written by hand and is never touched here. That split is the
whole point: a new config key changes the page without anybody remembering to update prose, and
prose survives every regeneration.

The script also creates a skeleton page for any module that has none yet, and writes the combined
``docs/reference/config.md``.

Source of truth is ``scripts/docgen/data/modules/*.json`` (checked by ``normalize_facts.py``
against build.gradle and a real config file) plus ``scripts/docgen/data/tested-versions.json``.

Usage:
    python3 scripts/docgen/gen_meta.py [--check]

``--check`` writes nothing and exits non-zero if any page is out of date — for CI.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DATA = REPO / "scripts" / "docgen" / "data"
PAGES = REPO / "docs" / "modules"
REFERENCE = REPO / "docs" / "reference" / "config.md"
RELEASE_BASE = "https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download"

META_START, META_END = "<!-- vpa:meta:start -->", "<!-- vpa:meta:end -->"
CFG_START, CFG_END = "<!-- vpa:config:start -->", "<!-- vpa:config:end -->"

SIDE_LABEL = {"both": "Client + Server", "client": "Client only", "server": "Server only"}

# Only slugs verified against the Modrinth API. A mod we cannot link is named, not guessed at.
MOD_LINKS = {
    "create": ("Create", "https://modrinth.com/mod/create"),
    "aeronautics": ("Create Aeronautics", "https://modrinth.com/mod/create-aeronautics"),
    "sable": ("Sable", "https://modrinth.com/mod/sable"),
    "waystones": ("Waystones", "https://modrinth.com/mod/waystones"),
    "quark": ("Quark", "https://modrinth.com/mod/quark"),
    "curios": ("Curios API", "https://modrinth.com/mod/curios"),
    "jei": ("JEI", "https://modrinth.com/mod/jei"),
    "toughasnails": ("Tough As Nails", "https://modrinth.com/mod/tough-as-nails"),
    "bluemap": ("BlueMap", "https://modrinth.com/plugin/bluemap"),
    "vc_gliders": ("Gliders", "https://modrinth.com/mod/gliders"),
    "enhancedai": ("Enhanced AI", "https://modrinth.com/mod/enhanced-ai"),
    "overpacked": ("Overpacked", "https://modrinth.com/mod/overpacked"),
    "bobo_lib": ("Bobo Lib", "https://modrinth.com/mod/bobo-lib"),
    "mr_dungeons_andtaverns": ("Dungeons and Taverns", "https://modrinth.com/mod/dungeons-and-taverns"),
    "endermanoverhaul": ("Enderman Overhaul", "https://modrinth.com/mod/enderman-overhaul"),
    "alexsmobs": ("Alex's Mobs", "https://modrinth.com/mod/alexs-mobs"),
    "betterfortresses": ("YUNG's Better Nether Fortresses",
                         "https://modrinth.com/mod/yungs-better-nether-fortresses"),
}


def load_facts() -> tuple[dict[str, dict], dict]:
    sheets = {p.stem: json.loads(p.read_text()) for p in sorted((DATA / "modules").glob("*.json"))}
    versions = json.loads((DATA / "tested-versions.json").read_text())
    return sheets, versions


def cell(text: str) -> str:
    """Make a string safe to drop into a markdown table cell."""
    return (text or "").replace("|", "\\|").replace("\n", " ").strip()


def fmt_default(value: object) -> str:
    """Strip Java literal noise so a user sees what they would actually type into the file.

    The fact sheets quote the source, so numbers arrive as ``0.8D``, ``20L``, ``1.5F``.
    Nobody writes that in a TOML file.
    """
    text = str(value).strip()
    if re.fullmatch(r"-?\d+(\.\d+)?[DdFfLl]", text):
        return text[:-1]
    return text


def mod_label(entry: dict, tested: dict) -> str:
    """Render one mod reference: linked where we know the slug, named where we do not."""
    mod_id = entry.get("modId")
    if not mod_id:
        return cell(entry.get("displayName", "an unidentified mod"))
    name, url = MOD_LINKS.get(mod_id, (mod_id, None))
    label = f"[{name}]({url})" if url else f"`{name}`"
    version = tested.get("mods", {}).get(mod_id)
    return f"{label} <sub>tested {version}</sub>" if version else label


def meta_block(module_id: str, facts: dict, versions: dict) -> str:
    tested = versions
    required = ", ".join(mod_label(m, tested) for m in facts["requiredMods"]) or "—"
    optional = ", ".join(mod_label(m, tested) for m in facts["optionalMods"]) or "—"

    if facts["hasStandaloneJar"]:
        jar = f"vpa_{module_id}.jar"
        download = f"[`{jar}`]({RELEASE_BASE}/{jar})"
        needs = ["vpa_core"] + [f"vpa_{d}" for d in facts.get("moduleDeps", [])]
        download += " · also needs " + ", ".join(f"`{n}`" for n in needs)
    else:
        download = "bundle only — no standalone jar"

    rows = [
        ("Module ID", f"`{module_id}`"),
        ("Side", SIDE_LABEL.get(facts["side"], facts["side"])),
        ("Requires", required),
        ("Works with", optional),
        ("Download", download),
        ("Config section", f"`[modules.{module_id}]`"),
        ("Since", "the next release" if facts["sinceVersion"] == "unreleased"
                   else f"`{facts['sinceVersion']}`"),
    ]
    body = "\n".join(f"| **{key}** | {value} |" for key, value in rows)
    return f"{META_START}\n|  |  |\n|---|---|\n{body}\n{META_END}"


def config_block(module_id: str, facts: dict) -> str:
    keys = facts.get("configKeys", [])
    # Only a module that ships its own jar has its own config file; six modules are bundle-only
    # and promising them a vpa_<module>-common.toml sends the reader looking for a file that
    # will never exist.
    standalone_note = (f" (or `config/vpa_{module_id}-common.toml` if you run the standalone jar)"
                       if facts.get("hasStandaloneJar") else "")
    head = (f"{CFG_START}\n## Configuration\n\n"
            f"Section `[modules.{module_id}]` in `config/vanillaplusadditions-common.toml`"
            f"{standalone_note}.\n\n"
            f"Every module also has the universal `enabled` and `debug_logging` keys — see the"
            f" [Configuration Guide](../guides/configuration.md).\n")
    if not keys:
        return head + "\nThis module has no settings of its own.\n" + CFG_END

    lines = ["", "| Key | Type | Default | Range | Effect |", "|---|---|---|---|---|"]
    for entry in sorted(keys, key=lambda k: k["key"]):
        lines.append(f"| `{cell(entry['key'])}` | {cell(entry.get('type', ''))} "
                     f"| `{cell(fmt_default(entry.get('defaultValue', '')))}` "
                     f"| {cell(entry.get('range', '')) or '—'} | {cell(entry.get('effect', ''))} |")
    return head + "\n".join(lines) + "\n" + CFG_END


def skeleton(module_id: str, facts: dict, versions: dict) -> str:
    """A fresh page: generated blocks in place, prose headings waiting to be written."""
    return "\n".join([
        f"# {facts['displayName']}",
        "",
        f"> **TL;DR** — {facts['oneLiner']}",
        "",
        meta_block(module_id, facts, versions),
        "",
        "## What it does",
        "",
        "<!-- TODO: written by hand -->",
        "",
        config_block(module_id, facts),
        "",
        "## See also",
        "",
        "* [Configuration Guide](../guides/configuration.md)",
        "* [All modules](../../README.md#modules)",
        "",
    ])


def replace_block(text: str, start: str, end: str, new: str) -> tuple[str, bool]:
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.S)
    if not pattern.search(text):
        return text, False
    return pattern.sub(lambda _: new, text, count=1), True


def reference_page(sheets: dict[str, dict], versions: dict) -> str:
    total = sum(len(f.get("configKeys", [])) for f in sheets.values())
    out = [
        "# Configuration Reference",
        "",
        f"Every setting of every module — {total} module-specific keys across {len(sheets)} modules,",
        "generated from the source. For what the file is and where it lives, see the",
        "[Configuration Guide](../guides/configuration.md).",
        "",
        "Two keys exist in every module and are not repeated below:",
        "",
        "| Key | Type | Default | Effect |",
        "|---|---|---|---|",
        "| `enabled` | boolean | varies | Whether the module is active. |",
        "| `debug_logging` | enum | `AUTO` | `AUTO`, `ON` or `OFF`. |",
        "",
    ]
    for module_id in sorted(sheets):
        facts = sheets[module_id]
        keys = facts.get("configKeys", [])
        out.append(f"## `{module_id}` — {facts['displayName']}")
        out.append("")
        out.append(f"{facts['oneLiner']} · [full page](../modules/{module_id}.md)")
        out.append("")
        if not keys:
            out.append("No settings of its own.")
            out.append("")
            continue
        out.append("| Key | Type | Default | Range | Effect |")
        out.append("|---|---|---|---|---|")
        for entry in sorted(keys, key=lambda k: k["key"]):
            out.append(f"| `{cell(entry['key'])}` | {cell(entry.get('type', ''))} "
                       f"| `{cell(fmt_default(entry.get('defaultValue', '')))}` "
                       f"| {cell(entry.get('range', '')) or '—'} | {cell(entry.get('effect', ''))} |")
        out.append("")
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report only; non-zero exit if stale")
    args = parser.parse_args()

    sheets, versions = load_facts()
    PAGES.mkdir(parents=True, exist_ok=True)
    REFERENCE.parent.mkdir(parents=True, exist_ok=True)

    created, updated, unchanged, no_markers = [], [], [], []

    for module_id in sorted(sheets):
        facts = sheets[module_id]
        page = PAGES / f"{module_id}.md"
        if not page.exists():
            created.append(module_id)
            if not args.check:
                page.write_text(skeleton(module_id, facts, versions))
            continue

        text = original = page.read_text()
        text, had_meta = replace_block(text, META_START, META_END,
                                       meta_block(module_id, facts, versions))
        text, had_config = replace_block(text, CFG_START, CFG_END, config_block(module_id, facts))
        if not (had_meta and had_config):
            no_markers.append(f"{module_id} (meta={'y' if had_meta else 'n'}, "
                              f"config={'y' if had_config else 'n'})")
        if text != original:
            updated.append(module_id)
            if not args.check:
                page.write_text(text)
        else:
            unchanged.append(module_id)

    reference = reference_page(sheets, versions)
    reference_stale = not REFERENCE.exists() or REFERENCE.read_text() != reference
    if reference_stale and not args.check:
        REFERENCE.write_text(reference)

    verb = "would create" if args.check else "created"
    print(f"{len(sheets)} modules: {verb} {len(created)}, "
          f"{'would update' if args.check else 'updated'} {len(updated)}, "
          f"unchanged {len(unchanged)}")
    if created:
        print(f"  new pages: {', '.join(created)}")
    if no_markers:
        print(f"\n  {len(no_markers)} pages are missing generated blocks — they still carry "
              f"hand-written config sections:")
        for item in no_markers:
            print(f"    {item}")
    print(f"\n  reference page: {'stale' if reference_stale else 'up to date'} ({REFERENCE.relative_to(REPO)})")

    if args.check and (created or updated or reference_stale or no_markers):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
