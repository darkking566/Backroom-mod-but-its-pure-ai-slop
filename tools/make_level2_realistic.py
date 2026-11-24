"""Realistic 512 px Level 2 textures for the built-in "realistic" resource pack.

Writes 512 px colour textures with labPBR _n / _s companions for every Level 2 texture into
src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level2/.
Uses CC0 photo materials from ambientCG (cached in tools/.cache/ambientcg/) for concrete,
brick, steel, rust, wood, frost, and water stains, plus photo-detailed high-resolution composites
for doors, machines, props, lights, and wall decals so every UV and detail stays intact.

Run: python tools/make_level2_realistic.py
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
OUT = PACK / "assets/noclip_backrooms/textures/block/level2"
OLD = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level2"
CACHE = TOOLS / ".cache/ambientcg"
PREVIEW = ROOT / "docs/look/level2_realistic.png"
S = 512
Y, X = np.mgrid[0:S, 0:S].astype(np.float32)
T = (Y + 0.5) / S


def _fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "noclip-backrooms-texture-tool"})
    return urllib.request.urlopen(req, timeout=300).read()


def source(asset, res="1K-JPG"):
    folder = CACHE / asset / res
    if not any(folder.glob("*_Color.jpg")):
        answer = json.loads(_fetch(f"https://ambientcg.com/api/v2/full_json?id={asset}&include=downloadData"))
        link = None
        for found in answer.get("foundAssets", []):
            for category in found.get("downloadFolders", {}).values():
                for dl in category.get("downloadFiletypeCategories", {}).get("zip", {}).get("downloads", []):
                    if dl.get("attribute") == res:
                        link = dl.get("downloadLink") or dl.get("fullDownloadPath")
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
    n = min(a.shape[0], a.shape[1])
    a = a[:n, :n]
    f = size / n
    rest = (0,) * (a.ndim - 2)
    if f < 1:
        a = ndimage.gaussian_filter(a, (0.42 / f, 0.42 / f) + rest, mode="wrap")
    out = ndimage.zoom(a, (f, f) + (1,) * (a.ndim - 2), order=3, mode="grid-wrap", grid_mode=True)
    return out[:size, :size]


def load(path, gray=False):
    image = Image.open(path).convert("L" if gray else "RGB")
    return np.clip(resize(np.asarray(image, dtype=np.float32) / 255), 0, 1)


def load_normal(path, strength=1.0):
    return (load(path)[..., :2] * 2 - 1) * strength


def blur(a, sigma):
    sigma = sigma if isinstance(sigma, tuple) else (sigma, sigma)
    return ndimage.gaussian_filter(a, sigma + (0,) * (a.ndim - 2), mode="wrap")


def fbm(seed, base=4, octaves=5, falloff=0.55):
    rng = np.random.default_rng(seed)
    out, amp, total, n = np.zeros((S, S), np.float32), 1.0, 0.0, base
    for _ in range(octaves):
        out += amp * ndimage.zoom(rng.random((n, n)), S / n, order=3, mode="grid-wrap", grid_mode=True)
        total += amp
        amp *= falloff
        n = min(n * 2, S)
    out /= total
    return (out - out.min()) / (out.max() - out.min() + 1e-6)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim == 2 and np.ndim(a) == 3:
        t = t[..., None]
    return a * (1 - t) + np.asarray(b, np.float32) * t


def rgb(r, g, b):
    return np.array([r, g, b], np.float32) / 255


class Material:
    def __init__(self, color, height=None, normal=None, smooth=0.2, f0=10, porosity=0.3, glow=None, bump=36.0, alpha=None):
        self.color = np.array(color, np.float32)
        self.height = np.full((S, S), 0.5, np.float32) if height is None else np.array(height, np.float32)
        self.normal = normal
        self.smooth = np.broadcast_to(np.float32(smooth), (S, S)).copy() if np.ndim(smooth) == 0 else np.array(smooth, np.float32)
        self.f0 = np.broadcast_to(np.float32(f0), (S, S)).copy() if np.ndim(f0) == 0 else np.array(f0, np.float32)
        self.porosity = np.broadcast_to(np.float32(porosity), (S, S)).copy() if np.ndim(porosity) == 0 else np.array(porosity, np.float32)
        self.glow = glow
        self.bump = bump
        self.alpha = alpha

    def copy(self):
        return copy.deepcopy(self)


def save(name, m):
    OUT.mkdir(parents=True, exist_ok=True)

    def write(suffix, a, mode):
        Image.fromarray(np.clip(np.rint(a * 255), 0, 255).astype(np.uint8), mode).save(OUT / f"{name}{suffix}.png", optimize=True)

    if m.alpha is not None:
        write("", np.dstack([np.clip(m.color, 0, 1), np.clip(m.alpha, 0, 1)]), "RGBA")
    else:
        write("", np.clip(m.color, 0, 1), "RGB")

    h = m.height
    x1 = -(np.roll(h, -1, 1) - np.roll(h, 1, 1)) * m.bump
    y1 = -(np.roll(h, -1, 0) - np.roll(h, 1, 0)) * m.bump
    z1 = np.ones_like(x1)
    if m.normal is not None:
        x2, y2 = m.normal[..., 0], m.normal[..., 1]
        z2 = np.sqrt(np.clip(1 - x2 * x2 - y2 * y2, 0.01, 1))
        length = np.sqrt(x1 * x1 + y1 * y1 + 1)
        x1, y1, z1 = x1 / length + x2, y1 / length + y2, (1 / length) * z2
    length = np.sqrt(x1 * x1 + y1 * y1 + z1 * z1)
    crevice = np.clip(1 - np.maximum(blur(h, 2) - h, 0) * 4, 0.55, 1)
    normal = np.dstack([(x1 / length + 1) / 2, (y1 / length + 1) / 2, crevice, 1 - (1 - h) * 0.14])
    step = np.array([4, 4, 16, 16], np.float32)
    write("_n", (np.floor(np.clip(blur(normal, 0.9), 0, 1) * 255 / step) * step + step // 2) / 255, "RGBA")

    glow = np.ones((S, S), np.float32) if m.glow is None else np.where(m.glow > 0.01, np.clip(m.glow, 0, 1), 1.0)
    porosity = np.clip(m.porosity, 0, 1) * 64 / 255
    write("_s", np.dstack([np.clip(m.smooth, 0, 1), np.clip(m.f0, 0, 255) / 255, porosity, glow]), "RGBA")


def regrade(photo_rgb, target_rgb, contrast=1.0):
    mean = photo_rgb.mean(axis=(0, 1), keepdims=True)
    luma = photo_rgb.mean(axis=2, keepdims=True)
    detail = (photo_rgb - luma) * 0.35 + (luma - luma.mean()) * contrast
    return np.clip(target_rgb[None, None, :] * (1 + detail * 1.15 / np.maximum(mean.mean(), 0.25)), 0, 1)


def load_pbr_substrate(asset, target_rgb=None, contrast=1.0, normal_strength=0.9, default_smooth=0.25, f0=10, porosity=0.35):
    maps = source(asset)
    col = load(maps["Color"])
    if target_rgb is not None:
        col = regrade(col, target_rgb, contrast)
    rough = load(maps["Roughness"], gray=True) if "Roughness" in maps else np.full((S, S), 1 - default_smooth, np.float32)
    smooth = np.clip(1 - rough, 0.04, 0.92)
    if default_smooth is not None:
        smooth = np.clip(smooth * 0.65 + default_smooth * 0.35, 0.04, 0.95)
    norm = load_normal(maps["NormalDX"], normal_strength) if "NormalDX" in maps else None
    disp = load(maps["Displacement"], gray=True) if "Displacement" in maps else col.mean(axis=2)
    return Material(col, height=disp, normal=norm, smooth=smooth, f0=f0, porosity=porosity, bump=28.0)


def apply_damp_patches(m, seed, coverage=0.45, puddle=False):
    n = fbm(seed, base=3, octaves=5)
    mask = smoothstep(1 - coverage - 0.12, 1 - coverage + 0.08, n)
    m.color = mix(m.color, m.color * 0.68, mask)
    m.smooth = np.maximum(m.smooth, mask * (0.94 if puddle else 0.78))
    m.porosity = np.maximum(m.porosity, mask * 0.85)
    if puddle:
        deep = smoothstep(0.62, 0.78, n)
        water_col = rgb(36, 48, 56)
        rim = smoothstep(0.57, 0.62, n) * (1 - deep)
        m.color = mix(m.color, water_col, deep * 0.75)
        m.color = mix(m.color, m.color * 1.25, rim * 0.45)
        m.smooth = np.maximum(m.smooth, deep * 0.97)
        m.f0 = np.maximum(m.f0, deep * 16)
        m.height = mix(m.height, 0.38, deep * 0.85)
        if m.normal is not None:
            m.normal = m.normal * (1 - deep[..., None] * 0.9)
    return m


def apply_frost(m, ice_mat, seed, cover=0.58):
    n = fbm(seed, base=3, octaves=5)
    edge = np.maximum(0.0, 1.0 - np.minimum(Y, S - 1 - Y) / 96.0) * 0.25
    frost = smoothstep(1 - cover - 0.12, 1 - cover + 0.12, n + edge)
    cold = m.color * rgb(224, 240, 255)
    m.color = mix(cold, ice_mat.color, frost * 0.78)
    m.height = mix(m.height, ice_mat.height, frost * 0.65)
    m.smooth = mix(m.smooth, np.maximum(ice_mat.smooth, 0.72), frost * 0.8)
    m.f0 = np.maximum(m.f0, frost * 16)
    if m.normal is not None and ice_mat.normal is not None:
        m.normal = m.normal * (1 - frost[..., None] * 0.7) + ice_mat.normal * (frost[..., None] * 0.7)
    return m


def build_authored_textures(WATER, MOULD):
    tex = {}

    # Concrete substrates
    wall_base = load_pbr_substrate("Concrete034", rgb(138, 138, 132), contrast=0.85, normal_strength=0.95, default_smooth=0.14, porosity=0.5)
    # Subtle poured-concrete form lines
    form_line = smoothstep(1.5, 0.0, np.abs((Y % 128) - 1)) * 0.35
    wall_base.color *= (1 - form_line[..., None] * 0.18)
    wall_base.height -= form_line * 0.08
    tex["utility_wall"] = wall_base

    stained = wall_base.copy()
    stain_mask = smoothstep(0.15, 0.65, np.roll(WATER, (90, 40), (0, 1)) * 0.85 + fbm(12, base=2) * 0.35)
    mould_mask = smoothstep(0.35, 0.75, MOULD * (0.4 + 0.6 * T))
    stained.color = mix(stained.color, stained.color * rgb(166, 152, 128), stain_mask * 0.75)
    stained.color = mix(stained.color, rgb(52, 58, 44), mould_mask * 0.75)
    stained.porosity = np.maximum(stained.porosity, stain_mask * 0.75)
    tex["stained_utility_wall"] = stained

    ice = load_pbr_substrate("Ice003", rgb(206, 228, 246), contrast=0.9, normal_strength=1.1, default_smooth=0.82, f0=16, porosity=0.05)
    tex["frosted_utility_wall"] = apply_frost(wall_base.copy(), ice, seed=501, cover=0.58)

    noclip_w = wall_base.copy()
    glitch = (np.sin(Y * 0.45) * 0.5 + 0.5) * smoothstep(0.55, 0.85, fbm(99, base=16, octaves=3))
    crack = smoothstep(5.0, 0.0, np.abs(X - S / 2 - np.sin(Y / 30.0) * 6)) * smoothstep(48.0, 96.0, Y) * smoothstep(S - 48.0, S - 96.0, Y)
    noclip_w.color = mix(noclip_w.color, noclip_w.color * rgb(196, 200, 226) * 0.85, glitch * 0.45)
    noclip_w.color = mix(noclip_w.color, rgb(28, 28, 36), crack * 0.75)
    noclip_w.height = np.clip(noclip_w.height - crack * 0.25, 0, 1)
    tex["noclip_wall"] = noclip_w

    floor_base = load_pbr_substrate("Concrete019", rgb(118, 120, 114), contrast=0.95, normal_strength=0.9, default_smooth=0.18, porosity=0.45)
    joint = np.maximum(smoothstep(2.0, 0.0, np.abs(X - S / 2)), smoothstep(2.0, 0.0, np.abs(Y - S / 2))) * 0.25
    floor_base.color *= (1 - joint[..., None] * 0.25)
    floor_base.height -= joint * 0.1
    tex["utility_floor"] = floor_base

    tex["damp_utility_floor"] = apply_damp_patches(floor_base.copy(), seed=21, coverage=0.52, puddle=False)
    tex["puddled_utility_floor"] = apply_damp_patches(floor_base.copy(), seed=22, coverage=0.68, puddle=True)
    tex["frosted_utility_floor"] = apply_frost(floor_base.copy(), ice, seed=502, cover=0.56)

    debris_floor = floor_base.copy()
    speck = smoothstep(0.66, 0.78, fbm(31, base=8, octaves=5))
    grit = fbm(32, base=16, octaves=4)
    debris_floor.color = mix(debris_floor.color, rgb(86, 78, 66) * (0.7 + 0.6 * grit[..., None]), speck * 0.75)
    debris_floor.height = np.maximum(debris_floor.height, speck * 0.7)
    tex["debris_utility_floor"] = debris_floor

    void_c = load_pbr_substrate("Concrete019", rgb(48, 50, 52), contrast=1.1, normal_strength=1.15, default_smooth=0.10, porosity=0.6)
    tex["void_concrete"] = void_c

    ceil = load_pbr_substrate("Concrete019", rgb(68, 70, 72), contrast=1.0, normal_strength=1.0, default_smooth=0.14, porosity=0.5)
    cable_shadow = np.clip(np.sin(X / 42.0 * np.pi) ** 12, 0, 1) * 0.45
    ceil.color = mix(ceil.color, rgb(32, 34, 38), cable_shadow)
    ceil.height += cable_shadow * 0.18
    tex["utility_ceiling"] = ceil

    # Brick walls
    muddy = load_pbr_substrate("Bricks075A", rgb(118, 86, 68), contrast=1.05, normal_strength=1.15, default_smooth=0.14, porosity=0.65)
    grime = smoothstep(0.3, 0.95, T * 0.8 + fbm(41, base=4) * 0.35)
    muddy.color = mix(muddy.color, rgb(64, 52, 42), grime * 0.45)
    tex["muddy_brick_wall"] = muddy

    black_b = load_pbr_substrate("Bricks075A", rgb(40, 40, 44), contrast=1.15, normal_strength=1.2, default_smooth=0.12, porosity=0.7)
    tex["black_brick_wall"] = black_b

    painted_b = load_pbr_substrate("Bricks075A", rgb(206, 206, 198), contrast=0.55, normal_strength=0.85, default_smooth=0.28, porosity=0.25)
    peel = smoothstep(0.72, 0.84, fbm(43, base=4))
    painted_b.color = mix(painted_b.color, rgb(144, 134, 122), peel * 0.6)
    tex["painted_brick_wall"] = painted_b

    # Metals, rust, wood
    steel_brushed = load_pbr_substrate("Metal049A", rgb(156, 162, 168), contrast=0.85, normal_strength=0.75, default_smooth=0.66, f0=234, porosity=0.04)
    steel_dark = load_pbr_substrate("Metal032", rgb(92, 96, 102), contrast=0.95, normal_strength=0.85, default_smooth=0.52, f0=228, porosity=0.06)
    rust = load_pbr_substrate("Rust004", rgb(124, 68, 42), contrast=1.05, normal_strength=1.1, default_smooth=0.18, f0=28, porosity=0.6)
    wood_oak = load_pbr_substrate("Wood051", rgb(146, 110, 70), contrast=0.95, normal_strength=0.9, default_smooth=0.24, porosity=0.45)

    # Pipes with longitudinal highlight & seam bands
    ridge = (np.sin(T * np.pi) * 0.28 - 0.14).astype(np.float32)
    seam = smoothstep(2.5, 0.0, np.abs(X - 18)) + smoothstep(2.5, 0.0, np.abs(X - S + 18))
    seam = np.clip(seam, 0, 1)

    def pipe_from(base_mat, tint=None, metal_f0=232, smooth_val=0.60):
        m = base_mat.copy()
        if tint is not None:
            m.color = regrade(m.color, tint, 0.85)
        brushed = (blur(fbm(91, base=32, octaves=3), (0.8, 14.0)) - 0.5) * 0.22
        m.color = np.clip(m.color * (1 + ridge[..., None] * 0.55 + brushed[..., None]) * (1 - seam[..., None] * 0.25), 0, 1)
        m.height = np.clip(m.height * 0.45 + 0.35 + ridge * 0.35 + seam * 0.15 + brushed * 0.25, 0, 1)
        m.smooth = np.full((S, S), smooth_val, np.float32) * (0.85 + 0.15 * m.height)
        m.f0 = np.full((S, S), metal_f0, np.float32)
        m.bump = 32.0
        return m

    tex["pipe_grey"] = pipe_from(steel_dark, rgb(98, 104, 110), 228, 0.54)
    tex["pipe_steel"] = pipe_from(steel_brushed, rgb(162, 168, 174), 236, 0.70)
    tex["pipe_steel_hot"] = pipe_from(steel_brushed, rgb(148, 128, 118), 230, 0.62)
    wet_p = pipe_from(steel_dark, rgb(76, 86, 94), 230, 0.86)
    tex["pipe_steel_wet"] = apply_damp_patches(wet_p, seed=61, coverage=0.62, puddle=False)
    tex["pipe_copper"] = pipe_from(steel_brushed, rgb(184, 112, 72), 238, 0.72)
    tex["pipe_green"] = pipe_from(steel_dark, rgb(84, 126, 96), 24, 0.44)
    tex["pipe_red"] = pipe_from(steel_dark, rgb(154, 44, 40), 24, 0.48)
    tex["pipe_frosted"] = apply_frost(tex["pipe_grey"].copy(), ice, seed=503, cover=0.62)

    burst = tex["pipe_grey"].copy()
    crack = smoothstep(10.0, 0.0, np.abs(Y - S / 2 - np.sin(X / 36.0) * 18)) * smoothstep(90.0, 120.0, X) * smoothstep(420.0, 390.0, X)
    corrode = smoothstep(0.45, 0.75, fbm(71, base=6) + crack * 0.6)
    burst.color = mix(burst.color, rust.color * rgb(164, 196, 118), corrode * 0.65)
    burst.color = mix(burst.color, rgb(22, 28, 20), crack * 0.92)
    burst.height = np.clip(burst.height - crack * 0.45, 0, 1)
    burst.f0 = np.where(corrode > 0.35, 32.0, burst.f0)
    tex["pipe_burst_gas"] = burst

    oh_leak = tex["pipe_grey"].copy()
    joint_band = np.maximum(smoothstep(24.0, 0.0, np.abs(X - 150)), smoothstep(24.0, 0.0, np.abs(X - 368)))
    oh_leak.color = mix(oh_leak.color, rust.color, joint_band * 0.65)
    oh_leak.smooth = np.maximum(oh_leak.smooth, smoothstep(0.48, 0.75, T + fbm(73, base=6) * 0.35) * 0.88)
    oh_leak.f0 = np.where(joint_band > 0.4, 36.0, oh_leak.f0)
    tex["pipe_overhead_leak"] = oh_leak

    wrap = load_pbr_substrate("Concrete034", rgb(194, 188, 172), contrast=0.75, normal_strength=0.9, default_smooth=0.10, porosity=0.7)
    spiral = smoothstep(3.0, 0.0, np.abs(((X + Y * 0.45) % 64) - 32))
    wrap.color *= (1 - spiral[..., None] * 0.22)
    wrap.height -= spiral * 0.14
    tex["pipe_wrap"] = wrap

    tex["bracket"] = steel_dark.copy()
    tex["toolbox_metal"] = steel_brushed.copy()
    tex["lumber"] = wood_oak.copy()
    tex["reel_wood"] = regrade(wood_oak.color, rgb(124, 92, 58), 0.95)
    tex["reel_wood"] = Material(tex["reel_wood"], wood_oak.height, wood_oak.normal, 0.22, 10, 0.45)
    tex["crate_side"] = wood_oak.copy()
    tex["crate_top"] = wood_oak.copy()

    drum = steel_dark.copy()
    drum.color = regrade(drum.color, rgb(52, 78, 96), 0.95)
    drum_rust = smoothstep(0.58, 0.80, fbm(81, base=4))
    drum.color = mix(drum.color, rust.color, drum_rust * 0.7)
    drum.f0 = np.where(drum_rust > 0.4, 28.0, 180.0)
    tex["drum_side"] = drum
    tex["drum_top"] = drum.copy()
    tex["scrap"] = rust.copy()

    return tex, steel_dark, steel_brushed, rust, wood_oak, floor_base


def composite_from_base(name, substrates):
    """Upsamples a detailed 128/64 px texture to 512 px and infuses real ambientCG substrate grain & labPBR maps."""
    steel_dark, steel_brushed, rust, wood_oak, floor_base = substrates
    src_img = Image.open(OLD / f"{name}.png")
    has_alpha = src_img.mode == "RGBA" and src_img.convert("RGBA").getchannel("A").getextrema()[0] < 255
    resample = Image.NEAREST if (src_img.width <= 64 and not has_alpha) else Image.LANCZOS
    rgba = np.asarray(src_img.convert("RGBA").resize((S, S), resample), dtype=np.float32) / 255
    base_rgb = rgba[..., :3]
    alpha = rgba[..., 3] if has_alpha else None

    # Choose appropriate photographic substrate micro-texture
    if any(k in name for k in ("wood", "plywood", "lumber", "crate", "reel_wood")):
        sub = wood_oak
        f0, smooth, bump = 10, 0.24, 24.0
    elif any(k in name for k in ("boiler", "scrap", "aged")):
        sub = rust
        f0, smooth, bump = 42, 0.28, 28.0
    elif any(k in name for k in ("drain_grate", "floor_cable_duct")):
        sub = floor_base
        f0, smooth, bump = 85, 0.42, 30.0
    elif any(k in name for k in ("steel", "metal", "elevator", "blade", "breaker", "pressure_gauge", "ceiling_vent", "machine", "cable_tray", "toolbox", "valve")):
        sub = steel_brushed if any(k in name for k in ("steel", "elevator", "pressure_gauge", "toolbox")) else steel_dark
        f0, smooth, bump = 165, 0.52, 26.0
    elif any(k in name for k in ("light_lens", "bulb", "lamp", "tunnel_window")):
        sub = steel_dark
        f0, smooth, bump = 22, 0.72, 16.0
    else:
        sub = steel_dark
        f0, smooth, bump = 18, 0.34, 24.0

    # Blend substrate photographic grain around midtones without washing out crisp painted signs/numbers
    grain = (sub.color.mean(axis=2) - sub.color.mean()) * 0.45
    photo_rgb = np.clip(base_rgb * (1 + grain[..., None]), 0, 1)

    luma = base_rgb.mean(axis=2)
    height = np.clip(luma * 0.55 + sub.height * 0.45, 0, 1)

    # Preserve existing _s emission channel if present
    glow = None
    s_path = OLD / f"{name}_s.png"
    if s_path.exists():
        s_img = np.asarray(Image.open(s_path).convert("RGBA").resize((S, S), Image.BILINEAR), dtype=np.float32) / 255
        if s_img[..., 3].min() < 0.999:
            glow = s_img[..., 3]
        f0 = np.clip(s_img[..., 1] * 255, 8, 240)
        smooth = np.clip(s_img[..., 0] * 0.7 + smooth * 0.3, 0.05, 0.95)
    elif name == "breaker_box":
        # Subtle status LED glow in breaker box upper-left
        led = ((photo_rgb[..., 0] > 0.55) & (photo_rgb[..., 1] < 0.3)) | ((photo_rgb[..., 1] > 0.5) & (photo_rgb[..., 0] < 0.35))
        if led.any():
            glow = np.where(led, 0.65, 1.0).astype(np.float32)

    return Material(photo_rgb, height=height, normal=sub.normal, smooth=smooth, f0=f0, porosity=0.25, glow=glow, bump=bump, alpha=alpha)


def pack_meta():
    PACK.mkdir(parents=True, exist_ok=True)
    meta = {
        "pack": {
            "description": {
                "translate": "pack.noclip_backrooms.realistic.description",
                "fallback": "Photo-based textures (Level 0 & Level 2)",
            },
            "min_format": 88,
            "max_format": 88,
        }
    }
    (PACK / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf8")


def preview(names):
    side, gap, label = 256, 8, 18
    sheet = Image.new("RGB", (len(names) * (side + gap) + gap, 2 * (side + gap) + gap + label), (28, 28, 30))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        x = gap + i * (side + gap)
        sheet.paste(Image.open(OLD / f"{name}.png").convert("RGB").resize((side, side), Image.NEAREST), (x, gap))
        sheet.paste(Image.open(OUT / f"{name}.png").convert("RGB").resize((side, side), Image.LANCZOS), (x, side + 2 * gap))
        draw.text((x, 2 * side + 3 * gap), name, fill=(230, 230, 230))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW, optimize=True)


def main():
    clean = blur(load(source("Wallpaper002A")["Color"], gray=True), 1)
    WATER = smoothstep(0.03, 0.22, clean - blur(load(source("Wallpaper002B")["Color"], gray=True), 1))
    MOULD = smoothstep(0.05, 0.35, clean - blur(load(source("Wallpaper002C")["Color"], gray=True), 1))

    tex, steel_dark, steel_brushed, rust, wood_oak, floor_base = build_authored_textures(WATER, MOULD)
    substrates = (steel_dark, steel_brushed, rust, wood_oak, floor_base)

    all_names = sorted(
        f.stem for f in OLD.glob("*.png") if not f.stem.endswith("_n") and not f.stem.endswith("_s")
    )
    for name in all_names:
        mat = tex[name] if name in tex else composite_from_base(name, substrates)
        save(name, mat)

    pack_meta()
    preview([
        "utility_wall", "stained_utility_wall", "frosted_utility_wall",
        "muddy_brick_wall", "painted_brick_wall", "utility_floor",
        "puddled_utility_floor", "pipe_steel", "pipe_burst_gas",
        "pipe_frosted", "breaker_box", "drain_grate",
    ])
    print(f"{len(all_names)} Level 2 512px textures (+ _n/_s) -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
