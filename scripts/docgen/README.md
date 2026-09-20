# docgen — the documentation toolchain

Five scripts. Together they make sure the documentation cannot quietly drift away from the code:
the facts come from the source, the images come from the models, and CI fails when either stops
matching.

| Script | What it does |
|---|---|
| `normalize_facts.py` | Tidies the per-module fact sheets and cross-checks them against `build.gradle` and a real generated config file |
| `gen_meta.py` | Writes the fact box and the settings table into every module page; writes `docs/reference/config.md` |
| `gen_readme.py` | Writes the module table and the tested-combination block into `README.md` |
| `render_models.py` | Renders the item and block icons into `docs/img/` |
| `gen_wiki.py` | Turns `docs/` + `README.md` into a GitHub wiki tree, rewriting every link |
| `check_docs.py` | Runs the checks of all of the above, plus completeness and dead links |

Run `check_docs.py` before pushing documentation; CI runs the same thing.

## The data

`data/` holds what the generators read. It is committed, so the documentation can be rebuilt
without re-deriving anything:

| File | Contents |
|---|---|
| `data/modules/<id>.json` | One fact sheet per module: display name, side, dependencies with evidence, registered content, config keys with defaults, the version it first shipped in |
| `data/categories.json` | Which section of the README table each module belongs to. A module missing here is an error |
| `data/tested-versions.json` | The one combination this is actually played on, read from the live server |
| `data/mod-versions.json` | Per third-party mod the version floor we can prove, and where that proof comes from |

A fact sheet is not hand-maintained prose — it is checked. `normalize_facts.py --check` fails if a
sheet disagrees with `build.gradle` about standalone jars or module dependencies, if it disagrees
with `VanillaPlusAdditions.java` about being registered, or if its config keys differ from the ones
a real config file contains.

## Regenerating the images

```bash
export VPA_CLIENT_JAR=/path/to/client-1.21.1.jar
python3 scripts/docgen/render_models.py            # writes docs/img/
python3 scripts/docgen/render_models.py --check    # are the committed PNGs current?
python3 scripts/docgen/render_models.py --list     # what would be rendered
python3 scripts/docgen/render_models.py --only cat_bowl,mob_loader
```

Useful flags: `--size` (edge length, default 256), `--ssaa` (supersampling, default 4),
`--no-variants` (skip the skins), `--selftest` (check the projection maths against known values).

### The client jar

Five vanilla textures (`block/lodestone_side`, `block/lodestone_top`, `block/sea_lantern`,
`item/cod`, `item/tropical_fish`) and a handful of vanilla parent models are only in the official
client jar, which is Mojang's and is not redistributed here. Point `VPA_CLIENT_JAR` at a copy of the
1.21.1 client, or pass `--client-jar`.

**Neither the Gradle build nor CI needs it.** The PNGs are committed. Without a jar, `--check`
reports that it skipped and exits successfully; only regenerating fails.

## What is deliberately not rendered

Eighteen entries are skipped, each with a reason the script prints:

* **`end_conduit`** — its model is `builtin/entity`. Vanilla draws conduits with a
  `BlockEntityRenderer` from geometry hard-coded in Java; neither our model nor vanilla's own
  `block/conduit.json` contains a single element. There is nothing to derive, so nothing is drawn.
  A screenshot is the only honest way to illustrate it.
* **`chunk_loader_track` segments** — partly `neoforge:obj` models whose geometry lives in an `.obj`
  file shipped by another mod, partly references into the `create` namespace. Neither is in this
  repository.

The script never invents geometry to fill a gap.

## Why the output can be trusted

The renderer was built three times independently and judged on its actual pixels. The winning
implementation reproduces a reference derived from the decompiled vanilla sources with **no pixel
differing by more than 16** across all seven 3D models, and its alpha histogram on `mob_loader.png`
is byte-identical to that reference. The face-corner table it projects with is checked against
vanilla's `FaceInfo` enum at import time by `assert_uv_tables_agree()`, so a typo there fails loudly
instead of silently mirroring a face — which is exactly the bug one of the losing implementations
shipped.

`render-manifest.json` records the hash of every source file that went into each PNG, which is what
`--check` compares against.

## One known difference from the real inventory

Minecraft lights an inventory block with two directional lights; this uses the fixed face
multipliers (`up` 1.0, `down` 0.5, north/south 0.8, east/west 0.6). The result is very close but not
identical to a screenshot taken in-game. Every icon shares one origin and one scale, so a bowl is
genuinely smaller than a full block and sits low in the frame — that is correct, and it makes a row
of icons line up on a common baseline.
