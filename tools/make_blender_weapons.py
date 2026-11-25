"""
Headless Blender 5.3 generator for all Backrooms true-curved 3D weapon meshes (.obj + .mtl).
Exports 22 game-optimized triangulated meshes directly to:
  src/main/resources/assets/noclip_backrooms/models/blender/
and writes the 4x4 pure-white untinted texture:
  src/main/resources/assets/noclip_backrooms/textures/item/blender_white.png

Coordinate convention (Blender -> OBJ with forward='-Z', up='Y' -> Minecraft Hand):
  - Origin (0, 0, 0) = player hand grip center.
  - Firearms: barrel points along Blender +Y (-> MC -Z forward), top/optics along +Z (-> MC +Y up).
  - Staffs / Melee / Relics: blade/crown extends along Blender +Z (-> MC +Y up), front edge faces +Y (-> MC -Z forward).

Run with:
  "E:\\SteamLibrary\\steamapps\\common\\Blender\\blender.exe" -b --python tools/make_blender_weapons.py
"""
import bpy
import bmesh
import math
import os
import struct
import zlib

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT_DIR = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "noclip_backrooms", "models", "blender")
TEX_PATH = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "noclip_backrooms", "textures", "item", "blender_white.png")
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(os.path.dirname(TEX_PATH), exist_ok=True)


def write_white_png(path, size=4):
    raw = b"".join(b"\x00" + (b"\xff\xff\xff\xff" * size) for _ in range(size))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    return bpy.context.scene


def make_mat(name, color, emit=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    if emit:
        bsdf.inputs["Emission Color"].default_value = (*color, 1.0)
        bsdf.inputs["Emission Strength"].default_value = 4.0
    return m


def standard_mats():
    return {
        "gunmetal": make_mat("MAT_GUNMETAL", (0.26, 0.29, 0.34)),
        "dark": make_mat("MAT_DARK_POLY", (0.12, 0.13, 0.16)),
        "grip": make_mat("MAT_GRIP", (0.09, 0.085, 0.095)),
        "chrome": make_mat("MAT_BLADE", (0.76, 0.80, 0.86)),
        "copper": make_mat("MAT_COPPER", (0.82, 0.38, 0.16)),
        "brass": make_mat("MAT_ACCENT", (0.86, 0.64, 0.22)),
        "wood": make_mat("MAT_WOOD", (0.34, 0.20, 0.11)),
        "obsidian": make_mat("MAT_OBSIDIAN", (0.16, 0.13, 0.24)),
        "leather": make_mat("MAT_LEATHER", (0.24, 0.14, 0.09)),
        "parchment": make_mat("MAT_PARCHMENT", (0.88, 0.82, 0.68)),
        "emit_main": make_mat("EMIT_MAIN", (0.20, 0.82, 1.0), emit=True),
        "emit_hot": make_mat("EMIT_HOT", (0.78, 0.95, 1.0), emit=True),
        "emit_alt": make_mat("EMIT_ALT", (1.0, 0.22, 0.28), emit=True),
    }


def smooth(obj, is_smooth=True):
    if obj.type == "MESH":
        for p in obj.data.polygons:
            p.use_smooth = is_smooth
    return obj


def add_box(name, loc, scale, mat, bevel=0.004, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=loc, rotation=rot)
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(scale=True)
    if bevel > 0:
        mod = obj.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 1
    obj.data.materials.append(mat)
    return smooth(obj, False)


def add_cyl(name, loc, radius, depth, mat, rot=(0, 0, 0), verts=14, bevel=0.0015):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=radius, depth=depth, location=loc, rotation=rot)
    obj = bpy.context.active_object
    obj.name = name
    if bevel > 0:
        mod = obj.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 1
    obj.data.materials.append(mat)
    return smooth(obj, True)


def add_cone(name, loc, r1, r2, depth, mat, rot=(0, 0, 0), verts=14):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r1, radius2=r2, depth=depth, location=loc, rotation=rot)
    obj = bpy.context.active_object
    obj.name = name
    obj.data.materials.append(mat)
    return smooth(obj, True)


def add_torus(name, loc, major_r, minor_r, mat, rot=(0, 0, 0), maj_seg=22, min_seg=8):
    bpy.ops.mesh.primitive_torus_add(
        major_segments=maj_seg, minor_segments=min_seg,
        major_radius=major_r, minor_radius=minor_r,
        location=loc, rotation=rot
    )
    obj = bpy.context.active_object
    obj.name = name
    obj.data.materials.append(mat)
    return smooth(obj, True)


def add_crystal(name, loc, rx, ry, rz, mat, rot=(0, 0, 0)):
    bm = bmesh.new()
    top = bm.verts.new((0, 0, rz))
    bot = bm.verts.new((0, 0, -rz))
    ring_u, ring_l = [], []
    sides = 6
    for i in range(sides):
        a = i * math.tau / sides
        ring_u.append(bm.verts.new((math.cos(a) * rx, math.sin(a) * ry, rz * 0.24)))
        ring_l.append(bm.verts.new((math.cos(a) * rx * 0.90, math.sin(a) * ry * 0.90, -rz * 0.24)))
    for i in range(sides):
        j = (i + 1) % sides
        bm.faces.new((top, ring_u[i], ring_u[j]))
        bm.faces.new((ring_u[i], ring_l[i], ring_l[j], ring_u[j]))
        bm.faces.new((bot, ring_l[j], ring_l[i]))
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    obj.location = loc
    obj.rotation_euler = rot
    obj.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(obj)
    return smooth(obj, False)


def add_curve_tube(name, pts, thick, mat, loc=(0, 0, 0), rot=(0, 0, 0), res_u=8, bev_res=2):
    cd = bpy.data.curves.new(name + "_Curve", type="CURVE")
    cd.dimensions = "3D"
    cd.resolution_u = res_u
    cd.bevel_depth = thick
    cd.bevel_resolution = bev_res
    cd.use_fill_caps = True
    sp = cd.splines.new("BEZIER")
    sp.bezier_points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        bp = sp.bezier_points[i]
        bp.co = p
        bp.handle_left_type = "AUTO"
        bp.handle_right_type = "AUTO"
    obj = bpy.data.objects.new(name, cd)
    obj.location = loc
    obj.rotation_euler = rot
    obj.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def add_diamond_blade(name, z0, z1, width_base, width_mid, thick, mat, y_curve=0.0):
    """True diamond cross-section sculpted sword/dao blade with beveled fuller and sharp tip."""
    bm = bmesh.new()
    segs = 7
    rings = []
    for i in range(segs):
        t = i / float(segs)
        z = z0 + (z1 - z0) * t
        # Taper profile
        if t < 0.72:
            w = width_base + (width_mid - width_base) * (t / 0.72)
        else:
            w = width_mid * (1.0 - ((t - 0.72) / 0.28) * 0.78)
        th = thick * (1.0 - t * 0.45)
        cy = y_curve * (t * t)
        # 4-point diamond ring: front edge (+Y), back edge (-Y), right spine (+X), left spine (-X)
        v_f = bm.verts.new((0.0, cy + w * 0.55, z))
        v_r = bm.verts.new((th * 0.5, cy, z))
        v_b = bm.verts.new((0.0, cy - w * 0.45, z))
        v_l = bm.verts.new((-th * 0.5, cy, z))
        rings.append((v_f, v_r, v_b, v_l))
    tip = bm.verts.new((0.0, y_curve * 1.15 + width_base * 0.12, z1))
    bot = bm.verts.new((0.0, 0.0, z0 - 0.01))
    # Cap bottom
    for k in range(4):
        bm.faces.new((bot, rings[0][(k + 1) % 4], rings[0][k]))
    # Connect rings
    for i in range(segs - 1):
        r0, r1 = rings[i], rings[i + 1]
        for k in range(4):
            nk = (k + 1) % 4
            bm.faces.new((r0[k], r0[nk], r1[nk], r1[k]))
    # Connect tip
    rl = rings[-1]
    for k in range(4):
        bm.faces.new((rl[k], rl[(k + 1) % 4], tip))
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    obj.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(obj)
    return smooth(obj, False)


def export_mesh(name, rotate_gun_x_to_y=False):
    """Apply all transforms, convert curves, optionally orient +X gun barrel to +Y (MC -Z), and export OBJ+MTL."""
    scene = bpy.context.scene
    objs = [o for o in scene.objects if o.type in ("MESH", "CURVE")]
    if rotate_gun_x_to_y:
        # Rotate +X barrel -> +Y forward (+90 deg around Z), and shift grip center to (0, 0, 0)
        for o in objs:
            # First wrap via empty parent so all child coordinates rotate around origin cleanly
            pass
        pivot = bpy.data.objects.new("ExportPivot", None)
        scene.collection.objects.link(pivot)
        for o in objs:
            o.parent = pivot
        pivot.rotation_euler = (0.0, 0.0, math.radians(90.0))
        bpy.context.view_layer.update()
        for o in objs:
            mat_world = o.matrix_world.copy()
            o.parent = None
            o.matrix_world = mat_world
        bpy.data.objects.remove(pivot, do_unlink=True)

    # Convert curves to meshes and bake modifiers
    bpy.ops.object.select_all(action="DESELECT")
    for o in list(scene.objects):
        if o.type == "CURVE":
            o.select_set(True)
            bpy.context.view_layer.objects.active = o
            bpy.ops.object.convert(target="MESH")
            o.select_set(False)

    # Count tris after modifier evaluation
    deps = bpy.context.evaluated_depsgraph_get()
    total_tris = 0
    for o in scene.objects:
        if o.type == "MESH":
            ev = o.evaluated_get(deps)
            me = ev.to_mesh()
            me.calc_loop_triangles()
            total_tris += len(me.loop_triangles)
            ev.to_mesh_clear()

    out_obj = os.path.join(OUT_DIR, f"{name}.obj")
    bpy.ops.wm.obj_export(
        filepath=out_obj,
        export_triangulated_mesh=True,
        export_normals=True,
        export_uv=False,
        export_materials=True,
        apply_modifiers=True,
        forward_axis="NEGATIVE_Z",
        up_axis="Y",
    )
    print(f"EXPORTED {name}.obj ({total_tris} tris)")
    return total_tris


# =============================================================================
# 1. FIREARMS (Built along +X with grip near (0,0,0), auto-rotated to +Y -> MC -Z!)
# =============================================================================
def build_gun_railgun():
    reset_scene()
    M = standard_mats()
    # Shift so grip sits cleanly at (0, 0, 0), sightline around z = +0.255
    ox, oz = 0.115, 0.105

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Receiver + glowing charge bars
    add_box("UpperRec", p(0.0, 0.0, 0.045), (0.34, 0.064, 0.068), M["gunmetal"], 0.006)
    add_box("LowerRec", p(-0.015, 0.0, -0.012), (0.30, 0.056, 0.054), M["dark"], 0.005)
    add_box("Magwell", p(0.065, 0.0, -0.052), (0.088, 0.060, 0.050), M["dark"], 0.005)
    add_box("EjectPort", p(0.02, -0.031, 0.052), (0.095, 0.008, 0.024), M["chrome"], 0.002)
    add_cyl("ChargeHandle", p(0.04, -0.042, 0.056), 0.007, 0.028, M["dark"], rot=(math.radians(90), 0, 0), verts=10)
    add_box("RecGlowL", p(-0.06, 0.033, 0.045), (0.11, 0.004, 0.009), M["emit_main"], 0.0)
    add_box("RecGlowR", p(-0.06, -0.033, 0.045), (0.11, 0.004, 0.009), M["emit_main"], 0.0)

    # Grip & Trigger
    add_box("Grip", p(-0.115, 0.0, -0.105), (0.052, 0.044, 0.135), M["grip"], 0.007, rot=(0, math.radians(-18), 0))
    add_box("GripBase", p(-0.094, 0.0, -0.170), (0.056, 0.048, 0.016), M["gunmetal"], 0.003, rot=(0, math.radians(-18), 0))
    add_box("TrigGuardB", p(-0.045, 0.0, -0.072), (0.095, 0.020, 0.010), M["dark"], 0.002)
    add_box("TrigGuardF", p(0.002, 0.0, -0.052), (0.012, 0.020, 0.044), M["dark"], 0.002)
    add_box("Trigger", p(-0.052, 0.0, -0.048), (0.010, 0.010, 0.036), M["brass"], 0.0, rot=(0, math.radians(20), 0))

    # Skeletonized Stock
    add_cyl("BufferTube", p(-0.27, 0.0, 0.036), 0.021, 0.24, M["gunmetal"], rot=(0, math.radians(90), 0), verts=14)
    add_box("StockBody", p(-0.37, 0.0, -0.005), (0.14, 0.046, 0.132), M["dark"], 0.007)
    add_box("Buttpad", p(-0.442, 0.0, -0.008), (0.024, 0.052, 0.146), M["grip"], 0.005)
    add_box("CheekRest", p(-0.315, 0.0, 0.068), (0.14, 0.052, 0.026), M["grip"], 0.005)
    add_box("StockStrut", p(-0.25, 0.0, -0.046), (0.16, 0.022, 0.018), M["gunmetal"], 0.003, rot=(0, math.radians(15), 0))

    # Energy Cell Mag
    add_box("MagBody", p(0.075, 0.0, -0.145), (0.068, 0.042, 0.162), M["dark"], 0.005, rot=(0, math.radians(11), 0))
    add_box("MagFloor", p(0.059, 0.0, -0.226), (0.074, 0.048, 0.018), M["gunmetal"], 0.003, rot=(0, math.radians(11), 0))
    for i in range(4):
        z = -0.10 - i * 0.028
        dx = -0.0054 * (i - 1.5)
        add_box(f"MagCell_{i}", p(0.075 + dx, 0.0, z), (0.022, 0.046, 0.014), M["emit_main"], 0.0, rot=(0, math.radians(11), 0))

    # Handguard + Foregrip + Twin Accelerator Rails + 5 Wound Copper Coils + Plasma Core
    add_box("Handguard", p(0.28, 0.0, 0.030), (0.26, 0.068, 0.088), M["dark"], 0.007)
    add_box("Foregrip", p(0.29, 0.0, -0.065), (0.038, 0.036, 0.102), M["grip"], 0.006, rot=(0, math.radians(-8), 0))
    add_box("RailUp", p(0.55, 0.0, 0.062), (0.60, 0.046, 0.026), M["gunmetal"], 0.004)
    add_box("RailLo", p(0.55, 0.0, -0.004), (0.60, 0.046, 0.026), M["gunmetal"], 0.004)
    add_box("TrackUp", p(0.57, 0.0, 0.045), (0.56, 0.022, 0.009), M["chrome"], 0.0)
    add_box("TrackLo", p(0.57, 0.0, 0.013), (0.56, 0.022, 0.009), M["chrome"], 0.0)
    add_cyl("PlasmaCore", p(0.54, 0.0, 0.029), 0.010, 0.60, M["emit_hot"], rot=(0, math.radians(90), 0), verts=12, bevel=0)
    for idx in range(5):
        cx = 0.43 + idx * 0.082
        add_torus(f"Coil_{idx}", p(cx, 0.0, 0.029), 0.041, 0.010, M["copper"], rot=(0, math.radians(90), 0), maj_seg=20, min_seg=8)
        add_torus(f"CoilGlow_{idx}", p(cx + 0.019, 0.0, 0.029), 0.032, 0.0038, M["emit_main"], rot=(0, math.radians(90), 0), maj_seg=18, min_seg=6)

    add_box("MuzzleTop", p(0.85, 0.0, 0.064), (0.072, 0.056, 0.028), M["dark"], 0.004)
    add_box("MuzzleBot", p(0.85, 0.0, -0.006), (0.072, 0.056, 0.028), M["dark"], 0.004)
    add_box("MuzzleGlow", p(0.875, 0.0, 0.029), (0.016, 0.014, 0.046), M["emit_hot"], 0.0)

    # Picatinny Rail (12 crisp teeth)
    add_box("PicBase", p(0.14, 0.0, 0.084), (0.58, 0.028, 0.012), M["dark"], 0.0)
    for i in range(12):
        add_box(f"Pic_{i}", p(-0.13 + i * 0.048, 0.0, 0.094), (0.022, 0.032, 0.009), M["gunmetal"], 0.0)

    # PEQ Laser Box
    add_box("LaserBox", p(0.34, -0.046, 0.036), (0.10, 0.028, 0.034), M["dark"], 0.003)
    add_cyl("LaserLens", p(0.392, -0.046, 0.036), 0.009, 0.008, M["emit_alt"], rot=(0, math.radians(90), 0), verts=10, bevel=0)

    # Cylindrical Optical Scope + Brass Turrets + Illuminated Lenses
    for rx in (-0.04, 0.11):
        add_box(f"SMount_{rx}", p(rx, 0.0, 0.114), (0.026, 0.032, 0.036), M["gunmetal"], 0.002)
        add_cyl(f"SRing_{rx}", p(rx, 0.0, 0.146), 0.028, 0.024, M["dark"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("ScopeTube", p(0.04, 0.0, 0.146), 0.022, 0.23, M["dark"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("ScopeObj", p(0.195, 0.0, 0.146), 0.035, 0.092, M["gunmetal"], rot=(0, math.radians(90), 0), verts=18)
    add_cyl("ScopeOcu", p(-0.115, 0.0, 0.146), 0.028, 0.072, M["gunmetal"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("LensF", p(0.240, 0.0, 0.146), 0.029, 0.006, M["emit_main"], rot=(0, math.radians(90), 0), verts=16, bevel=0)
    add_cyl("LensR", p(-0.151, 0.0, 0.146), 0.021, 0.006, M["emit_main"], rot=(0, math.radians(90), 0), verts=14, bevel=0)
    add_cyl("TurretT", p(0.035, 0.0, 0.178), 0.014, 0.024, M["brass"], verts=12)
    add_cyl("TurretS", p(0.035, -0.030, 0.146), 0.013, 0.022, M["brass"], rot=(math.radians(90), 0, 0), verts=12)
    export_mesh("gun_railgun", rotate_gun_x_to_y=True)


def build_gun_sniper():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.12, 0.10

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Long precision chassis & stock
    add_box("Receiver", p(0.04, 0.0, 0.035), (0.38, 0.056, 0.064), M["gunmetal"], 0.006)
    add_box("Chassis", p(0.24, 0.0, 0.012), (0.34, 0.052, 0.050), M["dark"], 0.006)
    add_box("Stock", p(-0.30, 0.0, 0.008), (0.30, 0.044, 0.105), M["dark"], 0.007)
    add_box("Cheek", p(-0.27, 0.0, 0.074), (0.14, 0.050, 0.026), M["grip"], 0.005)
    add_box("Butt", p(-0.452, 0.0, 0.004), (0.024, 0.050, 0.126), M["grip"], 0.005)
    add_box("Grip", p(-0.12, 0.0, -0.095), (0.048, 0.042, 0.130), M["grip"], 0.007, rot=(0, math.radians(-16), 0))
    add_box("TrigGuard", p(-0.04, 0.0, -0.062), (0.092, 0.018, 0.010), M["dark"], 0.002)
    add_box("Trigger", p(-0.05, 0.0, -0.040), (0.009, 0.009, 0.034), M["brass"], 0.0, rot=(0, math.radians(18), 0))
    add_box("Mag", p(0.08, 0.0, -0.090), (0.085, 0.038, 0.125), M["gunmetal"], 0.004)
    # Bolt handle + chrome knob
    add_cyl("BoltStem", p(-0.06, -0.042, 0.042), 0.006, 0.042, M["chrome"], rot=(math.radians(90), 0, 0), verts=10)
    add_cyl("BoltKnob", p(-0.06, -0.064, 0.034), 0.014, 0.018, M["dark"], verts=12)
    # Long heavy cylindrical barrel + muzzle brake + glowing muzzle ring
    add_cyl("Barrel", p(0.58, 0.0, 0.044), 0.019, 0.72, M["gunmetal"], rot=(0, math.radians(90), 0), verts=16)
    add_box("MuzzleBrake", p(0.96, 0.0, 0.044), (0.095, 0.048, 0.044), M["dark"], 0.005)
    add_box("BrakeSlot1", p(0.95, 0.0, 0.044), (0.018, 0.052, 0.018), M["emit_main"], 0.0)
    add_box("BrakeSlot2", p(0.985, 0.0, 0.044), (0.018, 0.052, 0.018), M["emit_main"], 0.0)
    # Folded bipod legs
    add_cyl("BipodL", p(0.46, 0.036, -0.022), 0.009, 0.22, M["gunmetal"], rot=(0, math.radians(96), 0), verts=10)
    add_cyl("BipodR", p(0.46, -0.036, -0.022), 0.009, 0.22, M["gunmetal"], rot=(0, math.radians(96), 0), verts=10)
    # Oversized high-power Sniper Optical Scope
    for rx in (-0.03, 0.14):
        add_box(f"SM_{rx}", p(rx, 0.0, 0.096), (0.026, 0.030, 0.042), M["gunmetal"], 0.002)
        add_cyl(f"SR_{rx}", p(rx, 0.0, 0.132), 0.028, 0.024, M["dark"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("ScopeTube", p(0.06, 0.0, 0.132), 0.022, 0.30, M["dark"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("ScopeObj", p(0.25, 0.0, 0.132), 0.040, 0.115, M["gunmetal"], rot=(0, math.radians(90), 0), verts=18)
    add_cyl("ScopeOcu", p(-0.12, 0.0, 0.132), 0.030, 0.080, M["gunmetal"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("LensF", p(0.306, 0.0, 0.132), 0.034, 0.006, M["emit_main"], rot=(0, math.radians(90), 0), verts=16, bevel=0)
    add_cyl("LensR", p(-0.160, 0.0, 0.132), 0.023, 0.006, M["emit_alt"], rot=(0, math.radians(90), 0), verts=14, bevel=0)
    add_cyl("TurretT", p(0.05, 0.0, 0.168), 0.015, 0.026, M["brass"], verts=12)
    export_mesh("gun_sniper", rotate_gun_x_to_y=True)


def build_gun_assault_rifle():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.11, 0.10

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Upper/Lower receiver
    add_box("Upper", p(0.02, 0.0, 0.042), (0.30, 0.056, 0.056), M["gunmetal"], 0.006)
    add_box("Lower", p(-0.01, 0.0, -0.008), (0.26, 0.050, 0.048), M["dark"], 0.005)
    add_box("Eject", p(0.03, -0.028, 0.046), (0.08, 0.008, 0.022), M["chrome"], 0.0)
    add_box("Grip", p(-0.11, 0.0, -0.095), (0.046, 0.040, 0.125), M["grip"], 0.007, rot=(0, math.radians(-18), 0))
    add_box("TrigGuard", p(-0.04, 0.0, -0.062), (0.088, 0.018, 0.009), M["dark"], 0.002)
    add_box("Trigger", p(-0.05, 0.0, -0.040), (0.009, 0.009, 0.032), M["brass"], 0.0, rot=(0, math.radians(18), 0))
    # Collapsible carbine stock
    add_cyl("BufTube", p(-0.24, 0.0, 0.036), 0.019, 0.22, M["gunmetal"], rot=(0, math.radians(90), 0), verts=14)
    add_box("Stock", p(-0.33, 0.0, 0.002), (0.13, 0.044, 0.118), M["dark"], 0.007)
    add_box("Butt", p(-0.398, 0.0, 0.0), (0.022, 0.048, 0.130), M["grip"], 0.004)
    # Curved 30-rd magazine
    add_box("MagTop", p(0.075, 0.0, -0.095), (0.058, 0.034, 0.105), M["dark"], 0.004, rot=(0, math.radians(8), 0))
    add_box("MagBot", p(0.095, 0.0, -0.185), (0.058, 0.034, 0.095), M["dark"], 0.004, rot=(0, math.radians(20), 0))
    add_box("MagStripe", p(0.086, 0.0, -0.15), (0.016, 0.038, 0.040), M["emit_main"], 0.0, rot=(0, math.radians(14), 0))
    # Tactical quad-rail handguard + angled foregrip + cylindrical suppressor/barrel
    add_box("Handguard", p(0.30, 0.0, 0.034), (0.28, 0.058, 0.072), M["dark"], 0.006)
    add_box("AngledGrip", p(0.32, 0.0, -0.028), (0.10, 0.036, 0.042), M["grip"], 0.006, rot=(0, math.radians(-22), 0))
    add_cyl("Barrel", p(0.51, 0.0, 0.040), 0.014, 0.22, M["gunmetal"], rot=(0, math.radians(90), 0), verts=14)
    add_cyl("Suppressor", p(0.67, 0.0, 0.040), 0.024, 0.18, M["dark"], rot=(0, math.radians(90), 0), verts=16)
    # Picatinny top rail + Holographic Reflex Sight (EOTech style with glowing dot!)
    add_box("TopRail", p(0.14, 0.0, 0.076), (0.48, 0.026, 0.010), M["gunmetal"], 0.0)
    add_box("HoloBase", p(0.04, 0.0, 0.094), (0.11, 0.042, 0.022), M["dark"], 0.003)
    add_box("HoloHoodL", p(0.04, 0.022, 0.126), (0.095, 0.008, 0.048), M["gunmetal"], 0.002)
    add_box("HoloHoodR", p(0.04, -0.022, 0.126), (0.095, 0.008, 0.048), M["gunmetal"], 0.002)
    add_box("HoloHoodT", p(0.04, 0.0, 0.150), (0.095, 0.052, 0.008), M["gunmetal"], 0.002)
    add_box("HoloDot", p(0.04, 0.0, 0.124), (0.008, 0.008, 0.008), M["emit_alt"], 0.0)
    # Side PEQ laser
    add_box("LaserBox", p(0.36, -0.042, 0.040), (0.09, 0.026, 0.030), M["dark"], 0.003)
    add_cyl("LaserDot", p(0.408, -0.042, 0.040), 0.007, 0.006, M["emit_alt"], rot=(0, math.radians(90), 0), verts=10, bevel=0)
    export_mesh("gun_assault_rifle", rotate_gun_x_to_y=True)


def build_gun_shotgun():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.12, 0.09

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Heavy receiver + wood/polymer shotgun stock
    add_box("Receiver", p(0.04, 0.0, 0.032), (0.28, 0.058, 0.068), M["gunmetal"], 0.006)
    add_box("Eject", p(0.06, -0.029, 0.038), (0.09, 0.008, 0.026), M["chrome"], 0.0)
    add_box("Grip", p(-0.12, 0.0, -0.075), (0.050, 0.044, 0.125), M["wood"], 0.008, rot=(0, math.radians(-26), 0))
    add_box("Stock", p(-0.29, 0.0, -0.015), (0.26, 0.048, 0.105), M["wood"], 0.008, rot=(0, math.radians(5), 0))
    add_box("Butt", p(-0.422, 0.0, -0.024), (0.024, 0.052, 0.122), M["grip"], 0.005)
    add_box("TrigGuard", p(-0.04, 0.0, -0.040), (0.084, 0.020, 0.010), M["dark"], 0.002)
    # Main cylindrical barrel + under-barrel tubular magazine + barrel clamp
    add_cyl("Barrel", p(0.48, 0.0, 0.052), 0.021, 0.64, M["gunmetal"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("TubeMag", p(0.45, 0.0, 0.008), 0.018, 0.56, M["dark"], rot=(0, math.radians(90), 0), verts=14)
    add_box("Clamp", p(0.68, 0.0, 0.030), (0.030, 0.046, 0.072), M["gunmetal"], 0.004)
    # Ribbed pump forend
    add_cyl("Pump", p(0.40, 0.0, 0.008), 0.032, 0.22, M["wood"], rot=(0, math.radians(90), 0), verts=14)
    for i in range(4):
        add_torus(f"PumpRib_{i}", p(0.32 + i * 0.052, 0.0, 0.008), 0.033, 0.004, M["grip"], rot=(0, math.radians(90), 0), maj_seg=16, min_seg=6)
    # Side shell saddle with 4 brass/red 12G shells!
    for i in range(4):
        sx = -0.02 + i * 0.032
        add_cyl(f"ShellB_{i}", p(sx, 0.036, 0.020), 0.011, 0.048, M["emit_alt"], verts=10, bevel=0)
        add_cyl(f"ShellC_{i}", p(sx, 0.036, 0.048), 0.0115, 0.014, M["brass"], verts=10, bevel=0)
    # Glowing ghost-ring sight + front tritium bead
    add_torus("GhostRing", p(-0.04, 0.0, 0.086), 0.016, 0.0035, M["dark"], rot=(0, math.radians(90), 0), maj_seg=16, min_seg=6)
    add_box("FrontSight", p(0.77, 0.0, 0.082), (0.016, 0.010, 0.020), M["emit_main"], 0.0)
    export_mesh("gun_shotgun", rotate_gun_x_to_y=True)


def build_gun_pistol():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.05, 0.085

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Machined slide + serrations + frame + cylindrical barrel
    add_box("Slide", p(0.08, 0.0, 0.045), (0.32, 0.046, 0.048), M["gunmetal"], 0.005)
    add_box("Frame", p(0.07, 0.0, 0.008), (0.28, 0.042, 0.034), M["dark"], 0.004)
    add_box("Eject", p(0.09, -0.022, 0.050), (0.068, 0.008, 0.020), M["chrome"], 0.0)
    add_cyl("Barrel", p(0.255, 0.0, 0.044), 0.013, 0.08, M["chrome"], rot=(0, math.radians(90), 0), verts=14)
    # Ergonomic grip + extended mag floorplate + trigger guard
    add_box("Grip", p(-0.045, 0.0, -0.085), (0.056, 0.042, 0.145), M["grip"], 0.007, rot=(0, math.radians(-16), 0))
    add_box("MagFloor", p(-0.068, 0.0, -0.162), (0.062, 0.046, 0.018), M["gunmetal"], 0.003, rot=(0, math.radians(-16), 0))
    add_box("TrigGuardB", p(0.03, 0.0, -0.050), (0.088, 0.018, 0.009), M["dark"], 0.002)
    add_box("TrigGuardF", p(0.072, 0.0, -0.028), (0.010, 0.018, 0.044), M["dark"], 0.002)
    add_box("Trigger", p(0.020, 0.0, -0.028), (0.009, 0.009, 0.032), M["brass"], 0.0, rot=(0, math.radians(18), 0))
    # Under-barrel tactical light + glowing lens
    add_box("TacLight", p(0.16, 0.0, -0.022), (0.095, 0.034, 0.032), M["dark"], 0.004)
    add_cyl("LightLens", p(0.210, 0.0, -0.022), 0.012, 0.006, M["emit_main"], rot=(0, math.radians(90), 0), verts=12, bevel=0)
    # Mini reflex optic + 3-dot tritium iron sights
    add_box("OpticBase", p(-0.02, 0.0, 0.076), (0.062, 0.036, 0.012), M["dark"], 0.002)
    add_box("OpticL", p(-0.02, 0.017, 0.098), (0.048, 0.006, 0.034), M["gunmetal"], 0.001)
    add_box("OpticR", p(-0.02, -0.017, 0.098), (0.048, 0.006, 0.034), M["gunmetal"], 0.001)
    add_box("OpticT", p(-0.02, 0.0, 0.114), (0.048, 0.040, 0.006), M["gunmetal"], 0.001)
    add_box("OpticDot", p(-0.02, 0.0, 0.096), (0.007, 0.007, 0.007), M["emit_alt"], 0.0)
    add_box("FrontSight", p(0.225, 0.0, 0.078), (0.014, 0.009, 0.016), M["emit_main"], 0.0)
    export_mesh("gun_pistol", rotate_gun_x_to_y=True)


def build_gun_revolver():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.07, 0.085

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Frame + sculpted curved walnut revolver grip
    add_box("Frame", p(0.03, 0.0, 0.032), (0.16, 0.038, 0.076), M["chrome"], 0.005)
    add_box("Grip", p(-0.07, 0.0, -0.075), (0.054, 0.040, 0.140), M["wood"], 0.009, rot=(0, math.radians(-28), 0))
    add_box("Hammer", p(-0.052, 0.0, 0.072), (0.028, 0.014, 0.032), M["dark"], 0.002, rot=(0, math.radians(-30), 0))
    add_box("TrigGuard", p(0.02, 0.0, -0.038), (0.078, 0.016, 0.010), M["chrome"], 0.002)
    add_box("Trigger", p(0.01, 0.0, -0.018), (0.009, 0.009, 0.028), M["brass"], 0.0, rot=(0, math.radians(18), 0))
    # Rotating cylindrical 6-shot drum + 6 brass cartridge rims!
    add_cyl("Drum", p(0.045, 0.0, 0.038), 0.038, 0.082, M["gunmetal"], rot=(0, math.radians(90), 0), verts=18)
    for i in range(6):
        a = i * math.tau / 6.0
        add_cyl(f"Shell_{i}", p(0.002, math.cos(a) * 0.022, 0.038 + math.sin(a) * 0.022), 0.0085, 0.010, M["brass"], rot=(0, math.radians(90), 0), verts=8, bevel=0)
    # Heavy vented magnum barrel + underlug + glowing front blade
    add_cyl("Barrel", p(0.24, 0.0, 0.056), 0.017, 0.28, M["chrome"], rot=(0, math.radians(90), 0), verts=14)
    add_box("Underlug", p(0.23, 0.0, 0.034), (0.26, 0.024, 0.026), M["gunmetal"], 0.003)
    add_box("TopRib", p(0.24, 0.0, 0.076), (0.26, 0.018, 0.014), M["dark"], 0.002)
    add_box("FrontSight", p(0.355, 0.0, 0.092), (0.022, 0.009, 0.020), M["emit_alt"], 0.0)
    export_mesh("gun_revolver", rotate_gun_x_to_y=True)


def build_gun_flamethrower():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.12, 0.09

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Heavy industrial chassis + dual pressurized red/copper fuel canisters!
    add_box("Chassis", p(0.08, 0.0, 0.025), (0.40, 0.072, 0.076), M["gunmetal"], 0.006)
    add_box("Grip", p(-0.11, 0.0, -0.085), (0.050, 0.044, 0.130), M["grip"], 0.007, rot=(0, math.radians(-16), 0))
    add_box("RearBrace", p(-0.26, 0.0, 0.015), (0.18, 0.048, 0.090), M["dark"], 0.006)
    add_cyl("TankL", p(0.06, 0.062, -0.035), 0.048, 0.30, M["copper"], rot=(0, math.radians(90), 0), verts=16)
    add_cyl("TankR", p(0.06, -0.062, -0.035), 0.048, 0.30, M["copper"], rot=(0, math.radians(90), 0), verts=16)
    add_torus("TankBandF", p(0.16, 0.0, -0.035), 0.082, 0.008, M["dark"], rot=(0, math.radians(90), 0), maj_seg=18, min_seg=6)
    # Curved Bezier fuel hose + brass gauge
    add_curve_tube("FuelHose", [p(0.20, 0.06, -0.035), p(0.32, 0.05, -0.08), p(0.42, 0.0, 0.01)], 0.010, M["grip"])
    add_cyl("Gauge", p(0.02, -0.045, 0.078), 0.022, 0.016, M["brass"], rot=(math.radians(90), 0, 0), verts=14)
    # Perforated heat shroud + nozzle + glowing molten pilot flame!
    add_cyl("NozzlePipe", p(0.48, 0.0, 0.032), 0.020, 0.48, M["dark"], rot=(0, math.radians(90), 0), verts=14)
    add_cyl("HeatShroud", p(0.44, 0.0, 0.032), 0.038, 0.32, M["gunmetal"], rot=(0, math.radians(90), 0), verts=14)
    for i in range(4):
        add_torus(f"ShroudRing_{i}", p(0.32 + i * 0.08, 0.0, 0.032), 0.041, 0.005, M["brass"], rot=(0, math.radians(90), 0), maj_seg=16, min_seg=6)
    add_cyl("NozzleBell", p(0.73, 0.0, 0.032), 0.030, 0.055, M["copper"], rot=(0, math.radians(90), 0), verts=14)
    add_crystal("PilotFlame", p(0.79, 0.0, 0.044), 0.016, 0.016, 0.044, M["emit_main"], rot=(0, math.radians(82), 0))
    add_crystal("PilotHot", p(0.78, 0.0, 0.042), 0.009, 0.009, 0.024, M["emit_hot"], rot=(0, math.radians(82), 0))
    export_mesh("gun_flamethrower", rotate_gun_x_to_y=True)


def build_gun_sonic_pulse():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.10, 0.09

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Futuristic bullpup chassis + open glowing core cage + parabolic sonic dish!
    add_box("Body", p(-0.02, 0.0, 0.025), (0.36, 0.068, 0.084), M["dark"], 0.008)
    add_box("Grip", p(-0.10, 0.0, -0.085), (0.048, 0.042, 0.125), M["grip"], 0.007, rot=(0, math.radians(-14), 0))
    add_box("TopHandle", p(0.06, 0.0, 0.102), (0.28, 0.032, 0.020), M["gunmetal"], 0.004)
    add_box("HandlePillarF", p(0.18, 0.0, 0.076), (0.028, 0.030, 0.050), M["gunmetal"], 0.003)
    add_box("HandlePillarR", p(-0.06, 0.0, 0.076), (0.028, 0.030, 0.050), M["gunmetal"], 0.003)
    # Floating glowing resonance crystal inside open cage
    add_crystal("SonicCore", p(0.28, 0.0, 0.034), 0.036, 0.036, 0.078, M["emit_main"], rot=(0, math.radians(90), 0))
    add_crystal("SonicHot", p(0.28, 0.0, 0.034), 0.020, 0.020, 0.046, M["emit_hot"], rot=(0, math.radians(90), 0))
    for ang in (0, 120, 240):
        ra = math.radians(ang)
        add_box(f"CageStrut_{ang}", p(0.32, math.cos(ra) * 0.058, 0.034 + math.sin(ra) * 0.058), (0.34, 0.014, 0.014), M["gunmetal"], 0.002)
    # Triple concentric parabolic emitter rings!
    for i, (rx, rad) in enumerate(((0.46, 0.054), (0.55, 0.076), (0.64, 0.098))):
        add_torus(f"DishFrame_{i}", p(rx, 0.0, 0.034), rad, 0.009, M["brass"], rot=(0, math.radians(90), 0), maj_seg=22, min_seg=8)
        add_torus(f"DishGlow_{i}", p(rx + 0.012, 0.0, 0.034), rad * 0.88, 0.005, M["emit_main"], rot=(0, math.radians(90), 0), maj_seg=20, min_seg=6)
    export_mesh("gun_sonic_pulse", rotate_gun_x_to_y=True)


def build_office_crossbow():
    reset_scene()
    M = standard_mats()
    ox, oz = 0.10, 0.08

    def p(x, y, z):
        return (x + ox, y, z + oz)

    # Improvised wooden/aluminum track + taped grip + curved Bezier bow limbs + loaded pen bolt!
    add_box("Stock", p(-0.12, 0.0, 0.005), (0.28, 0.040, 0.058), M["wood"], 0.006)
    add_box("Track", p(0.22, 0.0, 0.026), (0.44, 0.046, 0.030), M["gunmetal"], 0.004)
    add_box("Grip", p(-0.10, 0.0, -0.075), (0.044, 0.038, 0.115), M["grip"], 0.006, rot=(0, math.radians(-18), 0))
    add_box("TapeWrap", p(0.06, 0.0, 0.026), (0.07, 0.052, 0.036), M["dark"], 0.003)
    # Curved Bezier crossbow prod limbs
    add_curve_tube("LimbL", [p(0.41, 0.02, 0.032), p(0.36, 0.16, 0.034), p(0.24, 0.28, 0.032)], 0.012, M["brass"])
    add_curve_tube("LimbR", [p(0.41, -0.02, 0.032), p(0.36, -0.16, 0.034), p(0.24, -0.28, 0.032)], 0.012, M["brass"])
    # Tensioned rubber-band bowstring
    add_curve_tube("StringL", [p(0.24, 0.28, 0.032), p(0.08, 0.0, 0.046)], 0.004, M["chrome"], res_u=4, bev_res=1)
    add_curve_tube("StringR", [p(0.24, -0.28, 0.032), p(0.08, 0.0, 0.046)], 0.004, M["chrome"], res_u=4, bev_res=1)
    # Loaded glowing ballpoint-pen bolt!
    add_cyl("BoltShaft", p(0.29, 0.0, 0.050), 0.007, 0.42, M["emit_main"], rot=(0, math.radians(90), 0), verts=10, bevel=0)
    add_cone("BoltTip", p(0.51, 0.0, 0.050), 0.012, 0.001, 0.045, M["chrome"], rot=(0, math.radians(90), 0), verts=8)
    add_box("Sight", p(0.02, 0.0, 0.072), (0.020, 0.034, 0.026), M["dark"], 0.002)
    add_box("SightDot", p(0.02, 0.0, 0.082), (0.008, 0.008, 0.008), M["emit_alt"], 0.0)
    export_mesh("office_crossbow", rotate_gun_x_to_y=True)


# =============================================================================
# 2. EVOLVING MAGIC STAFFS (TIER 1 -> TIER 2 -> TIER 3!) + OTHER MAGIC RELICS
#    Built upright along +Z with grip centered at (0, 0, 0) -> exports directly to MC +Y!
# =============================================================================
def build_magic_staff(tier):
    reset_scene()
    M = standard_mats()
    shaft_mat = M["wood"] if tier == 1 else (M["gunmetal"] if tier == 2 else M["obsidian"])
    trim_mat = M["gunmetal"] if tier == 1 else M["brass"]

    # Grip centered at z = 0.0 (total height ~1.35m from pommel -0.45 to crown +0.95)
    add_cyl("Shaft", (0, 0, 0.22), 0.021 + tier * 0.002, 1.24, shaft_mat, verts=14)
    add_cyl("Grip", (0, 0, 0.0), 0.028, 0.28, M["leather"], verts=14)
    for r in range(4):
        add_torus(f"GripRing_{r}", (0, 0, -0.105 + r * 0.07), 0.029, 0.0038, trim_mat, maj_seg=18, min_seg=6)

    # Pommel
    add_cyl("Pommel", (0, 0, -0.40), 0.032, 0.055, trim_mat, verts=14)
    if tier >= 2:
        add_crystal("PommelGem", (0, 0, -0.46), 0.020, 0.020, 0.048, M["emit_main"])

    # Spiral filigree bands on Tier 2 & 3
    if tier >= 2:
        for i in range(5 if tier == 3 else 3):
            z = 0.22 + i * (0.11 if tier == 3 else 0.17)
            add_torus(f"Band_{i}", (0, 0, z), 0.030, 0.005, M["brass"], rot=(math.radians(14), 0, i * 0.8), maj_seg=18, min_seg=6)
            if tier == 3 and i % 2 == 0:
                add_torus(f"Rune_{i}", (0, 0, z + 0.052), 0.028, 0.0036, M["emit_alt"], maj_seg=18, min_seg=6)

    # Crown collar
    add_cyl("Collar", (0, 0, 0.83), 0.040 + tier * 0.004, 0.062, trim_mat, verts=16)
    add_torus("CollarRing", (0, 0, 0.865), 0.048 + tier * 0.005, 0.007, trim_mat, maj_seg=20, min_seg=7)

    # Curved Bezier claws (2 -> 3 -> 4 + 4 glowing inner prongs!)
    n_claws = 2 if tier == 1 else (3 if tier == 2 else 4)
    for c in range(n_claws):
        ang = c * math.tau / n_claws + math.radians(20)
        rout = 0.088 + tier * 0.020
        ht = 0.22 + tier * 0.048
        pts = [(0.018, 0.0, 0.0), (rout * 1.15, 0.0, ht * 0.52), (rout * 0.45, 0.0, ht * 1.04)]
        add_curve_tube(f"Claw_{c}", pts, 0.009 + tier * 0.0018, trim_mat, loc=(0, 0, 0.85), rot=(0, 0, ang), res_u=7, bev_res=2)
        if tier == 3:
            pts2 = [(0.015, 0.0, 0.0), (0.080, 0.0, 0.095), (0.038, 0.0, 0.175)]
            add_curve_tube(f"InnerClaw_{c}", pts2, 0.0055, M["emit_alt"], loc=(0, 0, 0.865), rot=(0, 0, ang + math.pi / 4), res_u=6, bev_res=1)

    # Floating faceted crystal + glowing halos + satellites!
    cy = 1.00 + tier * 0.025
    if tier == 1:
        add_crystal("Core", (0, 0, cy), 0.042, 0.042, 0.088, M["emit_main"], rot=(0, 0, math.radians(15)))
    elif tier == 2:
        add_crystal("Core", (0, 0, cy), 0.054, 0.054, 0.115, M["emit_main"], rot=(0, 0, math.radians(25)))
        add_torus("Halo1", (0, 0, cy), 0.135, 0.0058, M["emit_main"], rot=(math.radians(65), math.radians(20), 0), maj_seg=26, min_seg=7)
    else:
        add_crystal("CoreOuter", (0, 0, cy), 0.066, 0.066, 0.142, M["emit_main"], rot=(0, 0, math.radians(30)))
        add_crystal("CoreHot", (0, 0, cy), 0.036, 0.036, 0.084, M["emit_hot"])
        add_torus("HaloA", (0, 0, cy), 0.172, 0.0075, M["emit_main"], rot=(math.radians(68), math.radians(24), 0), maj_seg=28, min_seg=8)
        add_torus("HaloB", (0, 0, cy), 0.208, 0.0058, M["emit_alt"], rot=(math.radians(-62), math.radians(-26), 0), maj_seg=28, min_seg=8)
        add_torus("GoldHalo", (0, 0, cy), 0.144, 0.0055, M["brass"], rot=(math.radians(82), math.radians(-14), 0), maj_seg=24, min_seg=7)
        for s in range(6):
            sa = s * math.tau / 6.0
            sx = math.cos(sa) * 0.190
            sy = math.sin(sa) * 0.108
            sz = cy + math.sin(sa * 2.0) * 0.088
            add_crystal(f"Sat_{s}", (sx, sy, sz), 0.015, 0.015, 0.038, M["emit_alt"] if s % 2 == 0 else M["emit_main"], rot=(0.3, -0.3, sa))
        add_crystal("Spire", (0, 0, cy + 0.225), 0.022, 0.022, 0.062, M["emit_hot"])

    export_mesh(f"magic_staff_t{tier}")


def build_magic_crimson_scythe():
    reset_scene()
    M = standard_mats()
    # Long obsidian haft + wrapped grip + ornate collar + sweeping curved crescent blade + glowing inner edge!
    add_cyl("Shaft", (0, 0, 0.24), 0.022, 1.38, M["obsidian"], verts=14)
    add_cyl("Grip", (0, 0, 0.0), 0.029, 0.30, M["leather"], verts=14)
    add_cyl("Pommel", (0, 0, -0.45), 0.034, 0.06, M["brass"], verts=14)
    add_crystal("PommelOrb", (0, 0, -0.51), 0.022, 0.022, 0.044, M["emit_main"])
    add_cyl("Collar", (0, 0, 0.90), 0.044, 0.08, M["brass"], verts=14)
    # Sweeping curved scythe blade extending forward (+Y -> MC -Z!)
    add_curve_tube("BladeSpine", [(0, -0.08, 0.92), (0, 0.22, 1.00), (0, 0.48, 0.84), (0, 0.56, 0.56)], 0.022, M["chrome"], res_u=10, bev_res=2)
    add_curve_tube("BladeEdgeGlow", [(0, -0.02, 0.87), (0, 0.22, 0.94), (0, 0.44, 0.78), (0, 0.53, 0.54)], 0.011, M["emit_main"], res_u=10, bev_res=1)
    add_curve_tube("BackSpike", [(0, -0.04, 0.92), (0, -0.18, 0.98), (0, -0.26, 0.92)], 0.012, M["brass"], res_u=6, bev_res=1)
    # Floating blood/arcane orb trapped in the crook of the crescent!
    add_crystal("CrookOrb", (0, 0.20, 0.78), 0.048, 0.048, 0.072, M["emit_main"])
    add_torus("CrookHalo", (0, 0.20, 0.78), 0.088, 0.005, M["emit_alt"], rot=(math.radians(70), math.radians(15), 0), maj_seg=22, min_seg=6)
    export_mesh("magic_crimson_scythe")


def build_magic_grimoire():
    reset_scene()
    M = standard_mats()
    # Open levitating arcane spellbook (tilted nicely toward holder)
    add_box("Spine", (0, 0.0, 0.14), (0.045, 0.28, 0.036), M["leather"], 0.005)
    add_box("CoverL", (-0.11, 0.0, 0.17), (0.21, 0.29, 0.022), M["obsidian"], 0.005, rot=(0, math.radians(-20), 0))
    add_box("CoverR", (0.11, 0.0, 0.17), (0.21, 0.29, 0.022), M["obsidian"], 0.005, rot=(0, math.radians(20), 0))
    add_box("PagesL", (-0.10, 0.0, 0.195), (0.185, 0.26, 0.026), M["parchment"], 0.003, rot=(0, math.radians(-18), 0))
    add_box("PagesR", (0.10, 0.0, 0.195), (0.185, 0.26, 0.026), M["parchment"], 0.003, rot=(0, math.radians(18), 0))
    # Brass corner clasps
    for sx in (-0.20, 0.20):
        for sy in (-0.13, 0.13):
            add_box(f"Clasp_{sx}_{sy}", (sx, sy, 0.205), (0.032, 0.032, 0.030), M["brass"], 0.003)
    # Floating glowing arcane sigil ring + miniature hovering crystal above open pages!
    add_torus("SigilOuter", (0, 0.0, 0.34), 0.13, 0.006, M["emit_main"], rot=(math.radians(12), 0, 0), maj_seg=24, min_seg=6)
    add_torus("SigilInner", (0, 0.0, 0.34), 0.085, 0.0045, M["emit_alt"], rot=(math.radians(-15), math.radians(18), 0), maj_seg=20, min_seg=6)
    add_crystal("BookCrystal", (0, 0.0, 0.36), 0.034, 0.034, 0.072, M["emit_hot"])
    export_mesh("magic_grimoire")


def build_magic_bell():
    reset_scene()
    M = standard_mats()
    # Ceremonial hand bell with ornate handle + flared lathed bell + glowing soundwave ring
    add_cyl("Handle", (0, 0, 0.02), 0.022, 0.22, M["wood"], verts=14)
    add_torus("HandleRing", (0, 0, 0.12), 0.032, 0.006, M["brass"], maj_seg=18, min_seg=6)
    add_crystal("HandleGem", (0, 0, -0.12), 0.020, 0.020, 0.042, M["emit_main"])
    # Bell crown -> waist -> flared rim (pointing up/forward for visibility in 1st person)
    add_cone("BellUpper", (0, 0, 0.22), 0.070, 0.032, 0.12, M["brass"], verts=18)
    add_cone("BellSkirt", (0, 0, 0.34), 0.115, 0.068, 0.14, M["brass"], verts=18)
    add_torus("BellRim", (0, 0, 0.41), 0.118, 0.012, M["brass"], maj_seg=22, min_seg=8)
    add_cyl("Clapper", (0, 0, 0.36), 0.018, 0.09, M["emit_hot"], verts=12)
    add_torus("SoundWave1", (0, 0, 0.43), 0.152, 0.0055, M["emit_main"], maj_seg=24, min_seg=6)
    add_torus("SoundWave2", (0, 0, 0.47), 0.188, 0.0040, M["emit_alt"], maj_seg=24, min_seg=6)
    export_mesh("magic_bell")


def build_magic_watch():
    reset_scene()
    M = standard_mats()
    # Ornate pocket watch / mirror / relic facing holder along -Y / slightly angled
    add_cyl("Case", (0, 0.0, 0.18), 0.135, 0.042, M["brass"], rot=(math.radians(90), 0, 0), verts=22)
    add_torus("Bezel", (0, -0.022, 0.18), 0.135, 0.012, M["brass"], rot=(math.radians(90), 0, 0), maj_seg=24, min_seg=8)
    add_cyl("GlowDial", (0, -0.020, 0.18), 0.115, 0.010, M["emit_main"], rot=(math.radians(90), 0, 0), verts=20, bevel=0)
    # Clock hands
    add_box("HandHour", (0.022, -0.028, 0.205), (0.012, 0.006, 0.072), M["obsidian"], 0.0, rot=(0, math.radians(-32), 0))
    add_box("HandMin", (-0.015, -0.030, 0.220), (0.009, 0.006, 0.098), M["emit_hot"], 0.0, rot=(0, math.radians(18), 0))
    # Hinged open lid + crown bow ring
    add_cyl("OpenLid", (0, 0.068, 0.345), 0.130, 0.016, M["brass"], rot=(math.radians(28), 0, 0), verts=20)
    add_cyl("Crown", (0, 0.0, 0.335), 0.020, 0.040, M["brass"], verts=12)
    add_torus("BowRing", (0, 0.0, 0.375), 0.042, 0.007, M["brass"], rot=(math.radians(90), 0, 0), maj_seg=18, min_seg=6)
    add_torus("TimeHalo", (0, -0.035, 0.18), 0.168, 0.005, M["emit_alt"], rot=(math.radians(90), 0, 0), maj_seg=24, min_seg=6)
    export_mesh("magic_watch")


# =============================================================================
# 3. SIGNATURE MELEE WEAPONS (5 Sculpted Curved / Diamond-Section Silhouettes)
#    Grip centered at (0, 0, 0), blade extends +Z (-> MC +Y), edge faces +Y (-> MC -Z)!
# =============================================================================
def build_melee_greatsword():
    reset_scene()
    M = standard_mats()
    # Leather wrapped grip + faceted weighted pommel
    add_cyl("Grip", (0, 0, 0.0), 0.024, 0.24, M["leather"], verts=12)
    for i in range(4):
        add_torus(f"GripBand_{i}", (0, 0, -0.09 + i * 0.06), 0.026, 0.0035, M["brass"], maj_seg=16, min_seg=6)
    add_crystal("Pommel", (0, 0, -0.16), 0.038, 0.038, 0.048, M["brass"])
    add_crystal("PommelGem", (0, 0, -0.16), 0.022, 0.044, 0.026, M["emit_main"])
    # Sweeping curved Gothic crossguard!
    add_box("GuardHub", (0, 0, 0.145), (0.044, 0.095, 0.044), M["brass"], 0.005)
    add_curve_tube("GuardF", [(0, 0.03, 0.145), (0, 0.13, 0.155), (0, 0.20, 0.205)], 0.014, M["brass"], res_u=6, bev_res=2)
    add_curve_tube("GuardB", [(0, -0.03, 0.145), (0, -0.13, 0.155), (0, -0.20, 0.205)], 0.014, M["brass"], res_u=6, bev_res=2)
    add_crystal("GuardGem", (0, 0, 0.145), 0.028, 0.028, 0.028, M["emit_main"])
    # Sculpted diamond-section greatsword blade + glowing central rune fuller!
    add_diamond_blade("Blade", 0.16, 1.18, 0.105, 0.092, 0.032, M["chrome"], y_curve=0.0)
    add_box("RuneFuller", (0, 0.004, 0.58), (0.035, 0.018, 0.68), M["emit_main"], 0.0)
    export_mesh("melee_greatsword")


def build_melee_curved_dao():
    reset_scene()
    M = standard_mats()
    # Chinese Tang Dao / Miaodao / Curved Saber with graceful Bezier spine + disc guard + ring pommel!
    add_cyl("Grip", (0, 0, 0.0), 0.022, 0.26, M["leather"], verts=12)
    add_torus("RingPommel", (0, 0, -0.165), 0.036, 0.007, M["brass"], rot=(0, math.radians(90), 0), maj_seg=18, min_seg=6)
    add_cyl("DiscGuard", (0, 0, 0.145), 0.072, 0.018, M["brass"], verts=18)
    add_torus("GuardRim", (0, 0, 0.145), 0.074, 0.007, M["gunmetal"], maj_seg=18, min_seg=6)
    # Graceful sweeping curved blade (+Y cutting edge!)
    add_diamond_blade("CurvedBlade", 0.155, 1.14, 0.076, 0.088, 0.026, M["chrome"], y_curve=-0.09)
    add_curve_tube("EdgeGlow", [(0, 0.038, 0.20), (0, 0.025, 0.58), (0, -0.022, 0.96), (0, -0.075, 1.10)], 0.0065, M["emit_main"], res_u=8, bev_res=1)
    export_mesh("melee_curved_dao")


def build_melee_warhammer():
    reset_scene()
    M = standard_mats()
    # Heavy flanged warhammer / Thunder Colossus / Crimson Crusher
    add_cyl("Haft", (0, 0, 0.24), 0.024, 1.08, M["wood"], verts=14)
    add_cyl("Grip", (0, 0, 0.0), 0.030, 0.28, M["leather"], verts=14)
    add_cyl("Pommel", (0, 0, -0.32), 0.036, 0.06, M["brass"], verts=14)
    add_cyl("Socket", (0, 0, 0.78), 0.044, 0.14, M["brass"], verts=14)
    # Massive beveled hammer head (+Y front face, -Y armor-piercing curved pick!)
    add_box("HammerCore", (0, 0.04, 0.80), (0.14, 0.22, 0.16), M["gunmetal"], 0.014)
    add_box("ImpactFace", (0, 0.165, 0.80), (0.165, 0.042, 0.185), M["chrome"], 0.008)
    add_box("GlowBandL", (0.072, 0.04, 0.80), (0.010, 0.14, 0.065), M["emit_main"], 0.0)
    add_box("GlowBandR", (-0.072, 0.04, 0.80), (0.010, 0.14, 0.065), M["emit_main"], 0.0)
    add_box("FaceRune", (0, 0.188, 0.80), (0.095, 0.008, 0.105), M["emit_hot"], 0.0)
    # Curved rear crow-beak spike
    add_curve_tube("RearBeak", [(0, -0.05, 0.81), (0, -0.17, 0.80), (0, -0.25, 0.72)], 0.020, M["chrome"], res_u=6, bev_res=1)
    add_cone("TopSpike", (0, 0.0, 0.96), 0.034, 0.002, 0.16, M["brass"], verts=8)
    export_mesh("melee_warhammer")


def build_melee_polearm():
    reset_scene()
    M = standard_mats()
    # Guandao / Fangtian Ji / Western Halberd
    add_cyl("Shaft", (0, 0, 0.30), 0.021, 1.46, M["obsidian"], verts=14)
    add_cyl("Grip", (0, 0, 0.0), 0.028, 0.32, M["leather"], verts=14)
    add_cyl("ButtCap", (0, 0, -0.44), 0.028, 0.07, M["brass"], verts=12)
    add_cyl("Collar", (0, 0, 0.98), 0.040, 0.09, M["brass"], verts=14)
    # Glowing energy ring collar + piercing central spearhead
    add_torus("SocketGlow", (0, 0, 1.02), 0.052, 0.007, M["emit_main"], maj_seg=18, min_seg=6)
    add_diamond_blade("SpearTip", 1.02, 1.48, 0.062, 0.050, 0.024, M["chrome"], y_curve=0.0)
    # Sweeping crescent side-blade (+Y front) & counter-hook (-Y back)
    add_curve_tube("CrescentBlade", [(0, 0.03, 0.98), (0, 0.18, 1.12), (0, 0.16, 1.32), (0, 0.04, 1.38)], 0.018, M["chrome"], res_u=8, bev_res=2)
    add_curve_tube("CrescentGlow", [(0, 0.08, 1.02), (0, 0.21, 1.14), (0, 0.19, 1.30)], 0.008, M["emit_main"], res_u=7, bev_res=1)
    add_curve_tube("BackHook", [(0, -0.03, 1.04), (0, -0.15, 1.08), (0, -0.19, 0.98)], 0.012, M["brass"], res_u=6, bev_res=1)
    export_mesh("melee_polearm")


def build_melee_dagger():
    reset_scene()
    M = standard_mats()
    # Sleek agile tactical dagger / rapier / ritual blade
    add_cyl("Grip", (0, 0, 0.0), 0.020, 0.18, M["leather"], verts=12)
    add_crystal("Pommel", (0, 0, -0.12), 0.026, 0.026, 0.034, M["brass"])
    add_curve_tube("SGuard", [(0, -0.09, 0.08), (0, 0.0, 0.105), (0, 0.10, 0.135)], 0.010, M["brass"], res_u=7, bev_res=1)
    add_diamond_blade("DaggerBlade", 0.11, 0.68, 0.062, 0.048, 0.022, M["chrome"], y_curve=0.03)
    add_box("EdgeCore", (0, 0.008, 0.36), (0.025, 0.014, 0.38), M["emit_main"], 0.0)
    export_mesh("melee_dagger")


# =============================================================================
# 4. SHARED ANIMATED EVOLUTION AURA HALO (Spins around Tier 2/3 Evolved Weapons!)
# =============================================================================
def build_evo_halo_ring():
    reset_scene()
    M = standard_mats()
    add_torus("EvoRing", (0, 0, 0), 0.145, 0.0055, M["emit_main"], maj_seg=26, min_seg=6)
    for i in range(4):
        a = i * math.tau / 4.0
        add_crystal(f"EvoNode_{i}", (math.cos(a) * 0.145, math.sin(a) * 0.145, 0), 0.012, 0.012, 0.026, M["emit_hot"], rot=(math.radians(90), 0, a))
    export_mesh("evo_halo_ring")


def main():
    write_white_png(TEX_PATH, size=4)
    print(f"Wrote white texture: {TEX_PATH}")

    builders = [
        build_gun_railgun,
        build_gun_sniper,
        build_gun_assault_rifle,
        build_gun_shotgun,
        build_gun_pistol,
        build_gun_revolver,
        build_gun_flamethrower,
        build_gun_sonic_pulse,
        build_office_crossbow,
        lambda: build_magic_staff(1),
        lambda: build_magic_staff(2),
        lambda: build_magic_staff(3),
        build_magic_crimson_scythe,
        build_magic_grimoire,
        build_magic_bell,
        build_magic_watch,
        build_melee_greatsword,
        build_melee_curved_dao,
        build_melee_warhammer,
        build_melee_polearm,
        build_melee_dagger,
        build_evo_halo_ring,
    ]
    for fn in builders:
        fn()
    print(f"ALL {len(builders)} BLENDER WEAPON MESHES EXPORTED SUCCESSFULLY!")


if __name__ == "__main__":
    main()
