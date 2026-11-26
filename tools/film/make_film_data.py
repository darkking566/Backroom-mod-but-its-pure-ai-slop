"""Puts the film props into the mod: meshes, textures, icons, block states, item models, drops, names, tool tags, and the
buyer's price for VHS tapes. Everything comes from film_list.py and the Blender output of make_film_props.py.

Run (from the repo root; Blender 5.3, the photos come from ambientCG, CC0, into tools/.cache/):
  py tools/film/prints.py tools/film/prints
  blender -b --python tools/film/make_film_props.py -- <out> <preview>
  blender -b --python tools/film/make_film_icons.py -- <out> <icons> 64
  py tools/film/make_film_data.py <out> <icons>
"""
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from film_list import DROPS, PROPS, TAPES, TOOL, VHS_LABELS  # noqa: E402

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


def material_pool(item, lo, hi, chance):
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{item}",
                                     "functions": [{"function": "minecraft:set_count", "count": count(lo, hi)}]}]}
    if chance < 1.0:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


def tape_pool(lo, hi):
    """Each roll one tape with one of the labels; the same label stacks."""
    entries = [{"type": "minecraft:item", "name": f"{NS}:vhs_tape",
                "functions": [{"function": "minecraft:set_lore", "mode": "append",
                               "lore": [{"translate": f"item.{NS}.vhs_tape.label.{i}", "color": "gray", "italic": False}]}]}
               for i in range(len(VHS_LABELS))]
    return {"rolls": count(lo, hi), "entries": entries}


def update_lang(file, entries):
    path = ASSETS / "lang" / file
    lang = json.loads(path.read_text(encoding="utf-8"))
    lang.update(entries)
    path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def update_tag(tag, ids):
    path = RES / "data/minecraft/tags/block/mineable" / f"{tag}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    values = [v for v in data["values"] if not (isinstance(v, str) and v.startswith(f"{NS}:film_"))]
    values += [f"{NS}:{i}" for i in ids]
    data["values"] = values
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    out, icons = Path(sys.argv[1]), Path(sys.argv[2])
    en, zh = {}, {}
    tools = {"axe": [], "pickaxe": []}
    for pid, size, mount, mat, zh_name, en_name in PROPS:
        (ASSETS / "models/film").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(out / f"{pid}.obj", ASSETS / "models/film" / f"{pid}.obj")
        (ASSETS / "textures/film").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(out / "textures" / f"{pid}.png", ASSETS / "textures/film" / f"{pid}.png")
        (ASSETS / "textures/item/film").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(icons / f"{pid}.png", ASSETS / "textures/item/film" / f"{pid}.png")
        write(ASSETS / "models/item/film" / f"{pid}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/film/{pid}"}})
        write(ASSETS / "items" / f"{pid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/film/{pid}"}})
        # The block itself is invisible (the mesh is drawn by its block entity); the model only gives break particles.
        write(ASSETS / "models/block/film" / f"{pid}.json", {"textures": {"particle": f"{NS}:item/film/{pid}"}})
        write(ASSETS / "blockstates" / f"{pid}.json", {"variants": {"": {"model": f"{NS}:block/film/{pid}"}}})
        pools = [material_pool(*d) for d in DROPS.get(mat, [])]
        if mat in TAPES:
            pools.append(tape_pool(*TAPES[mat]))
        write(DATA / "loot_table/blocks" / f"{pid}.json", {"type": "minecraft:block", "pools": pools})
        en[f"block.{NS}.{pid}"] = en_name
        zh[f"block.{NS}.{pid}"] = zh_name
        if TOOL.get(mat):
            tools[TOOL[mat]].append(pid)
    shutil.copyfile(icons / "film_vhs_tape.png", ASSETS / "textures/item/film/vhs_tape.png")
    write(ASSETS / "models/item/film/vhs_tape.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/film/vhs_tape"}})
    write(ASSETS / "items/vhs_tape.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/film/vhs_tape"}})
    en[f"item.{NS}.vhs_tape"] = "VHS Tape"
    zh[f"item.{NS}.vhs_tape"] = "VHS 錄影帶"
    for i, (zh_label, en_label) in enumerate(VHS_LABELS):
        en[f"item.{NS}.vhs_tape.label.{i}"] = en_label
        zh[f"item.{NS}.vhs_tape.label.{i}"] = zh_label
    update_lang("en_us.json", en)
    update_lang("zh_tw.json", zh)
    for tag, ids in tools.items():
        update_tag(tag, ids)
    # The buyer takes tapes one at a time.
    shop = DATA / "shops/buyer.json"
    buyer = json.loads(shop.read_text(encoding="utf-8"))
    if not any(b["item"] == f"{NS}:vhs_tape" for b in buyer["buys"]):
        text = shop.read_text(encoding="utf-8")
        line = '    {"item": "noclip_backrooms:vhs_tape", "count": 1, "price": 8},\n'
        text = text.replace('  "buys": [\n', '  "buys": [\n' + line, 1)
        shop.write_text(text, encoding="utf-8")
        json.loads(text)
    print("FILM DATA", len(PROPS), "props")


main()
