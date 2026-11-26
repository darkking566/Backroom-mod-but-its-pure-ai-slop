"""Draws every Level 4 ("Abandoned Office") texture at 128x128 (or cutout/animated) and writes
Level 4 block models, blockstates, item models, and loot tables.

    py tools/make_level4.py

Follows docs/look/STYLE_GUIDE.md:
- Charcoal/blue-grey speckled commercial carpet (plus 4 desk/chair dent variants and damp carpet)
- Matte cool off-white drywall with white baseboards (+ 9 subtle wear variants)
- 4-tile acoustic ceiling grid + HVAC vents + 4-state louvered fluorescent troffers (lit/dim/flicker/broken)
- Safe blacked-out windows vs bright Entity 2 trap windows vs dark rainy exterior glass
- Orange-wood fire doors, brass/steel Level 3 elevators, Level 5/6/71/4.3/11/2/13 exits
- Interactive water coolers (0/1/2 bottles), almond water fountains (0/1/2 bottles), vending machines,
  M.E.G. Omega Base, Amor Incrementum fungal planters, T.B.D. curio shelves, breakers, photocopiers, desk phones.
"""

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_level1 as l1  # noqa: E402
import make_level2 as l2  # noqa: E402
import make_textures as mt  # noqa: E402
from make_textures import SIZE, X, Y, box, clouds, flat, inside, metal, noise, paint, step, tint  # noqa: E402

ASSETS = mt.ASSETS
TEXTURES = ASSETS / "textures/block/level4"
MODELS = ASSETS / "models/block/level4"
NS = "noclip_backrooms:block/level4/"
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def box_el(x0, y0, z0, x1, y1, z1, tex, front=None, front_tex=None):
    faces = {f: {"uv": [0, 0, 16, 16], "texture": front_tex if f == front and front_tex else tex}
             for f in ("north", "south", "east", "west", "up", "down")}
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


# ---------------------------------------------------------------------------- Textures

def carpet(seed, dent=0, damp=False):
    """Dark blue-grey commercial short-pile carpet matching the Wikidot Level 4 photo."""
    m = flat((74, 80, 88), smooth=0.05, f0=8, porosity=210)
    weave = noise(seed, 64) * 0.05 + noise(seed + 1, 128) * 0.07
    m.color *= (1 + weave)[..., None]
    m.height += noise(seed + 2, 128) * 0.35
    # Subtle fine speckle flecks
    speck = step(0.75, 0.95, noise(seed + 3, 128))
    tint(m, speck * 0.35, (0.82, 0.85, 0.90))
    # Subtle wear variation
    patch = step(0.1, 0.7, clouds(seed + 4, 3)) * 0.14
    tint(m, patch, (0.90, 0.92, 0.94))
    if dent > 0:
        # Desk leg / chair caster indentations on carpet (4 patterns)
        mask = np.zeros((SIZE, SIZE))
        pts = {
            1: [(24, 24), (104, 24), (24, 104), (104, 104)],
            2: [(40, 48), (88, 48), (52, 88), (76, 88)],
            3: [(30, 36), (98, 36), (64, 96)],
            4: [(64, 64), (42, 42), (86, 42), (42, 86), (86, 86)],
        }[dent]
        for cx, cy in pts:
            r = 10 if dent == 4 and (cx, cy) == (64, 64) else 6
            mask = np.maximum(mask, 1 - step(r - 2, r + 2, np.hypot(X - cx, Y - cy)))
        tint(m, mask * 0.6, (0.76, 0.78, 0.82))
        paint(m, mask, height=-1.4, smooth=0.03)
    if damp:
        puddle = step(0.05, 0.45, clouds(seed + 9, 4)) * inside()
        tint(m, puddle * 0.75, (0.62, 0.65, 0.70))
        paint(m, puddle, height=-0.4, smooth=0.82, f0=18, porosity=10)
    return m


def drywall(seed, baseboard=True, variant=0):
    """Matte cool off-white office drywall with a crisp pale baseboard."""
    m = flat((216, 219, 215), smooth=0.14, f0=10, porosity=45)
    grain = noise(seed, 64) * 0.018 + clouds(seed + 1, 4) * 0.025
    m.color *= (1 + grain)[..., None]
    m.height += noise(seed + 2, 128) * 0.15
    if baseboard:
        # Clean light grey-white baseboard at bottom (bottom 14px in UV -> Y >= 114)
        bb = step(112, 114, Y)
        paint(m, bb, (232, 234, 232), height=0.8, smooth=0.35, porosity=15)
        groove = box(-2, 112, 130, 114, 0.6)
        paint(m, groove, (172, 176, 174), height=-0.4)
    # Unified 1..9 subtle wear variants (kept faint so the office still looks clean overall)
    edge = inside()
    if variant == 1:  # water stain
        w = step(0.22, 0.65, clouds(seed + 10, 4)) * edge * (1 - step(70, 110, Y))
        tint(m, w * 0.6, (0.86, 0.84, 0.78))
    elif variant == 2:  # short drip
        for cx in (34, 88):
            d = (1 - step(1, 2.5, np.abs(X - cx))) * step(18, 24, Y) * (1 - step(46, 56, Y))
            tint(m, d * 0.5, (0.80, 0.79, 0.75))
    elif variant == 3:  # long streak
        s = (1 - step(1.5, 3.5, np.abs(X - 62 + clouds(seed + 11, 3) * 4))) * step(8, 16, Y) * (1 - step(98, 110, Y))
        tint(m, s * 0.45, (0.80, 0.80, 0.78))
    elif variant == 4:  # hand smudge near light-switch height
        sm = step(0.25, 0.6, clouds(seed + 12, 6)) * box(42, 48, 86, 86, 8)
        tint(m, sm * 0.55, (0.82, 0.82, 0.80))
    elif variant == 5:  # spackle patch
        p = box(36, 38, 76, 70, 2)
        paint(m, p, (228, 229, 225), height=0.35, smooth=0.08)
    elif variant == 6:  # paint blister
        b = (1 - step(8, 14, np.hypot(X - 66, Y - 54))) * edge
        paint(m, b, (224, 226, 222), height=1.1, smooth=0.22)
    elif variant == 7:  # small drywall dent/hole
        h = 1 - step(4, 7, np.hypot(X - 58, Y - 64))
        paint(m, h, (118, 120, 118), height=-1.8, smooth=0.04, porosity=120)
    elif variant == 8:  # corner damp mould
        mu = step(0.2, 0.65, clouds(seed + 15, 5)) * step(88, 112, Y) * edge
        tint(m, mu * 0.7, (0.74, 0.76, 0.70))
    elif variant == 9:  # scuff speckles
        sp = step(0.75, 0.92, noise(seed + 16, 64)) * box(20, 72, 108, 110, 4)
        tint(m, sp * 0.65, (0.76, 0.77, 0.78))
    return m


def pillar(seed):
    """Office structural column matching drywall with crisp corner bevels and baseboard."""
    m = drywall(seed, baseboard=True, variant=0)
    edge = np.maximum(1 - step(1, 3, X), 1 - step(1, 3, SIZE - 1 - X))
    tint(m, edge * 0.45, (0.86, 0.88, 0.86))
    paint(m, edge, height=0.4)
    return m


def radiator_wall(seed):
    """Drywall with a white/light-grey office convector radiator under windows."""
    m = drywall(seed, baseboard=True, variant=0)
    body = box(14, 22, 114, 102, 1.5)
    paint(m, body, (228, 231, 232), height=1.6, smooth=0.45, f0=18)
    # Fin slats
    slat = ((X % 6) < 2) * box(18, 28, 110, 96, 1)
    paint(m, slat, (186, 190, 194), height=0.8, smooth=0.35)
    top_lip = box(12, 20, 116, 26, 0.8)
    paint(m, top_lip, (238, 240, 240), height=2.0, smooth=0.5)
    return m


def acoustic_ceiling(seed):
    """4-tile suspended office acoustic ceiling grid with metal T-bars (STYLE_GUIDE 64px tiles on 128px block)."""
    m = flat((194, 198, 195), smooth=0.10, f0=10, porosity=90)
    # Acoustic stipple pores
    stip = step(0.55, 0.9, noise(seed, 128))
    tint(m, stip * 0.35, (0.82, 0.84, 0.82))
    m.height -= stip * 0.4
    # T-bar metal grid (2x2 tiles per block -> seams at 0, 64)
    gx = np.minimum(X % 64, 64 - X % 64)
    gy = np.minimum(Y % 64, 64 - Y % 64)
    grid = np.maximum(1 - step(1.2, 2.2, gx), 1 - step(1.2, 2.2, gy))
    paint(m, grid, (218, 222, 220), height=0.7, smooth=0.45, f0=40, porosity=10)
    patch = step(0.2, 0.7, clouds(seed + 3, 4)) * 0.12
    tint(m, patch, (0.90, 0.89, 0.86))
    return m


def ceiling_vent(seed):
    """HVAC square ceiling diffuser set inside acoustic ceiling."""
    m = acoustic_ceiling(seed)
    rim = box(20, 20, 108, 108, 1)
    paint(m, rim, (226, 229, 228), height=1.2, smooth=0.5, f0=30)
    # Concentric louver rings
    dist = np.maximum(np.abs(X - 64), np.abs(Y - 64))
    rings = ((dist % 10) < 4) * box(26, 26, 102, 102, 0.5)
    paint(m, rings, (66, 70, 74), height=-1.2, smooth=0.2)
    return m


def troffer(kind):
    """Louvered office fluorescent troffer in 4 states (lit, dim, flicker, broken)."""
    m = flat((196, 200, 198), smooth=0.55, f0=180, porosity=10)
    # Louver baffle grid
    parabolic = ((Y % 21) < 3) | ((X % 32) < 2)
    if kind == "lit":
        m = flat((228, 246, 236), smooth=0.75, f0=16, porosity=0)
        m.glow[:] = 0.88
    elif kind == "dim":
        m = flat((226, 206, 152), smooth=0.65, f0=16, porosity=0)
        ends = np.maximum(1 - step(8, 28, X), 1 - step(8, 28, SIZE - X))
        tint(m, ends * 0.55, (0.42, 0.38, 0.32))
        m.glow[:] = 0.42 * (1 - ends * 0.5)
    elif kind == "flicker":
        m = flat((208, 226, 214), smooth=0.7, f0=16, porosity=0)
        m.glow[:] = 0.55
    else:
        m = flat((76, 82, 86), smooth=0.35, f0=120, porosity=15)
    paint(m, parabolic * 1.0, (148, 154, 158) if kind != "broken" else (58, 62, 66), height=0.8, smooth=0.5, f0=180, glow=0.0)
    frame = np.maximum(1 - step(2, 4, np.minimum(X, SIZE - X)), 1 - step(2, 4, np.minimum(Y, SIZE - Y)))
    paint(m, frame, (216, 220, 218), height=1.2, smooth=0.45, glow=0.0)
    return m


def window_tex(kind):
    """Office window textures: blacked-out (safe), trap (Entity 2), rain (atrium)."""
    if kind == "blacked":
        m = flat((20, 22, 26), smooth=0.72, f0=22, porosity=5)
        streak = noise(401, 16) * 0.12 + clouds(402, 4) * 0.10
        m.color *= (1 + streak)[..., None]
        # Blind / frame border
        frame = np.maximum(1 - step(6, 8, np.minimum(X, SIZE - X)), 1 - step(6, 8, np.minimum(Y, SIZE - Y)))
        paint(m, frame, (194, 198, 196), height=1.4, smooth=0.35, f0=20)
        return m
    if kind == "trap":
        m = flat((248, 250, 252), smooth=0.82, f0=16, porosity=0)
        m.glow[:] = 0.85
        # Soft overexposed blind edges
        blinds = box(8, 8, 22, 120, 1) + box(106, 8, 120, 120, 1)
        paint(m, np.clip(blinds, 0, 1), (206, 178, 148), height=0.6, smooth=0.25, glow=0.1)
        # Eerie dark Entity 2 silhouette behind the glare: head, shoulders, raised beckoning hand
        head = 1 - step(10, 13, np.hypot((X - 64) * 1.15, Y - 48))
        torso = (1 - step(14, 19, np.abs(X - 64))) * step(58, 66, Y) * (1 - step(112, 120, Y))
        arm = box(74, 42, 86, 82, 2.5)
        palm = 1 - step(6, 9, np.hypot(X - 81, Y - 38))
        sil = np.clip(np.maximum.reduce([head, torso, arm, palm]), 0, 1)
        paint(m, sil, (34, 38, 46), smooth=0.75, glow=0.05)
        frame = np.maximum(1 - step(5, 7, np.minimum(X, SIZE - X)), 1 - step(5, 7, np.minimum(Y, SIZE - Y)))
        paint(m, frame, (96, 84, 76), height=1.5, smooth=0.4, glow=0.0)
        return m
    # rain (also an un-blacked Entity 2 trap with a faint ghostly silhouette/handprint)
    m = flat((34, 44, 54), smooth=0.88, f0=24, porosity=0)
    m.glow[:] = 0.08
    rain = step(0.6, 0.92, np.sin((X * 0.4 + Y * 0.18 + noise(405, 32) * 3)) * 0.5 + 0.5)
    tint(m, rain * 0.35, (1.35, 1.45, 1.55))
    ghost_head = 1 - step(9, 14, np.hypot((X - 58) * 1.1, Y - 56))
    ghost_hand = 1 - step(5, 8, np.hypot(X - 78, Y - 66))
    ghost = np.clip(np.maximum(ghost_head * 0.45, ghost_hand * 0.65), 0, 1)
    paint(m, ghost, (132, 154, 174), smooth=0.85, glow=0.16)
    frame = np.maximum(1 - step(5, 7, np.minimum(X, SIZE - X)), 1 - step(5, 7, np.minimum(Y, SIZE - Y)))
    paint(m, frame, (78, 84, 92), height=1.5, smooth=0.45, f0=80, glow=0.0)
    return m


def fire_door():
    """Orange-wood office fire door with narrow vertical glass slit & dark kickplate (level4_ref.jpg)."""
    m = flat((168, 114, 58), smooth=0.34, f0=12, porosity=40)
    grain = noise(410, 64, 16) * 0.08
    m.color *= (1 + grain)[..., None]
    # Narrow slit window
    slit = box(48, 18, 70, 62, 0.8)
    paint(m, slit, (42, 48, 52), height=-0.6, smooth=0.88, f0=20)
    # Push plate / handle
    plate = box(92, 56, 104, 78, 0.5)
    paint(m, plate, (192, 196, 198), height=0.8, smooth=0.75, f0=220)
    # Dark kickplate at bottom
    kick = step(106, 108, Y)
    paint(m, kick, (38, 42, 46), height=0.5, smooth=0.4, f0=120)
    # White steel frame around edges
    frame = np.maximum(1 - step(4, 6, np.minimum(X, SIZE - X)), 1 - step(4, 6, Y))
    paint(m, frame, (220, 224, 222), height=1.2, smooth=0.4)
    return m


def exit_door(bg, stripe, label):
    """Generic clean 128px office exit door face with stenciled label."""
    m = flat(bg, smooth=0.42, f0=80, porosity=20)
    seam = np.maximum(1 - step(3, 5, np.minimum(X, SIZE - X)), 1 - step(3, 5, Y))
    paint(m, seam, (52, 56, 62), height=-0.8)
    band = box(10, 26, 118, 52, 0.5)
    paint(m, band, stripe, height=0.4, smooth=0.55)
    txt = l2.text_mask(label, 22 if len(label) <= 3 else 16, (64, 39), "arialbd.ttf") * band
    paint(m, txt, (244, 246, 248), glow=0.35)
    knob = 1 - step(3.5, 5, np.hypot(X - 102, Y - 84))
    paint(m, knob, (206, 208, 212), height=1.4, smooth=0.8, f0=220)
    return m


def elevator_door():
    m = metal((164, 168, 174), 0.65)
    center = 1 - step(1.5, 2.5, np.abs(X - 64))
    paint(m, center, (54, 58, 62), height=-1.5)
    frame = np.maximum(1 - step(5, 7, np.minimum(X, SIZE - X)), 1 - step(5, 7, Y))
    paint(m, frame, (176, 148, 88), height=1.2, smooth=0.65, f0=210)  # brushed brass frame
    panel = box(98, 50, 116, 80, 0.5)
    paint(m, panel, (48, 52, 56), height=0.6)
    btn = 1 - step(2, 3.5, np.hypot(X - 107, Y - 65))
    paint(m, btn, (240, 210, 120), glow=0.7)
    return m


def omega_wall(sign=False):
    m = flat((158, 164, 172), smooth=0.32, f0=90, porosity=20)
    rivet_x = np.minimum(X % 64, 64 - X % 64)
    seam = np.maximum(1 - step(0.8, 1.8, rivet_x), 1 - step(0.8, 1.8, np.minimum(Y, SIZE - Y)))
    paint(m, seam, (96, 102, 110), height=-0.9)
    stripe = box(-2, 46, 130, 58, 0.5)
    paint(m, stripe, (42, 84, 142), height=0.3, smooth=0.5)
    if sign:
        plate = box(14, 24, 114, 104, 1.2)
        paint(m, plate, (28, 54, 96), height=1.2, smooth=0.55)
        paint(m, l2.text_mask("M.E.G.", 16, (64, 48), "arialbd.ttf") * plate, (242, 236, 220), glow=0.2)
        paint(m, l2.text_mask("BASE OMEGA", 11, (64, 78), "arialbd.ttf") * plate, (220, 210, 150), glow=0.2)
    return m


def all_textures():
    t = {}
    for k in range(4):
        t[f"office_carpet{'' if k == 0 else '_' + str(k)}"] = carpet(4100 + k)
        t[f"office_carpet_dent_{k + 1}"] = carpet(4110 + k, dent=k + 1)
    t["office_damp_carpet"] = carpet(4120, damp=True)

    t["office_drywall"] = drywall(4200, baseboard=True, variant=0)
    for v in range(1, 10):
        t[f"office_drywall_{v}"] = drywall(4200 + v, baseboard=True, variant=v)
    t["office_drywall_upper"] = drywall(4210, baseboard=False, variant=0)
    for v in range(1, 10):
        t[f"office_drywall_upper_{v}"] = drywall(4210 + v, baseboard=False, variant=v)
    t["office_pillar"] = pillar(4220)
    t["office_pillar_upper"] = drywall(4220, baseboard=False, variant=0)
    edge = np.maximum(1 - step(1, 3, X), 1 - step(1, 3, SIZE - 1 - X))
    tint(t["office_pillar_upper"], edge * 0.45, (0.86, 0.88, 0.86))
    paint(t["office_pillar_upper"], edge, height=0.4)
    t["office_radiator_wall"] = radiator_wall(4230)

    for k in range(3):
        t[f"office_acoustic_ceiling{'' if k == 0 else '_' + str(k)}"] = acoustic_ceiling(4300 + k)
    t["office_ceiling_vent"] = ceiling_vent(4310)

    t["office_troffer_lit"] = troffer("lit")
    t["office_troffer_dim"] = troffer("dim")
    t["office_troffer_flicker_base"] = troffer("flicker")
    t["office_troffer_broken"] = troffer("broken")

    t["office_blacked_window"] = window_tex("blacked")
    t["office_trap_window"] = window_tex("trap")
    t["office_rain_window"] = window_tex("rain")

    t["office_fire_door"] = fire_door()
    t["office_exit_sign"] = mt.exit_sign()
    t["office_elevator"] = elevator_door()
    t["office_stairwell_5"] = exit_door((196, 198, 196), (168, 52, 46), "5")
    t["office_stairwell_6"] = exit_door((62, 64, 68), (40, 42, 46), "6")
    t["office_basement_71"] = exit_door((118, 120, 118), (74, 78, 82), "71")
    t["office_door_4p3"] = exit_door((242, 244, 246), (222, 226, 232), "4.3")
    t["office_door_11"] = exit_door((148, 156, 166), (72, 116, 168), "11")
    t["office_door_13"] = exit_door((172, 194, 206), (118, 164, 186), "13")
    t["office_hatch_2"] = exit_door((92, 96, 98), (186, 124, 42), "2")

    # Props / Factions
    t["cooler_body"] = flat((226, 228, 226), smooth=0.45, f0=18, porosity=10)
    paint(t["cooler_body"], box(28, 48, 100, 74, 0.5), (192, 198, 204), height=-0.5)
    paint(t["cooler_body"], box(38, 56, 52, 66, 0.4), (56, 120, 204), height=0.8)  # cold tap
    paint(t["cooler_body"], box(76, 56, 90, 66, 0.4), (204, 64, 56), height=0.8)  # almond/warm tap
    t["cooler_jug_full"] = flat((196, 224, 236), smooth=0.88, f0=22, porosity=0)
    t["cooler_jug_half"] = flat((196, 224, 236), smooth=0.88, f0=22, porosity=0)
    paint(t["cooler_jug_half"], 1 - step(64, 68, Y), (140, 152, 160), smooth=0.75)
    t["cooler_jug_empty"] = flat((140, 152, 160), smooth=0.75, f0=18, porosity=0)

    t["almond_fountain_basin"] = flat((208, 212, 214), smooth=0.55, f0=18, porosity=25)
    t["almond_liquid_full"] = flat((226, 224, 206), smooth=0.92, f0=22, porosity=0)
    t["almond_liquid_full"].glow[:] = 0.08
    t["almond_liquid_half"] = flat((212, 210, 196), smooth=0.88, f0=20, porosity=0)
    t["almond_liquid_empty"] = flat((148, 152, 156), smooth=0.35, f0=14, porosity=40)

    t["vending_front"] = flat((44, 74, 114), smooth=0.52, f0=36, porosity=5)
    paint(t["vending_front"], box(14, 14, 86, 86, 0.5), (218, 232, 238), height=-1.2, smooth=0.85, glow=0.32)
    paint(t["vending_front"], box(94, 40, 114, 72, 0.5), (34, 38, 42), height=0.6)
    paint(t["vending_front"], box(18, 94, 84, 114, 0.5), (26, 28, 32), height=-1.5)
    t["vending_side"] = flat((38, 62, 96), smooth=0.45, f0=30, porosity=5)

    t["omega_wall"] = omega_wall(False)
    t["omega_sign"] = omega_wall(True)
    t["omega_cot"] = flat((68, 94, 68), smooth=0.06, f0=8, porosity=200)
    paint(t["omega_cot"], box(4, 4, 124, 32, 1), (188, 192, 184), height=0.6, smooth=0.08)
    t["omega_locker"] = metal((96, 108, 118), 0.45)
    paint(t["omega_locker"], box(22, 16, 106, 26, 0.5), (42, 46, 52), height=-0.8)
    paint(t["omega_locker"], box(22, 32, 106, 42, 0.5), (42, 46, 52), height=-0.8)
    t["omega_barricade"] = l1.concrete((154, 156, 154), 4410, smooth=0.18, porosity=50)
    paint(t["omega_barricade"], box(-2, 92, 130, 110, 0.5), (42, 84, 142), height=0.2)

    t["amor_planter_side"] = flat((116, 94, 68), smooth=0.16, f0=10, porosity=80)
    t["amor_substrate"] = flat((162, 146, 84), smooth=0.08, f0=8, porosity=200)
    paint(t["amor_substrate"], step(0.1, 0.7, clouds(4420, 5)), (96, 88, 64), height=0.5)
    t["amor_substrate_l1"] = l1.concrete((122, 126, 128), 4421, smooth=0.12, porosity=120)
    paint(t["amor_substrate_l1"], step(0.18, 0.72, clouds(4422, 5)), (86, 118, 108), height=0.4)
    t["tbd_shelf"] = flat((122, 92, 62), smooth=0.24, f0=10, porosity=60)
    paint(t["tbd_shelf"], box(12, 18, 52, 54, 0.5), (178, 138, 72), height=0.8)
    paint(t["tbd_shelf"], box(68, 22, 112, 54, 0.5), (72, 126, 134), height=0.8)
    t["tbd_hoard_side"] = flat((112, 84, 56), smooth=0.18, f0=10, porosity=70)
    paint(t["tbd_hoard_side"], box(0, 0, 128, 14, 0.5), (84, 60, 38), height=0.8)
    paint(t["tbd_hoard_side"], box(0, 114, 128, 128, 0.5), (84, 60, 38), height=0.8)
    paint(t["tbd_hoard_side"], box(14, 58, 114, 70, 0.5), (92, 68, 44), height=0.5)
    t["tbd_hoard_front"] = flat((96, 72, 48), smooth=0.18, f0=10, porosity=70)
    paint(t["tbd_hoard_front"], box(8, 8, 120, 120, 0.5), (62, 48, 34), height=-0.8)
    paint(t["tbd_hoard_front"], box(18, 72, 42, 112, 0.5), (218, 212, 178), height=0.7, smooth=0.65)
    paint(t["tbd_hoard_front"], box(48, 76, 74, 112, 0.5), (168, 62, 54), height=0.7, smooth=0.5)
    paint(t["tbd_hoard_front"], box(80, 68, 108, 112, 0.5), (82, 132, 168), height=0.7, smooth=0.6)
    paint(t["tbd_hoard_front"], box(22, 22, 62, 58, 0.5), (146, 152, 158), height=0.7, smooth=0.5)
    paint(t["tbd_hoard_front"], box(70, 26, 108, 58, 0.5), (186, 156, 86), height=0.7, smooth=0.45)

    t["office_breaker"] = metal((144, 148, 152), 0.45)
    paint(t["office_breaker"], box(20, 16, 108, 112, 0.5), (118, 124, 128), height=0.8)
    paint(t["office_breaker"], 1 - step(3, 5, np.hypot(X - 92, Y - 34)), (64, 224, 96), glow=0.8)

    t["photocopier_body"] = flat((204, 202, 194), smooth=0.35, f0=14, porosity=15)
    paint(t["photocopier_body"], box(18, 20, 110, 42, 0.5), (56, 62, 68), height=0.6)
    paint(t["photocopier_body"], box(24, 52, 104, 68, 0.5), (178, 182, 178), height=-0.6)
    t["desk_wood"] = flat((138, 104, 70), smooth=0.28, f0=12, porosity=45)
    t["desk_phone"] = flat((44, 46, 52), smooth=0.45, f0=18, porosity=10)
    paint(t["desk_phone"], 1 - step(3, 4.5, np.hypot(X - 88, Y - 42)), (236, 48, 48), glow=0.85)
    return t


def save_cutout_glass():
    """Translucent interior office glass partition & cutout Amor fungus cluster."""
    mt.OUT = TEXTURES
    # Glass partition (translucent alpha=110 inside, crisp aluminum frame)
    img = Image.new("RGBA", (SIZE, SIZE), (186, 210, 218, 92))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, SIZE - 1, 5], fill=(198, 204, 208, 255))
    draw.rectangle([0, SIZE - 6, SIZE - 1, SIZE - 1], fill=(198, 204, 208, 255))
    draw.rectangle([0, 0, 5, SIZE - 1], fill=(198, 204, 208, 255))
    draw.rectangle([SIZE - 6, 0, SIZE - 1, SIZE - 1], fill=(198, 204, 208, 255))
    img.save(TEXTURES / "office_glass_wall.png", optimize=True)
    m = flat((186, 210, 218), smooth=0.90, f0=22, porosity=0)
    mt.save("office_glass_wall_tmp", m)
    for suf in ("_n", "_s"):
        (TEXTURES / f"office_glass_wall_tmp{suf}.png").replace(TEXTURES / f"office_glass_wall{suf}.png")
    (TEXTURES / "office_glass_wall_tmp.png").unlink(missing_ok=True)

    # Amor mushroom cluster (cutout RGBA)
    shroom = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(shroom)
    for sx, h, r in [(36, 48, 16), (64, 68, 22), (94, 42, 14)]:
        d.rectangle([sx - 4, SIZE - h, sx + 4, SIZE - 1], fill=(214, 198, 156, 255))
        d.ellipse([sx - r, SIZE - h - r // 2, sx + r, SIZE - h + r // 2], fill=(228, 168, 86, 255))
        d.ellipse([sx - r // 2, SIZE - h - r // 3, sx + r // 2, SIZE - h + r // 4], fill=(246, 206, 128, 255))
    shroom.save(TEXTURES / "amor_mushroom.png", optimize=True)
    ms = flat((228, 168, 86), smooth=0.35, f0=12, porosity=60)
    ms.glow[:] = 0.38
    mt.save("amor_mushroom_tmp", ms)
    for suf in ("_n", "_s"):
        (TEXTURES / f"amor_mushroom_tmp{suf}.png").replace(TEXTURES / f"amor_mushroom{suf}.png")
    (TEXTURES / "amor_mushroom_tmp.png").unlink(missing_ok=True)

    # Level 1 concrete wall-moss fungus (cutout RGBA)
    moss = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    dm = ImageDraw.Draw(moss)
    for sx, h, r in [(30, 36, 14), (56, 54, 20), (84, 46, 17), (104, 32, 12)]:
        dm.rectangle([sx - 3, SIZE - h, sx + 3, SIZE - 1], fill=(154, 178, 172, 255))
        dm.ellipse([sx - r, SIZE - h - r // 2, sx + r, SIZE - h + r // 2], fill=(92, 178, 158, 255))
        dm.ellipse([sx - r // 2, SIZE - h - r // 3, sx + r // 2, SIZE - h + r // 4], fill=(144, 218, 198, 255))
    moss.save(TEXTURES / "amor_mushroom_moss.png", optimize=True)
    mm = flat((92, 178, 158), smooth=0.30, f0=12, porosity=70)
    mm.glow[:] = 0.28
    mt.save("amor_mushroom_moss_tmp", mm)
    for suf in ("_n", "_s"):
        (TEXTURES / f"amor_mushroom_moss_tmp{suf}.png").replace(TEXTURES / f"amor_mushroom_moss{suf}.png")
    (TEXTURES / "amor_mushroom_moss_tmp.png").unlink(missing_ok=True)

    # Level 0 wallpaper veil fungus (cutout RGBA)
    veil = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    dv = ImageDraw.Draw(veil)
    for sx, h, r in [(38, 60, 18), (68, 78, 24), (96, 52, 16)]:
        dv.rectangle([sx - 3, SIZE - h, sx + 3, SIZE - 1], fill=(224, 214, 232, 255))
        dv.ellipse([sx - r, SIZE - h - r // 2, sx + r, SIZE - h + r // 2], fill=(176, 134, 208, 255))
        dv.ellipse([sx - r // 2, SIZE - h - r // 3, sx + r // 2, SIZE - h + r // 4], fill=(232, 212, 246, 255))
    veil.save(TEXTURES / "amor_mushroom_veil.png", optimize=True)
    mv = flat((176, 134, 208), smooth=0.40, f0=14, porosity=40)
    mv.glow[:] = 0.45
    mt.save("amor_mushroom_veil_tmp", mv)
    for suf in ("_n", "_s"):
        (TEXTURES / f"amor_mushroom_veil_tmp{suf}.png").replace(TEXTURES / f"amor_mushroom_veil{suf}.png")
    (TEXTURES / "amor_mushroom_veil_tmp.png").unlink(missing_ok=True)


def save_flicker_troffer():
    mt.OUT = TEXTURES
    lit = troffer("lit")
    dim = troffer("dim")
    off = troffer("broken")
    strip = Image.new("RGB", (SIZE, SIZE * 3))
    for i, m in enumerate((lit, dim, off)):
        strip.paste(Image.fromarray(np.clip(np.rint(m.color), 0, 255).astype("uint8"), "RGB"), (0, i * SIZE))
    strip.save(TEXTURES / "office_troffer_flicker.png", optimize=True)
    mt.save("office_troffer_flicker_tmp", lit)
    for suf in ("_n", "_s"):
        (TEXTURES / f"office_troffer_flicker_tmp{suf}.png").replace(TEXTURES / f"office_troffer_flicker{suf}.png")
    (TEXTURES / "office_troffer_flicker_tmp.png").unlink(missing_ok=True)
    write_json(TEXTURES / "office_troffer_flicker.png.mcmeta", {
        "animation": {"frametime": 3, "frames": [
            {"index": 0, "time": 24}, 2, 1, 0, {"index": 2, "time": 10}, 1, 0, 2, 0
        ]}
    })


# ---------------------------------------------------------------------------- Models & Blockstates

def cube(tex):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": NS + tex}}


def cube_col(side, end):
    return {"parent": "minecraft:block/cube_column", "textures": {"side": NS + side, "end": NS + end}}


def troffer_model(tex, emit=0):
    face = {"uv": [0, 0, 16, 16], "texture": "#face"}
    if emit:
        face["light_emission"] = emit
    side = {"uv": [0, 12, 16, 16], "texture": "#ceil"}
    return {
        "textures": {"face": NS + tex, "ceil": NS + "office_acoustic_ceiling", "particle": NS + "office_acoustic_ceiling"},
        "elements": [{
            "from": [0, 12, 0], "to": [16, 16, 16],
            "faces": {"down": face, "up": {"uv": [0, 0, 16, 16], "texture": "#ceil"},
                      "north": side, "south": side, "east": side, "west": side}
        }]
    }


def door_models():
    out = {}
    t = {"bottom": NS + "office_fire_door", "top": NS + "office_fire_door"}
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for opn in ("", "_open"):
                name = f"office_fire_door_{half}_{hinge}{opn}"
                out[name] = {"parent": f"minecraft:block/door_{half}_{hinge}{opn}", "textures": t}
    return out


def door_blockstate():
    # Matches standard Minecraft DoorBlock variants
    y_closed = {"east": 0, "south": 90, "west": 180, "north": 270}
    y_open_left = {"east": 90, "south": 180, "west": 270, "north": 0}
    y_open_right = {"east": 270, "south": 0, "west": 90, "north": 180}
    variants = {}
    for f in ("east", "south", "west", "north"):
        for half in ("lower", "upper"):
            mhalf = "bottom" if half == "lower" else "top"
            for hinge in ("left", "right"):
                for opn in (False, True):
                    y = (y_open_left[f] if hinge == "left" else y_open_right[f]) if opn else y_closed[f]
                    m = f"{NS}office_fire_door_{mhalf}_{hinge}{'_open' if opn else ''}"
                    v = {"model": m}
                    if y:
                        v["y"] = y
                    variants[f"facing={f},half={half},hinge={hinge},open={str(opn).lower()}"] = v
    return {"variants": variants}


def all_models():
    m = {}
    plain_cubes = [
        "office_carpet", "office_carpet_1", "office_carpet_2", "office_carpet_3",
        "office_carpet_dent_1", "office_carpet_dent_2", "office_carpet_dent_3", "office_carpet_dent_4",
        "office_damp_carpet", "office_drywall", "office_drywall_upper", "office_pillar", "office_pillar_upper",
        "office_acoustic_ceiling", "office_acoustic_ceiling_1", "office_acoustic_ceiling_2", "office_ceiling_vent",
        "office_blacked_window", "office_rain_window", "office_glass_wall",
        "omega_wall", "omega_sign", "almond_fountain_basin"
    ]
    for c in plain_cubes:
        m[c] = cube(c)
    for v in range(1, 10):
        m[f"office_drywall_{v}"] = cube(f"office_drywall_{v}")
        m[f"office_drywall_upper_{v}"] = cube(f"office_drywall_upper_{v}")

    # Trap window emits strong bright light
    m["office_trap_window"] = {
        "textures": {"all": NS + "office_trap_window", "particle": NS + "office_trap_window"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#all", "light_emission": 12} for f in ("north", "south", "east", "west", "up", "down")}
        }]
    }

    # 4-state troffer lights
    m["office_troffer_lit"] = troffer_model("office_troffer_lit", 15)
    m["office_troffer_dim"] = troffer_model("office_troffer_dim", 9)
    m["office_troffer_flicker"] = troffer_model("office_troffer_flicker", 11)
    m["office_troffer_broken"] = troffer_model("office_troffer_broken", 0)

    m.update(door_models())

    # Radiator wall (front face has radiator)
    m["office_radiator_wall"] = {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": NS + "office_radiator_wall",
            "side": NS + "office_drywall",
            "top": NS + "office_drywall",
        }
    }

    # Exit sign (mounted high on wall)
    m["office_exit_sign"] = {
        "textures": {"sign": NS + "office_exit_sign", "frame": NS + "omega_wall", "particle": NS + "office_exit_sign"},
        "elements": [{
            "from": [3, 10, 14], "to": [13, 15, 16],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#sign", "light_emission": 10},
                "south": {"uv": [0, 0, 16, 16], "texture": "#frame"},
                "east": {"uv": [0, 0, 2, 5], "texture": "#frame"},
                "west": {"uv": [0, 0, 2, 5], "texture": "#frame"},
                "up": {"uv": [0, 0, 10, 2], "texture": "#frame"},
                "down": {"uv": [0, 0, 10, 2], "texture": "#frame"},
            }
        }]
    }

    # Two-high exit doors (part 0 = bottom, part 1 = top)
    for name in ("office_elevator", "office_stairwell_5", "office_stairwell_6", "office_basement_71",
                 "office_door_4p3", "office_door_11", "office_door_13"):
        for p in (0, 1):
            m[f"{name}_{p}"] = {
                "parent": "minecraft:block/orientable",
                "textures": {"front": NS + name, "side": NS + "office_drywall", "top": NS + "office_drywall"}
            }

    # Floor hatch
    m["office_hatch_2"] = {
        "textures": {"top": NS + "office_hatch_2", "side": NS + "omega_wall", "particle": NS + "office_hatch_2"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 2, 16],
            "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "north": {"uv": [0, 0, 16, 2], "texture": "#side"},
                "south": {"uv": [0, 0, 16, 2], "texture": "#side"},
                "east": {"uv": [0, 0, 16, 2], "texture": "#side"},
                "west": {"uv": [0, 0, 16, 2], "texture": "#side"},
            }
        }]
    }

    # Water cooler (0=empty, 1=half, 2=full)
    for b, jug in ((2, "cooler_jug_full"), (1, "cooler_jug_half"), (0, "cooler_jug_empty")):
        m[f"office_water_cooler_{b}"] = {
            "textures": {"body": NS + "cooler_body", "jug": NS + jug, "particle": NS + "cooler_body"},
            "elements": [
                box_el(3, 0, 3, 13, 11, 13, "#body"),
                box_el(4, 11, 4, 12, 16, 12, "#jug"),
            ]
        }

    # Almond fountain pool (0=empty, 1=half, 2=full)
    for b, liq, h in ((2, "almond_liquid_full", 12), (1, "almond_liquid_half", 7), (0, "almond_liquid_empty", 2)):
        m[f"almond_fountain_pool_{b}"] = {
            "textures": {"liq": NS + liq, "base": NS + "almond_fountain_basin", "particle": NS + liq},
            "elements": [
                box_el(0, 0, 0, 16, 2, 16, "#base"),
                box_el(0, 2, 0, 16, h, 16, "#liq"),
            ]
        }

    # Vending machine (facing north)
    m["office_vending"] = {
        "textures": {"front": NS + "vending_front", "side": NS + "vending_side", "particle": NS + "vending_front"},
        "elements": [{
            "from": [1, 0, 2], "to": [15, 16, 14],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#front", "light_emission": 6},
                "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
            }
        }]
    }

    # Faction blocks & event props
    m["omega_cot"] = {
        "textures": {"tex": NS + "omega_cot", "leg": NS + "omega_wall", "particle": NS + "omega_cot"},
        "elements": [
            box_el(1, 4, 0, 15, 7, 16, "#tex"),
            box_el(2, 0, 1, 4, 4, 3, "#leg"),
            box_el(12, 0, 1, 14, 4, 3, "#leg"),
            box_el(2, 0, 13, 4, 4, 15, "#leg"),
            box_el(12, 0, 13, 14, 4, 15, "#leg"),
        ]
    }
    m["omega_locker"] = {
        "textures": {"front": NS + "omega_locker", "side": NS + "omega_wall", "particle": NS + "omega_locker"},
        "elements": [box_el(1, 0, 2, 15, 16, 14, "#side", front="north", front_tex="#front")]
    }
    m["omega_barricade"] = {
        "textures": {"tex": NS + "omega_barricade", "particle": NS + "omega_barricade"},
        "elements": [
            box_el(0, 0, 2, 16, 6, 14, "#tex"),
            box_el(0, 6, 4, 16, 14, 12, "#tex"),
        ]
    }
    m["amor_planter"] = {
        "textures": {"side": NS + "amor_planter_side", "top": NS + "amor_substrate", "particle": NS + "amor_substrate"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "north": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
            }
        }]
    }
    m["amor_planter_l1"] = {
        "textures": {"side": NS + "amor_planter_side", "top": NS + "amor_substrate_l1", "particle": NS + "amor_substrate_l1"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "north": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
            }
        }]
    }
    m["amor_mushroom"] = {"parent": "minecraft:block/cross", "textures": {"cross": NS + "amor_mushroom"}}
    m["amor_mushroom_moss"] = {"parent": "minecraft:block/cross", "textures": {"cross": NS + "amor_mushroom_moss"}}
    m["amor_mushroom_veil"] = {"parent": "minecraft:block/cross", "textures": {"cross": NS + "amor_mushroom_veil"}}
    m["tbd_curio_shelf"] = {
        "textures": {"tex": NS + "tbd_shelf", "particle": NS + "tbd_shelf"},
        "elements": [box_el(1, 0, 3, 15, 16, 13, "#tex")]
    }
    m["tbd_hoard_crate"] = {
        "textures": {"front": NS + "tbd_hoard_front", "side": NS + "tbd_hoard_side", "particle": NS + "tbd_hoard_side"},
        "elements": [box_el(0, 0, 0, 16, 16, 16, "#side", front="north", front_tex="#front")]
    }
    m["office_breaker"] = {
        "textures": {"front": NS + "office_breaker", "side": NS + "omega_wall", "particle": NS + "office_breaker"},
        "elements": [box_el(2, 2, 12, 14, 14, 16, "#side", front="north", front_tex="#front")]
    }
    m["office_photocopier"] = {
        "textures": {"front": NS + "photocopier_body", "side": NS + "omega_wall", "particle": NS + "photocopier_body"},
        "elements": [
            box_el(1, 0, 2, 15, 12, 14, "#front"),
            box_el(2, 12, 3, 14, 14, 13, "#side"),
        ]
    }
    m["office_desk_phone"] = {
        "textures": {"wood": NS + "desk_wood", "phone": NS + "desk_phone", "particle": NS + "desk_wood"},
        "elements": [
            box_el(1, 0, 2, 15, 11, 14, "#wood"),
            box_el(4, 11, 4, 12, 14, 11, "#phone"),
        ]
    }
    return m


def simple(model):
    return {"variants": {"": {"model": NS + model}}}


def facing_state(model):
    return {"variants": {f"facing={f}": {"model": NS + model, **({"y": y} if y else {})} for f, y in FACING_Y.items()}}


def axis_state(model):
    return {"variants": {"axis=x": {"model": NS + model}, "axis=z": {"model": NS + model, "y": 90}}}


def all_blockstates():
    b = {}
    b["office_carpet"] = {"variants": {"": [
        {"model": NS + n, **({"y": y} if y else {})}
        for n in ("office_carpet", "office_carpet_1", "office_carpet_2", "office_carpet_3")
        for y in (0, 90, 180, 270)
    ]}}
    b["office_carpet_dent"] = {"variants": {"": [
        {"model": NS + f"office_carpet_dent_{k}", **({"y": y} if y else {})}
        for k in (1, 2, 3, 4) for y in (0, 90, 180, 270)
    ]}}
    b["office_drywall"] = {"variants": {"": (
        [{"model": NS + "office_drywall", "weight": 8}]
        + [{"model": NS + f"office_drywall_{v}", "weight": 1} for v in range(1, 10)]
    )}}
    b["office_drywall_upper"] = {"variants": {"": (
        [{"model": NS + "office_drywall_upper", "weight": 8}]
        + [{"model": NS + f"office_drywall_upper_{v}", "weight": 1} for v in range(1, 10)]
    )}}
    b["office_acoustic_ceiling"] = {"variants": {"": [
        {"model": NS + n, **({"y": y} if y else {})}
        for n in ("office_acoustic_ceiling", "office_acoustic_ceiling_1", "office_acoustic_ceiling_2")
        for y in (0, 180)
    ]}}
    for c in ("office_damp_carpet", "office_pillar", "office_pillar_upper", "office_ceiling_vent", "office_blacked_window",
              "office_trap_window", "office_rain_window", "office_glass_wall", "omega_wall",
              "omega_sign", "almond_fountain_basin", "amor_planter", "amor_planter_l1",
              "amor_mushroom", "amor_mushroom_moss", "amor_mushroom_veil", "office_hatch_2"):
        b[c] = simple(c)
    for ax in ("office_troffer_lit", "office_troffer_dim", "office_troffer_flicker", "office_troffer_broken"):
        b[ax] = axis_state(ax)
    for fc in ("office_radiator_wall", "office_exit_sign", "office_vending", "omega_cot", "omega_locker",
               "omega_barricade", "tbd_curio_shelf", "tbd_hoard_crate", "office_breaker", "office_photocopier", "office_desk_phone"):
        b[fc] = facing_state(fc)
    b["office_fire_door"] = door_blockstate()
    for ex in ("office_elevator", "office_stairwell_5", "office_stairwell_6", "office_basement_71",
               "office_door_4p3", "office_door_11", "office_door_13"):
        b[ex] = {"variants": {
            f"facing={f},part={p}": {"model": f"{NS}{ex}_{p if p < 2 else 0}", **({"y": y} if y else {})}
            for f, y in FACING_Y.items() for p in range(8)
        }}
    b["office_water_cooler"] = {"variants": {
        f"facing={f},bottles={bt}": {"model": f"{NS}office_water_cooler_{bt}", **({"y": y} if y else {})}
        for f, y in FACING_Y.items() for bt in (0, 1, 2)
    }}
    b["almond_fountain_pool"] = {"variants": {
        f"bottles={bt}": {"model": f"{NS}almond_fountain_pool_{bt}"} for bt in (0, 1, 2)
    }}
    return b


NO_LOOT = {
    "office_elevator", "office_stairwell_5", "office_stairwell_6", "office_basement_71",
    "office_door_4p3", "office_door_11", "office_door_13", "office_hatch_2",
    "office_blacked_window", "office_trap_window", "office_rain_window",
    "omega_wall", "omega_sign", "almond_fountain_basin", "almond_fountain_pool",
    "amor_mushroom", "amor_mushroom_moss", "amor_mushroom_veil"
}


def item_model(name):
    if name in ("office_elevator", "office_stairwell_5", "office_stairwell_6", "office_basement_71",
                "office_door_4p3", "office_door_11", "office_door_13"):
        return NS + f"{name}_0"
    if name == "office_carpet_dent":
        return NS + "office_carpet_dent_1"
    if name == "office_fire_door":
        return NS + "office_fire_door_bottom_left"
    if name == "office_water_cooler":
        return NS + "office_water_cooler_2"
    if name == "almond_fountain_pool":
        return NS + "almond_fountain_pool_2"
    return NS + name


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def preview(textures):
    names = [
        "office_carpet", "office_carpet_dent_1", "office_damp_carpet", "office_drywall",
        "office_drywall_1", "office_radiator_wall", "office_acoustic_ceiling", "office_ceiling_vent",
        "office_troffer_lit", "office_troffer_dim", "office_troffer_broken", "office_blacked_window",
        "office_trap_window", "office_rain_window", "office_fire_door", "office_elevator",
        "office_stairwell_5", "office_stairwell_6", "omega_wall", "omega_sign",
        "vending_front", "cooler_body", "almond_liquid_full", "office_breaker"
    ]
    columns, gap, side = 6, 8, SIZE * 2
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (side + gap) + gap, rows * (side + gap) + gap), (30, 30, 30))
    for i, name in enumerate(names):
        tile = Image.fromarray(np.clip(np.rint(textures[name].color), 0, 255).astype("uint8"), "RGB")
        for dx in (0, SIZE):
            for dy in (0, SIZE):
                sheet.paste(tile, (gap + i % columns * (side + gap) + dx, gap + i // columns * (side + gap) + dy))
    out = mt.ROOT / "docs/project/5-levels/level4/textures.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, optimize=True)


def loot_table(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}]
        }],
        "random_sequence": f"noclip_backrooms:blocks/{name}"
    }


def check(blockstates):
    prefix = "noclip_backrooms:block/"
    for path in MODELS.glob("*.json"):
        for value in json.loads(path.read_text(encoding="utf8")).get("textures", {}).values():
            if value.startswith(NS):
                for suffix in ("", "_n", "_s"):
                    assert (TEXTURES / f"{value[len(NS):]}{suffix}.png").is_file(), (path.name, value, suffix)
    for name, state in blockstates.items():
        for entry in state["variants"].values():
            for v in (entry if isinstance(entry, list) else [entry]):
                assert (ASSETS / f"models/block/{v['model'][len(prefix):]}.json").is_file(), (name, v["model"])
        item = json.loads((ASSETS / f"items/{name}.json").read_text(encoding="utf8"))
        assert (ASSETS / f"models/block/{item['model']['model'][len(prefix):]}.json").is_file(), name


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES
    textures = all_textures()
    for name, material in textures.items():
        mt.save(name, material)
    save_cutout_glass()
    save_flicker_troffer()
    preview(textures)

    models = all_models()
    for name, model in models.items():
        write_json(MODELS / f"{name}.json", model)

    blockstates = all_blockstates()
    for name, state in blockstates.items():
        write_json(ASSETS / f"blockstates/{name}.json", state)
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": item_model(name)}})
        if name not in NO_LOOT:
            write_json(mt.ROOT / f"src/main/resources/data/noclip_backrooms/loot_table/blocks/{name}.json", loot_table(name))

    check(blockstates)
    print(f"Level 4: {len(textures)} base materials, {len(models)} models, {len(blockstates)} blocks verified.")


if __name__ == "__main__":
    main()
