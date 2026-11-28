"""Brings the realistic resource pack to the design spec's texture sizes (docs/企劃書, 7.1; docs/畫面規範/風格規範.md):
walls, floors, ceilings and anything else drawn on a whole block face at 128x128, props (furniture, lamps, pipes, small
things) at 64x64. The _n and _s maps follow their texture. Already small enough is left alone, so it can be run again
after any generator (make_level0_realistic.py and the others still draw at 512 and save through here).

Which is which comes from the block models: a texture is a surface when some model using it has a face covering a
whole block face (16 by 16); otherwise it is a prop.

Run from the repository root:  py -I tools/resize_realistic.py [--dry-run]
"""
import collections
import glob
import json
import os
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
PACK = RES / "resourcepacks/realistic/assets/noclip_backrooms"
SURFACE, PROP = 128, 64
FULL_PARENTS = {"block/cube_all", "block/cube", "block/cube_column", "block/cube_bottom_top", "block/cube_column_horizontal",
                "block/orientable", "block/cube_mirrored_all", "block/cube_mirrored", "block/leaves"}


def rel(path, base):
    return os.path.relpath(path, base).replace(os.sep, "/")


def load_models():
    models = {}
    for root in (RES / "assets/noclip_backrooms/models/block", PACK / "models/block"):
        for f in glob.glob(str(root / "**/*.json"), recursive=True):
            try:
                # The pack's own models replace the mod's of the same name, so they are read second.
                models["block/" + rel(f, root)[:-5]] = json.loads(Path(f).read_text(encoding="utf8"))
            except (OSError, ValueError) as e:
                print("skipped model", f, e)
    return models


def whole_face(model, models, depth=0):
    """Whether this model (or the parent it takes its shape from) has a face as big as a block's."""
    if "elements" in model:
        for element in model["elements"]:
            size = [abs(b - a) for a, b in zip(element["from"], element["to"])]
            if sum(1 for s in size if s >= 16) >= 2:
                return True
        return False
    parent = model.get("parent", "").split(":")[-1]
    if parent in FULL_PARENTS or parent.startswith("block/door_"):
        return True
    if model.get("parent", "").startswith("noclip_backrooms:") and parent in models and depth < 6:
        return whole_face(models[parent], models, depth + 1)
    return False


def kinds():
    models = load_models()
    seen = collections.defaultdict(set)
    for model in models.values():
        for value in (model.get("textures") or {}).values():
            if isinstance(value, str) and value.startswith("noclip_backrooms:"):
                seen[value.split(":", 1)[1]].add(whole_face(model, models))
    return seen


def resize(path, size, dry):
    image = Image.open(path)
    w, h = image.size
    if w <= size:
        return False
    factor = size / w
    target = (size, max(1, round(h * factor)))  # animation strips keep their frames
    if dry:
        return True
    maps = re.search(r"_(n|s)\.png$", path.name) is not None
    has_alpha = image.mode in ("RGBA", "LA") and image.getextrema()[-1][0] < 255
    # Normal and specular maps average (no ringing); plain opaque colour keeps its edges.
    method = Image.Resampling.BOX if maps or has_alpha else Image.Resampling.LANCZOS
    image.resize(target, method).save(path)
    return True


def main():
    dry = "--dry-run" in sys.argv
    seen = kinds()
    counts = collections.Counter()
    for f in sorted(glob.glob(str(PACK / "textures/block/**/*.png"), recursive=True)):
        path = Path(f)
        name = re.sub(r"_(n|s)$", "", rel(f, PACK / "textures")[:-4])
        surface = True in seen.get(name, {True})
        size = SURFACE if surface else PROP
        if resize(path, size, dry):
            counts["surface" if surface else "prop"] += 1
    print(("would resize" if dry else "resized"), dict(counts))


if __name__ == "__main__":
    main()
