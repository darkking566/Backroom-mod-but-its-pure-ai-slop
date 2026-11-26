"""Inventory icons for the film props: each baked OBJ + texture, seen from the front left and above, on transparent.

Run: blender -b --python make_film_icons.py -- <obj_dir> <icon_dir> [size]
"""
import glob
import math
import os
import sys

import bpy
from mathutils import Vector

ARGS = sys.argv[sys.argv.index("--") + 1:]
SRC, DST = os.path.abspath(ARGS[0]), os.path.abspath(ARGS[1])
SIZE = int(ARGS[2]) if len(ARGS) > 2 else 64
os.makedirs(DST, exist_ok=True)

for path in sorted(glob.glob(os.path.join(SRC, "*.obj"))):
    name = os.path.splitext(os.path.basename(path))[0]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.wm.obj_import(filepath=path, forward_axis="NEGATIVE_Z", up_axis="Y")
    obj = bpy.context.selected_objects[0]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(os.path.join(SRC, "textures", f"{name}.png"))
    tex.interpolation = "Closest"
    bsdf = next(n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    mat.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    shading = scene.display.shading
    shading.light = "STUDIO"
    shading.color_type = "TEXTURE"
    shading.show_specular_highlight = False
    shading.show_cavity = True
    shading.cavity_type = "WORLD"
    scene.render.film_transparent = True
    scene.render.resolution_x = scene.render.resolution_y = SIZE * 4
    scene.view_settings.view_transform = "Standard"
    corners = [obj.matrix_world @ Vector(c) for c in obj.bound_box]
    lo = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    hi = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    centre = (lo + hi) / 2
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    direction = Vector((-0.6, 0.9, 0.55)).normalized()
    cam.location = centre + direction * 20
    cam.rotation_euler = (-direction).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    bpy.context.view_layer.update()
    # Fit: project the box corners onto the camera plane and take the larger extent.
    inv = cam.matrix_world.inverted()
    pts = [inv @ c for c in corners]
    span = max(max(p.x for p in pts) - min(p.x for p in pts), max(p.y for p in pts) - min(p.y for p in pts))
    cam_data.ortho_scale = span * 1.08
    mid = Vector(((max(p.x for p in pts) + min(p.x for p in pts)) / 2, (max(p.y for p in pts) + min(p.y for p in pts)) / 2, 0))
    cam.location = cam.matrix_world @ mid + direction * 0
    scene.render.filepath = os.path.join(DST, f"{name}_big.png")
    bpy.ops.render.render(write_still=True)
    big = bpy.data.images.load(scene.render.filepath)
    big.scale(SIZE, SIZE)
    big.filepath_raw = os.path.join(DST, f"{name}.png")
    big.file_format = "PNG"
    big.save()
    os.remove(scene.render.filepath)
print("ICONS DONE")
