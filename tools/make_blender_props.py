"""
Headless Blender 5.3 generator for ALL 39 Backrooms multi-block & half-sliced noclipped 3D furniture/props.
Exports triangulated .obj + .mtl directly to:
  src/main/resources/assets/noclip_backrooms/models/blender_props/

Coordinate convention (Blender -> OBJ forward='-Z', up='Y' -> Minecraft World):
  - Master block bottom-center = (0, 0, 0) in Blender.
  - Multi-block (W wide x H tall x D deep) extends:
      * X: [-0.5 .. W - 0.5]  (center X = (W - 1) * 0.5)
      * Z: [ 0.0 .. H      ]  (floor = 0.0 -> MC Y = 0.0)
      * Y: [-(D - 0.5) .. +0.5] (front face looks toward +Y -> MC -Z NORTH; depth extends along -Y -> MC +Z!)
  - TRUE NOCLIPPED SLICING:
      Every noclipped_* / *_noclipped prop is tilted at a dramatic eerie angle AND physically sliced
      cleanly along Z = 0 (floor) or Y = -0.45 (wall) via bmesh.ops.bisect_plane(clear_inner=True, use_fill=True)
      so half of the sofa / fridge / chair / computer / phone / cabinet LITERALLY VANISHES into the surface!

Run with:
  "E:\\SteamLibrary\\steamapps\\common\\Blender\\blender.exe" -b --python tools/make_blender_props.py
"""
import bpy
import bmesh
import math
import os
from mathutils import Vector, Matrix

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT_DIR = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "noclip_backrooms", "models", "blender_props")
os.makedirs(OUT_DIR, exist_ok=True)


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def make_mat(name, color, emit=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    if emit:
        bsdf.inputs["Emission Color"].default_value = (*color, 1.0)
        bsdf.inputs["Emission Strength"].default_value = 3.5
    return m


def mats():
    return {
        "steel": make_mat("MAT_STEEL", (0.58, 0.62, 0.68)),
        "dark_metal": make_mat("MAT_DARK_METAL", (0.20, 0.22, 0.26)),
        "enamel": make_mat("MAT_ENAMEL", (0.86, 0.85, 0.80)),
        "beige": make_mat("MAT_BEIGE", (0.76, 0.72, 0.62)),
        "wood": make_mat("MAT_WOOD", (0.44, 0.28, 0.15)),
        "dark_wood": make_mat("MAT_DARK_WOOD", (0.24, 0.15, 0.09)),
        "fabric": make_mat("MAT_FABRIC", (0.46, 0.36, 0.26)),
        "fabric_mold": make_mat("MAT_FABRIC_MOLD", (0.36, 0.38, 0.28)),
        "leather": make_mat("MAT_LEATHER", (0.22, 0.16, 0.14)),
        "red": make_mat("MAT_RED", (0.72, 0.18, 0.16)),
        "blue": make_mat("MAT_BLUE", (0.20, 0.48, 0.72)),
        "glass": make_mat("MAT_GLASS", (0.62, 0.82, 0.88)),
        "brass": make_mat("MAT_BRASS", (0.82, 0.62, 0.22)),
        "copper": make_mat("MAT_COPPER", (0.76, 0.36, 0.18)),
        "cardboard": make_mat("MAT_CARDBOARD", (0.64, 0.48, 0.28)),
        "green": make_mat("MAT_GREEN", (0.20, 0.48, 0.22)),
        "bone": make_mat("MAT_BONE", (0.84, 0.80, 0.70)),
        "cut_seam": make_mat("EMIT_ALT", (0.48, 0.30, 0.92), emit=True),
        "emit_main": make_mat("EMIT_MAIN", (0.32, 0.86, 1.0), emit=True),
        "emit_hot": make_mat("EMIT_HOT", (0.94, 0.96, 1.0), emit=True),
        "emit_alt": make_mat("EMIT_ALT", (1.0, 0.26, 0.32), emit=True),
    }


def smooth(obj, flag=True):
    if obj.type == "MESH":
        for p in obj.data.polygons:
            p.use_smooth = flag
    return obj


def add_box(name, loc, scale, mat, bevel=0.015, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    bpy.ops.object.transform_apply(scale=True)
    if bevel > 0:
        m = o.modifiers.new("Bevel", "BEVEL")
        m.width = bevel
        m.segments = 1
    o.data.materials.append(mat)
    return smooth(o, False)


def add_cyl(name, loc, r, depth, mat, rot=(0, 0, 0), verts=14, bevel=0.005):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    if bevel > 0:
        m = o.modifiers.new("Bevel", "BEVEL")
        m.width = bevel
        m.segments = 1
    o.data.materials.append(mat)
    return smooth(o, True)


def add_cone(name, loc, r1, r2, depth, mat, rot=(0, 0, 0), verts=14):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r1, radius2=r2, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.data.materials.append(mat)
    return smooth(o, True)


def add_torus(name, loc, maj, minr, mat, rot=(0, 0, 0), maj_seg=18, min_seg=6):
    bpy.ops.mesh.primitive_torus_add(major_segments=maj_seg, minor_segments=min_seg,
                                     major_radius=maj, minor_radius=minr, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.data.materials.append(mat)
    return smooth(o, True)


def add_crystal(name, loc, rx, ry, rz, mat, rot=(0, 0, 0)):
    bm = bmesh.new()
    top = bm.verts.new((0, 0, rz))
    bot = bm.verts.new((0, 0, -rz))
    ru, rl = [], []
    for i in range(6):
        a = i * math.tau / 6
        ru.append(bm.verts.new((math.cos(a) * rx, math.sin(a) * ry, rz * 0.25)))
        rl.append(bm.verts.new((math.cos(a) * rx * 0.9, math.sin(a) * ry * 0.9, -rz * 0.25)))
    for i in range(6):
        j = (i + 1) % 6
        bm.faces.new((top, ru[i], ru[j]))
        bm.faces.new((ru[i], rl[i], rl[j], ru[j]))
        bm.faces.new((bot, rl[j], rl[i]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    o.rotation_euler = rot
    o.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(o)
    return smooth(o, False)


def bake_and_slice_noclip(tilt_euler=(0, 0, 0), shift=(0, 0, 0), plane_co=(0, 0, 0.002), plane_no=(0, 0, 1)):
    """
    THE SIGNATURE BACKROOMS EFFECT:
    Tilts the entire furniture group at an eerie glitch angle, sinks part of it below the floor
    (or behind a wall), and PHYSICALLY BISECTS & REMOVES the buried half so it genuinely looks
    half-vanished / noclipped into reality!
    """
    scene = bpy.context.scene
    objs = [o for o in list(scene.objects) if o.type == "MESH"]
    if not objs:
        return
    # Apply modifiers first
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        bpy.context.view_layer.objects.active = o
        for mod in list(o.modifiers):
            try:
                bpy.ops.object.modifier_apply(modifier=mod.name)
            except Exception:
                pass
    # Wrap under pivot -> rotate & sink -> bake world matrix
    pivot = bpy.data.objects.new("SlicePivot", None)
    scene.collection.objects.link(pivot)
    for o in objs:
        o.parent = pivot
    pivot.rotation_euler = tilt_euler
    pivot.location = shift
    bpy.context.view_layer.update()
    for o in objs:
        mw = o.matrix_world.copy()
        o.parent = None
        o.matrix_world = mw
    bpy.data.objects.remove(pivot, do_unlink=True)

    # Apply transforms and physically bisect every mesh along plane_co / plane_no!
    p_co = Vector(plane_co)
    p_no = Vector(plane_no).normalized()
    for o in list(objs):
        bm = bmesh.new()
        bm.from_mesh(o.data)
        bmesh.ops.transform(bm, matrix=o.matrix_world, verts=bm.verts)
        o.matrix_world = Matrix.Identity(4)
        geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
        if geom:
            res = bmesh.ops.bisect_plane(
                bm, geom=geom,
                plane_co=p_co, plane_no=p_no,
                clear_inner=True, clear_outer=False
            )
            cut_edges = [g for g in res.get("geom_cut", []) if isinstance(g, bmesh.types.BMEdge)]
            if cut_edges:
                try:
                    bmesh.ops.triangle_fill(bm, edges=cut_edges, use_beauty=True)
                except Exception:
                    pass
        if len(bm.verts) == 0 or len(bm.faces) == 0:
            bm.free()
            bpy.data.objects.remove(o, do_unlink=True)
        else:
            bm.normal_update()
            bm.to_mesh(o.data)
            bm.free()


def export_prop(name):
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
    print(f"EXPORTED PROP {name}.obj")


# =============================================================================
# MULTI-BLOCK PROPS (FULL REAL-WORLD MULTI-BLOCK DIMENSIONS!)
# =============================================================================
def build_fridge(noclipped=True):
    """2 wide x 3 tall x 1 deep double-door refrigerator (both intact & half-sliced noclipped variants)."""
    reset_scene()
    M = mats()
    cx = 0.5  # 2 blocks wide: X in [-0.5 .. 1.5]
    # Main body (1.72m wide x 2.72m tall x 0.82m deep)
    add_box("Body", (cx, 0.0, 1.44), (1.72, 0.82, 2.72), M["enamel"], 0.04)
    # Recessed door seam & twin French upper doors + bottom freezer drawer
    add_box("DoorUL", (cx - 0.43, 0.43, 1.88), (0.82, 0.06, 1.72), M["enamel"], 0.025)
    add_box("DoorUR", (cx + 0.43, 0.43, 1.88), (0.82, 0.06, 1.72), M["enamel"], 0.025)
    add_box("DoorBot", (cx, 0.43, 0.56), (1.68, 0.06, 0.84), M["enamel"], 0.025)
    # Curved stainless handles
    add_cyl("HandleUL", (cx - 0.09, 0.50, 1.78), 0.022, 0.72, M["steel"], verts=12)
    add_cyl("HandleUR", (cx + 0.09, 0.50, 1.78), 0.022, 0.72, M["steel"], verts=12)
    add_cyl("HandleBot", (cx, 0.50, 0.84), 0.022, 1.05, M["steel"], rot=(0, math.radians(90), 0), verts=12)
    # Water/Ice dispenser alcove + cool cyan LED display
    add_box("Dispenser", (cx - 0.43, 0.45, 1.68), (0.34, 0.06, 0.42), M["dark_metal"], 0.01)
    add_box("DispLED", (cx - 0.43, 0.485, 1.84), (0.24, 0.01, 0.06), M["emit_main"], 0.0)
    # Rear cooling coils & compressor
    for i in range(6):
        add_cyl(f"Coil_{i}", (cx, -0.44, 0.55 + i * 0.36), 0.018, 1.40, M["copper"], rot=(0, math.radians(90), 0), verts=8)
    if noclipped:
        bake_and_slice_noclip(tilt_euler=(math.radians(7), math.radians(-16), math.radians(-5)), shift=(0.06, 0.0, -0.26))
        export_prop("noclipped_fridge")
    else:
        export_prop("prop_fridge")


def build_vending_machine(noclipped=True):
    """2 wide x 2 tall x 1 deep illuminated Backrooms vending machine (intact & tilted-sliced)."""
    reset_scene()
    M = mats()
    cx = 0.5
    add_box("Cabinet", (cx, 0.0, 1.02), (1.76, 0.84, 1.94), M["red"], 0.03)
    add_box("GlassRecess", (cx - 0.22, 0.40, 1.22), (1.16, 0.08, 1.28), M["dark_metal"], 0.01)
    add_box("GlassGlow", (cx - 0.22, 0.42, 1.22), (1.08, 0.02, 1.20), M["emit_main"], 0.0)
    for r in range(3):
        z = 0.78 + r * 0.38
        add_box(f"Shelf_{r}", (cx - 0.22, 0.43, z - 0.08), (1.06, 0.10, 0.03), M["steel"], 0.0)
        for c in range(5):
            add_cyl(f"Can_{r}_{c}", (cx - 0.64 + c * 0.21, 0.44, z + 0.04), 0.045, 0.16,
                    M["brass"] if (r + c) % 2 == 0 else M["blue"], verts=10)
    add_box("Panel", (cx + 0.60, 0.43, 1.26), (0.34, 0.04, 0.72), M["dark_metal"], 0.01)
    add_box("Display", (cx + 0.60, 0.455, 1.48), (0.24, 0.01, 0.09), M["emit_alt"], 0.0)
    add_box("Pickup", (cx - 0.22, 0.43, 0.30), (1.10, 0.08, 0.30), M["dark_metal"], 0.01)
    if noclipped:
        bake_and_slice_noclip(tilt_euler=(math.radians(8), math.radians(11), math.radians(4)), shift=(-0.05, 0.0, -0.18))
        export_prop("noclipped_vending_machine")
    else:
        export_prop("prop_vending_machine")


def build_water_cooler(noclipped=True):
    """1 wide x 2 tall x 1 deep floor-standing water cooler (intact & tilted-sliced)."""
    reset_scene()
    M = mats()
    add_box("Base", (0.0, 0.0, 0.56), (0.58, 0.56, 1.06), M["enamel"], 0.025)
    add_box("Recess", (0.0, 0.27, 0.72), (0.36, 0.10, 0.32), M["dark_metal"], 0.01)
    add_box("TapHot", (-0.09, 0.31, 0.80), (0.05, 0.08, 0.06), M["red"], 0.005)
    add_box("TapCold", (0.09, 0.31, 0.80), (0.05, 0.08, 0.06), M["blue"], 0.005)
    add_cyl("Collar", (0.0, 0.0, 1.12), 0.16, 0.10, M["steel"], verts=14)
    add_cyl("Jug", (0.0, 0.0, 1.50), 0.23, 0.60, M["glass"], verts=16, bevel=0.03)
    add_cyl("WaterCore", (0.0, 0.0, 1.44), 0.20, 0.44, M["emit_main"], verts=14, bevel=0)
    for z in (1.36, 1.52, 1.66):
        add_torus(f"JugRib_{z}", (0.0, 0.0, z), 0.232, 0.010, M["glass"], maj_seg=18, min_seg=6)
    if noclipped:
        bake_and_slice_noclip(tilt_euler=(math.radians(-12), math.radians(15), math.radians(8)), shift=(0.04, 0.0, -0.16))
        export_prop("noclipped_water_cooler")
    else:
        export_prop("prop_water_cooler")


def build_file_cabinet(noclipped=True):
    """1 wide x 2 tall x 1 deep 4-drawer office steel cabinet (intact & tilted-sliced)."""
    reset_scene()
    M = mats()
    add_box("Shell", (0.0, 0.0, 0.96), (0.76, 0.76, 1.88), M["steel"], 0.02)
    for i in range(4):
        z = 0.28 + i * 0.44
        pull = 0.28 if (i == 2 and noclipped) else (0.14 if i == 2 else 0.0)
        add_box(f"Drawer_{i}", (0.0, 0.38 + pull, z), (0.68, 0.05, 0.39), M["beige"], 0.01)
        add_box(f"Handle_{i}", (0.0, 0.43 + pull, z + 0.04), (0.24, 0.04, 0.04), M["dark_metal"], 0.005)
        if i == 2:
            add_box("OpenBox", (0.0, 0.22, z - 0.04), (0.62, 0.42, 0.30), M["dark_metal"], 0.005)
            add_box("Folder1", (-0.08, 0.32, z + 0.06), (0.50, 0.04, 0.26), M["enamel"], 0.0, rot=(math.radians(12), 0, 0))
            add_box("Folder2", (0.06, 0.22, z + 0.08), (0.48, 0.04, 0.26), M["cardboard"], 0.0, rot=(math.radians(-8), 0, 0))
    if noclipped:
        bake_and_slice_noclip(tilt_euler=(math.radians(10), math.radians(-19), math.radians(-6)), shift=(0.05, 0.0, -0.24))
        export_prop("noclipped_file_cabinet")
    else:
        export_prop("prop_file_cabinet")


def build_sofa(noclipped=False):
    """2 wide x 1 tall x 1 deep plush curved sofa! Noclipped variant tilts 26 deg & LITERALLY VANISHES half into floor!"""
    reset_scene()
    M = mats()
    cx = 0.5
    fab = M["fabric_mold"] if noclipped else M["fabric"]
    # Wooden legs
    for lx in (-0.36, 1.36):
        for ly in (-0.32, 0.32):
            add_cyl(f"Leg_{lx}_{ly}", (lx, ly, 0.08), 0.04, 0.16, M["dark_wood"], verts=10)
    # Seat base + 2 rounded cushions + slanted backrest + curved rolled armrests!
    add_box("Frame", (cx, 0.0, 0.26), (1.84, 0.80, 0.22), fab, 0.03)
    add_box("CushL", (cx - 0.39, 0.06, 0.44), (0.76, 0.68, 0.18), fab, 0.06)
    add_box("CushR", (cx + 0.39, 0.06, 0.44), (0.76, 0.68, 0.18), fab, 0.06)
    add_box("BackL", (cx - 0.39, -0.28, 0.72), (0.78, 0.24, 0.54), fab, 0.07, rot=(math.radians(-10), 0, 0))
    add_box("BackR", (cx + 0.39, -0.28, 0.72), (0.78, 0.24, 0.54), fab, 0.07, rot=(math.radians(-10), 0, 0))
    add_cyl("ArmL", (cx - 0.84, 0.0, 0.54), 0.13, 0.82, fab, rot=(math.radians(90), 0, 0), verts=14, bevel=0.02)
    add_cyl("ArmR", (cx + 0.84, 0.0, 0.54), 0.13, 0.82, fab, rot=(math.radians(90), 0, 0), verts=14, bevel=0.02)
    if noclipped:
        # Tilted diagonally with the entire right-bottom half physically sliced & vanished into the Backrooms floor!
        bake_and_slice_noclip(tilt_euler=(math.radians(16), math.radians(28), math.radians(-12)), shift=(0.08, 0.0, -0.26))
        export_prop("prop_sofa_noclipped")
    else:
        export_prop("prop_sofa")


def build_folding_table(noclipped=True):
    """2 wide x 1 tall x 1 deep folding office table (intact & tilted-sliced)."""
    reset_scene()
    M = mats()
    cx = 0.5
    add_box("Top", (cx, 0.0, 0.76), (1.88, 0.84, 0.07), M["beige"], 0.015)
    for lx in (-0.30, 1.30):
        add_cyl(f"LegF_{lx}", (lx, 0.32, 0.37), 0.022, 0.74, M["steel"], verts=10)
        add_cyl(f"LegB_{lx}", (lx, -0.32, 0.37), 0.022, 0.74, M["steel"], verts=10)
        add_cyl(f"Cross_{lx}", (lx, 0.0, 0.24), 0.018, 0.64, M["steel"], rot=(math.radians(90), 0, 0), verts=8)
    if noclipped:
        bake_and_slice_noclip(tilt_euler=(math.radians(-8), math.radians(22), math.radians(6)), shift=(0.0, 0.0, -0.22))
        export_prop("noclipped_folding_table")
    else:
        export_prop("prop_folding_table")


def add_wooden_chair_parts(prefix, M, offset=(0, 0, 0), rot=(0, 0, 0)):
    """Movie-accurate wooden ladder-back dining/lobby chair with grey padded seat cushion."""
    ox, oy, oz = offset
    pivot = bpy.data.objects.new(f"ChairPivot_{prefix}", None)
    bpy.context.scene.collection.objects.link(pivot)
    parts = []
    # 4 tapered wooden legs + stretchers
    for lx in (-0.21, 0.21):
        for ly in (-0.20, 0.20):
            parts.append(add_cyl(f"{prefix}_Leg_{lx}_{ly}", (lx, ly, 0.23), 0.022, 0.46, M["wood"], verts=10))
        parts.append(add_cyl(f"{prefix}_SideBar_{lx}", (lx, 0.0, 0.16), 0.014, 0.40, M["wood"], rot=(math.radians(90), 0, 0), verts=8))
        # Tall slightly curved backrest posts
        parts.append(add_cyl(f"{prefix}_Post_{lx}", (lx, -0.21, 0.72), 0.021, 0.56, M["wood"], rot=(math.radians(-6), 0, 0), verts=10))
    # Wooden seat frame + slate-grey fabric cushion (like Kane Pixels Backrooms lobby chairs!)
    parts.append(add_box(f"{prefix}_SeatFrame", (0.0, 0.0, 0.46), (0.50, 0.48, 0.05), M["wood"], 0.012))
    parts.append(add_box(f"{prefix}_Cushion", (0.0, 0.01, 0.495), (0.44, 0.42, 0.045), M["steel"], 0.02))
    # 4 horizontal ladder-back slats aligned cleanly along the -6 deg backrest posts!
    for i, sz in enumerate((0.58, 0.70, 0.82, 0.94)):
        sy = -0.21 + (sz - 0.72) * math.tan(math.radians(6))
        parts.append(add_box(f"{prefix}_Slat_{i}", (0.0, sy, sz), (0.42, 0.025, 0.055), M["wood"], 0.008, rot=(math.radians(-6), 0, 0)))
    for p in parts:
        p.parent = pivot
    pivot.rotation_euler = rot
    pivot.location = (ox, oy, oz)
    bpy.context.view_layer.update()
    for p in parts:
        mw = p.matrix_world.copy()
        p.parent = None
        p.matrix_world = mw
    bpy.data.objects.remove(pivot, do_unlink=True)


def build_wood_chair_pile():
    """2 wide x 2 tall x 2 deep towering Backrooms Lobby movie-style stacked wooden chair mountain!"""
    reset_scene()
    M = mats()
    # Ground layer: 4 upright/slightly angled wooden chairs forming the base
    base_poses = [
        (-0.08, -0.05, 0.0, (0.0, 0.0, math.radians(12))),
        (0.76, -0.08, 0.0, (0.0, 0.0, math.radians(-24))),
        (0.18, -0.82, 0.0, (0.0, 0.0, math.radians(155))),
        (0.92, -0.78, 0.0, (0.0, 0.0, math.radians(-108))),
    ]
    # Mid & Upper tangled stacked tower (rising up to ~2.0 blocks tall like the film!)
    stack_poses = [
        (0.28, -0.32, 0.56, (math.radians(24), math.radians(-18), math.radians(38))),
        (0.78, -0.46, 0.64, (math.radians(-32), math.radians(22), math.radians(-62))),
        (0.45, -0.76, 0.78, (math.radians(68), math.radians(14), math.radians(115))),
        (0.42, -0.44, 1.18, (math.radians(-18), math.radians(-28), math.radians(-18))),
        (0.72, -0.58, 1.32, (math.radians(152), math.radians(20), math.radians(74))),
        (0.52, -0.50, 1.62, (math.radians(42), math.radians(-36), math.radians(140))),
    ]
    for i, (x, y, z, r) in enumerate(base_poses + stack_poses):
        add_wooden_chair_parts(f"Ch{i}", M, offset=(x, y, z), rot=r)
    export_prop("prop_wood_chair_pile")


def build_arcade_cabinet():
    """1 wide x 2 tall x 1 deep retro glowing arcade cabinet!"""
    reset_scene()
    M = mats()
    add_box("Lower", (0.0, 0.0, 0.48), (0.82, 0.78, 0.96), M["dark_metal"], 0.02)
    add_box("Upper", (0.0, -0.08, 1.38), (0.82, 0.62, 0.94), M["blue"], 0.02)
    add_box("Marquee", (0.0, 0.22, 1.78), (0.76, 0.14, 0.20), M["emit_main"], 0.01)
    add_box("Screen", (0.0, 0.20, 1.32), (0.64, 0.06, 0.52), M["emit_alt"], 0.01, rot=(math.radians(-16), 0, 0))
    add_box("Deck", (0.0, 0.26, 0.96), (0.80, 0.34, 0.08), M["dark_metal"], 0.015, rot=(math.radians(10), 0, 0))
    add_cyl("Stick", (-0.18, 0.30, 1.05), 0.016, 0.12, M["steel"], verts=10)
    add_cyl("StickBall", (-0.18, 0.30, 1.12), 0.036, 0.05, M["red"], verts=12)
    for bx in (0.08, 0.18, 0.26):
        add_cyl(f"Btn_{bx}", (bx, 0.30, 1.01), 0.028, 0.025, M["emit_hot"], verts=10)
    export_prop("arcade_cabinet")


def build_hospital_gurney():
    """1 wide x 1 tall x 2 deep wheeled stainless steel hospital gurney + IV pole!"""
    reset_scene()
    M = mats()
    cy = -0.5  # 2 deep: Y in [-1.5 .. +0.5]
    add_box("Mattress", (0.0, cy, 0.80), (0.80, 1.84, 0.14), M["enamel"], 0.04)
    add_box("Pillow", (0.0, cy + 0.66, 0.90), (0.58, 0.34, 0.10), M["enamel"], 0.04)
    add_box("BedFrame", (0.0, cy, 0.68), (0.84, 1.88, 0.08), M["steel"], 0.01)
    for lx in (-0.36, 0.36):
        add_cyl(f"Rail_{lx}", (lx, cy, 0.92), 0.018, 1.36, M["steel"], rot=(math.radians(90), 0, 0), verts=10)
        for ly in (cy - 0.72, cy + 0.72):
            add_cyl(f"Leg_{lx}_{ly}", (lx, ly, 0.38), 0.022, 0.60, M["steel"], verts=10)
            add_cyl(f"Wheel_{lx}_{ly}", (lx, ly, 0.08), 0.08, 0.04, M["dark_metal"], rot=(0, math.radians(90), 0), verts=12)
    export_prop("hospital_gurney")


def build_steam_boiler_unit():
    """2 wide x 2 tall x 2 deep heavy riveted cylindrical industrial steam boiler!"""
    reset_scene()
    M = mats()
    cx, cy = 0.5, -0.5
    add_box("Plinth", (cx, cy, 0.14), (1.84, 1.84, 0.28), M["dark_metal"], 0.02)
    add_cyl("MainTank", (cx, cy, 1.06), 0.76, 1.62, M["copper"], rot=(math.radians(90), 0, 0), verts=20, bevel=0.04)
    for dy in (-0.52, 0.0, 0.52):
        add_torus(f"Band_{dy}", (cx, cy + dy, 1.06), 0.77, 0.032, M["dark_metal"], rot=(math.radians(90), 0, 0), maj_seg=22, min_seg=6)
    # Glowing firebox grate on front (+Y)
    add_cyl("FireDoor", (cx, cy + 0.84, 0.96), 0.36, 0.08, M["dark_metal"], rot=(math.radians(90), 0, 0), verts=16)
    add_cyl("FireGlow", (cx, cy + 0.87, 0.96), 0.26, 0.04, M["emit_main"], rot=(math.radians(90), 0, 0), verts=14, bevel=0)
    # Brass gauge + twin vertical exhaust chimneys
    add_cyl("Gauge", (cx + 0.44, cy + 0.84, 1.44), 0.13, 0.06, M["brass"], rot=(math.radians(90), 0, 0), verts=14)
    add_cyl("Chimney1", (cx - 0.32, cy - 0.35, 1.72), 0.18, 0.56, M["steel"], verts=14)
    add_cyl("Chimney2", (cx + 0.34, cy - 0.35, 1.68), 0.14, 0.48, M["steel"], verts=14)
    export_prop("steam_boiler_unit")


def build_standing_mirror():
    """1 wide x 2 tall x 1 deep freestanding bevelled floor mirror with rear easel leg!"""
    reset_scene()
    M = mats()
    add_box("Frame", (0.0, 0.0, 1.02), (0.76, 0.07, 1.88), M["wood"], 0.02, rot=(math.radians(-8), 0, 0))
    add_box("Glass", (0.0, 0.032, 1.02), (0.64, 0.02, 1.76), M["glass"], 0.0, rot=(math.radians(-8), 0, 0))
    add_box("Easel", (0.0, -0.32, 0.72), (0.52, 0.05, 1.48), M["dark_wood"], 0.01, rot=(math.radians(18), 0, 0))
    export_prop("prop_standing_mirror")


def build_floor_lamp():
    """1 wide x 2 tall x 1 deep slender brass floor lamp with warm glowing conical shade!"""
    reset_scene()
    M = mats()
    add_cyl("Base", (0.0, 0.0, 0.04), 0.24, 0.08, M["brass"], verts=16)
    add_cyl("Pole", (0.0, 0.0, 0.86), 0.026, 1.56, M["brass"], verts=12)
    add_cone("Shade", (0.0, 0.0, 1.66), 0.30, 0.17, 0.44, M["enamel"], verts=18)
    add_cyl("BulbGlow", (0.0, 0.0, 1.60), 0.11, 0.20, M["emit_main"], verts=12, bevel=0)
    export_prop("prop_floor_lamp")


def build_glass_partition(broken=False):
    """2 wide x 2 tall x 1 deep slim aluminum cubicle glass partition (intact or jagged broken)!"""
    reset_scene()
    M = mats()
    cx = 0.5
    add_box("BotRail", (cx, 0.0, 0.14), (1.96, 0.10, 0.28), M["steel"], 0.01)
    add_box("TopRail", (cx, 0.0, 1.94), (1.96, 0.08, 0.10), M["steel"], 0.01)
    for px in (-0.44, 0.50, 1.44):
        add_box(f"Post_{px}", (px, 0.0, 1.00), (0.08, 0.10, 1.96), M["steel"], 0.01)
    if not broken:
        add_box("PaneL", (0.03, 0.0, 1.08), (0.86, 0.03, 1.62), M["glass"], 0.0)
        add_box("PaneR", (0.97, 0.0, 1.08), (0.86, 0.03, 1.62), M["glass"], 0.0)
        export_prop("prop_glass_partition")
    else:
        # Jagged remaining glass shards in frame + shattered pieces on floor
        add_box("Shard1", (-0.16, 0.0, 0.56), (0.44, 0.03, 0.62), M["glass"], 0.0, rot=(0, math.radians(16), 0))
        add_box("Shard2", (1.22, 0.0, 1.54), (0.38, 0.03, 0.56), M["glass"], 0.0, rot=(0, math.radians(-22), 0))
        add_box("FloorS1", (0.42, 0.18, 0.02), (0.28, 0.22, 0.02), M["glass"], 0.0, rot=(0, 0, 0.6))
        add_box("FloorS2", (0.88, -0.14, 0.02), (0.22, 0.16, 0.02), M["glass"], 0.0, rot=(0, 0, -0.4))
        export_prop("prop_glass_partition_broken")


def build_cardboard_pile():
    """2 wide x 2 tall x 2 deep tumbled cardboard box mountain!"""
    reset_scene()
    M = mats()
    boxes = [
        (0.05, -0.05, 0.36, (0.84, 0.78, 0.72), (0, 0, 0.10)),
        (0.95, -0.12, 0.32, (0.82, 0.74, 0.64), (0, 0, -0.14)),
        (0.18, -0.96, 0.38, (0.88, 0.76, 0.76), (0, 0, 0.06)),
        (0.98, -0.92, 0.30, (0.76, 0.76, 0.60), (0, 0, 0.22)),
        (0.42, -0.42, 0.98, (0.76, 0.70, 0.58), (0.04, -0.05, 0.28)),
        (0.88, -0.68, 0.90, (0.66, 0.62, 0.54), (-0.06, 0.08, -0.32)),
        (0.54, -0.52, 1.54, (0.58, 0.54, 0.50), (0.08, 0.06, -0.18)),
    ]
    for i, (x, y, z, sc, rt) in enumerate(boxes):
        add_box(f"Box_{i}", (x, y, z), sc, M["cardboard"], 0.015, rot=rt)
        add_box(f"Tape_{i}", (x, y, z + sc[2] * 0.5 + 0.004), (sc[0] * 0.96, 0.08, 0.008), M["beige"], 0.0, rot=rt)
    export_prop("prop_cardboard_pile")


def build_folding_chair_pile():
    """2 wide x 1 tall x 2 deep tangled heap of overturned metal folding chairs!"""
    reset_scene()
    M = mats()
    poses = [
        (0.10, -0.10, 0.24, (math.radians(72), math.radians(15), math.radians(20))),
        (0.92, -0.25, 0.30, (math.radians(-64), math.radians(28), math.radians(-40))),
        (0.32, -0.96, 0.26, (math.radians(58), math.radians(-30), math.radians(110))),
        (0.95, -0.88, 0.44, (math.radians(-42), math.radians(-18), math.radians(65))),
    ]
    for i, (x, y, z, r) in enumerate(poses):
        add_box(f"Seat_{i}", (x, y, z), (0.54, 0.50, 0.05), M["steel"], 0.01, rot=r)
        add_box(f"Back_{i}", (x + 0.14, y - 0.14, z + 0.22), (0.52, 0.05, 0.42), M["dark_metal"], 0.01, rot=r)
        add_cyl(f"LegA_{i}", (x - 0.18, y + 0.10, z - 0.08), 0.016, 0.62, M["steel"], rot=r, verts=8)
        add_cyl(f"LegB_{i}", (x + 0.18, y + 0.10, z - 0.08), 0.016, 0.62, M["steel"], rot=r, verts=8)
    export_prop("prop_folding_chair_pile")


def build_simple_multi_props():
    # 1. neon_sign_prop (2x1x1)
    reset_scene()
    M = mats()
    cx = 0.5
    add_box("Backplate", (cx, -0.22, 0.52), (1.84, 0.08, 0.64), M["dark_metal"], 0.02)
    add_box("BorderT", (cx, -0.15, 0.78), (1.72, 0.04, 0.05), M["emit_main"], 0.01)
    add_box("BorderB", (cx, -0.15, 0.26), (1.72, 0.04, 0.05), M["emit_main"], 0.01)
    for ix, tx in enumerate((-0.14, 0.28, 0.72, 1.14)):
        add_cyl(f"Tube_{ix}", (tx, -0.14, 0.52), 0.025, 0.36, M["emit_alt"] if ix % 2 == 0 else M["emit_main"], rot=(0, math.radians(15 if ix % 2 else -15), 0), verts=10)
    export_prop("neon_sign_prop")

    # 2. boarded_barricade (2x2x1)
    reset_scene()
    M = mats()
    cx = 0.5
    for px in (-0.34, 1.34):
        add_box(f"Post_{px}", (px, 0.0, 0.96), (0.14, 0.14, 1.92), M["dark_wood"], 0.01)
    for i, (z, ang) in enumerate(((0.32, 4), (0.76, -6), (1.20, 5), (1.62, -4))):
        add_box(f"Plank_{i}", (cx, 0.09, z), (1.92, 0.07, 0.24), M["wood"], 0.01, rot=(0, math.radians(ang), 0))
    export_prop("boarded_barricade")

    # 3. barbed_wire_fence (2x1x1)
    reset_scene()
    M = mats()
    for px in (-0.38, 0.50, 1.38):
        add_cyl(f"Stake_{px}", (px, 0.0, 0.46), 0.028, 0.92, M["dark_metal"], verts=8)
    for i in range(6):
        add_torus(f"Coil_{i}", (-0.32 + i * 0.33, 0.0, 0.48), 0.26, 0.014, M["steel"], rot=(0, math.radians(78), 0), maj_seg=18, min_seg=6)
    export_prop("barbed_wire_fence")

    # 4. reinforced_steel_bulkhead (2x2x1)
    reset_scene()
    M = mats()
    cx = 0.5
    add_box("Wall", (cx, 0.0, 1.0), (1.96, 0.36, 1.96), M["dark_metal"], 0.02)
    add_box("DoorLeaf", (cx, 0.06, 0.96), (1.24, 0.32, 1.72), M["steel"], 0.02)
    add_torus("Wheel", (cx, 0.26, 1.02), 0.24, 0.026, M["red"], rot=(math.radians(90), 0, 0), maj_seg=18, min_seg=6)
    export_prop("reinforced_steel_bulkhead")

    # 5. anomaly_archive_board (2x2x1)
    reset_scene()
    M = mats()
    cx = 0.5
    add_box("Frame", (cx, -0.10, 1.06), (1.88, 0.10, 1.48), M["dark_wood"], 0.02)
    add_box("Cork", (cx, -0.05, 1.06), (1.72, 0.05, 1.32), M["cardboard"], 0.0)
    for lx in (-0.30, 1.30):
        add_cyl(f"Leg_{lx}", (lx, -0.10, 0.40), 0.04, 0.80, M["dark_wood"], verts=10)
    for i, (px, pz, col) in enumerate(((-0.08, 1.36, M["enamel"]), (0.48, 1.40, M["beige"]), (1.02, 1.24, M["enamel"]), (0.20, 0.84, M["enamel"]), (0.86, 0.78, M["emit_alt"]))):
        add_box(f"Note_{i}", (px, -0.01, pz), (0.36, 0.015, 0.28), col, 0.0, rot=(0, math.radians((i - 2) * 5), 0))
    export_prop("anomaly_archive_board")

    # 6. prop_curtain_heap (2x1x1)
    reset_scene()
    M = mats()
    for i, (x, y, z, rx, ry, rz) in enumerate(((0.0, 0.0, 0.14, 0.85, 0.65, 0.26), (0.64, -0.08, 0.18, 0.95, 0.70, 0.32), (1.10, 0.06, 0.12, 0.75, 0.60, 0.22))):
        add_box(f"Drapes_{i}", (x, y, z), (rx, ry, rz), M["fabric_mold"], 0.08, rot=(0.06, -0.08, i * 0.4))
    export_prop("prop_curtain_heap")


# =============================================================================
# SINGLE-BLOCK SCULPTED & HALF-SLICED NOCLIPPED PROPS
# =============================================================================
def build_single_props():
    # 1. Desktop computers (intact, damaged, half-sliced noclipped!)
    for mode in ("intact", "damaged", "noclipped"):
        reset_scene()
        M = mats()
        # Slim curved monitor + stand + slim keyboard + tower case!
        add_box("StandBase", (-0.06, -0.06, 0.03), (0.28, 0.22, 0.03), M["beige"], 0.008)
        add_cyl("StandNeck", (-0.06, -0.10, 0.14), 0.04, 0.20, M["beige"], verts=10)
        add_box("MonitorShell", (-0.06, -0.14, 0.42), (0.58, 0.34, 0.44), M["beige"], 0.03)
        scr_mat = M["emit_main"] if mode != "damaged" else M["dark_metal"]
        add_box("Screen", (-0.06, 0.035, 0.43), (0.48, 0.02, 0.34), scr_mat, 0.005)
        add_box("Tower", (0.34, -0.08, 0.26), (0.20, 0.46, 0.50), M["beige"], 0.015)
        add_box("PowerLED", (0.34, 0.155, 0.40), (0.04, 0.01, 0.02), M["emit_alt"], 0.0)
        add_box("Keyboard", (-0.06, 0.26, 0.03), (0.52, 0.20, 0.035), M["beige"], 0.008, rot=(math.radians(6), 0, 0))
        if mode == "damaged":
            bake_and_slice_noclip(tilt_euler=(math.radians(14), math.radians(-12), math.radians(18)), shift=(0, 0, 0.04))
            export_prop("prop_desktop_computer_damaged")
        elif mode == "noclipped":
            bake_and_slice_noclip(tilt_euler=(math.radians(24), math.radians(-32), math.radians(-16)), shift=(0.04, 0, -0.18))
            export_prop("prop_desktop_computer_noclipped")
        else:
            export_prop("prop_desktop_computer")

    # 2. Retro office phones (intact, cracked, half-sliced noclipped!)
    for mode in ("intact", "cracked", "noclipped"):
        reset_scene()
        M = mats()
        add_box("PhoneBase", (0.0, 0.0, 0.07), (0.36, 0.38, 0.12), M["dark_metal"], 0.02, rot=(math.radians(12), 0, 0))
        add_box("Keypad", (0.0, 0.06, 0.13), (0.20, 0.18, 0.02), M["emit_main"] if mode != "cracked" else M["beige"], 0.004, rot=(math.radians(12), 0, 0))
        add_cyl("Handset", (0.0, -0.10, 0.18), 0.036, 0.44, M["dark_metal"], rot=(0, math.radians(90), math.radians(14 if mode != "intact" else 0)), verts=12)
        if mode == "noclipped":
            bake_and_slice_noclip(tilt_euler=(math.radians(32), math.radians(28), 0), shift=(0, 0, -0.07))
            export_prop("prop_phone_noclipped")
        elif mode == "cracked":
            export_prop("prop_phone_cracked")
        else:
            export_prop("prop_phone")

    # 3. Office chair (intact & half-sliced noclipped!) + wooden chair (noclipped!)
    for name, is_nc in (("prop_office_chair", False), ("noclipped_office_chair", True)):
        reset_scene()
        M = mats()
        add_cyl("Piston", (0.0, 0.0, 0.24), 0.035, 0.40, M["steel"], verts=12)
        for i in range(5):
            a = i * math.tau / 5
            add_box(f"StarLeg_{i}", (math.cos(a) * 0.17, math.sin(a) * 0.17, 0.06), (0.34, 0.04, 0.04), M["dark_metal"], 0.008, rot=(0, 0, a))
            add_cyl(f"Caster_{i}", (math.cos(a) * 0.32, math.sin(a) * 0.32, 0.03), 0.03, 0.03, M["dark_metal"], rot=(math.radians(90), 0, a), verts=8)
        add_box("Seat", (0.0, 0.0, 0.48), (0.56, 0.54, 0.10), M["leather"], 0.03)
        add_box("Back", (0.0, -0.26, 0.78), (0.52, 0.10, 0.52), M["leather"], 0.03, rot=(math.radians(-10), 0, 0))
        if is_nc:
            bake_and_slice_noclip(tilt_euler=(math.radians(-26), math.radians(32), math.radians(14)), shift=(0, 0, -0.28))
        export_prop(name)

    # 3B. Wooden Lobby Chair (both movie-accurate intact & half-sliced noclipped!)
    for name, is_nc in (("prop_wood_chair", False), ("noclipped_wood_chair", True)):
        reset_scene()
        M = mats()
        add_wooden_chair_parts("Single", M)
        if is_nc:
            bake_and_slice_noclip(tilt_euler=(math.radians(28), math.radians(-35), math.radians(22)), shift=(0, 0, -0.26))
        export_prop(name)

    # 4. CRT TV (both intact & half-sliced noclipped!)
    for name, is_nc in (("prop_crt_tv", False), ("noclipped_crt_tv", True)):
        reset_scene()
        M = mats()
        add_box("FrontBox", (0.0, 0.08, 0.36), (0.74, 0.40, 0.60), M["dark_metal"], 0.03)
        add_box("RearTube", (0.0, -0.22, 0.36), (0.48, 0.28, 0.42), M["dark_metal"], 0.03)
        add_box("Screen", (-0.06, 0.29, 0.37), (0.54, 0.03, 0.44), M["emit_main"], 0.01)
        add_cyl("AntL", (-0.14, 0.0, 0.80), 0.008, 0.36, M["steel"], rot=(0, math.radians(-26), 0), verts=8)
        add_cyl("AntR", (0.14, 0.0, 0.80), 0.008, 0.36, M["steel"], rot=(0, math.radians(26), 0), verts=8)
        if is_nc:
            bake_and_slice_noclip(tilt_euler=(math.radians(-18), math.radians(28), math.radians(-10)), shift=(0, 0, -0.18))
        export_prop(name)

    # 5. Electric fan (circular wire cage + angled fan blades + slim pedestal!)
    reset_scene()
    M = mats()
    add_cyl("Base", (0.0, 0.0, 0.04), 0.20, 0.07, M["beige"], verts=16)
    add_cyl("Neck", (0.0, -0.04, 0.26), 0.032, 0.40, M["steel"], verts=10)
    add_cyl("Motor", (0.0, -0.08, 0.56), 0.09, 0.20, M["beige"], rot=(math.radians(90), 0, 0), verts=14)
    add_torus("CageOuter", (0.0, 0.06, 0.56), 0.30, 0.016, M["steel"], rot=(math.radians(90), 0, 0), maj_seg=22, min_seg=6)
    add_torus("CageInner", (0.0, 0.08, 0.56), 0.16, 0.010, M["steel"], rot=(math.radians(90), 0, 0), maj_seg=18, min_seg=6)
    for i in range(4):
        a = i * math.tau / 4
        add_box(f"Blade_{i}", (math.cos(a) * 0.14, 0.05, 0.56 + math.sin(a) * 0.14), (0.22, 0.015, 0.09), M["blue"], 0.005, rot=(math.radians(22), a, 0))
    export_prop("prop_electric_fan")

    # 6. Microwave
    reset_scene()
    M = mats()
    add_box("Body", (0.0, 0.0, 0.22), (0.76, 0.50, 0.42), M["enamel"], 0.025)
    add_box("Window", (-0.10, 0.255, 0.22), (0.44, 0.02, 0.28), M["dark_metal"], 0.008)
    add_box("InnerGlow", (-0.10, 0.258, 0.22), (0.36, 0.01, 0.22), M["emit_main"], 0.0)
    add_box("Clock", (0.24, 0.255, 0.30), (0.14, 0.01, 0.07), M["emit_alt"], 0.0)
    export_prop("prop_microwave")

    # 7. Plastic potted plant (tapered terracotta pot + radiating curved leaves!)
    reset_scene()
    M = mats()
    add_cone("Pot", (0.0, 0.0, 0.18), 0.14, 0.20, 0.36, M["copper"], verts=14)
    add_cyl("Soil", (0.0, 0.0, 0.34), 0.17, 0.04, M["dark_wood"], verts=12)
    for i in range(7):
        a = i * math.tau / 7
        add_box(f"Leaf_{i}", (math.cos(a) * 0.14, math.sin(a) * 0.14, 0.54 + (i % 2) * 0.08),
                (0.32, 0.10, 0.02), M["green"], 0.005, rot=(math.radians(32), math.radians(-38), a))
    export_prop("prop_potted_plant_plastic")

    # 8. Wall first-aid kit + industrial fuse box
    reset_scene()
    M = mats()
    add_box("Case", (0.0, -0.34, 0.50), (0.56, 0.22, 0.46), M["enamel"], 0.025)
    add_box("CrossV", (0.0, -0.22, 0.50), (0.09, 0.02, 0.28), M["red"], 0.005)
    add_box("CrossH", (0.0, -0.22, 0.50), (0.28, 0.02, 0.09), M["red"], 0.005)
    export_prop("prop_wall_first_aid_kit")

    reset_scene()
    M = mats()
    add_box("Box", (0.0, -0.30, 0.50), (0.64, 0.28, 0.76), M["steel"], 0.02)
    add_cyl("Lever", (0.36, -0.26, 0.56), 0.025, 0.28, M["red"], rot=(math.radians(35), 0, 0), verts=10)
    add_box("WarnLED", (0.0, -0.15, 0.72), (0.14, 0.02, 0.06), M["emit_alt"], 0.0)
    export_prop("industrial_fuse_box")

    # 9. Vintage chandelier
    reset_scene()
    M = mats()
    add_cyl("Chain", (0.0, 0.0, 0.80), 0.025, 0.40, M["brass"], verts=10)
    add_torus("Ring", (0.0, 0.0, 0.42), 0.36, 0.024, M["brass"], maj_seg=20, min_seg=6)
    for i in range(6):
        a = i * math.tau / 6
        add_cyl(f"Candle_{i}", (math.cos(a) * 0.36, math.sin(a) * 0.36, 0.52), 0.028, 0.14, M["enamel"], verts=10)
        add_crystal(f"Flame_{i}", (math.cos(a) * 0.36, math.sin(a) * 0.36, 0.64), 0.024, 0.024, 0.055, M["emit_main"])
    export_prop("vintage_chandelier")

    # 10. Red anomaly cluster + crystal vein node + wanderer remains
    for name, c_mat in (("red_anomaly_cluster", "emit_main"), ("crystal_vein_node", "emit_main")):
        reset_scene()
        M = mats()
        add_box("RockBase", (0.0, 0.0, 0.12), (0.72, 0.70, 0.24), M["dark_metal"], 0.05)
        for i, (x, y, rx, rz, rt) in enumerate(((0, 0, 0.15, 0.44, (0, 0, 0)), (0.20, 0.12, 0.10, 0.32, (0.28, 0.22, 0.6)), (-0.22, 0.10, 0.11, 0.34, (0.22, -0.30, 1.8)), (0.06, -0.22, 0.09, 0.28, (-0.34, 0.12, 2.5)))):
            add_crystal(f"Crys_{i}", (x, y, 0.18 + rz * 0.55), rx, rx, rz, M[c_mat], rot=rt)
        export_prop(name)

    reset_scene()
    M = mats()
    add_box("Pack", (-0.18, -0.12, 0.16), (0.34, 0.26, 0.28), M["fabric"], 0.04, rot=(0.1, 0.2, 0.5))
    add_cyl("Skull", (0.16, 0.10, 0.12), 0.12, 0.18, M["bone"], rot=(0.4, 0.3, -0.5), verts=12)
    add_cyl("Rib1", (0.0, 0.16, 0.04), 0.025, 0.36, M["bone"], rot=(0, math.radians(82), 0.4), verts=8)
    add_cyl("Rib2", (0.18, -0.14, 0.04), 0.025, 0.38, M["bone"], rot=(0, math.radians(85), -0.6), verts=8)
    export_prop("wanderer_remains")


def main():
    build_fridge(noclipped=True)
    build_fridge(noclipped=False)
    build_vending_machine(noclipped=True)
    build_vending_machine(noclipped=False)
    build_water_cooler(noclipped=True)
    build_water_cooler(noclipped=False)
    build_file_cabinet(noclipped=True)
    build_file_cabinet(noclipped=False)
    build_sofa(noclipped=False)
    build_sofa(noclipped=True)
    build_folding_table(noclipped=True)
    build_folding_table(noclipped=False)
    build_wood_chair_pile()
    build_arcade_cabinet()
    build_hospital_gurney()
    build_steam_boiler_unit()
    build_standing_mirror()
    build_floor_lamp()
    build_glass_partition(broken=False)
    build_glass_partition(broken=True)
    build_cardboard_pile()
    build_folding_chair_pile()
    build_simple_multi_props()
    build_single_props()
    print("ALL 47 BLENDER INTACT, MULTI-BLOCK & HALF-SLICED NOCLIPPED PROPS EXPORTED!")


if __name__ == "__main__":
    main()
