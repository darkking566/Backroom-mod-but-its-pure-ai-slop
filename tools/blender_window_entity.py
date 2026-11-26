"""Run inside Blender 5.3 (blender -b -P tools/blender_window_entity.py):
Builds the complete articulated WindowEntity ("Entity 2") rig from
art/monsters/window_entity/window_entity_minecraft_spec.json, attaches UV-mapped
sculpted boxes + weathered 512x512 atlas, applies beckon_lure keyframes, saves
window_entity.blend, exports window_entity.glb, and renders window_entity_preview.png.
"""
import json
import math
from pathlib import Path

import bpy
from mathutils import Euler, Vector

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "art/monsters/window_entity"
SPEC = ART / "window_entity_minecraft_spec.json"
TEX = ART / "window_entity.png"

spec = json.loads(SPEC.read_text(encoding="utf8"))
W = spec["texture_atlas"]["width"]
H = spec["texture_atlas"]["height"]


def mc_pos_to_blender(x, y, z, root_ground=False):
    if root_ground:
        return Vector((x / 16.0, z / 16.0, (24.0 - y) / 16.0))
    return Vector((x / 16.0, z / 16.0, -y / 16.0))


def mc_rot_to_blender(rx, ry, rz):
    # MC (X, Y_down, Z_back) -> Blender (X, Z_back, -Y_down): det = +1
    return Euler((rx, rz, -ry), "XYZ")


def make_box(name, origin, size, uv):
    x, y, z = origin
    w, h, d = size
    c = lambda dx, dy, dz: mc_pos_to_blender(x + dx, y + dy, z + dz, False)
    corners = {(i, j, k): c(i * w, j * h, k * d) for i in (0, 1) for j in (0, 1) for k in (0, 1)}
    quads = {
        "front": ([(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)], (d, d, w, h)),
        "back": ([(1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)], (d + w + d, d, w, h)),
        "left": ([(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)], (0, d, d, h)),
        "right": ([(0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)], (d + w, d, d, h)),
        "top": ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (d, 0, w, d)),
        "bottom": ([(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)], (d + w, 0, w, d)),
    }
    verts, faces, uvs = [], [], []
    for _, (ring, (a, b, fw, fh)) in quads.items():
        base = len(verts)
        verts += [corners[k] for k in ring]
        faces.append((base, base + 1, base + 2, base + 3))
        u0, v0 = uv[0] + a, uv[1] + b
        u1, v1 = u0 + fw, v0 + fh
        rect = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        uvs += [(px / W, 1.0 - py / H) for px, py in rect]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(v) for v in verts], [], faces)
    layer = mesh.uv_layers.new(name="UV")
    for i, uv_xy in enumerate(uvs):
        layer.data[i].uv = uv_xy
    obj = bpy.data.objects.new(name, mesh)
    return obj


def clear_scene():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for mesh in list(bpy.data.meshes):
        bpy.data.meshes.remove(mesh)
    for mat in list(bpy.data.materials):
        bpy.data.materials.remove(mat)


def build_rig():
    clear_scene()
    col = bpy.context.scene.collection

    image = bpy.data.images.load(str(TEX), check_existing=False)
    image.colorspace_settings.name = "sRGB"
    mat = bpy.data.materials.new("mat_window_entity")
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = image
    tex.interpolation = "Closest"
    bsdf = next((n for n in nodes if n.type == "BSDF_PRINCIPLED"), None) or nodes.new("ShaderNodeBsdfPrincipled")
    out = next((n for n in nodes if n.type == "OUTPUT_MATERIAL"), None) or nodes.new("ShaderNodeOutputMaterial")
    mat.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    mat.node_tree.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    bsdf.inputs["Roughness"].default_value = 0.85

    root_empty = bpy.data.objects.new("WindowEntity_Root", None)
    root_empty.empty_display_size = 0.2
    col.objects.link(root_empty)

    joints = {"root": root_empty}
    pending = list(spec["parts"])
    while pending:
        for p in list(pending):
            if p["parent"] not in joints:
                continue
            parent_obj = joints[p["parent"]]
            j_obj = bpy.data.objects.new(p["name"], None)
            j_obj.empty_display_size = 0.08
            j_obj.rotation_mode = "XYZ"
            px, py, pz = p["pivot_mc"]
            j_obj.location = mc_pos_to_blender(px, py, pz, root_ground=(p["parent"] == "root"))
            rx, ry, rz = p["rest_rot_rad_mc"]
            j_obj.rotation_euler = mc_rot_to_blender(rx, ry, rz)
            col.objects.link(j_obj)
            j_obj.parent = parent_obj
            joints[p["name"]] = j_obj

            for idx, c in enumerate(p["cubes"]):
                box = make_box(f"{p['name']}_box_{idx}", c["origin_mc"], c["size_mc"], c["uv_region"])
                box.data.materials.append(mat)
                col.objects.link(box)
                if c.get("needs_subpart"):
                    sub = bpy.data.objects.new(f"{p['name']}_sub_{idx}", None)
                    sub.rotation_mode = "XYZ"
                    ox, oy, oz = c["subpart_offset_mc"]
                    srx, sry, srz = c["subpart_rot_rad_mc"]
                    sub.location = mc_pos_to_blender(ox, oy, oz, False)
                    sub.rotation_euler = mc_rot_to_blender(srx, sry, srz)
                    col.objects.link(sub)
                    sub.parent = j_obj
                    box.parent = sub
                else:
                    box.parent = j_obj
            pending.remove(p)

    # Apply beckon_lure keyframes at 24fps
    fps = 24
    bpy.context.scene.render.fps = fps
    clip = spec["animations"]["beckon_lure"]
    for kf in clip["keyframes"]:
        frame = int(round(kf["time_sec"] * fps)) + 1
        bpy.context.scene.frame_set(frame)
        for jname, data in kf["joints"].items():
            obj = joints.get(jname)
            if obj is None:
                continue
            rx, ry, rz = data["rot_rad_mc"]
            obj.rotation_euler = mc_rot_to_blender(rx, ry, rz)
            obj.keyframe_insert(data_path="rotation_euler", frame=frame)
            if "pos_mc" in data:
                px, py, pz = data["pos_mc"]
                obj.location = mc_pos_to_blender(px, py, pz, root_ground=True)
                obj.keyframe_insert(data_path="location", frame=frame)
    bpy.context.scene.frame_start = 1
    bpy.context.scene.frame_end = int(round(clip["keyframes"][-1]["time_sec"] * fps)) + 1
    bpy.context.scene.frame_set(15)
    return root_empty


def setup_stage_and_render():
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 768
    scene.render.resolution_y = 960
    scene.render.film_transparent = False
    world = bpy.data.worlds.new("MistWorld") if not scene.world else scene.world
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.48, 0.53, 0.57, 1.0)
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.9

    floor = bpy.data.objects.new("StudioFloor", bpy.data.meshes.new("StudioFloor"))
    floor.data.from_pydata([(-3, -3, 0), (3, -3, 0), (3, 3, 0), (-3, 3, 0)], [], [(0, 1, 2, 3)])
    scene.collection.objects.link(floor)
    fmat = bpy.data.materials.new("mat_floor")
    fmat.use_nodes = True
    fbsdf = next(n for n in fmat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    fbsdf.inputs["Base Color"].default_value = (0.14, 0.16, 0.18, 1.0)
    floor.data.materials.append(fmat)

    key = bpy.data.objects.new("KeyLight", bpy.data.lights.new("KeyLight", "AREA"))
    key.data.energy = 160
    key.data.size = 2.5
    key.location = (-2.2, -2.6, 2.5)
    key.rotation_euler = (math.radians(58), 0, math.radians(-38))
    scene.collection.objects.link(key)

    rim = bpy.data.objects.new("RimLight", bpy.data.lights.new("RimLight", "AREA"))
    rim.data.energy = 120
    rim.data.color = (0.76, 0.88, 0.96)
    rim.location = (1.8, 2.2, 2.2)
    rim.rotation_euler = (math.radians(-60), 0, math.radians(140))
    scene.collection.objects.link(rim)

    cam = bpy.data.objects.new("Camera", bpy.data.cameras.new("Camera"))
    cam.location = (2.05, -4.4, 1.52)
    cam.rotation_euler = (math.radians(84), 0, math.radians(24))
    cam.data.lens = 40
    scene.collection.objects.link(cam)
    scene.camera = cam

    scene.render.filepath = str(ART / "window_entity_preview.png")
    bpy.ops.render.render(write_still=True)


def main():
    build_rig()
    ART.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(ART / "window_entity.blend"))
    bpy.ops.export_scene.gltf(filepath=str(ART / "window_entity.glb"), export_format="GLB")
    setup_stage_and_render()
    print("Saved blend, glb, and preview render ->", ART)


if __name__ == "__main__":
    main()
