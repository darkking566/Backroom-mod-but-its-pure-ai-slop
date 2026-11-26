"""Realistic surfaces for the film props.

Every material starts from a real CC0 photo (ambientCG) recoloured to the material, with prints (the film's fabrics),
pictures and sign faces laid on top, then grime, stains, yellowing, chipped paint and rust. export() joins the prop,
unwraps it, bakes colour and ambient occlusion into one texture, writes OBJ + PNG and a matte preview lit like Level 0.
"""
import glob
import math
import os

import bpy
import numpy as np
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
PRINTS = os.path.join(HERE, "prints")
# The shared photo cache of the texture tools (tools/.cache is ignored by git); a missing photo is fetched once.
CACHE = os.path.join(HERE, "..", ".cache", "ambientcg")
OUT_DIR = "out"
PREVIEW = None
SIZES = {}

# Photo sources (ambientCG, CC0).
PHOTO = {
    "upholstery": "Fabric028", "cotton": "Fabric030", "weave": "Fabric061", "leather": "Leather026", "plastic": "Plastic010",
    "black_plastic": "Plastic006", "cardboard": "Cardboard004", "wood_light": "Wood049", "wood_red": "Wood066",
    "wood_dark": "Wood051", "paper": "Paper001", "paint": "PaintedMetal004", "steel": "Metal032", "metal_dark": "Metal049A",
    "rust": "Rust004", "grime": "Wallpaper002C", "stain": "Wallpaper002B", "carpet": "Carpet016",
}

# kind: colour, photo, tile (blocks per photo), contrast, grime, extra
KINDS = {
    # upholstery seen in the film's store
    "tan": ((0.56, 0.42, 0.28), "upholstery", 0.6, 0.6, 0.5, None),
    "camel": ((0.62, 0.48, 0.32), "upholstery", 0.6, 0.6, 0.5, None),
    "chocolate": ((0.20, 0.13, 0.09), "upholstery", 0.6, 0.7, 0.4, None),
    "mauve": ((0.50, 0.33, 0.31), "upholstery", 0.6, 0.6, 0.5, None),
    "microsuede": ((0.58, 0.49, 0.36), "upholstery", 0.6, 0.5, 0.5, None),
    "bluegrey": ((0.34, 0.42, 0.50), "upholstery", 0.6, 0.6, 0.5, None),
    "teal": ((0.13, 0.33, 0.39), "upholstery", 0.5, 1.0, 0.4, None),
    "green_velvet": ((0.09, 0.24, 0.16), "upholstery", 0.5, 1.0, 0.4, None),
    "purple_velvet": ((0.24, 0.10, 0.32), "upholstery", 0.5, 1.0, 0.3, None),
    "leather_brown": ((0.22, 0.11, 0.07), "leather", 0.6, 1.0, 0.4, None),
    "green_vinyl": ((0.12, 0.26, 0.20), "leather", 0.6, 0.8, 0.4, None),
    "cream": ((0.80, 0.76, 0.64), "upholstery", 0.6, 0.6, 0.6, None),
    "oat": ((0.55, 0.49, 0.39), "upholstery", 0.6, 0.6, 0.6, None),
    "navy": ((0.12, 0.15, 0.27), "cotton", 0.5, 1.0, 0.4, None),
    "sheet": ((0.82, 0.80, 0.72), "cotton", 0.5, 1.0, 0.4, "stains"),
    "lampshade": ((0.86, 0.78, 0.60), "cotton", 0.3, 0.8, 0.5, "stains"),
    # wood
    "honey_oak": ((0.60, 0.38, 0.16), "wood_light", 1.0, 1.0, 0.4, None),
    "pine": ((0.68, 0.50, 0.28), "wood_light", 1.0, 1.0, 0.4, None),
    "cherry": ((0.28, 0.11, 0.07), "wood_red", 1.0, 1.0, 0.3, None),
    "mahogany": ((0.20, 0.09, 0.06), "wood_red", 1.0, 1.0, 0.3, None),
    "dark_wood": ((0.20, 0.12, 0.07), "wood_dark", 1.0, 1.0, 0.3, None),
    "veneer": ((0.46, 0.29, 0.15), "wood_red", 1.0, 1.0, 0.4, None),
    "rattan": ((0.66, 0.44, 0.20), "weave", 0.25, 1.4, 0.4, None),
    "white_wicker": ((0.80, 0.78, 0.70), "weave", 0.25, 1.2, 0.6, None),
    "gilt": ((0.70, 0.52, 0.20), "steel", 1.0, 1.0, 0.5, "chips"),
    # hard surfaces
    "laminate": ((0.76, 0.74, 0.68), "paper", 1.0, 1.0, 0.6, None),
    "grey_laminate": ((0.26, 0.28, 0.30), "paper", 1.0, 1.0, 0.4, None),
    "steel": ((0.52, 0.54, 0.56), "steel", 1.0, 1.0, 0.5, "rust"),
    "chrome": ((0.62, 0.64, 0.66), "steel", 1.0, 1.0, 0.3, None),
    "dark": ((0.13, 0.14, 0.15), "metal_dark", 1.0, 1.0, 0.3, None),
    "black_metal": ((0.05, 0.05, 0.05), "metal_dark", 1.0, 1.0, 0.3, None),
    "iron": ((0.06, 0.06, 0.06), "metal_dark", 1.0, 1.0, 0.4, "rust"),
    "beige_metal": ((0.64, 0.60, 0.50), "steel", 1.0, 0.6, 0.6, "chips"),
    "glass": ((0.42, 0.50, 0.48), "paper", 1.0, 0.2, 0.3, None),
    "plastic": ((0.84, 0.83, 0.76), "plastic", 0.6, 1.0, 0.6, "yellowed"),
    "resin": ((0.88, 0.88, 0.84), "plastic", 0.6, 1.0, 0.7, "yellowed"),
    "grey_plastic": ((0.48, 0.48, 0.46), "plastic", 0.6, 1.0, 0.5, "yellowed"),
    "almond_plastic": ((0.74, 0.68, 0.56), "plastic", 0.6, 1.0, 0.5, "yellowed"),
    "black_plastic": ((0.08, 0.08, 0.09), "black_plastic", 0.6, 1.2, 0.2, None),
    "screen": ((0.08, 0.11, 0.11), "plastic", 0.6, 0.4, 0.3, None),
    "screen_on": ((0.50, 0.62, 0.64), "plastic", 0.3, 0.3, 0.0, "static"),
    "red": ((0.64, 0.11, 0.09), "plastic", 1.0, 0.5, 0.5, "chips"),
    "yellow": ((0.84, 0.68, 0.16), "plastic", 1.0, 0.5, 0.5, "chips"),
    "white": ((0.84, 0.84, 0.80), "plastic", 1.0, 0.5, 0.6, "chips"),
    "green": ((0.24, 0.44, 0.26), "plastic", 0.6, 1.0, 0.3, None),
    "school_green": ((0.16, 0.36, 0.26), "plastic", 0.6, 1.0, 0.4, None),
    "school_red": ((0.46, 0.10, 0.08), "plastic", 0.6, 1.0, 0.4, None),
    "school_black": ((0.07, 0.07, 0.08), "black_plastic", 0.6, 1.0, 0.3, None),
    "skin": ((0.82, 0.80, 0.74), "plastic", 0.6, 1.0, 0.6, "chips"),
    "standee": ((0.90, 0.90, 0.88), "cardboard", 1.0, 0.25, 0.4, None),
    "brass": ((0.62, 0.48, 0.20), "steel", 1.0, 1.0, 0.5, "tarnish"),
    "cardboard": ((0.60, 0.45, 0.26), "cardboard", 1.0, 1.0, 0.6, None),
    "candle": ((0.92, 0.86, 0.66), "paper", 0.5, 0.5, 0.2, None),
    "placemat": ((0.24, 0.42, 0.56), "weave", 0.3, 1.0, 0.3, None),
    "denim": ((0.23, 0.32, 0.48), "weave", 0.3, 1.3, 0.4, None),
    "shirt": ((0.76, 0.72, 0.60), "cotton", 0.4, 1.0, 0.4, None),
    "sock": ((0.36, 0.36, 0.40), "weave", 0.3, 1.0, 0.4, None),
    "tape_label": ((0.86, 0.84, 0.76), "paper", 0.3, 1.0, 0.3, "writing"),
    "lamp_glow": ((1.0, 0.88, 0.62), "paper", 1.0, 0.0, 0.0, None),
    # carpet rolls
    "carpet_rose": ((0.56, 0.36, 0.34), "carpet", 0.5, 1.0, 0.3, None),
    "carpet_sage": ((0.34, 0.40, 0.30), "carpet", 0.5, 1.0, 0.3, None),
    "carpet_grey": ((0.42, 0.42, 0.42), "carpet", 0.5, 1.0, 0.3, None),
    "carpet_navy": ((0.12, 0.14, 0.26), "carpet", 0.5, 1.0, 0.3, None),
    "carpet_brown": ((0.30, 0.20, 0.12), "carpet", 0.5, 1.0, 0.3, None),
    "carpet_cream": ((0.74, 0.68, 0.54), "carpet", 0.5, 1.0, 0.3, None),
    "carpet_maroon": ((0.30, 0.08, 0.10), "carpet", 0.5, 1.0, 0.3, None),
    "preview_floor": ((0.56, 0.48, 0.28), "carpet", 0.8, 1.0, 0.3, None),
}

_IMAGES = {}
_MEANS = {}
_CACHE = {}


def _load(path):
    image = bpy.data.images.load(path, check_existing=True)
    if path not in _MEANS:
        px = np.array(image.pixels[:], dtype=np.float32).reshape(-1, image.channels)[:, :3]
        linear = np.where(px <= 0.04045, px / 12.92, ((px + 0.055) / 1.055) ** 2.4)
        _MEANS[path] = linear.mean(axis=0)
    return image, _MEANS[path]


def photo(key):
    if key in _IMAGES:
        try:
            _IMAGES[key][0].name
            return _IMAGES[key]
        except ReferenceError:
            pass
    asset = PHOTO[key]
    found = glob.glob(os.path.join(CACHE, asset, "*", "*_Color.jpg"))
    if not found:
        fetch(asset)
        found = glob.glob(os.path.join(CACHE, asset, "*", "*_Color.jpg"))
    _IMAGES[key] = _load(found[0])
    return _IMAGES[key]


def fetch(asset, res="1K-JPG"):
    """Downloads an ambientCG material (CC0) into the cache, the way tools/make_level0_realistic.py does."""
    import io
    import json
    import re
    import urllib.request
    import zipfile

    def get(url):
        return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "noclip-backrooms-texture-tool"}), timeout=300).read()

    answer = json.loads(get(f"https://ambientcg.com/api/v2/full_json?id={asset}&include=downloadData"))
    link = None
    for found in answer.get("foundAssets", []):
        for category in found.get("downloadFolders", {}).values():
            for download in category.get("downloadFiletypeCategories", {}).get("zip", {}).get("downloads", []):
                if download.get("attribute") == res:
                    link = download.get("downloadLink") or download.get("fullDownloadPath")
    if not link or not re.match(r"^https://(www\.)?ambientcg\.com/", link):
        raise SystemExit(f"ambientCG has no {res} download for {asset}")
    folder = os.path.join(CACHE, asset, res)
    os.makedirs(folder, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(get(link))) as archive:
        for member in archive.namelist():
            name = os.path.basename(member)
            if re.fullmatch(r"[A-Za-z0-9_\-]+\.(jpg|png)", name):
                with open(os.path.join(folder, name), "wb") as f:
                    f.write(archive.read(member))


def build(name, target, source, tile, contrast, grime, extra=None, print_file=None, print_tile=0.6, uv_image=None):
    """A material: a photo of the real surface recoloured to `target` (or carrying a print or picture), with wear on top."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nodes, links = m.node_tree.nodes, m.node_tree.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Roughness"].default_value = 0.95
    bsdf.inputs["Specular IOR Level"].default_value = 0.05
    coord = nodes.new("ShaderNodeTexCoord")

    def mapped(scale):
        mp = nodes.new("ShaderNodeMapping")
        mp.inputs["Scale"].default_value = (scale, scale, scale)
        links.new(coord.outputs["Object"], mp.inputs["Vector"])
        return mp.outputs["Vector"]

    def image(loaded, scale=None, uv=False):
        img, mean = loaded
        t = nodes.new("ShaderNodeTexImage")
        t.image = img
        if uv:
            uvn = nodes.new("ShaderNodeUVMap")
            uvn.uv_map = "src"
            links.new(uvn.outputs["UV"], t.inputs["Vector"])
            t.extension = "EXTEND"
        else:
            t.projection = "BOX"
            t.projection_blend = 0.25
            links.new(mapped(scale), t.inputs["Vector"])
        return t.outputs["Color"], mean

    def bw(colour):
        n = nodes.new("ShaderNodeRGBToBW")
        links.new(colour, n.inputs["Color"])
        return n.outputs["Val"]

    def ramp(fac, lo, hi, out_lo=0.0, out_hi=1.0):
        r = nodes.new("ShaderNodeMapRange")
        links.new(fac, r.inputs["Value"])
        r.inputs["From Min"].default_value = lo
        r.inputs["From Max"].default_value = hi
        r.inputs["To Min"].default_value = out_lo
        r.inputs["To Max"].default_value = out_hi
        return r.outputs["Result"]

    def mix(fac, a, b, blend="MIX"):
        mx = nodes.new("ShaderNodeMix")
        mx.data_type = "RGBA"
        mx.blend_type = blend
        if isinstance(fac, (int, float)):
            mx.inputs["Factor"].default_value = fac
        else:
            links.new(fac, mx.inputs["Factor"])
        for slot, value in ((6, a), (7, b)):
            if isinstance(value, tuple):
                mx.inputs[slot].default_value = (*value, 1.0)
            else:
                links.new(value, mx.inputs[slot])
        return mx.outputs[2]

    def noise(scale, detail=4.0):
        n = nodes.new("ShaderNodeTexNoise")
        n.inputs["Scale"].default_value = scale
        n.inputs["Detail"].default_value = detail
        links.new(mapped(1.0), n.inputs["Vector"])
        return n.outputs["Fac"]

    pic, mean = image(photo(source), 1.0 / tile)
    inv = tuple(float(1.0 / max(mu, 1e-3)) for mu in mean)
    detail = mix(min(contrast, 1.0), (1.0, 1.0, 1.0), mix(1.0, pic, inv, "MULTIPLY"))
    if uv_image:
        pic_face, _ = image(_load(os.path.join(PRINTS, uv_image)), uv=True)
        colour = mix(1.0, pic_face, detail, "MULTIPLY")
    elif print_file:
        cloth, _ = image(_load(os.path.join(PRINTS, print_file)), 1.0 / print_tile)
        colour = mix(1.0, cloth, detail, "MULTIPLY")
    else:
        colour = mix(1.0, detail, target, "MULTIPLY")
    if contrast > 1.0:
        colour = mix(contrast - 1.0, colour, mix(1.0, colour, colour, "MULTIPLY"))
    if extra == "stains":
        stain, _ = image(photo("stain"), 1.0 / 1.6)
        colour = mix(ramp(bw(stain), 0.62, 0.42, 0.0, 0.75), colour, (0.62, 0.52, 0.32), "MULTIPLY")
    elif extra == "yellowed":
        stain, _ = image(photo("stain"), 1.0 / 1.2)
        colour = mix(ramp(bw(stain), 0.7, 0.4, 0.15, 0.7), colour, (0.86, 0.76, 0.52), "MULTIPLY")
    elif extra == "rust":
        rust, _ = image(photo("rust"), 1.0)
        grime_pic, _ = image(photo("grime"), 1.0 / 1.5)
        colour = mix(ramp(bw(grime_pic), 0.42, 0.30), colour, rust)
    elif extra == "tarnish":
        grime_pic, _ = image(photo("grime"), 1.0 / 1.2)
        colour = mix(ramp(bw(grime_pic), 0.6, 0.3, 0.0, 0.8), colour, (0.28, 0.30, 0.20))
    elif extra == "chips":
        paint, _ = image(photo("paint"), 1.0 / 1.5)
        chips = nodes.new("ShaderNodeSeparateColor")
        links.new(paint, chips.inputs["Color"])
        colour = mix(ramp(chips.outputs["Green"], 0.30, 0.60, 0.0, 0.8), colour, (0.42, 0.42, 0.42))
    elif extra == "writing":
        wave = nodes.new("ShaderNodeTexWave")
        wave.inputs["Scale"].default_value = 40.0
        wave.inputs["Distortion"].default_value = 8.0
        links.new(mapped(1.0), wave.inputs["Vector"])
        colour = mix(ramp(wave.outputs["Fac"], 0.92, 0.97), colour, (0.12, 0.12, 0.18))
    elif extra == "static":
        colour = mix(ramp(noise(120.0, 1.0), 0.3, 0.7), colour, (0.36, 0.44, 0.46))
    if grime > 0:
        dirt, _ = image(photo("grime"), 1.0 / 2.0)
        colour = mix(ramp(bw(dirt), 0.75, 0.30, 0.0, grime), colour, mix(1.0, colour, (0.55, 0.52, 0.46), "MULTIPLY"))
    links.new(colour, bsdf.inputs["Base Color"])
    return m


def materials():
    """A fresh set of the named materials (call after each scene reset)."""
    _CACHE.clear()
    return {kind: build(f"MAT_{kind.upper()}", *spec) for kind, spec in KINDS.items() if kind != "preview_floor"}


def fabric(file, tile=0.6, source="upholstery", contrast=0.6, grime=0.5):
    """A printed fabric (prints/<file>.png), wrapped around whatever wears it."""
    key = ("fabric", file, tile)
    if key not in _CACHE:
        _CACHE[key] = build(f"PRINT_{file.upper()}", None, source, 0.6, contrast, grime, print_file=f"{file}.png", print_tile=tile)
    return _CACHE[key]


def face(file, source="paper", contrast=0.3, grime=0.35):
    """A picture or a sign face (prints/<file>.png) laid once across a panel."""
    key = ("face", file)
    if key not in _CACHE:
        _CACHE[key] = build(f"FACE_{file.upper()}", None, source, 0.5, contrast, grime, uv_image=f"{file}.png")
    return _CACHE[key]


def export(name, size):
    SIZES[name] = size
    scene = bpy.context.scene
    meshes = [o for o in scene.objects if o.type == "MESH"]
    for o in scene.objects:
        o.select_set(False)
    for o in meshes:
        o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.convert(target="MESH")
    bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    obj.name = name
    me = obj.data
    target_uv = me.uv_layers.new(name="bake")
    me.uv_layers.active = target_uv
    target_uv.active_render = True
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=0.01)
    bpy.ops.object.mode_set(mode="OBJECT")
    # Texture size from the surface to cover: about 64 pixels a block (the props' standard), whatever the prop's size.
    area = sum(p.area for p in me.polygons)
    final = 64
    while final < 1024 and final * 0.75 < math.sqrt(area) * 64:
        final *= 2
    work = min(final * 2, 2048)
    print("TEXTURE", name, round(area, 2), final)
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.render.bake.margin = 6
    images = {}
    for kind in ("color", "ao"):
        image = bpy.data.images.new(f"{name}_{kind}", work, work, alpha=False)
        for mat in me.materials:
            nodes = mat.node_tree.nodes
            tex = nodes.get("bake_target") or nodes.new("ShaderNodeTexImage")
            tex.name = "bake_target"
            tex.image = image
            nodes.active = tex
        if kind == "color":
            scene.cycles.samples = 4
            scene.render.bake.use_pass_direct = False
            scene.render.bake.use_pass_indirect = False
            scene.render.bake.use_pass_color = True
            bpy.ops.object.bake(type="DIFFUSE")
        else:
            scene.cycles.samples = 48
            bpy.ops.object.bake(type="AO")
        images[kind] = image
    colour = np.array(images["color"].pixels[:], dtype=np.float32).reshape(work, work, 4)
    ao = np.array(images["ao"].pixels[:], dtype=np.float32).reshape(work, work, 4)[..., 0]
    shade = (0.48 + 0.52 * ao)[..., None]
    dust = np.clip((0.55 - ao) / 0.4, 0, 1)[..., None] * 0.35
    rgb = colour[..., :3] * shade
    rgb = rgb * (1 - dust) + np.array([0.42, 0.38, 0.30], dtype=np.float32) * dust
    out = np.dstack([np.clip(rgb, 0, 1), np.ones((work, work), dtype=np.float32)])
    texture = bpy.data.images.new(f"{name}_tex", work, work, alpha=False)
    texture.pixels[:] = out.ravel()
    texture.scale(final, final)
    tex_dir = os.path.join(OUT_DIR, "textures")
    os.makedirs(tex_dir, exist_ok=True)
    texture.filepath_raw = os.path.join(tex_dir, f"{name}.png")
    texture.file_format = "PNG"
    texture.save()
    for layer in [l for l in me.uv_layers if l.name != "bake"]:
        me.uv_layers.remove(layer)
    for o in scene.objects:
        o.select_set(False)
    obj.select_set(True)
    bpy.ops.wm.obj_export(filepath=os.path.join(OUT_DIR, f"{name}.obj"), export_selected_objects=True, export_uv=True,
                          export_normals=True, export_materials=False, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z",
                          up_axis="Y", apply_modifiers=True)
    if PREVIEW:
        preview(obj, texture, name, size)


def preview(obj, texture, name, size):
    """Matte, lit from above by a warm panel like Level 0's: about how the game will show it."""
    scene = bpy.context.scene
    shown = bpy.data.materials.new(f"{name}_shown")
    shown.use_nodes = True
    nodes, links = shown.node_tree.nodes, shown.node_tree.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Roughness"].default_value = 1.0
    bsdf.inputs["Specular IOR Level"].default_value = 0.0
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = texture
    tex.interpolation = "Closest"
    links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    obj.data.materials.clear()
    obj.data.materials.append(shown)
    w, h, d = size
    bpy.ops.mesh.primitive_plane_add(size=14, location=((w - 1) / 2, -(d - 1) / 2, 0))
    floor = bpy.context.active_object
    floor.data.materials.append(build("floor", *KINDS["preview_floor"]))
    light_data = bpy.data.lights.new("panel", "AREA")
    light_data.energy = 140 * max(1.0, h / 1.5) ** 2
    light_data.size = 2.0
    light_data.color = (1.0, 0.92, 0.74)
    light = bpy.data.objects.new("panel", light_data)
    light.location = ((w - 1) / 2 + 0.6, -(d - 1) / 2 + 0.8, h + 3.0)
    scene.collection.objects.link(light)
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.20, 0.18, 0.13, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.35
    scene.world = world
    centre = Vector(((w - 1) / 2, -(d - 1) / 2, h / 2))
    distance = max(w, h, d) * 1.6 + 0.9
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    cam.location = centre + Vector((-0.55, 0.85, 0.45)).normalized() * distance
    cam.rotation_euler = (centre - cam.location).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 24
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = "AgX"
    scene.render.resolution_x = scene.render.resolution_y = 360
    scene.render.filepath = os.path.join(PREVIEW, f"{name}.png")
    bpy.ops.render.render(write_still=True)
