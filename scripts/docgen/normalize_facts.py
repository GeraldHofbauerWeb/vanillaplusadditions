#!/usr/bin/env python3
"""Normalise and cross-check the per-module fact sheets in scripts/docgen/data/modules/.

The fact sheets are produced by reading the source; this script turns them into something the
generators can rely on, and — more importantly — checks them against two independent sources:

  * ``build.gradle``  for which modules ship a standalone jar and which module jars depend on
    each other, and
  * a generated ``vanillaplusadditions-common.toml`` for the set of config keys per module.

Where the fact sheets disagree with those, the script reports it rather than quietly papering
over it: a mismatch means either the facts or the build is wrong, and both are worth knowing.

Idempotent — running it twice changes nothing the second time.

Usage:
    python3 scripts/docgen/normalize_facts.py [--toml PATH] [--check]

``--check`` exits non-zero on any problem instead of writing, for use in CI.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DATA = REPO / "scripts" / "docgen" / "data" / "modules"
MODULES_SRC = REPO / "src/main/java/net/geraldhofbauer/vanillaplusadditions/modules"
BUILD_GRADLE = REPO / "build.gradle"
ENTRYPOINT = REPO / "src/main/java/net/geraldhofbauer/vanillaplusadditions/VanillaPlusAdditions.java"
DEFAULT_TOML = Path.home() / ".minecraft-instances/sebsmodpack5/config/vanillaplusadditions-common.toml"

LIST_FIELDS = ["requiredMods", "optionalMods", "items", "blocks", "entities", "commands",
               "keybinds", "mixins", "events", "recipes", "configKeys"]
TEXT_FIELDS = ["displayName", "shortDescription", "oneLiner", "side", "sinceVersion",
               "sinceCommit", "notes"]
UNIVERSAL_KEYS = {"enabled", "debug_logging"}


def parse_standalone_modules(text: str) -> dict[str, list[str]]:
    """Return {moduleId: [moduleDeps]} for every entry of build.gradle's standaloneModules list."""
    start = text.index("def standaloneModules = [")
    depth, i = 0, start + len("def standaloneModules = ")
    for i in range(i, len(text)):
        if text[i] == "[":
            depth += 1
        elif text[i] == "]":
            depth -= 1
            if depth == 0:
                break
    block = text[start:i + 1]
    out: dict[str, list[str]] = {}
    for entry in re.finditer(r"moduleId:\s*'([^']+)'(.*?)(?=moduleId:\s*'|\Z)", block, re.S):
        module_id, body = entry.group(1), entry.group(2)
        deps_match = re.search(r"moduleDeps:\s*\[([^\]]*)\]", body)
        deps = re.findall(r"'([^']+)'", deps_match.group(1)) if deps_match else []
        out[module_id] = deps
    return out


def flatten_keys(section: dict, prefix: str = "") -> set[str]:
    """Flatten a config section to dotted leaf keys: {"combat": {"x": 1}} -> {"combat.x"}.

    Modules group related settings into sub-tables, and the fact sheets name those settings the
    way a user writes them in the file — dotted. So must we, or every grouped module looks like
    a mismatch.
    """
    keys: set[str] = set()
    for name, value in section.items():
        path = f"{prefix}{name}"
        if isinstance(value, dict):
            keys |= flatten_keys(value, f"{path}.")
        else:
            keys.add(path)
    return keys


def parse_toml_keys(path: Path) -> dict[str, set[str]]:
    """Return {moduleId: {dotted config keys}} from a generated config file, minus the universal ones."""
    with path.open("rb") as handle:
        data = tomllib.load(handle)
    modules = data.get("modules", {})
    return {mid: flatten_keys(section) - UNIVERSAL_KEYS
            for mid, section in modules.items() if isinstance(section, dict)}


def split_internal_deps(sheet: dict) -> list[str]:
    """Move vpa_* entries out of required/optionalMods into moduleDeps / relatedModules."""
    notes = []
    module_deps = list(sheet.get("moduleDeps", []))
    related = list(sheet.get("relatedModules", []))
    for field, target in (("requiredMods", module_deps), ("optionalMods", related)):
        keep = []
        for entry in sheet.get(field, []):
            mod_id = entry.get("modId") or ""
            if not mod_id:
                # Already normalised on an earlier run: a mod we can only name, not identify.
                keep.append(entry)
                continue
            bare = mod_id.split(" ")[0]
            if bare.startswith("vpa_"):
                module = bare[len("vpa_"):]
                if module not in target:
                    target.append(module)
                notes.append(f"{field}: moved {bare} -> {'moduleDeps' if field == 'requiredMods' else 'relatedModules'}")
                continue
            if " " in mod_id:
                # A mod named only in prose — keep the knowledge, drop the fake id.
                entry["displayName"] = re.sub(r"\s*\(.*\)\s*$", "", mod_id).strip()
                entry["modId"] = None
                entry["unverifiedModId"] = True
                notes.append(f"{field}: {entry['displayName']} has no provable mod id")
            keep.append(entry)
        sheet[field] = keep
    sheet["moduleDeps"] = module_deps
    sheet["relatedModules"] = related
    return notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--toml", type=Path, default=DEFAULT_TOML,
                        help="a generated vanillaplusadditions-common.toml to cross-check keys against")
    parser.add_argument("--check", action="store_true", help="report only, exit non-zero on problems")
    args = parser.parse_args()

    source_dirs = sorted(p.name for p in MODULES_SRC.iterdir() if p.is_dir())
    sheets = {p.stem: json.loads(p.read_text()) for p in sorted(DATA.glob("*.json"))}

    problems: list[str] = []
    changes: list[str] = []
    warnings: list[str] = []

    missing = set(source_dirs) - set(sheets)
    extra = set(sheets) - set(source_dirs)
    if missing:
        problems.append(f"no fact sheet for: {', '.join(sorted(missing))}")
    if extra:
        problems.append(f"fact sheet without a module directory: {', '.join(sorted(extra))}")

    standalone = parse_standalone_modules(BUILD_GRADLE.read_text())
    registered = set(re.findall(r"registerModule\(new (\w+)Module\(\)\)", ENTRYPOINT.read_text()))
    toml_keys = parse_toml_keys(args.toml) if args.toml.exists() else {}
    if not toml_keys:
        # A generated config file only exists where the mod has actually run. On a CI runner it
        # never does, and that is not a defect in the data - it only means this one cross-check
        # could not be performed. Warn, do not fail.
        warnings.append(f"no config file at {args.toml} - the config-key cross-check was skipped "
                        f"(pass --toml to point at one)")

    for module_id, sheet in sheets.items():
        for field in LIST_FIELDS:
            sheet.setdefault(field, [])
        for field in TEXT_FIELDS:
            sheet.setdefault(field, "")

        version = sheet["sinceVersion"]
        if version and version[0].isdigit():
            sheet["sinceVersion"] = "v" + version
            changes.append(f"{module_id}: sinceVersion {version} -> v{version}")

        for note in split_internal_deps(sheet):
            changes.append(f"{module_id}: {note}")

        # camelCase the directory name the way the module classes are named: mob_drops -> MobDrops
        class_name = "".join(part.capitalize() for part in module_id.split("_"))
        is_registered = class_name in registered
        if sheet.get("registeredInBundle") != is_registered:
            problems.append(f"{module_id}: registeredInBundle={sheet.get('registeredInBundle')} "
                            f"but VanillaPlusAdditions.java says {is_registered}")
            sheet["registeredInBundle"] = is_registered

        has_jar = module_id in standalone
        if sheet.get("hasStandaloneJar") != has_jar:
            problems.append(f"{module_id}: hasStandaloneJar={sheet.get('hasStandaloneJar')} "
                            f"but build.gradle says {has_jar}")
            sheet["hasStandaloneJar"] = has_jar
        expected_deps = sorted(d[len('vpa_'):] for d in standalone.get(module_id, []))
        if sorted(sheet["moduleDeps"]) != expected_deps:
            problems.append(f"{module_id}: moduleDeps {sorted(sheet['moduleDeps'])} "
                            f"but build.gradle says {expected_deps}")
            sheet["moduleDeps"] = expected_deps

        if module_id in toml_keys:
            documented = {k["key"] for k in sheet["configKeys"]}
            actual = toml_keys[module_id]
            if documented - actual:
                problems.append(f"{module_id}: documented keys not in the config file: "
                                f"{sorted(documented - actual)}")
            if actual - documented:
                problems.append(f"{module_id}: config keys missing from the fact sheet: "
                                f"{sorted(actual - documented)}")
        leaked = sorted(UNIVERSAL_KEYS & {k["key"] for k in sheet["configKeys"]})
        if leaked:
            problems.append(f"{module_id}: universal keys leaked into configKeys: {leaked}")

    if not args.check:
        for module_id, sheet in sheets.items():
            path = DATA / f"{module_id}.json"
            path.write_text(json.dumps(sheet, indent=2, ensure_ascii=False) + "\n")

    print(f"{len(sheets)} fact sheets, {len(source_dirs)} module directories")
    if changes:
        print(f"\n{len(changes)} normalisations:")
        for change in changes:
            print(f"  {change}")
    if warnings:
        print(f"\n{len(warnings)} warnings:")
        for warning in warnings:
            print(f"  ~ {warning}")
    if problems:
        print(f"\n{len(problems)} problems:")
        for problem in problems:
            print(f"  ! {problem}")
    else:
        checked = "build.gradle and the config file" if toml_keys else "build.gradle"
        print(f"\nno problems — fact sheets agree with {checked}")
    return 1 if problems and args.check else 0


if __name__ == "__main__":
    sys.exit(main())
