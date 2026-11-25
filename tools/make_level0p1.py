"""Draws every Level 0.1 (Zenith Station) texture at 128x128 and writes its block models, blockstates, item models,
drop tables, and the entries the shared files need (language files, mining tags, sounds).

    py tools/make_level0p1.py

Uses the drawing helpers and the labPBR writer of make_textures.py (Pillow, NumPy). Textures go to
textures/block/level0p1/, models to models/block/level0p1/. Also writes docs/levels/level0p1/textures.png.
Looks follow the Wikidot page: glossy white-and-steel futurism, broken down; cables out of the walls; lived-in rooms.
Safe to run again: it only touches Level 0.1's own files and adds missing lines to the shared ones.
"""

import json
import sys

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True  # no __pycache__ next to the scripts
import make_textures as mt  # noqa: E402
from make_textures import SIZE, X, Y, box, clouds, flat, noise, paint, step, tint  # noqa: E402

# The same drawing functions make every variant of a surface: SHIFT moves all their noise seeds, so each variant has its own grime.
SHIFT = 0
_noise, _clouds = noise, clouds


def noise(seed, *args, **kwargs):  # noqa: F811
    return _noise(seed + SHIFT, *args, **kwargs)


def clouds(seed, cells=4):  # noqa: F811
    return _clouds(seed + SHIFT, cells)


ASSETS = mt.ASSETS
TEXTURES = ASSETS / "textures/block/level0p1"
MODELS = ASSETS / "models/block/level0p1"
NS = "noclip_backrooms:block/level0p1/"
DATA = mt.ROOT / "src/main/resources/data"
ALPHA = {}  # texture name -> alpha channel, for the textures with holes or glass


# Drawing helpers

def grain(m, seed, amount=0.03):
    fibre = noise(seed, 128, 8) * 0.5 + noise(seed + 1, 8, 128) * 0.5
    m.color *= (1 + fibre * amount)[..., None]
    m.height += fibre * 0.08
    return m


def lines(spacing, width=1.0, offset=0, axes="xy"):
    """1 on grid lines every `spacing` pixels."""
    out = np.zeros((SIZE, SIZE))
    if "x" in axes:
        gx = np.abs(((X + offset + spacing / 2) % spacing) - spacing / 2)
        out = np.maximum(out, 1 - step(width - 0.4, width + 0.7, gx))
    if "y" in axes:
        gy = np.abs(((Y + offset + spacing / 2) % spacing) - spacing / 2)
        out = np.maximum(out, 1 - step(width - 0.4, width + 0.7, gy))
    return out


def dots(spacing, radius=1.8, offset=0):
    gx = ((X + offset + spacing / 2) % spacing) - spacing / 2
    gy = ((Y + offset + spacing / 2) % spacing) - spacing / 2
    return 1 - step(radius - 0.6, radius + 0.4, np.hypot(gx, gy))


def weather(m, seed, amount=0.5, color=(0.78, 0.76, 0.72)):
    """Grime in streaks that run downwards, heavier near the bottom."""
    streak = step(0.1, 0.8, noise(seed, 40, 3) * 0.6 + noise(seed + 1, 12, 9) * 0.4)
    low = 0.35 + 0.65 * (Y / SIZE)
    tint(m, streak * low * amount, color)
    return m


def scorch(m, seed, amount=1.0):
    soot = step(0.15, 0.7, clouds(seed, 4) + (Y / SIZE) * -0.2 + 0.15) * amount
    tint(m, soot * 0.7, (0.45, 0.43, 0.42))
    return m


def plate(color, seed, smooth=0.55, f0=60, spacing=64, light=0.12):
    """A steel panel wall: panels with sunk seams, rivets, a little brushing and grime."""
    m = flat(color, smooth, f0)
    grain(m, seed)
    seam = lines(spacing, 1.1)
    paint(m, seam, tuple(c * 0.55 for c in color), height=-2.2, smooth=smooth * 0.5)
    paint(m, lines(spacing, 3.4, offset=0) * (1 - seam) * light, tuple(min(255, c * 1.25) for c in color), height=0.6)
    paint(m, dots(spacing, 1.7, offset=spacing / 2 - 6) * 0.9, tuple(c * 0.7 for c in color), height=0.9)
    return m


def mullion(m, color, *bars):
    for kind, at, size in bars:
        mask = box(at, -2, at + size, 130) if kind == "v" else box(-2, at, 130, at + size)
        paint(m, mask, color, height=1.2, smooth=0.6)
    return m


def glowing(color, centre=1.0, seed=0, ring=0.0):
    m = flat(color, 0.9, 20)
    m.glow += centre
    return m


# The shell

def hull_plate():
    m = plate((58, 64, 74), 11, 0.6, 90, 64)
    weather(m, 12, 0.8, (0.7, 0.7, 0.72))
    paint(m, step(0.62, 0.7, clouds(13, 5)) * 0.4, (86, 80, 72), height=-0.4)  # a little rust at the edges
    return m


def hull_seam():
    m = hull_plate()
    paint(m, box(-2, 58, 130, 70), (86, 94, 106), height=2.2, smooth=0.7)
    paint(m, dots(16, 1.6, 4) * box(-2, 58, 130, 70), (42, 46, 54), height=0.8)
    paint(m, box(-2, 63, 130, 65), (40, 70, 84), glow=0.3)
    return m


def inner_hull_plate():
    m = plate((206, 211, 216), 21, 0.85, 120, 64, 0.18)
    paint(m, lines(64, 0.7, axes="y") * 0.0 + box(-2, 58, 130, 61), (96, 190, 220), glow=0.7)
    weather(m, 22, 0.3)
    return m


def observation_glass():
    m = flat((170, 205, 225), 0.95, 200)
    frame = np.maximum(1 - step(5, 7, np.minimum(np.minimum(X, SIZE - 1 - X), np.minimum(Y, SIZE - 1 - Y))), 0)
    glint = step(0.0, 1.0, 1 - np.abs((X + Y * 0.6) % 90 - 22) / 5) * 0.25
    paint(m, frame, (196, 202, 208), height=2, smooth=0.7)
    alpha = np.where(frame > 0.5, 255.0, 46.0 + glint * 70)
    ALPHA["observation_glass"] = alpha
    return m


def bulkhead():
    m = plate((112, 122, 134), 31, 0.5, 70, 128)
    stripe = ((X + Y) % 32 < 16).astype(float) * box(-2, 104, 130, 124, 0.5)
    paint(m, box(-2, 104, 130, 124, 0.5), (36, 38, 42))
    paint(m, stripe, (230, 190, 40))
    weather(m, 32, 0.9)
    return m


# Floors and ceilings

def deck_floor():
    m = flat((176, 181, 186), 0.7, 50)
    grain(m, 41, 0.025)
    paint(m, lines(32, 0.9), (110, 114, 120), height=-1.6)
    tile = noise(42, 4, 4)
    tint(m, np.ones((SIZE, SIZE)) * 0, (1, 1, 1))
    m.color *= (1 + tile * 0.03)[..., None]
    weather(m, 43, 0.4)
    return m


def corridor_floor():
    m = flat((118, 124, 132), 0.6, 40)
    grain(m, 51, 0.03)
    tread = ((X + Y) % 8 < 2).astype(float) * 0.5 + ((X - Y) % 8 < 2).astype(float) * 0.5
    paint(m, tread * 0.35, (150, 156, 164), height=0.7)
    paint(m, lines(64, 1.0), (60, 64, 70), height=-2)
    weather(m, 52, 0.5)
    return m


def corridor_floor_stripe():
    m = corridor_floor()
    for cx, cy in ((32, 32), (96, 32), (32, 96), (96, 96)):
        arrow = np.maximum(0, 1 - (np.abs(X - cx) * 0.8 + np.abs(Y - cy)) / 11)
        paint(m, step(0.2, 0.5, arrow), (214, 176, 48), height=0.4)
    return m


def quarters_carpet():
    m = flat((108, 86, 74), 0.1, 6, 120)
    fibre = noise(61, 128) * 0.5 + noise(62, 64) * 0.5
    m.color *= (1 + fibre * 0.07)[..., None]
    m.height += fibre * 0.3
    paint(m, lines(64, 2.4) * 0.5, (132, 108, 92), height=0.4)
    weather(m, 63, 0.25, (0.82, 0.8, 0.78))
    return m


def kitchen_tile():
    m = flat((222, 224, 224), 0.85, 120)
    grout = lines(16, 0.9)
    paint(m, grout, (150, 154, 156), height=-1.4, smooth=0.3)
    m.color *= (1 + noise(71, 32) * 0.02)[..., None]
    paint(m, step(0.7, 0.85, clouds(72, 6)) * 0.35, (170, 168, 150))
    return m


def gym_mat():
    m = flat((46, 78, 138), 0.3, 10, 30)
    grain(m, 81, 0.04)
    paint(m, lines(64, 1.3), (20, 36, 66), height=-1.8)
    paint(m, box(2, 2, 126, 4) + box(2, 124, 126, 126), (200, 206, 214), height=0.3)
    m.color *= (1 + noise(82, 6) * 0.03)[..., None]
    return m


def garden_floor():
    m = flat((62, 96, 54), 0.2, 8, 140)
    soil = clouds(91, 8)
    tint(m, step(0.2, 0.6, soil) * 0.5, (0.72, 0.86, 0.6))
    paint(m, step(0.5, 0.62, noise(92, 40)) * 0.8, (36, 62, 34), height=0.7)
    paint(m, step(0.84, 0.9, noise(93, 52)) * 0.9, (150, 180, 90), height=0.6)
    m.height += soil * 0.4
    return m


def worn_floor():
    m = flat((70, 72, 76), 0.4, 30)
    grain(m, 101, 0.05)
    paint(m, lines(64, 1.1), (30, 32, 36), height=-2.2)
    crack = step(0.0, 0.5, 1 - np.abs(noise(102, 6, 6)) * 14)
    paint(m, crack, (22, 22, 24), height=-2.5)
    rust = step(0.55, 0.75, clouds(103, 6))
    tint(m, rust * 0.7, (1.35, 0.9, 0.6))
    scorch(m, 104, 0.5)
    return m


def hazard_floor():
    m = flat((222, 180, 36), 0.45, 40)
    grain(m, 111, 0.03)
    paint(m, (((X + Y) % 32) < 16).astype(float), (28, 28, 30))
    paint(m, lines(128, 1.0) * 0.6, (50, 50, 50), height=-1.2)
    weather(m, 112, 0.7)
    return m


def deck_ceiling():
    m = flat((206, 207, 202), 0.2, 8, 40)
    m.color *= (1 + noise(121, 128, 128) * 0.03)[..., None]
    pits = dots(8, 0.9, 2) * 0.35
    paint(m, pits, (150, 150, 146), height=-0.6)
    paint(m, lines(64, 1.3), (120, 122, 118), height=-2.2)
    weather(m, 122, 0.55, (0.82, 0.8, 0.74))
    return m


def ceiling_light():
    m = flat((236, 244, 255), 0.9, 30)
    m.glow += 0.95
    frame = np.maximum(1 - step(6, 8, np.minimum(np.minimum(X, SIZE - 1 - X), np.minimum(Y, SIZE - 1 - Y))), 0)
    paint(m, frame, (190, 196, 204), height=2, glow=0.0)
    paint(m, (1 - frame) * (0.8 + 0.2 * np.sin(X / 4.0)) * 0.12, (255, 255, 255))
    return m


def dead_ceiling_light():
    m = flat((92, 96, 100), 0.5, 20)
    frame = np.maximum(1 - step(6, 8, np.minimum(np.minimum(X, SIZE - 1 - X), np.minimum(Y, SIZE - 1 - Y))), 0)
    paint(m, frame, (130, 134, 138), height=2)
    paint(m, step(0.4, 0.8, noise(131, 10)) * (1 - frame), (64, 66, 70))
    paint(m, (1 - step(0.5, 1.4, np.abs(X - Y * 0.6 - 30)) ) * 0.8, (30, 30, 32), height=-1)  # a crack
    return m


# Walls

def wall_panel():
    m = plate((206, 210, 214), 141, 0.8, 120, 64, 0.1)
    paint(m, box(-2, 98, 130, 100), (118, 190, 214), glow=0.35)  # a thin blue guide line
    paint(m, box(-2, 108, 130, 130), (140, 146, 154), height=-0.8)  # skirting
    weather(m, 142, 0.5)
    return m


def quarters_wall():
    m = flat((208, 196, 178), 0.4, 20, 40)
    grain(m, 151, 0.025)
    paint(m, lines(64, 1.0, axes="x"), (164, 150, 132), height=-1.8)
    paint(m, box(-2, 60, 130, 64), (176, 128, 84), height=0.6)  # a warm trim, to make it a home
    paint(m, box(-2, 108, 130, 130), (118, 98, 80), height=0.6)
    weather(m, 152, 0.4)
    return m


def kitchen_wall():
    m = flat((230, 232, 230), 0.88, 130)
    paint(m, lines(16, 0.8), (160, 164, 166), height=-1.2)
    paint(m, box(-2, 48, 130, 56), (96, 150, 130), height=0.5)
    paint(m, step(0.72, 0.85, clouds(161, 6)) * 0.4, (176, 172, 150))
    weather(m, 162, 0.35)
    return m


def gym_wall():
    m = flat((42, 70, 124), 0.25, 8, 40)
    grain(m, 171, 0.04)
    for ox in (0, 32):
        for oy in (0, 32):
            diamond = np.maximum(0, 1 - (np.abs(((X + ox) % 64) - 32) + np.abs(((Y + oy) % 64) - 32)) / 30)
            tint(m, step(0.0, 0.4, diamond) * 0.5, (1.18, 1.18, 1.22))
    paint(m, (lines(64, 0.9) + lines(64, 0.9, offset=32)).clip(0, 1) * 0.6, (18, 30, 56), height=-1.4)
    paint(m, dots(64, 2.2, 32) + dots(64, 2.2, 0), (24, 40, 76), height=1.0)
    return m


def damaged_wall():
    """A scorched panel wall. The base has the big hole with cables; the variants put it elsewhere, smaller, or leave the wall whole."""
    m = flat((84, 87, 94), 0.4, 30)
    grain(m, 181, 0.05)
    paint(m, lines(64, 1.1), (24, 26, 28), height=-2.2)
    rng = np.random.default_rng(SHIFT + 181)
    hx, hy, hw, hh = (70, 62, 38, 30) if SHIFT == 0 else (rng.uniform(30, 98), rng.uniform(30, 98), rng.uniform(14, 30), rng.uniform(12, 26))
    hole = step(0.1, 0.5, 1 - np.hypot((X - hx) / hw, (Y - hy) / hh))
    if SHIFT == 2 * 1009:
        hole = hole * 0  # this one stays whole, just scorched and scratched
    paint(m, hole, (14, 14, 16), height=-4)
    palette = [(200, 50, 40), (220, 190, 60), (60, 130, 200), (70, 180, 90)]
    for i, colour in enumerate(palette):
        curve = 1 - step(0.8, 2.2, np.abs(Y - (hy - 6 + i * 5 + 6 * np.sin(X / 9.0 + i))))
        paint(m, curve * hole * 0.9, colour, height=1.4, smooth=0.5, glow=0.5)
    scorch(m, 182, 1.3)
    paint(m, step(0.62, 0.74, clouds(183, 5)) * 0.4, (110, 70, 44), height=-0.5)
    # Lighter dents and scratches so the dark wall still shows its shape by a little light.
    paint(m, (1 - step(0.0, 0.1, np.abs(noise(185, 5, 9) + noise(186, 30, 4) * 0.3))) * 0.5, (110, 112, 118), height=-0.8)
    return m


def frost_wall():
    """A panel wall under frost: white-blue rime in drifts, ice crystals, icicle streaks, the seams still dark under it."""
    m = flat((120, 138, 158), 0.7, 40)
    grain(m, 191, 0.04)
    paint(m, lines(64, 1.1), (30, 38, 48), height=-2.2)
    rime = step(-0.5, 0.25, clouds(192, 4) + (1 - Y / SIZE) * 0.1)
    paint(m, rime * 0.92, (214, 230, 244), height=1.2, smooth=0.85)
    paint(m, step(0.55, 0.8, noise(193, 40, 40)) * rime, (248, 252, 255), height=1.8)
    icicles = step(0.45, 0.8, noise(194, 28, 2)) * step(0.0, 0.7, Y / SIZE)
    paint(m, icicles * 0.7, (232, 244, 255), height=1.5, smooth=0.95)
    paint(m, (1 - step(0.0, 0.06, np.abs(noise(195, 8, 14)))) * 0.5, (150, 176, 204), height=-0.6)
    return m


def scorched_wall():
    """A panel wall cooked black, with cracks still glowing orange and soot over everything."""
    m = flat((46, 38, 34), 0.35, 30)
    grain(m, 201, 0.06)
    paint(m, lines(64, 1.1), (16, 14, 14), height=-2.2)
    soot = step(-0.2, 0.6, clouds(202, 3))
    tint(m, soot * 0.55, (0.55, 0.52, 0.5))
    veins = 1 - step(0.0, 0.1, np.abs(noise(203, 7, 11) + noise(204, 19, 9) * 0.35))
    paint(m, veins * 0.95, (255, 120, 36), height=-1.2, glow=1.0)
    paint(m, veins * step(0.25, 0.7, noise(205, 20)) * 0.8, (255, 214, 120), glow=1.0)
    paint(m, step(0.5, 0.85, noise(206, 14, 14)) * 0.5, (88, 66, 52), height=0.8)
    return m


def frost_floor():
    """Deck plating under ice: a sheet of pale ice with cracks, white rime in the corners of each plate, seams dark underneath."""
    m = flat((96, 110, 126), 0.75, 40)
    grain(m, 211, 0.04)
    paint(m, lines(64, 1.1), (30, 38, 48), height=-2.2)
    rime = step(-0.4, 0.3, clouds(212, 4))
    paint(m, rime * 0.9, (206, 224, 242), height=1.0, smooth=0.9)
    paint(m, step(0.55, 0.8, noise(213, 36, 36)) * rime, (248, 252, 255), height=1.5)
    paint(m, (1 - step(0.0, 0.05, np.abs(noise(214, 9, 9) + noise(215, 21, 21) * 0.3))) * 0.6, (150, 178, 208), height=-0.8)
    return m


def scorched_floor():
    """Deck plating burnt black, ash drifted in the seams, cracks still glowing."""
    m = flat((40, 34, 32), 0.35, 30)
    grain(m, 221, 0.06)
    paint(m, lines(64, 1.1), (14, 12, 12), height=-2.2)
    ash = step(0.0, 0.7, clouds(222, 4))
    tint(m, ash * 0.5, (1.5, 1.45, 1.4))
    veins = 1 - step(0.0, 0.09, np.abs(noise(223, 8, 8) + noise(224, 18, 18) * 0.35))
    paint(m, veins * 0.9, (255, 112, 32), height=-1.0, glow=1.0)
    paint(m, veins * step(0.3, 0.7, noise(225, 18)) * 0.7, (255, 210, 110), glow=1.0)
    return m


def frost_ceiling():
    """Ceiling plates furred with rime, with icicle points in clusters."""
    m = flat((150, 164, 178), 0.5, 20, 40)
    m.color *= (1 + noise(231, 128, 128) * 0.03)[..., None]
    paint(m, lines(64, 1.3), (60, 72, 86), height=-2.2)
    rime = step(-0.55, 0.2, clouds(232, 4))
    paint(m, rime * 0.92, (222, 236, 248), height=1.2, smooth=0.85)
    points = step(0.5, 0.8, noise(233, 30, 30)) * rime
    paint(m, points, (252, 254, 255), height=2.2, smooth=0.95)
    return m


def scorched_ceiling():
    """Ceiling plates blackened by smoke, a few cracks still dull red."""
    m = flat((54, 48, 46), 0.25, 8, 40)
    m.color *= (1 + noise(241, 128, 128) * 0.05)[..., None]
    paint(m, lines(64, 1.3), (16, 14, 14), height=-2.2)
    soot = step(-0.3, 0.6, clouds(242, 3))
    tint(m, soot * 0.6, (0.5, 0.48, 0.47))
    veins = 1 - step(0.0, 0.07, np.abs(noise(243, 6, 9) + noise(244, 16, 12) * 0.35))
    paint(m, veins * 0.7, (200, 70, 28), height=-1.0, glow=0.6)
    return m


# Doors, hatch, stairs, rails

def door_leaf(top):
    m = flat((226, 230, 234), 0.85, 120)
    grain(m, 191, 0.02)
    paint(m, lines(128, 1.0, axes="x") * 0.0 + box(61, -2, 67, 130), (110, 116, 124), height=-2.4)  # the two leaves meet
    frame = np.maximum(1 - step(5, 7, np.minimum(X, SIZE - 1 - X)), 0)
    paint(m, frame, (150, 156, 164), height=1.8, smooth=0.7)
    if top:
        paint(m, box(26, 20, 102, 96), (34, 40, 52), height=-1.5)
        sky = (66 + 90 * (Y / SIZE))[..., None] * np.array([0.7, 0.9, 1.2])
        paint(m, box(28, 22, 100, 94) * 0.55, np.clip(sky, 0, 255), glow=0.2)
        paint(m, box(-2, 110, 130, 114), (214, 176, 48), height=0.6)
    else:
        paint(m, box(8, 100, 120, 126), (140, 146, 154), height=1.2)
        paint(m, box(54, 52, 58, 76) + box(70, 52, 74, 76), (190, 196, 204), height=1.6, smooth=0.9)
        paint(m, box(-2, 4, 130, 8), (214, 176, 48), height=0.6)
    weather(m, 192 + int(top), 0.35)
    return m


def hatch(part):
    m = flat((232, 235, 238), 0.85, 120)
    grain(m, 201, 0.02)
    frame = 1 - step(7, 9, np.minimum(X, SIZE - 1 - X))
    if part == 1:
        frame = np.maximum(frame, 1 - step(7, 9, Y))
    paint(m, frame, (230, 192, 40), height=2)
    paint(m, frame * (((X + Y) % 24) < 12), (30, 30, 32), height=2)
    paint(m, box(61, -2, 67, 130), (120, 126, 134), height=-2.4)
    if part == 1:
        paint(m, box(40, 30, 88, 54), (22, 26, 32), height=-1.5)
        paint(m, box(44, 34, 84, 50), (72, 190, 120), glow=0.8)
    else:
        paint(m, box(14, 8, 44, 14), (140, 146, 154))
        paint(m, box(86, 70, 92, 92), (70, 74, 82), height=1.4)
    weather(m, 202 + part, 0.4)
    return m


def stair_steps():
    m = flat((92, 98, 106), 0.5, 40)
    grain(m, 211, 0.04)
    tread = ((X + Y) % 10 < 3).astype(float)
    paint(m, tread * 0.4, (130, 136, 144), height=0.6)
    paint(m, box(-2, -2, 130, 12), (224, 184, 40), height=1.0)
    paint(m, box(-2, 114, 130, 130), (224, 184, 40), height=1.0)
    paint(m, lines(128, 1.2) * 0.7, (40, 42, 46), height=-2)
    weather(m, 212, 0.5)
    return m


def stair_core(lit):
    m = plate((72, 80, 92), 221, 0.55, 80, 128)
    strip = box(52, -2, 76, 130)
    if lit:
        paint(m, strip, (210, 240, 255), glow=0.95)
        paint(m, lines(32, 1.0, axes="y") * strip, (100, 130, 150))
    else:
        paint(m, strip, (50, 56, 62), height=-1)
        paint(m, step(0.4, 0.8, noise(222, 8, 20)) * strip, (36, 40, 44))
    return m


def rail_texture():
    m = flat((210, 176, 52), 0.5, 40)
    grain(m, 231, 0.03)
    paint(m, (((X + Y) % 32) < 16).astype(float) * 0.5, (40, 40, 44))
    weather(m, 232, 0.4)
    return m


def rail_lamp_texture():
    m = flat((190, 235, 255), 0.9, 30)
    m.glow += 0.95
    paint(m, lines(32, 1.0) * 0.4, (120, 170, 200))
    return m


def orb():
    d = np.hypot(X - 64, Y - 64) / 64.0
    m = flat((220, 245, 255), 0.95, 30)
    m.glow += 1.0
    tint(m, step(0.2, 1.0, d) * 0.5, (0.65, 0.85, 1.0))
    return m


# Furniture

def bed_top():
    m = flat((70, 98, 150), 0.12, 6, 90)
    fibre = noise(241, 128) * 0.5 + noise(242, 64) * 0.5
    m.color *= (1 + fibre * 0.06)[..., None]
    paint(m, lines(64, 1.0, axes="y", offset=10), (48, 70, 112), height=-1.2)
    paint(m, box(8, 96, 120, 124), (226, 228, 232), height=1.5, smooth=0.1)  # the pillow, at the head
    return m


def bed_side():
    m = plate((200, 204, 208), 243, 0.7, 70, 64)
    paint(m, box(-2, 0, 130, 50), (70, 98, 150), height=1.0)
    return m


def locker_front():
    m = plate((150, 160, 172), 251, 0.6, 80, 128)
    for y0 in (14, 52, 90):
        paint(m, box(26, y0, 102, y0 + 8), (40, 44, 50), height=-1.5)
    paint(m, box(100, 56, 106, 84), (210, 214, 220), height=1.8, smooth=0.9)
    paint(m, box(20, 106, 108, 112), (200, 70, 50), glow=0.3)
    return m


def locker_side():
    return plate((136, 146, 158), 252, 0.6, 80, 128)


def counter_front():
    m = plate((212, 216, 218), 261, 0.8, 100, 64)
    paint(m, box(14, 18, 114, 46), (190, 194, 198), height=1.2)
    paint(m, box(54, 28, 74, 32), (120, 126, 134), height=2.0)
    paint(m, box(14, 62, 114, 112), (190, 194, 198), height=1.2)
    paint(m, box(54, 72, 74, 76), (120, 126, 134), height=2.0)
    return m


def counter_top():
    m = flat((200, 206, 210), 0.92, 220)
    grain(m, 262, 0.03)
    paint(m, box(0, 0, 128, 5) + box(0, 123, 128, 128), (150, 156, 162), height=1.4)
    weather(m, 263, 0.3)
    return m


def stove_top():
    m = flat((54, 58, 62), 0.7, 80)
    for cx, cy in ((36, 36), (92, 36), (36, 92), (92, 92)):
        ring = (1 - step(15, 17, np.hypot(X - cx, Y - cy))) * step(8, 10, np.hypot(X - cx, Y - cy))
        paint(m, ring, (150, 70, 40), height=1.4, glow=0.35)
        paint(m, 1 - step(7, 9, np.hypot(X - cx, Y - cy)), (22, 24, 26), height=-1)
    return m


def stove_front():
    m = plate((214, 218, 220), 271, 0.8, 100, 128)
    paint(m, box(16, 40, 112, 106), (36, 40, 46), height=-1.5)
    paint(m, box(20, 44, 108, 102), (74, 82, 92), smooth=0.9)
    paint(m, box(22, 24, 106, 30), (170, 176, 184), height=2, smooth=0.9)
    for cx in (28, 52, 76, 100):
        paint(m, 1 - step(3, 5, np.hypot(X - cx, Y - 12)), (30, 32, 36), height=1.4)
    return m


def treadmill_belt():
    m = flat((34, 36, 40), 0.35, 14)
    grain(m, 281, 0.05)
    paint(m, ((Y % 8) < 1.5).astype(float) * 0.6, (56, 58, 62), height=0.6)
    paint(m, box(0, 0, 12, 128) + box(116, 0, 128, 128), (170, 176, 184), height=1.4)
    return m


def treadmill_side():
    return plate((190, 196, 202), 282, 0.7, 80, 64)


def weights_side():
    m = plate((54, 58, 64), 291, 0.6, 80, 64)
    for cy in (34, 88):
        paint(m, 1 - step(26, 28, np.hypot(X - 64, Y - cy)), (22, 24, 26), height=1.6)
        paint(m, 1 - step(8, 10, np.hypot(X - 64, Y - cy)), (130, 136, 144), height=2)
    return m


def planter_side():
    m = plate((226, 228, 226), 301, 0.8, 100, 128)
    paint(m, box(-2, 0, 130, 14), (84, 190, 120), glow=0.5)
    return m


def planter_top():
    m = flat((70, 54, 40), 0.2, 8, 120)
    grain(m, 302, 0.1)
    paint(m, box(8, 8, 120, 120) * 0.0 + (1 - step(5, 7, np.minimum(np.minimum(X, SIZE - 1 - X), np.minimum(Y, SIZE - 1 - Y)))), (226, 228, 226), height=1.4)
    paint(m, step(0.2, 0.7, clouds(303, 6)) * 0.35, (96, 74, 52))
    return m


def planter_plant():
    m = flat((70, 150, 70), 0.3, 8)
    alpha = np.zeros((SIZE, SIZE))
    rng = np.random.default_rng(304)
    for _ in range(30):
        cx, cy = rng.uniform(10, 118), rng.uniform(30, 124)
        leaf = np.maximum(0, 1 - np.hypot((X - cx) / rng.uniform(4, 9), (Y - cy) / rng.uniform(8, 22)))
        alpha = np.maximum(alpha, step(0.0, 0.2, leaf))
        tint(m, step(0.0, 0.5, leaf) * 0.25, (1.0, 1.4 * rng.uniform(0.8, 1.1), 0.9))
    m.color *= (1 + noise(305, 24) * 0.12)[..., None]
    ALPHA["planter_plant"] = np.where(alpha > 0.5, 255.0, 0.0)
    return m


def console_front():
    m = flat((26, 30, 36), 0.6, 40)
    paint(m, box(10, 16, 118, 100), (10, 14, 20), height=-1.2)
    glow = (60 + 30 * np.sin(Y / 5.0))[..., None] * np.array([0.4, 1.0, 1.3])
    paint(m, box(14, 20, 114, 96), np.clip(glow + 40, 0, 255), glow=0.85)
    paint(m, ((Y % 12) < 2).astype(float) * box(14, 20, 114, 96) * 0.25, (200, 255, 255))
    return m


def console_top():
    m = plate((70, 76, 84), 311, 0.5, 80, 64)
    for i in range(6):
        for j in range(3):
            paint(m, box(14 + i * 18, 70 + j * 16, 26 + i * 18, 80 + j * 16), (130, 136, 146), height=1.2)
    return m


def console_side():
    return plate((64, 70, 78), 312, 0.55, 80, 64)


def broken_console_front():
    m = flat((24, 26, 30), 0.5, 30)
    paint(m, box(10, 16, 118, 100), (8, 8, 10), height=-1.2)
    shard = (1 - step(0.5, 1.6, np.abs((X - 20) * 0.5 + (Y - 30) - 0.3 * (X - 60)))) * box(10, 16, 118, 100)
    paint(m, shard * 0.9, (160, 170, 176))
    paint(m, box(40, 40, 70, 46) * 0.9, (230, 120, 40), glow=0.6)
    scorch(m, 321, 1.2)
    return m


def server_front():
    m = plate((34, 36, 42), 331, 0.5, 70, 128)
    rng = np.random.default_rng(332)
    for row in range(8):
        paint(m, box(14, 10 + row * 14, 114, 18 + row * 14), (18, 20, 24), height=-1)
        for col in range(6):
            lit = rng.random() < 0.6
            hue = (80, 230, 120) if rng.random() < 0.7 else (240, 170, 60)
            paint(m, box(18 + col * 6, 12 + row * 14, 21 + col * 6, 15 + row * 14), hue if lit else (30, 34, 36), glow=0.8 if lit else 0.0)
    return m


def crate_texture():
    m = plate((120, 126, 130), 341, 0.4, 40, 128)
    paint(m, box(10, 10, 118, 118) * 0.0 + (1 - step(6, 8, np.minimum(np.minimum(X, SIZE - 1 - X), np.minimum(Y, SIZE - 1 - Y)))), (92, 98, 102), height=2)
    paint(m, box(46, 52, 82, 76), (214, 176, 48), height=1.0)
    paint(m, box(50, 56, 54, 72) + box(60, 56, 64, 72) + box(70, 56, 78, 60) + box(70, 68, 78, 72), (36, 38, 42))
    return m


def cables_texture():
    m = flat((24, 24, 26), 0.35, 20)
    alpha = np.zeros((SIZE, SIZE))
    palette = [(200, 50, 40), (60, 130, 200), (220, 190, 60), (30, 30, 34), (70, 180, 90)]
    rng = np.random.default_rng(351)
    for i in range(7):
        x0, length = rng.uniform(14, 114), rng.uniform(50, 126)
        sway = rng.uniform(-5, 5)
        wire = (1 - step(1.4, 2.6, np.abs(X - (x0 + sway * np.sin(Y / 30.0 + i))))) * (1 - step(length - 4, length, Y))
        colour = palette[i % len(palette)]
        paint(m, wire, colour, height=1.2)
        alpha = np.maximum(alpha, wire)
    ALPHA["hanging_cables"] = np.where(alpha > 0.4, 255.0, 0.0)
    return m


def table_top():
    m = flat((214, 218, 220), 0.85, 120)
    grain(m, 361, 0.03)
    paint(m, (1 - step(5, 7, np.minimum(np.minimum(X, SIZE - 1 - X), np.minimum(Y, SIZE - 1 - Y)))), (150, 156, 162), height=1.4)
    weather(m, 362, 0.3)
    return m


def leg_texture():
    return plate((130, 136, 144), 371, 0.6, 80, 64)


def doorway(part):
    """A doorway standing in the air with yellow rooms behind it: the way back to Level 0."""
    m = flat((206, 186, 108), 0.3, 10, 40)
    grain(m, 381, 0.04)
    col = X % 32
    paint(m, box_x(col, 0, 2.2), (170, 150, 74), height=-0.6)  # wallpaper stripes, as in Level 0
    m.glow += 0.35
    frame = 1 - step(6, 9, np.minimum(X, SIZE - 1 - X))
    if part == 1:
        frame = np.maximum(frame, 1 - step(6, 9, Y))
    paint(m, frame, (236, 238, 236), height=2.4, glow=0.0)
    far = step(0.0, 1.0, 1 - np.abs(X - 64) / 56) * (1 - step(30, 128, Y) * 0.0)
    paint(m, far * (1 - frame) * 0.35, (255, 238, 170), glow=0.8)
    return m


def box_x(column, left, right):
    return step(left - 0.7, left, column) * (1 - step(right, right + 0.7, column))


def doorway_frame():
    m = flat((222, 180, 36), 0.45, 40)
    grain(m, 391, 0.03)
    paint(m, (((X + Y) % 32) < 16).astype(float), (28, 28, 30))
    weather(m, 392, 0.5)
    return m


def static_tear():
    rng = np.random.default_rng(401)
    m = flat((150, 156, 160), 0.2, 10)
    speck = rng.random((SIZE, SIZE))
    gray = (60 + speck * 190)[..., None] * np.ones(3)
    m.color = gray.astype(float)
    band = (np.sin(Y / 3.0 + noise(402, 4) * 6) > 0.4).astype(float)
    tint(m, band * 0.4, (0.7, 0.8, 0.9))
    m.glow += 0.8
    paint(m, step(0.7, 0.8, noise(403, 6)) * 0.6, (120, 255, 235), glow=1.0)
    return m


# The middle

def grating():
    m = flat((92, 98, 106), 0.6, 60)
    grain(m, 411, 0.03)
    paint(m, lines(16, 1.6) * 0.0 + lines(16, 1.8), (150, 156, 164), height=1.2)
    hole = (1 - lines(16, 3.0))
    ALPHA["catwalk_grating"] = np.where(hole > 0.5, 0.0, 255.0)
    paint(m, box(-2, -2, 130, 6) + box(-2, 122, 130, 130) + box(-2, -2, 6, 130) + box(122, -2, 130, 130), (214, 176, 48), height=1.4)
    ALPHA["catwalk_grating"] = np.maximum(ALPHA["catwalk_grating"], (box(-2, -2, 130, 6) + box(-2, 122, 130, 130) + box(-2, -2, 6, 130) + box(122, -2, 130, 130)).clip(0, 1) * 255)
    return m


def truss():
    m = flat((84, 90, 100), 0.55, 60)
    grain(m, 421, 0.04)
    beams = np.maximum.reduce([box(-2, -2, 130, 14), box(-2, 114, 130, 130), box(-2, -2, 14, 130), box(114, -2, 130, 130),
                               1 - step(5, 7.5, np.abs(X - Y)), 1 - step(5, 7.5, np.abs(X + Y - 127))])
    ALPHA["truss"] = np.where(beams > 0.5, 255.0, 0.0)
    paint(m, (((X + Y) % 32) < 16).astype(float) * box(-2, -2, 14, 130), (214, 176, 48))
    return m


def tether():
    m = flat((26, 28, 32), 0.45, 40)
    strand = (np.sin(X / 3.2 + Y / 14.0) > 0.2).astype(float)
    paint(m, strand * 0.55, (54, 58, 64), height=1.0)
    paint(m, box(60, -2, 68, 130), (110, 200, 230), glow=0.6)
    return m


def girder():
    m = plate((118, 126, 136), 431, 0.55, 80, 128)
    paint(m, box(-2, 20, 130, 34) + box(-2, 94, 130, 108), (80, 88, 98), height=2.0)
    paint(m, box(54, 34, 74, 94), (96, 104, 114), height=1.2)
    return m


def girder_lamp():
    m = girder()
    paint(m, box(40, 46, 88, 82), (200, 235, 255), glow=1.0, height=-0.5)
    return m


def singularity_core():
    m = flat((255, 255, 255), 0.9, 20)
    m.glow += 1.0
    m.color *= (1 - 0.06 * clouds(441, 5))[..., None]
    return m


def singularity_shell():
    """Near black with a violet cast and thin bright veins: the dark ball in the middle, lit only by the ring round it."""
    f = clouds(451, 3)
    m = flat((26, 12, 52), 0.9, 20)
    m.glow += 0.55
    tint(m, step(-0.2, 0.6, f) * 0.8, (1.5, 1.2, 2.0))
    veins = 1 - step(0.0, 0.12, np.abs(noise(452, 10) + noise(453, 22) * 0.4))
    paint(m, veins * 0.9, (170, 140, 255), glow=1.0)
    return m


PLASMA_COLOURS = [(255, 250, 232), (255, 214, 118), (255, 148, 58), (196, 66, 38)]


def plasma(heat):
    """Hot gas: bright filaments and dark gaps, whiter inside and a deep ember red at the rim."""
    base = np.array(PLASMA_COLOURS[heat], float)
    m = flat(tuple(base), 0.9, 20)
    m.glow += 0.9 if heat < 3 else 0.7
    swirl = clouds(461 + heat, 4) + noise(470 + heat, 24, 6) * 0.5
    tint(m, step(-0.3, 0.7, swirl) * 0.55, (1.0, 0.72, 0.5))
    paint(m, step(0.35, 0.75, noise(465 + heat, 16)) * 0.4, tuple(np.minimum(base * 1.25 + 30, 255)))
    tint(m, step(0.55, 0.95, -swirl) * 0.6, (0.55, 0.38, 0.32))
    return m


# Texture tables

def wear(m, seed):
    """Stains, scuffs and the odd dent: what keeps a big wall of one tile from looking like a stamp."""
    blot = step(0.35, 0.75, clouds(seed + 7, 3))
    tint(m, blot * 0.5, (0.62, 0.6, 0.56))
    streak = step(0.55, 0.9, noise(seed + 9, 48, 3))
    tint(m, streak * 0.45, (0.55, 0.54, 0.52))
    dent = step(0.6, 0.85, noise(seed + 11, 12, 12))
    m.height -= dent * 1.2
    m.color *= (1 - dent * 0.12)[..., None]
    return m


# Surfaces drawn in several variants that the blockstate picks between at random (and turns 4 ways), so no tile repeats in a row.
VARIANTED = {"wall_panel": wall_panel, "deck_floor": deck_floor, "station_corridor_floor": corridor_floor,
             "deck_ceiling": deck_ceiling, "quarters_wall": quarters_wall, "kitchen_wall": kitchen_wall, "damaged_wall": damaged_wall,
             "frost_wall": frost_wall, "scorched_wall": scorched_wall,
             "frost_floor": frost_floor, "scorched_floor": scorched_floor,
             "frost_ceiling": frost_ceiling, "scorched_ceiling": scorched_ceiling}
VARIANTS = 3


def variants():
    global SHIFT
    out = {}
    try:
        for name, draw in VARIANTED.items():
            SHIFT = 0
            out[name] = wear(draw(), 0)
            for n in range(1, VARIANTS + 1):
                SHIFT = n * 1009
                out[f"{name}_v{n}"] = wear(draw(), n * 1009)
    finally:
        SHIFT = 0
    return out


def all_textures():
    t = {
        "hull_plate": hull_plate(), "hull_seam": hull_seam(), "inner_hull_plate": inner_hull_plate(),
        "observation_glass": observation_glass(), "bulkhead": bulkhead(),
        "deck_floor": deck_floor(), "station_corridor_floor": corridor_floor(), "station_corridor_floor_stripe": corridor_floor_stripe(),
        "quarters_carpet": quarters_carpet(), "kitchen_tile": kitchen_tile(), "gym_mat": gym_mat(), "garden_floor": garden_floor(),
        "worn_floor": worn_floor(), "hazard_floor": hazard_floor(), "deck_ceiling": deck_ceiling(),
        "ceiling_light": ceiling_light(), "dead_ceiling_light": dead_ceiling_light(),
        "wall_panel": wall_panel(), "quarters_wall": quarters_wall(), "kitchen_wall": kitchen_wall(), "gym_wall": gym_wall(),
        "damaged_wall": damaged_wall(),
        "frost_wall": frost_wall(), "scorched_wall": scorched_wall(),
        "frost_floor": frost_floor(), "scorched_floor": scorched_floor(),
        "frost_ceiling": frost_ceiling(), "scorched_ceiling": scorched_ceiling(),
        "station_door_bottom": door_leaf(False), "station_door_top": door_leaf(True),
        "station_hatch_0": hatch(0), "station_hatch_1": hatch(1),
        "stair_steps": stair_steps(), "stair_core": stair_core(True), "dead_stair_core": stair_core(False),
        "safety_rail": rail_texture(), "rail_lamp": rail_lamp_texture(), "glow_orb": orb(),
        "bed_top": bed_top(), "bed_side": bed_side(), "locker_front": locker_front(), "locker_side": locker_side(),
        "counter_front": counter_front(), "counter_top": counter_top(), "stove_top": stove_top(), "stove_front": stove_front(),
        "treadmill_belt": treadmill_belt(), "treadmill_side": treadmill_side(), "weights_side": weights_side(),
        "planter_side": planter_side(), "planter_top": planter_top(), "planter_plant": planter_plant(),
        "console_front": console_front(), "console_top": console_top(), "console_side": console_side(),
        "broken_console_front": broken_console_front(), "server_front": server_front(), "crate": crate_texture(),
        "hanging_cables": cables_texture(), "table_top": table_top(), "leg": leg_texture(),
        "yellow_doorway_0": doorway(0), "yellow_doorway_1": doorway(1), "doorway_frame": doorway_frame(), "static_tear": static_tear(),
        "catwalk_grating": grating(), "truss": truss(), "tether": tether(), "girder": girder(), "girder_lamp": girder_lamp(),
        "singularity_core": singularity_core(), "singularity_shell": singularity_shell(),
    }
    for heat in range(4):
        t[f"plasma_{heat}"] = plasma(heat)
    t.update(variants())
    return t


def save_texture(name, material):
    mt.save(name, material)
    if name in ALPHA:
        rgb = np.clip(np.rint(material.color), 0, 255).astype("uint8")
        alpha = np.clip(np.rint(ALPHA[name]), 0, 255).astype("uint8")
        Image.fromarray(np.dstack([rgb, alpha]), "RGBA").save(TEXTURES / f"{name}.png", optimize=True)


def beacon_icon():
    """The B.N.T.G. beacon: a 16x16 pocket device, a grey case with a blue lens and a yellow band."""
    img = np.zeros((16, 16, 4), "uint8")
    def put(x0, y0, x1, y1, c):
        img[y0:y1, x0:x1] = (*c, 255)
    put(5, 2, 11, 14, (96, 104, 116))
    put(6, 3, 10, 13, (150, 158, 170))
    put(6, 9, 10, 11, (222, 180, 40))
    put(6, 4, 10, 8, (60, 170, 230))
    put(7, 5, 9, 7, (200, 240, 255))
    put(7, 12, 9, 13, (90, 96, 106))
    put(7, 1, 9, 2, (180, 186, 196))
    out = mt.ASSETS / "textures/item/level0p1"
    out.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(out / "bntg_beacon.png", optimize=True)


# Models

def tex(name):
    return NS + name


def el(frm, to, faces, **extra):
    element = {"from": list(frm), "to": list(to), "faces": {}}
    for face, spec in faces.items():
        element["faces"][face] = {"texture": spec} if isinstance(spec, str) else spec
    element.update(extra)
    return element


def sides(texture, names=("north", "south", "east", "west", "up", "down"), **face):
    return {name: dict(texture=texture, **face) for name in names}


def cube(texture):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": tex(texture)}}


def orientable(front, side, top):
    return {"parent": "minecraft:block/orientable", "textures": {"front": tex(front), "side": tex(side), "top": tex(top)}}


def glowing_cube(texture, emission=15):
    return {"parent": "minecraft:block/block", "textures": {"particle": tex(texture), "all": tex(texture)},
            "elements": [el((0, 0, 0), (16, 16, 16), sides("#all"), shade=False, light_emission=emission)]}


def model_bed():
    return {"parent": "minecraft:block/block",
            "textures": {"particle": tex("bed_side"), "side": tex("bed_side"), "top": tex("bed_top")},
            "elements": [el((0, 0, 0), (16, 5, 16), sides("#side")),
                         el((0.5, 5, 0.5), (15.5, 9, 15.5), {"up": "#top", "down": "#side", **sides("#side", ("north", "south", "east", "west"))})]}


def model_table():
    legs = [((1, 0, 1), (3, 11, 3)), ((13, 0, 1), (15, 11, 3)), ((1, 0, 13), (3, 11, 15)), ((13, 0, 13), (15, 11, 15))]
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("leg"), "top": tex("table_top"), "leg": tex("leg")},
            "elements": [el((0, 11, 0), (16, 13, 16), {"up": "#top", "down": "#leg", **sides("#leg", ("north", "south", "east", "west"))})]
            + [el(a, b, sides("#leg")) for a, b in legs]}


def model_bench():
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("leg"), "top": tex("table_top"), "leg": tex("leg")},
            "elements": [el((1, 5, 3), (15, 7, 13), {"up": "#top", "down": "#leg", **sides("#leg", ("north", "south", "east", "west"))}),
                         el((2, 0, 4), (4, 5, 12), sides("#leg")), el((12, 0, 4), (14, 5, 12), sides("#leg"))]}


def model_treadmill():
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("treadmill_side"), "belt": tex("treadmill_belt"), "side": tex("treadmill_side")},
            "elements": [el((1, 0, 0), (15, 5, 16), sides("#side")),
                         el((2, 5, 1), (14, 6, 15), {"up": "#belt", "down": "#side", **sides("#side", ("north", "south", "east", "west"))}),
                         el((1, 6, 12), (3, 7, 15), sides("#side")), el((13, 6, 12), (15, 7, 15), sides("#side"))]}


def model_weights():
    boxes = [((2, 0, 2), (14, 3, 14)), ((3, 3, 7), (13, 13, 9)), ((2, 10, 2), (4, 13, 14)), ((12, 10, 2), (14, 13, 14))]
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("weights_side"), "s": tex("weights_side")},
            "elements": [el(a, b, sides("#s")) for a, b in boxes]}


def model_planter():
    plant = {"north": "#plant", "south": "#plant"}
    return {"parent": "minecraft:block/block",
            "textures": {"particle": tex("planter_side"), "side": tex("planter_side"), "top": tex("planter_top"), "plant": tex("planter_plant")},
            "elements": [el((0, 0, 0), (16, 10, 16), {"up": "#top", "down": "#side", **sides("#side", ("north", "south", "east", "west"))}),
                         el((0, 10, 8), (16, 16, 8), {"north": "#plant", "south": "#plant"}, shade=False),
                         el((8, 10, 0), (8, 16, 16), {"east": "#plant", "west": "#plant"}, shade=False)]}


def model_cables():
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("hanging_cables"), "c": tex("hanging_cables")},
            "elements": [el((0, 0, 8), (16, 16, 8), {"north": "#c", "south": "#c"}, shade=False),
                         el((8, 0, 0), (8, 16, 16), {"east": "#c", "west": "#c"}, shade=False)]}


def model_console():
    return {"parent": "minecraft:block/block",
            "textures": {"particle": tex("console_side"), "side": tex("console_side"), "top": tex("console_top"), "screen": tex("console_front")},
            "elements": [el((0, 0, 0), (16, 8, 16), {"up": "#top", "down": "#side", "north": "#side", "south": "#side", "east": "#side", "west": "#side"}),
                         el((1, 8, 9), (15, 12, 15), {"north": {"texture": "#screen", "light_emission": 12}, **sides("#side", ("south", "east", "west", "up"))},
                            rotation={"angle": -22.5, "axis": "x", "origin": [8, 8, 12]})]}


def model_broken_console():
    m = model_console()
    m["textures"]["screen"] = tex("broken_console_front")
    m["elements"][1]["faces"]["north"] = {"texture": "#screen"}
    return m


def model_counter():
    return {"parent": "minecraft:block/block",
            "textures": {"particle": tex("counter_front"), "front": tex("counter_front"), "top": tex("counter_top"), "side": tex("locker_side")},
            "elements": [el((0, 0, 0), (16, 13, 16), {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"}),
                         el((0, 13, 0), (16, 14, 16), {"up": "#top", "down": "#side", **sides("#top", ("north", "south", "east", "west"))})]}


def model_stove():
    return {"parent": "minecraft:block/block",
            "textures": {"particle": tex("stove_front"), "front": tex("stove_front"), "top": tex("stove_top"), "side": tex("locker_side")},
            "elements": [el((0, 0, 0), (16, 14, 16), {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"})]}


def model_catwalk():
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("catwalk_grating"), "g": tex("catwalk_grating")},
            "elements": [el((0, 12, 0), (16, 16, 16), sides("#g"))]}


def model_tether():
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("tether"), "t": tex("tether")},
            "elements": [el((2, 0, 2), (14, 16, 14), sides("#t"))]}


def model_orb():
    return {"parent": "minecraft:block/block", "textures": {"particle": tex("glow_orb"), "o": tex("glow_orb")},
            "elements": [el((4, 4, 4), (12, 12, 12), sides("#o"), shade=False, light_emission=15)]}


DOOR_MODELS = ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
               "top_left", "top_left_open", "top_right", "top_right_open")
CUBES = ["hull_plate", "hull_seam", "inner_hull_plate", "observation_glass", "bulkhead", "deck_floor", "station_corridor_floor",
         "station_corridor_floor_stripe", "quarters_carpet", "kitchen_tile", "gym_mat", "garden_floor", "worn_floor", "hazard_floor",
         "deck_ceiling", "ceiling_light", "dead_ceiling_light", "wall_panel", "quarters_wall", "kitchen_wall", "gym_wall",
         "damaged_wall", "frost_wall", "scorched_wall", "frost_floor", "scorched_floor", "frost_ceiling", "scorched_ceiling", "stair_core", "dead_stair_core", "station_crate", "doorway_frame", "static_tear", "truss", "girder",
         "girder_lamp"]
CUBE_TEXTURE = {"station_crate": "crate", "static_tear": "static_tear", "truss": "truss", "girder": "girder", "girder_lamp": "girder_lamp"}
EMISSIVE = {"ceiling_light": 15, "static_tear": 10, "girder_lamp": 15, "stair_core": 8}
PARTS = {"station_hatch": 2, "yellow_doorway": 2}


def all_models():
    m = {}
    for name in CUBES:
        texture = CUBE_TEXTURE.get(name, name)
        m[name] = glowing_cube(texture, EMISSIVE[name]) if name in EMISSIVE else cube(texture)
    for name in VARIANTED:
        for n in range(1, VARIANTS + 1):
            m[f"{name}_v{n}"] = cube(f"{name}_v{n}")
    m["singularity_core"] = glowing_cube("singularity_core")
    m["singularity_shell"] = glowing_cube("singularity_shell")
    for heat in range(4):
        m[f"plasma_{heat}"] = glowing_cube(f"plasma_{heat}")
    m["crew_locker"] = orientable("locker_front", "locker_side", "locker_side")
    m["server_rack"] = orientable("server_front", "locker_side", "locker_side")
    m["crew_bed"] = model_bed()
    m["kitchen_counter"] = model_counter()
    m["stove"] = model_stove()
    m["treadmill"] = model_treadmill()
    m["weight_rack"] = model_weights()
    m["hydroponic_planter"] = model_planter()
    m["console"] = model_console()
    m["broken_console"] = model_broken_console()
    m["hanging_cables"] = model_cables()
    m["crew_table"] = model_table()
    m["station_bench"] = model_bench()
    m["catwalk_grating"] = model_catwalk()
    m["tether_cable"] = model_tether()
    m["glow_orb"] = model_orb()
    for name, count in PARTS.items():
        for part in range(count):
            m[f"{name}_{part}"] = hatch_model(part) if name == "station_hatch" else glowing_cube(f"{name}_{part}", 10)
    for part in DOOR_MODELS:
        m[f"station_door_{part}"] = {"parent": f"minecraft:block/door_{part}",
                                     "textures": {"bottom": tex("station_door_bottom"), "top": tex("station_door_top")}}
    for shape, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        m[f"station_stairs{shape}"] = {"parent": f"minecraft:block/{parent}",
                                       "textures": {"bottom": tex("stair_steps"), "top": tex("stair_steps"), "side": tex("stair_steps")}}
    for name, texture in (("safety_rail", "safety_rail"), ("rail_lamp", "rail_lamp")):
        m[f"{name}_post"] = {"parent": "minecraft:block/fence_post", "textures": {"texture": tex(texture)}}
        m[f"{name}_side"] = {"parent": "minecraft:block/fence_side", "textures": {"texture": tex(texture)}}
        m[f"{name}_inventory"] = {"parent": "minecraft:block/fence_inventory", "textures": {"texture": tex(texture)}}
    return m


def hatch_model(part):
    """The hatch sits in a Level 0 wall: only the north face is the hatch, the rest is that wall's wallpaper."""
    wall = "noclip_backrooms:block/level0/wallpaper"
    return {"parent": "minecraft:block/block", "textures": {"particle": tex(f"station_hatch_{part}"), "hatch": tex(f"station_hatch_{part}"), "wall": wall},
            "elements": [el((0, 0, 0), (16, 16, 16), sides("#wall", ("south", "east", "west", "up", "down"))),
                         el((0, 0, 0), (16, 16, 0), {"north": "#hatch"}, shade=False, light_emission=4)]}


# Blockstates

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_state(model):
    return {"variants": {f"facing={f}": {"model": NS + model, **({"y": y} if y else {})} for f, y in FACING_Y.items()}}


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


def stairs_state(name):
    bottom = {"straight": ("", {"east": 0, "west": 180, "south": 90, "north": 270}),
              "inner_left": ("_inner", {"east": 270, "west": 90, "south": 0, "north": 180}),
              "inner_right": ("_inner", {"east": 0, "west": 180, "south": 90, "north": 270}),
              "outer_left": ("_outer", {"east": 270, "west": 90, "south": 0, "north": 180}),
              "outer_right": ("_outer", {"east": 0, "west": 180, "south": 90, "north": 270})}
    top = {"straight": ("", {"east": 0, "west": 180, "south": 90, "north": 270}),
           "inner_left": ("_inner", {"east": 0, "west": 180, "south": 90, "north": 270}),
           "inner_right": ("_inner", {"east": 90, "west": 270, "south": 180, "north": 0}),
           "outer_left": ("_outer", {"east": 0, "west": 180, "south": 90, "north": 270}),
           "outer_right": ("_outer", {"east": 90, "west": 270, "south": 180, "north": 0})}
    variants = {}
    for half, table in (("bottom", bottom), ("top", top)):
        for shape, (suffix, turns) in table.items():
            for facing, y in turns.items():
                variant = {"model": NS + f"{name}{suffix}", "uvlock": True}
                if half == "top":
                    variant["x"] = 180
                if y:
                    variant["y"] = y
                variants[f"facing={facing},half={half},shape={shape}"] = variant
    return {"variants": variants}


def fence_state(name):
    side = lambda y: {"model": NS + f"{name}_side", "uvlock": True, **({"y": y} if y else {})}
    return {"multipart": [{"apply": {"model": NS + f"{name}_post"}},
                          {"when": {"north": "true"}, "apply": side(0)}, {"when": {"east": "true"}, "apply": side(90)},
                          {"when": {"south": "true"}, "apply": side(180)}, {"when": {"west": "true"}, "apply": side(270)}]}


SIMPLE = ["crew_bed", "hydroponic_planter", "hanging_cables", "crew_table", "station_bench", "catwalk_grating", "tether_cable",
          "glow_orb", "singularity_core", "singularity_shell"] + CUBES
FACING = ["crew_locker", "server_rack", "kitchen_counter", "stove", "treadmill", "weight_rack", "console", "broken_console"]
# Blocks whose models are written under another name than the block.
ITEM_MODEL = {"station_stairs": "station_stairs", "safety_rail": "safety_rail_inventory", "rail_lamp": "rail_lamp_inventory",
              "station_door": "station_door_bottom_left", "station_hatch": "station_hatch_0", "yellow_doorway": "yellow_doorway_0",
              "plasma": "plasma_0", "station_crate": "station_crate"}
FACING_MODEL = {"crew_locker": "crew_locker", "server_rack": "server_rack", "kitchen_counter": "kitchen_counter", "stove": "stove",
                "treadmill": "treadmill", "weight_rack": "weight_rack", "console": "console", "broken_console": "broken_console"}
UNBREAKABLE = {"hull_plate", "hull_seam", "inner_hull_plate", "observation_glass", "station_hatch", "yellow_doorway", "doorway_frame",
               "static_tear", "singularity_core", "singularity_shell", "plasma"}


def all_blockstates():
    b = {name: {"variants": {"": {"model": NS + name}}} for name in SIMPLE}
    for name in FACING:
        b[name] = facing_state(FACING_MODEL[name])
    b["station_door"] = door_state("station_door")
    b["station_stairs"] = stairs_state("station_stairs")
    b["safety_rail"] = fence_state("safety_rail")
    b["rail_lamp"] = fence_state("rail_lamp")
    b["yellow_doorway"] = {"variants": {f"part={p}": {"model": NS + f"yellow_doorway_{p}"} for p in range(PARTS["yellow_doorway"])}}
    b["station_hatch"] = {"variants": {f"facing={f},part={p}": {"model": NS + f"station_hatch_{p}", **({"y": y} if y else {})}
                                       for f, y in FACING_Y.items() for p in range(PARTS["station_hatch"])}}
    b["plasma"] = {"variants": {f"heat={h}": {"model": NS + f"plasma_{h}"} for h in range(4)}}
    for name in VARIANTED:
        models = [name] + [f"{name}_v{n}" for n in range(1, VARIANTS + 1)]
        b[name] = {"variants": {"": [{"model": NS + model, "y": y} for model in models for y in (0, 90, 180, 270)]}}
    return b


def item_model(name):
    return NS + ITEM_MODEL.get(name, name)


# Writing

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def preview(textures):
    names = ["hull_plate", "hull_seam", "inner_hull_plate", "observation_glass", "bulkhead", "deck_floor", "station_corridor_floor",
             "station_corridor_floor_stripe", "quarters_carpet", "kitchen_tile", "gym_mat", "garden_floor", "worn_floor", "hazard_floor",
             "deck_ceiling", "ceiling_light", "wall_panel", "quarters_wall", "kitchen_wall", "gym_wall", "damaged_wall",
             "station_door_top", "station_door_bottom", "station_hatch_1", "stair_steps", "stair_core", "locker_front", "counter_front",
             "console_front", "server_front", "crate", "static_tear", "yellow_doorway_0", "catwalk_grating", "truss", "plasma_0",
             "plasma_2", "singularity_shell", "girder_lamp", "tether"]
    columns, gap, side = 8, 8, SIZE
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (side + gap) + gap, rows * (side + gap) + gap), (24, 24, 28))
    for i, name in enumerate(names):
        tile = Image.fromarray(np.clip(np.rint(textures[name].color), 0, 255).astype("uint8"), "RGB")
        sheet.paste(tile, (gap + i % columns * (side + gap), gap + i // columns * (side + gap)))
    out = mt.ROOT / "docs/levels/level0p1/textures.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, optimize=True)


def loot_table(name, door=False):
    pool = {"rolls": 1.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}]}
    if door:
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": f"noclip_backrooms:{name}",
                                   "properties": {"half": "lower"}})
    return {"type": "minecraft:block", "pools": [pool], "random_sequence": f"noclip_backrooms:blocks/{name}"}


def check(blockstates):
    prefix = "noclip_backrooms:block/"
    for path in MODELS.glob("*.json"):
        data = json.loads(path.read_text(encoding="utf8"))
        for value in data.get("textures", {}).values():
            if value.startswith(NS):
                for suffix in ("", "_n", "_s"):
                    assert (TEXTURES / f"{value[len(NS):]}{suffix}.png").is_file(), (path.name, value, suffix)
    for name, state in blockstates.items():
        entries = state["variants"].values() if "variants" in state else [part["apply"] for part in state["multipart"]]
        flat_entries = [v for entry in entries for v in (entry if isinstance(entry, list) else [entry])]
        for variant in flat_entries:
            if variant["model"].startswith(prefix):
                assert (ASSETS / f"models/block/{variant['model'][len(prefix):]}.json").is_file(), (name, variant["model"])
        item = json.loads((ASSETS / f"items/{name}.json").read_text(encoding="utf8"))
        assert (ASSETS / f"models/block/{item['model']['model'][len(prefix):]}.json").is_file(), name


# The shared files: language, mining tags, sounds

LANG_EN = {
    "hull_plate": "Hull Plate", "hull_seam": "Hull Seam", "inner_hull_plate": "Inner Hull Plate", "observation_glass": "Observation Glass",
    "bulkhead": "Bulkhead", "deck_floor": "Deck Floor", "station_corridor_floor": "Corridor Floor", "station_corridor_floor_stripe": "Marked Corridor Floor",
    "quarters_carpet": "Quarters Carpet", "kitchen_tile": "Galley Tile", "gym_mat": "Gym Mat", "garden_floor": "Hydroponic Moss",
    "worn_floor": "Worn Deck Floor", "hazard_floor": "Hazard Floor", "deck_ceiling": "Deck Ceiling", "ceiling_light": "Ceiling Light",
    "dead_ceiling_light": "Dead Ceiling Light", "wall_panel": "Wall Panel", "quarters_wall": "Quarters Wall", "kitchen_wall": "Galley Wall",
    "gym_wall": "Padded Wall", "damaged_wall": "Damaged Wall", "frost_wall": "Frosted Wall", "scorched_wall": "Scorched Wall", "frost_floor": "Frosted Floor", "scorched_floor": "Scorched Floor", "frost_ceiling": "Frosted Ceiling", "scorched_ceiling": "Scorched Ceiling", "station_door": "Station Door", "station_stairs": "Station Stairs",
    "stair_core": "Stair Core", "dead_stair_core": "Dead Stair Core", "safety_rail": "Safety Rail", "rail_lamp": "Rail Lamp",
    "glow_orb": "Glow Orb", "crew_bed": "Crew Bunk", "crew_locker": "Crew Locker", "kitchen_counter": "Galley Counter",
    "stove": "Galley Stove", "treadmill": "Treadmill", "weight_rack": "Weight Rack", "hydroponic_planter": "Hydroponic Planter",
    "console": "Console", "broken_console": "Broken Console", "server_rack": "Server Rack", "station_crate": "Supply Crate",
    "hanging_cables": "Hanging Cables", "crew_table": "Crew Table", "station_bench": "Station Bench", "station_hatch": "Station Hatch",
    "yellow_doorway": "Yellow Doorway", "doorway_frame": "Doorway Frame", "static_tear": "Static Tear", "catwalk_grating": "Catwalk Grating",
    "truss": "Truss", "tether_cable": "Tether Cable", "girder": "Girder", "girder_lamp": "Girder Lamp",
    "singularity_core": "Singularity Core", "singularity_shell": "Singularity Shell", "plasma": "Plasma",
}
LANG_ZH = {
    "hull_plate": "外殼板", "hull_seam": "外殼接縫", "inner_hull_plate": "內殼板", "observation_glass": "觀景玻璃",
    "bulkhead": "隔艙壁", "deck_floor": "甲板地板", "station_corridor_floor": "走廊地板", "station_corridor_floor_stripe": "標線走廊地板",
    "quarters_carpet": "寢室地毯", "kitchen_tile": "廚房地磚", "gym_mat": "健身墊", "garden_floor": "水培苔蘚",
    "worn_floor": "磨損甲板", "hazard_floor": "警示地板", "deck_ceiling": "甲板天花板", "ceiling_light": "天花板燈",
    "dead_ceiling_light": "壞掉的天花板燈", "wall_panel": "牆板", "quarters_wall": "寢室牆", "kitchen_wall": "廚房牆",
    "gym_wall": "軟墊牆", "damaged_wall": "損壞的牆", "frost_wall": "結霜的牆", "scorched_wall": "焦黑的牆", "frost_floor": "結冰的地板", "scorched_floor": "焦黑的地板", "frost_ceiling": "結霜的天花板", "scorched_ceiling": "焦黑的天花板", "station_door": "站內自動門", "station_stairs": "站內樓梯",
    "stair_core": "樓梯中柱", "dead_stair_core": "熄滅的樓梯中柱", "safety_rail": "安全欄杆", "rail_lamp": "欄杆燈",
    "glow_orb": "發光球", "crew_bed": "船員床鋪", "crew_locker": "船員置物櫃", "kitchen_counter": "廚房檯面",
    "stove": "廚房爐具", "treadmill": "跑步機", "weight_rack": "重訓架", "hydroponic_planter": "水培種植槽",
    "console": "控制台", "broken_console": "壞掉的控制台", "server_rack": "伺服器機櫃", "station_crate": "補給箱",
    "hanging_cables": "垂掛電纜", "crew_table": "船員桌", "station_bench": "站內長椅", "station_hatch": "站內艙門",
    "yellow_doorway": "黃色門框", "doorway_frame": "門框", "static_tear": "雜訊裂縫", "catwalk_grating": "格柵步道",
    "truss": "桁架", "tether_cable": "繫纜", "girder": "大樑", "girder_lamp": "大樑燈",
    "singularity_core": "奇點核心", "singularity_shell": "奇點外殼", "plasma": "電漿",
}
MESSAGES_EN = {
    "message.noclip_backrooms.level0p1.cold": "The air here is deadly cold... the climate control is dead.",
    "message.noclip_backrooms.level0p1.hot": "The heat in here is close to lethal... the climate control is dead.",
    "message.noclip_backrooms.level0p1.beacon_dead": "The beacon only hums. Whatever it is tuned to, it is not here.",
    "message.noclip_backrooms.level0p1.announce.0": "[Station] Please remain calm. All personnel proceed to your assigned shelter.",
    "message.noclip_backrooms.level0p1.announce.1": "[Station] Containment of the bridge has failed. Please remain calm.",
    "message.noclip_backrooms.level0p1.announce.2": "[Station] Countdown suspended at zero. Please remain calm.",
    "message.noclip_backrooms.level0p1.announce.3": "[Station] Contact with Earth has not been re-established. Please remain calm.",
    "message.noclip_backrooms.level0p1.announce.4": "[Station] Climate control fault on all decks. Maintenance has been notified.",
    "message.noclip_backrooms.level0p1.announce.5": "[Station] Dr. Valpri, please report to the observation deck. Dr. Valpri.",
    "item.noclip_backrooms.bntg_beacon": "B.N.T.G. Beacon",
    "entity.noclip_backrooms.robot_cleaner": "Seven-Legged Cleaning Robot",
    "entity.noclip_backrooms.robot_chef": "Galley Robot",
    "entity.noclip_backrooms.robot_servant": "Broken Servant Robot",
    "subtitles.noclip_backrooms.level0p1.hum": "Station hums",
    "subtitles.noclip_backrooms.level0p1.alarm": "Alarm sounds far off",
    "subtitles.noclip_backrooms.level0p1.chime": "Announcement chime",
    "subtitles.noclip_backrooms.level0p1.creak": "Metal creaks",
    "subtitles.noclip_backrooms.level0p1.spark": "Sparks crackle",
    "subtitles.noclip_backrooms.level0p1.door": "Door slides",
}
MESSAGES_ZH = {
    "message.noclip_backrooms.level0p1.cold": "這裡冷得致命……空調早就壞了。",
    "message.noclip_backrooms.level0p1.hot": "這裡熱得快要致命……空調早就壞了。",
    "message.noclip_backrooms.level0p1.beacon_dead": "信標只是嗡嗡響。它對準的地方不在這裡。",
    "message.noclip_backrooms.level0p1.announce.0": "【站內廣播】請保持冷靜。所有人員請前往指定避難所。",
    "message.noclip_backrooms.level0p1.announce.1": "【站內廣播】蟲洞約束失效。請保持冷靜。",
    "message.noclip_backrooms.level0p1.announce.2": "【站內廣播】倒數暫停於零。請保持冷靜。",
    "message.noclip_backrooms.level0p1.announce.3": "【站內廣播】尚未與地球重新取得聯繫。請保持冷靜。",
    "message.noclip_backrooms.level0p1.announce.4": "【站內廣播】各層空調故障，已通知維修人員。",
    "message.noclip_backrooms.level0p1.announce.5": "【站內廣播】瓦爾普里博士，請到觀景台報到。瓦爾普里博士。",
    "item.noclip_backrooms.bntg_beacon": "B.N.T.G. 信標",
    "entity.noclip_backrooms.robot_cleaner": "七足清洗機器人",
    "entity.noclip_backrooms.robot_chef": "廚房機器人",
    "entity.noclip_backrooms.robot_servant": "壞掉的服務機器人",
    "subtitles.noclip_backrooms.level0p1.hum": "太空站嗡鳴",
    "subtitles.noclip_backrooms.level0p1.alarm": "遠處警報聲",
    "subtitles.noclip_backrooms.level0p1.chime": "廣播提示音",
    "subtitles.noclip_backrooms.level0p1.creak": "金屬嘎吱作響",
    "subtitles.noclip_backrooms.level0p1.spark": "火花劈啪",
    "subtitles.noclip_backrooms.level0p1.door": "門滑開",
}
SOUNDS = {
    "level0p1.hum": ("minecraft:block.beacon.ambient", 0.7, 0.5),
    "level0p1.alarm": ("minecraft:block.note_block.bit", 0.9, 0.5),
    "level0p1.chime": ("minecraft:block.note_block.chime", 0.9, 0.8),
    "level0p1.creak": ("minecraft:block.chain.break", 0.6, 0.5),
    "level0p1.spark": ("minecraft:block.fire.extinguish", 0.5, 1.4),
    "level0p1.door": ("minecraft:block.piston.extend", 0.5, 1.6),
}


def update_lang():
    for file, blocks, messages in (("en_us.json", LANG_EN, MESSAGES_EN), ("zh_tw.json", LANG_ZH, MESSAGES_ZH)):
        path = ASSETS / "lang" / file
        data = json.loads(path.read_text(encoding="utf8"))
        for name, text in blocks.items():
            data[f"block.noclip_backrooms.{name}"] = text
        data.update(messages)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")


def update_tags(unbreakable_free):
    path = DATA / "minecraft/tags/block/mineable/pickaxe.json"
    data = json.loads(path.read_text(encoding="utf8"))
    values = data["values"]
    for name in unbreakable_free:
        entry = f"noclip_backrooms:{name}"
        if entry not in values:
            values.append(entry)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def update_sounds():
    path = ASSETS / "sounds.json"
    data = json.loads(path.read_text(encoding="utf8"))
    for name, (event, volume, pitch) in SOUNDS.items():
        data[name] = {"subtitle": f"subtitles.noclip_backrooms.{name}",
                      "sounds": [{"name": event, "type": "event", "volume": volume, "pitch": pitch}]}
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES  # make_textures writes Level 0's textures; point its writer at Level 0.1's folder
    textures = all_textures()
    for name, material in textures.items():
        save_texture(name, material)
    beacon_icon()
    preview(textures)
    for name, model in all_models().items():
        write_json(MODELS / f"{name}.json", model)
    blockstates = all_blockstates()
    mineable = []
    for name, state in blockstates.items():
        write_json(ASSETS / f"blockstates/{name}.json", state)
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": item_model(name)}})
        if name not in UNBREAKABLE:
            write_json(DATA / f"noclip_backrooms/loot_table/blocks/{name}.json", loot_table(name, door=name == "station_door"))
            mineable.append(name)
    # The beacon: item definition and model
    write_json(ASSETS / "items/bntg_beacon.json", {"model": {"type": "minecraft:model", "model": "noclip_backrooms:item/level0p1/bntg_beacon"}})
    write_json(ASSETS / "models/item/level0p1/bntg_beacon.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "noclip_backrooms:item/level0p1/bntg_beacon"}})
    check(blockstates)
    update_lang()
    update_tags(mineable)
    update_sounds()
    print(f"{len(textures)} materials, {len(blockstates)} blocks")


if __name__ == "__main__":
    main()
