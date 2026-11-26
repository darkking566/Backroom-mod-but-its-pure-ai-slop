"""Draws every Level 3 texture at 128x128 and writes the Level 3 block models, blockstates, item models and loot tables.

    py tools/make_level3.py

Uses the helpers of make_textures.py, make_level1.py and make_level2.py (Pillow, NumPy).
Textures go to textures/block/level3/, models to models/block/level3/. Also writes docs/project/5-levels/level3/textures.png.
Looks follow the photos on the Wikidot Level 3 page: orange-red brick, cream glazed tile and grey cinder block,
white cage bars and black iron bars, grey conduits and electrical boxes, green switchgear with porcelain
insulators, brass boilers, a bronze angel, stained glass, and black sludge.
"""

import json
import sys

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True  # no __pycache__ next to the scripts
import make_level1 as l1  # noqa: E402
import make_level2 as l2  # noqa: E402
import make_textures as mt  # noqa: E402
from make_textures import SIZE, X, Y, box, clouds, flat, inside, metal, noise, paint, step, tint  # noqa: E402

ASSETS = mt.ASSETS
TEXTURES = ASSETS / "textures/block/level3"
MODELS = ASSETS / "models/block/level3"
NS = "noclip_backrooms:block/level3/"
el, sides = l1.el, l1.sides
shaded, text_mask, recolor = l2.shaded, l2.text_mask, l2.recolor


# Walls

def brick(seed, palette=(1.18, 0.72, 0.46), gain=1.0, mortar=(70, 56, 46)):
    m = recolor(l1.bricks(seed, aged=True), palette, gain)
    course, length = 16, 32
    row = (Y // course).astype(int)
    shifted = (X + (row % 2) * (length // 2)) % length
    joint = np.maximum(1 - step(0.8, 2.0, np.minimum(Y % course, course - Y % course)),
                       1 - step(0.8, 2.0, np.minimum(shifted, length - shifted)))
    paint(m, joint, mortar)
    soot = step(0.1, 0.6, clouds(seed + 9, 3)) * 0.5
    tint(m, soot, (0.8, 0.76, 0.72))
    return m


def glazed(seed, color=(222, 212, 178), course=32):
    """Glazed tile: glossy, slightly uneven squares, dark grout, crazing."""
    m = flat(color, 0.85, 40, 5)
    rng = np.random.default_rng(seed)
    cell = (X // course).astype(int) + (Y // course).astype(int) * 8
    m.color *= rng.uniform(0.93, 1.05, 64)[cell][..., None]
    grout = np.maximum(1 - step(0.8, 1.8, np.minimum(X % course, course - X % course)),
                       1 - step(0.8, 1.8, np.minimum(Y % course, course - Y % course)))
    paint(m, grout, (70, 66, 58), height=-1.2, smooth=0.1, porosity=40)
    bevel = 1 - step(1.8, 4, np.minimum(np.minimum(X % course, course - X % course), np.minimum(Y % course, course - Y % course)))
    tint(m, bevel * (1 - grout), (0.92, 0.92, 0.9))
    craze = step(0.92, 0.96, noise(seed + 1, 64)) * 0.4
    tint(m, craze, (0.8, 0.78, 0.72))
    grime = step(0.0, 0.6, clouds(seed + 2, 4)) * 0.35
    tint(m, grime, (0.85, 0.8, 0.7))
    return m


def wainscot(seed):
    m = glazed(seed, (52, 84, 70), course=32)
    paint(m, box(-2, 0, 130, 10, 1), (40, 64, 54), height=2.5, smooth=0.85)  # the cap along the top
    return m


def cinder(seed):
    m = l1.block_wall((132, 130, 124), seed, course=32, length=64, mortar=(0.8, 0.8, 0.78), depth=-1.2)
    pores = step(0.75, 0.9, noise(seed + 1, 128))
    paint(m, pores, height=-1, porosity=70)
    tint(m, pores, (0.75, 0.75, 0.73))
    tint(m, step(0.1, 0.6, clouds(seed + 2, 3)) * 0.4, (0.82, 0.8, 0.76))
    return m


def purpified(seed):
    """Purple and white swirled like marble, where something clipped into the wall."""
    field = np.sin((X * 0.08 + noise(seed, 8) * 3 + clouds(seed + 1, 4) * 4)) * 0.5 + 0.5
    m = flat((200, 150, 210), 0.5, 20, 10)
    m.color = np.array([238, 222, 240]) * (1 - field[..., None]) + np.array([150, 70, 170]) * field[..., None]
    m.glow += step(0.85, 0.95, field) * 0.15
    course = 16
    row = (Y // course).astype(int)
    shifted = (X + (row % 2) * 16) % 32
    joint = np.maximum(1 - step(0.8, 2.0, np.minimum(Y % course, course - Y % course)),
                       1 - step(0.8, 2.0, np.minimum(shifted, 32 - shifted)))
    paint(m, joint, (90, 50, 100), height=-1.2)
    return m


def meg_wall(seed):
    m = l1.concrete((150, 152, 150), seed, smooth=0.3, porosity=20)
    seam = np.maximum(1 - step(0.6, 1.5, np.minimum(X, SIZE - X)), 1 - step(0.6, 1.5, np.minimum(Y, SIZE - Y)))
    paint(m, seam, (90, 92, 90), height=-1.5)
    for cx in (8, 120):
        for cy in (8, 120):
            paint(m, 1 - step(2, 3, np.hypot(X - cx, Y - cy)), (110, 112, 110), height=1.5, f0=200)
    return m


def meg_sign(seed):
    m = meg_wall(seed)
    plate = box(16, 24, 112, 104, 1.5)
    paint(m, plate, (30, 58, 104), height=1, smooth=0.6)
    ring = (1 - step(1.5, 3, np.abs(np.hypot(X - 64, Y - 56) - 22))) * plate
    paint(m, ring, (232, 214, 120))
    paint(m, text_mask("M.E.G.", 12, (64, 56), "arialbd.ttf") * plate, (240, 236, 220))
    paint(m, text_mask("BASE GAMMA", 10, (64, 92), "arialbd.ttf") * plate, (232, 214, 120))
    return m


def dusty_floor(seed):
    m = l1.concrete((138, 132, 122), seed, smooth=0.15, porosity=45)
    dust = step(-0.1, 0.6, clouds(seed + 1, 4)) * 0.5
    tint(m, dust, (1.1, 1.08, 1.03))
    l1.scuffs(m, seed + 2)
    return m


def ceiling(seed):
    m = l1.concrete((74, 70, 66), seed, smooth=0.15, porosity=40)
    for y0, color in ((30, (150, 152, 150)), (40, (150, 152, 150)), (88, (40, 36, 32))):
        pipe = 1 - step(2.2, 3.2, np.abs(Y - y0))
        paint(m, pipe, color, height=3, smooth=0.5, f0=120)
        tint(m, pipe, np.stack([0.8 + 0.25 * (1 - np.abs(Y - y0) / 3)] * 3, -1))
    return m


def outlet(seed):
    m = brick(seed, (1.25, 0.66, 0.4), 1.55)
    plate = box(48, 40, 80, 88, 1)
    paint(m, plate, (206, 198, 176), height=1.5, smooth=0.5)
    for cy in (54, 74):
        paint(m, box(56, cy - 3, 59, cy + 3, 0.5) + box(69, cy - 3, 72, cy + 3, 0.5), (30, 28, 26), height=-1)
    scorch = (1 - step(10, 26, np.hypot(X - 64, Y - 50))) * (0.6 + 0.4 * noise(seed + 3, 24))
    tint(m, scorch * 0.8, (0.35, 0.32, 0.3))
    return m


# Metalwork

def bars(color):
    m = metal(color, 0.4)
    chips = step(0.84, 0.92, noise(900 + color[0], 32)) * 0.7
    paint(m, chips, (110, 92, 76), height=-0.2)
    return m


def conduit():
    return shaded(metal((168, 170, 168), 0.6))


def sludge_steel(seed, wet=False):
    m = metal((58, 54, 50), 0.4)
    tint(m, step(0.0, 0.5, clouds(seed, 4)) * 0.6, (0.6, 0.55, 0.5))
    if wet:
        runs = step(0.45, 0.7, noise(seed + 1, 16, 2)) * step(60, 128, Y)
        paint(m, runs, (8, 6, 5), smooth=0.95)
    return shaded(m)


def electrical_front(seed):
    m = metal((150, 154, 150), 0.45)
    paint(m, box(10, 10, 118, 118, 1.5), (138, 142, 138), height=1)
    paint(m, box(96, 50, 104, 78, 1), (60, 60, 60), height=2.5, f0=200)
    label = box(24, 22, 72, 38, 0.5)
    paint(m, label, (220, 210, 150), smooth=0.4)
    paint(m, text_mask("DANGER", 9, (48, 30), "arialbd.ttf") * label, (170, 30, 26))
    tint(m, step(0.2, 0.6, clouds(seed, 4)) * 0.4, (0.82, 0.74, 0.64))
    return m


def sludge():
    m = flat((10, 8, 7), 0.95, 30, 0)
    bubbles = 1 - step(1.5, 3, np.minimum.reduce([np.hypot(X - cx, Y - cy) for cx, cy in ((40, 50), (82, 70), (60, 92), (90, 36))]))
    paint(m, bubbles, (30, 26, 22), height=1.5)
    sheen = step(0.4, 0.7, noise(910, 16)) * 0.3
    tint(m, sheen, (1.8, 1.7, 1.6))
    alpha = step(-0.15, 0.05, l1.blob(911, (44, 40), 0.55))
    return m, alpha


def cable(seed):
    m = flat((26, 24, 22), 0.5, 12)
    m.height += np.sin(np.pi * (Y % 16) / 16) * 2
    tint(m, np.ones((SIZE, SIZE)), np.stack([0.7 + 0.4 * np.sin(np.pi * (Y % 16) / 16)] * 3, -1))
    return m


def copper():
    return metal((196, 120, 66), 0.75)


# Lights

def lens(tube, glow):
    return mt.panel((tube[0] - 30, tube[1] - 30, tube[2] - 30), tube, glow)


# Halls

def green_steel(seed):
    m = metal((74, 104, 82), 0.35)
    m.porosity[:] = 20
    chips = step(0.64, 0.76, noise(seed, 32) * 0.6 + noise(seed + 1, 96) * 0.4)
    paint(m, chips, (90, 84, 78), height=-0.3)
    tint(m, step(0.1, 0.6, clouds(seed + 2, 4)) * 0.4, (0.8, 0.76, 0.68))
    return m


def generator_side(seed):
    m = green_steel(seed)
    for x0 in (20, 60, 100):
        paint(m, box(x0, 0, x0 + 10, 128, 0.5), (196, 120, 66), height=3, smooth=0.75, f0=230)  # copper bus bars
    paint(m, box(0, 56, 128, 64, 0.5), (50, 70, 56), height=2)
    return m


def switchgear_front(seed, top):
    m = green_steel(seed)
    paint(m, box(6, 6, 122, 122, 1.5), (64, 92, 72), height=1)
    if top:
        for cx in (36, 92):
            d = np.hypot(X - cx, Y - 48)
            paint(m, 1 - step(18, 20, d), (30, 30, 30), height=2)
            paint(m, 1 - step(15, 16, d), (232, 226, 206), height=1.5, smooth=0.8)
            paint(m, (1 - step(0.6, 1.2, np.abs((X - cx) * 0.6 - (Y - 48) * 0.8))) * (1 - step(12, 13, d)), (30, 30, 30))
        for cx, color, glow in ((32, (190, 30, 24), 0.8), (64, (40, 160, 50), 0.6), (96, (220, 170, 40), 0.0)):
            paint(m, 1 - step(4, 5, np.hypot(X - cx, Y - 96)), color, height=1.5, smooth=0.9, glow=glow)
    else:
        for row in (30, 62, 94):
            for cx in (30, 64, 98):
                paint(m, box(cx - 8, row - 10, cx + 8, row + 10, 0.8), (40, 40, 40), height=1)
                paint(m, box(cx - 2, row - 14, cx + 2, row, 0.5), (180, 180, 176), height=3, f0=220)  # knife switch
    return m


def insulator():
    m = flat((236, 232, 222), 0.92, 40)
    ribs = np.sin(Y * np.pi / 8) * 0.5 + 0.5
    m.height += ribs * 2
    tint(m, np.ones((SIZE, SIZE)), np.stack([0.85 + 0.15 * ribs] * 3, -1))
    return m


def brass(seed, top=False):
    m = metal((190, 150, 76), 0.65)
    m.f0[:] = 230
    tint(m, step(0.0, 0.6, clouds(seed, 4)) * 0.5, (0.78, 0.7, 0.55))
    seams = [32, 96] if not top else [64]
    for y0 in seams:
        paint(m, box(-2, y0 - 3, 130, y0 + 3), (168, 128, 60), height=1.5)
        for cx in range(8, 128, 16):
            paint(m, 1 - step(1.5, 2.5, np.hypot(X - cx, Y - y0)), (210, 176, 96), height=2.5)
    verdigris = step(0.3, 0.6, clouds(seed + 1, 6)) * step(90, 128, Y) * 0.6
    paint(m, verdigris, (90, 140, 116), smooth=0.2, f0=20)
    return m


def belt(offset):
    m = flat((34, 32, 30), 0.3, 10, 30)
    ribs = 1 - step(1, 2.5, np.abs((Y + offset) % 16 - 8))
    paint(m, ribs, (56, 54, 50), height=1.5)
    edge = np.maximum(1 - step(4, 6, X), step(SIZE - 7, SIZE - 5, X))
    paint(m, edge, (120, 122, 120), height=2, f0=200)
    return m


def conveyor_side():
    m = metal((110, 112, 110), 0.45)
    paint(m, box(-2, 40, 130, 48), (70, 70, 70), height=-1)
    for cx in range(8, 128, 24):
        paint(m, 1 - step(5, 6, np.hypot(X - cx, Y - 30)), (150, 150, 146), height=2, f0=220)
    paint(m, box(-2, 64, 130, 128), (60, 60, 60), height=-0.5)
    return m


def press_front(seed):
    m = metal((96, 98, 100), 0.4)
    paint(m, box(16, 8, 112, 120, 1.5), (80, 82, 84), height=1)
    stripes = ((X + Y) // 12 % 2).astype(float) * box(16, 100, 112, 120)
    paint(m, stripes, (220, 180, 40))
    tint(m, step(0.2, 0.6, clouds(seed, 4)) * 0.5, (0.8, 0.66, 0.54))
    return m


def rusty_chain():
    m = metal((110, 76, 52), 0.3)
    links = 1 - step(0.5, 1.5, np.abs(np.hypot((X % 32) - 16, (Y % 48) - 24) - 10))
    paint(m, links, (80, 54, 38), height=2)
    return m


def pillar_side(seed):
    """Brown stone twisted into a spiral."""
    m = brick(seed, (1.0, 0.72, 0.5), 0.95)
    twist = np.sin((X * 2 + Y) * np.pi / 32) * 0.5 + 0.5
    m.color = np.array([150, 112, 80]) * (0.7 + 0.45 * twist[..., None])
    m.height = twist * 4
    m.smooth[:] = 0.25
    tint(m, step(0.2, 0.6, clouds(seed + 1, 4)) * 0.4, (0.85, 0.8, 0.74))
    return m


def stone_end(color=(146, 112, 82)):
    m = l1.concrete(color, 930, smooth=0.3, porosity=30)
    paint(m, 1 - step(1.5, 3, np.abs(np.hypot(X - 64, Y - 64) - 56)), np.array(color) * 0.8, height=-1)
    return m


def capital(seed):
    m = pillar_side(seed)
    for y0, h in ((0, 22), (106, 22)):
        band = box(-2, y0, 130, y0 + h)
        paint(m, band, (170, 132, 96), height=3)
        scroll = (1 - step(0.5, 1.5, np.abs(np.hypot((X % 32) - 16, Y - y0 - h / 2) - 6))) * band
        paint(m, scroll, (120, 90, 64), height=-1)
    return m


def marble(seed):
    m = flat((60, 52, 48), 0.85, 30)
    veins = step(0.9, 0.97, 1 - np.abs(np.sin(X * 0.05 + clouds(seed, 4) * 6)))
    paint(m, veins, (170, 150, 130))
    return m


def bronze(seed):
    m = metal((122, 92, 52), 0.55)
    m.f0[:] = 220
    patina = step(0.1, 0.5, clouds(seed, 5))
    paint(m, patina * 0.7, (70, 118, 100), smooth=0.25, f0=30)
    highlight = step(0.6, 0.8, noise(seed + 1, 24)) * 0.3
    tint(m, highlight, (1.35, 1.25, 1.05))
    return m


def stained_glass():
    m = flat((40, 40, 40), 0.9, 30)
    rng = np.random.default_rng(940)
    palette = np.array([[180, 30, 30], [40, 70, 170], [220, 180, 50], [60, 140, 70], [150, 60, 150], [230, 220, 200]], float)
    points = rng.uniform(0, 128, (24, 2))
    d = np.stack([np.hypot(X - px, Y - py) for px, py in points])
    order = np.sort(d, axis=0)
    nearest = np.argmin(d, axis=0)
    m.color = palette[nearest % len(palette)]
    lead = 1 - step(1.0, 2.5, order[1] - order[0])
    frame = np.maximum(1 - step(4, 7, np.minimum(X, SIZE - X)), 1 - step(4, 7, np.minimum(Y, SIZE - Y)))
    lead = np.maximum(lead, frame)
    m.glow += (1 - lead) * 0.8
    paint(m, lead, (30, 28, 26), height=1.5, glow=0.0)
    return m


def car_paint(seed):
    m = metal((122, 34, 30), 0.35)
    m.f0[:] = 40
    rust = step(0.2, 0.55, clouds(seed, 5)) * (0.5 + 0.5 * noise(seed + 1, 32))
    paint(m, rust * 0.8, (110, 70, 40), smooth=0.1, porosity=30)
    dust = step(-0.2, 0.5, clouds(seed + 2, 3)) * 0.35
    tint(m, dust, (1.15, 1.1, 1.05))
    return m


def car_glass():
    m = flat((40, 48, 52), 0.95, 40)
    streak = step(0.4, 0.7, noise(950, 4, 16)) * 0.3
    tint(m, streak, (1.6, 1.6, 1.6))
    return m


def car_front():
    m = car_paint(951)
    grille = box(16, 40, 112, 88, 1)
    paint(m, grille, (60, 60, 60), height=-1)
    paint(m, grille * (1 - step(0.8, 1.6, np.abs(X % 8 - 4))), (176, 178, 176), height=1, f0=230)
    for cx in (12, 116):
        paint(m, 1 - step(10, 11, np.hypot(X - cx, Y - 60)), (226, 220, 190), height=2, smooth=0.95, f0=60)
    paint(m, box(-2, 100, 130, 116), (170, 172, 170), height=2, f0=230, smooth=0.8)  # bumper
    return m


def car_rear():
    m = car_paint(952)
    for cx in (14, 114):
        paint(m, box(cx - 10, 44, cx + 10, 68, 1), (150, 30, 24), height=1.5, smooth=0.9)
    paint(m, box(40, 56, 88, 72, 1), (230, 210, 120), height=0.5)  # plate
    paint(m, box(-2, 100, 130, 116), (170, 172, 170), height=2, f0=230, smooth=0.8)
    return m


def tyre():
    m = flat((22, 22, 22), 0.2, 10, 30)
    d = np.hypot(X - 64, Y - 64)
    paint(m, 1 - step(26, 30, d), (150, 150, 146), height=1.5, f0=220)
    return m


def door_steel(top, seed, logo):
    m = metal((128, 132, 134), 0.45)
    jamb = np.maximum(1 - step(4, 6, X), step(SIZE - 7, SIZE - 5, X))
    if top:
        jamb = np.maximum(jamb, 1 - step(4, 6, Y))
    paint(m, jamb, (70, 72, 74), height=2.5)
    for cy in range(16, 128, 32):
        for cx in (14, 114):
            paint(m, 1 - step(1.5, 2.5, np.hypot(X - cx, Y - cy)), (160, 162, 160), height=1.2, f0=220)
    if logo and top:
        paint(m, box(28, 28, 100, 100, 1.5), (30, 58, 104), height=1, smooth=0.6)
        paint(m, text_mask("M.E.G.", 16, (64, 56), "arialbd.ttf"), (240, 236, 220))
        paint(m, text_mask("LEVEL 11", 10, (64, 82), "arialbd.ttf"), (232, 214, 120))
    if logo and not top:
        paint(m, box(96, 8, 112, 32, 1), (40, 40, 40), height=2)  # keypad
        for ky in (13, 19, 25):
            for kx in (100, 104, 108):
                paint(m, 1 - step(0.8, 1.4, np.hypot(X - kx, Y - ky)), (150, 200, 150), glow=0.5)
    tint(m, step(0.3, 0.6, clouds(seed, 4)) * 0.4, (0.85, 0.8, 0.72))
    return m


def elevator_door(top):
    m = brass(960 + top)
    seam = 1 - step(0.6, 1.4, np.abs(X - 64))
    paint(m, seam, (60, 44, 20), height=-2)
    lattice = (1 - step(0.8, 1.8, np.abs(((X + Y) % 24) - 12))) * box(8, 0 if not top else 34, 120, 128)
    lattice2 = (1 - step(0.8, 1.8, np.abs(((X - Y) % 24) - 12))) * box(8, 0 if not top else 34, 120, 128)
    paint(m, np.maximum(lattice, lattice2), (120, 90, 40), height=1.5)
    if top:
        d = np.hypot(X - 64, Y - 18)
        paint(m, 1 - step(14, 15, d) * 1.0, (60, 44, 20), height=1)
        paint(m, (1 - step(13, 14, d)) * step(0, 1, 18 - Y), (230, 214, 160), smooth=0.8, glow=0.3)
        paint(m, (1 - step(0.6, 1.2, np.abs((X - 64) * 0.8 + (Y - 18) * 0.6))) * (1 - step(10, 11, d)), (30, 20, 10))
    return m


def canvas(color, seed):
    m = flat(color, 0.15, 6, 50)
    weave = noise(seed, 128, 64) * 0.5 + noise(seed + 1, 64, 128) * 0.5
    m.color *= (1 + weave[..., None] * 0.08)
    return m


def meg_crate(top):
    m = l1.crate(top, 970)
    m.color = m.color.mean(-1, keepdims=True) * np.array([0.82, 0.96, 0.62])
    if not top:
        paint(m, text_mask("M.E.G.", 18, (64, 64), "arialbd.ttf"), (220, 214, 190))
    return m


def parts_crate(top):
    m = l1.crate(top, 980)
    if not top:
        paint(m, text_mask("PARTS", 16, (64, 64), "impact.ttf"), (40, 34, 28))
    return m


def meg_arrow(right):
    m = metal((40, 70, 130), 0.5)
    paint(m, box(4, 4, 124, 124, 1), (30, 58, 110), height=1)
    paint(m, text_mask("M.E.G.", 12, (64, 24), "arialbd.ttf"), (232, 214, 120))
    paint(m, text_mask("BASE GAMMA", 13, (64, 52), "arialbd.ttf"), (240, 238, 230))
    paint(m, text_mask("\u2192" if right else "\u2190", 56, (64, 92), "arialbd.ttf"), (240, 238, 230))
    return m


def meg_warning():
    m = metal((222, 186, 40), 0.4)
    stripes = ((X + Y) // 10 % 2).astype(float) * np.maximum(1 - box(8, 8, 120, 120), 0)
    paint(m, stripes, (30, 28, 26))
    paint(m, text_mask("CAUTION", 18, (64, 30), "impact.ttf"), (30, 28, 26))
    paint(m, text_mask("SCOPOPHOBIA", 11, (64, 58), "arialbd.ttf"), (30, 28, 26))
    paint(m, text_mask("HOTSPOT AHEAD", 11, (64, 76), "arialbd.ttf"), (30, 28, 26))
    paint(m, text_mask("- M.E.G. -", 10, (64, 100), "arialbd.ttf"), (120, 30, 24))
    return m


def all_textures():
    t = {}
    t["red_brick"] = brick(1000, (1.25, 0.66, 0.4), 1.55)
    t["brown_brick"] = brick(1010, (1.05, 0.74, 0.5), 1.15)
    t["glazed_tile"] = glazed(1020)
    t["glazed_tile_wainscot"] = wainscot(1030)
    t["cinder_block"] = cinder(1040)
    t["purpified_brick"] = purpified(1050)
    t["meg_wall"] = meg_wall(1060)
    t["meg_sign"] = meg_sign(1061)
    t["dusty_floor"] = dusty_floor(1070)
    t["station_ceiling"] = ceiling(1080)
    t["sparking_outlet"] = outlet(1090)
    t["bars_white"] = bars((214, 214, 206))
    t["bars_black"] = bars((40, 40, 42))
    t["lock"] = metal((50, 50, 52), 0.5)
    t["conduit"] = conduit()
    t["sludge_steel"] = sludge_steel(1100)
    t["sludge_steel_wet"] = sludge_steel(1101, wet=True)
    t["electrical_front"] = electrical_front(1110)
    t["electrical_side"] = metal((140, 144, 140), 0.45)
    t["cable_black"] = cable(1120)
    t["copper"] = copper()
    t["tray"] = l2.tray(1130)
    t["cables"] = l2.cables(1131)
    t["light_housing"] = metal((70, 68, 64), 0.45)
    t["light_lens"] = lens((236, 236, 220), 0.85)
    t["light_lens_off"] = mt.panel((92, 90, 80), (120, 118, 108), 0)
    t["floodlight_lens"] = lens((255, 252, 240), 1.0)
    t["bulb_warm"] = l2.lamp((255, 206, 140), 1.0)
    t["bulb_off"] = l2.lamp((110, 100, 88), 0.0)
    t["cage"] = metal((44, 44, 42), 0.4)
    t["generator_side"] = generator_side(1140)
    t["generator_top"] = green_steel(1141)
    t["insulator"] = insulator()
    t["switchgear_front"] = switchgear_front(1150, False)
    t["switchgear_front_top"] = switchgear_front(1151, True)
    t["green_steel"] = green_steel(1152)
    t["brass"] = brass(1160)
    t["brass_top"] = brass(1161, top=True)
    t["valve"] = l2.red_paint(1162)
    t["conveyor_side"] = conveyor_side()
    t["press_front"] = press_front(1170)
    t["press_side"] = metal((96, 98, 100), 0.4)
    t["chain"] = rusty_chain()
    t["parts_crate_side"] = parts_crate(False)
    t["parts_crate_top"] = parts_crate(True)
    t["pillar"] = pillar_side(1180)
    t["pillar_end"] = stone_end()
    t["pillar_capital"] = capital(1181)
    t["pillar_base"] = capital(1182)
    t["marble"] = marble(1190)
    t["bronze"] = bronze(1200)
    t["stained_glass"] = stained_glass()
    t["car_paint"] = car_paint(1210)
    t["car_glass"] = car_glass()
    t["car_front"] = car_front()
    t["car_rear"] = car_rear()
    t["tyre"] = tyre()
    for top, part in ((False, 0), (True, 1)):
        t[f"elevator_{part}"] = elevator_door(top)
        t[f"base_gamma_door_{part}"] = door_steel(top, 1220 + top, True)
    t["cot_canvas"] = canvas((92, 100, 64), 1230)
    t["meg_crate_side"] = meg_crate(False)
    t["meg_crate_top"] = meg_crate(True)
    t["table_top"] = metal((130, 132, 130), 0.5)
    t["meg_arrow_r"] = meg_arrow(True)
    t["meg_arrow_l"] = meg_arrow(False)
    t["meg_warning"] = meg_warning()
    return t


def belt_frames():
    return [belt(i * 4) for i in range(4)]


def flicker_frames():
    on = lens((236, 236, 220), 0.85)
    dim = lens((180, 180, 168), 0.3)
    off = mt.panel((92, 90, 80), (120, 118, 108), 0)
    return [on, off, on, dim, on, off]


FLICKER = [(0, 30), (1, 2), (2, 6), (3, 2), (4, 40), (5, 4)]


def save_animated(name, frames, timing):
    parts = []
    for i, frame in enumerate(frames):
        mt.save(f"_frame{i}", frame)
        images = []
        for suffix in ("", "_n", "_s"):
            with Image.open(TEXTURES / f"_frame{i}{suffix}.png") as image:
                images.append(image.copy())
        parts.append(images)
    for k, suffix in enumerate(("", "_n", "_s")):
        first = parts[0][k]
        strip = Image.new(first.mode, (SIZE, SIZE * len(frames)))
        for i, images in enumerate(parts):
            strip.paste(images[k], (0, SIZE * i))
        strip.save(TEXTURES / f"{name}{suffix}.png", optimize=True)
        meta = {"animation": {"interpolate": False, "frames": [{"index": index, "time": time} for index, time in timing]}}
        (TEXTURES / f"{name}{suffix}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf8")
    for i in range(len(frames)):
        for suffix in ("", "_n", "_s"):
            (TEXTURES / f"_frame{i}{suffix}.png").unlink()


def save_cutout(name, m, alpha):
    mt.save(name, m)
    rgb = np.clip(np.rint(m.color), 0, 255).astype("uint8")
    a = (alpha > 0.5).astype("uint8") * 255
    Image.fromarray(np.dstack([rgb, a]), "RGBA").save(TEXTURES / f"{name}.png", optimize=True)


# Models (things that face a way are drawn facing south: mounted on the north side, sticking out towards +Z)

def face(texture, **extra):
    return {"texture": texture, "uv": [0, 0, 16, 16], **extra}


def cube(texture):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": NS + texture}}


def column(side, end):
    return {"parent": "minecraft:block/cube_column", "textures": {"side": NS + side, "end": NS + end}}


def custom(textures, elements, particle=None):
    first = next(iter(textures.values()))
    return {"parent": "minecraft:block/block", "textures": {"particle": NS + (particle or first), **{k: NS + v for k, v in textures.items()}},
            "elements": elements}


def pipe(frm, to, texture):
    return l2.pipe_body(frm, to, texture)


def bars_model(texture, gate=False, part=0, opened=False):
    elements = []
    for x0 in (1, 5, 9, 13):
        elements.append(el((x0, 0, 7.25), (x0 + 1.5, 16, 8.75), sides("#bars")))
    for y0 in (1, 13.5) if part == 0 else (0.5, 13.5):
        elements.append(el((0, y0, 7), (16, y0 + 1.5, 9), sides("#bars")))
    if gate:
        elements.append(el((0, 0, 6.75), (1.5, 16, 9.25), sides("#bars")))  # hinge post
        if part == 0:
            elements.append(el((12, 12, 6), (15, 16, 10), sides("#lock")))
    if opened:
        # Swung open against its hinge: the same bars, turned to run along Z at the hinge side.
        turned = []
        for e in elements:
            (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
            turned.append({**e, "from": [z0 - 6.5, y0, x0], "to": [z1 - 6.5, y1, x1]})
        elements = [{**e, "from": [max(0, e["from"][0]), e["from"][1], e["from"][2]], "to": [max(1.5, e["to"][0]), e["to"][1], e["to"][2]]}
                    for e in turned]
    return custom({"bars": texture, "lock": "lock"}, elements)


def fixture(lens_name, wide=False):
    glow = {"shade": False, "light_emission": 15} if lens_name != "light_lens_off" else {}
    rim = {"texture": "#lens", "uv": [0, 4.5, 16, 5.5]}
    z0, z1 = (4, 12) if wide else (5, 11)
    return custom({"housing": "light_housing", "lens": lens_name}, [
        el((2.5, 14, 7.6), (3.3, 16, 8.4), sides("#housing", ("north", "south", "east", "west"))),
        el((12.7, 14, 7.6), (13.5, 16, 8.4), sides("#housing", ("north", "south", "east", "west"))),
        el((0.5, 12, z0), (15.5, 14, z1), sides("#housing", ("north", "south", "east", "west", "up"))),
        el((1, 11.2, z0 + 0.5), (15, 12, z1 - 0.5), {"down": "#lens", "north": rim, "south": rim, "east": rim, "west": rim}, **glow),
    ], particle="light_housing")


def bulb(glass):
    glow = {"shade": False, "light_emission": 15} if glass != "bulb_off" else {}
    cage = [el((x0, 9, z0), (x0 + 0.4, 13, z0 + 0.4), sides("#cage", ("north", "south", "east", "west")))
            for x0, z0 in ((5.6, 5.6), (10, 5.6), (5.6, 10), (10, 10))]
    cage.append(el((5.6, 8.6, 5.6), (10.4, 9, 10.4), sides("#cage")))
    return custom({"glass": glass, "cage": "cage", "socket": "light_housing"}, [
        el((5, 14, 5), (11, 16, 11), sides("#socket")),
        el((6.5, 9.5, 6.5), (9.5, 14, 9.5), sides("#glass"), **glow),
        *cage,
    ], particle=glass)


def all_models():
    m = {}
    for name, texture in (("red_brick_wall", "red_brick"), ("brown_brick_wall", "brown_brick"), ("glazed_tile_wall", "glazed_tile"),
                          ("glazed_tile_wainscot", "glazed_tile_wainscot"), ("cinder_block_wall", "cinder_block"),
                          ("purpified_brick", "purpified_brick"), ("meg_wall", "meg_wall"), ("meg_sign", "meg_sign"),
                          ("dusty_floor", "dusty_floor"), ("station_ceiling", "station_ceiling"), ("sparking_outlet", "sparking_outlet")):
        m[name] = cube(texture)
    m["low_ceiling"] = {"parent": "minecraft:block/slab_top", "textures": {"bottom": NS + "station_ceiling", "top": NS + "red_brick", "side": NS + "red_brick"}}
    m["wall_conduit"] = custom({"pipe": "conduit"}, [pipe((0, 8, 0.5), (16, 10.5, 3), "#pipe"), pipe((0, 12, 1), (16, 14.5, 3.5), "#pipe"),
                                                     el((7.5, 7, 0), (8.5, 15, 4), sides("#pipe"))])
    for name, texture in (("sludge_pipe", "sludge_steel"), ("leaking_sludge_pipe", "sludge_steel_wet")):
        m[name] = custom({"pipe": texture, "bracket": "lock"}, [pipe((0, 6, 0.5), (16, 13, 7.5), "#pipe"), el((7, 4.5, 0), (9, 14.5, 8), sides("#bracket"))])
    m["electrical_box"] = custom({"front": "electrical_front", "side": "electrical_side", "pipe": "conduit"}, [
        el((3, 0, 0), (13, 14, 5), {**sides("#side", ("north", "east", "west", "up", "down")), "south": face("#front")}),
        el((7, 14, 1.5), (9, 16, 3.5), sides("#pipe")),
    ], particle="electrical_side")
    m["live_wire"] = custom({"cable": "cable_black", "copper": "copper"}, [
        el((0, 0, 6), (9, 1.2, 7.2), sides("#cable")), el((8, 0, 6), (9.2, 1.2, 14), sides("#cable")),
        el((8, 0, 13), (16, 1.2, 14.2), sides("#cable")), el((3, 0, 6.2), (3.5, 1.6, 4), sides("#copper")),
        el((4, 0, 5), (4.6, 1.4, 3), sides("#copper")),
    ], particle="cable_black")
    m["sludge_puddle"] = custom({"sludge": "sludge_puddle"}, [el((0, 0, 0), (16, 0.6, 16), {"up": face("#sludge"), "down": face("#sludge")})])
    m["white_bars"] = bars_model("bars_white")
    m["black_bars"] = bars_model("bars_black")
    for opened in (False, True):
        for part in (0, 1):
            m[f"cage_gate_{'lower' if part == 0 else 'upper'}{'_open' if opened else ''}"] = bars_model("bars_white", True, part, opened)
    m["station_light"] = fixture("light_lens")
    m["flickering_station_light"] = fixture("light_lens_flicker")
    m["broken_station_light"] = fixture("light_lens_off")
    m["station_floodlight"] = fixture("floodlight_lens", wide=True)
    m["cage_bulb"] = bulb("bulb_warm")
    m["dead_cage_bulb"] = bulb("bulb_off")
    m["hanging_cable_tray"] = custom({"tray": "tray", "cables": "cables", "rod": "lock"}, [
        el((0, 10, 2), (16, 11, 14), sides("#tray")), el((0, 11, 2), (16, 13, 3), sides("#tray")), el((0, 11, 13), (16, 13, 14), sides("#tray")),
        pipe((0, 11, 3), (16, 12.6, 13), "#cables"), el((7.5, 13, 7.5), (8.5, 16, 8.5), sides("#rod"))])
    m["ceiling_sludge_pipe"] = custom({"pipe": "sludge_steel", "rod": "lock"}, [
        pipe((0, 8, 5), (16, 14, 11), "#pipe"), el((7.5, 14, 7.5), (8.5, 16, 8.5), sides("#rod"))])
    m["hoist_chain"] = custom({"chain": "chain"}, [el((7, 0, 7), (9, 16, 9), sides("#chain"))])
    m["hoist_hook"] = custom({"chain": "chain", "hook": "lock"}, [
        el((7, 8, 7), (9, 16, 9), sides("#chain")), el((5.5, 4, 7), (10.5, 8, 9), sides("#hook")),
        el((5.5, 2, 7), (7, 4, 9), sides("#hook")), el((9, 1, 7), (10.5, 4, 9), sides("#hook"))])
    for part in (0, 1):
        m[f"station_generator_{part}"] = cube("generator_side") if part == 0 else custom(
            {"side": "generator_side", "top": "generator_top", "ins": "insulator"},
            [el((0, 0, 0), (16, 10, 16), {**sides("#side", ("north", "south", "east", "west", "down")), "up": face("#top")}),
             *[el((x, 10, z), (x + 4, 16, z + 4), sides("#ins")) for x, z in ((2, 2), (10, 2), (2, 10), (10, 10))]], particle="generator_side")
        front = "switchgear_front" if part == 0 else "switchgear_front_top"
        m[f"switchgear_{part}"] = {"parent": "minecraft:block/cube", "textures": {"particle": NS + "green_steel", "south": NS + front,
                                   "north": NS + "green_steel", "east": NS + "green_steel", "west": NS + "green_steel",
                                   "up": NS + "green_steel", "down": NS + "green_steel"}}
        m[f"brass_boiler_{part}"] = column("brass", "brass_top")
        m[f"assembly_press_{part}"] = {"parent": "minecraft:block/cube", "textures": {
            "particle": NS + "press_side", "south": NS + "press_front", "north": NS + "press_side", "east": NS + "press_side",
            "west": NS + "press_side", "up": NS + "press_side", "down": NS + "press_side"}}
        m[f"station_elevator_{part}"] = cube(f"elevator_{part}")
        m[f"base_gamma_door_{part}"] = cube(f"base_gamma_door_{part}")
    m["boiler_valve"] = custom({"brass": "brass", "wheel": "valve"}, [
        el((7, 0, 7), (9, 6, 9), sides("#brass")), el((3, 6, 3), (13, 7, 4), sides("#wheel")), el((3, 6, 12), (13, 7, 13), sides("#wheel")),
        el((3, 6, 4), (4, 7, 12), sides("#wheel")), el((12, 6, 4), (13, 7, 12), sides("#wheel")), el((7.5, 6, 3.5), (8.5, 7.5, 12.5), sides("#wheel")),
        el((5, 7, 5), (11, 11, 11), sides("#brass"))])
    m["conveyor_belt"] = custom({"side": "conveyor_side", "belt": "conveyor_belt"}, [
        el((0, 0, 0), (16, 9, 16), {**sides("#side", ("north", "south", "east", "west", "down")), "up": face("#belt")})], particle="conveyor_side")
    m["parts_crate"] = {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": NS + "parts_crate_side", "top": NS + "parts_crate_top", "bottom": NS + "parts_crate_top"}}
    m["meg_supply_crate"] = {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": NS + "meg_crate_side", "top": NS + "meg_crate_top", "bottom": NS + "meg_crate_top"}}
    m["sanctum_pillar"] = column("pillar", "pillar_end")
    m["sanctum_pillar_base"] = column("pillar_base", "pillar_end")
    m["sanctum_pillar_top"] = column("pillar_capital", "pillar_end")
    m["angel_pedestal"] = column("marble", "marble")
    m["angel_statue_0"] = custom({"b": "bronze"}, [
        el((4, 0, 4), (12, 2, 12), sides("#b")), el((4.5, 2, 5.5), (11.5, 7, 10.5), sides("#b")), el((5.5, 7, 6.5), (10.5, 16, 9.5), sides("#b"))])
    m["angel_statue_1"] = custom({"b": "bronze"}, [
        el((5.5, 0, 6.5), (10.5, 7, 9.5), sides("#b")), el((6.5, 7, 6.8), (9.5, 10.5, 9.8), sides("#b")),
        el((-3, 2, 9), (6, 13, 10), sides("#b"), rotation={"angle": 22.5, "axis": "z", "origin": [6, 2, 9.5]}),
        el((10, 2, 9), (19, 13, 10), sides("#b"), rotation={"angle": -22.5, "axis": "z", "origin": [10, 2, 9.5]}),
        el((10.5, 6, 7.5), (12, 12, 9), sides("#b")),
        el((10, 11, 7.6), (18, 12.4, 8.9), sides("#b"), rotation={"angle": 45, "axis": "z", "origin": [11, 11, 8]}),
        el((3.5, 4, 7.5), (5.5, 9, 9), sides("#b"))])
    m["sanctum_stained_glass"] = custom({"g": "stained_glass"}, [el((0, 0, 0), (16, 16, 16), sides("#g"), shade=False, light_emission=12)])
    m["meg_cot"] = custom({"canvas": "cot_canvas", "frame": "lock"}, [
        el((1, 5, 0), (15, 6.5, 16), sides("#canvas")),
        *[el((x, 0, z), (x + 1, 5, z + 1), sides("#frame")) for x, z in ((1, 0), (14, 0), (1, 15), (14, 15))]], particle="cot_canvas")
    m["meg_table"] = custom({"top": "table_top"}, [
        el((0, 13, 0), (16, 15, 16), sides("#top")), *[el((x, 0, z), (x + 1.5, 13, z + 1.5), sides("#top")) for x, z in ((1, 1), (13.5, 1), (1, 13.5), (13.5, 13.5))]])
    m.update(car_models())
    for name, texture in (("meg_arrow_sign_r", "meg_arrow_r"), ("meg_arrow_sign_l", "meg_arrow_l"), ("meg_warning_sign", "meg_warning")):
        m[name] = custom({"plate": texture, "rim": "lock"}, [
            el((2, 3, 0), (14, 13, 1), {**sides("#rim", ("north", "east", "west", "up", "down")), "south": face("#plate")})], particle=texture)
    return m


def car_models():
    """Eight blocks of a car facing south (+Z): parts 0-5 the body, front row first, the left (east) block first;
    6-7 the cabin over the middle row. The car's middle line runs between the two columns."""
    models = {}
    for part in range(8):
        cabin = part >= 6
        along = 1 if cabin else part // 2
        left = (part % 2) == 0
        x0, x1 = (0, 14) if left else (2, 16)
        outer = "east" if left else "west"
        if cabin:
            elements = [el((x0 if left else 3, 0, 1), (13 if left else x1, 7, 15),
                           {**sides("#glass", ("north", "south", outer)), "up": "#paint", "down": "#paint",
                            ("west" if left else "east"): "#paint"}),
                        el((x0, 7, 0), (x1 if left else x1, 8.5, 16), sides("#paint"))]
        else:
            body = {f: "#paint" for f in ("north", "south", "east", "west", "up", "down")}
            if along == 0:
                body["south"] = face("#front")
            if along == 2:
                body["north"] = face("#rear")
            elements = [el((x0, 3, 0), (x1, 10 if along != 1 else 11, 16), body)]
            if along != 1:
                wx0, wx1 = (11, 14.5) if left else (1.5, 5)
                elements.append(el((wx0, 0, 3), (wx1, 6, 13), {**sides("#tyre", ("north", "south", "up", "down")), outer: face("#tyre"),
                                                               ("west" if left else "east"): face("#tyre")}))
        models[f"rusted_car_{part}"] = custom({"paint": "car_paint", "glass": "car_glass", "front": "car_front", "rear": "car_rear",
                                                 "tyre": "tyre"}, elements, particle="car_paint")
    return models


# Blockstates

FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}


def variant(model, y=0):
    return {"model": NS + model, **({"y": y} if y else {})}


def simple(model):
    return {"variants": {"": variant(model)}}


def facing_state(model):
    return {"variants": {f"facing={f}": variant(model, y) for f, y in FACING_Y.items()}}


def axis_state(model):
    return {"variants": {"axis=x": variant(model), "axis=z": variant(model, 90)}}


def part_state(name, parts, turns=True):
    return {"variants": {f"facing={f},part={p}": variant(f"{name}_{p if p < parts else 0}", y if turns else 0)
                         for f, y in FACING_Y.items() for p in range(8)}}


def gate_state():
    variants = {}
    for axis, y in (("x", 0), ("z", 90)):
        for half in ("lower", "upper"):
            for opened in (False, True):
                variants[f"axis={axis},half={half},open={str(opened).lower()}"] = variant(f"cage_gate_{half}{'_open' if opened else ''}", y)
    return {"variants": variants}


CUBES = ["red_brick_wall", "brown_brick_wall", "glazed_tile_wall", "glazed_tile_wainscot", "cinder_block_wall", "purpified_brick",
         "meg_wall", "meg_sign", "dusty_floor", "station_ceiling", "sparking_outlet", "live_wire", "sludge_puddle", "parts_crate",
         "meg_supply_crate", "sanctum_pillar", "sanctum_pillar_base", "sanctum_pillar_top", "angel_pedestal", "sanctum_stained_glass"]
AXIS = ["low_ceiling", "white_bars", "black_bars", "station_light", "flickering_station_light", "broken_station_light", "cage_bulb",
        "dead_cage_bulb", "station_floodlight", "hanging_cable_tray", "ceiling_sludge_pipe", "hoist_chain", "hoist_hook"]
FACING = ["wall_conduit", "sludge_pipe", "leaking_sludge_pipe", "electrical_box", "boiler_valve", "conveyor_belt", "meg_cot", "meg_table"]
PARTS = {"station_generator": 2, "switchgear": 2, "brass_boiler": 2, "assembly_press": 2, "angel_statue": 2, "rusted_car": 8,
         "station_elevator": 2, "base_gamma_door": 2}
# Nothing breaks these, so they have no loot table.
NO_LOOT = {"white_bars", "black_bars", "cage_gate", "angel_statue", "sanctum_stained_glass", "station_elevator", "base_gamma_door", "rusted_car"}


def all_blockstates():
    b = {name: simple(name) for name in CUBES}
    b.update({name: axis_state(name) for name in AXIS})
    b.update({name: facing_state(name) for name in FACING})
    b.update({name: part_state(name, parts) for name, parts in PARTS.items()})
    b["cage_gate"] = gate_state()
    b["meg_arrow_sign"] = {"variants": {f"facing={f},right={str(r).lower()}": variant(f"meg_arrow_sign_{'r' if r else 'l'}", y)
                                        for f, y in FACING_Y.items() for r in (False, True)}}
    b["meg_warning_sign"] = {"variants": {f"facing={f},right={str(r).lower()}": variant("meg_warning_sign", y)
                                          for f, y in FACING_Y.items() for r in (False, True)}}
    b["meg_table"] = simple("meg_table")
    return b


def item_model(name):
    if name in PARTS:
        return NS + f"{name}_0"
    if name == "cage_gate":
        return NS + "cage_gate_lower"
    if name == "meg_arrow_sign":
        return NS + "meg_arrow_sign_r"
    return NS + name


# Writing

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def preview(textures):
    names = ["red_brick", "brown_brick", "glazed_tile", "glazed_tile_wainscot", "cinder_block", "purpified_brick",
             "dusty_floor", "station_ceiling", "sparking_outlet", "meg_wall", "meg_sign", "electrical_front",
             "generator_side", "switchgear_front", "switchgear_front_top", "brass", "press_front", "pillar",
             "pillar_capital", "marble", "bronze", "stained_glass", "car_front", "car_rear",
             "elevator_0", "elevator_1", "base_gamma_door_0", "base_gamma_door_1", "parts_crate_side", "meg_crate_side"]
    columns, gap, side = 6, 8, SIZE * 2
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (side + gap) + gap, rows * (side + gap) + gap), (30, 30, 30))
    for i, name in enumerate(names):
        tile = Image.fromarray(np.clip(np.rint(textures[name].color), 0, 255).astype("uint8"), "RGB")
        for dx in (0, SIZE):
            for dy in (0, SIZE):
                sheet.paste(tile, (gap + i % columns * (side + gap) + dx, gap + i // columns * (side + gap) + dy))
    out = mt.ROOT / "docs/project/5-levels/level3/textures.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, optimize=True)


def check(blockstates):
    prefix = "noclip_backrooms:block/"
    for path in MODELS.glob("*.json"):
        for value in json.loads(path.read_text(encoding="utf8")).get("textures", {}).values():
            if value.startswith(NS):
                for suffix in ("", "_n", "_s"):
                    assert (TEXTURES / f"{value[len(NS):]}{suffix}.png").is_file(), (path.name, value, suffix)
    for name, state in blockstates.items():
        for v in state["variants"].values():
            assert (ASSETS / f"models/block/{v['model'][len(prefix):]}.json").is_file(), (name, v["model"])
        item = json.loads((ASSETS / f"items/{name}.json").read_text(encoding="utf8"))
        assert (ASSETS / f"models/block/{item['model']['model'][len(prefix):]}.json").is_file(), name


def loot_table(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}]}], "random_sequence": f"noclip_backrooms:blocks/{name}"}


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES  # make_textures writes Level 0's textures; point its writer at Level 3's folder
    textures = all_textures()
    for name, material in textures.items():
        mt.save(name, material)
    save_animated("light_lens_flicker", flicker_frames(), FLICKER)
    save_animated("conveyor_belt", belt_frames(), [(i, 3) for i in range(4)])
    save_cutout("sludge_puddle", *sludge())
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
    print(f"{len(textures) + 3} materials, {len(models)} models, {len(blockstates)} blocks")


if __name__ == "__main__":
    main()
