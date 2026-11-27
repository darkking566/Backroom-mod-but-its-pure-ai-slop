"""Generates Entity 2 ("Window" / window_entity) 512x512 weathered atlas, emissive eyes,
Route A BoxRig spec (26 articulated joints, ~118 sculpted boxes, 4 smooth 24fps clips),
and 16x16 spawn egg assets.

Run: python tools/make_window_entity.py
"""
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
ART = ROOT / "art/monsters/window_entity"
SPEC_OUT = ASSETS / "monsters/window_entity.json"
ART_SPEC_OUT = ART / "window_entity_minecraft_spec.json"
TEX_OUT = ASSETS / "textures/entity/window_entity.png"
EYES_OUT = ASSETS / "textures/entity/window_entity_eyes.png"

ATLAS_W = 512
ATLAS_H = 512
REGIONS = {
    "void_skin": (8, 8, 152, 152),
    "ashen_rib": (180, 8, 152, 152),
    "damp_shroud": (352, 8, 152, 152),
    "pale_palm": (8, 180, 152, 152),
    "hollow_face": (180, 180, 152, 152),
    "eye": (352, 180, 152, 152),
    "inner_maw": (8, 352, 152, 152),
}


def noise_tile(rng, base_rgb, delta_rgb, streak_y=False, veins=False):
    h, w = 152, 152
    n_fine = rng.normal(0.0, 1.0, (h, w))
    yy, xx = np.mgrid[0:h, 0:w]
    macro = (
        np.sin(xx * 0.08 + rng.uniform(0, 6.28)) * 0.45
        + np.cos(yy * 0.09 + rng.uniform(0, 6.28)) * 0.45
        + np.sin((xx + yy) * 0.05) * 0.3
    )
    if streak_y:
        col_noise = rng.normal(0.0, 0.9, (1, w))
        macro = macro * 0.4 + col_noise + np.sin(xx * 0.25) * 0.25
    if veins:
        v = np.exp(-((np.sin(xx * 0.14 + np.sin(yy * 0.08) * 1.8)) ** 2) * 18.0)
        macro += v * 0.7
    t = np.clip(0.5 + macro * 0.22 + n_fine * 0.14, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    for c in range(3):
        rgb[..., c] = base_rgb[c] + (delta_rgb[c] - base_rgb[c]) * t
    return np.clip(np.rint(rgb), 0, 255).astype("uint8")


def build_textures():
    rng = np.random.default_rng(20261127)
    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (18, 20, 24, 255))

    tiles = {
        "void_skin": noise_tile(rng, (20, 23, 28), (42, 47, 55), streak_y=True, veins=True),
        "ashen_rib": noise_tile(rng, (58, 64, 72), (104, 112, 122), streak_y=False, veins=True),
        "damp_shroud": noise_tile(rng, (22, 27, 34), (48, 56, 66), streak_y=True, veins=False),
        "pale_palm": noise_tile(rng, (124, 134, 144), (178, 188, 196), streak_y=False, veins=True),
        "hollow_face": noise_tile(rng, (24, 27, 33), (52, 57, 65), streak_y=True, veins=False),
        "eye": noise_tile(rng, (214, 236, 246), (245, 252, 255), streak_y=False, veins=False),
        "inner_maw": noise_tile(rng, (36, 16, 20), (68, 28, 34), streak_y=True, veins=True),
    }

    for name, (u, v, w, h) in REGIONS.items():
        tile_img = Image.fromarray(tiles[name], "RGB").convert("RGBA")
        # Pad 8px border around each 152x152 block to prevent mipmap bleed
        atlas.paste(tile_img, (u, v))

    TEX_OUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(TEX_OUT, optimize=True)
    ART.mkdir(parents=True, exist_ok=True)
    atlas.save(ART / "window_entity.png", optimize=True)

    # Emissive eyes layer (only the eye atlas cell is opaque)
    eyes = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    eu, ev, ew, eh = REGIONS["eye"]
    eyes.paste(Image.fromarray(tiles["eye"], "RGB").convert("RGBA"), (eu, ev))
    eyes.save(EYES_OUT, optimize=True)


def cube(origin, size, region="void_skin", rot=None, offset=None):
    u, v, _, _ = REGIONS[region]
    c = {
        "origin_mc": [round(float(x), 3) for x in origin],
        "size_mc": [round(float(x), 3) for x in size],
        "region": region,
        "uv_region": [u + 8, v + 8],
    }
    if rot is not None:
        c["needs_subpart"] = True
        c["subpart_offset_mc"] = [round(float(x), 3) for x in (offset or (0.0, 0.0, 0.0))]
        c["subpart_rot_rad_mc"] = [round(float(x), 4) for x in rot]
    return c


def part(name, parent, pivot, cubes, rest=(0.0, 0.0, 0.0)):
    return {
        "name": name,
        "parent": parent,
        "pivot_mc": [round(float(x), 3) for x in pivot],
        "rest_rot_rad_mc": [round(float(x), 4) for x in rest],
        "cubes": cubes,
    }


def build_parts():
    parts = []

    # 1. Pelvis & gaunt hanging lower limbs (feet reach ~y=24)
    parts.append(part("Pelvis", "root", (0.0, 5.5, 0.0), [
        cube((-2.8, -1.6, -1.7), (5.6, 3.2, 3.4), "void_skin"),
        cube((-3.1, -0.8, -1.3), (6.2, 1.8, 2.6), "ashen_rib"),
        cube((-2.3, 1.2, -1.2), (4.6, 1.4, 2.4), "damp_shroud"),
        cube((-0.7, -1.8, 1.2), (1.4, 3.0, 1.0), "ashen_rib"),
    ]))

    for side, sx in (("L", -1.0), ("R", 1.0)):
        parts.append(part(f"Thigh_{side}", "Pelvis", (sx * 2.1, 0.8, 0.0), [
            cube((-1.15, 0.0, -1.15), (2.3, 8.8, 2.3), "void_skin"),
            cube((-0.75, 1.0, -1.35), (1.5, 6.8, 0.6), "ashen_rib"),
            cube((-1.3, 0.4, -0.8), (0.6, 5.6, 1.8), "damp_shroud"),
        ], rest=(0.08, 0.0, sx * 0.05)))

        parts.append(part(f"Shin_{side}", f"Thigh_{side}", (0.0, 8.6, 0.2), [
            cube((-0.9, 0.0, -0.9), (1.8, 8.2, 1.8), "void_skin"),
            cube((-0.55, 0.6, -1.1), (1.1, 6.8, 0.5), "ashen_rib"),
            cube((-1.05, 1.5, 0.6), (2.1, 5.4, 0.7), "damp_shroud"),
        ], rest=(-0.12, 0.0, 0.0)))

        parts.append(part(f"Foot_{side}", f"Shin_{side}", (0.0, 8.0, -0.2), [
            cube((-0.95, 0.0, -2.5), (1.9, 1.2, 3.4), "void_skin"),
            cube((-0.85, 0.2, -3.2), (0.45, 0.8, 1.1), "pale_palm"),
            cube((-0.2, 0.2, -3.4), (0.45, 0.8, 1.3), "pale_palm"),
            cube((0.45, 0.2, -3.1), (0.45, 0.8, 1.0), "pale_palm"),
        ], rest=(0.04, 0.0, 0.0)))

    # 2. Hunched skeletal torso & protruding spine/ribs
    parts.append(part("LowerSpine", "Pelvis", (0.0, -1.4, 0.0), [
        cube((-2.2, -6.8, -1.4), (4.4, 7.0, 2.8), "void_skin"),
        cube((-0.65, -6.5, 1.0), (1.3, 6.4, 0.9), "ashen_rib"),
        cube((-2.5, -5.2, -1.1), (5.0, 1.1, 2.2), "ashen_rib"),
        cube((-2.3, -2.8, -1.0), (4.6, 1.0, 2.0), "ashen_rib"),
        cube((-2.6, -4.4, 0.3), (0.8, 4.4, 1.4), "damp_shroud"),
        cube((1.8, -4.4, 0.3), (0.8, 4.4, 1.4), "damp_shroud"),
    ], rest=(0.14, 0.0, 0.0)))

    torso_cubes = [
        cube((-3.5, -7.8, -2.1), (7.0, 8.0, 4.2), "void_skin"),
        # Sunken hollow chest cavity
        cube((-2.4, -6.6, -2.35), (4.8, 5.4, 0.7), "hollow_face"),
        # Four layered rib-cage arches + sternum + collarbones
        cube((-3.7, -7.4, -2.2), (7.4, 1.1, 1.1), "ashen_rib", rot=(0.0, 0.0, 0.0)),
        cube((-3.8, -5.8, -2.25), (7.6, 0.9, 4.3), "ashen_rib"),
        cube((-3.6, -4.1, -2.15), (7.2, 0.85, 4.1), "ashen_rib"),
        cube((-3.2, -2.5, -1.95), (6.4, 0.8, 3.8), "ashen_rib"),
        cube((-0.6, -7.2, -2.5), (1.2, 6.0, 0.7), "ashen_rib"),
        # Dorsal vertebrae spikes
        cube((-0.7, -7.4, 1.7), (1.4, 1.4, 1.3), "ashen_rib", rot=(0.25, 0.0, 0.0)),
        cube((-0.65, -5.2, 1.8), (1.3, 1.3, 1.2), "ashen_rib", rot=(0.22, 0.0, 0.0)),
        cube((-0.6, -3.0, 1.7), (1.2, 1.2, 1.1), "ashen_rib", rot=(0.18, 0.0, 0.0)),
        # Hunched shoulder blades & tattered shroud ridges
        cube((-4.1, -8.2, -1.6), (2.2, 3.0, 3.4), "damp_shroud", rot=(0.0, 0.0, -0.18)),
        cube((1.9, -8.2, -1.6), (2.2, 3.0, 3.4), "damp_shroud", rot=(0.0, 0.0, 0.18)),
    ]
    parts.append(part("UpperTorso", "LowerSpine", (0.0, -6.6, -0.3), torso_cubes, rest=(0.18, 0.0, 0.0)))

    # Trailing shroud wisps from back/shoulders
    parts.append(part("Tendril_L", "UpperTorso", (-3.4, -6.2, 1.8), [
        cube((-0.7, 0.0, -0.4), (1.4, 8.5, 0.8), "damp_shroud"),
        cube((-0.45, 7.8, -0.3), (0.9, 5.5, 0.6), "void_skin"),
    ], rest=(0.22, 0.0, -0.14)))
    parts.append(part("Tendril_R", "UpperTorso", (3.4, -6.2, 1.8), [
        cube((-0.7, 0.0, -0.4), (1.4, 8.5, 0.8), "damp_shroud"),
        cube((-0.45, 7.8, -0.3), (0.9, 5.5, 0.6), "void_skin"),
    ], rest=(0.22, 0.0, 0.14)))
    parts.append(part("Tendril_Back", "UpperTorso", (0.0, -5.5, 2.2), [
        cube((-1.2, 0.0, -0.4), (2.4, 10.5, 0.8), "damp_shroud"),
        cube((-0.7, 9.5, -0.3), (1.4, 6.0, 0.6), "void_skin"),
    ], rest=(0.28, 0.0, 0.0)))

    # 3. Crane-like hunched neck + gaunt skull + hinged jaw + pinprick glowing eyes
    parts.append(part("NeckLower", "UpperTorso", (0.0, -7.8, -0.7), [
        cube((-1.3, -3.5, -1.3), (2.6, 3.8, 2.6), "void_skin"),
        cube((-0.55, -3.3, 0.9), (1.1, 3.3, 0.8), "ashen_rib"),
    ], rest=(0.24, 0.0, 0.0)))

    parts.append(part("NeckUpper", "NeckLower", (0.0, -3.2, -0.4), [
        cube((-1.15, -3.0, -1.15), (2.3, 3.2, 2.3), "void_skin"),
        cube((-0.5, -2.8, 0.8), (1.0, 2.8, 0.7), "ashen_rib"),
    ], rest=(-0.18, 0.0, 0.0)))

    head_cubes = [
        # Elongated gaunt cranium & sunken hollow face
        cube((-2.4, -5.8, -2.6), (4.8, 4.8, 5.2), "void_skin"),
        cube((-2.1, -5.2, -2.9), (4.2, 3.8, 0.7), "hollow_face"),
        # Sharp cheekbones & brow ridge
        cube((-2.5, -4.8, -3.15), (5.0, 0.9, 1.0), "ashen_rib"),
        cube((-2.3, -3.0, -3.05), (1.3, 1.1, 0.8), "ashen_rib"),
        cube((1.0, -3.0, -3.05), (1.3, 1.1, 0.8), "ashen_rib"),
        # Deep eye sockets + tiny glowing pinprick pupils
        cube((-1.8, -4.1, -3.0), (1.2, 1.1, 0.4), "void_skin"),
        cube((0.6, -4.1, -3.0), (1.2, 1.1, 0.4), "void_skin"),
        cube((-1.45, -3.8, -3.12), (0.55, 0.55, 0.25), "eye"),
        cube((0.90, -3.8, -3.12), (0.55, 0.55, 0.25), "eye"),
        # Ragged wet wisps hanging along temples & crown
        cube((-2.7, -5.6, -1.2), (0.6, 5.2, 2.8), "damp_shroud", rot=(0.0, 0.0, -0.08)),
        cube((2.1, -5.6, -1.2), (0.6, 5.2, 2.8), "damp_shroud", rot=(0.0, 0.0, 0.08)),
        cube((-2.0, -6.2, -1.8), (4.0, 0.7, 4.2), "damp_shroud"),
    ]
    parts.append(part("Head", "NeckUpper", (0.0, -2.6, -0.2), head_cubes, rest=(-0.18, 0.0, 0.0)))

    parts.append(part("Jaw", "Head", (0.0, -1.3, -0.5), [
        cube((-1.8, 0.0, -2.4), (3.6, 1.7, 2.8), "hollow_face"),
        cube((-1.5, -0.15, -2.1), (3.0, 0.7, 2.0), "inner_maw"),
        cube((-1.6, 1.1, -2.5), (3.2, 0.7, 2.2), "ashen_rib"),
        # Sparse pale teeth tips
        cube((-1.2, -0.35, -2.45), (0.4, 0.5, 0.3), "pale_palm"),
        cube((-0.2, -0.35, -2.45), (0.4, 0.5, 0.3), "pale_palm"),
        cube((0.8, -0.35, -2.45), (0.4, 0.5, 0.3), "pale_palm"),
    ], rest=(0.18, 0.0, 0.0)))

    # 4. Long skeletal arms & articulated splayed beckoning hands/fingers
    for side, sx in (("L", -1.0), ("R", 1.0)):
        parts.append(part(f"Shoulder_{side}", "UpperTorso", (sx * 4.0, -7.2, -0.2), [
            cube((-1.4, -1.3, -1.4), (2.8, 2.6, 2.8), "void_skin"),
            cube((-1.5, -1.5, -1.1), (3.0, 1.2, 2.2), "ashen_rib"),
        ]))

        parts.append(part(f"UpperArm_{side}", f"Shoulder_{side}", (sx * 0.6, 0.6, 0.0), [
            cube((-0.95, 0.0, -0.95), (1.9, 10.8, 1.9), "void_skin"),
            cube((-0.6, 1.2, -1.15), (1.2, 8.6, 0.5), "ashen_rib"),
            cube((-1.1, 2.0, 0.5), (2.2, 6.2, 0.7), "damp_shroud"),
        ], rest=(0.10, 0.0, sx * 0.14)))

        parts.append(part(f"Forearm_{side}", f"UpperArm_{side}", (0.0, 10.6, 0.0), [
            cube((-0.85, 0.0, -0.85), (1.7, 11.4, 1.7), "void_skin"),
            cube((-0.55, 0.8, -1.05), (1.1, 9.8, 0.5), "ashen_rib"),
            cube((-0.7, 7.6, -0.9), (1.4, 3.6, 1.8), "pale_palm"),
        ], rest=(-0.28, 0.0, 0.0)))

        parts.append(part(f"Hand_{side}", f"Forearm_{side}", (0.0, 11.2, 0.0), [
            cube((-1.3, 0.0, -0.8), (2.6, 3.4, 1.6), "pale_palm"),
            cube((-1.4, -0.1, -0.95), (2.8, 1.1, 1.9), "ashen_rib"),
        ]))

        parts.append(part(f"Thumb_{side}", f"Hand_{side}", (-sx * 1.2, 1.2, -0.3), [
            cube((-0.4, 0.0, -0.4), (0.8, 3.4, 0.8), "pale_palm"),
            cube((-0.3, 3.1, -0.3), (0.6, 1.5, 0.6), "ashen_rib"),
        ], rest=(0.15, 0.0, -sx * 0.45)))

        parts.append(part(f"FingersInner_{side}", f"Hand_{side}", (-sx * 0.55, 3.2, 0.0), [
            cube((-0.35, 0.0, -0.4), (0.7, 4.4, 0.8), "pale_palm"),
            cube((0.35 if sx < 0 else -1.05, 0.0, -0.35), (0.65, 4.9, 0.75), "pale_palm"),
            cube((-0.3, 4.2, -0.3), (0.6, 1.7, 0.6), "ashen_rib"),
        ], rest=(-0.18, 0.0, 0.0)))

        parts.append(part(f"FingersOuter_{side}", f"Hand_{side}", (sx * 0.75, 3.1, 0.0), [
            cube((-0.35, 0.0, -0.4), (0.7, 4.6, 0.8), "pale_palm"),
            cube((sx * 0.45 - 0.3, 0.0, -0.35), (0.6, 3.8, 0.7), "pale_palm"),
            cube((-0.3, 4.3, -0.3), (0.6, 1.6, 0.6), "ashen_rib"),
        ], rest=(-0.22, 0.0, sx * 0.14)))

    return parts


def pose_template():
    joints = {}
    for p in build_parts():
        entry = {"rot_rad_mc": list(p["rest_rot_rad_mc"])}
        if p["name"] == "Pelvis":
            entry["pos_mc"] = list(p["pivot_mc"])
        joints[p["name"]] = entry
    return joints


def kf(time_sec, overrides):
    pose = pose_template()
    for name, vals in overrides.items():
        if "rot" in vals:
            pose[name]["rot_rad_mc"] = [round(float(x), 4) for x in vals["rot"]]
        if "pos" in vals:
            pose[name]["pos_mc"] = [round(float(x), 3) for x in vals["pos"]]
    return {"time_sec": round(float(time_sec), 4), "joints": pose}


def build_animations():
    # 1. idle_hover (2.5s loop): eerie swaying hover, head tilt, jaw tremble
    idle_frames = []
    for i in range(9):
        t = i * (2.5 / 8.0)
        ph = (i / 8.0) * math.tau
        s = math.sin(ph)
        c = math.cos(ph)
        idle_frames.append(kf(t, {
            "Pelvis": {"pos": (0.0, 5.5 + s * 0.5, 0.0), "rot": (0.03 * c, 0.04 * s, 0.02 * s)},
            "LowerSpine": {"rot": (0.14 + 0.04 * s, 0.0, 0.03 * c)},
            "UpperTorso": {"rot": (0.18 + 0.05 * c, 0.03 * s, 0.02 * s)},
            "Head": {"rot": (-0.18 + 0.05 * s, 0.08 * c, 0.14 * s)},
            "Jaw": {"rot": (0.18 + 0.06 * abs(s), 0.0, 0.0)},
            "UpperArm_L": {"rot": (0.10 + 0.06 * s, 0.0, -0.14 - 0.03 * c)},
            "UpperArm_R": {"rot": (0.10 - 0.06 * s, 0.0, 0.14 + 0.03 * c)},
            "Tendril_L": {"rot": (0.24 + 0.12 * s, 0.0, -0.14 + 0.08 * c)},
            "Tendril_R": {"rot": (0.24 - 0.12 * s, 0.0, 0.14 - 0.08 * c)},
            "Tendril_Back": {"rot": (0.30 + 0.14 * c, 0.0, 0.06 * s)},
        }))

    # 2. beckon_lure (2.4s loop): raises right skeletal arm high & waves elongated pale fingers; left hand presses forward
    beckon_frames = []
    for i in range(9):
        t = i * (2.4 / 8.0)
        ph = (i / 8.0) * math.tau
        s = math.sin(ph)
        c = math.cos(ph)
        beckon_frames.append(kf(t, {
            "Pelvis": {"pos": (0.0, 5.2 + s * 0.4, -0.4), "rot": (0.05, 0.05 * s, 0.0)},
            "LowerSpine": {"rot": (0.20, 0.03 * c, 0.04 * s)},
            "UpperTorso": {"rot": (0.24 + 0.04 * c, -0.08, 0.04 * s)},
            "NeckLower": {"rot": (0.28, 0.0, 0.0)},
            "Head": {"rot": (-0.28 + 0.05 * s, 0.08 * c, 0.22 + 0.06 * s)},
            "Jaw": {"rot": (0.34 + 0.08 * s, 0.0, 0.0)},
            # Left hand gently pressed forward near the glass
            "UpperArm_L": {"rot": (-0.65 + 0.06 * c, 0.12, -0.18)},
            "Forearm_L": {"rot": (-0.42 - 0.08 * s, 0.0, 0.0)},
            "Hand_L": {"rot": (0.25 + 0.10 * s, 0.0, 0.0)},
            "FingersInner_L": {"rot": (-0.12 - 0.12 * s, 0.0, 0.0)},
            "FingersOuter_L": {"rot": (-0.15 - 0.10 * c, 0.0, -0.10)},
            # Right arm raised high, beckoning fingers curling hypnotically
            "UpperArm_R": {"rot": (-1.75 + 0.12 * s, -0.18, 0.26 + 0.08 * c)},
            "Forearm_R": {"rot": (-0.75 + 0.18 * c, 0.0, -0.12)},
            "Hand_R": {"rot": (-0.25 + 0.35 * s, 0.0, 0.12 * c)},
            "FingersInner_R": {"rot": (-0.35 - 0.45 * c, 0.0, 0.0)},
            "FingersOuter_R": {"rot": (-0.45 - 0.45 * s, 0.0, 0.14)},
            "Thumb_R": {"rot": (0.25 + 0.15 * s, 0.0, -0.45)},
            "Tendril_L": {"rot": (0.32 + 0.14 * s, 0.0, -0.16)},
            "Tendril_R": {"rot": (0.32 - 0.14 * s, 0.0, 0.16)},
        }))

    # 3. lunge_grab (1.1s non-loop): coils back, jaw wide open, whips both claws violently forward, recoils
    lunge_frames = [
        kf(0.0, {}),
        kf(0.22, {
            "Pelvis": {"pos": (0.0, 6.2, 2.2), "rot": (-0.12, 0.0, 0.0)},
            "LowerSpine": {"rot": (-0.06, 0.0, 0.0)},
            "UpperTorso": {"rot": (-0.10, 0.0, 0.0)},
            "Head": {"rot": (-0.10, 0.0, -0.12)},
            "Jaw": {"rot": (0.72, 0.0, 0.0)},
            "UpperArm_L": {"rot": (-0.85, 0.25, -0.45)},
            "UpperArm_R": {"rot": (-0.85, -0.25, 0.45)},
            "Forearm_L": {"rot": (-1.05, 0.0, 0.0)},
            "Forearm_R": {"rot": (-1.05, 0.0, 0.0)},
        }),
        kf(0.45, {
            "Pelvis": {"pos": (0.0, 4.6, -5.2), "rot": (0.18, 0.0, 0.0)},
            "LowerSpine": {"rot": (0.34, 0.0, 0.0)},
            "UpperTorso": {"rot": (0.42, 0.0, 0.0)},
            "Head": {"rot": (-0.45, 0.0, 0.05)},
            "Jaw": {"rot": (0.95, 0.0, 0.0)},
            "UpperArm_L": {"rot": (-1.68, -0.15, -0.12)},
            "UpperArm_R": {"rot": (-1.68, 0.15, 0.12)},
            "Forearm_L": {"rot": (-0.15, 0.0, 0.0)},
            "Forearm_R": {"rot": (-0.15, 0.0, 0.0)},
            "FingersInner_L": {"rot": (-0.75, 0.0, 0.0)},
            "FingersOuter_L": {"rot": (-0.75, 0.0, -0.18)},
            "FingersInner_R": {"rot": (-0.75, 0.0, 0.0)},
            "FingersOuter_R": {"rot": (-0.75, 0.0, 0.18)},
        }),
        kf(0.75, {
            "Pelvis": {"pos": (0.0, 5.1, -2.5), "rot": (0.10, 0.0, 0.0)},
            "Jaw": {"rot": (0.55, 0.0, 0.0)},
            "UpperArm_L": {"rot": (-1.20, 0.0, -0.18)},
            "UpperArm_R": {"rot": (-1.20, 0.0, 0.18)},
            "Forearm_L": {"rot": (-0.55, 0.0, 0.0)},
            "Forearm_R": {"rot": (-0.55, 0.0, 0.0)},
        }),
        kf(1.10, {}),
    ]

    # 4. recoil_banish (0.9s non-loop): throws arms across face & shrinks back into fog
    banish_frames = [
        kf(0.0, {}),
        kf(0.28, {
            "Pelvis": {"pos": (0.0, 6.8, 3.5), "rot": (-0.18, 0.0, 0.0)},
            "UpperTorso": {"rot": (-0.16, 0.0, 0.0)},
            "Head": {"rot": (0.28, 0.0, -0.25)},
            "Jaw": {"rot": (0.65, 0.0, 0.0)},
            "UpperArm_L": {"rot": (-1.45, 0.55, 0.25)},
            "UpperArm_R": {"rot": (-1.45, -0.55, -0.25)},
            "Forearm_L": {"rot": (-1.35, 0.0, 0.0)},
            "Forearm_R": {"rot": (-1.35, 0.0, 0.0)},
        }),
        kf(0.90, {
            "Pelvis": {"pos": (0.0, 8.5, 6.5), "rot": (-0.25, 0.0, 0.0)},
            "UpperTorso": {"rot": (0.35, 0.0, 0.0)},
            "Head": {"rot": (0.45, 0.0, 0.0)},
            "UpperArm_L": {"rot": (-0.65, 0.65, 0.35)},
            "UpperArm_R": {"rot": (-0.65, -0.65, -0.35)},
            "Forearm_L": {"rot": (-1.45, 0.0, 0.0)},
            "Forearm_R": {"rot": (-1.45, 0.0, 0.0)},
        }),
    ]

    return {
        "idle_hover": {"loop": True, "keyframes": idle_frames},
        "beckon_lure": {"loop": True, "keyframes": beckon_frames},
        "lunge_grab": {"loop": False, "keyframes": lunge_frames},
        "recoil_banish": {"loop": False, "keyframes": banish_frames},
    }


def spawn_egg():
    rng = random.Random(202)
    mist_light = (212, 224, 232)
    mist_mid = (164, 178, 190)
    dark = (22, 25, 31)
    ash = (66, 74, 84)
    outline = (14, 16, 20)
    inside = set()
    for y in range(16):
        for x in range(16):
            t = (y - 1) / 14
            rx = 5.3 * (0.72 + 0.28 * min(1.0, t * 1.6))
            if ((x + 0.5 - 8) / rx) ** 2 + ((y + 0.5 - 8.6) / 6.9) ** 2 <= 1:
                inside.add((x, y))
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            col = outline
        elif (x == 8 or y == 8) and y >= 4:
            col = ash
        elif y >= 10 or (x in (6, 7, 9, 10) and y >= 7 and rng.random() < 0.6):
            col = dark
        else:
            col = mist_light if y < 6 else mist_mid
        image.putpixel((x, y), col + (255,))
    # Two pinprick pale eyes inside the dark creeping shadow
    for ex, ey in ((6, 9), (9, 9)):
        image.putpixel((ex, ey), (230, 246, 255, 255))
    folder = ASSETS / "textures/item/monsters"
    folder.mkdir(parents=True, exist_ok=True)
    image.save(folder / "window_entity_spawn_egg.png", optimize=True)
    m_path = ASSETS / "models/item/monsters/window_entity_spawn_egg.json"
    m_path.parent.mkdir(parents=True, exist_ok=True)
    m_path.write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "noclip_backrooms:item/monsters/window_entity_spawn_egg"},
    }, indent=2) + "\n", encoding="utf8")
    i_path = ASSETS / "items/window_entity_spawn_egg.json"
    i_path.parent.mkdir(parents=True, exist_ok=True)
    i_path.write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "noclip_backrooms:item/monsters/window_entity_spawn_egg"},
    }, indent=2) + "\n", encoding="utf8")


def main():
    build_textures()
    spec = {
        "entity_id": "noclip_backrooms:window_entity",
        "display_name_zh_tw": "窗戶",
        "display_name_en_us": "Window",
        "texture_atlas": {
            "file": "window_entity.png",
            "width": ATLAS_W,
            "height": ATLAS_H,
            "regions": {k: {"u": v[0], "v": v[1], "w": v[2], "h": v[3]} for k, v in REGIONS.items()},
        },
        "parts": build_parts(),
        "animations": build_animations(),
    }
    text = json.dumps(spec, indent=2, ensure_ascii=False) + "\n"
    SPEC_OUT.parent.mkdir(parents=True, exist_ok=True)
    SPEC_OUT.write_text(text, encoding="utf8")
    ART.mkdir(parents=True, exist_ok=True)
    ART_SPEC_OUT.write_text(text, encoding="utf8")
    spawn_egg()
    parts = spec["parts"]
    cubes = sum(len(p["cubes"]) for p in parts)
    print(f"WindowEntity: {len(parts)} joints, {cubes} cubes, {len(spec['animations'])} animations -> {SPEC_OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
