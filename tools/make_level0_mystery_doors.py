"""Generates 128px base and 512px labPBR realistic textures, models, blockstates, and item definitions
for Level 0's mystery foreshadowing exits (`hub_door`, `hub_door_top`, `fun_door`, `fun_door_top`).

Run:
    python tools/make_level0_mystery_doors.py
"""

import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
REALISTIC = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms"
S = 512


def labpbr(height: np.ndarray, smooth: np.ndarray, f0: np.ndarray, porosity: np.ndarray, glow: np.ndarray):
    gx = np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)
    gy = np.roll(height, -1, axis=0) - np.roll(height, 1, axis=0)
    nx, ny = -gx * 0.4, -gy * 0.4
    nz = np.sqrt(np.clip(1.0 - nx * nx - ny * ny, 0.0, 1.0))
    ao = np.clip(1.0 - np.abs(gx) * 0.08 - np.abs(gy) * 0.08, 0.4, 1.0)
    n_img = np.stack([
        np.clip((nx * 0.5 + 0.5) * 255.0, 0, 255),
        np.clip((ny * 0.5 + 0.5) * 255.0, 0, 255),
        np.clip(ao * 255.0, 0, 255),
        np.clip(255.0 - np.clip(128.0 - height * 8.0, 0, 255), 0, 255),
    ], axis=-1).astype(np.uint8)
    s_img = np.stack([
        np.clip(smooth * 255.0, 0, 255),
        np.clip(f0, 0, 255),
        np.clip(porosity * 255.0, 0, 255),
        np.clip(glow * 255.0, 0, 255),
    ], axis=-1).astype(np.uint8)
    return Image.fromarray(n_img, "RGBA"), Image.fromarray(s_img, "RGBA")


def save_tex(name: str, color: Image.Image, height: np.ndarray, smooth: np.ndarray,
             f0: np.ndarray, porosity: np.ndarray, glow: np.ndarray):
    base_out = ASSETS / "textures/block/level0"
    real_out = REALISTIC / "textures/block/level0"
    base_out.mkdir(parents=True, exist_ok=True)
    real_out.mkdir(parents=True, exist_ok=True)

    c512 = color.convert("RGB")
    c128 = c512.resize((128, 128), Image.Resampling.LANCZOS)
    c128.save(base_out / f"{name}.png")
    c512.save(real_out / f"{name}.png")

    n512, s512 = labpbr(height, smooth, f0, porosity, glow)
    n512.save(real_out / f"{name}_n.png")
    s512.save(real_out / f"{name}_s.png")
    n512.resize((128, 128), Image.Resampling.BILINEAR).save(base_out / f"{name}_n.png")
    s512.resize((128, 128), Image.Resampling.BILINEAR).save(base_out / f"{name}_s.png")


def noise_grid(seed: int, scale: int = 32) -> np.ndarray:
    rng = np.random.default_rng(seed)
    small = rng.normal(0.0, 1.0, (scale, scale))
    img = Image.fromarray(np.clip((small * 0.25 + 0.5) * 255, 0, 255).astype(np.uint8))
    return (np.asarray(img.resize((S, S), Image.Resampling.BICUBIC), dtype=float) / 255.0 - 0.5) * 2.0


def build_hub_door(top: bool):
    rng = np.random.default_rng(101 if top else 102)
    base = np.zeros((S, S, 3), dtype=float)
    base[..., 0] = 78
    base[..., 1] = 84
    base[..., 2] = 90
    grime = noise_grid(41 if top else 42, 24) * 14 + noise_grid(77 if top else 78, 96) * 6
    base += grime[..., None]

    height = noise_grid(88 if top else 89, 64) * 0.4
    smooth = np.full((S, S), 0.34)
    f0 = np.full((S, S), 90.0)
    porosity = np.full((S, S), 0.0)
    glow = np.zeros((S, S))

    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)

    # Recessed industrial door panels
    for y0, y1 in ([(44, 244), (272, 468)] if top else [(44, 244), (272, 476)]):
        draw.rectangle([52, y0, S - 52, y1], outline=(48, 52, 56), width=8)
        draw.rectangle([60, y0 + 8, S - 60, y1 - 8], outline=(98, 104, 112), width=3)
        height[y0:y1, 52:60] -= 1.2
        height[y0:y1, S - 60:S - 52] -= 1.2

    if top:
        # Stenciled faded white HUB badge & directional chevron on the top half
        draw.rectangle([128, 92, 384, 198], outline=(202, 206, 204), width=5)
        # H
        for x0 in (154, 194):
            draw.rectangle([x0, 114, x0 + 12, 176], fill=(212, 216, 212))
        draw.rectangle([154, 140, 206, 152], fill=(212, 216, 212))
        # U
        draw.rectangle([226, 114, 238, 176], fill=(212, 216, 212))
        draw.rectangle([264, 114, 276, 176], fill=(212, 216, 212))
        draw.rectangle([226, 164, 276, 176], fill=(212, 216, 212))
        # B
        draw.rectangle([296, 114, 308, 176], fill=(212, 216, 212))
        draw.rectangle([296, 114, 342, 126], fill=(212, 216, 212))
        draw.rectangle([296, 140, 338, 150], fill=(212, 216, 212))
        draw.rectangle([296, 164, 342, 176], fill=(212, 216, 212))
        draw.rectangle([332, 122, 344, 144], fill=(212, 216, 212))
        draw.rectangle([332, 148, 344, 168], fill=(212, 216, 212))
        # Tiny slit window below badge
        draw.rectangle([176, 306, 336, 404], fill=(20, 24, 28), outline=(44, 48, 52), width=10)
        smooth[306:404, 176:336] = 0.85
        f0[306:404, 176:336] = 40
    else:
        # Kickplate & brushed steel handle
        draw.rectangle([24, 412, S - 24, S - 10], fill=(64, 68, 72), outline=(46, 50, 54), width=4)
        draw.rectangle([396, 14, 434, 124], fill=(142, 146, 150), outline=(42, 46, 50), width=4)
        smooth[14:124, 396:434] = 0.72
        f0[14:124, 396:434] = 190

    # Subtle grime scratches
    for _ in range(18):
        sx = int(rng.integers(40, S - 40))
        sy = int(rng.integers(40, S - 40))
        dx = int(rng.integers(-24, 24))
        dy = int(rng.integers(8, 48))
        draw.line([(sx, sy), (sx + dx, sy + dy)], fill=(54, 58, 62), width=2)

    save_tex("hub_door_top" if top else "hub_door", img, height, smooth, f0, porosity, glow)


def build_fun_door(top: bool):
    rng = np.random.default_rng(201 if top else 202)
    base = np.zeros((S, S, 3), dtype=float)
    # Faded party cream-yellow upper, pale teal-blue lower
    if top:
        base[..., 0], base[..., 1], base[..., 2] = 212, 192, 122
    else:
        base[..., 0], base[..., 1], base[..., 2] = 196, 176, 108
        base[256:, :, 0] = 128
        base[256:, :, 1] = 166
        base[256:, :, 2] = 182
    grime = noise_grid(51 if top else 52, 20) * 12 + noise_grid(61 if top else 62, 64) * 5
    base += grime[..., None]

    height = noise_grid(91 if top else 92, 48) * 0.35
    smooth = np.full((S, S), 0.25)
    f0 = np.full((S, S), 14.0)
    porosity = np.full((S, S), 0.12)
    glow = np.zeros((S, S))

    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)

    # Border trim
    draw.rectangle([28, 24, S - 28, S - 24], outline=(146, 128, 78), width=6)

    if top:
        # Unsettling painted red =) smiley face
        face_col = (168, 36, 36)
        # Eyes (=)
        draw.rectangle([166, 152, 224, 172], fill=face_col)
        draw.rectangle([166, 194, 224, 214], fill=face_col)
        # Smile )
        draw.arc([246, 114, 344, 252], start=-72, end=72, fill=face_col, width=18)

        # Painted balloon in the corner
        draw.ellipse([78, 312, 156, 404], fill=(176, 56, 58), outline=(120, 38, 40), width=4)
        draw.line([(117, 404), (112, 470)], fill=(96, 86, 70), width=3)
        draw.ellipse([366, 294, 438, 378], fill=(64, 122, 178), outline=(44, 86, 130), width=4)
        draw.line([(402, 378), (408, 466)], fill=(96, 86, 70), width=3)
    else:
        # Painted confetti specks & faded brass knob
        for _ in range(28):
            cx = int(rng.integers(48, S - 48))
            cy = int(rng.integers(48, S - 48))
            col = [(176, 56, 58), (64, 122, 178), (214, 172, 58)][int(rng.integers(0, 3))]
            draw.rectangle([cx, cy, cx + 10, cy + 10], fill=col)
        draw.ellipse([392, 40, 444, 92], fill=(196, 166, 84), outline=(112, 90, 42), width=5)
        smooth[40:92, 392:444] = 0.72
        f0[40:92, 392:444] = 175

    save_tex("fun_door_top" if top else "fun_door", img, height, smooth, f0, porosity, glow)


def build_frame(name: str, rgb_col: tuple[int, int, int], f0_val: float):
    base = np.ones((S, S, 3), dtype=float) * np.array(rgb_col, dtype=float)
    base += noise_grid(303, 48)[..., None] * 10
    height = noise_grid(304, 48) * 0.3
    smooth = np.full((S, S), 0.38)
    f0 = np.full((S, S), f0_val)
    porosity = np.zeros((S, S))
    glow = np.zeros((S, S))
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    save_tex(name, img, height, smooth, f0, porosity, glow)


def write_door_assets(name: str, top: bool, frame_tex: str):
    leaf_tex = f"noclip_backrooms:block/level0/{name}"
    frame = f"noclip_backrooms:block/level0/{frame_tex}"
    bar = "noclip_backrooms:block/level0/exit_bar"

    if not top:
        model = json.loads((ASSETS / "models/block/level0/exit_door.json").read_text(encoding="utf-8"))
        model["textures"] = {"particle": frame, "leaf": leaf_tex, "frame": frame, "bar": bar}
    else:
        model = json.loads((ASSETS / "models/block/level0/exit_door_top.json").read_text(encoding="utf-8"))
        model["textures"] = {"particle": frame, "leaf": leaf_tex, "frame": frame, "bar": bar}

    model_path = ASSETS / f"models/block/level0/{name}.json"
    model_path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")

    bs = {
        "variants": {
            "axis=x": {"model": f"noclip_backrooms:block/level0/{name}"},
            "axis=z": {"model": f"noclip_backrooms:block/level0/{name}", "y": 90},
        }
    }
    (ASSETS / f"blockstates/{name}.json").write_text(json.dumps(bs, indent=2) + "\n", encoding="utf-8")

    item = {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:block/level0/{name}"}}
    (ASSETS / f"items/{name}.json").write_text(json.dumps(item, indent=2) + "\n", encoding="utf-8")


def main():
    build_frame("hub_frame", (58, 62, 66), 110.0)
    build_frame("fun_frame", (166, 148, 96), 16.0)
    build_hub_door(False)
    build_hub_door(True)
    build_fun_door(False)
    build_fun_door(True)
    write_door_assets("hub_door", False, "hub_frame")
    write_door_assets("hub_door_top", True, "hub_frame")
    write_door_assets("fun_door", False, "fun_frame")
    write_door_assets("fun_door_top", True, "fun_frame")
    print("Generated Level 0 mystery door textures, models, blockstates, and items.")


if __name__ == "__main__":
    main()
