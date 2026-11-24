"""Run inside Blender (blender -b -P tools/blender_robots.py) or with the bpy module (python tools/blender_robots.py):
builds each robot from art/robots/robots_spec.json, textures it with the generated PNGs, saves art/robots/robots.blend
and renders one preview per robot. Rest pose only; the animation clips live in the spec and are played by the game (BoxRig).
"""
import json
import math
from pathlib import Path

import bpy
from mathutils import Vector

ROOT = Path(bpy.data.filepath).resolve().parent.parent if bpy.data.filepath else Path(__file__).resolve().parent.parent
ART = ROOT / "art" / "robots"
TEX = ROOT / "src/main/resources/assets/noclip_backrooms/textures/entity/robots"
spec = json.loads((ART / "robots_spec.json").read_text(encoding="utf8"))
W = spec["texture_atlas"]["width"]  # UVs are in model pixels; the PNG is finer but normalised the same


def to_blender(x, y, z):
    """Minecraft model pixels (y down, feet at 24, facing -z) to Blender metres (z up, facing -y)."""
    return Vector((x / 16, z / 16, (24 - y) / 16))


def make_box(name, origin, size, uv, joint_world):
    x, y, z = origin
    w, h, d = size
    c = lambda dx, dy, dz: to_blender(joint_world[0] + x + dx, joint_world[1] + y + dy, joint_world[2] + z + dz)
    corners = {(i, j, k): c(i * w, j * h, k * d) for i in (0, 1) for j in (0, 1) for k in (0, 1)}
    quads = {  # face: (corners counter-clockwise seen from outside, pixel rect in the atlas)
        "front": ([(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)], (d, d, w, h)),
        "back": ([(1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)], (d + w + d, d, w, h)),
        "left": ([(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)], (0, d, d, h)),
        "right": ([(0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)], (d + w, d, d, h)),
        "top": ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (d, 0, w, d)),
        "bottom": ([(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)], (d + w, 0, w, d)),
    }
    verts, faces, uvs = [], [], []
    for face, (ring, (a, b, fw, fh)) in quads.items():
        base = len(verts)
        verts += [corners[k] for k in ring]
        faces.append((base, base + 1, base + 2, base + 3))
        u0, v0 = uv[0] + a, uv[1] + b
        u1, v1 = u0 + fw, v0 + fh
        rect = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]  # atlas v runs down; Blender's runs up
        uvs += [(px / W, 1 - py / W) for px, py in rect]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(v) for v in verts], [], faces)
    layer = mesh.uv_layers.new(name="UV")
    for i, uv_xy in enumerate(uvs):
        layer.data[i].uv = uv_xy
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    return obj


def build(kind, offset=0.0):
    image = bpy.data.images.load(str(TEX / f"{kind}.png"), check_existing=False)
    image.colorspace_settings.name = "sRGB"
    mat = bpy.data.materials.new(f"mat_{kind}")
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = image
    tex.interpolation = "Closest"
    bsdf = next((n for n in nodes if n.type == "BSDF_PRINCIPLED"), None) or nodes.new("ShaderNodeBsdfPrincipled")
    out = next((n for n in nodes if n.type == "OUTPUT_MATERIAL"), None) or nodes.new("ShaderNodeOutputMaterial")
    mat.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    mat.node_tree.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    bsdf.inputs["Roughness"].default_value = 0.55
    bsdf.inputs["Metallic"].default_value = 0.35
    pivots = {p["name"]: p for p in spec["parts"]}

    def joint_world(name):
        if name == "root":
            return (0.0, 0.0, 0.0)
        part = pivots[name]
        px, py, pz = joint_world(part["parent"])
        return (px + part["pivot_mc"][0], py + part["pivot_mc"][1], pz + part["pivot_mc"][2])

    visible = set(spec["looks"][kind])
    made = []
    for part in spec["parts"]:
        if part["name"] not in visible:
            continue
        jw = joint_world(part["name"])
        for i, cube in enumerate(part["cubes"]):
            obj = make_box(f"{kind}_{part['name']}_{i}", cube["origin_mc"], cube["size_mc"], cube["uv_region"], jw)
            obj.data.materials.append(mat)
            obj.location.x += offset
            made.append(obj)
    return made


def stage():
    scene = bpy.context.scene
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 24
    scene.render.resolution_x, scene.render.resolution_y = 600, 700
    world = bpy.data.worlds.new("w") if not scene.world else scene.world
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.12, 0.15, 0.2, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.9
    floor = bpy.data.objects.new("floor", bpy.data.meshes.new("floor"))
    floor.data.from_pydata([(-3, -3, 0), (3, -3, 0), (3, 3, 0), (-3, 3, 0)], [], [(0, 1, 2, 3)])
    bpy.context.collection.objects.link(floor)
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.5
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(-35))
    bpy.context.collection.objects.link(sun)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.location = (1.5, -2.6, 1.2)
    cam.rotation_euler = (math.radians(80), 0, math.radians(30))
    cam.data.lens = 50
    bpy.context.collection.objects.link(cam)
    scene.camera = cam


stage()
# One preview each, then all three side by side in the saved file.
for kind in spec["looks"]:
    made = build(kind)
    bpy.context.scene.render.filepath = str(ART / f"preview_{kind}.png")
    bpy.ops.render.render(write_still=True)
    for obj in made:
        bpy.data.objects.remove(obj, do_unlink=True)
for i, kind in enumerate(spec["looks"]):
    build(kind, offset=(i - 1) * 1.4)
bpy.ops.wm.save_as_mainfile(filepath=str(ART / "robots.blend"))
print("robots done")
