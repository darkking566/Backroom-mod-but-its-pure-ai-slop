"""Runs inside Blender (headless): models items with real shapes (bevelled edges, round parts, a blade's cross-section,
cut grooves), unwraps them, bakes colour, ambient occlusion and the labPBR specular values, and exports OBJ.

    blender -b --factory-startup -P tools/item_look/blender_mesh.py -- job.json out_dir

job.json: {"size": 256, "materials": {name: {"png": path, "pbr": [smooth, f0, porosity]}},
           "items": [{"id": ..., "shift": [dx, dy, dz]}]}
Shapes are written in Minecraft model units (sixteenths of a block, y up) at the same places as the box models in
tools/item_look (so the poses in hand and the sight lines carry over), then moved by `shift` like those were.
Writes <id>.obj, <id>_color.png, <id>_ao.png, <id>_spec.png.
"""
import json
import math
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

args = sys.argv[sys.argv.index("--") + 1:]
job = json.load(open(args[0], encoding="utf8"))
OUT = args[1]
SIZE = job["size"]
SHIFT = [0.0, 0.0, 0.0]

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.render.bake.margin = 4
world = bpy.data.worlds.new("world")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.3, 0.3, 0.3, 1)
world.light_settings.distance = 0.2

# ---------------------------------------------------------------- materials

MATS = {}


def material(name):
    if name in MATS:
        return MATS[name]
    spec = job["materials"][name]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    coord = nt.nodes.new("ShaderNodeTexCoord")
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(spec["png"])
    tex.projection = "BOX"
    tex.projection_blend = 0.25
    tex.interpolation = "Closest"
    nt.links.new(coord.outputs["Object"], tex.inputs["Vector"])
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 1.0
    smooth, f0, porosity = spec["pbr"][:3]
    bsdf.inputs["Emission Color"].default_value = (smooth, f0 / 255.0, porosity * 64 / 255.0, 1)
    bsdf.inputs["Emission Strength"].default_value = 1.0
    MATS[name] = mat
    return mat


# ---------------------------------------------------------------- geometry helpers (Minecraft sixteenths, y up)


def V(x, y, z):
    return Vector(((x + SHIFT[0]) / 16.0, -(z + SHIFT[2]) / 16.0, (y + SHIFT[1]) / 16.0))


def rot_matrix(rot):
    """A Minecraft element-style rotation (axis, degrees, origin) as a Blender matrix."""
    if not rot:
        return Matrix.Identity(4)
    axis, angle, origin = rot
    o = V(*origin)
    a = math.radians(angle)
    r = {"x": Matrix.Rotation(a, 4, "X"), "y": Matrix.Rotation(a, 4, "Z"), "z": Matrix.Rotation(-a, 4, "Y")}[axis]
    return Matrix.Translation(o) @ r @ Matrix.Translation(-o)


PARTS = []


def finish(bm, mat, bevel=0.0, segments=2, rot=None, name="part", cut=()):
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    mesh.transform(rot_matrix(rot))
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    if isinstance(mat, str):
        obj.data.materials.append(material(mat))
    else:
        for m in mat:
            obj.data.materials.append(material(m))
    if bevel:
        mod = obj.modifiers.new("bevel", "BEVEL")
        mod.width = bevel / 16.0
        mod.segments = segments
        mod.limit_method = "ANGLE"
        mod.harden_normals = False
    for c in cut:
        mod = obj.modifiers.new("cut", "BOOLEAN")
        mod.operation = "DIFFERENCE"
        mod.solver = "EXACT"
        mod.material_mode = "INDEX"
        mod.object = c
    PARTS.append(obj)
    return obj


def box(frm, to, mat, bevel=0.15, segments=2, rot=None, cut=()):
    bm = bmesh.new()
    x0, y0, z0 = frm
    x1, y1, z1 = to
    corners = [V(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    vs = [bm.verts.new(c) for c in corners]
    for f in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
        bm.faces.new([vs[i] for i in f])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return finish(bm, mat, bevel, segments, rot, cut=cut)


def cutter(frm, to, rot=None):
    bm = bmesh.new()
    x0, y0, z0 = frm
    x1, y1, z1 = to
    vs = [bm.verts.new(V(x, y, z)) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    for f in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
        bm.faces.new([vs[i] for i in f])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh = bpy.data.meshes.new("cutter")
    bm.to_mesh(mesh)
    bm.free()
    mesh.transform(rot_matrix(rot))
    obj = bpy.data.objects.new("cutter", mesh)
    scene.collection.objects.link(obj)
    obj.hide_render = True
    obj.hide_set(True)
    return obj


def cylinder(centre, radius, length, axis, mat, segments=16, bevel=0.0, rot=None, radius2=None):
    """A cylinder along a Minecraft axis ('x', 'y' or 'z'), centred on `centre`."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=segments, radius1=radius / 16.0,
                          radius2=(radius if radius2 is None else radius2) / 16.0, depth=length / 16.0)
    turn = {"y": Matrix.Identity(4), "x": Matrix.Rotation(math.radians(90), 4, "Y"), "z": Matrix.Rotation(math.radians(90), 4, "X")}[axis]
    bmesh.ops.transform(bm, matrix=Matrix.Translation(V(*centre)) @ turn, verts=bm.verts)
    return finish(bm, mat, bevel, 2, rot)


def lathe(points, mat, segments=16, rot=None, axis_x=8.0, axis_z=8.0):
    """A turned shape about a vertical axis: points are (radius, y) from bottom to top."""
    bm = bmesh.new()
    rings = []
    for r, y in points:
        ring = []
        for k in range(segments):
            a = 2 * math.pi * k / segments
            ring.append(bm.verts.new(V(axis_x + math.cos(a) * r, y, axis_z + math.sin(a) * r)))
        rings.append(ring)
    for a, b in zip(rings, rings[1:]):
        for k in range(segments):
            bm.faces.new([a[k], a[(k + 1) % segments], b[(k + 1) % segments], b[k]])
    bm.faces.new(list(reversed(rings[0])))
    bm.faces.new(rings[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return finish(bm, mat, 0, 2, rot)


# ---------------------------------------------------------------- the items


def gun_pistol_9mm():
    serr = [cutter((6.95, 8.9, z), (7.22, 10.15, z + 0.2)) for z in (4.9, 5.4, 5.9, 6.4, 6.9)]
    serr += [cutter((8.78, 8.9, z), (9.05, 10.15, z + 0.2)) for z in (4.9, 5.4, 5.9, 6.4, 6.9)]
    port = cutter((8.55, 9.35, 0.2), (9.2, 10.6, 2.8))
    box((7.1, 8.6, -4.0), (8.9, 10.4, 7.4), "gun_black", bevel=0.22, cut=serr + [port])
    cylinder((8.0, 9.55, -4.05), 0.45, 0.3, "z", "steel_dark", 16, 0.05)
    cylinder((8.0, 9.55, -4.2), 0.22, 0.1, "z", "wire_black", 12)
    cylinder((8.0, 9.55, 1.5), 0.42, 2.4, "z", "steel_worn", 12)
    rail = [cutter((7.1, 7.3, z), (8.9, 7.55, z + 0.3)) for z in (-3.0, -2.2, -1.4)]
    box((7.2, 7.4, -3.4), (8.8, 8.6, 6.6), "gun_polymer", bevel=0.2, cut=rail)
    inner = cutter((7.5, 6.65, 0.85), (8.5, 7.5, 3.45))
    box((7.62, 6.35, 0.55), (8.38, 7.45, 3.75), "gun_polymer", bevel=0.18, cut=[inner])
    box((7.86, 6.75, 2.0), (8.14, 7.45, 2.35), "steel_dark", bevel=0.06, rot=("x", -15, (8, 7.45, 2.2)))
    grip = ("x", 16, (8, 7.6, 5.6))
    box((7.15, 2.3, 4.3), (8.85, 7.6, 6.9), "gun_polymer", bevel=0.45, segments=3, rot=grip)
    box((7.1, 2.0, 4.15), (8.9, 2.6, 7.0), "gun_black", bevel=0.12, rot=grip)
    box((7.45, 7.1, 6.5), (8.55, 7.9, 7.6), "gun_polymer", bevel=0.3, segments=3)
    box((7.8, 10.35, -3.7), (8.2, 10.9, -3.15), "gun_black", bevel=0.08)
    box((7.2, 10.35, 6.55), (8.8, 10.6, 7.15), "gun_black", bevel=0.06)
    box((7.3, 10.6, 6.55), (7.82, 11.0, 7.15), "gun_black", bevel=0.06)
    box((8.18, 10.6, 6.55), (8.7, 11.0, 7.15), "gun_black", bevel=0.06)
    box((7.0, 8.35, 1.4), (7.12, 8.9, 3.2), "steel_dark", bevel=0.04)


def west_arming_sword():
    # The blade: a lens-shaped section with a fuller, narrowing to a point; bright bevelled edges.
    bm = bmesh.new()
    sections = []
    steps = 18
    for i in range(steps + 1):
        f = i / steps
        y = 7.0 + f * 21.0
        width = 2.4 * (1 - 0.25 * f) * (1 - max(0, (f - 0.78) / 0.22) ** 1.6)
        thick = 0.55 * (1 - 0.55 * f)
        half = max(width / 2, 0.02)
        fuller = 0.32 if f < 0.62 else 0.0
        prof = [(-half, 0.0), (-half * 0.62, thick * 0.5), (-fuller, thick * 0.44), (fuller, thick * 0.44), (half * 0.62, thick * 0.5),
                (half, 0.0), (half * 0.62, -thick * 0.5), (fuller, -thick * 0.44), (-fuller, -thick * 0.44), (-half * 0.62, -thick * 0.5)]
        sections.append([bm.verts.new(V(8 + px, y, 8 + pz)) for px, pz in prof])
    mats = [1, 0, 2, 0, 1, 1, 0, 2, 0, 1]
    for a, b in zip(sections, sections[1:]):
        n = len(a)
        for k in range(n):
            face = bm.faces.new([a[k], a[(k + 1) % n], b[(k + 1) % n], b[k]])
            face.material_index = mats[k]
    bm.faces.new(list(reversed(sections[0])))
    tip = bm.verts.new(V(8, 28.6, 8))
    last = sections[-1]
    for k in range(len(last)):
        face = bm.faces.new([last[k], last[(k + 1) % len(last)], tip])
        face.material_index = 1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    finish(bm, ["blade", "blade_edge", "blade_dark"], name="blade")
    # The cross-guard, its ends turned down a little, with a centre block.
    box((5.4, 6.0, 7.45), (10.6, 6.95, 8.55), "steel_dark", bevel=0.25, segments=3)
    box((3.8, 6.0, 7.5), (5.6, 6.9, 8.5), "steel_dark", bevel=0.25, segments=3, rot=("z", -12, (5.5, 6.4, 8)))
    box((10.4, 6.0, 7.5), (12.2, 6.9, 8.5), "steel_dark", bevel=0.25, segments=3, rot=("z", 12, (10.5, 6.4, 8)))
    box((7.2, 5.8, 7.25), (8.8, 7.15, 8.75), "steel_dark", bevel=0.3, segments=3)
    # The grip: leather over wood, cord turns, a ferrule at each end.
    lathe([(0.6, 0.6), (0.7, 1.2), (0.72, 3.3), (0.7, 5.4), (0.62, 6.0)], "leather_brown", 12)
    for y in (1.4, 2.2, 3.0, 3.8, 4.6):
        lathe([(0.73, y), (0.8, y + 0.12), (0.73, y + 0.24)], "cord_black", 12)
    lathe([(0.78, 5.6), (0.82, 5.85), (0.78, 6.05)], "steel_dark", 12)
    lathe([(0.66, 0.35), (0.8, 0.5), (0.76, 0.75)], "steel_dark", 12)
    # A wheel pommel and its peen.
    cylinder((8.0, -0.15, 8.0), 1.05, 0.95, "z", "steel_dark", 20, 0.22)
    cylinder((8.0, 0.55, 8.0), 0.35, 0.4, "y", "steel_dark", 10, 0.05)


def prop_office_chair():
    cylinder((8, 1.5, 8), 1.2, 1.0, "y", "dark_plastic", 16, 0.15)
    for k in range(5):
        a = k * 72 + 18
        leg = ("y", 0, (8, 1, 8))
        r = math.radians(a)
        obj = box((8.6, 1.0, 7.55), (13.0, 1.65, 8.45), "dark_plastic", bevel=0.2, segments=2)
        obj.data.transform(Matrix.Translation(V(8, 1.3, 8)) @ Matrix.Rotation(r, 4, "Z") @ Matrix.Translation(-V(8, 1.3, 8)))
        x, z = 8 + math.cos(r) * 4.8, 8 - math.sin(r) * 4.8
        cylinder((x, 0.55, z), 0.55, 0.5, "x", "rubber_black", 14, 0.06)
        box((x - 0.25, 0.8, z - 0.3), (x + 0.25, 1.4, z + 0.3), "dark_plastic", bevel=0.08)
    lathe([(0.78, 2.0), (0.78, 2.4), (0.7, 2.5), (0.78, 2.6), (0.7, 2.7), (0.78, 2.8), (0.7, 2.9), (0.78, 3.0), (0.72, 3.6)], "dark_plastic", 16)
    lathe([(0.52, 3.6), (0.52, 5.6)], "chrome_worn", 16)
    box((4.6, 5.55, 4.6), (11.4, 6.25, 11.4), "dark_plastic", bevel=0.2)
    box((3.4, 6.2, 3.2), (12.6, 8.1, 12.2), "office_fabric", bevel=0.8, segments=4)
    back = ("x", -10, (8, 9.2, 12.7))
    box((7.2, 6.0, 11.4), (8.8, 10.2, 12.4), "dark_plastic", bevel=0.2, rot=back)
    box((3.8, 9.2, 11.8), (12.2, 16.0, 13.6), "office_fabric", bevel=0.75, segments=4, rot=back)
    box((4.0, 9.4, 13.4), (12.0, 15.8, 14.0), "dark_plastic", bevel=0.25, rot=back)
    for x0 in (3.0, 12.2):
        box((x0, 6.6, 6.6), (x0 + 0.8, 10.4, 7.6), "dark_plastic", bevel=0.2)
        box((x0 - 0.2, 10.35, 5.3), (x0 + 1.0, 11.2, 10.3), "dark_plastic", bevel=0.35, segments=3)


BUILDERS = {f.__name__: f for f in (gun_pistol_9mm, west_arming_sword, prop_office_chair)}

# ---------------------------------------------------------------- bake and export


def bake(obj, image, kind):
    for mat in obj.data.materials:
        if mat is None:
            continue
        nodes = mat.node_tree.nodes
        tex = nodes.get("bake_target") or nodes.new("ShaderNodeTexImage")
        tex.name = "bake_target"
        tex.image = image
        nodes.active = tex
    for o in scene.objects:
        o.select_set(False)
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    if kind == "color":
        scene.cycles.samples = 8
        scene.render.bake.use_pass_direct = False
        scene.render.bake.use_pass_indirect = False
        scene.render.bake.use_pass_color = True
        bpy.ops.object.bake(type="DIFFUSE")
    elif kind == "ao":
        scene.cycles.samples = 96
        bpy.ops.object.bake(type="AO")
    else:
        scene.cycles.samples = 4
        bpy.ops.object.bake(type="EMIT")


for item in job["items"]:
    SHIFT[:] = item["shift"]
    PARTS.clear()
    BUILDERS[item["id"]]()
    for o in scene.objects:
        o.select_set(False)
    for p in PARTS:
        p.select_set(True)
    bpy.context.view_layer.objects.active = PARTS[0]
    bpy.ops.object.convert(target="MESH")
    bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    obj.name = item["id"]
    for o in list(scene.objects):
        if o.name.startswith("cutter"):
            bpy.data.objects.remove(o)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=0.02)
    bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.shade_smooth_by_angle(angle=math.radians(40))
    for kind in ("color", "ao", "spec"):
        image = bpy.data.images.new(f"{item['id']}_{kind}", SIZE, SIZE, alpha=False)
        bake(obj, image, kind)
        image.filepath_raw = f"{OUT}/{item['id']}_{kind}.png"
        image.file_format = "PNG"
        image.save()
    for o in scene.objects:
        o.select_set(False)
    obj.select_set(True)
    bpy.ops.wm.obj_export(filepath=f"{OUT}/{item['id']}.obj", export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=False, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y",
                          apply_modifiers=True)
    bpy.data.objects.remove(obj)
print("MESHED", len(job["items"]))
