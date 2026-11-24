"""Builds the Level 1 office wastebasket: block model, textures, blockstate, item, loot table and lang entries.

The boxes below are the single source: this script writes them as the block model, and tools/blender_wastebasket.py
reads art/props/wastebasket_spec.json to build the same bin in Blender (preview render and .blend).
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/noclip_backrooms"
NS = "noclip_backrooms:block/level1/"

# (name, from, to, texture) in block pixels (16 = one block). A perforated steel bin that flares a little toward the top.
BOXES = [
    ("base", (5, 0, 5), (11, 0.5, 11), "wastebasket"),
]
# Four tiers, each a little wider than the one below, so the sides flare smoothly.
for tier in range(4):
    lo, hi, y0, y1 = 5 - tier * 0.25, 11 + tier * 0.25, 0.5 + tier * 2.6, 0.5 + (tier + 1) * 2.6
    BOXES += [(f"wall_s{tier}", (lo, y0, lo), (hi, y1, lo + 0.5), "wastebasket"),
              (f"wall_n{tier}", (lo, y0, hi - 0.5), (hi, y1, hi), "wastebasket"),
              (f"wall_w{tier}", (lo, y0, lo + 0.5), (lo + 0.5, y1, hi - 0.5), "wastebasket"),
              (f"wall_e{tier}", (hi - 0.5, y0, lo + 0.5), (hi, y1, hi - 0.5), "wastebasket")]
BOXES += [
    ("rim_s", (4, 10.9, 4), (12, 11.5, 4.6), "wastebasket_rim"),
    ("rim_n", (4, 10.9, 11.4), (12, 11.5, 12), "wastebasket_rim"),
    ("rim_w", (4, 10.9, 4.6), (4.6, 11.5, 11.4), "wastebasket_rim"),
    ("rim_e", (11.4, 10.9, 4.6), (12, 11.5, 11.4), "wastebasket_rim"),
    # Crumpled paper heaped inside, one scrap poking over the rim.
    ("paper_a", (6, 0.5, 6), (9.5, 6.5, 9.5), "wastebasket_paper"),
    ("paper_b", (8, 0.5, 7), (10.5, 8.5, 10), "wastebasket_paper"),
    ("paper_c", (6.5, 0.5, 8.5), (8.5, 9, 10.5), "wastebasket_paper"),
]


def save(name, rgb):
    Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").save(ASSETS / f"textures/block/level1/{name}.png")


def steel(size, base, seed):
    rng = np.random.default_rng(seed)
    img = np.empty((size, size, 3))
    img[:] = base
    img *= (1 + rng.normal(0, 0.04, (size, size, 1)))
    # Grime gathers toward the bottom.
    img *= np.linspace(0.8, 1.0, size)[:, None, None][::-1]
    return img


def textures():
    size = 64
    mesh = steel(size, (88, 92, 98), 1)
    for y in range(4, size, 8):
        for x in range(4, size, 8):
            mesh[y - 1:y + 2, x - 1:x + 2] = (26, 28, 32)  # punched holes
    mesh[:3] = (60, 62, 68)
    mesh[-3:] = (60, 62, 68)
    save("wastebasket", mesh)
    rim = steel(size, (150, 154, 160), 2)
    rim[::16] *= 0.85
    save("wastebasket_rim", rim)
    rng = np.random.default_rng(3)
    paper = steel(size, (226, 222, 204), 4)
    for _ in range(14):  # creases and a few printed lines
        y, x = rng.integers(0, size), rng.integers(0, size)
        paper[y:y + 1, x:x + int(rng.integers(8, 28))] *= 0.62
    for _ in range(6):
        y, x = rng.integers(0, size - 8), rng.integers(0, size - 8)
        paper[y:y + 6, x:x + 6] = (214, 196, 150)
    save("wastebasket_paper", paper)


def model():
    elements = []
    for name, a, b, tex in BOXES:
        faces = {side: {"texture": "#" + tex} for side in ("north", "south", "east", "west", "up", "down")}
        elements.append({"name": name, "from": list(a), "to": list(b), "faces": faces})
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "wastebasket", **{t: NS + t for t in ("wastebasket", "wastebasket_rim", "wastebasket_paper")}},
            "elements": elements}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")


def main():
    textures()
    write(ASSETS / "models/block/level1/wastebasket.json", model())
    write(ASSETS / "blockstates/wastebasket.json", {"variants": {"": {"model": NS + "wastebasket"}}})
    write(ASSETS / "items/wastebasket.json", {"model": {"type": "minecraft:model", "model": NS + "wastebasket"}})
    write(RES / "data/noclip_backrooms/loot_table/blocks/wastebasket.json", {
        "type": "minecraft:block",
        "pools": [{"conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "noclip_backrooms:wastebasket"}], "rolls": 1.0}],
        "random_sequence": "noclip_backrooms:blocks/wastebasket"})
    for lang, text in (("zh_tw", "辦公室垃圾桶"), ("en_us", "Office Wastebasket")):
        path = ASSETS / f"lang/{lang}.json"
        data = json.loads(path.read_text(encoding="utf8"))
        data["block.noclip_backrooms.wastebasket"] = text
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
    spec = ROOT / "art/props/wastebasket_spec.json"
    write(spec, [{"name": n, "from": a, "to": b, "texture": t} for n, a, b, t in BOXES])
    print("wastebasket written")


if __name__ == "__main__":
    main()
