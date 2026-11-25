"""The material library: every surface the items are made of, at 64 pixels, worn the way things in the Backrooms are.

Dull colours; rust, chipped paint, grime at the edges, scratches. Only a few materials give light (a tube, a screen, a
cracked crystal), and dimly. Photo grain from ambientCG CC0 materials (Rust004, Metal032, Metal049A, Wood051,
Fabric028, Concrete034).
"""
import random

import numpy as np

from lib import S, bands, col, define, flat, grime, lum, noise, photo, rgba, scratches, spots, tint

# ---------------------------------------------------------------- families


def metal(colour, seed, rust=0.0, paint=None, chips=0.0, scratch=10, smooth=0.45, f0=230, crop=240, dirt=0.3):
    base = tint(photo("Metal032", crop, seed), colour, 0.25)
    if rust:
        base = np.where((noise(seed + 1, 2.5) > 1 - rust)[..., None], tint(photo("Rust004", 220, seed + 2), (108, 66, 40), 0.45), base)
    if paint is not None:
        painted = flat(paint, seed + 3, 0.1, 1.2)
        base = np.where((noise(seed + 4, 1.6) > chips)[..., None], painted, base)
    out = scratches(grime(base, seed + 5, dirt, 0.2), seed + 6, scratch, tuple(np.clip(col(colour) * 1.35 + 0.1, 0, 1)))
    return rgba(out), (smooth, f0 if paint is None else 40, 0.2)


def wood(colour, seed, crop=300, dirt=0.4, knots=0):
    base = tint(photo("Wood051", crop, seed), colour, 0.35)
    out = grime(base, seed + 1, dirt, 0.25)
    rnd = random.Random(seed)
    for _ in range(knots):
        x, y = rnd.randint(4, S - 5), rnd.randint(4, S - 5)
        out[y - 1:y + 2, x - 1:x + 2] *= 0.55
    return rgba(out), (0.16, 20, 0.5)


def cloth(colour, seed, crop=170, dirt=0.3, stains=0.0):
    base = tint(photo("Fabric028", crop, seed), colour, 0.12)
    if stains:
        base = np.where((noise(seed + 1, 5) > 1 - stains)[..., None], base * 0.7, base)
    return rgba(grime(base, seed + 2, dirt, 0.25)), (0.06, 20, 0.7)


def leather(colour, seed, dirt=0.35):
    base = tint(photo("Fabric028", 90, seed), colour, 0.05)
    base *= (0.85 + 0.25 * noise(seed + 1, 0.8))[..., None]
    base = spots(base, seed + 2, np.clip(col(colour) * 1.3, 0, 1), 0.82, 1.2, 0.5)
    return rgba(grime(base, seed + 3, dirt, 0.3)), (0.3, 30, 0.3)


def wrap(colour, seed, period=6, dark=0.6):
    """A grip wound with cord or tape: diagonal turns."""
    y, x = np.mgrid[0:S, 0:S]
    turn = ((x + y) % period) < period * 0.75
    rgb = np.where(turn[..., None], col(colour), col(colour) * dark)
    rgb = rgb * (0.88 + 0.2 * noise(seed, 1.0))[..., None]
    return rgba(grime(rgb, seed + 1, 0.3, 0.2)), (0.2, 20, 0.5)


def plastic(colour, seed, smooth=0.4, dirt=0.3, scratch=6):
    base = flat(colour, seed, 0.05, 3)
    return rgba(scratches(grime(base, seed + 1, dirt, 0.25), seed + 2, scratch, tuple(np.clip(col(colour) * 1.25, 0, 1)))), (smooth, 40, 0.0)


def bone(colour, seed, cracks=8):
    base = flat(colour, seed, 0.1, 2.5)
    base = spots(base, seed + 1, np.clip(col(colour) * 0.7, 0, 1), 0.75, 3, 0.5)
    out = scratches(grime(base, seed + 2, 0.35, 0.25), seed + 3, cracks, tuple(np.clip(col(colour) * 0.45, 0, 1)), 8)
    return rgba(out), (0.25, 30, 0.3)


def flesh(colour, seed, veins=12):
    base = flat(colour, seed, 0.18, 1.6)
    base = spots(base, seed + 1, np.clip(col(colour) * 0.55, 0, 1), 0.7, 2, 0.6)
    out = scratches(base, seed + 2, veins, tuple(np.clip(col(colour) * 0.4, 0, 1)), 14)
    return rgba(grime(out, seed + 3, 0.3, 0.2)), (0.55, 30, 0.1)


def crystal(colour, seed, glow=0.0):
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    facets = (np.floor(x * 4 + y * 3) + np.floor(x * 3 - y * 4)) % 3 / 2
    rgb = col(colour) * (0.55 + 0.35 * facets[..., None] + 0.15 * noise(seed, 2)[..., None])
    rgb = scratches(rgb, seed + 1, 8, tuple(np.clip(col(colour) * 1.5, 0, 1)), 12)
    return rgba(np.clip(rgb, 0, 1)), (0.8, 40, 0.0, glow) if glow else (0.8, 40, 0.0)


def glass(colour, seed, alpha=0.5, liquid=None, level=0.35):
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    rgb = np.ones((S, S, 3)) * col(colour) * (0.95 + 0.05 * noise(seed, 2))[..., None]
    a = np.full((S, S), alpha, np.float32)
    if liquid is not None:
        wet = y > level + 0.02 * np.sin(x * 9)
        rgb = np.where(wet[..., None], col(liquid) * (0.9 + 0.1 * noise(seed + 1, 3))[..., None], rgb)
        a = np.where(wet, 0.92, a)
    streak = np.abs(x - 0.25) < 0.04
    rgb[streak] = rgb[streak] * 0.6 + 0.4
    return rgba(grime(rgb, seed + 2, 0.25, 0.1), a), (0.85, 40, 0.0)


def paper(colour, seed, writing=0, stains=0.3):
    base = flat(colour, seed, 0.06, 2)
    rnd = random.Random(seed)
    for row in range(writing):
        yy = 14 + row * 9
        xx = 8
        while xx < 54:
            w = rnd.randint(2, 6)
            base[yy:yy + 2, xx:xx + w] = (0.26, 0.22, 0.18)
            xx += w + rnd.randint(1, 3)
    if stains:
        base = np.where((noise(seed + 1, 6) > 1 - stains)[..., None], base * np.array([0.86, 0.80, 0.68]), base)
    return rgba(grime(base, seed + 2, 0.3, 0.3)), (0.08, 20, 0.6)


def stone(colour, seed, crop=260):
    base = tint(photo("Concrete034", crop, seed), colour, 0.2)
    return rgba(grime(base, seed + 1, 0.3, 0.2)), (0.15, 30, 0.5)


def glow_panel(colour, seed, strength=0.35):
    """Something that gives a little light of its own: a tube, an indicator, a crack."""
    rgb = col(colour) * (0.85 + 0.15 * noise(seed, 3))[..., None]
    return rgba(rgb), (0.6, 40, 0.0, strength)


# ---------------------------------------------------------------- named materials

M = {
    # Metals.
    "steel": lambda: metal((130, 130, 126), 100),
    "steel_dark": lambda: metal((78, 80, 82), 101, smooth=0.5),
    "steel_worn": lambda: metal((126, 124, 118), 102, rust=0.18),
    "steel_rusty": lambda: metal((112, 100, 88), 103, rust=0.55),
    "blade": lambda: metal((138, 140, 138), 104, scratch=18, smooth=0.6, dirt=0.32),
    "blade_dark": lambda: metal((96, 98, 100), 105, scratch=14, smooth=0.55, dirt=0.2),
    "blade_edge": lambda: metal((170, 172, 168), 106, scratch=6, smooth=0.75, dirt=0.15, crop=120),
    "blade_acid": lambda: metal((118, 132, 104), 107, rust=0.35, scratch=10),
    "blade_blood": lambda: metal((112, 92, 90), 108, rust=0.25, scratch=12),
    "blade_void": lambda: metal((44, 42, 50), 109, scratch=8, smooth=0.6),
    "iron_black": lambda: metal((58, 56, 54), 110, rust=0.12, smooth=0.35),
    "iron_crimson": lambda: metal((92, 46, 42), 111, rust=0.3, smooth=0.35),
    "brass": lambda: metal((168, 138, 78), 112, smooth=0.55, f0=231, dirt=0.35),
    "bronze": lambda: metal((132, 96, 62), 113, smooth=0.45, f0=232, rust=0.1),
    "copper": lambda: metal((172, 104, 66), 114, smooth=0.55, f0=234),
    "gilt": lambda: metal((176, 148, 86), 115, smooth=0.6, f0=231, dirt=0.4),
    "galv_steel": lambda: metal((128, 128, 122), 5, rust=0.12),
    "bare_steel": lambda: metal((150, 148, 142), 10, scratch=14),
    "rust_pipe": lambda: metal((112, 72, 46), 1, rust=0.85, scratch=4),
    "red_paint": lambda: metal((98, 96, 92), 13, rust=0.25, paint=(118, 34, 28), chips=0.62, scratch=16),
    "chain": lambda: (rgba(np.where(((np.mgrid[0:S, 0:S][0] // 4) % 2 == 0)[..., None],
                                    metal((90, 88, 84), 116)[0][..., :3], metal((60, 58, 56), 117)[0][..., :3] * 0.6)), (0.45, 230, 0.1)),
    "nail_heads": lambda: metal((160, 156, 148), 118, rust=0.3, scratch=20),
    "barbed": lambda: metal((96, 90, 84), 119, rust=0.5, scratch=6),
    # Woods.
    "old_wood": lambda: wood((112, 86, 60), 23),
    "scrap_wood": lambda: wood((96, 76, 56), 26, crop=220, dirt=0.45, knots=3),
    "bat_wood": lambda: wood((150, 118, 80), 200, dirt=0.35),
    "dark_wood": lambda: wood((70, 52, 38), 201),
    "peach_wood": lambda: wood((156, 104, 78), 202, dirt=0.3),
    "lacquer_red": lambda: (rgba(scratches(grime(flat((112, 34, 30), 203, 0.08, 2), 204, 0.35, 0.3), 205, 10, (0.5, 0.3, 0.25))), (0.5, 40, 0.1)),
    "lacquer_black": lambda: (rgba(scratches(grime(flat((32, 28, 28), 206, 0.08, 2), 207, 0.3, 0.3), 208, 10, (0.35, 0.3, 0.28))), (0.5, 40, 0.1)),
    # Grips and cloth.
    "leather_brown": lambda: leather((96, 66, 44), 210),
    "leather_black": lambda: leather((40, 36, 34), 211),
    "leather_red": lambda: leather((96, 36, 32), 212),
    "cord_black": lambda: wrap((44, 40, 38), 213),
    "cord_red": lambda: wrap((120, 40, 34), 214),
    "cord_blue": lambda: wrap((44, 56, 84), 215),
    "tape_black": lambda: (rgba(grime(bands((40, 40, 42), (26, 26, 28), 6), 20, 0.2, 0.1)), (0.5, 20, 0.0)),
    "duct_tape": lambda: cloth((150, 152, 150), 21, 160, 0.35),
    "tassel_red": lambda: (rgba(grime(bands((150, 40, 34), (110, 26, 24), 3, axis=1), 216, 0.3, 0.2)), (0.1, 20, 0.7)),
    "velvet_wine": lambda: cloth((84, 28, 36), 217, 120, 0.3),
    "rag_grey": lambda: cloth((110, 108, 100), 218, 180, 0.45, 0.3),
    # Organic.
    "bone": lambda: bone((198, 186, 158), 220),
    "bone_dark": lambda: bone((140, 124, 98), 221),
    "fang": lambda: bone((214, 204, 176), 222, 5),
    "flesh": lambda: flesh((128, 78, 70), 223),
    "flesh_dark": lambda: flesh((84, 46, 44), 224),
    "hide_hound": lambda: cloth((60, 54, 48), 225, 90, 0.4),
    # Anomalous: low glow, dull colours.
    "crystal_violet": lambda: crystal((96, 74, 120), 230, 0.25),
    "crystal_red": lambda: crystal((128, 52, 50), 231, 0.25),
    "crystal_blue": lambda: crystal((74, 104, 128), 232, 0.25),
    "crystal_amber": lambda: crystal((150, 112, 60), 233, 0.25),
    "void": lambda: (rgba(scratches(flat((22, 20, 28), 234, 0.15, 3), 235, 10, (0.26, 0.2, 0.34), 14)), (0.7, 40, 0.0)),
    "void_crack": lambda: glow_panel((112, 84, 150), 236, 0.45),
    "ember": lambda: glow_panel((180, 92, 46), 237, 0.45),
    "spark_blue": lambda: glow_panel((124, 152, 176), 238, 0.4),
    # The Backrooms.
    "wallpaper_strip": lambda: _wallpaper(),
    "fluoro_tube": lambda: _tube(),
    "lamp_metal": lambda: metal((176, 172, 160), 33, scratch=10, dirt=0.45),
    "wire_black": lambda: (rgba(flat((26, 26, 26), 36, 0.15, 0.8)), (0.4, 20, 0.0)),
    "copper_winding": lambda: _winding(),
    "copper_bright": lambda: (rgba(flat((210, 128, 76), 40, 0.12, 1)), (0.75, 234, 0.0)),
    "spool": lambda: wood((122, 98, 70), 41, crop=400, dirt=0.35),
    "glass_milk": lambda: glass((176, 184, 176), 43, 0.55, (226, 214, 180), 0.3),
    "glass_clear": lambda: glass((170, 182, 176), 44, 0.4),
    "glass_brown": lambda: glass((110, 78, 44), 45, 0.75, (120, 84, 40), 0.25),
    "cork": lambda: (rgba(grime(spots(flat((158, 122, 82), 46, 0.15, 0.7), 47, (0.34, 0.26, 0.18), 0.8, 0.5), 48, 0.2)), (0.1, 20, 0.7)),
    "label": lambda: paper((208, 194, 152), 49, writing=3),
    "twine": lambda: (rgba(bands((150, 126, 88), (118, 96, 64), 3, axis=1)), (0.1, 20, 0.6)),
    "concrete": lambda: stone((130, 128, 122), 240),
    # Guns and ammunition.
    "gun_black": lambda: metal((54, 56, 58), 400, scratch=14, smooth=0.4, dirt=0.25),
    "gun_polymer": lambda: plastic((40, 40, 40), 401, 0.3, 0.25, 10),
    "gun_wood": lambda: wood((92, 60, 40), 402, crop=260, dirt=0.35),
    "yellow_paint": lambda: metal((96, 94, 90), 403, rust=0.15, paint=(170, 136, 46), chips=0.66, scratch=14),
    "olive_paint": lambda: metal((90, 90, 86), 404, rust=0.1, paint=(84, 86, 60), chips=0.72, scratch=10),
    "rail": lambda: (rgba(grime(bands((60, 62, 64), (30, 31, 32), 4, axis=0, duty=0.6), 405, 0.2, 0.1)), (0.45, 230, 0.1)),
    "vented": lambda: _vented(),
    "lens": lambda: (rgba(np.clip(flat((34, 44, 52), 406, 0.05, 3) + (np.clip(1 - np.hypot(*(np.mgrid[0:S, 0:S] / (S - 1) - 0.3)) / 0.35, 0, 1) * 0.12)[..., None], 0, 1)), (0.95, 40, 0.0)),
    "red_dot": lambda: glow_panel((150, 40, 36), 407, 0.35),
    "brass_case": lambda: metal((176, 140, 70), 408, smooth=0.6, f0=231, dirt=0.25, scratch=6),
    "copper_tip": lambda: metal((170, 104, 64), 409, smooth=0.55, f0=234, dirt=0.2),
    "shell_red": lambda: plastic((132, 40, 34), 410, 0.35, 0.3, 4),
    "shell_green": lambda: plastic((84, 110, 62), 411, 0.35, 0.3, 4),
    "tip_phosphor": lambda: glow_panel((150, 176, 120), 412, 0.25),
    "cardboard": lambda: (rgba(grime(scratches(flat((150, 120, 84), 413, 0.08, 2), 414, 6, (0.42, 0.33, 0.22)), 415, 0.35, 0.35)), (0.08, 20, 0.6)),
    "ruler": lambda: _ruler(),
    "pen_blue": lambda: plastic((48, 70, 132), 416, 0.45, 0.2, 4),
    "pen_white": lambda: plastic((210, 206, 196), 417, 0.4, 0.3, 4),
    "tank_red": lambda: metal((96, 92, 88), 418, rust=0.3, paint=(126, 38, 32), chips=0.6, scratch=12),
    "rubber_black": lambda: plastic((30, 30, 30), 419, 0.15, 0.2, 2),
    "lead": lambda: metal((96, 96, 100), 420, smooth=0.3, dirt=0.3, scratch=4),
    # Props (block textures).
    "office_fabric": lambda: cloth((58, 63, 74), 53, 180, 0.25, 0.28),
    "dark_plastic": lambda: plastic((42, 42, 44), 57, 0.45, 0.2, 8),
    "chrome_worn": lambda: metal((160, 160, 156), 60, rust=0.2, smooth=0.7),
    "beige_plastic": lambda: plastic((198, 188, 152), 63, 0.35, 0.35, 6),
    "crt_screen": lambda: _screen(),
    "keyboard": lambda: _keyboard(),
    "cable": lambda: (rgba(flat((36, 36, 36), 68, 0.1, 0.8)), (0.35, 20, 0.0)),
}


def _vented():
    base = metal((52, 54, 56), 430, scratch=8, smooth=0.4, dirt=0.25)[0][..., :3].copy()
    for y in range(6, S - 4, 12):
        for x in range(4, S - 4, 10):
            base[y:y + 6, x:x + 6] *= 0.2
    return rgba(base), (0.4, 230, 0.1)


def _ruler():
    base = wood((176, 150, 104), 431, crop=200, dirt=0.25)[0][..., :3].copy()
    for x in range(2, S, 4):
        base[0:6 if x % 16 == 2 else 3, x] = (0.18, 0.15, 0.12)
    return rgba(base), (0.25, 20, 0.4)


def _wallpaper():
    """A torn strip of Level 0's own wallpaper (from the realistic pack), yellowed and soiled."""
    from PIL import Image

    from lib import ROOT

    src = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level0/wallpaper.png"
    image = np.asarray(Image.open(src).convert("RGB").resize((S, S), Image.LANCZOS), np.float32) / 255
    out = grime(image * 0.9, 28, 0.4, 0.2)
    y = np.mgrid[0:S, 0:S][0]
    out[(noise(29, 1.0) > 0.86) & ((y < 6) | (y > S - 7))] = (0.55, 0.48, 0.33)
    return rgba(out), (0.1, 25, 0.5)


def _tube():
    y = np.mgrid[0:S, 0:S][0] / (S - 1)
    v = 0.86 - 0.10 * noise(30, 4)
    rgb = np.dstack([v, v * 0.97, v * 0.86])
    rgb[(y < 0.08) | (y > 0.92)] *= 0.45
    rgb *= (1 - 0.25 * noise(31, 1.5) * noise(32, 6))[..., None]
    return rgba(rgb), (0.75, 40, 0.0, 0.55)


def _winding():
    y = np.arange(S)[:, None] * np.ones((1, S))
    band = 0.75 + 0.25 * np.cos(y / 2.0 * np.pi)
    copper = np.array([176, 100, 58]) / 255 * band[..., None]
    copper = copper * (0.85 + 0.25 * lum(photo("Metal032", 160, 37))[..., None])
    out = np.where((noise(38, 3) > 0.7)[..., None], copper * 0.55 + np.array([96, 120, 92]) / 255 * 0.45, copper)
    return rgba(grime(out, 39, 0.3, 0.1)), (0.6, 234, 0.0)


def _screen():
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    v = 0.12 + 0.05 * (1 - ((x - 0.5) ** 2 + (y - 0.5) ** 2) * 2)
    rgb = np.dstack([v * 0.85, v * 1.05, v * 0.92])
    rgb[(np.mgrid[0:S, 0:S][0] % 3) == 0] *= 0.82
    rgb[14:16, 10:40] += 0.06
    rgb[20:22, 10:30] += 0.05
    rgb[26:29, 10:12] += 0.10
    rgb += (np.clip(1 - np.hypot(x - 0.3, y - 0.25) / 0.25, 0, 1) * 0.08)[..., None]
    rgb[noise(66, 0.6) > 0.86] += 0.05
    return rgba(rgb), (0.9, 40, 0.0, 0.18)


def _keyboard():
    rgb = np.ones((S, S, 3)) * np.array([0.56, 0.53, 0.44])
    for ky in range(4, S - 4, 8):
        for kx in range(2, S - 2, 6):
            shade = 0.80 + 0.12 * random.Random(kx * 97 + ky).random()
            rgb[ky:ky + 6, kx:kx + 5] = np.array([0.80, 0.76, 0.64]) * shade
            rgb[ky + 5, kx:kx + 5] *= 0.75
    return rgba(grime(rgb, 67, 0.4, 0.2)), (0.3, 40, 0.0)


for _name, _maker in M.items():
    define(_name, _maker)
