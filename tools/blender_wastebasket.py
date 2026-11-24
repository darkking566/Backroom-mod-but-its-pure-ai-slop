"""Builds the wastebasket in Blender from art/props/wastebasket_spec.json and renders a preview.

Run: blender.exe -b -P tools/blender_wastebasket.py   (writes art/props/wastebasket.blend and wastebasket_preview.png)
Units: 16 block pixels = 1 Blender metre / 16 per pixel, centred on the block.
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
spec = json.loads((OUT / "wastebasket_spec.json").read_text(encoding="utf8"))

mats = {}
for tex in {b["texture"] for b in spec}:
    mat = bpy.data.materials.new(tex)
    mat.use_nodes = True
    node = mat.node_tree.nodes.new("ShaderNodeTexImage")
    node.image = bpy.data.images.load(str(TEX / f"{tex}.png"))
    node.interpolation = "Closest"
    shader = mat.node_tree.nodes["Principled BSDF"]
    shader.inputs["Roughness"].default_value = 0.7
    mat.node_tree.links.new(node.outputs["Color"], shader.inputs["Base Color"])
    mats[tex] = mat

for b in spec:
    a, c = b["from"], b["to"]
    bpy.ops.mesh.primitive_cube_add()
    obj = bpy.context.active_object
    obj.name = b["name"]
    # Minecraft is Y-up; Blender is Z-up.
    obj.location = ((a[0] + c[0]) / 2 - 8) * S, -((a[2] + c[2]) / 2 - 8) * S, (a[1] + c[1]) / 2 * S
    obj.scale = ((c[0] - a[0]) / 2 * S, (c[2] - a[2]) / 2 * S, (c[1] - a[1]) / 2 * S)
    obj.data.materials.append(mats[b["texture"]])

# A scrap of floor, a light and a camera for the preview.
bpy.ops.mesh.primitive_plane_add(size=2, location=(0, 0, 0))
bpy.context.active_object.data.materials.append(bpy.data.materials.new("floor"))
bpy.context.active_object.data.materials[0].diffuse_color = (0.35, 0.34, 0.32, 1)
bpy.ops.object.light_add(type="AREA", location=(0.8, -0.8, 1.6))
bpy.context.active_object.data.energy = 300
bpy.ops.object.camera_add(location=(1.1, -1.1, 0.9), rotation=(math.radians(65), 0, math.radians(45)))
bpy.context.scene.camera = bpy.context.active_object
bpy.context.scene.render.engine = "BLENDER_EEVEE"
bpy.context.scene.render.resolution_x = bpy.context.scene.render.resolution_y = 512
bpy.context.scene.render.filepath = str(OUT / "wastebasket_preview.png")
bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "wastebasket.blend"))
print("blender wastebasket done")
