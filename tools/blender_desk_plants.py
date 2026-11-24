"""Builds the two desk plants in Blender from art/props/desk_plants_spec.json and renders a preview.

Run: blender.exe -b -P tools/blender_desk_plants.py   (writes art/props/desk_plants.blend and desk_plants_preview.png)
"""
import json
import math
from pathlib import Path

import bpy

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art/props"
TEX = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level1"
S = 1 / 16

bpy.ops.wm.read_factory_settings(use_empty=True)
spec = json.loads((OUT / "desk_plants_spec.json").read_text(encoding="utf8"))


def material(tex, alpha=False):
    mat = bpy.data.materials.new(tex)
    mat.use_nodes = True
    node = mat.node_tree.nodes.new("ShaderNodeTexImage")
    node.image = bpy.data.images.load(str(TEX / f"{tex}.png"))
    node.interpolation = "Closest"
    shader = mat.node_tree.nodes["Principled BSDF"]
    mat.node_tree.links.new(node.outputs["Color"], shader.inputs["Base Color"])
    if alpha:
        mat.node_tree.links.new(node.outputs["Alpha"], shader.inputs["Alpha"])
    return mat


def cube(a, c, mat, dx):
    bpy.ops.mesh.primitive_cube_add()
    o = bpy.context.active_object
    o.location = (((a[0] + c[0]) / 2 - 8) * S + dx, -((a[2] + c[2]) / 2 - 8) * S, (a[1] + c[1]) / 2 * S)
    o.scale = ((c[0] - a[0]) / 2 * S, (c[2] - a[2]) / 2 * S, (c[1] - a[1]) / 2 * S)
    o.data.materials.append(mat)


for n, (block, plant) in enumerate(spec["plants"].items()):
    dx = (n - 0.5) * 0.45
    pot_mats = {}
    for b in spec["pot"]:
        mat = pot_mats.setdefault(b["texture"], material(b["texture"]))
        cube(b["from"], b["to"], mat, dx)
    plant_mat = material(plant["texture"], alpha=True)
    for angle in (45, -45):
        height = (plant["y1"] - plant["y0"]) * S
        bpy.ops.mesh.primitive_plane_add(size=1)
        o = bpy.context.active_object
        o.scale = (14.4 * S, height, 1)
        o.rotation_euler = (math.radians(90), 0, math.radians(angle))
        o.location = (dx, 0, plant["y0"] * S + height / 2)
        o.data.materials.append(plant_mat)

bpy.ops.mesh.primitive_plane_add(size=3, location=(0, 0, 0))
bpy.context.active_object.data.materials.append(bpy.data.materials.new("desk"))
bpy.context.active_object.data.materials[0].diffuse_color = (0.45, 0.33, 0.22, 1)
bpy.ops.object.light_add(type="AREA", location=(0.6, -0.8, 1.5))
bpy.context.active_object.data.energy = 250
bpy.ops.object.camera_add(location=(0.0, -1.8, 0.7), rotation=(math.radians(78), 0, 0))
bpy.context.scene.camera = bpy.context.active_object
bpy.context.scene.render.engine = "BLENDER_EEVEE"
bpy.context.scene.render.resolution_x, bpy.context.scene.render.resolution_y = 768, 512
bpy.context.scene.render.filepath = str(OUT / "desk_plants_preview.png")
bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "desk_plants.blend"))
print("blender desk plants done")
