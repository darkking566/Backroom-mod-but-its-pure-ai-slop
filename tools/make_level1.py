"""Draws every Level 1 texture at 128x128 and writes the Level 1 block models, blockstates and item models.

    python tools/make_level1.py

Uses the drawing helpers and the labPBR writer of make_textures.py (Pillow and NumPy).
Textures go to textures/block/level1/, models to models/block/level1/. Also writes docs/level1_textures.png.
Level 0 files are not touched.
"""

import json
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.dont_write_bytecode = True  # no __pycache__ next to the scripts
import make_textures as mt  # noqa: E402
from make_textures import SIZE, X, Y, box, box_x, clouds, flat, inside, metal, noise, paint, step, tint  # noqa: E402

ASSETS = mt.ASSETS
TEXTURES = ASSETS / "textures/block/level1"
MODELS = ASSETS / "models/block/level1"
NS = "noclip_backrooms:block/level1/"


# Concrete

def concrete(color, seed, smooth=0.22, porosity=35):
    m = flat(color, smooth, 10, porosity)
    grain = noise(seed, 128) * 0.45 + noise(seed + 1, 64) * 0.3 + noise(seed + 2, 16) * 0.25
    m.color *= (1 + grain * 0.07 + clouds(seed + 3, 3) * 0.08)[..., None]
    m.height += grain * 0.3
    specks = step(0.6, 0.8, noise(seed + 4, 128))
    tint(m, specks * 0.6, (0.82, 0.82, 0.8))
    pores = step(0.86, 0.96, noise(seed + 5, 128))
    paint(m, pores, height=-1.2)
    tint(m, pores, (0.7, 0.7, 0.68))
    return m


def blob(seed, radius, wobble=0.45, centre=None):
    """A rough blob field, positive inside, kept clear of the border."""
    rng = np.random.default_rng(seed)
    cx, cy = centre if centre else rng.uniform(46, 82, 2)
    field = 1 - np.hypot((X - cx) / radius[0], (Y - cy) / radius[1]) + clouds(seed, 6) * wobble
    return field * inside() - (1 - inside())


def oil(m, seed):
    field = blob(seed, (30, 24), 0.6)
    stain = step(-0.2, 0.45, field) * (0.75 + 0.25 * noise(seed + 1, 24))
    tint(m, stain, (0.66, 0.64, 0.62))
    rim = (1 - step(0.02, 0.09, np.abs(field + 0.1))) * 0.35
    tint(m, rim, (0.94, 0.9, 1.0))  # a faint oily sheen at the edge
    paint(m, stain, smooth=0.6, porosity=5)
    return m


def crack(m, seed, vertical=False):
    rng = np.random.default_rng(seed)
    mask = Image.new("L", (SIZE * 4, SIZE * 4))
    draw = ImageDraw.Draw(mask)
    x, y = (rng.uniform(20, 108), 0) if vertical else (0, rng.uniform(20, 108))
    points = [(x, y)]
    while 0 <= x <= SIZE and 0 <= y <= SIZE:
        if vertical:
            y += rng.uniform(5, 11)
            x += rng.uniform(-5, 5)
        else:
            x += rng.uniform(5, 11)
            y += rng.uniform(-5, 5)
        points.append((x, y))
    draw.line([(px * 4, py * 4) for px, py in points], fill=255, width=6)
    for i in range(2):
        start = points[rng.integers(2, len(points) - 2)]
        branch = [start]
        for _ in range(4):
            branch.append((branch[-1][0] + rng.uniform(-7, 7), branch[-1][1] + rng.uniform(-7, 7)))
        draw.line([(px * 4, py * 4) for px, py in branch], fill=180, width=3)
    line = np.asarray(mask.resize((SIZE, SIZE), Image.Resampling.LANCZOS), float) / 255
    tint(m, line, (0.45, 0.45, 0.44))
    paint(m, line, height=-1.5)
    return m


def tyres(m, seed):
    """Two faded tyre tracks along the aisle."""
    tread = 0.6 + 0.4 * step(0.4, 0.6, np.abs(Y % 6 - 3) / 3)
    fade = 0.5 + 0.5 * step(-0.3, 0.3, noise(seed, 3, 8))
    for centre in (34, 94):
        track = (1 - step(7, 10, np.abs(X - centre - 3 * noise(seed + centre, 1, 4)))) * tread * fade
        tint(m, track * 0.8, (0.8, 0.79, 0.77))
    return m


def patch(m, seed):
    """A square of newer concrete poured into a repair."""
    rng = np.random.default_rng(seed)
    left, top = rng.uniform(22, 40, 2)
    right, bottom = left + rng.uniform(46, 62), top + rng.uniform(42, 58)
    area = box(left, top, right, bottom, 1.5)
    tint(m, area, (1.08, 1.08, 1.06))
    edge = np.clip(box(left - 1.5, top - 1.5, right + 1.5, bottom + 1.5) - box(left + 1, top + 1, right - 1, bottom - 1), 0, 1)
    tint(m, edge, (0.62, 0.62, 0.6))
    paint(m, edge, height=-0.8)
    return m


def wet(m, seed, strength=1.0):
    field = blob(seed, (34, 28), 0.7)
    patch_ = step(-0.1, 0.25, field) * strength
    tint(m, patch_, (0.7, 0.72, 0.75))
    paint(m, patch_, smooth=0.8, porosity=15)
    return m


def puddle(seed):
    """Standing water over concrete: dark, a little blue, glassy under shaders, with a few faint rings."""
    m = concrete((118, 118, 114), seed)
    m.color *= np.array([0.52, 0.54, 0.56])
    m.color += np.array([4, 6, 8]) * (1 + clouds(seed + 7, 3)[..., None] * 0.5)
    rng = np.random.default_rng(seed)
    cx, cy = rng.uniform(40, 88, 2)
    r = np.hypot(X - cx, Y - cy)
    for radius in (9, 19, 31):
        ring = (1 - step(0.4, 1.6, np.abs(r - radius))) * 0.35
        tint(m, ring, (1.14, 1.15, 1.17))
    dust = step(0.82, 0.93, noise(seed + 9, 96))
    tint(m, dust * 0.5, (1.25, 1.22, 1.15))
    m.height[:] = 0
    m.smooth[:] = 0.97
    m.f0[:] = 6
    m.porosity[:] = 0
    return m


def stripe(m, seed, worn=0.0):
    """Yellow road paint along the block, chipped where tyres and feet wore it down."""
    paint_mask = box_x(X, 56, 72)
    chips = step(0.55 - worn, 0.7 - worn, noise(seed, 32) * 0.6 + noise(seed + 1, 96) * 0.4)
    paint_mask *= 1 - chips
    yellow = np.array([214, 176, 54]) * (1 - worn * 0.25)
    paint(m, paint_mask, yellow, height=0.4, smooth=0.4, porosity=5)
    return m


def formwork(m, seed):
    """Board marks left by the timber the ceiling was cast against."""
    rng = np.random.default_rng(seed)
    shades = rng.uniform(0.94, 1.05, 8)
    board = (Y // 16).astype(int) % 8
    tint(m, np.ones((SIZE, SIZE)), np.stack([shades[board]] * 3, -1))
    joint = 1 - step(0.4, 1.2, np.abs(Y % 16 - 0.5))
    tint(m, joint, (0.8, 0.8, 0.79))
    paint(m, joint, height=-0.6)
    grain = noise(seed + 1, 8, 128) * 0.5 + noise(seed + 2, 4, 64) * 0.5
    tint(m, step(0.3, 0.7, grain) * 0.4, (0.94, 0.94, 0.93))
    return m


def water_mark(m, seed):
    field = clouds(seed, 4) + inside() * 0.9 - 0.75
    stain = step(0, 0.12, field) * inside()
    tint(m, stain, (0.9, 0.88, 0.82))
    for level in (0.02, 0.18):
        ring = (1 - step(0.01, 0.06, np.abs(field - level))) * inside()
        tint(m, ring, (0.8, 0.74, 0.62))
    return m


def runs(m, seed, color=(0.82, 0.8, 0.76), top=(0, 30)):
    """Water or rust that ran down a wall."""
    rng = np.random.default_rng(seed)
    run = np.zeros((SIZE, SIZE))
    for i in range(rng.integers(3, 7)):
        x, start = rng.uniform(16, 112), rng.uniform(*top)
        length, width = rng.uniform(40, 100), rng.uniform(1.5, 5)
        line = 1 - step(width * 0.4, width, np.abs(X - x - 2 * noise(seed + i, 2, 6)))
        fade = step(start - 3, start + 3, Y) * (1 - step(start + length * 0.5, start + length, Y))
        run = np.maximum(run, line * fade)
    tint(m, run, color)
    paint(m, run, smooth=0.4)
    return m


def tie_holes(m):
    """The plugged holes of the formwork ties, on a grid that carries on into the next block."""
    for cx in (32, 96):
        for cy in (32, 96):
            hole = 1 - step(2, 3.5, np.hypot(X - cx, Y - cy))
            ring = 1 - step(0.5, 1.5, np.abs(np.hypot(X - cx, Y - cy) - 4.5))
            tint(m, hole, (0.6, 0.6, 0.58))
            tint(m, ring * 0.5, (0.85, 0.85, 0.83))
            paint(m, hole, height=-1.5)
    return m


def hazard(m, seed):
    """Yellow and black stripes round the foot of a pillar."""
    band = step(74, 76, Y)
    stripes = ((X + Y) % 32 < 16).astype(float)
    color = np.where(stripes[..., None] > 0.5, np.array([222, 178, 36]), np.array([34, 33, 30]))
    chips = step(0.6, 0.72, noise(seed, 40) * 0.6 + noise(seed + 1, 128) * 0.4) * step(84, 120, Y)
    mask = band * (1 - chips)
    m.color += (color - m.color) * mask[..., None]
    paint(m, mask, height=0.3, smooth=0.35)
    edge = (1 - step(0.5, 1.5, np.abs(Y - 72))) * 0.7
    tint(m, edge, (0.4, 0.4, 0.38))
    dirt = step(108, 128, Y)
    tint(m, dirt * 0.8, (0.72, 0.68, 0.6))
    return m


def stencil(m, text_mask, band_color, ink):
    band = box(-2, 30, 130, 98, 1)
    worn = step(0.62, 0.74, noise(77, 48) * 0.6 + noise(78, 128) * 0.4)
    paint(m, band * (1 - worn * 0.8), band_color, height=0.3, smooth=0.35)
    paint(m, text_mask * (1 - worn), ink, height=0.4)
    return m


def p1_mask():
    scale = 4
    mask = Image.new("L", (SIZE * scale, SIZE * scale))
    draw = ImageDraw.Draw(mask)

    def rect(l, t, r, b):
        draw.rectangle([l * scale, t * scale, r * scale, b * scale], fill=255)

    rect(24, 40, 34, 88)  # P stem
    draw.rounded_rectangle([24 * scale, 40 * scale, 62 * scale, 68 * scale], radius=12 * scale, outline=255, width=10 * scale)
    rect(80, 40, 90, 88)  # 1
    draw.polygon([(80 * scale, 40 * scale), (90 * scale, 40 * scale), (74 * scale, 54 * scale), (70 * scale, 48 * scale)], fill=255)
    rect(72, 80, 98, 88)
    return np.asarray(mask.resize((SIZE, SIZE), Image.Resampling.LANCZOS), float) / 255


# Corridors

def block_wall(color, seed, course=32, length=64, mortar=(0.86, 0.86, 0.84), depth=-0.9):
    """Painted concrete block: offset courses with recessed joints, porous under the paint."""
    m = flat(color, 0.32, 10, 25)
    pores = noise(seed, 128) * 0.5 + noise(seed + 1, 48) * 0.5
    m.color *= (1 + pores * 0.035 + clouds(seed + 2, 3) * 0.03)[..., None]
    m.height += pores * 0.4
    row = (Y // course).astype(int)
    shifted = (X + (row % 2) * (length // 2)) % length
    joint = np.maximum(1 - step(0.6, 1.6, np.minimum(Y % course, course - Y % course)),
                       1 - step(0.6, 1.6, np.minimum(shifted, length - shifted)))
    tint(m, joint, mortar)
    paint(m, joint, height=depth)
    return m


def scuffs(m, seed):
    marks = step(0.45, 0.8, noise(seed, 40, 6) * 0.7 + noise(seed + 1, 8) * 0.5) * step(60, 120, Y)
    tint(m, marks, (0.8, 0.79, 0.77))
    return m


def bricks(seed, aged=False):
    m = flat((128, 62, 46), 0.2, 10, 45)
    rng = np.random.default_rng(seed)
    course, length = 16, 32
    row = (Y // course).astype(int)
    col = ((X + (row % 2) * (length // 2)) // length).astype(int) % (SIZE // length)
    shades = rng.uniform(0.78, 1.12, (SIZE // course, SIZE // length, 1)) * np.array([1, 0.95, 0.92])
    m.color *= shades[row, col]
    grain = noise(seed + 1, 128) * 0.5 + noise(seed + 2, 32) * 0.5
    m.color *= (1 + grain * 0.1)[..., None]
    m.height += grain * 0.5
    shifted = (X + (row % 2) * (length // 2)) % length
    joint = np.maximum(1 - step(0.8, 2.0, np.minimum(Y % course, course - Y % course)),
                       1 - step(0.8, 2.0, np.minimum(shifted, length - shifted)))
    paint(m, joint, (148, 138, 122), height=-1.4, smooth=0.1, porosity=60)
    if aged:
        soot = step(-0.1, 0.4, clouds(seed + 3, 3)) * 0.7
        tint(m, soot, (0.62, 0.6, 0.58))
        bloom = step(0.5, 0.75, noise(seed + 4, 24)) * step(70, 128, Y)
        tint(m, bloom, (1.3, 1.28, 1.22))  # efflorescence
    return m


def padded(seed, stained=False):
    m = flat((222, 216, 198), 0.3, 8, 20)
    a, b = (X + Y) % 32, (X - Y) % 32
    pillow = np.sin(np.pi * a / 32) * np.sin(np.pi * b / 32)
    m.height += pillow * 4
    tint(m, 1 - pillow, (0.86, 0.85, 0.82))
    seam = np.maximum(1 - step(0.4, 1.2, np.minimum(a, 32 - a)), 1 - step(0.4, 1.2, np.minimum(b, 32 - b)))
    tint(m, seam, (0.8, 0.79, 0.75))
    # Buttons where the seams cross: every 32 pixels, and halfway between.
    corner = np.hypot(np.minimum(X % 32, 32 - X % 32), np.minimum(Y % 32, 32 - Y % 32))
    middle = np.hypot(X % 32 - 16, Y % 32 - 16)
    button = 1 - step(1.2, 2.4, np.minimum(corner, middle))
    paint(m, button, (150, 144, 126), height=-1.5)
    if stained:
        stain = step(-0.05, 0.25, blob(seed, (30, 40), 0.6))
        tint(m, stain, (0.86, 0.8, 0.66))
    return m


def bulb_glass(color, glow):
    m = flat(color, 0.92, 30)
    m.color *= (1 + 0.12 * np.exp(-((X - 50) ** 2 + (Y - 46) ** 2) / 500))[..., None]
    m.glow += glow
    return m


def running_man():
    scale = 4
    mask = Image.new("L", (SIZE * scale, SIZE * scale))
    draw = ImageDraw.Draw(mask)

    def line(points, width):
        draw.line([(x * scale, y * scale) for x, y in points], fill=255, width=int(width * scale), joint="curve")

    draw.ellipse([46 * scale, 34 * scale, 56 * scale, 44 * scale], fill=255)  # head
    line([(50, 46), (44, 66)], 7)  # torso
    line([(48, 50), (38, 56), (32, 52)], 4.5)  # back arm
    line([(48, 50), (58, 56), (62, 50)], 4.5)  # front arm
    line([(44, 66), (52, 76), (50, 88)], 5)  # front leg
    line([(44, 66), (36, 74), (26, 76)], 5)  # back leg
    draw.rectangle([72 * scale, 34 * scale, 100 * scale, 88 * scale], outline=255, width=5 * scale)  # door
    draw.polygon([(80 * scale, 61 * scale), (92 * scale, 61 * scale), (92 * scale, 55 * scale),
                  (100 * scale, 64 * scale), (92 * scale, 73 * scale), (92 * scale, 67 * scale), (80 * scale, 67 * scale)], fill=255)
    return np.asarray(mask.resize((SIZE, SIZE), Image.Resampling.LANCZOS), float) / 255


def steel_door(top, seed):
    m = metal((94, 100, 104), 0.5)
    rng = np.random.default_rng(seed)
    dents = sum((1 - step(2, 7, np.hypot(X - x, Y - y))) for x, y in rng.uniform(20, 108, (5, 2)))
    m.height -= dents * 0.8
    rust = step(0.15, 0.45, clouds(seed, 5) + step(90, 128, Y) * 0.4 - 0.3) * inside()
    paint(m, rust * 0.85, (118, 72, 44), smooth=0.15, f0=10, porosity=30)
    if top:
        paint(m, box(40, 26, 88, 70, 2), (44, 48, 50), height=2)  # bead
        glass = box(45, 31, 83, 65)
        paint(m, glass, (36, 42, 44), height=-2, smooth=0.95)
        wire = np.maximum(1 - step(0.4, 1.2, np.abs((X + Y) % 12 - 6)), 1 - step(0.4, 1.2, np.abs((X - Y) % 12 - 6)))
        paint(m, wire * glass, (88, 92, 92))
    else:
        kick = box(10, 86, 118, 122)
        paint(m, kick, (132, 134, 132), height=1.5, smooth=0.6, f0=255)
        scrape = step(0.45, 0.8, noise(seed + 3, 8, 70)) * kick
        tint(m, scrape, (0.7, 0.7, 0.68))
    return m


def laminate():
    m = flat((176, 146, 104), 0.45, 12)
    grain = noise(500, 4, 128) * 0.6 + noise(501, 16, 128) * 0.3 + noise(502, 64) * 0.1
    m.color *= (1 + grain * 0.12)[..., None]
    rings = np.sin(X * 0.35 + noise(503, 3, 16) * 6) * 0.5 + 0.5
    tint(m, step(0.75, 0.95, rings) * 0.5, (0.88, 0.85, 0.8))
    return m


def plastic(color, seed):
    m = flat(color, 0.4, 12)
    m.color *= (1 + noise(seed, 96) * 0.03 + clouds(seed + 1, 3) * 0.04)[..., None]
    yellowed = step(-0.2, 0.5, clouds(seed + 2, 2))
    tint(m, yellowed, (1.0, 0.97, 0.88))
    return m


def crate(top, seed):
    m = flat((158, 118, 72), 0.18, 8, 40)
    rng = np.random.default_rng(seed)
    plank = ((X if top else Y) // 32).astype(int)
    shades = rng.uniform(0.82, 1.1, 4)
    m.color *= shades[plank][..., None]
    along = Y if top else X
    grain = noise(seed, 4, 128) if top else noise(seed, 128, 4)
    m.color *= (1 + grain[..., None] * 0.12 + noise(seed + 1, 64)[..., None] * 0.05)
    gap_axis = X if top else Y
    gap = 1 - step(0.6, 1.6, np.minimum(gap_axis % 32, 32 - gap_axis % 32))
    paint(m, gap, (60, 42, 26), height=-2)
    if not top:
        frame = np.clip(1 - box(13, 13, 115, 115, 1), 0, 1)
        paint(m, frame, (136, 98, 56), height=1.5)
        along, across = (X - 64) * 0.7071 + (Y - 64) * 0.7071, -(X - 64) * 0.7071 + (Y - 64) * 0.7071
        brace = (1 - step(7, 8.5, np.abs(across))) * box(13, 13, 115, 115, 1)
        paint(m, brace, (142, 104, 60), height=1.2)
        nails = [(6, 6), (122, 6), (6, 122), (122, 122), (64, 6), (64, 122), (6, 64), (122, 64)]
    else:
        nails = [(16, 8), (48, 8), (80, 8), (112, 8), (16, 120), (48, 120), (80, 120), (112, 120)]
    for nx, ny in nails:
        head = 1 - step(1.2, 2.2, np.hypot(X - nx, Y - ny))
        paint(m, head, (70, 66, 60), height=0.5, smooth=0.5, f0=200)
    return m


def flicker_frames():
    base = mt.wallpaper(mt.YELLOW)
    frames = []

    def copy(scale=1.0, glow=0.0):
        m = mt.Material(base.color * scale, base.height.copy(), base.smooth.copy(), base.f0.copy(), base.porosity.copy(),
                        np.full((SIZE, SIZE), glow))
        return m

    frames.append(copy(1.0, 0.15))
    frames.append(copy(1.25, 0.7))
    frames.append(copy(0.55, 0.0))
    glitch = copy(1.05, 0.4)
    rng = np.random.default_rng(900)
    for top in rng.integers(0, 120, 7):
        rows = slice(int(top), int(top) + int(rng.integers(3, 10)))
        glitch.color[rows] = np.roll(glitch.color[rows], int(rng.integers(-24, 24)), axis=1)
        glitch.color[rows, :, 2] *= 0.7
    frames.append(glitch)
    frames.append(copy(0.22, 0.0))
    return frames


FLICKER = [(0, 40), (1, 2), (0, 3), (2, 2), (0, 25), (3, 2), (1, 1), (0, 18), (4, 3), (2, 2), (0, 30), (3, 1), (0, 6), (1, 3)]


def all_textures():
    t = {}
    floor = (118, 118, 114)
    t["garage_floor"] = concrete(floor, 300)
    t["garage_floor_1"] = concrete(floor, 310)
    t["garage_floor_2"] = oil(concrete(floor, 300), 320)
    t["garage_floor_3"] = crack(concrete(floor, 310), 330)
    t["garage_floor_4"] = tyres(concrete(floor, 300), 340)
    t["garage_floor_5"] = patch(concrete(floor, 310), 350)
    t["garage_floor_line"] = stripe(concrete(floor, 300), 360)
    t["garage_floor_line_1"] = stripe(concrete(floor, 310), 370, 0.18)
    t["damp_garage_floor"] = wet(concrete(floor, 300), 380)
    t["damp_garage_floor_1"] = wet(concrete(floor, 310), 390)
    t["damp_garage_floor_2"] = wet(wet(concrete(floor, 300), 400, 0.6), 405, 0.6)
    t["puddled_garage_floor"] = puddle(410)
    t["puddled_garage_floor_1"] = puddle(420)

    wall = (136, 136, 131)
    t["garage_wall"] = tie_holes(concrete(wall, 430))
    t["garage_wall_1"] = runs(tie_holes(concrete(wall, 440)), 445)
    t["garage_wall_2"] = crack(tie_holes(concrete(wall, 430)), 450, True)
    t["garage_wall_3"] = runs(tie_holes(concrete(wall, 440)), 455, (0.86, 0.72, 0.58), (20, 60))
    pillar = (146, 146, 140)
    t["garage_pillar"] = concrete(pillar, 460)
    t["garage_pillar_base"] = hazard(concrete(pillar, 460), 465)
    t["garage_pillar_marked"] = stencil(concrete(pillar, 460), p1_mask(), (214, 172, 40), (30, 30, 28))
    t["garage_ceiling"] = formwork(concrete((124, 124, 120), 470), 475)
    t["garage_ceiling_1"] = water_mark(formwork(concrete((124, 124, 120), 470), 475), 480)

    t["light_housing"] = metal((206, 207, 204))
    t["light_lens"] = mt.panel((236, 240, 244), (255, 255, 255), 1)
    t["light_lens_off"] = mt.panel((116, 118, 120), (142, 144, 146), 0)
    pipe = metal((96, 106, 100), 0.45)
    collars = np.maximum(box_x(X, 0, 4), box_x(X, 64, 68))
    paint(pipe, collars, (74, 80, 76), height=1)
    chips = step(0.82, 0.9, noise(490, 32))
    paint(pipe, chips * 0.7, (140, 140, 136), smooth=0.7, f0=230)
    t["pipe"] = pipe
    rusty = metal((96, 106, 100), 0.45)
    paint(rusty, collars, (74, 80, 76), height=1)
    rust = step(0.15, 0.45, clouds(495, 5) + 0.15 * noise(496, 64)) * 0.85
    paint(rusty, rust, (118, 74, 44), smooth=0.12, f0=10, porosity=40)
    paint(rusty, np.maximum(collars, box_x(X, 28, 36)) * 0.6, (104, 62, 36))  # weeping joints
    t["pipe_rusty"] = rusty
    t["pipe_bracket"] = metal((72, 73, 72), 0.55)

    frame = metal((52, 72, 102), 0.45)
    slots = box_x(X % 16, 6, 10) * (1 - step(0.5, 1.5, np.abs(Y % 16 - 8) - 4))
    paint(frame, slots, (20, 24, 30), height=-1.5)
    t["shelf_frame"] = frame
    board = flat((172, 146, 106), 0.15, 8, 50)
    board.color *= (1 + noise(500, 128)[..., None] * 0.12 + noise(501, 32)[..., None] * 0.06)
    t["shelf_board"] = board
    cardboard = flat((168, 128, 80), 0.12, 8, 60)
    cardboard.color *= (1 + noise(510, 2, 128)[..., None] * 0.05 + noise(511, 64)[..., None] * 0.04)
    paint(cardboard, box(0, 56, 128, 72, 1), (196, 170, 124), smooth=0.6)  # tape
    t["cardboard"] = cardboard
    t["crate_side"] = crate(False, 520)
    t["crate_top"] = crate(True, 530)

    white = (226, 226, 220)
    t["painted_wall"] = block_wall(white, 540)
    t["painted_wall_1"] = scuffs(block_wall(white, 545), 546)
    t["painted_wall_2"] = runs(block_wall(white, 550), 551, (0.88, 0.84, 0.74))
    t["painted_wall_3"] = crack(block_wall(white, 555), 556, True)
    corridor = (152, 152, 148)
    t["corridor_floor"] = concrete(corridor, 560, 0.42, 15)
    t["corridor_floor_1"] = concrete(corridor, 565, 0.42, 15)
    t["corridor_floor_2"] = scuffs(concrete(corridor, 570, 0.42, 15), 571)

    t["bulb_glass"] = bulb_glass((255, 236, 186), 1.0)
    t["bulb_glass_off"] = bulb_glass((176, 174, 166), 0.0)
    t["bulb_glass_green"] = bulb_glass((120, 255, 150), 1.0)
    t["bulb_socket"] = flat((44, 38, 32), 0.55, 12)
    t["bulb_cord"] = flat((26, 26, 26), 0.3, 8)

    sign = metal((232, 233, 228), 0.6)
    face = box(6, 22, 122, 106)
    paint(sign, face, (18, 148, 72), height=1, smooth=0.8, glow=0.6)
    paint(sign, running_man(), (240, 255, 244), height=1.4, glow=1.0)
    t["emergency_sign"] = sign
    t["emergency_sign_frame"] = metal((232, 233, 228), 0.6)

    t["metal_door"] = steel_door(False, 580)
    t["metal_door_top"] = steel_door(True, 590)
    t["metal_door_frame"] = metal((62, 64, 66), 0.5)
    t["metal_door_bar"] = metal((176, 178, 176), 0.85)
    t["metal_door_bar"].f0[:] = 255

    t["old_brick"] = bricks(600)
    t["old_brick_1"] = bricks(610, True)
    t["padded_wall"] = padded(620)
    t["padded_wall_1"] = padded(630, True)
    t["desk_top"] = laminate()
    t["desk_leg"] = metal((86, 86, 88), 0.5)
    t["computer_case"] = plastic((198, 188, 160), 640)
    screen = flat((34, 42, 38), 0.95, 20)
    screen.color *= (1 + 0.25 * np.exp(-((X - 44) ** 2 + (Y - 40) ** 2) / 900))[..., None]
    paint(screen, np.clip(1 - box(10, 10, 118, 118, 3), 0, 1), (60, 60, 56), smooth=0.4)
    t["computer_screen"] = screen
    return t


# Models

def el(frm, to, faces, **extra):
    element = {"from": list(frm), "to": list(to), "faces": {}}
    for face, spec in faces.items():
        element["faces"][face] = {"texture": spec} if isinstance(spec, str) else spec
    element.update(extra)
    return element


def sides(texture, names=("north", "south", "east", "west", "up", "down"), **face):
    return {name: dict(texture=texture, **face) for name in names}


def lamp_model(lit):
    lens = "#lens"
    glow = {"shade": False, "light_emission": 15} if lit else {}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": NS + "light_housing", "housing": NS + "light_housing",
                     "lens": NS + ("light_lens" if lit else "light_lens_off")},
        "elements": [
            el((2.5, 14, 7.6), (3.3, 16, 8.4), sides("#housing", ("north", "south", "east", "west"))),
            el((12.7, 14, 7.6), (13.5, 16, 8.4), sides("#housing", ("north", "south", "east", "west"))),
            el((0.5, 11.5, 5), (15.5, 14, 11), sides("#housing", ("north", "south", "east", "west", "up"))),
            el((1, 10.6, 5.5), (15, 11.5, 10.5), {"down": lens, "north": {"texture": lens, "uv": [0, 4.5, 16, 5.5]},
                                                    "south": {"texture": lens, "uv": [0, 4.5, 16, 5.5]},
                                                    "east": {"texture": lens, "uv": [0, 4.5, 16, 5.5]},
                                                    "west": {"texture": lens, "uv": [0, 4.5, 16, 5.5]}}, **glow),
        ],
    }


def pipe_model(texture):
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": NS + texture, "pipe": NS + texture, "bracket": NS + "pipe_bracket"},
        "elements": [
            el((0, 10, 6.25), (16, 13.5, 9.75), {**sides("#pipe", ("north", "south", "up", "down")),
                                                  "east": {"texture": "#pipe", "uv": [0, 6, 3.5, 9.5]},
                                                  "west": {"texture": "#pipe", "uv": [0, 6, 3.5, 9.5]}}),
            el((7, 9.6, 5.8), (9, 13.9, 10.2), sides("#bracket")),
            el((7.6, 13.9, 7.6), (8.4, 16, 8.4), sides("#bracket", ("north", "south", "east", "west"))),
        ],
    }


def shelf_model(boxes):
    elements = []
    for x0, z0 in ((0, 0), (14.8, 0), (0, 14.8), (14.8, 14.8)):
        elements.append(el((x0, 0, z0), (x0 + 1.2, 16, z0 + 1.2), sides("#frame", ("north", "south", "east", "west"))))
    for y0 in (0.6, 8.2):
        for z0 in (0, 14.8):
            elements.append(el((1.2, y0, z0), (14.8, y0 + 1.4, z0 + 1.2), sides("#frame")))
        elements.append(el((1.2, y0 + 1.4, 1.2), (14.8, y0 + 2, 14.8), sides("#board")))
    if boxes:
        for frm, to in (((2, 2.6, 2.6), (8.4, 7.4, 9.6)), ((9, 2.6, 4), (14, 6.6, 13.2)), ((3, 10.2, 3.2), (10.4, 14.8, 12.6))):
            elements.append(el(frm, to, sides("#box")))
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "shelf_frame", "frame": NS + "shelf_frame", "board": NS + "shelf_board", "box": NS + "cardboard"},
            "elements": elements}


def bulb_model(glass, lit):
    glow = {"shade": False, "light_emission": 15} if lit else {}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": NS + glass, "glass": NS + glass, "socket": NS + "bulb_socket", "cord": NS + "bulb_cord"},
        "elements": [
            el((7.7, 10, 7.7), (8.3, 16, 8.3), sides("#cord", ("north", "south", "east", "west"))),
            el((6.8, 7.6, 6.8), (9.2, 10, 9.2), sides("#socket")),
            el((6.2, 3.6, 6.2), (9.8, 7.6, 9.8), sides("#glass"), **glow),
        ],
    }


def sign_model():
    face = {"texture": "#sign", "uv": [0.25, 3.4, 15.75, 12.6]}
    rim = sides("#frame", ("down", "up", "north", "south"), uv=[0, 7, 16, 8])
    glow = {"shade": False, "light_emission": 15}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": NS + "emergency_sign_frame", "frame": NS + "emergency_sign_frame", "sign": NS + "emergency_sign"},
        "elements": [
            el((0, 0, 0), (16, 16, 16), {f: {"texture": "#frame", "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}),
            el((-1.1, 3.4, 0.25), (0, 12.6, 15.75), rim),
            el((-1.1, 3.4, 0.25), (0, 12.6, 15.75), {"west": face}, **glow),
            el((16, 3.4, 0.25), (17.1, 12.6, 15.75), rim),
            el((16, 3.4, 0.25), (17.1, 12.6, 15.75), {"east": face}, **glow),
        ],
    }


def desk_model():
    legs = [el((x, 0, z), (x + 1.4, 14.5, z + 1.4), sides("#leg", ("north", "south", "east", "west")))
            for x in (0.8, 13.8) for z in (1.3, 13.3)]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "desk_top", "top": NS + "desk_top", "leg": NS + "desk_leg"},
            "elements": [el((0, 14.5, 0.5), (16, 16, 15.5), sides("#top"))] + legs}


def computer_model():
    screen = {"texture": "#screen"}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": NS + "computer_case", "case": NS + "computer_case", "screen": NS + "computer_screen"},
        "elements": [
            el((2.5, 0.8, 3.5), (13.5, 11, 12.5), sides("#case")),
            el((4, 2, 12.5), (12, 9.5, 15), sides("#case")),
            el((3.4, 1.8, 3.2), (12.6, 10, 3.5), {"north": screen}),
            el((3, 0, 0.6), (13, 0.8, 2.8), sides("#case")),
            el((3.5, 0, 3.5), (12.5, 0.8, 12.5), sides("#case")),
        ],
    }


def cube_all(texture):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": NS + texture}}


def bottom_top(top, side, bottom=None):
    return {"parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": NS + top, "side": NS + side, "bottom": NS + (bottom or side)}}


def all_models():
    m = {}
    for name in ("garage_floor", *(f"garage_floor_{i}" for i in range(1, 6))):
        m[name] = bottom_top(name, "garage_floor")
    for name in ("garage_floor_line", "garage_floor_line_1", "damp_garage_floor", "damp_garage_floor_1", "damp_garage_floor_2",
                 "puddled_garage_floor", "puddled_garage_floor_1"):
        m[name] = bottom_top(name, "garage_floor")
    for name in ("garage_wall", "garage_wall_1", "garage_wall_2", "garage_wall_3", "garage_pillar", "garage_pillar_base",
                 "garage_pillar_marked", "garage_ceiling", "garage_ceiling_1", "painted_wall", "painted_wall_1", "painted_wall_2",
                 "painted_wall_3", "old_brick", "old_brick_1", "padded_wall", "padded_wall_1"):
        m[name] = cube_all(name)
    for name in ("corridor_floor", "corridor_floor_1", "corridor_floor_2"):
        m[name] = bottom_top(name, "corridor_floor")
    m["garage_light"] = lamp_model(True)
    m["garage_light_off"] = lamp_model(False)
    m["broken_garage_light"] = lamp_model(False)
    m["ceiling_pipe"] = pipe_model("pipe")
    m["leaking_ceiling_pipe"] = pipe_model("pipe_rusty")
    m["metal_shelf"] = shelf_model(False)
    m["metal_shelf_boxes"] = shelf_model(True)
    m["supply_crate"] = bottom_top("crate_top", "crate_side")
    m["bare_bulb"] = bulb_model("bulb_glass", True)
    m["bare_bulb_off"] = bulb_model("bulb_glass_off", False)
    m["broken_bare_bulb"] = bulb_model("bulb_glass_off", False)
    m["green_bare_bulb"] = bulb_model("bulb_glass_green", True)
    m["emergency_sign"] = sign_model()
    m["metal_exit_door"] = {"parent": "noclip_backrooms:block/exit_door", "textures": {
        "particle": NS + "metal_door_frame", "leaf": NS + "metal_door", "frame": NS + "metal_door_frame", "bar": NS + "metal_door_bar"}}
    m["metal_exit_door_top"] = {"parent": "noclip_backrooms:block/exit_door_top", "textures": {
        "particle": NS + "metal_door_frame", "leaf": NS + "metal_door_top", "frame": NS + "metal_door_frame", "bar": NS + "metal_door_bar"}}
    m["office_desk"] = desk_model()
    m["old_computer"] = computer_model()
    m["flickering_wallpaper"] = cube_all("flickering_wallpaper")
    return m


def spin(model, weight=None, turns=(0, 90, 180, 270)):
    out = []
    for y in turns:
        variant = {"model": NS + model}
        if y:
            variant["y"] = y
        if weight:
            variant["weight"] = weight
        out.append(variant)
    return out


def single(model):
    return {"variants": {"": {"model": NS + model}}}


def all_blockstates():
    b = {}
    b["garage_floor"] = {"variants": {"": spin("garage_floor", 30) + spin("garage_floor_1", 30) + spin("garage_floor_2", 3)
                                      + spin("garage_floor_3", 4) + spin("garage_floor_4", 6) + spin("garage_floor_5", 3)}}
    b["garage_floor_line"] = {"variants": {"": spin("garage_floor_line", 6, (0, 180)) + spin("garage_floor_line_1", 2, (0, 180))}}
    b["damp_garage_floor"] = {"variants": {"": spin("damp_garage_floor") + spin("damp_garage_floor_1") + spin("damp_garage_floor_2")}}
    b["puddled_garage_floor"] = {"variants": {"": spin("puddled_garage_floor") + spin("puddled_garage_floor_1")}}
    b["garage_wall"] = {"variants": {"": [{"model": NS + "garage_wall", "weight": 20}, {"model": NS + "garage_wall_1", "weight": 5},
                                          {"model": NS + "garage_wall_2", "weight": 3}, {"model": NS + "garage_wall_3", "weight": 3}]}}
    for name in ("garage_pillar", "garage_pillar_base", "garage_pillar_marked", "ceiling_pipe", "leaking_ceiling_pipe",
                 "broken_garage_light", "supply_crate", "broken_bare_bulb", "green_bare_bulb", "metal_exit_door",
                 "metal_exit_door_top", "office_desk", "flickering_wallpaper"):
        b[name] = single(name)
    # Formwork boards all run the same way, so only half turns.
    b["garage_ceiling"] = {"variants": {"": spin("garage_ceiling", 8, (0, 180)) + spin("garage_ceiling_1", 1, (0, 180))}}
    b["garage_light"] = {"variants": {"lit=true": {"model": NS + "garage_light"}, "lit=false": {"model": NS + "garage_light_off"}}}
    b["bare_bulb"] = {"variants": {"lit=true": {"model": NS + "bare_bulb"}, "lit=false": {"model": NS + "bare_bulb_off"}}}
    b["metal_shelf"] = {"variants": {"": [{"model": NS + "metal_shelf", "weight": 2}, {"model": NS + "metal_shelf_boxes", "weight": 3}]}}
    b["painted_concrete_wall"] = {"variants": {"": [{"model": NS + "painted_wall", "weight": 16}, {"model": NS + "painted_wall_1", "weight": 5},
                                                    {"model": NS + "painted_wall_2", "weight": 3}, {"model": NS + "painted_wall_3", "weight": 2}]}}
    b["corridor_floor"] = {"variants": {"": spin("corridor_floor", 10) + spin("corridor_floor_1", 10) + spin("corridor_floor_2", 4)}}
    b["emergency_sign"] = {"variants": {"axis=z": {"model": NS + "emergency_sign"}, "axis=x": {"model": NS + "emergency_sign", "y": 90}}}
    b["old_brick"] = {"variants": {"": [{"model": NS + "old_brick", "weight": 3}, {"model": NS + "old_brick_1"}]}}
    b["padded_wall"] = {"variants": {"": [{"model": NS + "padded_wall", "weight": 5}, {"model": NS + "padded_wall_1"}]}}
    b["old_computer"] = {"variants": {f"facing={f}": ({"model": NS + "old_computer", "y": y} if y else {"model": NS + "old_computer"})
                                      for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
    return b


# Writing

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def save_animated(name, frames, timing):
    parts = []
    for i, frame in enumerate(frames):
        mt.save(f"level1/_frame{i}", frame)
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


def preview(textures):
    names = ["garage_floor", "garage_floor_2", "garage_floor_3", "garage_floor_4", "garage_floor_line", "puddled_garage_floor",
             "damp_garage_floor", "garage_wall", "garage_wall_1", "garage_pillar_base", "garage_pillar_marked", "garage_ceiling",
             "light_lens", "pipe", "pipe_rusty", "shelf_frame", "crate_side", "crate_top",
             "painted_wall", "painted_wall_2", "corridor_floor", "emergency_sign", "metal_door", "metal_door_top",
             "old_brick", "old_brick_1", "padded_wall", "desk_top", "computer_case", "computer_screen"]
    columns, gap, side = 6, 8, SIZE * 2
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (side + gap) + gap, rows * (side + gap) + gap), (30, 30, 30))
    for i, name in enumerate(names):
        tile = Image.fromarray(np.clip(np.rint(textures[name].color), 0, 255).astype("uint8"), "RGB")
        for dx in (0, SIZE):
            for dy in (0, SIZE):
                sheet.paste(tile, (gap + i % columns * (side + gap) + dx, gap + i // columns * (side + gap) + dy))
    sheet.save(mt.ROOT / "docs/level1_textures.png", optimize=True)


def check(blockstates):
    """Every texture a Level 1 model uses exists with both companions; every model a blockstate or item names exists."""
    prefix = "noclip_backrooms:block/"

    def model_file(name):
        return ASSETS / f"models/block/{name[len(prefix):]}.json"

    for path in MODELS.glob("*.json"):
        for value in json.loads(path.read_text(encoding="utf8")).get("textures", {}).values():
            if value.startswith(NS):
                for suffix in ("", "_n", "_s"):
                    assert (TEXTURES / f"{value[len(NS):]}{suffix}.png").is_file(), (path.name, value, suffix)
            elif value.startswith(prefix):
                assert (ASSETS / f"textures/block/{value[len(prefix):]}.png").is_file(), (path.name, value)
        parent = json.loads(path.read_text(encoding="utf8")).get("parent", "")
        if parent.startswith(prefix):
            assert model_file(parent).is_file(), (path.name, parent)
    for name, state in blockstates.items():
        for entry in state["variants"].values():
            for variant in entry if isinstance(entry, list) else [entry]:
                assert model_file(variant["model"]).is_file(), (name, variant["model"])
        item = json.loads((ASSETS / f"items/{name}.json").read_text(encoding="utf8"))
        assert model_file(item["model"]["model"]).is_file(), name


ITEM_MODELS = {"garage_floor": "garage_floor", "garage_floor_line": "garage_floor_line", "damp_garage_floor": "damp_garage_floor",
               "puddled_garage_floor": "puddled_garage_floor", "garage_wall": "garage_wall", "garage_ceiling": "garage_ceiling",
               "metal_shelf": "metal_shelf_boxes", "painted_concrete_wall": "painted_wall", "corridor_floor": "corridor_floor",
               "old_brick": "old_brick", "padded_wall": "padded_wall"}


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    textures = all_textures()
    for name, material in textures.items():
        mt.save("level1/" + name, material)
    save_animated("flickering_wallpaper", flicker_frames(), FLICKER)
    preview(textures)

    models = all_models()
    for name, model in models.items():
        write_json(MODELS / f"{name}.json", model)
    blockstates = all_blockstates()
    for name, state in blockstates.items():
        write_json(ASSETS / f"blockstates/{name}.json", state)
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + ITEM_MODELS.get(name, name)}})
    check(blockstates)
    print(f"{len(textures) + 1} materials, {len(models)} models, {len(blockstates)} blocks")


if __name__ == "__main__":
    main()
