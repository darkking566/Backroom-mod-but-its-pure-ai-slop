"""Generates realistic 64x64 textures and true 3D forward-pointing JSON item models for
both Backrooms flashlights:
  1. flashlight / flashlight_on: worn dark anodized aluminum heavy-duty tactical/utility flashlight
  2. ambient_flashlight / ambient_flashlight_on: Level 3 brass/gunmetal induction-coil infinite flashlight

Follows docs/look/STYLE_GUIDE.md (64x64 prop/item texture resolution, weathered gritty palette).
"""

import json
import math
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "noclip_backrooms")
TEX_DIR = os.path.join(ASSETS, "textures", "item", "level0")
MODEL_DIR = os.path.join(ASSETS, "models", "item", "level0")


def clamp(v):
    return max(0, min(255, int(v)))


def add_noise(img, x0, y0, x1, y1, rng, amp=8):
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = rng.randint(-amp, amp)
            px[x, y] = (clamp(r + d), clamp(g + d), clamp(b + d), a)


def make_standard_texture():
    """64x64 atlas for standard heavy-duty black anodized flashlight:
    (0..32, 0..32):   weathered dark anodized body + knurled grip section
    (32..64, 0..32):  ribbed metal bezel head + brushed steel ring
    (0..32, 32..64):  unlit glass lens + metallic chrome reflector + tail cap
    (32..64, 32..64): lit warm halogen lens (glowing cream-gold core) + switch + green LED
    """
    rng = random.Random(4201)
    img = Image.new("RGBA", (64, 64), (34, 36, 40, 255))
    draw = ImageDraw.Draw(img)

    # Quadrant 1 (0..32, 0..32): Anodized dark aluminum barrel + knurled diamond grip
    draw.rectangle((0, 0, 31, 31), fill=(40, 43, 48, 255))
    for y in range(2, 30, 2):
        for x in range(2, 30, 2):
            c = 50 if ((x + y) // 2) % 2 == 0 else 30
            draw.point((x, y), fill=(c, c + 2, c + 5, 255))
    # Subtle worn metallic edges
    draw.line((0, 0, 31, 0), fill=(76, 80, 88, 255))
    draw.line((0, 31, 31, 31), fill=(24, 26, 30, 255))
    add_noise(img, 0, 0, 32, 32, rng, 6)

    # Quadrant 2 (32..64, 0..32): Flared bezel head + brushed alloy ring + rubberized grip ribs
    draw.rectangle((32, 0, 63, 31), fill=(52, 56, 62, 255))
    for x in range(34, 62, 3):
        draw.line((x, 2, x, 29), fill=(36, 39, 44, 255))
        draw.line((x + 1, 2, x + 1, 29), fill=(68, 73, 80, 255))
    # Yellow industrial safety stripe near head collar (y=24..28)
    draw.rectangle((32, 24, 63, 27), fill=(178, 146, 52, 255))
    draw.line((32, 28, 63, 28), fill=(28, 30, 34, 255))
    add_noise(img, 32, 0, 64, 32, rng, 7)

    # Quadrant 3 (0..32, 32..64): Unlit glass lens & reflector (0..16, 32..48) + tail cap / switch (16..32, 32..64)
    draw.rectangle((0, 32, 31, 63), fill=(30, 33, 38, 255))
    # Unlit dark glass lens with subtle parabolic reflector rings at (0..16, 32..48)
    cx, cy = 8, 40
    for y in range(32, 48):
        for x in range(0, 16):
            dist = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if dist < 2.2:
                col = (145, 148, 142, 255)  # dim bulb filament
            elif dist < 5.8:
                t = int(112 - dist * 9)
                col = (clamp(t), clamp(t + 4), clamp(t + 10), 255)
            elif dist < 7.5:
                col = (66, 70, 76, 255)
            else:
                col = (34, 37, 42, 255)
            img.putpixel((x, y), col)
    # Dark rubber power switch + tail cap metal (16..32, 32..64)
    draw.rectangle((16, 32, 31, 47), fill=(68, 72, 78, 255))  # brushed tail metal
    draw.rectangle((18, 34, 29, 45), fill=(44, 47, 52, 255))
    draw.rectangle((0, 48, 15, 63), fill=(168, 44, 38, 255))  # weathered red slide-switch
    draw.rectangle((16, 48, 31, 63), fill=(32, 34, 38, 255))  # dark unlit LED housing
    add_noise(img, 0, 32, 32, 64, rng, 5)

    # Quadrant 4 (32..64, 32..64): LIT glowing warm halogen lens (32..48, 32..48) + glowing LED (48..64, 48..64)
    draw.rectangle((32, 32, 63, 63), fill=(42, 45, 50, 255))
    cx2, cy2 = 40, 40
    for y in range(32, 48):
        for x in range(32, 48):
            dist = math.hypot(x - cx2 + 0.5, y - cy2 + 0.5)
            if dist < 3.0:
                col = (255, 252, 232, 255)  # hot incandescent core
            elif dist < 6.0:
                f = (dist - 3.0) / 3.0
                col = (clamp(255 - f * 18), clamp(244 - f * 48), clamp(202 - f * 88), 255)
            elif dist < 7.6:
                col = (205, 178, 112, 255)
            else:
                col = (56, 58, 62, 255)
            img.putpixel((x, y), col)
    # Bright green active indicator LED (48..64, 48..64)
    draw.rectangle((48, 48, 63, 63), fill=(86, 232, 110, 255))
    draw.rectangle((51, 51, 60, 60), fill=(175, 255, 185, 255))
    # Brushed chrome inner ring (48..64, 32..48)
    draw.rectangle((48, 32, 63, 47), fill=(176, 182, 190, 255))
    add_noise(img, 48, 32, 64, 48, rng, 6)

    out = os.path.join(TEX_DIR, "flashlight_3d.png")
    img.save(out)
    print("Wrote", out)


def make_ambient_texture():
    """64x64 atlas for Level 3 infinite ambient-energy flashlight:
    (0..32, 0..32):   industrial dark gunmetal + aged brass bands
    (32..64, 0..32):  copper induction coils wrapped around casing
    (0..32, 32..64):  unlit arc lens (0..16, 32..48), brass endcap (16..32, 32..48), glowing cyan condenser core (0..32, 48..64)
    (32..64, 32..64): lit high-output cyan-gold arc lens (32..48, 32..48), bright energy tube (48..64, 48..64)
    """
    rng = random.Random(4203)
    img = Image.new("RGBA", (64, 64), (42, 46, 54, 255))
    draw = ImageDraw.Draw(img)

    # Quadrant 1 (0..32, 0..32): Industrial gunmetal housing + dark steel rivets
    draw.rectangle((0, 0, 31, 31), fill=(46, 52, 60, 255))
    for y in range(4, 28, 6):
        draw.line((0, y, 31, y), fill=(34, 38, 46, 255))
        draw.line((0, y + 1, 31, y + 1), fill=(64, 72, 82, 255))
    draw.rectangle((0, 24, 31, 28), fill=(158, 128, 72, 255))  # aged brass trim band
    add_noise(img, 0, 0, 32, 32, rng, 6)

    # Quadrant 2 (32..64, 0..32): Exposed copper induction coil windings
    draw.rectangle((32, 0, 63, 31), fill=(120, 70, 42, 255))
    for y in range(0, 32, 3):
        draw.line((32, y, 63, y), fill=(186, 118, 68, 255))
        draw.line((32, y + 1, 63, y + 1), fill=(142, 84, 46, 255))
        draw.line((32, y + 2, 63, y + 2), fill=(76, 42, 24, 255))
    add_noise(img, 32, 0, 64, 32, rng, 7)

    # Quadrant 3 (0..32, 32..64): Unlit arc emitter lens + brass tail cap + dim cyan condenser
    draw.rectangle((0, 32, 31, 63), fill=(36, 40, 48, 255))
    cx, cy = 8, 40
    for y in range(32, 48):
        for x in range(0, 16):
            dist = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if dist < 2.6:
                col = (94, 178, 192, 255)  # faint residual energy core
            elif dist < 6.0:
                col = (68, 92, 108, 255)
            elif dist < 7.5:
                col = (140, 112, 64, 255)  # brass bezel
            else:
                col = (44, 48, 56, 255)
            img.putpixel((x, y), col)
    # Aged brass tail / radiator fin texture (16..32, 32..48)
    draw.rectangle((16, 32, 31, 47), fill=(156, 126, 70, 255))
    draw.rectangle((19, 35, 28, 44), fill=(118, 92, 50, 255))
    # Standby ambient energy glass tube (0..16, 48..64) - softly glowing cyan even when off!
    draw.rectangle((0, 48, 15, 63), fill=(48, 148, 168, 255))
    draw.rectangle((3, 50, 12, 61), fill=(92, 212, 228, 255))
    # Switch brass toggle (16..32, 48..64)
    draw.rectangle((16, 48, 31, 63), fill=(192, 152, 76, 255))
    add_noise(img, 0, 32, 32, 64, rng, 5)

    # Quadrant 4 (32..64, 32..64): LIT intense cyan-white arc lens + energized condenser core
    draw.rectangle((32, 32, 63, 63), fill=(46, 54, 64, 255))
    cx2, cy2 = 40, 40
    for y in range(32, 48):
        for x in range(32, 48):
            dist = math.hypot(x - cx2 + 0.5, y - cy2 + 0.5)
            if dist < 3.2:
                col = (242, 255, 255, 255)
            elif dist < 6.2:
                f = (dist - 3.2) / 3.0
                col = (clamp(215 - f * 95), clamp(252 - f * 22), clamp(255 - f * 10), 255)
            elif dist < 7.6:
                col = (182, 148, 86, 255)
            else:
                col = (54, 60, 70, 255)
            img.putpixel((x, y), col)
    # Fully energized cyan-gold ambient condenser tube (48..64, 48..64)
    draw.rectangle((48, 48, 63, 63), fill=(96, 238, 255, 255))
    draw.rectangle((51, 51, 60, 60), fill=(215, 255, 255, 255))
    # Radiator steel highlight (48..64, 32..48)
    draw.rectangle((48, 32, 63, 47), fill=(142, 156, 170, 255))
    add_noise(img, 48, 32, 64, 48, rng, 5)

    out = os.path.join(TEX_DIR, "ambient_flashlight_3d.png")
    img.save(out)
    print("Wrote", out)


def box(f, t, uv_all, faces_override=None, light_emission=0):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        uv = uv_all if not faces_override or d not in faces_override else faces_override[d]
        faces[d] = {"uv": uv, "texture": "#0"}
    el = {"from": f, "to": t, "faces": faces}
    if light_emission > 0:
        el["light_emission"] = light_emission
    return el


DISPLAY_3D = {
    "firstperson_righthand": {
        "rotation": [2, 0, 0],
        "translation": [0.8, 1.8, -1.6],
        "scale": [0.85, 0.85, 0.85]
    },
    "firstperson_lefthand": {
        "rotation": [2, 0, 0],
        "translation": [0.8, 1.8, -1.6],
        "scale": [0.85, 0.85, 0.85]
    },
    "thirdperson_righthand": {
        "rotation": [0, 0, 0],
        "translation": [0.0, 1.2, -1.2],
        "scale": [0.68, 0.68, 0.68]
    },
    "thirdperson_lefthand": {
        "rotation": [0, 0, 0],
        "translation": [0.0, 1.2, -1.2],
        "scale": [0.68, 0.68, 0.68]
    },
    "gui": {
        "rotation": [26, 138, 0],
        "translation": [0.2, 0.4, 0.0],
        "scale": [1.18, 1.18, 1.18]
    },
    "ground": {
        "rotation": [0, 0, 90],
        "translation": [0.0, 2.0, 0.0],
        "scale": [0.62, 0.62, 0.62]
    },
    "fixed": {
        "rotation": [0, 90, 0],
        "translation": [0.0, 0.0, 0.0],
        "scale": [1.1, 1.1, 1.1]
    }
}


def make_standard_models():
    """True 3D tubular heavy-duty flashlight pointing forward along -Z (tail at z=14, lens at z=1.5)."""
    uv_body = [0, 0, 8, 8]
    uv_head = [8, 0, 16, 8]
    uv_tail = [4, 8, 8, 12]
    uv_switch = [0, 12, 4, 16]
    uv_chrome = [12, 8, 16, 12]

    for lit in (False, True):
        uv_lens = [8, 8, 12, 12] if lit else [0, 8, 4, 12]
        uv_led = [12, 12, 16, 16] if lit else [4, 12, 8, 16]
        sw_z0, sw_z1 = (5.4, 7.0) if lit else (6.6, 8.2)
        elements = [
            # Tail cap + subtle lanyard eyelet
            box([6.4, 6.4, 12.6], [9.6, 9.6, 14.2], uv_tail),
            box([7.3, 7.3, 14.2], [8.7, 8.7, 14.8], uv_chrome),
            # Main cylindrical/octagonal knurled aluminum barrel
            box([6.6, 6.6, 5.2], [9.4, 9.4, 12.6], uv_body),
            box([6.3, 7.0, 5.6], [9.7, 9.0, 12.2], uv_body),
            box([7.0, 6.3, 5.6], [9.0, 9.7, 12.2], uv_body),
            # Slide power switch on top of grip + LED indicator
            box([7.3, 9.5, sw_z0], [8.7, 10.3, sw_z1], uv_switch),
            box([7.6, 9.6, 4.9], [8.4, 10.0, 5.4], uv_led, light_emission=12 if lit else 0),
            # Flared neck collar
            box([6.1, 6.1, 4.0], [9.9, 9.9, 5.2], uv_head),
            # Wide flared lamp head housing
            box([5.5, 5.5, 1.8], [10.5, 10.5, 4.0], uv_head),
            # Protective recessed bezel shroud rim (4 walls around lens)
            box([5.4, 10.0, 1.1], [10.6, 10.6, 1.9], uv_head),
            box([5.4, 5.4, 1.1], [10.6, 6.0, 1.9], uv_head),
            box([5.4, 6.0, 1.1], [6.0, 10.0, 1.9], uv_head),
            box([10.0, 6.0, 1.1], [10.6, 10.0, 1.9], uv_head),
            # Front optical glass lens (emissive when ON!)
            box([6.0, 6.0, 1.4], [10.0, 10.0, 1.9], uv_chrome,
                faces_override={"north": uv_lens}, light_emission=15 if lit else 0),
        ]
        model = {
            "textures": {
                "0": "noclip_backrooms:item/level0/flashlight_3d",
                "particle": "noclip_backrooms:item/level0/flashlight_3d"
            },
            "elements": elements,
            "display": DISPLAY_3D
        }
        name = "flashlight_on.json" if lit else "flashlight.json"
        out = os.path.join(MODEL_DIR, name)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)
        print("Wrote", out)


def make_ambient_models():
    """True 3D Level 3 industrial ambient-energy flashlight with copper coils & glowing condenser core."""
    uv_metal = [0, 0, 8, 8]
    uv_coil = [8, 0, 16, 8]
    uv_brass = [4, 8, 8, 12]
    uv_switch = [4, 12, 8, 16]
    uv_steel = [12, 8, 16, 12]

    for lit in (False, True):
        uv_lens = [8, 8, 12, 12] if lit else [0, 8, 4, 12]
        uv_core = [12, 12, 16, 16] if lit else [0, 12, 4, 16]
        core_emit = 14 if lit else 7
        elements = [
            # Heavy brass collector tail cap + intake vents
            box([6.2, 6.2, 12.6], [9.8, 9.8, 14.5], uv_brass),
            box([6.8, 6.8, 14.5], [9.2, 9.2, 15.1], uv_steel),
            # Rear grip barrel (gunmetal)
            box([6.5, 6.5, 9.2], [9.5, 9.5, 12.6], uv_metal),
            # Exposed glowing ambient-energy condenser cylinder in center
            box([6.8, 6.8, 5.4], [9.2, 9.2, 9.2], uv_core, light_emission=core_emit),
            # Twin copper induction coil rings & 4 protective brass rails framing the energy core
            box([6.1, 6.1, 8.2], [9.9, 9.9, 9.1], uv_coil),
            box([6.1, 6.1, 5.5], [9.9, 9.9, 6.4], uv_coil),
            box([6.2, 9.3, 5.3], [6.9, 9.9, 9.3], uv_brass),
            box([9.1, 9.3, 5.3], [9.8, 9.9, 9.3], uv_brass),
            box([6.2, 6.1, 5.3], [6.9, 6.7, 9.3], uv_brass),
            box([9.1, 6.1, 5.3], [9.8, 6.7, 9.3], uv_brass),
            # Top brass switch lever
            box([7.4, 9.6, 9.8 if lit else 10.6], [8.6, 10.4, 11.0 if lit else 11.8], uv_switch),
            # Flared industrial emitter head + heat-sink collar fins
            box([5.9, 5.9, 3.8], [10.1, 10.1, 5.4], uv_metal),
            box([5.3, 5.3, 1.8], [10.7, 10.7, 3.8], uv_metal),
            box([5.0, 7.2, 2.3], [11.0, 8.8, 3.4], uv_brass),
            box([7.2, 5.0, 2.3], [8.8, 11.0, 3.4], uv_brass),
            # Recessed emitter bezel rim
            box([5.2, 10.1, 1.0], [10.8, 10.8, 1.9], uv_brass),
            box([5.2, 5.2, 1.0], [10.8, 5.9, 1.9], uv_brass),
            box([5.2, 5.9, 1.0], [5.9, 10.1, 1.9], uv_brass),
            box([10.1, 5.9, 1.0], [10.8, 10.1, 1.9], uv_brass),
            # High-output cyan-white arc emitter lens
            box([5.9, 5.9, 1.3], [10.1, 10.1, 1.9], uv_steel,
                faces_override={"north": uv_lens}, light_emission=15 if lit else 5),
        ]
        model = {
            "textures": {
                "0": "noclip_backrooms:item/level0/ambient_flashlight_3d",
                "particle": "noclip_backrooms:item/level0/ambient_flashlight_3d"
            },
            "elements": elements,
            "display": DISPLAY_3D
        }
        name = "ambient_flashlight_on.json" if lit else "ambient_flashlight.json"
        out = os.path.join(MODEL_DIR, name)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)
        print("Wrote", out)


if __name__ == "__main__":
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    make_standard_texture()
    make_ambient_texture()
    make_standard_models()
    make_ambient_models()
