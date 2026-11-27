"""Puts the Frontrooms' objects into the mod: meshes, textures, icons, block states, item models, drops, names and tool
tags, from institute_list.py (or, with "town", town_list.py) and the Blender output of make_institute_props.py (or
make_town_props.py); the steps are listed there.

Run: py tools/film/make_institute_data.py <out> <icons> [institute|town]
"""
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
SET = sys.argv[3] if len(sys.argv) > 3 else "institute"
if SET == "town":
    from town_list import DROPS, PROPS, TOOL  # noqa: E402
else:
    from institute_list import DROPS, PROPS, TOOL  # noqa: E402
PREFIX = "town_" if SET == "town" else "inst_"

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/noclip_backrooms"
DATA = RES / "data/noclip_backrooms"
NS = "noclip_backrooms"


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def count(lo, hi):
    return lo if lo == hi else {"type": "minecraft:uniform", "min": lo, "max": hi}


def pool(item, lo, hi, chance):
    entry = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{item}",
                                      "functions": [{"function": "minecraft:set_count", "count": count(lo, hi)}]}]}
    if chance < 1.0:
        entry["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return entry


def update_lang(file, entries):
    path = ASSETS / "lang" / file
    lang = json.loads(path.read_text(encoding="utf-8"))
    lang.update(entries)
    path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def update_tag(tag, ids):
    path = RES / "data/minecraft/tags/block/mineable" / f"{tag}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    values = [v for v in data["values"] if not (isinstance(v, str) and v.startswith(f"{NS}:{PREFIX}"))]
    values += [f"{NS}:{i}" for i in ids]
    data["values"] = values
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    out, icons = Path(sys.argv[1]), Path(sys.argv[2])
    en, zh = {}, {}
    tools = {"axe": [], "pickaxe": []}
    for pid, size, mount, mat, zh_name, en_name in PROPS:
        (ASSETS / f"models/{SET}").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(out / f"{pid}.obj", ASSETS / f"models/{SET}" / f"{pid}.obj")
        (ASSETS / f"textures/{SET}").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(out / "textures" / f"{pid}.png", ASSETS / f"textures/{SET}" / f"{pid}.png")
        (ASSETS / f"textures/item/{SET}").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(icons / f"{pid}.png", ASSETS / f"textures/item/{SET}" / f"{pid}.png")
        write(ASSETS / f"models/item/{SET}" / f"{pid}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{SET}/{pid}"}})
        write(ASSETS / "items" / f"{pid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{SET}/{pid}"}})
        # The block itself is invisible (the mesh is drawn by its block entity); the model only gives break particles.
        write(ASSETS / f"models/block/{SET}" / f"{pid}.json", {"textures": {"particle": f"{NS}:item/{SET}/{pid}"}})
        write(ASSETS / "blockstates" / f"{pid}.json", {"variants": {"": {"model": f"{NS}:block/{SET}/{pid}"}}})
        write(DATA / "loot_table/blocks" / f"{pid}.json", {"type": "minecraft:block", "pools": [pool(*d) for d in DROPS.get(mat, [])]})
        en[f"block.{NS}.{pid}"] = en_name
        zh[f"block.{NS}.{pid}"] = zh_name
        if TOOL.get(mat):
            tools[TOOL[mat]].append(pid)
    update_lang("en_us.json", en)
    update_lang("zh_tw.json", zh)
    for tag, ids in tools.items():
        update_tag(tag, ids)
    print(SET.upper(), "DATA", len(PROPS), "objects")


main()
