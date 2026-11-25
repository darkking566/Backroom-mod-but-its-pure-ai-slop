"""Realistic Level 0 textures for the built-in "realistic" resource pack (first sample, 2026-11-24).

Writes 512 px colour textures with labPBR _n/_s companions into
src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level0/. A resource pack
overrides textures by name, so the drawn 128 px textures of make_textures.py (and that tool) stay as they are;
turning the pack off in the Resource Packs screen brings them back. Also writes the pack's pack.mcmeta and a
before/after sheet at docs/look/level0_realistic.png.

Photo material comes from ambientCG (https://ambientcg.com, CC0), fetched on first use into tools/.cache/ambientcg/
(not committed): Carpet016 and Fabric028 for the carpet; Wallpaper001A for the paper grain; Wallpaper002A/B/C for
water stains and mould. The wallpaper print (after the original Level 0 photo: beaded lines with stacked chevrons
between them, every other column half a step lower), the ceiling tiles, the light panels, the baseboard and the
painted metal are drawn here. Shading for raised or sunken detail is drawn into the colour as well, because
without a shader pack Minecraft ignores the normal maps.

Run: py tools/make_level0_realistic.py   (needs Pillow, NumPy and SciPy)
"""
import copy
import io
import json
import re
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
PACK = ROOT / "src/main/resources/resourcepacks/realistic"
OUT = PACK / "assets/noclip_backrooms/textures/block/level0"
OLD = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level0"
CACHE = TOOLS / ".cache/ambientcg"
PREVIEW = ROOT / "docs/look/level0_realistic.png"
S = 512
Y, X = np.mgrid[0:S, 0:S].astype(np.float32)
T = (Y + 0.5) / S  # 0 at the top of a texture, 1 at the bottom


# ------------------------------------------------------------------------------------------ photo sources

def _fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "noclip-backrooms-texture-tool"})
    return urllib.request.urlopen(request, timeout=300).read()


def source(asset, res):
    """The maps of an ambientCG material, {"Color": path, "NormalDX": path, ...}, downloaded once into the cache."""
    folder = CACHE / asset / res
    if not any(folder.glob("*_Color.jpg")):
        answer = json.loads(_fetch(f"https://ambientcg.com/api/v2/full_json?id={asset}&include=downloadData"))
        link = None
        for found in answer.get("foundAssets", []):
            for category in found.get("downloadFolders", {}).values():
                for download in category.get("downloadFiletypeCategories", {}).get("zip", {}).get("downloads", []):
                    if download.get("attribute") == res:
                        link = download.get("downloadLink") or download.get("fullDownloadPath")
        if not link or not re.match(r"^https://(www\.)?ambientcg\.com/", link):
            raise SystemExit(f"ambientCG has no {res} download for {asset}")
        folder.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(io.BytesIO(_fetch(link))) as archive:
            for member in archive.namelist():
                name = Path(member).name
                if re.fullmatch(r"[A-Za-z0-9_\-]+\.(jpg|png)", name):
                    (folder / name).write_bytes(archive.read(member))
    return {f.stem.rsplit("_", 1)[-1]: f for f in folder.glob("*.jpg")}


def resize(a, size=S):
    """Resizes a seamless image so that it stays seamless."""
    f = size / a.shape[0]
    rest = (0,) * (a.ndim - 2)
    if f < 1:
        a = ndimage.gaussian_filter(a, (0.42 / f, 0.42 / f) + rest, mode="wrap")
    return ndimage.zoom(a, (f, f) + (1,) * (a.ndim - 2), order=3, mode="grid-wrap", grid_mode=True)


def load(path, gray=False):
    image = Image.open(path).convert("L" if gray else "RGB")
    return np.clip(resize(np.asarray(image, dtype=np.float32) / 255), 0, 1)


def crop_seamless(path, part=768):
    """A part of a photo at close to its own scale, made seamless by fading into a copy shifted by half."""
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.float32)[:part, :part] / 255
    shifted = np.roll(a, (part // 2, part // 2), (0, 1))
    i = np.arange(part, dtype=np.float32)
    w = np.minimum(i, part - 1 - i) / (part / 2)
    w = np.clip(np.minimum.outer(w, w) * 2.2 - 0.25, 0, 1)
    w = w * w * (3 - 2 * w)
    return np.clip(resize(a * w[..., None] + shifted * (1 - w[..., None])), 0, 1)


def load_normal(path, strength=1.0):
    return (load(path)[..., :2] * 2 - 1) * strength


# ------------------------------------------------------------------------------------------ helpers

def rgb(r, g, b):
    return np.array([r, g, b], np.float32) / 255


def blur(a, sigma):
    sigma = sigma if isinstance(sigma, tuple) else (sigma, sigma)
    return ndimage.gaussian_filter(a, sigma + (0,) * (a.ndim - 2), mode="wrap")


def fbm(seed, base=4, octaves=5, falloff=0.55):
    """Seamless fractal noise in 0..1."""
    rng = np.random.default_rng(seed)
    out, amp, total, n = np.zeros((S, S), np.float32), 1.0, 0.0, base
    for _ in range(octaves):
        out += amp * ndimage.zoom(rng.random((n, n)), S / n, order=3, mode="grid-wrap", grid_mode=True)
        total += amp
        amp *= falloff
        n = min(n * 2, S)
    out /= total
    return (out - out.min()) / (out.max() - out.min())


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    return a * (1 - t[..., None]) + np.asarray(b, np.float32) * t[..., None] if t.ndim == 2 else a * (1 - t) + b * t


def shade(m, factor):
    m.color = m.color * np.asarray(factor, np.float32)[..., None]


def fade_edges():
    """1 in the middle, 0 at the block edge: stains fade out before it, so blocks meet without a cut-off stain."""
    return smoothstep(6, 64, edge_distance())


def big_stain(seed, cover):
    """One large stain with a ragged edge: a soft blob roughened by the photo stains."""
    rough = blur(np.roll(WATER, (seed * 131 % S, seed * 71 % S), (0, 1)), 3)
    n = (fbm(seed, base=2, octaves=4) + 0.35 * rough) * fade_edges()
    t = np.quantile(n, 1 - cover)
    return smoothstep(t - 0.03, t + 0.03, n)


def region(seed, cover, soft=0.12, base=2):
    """A soft blob covering about `cover` of the texture."""
    n = fbm(seed, base=base, octaves=4) * fade_edges()
    t = np.quantile(n, 1 - cover)
    return smoothstep(t - soft, t + soft, n)


def supersample(draw, scale=4):
    """A greyscale mask drawn by draw(ImageDraw, size) at `scale` times the size, then shrunk smoothly."""
    big = S * scale
    image = Image.new("L", (big, big), 0)
    draw(ImageDraw.Draw(image), big)
    return np.asarray(image.resize((S, S), Image.LANCZOS), dtype=np.float32) / 255


def edge_distance():
    return np.minimum(np.minimum(X, S - 1 - X), np.minimum(Y, S - 1 - Y))


class Material:
    def __init__(self, color, height=None, normal=None, smooth=0.2, f0=10, porosity=0.3, glow=None, bump=40.0):
        self.color = np.array(color, np.float32)
        self.height = np.full((S, S), 0.5, np.float32) if height is None else np.array(height, np.float32)
        self.normal = normal
        self.smooth = np.broadcast_to(np.float32(smooth), (S, S)).copy() if np.ndim(smooth) == 0 else np.array(smooth, np.float32)
        self.f0 = f0
        self.porosity = porosity
        self.glow = glow
        self.bump = bump

    def copy(self):
        return copy.deepcopy(self)

    def roll(self, dy, dx):
        for name in ("color", "height", "smooth", "normal"):
            value = getattr(self, name)
            if value is not None:
                setattr(self, name, np.roll(value, (dy, dx), (0, 1)))
        return self


def save(name, m):
    """The colour texture with labPBR companions, laid out like make_textures.save."""
    OUT.mkdir(parents=True, exist_ok=True)

    def write(suffix, a, mode):
        Image.fromarray(np.clip(np.rint(a * 255), 0, 255).astype(np.uint8), mode).save(OUT / f"{name}{suffix}.png", optimize=True)

    write("", np.clip(m.color, 0, 1), "RGB")
    h = m.height
    # DirectX-style, as labPBR wants: a slope rising to the right tilts the normal left.
    x1 = -(np.roll(h, -1, 1) - np.roll(h, 1, 1)) * m.bump
    y1 = -(np.roll(h, -1, 0) - np.roll(h, 1, 0)) * m.bump
    z1 = np.ones_like(x1)
    if m.normal is not None:  # "whiteout" blend with the photo's own normals
        x2, y2 = m.normal[..., 0], m.normal[..., 1]
        z2 = np.sqrt(np.clip(1 - x2 * x2 - y2 * y2, 0.01, 1))
        length = np.sqrt(x1 * x1 + y1 * y1 + 1)
        x1, y1, z1 = x1 / length + x2, y1 / length + y2, (1 / length) * z2
    length = np.sqrt(x1 * x1 + y1 * y1 + z1 * z1)
    crevice = np.clip(1 - np.maximum(blur(h, 2) - h, 0) * 4, 0.6, 1)
    normal = np.dstack([(x1 / length + 1) / 2, (y1 / length + 1) / 2, crevice, 1 - (1 - h) * 0.12])
    # Softened by a pixel and stored a little coarser: the photo noise otherwise doubles the file size, and these maps
    # only show with a shader pack.
    step = np.array([4, 4, 16, 16], np.float32)
    write("_n", (np.floor(np.clip(blur(normal, 1.0), 0, 1) * 255 / step) * step + step // 2) / 255, "RGBA")
    glow = np.ones((S, S)) if m.glow is None else np.where(m.glow > 0.01, np.minimum(m.glow, 1) * 254 / 255, 1.0)
    porosity = np.broadcast_to(np.float32(m.porosity), (S, S)) * 64 / 255
    write("_s", np.dstack([np.clip(m.smooth, 0, 1), np.full((S, S), m.f0 / 255), porosity, glow]), "RGBA")


# ------------------------------------------------------------------------------------------ stains

def water_stain(m, mask, fill=0.35, rim=0.45):
    """The area browns a little and a darker tide line marks where the water stopped."""
    inner = ndimage.grey_erosion(mask, size=(5, 5), mode="wrap")
    edge = blur(np.clip(mask - inner, 0, 1), 1.0)
    m.color = mix(m.color, m.color * rgb(222, 196, 146), mask * fill)
    m.color = mix(m.color, m.color * rgb(150, 118, 76), np.clip(edge * 2.5, 0, 1) * rim)


def tide_rings(m, mask, levels=(0.12, 0.38, 0.66), fill=0.5):
    """A ceiling stain that dried and wetted again: a yellow-brown fill with a ring at each level it reached."""
    soft = blur(mask, 6)
    m.color = mix(m.color, m.color * rgb(236, 206, 150), smoothstep(0.06, 0.3, soft) * fill)
    for level in levels:
        line = np.exp(-((soft - level) / 0.03) ** 2) * (soft > 0.02)
        m.color = mix(m.color, m.color * rgb(170, 128, 78), line * 0.55)


def drips(seed, count, length, width, top=(0.0, 0.45)):
    """Streaks of water run down from where they started."""
    rng = np.random.default_rng(seed)
    out = np.zeros((S, S), np.float32)
    for _ in range(count):
        x, y0 = rng.uniform(0, S), rng.uniform(*top) * S
        run, w = rng.uniform(*length) * S, rng.uniform(*width)
        wobble = ndimage.gaussian_filter1d(rng.standard_normal(S).astype(np.float32), 18, mode="wrap") * 30
        dx = (X - x - wobble[Y.astype(int)] + S / 2) % S - S / 2
        along = (Y - y0) / run
        fade = np.where((along >= 0) & (along <= 1), np.clip(1 - along, 0, 1) ** 0.6, 0) * smoothstep(-0.02, 0.03, along)
        out = np.maximum(out, np.exp(-(dx / w) ** 2) * fade)
    return out


def dots(seed, count, radius, satellites=3):
    rng = np.random.default_rng(seed)

    def draw(d, big):
        k = big / S
        for _ in range(count):
            x, y, r = rng.uniform(40, S - 40), rng.uniform(40, S - 40), rng.uniform(*radius)
            d.ellipse([(x - r) * k, (y - r * rng.uniform(0.7, 1.2)) * k, (x + r) * k, (y + r) * k], fill=int(rng.uniform(170, 255)))
            for _ in range(rng.integers(0, satellites + 1)):
                a, s = rng.uniform(0, 2 * np.pi), rng.uniform(0.2, 0.45) * r
                sx, sy = x + np.cos(a) * r * rng.uniform(1.4, 2.6), y + np.sin(a) * r * rng.uniform(1.4, 2.6)
                d.ellipse([(sx - s) * k, (sy - s) * k, (sx + s) * k, (sy + s) * k], fill=int(rng.uniform(140, 230)))

    return blur(supersample(draw), 0.4)


def specks(seed, density, size=0.7):
    rng = np.random.default_rng(seed)
    return np.clip(blur((rng.random((S, S)) < density).astype(np.float32), size) * 6, 0, 1)


# ------------------------------------------------------------------------------------------ wallpaper

WALL = rgb(216, 196, 104)
INK_LIGHT = rgb(240, 228, 166)
INK_DARK = rgb(150, 128, 62)
# (paper, light ink, dark ink, baseboard) for each wallpaper, after make_textures' palettes
YELLOW = (WALL, INK_LIGHT, INK_DARK, rgb(150, 126, 80))
PALE = (rgb(222, 216, 198), rgb(238, 234, 220), rgb(184, 178, 158), rgb(168, 160, 140))
RED = (rgb(130, 36, 30), rgb(152, 52, 42), rgb(92, 20, 18), rgb(70, 22, 19))
ROUGH = (rgb(166, 148, 82), rgb(184, 170, 120), rgb(116, 100, 50), rgb(96, 80, 48))  # the unlit rooms'


def wallpaper_print():
    """After the original photo: thin beaded lines between columns, stacked chevrons in the columns."""
    columns, period = 8, 256  # at 4x: 8 columns a block, a chevron group every 64 px, alternate columns half a step lower

    def light(d, big):
        col = big // columns
        for c in range(columns + 1):
            x0 = c * col
            d.rectangle([x0 - 5, 0, x0 + 5, big], fill=150)
            for y in range(-64, big + 64, 64):
                d.ellipse([x0 - 13, y + 10, x0 + 13, y + 54], fill=255)
            if c == columns:
                continue
            cx, drop = x0 + col // 2, (period // 2) * (c % 2)
            for g in range(-1, big // period + 1):
                for k in range(3):
                    top = g * period + drop + k * 34
                    d.line([(cx - 76, top + 46), (cx, top), (cx + 76, top + 46)], fill=255, width=19, joint="curve")

    def dark(d, big):
        col = big // columns
        for c in range(columns + 1):
            for y in range(-64, big + 64, 64):
                d.ellipse([c * col - 6, y + 22, c * col + 6, y + 42], fill=255)

    return blur(supersample(light), 0.5), blur(supersample(dark), 0.5)


def paper():
    maps = source("Wallpaper001A", "2K-JPG")
    lum = load(maps["Color"], gray=True)
    return lum - blur(lum, 6), load_normal(maps["NormalDX"], 0.6), 1 - load(maps["Roughness"], gray=True)


def wallpaper_base(seed, pal=YELLOW):
    light, dark = PRINT
    grain, normal, smooth = PAPER
    ink = 0.8 + 0.2 * fbm(seed + 1, base=16, octaves=3)
    color = np.broadcast_to(pal[0], (S, S, 3)).copy()
    color = mix(color, pal[1], light * 0.85 * ink)
    color = mix(color, pal[2], dark * 0.8 * ink)
    color *= (1 + grain * 1.8)[..., None]
    age = fbm(seed + 2, base=3)
    color *= (0.95 + 0.07 * age)[..., None]
    streaks = blur(np.random.default_rng(seed + 3).random((1, S)).astype(np.float32).repeat(S, 0), (0, 2))
    color *= (0.985 + 0.03 * (streaks - streaks.mean()) / (streaks.std() + 1e-6) * 0.5)[..., None]
    color = mix(color, color * rgb(236, 218, 176), (1 - age) * 0.3)
    height = 0.5 + grain * 2 + light * 0.03
    return Material(color, height, normal, smooth=np.clip(smooth * 0.6, 0.05, 0.4), porosity=0.6, bump=60)


def blister(m, seed, count, radius):
    """Air under the paper: raised domes, lit from the ceiling above."""
    rng = np.random.default_rng(seed)
    for _ in range(count):
        cx, cy, r = rng.uniform(70, S - 70), rng.uniform(70, S - 70), rng.uniform(*radius)
        sx = r * rng.uniform(0.8, 1.3)
        d2 = ((X - cx) / sx) ** 2 + ((Y - cy) / r) ** 2
        dome = np.clip(1 - d2, 0, 1) ** 1.5
        slope = np.clip(-(Y - cy) / r, -1, 1) * dome  # the upper half faces the lights
        m.height = m.height + dome * 0.35
        shade(m, 1 + 0.2 * slope - 0.14 * np.exp(-((np.sqrt(d2) - 1) / 0.14) ** 2) * (Y > cy))


def wallpaper_variant(i, pal=YELLOW):
    m = wallpaper_base(seed=10 * i, pal=pal)
    rng = np.random.default_rng(100 + i)
    if i == 1:  # water stains
        water_stain(m, big_stain(i, 0.22), fill=0.28, rim=0.3)
    elif i == 2:  # short drips
        streak = drips(i, 9, (0.08, 0.3), (1.2, 2.6))
        m.color = mix(m.color, m.color * rgb(176, 146, 100), streak * 0.4)
    elif i == 3:  # long runs from a leak above
        streak = drips(i, 6, (0.5, 1.0), (2.5, 6), top=(0.0, 0.1))
        m.color = mix(m.color, m.color * rgb(170, 140, 96), streak * 0.35)
        water_stain(m, np.roll(WATER, (0, rng.integers(S)), (0, 1)) * (1 - smoothstep(0.1, 0.35, T)) * fade_edges(), fill=0.22, rim=0.25)
    elif i == 4:  # a dirty smear at hand height
        swipe = blur(rng.random((S, S)).astype(np.float32), (1.5, 16))
        swipe = smoothstep(0.5, 0.56, swipe / swipe.max()) * region(i + 50, 0.18, soft=0.1, base=3)
        m.color = mix(m.color, rgb(96, 84, 58), blur(swipe, 1.2) * 0.3)
    elif i == 5:  # a patch of newer paper, nailed up crooked
        x0, y0, w, h = 168, 150, 158, 132
        inside = ((X >= x0) & (X < x0 + w) & (Y >= y0) & (Y < y0 + h)).astype(np.float32)
        patch = wallpaper_base(seed=777, pal=pal).roll(37, 23)
        m.color = mix(m.color, patch.color * 1.035, inside)
        m.height = m.height + inside * 0.05
        rim_low = ((Y >= y0 + h) & (Y < y0 + h + 3) & (X >= x0) & (X < x0 + w + 3)) | ((X >= x0 + w) & (X < x0 + w + 3) & (Y >= y0) & (Y < y0 + h + 3))
        shade(m, np.where(rim_low, 0.8, 1) * np.where(((Y == y0) | (X == x0)) & (inside > 0), 1.06, 1))
        nail = np.exp(-(((X - x0 - w / 2) ** 2 + (Y - y0 - 9) ** 2) / 6))
        m.color = mix(m.color, rgb(60, 56, 50), nail)
    elif i == 6:  # blisters
        blister(m, i, 5, (14, 34))
    elif i == 7:  # a small tear showing the backing paper
        tear = smoothstep(0.55, 0.6, region(i, 0.03, soft=0.02, base=4))
        rim = np.clip(tear - ndimage.grey_erosion(tear, size=(4, 4), mode="wrap"), 0, 1)
        backing = rgb(224, 218, 202) * (1 + PAPER[0] * 2)[..., None]
        m.color = mix(m.color, backing, tear)
        m.color = mix(m.color, rgb(246, 242, 230), rim * 0.8)
        under = np.roll(rim, 2, 0) * (1 - tear)
        shade(m, 1 - under * 0.3)
        m.height = m.height - tear * 0.2
    elif i == 8:  # mould
        mould = np.rot90(MOULD, i % 4) * region(i, 0.3, soft=0.15)
        m.color = mix(m.color, rgb(84, 88, 60), mould * 0.38)
        m.color = mix(m.color, rgb(48, 50, 36), specks(i, 0.006) * mould * 0.7)
    elif i == 9:  # dark spots
        m.color = mix(m.color, rgb(92, 68, 44), dots(i, 8, (2, 6)) * 0.6)
    return m


def peel(seed, shape, wobble=0.75):
    """Yellow paper torn or peeling away from the red paper under it (the warning walls round a Red Room).
    `shape` is positive where the paper is gone, in make_textures' 128-px coordinates."""
    m = wallpaper_base(seed=900 + seed)
    below = wallpaper_base(seed=950 + seed, pal=RED)
    edge = fade_edges()
    field = shape + (fbm(seed, base=6) - 0.5) * wobble * 0.6 + (fbm(seed + 9, base=48, octaves=2) - 0.5) * 0.12
    field = field * edge - 0.6 * (1 - edge)
    torn = smoothstep(0.04, 0.07, field)
    shade(m, 1 - 0.22 * smoothstep(-0.28, 0.0, field))  # wet and darker round the tear
    backing = smoothstep(-0.07, -0.02, field) * (1 - torn)
    m.color = mix(m.color, rgb(214, 198, 158) * (1 + PAPER[0] * 2)[..., None], backing)
    shade(below, 1 - 0.6 * (1 - smoothstep(0.06, 0.2, field)))  # in the shadow of the lifted edge
    shade(below, 1 - 0.28 * smoothstep(0.3, 0.6, fbm(seed + 20, base=10)))  # old glue
    m.color = mix(m.color, below.color, torn)
    m.height = m.height - torn * 0.15 + backing * 0.2
    return m


def peeling_variants():
    """The same nine tears as make_textures, at four times the size."""
    x, y = X / 4, Y / 4

    def blob(size, centre):
        return 1 - np.hypot((x - centre[0]) / size[0], (y - centre[1]) / size[1])

    def turned(angle):
        dx, dy = x - 64, y - 64
        return dx * np.cos(angle) + dy * np.sin(angle), -dx * np.sin(angle) + dy * np.cos(angle)

    a, b = turned(0.8)
    c, d = turned(1.15)
    holes = [1 - np.hypot((x - hx) / r, (y - hy) / (r * 0.8)) for hx, hy, r in ((44, 46, 13), (82, 58, 10), (58, 86, 11), (90, 92, 7))]
    return [
        peel(200, blob((42, 50), (64, 64))),
        peel(210, blob((24, 56), (48, 66))),
        peel(220, blob((56, 26), (64, 48))),
        peel(230, blob((22, 24), (78, 80))),
        peel(240, 1 - np.hypot((x - 70) / 9, np.maximum(np.abs(y - 62) - 22, 0) / 10), 0.45),  # along a seam
        peel(250, np.maximum.reduce(holes), 0.5),  # small tears
        peel(260, blob((44, 18), (64, 36))),  # a sheet hanging from the top
        peel(270, 1 - np.hypot(a / 52, b / 11), 0.45),  # a long tear
        peel(280, np.maximum.reduce([1 - np.hypot(c / 40, (d - 9 * k) / 2.2) for k in (-1, 0, 1)]), 0.08),  # gouges
    ]


def wallpaper_baseboard(stained, pal=YELLOW):
    m = wallpaper_base(seed=200, pal=pal)
    shade(m, 1 - 0.24 * smoothstep(0.70, 0.825, T))  # in the board's shadow (the board covers the bottom 2.8/16)
    wick = smoothstep(0.52, 0.82, T) * (0.6 + 0.4 * fbm(201, base=8))
    m.color = mix(m.color, m.color * rgb(226, 204, 158), wick * 0.4)
    if stained:
        water_stain(m, big_stain(202, 0.45) * smoothstep(0.35, 0.7, T), fill=0.5)
    return m


def board(base=YELLOW[3]):
    """The baseboard, drawn tall: the model squeezes this whole texture into a strip 2.8/16 of a block high. Nothing
    changes along its length: at inside corners two boards overlap, and any difference there flickers."""
    rng = np.random.default_rng(300)
    profile = np.where(T < 0.12, 0.93 + 0.14 * np.sin(np.pi * T / 0.12),
                       np.where(T < 0.16, 0.72, np.where(T < 0.88, 1 - 0.07 * (T - 0.16) / 0.72, 0.86 - 0.12 * smoothstep(0.88, 1, T))))
    grain = blur(rng.random((S, 1)).astype(np.float32), (1.5, 0)).repeat(S, 1)
    grain = (grain - grain.mean()) / grain.std()
    color = base * (profile * (1 + 0.03 * grain))[..., None]
    color = mix(color, base * 0.53, smoothstep(0.78, 1.0, T) * 0.25)  # dirt along the floor
    height = np.where(T < 0.12, 0.8 + 0.2 * np.sin(np.pi * T / 0.12), np.where(T < 0.16, 0.4, 0.7))
    return Material(color, height, smooth=0.45, porosity=0.1, bump=8)


# ------------------------------------------------------------------------------------------ carpet

CARPET = rgb(150, 126, 82)


DRY_CARPET = rgb(176, 150, 96)  # paler and trodden flat


def carpet_base(seed, shift=(0, 0), tone=CARPET, contrast=1.5):
    maps = source("Carpet016", "2K-JPG")
    photo = crop_seamless(maps["Color"])
    lum = photo.mean(2)
    color = tone * (lum / lum.mean())[..., None] ** contrast + (photo - photo.mean((0, 1))) * 0.35
    color *= (1 - 0.08 * DIRT)[..., None]
    color *= (0.93 + 0.10 * fbm(seed, base=3))[..., None]
    normal = crop_seamless(maps["NormalDX"])[..., :2] * 2 - 1
    m = Material(color, crop_seamless(maps["Displacement"]).mean(2), normal,
                 smooth=(1 - crop_seamless(maps["Roughness"]).mean(2)) * 0.4, porosity=0.9, bump=30)
    return m.roll(*shift)


def wet(m, mask, darken=0.32, rim=0.12):
    edge = blur(np.clip(mask - ndimage.grey_erosion(mask, size=(5, 5), mode="wrap"), 0, 1), 1.5)
    shade(m, (1 - darken * mask) * (1 - rim * np.clip(edge * 2, 0, 1)))
    m.smooth = np.maximum(m.smooth, 0.35 * mask)


def carpet_variant(i, tone=CARPET, contrast=1.5):
    m = carpet_base(seed=20 + i, shift=((i * 197) % S, (i * 311) % S), tone=tone, contrast=contrast)
    rng = np.random.default_rng(400 + i)
    if i == 2:  # big damp stains
        wet(m, region(i, 0.35, soft=0.08, base=3) * 0.9)
    elif i == 3:  # a damp area
        wet(m, region(i, 0.5, soft=0.25) * 0.6, rim=0)
    elif i == 4:  # a ring where a puddle dried
        cx, cy, r = S * 0.45, S * 0.42, S * 0.17
        a = np.arctan2(Y - cy, X - cx)
        wobble = 1 + 0.12 * np.sin(3 * a + 1.3) + 0.07 * np.sin(7 * a + 0.4)
        d = np.hypot(X - cx, Y - cy) / wobble
        m.color = mix(m.color, m.color * rgb(150, 120, 86), np.exp(-((d - r) / 4.5) ** 2) * 0.8)
        shade(m, 1 - 0.1 * (d < r))
    elif i == 5:  # dark spots
        m.color = mix(m.color, rgb(70, 50, 32), dots(i, 9, (3, 9)) * 0.8)
    elif i == 6:  # a dark wet patch going bad
        patch = region(i, 0.22, soft=0.1, base=3)
        m.color = mix(m.color, rgb(84, 82, 72), patch * 0.6)
        m.smooth = np.maximum(m.smooth, 0.4 * patch)
    elif i == 7:  # something was dragged across
        cx = S * 0.5 + 40 * np.sin(Y / S * np.pi * 1.3)
        band = np.exp(-((X - cx) / 9) ** 2) * smoothstep(0.1, 0.2, T) * (1 - smoothstep(0.75, 0.9, T))
        m.color = mix(m.color, m.color * 0.7, band * (0.6 + 0.4 * fbm(i, base=16, octaves=2)))
    elif i == 8:  # bleached by something spilled
        spot = region(i, 0.12, soft=0.08, base=3)
        lum = m.color.mean(2, keepdims=True)
        m.color = mix(m.color, np.clip(lum * 1.3 + 0.04, 0, 1).repeat(3, 2) * rgb(255, 250, 236), spot * 0.55)
    elif i == 9:  # mould
        mould = region(i, 0.25, soft=0.12, base=3)
        m.color = mix(m.color, rgb(78, 92, 58), mould * np.clip(specks(i, 0.05, 1.1) + 0.35, 0, 1) * 0.65)
    return m


def carpet_edge(tone=CARPET):
    """A carpet block's side: the pile and its backing on top, the bare subfloor below."""
    rng = np.random.default_rng(500)
    fibres = blur(rng.random((S, S)).astype(np.float32), (5, 0.7))
    fibres = (fibres - fibres.min()) / (fibres.max() - fibres.min())
    pile = tone * ((0.7 + 0.55 * fibres) * (1 - 0.4 * smoothstep(8, 58, Y)))[..., None]
    floor = rgb(86, 82, 74) * (0.85 + 0.25 * fbm(501, base=6))[..., None]
    floor = mix(floor, rgb(52, 50, 46), specks(502, 0.004, 1.0))
    top = smoothstep(2, 6, Y + 4 * blur(rng.random((1, S)).astype(np.float32).repeat(S, 0), (0, 1.5)))
    color = np.where((Y < 60)[..., None], pile * top[..., None] + rgb(40, 38, 34) * (1 - top)[..., None],
                     np.where((Y < 68)[..., None], rgb(58, 52, 42), floor))
    return Material(color, np.where(Y < 60, 0.6 + 0.3 * fibres, 0.45), smooth=0.05, porosity=0.7, bump=20)


# ------------------------------------------------------------------------------------------ ceiling and lights

def ceiling_base(seed):
    """A mineral-fibre drop ceiling tile: pin holes and short fissures, a bevelled edge."""
    rng = np.random.default_rng(seed)

    def fissures(d, big):
        k = big / S
        for _ in range(1500):
            x, y = rng.uniform(0, S, 2)
            angle, points = rng.uniform(0, 2 * np.pi), [(x * k, y * k)]
            for _ in range(rng.integers(2, 5)):
                angle += rng.normal(0, 0.8)
                step = rng.uniform(1.5, 3.5)
                x, y = x + np.cos(angle) * step, y + np.sin(angle) * step
                points.append((x * k, y * k))
            d.line(points, fill=int(rng.uniform(110, 255)), width=int(rng.integers(1, 3)))
        for _ in range(3800):
            x, y, r = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0.3, 0.8)
            d.ellipse([(x - r) * k, (y - r) * k, (x + r) * k, (y + r) * k], fill=int(rng.uniform(100, 230)))

    pits = supersample(fissures, scale=2)
    color = rgb(218, 212, 194) * ((0.965 + 0.06 * fbm(seed + 5, base=4)) * (1 - 0.32 * pits))[..., None]
    # Four tiles a block, about the size of real 60 cm tiles, so the ceiling stops following the block grid. The bars
    # between them are painted; the model's own grid strips (0.75/16 of a block, 24 px) run along two block edges.
    bar = (np.abs(X - (S - 1) / 2) < 12) | (np.abs(Y - (S - 1) / 2) < 12)
    across = np.minimum(np.minimum(X, S - 1 - X), np.abs(X - (S - 1) / 2) - 12)
    down = np.minimum(np.minimum(Y, S - 1 - Y), np.abs(Y - (S - 1) / 2) - 12)
    d = np.maximum(np.minimum(across, down), 0)
    bevel = smoothstep(0, 10, d)
    color *= ((0.78 + 0.22 * bevel) * np.where(d < 2, 0.84, 1))[..., None]
    dust = (1 - smoothstep(0, 40, d)) * fbm(seed + 6, base=8)
    color = mix(color, color * rgb(205, 200, 186), dust * 0.5)
    metal = painted_metal(rgb(196, 194, 184), seed + 7).color
    color = np.where(bar[..., None], metal * (0.92 + 0.08 * smoothstep(0, 10, 12 - np.minimum(np.abs(X - (S - 1) / 2), np.abs(Y - (S - 1) / 2))))[..., None], color)
    height = np.where(bar, 0.75, 0.55 + 0.45 * bevel - pits * 0.25)
    m = Material(color, height, smooth=np.where(bar, 0.55, 0.06), porosity=0.8, bump=30)
    m.bar = bar
    return m


def ceiling_variant(i):
    m = ceiling_base(seed=600)
    if i:
        before = m.color.copy()
        tide_rings(m, big_stain(600 + i, 0.32))
        m.color = np.where(m.bar[..., None], mix(before, m.color, 0.35), m.color)  # the metal bars barely take the stain
    return m


def painted_metal(color, seed):
    rng = np.random.default_rng(seed)
    brushed = blur(rng.random((S, S)).astype(np.float32), (0.6, 12))
    brushed = (brushed - brushed.mean()) / brushed.std()
    c = color * ((1 + 0.02 * brushed) * (0.97 + 0.05 * fbm(seed, base=4)))[..., None]
    c = mix(c, c * 0.8, specks(seed + 1, 0.002))
    return Material(c, 0.5 + 0.01 * brushed, smooth=0.55, porosity=0.05, bump=10)


def light_panel(broken, tint=None):
    """A recessed fluorescent fitting behind a prismatic diffuser: two tubes, a few dead insects, dust."""
    rng = np.random.default_rng(700 + broken)
    tubes = np.clip(np.exp(-((T - 0.31) / 0.07) ** 2) + np.exp(-((T - 0.72) / 0.07) ** 2), 0, 1)
    px, py = (X % 8) / 8, (Y % 8) / 8
    prism = blur(1 - 2 * np.maximum(abs(px - 0.5), abs(py - 0.5)), 0.6)
    vignette = 0.86 + 0.14 * smoothstep(0, 40, edge_distance())
    if broken:
        level = (0.36 + 0.16 * tubes) * vignette * (0.94 + 0.08 * prism)
        color = rgb(208, 204, 192) * level[..., None]
        ends = (1 - smoothstep(30, 90, np.minimum(X, S - 1 - X))) * tubes  # burnt tube ends
        color *= (1 - 0.5 * ends)[..., None]
        glow = None
    else:
        level = (0.9 + 0.16 * tubes) * (0.93 + 0.07 * vignette) * (0.97 + 0.04 * prism)
        color = np.clip((rgb(255, 250, 232) if tint is None else tint) * level[..., None], 0, 1)
        glow = np.clip(level, 0, 1)
    m = Material(color, 0.5 + 0.02 * prism, smooth=0.75, porosity=0.0, glow=glow, bump=20)

    def insects(d, big):
        k = big / S
        for _ in range(6 if broken else 3):
            x, y, a = rng.uniform(60, S - 60), rng.uniform(60, S - 60), rng.uniform(0, np.pi)
            dx, dy = np.cos(a), np.sin(a)
            d.ellipse([(x - 4) * k, (y - 4) * k, (x + 4) * k, (y + 4) * k], fill=255)
            for side in (-1, 1):
                wx, wy = x + side * dy * 6, y - side * dx * 6
                d.ellipse([(wx - 5) * k, (wy - 3) * k, (wx + 5) * k, (wy + 3) * k], fill=150)

    m.color = mix(m.color, m.color * 0.35, blur(supersample(insects), 0.8) * 0.75)
    m.color = mix(m.color, m.color * 0.7, specks(701 + broken, 0.003 if broken else 0.0003, 1.0) * 0.6)
    if broken:
        def cracks(d, big):
            k = big / S
            for _ in range(2):
                x, y, angle = rng.uniform(100, S - 100), rng.uniform(100, S - 100), rng.uniform(0, 2 * np.pi)
                points = [(x * k, y * k)]
                for _ in range(14):
                    angle += rng.normal(0, 0.5)
                    x, y = x + np.cos(angle) * 12, y + np.sin(angle) * 12
                    points.append((x * k, y * k))
                d.line(points, fill=255, width=int(1.5 * k))

        m.color = mix(m.color, m.color * 0.4, supersample(cracks) * 0.8)
    return m


# ------------------------------------------------------------------------------------------ models

def troffer(panel, glow):
    """A light as a square fitting recessed into the whole block, instead of a flat glowing block. Lamps have no facing,
    so a half-block fitting striped every north-south strip and 2x2 panel; a whole one lines up whichever way they run.
    Same textures as before, plus the ceiling's; the grid strips match ceiling_tile_base."""
    level0 = "noclip_backrooms:block/level0/"

    def face(texture, uv=None, cull=None):
        f = {"texture": texture}
        if uv:
            f["uv"] = uv
        if cull:
            f["cullface"] = cull
        return f

    diffuser = {"from": [0.5, 1.5, 0.5], "to": [15.5, 1.5, 15.5], "faces": {"down": face("#panel", [0, 0, 16, 16])}}
    if glow:
        diffuser.update({"shade": False, "light_emission": 15})
    grid = [0, 7, 16, 8]
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": level0 + panel, "panel": level0 + panel, "housing": level0 + "light_housing",
                     "tile": level0 + "ceiling_tile", "grid": level0 + "ceiling_grid"},
        "elements": [
            # Above the ceiling line it is a ceiling block like its neighbours.
            {"from": [0, 0, 0], "to": [16, 16, 16],
             "faces": {side: face("#tile", cull=side) for side in ("north", "south", "west", "east", "up")}},
            # The fitting's frame round the opening, and its inside walls.
            {"from": [0, 0, 0], "to": [16, 1.5, 0.5], "faces": {"down": face("#housing", [0, 0, 16, 0.5]), "south": face("#housing", [0, 0, 16, 1.5])}},
            {"from": [0, 0, 15.5], "to": [16, 1.5, 16], "faces": {"down": face("#housing", [0, 0, 16, 0.5]), "north": face("#housing", [0, 0, 16, 1.5])}},
            {"from": [0, 0, 0.5], "to": [0.5, 1.5, 15.5], "faces": {"down": face("#housing", [0, 0, 0.5, 15]), "east": face("#housing", [0, 0, 15, 1.5])}},
            {"from": [15.5, 0, 0.5], "to": [16, 1.5, 15.5], "faces": {"down": face("#housing", [0, 0, 0.5, 15]), "west": face("#housing", [0, 0, 15, 1.5])}},
            diffuser,
            # The ceiling grid, as on every ceiling tile.
            {"from": [0, -0.3, 0], "to": [16, 0, 0.75], "faces": {s: face("#grid", grid) for s in ("down", "north", "south")}},
            {"from": [0, -0.3, 0.75], "to": [0.75, 0, 16], "faces": {s: face("#grid", grid) for s in ("down", "west", "east")}},
        ],
    }


def models():
    folder = PACK / "assets/noclip_backrooms/models/block/level0"
    folder.mkdir(parents=True, exist_ok=True)
    for name, panel, glow in (("fluorescent_light", "fluorescent_light", True), ("broken_fluorescent_light", "broken_fluorescent_light", False),
                              ("red_fluorescent_light", "red_fluorescent_light", True), ("exit_light", "exit_light", True)):
        (folder / f"{name}.json").write_text(json.dumps(troffer(panel, glow), indent=2) + "\n", encoding="utf8")


# ------------------------------------------------------------------------------------------ pack and preview

def pack_meta():
    PACK.mkdir(parents=True, exist_ok=True)
    meta = {"pack": {"description": {"translate": "pack.noclip_backrooms.realistic.description",
                                     "fallback": "Photo-based textures (Level 0, 0.1 & 2)"},
                     "min_format": 88, "max_format": 88}}
    (PACK / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf8")


def preview(names):
    side, gap, label = 256, 8, 18
    sheet = Image.new("RGB", (len(names) * (side + gap) + gap, 2 * (side + gap) + gap + label), (30, 30, 30))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        x = gap + i * (side + gap)
        sheet.paste(Image.open(OLD / f"{name}.png").convert("RGB").resize((side, side), Image.NEAREST), (x, gap))
        sheet.paste(Image.open(OUT / f"{name}.png").convert("RGB").resize((side, side), Image.LANCZOS), (x, side + 2 * gap))
        draw.text((x, 2 * side + 3 * gap), name, fill=(230, 230, 230))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW, optimize=True)


def main():
    global PRINT, PAPER, WATER, MOULD, DIRT
    PRINT, PAPER = wallpaper_print(), paper()
    clean = blur(load(source("Wallpaper002A", "1K-JPG")["Color"], gray=True), 1)
    WATER = smoothstep(0.03, 0.22, clean - blur(load(source("Wallpaper002B", "1K-JPG")["Color"], gray=True), 1))
    MOULD = smoothstep(0.05, 0.35, clean - blur(load(source("Wallpaper002C", "1K-JPG")["Color"], gray=True), 1))
    dirt = blur(load(source("Fabric028", "1K-JPG")["Color"], gray=True), 4)
    DIRT = 1 - (dirt - dirt.min()) / (dirt.max() - dirt.min())

    textures = {"wallpaper": wallpaper_variant(0)}
    textures.update({f"wallpaper_{i}": wallpaper_variant(i) for i in range(1, 10)})
    textures["wallpaper_baseboard"] = wallpaper_baseboard(False)
    textures["wallpaper_baseboard_1"] = wallpaper_baseboard(True)
    textures["board"] = board()
    for prefix, pal in (("pale_", PALE), ("red_", RED)):
        textures[prefix + "wallpaper"] = wallpaper_variant(0, pal)
        textures.update({f"{prefix}wallpaper_{i}": wallpaper_variant(i, pal) for i in range(1, 10)})
        textures[prefix + "wallpaper_baseboard"] = wallpaper_baseboard(False, pal)
        textures[prefix + "wallpaper_baseboard_1"] = wallpaper_baseboard(True, pal)
        textures[prefix + "board"] = board(pal[3])
    textures.update({"rough_wallpaper": wallpaper_variant(0, ROUGH), "rough_wallpaper_1": wallpaper_variant(1, ROUGH),
                     "rough_wallpaper_baseboard": wallpaper_baseboard(True, ROUGH), "rough_board": board(ROUGH[3])})
    textures.update({"peeling_red_wallpaper" + (f"_{i}" if i else ""): m for i, m in enumerate(peeling_variants())})
    textures["carpet"] = carpet_variant(0)
    textures.update({f"carpet_{i}": carpet_variant(i) for i in range(1, 10)})
    textures["carpet_edge"] = carpet_edge()
    textures.update({"dry_carpet": carpet_variant(0, DRY_CARPET, 1.0), "dry_carpet_1": carpet_variant(1, DRY_CARPET, 1.0),
                     "dry_carpet_edge": carpet_edge(DRY_CARPET)})
    # Deeper pile: the same carpet, shaded harder; thick_carpet_2 is the rare damp one.
    textures.update({"thick_carpet": carpet_variant(0, CARPET, 2.0), "thick_carpet_1": carpet_variant(1, CARPET, 2.0),
                     "thick_carpet_2": carpet_variant(2, CARPET, 2.0), "thick_carpet_edge": carpet_edge()})
    textures.update({"ceiling_tile": ceiling_variant(0), "ceiling_tile_1": ceiling_variant(1), "ceiling_tile_2": ceiling_variant(2)})
    textures["ceiling_grid"] = painted_metal(rgb(196, 194, 184), 800)
    textures["light_housing"] = painted_metal(rgb(212, 208, 192), 801)
    textures["fluorescent_light"] = light_panel(broken=False)
    textures["broken_fluorescent_light"] = light_panel(broken=True)
    textures["red_fluorescent_light"] = light_panel(broken=False, tint=rgb(236, 70, 56))
    textures["exit_light"] = light_panel(broken=False, tint=rgb(150, 255, 176))
    for name, m in textures.items():
        save(name, m)
    models()
    pack_meta()
    preview(["wallpaper", "wallpaper_1", "pale_wallpaper", "red_wallpaper", "rough_wallpaper", "peeling_red_wallpaper",
             "peeling_red_wallpaper_7", "board", "carpet", "carpet_2", "dry_carpet", "thick_carpet", "ceiling_tile",
             "ceiling_tile_1", "fluorescent_light", "red_fluorescent_light", "exit_light"])
    print(len(textures), "textures ->", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
