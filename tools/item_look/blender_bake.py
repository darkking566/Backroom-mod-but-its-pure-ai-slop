"""Runs inside Blender (headless): bakes ambient occlusion and soft light for box models laid out on per-item atlases.

    blender -b --factory-startup -P tools/item_look/blender_bake.py -- job.json out_dir

job.json: {"size": 128, "items": [{"id": ..., "faces": [{"corners": [[x, y, z] * 4], "uv": [[u, v] * 4], "normal": [x, y, z]}]}]}
Corners are in Minecraft model units (y up); uv in atlas pixels, v downward. Writes <id>_ao.png and <id>_light.png.
"""
import json
import math
import sys

import bmesh
import bpy

args = sys.argv[sys.argv.index("--") + 1:]
job = json.load(open(args[0], encoding="utf8"))
out_dir = args[1]
size = job["size"]

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 96
scene.render.bake.margin = 2
world = bpy.data.worlds.new("world")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.32, 0.32, 0.32, 1)
world.light_settings.distance = 3.0
sun_data = bpy.data.lights.new("sun", "SUN")
sun_data.energy = 3.5
sun_data.angle = math.radians(35)
sun = bpy.data.objects.new("sun", sun_data)
sun.rotation_euler = (math.radians(40), math.radians(-25), math.radians(30))
scene.collection.objects.link(sun)


def to_blender(p):
    """Minecraft (x, y up, z south) to Blender (x, y north, z up): a proper rotation, so shapes and normals keep."""
    return (p[0] / 16.0, -p[2] / 16.0, p[1] / 16.0)


def bake(obj, image, kind):
    mat = obj.data.materials[0]
    nodes = mat.node_tree.nodes
    tex = nodes["target"]
    tex.image = image
    nodes.active = tex
    for o in scene.objects:
        o.select_set(False)
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    if kind == "AO":
        bpy.ops.object.bake(type="AO")
    else:
        scene.render.bake.use_pass_direct = True
        scene.render.bake.use_pass_indirect = True
        scene.render.bake.use_pass_color = False
        bpy.ops.object.bake(type="DIFFUSE")


for item in job["items"]:
    mesh = bpy.data.meshes.new(item["id"])
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap")
    for face in item["faces"]:
        verts = [bm.verts.new(to_blender(c)) for c in face["corners"]]
        uvs = [(u / size, 1.0 - v / size) for u, v in face["uv"]]
        # Keep the face's front where its normal says, whatever order the corners came in.
        a, b, c = (verts[i].co for i in range(3))
        n = (b - a).cross(c - a)
        want = to_blender(face["normal"])
        if n.x * want[0] + n.y * want[1] + n.z * want[2] < 0:
            verts.reverse()
            uvs.reverse()
        f = bm.faces.new(verts)
        for loop, uv in zip(f.loops, uvs):
            loop[uv_layer].uv = uv
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(item["id"], mesh)
    scene.collection.objects.link(obj)
    mat = bpy.data.materials.new(item["id"])
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (0.8, 0.8, 0.8, 1)
    bsdf.inputs["Roughness"].default_value = 1.0
    tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
    tex.name = "target"
    mesh.materials.append(mat)
    for kind in ("AO", "LIGHT"):
        image = bpy.data.images.new(f"{item['id']}_{kind}", size, size, alpha=False)
        bake(obj, image, kind)
        image.filepath_raw = f"{out_dir}/{item['id']}_{kind.lower()}.png"
        image.file_format = "PNG"
        image.save()
    bpy.data.objects.remove(obj)
print("BAKED", len(job["items"]))
