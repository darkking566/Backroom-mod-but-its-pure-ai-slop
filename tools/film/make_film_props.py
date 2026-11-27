"""The film's Level 0 (A24's Backrooms, 2026): a 1991 bargain furniture store the Backrooms copied without understanding it.

Modelled from the film's stills: overstuffed rolled-arm sofas and recliners in tan, mauve, chocolate and teal velvet,
price tags on every back, honey-oak armoires and entertainment centres, ladder-back chairs, rolled carpets, chandeliers,
hanging SALE signs, and the piles of chairs, ottomans and cabinets that reach the ceiling. Built the way Gemini's
tools/make_blender_props.py builds props (same axes; OBJ), with a baked texture per prop (realistic.py).

Axes (Blender -> OBJ forward -Z, up Y -> Minecraft): the master block's bottom centre is (0, 0, 0); a prop W wide,
H tall and D deep spans X -0.5..W-0.5, Z 0..H, Y -(D-0.5)..0.5; its front looks toward +Y (Minecraft north).

Run (from the repo root; the steps are listed in tools/film/make_film_data.py):
  py tools/film/prints.py tools/film/prints
  blender -b --python tools/film/make_film_props.py -- <out_dir> [preview_dir] [only,names]
"""
import math
import os
import random
import sys

import bmesh
import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic as R  # noqa: E402

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
R.OUT_DIR = os.path.abspath(ARGS[0]) if ARGS else os.path.abspath("film_out")
R.PREVIEW = os.path.abspath(ARGS[1]) if len(ARGS) > 1 and ARGS[1] else None
ONLY = set(ARGS[2].split(",")) if len(ARGS) > 2 and ARGS[2] else None
os.makedirs(R.OUT_DIR, exist_ok=True)
if R.PREVIEW:
    os.makedirs(R.PREVIEW, exist_ok=True)
D90 = math.pi / 2


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    return R.materials()


def _smooth(o):
    """Smooth sides, flat caps: shading follows the angle between faces."""
    for other in bpy.context.selected_objects:
        other.select_set(False)
    o.select_set(True)
    bpy.context.view_layer.objects.active = o
    bpy.ops.object.shade_smooth_by_angle(angle=math.radians(50))


def barrel(name, centre, r_out, thick, z0, h_arm, h_back, m, t0=math.radians(30), t1=math.radians(-210), n=28):
    """A curved chair back that wraps round from one arm to the other, lower at the arms, highest at the back."""
    bm = bmesh.new()
    rings = []
    for i in range(n + 1):
        t = t0 + (t1 - t0) * i / n
        h = h_arm + (h_back - h_arm) * max(0.0, -math.sin(t)) ** 0.7
        ring = [bm.verts.new((centre[0] + math.cos(t) * r, centre[1] + math.sin(t) * r, z))
                for r, z in ((r_out, z0), (r_out, z0 + h), (r_out - thick, z0 + h), (r_out - thick, z0))]
        rings.append(ring)
    for a, b in zip(rings, rings[1:]):
        for k in range(4):
            bm.faces.new((a[k], b[k], b[(k + 1) % 4], a[(k + 1) % 4]))
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    me.uv_layers.new(name="UVMap")
    bev = o.modifiers.new("Bevel", "BEVEL")
    bev.width = min(0.05, thick * 0.45)
    bev.segments = 3
    o.data.materials.append(m)
    _smooth(o)
    return o


def box(name, loc, size, m, bevel=0.012, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    bpy.ops.object.transform_apply(scale=True)
    if bevel > 0:
        b = o.modifiers.new("Bevel", "BEVEL")
        b.width = min(bevel, min(size) * 0.49)
        # Soft things (cushions, mattresses, pillows) get rounded edges; hard ones a single chamfer.
        b.segments = 3 if bevel >= 0.03 else 1
    o.data.materials.append(m)
    return o


def cyl(name, loc, r, depth, m, rot=(0, 0, 0), verts=12, r2=None):
    if r2 is None:
        bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    else:
        bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, radius2=r2, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.data.materials.append(m)
    _smooth(o)
    return o


def sphere(name, loc, r, m, scale=(1, 1, 1), segs=10):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segs, ring_count=max(6, segs // 2), radius=r, location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    bpy.ops.object.transform_apply(scale=True)
    o.data.materials.append(m)
    _smooth(o)
    return o


def torus(name, loc, major, minor, m, rot=(0, 0, 0), segs=24, minor_segs=6):
    bpy.ops.mesh.primitive_torus_add(major_segments=segs, minor_segments=minor_segs, major_radius=major, minor_radius=minor,
                                     location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.data.materials.append(m)
    _smooth(o)
    return o


def panel(name, loc, w, h, m, facing="front", rot=None):
    """A flat picture: the image's top is up and it reads left to right for someone facing it."""
    bpy.ops.mesh.primitive_plane_add(size=1.0, location=(0, 0, 0))
    o = bpy.context.active_object
    o.name = name
    o.scale = (w, h, 1)
    bpy.ops.object.transform_apply(scale=True)
    o.data.uv_layers[0].name = "src"
    o.rotation_euler = rot if rot is not None else {"front": (D90, 0, math.pi), "back": (D90, 0, 0), "up": (0, 0, 0)}[facing]
    o.location = loc
    o.data.materials.append(m)
    return o


def group(objs, loc=(0, 0, 0), rot=(0, 0, 0)):
    """Moves and turns a set of parts together (built around their own origin first)."""
    pivot = bpy.data.objects.new("pivot", None)
    bpy.context.scene.collection.objects.link(pivot)
    for o in objs:
        o.parent = pivot
    pivot.location = loc
    pivot.rotation_euler = rot
    bpy.context.view_layer.update()
    for o in objs:
        mw = o.matrix_world.copy()
        o.parent = None
        o.matrix_world = mw
    bpy.data.objects.remove(pivot)
    return objs


def tag(loc, file="price_tag", tilt=75, size=0.13):
    """A store price tag hanging off the top of a back, facing the aisle."""
    return panel("Tag", loc, size, size, R.face(file), rot=(math.radians(tilt), 0, math.pi))


def export(name, size):
    if ONLY is None or name in ONLY:
        R.export(name, size)


# ============================================================================================ furniture pieces

def sofa(m, fab, width=2.1, skirt=False, trim=None, feet="dark_wood", loc=(0, 0, 0), rot=(0, 0, 0), price=None):
    """The store's overstuffed sofa: rolled pillow arms, puffy seat and back cushions, bun feet or a skirt."""
    p = []
    d = 0.95
    lift = 0.0 if skirt else 0.07
    if skirt:
        # The skirt reaches the floor; the base sits a little above it so their bottoms never overlap.
        p.append(box("Skirt", (0, 0, 0.09), (width - 0.02, d, 0.18), fab, 0.02))
        p.append(box("Base", (0, 0, 0.18), (width - 0.08, d - 0.06, 0.28), fab, 0.04))
    else:
        p.append(box("Base", (0, 0, lift + 0.16), (width - 0.08, d - 0.06, 0.32), fab, 0.04))
    if skirt:
        pass
    else:
        for sx in (-1, 1):
            for sy in (-1, 1):
                p.append(sphere("Bun", (sx * (width / 2 - 0.12), sy * (d / 2 - 0.1), 0.04), 0.045, m[feet], scale=(1, 1, 0.9), segs=8))
    for sx in (-1, 1):
        ax = sx * (width / 2 - 0.13)
        p.append(box("Arm", (ax, 0.02, lift + 0.34), (0.24, d - 0.04, 0.38), fab, 0.05))
        p.append(cyl("Roll", (ax + sx * 0.02, 0.03, lift + 0.56), 0.15, d - 0.02, fab, rot=(D90, 0, 0), verts=16))
        if trim:
            p.append(box("ArmTrim", (ax + sx * 0.02, d / 2 + 0.01, lift + 0.36), (0.18, 0.03, 0.2), m[trim], 0.01))
    n = 3 if width > 1.7 else 2
    inner = width - 0.52
    cw = inner / n
    for i in range(n):
        cx = -inner / 2 + cw * (i + 0.5)
        p.append(box("Seat", (cx, 0.08, lift + 0.40), (cw - 0.02, 0.74, 0.17), fab, 0.07))
        p.append(box("BackPillow", (cx, -0.24, lift + 0.66), (cw - 0.02, 0.26, 0.44), fab, 0.09, rot=(math.radians(12), 0, 0)))
    p.append(box("BackFrame", (0, -0.38, lift + 0.56), (width - 0.1, 0.2, 0.6), fab, 0.06))
    if trim:
        p.append(box("FrontTrim", (0, d / 2 - 0.01, lift + 0.05), (width - 0.1, 0.04, 0.07), m[trim], 0.01))
    if price:
        p.append(tag((width / 2 - 0.45, -0.26, lift + 0.86), price))
    return group(p, loc, rot)


def recliner(m, fab, pillow=True, loc=(0, 0, 0), rot=(0, 0, 0), price="price_tag"):
    """A 90s recliner: a skirted box, pillow arms, a back of stacked pillows (or one cushion), the lever on the side."""
    p = [box("Base", (0, 0, 0.20), (0.86, 0.86, 0.36), fab, 0.05),
         box("Seat", (0, 0.08, 0.46), (0.52, 0.66, 0.16), fab, 0.07),
         box("Back", (0, -0.36, 0.62), (0.70, 0.18, 0.70), fab, 0.07, rot=(math.radians(8), 0, 0)),
         box("Footrest", (0, 0.44, 0.17), (0.52, 0.04, 0.26), fab, 0.02),
         box("Lever", (0.465, 0.12, 0.38), (0.02, 0.05, 0.12), m["dark_wood"], 0.005)]
    for sx in (-1, 1):
        p.append(box("Arm", (sx * 0.36, 0.0, 0.41), (0.2, 0.84, 0.25), fab, 0.07))
        p.append(cyl("ArmRoll", (sx * 0.37, 0.0, 0.6), 0.11, 0.84, fab, rot=(D90, 0, 0), verts=14))
    if pillow:
        for i, z in enumerate((0.58, 0.75, 0.90)):
            p.append(box("Pillow", (0, -0.24 - i * 0.025, z), (0.54, 0.2, 0.19), fab, 0.09, rot=(math.radians(8), 0, 0)))
    else:
        p.append(box("BackCushion", (0, -0.24, 0.74), (0.54, 0.18, 0.46), fab, 0.09, rot=(math.radians(8), 0, 0)))
    if price:
        p.append(tag((0.14, -0.33, 0.93), price))
    return group(p, loc, rot)


def swivel_chair(m, fab, loc=(0, 0, 0), rot=(0, 0, 0), price="price_tag"):
    """The teal velvet swivel chairs: a skirted barrel with a button-tufted back that curls into the arms."""
    p = [box("Skirt", (0, 0.02, 0.13), (0.8, 0.78, 0.26), fab, 0.03),
         box("Seat", (0, 0.1, 0.36), (0.56, 0.56, 0.16), fab, 0.07),
         barrel("Barrel", (0, 0.02), 0.41, 0.17, 0.24, 0.36, 0.68, fab)]
    for i in range(7):
        t = math.radians(-35 - i * 18.3)
        for z in (0.52, 0.70):
            p.append(sphere("Button", (math.cos(t) * 0.235, math.sin(t) * 0.235 + 0.02, z), 0.018, fab, segs=6))
    if price:
        p.append(tag((0.12, -0.24, 0.92), price))
    return group(p, loc, rot)


def wood_trim_chair(m, fab, loc=(0, 0, 0), rot=(0, 0, 0)):
    """A squarer armchair with oak caps on the fronts of its arms."""
    p = [box("Base", (0, 0, 0.22), (0.84, 0.84, 0.32), fab, 0.04),
         box("Seat", (0, 0.08, 0.44), (0.54, 0.64, 0.15), fab, 0.07),
         box("Back", (0, -0.34, 0.66), (0.74, 0.16, 0.64), fab, 0.07, rot=(math.radians(8), 0, 0)),
         box("BackCushion", (0, -0.22, 0.7), (0.56, 0.16, 0.42), fab, 0.08, rot=(math.radians(8), 0, 0))]
    for sx in (-1, 1):
        p.append(box("Arm", (sx * 0.34, 0, 0.46), (0.18, 0.82, 0.32), fab, 0.06))
        p.append(box("ArmCap", (sx * 0.34, 0.415, 0.48), (0.16, 0.03, 0.3), m["honey_oak"], 0.01))
        for sy in (-1, 1):
            p.append(box("Foot", (sx * 0.36, sy * 0.36, 0.03), (0.06, 0.06, 0.06), m["honey_oak"], 0.005))
    p.append(tag((0.14, -0.3, 0.96), "price_tag"))
    return group(p, loc, rot)


def accent_chair(m, fab, loc=(0, 0, 0), rot=(0, 0, 0)):
    p = [box("Seat", (0, 0.04, 0.34), (0.66, 0.66, 0.18), fab, 0.05),
         box("SeatCushion", (0, 0.1, 0.47), (0.5, 0.54, 0.12), fab, 0.06),
         box("Back", (0, -0.3, 0.68), (0.6, 0.14, 0.62), fab, 0.07, rot=(math.radians(10), 0, 0))]
    for sx in (-1, 1):
        p.append(box("Arm", (sx * 0.3, 0.02, 0.5), (0.12, 0.62, 0.18), fab, 0.05))
        p.append(cyl("ArmRoll", (sx * 0.33, 0.04, 0.6), 0.07, 0.6, fab, rot=(D90, 0, 0), verts=12))
        for sy in (-1, 1):
            p.append(cyl("Leg", (sx * 0.27, sy * 0.26, 0.12), 0.025, 0.24, m["dark_wood"], verts=8, r2=0.017))
    p.append(tag((0.15, -0.28, 0.95), "price_tag"))
    return group(p, loc, rot)


def ottoman_parts(m, fab, loc=(0, 0, 0), rot=(0, 0, 0), tufted=False, legs="dark_wood"):
    p = [box("Body", (0, 0, 0.24), (0.6, 0.46, 0.3), fab, 0.05)]
    for sx in (-1, 1):
        for sy in (-1, 1):
            p.append(cyl("Leg", (sx * 0.25, sy * 0.18, 0.045), 0.025, 0.09, m[legs], verts=8, r2=0.018))
    if tufted:
        for sx in (-1, 0, 1):
            for sy in (-1, 1):
                p.append(sphere("Button", (sx * 0.16, sy * 0.09, 0.39), 0.016, fab, segs=6))
    return group(p, loc, rot)


def ladderback(m, wood="honey_oak", seat="green_vinyl", loc=(0, 0, 0), rot=(0, 0, 0)):
    """The film's wooden ladder-back chair, a green vinyl pad on the seat."""
    p = []
    for sx in (-1, 1):
        p.append(cyl("FrontLeg", (sx * 0.2, 0.18, 0.23), 0.022, 0.46, m[wood], verts=10))
        p.append(cyl("BackPost", (sx * 0.19, -0.21, 0.5), 0.022, 1.0, m[wood], rot=(math.radians(6), 0, 0), verts=10))
        p.append(sphere("Finial", (sx * 0.19, -0.26, 1.0), 0.03, m[wood], segs=8))
        p.append(box("SideRail", (sx * 0.2, -0.01, 0.16), (0.02, 0.38, 0.02), m[wood], 0.0))
    p.append(box("SeatFrame", (0, 0, 0.46), (0.46, 0.44, 0.05), m[wood], 0.01))
    p.append(box("SeatPad", (0, 0.01, 0.5), (0.42, 0.4, 0.05), m[seat], 0.02))
    for i in range(4):
        z = 0.62 + i * 0.1
        p.append(box("Slat", (0, -0.21 - (z - 0.5) * 0.1, z), (0.38, 0.025, 0.06), m[wood], 0.008, rot=(math.radians(6), 0, 0)))
    for sy in (-1, 1):
        p.append(box("Stretcher", (0, sy * 0.18, 0.14), (0.38, 0.02, 0.02), m[wood], 0.0))
    return group(p, loc, rot)


def school_chair(m, colour="school_green", loc=(0, 0, 0), rot=(0, 0, 0)):
    p = [box("Seat", (0, 0.02, 0.46), (0.42, 0.40, 0.03), m[colour], 0.012),
         box("Back", (0, -0.21, 0.72), (0.40, 0.03, 0.2), m[colour], 0.012, rot=(math.radians(8), 0, 0))]
    for sx in (-1, 1):
        for sy in (-1, 1):
            p.append(cyl("Leg", (sx * 0.18, sy * 0.18, 0.22), 0.012, 0.44, m["chrome"], verts=8))
        p.append(cyl("BackPost", (sx * 0.18, -0.2, 0.63), 0.012, 0.38, m["chrome"], verts=8))
    return group(p, loc, rot)


def resin_chair(m, loc=(0, 0, 0), rot=(0, 0, 0)):
    """A white moulded plastic patio chair."""
    p = [box("Seat", (0, 0.02, 0.42), (0.48, 0.46, 0.04), m["resin"], 0.02),
         box("Back", (0, -0.22, 0.70), (0.48, 0.04, 0.52), m["resin"], 0.02, rot=(math.radians(12), 0, 0))]
    for i in range(4):
        p.append(box("Slot", (0, -0.198 + (0.04 + i * 0.1) * 0.21, 0.56 + i * 0.1), (0.36, 0.005, 0.025), m["dark"], 0.0,
                     rot=(math.radians(12), 0, 0)))
    for sx in (-1, 1):
        p.append(box("Arm", (sx * 0.25, 0.0, 0.62), (0.04, 0.44, 0.04), m["resin"], 0.012))
        p.append(box("FrontLeg", (sx * 0.22, 0.2, 0.21), (0.05, 0.05, 0.42), m["resin"], 0.01))
        p.append(box("BackLeg", (sx * 0.22, -0.18, 0.21), (0.05, 0.05, 0.44), m["resin"], 0.01, rot=(math.radians(-10), 0, 0)))
    return group(p, loc, rot)


def crt_parts(m, loc=(0, 0, 0), rot=(0, 0, 0), on=False, size=0.62, shell="grey_plastic"):
    s = size
    p = [box("CRT", (0, -s * 0.15, s / 2), (s, s * 0.9, s * 0.82), m[shell], 0.04),
         box("CRTBack", (0, -s * 0.62, s * 0.45), (s * 0.7, s * 0.4, s * 0.6), m[shell], 0.04),
         box("Screen", (0, s * 0.31, s * 0.52), (s * 0.74, 0.02, s * 0.58), m["screen_on" if on else "screen"], 0.0),
         box("Buttons", (s * 0.3, s * 0.31, s * 0.12), (s * 0.2, 0.02, s * 0.06), m["black_plastic"], 0.0)]
    return group(p, loc, rot)


def tape_parts(m, loc=(0, 0, 0), rot=(0, 0, 0)):
    """One VHS tape lying flat: 0.19 x 0.10 x 0.025 blocks."""
    p = [box("Tape", (0, 0, 0.0125), (0.19, 0.10, 0.025), m["black_plastic"], 0.003),
         box("Label", (0, 0.0505, 0.0125), (0.15, 0.002, 0.016), m["tape_label"], 0.0)]
    return group(p, loc, rot)


def clothes_parts(m, loc=(0, 0, 0), seed=0, count=9, spread=0.32):
    """A soft heap of clothes: squashed lumps, the middle higher than the edges."""
    rnd = random.Random(seed)
    p = []
    for i in range(count):
        colour = rnd.choice(["denim", "shirt", "sock", "mauve", "teal", "sheet", "navy", "oat"])
        r = spread * math.sqrt(rnd.random())
        a = rnd.uniform(0, math.tau)
        lift = (1 - r / spread) * 0.18 + rnd.uniform(0, 0.05)
        o = sphere(f"Cloth{i}", (math.cos(a) * r, math.sin(a) * r, 0.04 + lift), 0.16, m[colour],
                   scale=(rnd.uniform(0.9, 1.5), rnd.uniform(0.6, 1.1), rnd.uniform(0.25, 0.4)), segs=8)
        o.rotation_euler = (rnd.uniform(-0.3, 0.3), rnd.uniform(-0.3, 0.3), rnd.uniform(0, math.tau))
        p.append(o)
    return group(p, loc)


def mattress_parts(m, loc=(0, 0, 0), rot=(0, 0, 0), length=1.9, width=0.95):
    quilt = R.fabric("matelasse", tile=0.5, source="cotton", contrast=0.5, grime=0.6)
    p = [box("Mattress", (0, 0, 0.11), (width, length, 0.22), quilt, 0.05),
         box("Band", (0, 0, 0.11), (width + 0.006, length + 0.006, 0.07), m["cream"], 0.01)]
    return group(p, loc, rot)


def drawer_chest(m, wood, w, h, d, rows, cols=1, pulled=(), loc=(0, 0, 0), rot=(0, 0, 0)):
    """A box of drawers with brass bail pulls; some drawers can stand out."""
    p = [box("Carcass", (0, 0, h / 2), (w, d, h), m[wood], 0.01)]
    dh = (h - 0.08) / rows
    dw = (w - 0.06) / cols
    for r in range(rows):
        for c in range(cols):
            out = 0.22 if (r, c) in pulled else 0.0
            x, z = -w / 2 + 0.03 + dw * (c + 0.5), 0.05 + dh * (r + 0.5)
            p.append(box("Drawer", (x, d / 2 + 0.005 + out - (d * 0.4 if out else 0), z), (dw - 0.02, 0.02 + (d * 0.8 if out else 0), dh - 0.02),
                         m[wood], 0.006))
            p.append(torus("Pull", (x, d / 2 + 0.02 + out, z - dh * 0.1), 0.035, 0.006, m["brass"], rot=(D90, 0, 0), segs=12, minor_segs=4))
    return group(p, loc, rot)


# ============================================================================================ seating

def build_sofas():
    m = reset()
    sofa(m, m["tan"], 2.1, loc=(1, 0, 0), price="price_tag")
    export("film_sofa_tan", (3, 1, 1))
    m = reset()
    sofa(m, m["chocolate"], 2.1, loc=(1, 0, 0), price="price_tag_yellow")
    export("film_sofa_brown", (3, 1, 1))
    m = reset()
    sofa(m, R.fabric("tropical", tile=0.8), 2.2, trim="honey_oak", loc=(1, 0, 0))
    export("film_sofa_tropical", (3, 1, 1))
    m = reset()
    sofa(m, R.fabric("matelasse", tile=0.5, source="cotton"), 2.1, skirt=True, loc=(1, 0, 0), price="price_tag")
    export("film_sofa_quilted", (3, 1, 1))
    m = reset()
    sofa(m, R.fabric("floral", tile=0.55, source="cotton"), 1.55, skirt=True, loc=(0.5, 0, 0), price="price_tag_yellow")
    export("film_loveseat_floral", (2, 1, 1))


def build_recliners():
    for name, kind, pillow in (("film_recliner_leather", "leather_brown", True), ("film_recliner_mauve", "mauve", False),
                               ("film_recliner_tan", "microsuede", True), ("film_recliner_bluegrey", "bluegrey", False)):
        m = reset()
        recliner(m, m[kind], pillow=pillow)
        export(name, (1, 1, 1))


def build_chairs():
    m = reset()
    swivel_chair(m, m["teal"])
    export("film_swivel_chair_teal", (1, 1, 1))
    m = reset()
    wood_trim_chair(m, m["teal"])
    export("film_armchair_wood_trim", (1, 1, 1))
    m = reset()
    accent_chair(m, R.fabric("diamond", tile=0.35))
    export("film_accent_chair_diamond", (1, 1, 1))
    m = reset()
    ottoman_parts(m, R.fabric("diamond", tile=0.35))
    export("film_ottoman_diamond", (1, 1, 1))
    m = reset()
    ottoman_parts(m, m["teal"], tufted=True)
    export("film_ottoman_tufted", (1, 1, 1))
    m = reset()
    ladderback(m)
    export("film_ladderback_chair", (1, 1, 1))
    m = reset()
    school_chair(m)
    export("film_school_chair", (1, 1, 1))
    m = reset()
    resin_chair(m)
    export("film_resin_chair", (1, 1, 1))


def build_wicker_chair():
    m = reset()
    p = [box("Base", (0, 0.02, 0.2), (0.7, 0.68, 0.36), m["white_wicker"], 0.03),
         box("Cushion", (0, 0.08, 0.43), (0.56, 0.54, 0.1), R.fabric("floral", tile=0.4, source="cotton"), 0.05)]
    p.append(barrel("Weave", (0, 0.02), 0.36, 0.06, 0.36, 0.3, 0.66, m["white_wicker"]))
    torus("Rim", (0, 0.02, 0.4), 0.33, 0.025, m["white_wicker"])
    export("film_wicker_chair", (1, 1, 1))


def build_throne():
    """The gilded throne with purple velvet that sits in the middle of the store."""
    m = reset()
    gilt, vel = m["gilt"], m["purple_velvet"]
    box("SeatFrame", (0, 0, 0.46), (0.74, 0.64, 0.12), gilt, 0.02)
    box("Cushion", (0, 0.02, 0.56), (0.62, 0.56, 0.1), vel, 0.04)
    for sx in (-1, 1):
        for sy in (-1, 1):
            cyl("Leg", (sx * 0.32, sy * 0.27, 0.22), 0.045, 0.42, gilt, verts=10, r2=0.03)
            sphere("Foot", (sx * 0.32, sy * 0.27 + 0.02, 0.03), 0.045, gilt, segs=8)
        box("Arm", (sx * 0.34, 0.02, 0.8), (0.07, 0.54, 0.06), gilt, 0.02)
        cyl("ArmPost", (sx * 0.34, 0.26, 0.68), 0.03, 0.24, gilt, verts=8)
        sphere("ArmEnd", (sx * 0.34, 0.3, 0.82), 0.05, gilt, segs=8)
        cyl("BackPost", (sx * 0.34, -0.28, 1.1), 0.045, 1.3, gilt, verts=10)
        sphere("Finial", (sx * 0.34, -0.28, 1.8), 0.06, gilt, segs=10)
    box("BackPanel", (0, -0.27, 1.12), (0.6, 0.05, 1.0), vel, 0.03)
    box("BackFrameTop", (0, -0.28, 1.66), (0.74, 0.08, 0.1), gilt, 0.02)
    cyl("Crest", (0, -0.28, 1.74), 0.12, 0.08, gilt, rot=(D90, 0, 0), verts=16)
    sphere("CrestTop", (0, -0.28, 1.88), 0.07, gilt, segs=10)
    for sx in (-1, 1):
        torus("Scroll", (sx * 0.2, -0.28, 1.74), 0.07, 0.018, gilt, rot=(D90, 0, 0), segs=14, minor_segs=4)
    export("film_throne", (1, 2, 1))


def build_stool():
    """Kane Parsons' stool with one leg too many."""
    m = reset()
    cyl("Seat", (0, 0, 0.56), 0.19, 0.05, m["honey_oak"], verts=18)
    for i in range(5):
        a = i * math.tau / 5
        cyl("Leg", (math.cos(a) * 0.12, math.sin(a) * 0.12, 0.27), 0.018, 0.56, m["honey_oak"],
            rot=(math.sin(a) * 0.18, -math.cos(a) * 0.18, 0), verts=8)
    torus("Ring", (0, 0, 0.2), 0.14, 0.01, m["dark_wood"], segs=20, minor_segs=5)
    export("film_five_legged_stool", (1, 1, 1))


def build_bistro_set():
    """The basement showroom's wrought-iron bistro set: a round table and four curly-backed chairs."""
    m = reset()
    cx, cy = 0.5, -0.5
    cyl("Top", (cx, cy, 0.72), 0.42, 0.03, m["honey_oak"], verts=24)
    torus("TopRim", (cx, cy, 0.72), 0.42, 0.012, m["iron"], segs=24, minor_segs=4)
    cyl("Column", (cx, cy, 0.38), 0.025, 0.68, m["iron"], verts=8)
    for i in range(4):
        a = i * math.tau / 4 + math.pi / 4
        cyl("Foot", (cx + math.cos(a) * 0.16, cy + math.sin(a) * 0.16, 0.06), 0.012, 0.36, m["iron"],
            rot=(math.sin(a) * 1.2, -math.cos(a) * 1.2, 0), verts=6)
    for i in range(4):
        a = i * math.tau / 4
        px, py = cx + math.cos(a) * 0.72, cy + math.sin(a) * 0.72
        p = [cyl("Seat", (0, 0, 0.46), 0.18, 0.03, m["honey_oak"], verts=16),
             torus("SeatRim", (0, 0, 0.46), 0.18, 0.01, m["iron"], segs=16, minor_segs=4),
             torus("BackLoop", (0, -0.17, 0.74), 0.15, 0.01, m["iron"], rot=(D90, 0, 0), segs=16, minor_segs=4),
             torus("Curl", (0, -0.17, 0.74), 0.06, 0.008, m["iron"], rot=(D90, 0, 0), segs=12, minor_segs=4)]
        for j in range(4):
            b = j * math.tau / 4 + math.pi / 4
            p.append(cyl("Leg", (math.cos(b) * 0.14, math.sin(b) * 0.14, 0.22), 0.01, 0.46, m["iron"], verts=6))
        group(p, (px, py, 0), (0, 0, a + D90))
    export("film_bistro_set", (2, 1, 2))


# ============================================================================================ tables, storage

def build_dining_set():
    """Octagonal oak table, oval-back chairs in plaid, blue placemats and plates laid out, a candle."""
    m = reset()
    cx, cy = 0.5, -0.5
    plaid = R.fabric("plaid", tile=0.4, source="cotton")
    cyl("Top", (cx, cy, 0.74), 0.62, 0.04, m["honey_oak"], verts=8, rot=(0, 0, math.pi / 8))
    cyl("Apron", (cx, cy, 0.67), 0.56, 0.1, m["honey_oak"], verts=8, rot=(0, 0, math.pi / 8))
    for sx in (-1, 1):
        for sy in (-1, 1):
            cyl("Leg", (cx + sx * 0.36, cy + sy * 0.36, 0.34), 0.035, 0.68, m["honey_oak"], verts=10, r2=0.025)
    for i in range(4):
        a = i * math.tau / 4
        px, py = cx + math.cos(a) * 0.36, cy + math.sin(a) * 0.36
        box("Placemat", (px, py, 0.763), (0.34, 0.26, 0.004), m["placemat"], 0.0, rot=(0, 0, a))
        cyl("Plate", (px, py, 0.77), 0.1, 0.012, m["white"], verts=16)
    cyl("Candle", (cx, cy, 0.8), 0.025, 0.08, m["candle"], verts=10)
    cyl("Flame", (cx, cy, 0.85), 0.008, 0.02, m["lamp_glow"], verts=6)
    for i in range(4):
        a = i * math.tau / 4
        px, py = cx + math.cos(a) * 0.86, cy + math.sin(a) * 0.86
        p = [box("Seat", (0, 0, 0.45), (0.44, 0.42, 0.05), m["honey_oak"], 0.01),
             box("SeatPad", (0, 0.01, 0.49), (0.42, 0.4, 0.05), plaid, 0.025)]
        for sx in (-1, 1):
            p.append(cyl("FrontLeg", (sx * 0.19, 0.18, 0.22), 0.02, 0.44, m["honey_oak"], verts=8))
            p.append(cyl("BackPost", (sx * 0.19, -0.19, 0.5), 0.02, 1.0, m["honey_oak"], rot=(math.radians(6), 0, 0), verts=8))
        p.append(cyl("OvalFrame", (0, -0.23, 0.78), 0.19, 0.03, m["honey_oak"], rot=(math.radians(96), 0, 0), verts=20))
        p.append(cyl("OvalPad", (0, -0.21, 0.78), 0.16, 0.03, plaid, rot=(math.radians(96), 0, 0), verts=20))
        for o in p[-2:]:
            o.scale = (0.85, 1.25, 1.0)
        group(p, (px, py, 0), (0, 0, a - D90))
    export("film_dining_set", (2, 1, 2))


def build_tables():
    m = reset()
    for sx in (-1, 1):
        box("RailX", (0, sx * 0.38, 0.4), (0.8, 0.05, 0.05), m["honey_oak"], 0.008)
        box("RailY", (sx * 0.38, 0, 0.4), (0.05, 0.8, 0.05), m["honey_oak"], 0.008)
        for sy in (-1, 1):
            box("Leg", (sx * 0.37, sy * 0.37, 0.2), (0.06, 0.06, 0.4), m["honey_oak"], 0.008)
    box("Glass", (0, 0, 0.41), (0.72, 0.72, 0.012), m["glass"], 0.0)
    box("Shelf", (0, 0, 0.1), (0.7, 0.7, 0.02), m["honey_oak"], 0.0)
    export("film_coffee_table_glass", (1, 1, 1))

    m = reset()
    box("Tray", (0, 0, 0.64), (0.52, 0.38, 0.02), m["veneer"], 0.005)
    for sx in (-1, 1):
        box("Rim", (0, sx * 0.19, 0.655), (0.52, 0.012, 0.03), m["brass"], 0.0)
        for a in (32, -32):
            cyl("Leg", (sx * 0.22, 0, 0.32), 0.008, 0.74, m["brass"], rot=(math.radians(a), 0, 0), verts=6)
    box("Glass", (0, 0, 0.66), (0.06, 0.06, 0.12), m["glass"], 0.005)
    cyl("Plate", (0.12, 0.04, 0.656), 0.09, 0.01, m["white"], verts=14)
    export("film_tv_tray", (1, 1, 1))

    m = reset()
    box("Top", (0, 0, 0.53), (0.58, 0.58, 0.04), m["honey_oak"], 0.01)
    box("Body", (0, 0, 0.38), (0.5, 0.5, 0.26), m["honey_oak"], 0.008)
    box("Drawer", (0, 0.255, 0.42), (0.42, 0.02, 0.12), m["honey_oak"], 0.006)
    torus("Pull", (0, 0.27, 0.41), 0.03, 0.005, m["brass"], rot=(D90, 0, 0), segs=10, minor_segs=4)
    box("Shelf", (0, 0, 0.08), (0.5, 0.5, 0.02), m["honey_oak"], 0.0)
    for sx in (-1, 1):
        for sy in (-1, 1):
            box("Leg", (sx * 0.25, sy * 0.25, 0.26), (0.05, 0.05, 0.52), m["honey_oak"], 0.006)
    cyl("LampBase", (0, -0.04, 0.57), 0.1, 0.04, m["brass"], verts=16)
    cyl("Column", (0, -0.04, 0.8), 0.025, 0.42, m["brass"], verts=10)
    cyl("Shade", (0, -0.04, 1.1), 0.24, 0.26, m["lampshade"], verts=24, r2=0.13)
    cyl("Bulb", (0, -0.04, 1.02), 0.04, 0.06, m["lamp_glow"], verts=8)
    sphere("Finial", (0, -0.04, 1.26), 0.02, m["brass"], segs=6)
    for i in range(24):
        a = i * math.tau / 24
        box("Pleat", (math.cos(a) * 0.19, -0.04 + math.sin(a) * 0.19, 1.1), (0.008, 0.012, 0.25), m["lampshade"], 0.0,
            rot=(0, -math.sin(a) * 0.4, a))
    export("film_end_table_lamp", (1, 2, 1))


def build_storage():
    m = reset()
    oak = m["honey_oak"]
    box("Body", (0, -0.02, 0.97), (0.98, 0.56, 1.8), oak, 0.01)
    box("Crown", (0, -0.02, 1.9), (1.06, 0.64, 0.06), oak, 0.01)
    box("Crown2", (0, -0.02, 1.85), (1.02, 0.6, 0.05), oak, 0.01)
    box("Plinth", (0, -0.02, 0.05), (1.02, 0.6, 0.1), oak, 0.01)
    for sx in (-1, 1):
        box("Door", (sx * 0.24, 0.27, 1.22), (0.46, 0.02, 1.22), oak, 0.008)
        for z in (0.92, 1.5):
            box("RaisedPanel", (sx * 0.24, 0.285, z), (0.34, 0.015, 0.46), oak, 0.02)
        sphere("Knob", (sx * 0.04, 0.3, 1.2), 0.022, m["brass"], segs=8)
    for z in (0.22, 0.46):
        box("Drawer", (0, 0.27, z), (0.92, 0.02, 0.2), oak, 0.008)
        for sx in (-1, 1):
            torus("Pull", (sx * 0.22, 0.29, z - 0.02), 0.035, 0.006, m["brass"], rot=(D90, 0, 0), segs=12, minor_segs=4)
    export("film_armoire", (1, 2, 1))

    m = reset()
    oak = m["honey_oak"]
    cx = 0.5
    box("Base", (cx, -0.05, 0.05), (1.9, 0.54, 0.1), oak, 0.01)
    box("Top", (cx, -0.05, 1.78), (1.94, 0.58, 0.05), oak, 0.01)
    for x in (-0.44, 0.06, 0.94, 1.44):
        box("Side", (cx - 0.5 + x + 0.0, -0.05, 0.92), (0.04, 0.52, 1.72), oak, 0.005)
    box("Back", (cx, -0.3, 0.92), (1.88, 0.02, 1.72), oak, 0.0)
    box("TVShelf", (cx, -0.05, 0.6), (0.86, 0.5, 0.03), oak, 0.0)
    box("BayTop", (cx, -0.05, 1.3), (0.86, 0.5, 0.03), oak, 0.0)
    crt_parts(m, (cx, -0.02, 0.62), on=True, size=0.6)
    box("VCR", (cx, 0.0, 0.5), (0.5, 0.36, 0.08), m["black_plastic"], 0.008)
    box("VCRClock", (cx + 0.15, 0.182, 0.5), (0.08, 0.005, 0.02), m["lamp_glow"], 0.0)
    for sx in (-1, 1):
        box("LowDoor", (cx + sx * 0.21, 0.2, 0.25), (0.4, 0.02, 0.28), oak, 0.006)
        sphere("Knob", (cx + sx * 0.05, 0.22, 0.25), 0.015, m["brass"], segs=6)
        tx = cx + sx * 0.69
        box("TowerDoorFrame", (tx, 0.2, 1.0), (0.46, 0.02, 1.4), oak, 0.006)
        box("TowerGlass", (tx, 0.212, 1.1), (0.36, 0.01, 1.0), m["glass"], 0.0)
        box("TowerDoorLow", (tx, 0.2, 0.25), (0.46, 0.02, 0.28), oak, 0.006)
        for z in (0.75, 1.15):
            box("TowerShelf", (tx, -0.05, z), (0.44, 0.46, 0.02), oak, 0.0)
    box("BayCabinet", (cx, 0.2, 1.55), (0.86, 0.02, 0.42), oak, 0.006)
    export("film_entertainment_center", (2, 2, 1))

    m = reset()
    ch = m["cherry"]
    box("LowerBody", (0, -0.05, 0.42), (0.96, 0.48, 0.8), ch, 0.01)
    box("LowerTop", (0, -0.03, 0.83), (1.0, 0.52, 0.04), ch, 0.01)
    for sx in (-1, 1):
        box("Door", (sx * 0.235, 0.2, 0.42), (0.44, 0.02, 0.66), ch, 0.008)
        box("Panel", (sx * 0.235, 0.215, 0.42), (0.32, 0.012, 0.5), ch, 0.02)
        sphere("Knob", (sx * 0.04, 0.23, 0.46), 0.02, m["brass"], segs=8)
        box("UpperSide", (sx * 0.46, -0.12, 1.34), (0.04, 0.3, 0.98), ch, 0.005)
    box("UpperBack", (0, -0.26, 1.34), (0.9, 0.02, 0.98), ch, 0.0)
    for z in (1.1, 1.42):
        box("Shelf", (0, -0.12, z), (0.9, 0.28, 0.02), ch, 0.0)
    box("Crown", (0, -0.1, 1.86), (1.0, 0.36, 0.06), ch, 0.01)
    rnd = random.Random(7)
    x = -0.42
    while x < 0.4:
        w = rnd.uniform(0.03, 0.06)
        h = rnd.uniform(0.18, 0.26)
        box("Book", (x + w / 2, -0.14, 1.11 + h / 2), (w, 0.2, h), m[rnd.choice(["navy", "school_red", "green_velvet", "oat"])], 0.004)
        x += w + 0.004
    export("film_bookcase_hutch", (1, 2, 1))

    m = reset()
    drawer_chest(m, "honey_oak", 1.7, 0.82, 0.5, 3, 2, loc=(0.5, -0.05, 0))
    box("MirrorFrame", (0.5, -0.27, 1.3), (1.1, 0.05, 0.86), m["honey_oak"], 0.02)
    box("Mirror", (0.5, -0.24, 1.3), (0.96, 0.01, 0.72), m["glass"], 0.0)
    export("film_dresser", (2, 2, 1))

    m = reset()
    drawer_chest(m, "honey_oak", 0.52, 0.5, 0.44, 2)
    sphere("Jar", (0, -0.04, 0.62), 0.11, m["almond_plastic"], scale=(1, 1, 1.25), segs=14)
    cyl("Neck", (0, -0.04, 0.77), 0.03, 0.06, m["brass"], verts=8)
    cyl("Shade", (0, -0.04, 0.88), 0.17, 0.18, m["lampshade"], verts=20, r2=0.11)
    cyl("Bulb", (0, -0.04, 0.84), 0.03, 0.05, m["lamp_glow"], verts=8)
    export("film_nightstand", (1, 1, 1))

    m = reset()
    cx = 0.5
    box("Counter", (cx, 0, 0.5), (1.8, 0.62, 0.96), m["grey_laminate"], 0.02)
    box("Top", (cx, -0.02, 0.99), (1.86, 0.7, 0.04), m["laminate"], 0.01)
    box("Kick", (cx, 0.27, 0.05), (1.76, 0.06, 0.1), m["black_plastic"], 0.0)
    box("Register", (cx - 0.4, -0.05, 1.11), (0.40, 0.36, 0.2), m["almond_plastic"], 0.03)
    box("RegDisplay", (cx - 0.4, 0.08, 1.27), (0.24, 0.05, 0.1), m["screen_on"], 0.0, rot=(math.radians(-20), 0, 0))
    box("Keys", (cx - 0.4, 0.1, 1.215), (0.3, 0.14, 0.01), m["grey_plastic"], 0.0, rot=(math.radians(-10), 0, 0))
    box("Pad", (cx + 0.3, 0.0, 1.015), (0.4, 0.3, 0.01), m["navy"], 0.0)
    cyl("Bell", (cx + 0.6, 0.0, 1.03), 0.05, 0.04, m["brass"])
    box("Receipts", (cx + 0.15, -0.1, 1.02), (0.12, 0.16, 0.02), m["white"], 0.0)
    export("film_checkout_counter", (2, 1, 1))


# ============================================================================================ beds

def build_beds():
    m = reset()
    oak = m["honey_oak"]
    cx, cy = 0.5, -0.5
    for sx in (-1, 1):
        cyl("HeadPost", (cx + sx * 0.84, cy - 0.98, 0.62), 0.045, 1.24, oak, verts=12)
        sphere("HeadFinial", (cx + sx * 0.84, cy - 0.98, 1.27), 0.05, oak, segs=8)
        cyl("FootPost", (cx + sx * 0.84, cy + 0.98, 0.36), 0.04, 0.72, oak, verts=12)
        box("SideRail", (cx + sx * 0.82, cy, 0.24), (0.04, 1.92, 0.16), oak, 0.006)
    box("HeadRail", (cx, cy - 0.98, 1.12), (1.64, 0.06, 0.12), oak, 0.01)
    box("HeadLow", (cx, cy - 0.98, 0.42), (1.64, 0.05, 0.1), oak, 0.008)
    for i in range(9):
        cyl("Spindle", (cx - 0.68 + i * 0.17, cy - 0.98, 0.77), 0.016, 0.6, oak, verts=8)
    box("FootRail", (cx, cy + 0.98, 0.58), (1.64, 0.05, 0.1), oak, 0.008)
    box("FootPanel", (cx, cy + 0.98, 0.42), (1.6, 0.03, 0.26), oak, 0.008)
    mattress_parts(m, (cx, cy, 0.30), length=1.9, width=1.6)
    quilt = R.fabric("comforter", tile=0.9, source="cotton", contrast=0.5)
    box("Comforter", (cx, cy + 0.2, 0.56), (1.72, 1.5, 0.08), quilt, 0.04)
    for sx in (-1, 1):
        box("Drape", (cx + sx * 0.86, cy + 0.2, 0.4), (0.03, 1.5, 0.36), quilt, 0.015)
    box("DrapeFoot", (cx, cy + 0.95, 0.4), (1.72, 0.03, 0.36), quilt, 0.015)
    for px in (-0.4, 0.4):
        box("Pillow", (cx + px, cy - 0.72, 0.6), (0.62, 0.34, 0.15), m["sheet"], 0.06)
        box("Sham", (cx + px, cy - 0.82, 0.66), (0.62, 0.12, 0.34), quilt, 0.05, rot=(math.radians(20), 0, 0))
    box("BedSkirt", (cx, cy, 0.16), (1.62, 1.9, 0.18), m["cream"], 0.01)
    tag((cx + 0.6, cy + 1.005, 0.56), "price_tag", tilt=90, size=0.12)
    export("film_showroom_bed", (2, 2, 2))

    m = reset()
    mattress_parts(m, (0, -0.5, 0.0), length=1.9, width=0.96)
    export("film_bare_mattress", (1, 1, 2))

    m = reset()
    for i in range(6):
        mattress_parts(m, (0.5 + (i % 2) * 0.12 - 0.06, -0.5 + (i % 3 - 1) * 0.10, i * 0.23),
                       rot=(0, 0, math.radians((i * 23) % 17 - 8)), length=1.9, width=1.4)
    mattress_parts(m, (1.25, -0.5, 0.75), rot=(0, math.radians(70), 0), length=1.9, width=1.4)
    export("film_mattress_stack", (2, 2, 2))


# ============================================================================================ lights, decor, store

def build_lights():
    m = reset()
    cyl("Base", (0, 0, 0.02), 0.16, 0.04, m["black_metal"], verts=20)
    cyl("Pole", (0, 0, 0.9), 0.012, 1.72, m["black_metal"], verts=8)
    cyl("Bowl", (0, 0, 1.78), 0.05, 0.12, m["black_metal"], verts=20, r2=0.22)
    cyl("Glow", (0, 0, 1.83), 0.19, 0.01, m["lamp_glow"], verts=20)
    export("film_torchiere", (1, 2, 1))

    m = reset()
    cyl("Canopy", (0, 0, 0.98), 0.08, 0.04, m["brass"], verts=14)
    cyl("Chain", (0, 0, 0.78), 0.008, 0.38, m["brass"], verts=6)
    cyl("Column", (0, 0, 0.5), 0.035, 0.22, m["brass"], verts=12)
    sphere("Drop", (0, 0, 0.36), 0.04, m["brass"], segs=8)
    torus("Ring", (0, 0, 0.44), 0.26, 0.012, m["brass"], segs=28, minor_segs=4)
    for i in range(5):
        a = i * math.tau / 5
        ax, ay = math.cos(a) * 0.26, math.sin(a) * 0.26
        cyl("Arm", (ax / 2, ay / 2, 0.47), 0.008, 0.26, m["brass"], rot=(0, D90, a), verts=6)
        cyl("Cup", (ax, ay, 0.47), 0.025, 0.04, m["brass"], verts=8)
        cyl("Tulip", (ax, ay, 0.53), 0.03, 0.1, m["lampshade"], verts=12, r2=0.07)
        cyl("Bulb", (ax, ay, 0.53), 0.018, 0.06, m["lamp_glow"], verts=6)
        sphere("Crystal", (ax * 1.05, ay * 1.05, 0.38), 0.015, m["glass"], segs=6)
    export("film_chandelier", (1, 1, 1))


def build_decor():
    m = reset()
    box("Backing", (0, -0.475, 0.55), (0.86, 0.02, 0.66), m["cardboard"], 0.0)
    panel("Print", (0, -0.463, 0.55), 0.82, 0.62, R.face("watercolor", contrast=0.2, grime=0.25))
    for sx in (-1, 1):
        box("FrameSide", (sx * 0.42, -0.46, 0.55), (0.02, 0.03, 0.66), m["gilt"], 0.003)
    for sz in (-1, 1):
        box("FrameTop", (0, -0.46, 0.55 + sz * 0.32), (0.86, 0.03, 0.02), m["gilt"], 0.003)
    export("film_watercolor_print", (1, 1, 1))

    m = reset()
    for i, (x, a) in enumerate(((-0.05, 20), (0.5, -20), (1.05, 20))):
        p = [box("Frame", (0, 0, 0.88), (0.54, 0.03, 1.74), m["rattan"], 0.008),
             box("Weave", (0, 0.0, 0.92), (0.44, 0.035, 1.4), m["rattan"], 0.0),
             box("Rail", (0, 0, 0.18), (0.5, 0.04, 0.04), m["rattan"], 0.006),
             box("Rail", (0, 0, 1.64), (0.5, 0.04, 0.04), m["rattan"], 0.006)]
        for sx in (-1, 1):
            p.append(cyl("Stile", (sx * 0.26, 0, 0.88), 0.022, 1.76, m["rattan"], verts=8))
        group(p, (x, -0.1, 0), (0, 0, math.radians(a)))
    export("film_wicker_screen", (2, 2, 1))

    m = reset()
    rnd = random.Random(12)
    colours = ["carpet_rose", "carpet_sage", "carpet_grey", "carpet_navy", "carpet_brown", "carpet_cream", "carpet_maroon", "carpet_grey"]
    x = -0.36
    for i, col in enumerate(colours):
        r = rnd.uniform(0.11, 0.15)
        h = rnd.uniform(2.0, 2.75)
        lean = rnd.uniform(-0.06, 0.06)
        p = [cyl("Roll", (0, 0, h / 2), r, h, m[col], verts=16),
             cyl("Core", (0, 0, h + 0.002), r * 0.3, 0.01, m["cardboard"], verts=10),
             cyl("Spiral", (0, 0, h + 0.004), r * 0.7, 0.008, m[col], verts=14)]
        if i % 2 == 0:
            p.append(panel("Tag", (0, r + 0.004, h * 0.62), 0.1, 0.1, R.face("price_tag" if i % 4 == 0 else "price_tag_yellow")))
        group(p, (x, -0.1 + (i % 2) * 0.1, 0), (lean, rnd.uniform(-0.05, 0.05), 0))
        x += 2 * r + 0.015
    export("film_carpet_rolls", (2, 3, 1))

    m = reset()
    st = m["standee"]
    sphere("Head", (0, 0, 1.6), 0.11, st, scale=(0.9, 0.12, 1.15), segs=12)
    box("Neck", (0, 0, 1.46), (0.09, 0.02, 0.08), st, 0.0)
    box("Torso", (0, 0, 1.15), (0.42, 0.02, 0.56), st, 0.02)
    for sx in (-1, 1):
        box("Arm", (sx * 0.26, 0, 1.12), (0.1, 0.02, 0.56), st, 0.02, rot=(0, sx * math.radians(6), 0))
        box("Leg", (sx * 0.1, 0, 0.44), (0.16, 0.02, 0.86), st, 0.02)
    box("Strut", (0, -0.25, 0.6), (0.12, 0.02, 1.2), m["cardboard"], 0.0, rot=(math.radians(-24), 0, 0))
    export("film_cardboard_standee", (1, 2, 1))

    m = reset()
    wood = m["mahogany"]
    box("Base", (0, -0.1, 0.04), (0.52, 0.42, 0.08), wood, 0.01)
    box("Post", (0, -0.12, 0.62), (0.16, 0.16, 1.12), wood, 0.01)
    torus("Rim", (0, 0.04, 1.32), 0.42, 0.045, wood, rot=(D90, 0, 0), segs=32, minor_segs=8)
    cyl("Hub", (0, 0.04, 1.32), 0.1, 0.1, m["brass"], rot=(D90, 0, 0), verts=16)
    for i in range(8):
        a = i * math.tau / 8
        cyl("Spoke", (math.cos(a) * 0.3, 0.04, 1.32 + math.sin(a) * 0.3), 0.022, 0.6, wood, rot=(0, -a + D90, 0), verts=8)
        cyl("Handle", (math.cos(a) * 0.52, 0.04, 1.32 + math.sin(a) * 0.52), 0.03, 0.14, wood, rot=(0, -a + D90, 0), verts=8)
        sphere("Knob", (math.cos(a) * 0.6, 0.04, 1.32 + math.sin(a) * 0.6), 0.032, wood, segs=8)
    export("film_ship_wheel", (1, 2, 1))

    m = reset()
    wood = m["mahogany"]
    cyl("Pole", (0, 0, 0.95), 0.03, 1.8, wood, verts=12)
    sphere("Finial", (0, 0, 1.87), 0.045, wood, segs=10)
    for i in range(3):
        a = i * math.tau / 3
        cyl("Foot", (math.cos(a) * 0.17, math.sin(a) * 0.17, 0.07), 0.022, 0.4, wood, rot=(math.sin(a) * 1.25, -math.cos(a) * 1.25, 0), verts=8)
    for i in range(6):
        a = i * math.tau / 6
        cyl("Hook", (math.cos(a) * 0.1, math.sin(a) * 0.1, 1.72 - (i % 2) * 0.16), 0.014, 0.24, wood,
            rot=(math.sin(a) * 0.9, -math.cos(a) * 0.9, 0), verts=6)
        sphere("HookEnd", (math.cos(a) * 0.19, math.sin(a) * 0.19, 1.8 - (i % 2) * 0.16), 0.02, wood, segs=6)
    box("Coat", (0.14, 0.0, 1.3), (0.1, 0.42, 0.72), m["oat"], 0.04, rot=(0, math.radians(8), 0))
    export("film_coat_rack", (1, 2, 1))

    m = reset()
    box("Rug", (0.5, -0.5, 0.006), (1.9, 1.9, 0.012), m["carpet_maroon"], 0.0)
    panel("Pattern", (0.5, -0.5, 0.0125), 1.86, 1.86, R.face("oriental", source="carpet", contrast=0.6, grime=0.4), facing="up")
    export("film_oriental_rug", (2, 1, 2))


def build_patio():
    m = reset()
    cx, cy = 0.5, -0.5
    cyl("Top", (cx, cy, 0.7), 0.5, 0.04, m["resin"], verts=24)
    cyl("Pedestal", (cx, cy, 0.36), 0.05, 0.68, m["resin"], verts=10)
    cyl("Foot", (cx, cy, 0.03), 0.3, 0.06, m["resin"], verts=16)
    cyl("Pole", (cx, cy, 1.4), 0.018, 1.5, m["chrome"], verts=8)
    umbrella = R.build("UMBRELLA", (0.10, 0.26, 0.56), "cotton", 0.5, 1.0, 0.4)
    cyl("Canopy", (cx, cy, 2.1), 1.05, 0.4, umbrella, verts=8, r2=0.05)
    sphere("Top", (cx, cy, 2.32), 0.04, m["white"], segs=6)
    for i in range(4):
        a = i * math.tau / 4 + math.pi / 4
        resin_chair(m, (cx + math.cos(a) * 0.78, cy + math.sin(a) * 0.78, 0), (0, 0, a + D90))
    export("film_patio_umbrella_set", (2, 3, 2))

    m = reset()
    cy = -0.5
    box("Frame", (0, cy + 0.15, 0.26), (0.62, 1.4, 0.05), m["resin"], 0.02)
    box("Back", (0, cy - 0.70, 0.52), (0.62, 0.62, 0.05), m["resin"], 0.02, rot=(math.radians(-50), 0, 0))
    for sx in (-1, 1):
        for sy in (-0.45, 0.75):
            box("Leg", (sx * 0.27, cy + sy, 0.13), (0.05, 0.05, 0.26), m["resin"], 0.0)
    for i in range(10):
        box("Slat", (0, cy - 0.3 + i * 0.14, 0.29), (0.56, 0.02, 0.01), m["grey_plastic"], 0.0)
    export("film_sun_lounger", (1, 1, 2))


def build_signs():
    for name, file in (("film_sign_sale75", "sign_sale75"), ("film_sign_must_go", "sign_must_go"), ("film_sign_on_sale", "sign_on_sale")):
        m = reset()
        box("Board", (0, 0, 0.48), (0.9, 0.025, 0.68), m["white"], 0.004)
        panel("Front", (0, 0.0135, 0.48), 0.88, 0.66, R.face(file), facing="front")
        panel("Back", (0, -0.0135, 0.48), 0.88, 0.66, R.face(file), facing="back")
        for sx in (-1, 1):
            cyl("Wire", (sx * 0.36, 0, 0.91), 0.004, 0.18, m["steel"], verts=4)
        export(name, (1, 1, 1))

    m = reset()
    cx = 1.0
    panel("Banner", (cx, -0.47, 0.62), 2.8, 0.52, R.face("sign_no_credit", source="cotton", contrast=0.4))
    box("Backing", (cx, -0.478, 0.62), (2.82, 0.01, 0.54), m["white"], 0.0)
    for sx in (-1, 1):
        for sz in (-1, 1):
            torus("Grommet", (cx + sx * 1.36, -0.465, 0.62 + sz * 0.22), 0.02, 0.006, m["brass"], rot=(D90, 0, 0), segs=10, minor_segs=4)
    export("film_banner_no_credit", (3, 1, 1))

    m = reset()
    cx = 0.5
    box("Board", (cx, -0.47, 1.0), (1.74, 0.05, 1.16), m["navy"], 0.01)
    panel("Face", (cx, -0.444, 1.0), 1.66, 1.1, R.face("sign_pirate", contrast=0.25))
    export("film_sign_pirate", (2, 2, 1))


# ============================================================================================ piles

def card_catalog(m, loc=(0, 0, 0), rot=(0, 0, 0)):
    p = [box("Body", (0, 0, 0.55), (0.8, 0.45, 1.1), m["honey_oak"], 0.01)]
    for r in range(6):
        for c in range(4):
            x, z = -0.3 + c * 0.2, 0.12 + r * 0.17
            p.append(box("Drawer", (x, 0.23, z), (0.18, 0.02, 0.15), m["pine"], 0.004))
            p.append(box("Pull", (x, 0.245, z - 0.03), (0.05, 0.01, 0.015), m["brass"], 0.0))
    return group(p, loc, rot)


def file_cabinet(m, loc=(0, 0, 0), rot=(0, 0, 0)):
    p = [box("Body", (0, 0, 0.66), (0.46, 0.6, 1.32), m["beige_metal"], 0.01)]
    for i in range(4):
        z = 0.18 + i * 0.32
        p.append(box("Drawer", (0, 0.305, z), (0.42, 0.02, 0.29), m["beige_metal"], 0.004))
        p.append(box("Handle", (0, 0.32, z + 0.06), (0.14, 0.02, 0.025), m["chrome"], 0.0))
    return group(p, loc, rot)


def cantilever_chair(m, loc=(0, 0, 0), rot=(0, 0, 0)):
    p = [box("Seat", (0, 0.02, 0.45), (0.44, 0.42, 0.06), m["chocolate"], 0.025),
         box("Back", (0, -0.2, 0.72), (0.42, 0.05, 0.36), m["chocolate"], 0.025, rot=(math.radians(10), 0, 0))]
    for sx in (-1, 1):
        p.append(cyl("Runner", (sx * 0.2, 0.0, 0.015), 0.012, 0.5, m["chrome"], rot=(D90, 0, 0), verts=6))
        p.append(cyl("Front", (sx * 0.2, 0.25, 0.22), 0.012, 0.44, m["chrome"], verts=6))
        p.append(cyl("BackTube", (sx * 0.21, -0.2, 0.62), 0.012, 0.5, m["chrome"], verts=6))
    return group(p, loc, rot)


def build_furniture_pile():
    """The Furniture Pile Room: armoires, card catalogues, file cabinets, drawers and chairs heaped toward the ceiling."""
    m = reset()
    cx, cy = 1.0, -1.0
    group([box("Armoire", (0, 0, 0.95), (0.98, 0.56, 1.9), m["honey_oak"], 0.01),
           box("DoorL", (-0.24, 0.29, 1.1), (0.46, 0.02, 1.4), m["honey_oak"], 0.008),
           box("DoorR", (0.24, 0.29, 1.1), (0.46, 0.02, 1.4), m["honey_oak"], 0.008)], (cx - 0.7, cy - 0.3, 0.1), (0, math.radians(18), 0.3))
    card_catalog(m, (cx + 0.1, cy + 0.3, 0.0), (0, 0, -0.2))
    file_cabinet(m, (cx + 0.8, cy - 0.6, 0.0), (0, 0, 0.4))
    file_cabinet(m, (cx + 0.6, cy + 0.6, 0.24), (D90, 0, 1.2))
    drawer_chest(m, "honey_oak", 1.1, 0.7, 0.48, 3, 1, pulled={(1, 0), (2, 0)}, loc=(cx - 0.5, cy + 0.8, 0.0), rot=(0, 0, 0.15))
    box("DeskTop", (cx + 0.2, cy - 0.5, 1.45), (1.3, 0.7, 0.05), m["honey_oak"], 0.01, rot=(0.5, -0.3, 0.2))
    rnd = random.Random(5)
    # A sofa wedged in on its side, a TV, a nightstand, a table on its back with its legs in the air.
    sofa(m, m["mauve"], 1.5, loc=(cx + 0.9, cy + 0.2, 0.75), rot=(0, D90 + 0.2, 0.5))
    crt_parts(m, (cx - 0.2, cy - 0.9, 1.05), rot=(0.3, 0.6, 0.2), size=0.5, shell="almond_plastic")
    drawer_chest(m, "honey_oak", 0.5, 0.5, 0.42, 2, loc=(cx + 0.2, cy + 0.9, 0.9), rot=(1.1, 0.2, 0.4))
    group([box("TableTop", (0, 0, 0), (1.0, 0.7, 0.04), m["honey_oak"], 0.008)] +
          [cyl("TableLeg", (sx * 0.42, sy * 0.28, 0.36), 0.025, 0.7, m["honey_oak"], verts=8) for sx in (-1, 1) for sy in (-1, 1)],
          (cx - 0.3, cy + 0.2, 2.05), (0.25, 0.15, 0.6))
    for i in range(16):
        t = i / 16
        z = 0.5 + t * 2.9
        r = 0.95 * (1 - t * 0.6) * math.sqrt(rnd.random())
        a = rnd.uniform(0, math.tau)
        loc = (cx + math.cos(a) * r, cy + math.sin(a) * r, z)
        rot = (rnd.choice([math.pi, rnd.uniform(-1.4, 1.4)]), rnd.uniform(-1.0, 1.0), rnd.uniform(0, math.tau))
        (ladderback if i % 3 else cantilever_chair)(m, loc=loc, rot=rot)
    cyl("Shade", (cx - 0.9, cy + 0.2, 2.1), 0.22, 0.28, m["lampshade"], verts=20, r2=0.14, rot=(1.2, 0.3, 0))
    group([cyl("Pole", (0, 0, 0.9), 0.012, 1.72, m["black_metal"], verts=8),
           cyl("Bowl", (0, 0, 1.78), 0.05, 0.12, m["black_metal"], verts=20, r2=0.22)], (cx + 0.3, cy - 0.2, 1.4), (0.7, 0.4, 0))
    for i in range(3):
        box("LooseDrawer", (cx + rnd.uniform(-1, 1), cy + 1.2, 0.08 + i * 0.12), (0.5, 0.42, 0.14), m["honey_oak"], 0.006,
            rot=(0, 0, rnd.uniform(-0.6, 0.6)))
    export("film_furniture_pile", (3, 4, 3))


def build_chair_pile():
    """Chairs piled to the ceiling: school chairs and ladder-backs, upside down, on their sides, through each other."""
    m = reset()
    rnd = random.Random(8)
    cx, cy = 0.5, -0.5
    n = 56
    for i in range(n):
        t = i / n
        z = t * 4.4
        r = 0.95 * (1 - t * 0.55) * math.sqrt(rnd.random())
        a = rnd.uniform(0, math.tau)
        loc = (cx + math.cos(a) * r, cy + math.sin(a) * r, z)
        rot = (rnd.choice([0.0, math.pi, rnd.uniform(-1.6, 1.6)]), rnd.uniform(-1.0, 1.0), rnd.uniform(0, math.tau))
        if i % 3 == 0:
            ladderback(m, loc=loc, rot=rot)
        else:
            school_chair(m, rnd.choice(["school_green", "school_red", "school_black"]), loc=loc, rot=rot)
    export("film_chair_pile", (2, 5, 2))


def build_sofa_pile():
    m = reset()
    sofa(m, m["tan"], 1.55, loc=(0.5, -0.5, 0), rot=(0, 0, math.radians(8)))
    sofa(m, R.fabric("floral", tile=0.55, source="cotton"), 1.55, skirt=True, loc=(0.45, -0.55, 0.78),
         rot=(math.radians(10), math.radians(-6), math.radians(-14)))
    sofa(m, m["chocolate"], 1.5, loc=(0.6, -0.4, 1.5), rot=(math.radians(-16), math.radians(9), math.radians(30)))
    sofa(m, R.fabric("matelasse", tile=0.5, source="cotton"), 1.55, skirt=True, loc=(0.5, -0.5, 2.98), rot=(math.pi, 0, math.radians(-4)))
    export("film_sofa_pile", (2, 3, 2))


def build_ottoman_pile():
    """The store sells ottomans; the Backrooms stacked all of them."""
    m = reset()
    rnd = random.Random(9)
    fabrics = [m["teal"], m["tan"], m["mauve"], m["chocolate"], R.fabric("diamond", tile=0.35), R.fabric("floral", tile=0.5, source="cotton"),
               m["bluegrey"], m["leather_brown"]]
    cx, cy = 0.5, -0.5
    layers = [(4, 0.0), (4, 0.36), (3, 0.72), (3, 1.08), (2, 1.44), (2, 1.8), (1, 2.16)]
    for count, z in layers:
        for j in range(count):
            a = j / count * math.tau + rnd.uniform(-0.3, 0.3)
            r = 0.38 * (count > 1) * rnd.uniform(0.7, 1.0)
            ottoman_parts(m, rnd.choice(fabrics), loc=(cx + math.cos(a) * r, cy + math.sin(a) * r, z),
                          rot=(rnd.uniform(-0.15, 0.15) if z else 0, rnd.uniform(-0.15, 0.15) if z else 0, rnd.uniform(0, math.tau)),
                          tufted=rnd.random() < 0.4)
    export("film_ottoman_pile", (2, 3, 2))


def build_boxes():
    m = reset()
    box("Big", (0, 0, 0.23), (0.62, 0.5, 0.46), m["cardboard"], 0.008)
    box("Tape", (0, 0, 0.462), (0.08, 0.5, 0.004), m["oat"], 0.0)
    box("Small", (0.04, -0.02, 0.64), (0.46, 0.4, 0.34), m["cardboard"], 0.008, rot=(0, 0, 0.3))
    box("Tape2", (0.04, -0.02, 0.812), (0.07, 0.4, 0.004), m["oat"], 0.0, rot=(0, 0, 0.3))
    export("film_box_stack", (1, 1, 1))


# ============================================================================================ electronics, people, laundry

def build_electronics():
    m = reset()
    box("Body", (0, 0, 0.09), (0.42, 0.36, 0.16), m["plastic"], 0.03)
    box("Panel", (0.1, 0.12, 0.175), (0.18, 0.10, 0.02), m["grey_plastic"], 0.005)
    for r in range(3):
        for c in range(3):
            box("Key", (0.05 + c * 0.04, 0.10 + r * 0.03, 0.188), (0.025, 0.02, 0.008), m["dark"], 0.0)
    box("Display", (0.1, 0.165, 0.188), (0.14, 0.02, 0.008), m["screen_on"], 0.0)
    box("Tray", (-0.06, -0.12, 0.24), (0.28, 0.16, 0.01), m["grey_plastic"], 0.0, rot=(math.radians(-35), 0, 0))
    box("Paper", (-0.06, -0.13, 0.27), (0.22, 0.14, 0.005), m["white"], 0.0, rot=(math.radians(-35), 0, 0))
    cyl("Handset", (-0.12, 0.08, 0.19), 0.035, 0.30, m["plastic"], rot=(D90, 0, 0), verts=10)
    export("film_fax_machine", (1, 1, 1))

    m = reset()
    box("Stand", (0, 0, 0.2), (0.84, 0.5, 0.4), m["honey_oak"], 0.01)
    box("StandGlass", (0, 0.252, 0.18), (0.7, 0.01, 0.26), m["glass"], 0.0)
    box("VCR", (0, 0.02, 0.445), (0.62, 0.40, 0.09), m["black_plastic"], 0.01)
    box("Slot", (0, 0.222, 0.455), (0.30, 0.01, 0.02), m["dark"], 0.0)
    box("VCRClock", (0.2, 0.222, 0.445), (0.10, 0.01, 0.025), m["lamp_glow"], 0.0)
    tape_parts(m, (0, 0.25, 0.45), rot=(D90, 0, 0))
    crt_parts(m, (0, -0.04, 0.49), on=True, size=0.5)
    for px, py, a in ((0.3, 0.42, 20), (-0.25, 0.38, -50), (0.05, 0.45, 80)):
        tape_parts(m, (px, py, 0), rot=(0, 0, math.radians(a)))
    export("film_tv_vcr", (1, 1, 1))

    m = reset()
    for row in range(3):
        for col in range(3):
            crt_parts(m, (-0.15 + col * 0.66 + (row % 2) * 0.06, -0.1, row * 0.56), on=(row * 3 + col) in (1, 5, 6), size=0.56,
                      shell=("grey_plastic", "black_plastic", "almond_plastic")[(row + col) % 3])
    export("film_tv_wall", (2, 2, 1))


def mannequin(raised=False):
    m = reset()
    box("Base", (0, 0, 0.02), (0.36, 0.36, 0.04), m["chrome"], 0.01)
    cyl("Rod", (0, 0, 0.30), 0.015, 0.56, m["chrome"], verts=8)
    for sx in (-1, 1):
        cyl("Leg", (sx * 0.09, 0, 0.62), 0.065, 0.62, m["skin"], verts=10)
    box("Hips", (0, 0, 0.98), (0.34, 0.20, 0.18), m["skin"], 0.06)
    box("Torso", (0, 0, 1.30), (0.40, 0.22, 0.48), m["skin"], 0.08)
    cyl("Neck", (0, 0, 1.58), 0.045, 0.10, m["skin"], verts=8)
    sphere("Head", (0, 0, 1.73), 0.115, m["skin"], scale=(0.9, 1.0, 1.15))
    for sx in (-1, 1):
        if raised and sx == 1:
            cyl("ArmUp", (0.26, 0.04, 1.62), 0.04, 0.6, m["skin"], rot=(0, math.radians(-20), 0), verts=8)
            sphere("Hand", (0.33, 0.04, 1.93), 0.05, m["skin"], segs=8)
        else:
            cyl("Arm", (sx * 0.25, 0, 1.18), 0.04, 0.60, m["skin"], rot=(0, sx * math.radians(8), 0), verts=8)
            sphere("Hand", (sx * 0.28, 0, 0.86), 0.05, m["skin"], segs=8)
    export("film_mannequin_waving" if raised else "film_mannequin", (1, 2, 1))


def build_laundry():
    m = reset()
    clothes_parts(m, seed=3, count=14, spread=0.30)
    export("film_laundry_pile", (1, 1, 1))
    m = reset()
    cyl("Basket", (0, 0, 0.2), 0.26, 0.4, R.build("BASKET", (0.30, 0.52, 0.56), "plastic", 0.6, 1.0, 0.5, "yellowed"), verts=16)
    clothes_parts(m, (0, 0, 0.36), seed=5, count=7, spread=0.15)
    export("film_laundry_basket", (1, 1, 1))


# ============================================================================================ VHS tapes

def build_vhs():
    m = reset()
    tape_parts(m, rot=(0, 0, math.radians(25)))
    export("film_vhs_tape", (1, 1, 1))

    m = reset()
    rnd = random.Random(11)
    for i in range(7):
        tape_parts(m, (rnd.uniform(-0.35, 0.35), rnd.uniform(-0.35, 0.35), (i % 2) * 0.026), rot=(0, 0, rnd.uniform(0, math.tau)))
    export("film_vhs_scatter", (1, 1, 1))

    for size in (1, 2):
        m = reset()
        rnd = random.Random(20 + size)
        count, radius, height = (45, 0.44, 0.45) if size == 1 else (220, 0.95, 1.3)
        cx, cy = (0.0, 0.0) if size == 1 else (0.5, -0.5)
        for i in range(count):
            t = (i / count) ** 1.6
            r = radius * (1 - t * 0.85) * math.sqrt(rnd.random())
            a = rnd.uniform(0, math.tau)
            tape_parts(m, (cx + math.cos(a) * r, cy + math.sin(a) * r, t * height),
                       rot=(rnd.uniform(-0.4, 0.4), rnd.uniform(-0.4, 0.4), rnd.uniform(0, math.tau)))
        export("film_vhs_pile_small" if size == 1 else "film_vhs_pile_large", (1, 1, 1) if size == 1 else (2, 2, 2))

    m = reset()
    box("Box", (0, 0, 0.18), (0.56, 0.42, 0.34), m["cardboard"], 0.01)
    box("Inside", (0, 0, 0.33), (0.50, 0.36, 0.02), m["dark"], 0.0)
    for i in range(8):
        tape_parts(m, (-0.2 + i * 0.057, 0.0, 0.25), rot=(0, D90, 0))
    box("FlapL", (-0.36, 0, 0.42), (0.16, 0.42, 0.01), m["cardboard"], 0.0, rot=(0, math.radians(-60), 0))
    box("FlapR", (0.36, 0, 0.42), (0.16, 0.42, 0.01), m["cardboard"], 0.0, rot=(0, math.radians(60), 0))
    export("film_vhs_box", (1, 1, 1))

    m = reset()
    box("Back", (0, -0.42, 1.0), (0.96, 0.04, 1.96), m["dark_wood"], 0.01)
    for sx in (-1, 1):
        box("Side", (sx * 0.47, -0.25, 1.0), (0.03, 0.38, 1.96), m["dark_wood"], 0.01)
    rnd = random.Random(31)
    for s in range(6):
        z = 0.06 + s * 0.32
        box("Shelf", (0, -0.25, z), (0.92, 0.36, 0.025), m["pine"], 0.0)
        x = -0.43
        while x < 0.42:
            if rnd.random() < 0.08:
                x += 0.05
                continue
            group([box("Spine", (0, 0, 0.10), (0.03, 0.19, 0.20), m["black_plastic"], 0.002),
                   box("SpineLabel", (0, 0.096, 0.12), (0.022, 0.002, 0.10), m["tape_label"], 0.0)], (x, -0.24, z + 0.012))
            x += 0.034
    export("film_vhs_shelf", (1, 2, 1))


def main():
    for build in (build_sofas, build_recliners, build_chairs, build_wicker_chair, build_throne, build_stool, build_bistro_set,
                  build_dining_set, build_tables, build_storage, build_beds, build_lights, build_decor, build_patio, build_signs,
                  build_furniture_pile, build_chair_pile, build_sofa_pile, build_ottoman_pile, build_boxes, build_electronics,
                  lambda: mannequin(False), lambda: mannequin(True), build_laundry, build_vhs):
        build()
    with open(os.path.join(R.OUT_DIR, "sizes.txt"), "a" if ONLY else "w", encoding="utf8") as f:
        for name, (w, h, d) in R.SIZES.items():
            f.write(f"{name} {w} {h} {d}\n")
    print("FILM PROPS:", len(R.SIZES))


if __name__ == "__main__":
    main()
