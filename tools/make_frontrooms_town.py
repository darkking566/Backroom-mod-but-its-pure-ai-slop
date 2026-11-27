"""The staff town's houses in the Frontrooms (docs/企劃書/1-遊戲玩法/README.md, "前室的夜晚：398"): clapboard siding in
three colours, white trim, asphalt shingles (and their stairs for the roofs), six-pane windows, a garage door. A 1990s
American street, from CC0 photos (ambientCG, cached in tools/.cache/ambientcg) like the institute's surfaces.

Writes 128 px textures (labPBR _n and _s; glass with alpha), models, blockstates and item models.

Run: python tools/make_frontrooms_town.py
"""
import sys
from pathlib import Path

import numpy as np

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_textures as mt  # noqa: E402
from make_textures import X, Y, SIZE, clouds, flat, noise, paint, step, tint  # noqa: E402
from make_frontrooms import ASSETS, MODELS, NS, TEXTURES, box, cube_all, grime, variant, write_json  # noqa: E402
import make_frontrooms_institute as inst  # noqa: E402

inst.PHOTOS.update({"wood": "Wood049", "roof": "Asphalt031"})


def siding(seed, color):
    """Clapboards, eight to a block: each board's lower edge throws a shadow on the one below."""
    m = inst.surface(color, "wood", 0.5, (0.3, 0.4), contrast=0.18, smooth=0.35, porosity=20, bump=0.3)
    board = Y % 16
    shade = 1 - 0.16 * (board / 15.0)
    m.color *= shade[..., None]
    paint(m, 1 - step(0, 1.5, 15 - board), tuple(c * 0.55 for c in color), height=-1.5)
    m.height += board * 0.12
    # Dirt kicked up at the bottom, mildew running down from the eaves.
    tint(m, step(80, 128, Y) * step(0.3, 0.8, clouds(seed, 4)) * 0.25, (0.8, 0.78, 0.7))
    tint(m, step(0.55, 0.9, noise(seed + 3, 32, 4)) * 0.2, (0.82, 0.86, 0.8))
    return m


def trim(seed):
    m = inst.surface((232, 230, 222), "wood", 0.4, (0.6, 0.2), contrast=0.12, smooth=0.4, porosity=15, bump=0.2)
    grime(m, seed, 0.08)
    return m


def shingles(seed):
    """Asphalt shingles: four courses to a block, tabs offset course by course, granules, a few lifted corners."""
    m = inst.surface((74, 70, 66), "roof", 0.4, (0.2, 0.7), contrast=0.5, smooth=0.1, porosity=80, bump=0.5)
    course = Y // 32
    row = Y % 32
    offset = (course % 2) * 16
    tab = (X + offset) % 32
    paint(m, 1 - step(0, 1.2, np.minimum(tab, 31 - tab)), (40, 38, 36), height=-1.0)
    paint(m, 1 - step(0, 2.0, 31 - row), (36, 34, 32), height=-1.6)
    m.height += row * 0.06
    m.color *= (1 + ((X // 32 + course * 7) % 3 - 1)[..., None] * 0.04)
    grime(m, seed, 0.1)
    return m


def window():
    """A six-pane sash window in white frames, the glass dark and catching the sky."""
    image = np.zeros((SIZE, SIZE, 4))
    sky = 0.12 * (1 - Y / 128) + 0.06 * clouds(51, 3)
    image[..., 0], image[..., 1], image[..., 2] = 40 + sky * 170, 46 + sky * 170, 54 + sky * 180
    image[..., 3] = 175
    frame = (1 - box(7, 7, 121, 121, 0.4))
    for x in (45, 83):
        frame = np.maximum(frame, box(x - 2, 0, x + 2, 128, 0.4))
    frame = np.maximum(frame, box(0, 62, 128, 67, 0.4))
    for c, v in enumerate((236, 234, 226)):
        image[..., c] = image[..., c] * (1 - frame) + v * frame
    image[..., 3] = np.maximum(image[..., 3], frame * 255)
    return image


def garage_door(seed, windows):
    """A sectional garage door, two rows of raised panels to a block; the top block has its row of small windows."""
    m = flat((226, 224, 216), 0.45, 30)
    m.color *= (1 + clouds(seed, 4)[..., None] * 0.03)
    for row in range(2):
        y0 = row * 64
        paint(m, box(0, y0 + 61, 128, y0 + 64, 0.5), (150, 150, 146), height=-1.5)
        for col in range(2):
            x0 = col * 64
            inset = box(x0 + 8, y0 + 10, x0 + 56, y0 + 54, 0.8)
            if windows and row == 0:
                paint(m, inset, (36, 42, 50), height=-1.5, smooth=0.9, f0=40)
            else:
                paint(m, inset * 0.6, (214, 212, 204), height=1.2)
    tint(m, step(96, 128, Y) * step(0.3, 0.8, clouds(seed + 2, 4)) * 0.2, (0.85, 0.82, 0.76))
    return m


TEXTURES_SET = {
    "town_siding_gray": lambda: siding(61, (170, 172, 170)),
    "town_siding_blue": lambda: siding(62, (128, 148, 166)),
    "town_siding_cream": lambda: siding(63, (214, 204, 176)),
    "town_trim": lambda: trim(64),
    "town_shingles": lambda: shingles(65),
    "town_garage_door": lambda: garage_door(66, False),
    "town_garage_door_top": lambda: garage_door(67, True),
}

# Vanilla's rotations for a stairs block (checked against oak_stairs.json).
STAIR_BASE = {"east": 0, "south": 90, "west": 180, "north": 270}


def stairs_states(name):
    variants = {}
    for facing, base in STAIR_BASE.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = NS + name + ("" if shape == "straight" else "_inner" if shape.startswith("inner") else "_outer")
                y = base
                if half == "bottom" and shape.endswith("left"):
                    y = base - 90
                if half == "top" and shape.endswith("right"):
                    y = base + 90
                y %= 360
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                if y:
                    v["y"] = y
                if y or half == "top":
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES
    for name, make in TEXTURES_SET.items():
        mt.save(name, make())
    inst.save_rgba("town_window", window())
    for name in list(TEXTURES_SET) + ["town_window"]:
        write_json(MODELS / f"{name}.json", cube_all(name))
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": variant(name)}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})
    tex = NS + "town_shingles"
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write_json(MODELS / f"town_shingle_stairs{suffix}.json",
                   {"parent": f"minecraft:block/{parent}", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write_json(ASSETS / "blockstates/town_shingle_stairs.json", stairs_states("town_shingle_stairs"))
    write_json(ASSETS / "items/town_shingle_stairs.json", {"model": {"type": "minecraft:model", "model": NS + "town_shingle_stairs"}})
    print("Town textures written.")


if __name__ == "__main__":
    main()
