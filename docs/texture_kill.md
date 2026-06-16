# Texture Kill

## Overview

Hides cosmetic textures added by other mods by replacing them with fully transparent pixels —
either the whole texture or just a rectangular region of it. Originally built to hide Create's
contraption "hats" (the cosmetic head overlay shown on entities riding a contraption), but
works for any PNG resource.

Implemented as a virtual, top-priority client resource pack — no actual resource files are
modified, and original textures remain intact for other resource packs to use.

---

## Configuration

### `killed_textures`

Whole textures to replace entirely with a 1×1 transparent PNG.

Format: `namespace:textures/category/name.png`

Defaults:
```
create:textures/entity/train_hat.png
create:textures/entity/logistics_hat.png
```

### `erase_regions`

Erase a rectangular region within a texture, leaving the rest untouched. Useful when only
part of a texture (e.g. a hat on a skin sheet) needs hiding.

Format: `namespace:textures/path.png@x1:y1-x2:y2`

- Coordinates are pixel-based, end-exclusive: `@32:0-64:16` erases x∈[32,64), y∈[0,16).
- Multiple regions on the same texture are supported — just repeat the entry with different
  `@` ranges.

The default list pre-configures ~30 regions, mostly targeting cosmetic overlays on
**Alex's Mobs Zombies Revamped** and JEM-based entity model textures.

---

## Behaviour notes

- Loads/erases on every resource reload; the eraser cache is cleared first so original
  textures are always re-read from lower-priority packs.
- Registers both via `ResourceManager` and `TextureManager` for compatibility with mods like
  ETF (Entity Texture Features) that bypass the standard resource pipeline.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
