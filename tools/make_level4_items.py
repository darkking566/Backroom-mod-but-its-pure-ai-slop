"""Draws 64x64 multi-zone textures and builds 3D multi-element JSON models for Level 4's dedicated items:
- Religious items (for Amor Incrementum): brass_crucifix, faded_icon_frame
- T.B.D. useless curios: broken_nw_compass, calendar_1998, blank_stamp, unlabeled_tape, rain_glass_orb

Run: python tools/make_level4_items.py
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
TEX_DIR = ASSETS / "textures/item/level4"
MOD_DIR = ASSETS / "models/item/level4"
ITEMS_DIR = ASSETS / "items"
S = 64

DISPLAY = {
    "thirdperson_righthand": {"rotation": [65, 25, 0], "translation": [0, 2.2, 1.0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [65, 25, 0], "translation": [0, 2.2, 1.0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [-8, -22, 10], "translation": [1.5, 3.0, 0.5], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-8, -22, 10], "translation": [1.5, 3.0, 0.5], "scale": [0.68, 0.68, 0.68]},
    "gui": {"rotation": [28, 38, 0], "translation": [0, 0.5, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"translation": [0, 2.2, 0], "scale": [0.55, 0.55, 0.55]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
}


def fill_zone(img, qx, qy, base_rgb, rng, grain=10, stripe=False):
    arr = np.asarray(img, dtype=np.int16)
    x0, y0 = qx * 32, qy * 32
    noise = rng.integers(-grain, grain + 1, (32, 32, 1))
    patch = np.full((32, 32, 3), base_rgb, dtype=np.int16) + noise
    if stripe:
        for y in range(32):
            if y % 4 == 0:
                patch[y, :, :] -= 10
    arr[y0:y0 + 32, x0:x0 + 32, :3] = np.clip(patch, 0, 255)
    arr[y0:y0 + 32, x0:x0 + 32, 3] = 255
    return Image.fromarray(arr.astype("uint8"), "RGBA")


def q_uv(qx, qy):
    # UV coords in [0..16] mapped to quadrant (qx, qy)
    return [qx * 8 + 0.5, qy * 8 + 0.5, (qx + 1) * 8 - 0.5, (qy + 1) * 8 - 0.5]


def el(x0, y0, z0, x1, y1, z1, qx, qy, emit=0, rot=None):
    uv = q_uv(qx, qy)
    faces = {f: {"uv": uv, "texture": "#0", **({"light_emission": emit} if emit else {})}
             for f in ("north", "south", "east", "west", "up", "down")}
    out = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}
    if rot:
        out["rotation"] = rot
    return out


def write_item(name, img, elements):
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    MOD_DIR.mkdir(parents=True, exist_ok=True)
    img.save(TEX_DIR / f"{name}.png", optimize=True)
    tex_id = f"noclip_backrooms:item/level4/{name}"
    model = {"textures": {"0": tex_id, "particle": tex_id}, "gui_light": "side", "elements": elements, "display": DISPLAY}
    (MOD_DIR / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf8")
    (ITEMS_DIR / f"{name}.json").write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/level4/{name}"}}, indent=2) + "\n",
        encoding="utf8"
    )


def make_brass_crucifix():
    rng = np.random.default_rng(4001)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (194, 152, 68), rng, 12)   # brushed brass
    img = fill_zone(img, 1, 0, (226, 188, 96), rng, 8)    # bright gold rim
    img = fill_zone(img, 0, 1, (132, 92, 42), rng, 10)    # tarnished shadow
    img = fill_zone(img, 1, 1, (246, 224, 146), rng, 6)   # central glowing halo inlay
    elements = [
        el(7.1, 1.5, 7.3, 8.9, 14.5, 8.7, 0, 0),          # vertical shaft
        el(3.5, 9.6, 7.3, 12.5, 11.4, 8.7, 0, 0),         # crossbar
        el(6.6, 1.2, 7.1, 9.4, 2.2, 8.9, 1, 0),           # base cap
        el(3.2, 9.3, 7.1, 4.0, 11.7, 8.9, 1, 0),          # left end cap
        el(12.0, 9.3, 7.1, 12.8, 11.7, 8.9, 1, 0),        # right end cap
        el(6.3, 8.8, 7.0, 9.7, 12.2, 9.0, 0, 1),          # central backing ring
        el(7.0, 9.5, 6.8, 9.0, 11.5, 9.2, 1, 1, emit=6),  # glowing core
    ]
    write_item("brass_crucifix", img, elements)


def make_faded_icon_frame():
    rng = np.random.default_rng(4002)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (148, 108, 52), rng, 12)   # gilt wood frame
    img = fill_zone(img, 1, 0, (216, 196, 148), rng, 8)   # aged canvas
    img = fill_zone(img, 0, 1, (112, 48, 44), rng, 10)    # faded crimson robe
    img = fill_zone(img, 1, 1, (238, 206, 102), rng, 8)   # painted halo
    d = ImageDraw.Draw(img)
    d.ellipse([40, 36, 56, 52], fill=(244, 214, 118, 255))
    elements = [
        el(2.5, 1.5, 7.0, 13.5, 14.5, 8.4, 1, 0),          # canvas panel
        el(2.0, 1.0, 6.5, 14.0, 2.2, 9.0, 0, 0),           # bottom frame
        el(2.0, 13.8, 6.5, 14.0, 15.0, 9.0, 0, 0),         # top frame
        el(2.0, 2.2, 6.5, 3.2, 13.8, 9.0, 0, 0),           # left frame
        el(12.8, 2.2, 6.5, 14.0, 13.8, 9.0, 0, 0),         # right frame
        el(6.0, 8.8, 6.7, 10.0, 12.2, 7.2, 1, 1, emit=4),  # painted halo
        el(5.5, 3.2, 6.7, 10.5, 8.8, 7.2, 0, 1),           # figure robe
    ]
    write_item("faded_icon_frame", img, elements)


def make_broken_nw_compass():
    rng = np.random.default_rng(4003)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (172, 136, 66), rng, 10)   # brass body
    img = fill_zone(img, 1, 0, (232, 228, 214), rng, 6)   # dial paper
    img = fill_zone(img, 0, 1, (206, 48, 42), rng, 6)     # stuck red NW needle
    img = fill_zone(img, 1, 1, (164, 204, 216), rng, 8)   # scratched glass
    elements = [
        el(3.5, 6.2, 3.5, 12.5, 8.4, 12.5, 0, 0),         # brass casing
        el(4.5, 8.2, 4.5, 11.5, 8.7, 11.5, 1, 0),         # dial face
        el(4.8, 8.7, 7.4, 8.4, 9.2, 8.6, 0, 1,
           rot={"angle": 45, "axis": "y", "origin": [8, 8.9, 8]}),  # frozen NW needle
        el(7.2, 9.0, 2.4, 8.8, 9.8, 3.6, 0, 0),           # ring loop
    ]
    write_item("broken_nw_compass", img, elements)


def make_calendar_1998():
    rng = np.random.default_rng(4004)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (54, 62, 74), rng, 8)      # dark cardboard stand
    img = fill_zone(img, 1, 0, (240, 238, 232), rng, 6)   # printed 1998 page
    img = fill_zone(img, 0, 1, (186, 54, 48), rng, 6)     # red month header
    img = fill_zone(img, 1, 1, (176, 182, 188), rng, 8)   # wire spiral rings
    elements = [
        el(3.0, 2.0, 6.0, 13.0, 10.5, 9.5, 0, 0),         # tent stand
        el(3.4, 2.6, 5.2, 12.6, 9.6, 6.1, 1, 0),          # paper block
        el(3.4, 8.4, 5.0, 12.6, 9.7, 5.4, 0, 1),          # red header
        el(4.2, 10.2, 5.6, 5.2, 11.2, 6.8, 1, 1),         # spiral ring L
        el(10.8, 10.2, 5.6, 11.8, 11.2, 6.8, 1, 1),       # spiral ring R
    ]
    write_item("calendar_1998", img, elements)


def make_blank_stamp():
    rng = np.random.default_rng(4005)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (148, 102, 62), rng, 10, stripe=True)  # turned wood handle
    img = fill_zone(img, 1, 0, (184, 148, 76), rng, 8)                # brass ferrule
    img = fill_zone(img, 0, 1, (188, 72, 74), rng, 8)                 # dry blank rubber pad
    img = fill_zone(img, 1, 1, (64, 58, 56), rng, 8)                  # base mount
    elements = [
        el(4.0, 2.0, 5.5, 12.0, 3.3, 10.5, 0, 1),         # blank rubber pad
        el(3.8, 3.3, 5.3, 12.2, 4.8, 10.7, 1, 1),         # stamp block
        el(6.8, 4.8, 6.8, 9.2, 6.2, 9.2, 1, 0),           # brass collar
        el(7.1, 6.2, 7.1, 8.9, 11.5, 8.9, 0, 0),          # handle shaft
        el(6.3, 11.5, 6.3, 9.7, 13.8, 9.7, 0, 0),         # handle knob
    ]
    write_item("blank_stamp", img, elements)


def make_unlabeled_tape():
    rng = np.random.default_rng(4006)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (42, 44, 48), rng, 6)      # matte charcoal plastic
    img = fill_zone(img, 1, 0, (224, 220, 206), rng, 6)   # blank torn label
    img = fill_zone(img, 0, 1, (198, 202, 206), rng, 8)   # white spool hubs
    img = fill_zone(img, 1, 1, (74, 56, 42), rng, 6)      # magnetic tape
    elements = [
        el(2.2, 4.4, 7.2, 13.8, 11.6, 8.8, 0, 0),         # cassette shell
        el(3.2, 8.2, 7.0, 12.8, 10.8, 9.0, 1, 0),         # blank label
        el(4.5, 6.0, 6.9, 6.5, 8.0, 9.1, 0, 1),           # left spool
        el(9.5, 6.0, 6.9, 11.5, 8.0, 9.1, 0, 1),          # right spool
        el(6.6, 6.2, 7.0, 9.4, 7.8, 9.0, 1, 1),           # tape window
    ]
    write_item("unlabeled_tape", img, elements)


def make_rain_glass_orb():
    rng = np.random.default_rng(4007)
    img = Image.new("RGBA", (S, S))
    img = fill_zone(img, 0, 0, (148, 186, 212), rng, 10)  # misty rain glass
    img = fill_zone(img, 1, 0, (214, 236, 248), rng, 8)   # shimmering water drop core
    img = fill_zone(img, 0, 1, (164, 132, 68), rng, 10)   # brass stand
    img = fill_zone(img, 1, 1, (92, 124, 152), rng, 8)    # inner storm tint
    elements = [
        el(5.2, 2.0, 5.2, 10.8, 3.4, 10.8, 0, 1),         # brass stand base
        el(4.6, 3.6, 4.6, 11.4, 10.4, 11.4, 0, 0),        # glass orb body
        el(5.6, 4.6, 5.6, 10.4, 9.4, 10.4, 1, 0, emit=5), # rainy inner core
        el(5.0, 3.2, 5.0, 11.0, 4.2, 11.0, 1, 1),         # lower band
    ]
    write_item("rain_glass_orb", img, elements)


def main():
    make_brass_crucifix()
    make_faded_icon_frame()
    make_broken_nw_compass()
    make_calendar_1998()
    make_blank_stamp()
    make_unlabeled_tape()
    make_rain_glass_orb()
    print("Generated 7 Level 4 3D item models and 64x64 textures.")


if __name__ == "__main__":
    main()
