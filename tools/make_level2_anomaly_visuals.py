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


def make_frosted_texture(src_name, dst_name, seed, edge_bias=True):
    img = Image.open(os.path.join(TEX_DIR, src_name)).convert("RGBA")
    w, h = img.size
    px = img.load()
    n1 = value_noise(w, h, 16, seed)
    n2 = value_noise(w, h, 4, seed + 1)
    for y in range(h):
        edge = max(0.0, 1.0 - min(y, h - 1 - y) / 26.0) if edge_bias else 0.0
        for x in range(w):
            r, g, b, a = px[x, y]
            # Cold desaturation + pale frost patches
            frost = n1[y][x] * 0.65 + n2[y][x] * 0.35 + edge * 0.25
            cold_r = r * 0.86 + 18
            cold_g = g * 0.94 + 26
            cold_b = b * 1.04 + 40
            if frost > 0.68:
                t = min(1.0, (frost - 0.68) / 0.32)
                cold_r = cold_r * (1 - t) + 206 * t
                cold_g = cold_g * (1 - t) + 228 * t
                cold_b = cold_b * (1 - t) + 244 * t
            px[x, y] = (clamp(cold_r), clamp(cold_g), clamp(cold_b), a)
    img.save(os.path.join(TEX_DIR, dst_name))


def make_burst_gas_pipe_tex():
    img = Image.open(os.path.join(TEX_DIR, "pipe_grey.png")).convert("RGBA")
    w, h = img.size
    px = img.load()
    n = value_noise(w, h, 8, 707)
    rng = random.Random(708)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            # Slightly corroded greenish-brown metal tint
            stain = n[y][x]
            nr = r * 0.85 + stain * 22
            ng = g * 0.88 + stain * 28
            nb = b * 0.72 + stain * 10
            # Ruptured seam/crack running near the middle
            crack_y = h // 2 + int((n[y][x] - 0.5) * 18)
            if 34 <= x <= 96 and abs(y - crack_y) <= 3:
                if abs(y - crack_y) <= 1:
                    nr, ng, nb = 22, 28, 20  # Deep dark rupture slit
                else:
                    nr, ng, nb = 84, 108, 48  # Toxic residue rim
            elif 26 <= x <= 104 and abs(y - crack_y) <= 14 and rng.random() < 0.35:
                nr = nr * 0.65 + 78 * 0.35
                ng = ng * 0.65 + 102 * 0.35
                nb = nb * 0.65 + 42 * 0.35
            px[x, y] = (clamp(nr), clamp(ng), clamp(nb), a)
    img.save(os.path.join(TEX_DIR, "pipe_burst_gas.png"))


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def write_loot_and_item(name, model_path):
    write_json(
        os.path.join(ITEM_DIR, f"{name}.json"),
        {"model": {"type": "minecraft:model", "model": model_path}},
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


def write_cube(name):
    tex = f"noclip_backrooms:block/level2/{name}"
    model = f"noclip_backrooms:block/level2/{name}"
    write_json(
        os.path.join(MODEL_DIR, f"{name}.json"),
        {"parent": "minecraft:block/cube_all", "textures": {"all": tex}},
    )
    write_json(
        os.path.join(STATE_DIR, f"{name}.json"),
        {"variants": {"": {"model": model}}},
    )
    write_loot_and_item(name, model)


def write_pipe_variant(name, low_tex, high_tex):
    with open(os.path.join(MODEL_DIR, "steam_pipes.json"), "r", encoding="utf-8") as f:
        base = json.load(f)
    base["textures"] = {
        "particle": f"noclip_backrooms:block/level2/{low_tex}",
        "low": f"noclip_backrooms:block/level2/{low_tex}",
        "high": f"noclip_backrooms:block/level2/{high_tex}",
        "bracket": "noclip_backrooms:block/level2/bracket",
    }
    model = f"noclip_backrooms:block/level2/{name}"
    write_json(os.path.join(MODEL_DIR, f"{name}.json"), base)
    write_json(
        os.path.join(STATE_DIR, f"{name}.json"),
        {
            "variants": {
                "facing=south": {"model": model},
                "facing=west": {"model": model, "y": 90},
                "facing=north": {"model": model, "y": 180},
                "facing=east": {"model": model, "y": 270},
            }
        },
    )
    write_loot_and_item(name, model)


def main():
    make_frosted_texture("utility_wall.png", "frosted_utility_wall.png", 501, edge_bias=True)
    make_frosted_texture("utility_floor.png", "frosted_utility_floor.png", 502, edge_bias=False)
    make_frosted_texture("pipe_grey.png", "pipe_frosted.png", 503, edge_bias=False)
    make_burst_gas_pipe_tex()

    write_cube("frosted_utility_wall")
    write_cube("frosted_utility_floor")
    write_pipe_variant("frosted_pipes", "pipe_frosted", "pipe_frosted")
    write_pipe_variant("burst_gas_pipes", "pipe_burst_gas", "pipe_grey")
    print("Generated Level 2 anomaly visual block assets.")


if __name__ == "__main__":
    main()
