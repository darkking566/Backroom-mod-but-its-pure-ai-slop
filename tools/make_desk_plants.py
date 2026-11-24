"""Builds the two potted desk plants of Level 1 (a dead twig plant and a leafy one): textures, models, blockstates, items, loot, lang.

The box/plane lists are the single source: tools/blender_desk_plants.py reads art/props/desk_plants_spec.json to build the same pots in Blender.
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/noclip_backrooms"
TEX = ASSETS / "textures/block/level1"
NS = "noclip_backrooms:block/level1/"

# A tapered terracotta pot, 4..12 wide at the rim and 2.5 high on a desk: (name, from, to, texture).
POT = [
    ("pot_base", (5, 0, 5), (11, 1.5, 11), "desk_pot"),
    ("pot_body", (4.5, 1.5, 4.5), (11.5, 5, 11.5), "desk_pot"),
    ("pot_rim", (4, 5, 4), (12, 6, 12), "desk_pot"),
    ("soil", (4.6, 5.9, 4.6), (11.4, 6, 11.4), "desk_soil"),
]
# Crossed planes standing in the pot: (name, from, to, rotation about Y at the block centre, texture).
PLANTS = {
    "desk_plant_dead": ("dead_twigs", 6.0, 15.0),
    "desk_plant_leafy": ("leafy_plant", 6.0, 16.0),
}


def save(name, img):
    img.save(TEX / f"{name}.png")


def noisy(size, base, seed, amount=0.07):
    rng = np.random.default_rng(seed)
    a = np.empty((size, size, 3))
    a[:] = base
    a *= 1 + rng.normal(0, amount, (size, size, 1))
    return a


def textures():
    pot = noisy(32, (150, 84, 58), 1)
    pot[:3] = (118, 64, 44)  # darker band along the top, where soil stains it
    pot[::8] *= 0.9
    save("desk_pot", Image.fromarray(np.clip(pot, 0, 255).astype(np.uint8)))
    save("desk_soil", Image.fromarray(np.clip(noisy(16, (52, 38, 28), 2, 0.15), 0, 255).astype(np.uint8)))

    # Dead plant: bare brown twigs forking upward, a few shrivelled leaves hanging on.
    rng = np.random.default_rng(3)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    def twig(x, y, ang, length, depth):
        import math
        x2, y2 = x + math.sin(ang) * length, y - math.cos(ang) * length
        d.line((x, y, x2, y2), fill=(96, 72, 48, 255), width=1)
        if depth:
            twig(x2, y2, ang - 0.5 + rng.normal(0, 0.15), length * 0.7, depth - 1)
            twig(x2, y2, ang + 0.5 + rng.normal(0, 0.15), length * 0.7, depth - 1)
        elif rng.random() < 0.5:
            d.rectangle((x2 - 1, y2, x2, y2 + 1), fill=(130, 104, 56, 255))
    twig(16, 31, 0, 11, 3)
    twig(16, 31, -0.35, 8, 2)
    twig(16, 31, 0.4, 8, 2)
    save("dead_twigs", img)

    # Leafy plant: a rosette of green blades, lighter at the tips.
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(11):
        ang = (i - 5) * 0.28
        import math
        length = 22 + rng.integers(-3, 4)
        x2, y2 = 16 + math.sin(ang) * length * 0.8, 31 - math.cos(ang) * length
        d.line((16, 31, x2, y2), fill=(48, 104, 44, 255), width=2)
        d.line((16 + math.sin(ang) * 4, 31 - math.cos(ang) * 4, x2, y2), fill=(84, 150, 62, 255), width=1)
    save("leafy_plant", img)


def model(plant, y0, y1):
    elements = []
    for name, a, b, tex in POT:
        faces = {s: {"texture": "#" + tex} for s in ("north", "south", "east", "west", "up", "down")}
        elements.append({"name": name, "from": list(a), "to": list(b), "faces": faces})
    for angle in (45, -45):
        elements.append({"name": f"plant_{angle}", "from": [0.8, y0, 8], "to": [15.2, y1, 8],
                         "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": True},
                         "shade": False,
                         "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#plant"}, "south": {"uv": [0, 0, 16, 16], "texture": "#plant"}}})
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "desk_pot", "desk_pot": NS + "desk_pot", "desk_soil": NS + "desk_soil", "plant": NS + PLANTS_TEX[plant]},
            "elements": elements}


PLANTS_TEX = {k: v[0] for k, v in PLANTS.items()}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")


def main():
    textures()
    names = {"desk_plant_dead": ("枯萎的桌上盆栽", "Withered Desk Plant"), "desk_plant_leafy": ("桌上盆栽", "Desk Plant")}
    for block, (tex, y0, y1) in PLANTS.items():
        write(ASSETS / f"models/block/level1/{block}.json", model(block, y0, y1))
        write(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": NS + block}}})
        write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": NS + block}})
        write(RES / f"data/noclip_backrooms/loot_table/blocks/{block}.json", {
            "type": "minecraft:block",
            "pools": [{"conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{block}"}], "rolls": 1.0}],
            "random_sequence": f"noclip_backrooms:blocks/{block}"})
    for i, lang in enumerate(("zh_tw", "en_us")):
        path = ASSETS / f"lang/{lang}.json"
        data = json.loads(path.read_text(encoding="utf8"))
        for block, text in names.items():
            data[f"block.noclip_backrooms.{block}"] = text[i]
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
    spec = {"pot": [{"name": n, "from": a, "to": b, "texture": t} for n, a, b, t in POT],
            "plants": {k: {"texture": v[0], "y0": v[1], "y1": v[2]} for k, v in PLANTS.items()}}
    write(ROOT / "art/props/desk_plants_spec.json", spec)
    print("desk plants written")


if __name__ == "__main__":
    main()
