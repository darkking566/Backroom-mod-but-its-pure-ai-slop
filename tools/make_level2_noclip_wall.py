import json
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "noclip_backrooms")
TEX_DIR = os.path.join(ASSETS, "textures", "block", "level2")
MODEL_DIR = os.path.join(ASSETS, "models", "block", "level2")
STATE_DIR = os.path.join(ASSETS, "blockstates")
ITEM_DIR = os.path.join(ASSETS, "items")


def make_128_tex():
    img = Image.open(os.path.join(TEX_DIR, "utility_wall.png")).convert("RGB")
    w, h = img.size
    px = img.load()
    rng = random.Random(909)
    # Subtle static/glitch scanlines and faint dark seam so observant players notice something is off
    for y in range(h):
        if rng.random() < 0.22:
            shift = rng.randint(-6, 6)
            row = [px[(x + shift) % w, y] for x in range(w)]
            for x in range(w):
                r, g, b = row[x]
                if rng.random() < 0.14:
                    d = rng.randint(-38, 28)
                    r = max(0, min(255, r + d))
                    g = max(0, min(255, g + d - 4))
                    b = max(0, min(255, b + d + 8))
                px[x, y] = (r, g, b)
    # Faint dark vertical shadow crack down the middle
    for y in range(18, h - 18):
        cx = w // 2 + int(2 * (rng.random() - 0.5))
        for dx in range(-1, 2):
            r, g, b = px[cx + dx, y]
            px[cx + dx, y] = (int(r * 0.65), int(g * 0.65), int(b * 0.72))
    img.save(os.path.join(TEX_DIR, "noclip_wall.png"))


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    make_128_tex()
    model = "noclip_backrooms:block/level2/noclip_wall"
    write_json(
        os.path.join(MODEL_DIR, "noclip_wall.json"),
        {"parent": "minecraft:block/cube_all", "textures": {"all": model}},
    )
    write_json(
        os.path.join(STATE_DIR, "noclip_wall.json"),
        {"variants": {"": {"model": model}}},
    )
    write_json(
        os.path.join(ITEM_DIR, "noclip_wall.json"),
        {"model": {"type": "minecraft:model", "model": model}},
    )
    print("Generated noclip_wall 128px assets.")


if __name__ == "__main__":
    main()
