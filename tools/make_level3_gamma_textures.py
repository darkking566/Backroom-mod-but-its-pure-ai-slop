"""Generate Level 3 Base Gamma assets (titanium_wall, barbed_wire, electric_fence,
sanctum_rubbing, frantic_note) plus missing realistic labPBR textures for Gamma's
blocks (daylight_panel, checkpoint_frame, checkpoint_bar, titanium_wall, barbed_wire,
electric_fence) at 128px (whole-block surfaces) / 64px (props) per STYLE_GUIDE.

Run: uv run --no-project --with pillow --with numpy python tools/make_level3_gamma_textures.py
"""
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
REAL_L3 = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level3"
TEX_L3 = ASSETS / "textures/block/level3"
TEX_ITEM = ASSETS / "textures/item/level3"


def write_json(path: Path, data: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def save_pbr(out_dir: Path, name: str, rgb: np.ndarray, alpha: np.ndarray | None,
             height: np.ndarray, smooth: np.ndarray | float, f0: int,
             porosity: int = 10, emissive: np.ndarray | int = 0, bump: float = 3.0):
    out_dir.mkdir(parents=True, exist_ok=True)
    h, w = rgb.shape[:2]
    rgb_u8 = np.clip(rgb * 255.0, 0, 255).astype(np.uint8)
    if alpha is not None:
        a_u8 = np.clip(alpha * 255.0, 0, 255).astype(np.uint8)
        Image.fromarray(np.dstack([rgb_u8, a_u8]), "RGBA").save(out_dir / f"{name}.png")
    else:
        Image.fromarray(rgb_u8, "RGB").save(out_dir / f"{name}.png")

    # labPBR normal map: R=nx, G=ny, B=AO, A=height
    gx = (np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)) * bump
    gy = (np.roll(height, -1, axis=0) - np.roll(height, 1, axis=0)) * bump
    nz = np.ones_like(height)
    inv = 1.0 / np.sqrt(gx * gx + gy * gy + nz * nz)
    nx = (-gx * inv * 0.5 + 0.5) * 255.0
    ny = (-gy * inv * 0.5 + 0.5) * 255.0
    ao = np.clip(0.85 + 0.15 * height, 0.0, 1.0) * 255.0
    ha = np.clip(height, 0.0, 1.0) * 255.0
    n_img = np.stack([nx, ny, ao, ha], axis=-1).astype(np.uint8)
    Image.fromarray(n_img, "RGBA").save(out_dir / f"{name}_n.png")

    # labPBR specular map: R=smoothness, G=F0/metal, B=porosity, A=emissive (255 = no emission in labPBR 1.3)
    sm = np.full((h, w), smooth, dtype=np.float32) if isinstance(smooth, (int, float)) else smooth
    r_ch = np.clip(sm * 255.0, 0, 255).astype(np.uint8)
    g_ch = np.full((h, w), f0, dtype=np.uint8)
    b_ch = np.full((h, w), porosity, dtype=np.uint8)
    if isinstance(emissive, int):
        a_ch = np.full((h, w), 255 if emissive == 0 else emissive, dtype=np.uint8)
    else:
        a_ch = np.where(emissive > 0, np.clip(emissive, 1, 254), 255).astype(np.uint8)
    s_img = np.stack([r_ch, g_ch, b_ch, a_ch], axis=-1)
    Image.fromarray(s_img, "RGBA").save(out_dir / f"{name}_s.png")


def noise(size: int, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    return rng.random((size, size), dtype=np.float32)


def make_titanium(size: int):
    rng = np.random.default_rng(3401)
    base = np.array([0.46, 0.49, 0.53], dtype=np.float32)
    # Horizontal brushed titanium grain
    streak = rng.normal(0.0, 0.025, (size, max(4, size // 8))).astype(np.float32)
    streak = np.repeat(streak, size // streak.shape[1], axis=1)[:, :size]
    grain = rng.normal(0.0, 0.015, (size, size)).astype(np.float32)
    rgb = np.clip(base[None, None, :] + (streak + grain)[:, :, None], 0.0, 1.0)
    height = np.full((size, size), 0.72, dtype=np.float32) + streak * 0.4
    # Welded plate seams & corner rivets
    b = max(1, size // 32)
    height[:b, :] = 0.25
    height[-b:, :] = 0.25
    height[:, :b] = 0.25
    height[:, -b:] = 0.25
    height[size // 2 - b // 2:size // 2 + max(1, b // 2), :] = 0.30
    rgb[height < 0.35] *= 0.65
    # Corner bolts
    r = max(1, size // 24)
    for ry in (size // 8, size // 2 - size // 8, size // 2 + size // 8, size - size // 8):
        for rx in (size // 8, size - size // 8):
            yy, xx = np.ogrid[:size, :size]
            mask = (xx - rx) ** 2 + (yy - ry) ** 2 <= r * r
            height[mask] = 0.92
            rgb[mask] = np.array([0.34, 0.37, 0.41], dtype=np.float32)
    return rgb, height


def make_barbed_wire(size: int):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    w = max(1, size // 16)
    steel = (118, 124, 130, 255)
    dark = (68, 72, 78, 255)
    # Intertwined razor wire coils
    for offset in (size // 4, size // 2, (3 * size) // 4):
        pts1 = [(x, int(offset + np.sin(x * 0.25 + offset) * (size * 0.14))) for x in range(0, size, max(1, size // 16))]
        pts2 = [(x, int(offset - np.cos(x * 0.22 + offset) * (size * 0.12))) for x in range(0, size, max(1, size // 16))]
        draw.line(pts1, fill=dark, width=w + 1)
        draw.line(pts1, fill=steel, width=w)
        draw.line(pts2, fill=steel, width=w)
        for px, py in pts1[::2]:
            draw.line([(px - w * 2, py - w * 2), (px + w * 2, py + w * 2)], fill=steel, width=max(1, w // 2))
            draw.line([(px - w * 2, py + w * 2), (px + w * 2, py - w * 2)], fill=dark, width=max(1, w // 2))
    arr = np.asarray(img, dtype=np.float32) / 255.0
    rgb, alpha = arr[:, :, :3], arr[:, :, 3]
    height = np.where(alpha > 0.1, 0.75, 0.0).astype(np.float32)
    return rgb, alpha, height


def make_electric_fence(size: int):
    rng = np.random.default_rng(3402)
    rgb = np.full((size, size, 3), [0.24, 0.27, 0.30], dtype=np.float32) + rng.normal(0, 0.02, (size, size, 3)).astype(np.float32)
    height = np.full((size, size), 0.65, dtype=np.float32)
    emissive = np.zeros((size, size), dtype=np.int32)
    # Ceramic blue high-voltage insulator bands & subtle glowing arc tips
    band1 = (size // 4, size // 4 + max(2, size // 10))
    band2 = ((3 * size) // 4 - max(2, size // 10), (3 * size) // 4)
    for y0, y1 in (band1, band2):
        rgb[y0:y1, :] = [0.22, 0.68, 0.92]
        height[y0:y1, :] = 0.88
        emissive[y0:y1, :] = 140
    # Hazard yellow-black stripe near bottom
    sy0, sy1 = size - max(2, size // 8), size
    for x in range(size):
        if ((x // max(1, size // 8)) % 2) == 0:
            rgb[sy0:sy1, x] = [0.85, 0.70, 0.14]
        else:
            rgb[sy0:sy1, x] = [0.12, 0.12, 0.14]
    return np.clip(rgb, 0.0, 1.0), height, emissive


def make_item_icons():
    TEX_ITEM.mkdir(parents=True, exist_ok=True)
    # 1. Sanctum rubbing (16x16 charcoal parchment rubbing)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 1, 13, 14], fill=(214, 198, 164, 255), outline=(120, 102, 76, 255))
    for y in (4, 6, 8, 10, 12):
        d.line([(4, y), (11, y)], fill=(58, 52, 46, 255))
    d.point([(6, 5), (9, 7), (5, 9), (10, 11)], fill=(40, 36, 32, 255))
    img.save(TEX_ITEM / "sanctum_rubbing.png")

    # 2. Frantic note (16x16 crumpled scribbled paper)
    img2 = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(img2)
    d2.polygon([(3, 2), (12, 1), (14, 13), (2, 14)], fill=(235, 228, 206, 255), outline=(140, 130, 112, 255))
    for y, x2 in ((4, 11), (6, 10), (8, 12), (10, 9), (12, 11)):
        d2.line([(4, y), (x2, y)], fill=(48, 42, 42, 255))
    d2.line([(5, 7), (10, 11)], fill=(138, 34, 34, 255))
    img2.save(TEX_ITEM / "frantic_note.png")


def make_realistic_from_base(name: str, target_size: int, smooth: float, f0: int, emissive: int = 0):
    src = Image.open(TEX_L3 / f"{name}.png").convert("RGBA").resize((target_size, target_size), Image.LANCZOS)
    arr = np.asarray(src, dtype=np.float32) / 255.0
    rgb = arr[:, :, :3]
    alpha = arr[:, :, 3] if np.any(arr[:, :, 3] < 0.99) else None
    rng = np.random.default_rng(abs(hash(name)) % (2**31))
    micro = rng.normal(0.0, 0.018, (target_size, target_size)).astype(np.float32)
    rgb = np.clip(rgb + micro[:, :, None], 0.0, 1.0)
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    height = np.clip(0.45 + 0.4 * lum + micro, 0.0, 1.0)
    save_pbr(REAL_L3, name, rgb, alpha, height, smooth, f0, porosity=8, emissive=emissive)


def main():
    # Default 16px block textures + labPBR
    rgb_t, h_t = make_titanium(16)
    save_pbr(TEX_L3, "gamma_titanium_wall", rgb_t, None, h_t, smooth=0.62, f0=230, porosity=0)
    rgb_b, a_b, h_b = make_barbed_wire(16)
    save_pbr(TEX_L3, "gamma_razor_wire", rgb_b, a_b, h_b, smooth=0.55, f0=230, porosity=0)
    rgb_e, h_e, em_e = make_electric_fence(16)
    save_pbr(TEX_L3, "gamma_electric_fence", rgb_e, None, h_e, smooth=0.68, f0=230, porosity=0, emissive=em_e)

    # Realistic 128px / 64px textures + missing daylight_panel / checkpoint_frame / checkpoint_bar
    rgb_t128, h_t128 = make_titanium(128)
    save_pbr(REAL_L3, "gamma_titanium_wall", rgb_t128, None, h_t128, smooth=0.66, f0=230, porosity=0)
    rgb_b64, a_b64, h_b64 = make_barbed_wire(64)
    save_pbr(REAL_L3, "gamma_razor_wire", rgb_b64, a_b64, h_b64, smooth=0.58, f0=230, porosity=0)
    rgb_e64, h_e64, em_e64 = make_electric_fence(64)
    save_pbr(REAL_L3, "gamma_electric_fence", rgb_e64, None, h_e64, smooth=0.70, f0=230, porosity=0, emissive=em_e64)

    make_realistic_from_base("daylight_panel", 128, smooth=0.82, f0=18, emissive=220)
    make_realistic_from_base("checkpoint_frame", 64, smooth=0.58, f0=230, emissive=0)
    make_realistic_from_base("checkpoint_bar", 64, smooth=0.74, f0=230, emissive=120)

    make_item_icons()

    # Blockstates, block models, item definitions
    write_json(ASSETS / "blockstates/gamma_titanium_wall.json", {
        "variants": {"": {"model": "noclip_backrooms:block/level3/gamma_titanium_wall"}}
    })
    write_json(ASSETS / "models/block/level3/gamma_titanium_wall.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "noclip_backrooms:block/level3/gamma_titanium_wall"}
    })
    write_json(ASSETS / "items/gamma_titanium_wall.json", {
        "model": {"type": "minecraft:model", "model": "noclip_backrooms:block/level3/gamma_titanium_wall"}
    })

    write_json(ASSETS / "blockstates/gamma_razor_wire.json", {
        "variants": {"": {"model": "noclip_backrooms:block/level3/gamma_razor_wire"}}
    })
    write_json(ASSETS / "models/block/level3/gamma_razor_wire.json", {
        "parent": "minecraft:block/cross",
        "textures": {"cross": "noclip_backrooms:block/level3/gamma_razor_wire"}
    })
    write_json(ASSETS / "items/gamma_razor_wire.json", {
        "model": {"type": "minecraft:model", "model": "noclip_backrooms:block/level3/gamma_razor_wire"}
    })

    write_json(ASSETS / "blockstates/gamma_electric_fence.json", {
        "variants": {
            "axis=x": {"model": "noclip_backrooms:block/level3/gamma_electric_fence"},
            "axis=z": {"model": "noclip_backrooms:block/level3/gamma_electric_fence", "y": 90}
        }
    })
    # Reuse the bars geometry with gamma_electric_fence texture
    bars_model = json.loads((ASSETS / "models/block/level3/white_bars.json").read_text(encoding="utf-8"))
    bars_model["textures"] = {
        "particle": "noclip_backrooms:block/level3/gamma_electric_fence",
        "bars": "noclip_backrooms:block/level3/gamma_electric_fence",
        "lock": "noclip_backrooms:block/level3/gamma_electric_fence"
    }
    write_json(ASSETS / "models/block/level3/gamma_electric_fence.json", bars_model)
    write_json(ASSETS / "items/gamma_electric_fence.json", {
        "model": {"type": "minecraft:model", "model": "noclip_backrooms:block/level3/gamma_electric_fence"}
    })

    for item in ("sanctum_rubbing", "frantic_note"):
        write_json(ASSETS / f"models/item/level3/{item}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"noclip_backrooms:item/level3/{item}"}
        })
        write_json(ASSETS / f"items/{item}.json", {
            "model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/level3/{item}"}
        })

    print("Level 3 Base Gamma assets & realistic textures written.")


if __name__ == "__main__":
    main()
