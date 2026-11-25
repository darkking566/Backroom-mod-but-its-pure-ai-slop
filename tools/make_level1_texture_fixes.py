"""Level 1 texture review fixes, without re-running make_level1.py (which would overwrite hand-made assets).

    python tools/make_level1_texture_fixes.py

- Vehicles, the vending machine and other big props get their own paint, glass, rubber and lens textures instead of
  borrowed wall textures, and every face gets a uv inside 0..16 (no bleeding from neighbours in the atlas).
- Look-alikes get told apart: the white wood door, the sign-up booth, the clinic bed.
- Blocks that showed their front on all six faces get plain ends; upright neon columns get a vertical tube.
- Grain-direction textures stop turning 90 degrees; large single-texture walls and pillars get two stained variants.
Writes textures/block/level1/, models/block/level1/ and blockstates/ for the blocks it names, nothing else.
"""

import json
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
import make_textures as mt  # noqa: E402
import make_level1 as ml  # noqa: E402
from make_textures import SIZE, X, Y, box, clouds, flat, inside, metal, noise, paint, step, tint  # noqa: E402

ASSETS = ml.ASSETS
TEXTURES = ml.TEXTURES
MODELS = ml.MODELS
STATES = ASSETS / "blockstates"
NS = ml.NS


# Textures

def dust(m, seed, amount=0.12):
    tint(m, step(-0.1, 0.6, clouds(seed, 3)) * amount * 4, (0.92, 0.9, 0.86))


def scratches(m, seed, count=14):
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x0, y0 = rng.uniform(0, SIZE, 2)
        angle, length = rng.uniform(-0.5, 0.5), rng.uniform(10, 40)
        d = np.abs((Y - y0) * np.cos(angle) - (X - x0) * np.sin(angle))
        along = (X - x0) * np.cos(angle) + (Y - y0) * np.sin(angle)
        tint(m, (1 - step(0.3, 0.9, d)) * (along > 0) * (along < length) * 0.5, (1.12, 1.12, 1.1))


def rust(m, seed, low=True):
    spots = step(0.5, 0.7, clouds(seed, 6))
    if low:
        spots = np.maximum(spots, step(96, 124, Y) * step(-0.3, 0.3, noise(seed + 1, 16)))
    paint(m, spots * 0.7, (112, 62, 34), height=0.6, smooth=0.1)


def car_paint():
    m = metal((92, 106, 118), 0.55)
    dust(m, 3100)
    scratches(m, 3101)
    rust(m, 3102)
    return m


def van_paint():
    m = metal((212, 210, 200), 0.5)
    tint(m, step(-0.2, 0.7, clouds(3110, 4)) * 0.5, (0.86, 0.84, 0.78))
    scratches(m, 3111, 10)
    rust(m, 3112)
    return m


def vehicle_glass():
    m = flat((34, 42, 48), 0.92, 40)
    streak = np.abs(((X + Y * 0.6) % 64) - 32)
    tint(m, (1 - step(3, 9, streak)) * 0.5, (1.9, 1.9, 1.9))
    dust(m, 3120, 0.2)
    return m


def tail_light():
    m = flat((150, 26, 24), 0.8, 30)
    tint(m, (np.abs(Y % 16 - 8) < 1) * 0.6, (0.6, 0.5, 0.5))
    m.glow += 0.15
    return m


def tyre_rubber():
    m = flat((30, 30, 30), 0.15, 8, 30)
    paint(m, (np.abs(X % 16 - 8) < 1.5).astype(float), (16, 16, 16), height=-1.5)
    dust(m, 3130, 0.1)
    return m


def dark_plastic():
    m = flat((24, 24, 26), 0.4, 12)
    m.color *= (1 + noise(3140, 32) * 0.05)[..., None]
    return m


def vending_shell():
    m = metal((150, 32, 30), 0.55)
    scratches(m, 3150, 18)
    dust(m, 3151)
    tint(m, step(100, 126, Y) * 0.6, (0.7, 0.7, 0.7))
    return m


def white_wood_door(top):
    """Painted wood, four raised panels and the grain showing through: not the too-clean white door."""
    m = flat((226, 222, 210), 0.3, 10, 20)
    grain = noise(3160, 4, 128) * 0.6 + noise(3161, 24, 128) * 0.4
    m.color *= (1 + grain * 0.06)[..., None]
    m.height += grain * 0.3
    for left, right in ((14, 58), (70, 114)):
        top_y, bottom_y = (14, 124) if top else (4, 112)
        frame = box(left, top_y, right, bottom_y, 1) - box(left + 4, top_y + 4, right - 4, bottom_y - 4, 1)
        paint(m, np.clip(frame, 0, 1), (196, 190, 176), height=-1.2)
    tint(m, step(0.2, 0.6, clouds(3162 + top, 5)) * 0.5, (0.9, 0.87, 0.8))
    return m


def signup_booth():
    """A plywood kiosk front with sign-up sheets pinned to it."""
    m = flat((176, 140, 86), 0.2, 8, 30)
    m.color *= (1 + (noise(3170, 4, 128) * 0.5 + noise(3171, 32, 128) * 0.2) * 0.1)[..., None]
    paint(m, box(0, 0, 128, 10), (232, 196, 40), height=1)
    for left in (16, 54, 92):
        paint(m, box(left, 30, left + 24, 66, 1), (236, 234, 226), height=0.6)
        for line in range(36, 62, 5):
            paint(m, box(left + 3, line, left + 21, line + 1), (60, 60, 70))
        paint(m, box(left + 10, 28, left + 14, 32), (190, 50, 40))
    paint(m, box(0, 118, 128, 128), (90, 70, 46), height=-0.5)
    return m


def laminate_top():
    m = flat((196, 188, 170), 0.45, 10)
    m.color *= (1 + noise(3180, 8) * 0.04)[..., None]
    paint(m, np.clip(1 - box(3, 3, 125, 125, 1), 0, 1), (120, 110, 96))
    return m


def clinic_sheet():
    """The free clinic's beds: pale blue sheets with a green cross."""
    m = flat((176, 204, 220), 0.2, 8, 40)
    m.color *= (1 + noise(3190, 6, 64) * 0.04)[..., None]
    paint(m, box(54, 34, 74, 94), (46, 150, 90))
    paint(m, box(34, 54, 94, 74), (46, 150, 90))
    return m


def shelf_top():
    m = flat((86, 60, 38), 0.35, 10, 30)
    m.color *= (1 + (noise(3200, 4, 128) * 0.5 + noise(3201, 32, 128) * 0.2) * 0.1)[..., None]
    return m


def painting_frame():
    m = metal((168, 132, 64), 0.55)
    tint(m, step(0.1, 0.6, clouds(3210, 4)) * 0.6, (0.7, 0.62, 0.5))
    return m


def sign_back():
    m = metal((120, 122, 124), 0.5)
    dust(m, 3220)
    paint(m, np.clip(1 - box(4, 4, 124, 124, 1), 0, 1), (76, 78, 80), height=1.5)
    return m


NEW = {"car_paint": car_paint, "van_paint": van_paint, "vehicle_glass": vehicle_glass, "tail_light": tail_light,
       "tyre_rubber": tyre_rubber, "dark_plastic": dark_plastic, "vending_shell": vending_shell,
       "white_wood_door": lambda: white_wood_door(False), "white_wood_door_top": lambda: white_wood_door(True),
       "signup_booth": signup_booth, "signup_booth_top": laminate_top, "clinic_sheet": clinic_sheet,
       "shelf_top": shelf_top, "painting_frame": painting_frame, "sign_back": sign_back}

# Stained variants of the big single-texture surfaces: darker damp patches and a run-off streak, faded at the edges.
VARIANTS = ["garage_pillar", "gothic_brick", "gothic_column", "legend_wall", "legend_pillar", "garden_pillar", "copper_pillar", "tarp"]


def stained(name, k):
    seed = 3300 + 17 * VARIANTS.index(name) + k
    patch = step(0.15, 0.55, clouds(seed, 4))
    rng = np.random.default_rng(seed)
    x0 = rng.uniform(24, 104)
    streak = (1 - step(2, 7, np.abs(X - x0 - noise(seed + 1, 4, 16) * 4))) * step(0, 90, Y) * (k == 1)
    shade = 1 - (np.maximum(patch * 0.22, streak * 0.18)) * inside()
    for suffix in ("", "_n", "_s"):
        source = TEXTURES / f"{name}{suffix}.png"
        if not source.exists():
            continue
        with Image.open(source) as image:
            data = np.asarray(image).astype(float)
        if suffix == "":
            data[..., :3] *= shade[..., None]
        Image.fromarray(np.clip(np.rint(data), 0, 255).astype("uint8"), image.mode).save(TEXTURES / f"{name}_v{k}{suffix}.png", optimize=True)


# Models

def uv_for(face, start, end):
    w = {"north": 0, "south": 0, "up": 0, "down": 0, "east": 2, "west": 2}[face]
    h = {"north": 1, "south": 1, "east": 1, "west": 1, "up": 2, "down": 2}[face]
    size_u, size_v = abs(end[w] - start[w]), abs(end[h] - start[h])
    return [0, 0, round(min(size_u, 16), 3), round(min(size_v, 16), 3)]


def retexture(name, swaps, extra=None):
    """Points a big prop at its own textures and pins every face's uv inside the texture."""
    path = MODELS / f"{name}.json"
    model = json.loads(path.read_text(encoding="utf8"))
    for key, texture in swaps.items():
        model["textures"][key] = NS + texture
    for element in model["elements"]:
        if all(0 <= c <= 16 for c in element["from"] + element["to"]):
            continue  # inside the block: the default uv already stays on the texture
        for face, data in element["faces"].items():
            data.setdefault("uv", uv_for(face, element["from"], element["to"]))
    ml.write_json(path, model)


def column(name, side, end):
    ml.write_json(MODELS / f"{name}.json", {"parent": "minecraft:block/cube_column",
                                            "textures": {"particle": NS + side, "side": NS + side, "end": NS + end}})


def upright_neon(name):
    sides = {f: {"texture": "#all", "rotation": 90} for f in ("north", "south", "east", "west")}
    sides.update({f: {"texture": "#all"} for f in ("up", "down")})
    ml.write_json(MODELS / f"{name}_upright.json", {"parent": "minecraft:block/block",
                                                   "textures": {"particle": NS + name, "all": NS + name},
                                                   "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": sides}]})
    ml.write_json(STATES / f"{name}.json", {"variants": {"upright=false": {"model": NS + name},
                                                        "upright=true": {"model": NS + name + "_upright"}}})


def main():
    mt.OUT = TEXTURES
    for name, draw in NEW.items():
        mt.save(name, draw())
    for name in VARIANTS:
        for k in (1, 2):
            stained(name, k)
            ml.write_json(MODELS / f"{name}_v{k}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + f"{name}_v{k}"}})
        ml.write_json(STATES / f"{name}.json", {"variants": {"": [{"model": NS + name, "weight": 4},
                                                                  {"model": NS + f"{name}_v1", "weight": 1},
                                                                  {"model": NS + f"{name}_v2", "weight": 1}]}})

    vehicle = {"glass": "vehicle_glass", "red": "tail_light", "dark": "tyre_rubber"}
    retexture("abandoned_car", {"particle": "car_paint", "body": "car_paint", **vehicle})
    retexture("abandoned_van", {"particle": "van_paint", "body": "van_paint", **vehicle})
    retexture("vending_machine", {"particle": "vending_shell", "red": "vending_shell", "dark": "dark_plastic"})
    retexture("electrical_cabinet", {"red": "tail_light", "dark": "dark_plastic"})
    retexture("pallet_stack", {"red": "vending_shell", "dark": "dark_plastic"})
    retexture("floodlight", {"glow": "floodlight_lens"})
    retexture("site_machine", {})

    for name in ("white_wood_door", "white_wood_door_top"):
        retexture(name, {"particle": name, "leaf": name})
    column("signup_booth", "signup_booth", "signup_booth_top")
    clinic = json.loads((MODELS / "hospital_bed.json").read_text(encoding="utf8"))
    clinic["textures"].update({"particle": NS + "clinic_sheet", "sheet": NS + "clinic_sheet"})
    ml.write_json(MODELS / "clinic_bed.json", clinic)
    ml.write_json(STATES / "clinic_bed.json", {"variants": {f"facing={f}": ({"model": NS + "clinic_bed", "y": y} if y else {"model": NS + "clinic_bed"})
                                                           for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

    # Only the front is a drawer (the blockstate turns it by FACING); the back and ends are the booth's panels.
    for name in ("booth_drawer", "booth_drawer_open"):
        ml.write_json(MODELS / f"{name}.json", {"parent": "minecraft:block/orientable", "textures": {
            "particle": NS + name, "front": NS + name, "side": NS + "booth_wall", "top": NS + "booth_wall"}})
    column("book_shelf", "book_shelf", "shelf_top")
    column("palace_painting", "palace_painting", "painting_frame")
    column("attic_hatch", "attic_hatch", "garage_wall")
    for sign in ("first_light_sign", "blink_sign", "trader_sign", "clinic_sign", "tom_sign", "signup_sign", "justice_sign"):
        column(sign, sign, "sign_back")
    for neon in ("neon_cyan", "neon_magenta", "neon_amber"):
        upright_neon(neon)

    # Grain and brick courses run one way: half turns keep the variety, quarter turns broke the pattern.
    for name in ("legend_floor", "legend_floor_line", "legend_ceiling", "gothic_ceiling"):
        ml.write_json(STATES / f"{name}.json", {"variants": {"": ml.spin(name, None, (0, 180))}})


if __name__ == "__main__":
    main()
