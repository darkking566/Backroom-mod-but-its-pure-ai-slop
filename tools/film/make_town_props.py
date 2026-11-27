"""The staff town's objects (town_list.py), built with the film props' tools (make_film_props.py: same axes, same baked
photo textures). A 1990s American suburb, as 狐霖's Frontrooms ("Earth") pretends to be.

Run (from the repo root; the pictures come from institute_prints.py):
  blender -b --python tools/film/make_town_props.py -- <out_dir> [preview_dir] [only,names]
  blender -b --python tools/film/make_film_icons.py -- <out_dir> <icon_dir> 64
  py tools/film/make_institute_data.py <out_dir> <icon_dir> town
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic as R  # noqa: E402

R.KINDS.update({
    "mailbox_black": ((0.08, 0.08, 0.09), "plastic", 0.6, 0.8, 0.3, "chips"),
    "flag_red": ((0.62, 0.10, 0.08), "plastic", 0.6, 0.6, 0.4, "chips"),
    "park_green": ((0.12, 0.26, 0.16), "plastic", 0.6, 0.8, 0.4, "chips"),
    "hydrant_red": ((0.60, 0.10, 0.08), "plastic", 0.6, 0.8, 0.5, "chips"),
    "weathered_wood": ((0.46, 0.38, 0.28), "wood_light", 1.0, 1.0, 0.5, None),
    "post_white": ((0.86, 0.85, 0.80), "plastic", 0.6, 0.6, 0.5, "chips"),
    "rubber": ((0.06, 0.06, 0.06), "black_plastic", 0.6, 1.0, 0.2, None),
})

import make_film_props as F  # noqa: E402
from make_film_props import D90, box, cyl, export, panel, reset, sphere, torus  # noqa: E402


def rad(a):
    return math.radians(a)


def build_mailbox():
    m = reset()
    box("Post", (0, 0, 0.5), (0.1, 0.1, 1.0), m["post_white"], 0.01)
    box("Arm", (0, 0.08, 0.98), (0.1, 0.3, 0.06), m["post_white"], 0.01)
    # The box: a tunnel shape, a round top.
    box("Body", (0, 0.12, 1.12), (0.22, 0.48, 0.16), m["mailbox_black"], 0.01)
    cyl("Top", (0, 0.12, 1.2), 0.11, 0.48, m["mailbox_black"], rot=(D90, 0, 0), verts=16)
    box("Door", (0, 0.365, 1.14), (0.22, 0.01, 0.22), m["mailbox_black"], 0.004)
    box("Flag", (0.13, 0.05, 1.28), (0.02, 0.05, 0.22), m["flag_red"], 0.004)
    box("FlagTip", (0.13, 0.12, 1.36), (0.02, 0.14, 0.06), m["flag_red"], 0.004)
    export("town_mailbox", (1, 1, 1))


def build_picnic_table():
    m = reset()
    cx, cy = 0.5, -0.5
    for i in range(5):
        box("Plank", (cx, cy - 0.32 + i * 0.16, 0.74), (1.8, 0.14, 0.04), m["weathered_wood"], 0.006)
    for sy in (-1, 1):
        for i in range(2):
            box("Seat", (cx, cy + sy * (0.62 + i * 0.13), 0.44), (1.8, 0.12, 0.04), m["weathered_wood"], 0.006)
        box("Rail", (cx, cy + sy * 0.68, 0.4), (1.6, 0.05, 0.05), m["weathered_wood"], 0.004)
    for sx in (-0.65, 0.65):
        for sy in (-1, 1):
            box("Leg", (cx + sx, cy + sy * 0.35, 0.37), (0.07, 0.07, 0.86), m["weathered_wood"], 0.004, rot=(rad(sy * 30), 0, 0))
        box("Cross", (cx + sx, cy, 0.42), (0.06, 1.5, 0.06), m["weathered_wood"], 0.004)
    # Someone's lunch: a basket, plates, cups.
    box("Basket", (cx - 0.3, cy, 0.84), (0.3, 0.2, 0.16), m["rattan"], 0.02)
    for x in (0.1, 0.55, 0.95):
        cyl("Plate", (x, cy + 0.2, 0.765), 0.09, 0.012, m["white"], verts=14)
        cyl("Cup", (x + 0.12, cy - 0.15, 0.8), 0.03, 0.09, m["red"], verts=10)
    export("town_picnic_table", (2, 1, 2))


def build_park_bench():
    m = reset()
    cx = 0.5
    for i in range(3):
        box("Slat", (cx, 0.12 - i * 0.12, 0.44), (1.7, 0.1, 0.035), m["weathered_wood"], 0.005)
    for i in range(3):
        box("Back", (cx, -0.24, 0.6 + i * 0.12), (1.7, 0.035, 0.09), m["weathered_wood"], 0.005, rot=(rad(-12), 0, 0))
    for sx in (-0.78, 0.78):
        box("EndFront", (cx + sx, 0.16, 0.22), (0.05, 0.05, 0.44), m["iron"], 0.004)
        box("EndBack", (cx + sx, -0.22, 0.42), (0.05, 0.05, 0.84), m["iron"], 0.004, rot=(rad(-8), 0, 0))
        box("Arm", (cx + sx, -0.02, 0.64), (0.05, 0.42, 0.04), m["iron"], 0.004)
        box("Foot", (cx + sx, -0.03, 0.02), (0.07, 0.46, 0.04), m["iron"], 0.004)
    export("town_park_bench", (2, 1, 1))


def build_swing_set():
    m = reset()
    cx = 1.0
    for sx in (-1.25, 1.25):
        for sy in (-1, 1):
            cyl("Leg", (cx + sx, sy * 0.35, 0.95), 0.035, 2.0, m["park_green"], rot=(rad(sy * -20), 0, 0), verts=8)
    cyl("Bar", (cx, 0, 1.88), 0.045, 2.6, m["park_green"], rot=(0, D90, 0), verts=10)
    for i, (x, tilt) in enumerate(((cx - 0.55, 0), (cx + 0.55, 18))):
        for sx in (-0.18, 0.18):
            cyl("Chain", (x + sx, math.sin(rad(tilt)) * 0.65, 1.2), 0.008, 1.36, m["chrome"], rot=(rad(-tilt), 0, 0), verts=4)
        box("Seat", (x, math.sin(rad(tilt)) * 1.3, 0.5 + (1 - math.cos(rad(tilt))) * 1.3), (0.46, 0.16, 0.03), m["rubber"], 0.01,
            rot=(rad(-tilt), 0, 0))
    export("town_swing_set", (3, 2, 1))


def build_street_clock():
    m = reset()
    cyl("Base", (0, 0, 0.15), 0.2, 0.3, m["iron"], verts=12)
    cyl("Post", (0, 0, 1.3), 0.07, 2.2, m["iron"], verts=10)
    torus("Collar", (0, 0, 2.38), 0.09, 0.02, m["brass"], segs=12, minor_segs=4)
    cyl("Case", (0, 0, 2.62), 0.34, 0.16, m["iron"], rot=(D90, 0, 0), verts=24)
    for side, rot in ((0.082, (D90, 0, math.pi)), (-0.082, (D90, 0, 0))):
        panel("Face", (0, side, 2.62), 0.56, 0.56, R.face("town_clock_face", contrast=0.1, grime=0.2), rot=rot)
    sphere("Finial", (0, 0, 3.0), 0.06, m["brass"], segs=10)
    export("town_street_clock", (1, 3, 1))


def build_fire_hydrant():
    m = reset()
    cyl("Body", (0, 0, 0.32), 0.12, 0.5, m["hydrant_red"], verts=14)
    sphere("Cap", (0, 0, 0.6), 0.12, m["hydrant_red"], scale=(1, 1, 0.7), segs=12)
    cyl("Nut", (0, 0, 0.7), 0.03, 0.05, m["hydrant_red"], verts=5)
    torus("Flange", (0, 0, 0.12), 0.14, 0.025, m["hydrant_red"], segs=14, minor_segs=4)
    for ang in (0, 180):
        cyl("Nozzle", (math.cos(rad(ang)) * 0.15, math.sin(rad(ang)) * 0.15, 0.42), 0.045, 0.08, m["hydrant_red"],
            rot=(0, D90, rad(ang)), verts=10)
    cyl("Front", (0, 0.16, 0.38), 0.06, 0.08, m["hydrant_red"], rot=(D90, 0, 0), verts=10)
    export("town_fire_hydrant", (1, 1, 1))


def build_trash_can():
    m = reset()
    cyl("Can", (0, 0, 0.42), 0.26, 0.84, m["park_green"], verts=18)
    torus("Rim", (0, 0, 0.84), 0.26, 0.02, m["iron"], segs=18, minor_segs=4)
    cyl("Liner", (0, 0, 0.8), 0.24, 0.1, m["black_plastic"], verts=16)
    box("Paper", (0.05, 0.03, 0.86), (0.18, 0.12, 0.05), m["white"], 0.02, rot=(rad(20), rad(10), rad(30)))
    export("town_trash_can", (1, 1, 1))


def build_store_shelf():
    m = reset()
    box("Back", (0, -0.36, 0.95), (0.9, 0.04, 1.9), m["pine"], 0.004)
    for sx in (-0.44, 0.44):
        box("Side", (sx, -0.08, 0.95), (0.03, 0.6, 1.9), m["pine"], 0.004)
    for i, z in enumerate((0.1, 0.55, 1.0, 1.45)):
        box("Shelf", (0, -0.08, z), (0.86, 0.58, 0.03), m["pine"], 0.004)
        panel("Goods", (0, 0.06, z + 0.2), 0.84, 0.36, R.face("town_shelf_goods", contrast=0.15, grime=0.3), facing="front")
        box("GoodsBody", (0, -0.1, z + 0.2), (0.84, 0.32, 0.36), m["cardboard"], 0.01)
    export("town_store_shelf", (1, 2, 1))


def build_store_sign():
    m = reset()
    cx = 2.0
    box("Board", (cx, -0.46, 0.5), (4.8, 0.06, 1.0), m["flag_red"], 0.01)
    panel("Face", (cx, -0.428, 0.5), 4.7, 0.92, R.face("town_store_sign", contrast=0.15, grime=0.3), facing="front")
    export("town_store_sign", (5, 1, 1))


def main():
    for build in (build_mailbox, build_picnic_table, build_park_bench, build_swing_set, build_street_clock, build_fire_hydrant,
                  build_trash_can, build_store_shelf, build_store_sign):
        build()
    with open(os.path.join(R.OUT_DIR, "sizes.txt"), "a" if F.ONLY else "w", encoding="utf8") as f:
        for name, (w, h, d) in R.SIZES.items():
            f.write(f"{name} {w} {h} {d}\n")
    print("TOWN PROPS:", len(R.SIZES))


main()
