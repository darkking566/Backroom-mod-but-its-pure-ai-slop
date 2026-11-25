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


def ensure_dirs():
    for d in (TEX_DIR, MODEL_DIR, STATE_DIR, ITEM_DIR, LOOT_DIR):
        os.makedirs(d, exist_ok=True)


def noisy_fill(w, h, base, var, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = rng.randint(-var, var)
            px[x, y] = (
                max(0, min(255, base[0] + d)),
                max(0, min(255, base[1] + d)),
                max(0, min(255, base[2] + d)),
                255,
            )
    return img


def make_toolbox_tex():
    img = noisy_fill(64, 64, (148, 34, 30), 10, 101)
    px = img.load()
    # Lid seam and metal latch
    for x in range(64):
        for y in (26, 27):
            px[x, y] = (55, 18, 16, 255)
        for y in (2, 3, 60, 61):
            px[x, y] = (112, 26, 24, 255)
    for y in range(22, 32):
        for x in range(26, 38):
            px[x, y] = (178, 182, 188, 255) if 27 < x < 36 else (95, 98, 104, 255)
    img.save(os.path.join(TEX_DIR, "toolbox.png"))

    handle = noisy_fill(64, 64, (85, 88, 94), 8, 102)
    handle.save(os.path.join(TEX_DIR, "toolbox_metal.png"))


def make_cable_reel_tex():
    wood = noisy_fill(64, 64, (124, 92, 58), 12, 201)
    px = wood.load()
    for y in range(64):
        if y % 8 == 0:
            for x in range(64):
                px[x, y] = (92, 66, 40, 255)
    wood.save(os.path.join(TEX_DIR, "reel_wood.png"))

    cable = noisy_fill(64, 64, (42, 46, 52), 7, 202)
    cpx = cable.load()
    for y in range(0, 64, 4):
        for x in range(64):
            cpx[x, y] = (28, 30, 34, 255)
            if y + 1 < 64:
                cpx[x, y + 1] = (164, 98, 52, 255) if (y // 4) % 5 == 0 else (58, 62, 68, 255)
    cable.save(os.path.join(TEX_DIR, "reel_cable.png"))


def make_lumber_tex():
    img = noisy_fill(64, 64, (138, 104, 66), 14, 301)
    px = img.load()
    for y in range(64):
        if y % 16 in (0, 15):
            for x in range(64):
                px[x, y] = (72, 52, 32, 255)
        elif y % 4 == 0:
            for x in range(64):
                r, g, b, a = px[x, y]
                px[x, y] = (max(0, r - 14), max(0, g - 12), max(0, b - 8), 255)
    img.save(os.path.join(TEX_DIR, "lumber.png"))


def all_faces(tex, uv=(0, 0, 16, 16)):
    # Explicit uv: without it a part smaller than a block shows only a cropped window of its texture.
    return {f: {"texture": tex, "uv": list(uv)} for f in ("north", "south", "east", "west", "up", "down")}


def toolbox_faces():
    front = {"texture": "#box", "uv": [0, 0, 16, 16]}
    side = {"texture": "#box", "uv": [10, 0, 16, 16]}  # right of the latch: just the lid seam
    lid = {"texture": "#box", "uv": [0, 1, 16, 5]}  # above the seam: plain paint
    return {"north": front, "south": front, "east": side, "west": side, "up": lid, "down": lid}


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def write_block_assets(name, elements, textures, turns=(0, 90, 180, 270)):
    write_json(
        os.path.join(MODEL_DIR, f"{name}.json"),
        {"parent": "minecraft:block/block", "textures": textures, "elements": elements},
    )
    write_json(
        os.path.join(STATE_DIR, f"{name}.json"),
        # A list of variants: each placed block picks one turn, so a row of them is not one copy repeated.
        {"variants": {"": [{"model": f"noclip_backrooms:block/level2/{name}", "y": t} if t else
                           {"model": f"noclip_backrooms:block/level2/{name}"} for t in turns]}},
    )
    write_json(
        os.path.join(ITEM_DIR, f"{name}.json"),
        {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:block/level2/{name}"}},
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


def main():
    ensure_dirs()
    make_toolbox_tex()
    make_cable_reel_tex()
    make_lumber_tex()

    write_block_assets(
        "toolbox",
        [
            {"from": [2, 0, 4], "to": [14, 6, 12], "faces": toolbox_faces()},
            {"from": [5, 6, 7], "to": [11, 7.5, 9], "faces": all_faces("#metal")},
        ],
        {
            "particle": "noclip_backrooms:block/level2/toolbox",
            "box": "noclip_backrooms:block/level2/toolbox",
            "metal": "noclip_backrooms:block/level2/toolbox_metal",
        },
        turns=(0, 180),  # its outline is longer than wide; a quarter turn would leave it outside
    )

    write_block_assets(
        "cable_reel",
        [
            {"from": [2, 0, 2], "to": [14, 2, 14], "faces": all_faces("#wood")},
            {"from": [3.5, 2, 3.5], "to": [12.5, 10, 12.5], "faces": all_faces("#cable")},
            {"from": [2, 10, 2], "to": [14, 12, 14], "faces": all_faces("#wood")},
        ],
        {
            "particle": "noclip_backrooms:block/level2/reel_wood",
            "wood": "noclip_backrooms:block/level2/reel_wood",
            "cable": "noclip_backrooms:block/level2/reel_cable",
        },
    )

    write_block_assets(
        "lumber_stack",
        [
            {"from": [1, 0, 2], "to": [15, 3, 14], "faces": all_faces("#wood")},
            {"from": [2, 3, 1], "to": [14, 6, 15], "faces": all_faces("#wood")},
            {"from": [3, 6, 3], "to": [13, 8.5, 13], "faces": all_faces("#wood")},
        ],
        {
            "particle": "noclip_backrooms:block/level2/lumber",
            "wood": "noclip_backrooms:block/level2/lumber",
        },
    )
    print("Generated toolbox, cable_reel, and lumber_stack assets.")


if __name__ == "__main__":
    main()
