"""Finishing pass over Level 2's assets, run after the other make_level2*.py tools.

1. Textures the later tools drew without labPBR companions get _n/_s maps (height from brightness),
   so shader packs light them like the rest of Level 2 when the realistic pack is off.
2. Plain walls and floors pick a random turn (and walls a mirror image) per block, so a long corridor
   is not the same stain repeated every metre.
3. Painted brick gets three more drawings (other chips, grime and peel), one picked per block.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_textures as mt  # noqa: E402

ASSETS = mt.ROOT / "src/main/resources/assets/noclip_backrooms"
TEXTURES = ASSETS / "textures/block/level2"
MODELS = ASSETS / "models/block/level2"
NS = "noclip_backrooms:block/level2/"

METAL = ("pipe_", "toolbox_metal", "breaker_box", "ceiling_vent", "pressure_gauge", "drain_grate")
# Floors and ceilings: the top and bottom faces turn, which is where they are seen.
TURNED = ("utility_floor", "damp_utility_floor", "debris_utility_floor", "frosted_utility_floor",
          "puddled_utility_floor", "utility_ceiling", "void_concrete")
# Walls: a mirror image is the variation their side faces can show.
MIRRORED = ("utility_wall", "stained_utility_wall", "frosted_utility_wall")
# Painted brick: four separate drawings. Mirror images were tried, but a block beside its own mirror
# image turns the peeling paint into a symmetric butterfly along the seam. (Muddy and black brick are
# not done yet.)
PAINTED = ("painted_brick_wall", "painted_brick_wall_1", "painted_brick_wall_2", "painted_brick_wall_3")


def painted_variants():
    import make_level2 as l2  # noqa: E402  (heavy; only needed here)
    mt.OUT = TEXTURES
    for k, name in enumerate(PAINTED[1:], 1):
        mt.save(name, l2.painted_brick(540 + k))
        write_json(MODELS / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + name}})


def companions():
    made = []
    for path in sorted(TEXTURES.glob("*.png")):
        name = path.stem
        if name.endswith(("_n", "_s")) or (TEXTURES / f"{name}_n.png").exists():
            continue
        color = np.asarray(Image.open(path).convert("RGB"), float)
        full = lambda value: np.full(color.shape[:2], float(value))
        metal = name.startswith(METAL)
        m = mt.Material(color, color.mean(axis=2) / 255 * 6, full(0.45 if metal else 0.15),
                        full(230 if metal else 10), full(0 if metal else 40), full(0))
        mt.OUT = TEXTURES
        mt.save(f"{name}_tmp", m)
        for suffix in ("_n", "_s"):
            (TEXTURES / f"{name}_tmp{suffix}.png").replace(TEXTURES / f"{name}{suffix}.png")
        (TEXTURES / f"{name}_tmp.png").unlink()  # the colour map stays the one the drawing tool made
        made.append(name)
    return made


def mirrored_model(name):
    texture = json.loads((MODELS / f"{name}.json").read_text(encoding="utf8"))["textures"]["all"]
    flip = {"texture": "#all", "uv": [16, 0, 0, 16]}
    faces = {side: dict(flip, cullface=side) for side in ("north", "south", "east", "west", "up", "down")}
    return {"parent": "minecraft:block/block", "textures": {"particle": texture, "all": texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def variety():
    for name in TURNED:
        model = NS + name
        variants = [{"model": model, **({"y": y} if y else {})} for y in (0, 90, 180, 270)]
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": variants}})
    for name in MIRRORED:
        write_json(MODELS / f"{name}_mirrored.json", mirrored_model(name))
        variants = [{"model": NS + name}, {"model": NS + f"{name}_mirrored"}]
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": variants}})
    write_json(ASSETS / "blockstates/painted_brick_wall.json", {"variants": {"": [{"model": NS + name} for name in PAINTED]}})
    # scrap_heap comes from make_level2.py; its outline is square, so any quarter turn fits.
    model = NS + "scrap_heap"
    write_json(ASSETS / "blockstates/scrap_heap.json",
               {"variants": {"": [{"model": model, **({"y": y} if y else {})} for y in (0, 90, 180, 270)]}})


def main():
    painted_variants()
    made = companions()
    variety()
    print(f"companion maps for {len(made)} textures: {' '.join(made)}")
    for name in made:
        assert (TEXTURES / f"{name}_n.png").is_file() and (TEXTURES / f"{name}_s.png").is_file(), name


if __name__ == "__main__":
    main()
