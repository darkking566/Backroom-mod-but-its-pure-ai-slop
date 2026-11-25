"""Finishing pass over Level 3's base assets (without overwriting unrelated files):

1. Adds the fourth lamp state `dim_station_light` (warm, aging tube with darkened ends) to match STYLE_GUIDE.md.
2. Adds 4-variant brick/tile walls (no mirroring so mortar stays aligned), 3-variant x 4-turn dusty_floor, and
   3-variant x 0/180-turn station_ceiling so long passages do not repeat a single block texture.

Run: python tools/make_level3_polish.py
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_level3 as l3  # noqa: E402
import make_textures as mt  # noqa: E402

ASSETS = l3.ASSETS
TEXTURES = l3.TEXTURES
MODELS = l3.MODELS
NS = l3.NS


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def dim_light():
    mt.OUT = TEXTURES
    m = l3.lens((220, 178, 96), 0.45)
    # Darkened aging cathode ends on left/right of the tube
    ends = np.maximum(1 - mt.step(8, 28, mt.X), 1 - mt.step(8, 28, mt.SIZE - mt.X))
    mt.tint(m, ends * 0.65, (0.38, 0.34, 0.28))
    m.glow = np.maximum(0.0, m.glow * (1 - ends * 0.65))
    mt.save("light_lens_dim", m)

    model = l3.fixture("light_lens_dim")
    model["elements"][-1]["light_emission"] = 9
    write_json(MODELS / "dim_station_light.json", model)
    write_json(ASSETS / "blockstates/dim_station_light.json", l3.axis_state("dim_station_light"))
    write_json(ASSETS / "items/dim_station_light.json", {"model": {"type": "minecraft:model", "model": NS + "dim_station_light"}})
    write_json(mt.ROOT / "src/main/resources/data/noclip_backrooms/loot_table/blocks/dim_station_light.json", l3.loot_table("dim_station_light"))


def variants():
    mt.OUT = TEXTURES
    wall_specs = [
        ("red_brick_wall", "red_brick", 1000, 4, lambda s: l3.brick(s, (1.25, 0.66, 0.4), 1.55)),
        ("brown_brick_wall", "brown_brick", 1010, 4, lambda s: l3.brick(s, (1.05, 0.74, 0.5), 1.15)),
        ("cinder_block_wall", "cinder_block", 1040, 4, l3.cinder),
        ("glazed_tile_wall", "glazed_tile", 1020, 4, l3.glazed),
    ]
    for block, tex, base_seed, count, fn in wall_specs:
        models = [NS + block]
        for k in range(1, count):
            tname = f"{tex}_{k}"
            mname = f"{block}_{k}"
            mt.save(tname, fn(base_seed + k))
            write_json(MODELS / f"{mname}.json", l3.cube(tname))
            models.append(NS + mname)
        write_json(ASSETS / f"blockstates/{block}.json", {"variants": {"": [{"model": m} for m in models]}})

    floor_models = [NS + "dusty_floor"]
    for k in range(1, 3):
        name = f"dusty_floor_{k}"
        mt.save(name, l3.dusty_floor(1070 + k))
        write_json(MODELS / f"{name}.json", l3.cube(name))
        floor_models.append(NS + name)
    write_json(ASSETS / "blockstates/dusty_floor.json", {
        "variants": {"": [{"model": m, **({"y": y} if y else {})} for m in floor_models for y in (0, 90, 180, 270)]}
    })

    ceil_models = [NS + "station_ceiling"]
    for k in range(1, 3):
        name = f"station_ceiling_{k}"
        mt.save(name, l3.ceiling(1080 + k))
        write_json(MODELS / f"{name}.json", l3.cube(name))
        ceil_models.append(NS + name)
    write_json(ASSETS / "blockstates/station_ceiling.json", {
        "variants": {"": [{"model": m, **({"y": y} if y else {})} for m in ceil_models for y in (0, 180)]}
    })


def verify():
    prefix = "noclip_backrooms:block/"
    for name in ("dim_station_light", "red_brick_wall", "brown_brick_wall", "cinder_block_wall", "glazed_tile_wall", "dusty_floor", "station_ceiling"):
        bs = json.loads((ASSETS / f"blockstates/{name}.json").read_text(encoding="utf8"))
        for v in bs["variants"].values() if not isinstance(bs["variants"].get(""), list) else bs["variants"][""]:
            model_rel = v["model"][len(prefix):]
            mpath = ASSETS / f"models/block/{model_rel}.json"
            assert mpath.is_file(), mpath
            mdata = json.loads(mpath.read_text(encoding="utf8"))
            for tex in mdata.get("textures", {}).values():
                if tex.startswith(NS):
                    tname = tex[len(NS):]
                    for suf in ("", "_n", "_s"):
                        assert (TEXTURES / f"{tname}{suf}.png").is_file(), (name, tname, suf)


def main():
    dim_light()
    variants()
    verify()
    print("Level 3 polish assets written and verified.")


if __name__ == "__main__":
    main()
