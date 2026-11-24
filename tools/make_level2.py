"""Draws every Level 2 texture at 128x128 and writes the Level 2 block models, blockstates and item models.

    py tools/make_level2.py

Uses the drawing helpers and the labPBR writer of make_textures.py and a few of make_level1.py (Pillow, NumPy).
Textures go to textures/block/level2/, models to models/block/level2/. Also writes docs/levels/level2/textures.png.
Looks follow the photos on the Wikidot Level 2 page: chalky concrete and old brick, cream-wrapped and pale green
pipes, rusty steel and red valves, cable trays, white steel doors with embossed panels.
"""

import copy
import json
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.dont_write_bytecode = True  # no __pycache__ next to the scripts
import make_level1 as l1  # noqa: E402
import make_textures as mt  # noqa: E402
from make_textures import SIZE, X, Y, box, clouds, flat, inside, metal, noise, paint, step, tint  # noqa: E402

ASSETS = mt.ASSETS
TEXTURES = ASSETS / "textures/block/level2"
MODELS = ASSETS / "models/block/level2"
NS = "noclip_backrooms:block/level2/"
el, sides = l1.el, l1.sides


def shaded(m, strength=0.3):
    """Darkens towards the top and bottom edges, so a box reads as a round pipe."""
    tint(m, np.ones((SIZE, SIZE)), np.stack([0.7 + 0.3 * np.sin(np.pi * (Y + 0.5) / SIZE) ** 0.6] * 3, -1) ** (strength / 0.3))
    return m


def text_mask(text, size, centre, font="impact.ttf", rotate=0.0):
    """Text drawn at 4x and brought down, as a 0..1 mask."""
    big = Image.new("L", (SIZE * 4, SIZE * 4))
    draw = ImageDraw.Draw(big)
    try:
        face = ImageFont.truetype(font, size * 4)
    except OSError:
        try:
            face = ImageFont.truetype("arialbd.ttf", size * 4)
        except OSError:
            face = ImageFont.load_default(size * 4)
    draw.text((centre[0] * 4, centre[1] * 4), text, fill=255, font=face, anchor="mm")
    if rotate:
        big = big.rotate(rotate, resample=Image.Resampling.BICUBIC, center=(centre[0] * 4, centre[1] * 4))
    return np.asarray(big.resize((SIZE, SIZE), Image.Resampling.LANCZOS), float) / 255


def spray(m, mask, color, seed, drips=True):
    """Spray paint: soft overspray around the strokes, speckle, and a few runs."""
    image = Image.fromarray((np.clip(mask, 0, 1) * 255).astype("uint8"))
    over = np.asarray(image.filter(ImageFilter.GaussianBlur(2.2)), float) / 255
    speckle = step(0.2, 0.8, noise(seed, 128)) * 0.5 + 0.5
    coat = np.maximum(mask * (0.85 + 0.15 * speckle), over * 0.45 * speckle)
    if drips:
        rng = np.random.default_rng(seed)
        ys, xs = np.nonzero(mask > 0.6)
        for k in rng.choice(len(xs), min(5, len(xs)), replace=False) if len(xs) else []:
            x0, y0, length = xs[k], ys[k], rng.uniform(8, 26)
            run = (1 - step(0.5, 1.4, np.abs(X - x0))) * step(y0, y0 + 2, Y) * (1 - step(y0 + length * 0.6, y0 + length, Y))
            coat = np.maximum(coat, run * 0.85)
    paint(m, coat, color, smooth=0.35, porosity=10)
    return m


# Walls, floor, ceiling

def chalky(seed, color=(190, 186, 178)):
    """Withered concrete that leaves chalk on your fingers: pale powder streaks, grime low down."""
    m = l1.concrete(color, seed, smooth=0.1, porosity=60)
    streak = step(0.25, 0.75, noise(seed + 10, 24, 3) * 0.6 + noise(seed + 11, 64, 8) * 0.4)
    tint(m, streak * 0.5, (1.07, 1.07, 1.06))
    grime = step(60, 128, Y) * (0.45 + 0.4 * clouds(seed + 12, 4))
    tint(m, grime * 0.55, (0.83, 0.8, 0.75))
    return m


def stained(seed):
    m = chalky(seed)
    l1.runs(m, seed + 20, color=(0.8, 0.73, 0.6), top=(0, 24))
    field = clouds(seed + 21, 5)
    flake = step(0.22, 0.3, field) * inside()
    tint(m, flake, (0.76, 0.75, 0.72))
    paint(m, flake, height=-0.8)
    rim = (1 - step(0.0, 0.025, np.abs(field - 0.22))) * inside()
    tint(m, rim, (1.12, 1.12, 1.1))
    return l1.crack(m, seed + 22, vertical=True)


def recolor(m, palette, gain=1.0):
    luminance = m.color.mean(-1, keepdims=True)
    m.color = luminance * np.array(palette, float) * gain
    return m


def muddy_brick(seed):
    m = recolor(l1.bricks(seed, aged=True), (1.0, 0.78, 0.56), 1.02)
    mud = step(-0.1, 0.5, clouds(seed + 30, 4)) * step(50, 128, Y)
    tint(m, mud * 0.6, (0.72, 0.62, 0.5))
    return m


def black_brick(seed):
    m = recolor(l1.bricks(seed + 3, aged=True), (0.42, 0.4, 0.39))
    soot = step(-0.2, 0.4, clouds(seed + 31, 3))
    tint(m, soot * 0.5, (0.75, 0.74, 0.73))
    return m


def painted_brick(seed):
    m = l1.block_wall((224, 222, 214), seed, course=16, length=32, mortar=(0.9, 0.9, 0.88), depth=-1.0)
    chips = step(0.62, 0.72, noise(seed + 40, 24) * 0.6 + noise(seed + 41, 96) * 0.4)
    paint(m, chips, (128, 124, 116), height=-0.6, smooth=0.1)
    grime = step(70, 128, Y) * (0.4 + 0.5 * clouds(seed + 42, 3))
    tint(m, grime * 0.45, (0.8, 0.78, 0.72))
    return m


def floor(seed):
    m = l1.concrete((124, 122, 116), seed, smooth=0.25, porosity=35)
    l1.scuffs(m, seed + 50)
    dirt = step(-0.1, 0.5, clouds(seed + 51, 4))
    tint(m, dirt * 0.4, (0.82, 0.8, 0.76))
    return m


def debris(seed):
    m = floor(seed)
    rng = np.random.default_rng(seed + 52)
    for _ in range(6):
        cx, cy, w, h, a = *rng.uniform(16, 112, 2), *rng.uniform(5, 14, 2), rng.uniform(0, np.pi)
        u, v = (X - cx) * np.cos(a) + (Y - cy) * np.sin(a), -(X - cx) * np.sin(a) + (Y - cy) * np.cos(a)
        sheet = (1 - step(w - 0.8, w, np.abs(u))) * (1 - step(h - 0.8, h, np.abs(v)))
        shade = rng.uniform(0.85, 1.0)
        paint(m, sheet, np.array([226, 220, 204]) * shade, height=0.3, smooth=0.2, porosity=50)
        lines = sheet * (1 - step(0.3, 0.8, np.abs(v % 3 - 1.5))) * 0.5
        tint(m, lines, (0.75, 0.78, 0.85))
    for _ in range(14):
        cx, cy = rng.uniform(6, 122, 2)
        shard = 1 - step(0.6, 1.8, np.abs(X - cx) + np.abs(Y - cy) * 1.6)
        paint(m, shard, (210, 222, 220), height=0.5, smooth=0.95, f0=40)
    return m


def damp(seed):
    return l1.wet(floor(seed), seed + 53)


def ceiling(seed):
    """The underside of the slab, with cable trays running along it."""
    m = l1.concrete((92, 90, 86), seed, smooth=0.15, porosity=40)
    rng = np.random.default_rng(seed + 60)
    for top in (14, 74):
        tray = box(-2, top, 130, top + 38)
        paint(m, tray, (132, 134, 132), height=1, smooth=0.5, f0=120)
        for rail in (top, top + 37):
            paint(m, box(-2, rail - 1, 130, rail + 2), (150, 152, 150), height=2.5, f0=160)
        y = top + 3
        while y < top + 34:
            width = rng.uniform(1.6, 4.0)
            color = [(30, 30, 30), (54, 52, 50), (176, 92, 40), (200, 196, 186), (62, 70, 112)][rng.integers(0, 5)]
            sag = 0.6 * noise(int(y) + seed, 3, 1)
            cable = 1 - step(width * 0.4, width * 0.55, np.abs(Y - y - sag))
            paint(m, cable * tray, color, height=2 + width * 0.4, smooth=0.45, f0=12)
            y += width + rng.uniform(0.2, 1.5)
    return m


def void():
    m = flat((13, 13, 15), 0.08, 4, 20)
    m.color *= (1 + noise(70, 64)[..., None] * 0.08)
    return m


# Pipes

def wrap(seed):
    """Canvas-wrapped insulation, banded every 32 pixels, yellowed and dirty."""
    m = flat((214, 205, 182), 0.12, 6, 50)
    weave = noise(seed, 128, 64) * 0.5 + noise(seed + 1, 64) * 0.5
    m.color *= (1 + weave[..., None] * 0.06)
    m.height += weave * 0.4
    stain = step(-0.05, 0.45, clouds(seed + 2, 4))
    tint(m, stain * 0.55, (0.86, 0.8, 0.66))
    # One strap where two lengths of insulation meet, at the block edge, so a run reads as one long pipe.
    strap = 1 - step(1.5, 3.0, np.minimum(X, SIZE - X))
    paint(m, strap, (150, 146, 134), height=0.8, smooth=0.4, f0=120)
    return shaded(m)


def green_paint(seed):
    m = metal((148, 178, 150), 0.3)
    chips = step(0.64, 0.74, noise(seed, 32) * 0.6 + noise(seed + 1, 96) * 0.4)
    paint(m, chips, (110, 104, 96), height=-0.4)
    tint(m, step(0.0, 0.5, clouds(seed + 2, 4)) * 0.4, (0.84, 0.82, 0.74))
    joint = 1 - step(0.6, 1.6, np.abs(X % 64 - 32))
    paint(m, joint, (110, 132, 112), height=1.2)
    return shaded(m)


def steel(seed, wet=False, hot=False):
    m = metal((122, 120, 116), 0.45)
    rust = step(0.1, 0.5, clouds(seed, 5)) * (0.6 + 0.4 * noise(seed + 1, 64))
    paint(m, rust * 0.8, (122, 78, 48), smooth=0.15, f0=10, porosity=30)
    if wet:
        streak = step(0.4, 0.7, noise(seed + 2, 16, 2)) * step(64, 128, Y)
        paint(m, streak, (70, 60, 50), smooth=0.85)
    if hot:
        tint(m, np.ones((SIZE, SIZE)), (0.78, 0.7, 0.62))
        paint(m, step(0.3, 0.6, noise(seed + 3, 24)) * 0.6, (96, 72, 60), smooth=0.2)
    return shaded(m)


def red_paint(seed):
    m = metal((158, 42, 34), 0.35)
    chips = step(0.66, 0.76, noise(seed, 32) * 0.6 + noise(seed + 1, 96) * 0.4)
    paint(m, chips, (96, 84, 76), height=-0.4)
    return shaded(m)


def grey_paint(seed):
    m = metal((164, 164, 158), 0.35)
    chips = step(0.66, 0.76, noise(seed, 32) * 0.6 + noise(seed + 1, 96) * 0.4)
    paint(m, chips, (110, 100, 90), height=-0.4)
    tint(m, step(0.0, 0.5, clouds(seed + 2, 4)) * 0.45, (0.82, 0.78, 0.7))
    return shaded(m)


def copper(seed):
    m = metal((176, 112, 72), 0.55)
    verdigris = step(0.2, 0.55, clouds(seed, 5))
    paint(m, verdigris * 0.7, (92, 146, 122), smooth=0.2, porosity=20)
    return shaded(m)


def tray(seed):
    m = metal((150, 152, 150), 0.5)
    holes = (1 - step(1.2, 2.2, np.hypot(X % 16 - 8, Y % 32 - 16)))
    paint(m, holes, (40, 40, 40), height=-1.5)
    return m


def cables(seed):
    m = flat((40, 40, 40), 0.4, 10)
    rng = np.random.default_rng(seed)
    y = 0
    while y < SIZE:
        width = rng.uniform(5, 14)
        color = [(32, 32, 32), (58, 56, 54), (180, 94, 40), (206, 200, 188), (60, 70, 120), (140, 30, 30)][rng.integers(0, 6)]
        body = box(-2, y, 130, y + width, 0.8)
        roundness = np.sin(np.pi * np.clip((Y - y) / width, 0, 1))
        paint(m, body, color, height=0)
        m.height += body * roundness * 3
        tint(m, body, np.stack([0.75 + 0.3 * roundness] * 3, -1))
        y += width
    return m


def bracket():
    m = metal((64, 64, 66), 0.4)
    tint(m, step(0.0, 0.5, clouds(120, 3)) * 0.5, (0.8, 0.66, 0.54))
    return m


def lamp(color, glow):
    m = flat(color, 0.9, 30)
    m.color *= (1 + 0.15 * np.exp(-((X - 64) ** 2 + (Y - 64) ** 2) / 900))[..., None]
    m.glow += glow
    return m


# Machines

def machine_side(seed):
    m = metal((104, 110, 108), 0.35)
    seam = np.maximum(1 - step(0.6, 1.5, np.minimum(X, SIZE - 1 - X)), 1 - step(0.6, 1.5, np.minimum(Y, SIZE - 1 - Y)))
    paint(m, seam, (64, 68, 66), height=-1)
    for cx in (8, 120):
        for cy in (8, 120):
            paint(m, 1 - step(1.5, 2.5, np.hypot(X - cx, Y - cy)), (140, 144, 140), height=1, f0=200)
    rust = step(0.2, 0.5, clouds(seed, 4)) * step(80, 128, Y)
    paint(m, rust * 0.7, (112, 74, 48), smooth=0.15, porosity=25)
    return m


def machine_vents(seed):
    m = machine_side(seed)
    for top in range(28, 104, 10):
        slot = box(24, top, 104, top + 5)
        paint(m, slot, (24, 24, 24), height=-2)
        paint(m, box(24, top - 1, 104, top + 1), (136, 140, 136), height=1)
    plate = box(40, 108, 88, 120)
    paint(m, plate, (196, 186, 120), height=0.5, smooth=0.6, f0=160)
    return m


def machine_gauges(seed):
    m = machine_side(seed)
    for cx, cy, r in ((40, 48, 18), (88, 48, 18)):
        d = np.hypot(X - cx, Y - cy)
        paint(m, 1 - step(r, r + 1.5, d), (60, 60, 60), height=2, f0=200)
        paint(m, 1 - step(r - 3, r - 2, d), (226, 222, 206), height=1.5, smooth=0.8)
        ticks = (1 - step(r - 6, r - 5, d)) * step(r - 9, r - 8, d) * (1 - step(0.15, 0.3, np.abs(np.sin(np.arctan2(Y - cy, X - cx) * 6))))
        paint(m, ticks, (40, 40, 40))
        angle = np.deg2rad(200 + (cx % 7) * 20)
        along, across = (X - cx) * np.cos(angle) + (Y - cy) * np.sin(angle), -(X - cx) * np.sin(angle) + (Y - cy) * np.cos(angle)
        needle = (1 - step(0.6, 1.2, np.abs(across))) * step(-2, 0, along) * (1 - step(r - 8, r - 6, along))
        paint(m, needle, (170, 30, 24))
    for cx, color, glow in ((36, (180, 30, 24), 0.6), (52, (40, 120, 40), 0.0), (68, (190, 150, 40), 0.0)):
        paint(m, 1 - step(3.5, 4.5, np.hypot(X - cx, Y - 92)), color, height=1.5, smooth=0.9, glow=glow)
    paint(m, box(80, 84, 104, 100), (36, 36, 36), height=1)
    return m


def machine_top(seed):
    m = machine_side(seed)
    tint(m, step(-0.2, 0.6, clouds(seed + 5, 3)), (0.82, 0.8, 0.76))
    return m


# Doors

def door_base(color, top, seed, frame=True):
    """A painted steel door: embossed panels, paint worn at the edges, grime low down."""
    m = metal(color, 0.32)
    m.porosity[:] = 15
    panels = [(18, 18, 110, 62), (18, 72, 110, 118)] if top else [(18, 10, 110, 56), (18, 66, 110, 112)]
    for left, upper, right, lower in panels:
        raised = box(left, upper, right, lower, 1.5)
        m.height += raised * 1.8
        edge = raised * (1 - box(left + 2, upper + 2, right - 2, lower - 2, 1))
        tint(m, edge, (0.88, 0.88, 0.87))
    wear = step(0.55, 0.7, noise(seed, 24) * 0.6 + noise(seed + 1, 96) * 0.4) * (0.4 + 0.6 * (1 - inside()))
    paint(m, wear, (110, 108, 104), height=-0.3, smooth=0.5, f0=180)
    if not top:
        grime = step(50, 128, Y) * (0.4 + 0.5 * clouds(seed + 2, 4))
        tint(m, grime * 0.5, (0.8, 0.78, 0.72))
    if frame:
        jamb = np.maximum(1 - step(4, 6, X), step(SIZE - 7, SIZE - 5, X))
        if top:
            jamb = np.maximum(jamb, 1 - step(4, 6, Y))
        paint(m, jamb, (70, 72, 74), height=2.5, smooth=0.4, f0=160)
    return m


def handle(m, top):
    if top:
        return m
    paint(m, box(96, 2, 110, 26, 1), (90, 92, 92), height=2, f0=220, smooth=0.7)
    paint(m, box(86, 10, 110, 16, 1), (150, 152, 150), height=3.5, f0=230, smooth=0.8)
    return m


def steel_door(top, locked=False):
    m = handle(door_base((214, 212, 202), top, 300 + top), top)
    if locked and top:
        plate = box(32, 30, 96, 52, 1)
        paint(m, plate, (204, 40, 32), height=1, smooth=0.6)
        paint(m, text_mask("DO NOT", 9, (64, 37), "arialbd.ttf") * plate, (240, 236, 228))
        paint(m, text_mask("ENTER", 9, (64, 46), "arialbd.ttf") * plate, (240, 236, 228))
    if locked and not top:
        paint(m, box(84, 18, 96, 34, 1), (76, 76, 76), height=3, f0=200)
        shackle = (1 - step(1, 2, np.abs(np.hypot(X - 90, Y - 18) - 5))) * (1 - step(17, 18, Y))
        paint(m, shackle, (150, 150, 150), height=3.5, f0=230)
    return m


def marked_door(level, top):
    """A steel door an earlier wanderer marked with the level behind it."""
    m = handle(door_base((206, 204, 194), top, 320 + int(level) + top), top)
    if top:
        spray(m, text_mask(level, 60, (60, 70), rotate=-6), (176, 34, 28), 330 + int(level))
    else:
        arrow = text_mask("→", 40, (58, 74), "arialbd.ttf", rotate=-4)
        spray(m, arrow, (176, 34, 28), 340 + int(level), drips=False)
    return m


def painted_door(color, top, seed):
    return handle(door_base(color, top, seed), top)


def plain_metal(top):
    m = door_base((128, 130, 130), top, 360)
    if not top:
        paint(m, box(20, 4, 108, 14, 1.5), (176, 178, 176), height=3, f0=230, smooth=0.8)
    return m


def wood_door(top, seed, color=(120, 84, 52), peel=None, knob="brass"):
    m = flat(color, 0.2, 8, 40)
    rng = np.random.default_rng(seed)
    plank = (X // 32).astype(int)
    m.color *= rng.uniform(0.86, 1.1, 4)[plank][..., None]
    grain = noise(seed, 128, 6) * 0.6 + noise(seed + 1, 64, 3) * 0.4
    m.color *= (1 + grain[..., None] * 0.12)
    m.height += grain * 0.6
    gap = 1 - step(0.6, 1.6, np.minimum(X % 32, 32 - X % 32))
    paint(m, gap, np.array(color) * 0.45, height=-1.5)
    if peel is not None:
        coat = step(0.3, 0.38, clouds(seed + 2, 5) + 0.2 * noise(seed + 3, 64))
        paint(m, coat, peel, height=0.5, smooth=0.35)
    jamb = np.maximum(1 - step(4, 6, X), step(SIZE - 7, SIZE - 5, X))
    if top:
        jamb = np.maximum(jamb, 1 - step(4, 6, Y))
    paint(m, jamb, np.array(color) * 0.6, height=2.5)
    if not top and knob:
        if knob == "brass":
            paint(m, 1 - step(5, 6.5, np.hypot(X - 102, Y - 14)), (186, 150, 70), height=3.5, smooth=0.8, f0=230)
        else:
            # The knob broke off its spindle and hangs by the plate.
            paint(m, box(98, 4, 106, 22, 1), (120, 112, 96), height=1.5, f0=200)
            paint(m, 1 - step(1.2, 2, np.hypot(X - 102, Y - 12)), (30, 26, 22), height=-2)
            paint(m, 1 - step(5, 6.5, np.hypot(X - 99, Y - 32)), (176, 146, 80), height=3, smooth=0.7, f0=220)
    return m


def plywood(top):
    m = flat((196, 160, 110), 0.18, 8, 45)
    layers = noise(370, 6, 128) * 0.5 + noise(371, 18, 64) * 0.5
    m.color *= (1 + layers[..., None] * 0.18)
    knots = step(0.82, 0.9, noise(372 + top, 10))
    paint(m, knots, (130, 92, 54))
    edge = np.maximum(1 - step(3, 5, X), step(SIZE - 6, SIZE - 4, X))
    paint(m, edge, (214, 186, 140), height=1.5)
    for nx in (10, 118):
        for ny in (20, 64, 108):
            paint(m, 1 - step(1, 1.8, np.hypot(X - nx, Y - ny)), (90, 88, 84), f0=200)
    if not top:
        paint(m, box(96, 6, 112, 14, 1), (70, 70, 72), height=2.5, f0=200)
    return m


def giant_door(part, rainbow):
    """One of four blocks of a floor-to-ceiling door: steel, riveted, or painted in rainbow bands."""
    if rainbow:
        m = flat((200, 200, 200), 0.3, 10, 20)
        global_y = (3 - part) * SIZE + Y
        hue = (global_y / (4 * SIZE)) * 6
        bands = [(208, 52, 48), (226, 132, 40), (230, 206, 60), (70, 170, 76), (52, 110, 200), (128, 70, 170)]
        index = np.clip(hue.astype(int), 0, 5)
        m.color = np.array(bands, float)[index]
        chips = step(0.64, 0.74, noise(380 + part, 24) * 0.6 + noise(381 + part, 96) * 0.4)
        paint(m, chips, (150, 120, 90), height=-0.3)
    else:
        m = metal((112, 116, 116), 0.4)
        for rx in (12, 116):
            for ry in range(8, 128, 24):
                paint(m, 1 - step(1.6, 2.6, np.hypot(X - rx, Y - ry)), (150, 152, 150), height=1.2, f0=220)
        seam = 1 - step(0.6, 1.4, np.abs(X - 64))
        paint(m, seam, (60, 62, 62), height=-1)
        tint(m, step(0.2, 0.5, clouds(390 + part, 4)) * 0.5, (0.86, 0.76, 0.66))
        if part == 2:
            digits = text_mask("70", 64, (64, 62), "arialbd.ttf")
            paint(m, digits, (70, 72, 72), height=-2.5, smooth=0.2)
    jamb = np.maximum(1 - step(3, 5, X), step(SIZE - 6, SIZE - 4, X))
    if part == 3:
        jamb = np.maximum(jamb, 1 - step(3, 5, Y))
    paint(m, jamb, (60, 60, 60), height=2.5)
    return m


def boiler_hatch(top):
    m = metal((70, 66, 62), 0.3)
    tint(m, step(0.0, 0.5, clouds(400 + top, 4)) * 0.6, (0.8, 0.62, 0.5))
    if top:
        d = np.hypot(X - 64, Y - 58)
        paint(m, 1 - step(22, 24, d), (40, 40, 40), height=2)
        paint(m, 1 - step(18, 19, d), (226, 218, 196), height=1.5, smooth=0.8)
        needle = (1 - step(0.6, 1.2, np.abs((X - 64) * 0.8 + (Y - 58) * 0.6))) * (1 - step(14, 16, d)) * step(0, 1, X - 64)
        paint(m, needle, (180, 24, 20))
        plate = box(30, 92, 98, 116, 1)
        paint(m, plate, (220, 180, 40), height=1)
        paint(m, text_mask("HOT", 14, (64, 104), "arialbd.ttf") * plate, (30, 30, 30))
    else:
        door = box(16, 12, 112, 116, 1.5)
        paint(m, door, (56, 52, 48), height=2)
        for top_ in range(30, 100, 14):
            slit = box(30, top_, 98, top_ + 6)
            paint(m, slit, (255, 150, 50), height=-1.5, smooth=0.6, glow=1.0)
        paint(m, box(100, 54, 118, 74, 1), (110, 108, 104), height=3.5, f0=220)
    return m


def blade_door(top):
    m = handle(door_base((150, 150, 146), top, 410 + top), top)
    if top:
        # A long curved blade driven in at an angle, its wrapped grip sticking out.
        along, across = (X - 40) * 0.8 + (Y - 30) * 0.6, -(X - 40) * 0.6 + (Y - 30) * 0.8
        curve = across - 0.004 * along ** 2
        blade = (1 - step(3.5, 4.5, np.abs(curve))) * step(0, 2, along) * (1 - step(70, 74, along))
        edge = (1 - step(0.4, 1.0, np.abs(curve + 3))) * blade
        paint(m, blade, (190, 194, 196), height=3, smooth=0.9, f0=240)
        paint(m, edge, (232, 236, 238), smooth=0.95, f0=250)
        grip = (1 - step(3, 4, np.abs(across))) * step(-26, -24, along) * (1 - step(-2, 0, along))
        paint(m, grip, (54, 36, 26), height=4, smooth=0.3)
        wraps = grip * (1 - step(0.5, 1.2, np.abs(along % 5 - 2.5)))
        tint(m, wraps, (0.6, 0.55, 0.5))
        guard = (1 - step(6, 7, np.abs(across))) * step(-2, -1, along) * (1 - step(1, 2, along))
        paint(m, guard, (120, 100, 60), height=4.5, f0=220)
        crack = (1 - step(0.5, 1.2, np.abs(across + 0.3 * along))) * step(-8, 0, -along) * step(-20, -8, along)
        paint(m, crack, (40, 40, 40), height=-1)
    return m


def elevator(top):
    m = flat((176, 178, 180), 0.75, 230)
    brushed = noise(420, 128, 2) * 0.5 + noise(421, 64, 1) * 0.5
    m.color *= (1 + brushed[..., None] * 0.04)
    seam = 1 - step(0.5, 1.2, np.abs(X - 64))
    paint(m, seam, (40, 40, 40), height=-2)
    frame = np.maximum(1 - step(6, 9, X), step(SIZE - 10, SIZE - 7, X))
    if top:
        frame = np.maximum(frame, 1 - step(6, 9, Y))
        panel = box(44, 14, 84, 30, 1)
        paint(m, panel, (24, 24, 24), height=1)
        paint(m, text_mask("▲▼", 10, (64, 22), "arialbd.ttf") * panel, (240, 120, 40), glow=0.8)
    paint(m, frame, (120, 122, 124), height=2.5, smooth=0.6)
    smudge = step(0.3, 0.6, clouds(422 + top, 5)) * 0.4
    tint(m, smudge, (0.9, 0.9, 0.88))
    return m


def window():
    """Daylight where there should be none: a bright pane behind a cross of dark bars."""
    m = flat((236, 240, 232), 0.9, 30)
    glare = np.exp(-((X - 60) ** 2 + (Y - 44) ** 2) / 1600)
    m.color = np.array([206, 222, 236]) * (1 - glare[..., None]) + np.array([255, 252, 240]) * glare[..., None]
    m.glow += 1.0
    bars = np.maximum(np.maximum(1 - step(4, 7, X), step(SIZE - 8, SIZE - 5, X)),
                      np.maximum(np.maximum(1 - step(4, 7, Y), step(SIZE - 8, SIZE - 5, Y)),
                                 np.maximum(1 - step(2, 4, np.abs(X - 64)), 1 - step(2, 4, np.abs(Y - 64)))))
    paint(m, bars, (34, 34, 32), height=3, smooth=0.3, glow=0.0)
    return m


# Props

def drum(top):
    m = metal((62, 74, 82), 0.35)
    if top:
        paint(m, 1 - step(1.5, 3, np.abs(np.hypot(X - 64, Y - 64) - 56)), (48, 58, 64), height=1)
        paint(m, 1 - step(5, 6.5, np.hypot(X - 40, Y - 40)), (150, 150, 146), height=1, f0=230)
        paint(m, step(-0.1, 0.3, l1.blob(430, (26, 20), 0.6)), (24, 22, 20), smooth=0.9)
    else:
        for y in (36, 92):
            paint(m, box(-2, y - 4, 130, y + 4), (52, 62, 70), height=1.5)
        tint(m, step(-0.2, 0.4, clouds(431, 4)) * 0.5, (0.78, 0.7, 0.62))
        drips = step(0.6, 0.75, noise(432, 24, 2)) * (1 - step(20, 60, Y))
        paint(m, drips, (24, 22, 20), smooth=0.9)
    return m


def scrap(seed):
    m = metal((110, 108, 104), 0.35)
    rng = np.random.default_rng(seed)
    for _ in range(18):
        cx, cy, w, h = *rng.uniform(0, 128, 2), *rng.uniform(4, 18, 2)
        piece = box(cx - w, cy - h / 3, cx + w, cy + h / 3, 1)
        color = [(150, 152, 150), (120, 76, 48), (80, 80, 80), (170, 120, 70), (60, 90, 70)][rng.integers(0, 5)]
        paint(m, piece, color, height=rng.uniform(0.5, 3), f0=rng.choice([20, 200]))
    return m


def mark(level, right):
    """An earlier wanderer's spray paint: the level's number and an arrow, on nothing (the wall shows through)."""
    m = flat((176, 34, 28), 0.35, 10, 10)
    number = text_mask(level, 64, (34 if right else 94, 56), rotate=-5)
    arrow = arrow_mask(right)
    paint_mask = np.clip(number + arrow, 0, 1)
    rng = np.random.default_rng(int(level) * 2 + right)
    for _ in range(3):
        x0, y0, length = rng.uniform(30, 100), rng.uniform(60, 78), rng.uniform(10, 30)
        drip = (1 - step(0.5, 1.4, np.abs(X - x0))) * step(y0, y0 + 2, Y) * (1 - step(y0 + length * 0.6, y0 + length, Y))
        paint_mask = np.maximum(paint_mask, drip * (paint_mask[int(y0), int(x0)] > 0.3))
    speckle = 0.7 + 0.3 * noise(int(level) + 40, 128)
    return m, paint_mask * speckle


def arrow_mask(right):
    """A thick sprayed arrow, a shaft and a head, on the other half of the mark from the number."""
    big = Image.new("L", (SIZE * 4, SIZE * 4))
    draw = ImageDraw.Draw(big)
    if right:
        draw.polygon([(250, 225), (390, 225), (390, 290), (250, 290)], fill=255)
        draw.polygon([(370, 165), (490, 257), (370, 350)], fill=255)
    else:
        draw.polygon([(122, 225), (262, 225), (262, 290), (122, 290)], fill=255)
        draw.polygon([(142, 165), (22, 257), (142, 350)], fill=255)
    return np.asarray(big.resize((SIZE, SIZE), Image.Resampling.LANCZOS), float) / 255


def save_cutout(name, m, alpha):
    mt.save(name, m)
    rgb = np.clip(np.rint(m.color), 0, 255).astype("uint8")
    a = (alpha > 0.45).astype("uint8") * 255
    Image.fromarray(np.dstack([rgb, a]), "RGBA").save(TEXTURES / f"{name}.png", optimize=True)


def all_textures():
    t = {}
    t["utility_wall"] = chalky(500)
    t["stained_utility_wall"] = stained(510)
    t["muddy_brick_wall"] = muddy_brick(520)
    t["black_brick_wall"] = black_brick(530)
    t["painted_brick_wall"] = painted_brick(540)
    t["utility_floor"] = floor(550)
    t["debris_utility_floor"] = debris(560)
    t["damp_utility_floor"] = damp(570)
    t["utility_ceiling"] = ceiling(580)
    t["void_concrete"] = void()
    t["pipe_wrap"] = wrap(600)
    t["pipe_green"] = green_paint(610)
    t["pipe_steel"] = steel(620)
    t["pipe_steel_wet"] = steel(621, wet=True)
    t["pipe_steel_hot"] = steel(622, hot=True)
    t["pipe_red"] = red_paint(630)
    t["pipe_grey"] = grey_paint(635)
    t["pipe_copper"] = copper(640)
    t["valve_red"] = red_paint(650)
    t["blue_lamp"] = lamp((90, 150, 255), 1.0)
    t["cable_tray"] = tray(660)
    t["cables"] = cables(670)
    t["bracket"] = bracket()
    t["machine_side"] = machine_side(680)
    t["machine_vents"] = machine_vents(681)
    t["machine_gauges"] = machine_gauges(682)
    t["machine_top"] = machine_top(683)
    t["light_housing"] = metal((74, 74, 72), 0.45)
    t["light_lens"] = mt.panel((228, 230, 224), (255, 255, 250), 1.0)
    t["light_lens_warm"] = mt.panel((238, 202, 150), (255, 216, 160), 1.0)
    t["light_lens_off"] = mt.panel((98, 95, 80), (136, 133, 116), 0)
    t["bulb_warm"] = lamp((255, 214, 150), 1.0)
    t["bulb_off"] = lamp((120, 110, 96), 0.0)
    t["bulb_violet"] = lamp((186, 110, 255), 1.0)
    t["bulb_cage"] = metal((50, 50, 48), 0.4)
    t["bulb_socket"] = metal((40, 40, 38), 0.3)
    for top, part in ((False, "bottom"), (True, "top")):
        t[f"steel_door_{part}"] = steel_door(top)
        t[f"locked_steel_door_{part}"] = steel_door(top, locked=True)
        for level in ("1", "3", "4"):
            t[f"marked_door_{level}_{part}"] = marked_door(level, top)
        t[f"plywood_door_{part}"] = plywood(top)
        t[f"boiler_hatch_{part}"] = boiler_hatch(top)
        t[f"aged_wooden_door_{part}"] = wood_door(top, 700, (78, 58, 40), peel=(70, 104, 84))
        t[f"plain_metal_door_{part}"] = plain_metal(top)
        t[f"blue_door_{part}"] = painted_door((56, 94, 170), top, 710 + top)
        t[f"blade_door_{part}"] = blade_door(top)
        t[f"broken_knob_door_{part}"] = wood_door(top, 720, (132, 92, 58), knob="broken")
        t[f"pink_door_{part}"] = painted_door((232, 150, 186), top, 730 + top)
        t[f"elevator_door_{part}"] = elevator(top)
    for part in range(4):
        t[f"engraved_giant_door_{part}"] = giant_door(part, rainbow=False)
        t[f"rainbow_giant_door_{part}"] = giant_door(part, rainbow=True)
    t["tunnel_window"] = window()
    t["crate_side"] = l1.crate(False, 740)
    t["crate_top"] = l1.crate(True, 741)
    t["drum_side"] = drum(False)
    t["drum_top"] = drum(True)
    t["scrap"] = scrap(750)
    return t


# Models

def full(uv=(0, 0, 16, 16)):
    return {"uv": list(uv)}


def face(texture, **extra):
    return {"texture": texture, "uv": [0, 0, 16, 16], **extra}


def pipe_body(frm, to, texture, **extra):
    """A long box running along X whose sides each show the whole (shaded) pipe texture."""
    faces = {f: face(texture) for f in ("north", "south", "up", "down")}
    faces.update({f: {"texture": texture, "uv": [0, 0, to[2] - frm[2], to[1] - frm[1]]} for f in ("east", "west")})
    return el(frm, to, faces, **extra)


def cube(texture):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": NS + texture}}


def big_pipe_model(texture):
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + texture, "pipe": NS + texture, "bracket": NS + "bracket"},
            "elements": [
                pipe_body((0, 2.5, 1.5), (16, 13.5, 12.5), "#pipe"),
                el((7, 0, 3), (9, 2.5, 11), sides("#bracket")),
            ]}


def steel_pipes_model(texture, extra=(), extra_textures=None):
    textures = {"particle": NS + texture, "low": NS + texture, "high": NS + "pipe_grey", "bracket": NS + "bracket"}
    textures.update(extra_textures or {})
    return {"parent": "minecraft:block/block", "textures": textures,
            "elements": [
                pipe_body((0, 1.5, 1.5), (16, 6.5, 6.5), "#low"),
                pipe_body((0, 9, 2.5), (16, 14, 7.5), "#high"),
                el((7.5, 0, 0), (8.5, 16, 1), sides("#bracket")),
                el((7.5, 6.5, 1), (8.5, 7.5, 4), sides("#bracket")),
                *extra,
            ]}


def valve_elements():
    wheel = "#wheel"
    return [
        el((7.5, 10.5, 7.5), (8.5, 11.5, 11), sides(wheel)),
        el((5, 8, 11), (11, 9, 12), sides(wheel)),
        el((5, 13, 11), (11, 14, 12), sides(wheel)),
        el((5, 9, 11), (6, 13, 12), sides(wheel)),
        el((10, 9, 11), (11, 13, 12), sides(wheel)),
        el((7.5, 9, 11.2), (8.5, 13, 11.8), sides(wheel)),
        el((6, 10.5, 11.2), (10, 11.5, 11.8), sides(wheel)),
    ]


def blue_lamp_elements():
    glow = {"shade": False, "light_emission": 15}
    return [el((2.5, 6.5, 6.5), (4.5, 8.5, 8.5), sides("#lamp"), **glow),
            el((11.5, 6.5, 6.5), (13.5, 8.5, 8.5), sides("#lamp"), **glow)]


def tray_model():
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "cable_tray", "tray": NS + "cable_tray", "cables": NS + "cables", "bracket": NS + "bracket"},
            "elements": [
                el((0, 5, 0), (16, 6.5, 11), sides("#tray")),
                el((0, 6.5, 10), (16, 9, 11), sides("#tray")),
                pipe_body((0, 6.5, 1), (16, 8.8, 9.5), "#cables"),
                el((3, 6.5, 0), (4, 12, 1), sides("#bracket")),
                el((12, 6.5, 0), (13, 12, 1), sides("#bracket")),
            ]}


def small_pipes_model():
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "pipe_copper", "copper": NS + "pipe_copper", "steel": NS + "pipe_steel", "bracket": NS + "bracket"},
            "elements": [
                pipe_body((0, 2, 1), (16, 4.5, 3.5), "#copper"),
                pipe_body((0, 6, 2), (16, 8, 4), "#steel"),
                pipe_body((0, 10, 1), (16, 13, 4), "#steel"),
                el((7.5, 0, 0), (8.5, 16, 1), sides("#bracket")),
            ]}


def machine_model(front):
    return {"parent": "minecraft:block/cube",
            "textures": {"particle": NS + "machine_side", "south": NS + front, "north": NS + "machine_side", "east": NS + "machine_side",
                         "west": NS + "machine_side", "up": NS + "machine_top", "down": NS + "machine_side"}}


def fixture_model(lens):
    glow = {"shade": False, "light_emission": 15} if lens != "light_lens_off" else {}
    rim = {"texture": "#lens", "uv": [0, 4.5, 16, 5.5]}
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "light_housing", "housing": NS + "light_housing", "lens": NS + lens},
            "elements": [
                el((2.5, 14, 7.6), (3.3, 16, 8.4), sides("#housing", ("north", "south", "east", "west"))),
                el((12.7, 14, 7.6), (13.5, 16, 8.4), sides("#housing", ("north", "south", "east", "west"))),
                el((0.5, 11.5, 5), (15.5, 14, 11), sides("#housing", ("north", "south", "east", "west", "up"))),
                el((1, 10.6, 5.5), (15, 11.5, 10.5), {"down": "#lens", "north": rim, "south": rim, "east": rim, "west": rim}, **glow),
            ]}


def bulb_model(glass):
    glow = {"shade": False, "light_emission": 15} if glass != "bulb_off" else {}
    cage = []
    for x0, z0 in ((5.6, 5.6), (10, 5.6), (5.6, 10), (10, 10)):
        cage.append(el((x0, 3, z0), (x0 + 0.4, 8, z0 + 0.4), sides("#cage", ("north", "south", "east", "west"))))
    cage.append(el((5.6, 3, 5.6), (10.4, 3.4, 10.4), sides("#cage")))
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + glass, "glass": NS + glass, "socket": NS + "bulb_socket", "cage": NS + "bulb_cage"},
            "elements": [
                el((7.7, 10, 7.7), (8.3, 16, 8.3), sides("#socket", ("north", "south", "east", "west"))),
                el((6.8, 7.6, 6.8), (9.2, 10, 9.2), sides("#socket")),
                el((6.2, 3.6, 6.2), (9.8, 7.6, 9.8), sides("#glass"), **glow),
                *cage,
            ]}


def window_model():
    return {"parent": "minecraft:block/block", "textures": {"particle": NS + "tunnel_window", "pane": NS + "tunnel_window"},
            "elements": [el((0, 0, 0), (16, 16, 16), sides("#pane"), shade=False, light_emission=15)]}


def drum_model():
    return {"parent": "minecraft:block/block", "textures": {"particle": NS + "drum_side", "side": NS + "drum_side", "top": NS + "drum_top"},
            "elements": [el((2.5, 0, 2.5), (13.5, 15, 13.5), {**sides("#side", ("north", "south", "east", "west")),
                                                               "up": "#top", "down": "#top"})]}


def scrap_model():
    boxes = [((1, 0, 2), (9, 3, 9)), ((6, 0, 6), (15, 2.5, 14)), ((3, 2.5, 4), (11, 5, 8)), ((9, 2, 2), (14, 6, 6)), ((2, 0, 10), (7, 4, 15))]
    return {"parent": "minecraft:block/block", "textures": {"particle": NS + "scrap", "s": NS + "scrap"},
            "elements": [el(a, b, sides("#s")) for a, b in boxes]}


DOOR_MODELS = ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
               "top_left", "top_left_open", "top_right", "top_right_open")

BANK_BLOCKS = {
    "insulated_pipe": big_pipe_model("pipe_wrap"),
    "green_pipe": big_pipe_model("pipe_green"),
    "steel_pipes": steel_pipes_model("pipe_steel"),
    "valve_pipes": steel_pipes_model("pipe_steel", valve_elements(), {"wheel": NS + "valve_red"}),
    "leaking_pipes": steel_pipes_model("pipe_steel_wet"),
    "steam_pipes": steel_pipes_model("pipe_steel_hot"),
    "blue_light_pipes": steel_pipes_model("pipe_steel", blue_lamp_elements(), {"lamp": NS + "blue_lamp"}),
    "conduit_tray": tray_model(),
    "small_pipes": small_pipes_model(),
}
CUBES = {"utility_wall": "utility_wall", "stained_utility_wall": "stained_utility_wall", "muddy_brick_wall": "muddy_brick_wall",
         "black_brick_wall": "black_brick_wall", "painted_brick_wall": "painted_brick_wall", "utility_floor": "utility_floor",
         "debris_utility_floor": "debris_utility_floor", "damp_utility_floor": "damp_utility_floor",
         "utility_ceiling": "utility_ceiling", "void_concrete": "void_concrete"}
LIGHTS = {"utility_light": fixture_model("light_lens"), "warm_utility_light": fixture_model("light_lens_warm"),
          "broken_utility_light": fixture_model("light_lens_off"), "caged_bulb": bulb_model("bulb_warm"),
          "broken_caged_bulb": bulb_model("bulb_off"), "emergency_light": bulb_model("bulb_violet")}
EXITS = {"marked_door_1": 2, "marked_door_3": 2, "marked_door_4": 2, "plywood_door": 2, "engraved_giant_door": 4,
         "boiler_hatch": 2, "aged_wooden_door": 2, "plain_metal_door": 2, "blue_door": 2, "rainbow_giant_door": 4,
         "blade_door": 2, "broken_knob_door": 2, "pink_door": 2, "elevator_door": 2}


def all_models():
    m = {name: cube(texture) for name, texture in CUBES.items()}
    m.update(BANK_BLOCKS)
    m["machine_cabinet"] = machine_model("machine_vents")
    m["machine_panel"] = machine_model("machine_gauges")
    m.update(LIGHTS)
    for door in ("steel_door", "locked_steel_door"):
        for part in DOOR_MODELS:
            m[f"{door}_{part}"] = {"parent": f"minecraft:block/door_{part}",
                                   "textures": {"bottom": NS + f"{door}_bottom", "top": NS + f"{door}_top"}}
    for name, height in EXITS.items():
        for part in range(height):
            texture = f"{name}_{part}" if height == 4 else f"{name}_{'bottom' if part == 0 else 'top'}"
            m[f"{name}_{part}"] = cube(texture)
    m["tunnel_window"] = window_model()
    for level in range(3):
        for right in (False, True):
            name = f"wanderer_mark_{level}_{'r' if right else 'l'}"
            m[name] = {"parent": "minecraft:block/block", "textures": {"particle": NS + name, "paint": NS + name},
                       "elements": [el((0, 0, 0.02), (16, 16, 0.12), {"south": face("#paint")})]}
    m["wooden_crate"] = {"parent": "minecraft:block/cube_bottom_top",
                         "textures": {"side": NS + "crate_side", "top": NS + "crate_top", "bottom": NS + "crate_top"}}
    m["oil_drum"] = drum_model()
    m["scrap_heap"] = scrap_model()
    return m


# Blockstates

FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}


def facing_state(model):
    return {"variants": {f"facing={f}": {"model": NS + model, **({"y": y} if y else {})} for f, y in FACING_Y.items()}}


def axis_state(model):
    return {"variants": {"axis=x": {"model": NS + model}, "axis=z": {"model": NS + model, "y": 90}}}


def door_state(door):
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in base.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    y = y0 if not opened else (y0 + (90 if hinge == "left" else 270)) % 360
                    model = NS + f"{door}_{part}_{hinge}{'_open' if opened else ''}"
                    variants[f"facing={facing},half={half},hinge={hinge},open={str(opened).lower()}"] = {"model": model, **({"y": y} if y else {})}
    return {"variants": variants}


def exit_state(name, height):
    return {"variants": {f"part={p}": {"model": NS + f"{name}_{min(p, height - 1)}"} for p in range(4)}}


def all_blockstates():
    b = {name: {"variants": {"": {"model": NS + name}}} for name in CUBES}
    for name in BANK_BLOCKS:
        b[name] = facing_state(name)
    b["machine_cabinet"] = facing_state("machine_cabinet")
    b["machine_panel"] = facing_state("machine_panel")
    for name in LIGHTS:
        b[name] = axis_state(name)
    for door in ("steel_door", "locked_steel_door"):
        b[door] = door_state(door)
    for name, height in EXITS.items():
        b[name] = exit_state(name, height)
    for name in ("tunnel_window", "wooden_crate", "oil_drum", "scrap_heap"):
        b[name] = {"variants": {"": {"model": NS + name}}}
    b["wanderer_mark"] = {"variants": {
        f"facing={f},level={level},right={str(right).lower()}": {"model": NS + f"wanderer_mark_{level}_{'r' if right else 'l'}", **({"y": y} if y else {})}
        for f, y in FACING_Y.items() for level in range(3) for right in (False, True)}}
    return b


def item_model(name):
    if name in ("steel_door", "locked_steel_door"):
        return NS + f"{name}_bottom_left"
    if name in EXITS:
        return NS + f"{name}_0"
    if name == "wanderer_mark":
        return NS + "wanderer_mark_1_r"
    return NS + name


# Writing

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def preview(textures):
    names = ["utility_wall", "stained_utility_wall", "muddy_brick_wall", "black_brick_wall", "painted_brick_wall", "utility_ceiling",
             "utility_floor", "debris_utility_floor", "damp_utility_floor", "pipe_wrap", "pipe_green", "pipe_steel",
             "pipe_grey", "pipe_copper", "cables", "machine_vents", "machine_gauges", "tunnel_window",
             "steel_door_top", "locked_steel_door_top", "marked_door_1_top", "marked_door_3_top", "marked_door_4_top", "blade_door_top",
             "steel_door_bottom", "locked_steel_door_bottom", "plywood_door_bottom", "aged_wooden_door_bottom", "broken_knob_door_bottom", "boiler_hatch_bottom",
             "blue_door_top", "pink_door_top", "elevator_door_top", "plain_metal_door_bottom", "engraved_giant_door_2", "rainbow_giant_door_1"]
    columns, gap, side = 6, 8, SIZE * 2
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (side + gap) + gap, rows * (side + gap) + gap), (30, 30, 30))
    for i, name in enumerate(names):
        tile = Image.fromarray(np.clip(np.rint(textures[name].color), 0, 255).astype("uint8"), "RGB")
        for dx in (0, SIZE):
            for dy in (0, SIZE):
                sheet.paste(tile, (gap + i % columns * (side + gap) + dx, gap + i // columns * (side + gap) + dy))
    out = mt.ROOT / "docs/levels/level2/textures.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, optimize=True)


def check(blockstates):
    """Every texture a Level 2 model uses exists with both companions; every model a blockstate or item names exists."""
    prefix = "noclip_backrooms:block/"
    for path in MODELS.glob("*.json"):
        data = json.loads(path.read_text(encoding="utf8"))
        for value in data.get("textures", {}).values():
            if value.startswith(NS):
                for suffix in ("", "_n", "_s"):
                    assert (TEXTURES / f"{value[len(NS):]}{suffix}.png").is_file(), (path.name, value, suffix)
    for name, state in blockstates.items():
        for variant in state["variants"].values():
            if variant["model"].startswith(prefix):
                assert (ASSETS / f"models/block/{variant['model'][len(prefix):]}.json").is_file(), (name, variant["model"])
        item = json.loads((ASSETS / f"items/{name}.json").read_text(encoding="utf8"))
        assert (ASSETS / f"models/block/{item['model']['model'][len(prefix):]}.json").is_file(), name


def loot_table(name, door=False):
    """Blocks drop themselves; a door only from its lower half, like vanilla doors."""
    pool = {"rolls": 1.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}]}
    if door:
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": f"noclip_backrooms:{name}",
                                   "properties": {"half": "lower"}})
    return {"type": "minecraft:block", "pools": [pool], "random_sequence": f"noclip_backrooms:blocks/{name}"}


# Ways to other levels and the windows cannot be broken, so they have no loot table.
NO_LOOT = set(EXITS) | {"tunnel_window", "wanderer_mark"}


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES  # make_textures writes Level 0's textures; point its writer at Level 2's folder
    textures = all_textures()
    for name, material in textures.items():
        mt.save(name, material)
    for level, number in enumerate(("1", "3", "4")):
        for right in (False, True):
            save_cutout(f"wanderer_mark_{level}_{'r' if right else 'l'}", *mark(number, right))
    preview(textures)
    models = all_models()
    for name, model in models.items():
        write_json(MODELS / f"{name}.json", model)
    blockstates = all_blockstates()
    for name, state in blockstates.items():
        write_json(ASSETS / f"blockstates/{name}.json", state)
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": item_model(name)}})
        if name not in NO_LOOT:
            write_json(mt.ROOT / f"src/main/resources/data/noclip_backrooms/loot_table/blocks/{name}.json",
                       loot_table(name, door=name in ("steel_door", "locked_steel_door")))
    check(blockstates)
    print(f"{len(textures)} materials, {len(models)} models, {len(blockstates)} blocks")


if __name__ == "__main__":
    main()
