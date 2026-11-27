"""The M.E.G. institute's objects (institute_list.py), after the research facility at the end of the film (A24's
Backrooms, 2026): built with the film props' tools (make_film_props.py: same axes, same baked photo textures).

Axes as in make_film_props.py: the master block's bottom centre is (0, 0, 0); a prop W wide, H tall and D deep spans
X -0.5..W-0.5, Z 0..H, Y -(D-0.5)..0.5; its front looks toward +Y. A wall prop's back is against the wall at Y -0.5.

Run (from the repo root):
  py tools/film/prints.py tools/film/prints
  py tools/film/institute_prints.py tools/film/prints
  blender -b --python tools/film/make_institute_props.py -- <out_dir> [preview_dir] [only,names]
  blender -b --python tools/film/make_film_icons.py -- <out_dir> <icon_dir> 64
  py tools/film/make_institute_data.py <out_dir> <icon_dir>
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic as R  # noqa: E402

# The institute's own surfaces, next to the film's.
R.KINDS.update({
    "enamel": ((0.85, 0.85, 0.81), "plastic", 0.6, 0.6, 0.5, "chips"),
    "hazmat": ((0.46, 0.66, 0.20), "plastic", 0.6, 0.9, 0.4, None),
    "hazmat_dark": ((0.30, 0.46, 0.12), "plastic", 0.6, 0.9, 0.4, None),
    "vinyl_mattress": ((0.16, 0.34, 0.40), "leather", 0.6, 0.6, 0.4, None),
    "wrap": ((0.80, 0.83, 0.84), "plastic", 0.6, 0.5, 0.3, None),
    "folder_blue": ((0.08, 0.34, 0.64), "paper", 1.0, 0.4, 0.4, None),
    "liquid": ((0.30, 0.48, 0.34), "plastic", 0.3, 0.3, 0.0, None),
    "copper": ((0.62, 0.34, 0.18), "steel", 1.0, 1.0, 0.4, "tarnish"),
    "rubber": ((0.06, 0.06, 0.06), "black_plastic", 0.6, 1.0, 0.2, None),
    "epoxy": ((0.10, 0.11, 0.11), "black_plastic", 0.6, 0.8, 0.3, None),
    "hazard_yellow": ((0.86, 0.68, 0.10), "plastic", 0.6, 0.6, 0.5, "chips"),
    "machine": ((0.30, 0.32, 0.34), "plastic", 0.6, 0.7, 0.4, "chips"),
    "seat_blue": ((0.20, 0.30, 0.44), "leather", 0.6, 0.7, 0.4, None),
})

import make_film_props as F  # noqa: E402  (after the kinds: it builds materials on reset)
from make_film_props import D90, box, cyl, export, group, panel, reset, sphere, torus  # noqa: E402


def rad(a):
    return math.radians(a)


def wheel(m, loc, r=0.05, rot=(0, D90, 0)):
    return cyl("Caster", loc, r, 0.035, m["rubber"], rot=rot, verts=12)


# ============================================================================================ receiving room, medical

def build_gurney():
    m = reset()
    cy = -0.5
    box("Deck", (0, cy, 0.62), (0.62, 1.9, 0.05), m["chrome"], 0.01)
    box("Mattress", (0, cy + 0.12, 0.69), (0.58, 1.56, 0.09), m["vinyl_mattress"], 0.04)
    box("Back", (0, cy - 0.72, 0.84), (0.58, 0.5, 0.08), m["vinyl_mattress"], 0.04, rot=(rad(-38), 0, 0))
    box("Sheet", (0, cy + 0.25, 0.745), (0.6, 1.2, 0.02), m["sheet"], 0.01)
    box("Pillow", (0, cy - 0.78, 0.92), (0.42, 0.26, 0.08), m["sheet"], 0.03, rot=(rad(-38), 0, 0))
    for sx in (-1, 1):
        cyl("Rail", (sx * 0.32, cy + 0.1, 0.84), 0.012, 1.1, m["chrome"], rot=(D90, 0, 0), verts=6)
        for py in (-0.4, 0.6):
            cyl("RailPost", (sx * 0.32, cy + py, 0.74), 0.01, 0.2, m["chrome"], verts=6)
        for sy in (-1, 1):
            cyl("Leg", (sx * 0.25, cy + sy * 0.8, 0.34), 0.02, 0.56, m["chrome"], verts=8)
            wheel(m, (sx * 0.25, cy + sy * 0.8, 0.05))
        cyl("Low", (sx * 0.25, cy, 0.14), 0.014, 1.6, m["chrome"], rot=(D90, 0, 0), verts=6)
    export("inst_gurney", (1, 1, 2))


def build_wrapped_equipment():
    """Something tall on wheels (an X-ray stand) wrapped in plastic sheeting and tape, pushed into a corner."""
    m = reset()
    box("Base", (0, 0, 0.1), (0.62, 0.52, 0.08), m["enamel"], 0.01)
    for sx in (-1, 1):
        for sy in (-1, 1):
            wheel(m, (sx * 0.26, sy * 0.21, 0.045))
    box("Wrap", (0, 0, 0.9), (0.6, 0.5, 1.5), m["wrap"], 0.09)
    box("WrapTop", (0.05, 0.02, 1.68), (0.5, 0.44, 0.22), m["wrap"], 0.08, rot=(0, rad(8), rad(6)))
    box("Lump", (0.18, 0.12, 1.25), (0.28, 0.3, 0.3), m["wrap"], 0.1, rot=(rad(10), 0, rad(20)))
    box("Fold", (-0.24, 0.2, 0.6), (0.12, 0.14, 0.9), m["wrap"], 0.05, rot=(0, 0, rad(25)))
    for z in (0.45, 1.05, 1.55):
        box("Tape", (0, 0, z), (0.63, 0.53, 0.05), m["plastic"], 0.01)
    panel("Label", (0, 0.27, 1.05), 0.2, 0.2, R.face("inst_biohazard"), facing="front")
    export("inst_wrapped_equipment", (1, 2, 1))


def build_interview_table():
    m = reset()
    cx = 0.5
    box("Top", (cx, 0, 0.74), (1.6, 0.8, 0.035), m["grey_laminate"], 0.006)
    box("Apron", (cx, 0, 0.7), (1.5, 0.7, 0.06), m["chrome"], 0.004)
    for sx in (-1, 1):
        for sy in (-1, 1):
            box("Leg", (cx + sx * 0.74, sy * 0.34, 0.36), (0.04, 0.04, 0.7), m["chrome"], 0.004)
    box("Folder", (cx + 0.15, 0.05, 0.765), (0.32, 0.24, 0.015), m["folder_blue"], 0.002, rot=(0, 0, rad(8)))
    box("Papers", (cx + 0.15, 0.05, 0.775), (0.28, 0.21, 0.004), m["white"], 0.0, rot=(0, 0, rad(4)))
    cyl("Pen", (cx + 0.4, 0.0, 0.765), 0.006, 0.14, m["black_plastic"], rot=(0, D90, rad(30)), verts=6)
    cyl("Cup", (cx - 0.45, -0.15, 0.81), 0.04, 0.1, m["white"], verts=12)
    export("inst_interview_table", (2, 1, 1))


def stacking_chair(m, loc=(0, 0, 0), rot=(0, 0, 0)):
    p = [box("Seat", (0, 0.02, 0.45), (0.44, 0.42, 0.04), m["grey_plastic"], 0.02),
         box("Back", (0, -0.2, 0.74), (0.42, 0.04, 0.3), m["grey_plastic"], 0.02, rot=(rad(-8), 0, 0))]
    for sx in (-1, 1):
        p.append(cyl("FrontLeg", (sx * 0.2, 0.18, 0.22), 0.012, 0.44, m["chrome"], verts=6))
        p.append(cyl("BackLeg", (sx * 0.2, -0.18, 0.4), 0.012, 0.8, m["chrome"], verts=6, rot=(rad(-6), 0, 0)))
    return group(p, loc, rot)


def build_stacking_chair():
    m = reset()
    stacking_chair(m)
    export("inst_stacking_chair", (1, 1, 1))


def build_waiting_chairs():
    m = reset()
    box("Beam", (1.0, -0.05, 0.3), (2.7, 0.06, 0.05), m["chrome"], 0.005)
    for x in (-0.25, 2.25):
        box("Foot", (x, -0.05, 0.15), (0.05, 0.5, 0.04), m["chrome"], 0.005)
        box("Post", (x, -0.05, 0.22), (0.04, 0.04, 0.16), m["chrome"], 0.004)
    for i in range(3):
        x = float(i)
        box("Seat", (x, 0.03, 0.44), (0.72, 0.46, 0.08), m["seat_blue"], 0.03)
        box("Back", (x, -0.21, 0.74), (0.72, 0.06, 0.42), m["seat_blue"], 0.03, rot=(rad(-10), 0, 0))
        box("Shell", (x, -0.25, 0.73), (0.7, 0.02, 0.4), m["dark"], 0.005, rot=(rad(-10), 0, 0))
    for x in (0.5, 1.5):
        box("Arm", (x, 0.0, 0.62), (0.05, 0.42, 0.03), m["chrome"], 0.005)
        box("ArmPost", (x, 0.15, 0.52), (0.03, 0.03, 0.2), m["chrome"], 0.003)
    export("inst_waiting_chairs", (3, 1, 1))


def build_xray_viewer():
    m = reset()
    cx = 0.5
    box("Housing", (cx, -0.46, 0.72), (1.7, 0.08, 0.66), m["enamel"], 0.01)
    box("Frame", (cx, -0.418, 0.72), (1.62, 0.01, 0.58), m["chrome"], 0.0)
    for x in (cx - 0.4, cx + 0.4):
        panel("Film", (x, -0.411, 0.73), 0.62, 0.54, R.face("inst_xray", contrast=0.1, grime=0.05), facing="front")
    box("Switch", (cx + 0.78, -0.415, 0.46), (0.05, 0.02, 0.08), m["dark"], 0.0)
    export("inst_xray_viewer", (2, 1, 1))


def build_iv_stand():
    m = reset()
    for a in range(5):
        ang = rad(a * 72)
        box("Foot", (math.cos(ang) * 0.14, math.sin(ang) * 0.14, 0.07), (0.28, 0.03, 0.02), m["chrome"], 0.003, rot=(0, 0, ang))
        wheel(m, (math.cos(ang) * 0.27, math.sin(ang) * 0.27, 0.035), r=0.03)
    cyl("Pole", (0, 0, 1.0), 0.014, 1.9, m["chrome"], verts=8)
    cyl("Hook", (0, 0, 1.92), 0.008, 0.36, m["chrome"], rot=(0, D90, 0), verts=6)
    box("Bag", (0.16, 0, 1.68), (0.14, 0.05, 0.24), m["wrap"], 0.03)
    box("Fluid", (0.16, 0.006, 1.62), (0.12, 0.045, 0.12), m["liquid"], 0.02)
    cyl("Line", (0.16, 0, 1.2), 0.004, 0.72, m["wrap"], verts=4)
    export("inst_iv_stand", (1, 2, 1))


def build_wheelchair():
    m = reset()
    box("Seat", (0, 0.0, 0.5), (0.46, 0.44, 0.04), m["rubber"], 0.01)
    box("Back", (0, -0.24, 0.78), (0.46, 0.03, 0.4), m["rubber"], 0.01, rot=(rad(-6), 0, 0))
    for sx in (-1, 1):
        torus("Wheel", (sx * 0.29, -0.05, 0.3), 0.29, 0.02, m["rubber"], rot=(0, D90, 0), segs=24, minor_segs=6)
        torus("Rim", (sx * 0.31, -0.05, 0.3), 0.26, 0.008, m["chrome"], rot=(0, D90, 0), segs=24, minor_segs=4)
        cyl("Hub", (sx * 0.29, -0.05, 0.3), 0.03, 0.05, m["chrome"], rot=(0, D90, 0), verts=8)
        wheel(m, (sx * 0.2, 0.28, 0.05), r=0.05)
        cyl("Frame", (sx * 0.23, 0.0, 0.5), 0.012, 0.6, m["chrome"], rot=(D90, 0, 0), verts=6)
        cyl("Upright", (sx * 0.23, -0.26, 0.75), 0.012, 0.62, m["chrome"], verts=6)
        cyl("Handle", (sx * 0.23, -0.34, 1.04), 0.016, 0.14, m["rubber"], rot=(D90, 0, 0), verts=6)
        box("Footrest", (sx * 0.12, 0.33, 0.14), (0.18, 0.12, 0.015), m["dark"], 0.003)
        cyl("Hanger", (sx * 0.2, 0.3, 0.32), 0.01, 0.38, m["chrome"], rot=(rad(20), 0, 0), verts=6)
        box("Armrest", (sx * 0.25, -0.02, 0.72), (0.05, 0.36, 0.04), m["rubber"], 0.01)
    export("inst_wheelchair", (1, 1, 1))


def build_water_fountain():
    m = reset()
    box("Basin", (0, -0.32, 0.86), (0.48, 0.36, 0.14), m["chrome"], 0.02)
    box("Skirt", (0, -0.4, 0.62), (0.4, 0.2, 0.36), m["chrome"], 0.01)
    box("Plate", (0, -0.49, 0.75), (0.5, 0.02, 0.7), m["chrome"], 0.005)
    cyl("Bubbler", (0, -0.26, 0.95), 0.02, 0.05, m["chrome"], verts=8)
    box("Button", (0, -0.13, 0.86), (0.08, 0.02, 0.05), m["chrome"], 0.004)
    export("inst_water_fountain", (1, 1, 1))


# ============================================================================================ decontamination, suits

def build_decon_shower():
    m = reset()
    box("Grate", (0, 0, 0.02), (0.86, 0.86, 0.04), m["dark"], 0.004)
    for sx in (-1, 1):
        for sy in (-1, 1):
            box("Post", (sx * 0.42, sy * 0.42, 1.05), (0.05, 0.05, 2.1), m["hazard_yellow"], 0.004)
    for sy in (-1, 1):
        box("Top", (0, sy * 0.42, 2.08), (0.9, 0.05, 0.05), m["hazard_yellow"], 0.004)
    for sx in (-1, 1):
        box("TopX", (sx * 0.42, 0, 2.08), (0.05, 0.9, 0.05), m["hazard_yellow"], 0.004)
    torus("Ring", (0, 0, 1.98), 0.28, 0.02, m["chrome"], segs=20, minor_segs=4)
    for a in range(8):
        ang = rad(a * 45)
        cyl("Nozzle", (math.cos(ang) * 0.28, math.sin(ang) * 0.28, 1.95), 0.012, 0.05, m["chrome"], verts=6)
    cyl("Feed", (0.3, 0, 2.03), 0.02, 0.2, m["chrome"], rot=(0, D90, 0), verts=6)
    for sx in (-1, 1):
        for i in range(6):
            box("Strip", (sx * 0.43, -0.35 + i * 0.14, 1.1), (0.01, 0.13, 1.9), m["wrap"], 0.0)
    panel("Label", (0, -0.425, 1.6), 0.26, 0.26, R.face("inst_biohazard"), facing="front")
    export("inst_decon_shower", (1, 2, 1))


def hazmat_suit(m, loc, lean=0.0):
    # An empty suit hangs: a sagging torso, the hood folded forward, sleeves and legs gone slack.
    p = [sphere("Torso", (0, 0, 1.2), 0.24, m["hazmat"], scale=(0.95, 0.62, 1.45), segs=14),
         sphere("Hips", (0, 0, 0.92), 0.2, m["hazmat"], scale=(1.0, 0.7, 0.6), segs=12),
         sphere("Hood", (0, 0.03, 1.64), 0.17, m["hazmat"], scale=(1.0, 0.95, 1.1), segs=12),
         box("Visor", (0, 0.17, 1.63), (0.22, 0.03, 0.15), m["screen"], 0.04, rot=(rad(-12), 0, 0)),
         box("Zip", (0, 0.15, 1.15), (0.03, 0.01, 0.5), m["hazmat_dark"], 0.0),
         torus("Belt", (0, 0, 0.98), 0.2, 0.025, m["hazmat_dark"], rot=(0, 0, 0), segs=16, minor_segs=4)]
    for sx in (-1, 1):
        p.append(cyl("Arm", (sx * 0.25, 0.02, 1.08), 0.07, 0.6, m["hazmat"], rot=(rad(4), rad(sx * 8), 0), verts=10, r2=0.055))
        p.append(sphere("Glove", (sx * 0.27, 0, 0.74), 0.06, m["hazmat_dark"], segs=8))
        p.append(cyl("Leg", (sx * 0.1, 0, 0.55), 0.075, 0.66, m["hazmat"], verts=10))
        p.append(box("Boot", (sx * 0.1, 0.03, 0.15), (0.12, 0.22, 0.16), m["rubber"], 0.03))
    p.append(cyl("Hanger", (0, 0, 1.84), 0.006, 0.12, m["chrome"], verts=4))
    return group(p, loc, (rad(lean), 0, 0))


def build_hazmat_rack():
    m = reset()
    cx = 0.5
    for x in (cx - 0.88, cx + 0.88):
        box("Post", (x, -0.3, 0.95), (0.04, 0.04, 1.9), m["chrome"], 0.004)
        box("Foot", (x, -0.2, 0.02), (0.06, 0.5, 0.03), m["chrome"], 0.004)
    cyl("Rail", (cx, -0.3, 1.9), 0.016, 1.8, m["chrome"], rot=(0, D90, 0), verts=8)
    for i, x in enumerate((cx - 0.55, cx, cx + 0.55)):
        hazmat_suit(m, (x, -0.3, 0.02 if i != 1 else 0.0), lean=(3, -2, 4)[i])
    export("inst_hazmat_rack", (2, 2, 1))


# ============================================================================================ offices, control, labs

def crt(m, loc, size=0.42, shell="almond_plastic", face="inst_crt_text"):
    s = size
    p = [box("CRT", (0, -s * 0.12, s / 2), (s, s * 0.9, s * 0.84), m[shell], 0.03),
         box("CRTBack", (0, -s * 0.6, s * 0.46), (s * 0.68, s * 0.4, s * 0.62), m[shell], 0.03),
         box("Bezel", (0, s * 0.33, s * 0.52), (s * 0.86, 0.01, s * 0.7), m["dark"], 0.0),
         panel("Screen", (0, s * 0.33 + 0.008, s * 0.53), s * 0.74, s * 0.56, R.face(face, contrast=0.1, grime=0.05), facing="front")]
    return group(p, loc)


def build_monitor_console():
    m = reset()
    cx = 0.5
    box("Desk", (cx, -0.05, 0.38), (1.8, 0.8, 0.76), m["grey_plastic"], 0.01)
    box("Slope", (cx, 0.2, 0.82), (1.7, 0.36, 0.06), m["dark"], 0.005, rot=(rad(25), 0, 0))
    for i in range(14):
        box("Key", (cx - 0.7 + i * 0.1, 0.22, 0.85), (0.05, 0.04, 0.02), (m["red"], m["yellow"], m["white"])[i % 3], 0.0,
            rot=(rad(25), 0, 0))
    box("Shelf", (cx, -0.22, 1.32), (1.8, 0.5, 0.04), m["grey_plastic"], 0.005)
    for x in (cx - 0.88, cx + 0.88):
        box("Upright", (x, -0.22, 1.05), (0.04, 0.5, 0.56), m["grey_plastic"], 0.004)
    crt(m, (cx - 0.5, -0.15, 0.88))
    crt(m, (cx + 0.5, -0.15, 0.88))
    crt(m, (cx, -0.2, 1.34), size=0.46, shell="grey_plastic")
    box("Keyboard", (cx, 0.3, 0.79), (0.46, 0.16, 0.03), m["almond_plastic"], 0.005)
    box("Phone", (cx + 0.75, 0.25, 0.8), (0.18, 0.2, 0.07), m["dark"], 0.02)
    export("inst_monitor_console", (2, 2, 1))


def build_office_desk():
    m = reset()
    cx = 0.5
    box("Top", (cx, 0, 0.74), (1.6, 0.76, 0.04), m["grey_laminate"], 0.005)
    box("Pedestal", (cx + 0.55, 0, 0.36), (0.42, 0.7, 0.72), m["beige_metal"], 0.006)
    for i in range(3):
        box("Drawer", (cx + 0.55, 0.355, 0.15 + i * 0.22), (0.38, 0.01, 0.19), m["beige_metal"], 0.003)
        box("Pull", (cx + 0.55, 0.365, 0.2 + i * 0.22), (0.12, 0.01, 0.02), m["chrome"], 0.0)
    box("Modesty", (cx - 0.2, -0.33, 0.45), (1.0, 0.03, 0.5), m["beige_metal"], 0.004)
    for sy in (-1, 1):
        box("Leg", (cx - 0.76, sy * 0.33, 0.36), (0.04, 0.04, 0.72), m["beige_metal"], 0.004)
    crt(m, (cx - 0.3, -0.08, 0.76), size=0.4)
    box("Keyboard", (cx - 0.3, 0.25, 0.775), (0.44, 0.15, 0.025), m["almond_plastic"], 0.005)
    box("Papers", (cx + 0.4, 0.1, 0.765), (0.3, 0.22, 0.01), m["white"], 0.0, rot=(0, 0, rad(-10)))
    box("Folder", (cx + 0.42, 0.08, 0.775), (0.3, 0.23, 0.012), m["folder_blue"], 0.002, rot=(0, 0, rad(12)))
    cyl("Mug", (cx + 0.15, 0.22, 0.8), 0.04, 0.09, m["white"], verts=10)
    export("inst_office_desk", (2, 1, 1))


def build_file_cabinet():
    m = reset()
    F.file_cabinet(m)
    panel("Decal", (0, 0.316, 1.22), 0.16, 0.16, R.face("inst_decal"), facing="front")
    export("inst_file_cabinet", (1, 2, 1))


def build_tape_drive():
    m = reset()
    box("Cabinet", (0, -0.05, 0.92), (0.8, 0.7, 1.84), m["almond_plastic"], 0.01)
    box("Window", (0, 0.305, 1.32), (0.66, 0.01, 0.62), m["screen"], 0.0)
    for x in (-0.17, 0.17):
        cyl("Reel", (x, 0.3, 1.36), 0.15, 0.03, m["black_plastic"], rot=(D90, 0, 0), verts=20)
        cyl("Hub", (x, 0.32, 1.36), 0.04, 0.02, m["chrome"], rot=(D90, 0, 0), verts=10)
    box("Panel", (0, 0.305, 0.86), (0.66, 0.01, 0.28), m["grey_plastic"], 0.0)
    for i in range(8):
        box("Lamp", (-0.28 + i * 0.08, 0.315, 0.92), (0.04, 0.01, 0.03), (m["red"], m["lamp_glow"], m["green"])[i % 3], 0.0)
    for i in range(4):
        box("Switch", (-0.24 + i * 0.16, 0.315, 0.8), (0.05, 0.02, 0.04), m["dark"], 0.0)
    box("Vent", (0, 0.305, 0.3), (0.6, 0.01, 0.3), m["dark"], 0.0)
    panel("Decal", (0.25, 0.312, 1.72), 0.14, 0.14, R.face("inst_decal"), facing="front")
    export("inst_tape_drive", (1, 2, 1))


def build_lab_bench():
    m = reset()
    cx = 0.5
    box("Top", (cx, 0, 0.9), (1.8, 0.76, 0.05), m["epoxy"], 0.005)
    box("Base", (cx, -0.02, 0.44), (1.76, 0.7, 0.86), m["enamel"], 0.006)
    for i in range(4):
        box("Door", (cx - 0.66 + i * 0.44, 0.335, 0.44), (0.4, 0.01, 0.76), m["enamel"], 0.003)
        box("Pull", (cx - 0.5 + i * 0.44, 0.345, 0.7), (0.02, 0.01, 0.12), m["chrome"], 0.0)
    # A microscope, glassware, a centrifuge, a notebook.
    box("ScopeBase", (cx - 0.55, 0.05, 0.94), (0.16, 0.2, 0.04), m["dark"], 0.01)
    cyl("ScopeArm", (cx - 0.55, -0.03, 1.08), 0.025, 0.26, m["dark"], rot=(rad(15), 0, 0), verts=8)
    cyl("ScopeTube", (cx - 0.55, 0.04, 1.2), 0.025, 0.16, m["chrome"], rot=(rad(-30), 0, 0), verts=8)
    for i, x in enumerate((cx - 0.15, cx - 0.02, cx + 0.1)):
        cyl("Beaker", (x, 0.1, 0.99), 0.045, 0.14, m["glass"], verts=10)
        cyl("Fluid", (x, 0.1, 0.96), 0.042, 0.07, (m["liquid"], m["red"], m["folder_blue"])[i], verts=10)
    cyl("Centrifuge", (cx + 0.5, -0.05, 1.0), 0.16, 0.16, m["white"], verts=16)
    cyl("Lid", (cx + 0.5, -0.05, 1.09), 0.12, 0.02, m["grey_plastic"], verts=16)
    box("Notebook", (cx + 0.15, -0.2, 0.93), (0.22, 0.3, 0.015), m["folder_blue"], 0.002, rot=(0, 0, rad(-15)))
    export("inst_lab_bench", (2, 1, 1))


def build_specimen_tank():
    m = reset()
    cyl("Base", (0, 0, 0.14), 0.42, 0.28, m["steel"], verts=20)
    box("Stripe", (0, 0.0, 0.24), (0.86, 0.86, 0.04), m["hazard_yellow"], 0.004)
    cyl("Fluid", (0, 0, 0.98), 0.34, 1.4, m["liquid"], verts=24)
    panel("Window", (0, 0.345, 0.98), 0.44, 1.3, R.face("inst_specimen", contrast=0.1, grime=0.05), facing="front")
    for a in range(4):
        ang = rad(45 + a * 90)
        cyl("Strut", (math.cos(ang) * 0.36, math.sin(ang) * 0.36, 0.98), 0.02, 1.42, m["steel"], verts=6)
    cyl("Cap", (0, 0, 1.76), 0.4, 0.16, m["steel"], verts=20)
    for x in (-0.12, 0.12):
        cyl("Pipe", (x, -0.1, 1.9), 0.04, 0.14, m["chrome"], verts=8)
    panel("Label", (0, 0.42, 0.14), 0.16, 0.16, R.face("inst_biohazard"), facing="front")
    export("inst_specimen_tank", (1, 2, 1))


# ============================================================================================ B1

def build_deployment_gate():
    """The machine that sends people in: a heavy steel doorway ringed with coils, cables to the floor, and through it,
    a yellow room going on too long."""
    m = reset()
    cx, cy = 1.0, -0.5
    for x in (cx - 1.2, cx + 1.2):
        box("Pillar", (x, cy, 1.9), (0.5, 0.9, 3.8), m["machine"], 0.02)
        box("Hazard", (x, cy, 0.25), (0.52, 0.92, 0.5), m["hazard_yellow"], 0.01)
    box("Lintel", (cx, cy, 3.7), (2.9, 0.9, 0.5), m["machine"], 0.02)
    box("Sill", (cx, cy, 0.05), (1.9, 0.9, 0.1), m["dark"], 0.01)
    # Coils round the opening, front and back.
    for sy in (0.38, -1.38):
        for z in (0.7, 1.4, 2.1, 2.8):
            for x in (cx - 0.95, cx + 0.95):
                torus("Coil", (x, cy + sy * 0.5 + 0.2, z), 0.16, 0.05, m["copper"], rot=(D90, 0, 0), segs=16, minor_segs=6)
        torus("Arch", (cx, cy + sy * 0.5 + 0.2, 3.35), 0.45, 0.06, m["copper"], rot=(D90, 0, 0), segs=20, minor_segs=6)
    # Through the doorway: Level 0, as the cameras show it.
    panel("Through", (cx, cy, 1.65), 1.9, 3.2, R.face("inst_threshold", contrast=0.05, grime=0.02), facing="front")
    panel("ThroughBack", (cx, cy - 0.01, 1.65), 1.9, 3.2, R.face("inst_threshold", contrast=0.05, grime=0.02), facing="back")
    # Cables down the sides to the floor, junction boxes, the decal.
    for x in (cx - 1.5, cx + 1.5):
        for i in range(3):
            cyl("Cable", (x, cy - 0.2 + i * 0.18, 1.6), 0.035, 3.2, m["rubber"], verts=8)
        box("Junction", (x, cy + 0.1, 0.5), (0.2, 0.3, 0.4), m["grey_plastic"], 0.01)
    panel("Decal", (cx - 1.2, cy + 0.455, 2.6), 0.3, 0.3, R.face("inst_decal"), facing="front")
    panel("Bio", (cx + 1.2, cy + 0.455, 2.6), 0.3, 0.3, R.face("inst_biohazard"), facing="front")
    export("inst_deployment_gate", (3, 4, 2))


def main():
    for build in (build_gurney, build_wrapped_equipment, build_interview_table, build_stacking_chair, build_waiting_chairs,
                  build_xray_viewer, build_iv_stand, build_wheelchair, build_water_fountain, build_decon_shower,
                  build_hazmat_rack, build_monitor_console, build_office_desk, build_file_cabinet, build_tape_drive,
                  build_lab_bench, build_specimen_tank, build_deployment_gate):
        build()
    with open(os.path.join(R.OUT_DIR, "sizes.txt"), "a" if F.ONLY else "w", encoding="utf8") as f:
        for name, (w, h, d) in R.SIZES.items():
            f.write(f"{name} {w} {h} {d}\n")
    print("INSTITUTE PROPS:", len(R.SIZES))


main()
