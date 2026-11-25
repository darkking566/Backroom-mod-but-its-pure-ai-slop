"""Redraws the Garden sector's plants (grass tuft, fern, three wildflowers) with shading, in place of the flat drawings.

Each is painted at 1024 px and written at 512 px into the realistic resource pack and at 128 px into the mod, with
labPBR _n (flat) and _s (rough, a little subsurface scattering for leaves) companions. Cutout: alpha is 0 or 255.
Only these five textures are touched; the models and blockstates stay as make_level1.py made them.

Run: python tools/make_garden_plants.py
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level1"
PACK = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level1"
PREVIEW = ROOT / "docs/look/level1_garden_plants.png"
S = 1024
SPEC = (90, 10, 170, 255)  # smoothness, F0, subsurface (leaves let some light through), no emission


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def bezier(p0, p1, p2, t):
    u = 1 - t
    return (u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1])


def blade(d, p0, p1, p2, w0, w1, c0, c1, steps=28, rib=True):
    """A tapered blade or stem along a curve, darker at its foot, lit on one side of the midrib."""
    pts = [bezier(p0, p1, p2, i / steps) for i in range(steps + 1)]
    for i in range(steps):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        dx, dy = x1 - x0, y1 - y0
        n = math.hypot(dx, dy) or 1
        nx, ny = -dy / n, dx / n
        t0, t1 = i / steps, (i + 1) / steps
        wa, wb = (w0 + (w1 - w0) * t0) / 2, (w0 + (w1 - w0) * t1) / 2
        col = lerp(c0, c1, (t0 + t1) / 2)
        left = [(x0 + nx * wa, y0 + ny * wa), (x1 + nx * wb, y1 + ny * wb), (x1, y1), (x0, y0)]
        right = [(x0, y0), (x1, y1), (x1 - nx * wb, y1 - ny * wb), (x0 - nx * wa, y0 - ny * wa)]
        d.polygon(left, fill=shade(col, 1.12 if rib else 1.0) + (255,))
        d.polygon(right, fill=shade(col, 0.86 if rib else 1.0) + (255,))


def leaf(d, base, angle, length, width, c0, c1):
    """A pointed leaf from base, pointing at angle (radians, 0 = up), bending a little under its own weight."""
    tip = (base[0] + math.sin(angle) * length, base[1] - math.cos(angle) * length + length * 0.12)
    mid = (base[0] + math.sin(angle) * length * 0.55, base[1] - math.cos(angle) * length * 0.62)
    steps = 16
    pts = [bezier(base, mid, tip, i / steps) for i in range(steps + 1)]
    for i in range(steps):
        t = (i + 0.5) / steps
        w = width * math.sin(math.pi * min(1.0, t * 1.05)) / 2 + 1
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        n = math.hypot(x1 - x0, y1 - y0) or 1
        nx, ny = -(y1 - y0) / n, (x1 - x0) / n
        col = lerp(c0, c1, t)
        d.polygon([(x0 + nx * w, y0 + ny * w), (x1 + nx * w, y1 + ny * w), (x1, y1), (x0, y0)], fill=shade(col, 1.12) + (255,))
        d.polygon([(x0, y0), (x1, y1), (x1 - nx * w, y1 - ny * w), (x0 - nx * w, y0 - ny * w)], fill=shade(col, 0.84) + (255,))


GREENS = [(58, 92, 44), (72, 108, 50), (86, 118, 56), (64, 98, 58), (96, 120, 62)]
DRY = [(150, 132, 84), (128, 110, 70), (166, 148, 98)]


def grass(rng):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(95):
        x = S / 2 + np.clip(rng.normal(0, 110), -250, 250)
        h = rng.uniform(0.45, 0.95) * S
        lean = np.clip(rng.normal(0, 0.3) * h, -(x - 30), S - 30 - x)  # stay inside the picture
        dry = rng.random() < 0.16
        top = DRY[rng.integers(len(DRY))] if dry else GREENS[rng.integers(len(GREENS))]
        foot = shade(top, 0.45)
        tip = shade(top, 1.25) if not dry else shade(top, 1.1)
        p0 = (x, S)
        p2 = (x + lean, S - h)
        p1 = (x + lean * 0.2, S - h * 0.6)
        blade(d, p0, p1, p2, rng.uniform(16, 30), 2, foot, tip)
    return img


def fern(rng):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fronds = sorted(rng.uniform(-1.05, 1.05, 7), key=abs, reverse=True)  # the outer ones first, the upright ones in front
    for a in fronds:
        length = S * rng.uniform(0.62, 0.8) * (1 - abs(a) * 0.25)
        p0 = (S / 2 + a * 40, S)
        p2 = (S / 2 + math.sin(a) * length * 0.9, S - math.cos(a) * length * 0.75)
        p1 = (S / 2 + math.sin(a) * length * 0.35, S - length * 0.85)
        stem = (70, 84, 40)
        blade(d, p0, p1, p2, 12, 3, shade(stem, 0.6), stem, rib=False)
        pairs = 15
        for i in range(2, pairs):
            t = i / pairs
            x, y = bezier(p0, p1, p2, t)
            x2, y2 = bezier(p0, p1, p2, min(1, t + 0.01))
            heading = math.atan2(x2 - x, -(y2 - y))
            size = (1 - t) * 0.85 + 0.15
            dried = rng.random() < 0.08 + 0.25 * t * (abs(a) > 0.7)
            c1 = DRY[rng.integers(len(DRY))] if dried else (92, 128, 58)
            c0 = shade(c1, 0.6)
            for side in (-1, 1):
                leaf(d, (x, y), heading + side * 1.15, S * 0.16 * size, S * 0.045 * size, c0, c1)
    return img


FLOWERS = {
    "blue": ((84, 104, 168), (132, 150, 204), (58, 50, 70)),
    "red": ((150, 40, 34), (196, 70, 56), (40, 30, 28)),
    "yellow": ((196, 158, 54), (228, 200, 96), (120, 80, 30)),
}


def flower(rng, colour):
    petal0, petal1, centre = FLOWERS[colour]
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    stem = (66, 96, 46)
    # Leaves at the foot of the clump.
    for _ in range(9):
        a = rng.uniform(-1.2, 1.2)
        leaf(d, (S / 2 + rng.normal(0, 40), S), a, S * rng.uniform(0.22, 0.36), S * rng.uniform(0.05, 0.08), shade(stem, 0.55), shade(stem, 1.1))
    heads = []
    for k in range(int(rng.integers(3, 5))):
        x = S / 2 + rng.normal(0, 110)
        h = S * rng.uniform(0.55, 0.88)
        lean = rng.normal(0, 0.2) * h
        p0, p2 = (x, S), (x + lean, S - h)
        p1 = (x + lean * 0.1, S - h * 0.55)
        blade(d, p0, p1, p2, 14, 7, shade(stem, 0.5), stem, rib=False)
        for t in (0.35, 0.55):
            bx, by = bezier(p0, p1, p2, t)
            side = 1 if rng.random() < 0.5 else -1
            leaf(d, (bx, by), side * rng.uniform(0.7, 1.2), S * 0.15, S * 0.04, shade(stem, 0.6), shade(stem, 1.1))
        heads.append((p2, rng.uniform(0.75, 1.15) * (1 if k else 0.7)))
    for (cx, cy), size in heads:
        if size < 0.8:  # a bud that has not opened
            d.ellipse([cx - 22, cy - 34, cx + 22, cy + 8], fill=shade(petal0, 0.8) + (255,))
            d.ellipse([cx - 14, cy - 10, cx + 14, cy + 14], fill=shade(stem, 0.9) + (255,))
            continue
        r = 78 * size
        petals = int(rng.integers(6, 9))
        turn = rng.uniform(0, math.pi)
        for i in range(petals):
            if rng.random() < 0.1:
                continue  # one fallen off
            a = turn + i * 2 * math.pi / petals + rng.normal(0, 0.12)
            length = r * rng.uniform(0.85, 1.1)
            for j, k in enumerate(np.linspace(1.0, 0.35, 6)):
                px, py = cx + math.cos(a) * length * (0.55 * k + 0.1), cy + math.sin(a) * length * (0.55 * k + 0.1) * 0.8
                rw = length * 0.32 * k + 6
                col = lerp(shade(petal0, 0.7), petal1, 1 - j / 6)
                d.ellipse([px - rw, py - rw * 0.8, px + rw, py + rw * 0.8], fill=col + (255,))
        d.ellipse([cx - r * 0.24, cy - r * 0.2, cx + r * 0.24, cy + r * 0.2], fill=centre + (255,))
        for _ in range(14):
            ox, oy = rng.normal(0, r * 0.08), rng.normal(0, r * 0.07)
            d.ellipse([cx + ox - 4, cy + oy - 4, cx + ox + 4, cy + oy + 4], fill=shade(centre, 1.6) + (255,))
    return img


def write(name, img):
    for size, folder in ((512, PACK), (128, BASE)):
        small = img.resize((size, size), Image.LANCZOS)
        a = np.array(small)
        alpha = a[..., 3] > 110
        out = np.dstack([a[..., :3], (alpha * 255).astype("uint8")])  # Pillow resizes RGBA premultiplied: no dark fringe
        Image.fromarray(out, "RGBA").save(folder / f"{name}.png", optimize=True)
        Image.new("RGBA", (size, size), (128, 128, 255, 255)).save(folder / f"{name}_n.png", optimize=True)
        Image.new("RGBA", (size, size), SPEC).save(folder / f"{name}_s.png", optimize=True)


def main():
    plants = {
        "garden_grass": grass(np.random.default_rng(1700)),
        "garden_fern": fern(np.random.default_rng(1710)),
        "garden_flower_blue": flower(np.random.default_rng(1720), "blue"),
        "garden_flower_red": flower(np.random.default_rng(1730), "red"),
        "garden_flower_yellow": flower(np.random.default_rng(1740), "yellow"),
    }
    for name, img in plants.items():
        write(name, img)
    sheet = Image.new("RGBA", (5 * 260, 520), (46, 50, 44, 255))
    for i, name in enumerate(plants):
        for row, folder in enumerate((PACK, BASE)):
            im = Image.open(folder / f"{name}.png").convert("RGBA").resize((250, 250), Image.NEAREST if row else Image.LANCZOS)
            sheet.alpha_composite(im, (i * 260 + 5, row * 260 + 5))
    sheet.convert("RGB").save(PREVIEW)
    print("5 garden plants ->", PACK.relative_to(ROOT), "and", BASE.relative_to(ROOT), "; preview", PREVIEW.relative_to(ROOT))


if __name__ == "__main__":
    main()
