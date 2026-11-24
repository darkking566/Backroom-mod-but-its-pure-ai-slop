"""Builds the settlements' people: one box rig (assets/noclip_backrooms/monsters/people.json, the same format as the
hound's, read by BoxRig), one 512 px texture per look (textures/entity/people/NAME.png) and the Blender source
(art/people/people.blend plus preview renders, made by tools/blender_people.py).

Units are Minecraft model pixels (16 = one block), y down with the feet at y = 24, the figure facing -z.
Textures are drawn at 4 texels to a model pixel, so a face is 32 px across instead of vanilla's 8.
"""
import json
import math
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SCALE = 4  # texels per model pixel: the file is drawn 4x finer than the units the model's UVs use
UNITS = 160  # the atlas in model pixels (UVs are given in these); the PNG is UNITS * SCALE wide
ATLAS = UNITS * SCALE

# part name: (parent, pivot relative to parent, [cubes]); a cube is (material, origin, size)
PARTS = {
    "Body": ("root", (0, 0, 0), [("shirt", (-4, 0, -2), (8, 8, 4)), ("pants", (-3.8, 8, -1.8), (7.6, 4, 3.6)), ("belt", (-4.1, 7.2, -2.1), (8.2, 1.2, 4.2))]),
    "Head": ("Body", (0, 0, 0), [("skin", (-4, -8, -4), (8, 8, 8)), ("hair", (-4.25, -8.25, -4.25), (8.5, 2.5, 8.5)), ("hair", (-4.25, -6, 1.8), (8.5, 6, 2.45)),
                                  ("skin", (-1, -3.2, -5), (2, 2.6, 1)), ("skin", (-4.7, -4.5, -1.2), (0.7, 2, 2)), ("skin", (4, -4.5, -1.2), (0.7, 2, 2))]),
    "ArmR": ("Body", (-5, 2, 0), [("shirt", (-3, -2, -2), (4, 6, 4))]),
    "ForeR": ("ArmR", (-1, 4, 0), [("shirt", (-2, 0, -2), (4, 4.5, 4)), ("skin", (-1.6, 4.5, -1.6), (3.2, 2.5, 3.2))]),
    "ArmL": ("Body", (5, 2, 0), [("shirt", (-1, -2, -2), (4, 6, 4))]),
    "ForeL": ("ArmL", (1, 4, 0), [("shirt", (-2, 0, -2), (4, 4.5, 4)), ("skin", (-1.6, 4.5, -1.6), (3.2, 2.5, 3.2))]),
    "LegR": ("Body", (-1.9, 12, 0), [("pants", (-2, 0, -2), (4, 6, 4))]),
    "ShinR": ("LegR", (0, 6, 0), [("pants", (-2, 0, -2), (4, 4.6, 4)), ("shoe", (-2.2, 4.6, -3.2), (4.4, 1.4, 5.4))]),
    "LegL": ("Body", (1.9, 12, 0), [("pants", (-2, 0, -2), (4, 6, 4))]),
    "ShinL": ("LegL", (0, 6, 0), [("pants", (-2, 0, -2), (4, 4.6, 4)), ("shoe", (-2.2, 4.6, -3.2), (4.4, 1.4, 5.4))]),
    # accessories, shown per look
    "Cap": ("Head", (0, 0, 0), [("cap", (-4.4, -9, -4.4), (8.8, 2.4, 8.8)), ("cap", (-4.4, -7.4, -7), (8.8, 0.8, 2.8))]),
    "Beanie": ("Head", (0, 0, 0), [("cap", (-4.5, -9.2, -4.5), (9, 3.4, 9)), ("cap", (-1, -10.4, -1), (2, 1.4, 2))]),
    "ChefHat": ("Head", (0, 0, 0), [("white", (-4.4, -9, -4.4), (8.8, 1.8, 8.8)), ("white", (-4.9, -15, -4.9), (9.8, 6.2, 9.8))]),
    "WideHat": ("Head", (0, 0, 0), [("cap", (-7, -8.6, -7), (14, 0.8, 14)), ("cap", (-4.4, -12, -4.4), (8.8, 3.6, 8.8))]),
    "Goggles": ("Head", (0, 0, 0), [("trim", (-4.4, -6.8, -4.4), (8.8, 1.6, 1)), ("lens", (-3.6, -7.2, -5.2), (3, 2.4, 0.8)), ("lens", (0.6, -7.2, -5.2), (3, 2.4, 0.8))]),
    "LongHair": ("Head", (0, 0, 0), [("hair", (-4.5, -2, 2.2), (9, 12, 2.3))]),
    "Backpack": ("Body", (0, 0, 0), [("pack", (-3.2, 0.6, 2), (6.4, 8.4, 3.4)), ("trim", (-3.4, 0.2, 2.2), (6.8, 1.2, 3.2))]),
    "Coat": ("Body", (0, 0, 0), [("white", (-4.35, -0.1, -2.35), (8.7, 14.5, 4.7))]),
    "Apron": ("Body", (0, 0, 0), [("white", (-3.4, 1.5, -2.5), (6.8, 11, 0.6))]),
    "Scarf": ("Body", (0, 0, 0), [("cloth", (-4.5, -1.2, -2.5), (9, 2.6, 5)), ("cloth", (-3, 1, -2.7), (2.2, 4, 0.8))]),
    "Vest": ("Body", (0, 0, 0), [("vest", (-4.3, 0, -2.3), (8.6, 9, 4.6))]),
    "Tool": ("ForeR", (0, 6.5, 0), [("wood", (-0.5, -7.5, -1), (1, 11, 1)), ("metal", (-1, -8, -2.5), (2, 2.2, 5))]),
    "Clipboard": ("ForeL", (0, 6.5, 0), [("wood", (-0.6, -2, -2.5), (0.8, 6, 5)), ("white", (-1, -1.4, -2), (0.4, 5, 4))]),
}

# palette: skin, hair, shirt, pants, shoe, belt, cap, trim, accessory cloth, vest, pack
LOOKS = {
    "meg_patrol": dict(skin="d9b090", hair="2b2420", shirt="4d5a3d", pants="39422f", shoe="231f1a", belt="1e1b18", cap="3b4630", trim="8a8f76", cloth="6a7358", vest="2e3326", pack="4a4a3c",
                       white="d8d8cc", metal="8a8d92", wood="5a4630", lens="4f6c7a", parts=["Cap", "Backpack", "Vest"], face="stern"),
    "colonist": dict(skin="c89a78", hair="4a3a2a", shirt="7a6a55", pants="4a443d", shoe="2e2923", belt="2a241f", cap="5c4b3a", trim="3a342d", cloth="8a4a38", vest="5e5446", pack="5a4a38",
                     white="d8d2c4", metal="8a8d92", wood="5a4630", lens="4f6c7a", parts=["Beanie", "Scarf"], face="tired"),
    "trader": dict(skin="e0b896", hair="6b5a48", shirt="6b4a2f", pants="3f3a35", shoe="2a2018", belt="1e1a16", cap="5a3c24", trim="a8873a", cloth="8a6a3a", vest="5a4128", pack="7a5a36",
                   white="d8d2c4", metal="8a8d92", wood="5a4630", lens="4f6c7a", parts=["WideHat", "Backpack"], face="smile"),
    "medic": dict(skin="d9b090", hair="3a2c22", shirt="b8d2cc", pants="9db4b0", shoe="d0d0d0", belt="808c8a", cap="e8e8e4", trim="c03030", cloth="e0e0dc", vest="b0c4c0", pack="808c8a",
                  white="ecece6", metal="8a8d92", wood="5a4630", lens="4f6c7a", parts=["Coat", "Clipboard"], face="calm", cross=True),
    "tom": dict(skin="cfa07c", hair="30241c", shirt="7a2f2a", pants="3a3a42", shoe="241e1a", belt="1e1a16", cap="e8e8e4", trim="c0c0b8", cloth="e0e0dc", vest="a03a30", pack="5a4a38",
                white="ecece6", metal="8a8d92", wood="5a4630", lens="4f6c7a", parts=["ChefHat", "Apron"], face="smile"),
    "aiko": dict(skin="e6c4a6", hair="1c1816", shirt="2f4a5a", pants="343a44", shoe="241e1a", belt="1e1a16", cap="b03a3a", trim="d0d0c8", cloth="e0e0dc", vest="a03a30", pack="5a4a38",
                 white="ecece6", metal="8a8d92", wood="5a4630", lens="4f6c7a", parts=["LongHair", "Apron"], face="calm"),
    "speednoclipper": dict(skin="c4906c", hair="1e1a18", shirt="2f6ab0", pants="23262e", shoe="e8e8e4", belt="d8d8d0", cap="2f6ab0", trim="f0f0ea", cloth="e0e0dc", vest="d8a020", pack="2a2d34",
                           white="ecece6", metal="8a8d92", wood="5a4630", lens="e0a030", parts=["Goggles", "Backpack"], face="grin"),
    # Shadows in work clothes: near-black all over, faceless, with a dull orange reflective vest.
    "shadow_worker": dict(skin="14141a", hair="0c0c10", shirt="17171d", pants="101015", shoe="0a0a0d", belt="0d0d11", cap="1a1a20", trim="26262e", cloth="17171d", vest="231a12", pack="14141a",
                          white="17171d", metal="2a2c34", wood="1c1814", lens="000000", parts=["Cap", "Vest", "Tool"], face="void", stripes=True),
}


def hexrgb(text):
    return np.array([int(text[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def pack():
    """Gives every cube a corner in the atlas; the standard box unwrap, scaled."""
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
        cube["extent"] = (cw, ch)
        x += cw
        shelf = max(shelf, ch)
    assert y + shelf <= UNITS, "atlas too small"
    return cubes


def faces(cube):
    """Pixel rects (x0, y0, x1, y1) of the six faces in the atlas, in the vanilla unwrap."""
    w, h, d = cube["size"]
    u, v = cube["uv"][0] * SCALE, cube["uv"][1] * SCALE
    s = SCALE
    r = lambda a, b, c, e: (u + int(a * s), v + int(b * s), u + int(round((a + c) * s)), v + int(round((b + e) * s)))
    return {"top": r(d, 0, w, d), "bottom": r(d + w, 0, w, d), "left": r(0, d, d, h), "front": r(d, d, w, h), "right": r(d + w, d, d, h), "back": r(d + w + d, d, w, h)}


def paint(look, cubes):
    colors = LOOKS[look]
    rng = np.random.default_rng(sum(map(ord, look)))
    img = np.zeros((ATLAS, ATLAS, 4), dtype=np.float32)
    for cube in cubes:
        base = hexrgb(colors[cube["material"]])
        for face, (x0, y0, x1, y1) in faces(cube).items():
            w, h = x1 - x0, y1 - y0
            if w <= 0 or h <= 0:
                continue
            yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
            noise = rng.normal(0, 1, (h, w)).astype(np.float32)
            coarse = np.kron(rng.normal(0, 1, (max(h // 8, 1) + 1, max(w // 8, 1) + 1)), np.ones((8, 8)))[:h, :w].astype(np.float32)
            tone = 1 + noise * 0.025 + coarse * 0.04
            # cloth weave on clothes, softer on skin
            if cube["material"] in ("shirt", "pants", "cloth", "vest", "cap", "pack", "white"):
                tone *= 1 + 0.03 * (np.sin(xx * 1.6) * np.sin(yy * 1.6))
            # shade towards the edges of every face, and by facing
            edge = np.minimum.reduce([xx, yy, w - 1 - xx, h - 1 - yy]) / max(min(w, h) / 2, 1)
            tone *= 0.82 + 0.18 * np.clip(edge * 3, 0, 1)
            tone *= {"top": 1.12, "bottom": 0.7, "left": 0.9, "right": 0.9, "front": 1.0, "back": 0.94}[face]
            patch = np.clip(base[None, None, :] * tone[..., None], 0, 255)
            img[y0:y1, x0:x1, :3] = patch
            img[y0:y1, x0:x1, 3] = 255
    pil = Image.fromarray(img.astype(np.uint8), "RGBA")
    draw = ImageDraw.Draw(pil)
    by = {(c["part"], c["index"]): c for c in cubes}
    detail(draw, look, colors, by)
    return pil


def detail(draw, look, colors, by):
    ink = lambda key: tuple(int(v) for v in hexrgb(colors[key])) + (255,)
    # the face
    head = by[("Head", 0)]
    x0, y0, x1, y1 = faces(head)["front"]
    w, h = x1 - x0, y1 - y0
    face = colors["face"]
    eye = (24, 22, 26, 255)
    if face == "void":
        # no features at all: a shade with only a faint pale smear where eyes would be
        draw.rectangle((x0 + w * 0.22, y0 + h * 0.4, x0 + w * 0.40, y0 + h * 0.46), fill=(48, 48, 58, 255))
        draw.rectangle((x0 + w * 0.60, y0 + h * 0.4, x0 + w * 0.78, y0 + h * 0.46), fill=(48, 48, 58, 255))
    else:
        for ex in (0.2, 0.62):
            draw.rectangle((x0 + w * ex, y0 + h * 0.42, x0 + w * (ex + 0.18), y0 + h * 0.58), fill=(240, 240, 240, 255))
            draw.rectangle((x0 + w * (ex + 0.05 if ex < 0.5 else ex + 0.08), y0 + h * 0.44, x0 + w * (ex + 0.13), y0 + h * 0.58), fill=eye)
        brow_drop = {"stern": 0.06, "tired": 0.04, "calm": 0.0, "smile": -0.01, "grin": -0.01}[face]
        hair = ink("hair")
        for ex in (0.17, 0.6):
            draw.rectangle((x0 + w * ex, y0 + h * (0.34 + brow_drop), x0 + w * (ex + 0.23), y0 + h * (0.4 + brow_drop)), fill=hair)
        mouth = (120, 66, 62, 255)
        if face in ("smile", "grin"):
            draw.rectangle((x0 + w * 0.3, y0 + h * 0.78, x0 + w * 0.7, y0 + h * 0.83), fill=mouth)
            draw.rectangle((x0 + w * 0.26, y0 + h * 0.75, x0 + w * 0.3, y0 + h * 0.78), fill=mouth)
            draw.rectangle((x0 + w * 0.7, y0 + h * 0.75, x0 + w * 0.74, y0 + h * 0.78), fill=mouth)
        else:
            draw.rectangle((x0 + w * 0.34, y0 + h * 0.8, x0 + w * 0.66, y0 + h * 0.84), fill=mouth)
        if face == "tired":
            for ex in (0.2, 0.62):
                draw.rectangle((x0 + w * ex, y0 + h * 0.6, x0 + w * (ex + 0.18), y0 + h * 0.64), fill=(150, 110, 100, 255))
    # hair fringe on the front of the head
    hx0, hy0, hx1, hy1 = faces(by[("Head", 1)])["front"]
    # shirt: buttons / seams and the looks' own marks
    bx0, by0, bx1, by1 = faces(by[("Body", 0)])["front"]
    bw, bh = bx1 - bx0, by1 - by0
    if look == "medic":
        cx, cy = faces(by[("Coat", 0)])["front"][0:2]
        cw, ch = faces(by[("Coat", 0)])["front"][2] - cx, faces(by[("Coat", 0)])["front"][3] - cy
        draw.rectangle((cx + cw * 0.64, cy + ch * 0.1, cx + cw * 0.86, cy + ch * 0.16), fill=ink("trim"))
        draw.rectangle((cx + cw * 0.72, cy + ch * 0.05, cx + cw * 0.78, cy + ch * 0.21), fill=ink("trim"))
        draw.line((cx + cw // 2, cy, cx + cw // 2, cy + ch), fill=(150, 150, 146, 255), width=2)
    if look == "meg_patrol":
        vx0, vy0, vx1, vy1 = faces(by[("Vest", 0)])["front"]
        for k in range(3):
            draw.rectangle((vx0 + 4, vy0 + 6 + k * 16, vx0 + 18, vy0 + 16 + k * 16), outline=ink("trim"), width=1)
            draw.rectangle((vx1 - 18, vy0 + 6 + k * 16, vx1 - 4, vy0 + 16 + k * 16), outline=ink("trim"), width=1)
        draw.rectangle((vx0 + (vx1 - vx0) // 2 - 2, vy0, vx0 + (vx1 - vx0) // 2 + 2, vy1), fill=ink("trim"))
    if look == "speednoclipper":
        draw.rectangle((bx0 + bw * 0.46, by0, bx0 + bw * 0.54, by1), fill=ink("trim"))
        for part in ("ArmR", "ArmL"):
            for i in (0,):
                for face, (a, b, c, d) in faces(by[(part, i)]).items():
                    if face in ("front", "back", "left", "right"):
                        draw.rectangle((a + (c - a) * 0.4, b, a + (c - a) * 0.6, d), fill=ink("trim"))
    if look == "shadow_worker":
        vx0, vy0, vx1, vy1 = faces(by[("Vest", 0)])["front"]
        for k in (0.35, 0.65):
            draw.rectangle((vx0, vy0 + (vy1 - vy0) * k, vx1, vy0 + (vy1 - vy0) * k + 5), fill=(120, 76, 30, 255))
        for k in (0.16, 0.8):
            draw.rectangle((vx0 + (vx1 - vx0) * k, vy0, vx0 + (vx1 - vx0) * k + 5, vy1), fill=(104, 66, 26, 255))
        vb = faces(by[("Vest", 0)])["back"]
        for k in (0.35, 0.65):
            draw.rectangle((vb[0], vb[1] + (vb[3] - vb[1]) * k, vb[2], vb[1] + (vb[3] - vb[1]) * k + 5), fill=(120, 76, 30, 255))
    if look in ("tom", "aiko"):
        ax0, ay0, ax1, ay1 = faces(by[("Apron", 0)])["front"]
        draw.rectangle((ax0 + 4, ay0 + (ay1 - ay0) * 0.55, ax1 - 4, ay0 + (ay1 - ay0) * 0.8), outline=(190, 188, 180, 255), width=2)
        draw.rectangle((ax0, ay0, ax1, ay0 + 4), fill=(190, 188, 180, 255))
    if look == "trader":
        px = faces(by[("Backpack", 1)])["back"]
        draw.rectangle((px[0] + 3, px[1] + 2, px[2] - 3, px[3] - 2), outline=ink("trim"), width=2)
    # belt buckle
    belt = faces(by[("Body", 2)])["front"]
    draw.rectangle((belt[0] + (belt[2] - belt[0]) * 0.45, belt[1], belt[0] + (belt[2] - belt[0]) * 0.55, belt[3]), fill=(170, 150, 80, 255) if look != "shadow_worker" else (40, 40, 48, 255))
    # goggle lenses catch light
    if "Goggles" in colors["parts"]:
        for i in (1, 2):
            x0, y0, x1, y1 = faces(by[("Goggles", i)])["front"]
            draw.rectangle((x0 + 3, y0 + 3, x0 + (x1 - x0) // 2, y0 + 6), fill=(255, 240, 200, 255))


def animations():
    """Idle, walk and work clips as keyframes of absolute joint angles."""
    def pose(**kw):
        joints = {}
        for name in ("Body", "Head", "ArmR", "ForeR", "ArmL", "ForeL", "LegR", "ShinR", "LegL", "ShinL"):
            joints[name] = {"rot_rad_mc": [0.0, 0.0, 0.0]}
        joints["Body"]["pos_mc"] = [0.0, 0.0, 0.0]
        for key, value in kw.items():
            if key == "bob":
                joints["Body"]["pos_mc"][1] = value
            else:
                joints[key]["rot_rad_mc"] = [round(float(v), 4) for v in value]
        return joints

    clips = {}
    keys = []
    for i in range(8):
        t = i / 8
        s = math.sin(t * 2 * math.pi)
        keys.append({"time_sec": round(t * 3.2, 3), "joints": pose(bob=0.18 * s, Head=(0.03 * -s, 0.05 * math.sin(t * 4 * math.pi), 0), ArmR=(0.04 * s, 0, 0.06), ArmL=(-0.04 * s, 0, -0.06),
                                                                  ForeR=(-0.08 + 0.02 * s, 0, 0), ForeL=(-0.08 - 0.02 * s, 0, 0))})
    clips["idle"] = {"loop": True, "keyframes": keys}
    keys = []
    for i in range(8):
        t = i / 8
        a = math.sin(t * 2 * math.pi)
        b = math.sin(t * 2 * math.pi + math.pi)
        knee = lambda p: max(0.0, math.sin(p + math.pi / 2 + 0.4)) * 0.9
        keys.append({"time_sec": round(t * 1.0, 3), "joints": pose(
            bob=-0.5 * abs(math.cos(t * 2 * math.pi)),
            Head=(0.02, 0, 0),
            LegR=(0.7 * a, 0, 0), LegL=(0.7 * b, 0, 0),
            ShinR=(knee(t * 2 * math.pi) if a < 0 else 0.1, 0, 0), ShinL=(knee(t * 2 * math.pi + math.pi) if b < 0 else 0.1, 0, 0),
            ArmR=(-0.6 * a, 0, 0.05), ArmL=(-0.6 * b, 0, -0.05), ForeR=(-0.25 - 0.2 * max(0, -a), 0, 0), ForeL=(-0.25 - 0.2 * max(0, -b), 0, 0))})
    clips["walk"] = {"loop": True, "keyframes": keys}
    keys = []
    for i, (arm, fore, lean) in enumerate([(-0.2, -0.3, 0.0), (-2.5, -0.5, -0.08), (-2.7, -0.2, -0.1), (-0.6, -1.0, 0.1), (-0.2, -0.3, 0.0)]):
        keys.append({"time_sec": round(i * 0.12, 3), "joints": pose(ArmR=(arm, 0, 0.05), ForeR=(fore, 0, 0), Body=(lean, 0, 0))})
    clips["work"] = {"loop": False, "keyframes": keys}
    return clips


def main():
    cubes = pack()
    parts = []
    for name, (parent, pivot, boxes) in PARTS.items():
        parts.append({"name": name, "parent": parent, "pivot_mc": list(pivot), "rest_rot_rad_mc": [0, 0, 0],
                      "cubes": [{"origin_mc": list(c["origin"]), "size_mc": list(c["size"]), "uv_region": list(c["uv"]), "material": c["material"]}
                                for c in cubes if c["part"] == name]})
    spec = {"entity_id": "noclip_backrooms:people", "texture_atlas": {"width": UNITS, "height": UNITS},
            "looks": {look: colors["parts"] for look, colors in LOOKS.items()}, "parts": parts, "animations": animations()}
    out = ROOT / "src/main/resources/assets/noclip_backrooms/monsters/people.json"
    out.write_text(json.dumps(spec, indent=1) + "\n", encoding="utf8")
    tex = ROOT / "src/main/resources/assets/noclip_backrooms/textures/entity/people"
    tex.mkdir(parents=True, exist_ok=True)
    art = ROOT / "art/people"
    art.mkdir(parents=True, exist_ok=True)
    for look in LOOKS:
        paint(look, cubes).save(tex / f"{look}.png")
    (art / "people_spec.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf8")
    print("wrote", out, len(cubes), "boxes,", len(LOOKS), "looks")


if __name__ == "__main__":
    main()
