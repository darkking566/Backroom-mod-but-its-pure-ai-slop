"""Generates assets ONLY for Level 4 POI-specific blocks without touching any existing Level 4 textures or models.

    py tools/make_level4_pois.py

Follows docs/畫面規範/風格規範.md:
- 128x128 surface/cutout textures with labPBR _n / _s maps
- Transparent margins retain foreground RGB so realistic resizing produces crisp edges
"""

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
DATA = ROOT / "src/main/resources/data/noclip_backrooms"
TEXTURES = ASSETS / "textures/block/level4"
MODELS = ASSETS / "models/block/level4"
ITEM_MODELS = ASSETS / "models/item"
ITEMS_DEF = ASSETS / "items"
BLOCKSTATES = ASSETS / "blockstates"
LOOT = DATA / "loot_table/blocks"
NS = "noclip_backrooms:block/level4/"
SIZE = 128


def write_json(path: Path, obj: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def fbm_noise(seed: int, size: int = SIZE) -> np.ndarray:
    """Deterministic multi-octave normalized noise in [-1, 1] for organic surface wear."""
    rng = np.random.default_rng(seed)
    fine = rng.normal(0.0, 1.0, (size, size)).astype(np.float32)
    mid_img = Image.fromarray(np.clip((rng.normal(0.5, 0.28, (size // 4, size // 4))) * 255, 0, 255).astype("uint8"), "L")
    mid = np.asarray(mid_img.resize((size, size), Image.Resampling.BILINEAR).filter(ImageFilter.GaussianBlur(1.5)), dtype=np.float32) / 127.5 - 1.0
    coarse_img = Image.fromarray(np.clip((rng.normal(0.5, 0.30, (size // 8, size // 8))) * 255, 0, 255).astype("uint8"), "L")
    coarse = np.asarray(coarse_img.resize((size, size), Image.Resampling.BILINEAR).filter(ImageFilter.GaussianBlur(3.0)), dtype=np.float32) / 127.5 - 1.0
    combined = fine * 0.25 + mid * 0.45 + coarse * 0.30
    peak = max(1e-4, float(np.max(np.abs(combined))))
    return combined / peak


def height_to_normal(height: np.ndarray, strength: float = 3.2, ao: np.ndarray | None = None) -> Image.Image:
    """Bakes tangent-space labPBR _n.png from a [-1, 1] heightfield (B=AO, A=height)."""
    h = height.astype(np.float32)
    dx = np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)
    dy = np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)
    r = np.clip(np.rint(128.0 - dx * (strength * 28.0)), 0, 255)
    g = np.clip(np.rint(128.0 - dy * (strength * 28.0)), 0, 255)
    b = np.full_like(h, 255.0) if ao is None else np.clip(np.rint(ao), 140, 255)
    a = np.clip(np.rint(128.0 + h * 54.0), 20, 255)
    arr = np.stack([r, g, b, a], axis=-1).astype("uint8")
    return Image.fromarray(arr, "RGBA")


def blend_wear(rgba: Image.Image, noise: np.ndarray, amt: float = 12.0) -> Image.Image:
    """Applies subtle midtone organic wear/grain while keeping alpha channel verbatim."""
    arr = np.asarray(rgba.convert("RGBA"), dtype=np.float32)
    alpha = arr[..., 3:4]
    delta = (noise * amt)[..., None]
    arr[..., :3] = np.clip(arr[..., :3] + delta, 0, 255)
    arr[..., 3:4] = alpha
    return Image.fromarray(np.rint(arr).astype("uint8"), "RGBA")


def draw_meg_chalk_sign(slashed: bool):
    # Fill transparent background RGB with chalk blue so LANCZOS/mipmaps never bleed black
    bg = (46, 132, 212, 0)
    img = Image.new("RGBA", (SIZE, SIZE), bg)
    d = ImageDraw.Draw(img)

    blue = (54, 152, 236, 238)
    chalk = (224, 232, 240, 230)
    dark_blue = (30, 86, 152, 240)

    # Spray-painted M.E.G. arrow & faint vertical drips
    d.rectangle([20, 44, 78, 58], fill=blue)
    d.polygon([(74, 30), (108, 51), (74, 72)], fill=blue)
    d.line([(20, 44), (74, 44), (74, 30), (108, 51), (74, 72), (74, 58), (20, 58), (20, 44)], fill=dark_blue, width=2)
    for dx_pos, drop_len in [(32, 13), (58, 17), (76, 11)]:
        d.line([(dx_pos, 58), (dx_pos, 58 + drop_len)], fill=(44, 132, 210, 170), width=2)

    # Spray overspray speckles around stencil
    rng = np.random.default_rng(4201)
    for _ in range(95):
        sx = int(rng.integers(14, 114))
        sy = int(rng.integers(24, 108))
        alpha_sp = int(rng.integers(60, 155))
        d.point((sx, sy), fill=(62, 158, 238, alpha_sp) if sy < 76 else (218, 226, 234, alpha_sp))

    # Chalk M.E.G. stencil + safe emblem
    d.line([(20, 102), (20, 82), (28, 93), (36, 82), (36, 102)], fill=chalk, width=3)
    d.line([(44, 82), (44, 102)], fill=chalk, width=3)
    d.line([(44, 82), (56, 82)], fill=chalk, width=3)
    d.line([(44, 92), (54, 92)], fill=chalk, width=3)
    d.line([(44, 102), (56, 102)], fill=chalk, width=3)
    d.arc([62, 82, 78, 102], start=35, end=325, fill=chalk, width=3)
    d.line([(72, 93), (78, 93), (78, 100)], fill=chalk, width=3)
    d.ellipse([88, 82, 108, 102], outline=blue, width=3)

    noise = fbm_noise(4202 if not slashed else 4203)
    img = blend_wear(img, noise, amt=11.0)
    arr = np.asarray(img, dtype=np.float32)
    paint_mask = arr[..., 3] > 20
    height = noise * 0.14 + paint_mask.astype(np.float32) * 0.18

    if slashed:
        claw_mask_img = Image.new("L", (SIZE, SIZE), 0)
        cm_d = ImageDraw.Draw(claw_mask_img)
        cd = ImageDraw.Draw(img)
        for off in (-18, 0, 18):
            pts = [(16 + off, 18), (48 + off, 52), (76 + off, 80), (106 + off, 114)]
            cd.line(pts, fill=(24, 20, 24, 248), width=8)
            cd.line(pts, fill=(8, 6, 8, 255), width=4)
            cm_d.line(pts, fill=255, width=6)
        cm = np.asarray(claw_mask_img.filter(ImageFilter.GaussianBlur(1.2)), dtype=np.float32) / 255.0
        height = height - cm * 0.85

    n_img = height_to_normal(height, strength=2.6)
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    base_smooth = 18 if not slashed else 16
    s_arr[..., 0] = np.clip(np.rint(base_smooth + noise * 6.0), 6, 36)
    s_arr[..., 1] = 10
    s_arr[..., 2] = 65
    s_arr[..., 3] = 255
    return img, n_img, Image.fromarray(s_arr, "RGBA")


def draw_caution_tape():
    yellow = (232, 184, 28, 0)
    img = Image.new("RGBA", (SIZE, SIZE), yellow)
    d = ImageDraw.Draw(img)

    tape_yellow = (234, 186, 30, 246)
    tape_dark = (26, 26, 28, 250)
    tape_edge = (168, 128, 18, 246)

    for band_y0, band_y1 in [(34, 58), (76, 100)]:
        d.rectangle([0, band_y0, SIZE, band_y1], fill=tape_yellow)
        d.line([(0, band_y0), (SIZE, band_y0)], fill=tape_edge, width=2)
        d.line([(0, band_y1), (SIZE, band_y1)], fill=tape_edge, width=2)
        for sx in range(-32, SIZE + 32, 24):
            d.polygon([
                (sx, band_y1),
                (sx + 10, band_y1),
                (sx + 22, band_y0),
                (sx + 12, band_y0),
            ], fill=tape_dark)

    d.polygon([(0, 22), (0, 42), (SIZE, 96), (SIZE, 76)], fill=tape_yellow)
    for k in range(0, SIZE, 24):
        y_top = 22 + int(k * 54 / SIZE)
        d.polygon([(k, y_top), (k + 11, y_top + 4), (k + 4, y_top + 20), (k - 7, y_top + 16)], fill=tape_dark)

    noise = fbm_noise(4210)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    crinkle = np.sin(xx * 0.17 + yy * 0.23 + noise * 2.1) * 0.45 + noise * 0.25
    arr = np.asarray(img, dtype=np.float32)
    alpha = arr[..., 3:4]
    shade = (crinkle * 18.0)[..., None]
    arr[..., :3] = np.clip(arr[..., :3] + shade, 0, 255)
    arr[..., 3:4] = alpha
    img = Image.fromarray(np.rint(arr).astype("uint8"), "RGBA")

    height = np.where(arr[..., 3] > 20, crinkle * 0.55 + 0.2, 0.0).astype(np.float32)
    n_img = height_to_normal(height, strength=2.8)
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.clip(np.rint(154 + crinkle * 26.0), 115, 190)
    s_arr[..., 1] = 14
    s_arr[..., 2] = 0
    s_arr[..., 3] = 255
    return img, n_img, Image.fromarray(s_arr, "RGBA")


def draw_office_dark_mirror(mode: str = "plain"):
    # 128x128 opaque dark mirror glass: sleek brushed aluminum border, obsidian mirror gradient,
    # smudged dust micro-haze, diagonal specular streaks, and soft out-of-focus silhouette on bottom/top.
    img = Image.new("RGBA", (SIZE, SIZE), (18, 22, 28, 255))
    d = ImageDraw.Draw(img)

    for y in range(SIZE):
        t = y / float(SIZE - 1)
        base_r = int(14 + 10 * (1.0 - t))
        base_g = int(18 + 14 * (1.0 - t))
        base_b = int(26 + 18 * (1.0 - t))
        d.line([(0, y), (SIZE - 1, y)], fill=(base_r, base_g, base_b, 255))

    d.polygon([(14, 7), (42, 7), (7, 42), (7, 14)], fill=(42, 52, 66, 255))
    d.polygon([(52, 7), (68, 7), (7, 68), (7, 52)], fill=(34, 43, 56, 255))
    d.polygon([(SIZE - 8, 56), (SIZE - 8, 76), (56, SIZE - 8), (76, SIZE - 8)], fill=(36, 45, 58, 255))

    noise = fbm_noise(4220 + (1 if mode == "bottom" else 2 if mode == "top" else 0))
    arr = np.asarray(img, dtype=np.float32)
    arr[..., :3] = np.clip(arr[..., :3] + (noise * 4.5)[..., None], 0, 255)
    img = Image.fromarray(np.rint(arr).astype("uint8"), "RGBA")

    if mode in ("top", "bottom"):
        # Composite soft blurred reflection onto glass so it reads like an eerie delayed reflection
        sil_layer = img.copy()
        sd = ImageDraw.Draw(sil_layer)
        mist_color = (48, 59, 72, 255)
        silhouette = (7, 9, 13, 255)
        if mode == "top":
            sd.ellipse([34, 24, 94, 127], fill=mist_color)
            sd.ellipse([50, 32, 78, 62], fill=silhouette)
            sd.rectangle([58, 58, 70, 74], fill=silhouette)
            sd.polygon([(43, 76), (64, 67), (85, 76), (87, SIZE - 1), (41, SIZE - 1)], fill=silhouette)
            sil_layer = sil_layer.filter(ImageFilter.GaussianBlur(1.8))
            img = Image.blend(img, sil_layer, 0.88)
            ImageDraw.Draw(img).line([(6, SIZE - 1), (SIZE - 7, SIZE - 1)], fill=(26, 31, 39, 255), width=1)
        else:
            sd.ellipse([34, -12, 94, 118], fill=mist_color)
            sd.polygon([(41, 0), (87, 0), (82, 121), (46, 121)], fill=silhouette)
            sil_layer = sil_layer.filter(ImageFilter.GaussianBlur(1.8))
            img = Image.blend(img, sil_layer, 0.88)
            ImageDraw.Draw(img).line([(6, 0), (SIZE - 7, 0)], fill=(26, 31, 39, 255), width=1)

    d = ImageDraw.Draw(img)
    # Brushed dark anodized aluminum borders
    d.rectangle([0, 0, 5, SIZE - 1], fill=(22, 26, 32, 255))
    d.rectangle([0, 0, 3, SIZE - 1], fill=(34, 38, 45, 255))
    d.line([(5, 0), (5, SIZE - 1)], fill=(60, 68, 80, 255), width=1)
    d.line([(0, 0), (0, SIZE - 1)], fill=(20, 23, 29, 255), width=2)

    d.rectangle([SIZE - 6, 0, SIZE - 1, SIZE - 1], fill=(22, 26, 32, 255))
    d.rectangle([SIZE - 4, 0, SIZE - 1, SIZE - 1], fill=(34, 38, 45, 255))
    d.line([(SIZE - 6, 0), (SIZE - 6, SIZE - 1)], fill=(60, 68, 80, 255), width=1)
    d.line([(SIZE - 1, 0), (SIZE - 1, SIZE - 1)], fill=(20, 23, 29, 255), width=2)

    if mode != "bottom":
        d.rectangle([0, 0, SIZE - 1, 5], fill=(22, 26, 32, 255))
        d.rectangle([0, 0, SIZE - 1, 3], fill=(34, 38, 45, 255))
        d.line([(5, 5), (SIZE - 6, 5)], fill=(60, 68, 80, 255), width=1)
        d.line([(0, 0), (SIZE - 1, 0)], fill=(20, 23, 29, 255), width=2)
    if mode != "top":
        d.rectangle([0, SIZE - 6, SIZE - 1, SIZE - 1], fill=(22, 26, 32, 255))
        d.rectangle([0, SIZE - 4, SIZE - 1, SIZE - 1], fill=(34, 38, 45, 255))
        d.line([(5, SIZE - 6), (SIZE - 6, SIZE - 6)], fill=(60, 68, 80, 255), width=1)
        d.line([(0, SIZE - 1), (SIZE - 1, SIZE - 1)], fill=(20, 23, 29, 255), width=2)

    # Heightfield: raised brushed metal borders around recessed smooth mirror glass
    frame = np.zeros((SIZE, SIZE), dtype=bool)
    frame[:, :6] = True
    frame[:, -6:] = True
    if mode != "bottom":
        frame[:6, :] = True
    if mode != "top":
        frame[-6:, :] = True
    height = np.where(frame, 0.65 + noise * 0.12, -0.25 + noise * 0.02).astype(np.float32)
    ao = np.where(frame, 235.0, 255.0).astype(np.float32)
    n_img = height_to_normal(height, strength=3.0, ao=ao)

    # labPBR _s: ultra-gloss obsidian glass vs metallic aluminum bezel (F0=232), A=255 (no emission)
    smudge = np.clip(242.0 + noise * 22.0, 195, 250)
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.where(frame, np.clip(112 + noise * 14, 90, 138), smudge).astype("uint8")
    s_arr[..., 1] = np.where(frame, 232, 126)
    s_arr[..., 2] = 0
    s_arr[..., 3] = 255
    return img, n_img, Image.fromarray(s_arr, "RGBA")


def draw_corrupted_fountain_liquid():
    # 128x128 organic turbid gray-crimson algae/sludge + swirling veins + heightfield normal + subtle vein glow
    noise_c = fbm_noise(4310)
    noise_f = fbm_noise(4311)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    nx = (xx - SIZE * 0.5) / 18.0
    nz = (yy - SIZE * 0.5) / 18.0
    ring = np.sin(np.hypot(nx, nz) * 3.1 + np.sin(nx * 0.9) * 0.8 + noise_c * 1.4)
    swirl = np.sin(xx * 0.14 + np.cos(yy * 0.11) * 1.4 + noise_f * 1.1) * np.cos(yy * 0.13)
    vein = np.maximum(0.0, swirl * 0.65 + ring * 0.35 + noise_c * 0.18)
    hot = vein > 0.62

    rgb = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    r = np.where(hot, 148 + noise_f * 18, 56 + 38 * (0.5 + 0.5 * ring) + 18 * vein + noise_c * 12)
    g = np.where(hot, 54 + noise_f * 8, 20 + 10 * (0.5 + 0.5 * ring) + noise_c * 5)
    b = np.where(hot, 56 + noise_f * 8, 22 + 12 * (0.5 + 0.5 * ring) + noise_c * 5)
    rgb[..., 0] = np.clip(np.rint(r), 0, 255)
    rgb[..., 1] = np.clip(np.rint(g), 0, 255)
    rgb[..., 2] = np.clip(np.rint(b), 0, 255)
    rgb[..., 3] = 244

    height = np.clip(ring * 0.45 + vein * 0.55 + noise_f * 0.25, -1.0, 1.0)
    n_img = height_to_normal(height, strength=3.6)

    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.clip(np.rint(182 + vein * 48.0 + noise_c * 14.0), 145, 238)
    s_arr[..., 1] = np.where(hot, 78, 48)
    s_arr[..., 2] = 0
    s_arr[..., 3] = np.where(hot, 105, 255)
    return Image.fromarray(rgb, "RGBA"), n_img, Image.fromarray(s_arr, "RGBA")


def draw_executive_desk_wood():
    # 128x128 lacquered dark mahogany with organic wood grain + carved recessed drawer molding
    noise = fbm_noise(4410)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    grain = np.sin(yy * 0.25 + np.sin(xx * 0.08 + noise * 0.8) * 2.2) * 0.45 + np.sin(xx * 0.05 + yy * 0.14) * 0.35 + noise * 0.25
    arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    arr[..., 0] = np.clip(np.rint(58 + 16 * grain), 0, 255)
    arr[..., 1] = np.clip(np.rint(24 + 8 * grain), 0, 255)
    arr[..., 2] = np.clip(np.rint(17 + 6 * grain), 0, 255)
    arr[..., 3] = 255
    img = Image.fromarray(arr, "RGBA")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(36, 14, 10, 255), width=3)
    d.rectangle([10, 14, SIZE - 11, 56], outline=(38, 15, 11, 255), width=2)
    d.rectangle([10, 70, SIZE - 11, 114], outline=(38, 15, 11, 255), width=2)

    seam = Image.new("L", (SIZE, SIZE), 0)
    sd = ImageDraw.Draw(seam)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=180, width=3)
    sd.rectangle([10, 14, SIZE - 11, 56], outline=240, width=2)
    sd.rectangle([10, 70, SIZE - 11, 114], outline=240, width=2)
    seam_arr = np.asarray(seam, dtype=np.float32) / 255.0

    height = grain * 0.25 - seam_arr * 0.75
    ao = 255.0 - seam_arr * 70.0
    n_img = height_to_normal(height, strength=3.0, ao=ao)

    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.clip(np.rint(145 + grain * 18.0 - seam_arr * 65.0), 65, 175)
    s_arr[..., 1] = 18
    s_arr[..., 2] = 0
    s_arr[..., 3] = 255
    return img, n_img, Image.fromarray(s_arr, "RGBA")


def draw_executive_red_phone():
    # 128x128 glossy crimson Bakelite + metallic brass rotary dial + glowing amber/green status LEDs
    noise = fbm_noise(4420)
    img = Image.new("RGBA", (SIZE, SIZE), (156, 22, 26, 255))
    d = ImageDraw.Draw(img)
    for y in range(SIZE):
        shade = int(18 * math.cos(y / float(SIZE) * math.pi))
        d.line([(0, y), (SIZE - 1, y)], fill=(148 + shade, 20 + shade // 4, 24 + shade // 4, 255))
    img = blend_wear(img, noise, amt=6.0)
    d = ImageDraw.Draw(img)

    # Brass rotary dial & recessed finger holes
    d.ellipse([34, 38, 94, 98], fill=(198, 162, 68, 255), outline=(92, 68, 22, 255), width=3)
    d.ellipse([48, 52, 80, 84], fill=(162, 26, 30, 255), outline=(120, 92, 32, 255), width=2)
    dial_mask = Image.new("L", (SIZE, SIZE), 0)
    dm = ImageDraw.Draw(dial_mask)
    dm.ellipse([34, 38, 94, 98], fill=255)
    dm.ellipse([48, 52, 80, 84], fill=0)

    holes_mask = Image.new("L", (SIZE, SIZE), 0)
    hm = ImageDraw.Draw(holes_mask)
    for k in range(10):
        ang = k * (math.pi * 2 / 10)
        hx = int(64 + math.cos(ang) * 22)
        hy = int(68 + math.sin(ang) * 22)
        d.ellipse([hx - 4, hy - 4, hx + 4, hy + 4], fill=(28, 22, 22, 255))
        hm.ellipse([hx - 4, hy - 4, hx + 4, hy + 4], fill=255)

    # Lit status LEDs (amber + green)
    d.rectangle([16, 16, 28, 24], fill=(255, 196, 48, 255))
    d.rectangle([34, 16, 46, 24], fill=(74, 232, 92, 255))
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(98, 14, 16, 255), width=2)

    led_mask = Image.new("L", (SIZE, SIZE), 0)
    lm = ImageDraw.Draw(led_mask)
    lm.rectangle([16, 16, 28, 24], fill=255)
    lm.rectangle([34, 16, 46, 24], fill=255)

    dm_arr = np.asarray(dial_mask, dtype=bool)
    hm_arr = np.asarray(holes_mask, dtype=bool)
    lm_arr = np.asarray(led_mask, dtype=bool)
    height = noise * 0.05 + dm_arr.astype(np.float32) * 0.45 - hm_arr.astype(np.float32) * 0.75 + lm_arr.astype(np.float32) * 0.2
    ao = np.where(hm_arr, 175.0, 255.0).astype(np.float32)
    n_img = height_to_normal(height, strength=3.2, ao=ao)

    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.where(dm_arr & ~hm_arr, 188, np.where(hm_arr, 80, 206))
    s_arr[..., 1] = np.where(dm_arr & ~hm_arr, 236, 40)
    s_arr[..., 2] = 0
    s_arr[..., 3] = np.where(lm_arr, 210, 255)
    return img, n_img, Image.fromarray(s_arr, "RGBA")


def draw_office_elevator_frame() -> Image.Image:
    # 128x128 dark gunmetal/charcoal brushed architectural steel elevator casing
    img = Image.new("RGBA", (SIZE, SIZE), (54, 59, 66, 255))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            streak = math.sin(y * 0.85 + math.sin(x * 0.12) * 1.4) * 3.0 + math.sin(y * 2.1) * 1.5
            sheen = math.sin(x * 0.05 + y * 0.02) * 2.0
            delta = int(streak + sheen)
            px[x, y] = (
                max(0, min(255, 54 + delta)),
                max(0, min(255, 59 + delta)),
                max(0, min(255, 66 + delta)),
                255,
            )
    d = ImageDraw.Draw(img)
    # Crisp vertical panel seam grooves at x=2..4 and x=124..126
    d.rectangle([2, 0, 4, SIZE - 1], fill=(32, 36, 42, 255))
    d.line([(5, 0), (5, SIZE - 1)], fill=(72, 78, 86, 255), width=1)
    d.rectangle([SIZE - 5, 0, SIZE - 3, SIZE - 1], fill=(32, 36, 42, 255))
    d.line([(SIZE - 6, 0), (SIZE - 6, SIZE - 1)], fill=(72, 78, 86, 255), width=1)
    # Mid-height horizontal reveal groove at y=62..65
    d.rectangle([0, 62, SIZE - 1, 65], fill=(30, 34, 40, 255))
    d.line([(0, 66), (SIZE - 1, 66)], fill=(74, 80, 88, 255), width=1)
    # 4 subtle dark metallic rivets/fasteners near corners
    for rx, ry in [(12, 12), (SIZE - 13, 12), (12, SIZE - 13), (SIZE - 13, SIZE - 13)]:
        d.ellipse([rx - 2, ry - 2, rx + 2, ry + 2], fill=(38, 42, 48, 255), outline=(74, 80, 88, 255))
    return img


def save_elevator_frame_pbr(name: str, rgba: Image.Image):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    n_img = Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255))
    nd = ImageDraw.Draw(n_img)
    # Beveled panel seam normals
    nd.rectangle([2, 0, 4, SIZE - 1], fill=(104, 128, 250, 255))
    nd.line([(5, 0), (5, SIZE - 1)], fill=(152, 128, 250, 255), width=1)
    nd.rectangle([SIZE - 5, 0, SIZE - 3, SIZE - 1], fill=(152, 128, 250, 255))
    nd.line([(SIZE - 6, 0), (SIZE - 6, SIZE - 1)], fill=(104, 128, 250, 255), width=1)
    nd.rectangle([0, 62, SIZE - 1, 65], fill=(128, 102, 250, 255))
    nd.line([(0, 66), (SIZE - 1, 66)], fill=(128, 154, 250, 255), width=1)
    n_img.save(TEXTURES / f"{name}_n.png", optimize=True)
    # Brushed steel specular (smooth ~135, f0 ~195, porosity 0)
    s_img = Image.new("RGBA", (SIZE, SIZE), (135, 195, 0, 255))
    sd = ImageDraw.Draw(s_img)
    sd.rectangle([2, 0, 4, SIZE - 1], fill=(75, 140, 0, 255))
    sd.rectangle([SIZE - 5, 0, SIZE - 3, SIZE - 1], fill=(75, 140, 0, 255))
    sd.rectangle([0, 62, SIZE - 1, 65], fill=(75, 140, 0, 255))
    s_img.save(TEXTURES / f"{name}_s.png", optimize=True)


def write_fountain_model(name: str, top_y: int):
    faces_base = {f: {"uv": [0, 0, 16, 16], "texture": "#base"} for f in ("north", "south", "east", "west", "up", "down")}
    faces_liq = {f: {"uv": [0, 0, 16, 16], "texture": "#liq"} for f in ("north", "south", "east", "west", "up", "down")}
    write_json(MODELS / f"{name}.json", {
        "textures": {
            "liq": NS + "corrupted_fountain_liquid",
            "base": NS + "almond_fountain_basin",
            "particle": NS + "corrupted_fountain_liquid"
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 2, 16], "faces": faces_base},
            {"from": [0, 2, 0], "to": [16, top_y, 16], "faces": faces_liq}
        ]
    })


def write_item_files(name: str):
    write_json(ITEM_MODELS / f"{name}.json", {"parent": NS + name})
    write_json(ITEMS_DEF / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/{name}"}})


def write_loot_self(name: str):
    write_json(LOOT / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}]
        }]
    })


def box_faces(tex: str, sides_uv=None):
    uv = sides_uv or [0, 0, 16, 16]
    return {f: {"uv": uv, "texture": f"#{tex}"} for f in ("north", "south", "east", "west", "up", "down")}


def save_pbr_pair(name: str, rgba: Image.Image, n_img: Image.Image, s_img: Image.Image):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    n_img.save(TEXTURES / f"{name}_n.png", optimize=True)
    s_img.save(TEXTURES / f"{name}_s.png", optimize=True)


def make_bevel_normal(border: int = 4, strength: int = 24) -> Image.Image:
    n_img = Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255))
    nd = ImageDraw.Draw(n_img)
    nd.rectangle([0, 0, SIZE - 1, border], fill=(128, max(0, 128 - strength), 252, 255))
    nd.rectangle([0, SIZE - 1 - border, SIZE - 1, SIZE - 1], fill=(128, min(255, 128 + strength), 252, 255))
    nd.rectangle([0, 0, border, SIZE - 1], fill=(max(0, 128 - strength), 128, 252, 255))
    nd.rectangle([SIZE - 1 - border, 0, SIZE - 1, SIZE - 1], fill=(min(255, 128 + strength), 128, 252, 255))
    return n_img


def draw_meg_supply_crate():
    # Slate-blue polymer/steel M.E.G. tactical field crate with heightfield-baked ribs & hardware
    noise = fbm_noise(4510)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    in_ribs = (xx >= 24) & (xx <= 104) & (yy >= 32) & (yy <= 108) & (((yy.astype(int) // 8) % 2) == 0)
    rib_delta = np.where(in_ribs, 6.0, 0.0)
    wear = noise * 6.5
    edge_dirt = np.maximum(0.0, (yy - 102.0) / 26.0) * 10.0

    arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    arr[..., 0] = np.clip(np.rint(34 + rib_delta + wear - edge_dirt), 0, 255)
    arr[..., 1] = np.clip(np.rint(54 + rib_delta + wear - edge_dirt), 0, 255)
    arr[..., 2] = np.clip(np.rint(82 + rib_delta + wear - edge_dirt * 1.1), 0, 255)
    arr[..., 3] = 255
    side = Image.fromarray(arr, "RGBA")
    sd = ImageDraw.Draw(side)
    for bx, by in [(0, 0), (SIZE - 20, 0), (0, SIZE - 20), (SIZE - 20, SIZE - 20)]:
        sd.rectangle([bx, by, bx + 19, by + 19], fill=(24, 28, 34, 255))
    sd.rectangle([0, 0, SIZE - 1, 14], fill=(28, 34, 42, 255))
    sd.rectangle([0, SIZE - 12, SIZE - 1, SIZE - 1], fill=(24, 28, 34, 255))
    sd.rectangle([20, 16, SIZE - 21, 22], fill=(216, 174, 42, 255))
    for lx in (24, 92):
        sd.rectangle([lx, 12, lx + 12, 34], fill=(138, 148, 158, 255), outline=(44, 48, 54, 255), width=2)
    st = (224, 234, 244, 255)
    sd.line([(40, 86), (40, 62), (48, 74), (56, 62), (56, 86)], fill=st, width=3)
    sd.line([(64, 62), (64, 86)], fill=st, width=3)
    sd.line([(64, 62), (76, 62)], fill=st, width=3)
    sd.line([(64, 74), (74, 74)], fill=st, width=3)
    sd.line([(64, 86), (76, 86)], fill=st, width=3)
    sd.arc([82, 62, 96, 86], start=35, end=325, fill=st, width=3)
    sd.line([(90, 74), (96, 74), (96, 83)], fill=st, width=3)

    latch_mask = ((yy >= 12) & (yy <= 34)) & (((xx >= 24) & (xx <= 36)) | ((xx >= 92) & (xx <= 104)))
    bumper_mask = ((xx < 20) | (xx >= SIZE - 20)) & ((yy < 20) | (yy >= SIZE - 20)) | (yy < 15) | (yy >= SIZE - 12)
    h_side = noise * 0.12 + np.where(in_ribs, 0.25, 0.0) + np.where(bumper_mask, 0.35, 0.0) + np.where(latch_mask, 0.65, 0.0)
    ao_side = 255.0 - np.where(yy == 15, 55.0, 0.0)
    n_side = height_to_normal(h_side, strength=3.0, ao=ao_side)
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.where(latch_mask, 185, np.clip(np.rint(58 + noise * 14.0), 36, 84))
    s_arr[..., 1] = np.where(latch_mask, 232, 24)
    s_arr[..., 2] = np.where(latch_mask, 0, 12)
    s_arr[..., 3] = 255
    s_side = Image.fromarray(s_arr, "RGBA")

    top_ribs = (xx >= 20) & (xx <= 108) & (yy >= 18) & (yy <= 110) & (((xx.astype(int) // 12) % 2) == 0)
    top_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    top_arr[..., 0] = np.clip(np.rint(38 + np.where(top_ribs, 7.0, 0.0) + wear), 0, 255)
    top_arr[..., 1] = np.clip(np.rint(60 + np.where(top_ribs, 7.0, 0.0) + wear), 0, 255)
    top_arr[..., 2] = np.clip(np.rint(92 + np.where(top_ribs, 7.0, 0.0) + wear), 0, 255)
    top_arr[..., 3] = 255
    top = Image.fromarray(top_arr, "RGBA")
    td = ImageDraw.Draw(top)
    td.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(24, 28, 34, 255), width=8)
    for bx, by in [(0, 0), (SIZE - 22, 0), (0, SIZE - 22), (SIZE - 22, SIZE - 22)]:
        td.rectangle([bx, by, bx + 21, by + 21], fill=(24, 28, 34, 255))
    td.polygon([(64, 42), (86, 64), (64, 86), (42, 64)], fill=(28, 44, 68, 255), outline=(216, 174, 42, 255))

    top_pad = ((xx < 22) | (xx >= SIZE - 22)) & ((yy < 22) | (yy >= SIZE - 22))
    h_top = noise * 0.10 + np.where(top_ribs, 0.30, 0.0) + np.where(top_pad, 0.40, 0.0)
    n_top = height_to_normal(h_top, strength=3.0)
    s_top_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_top_arr[..., 0] = np.clip(np.rint(62 + noise * 14.0), 40, 88)
    s_top_arr[..., 1] = 24
    s_top_arr[..., 2] = 10
    s_top_arr[..., 3] = 255
    return (side, n_side, s_side), (top, n_top, Image.fromarray(s_top_arr, "RGBA"))


def draw_industrial_toolbox():
    # Red enameled steel cantilever toolbox with scuff wear, chrome latches & pressed lid ribs
    noise = fbm_noise(4520)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    scuff = noise * 11.0
    arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    arr[..., 0] = np.clip(np.rint(154 + scuff), 0, 255)
    arr[..., 1] = np.clip(np.rint(28 + scuff * 0.35), 0, 255)
    arr[..., 2] = np.clip(np.rint(28 + scuff * 0.35), 0, 255)
    arr[..., 3] = 255
    side = Image.fromarray(arr, "RGBA")
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(82, 18, 18, 255), width=4)
    sd.rectangle([6, 44, SIZE - 7, 52], fill=(192, 198, 206, 255), outline=(58, 62, 68, 255), width=1)
    sd.rectangle([50, 32, 78, 66], fill=(176, 184, 192, 255), outline=(48, 52, 58, 255), width=2)
    sd.rectangle([58, 42, 70, 56], fill=(74, 78, 84, 255))
    for bx, by in [(0, 0), (SIZE - 16, 0), (0, SIZE - 16), (SIZE - 16, SIZE - 16)]:
        sd.rectangle([bx, by, bx + 15, by + 15], fill=(42, 46, 52, 255))

    chrome_mask = ((xx >= 6) & (xx <= SIZE - 7) & (yy >= 44) & (yy <= 52)) | ((xx >= 50) & (xx <= 78) & (yy >= 32) & (yy <= 66))
    guard_mask = ((xx < 16) | (xx >= SIZE - 16)) & ((yy < 16) | (yy >= SIZE - 16))
    h_side = noise * 0.14 + np.where(chrome_mask, 0.55, 0.0) + np.where(guard_mask, 0.25, 0.0)
    ao_side = 255.0 - np.where((yy == 43) | (yy == 53), 50.0, 0.0)
    n_side = height_to_normal(h_side, strength=3.0, ao=ao_side)
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.where(chrome_mask, 205, np.clip(np.rint(145 + noise * 22.0), 105, 180))
    s_arr[..., 1] = np.where(chrome_mask, 240, 44)
    s_arr[..., 2] = 0
    s_arr[..., 3] = 255
    s_side = Image.fromarray(s_arr, "RGBA")

    top_ribs = (yy >= 28) & (yy <= 100) & (((yy.astype(int) // 16) % 2) == 0)
    tarr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    tarr[..., 0] = np.clip(np.rint(160 + np.where(top_ribs, 8.0, 0.0) + scuff * 0.8), 0, 255)
    tarr[..., 1] = np.clip(np.rint(32 + np.where(top_ribs, 4.0, 0.0) + scuff * 0.3), 0, 255)
    tarr[..., 2] = np.clip(np.rint(32 + np.where(top_ribs, 4.0, 0.0) + scuff * 0.3), 0, 255)
    tarr[..., 3] = 255
    top = Image.fromarray(tarr, "RGBA")
    td = ImageDraw.Draw(top)
    td.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(82, 18, 18, 255), width=5)
    td.rectangle([38, 52, 50, 76], fill=(176, 184, 192, 255), outline=(48, 52, 58, 255), width=2)
    td.rectangle([78, 52, 90, 76], fill=(176, 184, 192, 255), outline=(48, 52, 58, 255), width=2)

    mount_mask = (yy >= 52) & (yy <= 76) & (((xx >= 38) & (xx <= 50)) | ((xx >= 78) & (xx <= 90)))
    h_top = noise * 0.12 + np.where(top_ribs, 0.25, 0.0) + np.where(mount_mask, 0.55, 0.0)
    n_top = height_to_normal(h_top, strength=2.8)
    stop_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    stop_arr[..., 0] = np.where(mount_mask, 200, np.clip(np.rint(150 + noise * 18.0), 115, 182))
    stop_arr[..., 1] = np.where(mount_mask, 240, 44)
    stop_arr[..., 2] = 0
    stop_arr[..., 3] = 255
    return (side, n_side, s_side), (top, n_top, Image.fromarray(stop_arr, "RGBA"))


def draw_office_steel_safe():
    # Heavy matte gunmetal steel office safe with brushed grain, recessed door seam, chrome dial & glowing keypad LED
    noise = fbm_noise(4530)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    brushed = np.sin(yy * 0.7 + np.sin(xx * 0.08) * 1.1) * 2.5 + noise * 5.0
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.clip(np.rint(48 + brushed), 0, 255)
    s_arr[..., 1] = np.clip(np.rint(52 + brushed), 0, 255)
    s_arr[..., 2] = np.clip(np.rint(58 + brushed), 0, 255)
    s_arr[..., 3] = 255
    side = Image.fromarray(s_arr, "RGBA")
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(28, 31, 36, 255), width=5)
    bolt_mask = np.zeros((SIZE, SIZE), dtype=bool)
    for rx, ry in [(12, 12), (SIZE - 13, 12), (12, SIZE - 13), (SIZE - 13, SIZE - 13)]:
        sd.ellipse([rx - 3, ry - 3, rx + 3, ry + 3], fill=(92, 98, 106, 255), outline=(24, 26, 30, 255))
        bolt_mask |= ((xx - rx) ** 2 + (yy - ry) ** 2) <= 9.0

    h_side = noise * 0.10 + np.where(bolt_mask, 0.45, 0.0)
    n_side = height_to_normal(h_side, strength=2.8)
    spec_side = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    spec_side[..., 0] = np.where(bolt_mask, 185, np.clip(np.rint(112 + noise * 14.0), 90, 140))
    spec_side[..., 1] = np.where(bolt_mask, 232, 175)
    spec_side[..., 2] = 0
    spec_side[..., 3] = 255

    front = side.copy()
    fd = ImageDraw.Draw(front)
    fd.rectangle([10, 10, SIZE - 11, SIZE - 11], outline=(20, 22, 26, 255), width=3)
    fd.rectangle([14, 14, SIZE - 15, SIZE - 15], outline=(70, 76, 84, 255), width=1)
    for hy in (28, 82):
        fd.rectangle([6, hy, 14, hy + 18], fill=(116, 122, 130, 255), outline=(24, 26, 30, 255), width=1)
    fd.ellipse([44, 34, 84, 74], fill=(168, 174, 184, 255), outline=(30, 34, 38, 255), width=3)
    fd.ellipse([54, 44, 74, 64], fill=(42, 46, 52, 255), outline=(120, 126, 134, 255), width=2)
    for k in range(12):
        ang = k * (math.pi * 2 / 12)
        x0, y0 = int(64 + math.cos(ang) * 14), int(54 + math.sin(ang) * 14)
        x1, y1 = int(64 + math.cos(ang) * 19), int(54 + math.sin(ang) * 19)
        fd.line([(x0, y0), (x1, y1)], fill=(28, 30, 34, 255), width=1)
    fd.rectangle([86, 62, 106, 86], fill=(138, 144, 154, 255), outline=(26, 28, 32, 255), width=2)
    fd.rectangle([88, 34, 98, 40], fill=(74, 224, 110, 255))

    seam_img = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(seam_img).rectangle([10, 10, SIZE - 11, SIZE - 11], outline=255, width=3)
    seam_m = np.asarray(seam_img, dtype=bool)
    dial_dist = (xx - 64.0) ** 2 + (yy - 54.0) ** 2
    dial_m = dial_dist <= 20.0 ** 2
    hinge_m = (xx >= 6) & (xx <= 14) & (((yy >= 28) & (yy <= 46)) | ((yy >= 82) & (yy <= 100)))
    handle_m = (xx >= 86) & (xx <= 106) & (yy >= 62) & (yy <= 86)
    led_m = (xx >= 88) & (xx <= 98) & (yy >= 34) & (yy <= 40)
    hw_m = dial_m | hinge_m | handle_m

    h_front = noise * 0.10 - np.where(seam_m, 0.70, 0.0) + np.where(hw_m, 0.60, 0.0) + np.where(dial_dist <= 10.0 ** 2, -0.25, 0.0)
    ao_front = np.where(seam_m, 165.0, 255.0).astype(np.float32)
    n_front = height_to_normal(h_front, strength=3.4, ao=ao_front)

    sf_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    sf_arr[..., 0] = np.where(hw_m, 204, np.where(led_m, 215, np.clip(np.rint(118 + noise * 14.0), 92, 145)))
    sf_arr[..., 1] = np.where(hw_m, 238, np.where(led_m, 30, 180))
    sf_arr[..., 2] = 0
    sf_arr[..., 3] = np.where(led_m, 205, 255)
    return (front, n_front, Image.fromarray(sf_arr, "RGBA")), (side, n_side, Image.fromarray(spec_side, "RGBA"))


def draw_executive_broadcast_screen():
    # Glowing emerald CRT/projection broadcast monitor wall + brushed gunmetal vented housing
    noise_s = fbm_noise(4540)
    noise_f = fbm_noise(4541)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    side_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    br = np.sin(yy * 0.6 + np.sin(xx * 0.1)) * 2.5 + noise_s * 4.5
    side_arr[..., 0] = np.clip(np.rint(34 + br), 0, 255)
    side_arr[..., 1] = np.clip(np.rint(38 + br), 0, 255)
    side_arr[..., 2] = np.clip(np.rint(44 + br), 0, 255)
    side_arr[..., 3] = 255
    side = Image.fromarray(side_arr, "RGBA")
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(20, 22, 26, 255), width=4)
    vent_m = np.zeros((SIZE, SIZE), dtype=bool)
    for vy in range(28, 100, 10):
        sd.rectangle([22, vy, SIZE - 23, vy + 4], fill=(16, 18, 22, 255))
        vent_m |= (xx >= 22) & (xx <= SIZE - 23) & (yy >= vy) & (yy <= vy + 4)

    n_side = height_to_normal(noise_s * 0.12 - np.where(vent_m, 0.70, 0.0), strength=3.0, ao=np.where(vent_m, 165.0, 255.0))
    ss_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    ss_arr[..., 0] = np.where(vent_m, 45, np.clip(np.rint(112 + noise_s * 16.0), 88, 138))
    ss_arr[..., 1] = np.where(vent_m, 60, 225)
    ss_arr[..., 2] = 0
    ss_arr[..., 3] = 255
    s_side = Image.fromarray(ss_arr, "RGBA")

    front = Image.new("RGBA", (SIZE, SIZE), (24, 27, 32, 255))
    fd = ImageDraw.Draw(front)
    fd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(42, 46, 54, 255), width=3)
    fd.rectangle([7, 7, SIZE - 8, SIZE - 8], fill=(12, 34, 26, 255), outline=(62, 68, 76, 255), width=2)
    fpx = front.load()
    for y in range(10, SIZE - 10):
        scan = -6 if (y % 4 == 0) else 4
        for x in range(10, SIZE - 10):
            glow = int(10 * math.exp(-((x - 64) ** 2 + (y - 64) ** 2) / 2800.0))
            dust = int(noise_f[y, x] * 4.0)
            fpx[x, y] = (10, max(0, min(255, 40 + scan + glow + dust)), max(0, min(255, 28 + scan // 2 + glow + dust)), 255)
    for gx in range(20, SIZE - 12, 18):
        fd.line([(gx, 10), (gx, SIZE - 11)], fill=(18, 62, 46, 255), width=1)
    for gy in range(20, SIZE - 12, 18):
        fd.line([(10, gy), (SIZE - 11, gy)], fill=(18, 62, 46, 255), width=1)

    glow_img = Image.new("L", (SIZE, SIZE), 0)
    gd = ImageDraw.Draw(glow_img)
    for k, h_bar in enumerate((38, 62, 50, 74)):
        bx0 = 16 + k * 7
        fd.rectangle([bx0, 106 - h_bar, bx0 + 4, 106], fill=(56, 224, 142, 255))
        gd.rectangle([bx0, 106 - h_bar, bx0 + 4, 106], fill=195)
    pts = []
    for x in range(44, SIZE - 14, 2):
        amp = 22.0 * math.sin((x - 44) * 0.19) * (0.65 + 0.35 * math.cos((x - 44) * 0.07))
        pts.append((x, int(64 + amp)))
    fd.line(pts, fill=(84, 255, 168, 255), width=3)
    gd.line(pts, fill=200, width=3)
    pts2 = [(px_x, int(64 + (py_y - 64) * 0.45)) for px_x, py_y in pts]
    fd.line(pts2, fill=(238, 198, 72, 255), width=1)
    gd.line(pts2, fill=185, width=1)

    live_img = Image.new("L", (SIZE, SIZE), 0)
    fd.ellipse([102, 15, 112, 25], fill=(236, 48, 52, 255), outline=(120, 18, 20, 255), width=1)
    ImageDraw.Draw(live_img).ellipse([102, 15, 112, 25], fill=255)

    inside_screen = (xx >= 9) & (xx <= SIZE - 10) & (yy >= 9) & (yy <= SIZE - 10)
    bezel = ~inside_screen
    glow_arr = np.asarray(glow_img, dtype=np.uint8)
    live_arr = np.asarray(live_img, dtype=np.uint8)

    scan_relief = np.where((yy.astype(int) % 4) == 0, -0.08, 0.08)
    h_front = np.where(bezel, 0.55 + noise_f * 0.10, -0.35 + scan_relief + noise_f * 0.03).astype(np.float32)
    n_front = height_to_normal(h_front, strength=3.0, ao=np.where(bezel, 238.0, 255.0))

    sf_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    sf_arr[..., 0] = np.where(bezel, np.clip(np.rint(105 + noise_f * 14.0), 82, 132), 228)
    sf_arr[..., 1] = np.where(bezel, 230, 75)
    sf_arr[..., 2] = 0
    # Strict labPBR emissive mask: A=255 bezel off, A=55 faint phosphor screen, A=195..220 waveform & LIVE LED
    emissive = np.where(live_arr > 0, 220, np.where(glow_arr > 0, glow_arr, 55))
    sf_arr[..., 3] = np.where(bezel, 255, emissive).astype("uint8")
    return (front, n_front, Image.fromarray(sf_arr, "RGBA")), (side, n_side, s_side)


def draw_paint_bucket():
    # Commercial 5-gallon steel/HDPE pail with yellow caution band and glossy black paint drips
    noise = fbm_noise(4610)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    rib_m = (yy >= 4) & (yy <= 20) & (((yy.astype(int) // 6) % 2) == 0)
    label_m = (yy >= 54) & (yy <= 88)
    base = np.where(label_m, 0.0, np.where(rib_m, 14.0, 0.0)) + noise * 7.0
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.clip(np.rint(np.where(label_m, 214, 206) + base), 0, 255)
    s_arr[..., 1] = np.clip(np.rint(np.where(label_m, 174, 204) + base), 0, 255)
    s_arr[..., 2] = np.clip(np.rint(np.where(label_m, 42, 198) + base), 0, 255)
    s_arr[..., 3] = 255
    side = Image.fromarray(s_arr, "RGBA")
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, 8], fill=(166, 166, 162, 255))
    sd.rectangle([0, 52, SIZE - 1, 56], fill=(32, 32, 34, 255))
    sd.rectangle([0, 86, SIZE - 1, 90], fill=(32, 32, 34, 255))
    sd.rectangle([0, SIZE - 10, SIZE - 1, SIZE - 1], fill=(128, 128, 124, 255))

    drip_img = Image.new("L", (SIZE, SIZE), 0)
    dd = ImageDraw.Draw(drip_img)
    sd.rectangle([0, 0, SIZE - 1, 14], fill=(16, 17, 20, 255))
    dd.rectangle([0, 0, SIZE - 1, 14], fill=255)
    for dx0, dw, dlen in [(10, 8, 46), (26, 6, 32), (44, 10, 68), (72, 7, 40), (92, 9, 58), (112, 6, 36)]:
        sd.rectangle([dx0, 12, dx0 + dw, dlen], fill=(16, 17, 20, 255))
        sd.ellipse([dx0, dlen - dw // 2, dx0 + dw, dlen + dw // 2 + 2], fill=(16, 17, 20, 255))
        dd.rectangle([dx0, 12, dx0 + dw, dlen], fill=255)
        dd.ellipse([dx0, dlen - dw // 2, dx0 + dw, dlen + dw // 2 + 2], fill=255)
    drip_m = np.asarray(drip_img, dtype=bool)
    h_side = noise * 0.12 + np.where(rib_m, 0.35, 0.0) + np.where(drip_m, 0.25, 0.0)
    n_side = height_to_normal(h_side, strength=2.8)
    spec_side = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    spec_side[..., 0] = np.where(drip_m, 224, np.clip(np.rint(118 + noise * 16.0), 86, 146))
    spec_side[..., 1] = 0
    spec_side[..., 2] = 0
    spec_side[..., 3] = 255

    # Glossy black liquid paint top
    tnoise = fbm_noise(4611)
    t_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    swirl = np.sin(np.hypot(xx - 64.0, yy - 64.0) * 0.25) * 3.0 + tnoise * 3.5
    t_arr[..., 0] = np.clip(np.rint(14 + swirl), 6, 28)
    t_arr[..., 1] = np.clip(np.rint(15 + swirl), 7, 30)
    t_arr[..., 2] = np.clip(np.rint(18 + swirl), 9, 34)
    t_arr[..., 3] = 255
    top = Image.fromarray(t_arr, "RGBA")
    n_top = height_to_normal(tnoise * 0.05, strength=1.2)
    stop = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    stop[..., 0] = np.clip(np.rint(232 + tnoise * 8.0), 216, 246)
    stop[..., 1] = 0
    stop[..., 2] = 0
    stop[..., 3] = 255
    return (side, n_side, Image.fromarray(spec_side, "RGBA")), (top, n_top, Image.fromarray(stop, "RGBA"))


def draw_drip_bucket():
    # Institutional yellow-ochre HDPE utility bucket + murky ceiling rainwater
    noise = fbm_noise(4620)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    rib_m = ((yy >= 6) & (yy <= 20)) | ((yy >= 44) & (yy <= 50))
    grime = np.maximum(0.0, (yy - 92.0) / 36.0) * 26.0 + np.maximum(0.0, noise) * 9.0
    s_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_arr[..., 0] = np.clip(np.rint(214 + np.where(rib_m, 10.0, 0.0) - grime), 0, 255)
    s_arr[..., 1] = np.clip(np.rint(166 + np.where(rib_m, 8.0, 0.0) - grime * 0.9), 0, 255)
    s_arr[..., 2] = np.clip(np.rint(40 + np.where(rib_m, 4.0, 0.0) - grime * 0.4), 0, 255)
    s_arr[..., 3] = 255
    side = Image.fromarray(s_arr, "RGBA")
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, 7], fill=(184, 140, 32, 255))
    sd.rectangle([0, SIZE - 8, SIZE - 1, SIZE - 1], fill=(108, 86, 28, 255))

    n_side = height_to_normal(noise * 0.14 + np.where(rib_m, 0.35, 0.0), strength=2.6)
    spec_side = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    spec_side[..., 0] = np.clip(np.rint(120 + noise * 18.0), 86, 152)
    spec_side[..., 1] = 0
    spec_side[..., 2] = 0
    spec_side[..., 3] = 255

    # Murky greenish-brown ceiling rainwater with subtle drop ripples
    wnoise = fbm_noise(4621)
    r = np.hypot(xx - 64.0, yy - 64.0)
    ripple = np.sin(r * 0.55) * np.exp(-r / 38.0) * 8.0
    w_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    w_arr[..., 0] = np.clip(np.rint(68 + ripple + wnoise * 6.0), 30, 120)
    w_arr[..., 1] = np.clip(np.rint(92 + ripple + wnoise * 7.0), 40, 140)
    w_arr[..., 2] = np.clip(np.rint(84 + ripple + wnoise * 6.0), 38, 130)
    w_arr[..., 3] = 235
    water = Image.fromarray(w_arr, "RGBA")
    n_water = height_to_normal(wnoise * 0.08 + np.sin(r * 0.55) * np.exp(-r / 38.0) * 0.25, strength=2.2)
    sw = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    sw[..., 0] = np.clip(np.rint(236 + wnoise * 6.0), 220, 248)
    sw[..., 1] = 0
    sw[..., 2] = 0
    sw[..., 3] = 255
    return (side, n_side, Image.fromarray(spec_side, "RGBA")), (water, n_water, Image.fromarray(sw, "RGBA"))


def draw_camp_sleeping_bag():
    # Quilted olive-drab ripstop nylon sleeping bag + khaki-tan rolled camp pillow
    noise = fbm_noise(4630)
    xx, yy = np.meshgrid(np.arange(SIZE, dtype=np.float32), np.arange(SIZE, dtype=np.float32))
    baffle = np.sin((yy / 22.0) * math.pi)
    seam_m = np.abs(baffle) < 0.14
    zipper_m = (xx >= 112) & (xx <= 122)
    fold_shade = baffle * 9.0 + noise * 7.0
    arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    arr[..., 0] = np.clip(np.rint(np.where(zipper_m, 34, 74 + fold_shade)), 0, 255)
    arr[..., 1] = np.clip(np.rint(np.where(zipper_m, 36, 90 + fold_shade)), 0, 255)
    arr[..., 2] = np.clip(np.rint(np.where(zipper_m, 34, 56 + fold_shade * 0.7)), 0, 255)
    arr[..., 3] = 255
    bag = Image.fromarray(arr, "RGBA")
    bd = ImageDraw.Draw(bag)
    for sy in range(22, SIZE, 22):
        bd.line([(0, sy), (111, sy)], fill=(48, 58, 36, 255), width=2)
    for zy in range(6, SIZE, 6):
        bd.rectangle([115, zy, 119, zy + 2], fill=(122, 126, 130, 255))

    h_bag = noise * 0.12 + baffle * 0.42 - np.where(seam_m, 0.35, 0.0)
    n_bag = height_to_normal(h_bag, strength=3.0, ao=np.where(seam_m, 195.0, 255.0))
    s_bag = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_bag[..., 0] = np.where(zipper_m, 140, np.clip(np.rint(28 + noise * 8.0), 12, 48))
    s_bag[..., 1] = np.where(zipper_m, 180, 0)
    s_bag[..., 2] = 0
    s_bag[..., 3] = 255

    # Khaki-tan camp pillow / folded cot pillow
    pnoise = fbm_noise(4631)
    pcrease = np.exp(-((yy - 64.0) ** 2) / 90.0) * -10.0 + pnoise * 7.0
    p_arr = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    p_arr[..., 0] = np.clip(np.rint(168 + pcrease), 0, 255)
    p_arr[..., 1] = np.clip(np.rint(154 + pcrease), 0, 255)
    p_arr[..., 2] = np.clip(np.rint(126 + pcrease), 0, 255)
    p_arr[..., 3] = 255
    pillow = Image.fromarray(p_arr, "RGBA")
    ImageDraw.Draw(pillow).rectangle([2, 2, SIZE - 3, SIZE - 3], outline=(132, 120, 96, 255), width=3)
    n_pil = height_to_normal(pnoise * 0.14 - np.exp(-((yy - 64.0) ** 2) / 90.0) * 0.25, strength=2.4)
    s_pil = np.zeros((SIZE, SIZE, 4), dtype="uint8")
    s_pil[..., 0] = np.clip(np.rint(18 + pnoise * 6.0), 8, 34)
    s_pil[..., 1] = 0
    s_pil[..., 2] = 0
    s_pil[..., 3] = 255
    return (bag, n_bag, Image.fromarray(s_bag, "RGBA")), (pillow, n_pil, Image.fromarray(s_pil, "RGBA"))


def main():
    # 1. meg_chalk_sign (normal + slashed)
    chalk_img, chalk_n, chalk_s = draw_meg_chalk_sign(False)
    save_pbr_pair("meg_chalk_sign", chalk_img, chalk_n, chalk_s)
    slashed_img, slashed_n, slashed_s = draw_meg_chalk_sign(True)
    save_pbr_pair("meg_chalk_sign_slashed", slashed_img, slashed_n, slashed_s)
    for variant_name, tex_name in [("meg_chalk_sign", "meg_chalk_sign"), ("meg_chalk_sign_slashed", "meg_chalk_sign_slashed")]:
        write_json(MODELS / f"{variant_name}.json", {
            "textures": {"tex": NS + tex_name, "particle": NS + tex_name},
            "elements": [{
                "from": [1, 2, 15.4],
                "to": [15, 14, 15.6],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#tex"},
                    "south": {"uv": [16, 0, 0, 16], "texture": "#tex"}
                }
            }]
        })
    write_json(BLOCKSTATES / "meg_chalk_sign.json", {
        "variants": {
            f"facing={facing},slashed={str(slashed).lower()}": {
                "model": NS + ("meg_chalk_sign_slashed" if slashed else "meg_chalk_sign"),
                **({"y": y} if y else {})
            }
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
            for slashed in (False, True)
        }
    })
    write_item_files("meg_chalk_sign")

    # 2. caution_tape_barrier
    tape_img, tape_n, tape_s = draw_caution_tape()
    save_pbr_pair("caution_tape_barrier", tape_img, tape_n, tape_s)
    write_json(MODELS / "caution_tape_barrier.json", {
        "textures": {"tex": NS + "caution_tape_barrier", "particle": NS + "caution_tape_barrier"},
        "elements": [{
            "from": [0, 0, 7.6],
            "to": [16, 16, 8.4],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#tex"},
                "south": {"uv": [16, 0, 0, 16], "texture": "#tex"}
            }
        }]
    })
    write_json(BLOCKSTATES / "caution_tape_barrier.json", {
        "variants": {
            f"facing={facing}": {
                "model": NS + "caution_tape_barrier",
                **({"y": y} if y else {})
            }
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("caution_tape_barrier")
    write_loot_self("caution_tape_barrier")

    # 3. office_dark_mirror (Batch 2 POI #9 - unbreakable 3-variant dark mirror: plain + unified 2-tall silhouette)
    mirror_variants = [
        ("office_dark_mirror", "plain"),
        ("office_dark_mirror_figure_bottom", "bottom"),
        ("office_dark_mirror_figure_top", "top"),
    ]
    for tex_name, mode in mirror_variants:
        m_img, m_n, m_s = draw_office_dark_mirror(mode)
        save_pbr_pair(tex_name, m_img, m_n, m_s)
        write_json(MODELS / f"{tex_name}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": NS + tex_name}
        })
    write_json(BLOCKSTATES / "office_dark_mirror.json", {
        "variants": {
            "silhouette=0": {"model": NS + "office_dark_mirror"},
            "silhouette=1": {"model": NS + "office_dark_mirror_figure_bottom"},
            "silhouette=2": {"model": NS + "office_dark_mirror_figure_top"},
        }
    })
    write_item_files("office_dark_mirror")

    # 4. corrupted_fountain_pool (Batch 3 POI #16 - turbid gray-crimson corrupted fountain)
    cf_img, cf_n, cf_s = draw_corrupted_fountain_liquid()
    save_pbr_pair("corrupted_fountain_liquid", cf_img, cf_n, cf_s)
    write_fountain_model("corrupted_fountain_pool", 12)
    write_fountain_model("corrupted_fountain_pool_sampled", 7)
    write_json(BLOCKSTATES / "corrupted_fountain_pool.json", {
        "variants": {
            "sampled=false": {"model": NS + "corrupted_fountain_pool"},
            "sampled=true": {"model": NS + "corrupted_fountain_pool_sampled"}
        }
    })
    write_item_files("corrupted_fountain_pool")

    # 5. executive_red_phone (Batch 4 POI #20)
    ed_img, ed_n, ed_s = draw_executive_desk_wood()
    save_pbr_pair("executive_desk_wood", ed_img, ed_n, ed_s)
    ep_img, ep_n, ep_s = draw_executive_red_phone()
    save_pbr_pair("executive_red_phone", ep_img, ep_n, ep_s)
    wood_faces = {f: {"uv": [0, 0, 16, 16], "texture": "#wood"} for f in ("north", "south", "east", "west", "up", "down")}
    phone_faces = {f: {"uv": [0, 0, 16, 16], "texture": "#phone"} for f in ("north", "south", "east", "west", "up", "down")}
    write_json(MODELS / "executive_red_phone.json", {
        "textures": {
            "wood": NS + "executive_desk_wood",
            "phone": NS + "executive_red_phone",
            "particle": NS + "executive_desk_wood"
        },
        "elements": [
            {"from": [1, 0, 2], "to": [15, 11, 14], "faces": wood_faces},
            {"from": [4, 11, 4], "to": [12, 13.5, 11], "faces": phone_faces},
            {"from": [3, 13.5, 5], "to": [13, 15, 8], "faces": phone_faces}
        ]
    })
    write_json(BLOCKSTATES / "executive_red_phone.json", {
        "variants": {
            f"facing={facing}": {
                "model": NS + "executive_red_phone",
                **({"y": y} if y else {})
            }
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("executive_red_phone")
    write_loot_self("executive_red_phone")

    # 6. office_elevator_frame (dedicated brushed dark steel elevator shaft/cabin frame)
    save_elevator_frame_pbr("office_elevator_frame", draw_office_elevator_frame())
    write_json(MODELS / "office_elevator_frame.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": NS + "office_elevator_frame"}
    })
    write_json(BLOCKSTATES / "office_elevator_frame.json", {
        "variants": {"": {"model": NS + "office_elevator_frame"}}
    })
    write_item_files("office_elevator_frame")

    # 7. Route A POI container blocks (replaces all vanilla wooden barrels in Level 4 POIs)
    (crate_side, crate_n, crate_s), (crate_top, crate_tn, crate_ts) = draw_meg_supply_crate()
    save_pbr_pair("office_supply_crate_side", crate_side, crate_n, crate_s)
    save_pbr_pair("office_supply_crate_top", crate_top, crate_tn, crate_ts)
    crate_side_faces = {f: {"uv": [1, 4, 15, 15], "texture": "#side"} for f in ("north", "south", "east", "west")}
    crate_side_faces["down"] = {"uv": [1, 2, 15, 14], "texture": "#top"}
    crate_lid_faces = {f: {"uv": [0, 0, 16, 2], "texture": "#top"} for f in ("north", "south", "east", "west")}
    crate_lid_faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#top"}
    crate_lid_faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#top"}
    write_json(MODELS / "office_supply_crate.json", {
        "textures": {
            "side": NS + "office_supply_crate_side",
            "top": NS + "office_supply_crate_top",
            "particle": NS + "office_supply_crate_side"
        },
        "elements": [
            {"from": [1, 0, 2], "to": [15, 9, 14], "faces": crate_side_faces},
            {"from": [0.75, 9, 1.75], "to": [15.25, 11, 14.25], "faces": crate_lid_faces},
            {"from": [3.5, 7, 1.3], "to": [5, 10, 2], "faces": box_faces("side", [3, 1, 5, 4])},
            {"from": [11, 7, 1.3], "to": [12.5, 10, 2], "faces": box_faces("side", [11, 1, 13, 4])},
            {"from": [6, 11, 7], "to": [10, 12, 9], "faces": box_faces("top", [0, 0, 3, 3])}
        ]
    })
    write_json(BLOCKSTATES / "office_supply_crate.json", {
        "variants": {
            f"facing={facing}": {"model": NS + "office_supply_crate", **({"y": y} if y else {})}
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("office_supply_crate")
    write_loot_self("office_supply_crate")

    (tb_side, tb_n, tb_s), (tb_top, tb_tn, tb_ts) = draw_industrial_toolbox()
    save_pbr_pair("industrial_toolbox_side", tb_side, tb_n, tb_s)
    save_pbr_pair("industrial_toolbox_top", tb_top, tb_tn, tb_ts)
    tb_body_faces = {f: {"uv": [1, 6, 15, 15], "texture": "#side"} for f in ("north", "south", "east", "west")}
    tb_body_faces["down"] = {"uv": [1, 3, 15, 13], "texture": "#top"}
    tb_lid_faces = {f: {"uv": [1, 0, 15, 3], "texture": "#top"} for f in ("north", "south", "east", "west")}
    tb_lid_faces["up"] = {"uv": [1, 3, 15, 13], "texture": "#top"}
    tb_lid_faces["down"] = {"uv": [1, 3, 15, 13], "texture": "#top"}
    write_json(MODELS / "industrial_toolbox.json", {
        "textures": {
            "side": NS + "industrial_toolbox_side",
            "top": NS + "industrial_toolbox_top",
            "particle": NS + "industrial_toolbox_side"
        },
        "elements": [
            {"from": [1, 0, 3], "to": [15, 7.5, 13], "faces": tb_body_faces},
            {"from": [1.5, 7.5, 3.5], "to": [14.5, 9.5, 12.5], "faces": tb_lid_faces},
            {"from": [7, 6.2, 2.5], "to": [9, 8.6, 3.1], "faces": box_faces("side", [7, 5, 9, 8])},
            {"from": [5, 9.5, 7.5], "to": [6, 11, 8.5], "faces": box_faces("side", [7, 5, 8, 7])},
            {"from": [10, 9.5, 7.5], "to": [11, 11, 8.5], "faces": box_faces("side", [7, 5, 8, 7])},
            {"from": [5, 10.5, 7.5], "to": [11, 11.3, 8.5], "faces": box_faces("side", [5, 5, 11, 6])}
        ]
    })
    write_json(BLOCKSTATES / "industrial_toolbox.json", {
        "variants": {
            f"facing={facing}": {"model": NS + "industrial_toolbox", **({"y": y} if y else {})}
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("industrial_toolbox")
    write_loot_self("industrial_toolbox")

    (safe_front, safe_fn, safe_fs), (safe_side, safe_sn, safe_ss) = draw_office_steel_safe()
    save_pbr_pair("office_steel_safe_front", safe_front, safe_fn, safe_fs)
    save_pbr_pair("office_steel_safe_side", safe_side, safe_sn, safe_ss)
    safe_body_faces = {f: {"uv": [1, 1, 15, 15], "texture": "#side"} for f in ("south", "east", "west", "up", "down")}
    safe_body_faces["north"] = {"uv": [1, 1, 15, 15], "texture": "#front"}
    safe_door_faces = {f: {"uv": [0, 0, 16, 16], "texture": "#side"} for f in ("south", "east", "west", "up", "down")}
    safe_door_faces["north"] = {"uv": [1, 1, 15, 15], "texture": "#front"}
    write_json(MODELS / "office_steel_safe.json", {
        "textures": {
            "front": NS + "office_steel_safe_front",
            "side": NS + "office_steel_safe_side",
            "particle": NS + "office_steel_safe_side"
        },
        "elements": [
            {"from": [1, 0, 1.5], "to": [15, 15, 15], "faces": safe_body_faces},
            {"from": [2, 1, 1], "to": [14, 14, 1.5], "faces": safe_door_faces},
            {"from": [6.5, 8.5, 0.4], "to": [9.5, 11.5, 1], "faces": box_faces("front", [6, 4, 10, 8])},
            {"from": [10.5, 6.5, 0.3], "to": [12.5, 7.8, 1], "faces": box_faces("front", [11, 8, 13, 10])},
            {"from": [1.1, 3, 0.7], "to": [2, 5, 1.5], "faces": box_faces("front", [1, 10, 2, 12])},
            {"from": [1.1, 10, 0.7], "to": [2, 12, 1.5], "faces": box_faces("front", [1, 3, 2, 5])}
        ]
    })
    write_json(BLOCKSTATES / "office_steel_safe.json", {
        "variants": {
            f"facing={facing}": {"model": NS + "office_steel_safe", **({"y": y} if y else {})}
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("office_steel_safe")
    write_loot_self("office_steel_safe")

    # 8. Route B landmark monitor wall (executive_broadcast_screen)
    (scr_front, scr_fn, scr_fs), (scr_side, scr_sn, scr_ss) = draw_executive_broadcast_screen()
    save_pbr_pair("executive_broadcast_screen_front", scr_front, scr_fn, scr_fs)
    save_pbr_pair("executive_broadcast_screen_side", scr_side, scr_sn, scr_ss)
    write_json(MODELS / "executive_broadcast_screen.json", {
        "parent": "minecraft:block/cube",
        "textures": {
            "north": NS + "executive_broadcast_screen_front",
            "south": NS + "executive_broadcast_screen_side",
            "east": NS + "executive_broadcast_screen_side",
            "west": NS + "executive_broadcast_screen_side",
            "up": NS + "executive_broadcast_screen_side",
            "down": NS + "executive_broadcast_screen_side",
            "particle": NS + "executive_broadcast_screen_front"
        }
    })
    write_json(BLOCKSTATES / "executive_broadcast_screen.json", {
        "variants": {
            f"facing={facing}": {"model": NS + "executive_broadcast_screen", **({"y": y} if y else {})}
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("executive_broadcast_screen")
    write_loot_self("executive_broadcast_screen")

    # 9. Stage 2 vanilla-replacement POI props: paint_bucket, drip_bucket, camp_sleeping_bag, upgraded omega_cot
    (pb_side, pb_sn, pb_ss), (pb_top, pb_tn, pb_ts) = draw_paint_bucket()
    save_pbr_pair("paint_bucket_side", pb_side, pb_sn, pb_ss)
    save_pbr_pair("paint_bucket_top", pb_top, pb_tn, pb_ts)
    pb_body_faces = {f: {"uv": [3, 2, 13, 15], "texture": "#side"} for f in ("north", "south", "east", "west")}
    pb_body_faces["down"] = {"uv": [3, 3, 13, 13], "texture": "#side"}
    pb_rim_faces = {f: {"uv": [2, 0, 14, 2], "texture": "#side"} for f in ("north", "south", "east", "west", "up", "down")}
    write_json(MODELS / "paint_bucket.json", {
        "textures": {
            "side": NS + "paint_bucket_side",
            "top": NS + "paint_bucket_top",
            "particle": NS + "paint_bucket_side"
        },
        "elements": [
            {"from": [3, 0, 3], "to": [13, 9.5, 13], "faces": pb_body_faces},
            {"from": [2.6, 9.5, 2.6], "to": [13.4, 10.5, 3.8], "faces": pb_rim_faces},
            {"from": [2.6, 9.5, 12.2], "to": [13.4, 10.5, 13.4], "faces": pb_rim_faces},
            {"from": [2.6, 9.5, 3.8], "to": [3.8, 10.5, 12.2], "faces": pb_rim_faces},
            {"from": [12.2, 9.5, 3.8], "to": [13.4, 10.5, 12.2], "faces": pb_rim_faces},
            {"from": [3.8, 8.5, 3.8], "to": [12.2, 9.6, 12.2], "faces": {"up": {"uv": [2, 2, 14, 14], "texture": "#top"}}},
            {"from": [2.4, 5.2, 2.4], "to": [3.0, 9.4, 8.0], "faces": box_faces("side", [0, 1, 2, 3])},
            {"from": [13.0, 5.2, 2.4], "to": [13.6, 9.4, 8.0], "faces": box_faces("side", [0, 1, 2, 3])},
            {"from": [2.4, 4.9, 2.0], "to": [13.6, 5.5, 2.6], "faces": box_faces("side", [0, 1, 2, 3])}
        ]
    })
    write_json(BLOCKSTATES / "paint_bucket.json", {
        "variants": {"": {"model": NS + "paint_bucket"}}
    })
    write_item_files("paint_bucket")
    write_loot_self("paint_bucket")

    (db_side, db_sn, db_ss), (db_water, db_wn, db_ws) = draw_drip_bucket()
    save_pbr_pair("drip_bucket_side", db_side, db_sn, db_ss)
    save_pbr_pair("drip_bucket_water", db_water, db_wn, db_ws)
    db_wall_faces = {f: {"uv": [2, 1, 14, 15], "texture": "#side"} for f in ("north", "south", "east", "west", "up", "down")}
    db_base_elems = [
        {"from": [2.5, 0, 2.5], "to": [13.5, 1.2, 13.5], "faces": box_faces("side", [2, 13, 14, 16])},
        {"from": [2.5, 1.2, 2.5], "to": [13.5, 11.0, 3.6], "faces": db_wall_faces},
        {"from": [2.5, 1.2, 12.4], "to": [13.5, 11.0, 13.5], "faces": db_wall_faces},
        {"from": [2.5, 1.2, 3.6], "to": [3.6, 11.0, 12.4], "faces": db_wall_faces},
        {"from": [12.4, 1.2, 3.6], "to": [13.5, 11.0, 12.4], "faces": db_wall_faces},
        {"from": [2.0, 5.6, 2.1], "to": [2.5, 9.8, 8.2], "faces": box_faces("side", [0, 14, 2, 16])},
        {"from": [13.5, 5.6, 2.1], "to": [14.0, 9.8, 8.2], "faces": box_faces("side", [0, 14, 2, 16])},
        {"from": [2.0, 5.3, 1.8], "to": [14.0, 5.9, 2.4], "faces": box_faces("side", [0, 14, 2, 16])}
    ]
    for lvl, water_y in [(0, None), (1, 4.8), (2, 8.6)]:
        elems = list(db_base_elems)
        if water_y is not None:
            elems.append({
                "from": [3.6, 1.2, 3.6],
                "to": [12.4, water_y, 12.4],
                "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#water"}}
            })
        write_json(MODELS / f"drip_bucket_{lvl}.json", {
            "textures": {
                "side": NS + "drip_bucket_side",
                "water": NS + "drip_bucket_water",
                "particle": NS + "drip_bucket_side"
            },
            "elements": elems
        })
    write_json(BLOCKSTATES / "drip_bucket.json", {
        "variants": {
            "level=0": {"model": NS + "drip_bucket_0"},
            "level=1": {"model": NS + "drip_bucket_1"},
            "level=2": {"model": NS + "drip_bucket_2"}
        }
    })
    write_json(ITEM_MODELS / "drip_bucket.json", {"parent": NS + "drip_bucket_2"})
    write_json(ITEMS_DEF / "drip_bucket.json", {"model": {"type": "minecraft:model", "model": "noclip_backrooms:item/drip_bucket"}})
    write_loot_self("drip_bucket")

    (bag_img, bag_n, bag_s), (pil_img, pil_n, pil_s) = draw_camp_sleeping_bag()
    save_pbr_pair("camp_sleeping_bag_top", bag_img, bag_n, bag_s)
    save_pbr_pair("camp_sleeping_bag_pillow", pil_img, pil_n, pil_s)
    bag_faces = {f: {"uv": [0, 0, 13, 16], "texture": "#top"} for f in ("north", "south", "east", "west", "up", "down")}
    pil_faces = {f: {"uv": [0, 0, 16, 16], "texture": "#pillow"} for f in ("north", "south", "east", "west", "up", "down")}
    write_json(MODELS / "camp_sleeping_bag.json", {
        "textures": {
            "top": NS + "camp_sleeping_bag_top",
            "pillow": NS + "camp_sleeping_bag_pillow",
            "particle": NS + "camp_sleeping_bag_top"
        },
        "elements": [
            {"from": [2, 0, -8], "to": [14, 2.5, 11], "faces": bag_faces},
            {"from": [2.5, 0, -9], "to": [13.5, 2.2, -8], "faces": bag_faces},
            {"from": [2.5, 0, 11], "to": [13.5, 3.6, 15.2], "faces": pil_faces},
            {"from": [13.6, 1.5, -7], "to": [14.2, 2.3, 10.5], "faces": box_faces("top", [14, 0, 15.5, 16])}
        ]
    })
    write_json(BLOCKSTATES / "camp_sleeping_bag.json", {
        "variants": {
            f"facing={facing}": {"model": NS + "camp_sleeping_bag", **({"y": y} if y else {})}
            for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
        }
    })
    write_item_files("camp_sleeping_bag")
    write_loot_self("camp_sleeping_bag")

    # Upgrade stubby 1x1 omega_cot model to a full-length 26-unit folding military cot with brushed steel frame & pillow
    cot_canvas_faces = {f: {"uv": [0, 0, 16, 16], "texture": "#tex"} for f in ("north", "south", "east", "west", "down")}
    cot_canvas_faces["up"] = {"uv": [0, 16, 16, 0], "texture": "#tex"}
    cot_frame_faces = box_faces("frame", [0, 0, 16, 16])
    cot_leg_zs = [(-8.5, -7.3), (2.4, 3.6), (13.3, 14.5)]
    cot_elems = [
        {"from": [1.5, 5.2, -9.5], "to": [14.5, 6.8, 15.5], "faces": cot_canvas_faces},
        {"from": [0.8, 4.6, -10.0], "to": [1.8, 6.4, 16.0], "faces": cot_frame_faces},
        {"from": [14.2, 4.6, -10.0], "to": [15.2, 6.4, 16.0], "faces": cot_frame_faces},
        {"from": [1.8, 4.6, -10.0], "to": [14.2, 6.2, -9.0], "faces": cot_frame_faces},
        {"from": [1.8, 4.6, 15.0], "to": [14.2, 6.2, 16.0], "faces": cot_frame_faces},
        {"from": [3.0, 6.8, 10.5], "to": [13.0, 8.8, 14.8], "faces": pil_faces},
    ]
    for z0, z1 in cot_leg_zs:
        cot_elems.append({"from": [1.2, 0.0, z0], "to": [2.2, 4.6, z1], "faces": cot_frame_faces})
        cot_elems.append({"from": [13.8, 0.0, z0], "to": [14.8, 4.6, z1], "faces": cot_frame_faces})
        cot_elems.append({"from": [2.2, 0.6, z0], "to": [13.8, 1.4, z1], "faces": cot_frame_faces})
    write_json(MODELS / "omega_cot.json", {
        "textures": {
            "tex": NS + "omega_cot",
            "frame": NS + "office_elevator_frame",
            "pillow": NS + "camp_sleeping_bag_pillow",
            "particle": NS + "omega_cot"
        },
        "elements": cot_elems
    })
    print("Generated Level 4 Batch 1..4 + Route A/B + Stage 2 POI assets complete")


if __name__ == "__main__":
    main()
