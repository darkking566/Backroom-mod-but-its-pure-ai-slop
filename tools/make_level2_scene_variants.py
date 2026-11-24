import json
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "noclip_backrooms")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "noclip_backrooms")
TEX_DIR = os.path.join(ASSETS, "textures", "block", "level2")
MODEL_DIR = os.path.join(ASSETS, "models", "block", "level2")
STATE_DIR = os.path.join(ASSETS, "blockstates")
ITEM_DIR = os.path.join(ASSETS, "items")
LOOT_DIR = os.path.join(DATA, "loot_table", "blocks")


def clamp(v):
    return max(0, min(255, int(v)))


def value_noise(w, h, scale, seed):
    rng = random.Random(seed)
    gw, gh = w // scale + 2, h // scale + 2
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        gy, fy = divmod(y, scale)
        ty = fy / scale
        for x in range(w):
            gx, fx = divmod(x, scale)
            tx = fx / scale
            v00 = grid[gy][gx]
            v10 = grid[gy][gx + 1]
            v01 = grid[gy + 1][gx]
            v11 = grid[gy + 1][gx + 1]
            a = v00 * (1 - tx) + v10 * tx
            b = v01 * (1 - tx) + v11 * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def make_puddled_floor_tex():
    img = Image.open(os.path.join(TEX_DIR, "damp_utility_floor.png")).convert("RGBA")
    w, h = img.size
    px = img.load()
    n1 = value_noise(w, h, 16, 801)
    n2 = value_noise(w, h, 4, 802)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            pool = n1[y][x] * 0.72 + n2[y][x] * 0.28
            # Wet darkened concrete everywhere
            r, g, b = r * 0.82, g * 0.86, b * 0.90
            if pool > 0.56:
                # Standing dark slate puddle with slight sky/lamp reflection
                depth = min(1.0, (pool - 0.56) / 0.28)
                pr, pg, pb = 38 + n2[y][x] * 18, 48 + n2[y][x] * 22, 54 + n2[y][x] * 26
                if 0.56 < pool < 0.60:
                    pr, pg, pb = pr + 26, pg + 28, pb + 30  # Wet meniscus highlight
                r = r * (1 - depth) + pr * depth
                g = g * (1 - depth) + pg * depth
                b = b * (1 - depth) + pb * depth
            px[x, y] = (clamp(r), clamp(g), clamp(b), a)
    img.save(os.path.join(TEX_DIR, "puddled_utility_floor.png"))


def make_overhead_leak_tex():
    img = Image.open(os.path.join(TEX_DIR, "pipe_grey.png")).convert("RGBA")
    w, h = img.size
    px = img.load()
    n = value_noise(w, h, 8, 803)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            # Water-stained rusted joints around x=30..46 and x=84..100
            joint = min(abs(x - 38), abs(x - 92))
            if joint < 10:
                rust = max(0.0, 1.0 - joint / 10.0) * (0.5 + n[y][x] * 0.5)
                r = r * (1 - rust * 0.45) + 96 * rust * 0.45
                g = g * (1 - rust * 0.55) + 64 * rust * 0.55
                b = b * (1 - rust * 0.65) + 46 * rust * 0.65
            # Dark wet drip streaks on underside
            if y > h // 2 and n[y][x] > 0.55:
                r *= 0.72
                g *= 0.76
                b *= 0.82
            px[x, y] = (clamp(r), clamp(g), clamp(b), a)
    img.save(os.path.join(TEX_DIR, "pipe_overhead_leak.png"))


def all_faces(tex):
    return {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def write_loot_and_item(name, model):
    write_json(
        os.path.join(ITEM_DIR, f"{name}.json"),
        {"model": {"type": "minecraft:model", "model": model}},
    )
    write_json(
        os.path.join(LOOT_DIR, f"{name}.json"),
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1.0,
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                    "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}],
                }
            ],
            "random_sequence": f"noclip_backrooms:blocks/{name}",
        },
    )


def overhead_elements():
    return [
        # Two thick main pipes spanning X, slung close to ceiling
        {"from": [0, 7.5, 2], "to": [16, 13.5, 7], "faces": all_faces("#main")},
        {"from": [0, 8.5, 9.5], "to": [16, 13.5, 14.5], "faces": all_faces("#main")},
        # Smaller center conduit pipe
        {"from": [0, 9.5, 7.2], "to": [16, 12, 9.2], "faces": all_faces("#sub")},
        # Ceiling hanger straps / cross brackets
        {"from": [2, 6.5, 1.5], "to": [3.5, 16, 15], "faces": all_faces("#bracket")},
        {"from": [12.5, 6.5, 1.5], "to": [14, 16, 15], "faces": all_faces("#bracket")},
    ]


def write_overhead_pipe(name, main_tex, sub_tex):
    model = f"noclip_backrooms:block/level2/{name}"
    write_json(
        os.path.join(MODEL_DIR, f"{name}.json"),
        {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": f"noclip_backrooms:block/level2/{main_tex}",
                "main": f"noclip_backrooms:block/level2/{main_tex}",
                "sub": f"noclip_backrooms:block/level2/{sub_tex}",
                "bracket": "noclip_backrooms:block/level2/bracket",
            },
            "elements": overhead_elements(),
        },
    )
    write_json(
        os.path.join(STATE_DIR, f"{name}.json"),
        {"variants": {"axis=x": {"model": model}, "axis=z": {"model": model, "y": 90}}},
    )
    write_loot_and_item(name, model)


def main():
    make_puddled_floor_tex()
    make_overhead_leak_tex()

    puddle_model = "noclip_backrooms:block/level2/puddled_utility_floor"
    write_json(
        os.path.join(MODEL_DIR, "puddled_utility_floor.json"),
        {"parent": "minecraft:block/cube_all", "textures": {"all": puddle_model}},
    )
    write_json(
        os.path.join(STATE_DIR, "puddled_utility_floor.json"),
        {"variants": {"": {"model": puddle_model}}},
    )
    write_loot_and_item("puddled_utility_floor", puddle_model)

    write_overhead_pipe("overhead_pipe_bridge", "pipe_grey", "pipe_steel_hot")
    write_overhead_pipe("leaking_overhead_pipe", "pipe_overhead_leak", "pipe_steel_hot")
    print("Generated Level 2 flooded & overhead pipe scene variant assets.")


if __name__ == "__main__":
    main()
