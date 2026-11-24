"""Builds Zenith Station's robots: one box rig (assets/noclip_backrooms/monsters/robots.json, the same format as the
hound's and the people's, read by BoxRig), one 512 px texture per robot (textures/entity/robots/NAME.png) and the
Blender source (art/robots/robots.blend plus preview renders, made by tools/blender_robots.py).

    python tools/make_robots.py && python tools/blender_robots.py   (the second needs Blender's bpy: Blender 5.3, or
                                                                    `pip install bpy` in a virtual environment)

Three robots, all neutral (they go about the station's business and never attack):
  cleaner  the seven-legged cleaning robot of the Wiki: a low dome on seven little legs, a roller brush, a tank
  chef     a galley robot on wheels with a chef's hat, stirring
  servant  a broken worker robot: scuffed, rusty, one arm torn off, a red eye, cables hanging out of its back

Units are Minecraft model pixels (16 = one block), y down with the feet at y = 24, the robot facing -z.
Textures are drawn at 4 texels to a model pixel.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SCALE = 4
UNITS = 160
ATLAS = UNITS * SCALE

PARTS = {}  # name: (parent, pivot, [cubes (material, origin, size)])


def part(name, parent, pivot, *cubes):
    PARTS[name] = (parent, pivot, list(cubes))


# The cleaner: a low dome on seven legs that stand out from under its bumper
part("ClBody", "root", (0, 0, 0),
     ("shell", (-8, 9.5, -8), (16, 6, 16)), ("shell", (-6, 6, -6), (12, 3.5, 12)), ("rubber", (-8.3, 14.5, -8.3), (16.6, 1.4, 16.6)),
     ("lens", (-3, 7.5, -6.5), (6, 1.8, 0.8)), ("trim", (-2, 4.9, -2), (4, 1.1, 4)))
part("ClTank", "ClBody", (0, 0, 0), ("tank", (-4, 5.5, 3), (8, 6, 5)), ("hose", (-0.8, 2.5, 5), (1.6, 3, 1.6)))
part("ClBrush", "ClBody", (0, 17.2, -5), ("brush", (-5, -1.2, -2.2), (10, 2.4, 4.4)))
part("ClLamp", "ClBody", (0, 0, 0), ("lamp", (-1.5, 6, -6.4), (3, 2, 1.2)))
for i in range(7):
    a = 2 * math.pi * i / 7 + 0.2
    x, z = round(8.6 * math.cos(a), 2), round(8.6 * math.sin(a), 2)
    part(f"ClLeg{i}", "ClBody", (x, 15.4, z), ("leg", (-0.8, 0, -0.8), (1.6, 4.2, 1.6)))
    part(f"ClFoot{i}", f"ClLeg{i}", (0, 4.2, 0), ("leg", (-0.7, 0, -0.7), (1.4, 3.4, 1.4)), ("rubber", (-1.3, 3.4, -1.3), (2.6, 1.0, 2.6)))

# The chef
part("KBase", "root", (0, 0, 0), ("steel", (-6, 19, -6), (12, 3.4, 12)), ("trim", (-6.2, 21, -6.2), (12.4, 0.8, 12.4)))
part("KWheelR", "KBase", (-7, 21.5, 0), ("rubber", (-1.5, -2.5, -2.5), (3, 5, 5)))
part("KWheelL", "KBase", (7, 21.5, 0), ("rubber", (-1.5, -2.5, -2.5), (3, 5, 5)))
part("KTorso", "KBase", (0, 19, 0), ("white", (-4, -9, -3), (8, 9, 6)), ("trim", (-4.2, -3, -3.2), (8.4, 1, 6.4)), ("apron", (-3, -8.5, -3.5), (6, 8, 0.5)))
part("KHead", "KTorso", (0, -9, 0), ("steel", (-3.5, -6, -3.5), (7, 6, 7)), ("lens", (-3, -4.4, -3.9), (6, 2, 0.6)), ("trim", (-1.5, -1.8, -3.9), (3, 1, 0.5)))
part("KHat", "KHead", (0, -6, 0), ("white", (-3.7, -1, -3.7), (7.4, 1.4, 7.4)), ("white", (-4.4, -5.6, -4.4), (8.8, 4.8, 8.8)))
part("KArmR", "KTorso", (-5.6, -7.5, 0), ("steel", (-1.5, -1, -1.5), (3, 5, 3)))
part("KForeR", "KArmR", (0, 4, 0), ("steel", (-1.3, 0, -1.3), (2.6, 4.5, 2.6)), ("trim", (-1.6, 4.5, -1.6), (3.2, 1.6, 3.2)))
part("KTool", "KForeR", (0, 6, 0), ("tool", (-0.4, -1, -0.4), (0.8, 6, 0.8)), ("tool", (-1.8, 4.5, -0.3), (3.6, 2.5, 0.6)))
part("KArmL", "KTorso", (5.6, -7.5, 0), ("steel", (-1.5, -1, -1.5), (3, 5, 3)))
part("KForeL", "KArmL", (0, 4, 0), ("steel", (-1.3, 0, -1.3), (2.6, 4.5, 2.6)), ("trim", (-1.6, 4.5, -1.6), (3.2, 1.6, 3.2)))

# The broken servant
part("SBody", "root", (0, 0, 0), ("grey", (-4, 5, -2.5), (8, 9, 5)), ("rust", (-3.4, 6, -2.9), (6.8, 4, 0.5)), ("dark", (-3.5, 14, -2), (7, 3, 4)))
part("SHead", "SBody", (0, 5, 0), ("grey", (-3, -6, -3), (6, 6, 6)), ("dark", (-0.4, -8, -0.4), (0.8, 2, 0.8)))
part("SArmR", "SBody", (-5.2, 6, 0), ("grey", (-1.4, -0.5, -1.4), (2.8, 5, 2.8)))
part("SForeR", "SArmR", (0, 4.5, 0), ("dark", (-1.2, 0, -1.2), (2.4, 5, 2.4)))
part("SArmL", "SBody", (5.2, 6, 0), ("grey", (-1.4, -0.5, -1.4), (2.8, 5, 2.8)), ("wire", (-0.3, 4.5, -0.3), (0.6, 4, 0.6)), ("wire", (0.5, 4.5, 0.2), (0.6, 3, 0.6)))
part("SLegR", "SBody", (-2, 17, 0), ("dark", (-1.5, 0, -1.5), (3, 3.5, 3)))
part("SShinR", "SLegR", (0, 3.5, 0), ("grey", (-1.3, 0, -1.3), (2.6, 3.5, 2.6)), ("dark", (-1.6, 2.8, -2.8), (3.2, 0.7, 4.2)))
part("SLegL", "SBody", (2, 17, 0), ("dark", (-1.5, 0, -1.5), (3, 3.5, 3)))
part("SShinL", "SLegL", (0, 3.5, 0), ("grey", (-1.3, 0, -1.3), (2.6, 3.5, 2.6)), ("dark", (-1.6, 2.8, -2.8), (3.2, 0.7, 4.2)))
part("SCables", "SBody", (0, 0, 0), ("wire", (-3.6, 13, 2.5), (0.7, 6, 0.7)), ("wire", (2.4, 13, 2.6), (0.7, 5, 0.7)), ("wire", (-0.4, 12, 2.4), (0.7, 7, 0.7)))

KINDS = {
    "cleaner": ["ClBody", "ClTank", "ClBrush", "ClLamp"] + [f"Cl{p}{i}" for i in range(7) for p in ("Leg", "Foot")],
    "chef": ["KBase", "KWheelR", "KWheelL", "KTorso", "KHead", "KHat", "KArmR", "KForeR", "KTool", "KArmL", "KForeL"],
    "servant": ["SBody", "SHead", "SArmR", "SForeR", "SArmL", "SLegR", "SShinR", "SLegL", "SShinL", "SCables"],
}

PALETTES = {
    "cleaner": dict(shell="e4e8ea", rubber="26282c", lens="5ab8e8", trim="98a0a6", tank="4a78b8", hose="2e3034", brush="d8b438", lamp="fff0b0", leg="7c848a"),
    "chef": dict(white="e6e6e0", steel="a6abb0", rubber="26282c", trim="6c7176", lens="ffb43a", apron="c04a3a", tool="b8bcc0"),
    "servant": dict(grey="7a7e84", dark="2c2e32", rust="8a5a3c", lens="e03a30", wire="c8782a"),
}
DEFAULT = "808080"


def hexrgb(text):
    return np.array([int(text[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def pack():
    cubes = []
    for name, (parent, pivot, boxes) in PARTS.items():
        for i, (material, origin, size) in enumerate(boxes):
            cubes.append(dict(part=name, index=i, material=material, origin=origin, size=size))
    x = y = shelf = 0
    for cube in cubes:
        w, h, d = cube["size"]
        cw, ch = int(math.ceil(2 * (d + w))) + 1, int(math.ceil(d + h)) + 1
        if x + cw > UNITS:
            x, y, shelf = 0, y + shelf + 1, 0
        cube["uv"] = (x, y)
        x += cw
        shelf = max(shelf, ch)
    assert y + shelf <= UNITS, f"atlas too small: needs {y + shelf}"
    return cubes


def faces(cube):
    w, h, d = cube["size"]
    u, v = cube["uv"][0] * SCALE, cube["uv"][1] * SCALE
    s = SCALE
    r = lambda a, b, c, e: (u + int(a * s), v + int(b * s), u + int(round((a + c) * s)), v + int(round((b + e) * s)))
    return {"top": r(d, 0, w, d), "bottom": r(d + w, 0, w, d), "left": r(0, d, d, h), "front": r(d, d, w, h), "right": r(d + w, d, d, h), "back": r(d + w + d, d, w, h)}


def paint(kind, cubes):
    palette = PALETTES[kind]
    rng = np.random.default_rng(sum(map(ord, kind)))
    img = np.zeros((ATLAS, ATLAS, 4), dtype=np.float32)
    for cube in cubes:
        base = hexrgb(palette.get(cube["material"], DEFAULT))
        for face, (x0, y0, x1, y1) in faces(cube).items():
            w, h = x1 - x0, y1 - y0
            if w <= 0 or h <= 0:
                continue
            yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
            noise = rng.normal(0, 1, (h, w)).astype(np.float32)
            coarse = np.kron(rng.normal(0, 1, (max(h // 8, 1) + 1, max(w // 8, 1) + 1)), np.ones((8, 8)))[:h, :w].astype(np.float32)
            tone = 1 + noise * 0.02 + coarse * (0.09 if kind == "servant" else 0.035)
            edge = np.minimum.reduce([xx, yy, w - 1 - xx, h - 1 - yy]) / max(min(w, h) / 2, 1)
            tone *= 0.8 + 0.2 * np.clip(edge * 3, 0, 1)
            tone *= {"top": 1.12, "bottom": 0.7, "left": 0.9, "right": 0.9, "front": 1.0, "back": 0.94}[face]
            patch = np.clip(base[None, None, :] * tone[..., None], 0, 255)
            if kind == "servant" and cube["material"] in ("grey", "dark"):
                rust = np.clip(coarse * 0.8 + noise * 0.3 - 0.5, 0, 1)[..., None]
                patch = patch * (1 - rust * 0.5) + hexrgb("8a5a3c")[None, None, :] * rust * 0.5
            img[y0:y1, x0:x1, :3] = patch
            img[y0:y1, x0:x1, 3] = 255
    pil = Image.fromarray(img.astype(np.uint8), "RGBA")
    draw = ImageDraw.Draw(pil)
    by = {(c["part"], c["index"]): c for c in cubes}
    detail(draw, kind, palette, by)
    return pil


def rect(draw, box, fx0, fy0, fx1, fy1, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    draw.rectangle((x0 + w * fx0, y0 + h * fy0, x0 + w * fx1, y0 + h * fy1), fill=fill, outline=outline, width=width)


def detail(draw, kind, palette, by):
    ink = lambda key: tuple(int(v) for v in hexrgb(palette.get(key, DEFAULT))) + (255,)
    if kind == "cleaner":
        box = faces(by[("ClBody", 3)])["front"]  # the eye strip: a pupil sliding in a slot
        rect(draw, box, 0.05, 0.15, 0.95, 0.85, fill=(20, 40, 56, 255))
        rect(draw, box, 0.38, 0.2, 0.62, 0.8, fill=(190, 240, 255, 255))
        for k in range(3):  # seams in the shell
            for key in ("front", "back", "left", "right"):
                rect(draw, faces(by[("ClBody", 0)])[key], 0.0, 0.3 + k * 0.25, 1.0, 0.33 + k * 0.25, fill=(150, 156, 162, 255))
        for key in ("front", "back"):  # hazard stripes on the bumper
            x0, y0, x1, y1 = faces(by[("ClBody", 2)])[key]
            for sx in range(x0, x1, 12):
                draw.polygon([(sx, y1), (sx + 6, y1), (sx + 12, y0), (sx + 6, y0)], fill=(214, 176, 48, 255))
        for key in ("front", "back", "left", "right", "top"):  # bristles
            x0, y0, x1, y1 = faces(by[("ClBrush", 0)])[key]
            for sx in range(x0, x1, 3):
                draw.line((sx, y0, sx, y1), fill=(150, 120, 30, 255))
        x0, y0, x1, y1 = faces(by[("ClTank", 0)])["front"]
        rect(draw, (x0, y0, x1, y1), 0.1, 0.15, 0.9, 0.85, outline=(220, 230, 240, 255), width=2)
        rect(draw, (x0, y0, x1, y1), 0.15, 0.55, 0.85, 0.8, fill=(110, 190, 230, 255))
    elif kind == "chef":
        box = faces(by[("KHead", 1)])["front"]  # the visor with two amber eyes
        rect(draw, box, 0.0, 0.0, 1.0, 1.0, fill=(30, 28, 26, 255))
        rect(draw, box, 0.18, 0.2, 0.42, 0.8, fill=(255, 190, 70, 255))
        rect(draw, box, 0.58, 0.2, 0.82, 0.8, fill=(255, 190, 70, 255))
        box = faces(by[("KHead", 2)])["front"]
        for k in range(4):
            rect(draw, box, 0.1, 0.15 + k * 0.22, 0.9, 0.25 + k * 0.22, fill=(30, 32, 34, 255))
        box = faces(by[("KTorso", 2)])["front"]  # the apron pocket
        rect(draw, box, 0.18, 0.55, 0.82, 0.88, outline=(240, 220, 210, 255), width=2)
        rect(draw, box, 0.0, 0.0, 1.0, 0.08, fill=(240, 220, 210, 255))
        box = faces(by[("KTorso", 0)])["front"]
        for k in range(3):
            rect(draw, box, 0.08, 0.1 + k * 0.1, 0.18, 0.14 + k * 0.1, fill=(60, 200, 90, 255) if k == 0 else (200, 70, 60, 255))
    else:
        box = faces(by[("SHead", 0)])["front"]
        rect(draw, box, 0.0, 0.0, 1.0, 1.0, fill=None)
        rect(draw, box, 0.12, 0.3, 0.42, 0.52, fill=(14, 14, 16, 255))  # a dead eye, crossed out
        draw.line((box[0] + (box[2] - box[0]) * 0.14, box[1] + (box[3] - box[1]) * 0.32, box[0] + (box[2] - box[0]) * 0.4, box[1] + (box[3] - box[1]) * 0.5), fill=(90, 90, 96, 255), width=2)
        rect(draw, box, 0.58, 0.3, 0.86, 0.52, fill=(240, 60, 44, 255))  # the red one
        rect(draw, box, 0.66, 0.36, 0.78, 0.46, fill=(255, 190, 170, 255))
        draw.line((box[0] + (box[2] - box[0]) * 0.5, box[1], box[0] + (box[2] - box[0]) * 0.34, box[1] + (box[3] - box[1]) * 0.7), fill=(20, 20, 22, 255), width=2)  # a crack
        rect(draw, box, 0.3, 0.74, 0.7, 0.8, fill=(30, 30, 32, 255))
        box = faces(by[("SBody", 1)])["front"]  # a plate hanging off the chest, with a stencilled number
        rect(draw, box, 0.1, 0.2, 0.9, 0.8, outline=(210, 170, 70, 255), width=2)
        for k in range(3):
            rect(draw, box, 0.2 + k * 0.22, 0.35, 0.3 + k * 0.22, 0.65, fill=(210, 170, 70, 255))
        for key in ("front", "back"):
            x0, y0, x1, y1 = faces(by[("SBody", 0)])[key]
            rect(draw, (x0, y0, x1, y1), 0.05, 0.9, 0.95, 0.95, fill=(30, 30, 32, 255))
        for i, colour in enumerate(((200, 60, 50, 255), (60, 130, 200, 255), (220, 190, 60, 255))):
            for key in ("front", "back", "left", "right"):
                rect(draw, faces(by[("SCables", i)])[key], 0.0, 0.0, 1.0, 1.0, fill=colour)


def animations():
    clips = {}

    def pose(joints, **kw):
        out = {name: {"rot_rad_mc": [0.0, 0.0, 0.0]} for name in joints}
        for key, value in kw.items():
            if key.endswith("_pos"):
                out[key[:-4]]["pos_mc"] = [round(float(v), 4) for v in value]
            else:
                out[key]["rot_rad_mc"] = [round(float(v), 4) for v in value]
        return out

    def clip(name, joints, duration, count, make, loop=True):
        keys = []
        for i in range(count):
            t = i / count if loop else i / (count - 1)
            keys.append({"time_sec": round(t * duration, 3), "joints": pose(joints, **make(t))})
        clips[name] = {"loop": loop, "keyframes": keys}

    cl = KINDS["cleaner"]
    clip("idle_cleaner", cl, 3.2, 8, lambda t: dict(ClBody_pos=(0, 0.2 * math.sin(t * 2 * math.pi), 0), ClLamp=(0.1 * math.sin(t * 4 * math.pi), 0, 0),
                                                    **{f"ClLeg{i}": (0.04 * math.sin(t * 2 * math.pi + i), 0, 0) for i in range(7)}))
    clip("walk_cleaner", cl, 1.0, 8, lambda t: dict(
        ClBody_pos=(0, -0.3 * abs(math.sin(t * 4 * math.pi)), 0), ClBody=(0.02 * math.sin(t * 2 * math.pi), 0, 0.03 * math.sin(t * 4 * math.pi)),
        **{f"ClLeg{i}": (0.55 * math.sin(t * 2 * math.pi + i * 2.4), 0, 0.3 * math.cos(t * 2 * math.pi + i * 2.4)) for i in range(7)},
        **{f"ClFoot{i}": (-0.4 * max(0.0, math.sin(t * 2 * math.pi + i * 2.4 + 1.2)), 0, 0) for i in range(7)}))

    ch = KINDS["chef"]
    clip("idle_chef", ch, 3.6, 8, lambda t: dict(KTorso=(0.02 * math.sin(t * 2 * math.pi), 0, 0), KHead=(0.03, 0.18 * math.sin(t * 2 * math.pi), 0),
                                                 KArmL=(-0.15, 0, -0.1), KForeL=(-0.5 + 0.05 * math.sin(t * 2 * math.pi), 0, 0)))
    clip("walk_chef", ch, 1.2, 8, lambda t: dict(KTorso=(0.1, 0, 0.03 * math.sin(t * 2 * math.pi)), KHead=(-0.08, 0, 0),
                                                 KArmR=(-0.35, 0, 0.1), KArmL=(-0.35, 0, -0.1), KForeR=(-0.5, 0, 0), KForeL=(-0.5, 0, 0)))
    clip("cook_chef", ch, 1.6, 8, lambda t: dict(
        KTorso=(0.06, 0.1 * math.sin(t * 2 * math.pi), 0), KHead=(0.25, 0.1 * math.sin(t * 2 * math.pi + 1), 0),
        KArmR=(-1.15 + 0.12 * math.sin(t * 2 * math.pi), 0.2 * math.cos(t * 2 * math.pi), 0.3), KForeR=(-0.9 + 0.2 * math.cos(t * 2 * math.pi), 0, 0),
        KTool=(0.25 * math.sin(t * 2 * math.pi), 0, 0.25 * math.cos(t * 2 * math.pi)),
        KArmL=(-1.0, 0, -0.25), KForeL=(-1.1, 0, 0)))

    sv = KINDS["servant"]
    clip("idle_servant", sv, 4.0, 8, lambda t: dict(
        SBody=(0.05 + 0.02 * math.sin(t * 2 * math.pi), 0, 0.04 * math.sin(t * 2 * math.pi)), SHead=(0.2, 0.25 * math.sin(t * 2 * math.pi), 0.35),
        SArmR=(0.1, 0, 0.08), SForeR=(0.2 * math.sin(t * 4 * math.pi), 0, 0), SArmL=(-0.1, 0, -0.1)))
    clip("walk_servant", sv, 1.4, 8, lambda t: dict(
        SBody=(0.12, 0.06 * math.sin(t * 2 * math.pi), 0.1 * math.sin(t * 2 * math.pi)), SHead=(0.15, 0, 0.3),
        SLegR=(0.55 * math.sin(t * 2 * math.pi), 0, 0), SShinR=(0.4 * max(0, -math.sin(t * 2 * math.pi)), 0, 0),
        SLegL=(0.3 * math.sin(t * 2 * math.pi + math.pi), 0, 0.12), SShinL=(0.1, 0, 0),
        SArmR=(-0.3 * math.sin(t * 2 * math.pi), 0, 0.1), SForeR=(0.3, 0, 0), SArmL=(0.2 * math.sin(t * 2 * math.pi), 0, -0.1)))
    spasm = [(0, 0, 0), (0, 0, 0), (0.5, 0.6, 0.2), (-0.4, -0.5, 0.5), (0.6, 0.4, -0.3), (0, 0, 0), (0, 0, 0), (0, 0, 0)]
    clip("twitch_servant", sv, 4.0, 8, lambda t: (lambda s: dict(SHead=(0.1 + s[0], s[1], 0.3 + s[2]), SArmL=(-0.1 + s[0] * 1.5, 0, -0.1 + s[1]),
                                                                  SForeR=(0.2 + s[2] * 2, 0, 0), SBody=(0.05, s[1] * 0.3, 0)))(spasm[int(t * 8) % 8]))
    return clips


def main():
    cubes = pack()
    parts = []
    for name, (parent, pivot, boxes) in PARTS.items():
        parts.append({"name": name, "parent": parent, "pivot_mc": list(pivot), "rest_rot_rad_mc": [0, 0, 0],
                      "cubes": [{"origin_mc": list(c["origin"]), "size_mc": list(c["size"]), "uv_region": list(c["uv"]), "material": c["material"]}
                                for c in cubes if c["part"] == name]})
    spec = {"entity_id": "noclip_backrooms:robots", "texture_atlas": {"width": UNITS, "height": UNITS}, "looks": KINDS, "parts": parts,
            "animations": animations()}
    out = ROOT / "src/main/resources/assets/noclip_backrooms/monsters/robots.json"
    out.write_text(json.dumps(spec, indent=1) + "\n", encoding="utf8")
    tex = ROOT / "src/main/resources/assets/noclip_backrooms/textures/entity/robots"
    tex.mkdir(parents=True, exist_ok=True)
    art = ROOT / "art/robots"
    art.mkdir(parents=True, exist_ok=True)
    for kind in KINDS:
        paint(kind, cubes).save(tex / f"{kind}.png")
    (art / "robots_spec.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf8")
    print("wrote", out, len(cubes), "boxes,", len(KINDS), "robots")


if __name__ == "__main__":
    main()
