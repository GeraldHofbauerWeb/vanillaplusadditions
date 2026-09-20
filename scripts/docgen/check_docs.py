#!/usr/bin/env python3
"""Verify that the documentation still matches the code.

Documentation drifts the moment nobody is checking, so this runs in CI. It answers five questions:

1. Does every module have a page, and does every page have a module?
2. Does every page have the parts the template promises — a title, a TL;DR, both generated blocks?
3. Does every relative link and every image actually resolve?
4. Are the generated blocks up to date with the fact sheets?  (delegated to gen_meta.py --check)
5. Do the fact sheets still agree with build.gradle and a real config file?
   (delegated to normalize_facts.py --check)

Exits non-zero on any failure. Warnings alone do not fail the build.

Usage:
    python3 scripts/docgen/check_docs.py [--no-delegate]
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOCS = REPO / "docs"
PAGES = DOCS / "modules"
MODULES_SRC = REPO / "src/main/java/net/geraldhofbauer/vanillaplusadditions/modules"
DOCGEN = REPO / "scripts" / "docgen"

LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
EXTERNAL = ("http://", "https://", "mailto:", "#")
REQUIRED_MARKERS = ["<!-- vpa:meta:start -->", "<!-- vpa:meta:end -->",
                    "<!-- vpa:config:start -->", "<!-- vpa:config:end -->"]


def markdown_files() -> list[Path]:
    files = sorted(DOCS.rglob("*.md"))
    readme = REPO / "README.md"
    if readme.exists():
        files.append(readme)
    return files


def check_coverage(errors: list[str]) -> None:
    modules = {p.name for p in MODULES_SRC.iterdir() if p.is_dir()}
    pages = {p.stem for p in PAGES.glob("*.md")}
    for module in sorted(modules - pages):
        errors.append(f"module '{module}' has no page at docs/modules/{module}.md")
    for page in sorted(pages - modules):
        errors.append(f"docs/modules/{page}.md documents a module that does not exist")


def check_structure(errors: list[str], warnings: list[str]) -> None:
    for page in sorted(PAGES.glob("*.md")):
        text = page.read_text()
        rel = page.relative_to(REPO)
        if not text.lstrip().startswith("# "):
            errors.append(f"{rel}: does not start with a level-1 heading")
        if "**TL;DR**" not in text:
            errors.append(f"{rel}: no TL;DR line")
        for marker in REQUIRED_MARKERS:
            if marker not in text:
                errors.append(f"{rel}: missing {marker}")
        if "<!-- TODO" in text:
            warnings.append(f"{rel}: still contains a TODO marker")


def check_links(errors: list[str], warnings: list[str]) -> None:
    for path in markdown_files():
        rel = path.relative_to(REPO)
        for target in LINK_RE.findall(path.read_text()):
            target = target.split(" ")[0].strip()
            if not target or target.startswith(EXTERNAL):
                continue
            file_part = target.split("#")[0]
            if not file_part:
                continue
            resolved = (path.parent / file_part).resolve()
            if not resolved.exists():
                errors.append(f"{rel}: broken link -> {target}")
            elif resolved.is_relative_to(REPO) and resolved.suffix == ".png":
                if resolved.stat().st_size == 0:
                    warnings.append(f"{rel}: image is empty -> {target}")


def delegate(script: str, errors: list[str]) -> None:
    result = subprocess.run([sys.executable, str(DOCGEN / script), "--check"],
                            capture_output=True, text=True)
    if result.returncode != 0:
        detail = (result.stdout + result.stderr).strip().splitlines()
        errors.append(f"{script} --check failed:")
        errors.extend(f"    {line}" for line in detail[-25:])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-delegate", action="store_true",
                        help="skip gen_meta.py and normalize_facts.py")
    args = parser.parse_args()

    errors: list[str] = []
    warnings: list[str] = []

    check_coverage(errors)
    check_structure(errors, warnings)
    check_links(errors, warnings)
    if not args.no_delegate:
        delegate("normalize_facts.py", errors)
        delegate("gen_meta.py", errors)
        # Skips itself when no vanilla client jar is around, which is the normal CI case.
        delegate("render_models.py", errors)

    pages = len(list(PAGES.glob("*.md")))
    files = len(markdown_files())
    print(f"checked {pages} module pages and {files} markdown files")
    if warnings:
        print(f"\n{len(warnings)} warnings:")
        for warning in warnings:
            print(f"  ~ {warning}")
    if errors:
        print(f"\n{len(errors)} errors:")
        for error in errors:
            print(f"  ! {error}")
        return 1
    print("\ndocumentation is consistent")
    return 0


if __name__ == "__main__":
    sys.exit(main())
