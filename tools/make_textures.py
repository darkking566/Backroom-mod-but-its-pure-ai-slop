"""Draws every block texture from scratch at 128x128. Edit the colours, then run:

    python tools/make_textures.py

Needs Pillow and NumPy. Every texture gets two companions that shaders read in the labPBR format:
  *_n.png  bumps: normal (RG), crevice shading (B), depth (A)
  *_s.png  surface: smoothness (R), reflectance (G), porosity (B), glow (A)
Also writes docs/textures.png, a tiled preview.
"""

import json
from dataclasses import dataclass

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
OUT = ASSETS / "textures/block"
SIZE = 128
Y, X = np.mgrid[0:SIZE, 0:SIZE].astype(float)


@dataclass
class Material:
    color: np.ndarray
    height: np.ndarray  # in pixels; only differences matter
    smooth: np.ndarray
    f0: np.ndarray
    porosity: np.ndarray
    glow: np.ndarray


def rgb(*color):
    return np.array(color, float)


def flat(color, smooth=0.2, f0=10, porosity=0):
    full = lambda value: np.full((SIZE, SIZE), float(value))
    return Material(np.ones((SIZE, SIZE, 3)) * rgb(*color), full(0), full(smooth), full(f0), full(porosity), full(0))


def noise(seed, cells_x, cells_y=None):
    """Smooth noise in -1..1 that wraps on both axes."""
    cells_y = cells_y or cells_x
    grid = np.random.default_rng(seed).uniform(-1, 1, (cells_y, cells_x))
    gx, gy = X * cells_x / SIZE, Y * cells_y / SIZE
    x0, y0 = gx.astype(int), gy.astype(int)
    tx, ty = gx - x0, gy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    x1, y1 = (x0 + 1) % cells_x, (y0 + 1) % cells_y
    top = grid[y0, x0] * (1 - tx) + grid[y0, x1] * tx
    bottom = grid[y1, x0] * (1 - tx) + grid[y1, x1] * tx
    return top * (1 - ty) + bottom * ty


def clouds(seed, cells=4):
    return sum(noise(seed + i, min(SIZE, cells * 2 ** i)) * 0.5 ** i for i in range(4)) / 1.875


def step(low, high, value):
    t = np.clip((value - low) / (high - low), 0, 1)
    return t * t * (3 - 2 * t)


def inside():
    """1 in the middle and 0 on the border, so variants meet without seams."""
    edge = np.minimum.reduce([X, Y, SIZE - 1 - X, SIZE - 1 - Y])
    return step(1, 22, edge)


def box(left, top, right, bottom, soft=1.0):
    return (step(left - soft, left, X) * (1 - step(right, right + soft, X))
            * step(top - soft, top, Y) * (1 - step(bottom, bottom + soft, Y)))


def paint(m, mask, color=None, height=None, smooth=None, f0=None, porosity=None, glow=None):
    if color is not None:
        m.color += (np.array(color, float) - m.color) * mask[..., None]
    for name, value in (("height", height), ("smooth", smooth), ("f0", f0), ("porosity", porosity), ("glow", glow)):
        if value is not None:
            old = getattr(m, name)
            old += (value - old) * mask


def tint(m, mask, amount):
    """Multiplies the colour where mask is 1."""
    m.color *= 1 + (np.array(amount, float) - 1) * mask[..., None]


# Walls

YELLOW = dict(paper=(204, 184, 105), line=(174, 151, 74), light=(220, 203, 128), motif=(187, 165, 87), board=(150, 128, 78))
PALE = dict(paper=(214, 209, 191), line=(186, 180, 160), light=(228, 224, 208), motif=(198, 192, 172), board=(168, 161, 141))
RED = dict(paper=(124, 34, 29), line=(90, 19, 17), light=(144, 48, 39), motif=(105, 25, 22), board=(70, 22, 19))


def wallpaper(palette):
    m = flat(palette["paper"], 0.28, 10, 18)
    fibre = noise(1, 128, 42) * 0.6 + noise(2, 64, 128) * 0.4
    m.color *= (1 + fibre * 0.035 + noise(3, 32) * 0.018)[..., None]
    m.height += fibre * 0.16

    # Periods divide 128, so the pattern carries on into the next block.
    column = X % 32
    paint(m, box_x(column, 0, 2.2), palette["line"], height=-0.7, smooth=0.22)
    paint(m, box_x(column, 3.2, 4.2), palette["light"], height=0.55, smooth=0.36)
    paint(m, box_x(column, 29.5, 30.4) * 0.65, palette["line"], height=-0.3)

    middle = np.abs(column - 16.5)
    row = (Y + 16 * ((X // 32) % 2)) % 32
    for top, weight, reach in ((7, 1.3, 8.5), (11.5, 0.9, 6.0)):
        band = np.abs(row - top - middle * 0.75)
        chevron = (1 - step(weight, weight + 0.9, band)) * (1 - step(reach - 1, reach, middle))
        paint(m, chevron, palette["motif"], height=0.5, smooth=0.38)
    dot = 1 - step(1.2, 2.3, np.hypot(middle, row - 22))
    paint(m, dot, palette["motif"], height=0.5)
    return m


def box_x(column, left, right):
    return step(left - 0.7, left, column) * (1 - step(right, right + 0.7, column))


def damp(m, seed, strength=1.0):
    field = clouds(seed, 4) + inside() * 0.75 - 0.72
    patch = step(-0.02, 0.2, field) * inside() * strength
    rim = (1 - step(0.02, 0.1, np.abs(field - 0.04))) * inside() * strength
    tint(m, patch, (0.93, 0.91, 0.86))
    tint(m, rim, (0.91, 0.87, 0.78))
    paint(m, patch, smooth=0.55, porosity=40)
    return m


def scuffs(m, seed):
    streaks = step(0.35, 0.75, noise(seed, 50, 5) * 0.7 + noise(seed + 1, 8) * 0.5) * inside()
    tint(m, streaks, (0.82, 0.79, 0.72))
    paint(m, streaks, smooth=0.16)
    return m


def grime(m, seed=40):
    # Noise that only varies along the wall and wraps, under a fade from the floor.
    rise = 14 + 7 * noise(seed, 8, 1) + 3 * noise(seed + 1, 32, 1)
    dirt = 1 - step(-rise * 0.4, rise * 1.7, SIZE - 24 - Y)
    tint(m, dirt, (0.77, 0.74, 0.66))
    paint(m, dirt, smooth=0.14)
    return m


def board(color):
    # Nothing varies along the length: overlapping mitred ends must be pixel-identical.
    m = flat(color, 0.55, 12)
    m.height += 3 * step(0, 22, Y) - 1.5 * (1 - step(1.5, 4, np.abs(Y - 30)))
    tint(m, 1 - step(3, 9, Y), (1.18, 1.17, 1.13))
    tint(m, 1 - step(1.5, 4, np.abs(Y - 30)), (0.72, 0.7, 0.66))
    tint(m, step(86, 127, Y), (0.5, 0.46, 0.38))
    return m


def peeling(seed, size, centre):
    m = wallpaper(YELLOW)
    cx, cy = centre
    distance = np.hypot((X - cx) / size[0], (Y - cy) / size[1])
    field = 1 - distance + clouds(seed, 6) * 0.75 + noise(seed + 9, 64) * 0.07
    field = field * inside() - 0.6 * (1 - inside())
    below = wallpaper(RED)
    torn = step(0.04, 0.07, field)

    stained = step(-0.28, 0.0, field)
    tint(m, stained, (0.8, 0.74, 0.6))
    backing = step(-0.07, -0.02, field) * (1 - torn)
    paint(m, backing, (214, 198, 158), height=2.2, smooth=0.12)
    tint(below, 1 - step(0.06, 0.2, field), (0.4, 0.4, 0.4))  # shade under the lifted edge
    glue = step(0.3, 0.6, clouds(seed + 20, 10))
    tint(below, glue, (0.72, 0.7, 0.68))
    paint(m, torn, below.color, below.height - 2.5, below.smooth, 10, 30)
    return m


# Floor

def carpet(seed, thick=False):
    dark, light = ((98, 80, 49), (148, 125, 80)) if thick else ((122, 101, 59), (182, 160, 105))
    m = flat(dark, 0.06, 8, 60)
    depth = np.zeros((SIZE, SIZE))
    rng = np.random.default_rng(seed)
    for _ in range(5200 if thick else 6500):
        x, y = rng.uniform(0, SIZE, 2)
        angle = rng.uniform(0, 2 * np.pi)
        length = int(rng.integers(3, 9 if thick else 6))
        shade = rng.beta(3, 3)
        top = rng.uniform(0.5, 1.4) * (1.6 if thick else 1.0)
        color = np.array(dark) + (np.array(light) - np.array(dark)) * shade
        for i in range(length):
            arch = np.sin(np.pi * (i + 0.5) / length)  # each fibre is a little loop
            px, py = int(x + np.cos(angle) * i) % SIZE, int(y + np.sin(angle) * i) % SIZE
            if top * arch >= depth[py, px]:
                depth[py, px] = top * arch
                m.color[py, px] = color * (0.78 + 0.22 * arch)
    m.height += depth
    if thick:
        wet = step(0.05, 0.4, clouds(seed + 3, 5)) * inside()
        tint(m, wet, (0.88, 0.89, 0.89))
        paint(m, wet, smooth=0.62, f0=8)
    return m


def soiled(m, seed):
    field = clouds(seed, 5) + inside() * 0.7 - 0.62
    patch = step(-0.05, 0.22, field) * inside()
    tint(m, patch, (0.87, 0.86, 0.82))
    paint(m, patch, smooth=0.5, f0=8)
    return m


def carpet_edge(top):
    m = flat((74, 70, 62), 0.1, 10, 25)
    grit = noise(61, 64) * 0.7 + noise(62, 24) * 0.3
    m.color *= (1 + grit * 0.16)[..., None]
    m.height += grit * 0.8
    ragged = 1 - step(0, 2, Y - 11 - 4 * noise(63, 32, 1))
    paint(m, ragged, top.color, top.height + 2, 0.06, 8, 60)
    tint(m, (1 - step(1, 5, np.abs(Y - 19))) * (1 - ragged), (0.5, 0.48, 0.45))
    return m


# Ceiling and lights

def ceiling_tile():
    m = flat((208, 202, 180), 0.12, 8, 40)
    grain = noise(70, 128) * 0.5 + noise(71, 48) * 0.5
    m.color *= (1 + grain * 0.04)[..., None]
    m.height += grain * 0.35
    holes = step(0.7, 0.9, noise(72, 85)) + step(0.8, 0.92, noise(73, 40, 60))
    holes = np.clip(holes, 0, 1)
    paint(m, holes, (164, 157, 136), height=-1.3)
    edge = np.minimum.reduce([X, Y, SIZE - 1 - X, SIZE - 1 - Y])
    tint(m, 1 - step(2, 8, edge), (0.92, 0.91, 0.89))
    return m


def water_mark(m, seed):
    field = clouds(seed, 4) + inside() * 0.9 - 0.75
    patch = step(0, 0.12, field) * inside()
    tint(m, patch, (0.96, 0.92, 0.82))
    for level in (0.02, 0.2):
        ring = (1 - step(0.01, 0.06, np.abs(field - level))) * inside()
        tint(m, ring, (0.89, 0.8, 0.64))
    return m


def metal(color, smooth=0.7):
    m = flat(color, smooth, 20)
    brushed = noise(80, 8, 128)
    m.color *= (1 + brushed * 0.035)[..., None]
    m.height += brushed * 0.1
    return m


def panel(glass, tube, glow):
    m = flat(glass, 0.8, 12)
    # A prismatic diffuser over two tubes that run along X.
    prism = np.abs(X % 6 - 3) + np.abs(Y % 6 - 3)
    m.height += prism * 0.25
    m.glow += glow * 0.65
    for centre in (40, 88):
        halo = np.exp(-((Y - centre) / 13) ** 2)
        core = 1 - step(5, 9, np.abs(Y - centre))
        paint(m, halo * 0.7, tube, glow=glow * 0.85)
        paint(m, core, np.minimum(np.array(tube, float) * 1.12, 255), glow=glow)
    m.color *= (1 + (prism - 3) * 0.018)[..., None]
    return m


def dead_panel():
    m = panel((98, 95, 80), (136, 133, 116), 0)
    ends = (1 - step(10, 30, np.minimum(X, SIZE - 1 - X))) * (1 - step(6, 12, np.minimum(np.abs(Y - 40), np.abs(Y - 88))))
    tint(m, ends, (0.4, 0.38, 0.35))
    dead = step(0.78, 0.9, noise(82, 28))  # dead insects and dust in the cover
    tint(m, dead, (0.62, 0.6, 0.55))
    return m


# The way out

def door(top):
    m = flat((46, 108, 70), 0.58, 12)
    orange_peel = noise(90, 80) * 0.6 + noise(91, 16) * 0.4
    m.color *= (1 + orange_peel * 0.05)[..., None]
    m.height += orange_peel * 0.2
    if top:
        paint(m, box(38, 22, 90, 92, 2), (28, 42, 33), height=2.5)  # bead
        glass = box(45, 29, 83, 85)
        daylight = rgb(206, 236, 220) + (24 * (1 - Y / SIZE))[..., None]
        paint(m, glass, daylight, height=-2, smooth=0.94, glow=0.7)
        wire = np.maximum(1 - step(0.4, 1.2, np.abs((X + Y) % 14 - 7)), 1 - step(0.4, 1.2, np.abs((X - Y) % 14 - 7)))
        paint(m, wire * glass, (112, 138, 124), glow=0.1)
    else:
        kick = box(12, 82, 116, 120)
        paint(m, kick, metal((150, 154, 146)).color, height=1.8, smooth=0.75, f0=255)
        scrape = step(0.45, 0.8, noise(92, 8, 70)) * kick
        tint(m, scrape, (0.66, 0.65, 0.62))
        paint(m, scrape, smooth=0.35)
        for cx in (18, 110):
            for cy in (88, 114):
                screw = 1 - step(1.5, 3, np.hypot(X - cx, Y - cy))
                paint(m, screw, (70, 73, 70), height=0.5)
    chipped = step(0.68, 0.74, clouds(93 + top, 12)) * inside()
    paint(m, chipped * 0.7, (96, 98, 88), height=-0.5, smooth=0.2)
    return m


def exit_sign():
    scale = 4
    mask = Image.new("L", (SIZE * scale, SIZE * scale))
    draw = ImageDraw.Draw(mask)
    top, bottom, middle, t = 45, 83, 64, 6.5

    def poly(*points):
        draw.polygon([(x * scale, y * scale) for x, y in points], fill=255)

    def bar(left, upper, right, lower):
        poly((left, upper), (right, upper), (right, lower), (left, lower))

    bar(14, top, 14 + t, bottom)  # E
    for y in (top, middle - t / 2, bottom - t):
        bar(14, y, 37, y + t)
    poly((43, top), (43 + t + 1, top), (71, bottom), (71 - t - 1, bottom))  # X
    poly((71 - t - 1, top), (71, top), (43 + t + 1, bottom), (43, bottom))
    bar(77, top, 77 + t, bottom)  # I
    bar(90, top, 115, top + t)  # T
    bar(102.5 - t / 2, top, 102.5 + t / 2, bottom)
    letters = np.asarray(mask.resize((SIZE, SIZE), Image.Resampling.LANCZOS), float) / 255

    m = metal((40, 48, 43))
    face = box(6, 34, 122, 94)
    paint(m, face, (14, 140, 62), height=1, smooth=0.8, glow=0.6)
    paint(m, letters, (236, 255, 240), height=1.4, glow=1.0)
    return m


def all_textures():
    t = {}
    for prefix, palette in (("", YELLOW), ("pale_", PALE), ("red_", RED)):
        name = prefix + "wallpaper"
        t[name] = wallpaper(palette)
        t[name + "_1"] = damp(wallpaper(palette), 110)
        t[name + "_2"] = scuffs(wallpaper(palette), 120)
        t[name + "_3"] = damp(scuffs(wallpaper(palette), 130), 140, 0.7)
        t[name + "_baseboard"] = grime(wallpaper(palette))
        t[name + "_baseboard_1"] = damp(grime(wallpaper(palette)), 150)
        t[prefix + "board"] = board(palette["board"])

    t["peeling_red_wallpaper"] = peeling(200, (42, 50), (64, 64))
    t["peeling_red_wallpaper_1"] = peeling(210, (24, 56), (48, 66))
    t["peeling_red_wallpaper_2"] = peeling(220, (56, 26), (64, 48))
    t["peeling_red_wallpaper_3"] = peeling(230, (22, 24), (78, 80))

    t["carpet"] = carpet(300)
    t["carpet_1"] = carpet(310)
    t["carpet_2"] = soiled(carpet(300), 320)
    t["carpet_edge"] = carpet_edge(t["carpet"])
    t["thick_carpet"] = carpet(400, True)
    t["thick_carpet_1"] = carpet(410, True)
    t["thick_carpet_edge"] = carpet_edge(t["thick_carpet"])

    t["ceiling_tile"] = ceiling_tile()
    t["ceiling_tile_1"] = water_mark(ceiling_tile(), 500)
    t["ceiling_tile_2"] = water_mark(ceiling_tile(), 510)
    t["ceiling_grid"] = metal((176, 173, 158))
    t["light_housing"] = metal((206, 202, 182))

    t["fluorescent_light"] = panel((236, 231, 196), (255, 254, 232), 1)
    t["broken_fluorescent_light"] = dead_panel()
    t["red_fluorescent_light"] = panel((150, 32, 28), (236, 70, 56), 0.85)
    t["exit_light"] = panel((54, 170, 88), (172, 255, 188), 0.9)

    t["exit_door"] = door(False)
    t["exit_door_top"] = door(True)
    t["exit_frame"] = metal((36, 52, 42))
    t["exit_bar"] = metal((188, 192, 184), 0.85)
    t["exit_bar"].f0[:] = 255
    t["exit_sign"] = exit_sign()
    return t


# Writing

def save(name, m):
    def write(suffix, channels, mode):
        Image.fromarray(np.clip(np.rint(channels), 0, 255).astype("uint8"), mode).save(OUT / f"{name}{suffix}.png", optimize=True)

    write("", m.color, "RGB")

    h = m.height
    around = [np.roll(h, shift, axis) for axis in (0, 1) for shift in (-1, 1)]
    down, up, right, left = around
    # DirectX-style normals, as labPBR wants: a slope rising to the right tilts the normal left.
    nx, ny = -(right - left) * 0.55, -(down - up) * 0.55
    length = np.sqrt(nx * nx + ny * ny + 1)
    crevice = np.clip(1 - np.maximum(sum(around) / 4 - h, 0) * 0.35, 0.55, 1)
    depth = np.clip(255 - (h.max() - h) * 3.5, 225, 255)
    write("_n", np.dstack([(nx / length + 1) * 127.5, (ny / length + 1) * 127.5, crevice * 255, depth]), "RGBA")
    # An alpha of 255 means "no glow" in labPBR.
    glow = np.where(m.glow > 0.01, np.minimum(m.glow * 254, 254), 255)
    write("_s", np.dstack([m.smooth * 255, m.f0, m.porosity, glow]), "RGBA")


def preview(textures):
    names = ["wallpaper", "pale_wallpaper", "red_wallpaper", "wallpaper_1", "wallpaper_baseboard", "board",
             "peeling_red_wallpaper", "peeling_red_wallpaper_1", "peeling_red_wallpaper_2",
             "carpet", "carpet_2", "thick_carpet", "ceiling_tile", "ceiling_tile_1", "carpet_edge",
             "fluorescent_light", "broken_fluorescent_light", "red_fluorescent_light",
             "exit_light", "exit_sign", "exit_door_top", "exit_door"]
    columns, gap, side = 6, 8, SIZE * 2
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (side + gap) + gap, rows * (side + gap) + gap), (30, 30, 30))
    for i, name in enumerate(names):
        tile = Image.fromarray(np.clip(np.rint(textures[name].color), 0, 255).astype("uint8"), "RGB")
        for dx in (0, SIZE):
            for dy in (0, SIZE):
                sheet.paste(tile, (gap + i % columns * (side + gap) + dx, gap + i // columns * (side + gap) + dy))
    sheet.save(ROOT / "docs/textures.png", optimize=True)


def check():
    """Every texture a model uses must exist with both companions, and every model a block uses must exist."""
    prefix = "noclip_backrooms:block/"
    for path in (ASSETS / "models/block").glob("*.json"):
        for value in json.loads(path.read_text(encoding="utf8")).get("textures", {}).values():
            if value.startswith(prefix):
                for suffix in ("", "_n", "_s"):
                    assert (OUT / f"{value[len(prefix):]}{suffix}.png").is_file(), (path.name, value, suffix)
    for path in (ASSETS / "blockstates").glob("*.json"):
        for entry in json.loads(path.read_text(encoding="utf8"))["variants"].values():
            for variant in entry if isinstance(entry, list) else [entry]:
                model = ASSETS / f"models/block/{variant['model'][len(prefix):]}.json"
                assert model.is_file(), (path.name, variant["model"])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    textures = all_textures()
    for name, material in textures.items():
        save(name, material)
    preview(textures)
    check()
    print(f"{len(textures)} materials, {len(textures) * 3} files")


if __name__ == "__main__":
    main()
