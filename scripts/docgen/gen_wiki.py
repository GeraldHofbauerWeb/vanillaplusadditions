#!/usr/bin/env python3
"""Turn docs/ and README.md into a GitHub wiki tree.

The repository stays the single source of truth: nobody edits the wiki by hand, a workflow
regenerates and pushes it. This script does the translation, which is mostly about links — a wiki
has a flat namespace, so ``docs/modules/cat_guardian.md`` becomes the page ``Cat-Guardian`` and
every relative link in every file has to be rewritten to match.

Anything that has no wiki equivalent (LICENSE, CONTRIBUTING.md, source files, the test
environments) is rewritten to an absolute link back into the repository, so no link dies in the
crossing.

``docs/internal/`` is deliberately left out: those are German working notes, not user documentation.

Usage:
    python3 scripts/docgen/gen_wiki.py [--out build/wiki] [--check]
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOCS = REPO / "docs"
BLOB = "https://github.com/GeraldHofbauerWeb/vanillaplusadditions/blob/master"

LINK_RE = re.compile(r"(!?)\[([^\]]*)\]\(([^)]+)\)")
HTML_SRC_RE = re.compile(r'(src|href)="([^"]+)"')
SKIP_DIRS = {"internal"}


def page_name(path: Path) -> str:
    """Wiki page name for a markdown file: its H1, dashed. Falls back to the file name."""
    if path.name == "README.md" and path.parent == REPO:
        return "Home"
    for line in path.read_text().splitlines():
        if line.startswith("# "):
            title = line[2:].strip()
            title = re.sub(r"[^\w\s-]", "", title).strip()
            return re.sub(r"\s+", "-", title)
    return path.stem.replace("_", "-").title()


def collect(warn: list[str]) -> dict[Path, str]:
    """{repo-relative markdown path: wiki page name} for everything that crosses over."""
    pages: dict[Path, str] = {REPO / "README.md": "Home"}
    for path in sorted(DOCS.rglob("*.md")):
        if set(path.relative_to(DOCS).parts) & SKIP_DIRS:
            continue
        pages[path] = page_name(path)
    seen: dict[str, Path] = {}
    for path, name in pages.items():
        if name in seen:
            warn.append(f"two files want the wiki page '{name}': "
                        f"{seen[name].relative_to(REPO)} and {path.relative_to(REPO)}")
        seen[name] = path
    return pages


def rewrite_target(target: str, source: Path, pages: dict[Path, str]) -> str:
    """Point one link at its wiki equivalent, or back into the repository if it has none."""
    if target.startswith(("http://", "https://", "mailto:")):
        return target
    if target.startswith("#"):
        return target

    file_part, _, anchor = target.partition("#")
    if not file_part:
        return target
    resolved = (source.parent / file_part).resolve()

    if resolved.suffix.lower() in {".png", ".jpg", ".jpeg", ".gif", ".svg"}:
        try:
            return "img/" + str(resolved.relative_to(DOCS / "img"))
        except ValueError:
            return f"{BLOB}/{resolved.relative_to(REPO)}"

    if resolved in pages:
        return pages[resolved] + (f"#{anchor}" if anchor else "")

    try:
        rel = resolved.relative_to(REPO)
    except ValueError:
        return target
    return f"{BLOB}/{rel}" + (f"#{anchor}" if anchor else "")


def convert(source: Path, pages: dict[Path, str]) -> str:
    text = source.read_text()

    def markdown(match: re.Match) -> str:
        bang, label, target = match.groups()
        return f"{bang}[{label}]({rewrite_target(target.strip(), source, pages)})"

    def html(match: re.Match) -> str:
        attr, target = match.groups()
        return f'{attr}="{rewrite_target(target.strip(), source, pages)}"'

    return HTML_SRC_RE.sub(html, LINK_RE.sub(markdown, text))


def sidebar(pages: dict[Path, str]) -> str:
    import json
    categories = json.loads((REPO / "scripts/docgen/data/categories.json").read_text())
    by_module = {p.stem: name for p, name in pages.items() if p.parent == DOCS / "modules"}

    def link(rel_path: str, label: str) -> str:
        """Link a guide by its file, never by a guessed page name."""
        name = pages.get(REPO / rel_path)
        return f"* [{label}]({name})" if name else f"* {label} <!-- missing: {rel_path} -->"

    lines = ["### [Home](Home)", "",
             "**Getting started**", "",
             "* [Install](Home#-install)",
             link("docs/guides/configuration.md", "Configuration"),
             link("docs/reference/config.md", "Every setting"),
             link("docs/guides/debug-logging.md", "Debug logging"), ""]
    for category in categories["order"]:
        members = sorted(m for m, c in categories["modules"].items() if c == category)
        members = [m for m in members if m in by_module]
        if not members:
            continue
        lines.append(f"**{category}**")
        lines.append("")
        lines += [f"* [{by_module[m].replace('-', ' ')}]({by_module[m]})" for m in members]
        lines.append("")
    lines += ["**For contributors**", "",
              link("docs/guides/module-system.md", "Module system"),
              link("docs/guides/testing.md", "Testing"),
              link("docs/guides/companion-armor.md", "Companion armour"),
              link("docs/guides/instance-switcher.md", "Instance switcher")]
    return "\n".join(lines) + "\n"


def footer() -> str:
    return ("Generated from [the repository]"
            "(https://github.com/GeraldHofbauerWeb/vanillaplusadditions) — "
            "edit the files under `docs/`, not this wiki.\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default=str(REPO / "build" / "wiki"))
    parser.add_argument("--check", action="store_true", help="report only, write nothing")
    args = parser.parse_args()

    warnings: list[str] = []
    pages = collect(warnings)
    out = Path(args.out)

    if not args.check:
        if out.exists():
            shutil.rmtree(out)
        (out / "img").mkdir(parents=True)
        for source, name in pages.items():
            (out / f"{name}.md").write_text(convert(source, pages))
        (out / "_Sidebar.md").write_text(sidebar(pages))
        (out / "_Footer.md").write_text(footer())
        shutil.copytree(DOCS / "img", out / "img", dirs_exist_ok=True)

    images = len(list((DOCS / "img").rglob("*.png")))
    print(f"{len(pages)} wiki pages + sidebar + footer, {images} images -> {out}")
    for warning in warnings:
        print(f"  ! {warning}")
    return 1 if warnings else 0


if __name__ == "__main__":
    sys.exit(main())
