"""Realistic Level 4 ("Abandoned Office") textures for the built-in "realistic" resource pack.

Generates 512px textures + labPBR _n/_s maps (using cached CC0 ambientCG substrates in
tools/.cache/ambientcg/ when available, or high-frequency procedural grain otherwise), then
resizes surfaces to 128px and props to 64px via tools/resize_realistic.py to comply with
STYLE_GUIDE.md.

Run: python tools/make_level4_realistic.py
"""
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
OLD = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level4"
PACK = ROOT / "src/main/resources/resourcepacks/realistic"
OUT = PACK / "assets/noclip_backrooms/textures/block/level4"
CACHE = TOOLS / ".cache/ambientcg"
PREVIEW = ROOT / "docs/look/level4_realistic.png"
S = 512

MATERIAL = [
    (("carpet",), "carpet"),
    (("cot",), "fabric"),
    (("desk_wood", "fire_door", "tbd_shelf", "tbd_hoard", "amor_planter"), "wood"),
    (("elevator", "breaker", "locker", "vending", "cooler_body", "photocopier", "hatch"), "steel"),
    (("drywall", "pillar", "radiator", "acoustic_ceiling", "ceiling_vent", "omega_wall", "omega_barricade", "almond_fountain_basin"), "concrete"),
]
PLAIN = ("troffer", "window", "glass_wall", "exit_sign", "cooler_jug", "almond_liquid", "almond_bottle",
         "amor_mushroom", "amor_substrate", "desk_phone", "omega_sign",
         "stairwell", "door_4p3", "door_11", "door_13", "basement_71")


def kind(name):
    if any(k in name for k in PLAIN):
        return None
    for keys, material in MATERIAL:
        if any(k in name for k in keys):
            return material
    return "paint"


def find_cached(asset, suffix):
    folder = CACHE / asset
    if not folder.exists():
        return None
    matches = list(folder.rglob(f"*_{suffix}.jpg")) + list(folder.rglob(f"*_{suffix}.png"))
    return matches[0] if matches else None


def load_gray(path):
    img = Image.open(path).convert("L").resize((S, S), Image.Resampling.LANCZOS)
    return np.asarray(img, dtype=np.float32) / 255.0


def proc_grain(seed, sigma=0.06):
    rng = np.random.default_rng(seed)
    fine = rng.normal(0.0, sigma, (S, S)).astype(np.float32)
    coarse_img = Image.fromarray(np.clip((rng.normal(0.5, 0.25, (S // 4, S // 4))) * 255, 0, 255).astype("uint8"), "L")
    coarse = np.asarray(coarse_img.resize((S, S), Image.Resampling.BILINEAR).filter(ImageFilter.GaussianBlur(2)), dtype=np.float32) / 255.0 - 0.5
    return fine + coarse * (sigma * 2.2)


def load_substrate(asset, seed, strength, smooth_val):
    cpath = find_cached(asset, "Color")
    if cpath is not None:
        g = load_gray(cpath)
        blurred = np.asarray(Image.fromarray((g * 255).astype("uint8"), "L").filter(ImageFilter.GaussianBlur(10)), dtype=np.float32) / 255.0
        delta = (g - blurred) * strength
    else:
        delta = proc_grain(seed, strength * 0.14)
    return delta, smooth_val


def substrates():
    return {
        "carpet": load_substrate("Carpet016", 4501, 0.9, 0.05),
        "fabric": load_substrate("Fabric028", 4502, 0.85, 0.06),
        "wood": load_substrate("Wood051", 4503, 0.75, 0.30),
        "steel": load_substrate("Metal049A", 4504, 0.65, 0.52),
        "concrete": load_substrate("Concrete019", 4505, 0.45, 0.16),
        "paint": load_substrate("Metal032", 4506, 0.35, 0.32),
    }


def composite(name, subs):
    src = Image.open(OLD / f"{name}.png").convert("RGBA")
    has_alpha = src.getextrema()[-1][0] < 255
    rgb = np.asarray(src.convert("RGB").resize((S, S), Image.Resampling.LANCZOS), dtype=np.float32) / 255.0
    alpha = np.asarray(src.getchannel("A").resize((S, S), Image.Resampling.NEAREST), dtype=np.uint8) if has_alpha else None

    old_n = Image.open(OLD / f"{name}_n.png").convert("RGBA").resize((S, S), Image.Resampling.BILINEAR)
    old_s = Image.open(OLD / f"{name}_s.png").convert("RGBA").resize((S, S), Image.Resampling.BILINEAR)
    n_arr = np.asarray(old_n, dtype=np.float32)
    s_arr = np.asarray(old_s, dtype=np.float32)

    mat = kind(name)
    if mat is not None:
        delta, smooth_val = subs[mat]
        sx = zlib.crc32(name.encode()) % S
        sy = zlib.crc32(name[::-1].encode()) % S
        d = np.roll(delta, (sy, sx), axis=(0, 1))
        # Modulate midtones without blowing out bright white drywall or dark blacked-out areas
        mid = 1.0 - np.abs(rgb.mean(axis=2) * 2.0 - 1.0) * 0.4
        rgb = np.clip(rgb + (d * mid)[..., None], 0.0, 1.0)
        # Subtle micro-normal perturbation
        dx = np.roll(d, -1, axis=1) - np.roll(d, 1, axis=1)
        dy = np.roll(d, -1, axis=0) - np.roll(d, 1, axis=0)
        n_arr[..., 0] = np.clip(n_arr[..., 0] - dx * 90.0, 0, 255)
        n_arr[..., 1] = np.clip(n_arr[..., 1] - dy * 90.0, 0, 255)
        s_arr[..., 0] = np.clip(s_arr[..., 0] * 0.65 + (smooth_val * 255.0) * 0.35 + d * 40.0, 0, 255)

    out_rgb = np.clip(np.rint(rgb * 255.0), 0, 255).astype("uint8")
    if alpha is not None:
        Image.fromarray(np.dstack([out_rgb, alpha]), "RGBA").save(OUT / f"{name}.png", optimize=True)
    else:
        Image.fromarray(out_rgb, "RGB").save(OUT / f"{name}.png", optimize=True)
    Image.fromarray(np.clip(np.rint(n_arr), 0, 255).astype("uint8"), "RGBA").save(OUT / f"{name}_n.png", optimize=True)
    Image.fromarray(np.clip(np.rint(s_arr), 0, 255).astype("uint8"), "RGBA").save(OUT / f"{name}_s.png", optimize=True)
    return out_rgb


def preview(tiles):
    cols, gap, side = 6, 8, 128
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (side + gap) + gap, rows * (side + gap) + gap), (28, 30, 34))
    for i, rgb in enumerate(tiles):
        tile = Image.fromarray(rgb, "RGB").resize((side, side), Image.Resampling.LANCZOS)
        sheet.paste(tile, (gap + (i % cols) * (side + gap), gap + (i // cols) * (side + gap)))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW, optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    subs = substrates()
    names = []
    for f in sorted(OLD.glob("*.png")):
        if f.stem.endswith(("_n", "_s")) or f.with_suffix(".png.mcmeta").exists():
            continue
        with Image.open(f) as image:
            if image.width != image.height:
                continue
        names.append(f.stem)

    want_preview = {"office_carpet", "office_carpet_dent_1", "office_drywall", "office_acoustic_ceiling",
                    "office_troffer_lit", "office_blacked_window", "office_trap_window", "office_fire_door",
                    "office_elevator", "omega_wall", "vending_front", "cooler_body"}
    preview_tiles = []
    counts = {}
    for name in names:
        rgb = composite(name, subs)
        if name in want_preview:
            preview_tiles.append(rgb)
        tag = kind(name) or "plain"
        counts[tag] = counts.get(tag, 0) + 1

    preview(preview_tiles)
    print(f"{len(names)} Level 4 textures (+ _n/_s) -> {OUT.relative_to(ROOT)}", counts)


if __name__ == "__main__":
    main()
    import resize_realistic  # noqa: E402
    resize_realistic.main()
