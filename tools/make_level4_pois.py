"""Generates assets ONLY for Level 4 POI-specific blocks without touching any existing Level 4 textures or models.

    py tools/make_level4_pois.py

Follows docs/畫面規範/風格規範.md:
- 128x128 surface/cutout textures with labPBR _n / _s maps
- Transparent margins retain foreground RGB so realistic resizing produces crisp edges
"""

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

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


def save_cutout_pbr(name: str, rgba: Image.Image, normal_rgb=(128, 128, 255), smooth=24, f0=10, porosity=40):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    n_img = Image.new("RGBA", (SIZE, SIZE), (*normal_rgb, 255))
    s_img = Image.new("RGBA", (SIZE, SIZE), (smooth, f0, porosity, 255))
    n_img.save(TEXTURES / f"{name}_n.png", optimize=True)
    s_img.save(TEXTURES / f"{name}_s.png", optimize=True)


def draw_meg_chalk_sign(slashed: bool) -> Image.Image:
    # Fill transparent background RGB with chalk blue so LANCZOS/mipmaps never bleed black
    bg = (46, 132, 212, 0)
    img = Image.new("RGBA", (SIZE, SIZE), bg)
    d = ImageDraw.Draw(img)

    # Spray-painted M.E.G. arrow pointing right/forward + chalk border box
    blue = (54, 152, 236, 238)
    chalk = (224, 232, 240, 230)
    dark_blue = (30, 86, 152, 240)

    # Arrow shaft & head
    d.rectangle([20, 44, 78, 58], fill=blue)
    d.polygon([(74, 30), (108, 51), (74, 72)], fill=blue)
    d.line([(20, 44), (74, 44), (74, 30), (108, 51), (74, 72), (74, 58), (20, 58), (20, 44)], fill=dark_blue, width=2)

    # Chalk "OMEGA" / "M.E.G." stencil strokes below
    # M
    d.line([(20, 102), (20, 82), (28, 93), (36, 82), (36, 102)], fill=chalk, width=3)
    # E
    d.line([(44, 82), (44, 102)], fill=chalk, width=3)
    d.line([(44, 82), (56, 82)], fill=chalk, width=3)
    d.line([(44, 92), (54, 92)], fill=chalk, width=3)
    d.line([(44, 102), (56, 102)], fill=chalk, width=3)
    # G
    d.arc([62, 82, 78, 102], start=35, end=325, fill=chalk, width=3)
    d.line([(72, 93), (78, 93), (78, 100)], fill=chalk, width=3)
    # Safe tick mark / circle
    d.ellipse([88, 82, 108, 102], outline=blue, width=3)

    if slashed:
        # Three jagged dark claw gouges slashing diagonally across the sign
        claw_outer = (22, 18, 22, 248)
        claw_inner = (8, 6, 8, 255)
        for off in (-18, 0, 18):
            pts = [(16 + off, 18), (48 + off, 52), (76 + off, 80), (106 + off, 114)]
            d.line(pts, fill=claw_outer, width=8)
            d.line(pts, fill=claw_inner, width=4)
    return img


def draw_caution_tape() -> Image.Image:
    yellow = (232, 184, 28, 0)
    img = Image.new("RGBA", (SIZE, SIZE), yellow)
    d = ImageDraw.Draw(img)

    tape_yellow = (234, 186, 30, 246)
    tape_dark = (26, 26, 28, 250)
    tape_edge = (168, 128, 18, 246)

    # Two crisscrossed caution bands across the doorway
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

    # Diagonal top-left to bottom-right secondary strip
    d.polygon([(0, 22), (0, 42), (SIZE, 96), (SIZE, 76)], fill=tape_yellow)
    for k in range(0, SIZE, 24):
        y_top = 22 + int(k * 54 / SIZE)
        d.polygon([(k, y_top), (k + 11, y_top + 4), (k + 4, y_top + 20), (k - 7, y_top + 16)], fill=tape_dark)
    return img


def draw_office_dark_mirror(mode: str = "plain") -> Image.Image:
    # 128x128 opaque dark mirror glass: sleek anodized dark aluminum border, obsidian-blue-gray mirror,
    # diagonal cool-silver specular reflection bands. "top" and "bottom" form ONE tall 2-block silhouette.
    img = Image.new("RGBA", (SIZE, SIZE), (18, 22, 28, 255))
    d = ImageDraw.Draw(img)

    # Subtle vertical foggy reflection tint gradient & faint office horizon
    for y in range(SIZE):
        t = y / float(SIZE - 1)
        base_r = int(14 + 10 * (1.0 - t))
        base_g = int(18 + 14 * (1.0 - t))
        base_b = int(26 + 18 * (1.0 - t))
        d.line([(0, y), (SIZE - 1, y)], fill=(base_r, base_g, base_b, 255))

    # Diagonal cool-silver specular reflection bands
    d.polygon([(14, 7), (42, 7), (7, 42), (7, 14)], fill=(42, 52, 66, 255))
    d.polygon([(52, 7), (68, 7), (7, 68), (7, 52)], fill=(34, 43, 56, 255))
    d.polygon([(SIZE - 8, 56), (SIZE - 8, 76), (56, SIZE - 8), (76, SIZE - 8)], fill=(36, 45, 58, 255))

    mist_color = (46, 56, 68, 255)
    silhouette = (8, 10, 14, 255)
    if mode == "top":
        d.line([(6, SIZE - 1), (SIZE - 7, SIZE - 1)], fill=(28, 33, 41, 255), width=1)
        d.ellipse([36, 26, 92, 127], fill=mist_color)
        d.ellipse([51, 34, 77, 62], fill=silhouette)
        d.rectangle([59, 60, 69, 74], fill=silhouette)
        d.polygon([(44, 76), (64, 68), (84, 76), (86, SIZE - 1), (42, SIZE - 1)], fill=silhouette)
    elif mode == "bottom":
        d.line([(6, 0), (SIZE - 7, 0)], fill=(28, 33, 41, 255), width=1)
        d.ellipse([36, -12, 92, 118], fill=mist_color)
        d.polygon([(42, 0), (86, 0), (82, 121), (46, 121)], fill=silhouette)

    # Side frames (always present so stacked mirror panes join vertically)
    d.rectangle([0, 0, 5, SIZE - 1], fill=(22, 26, 32, 255))
    d.rectangle([0, 0, 3, SIZE - 1], fill=(32, 36, 42, 255))
    d.line([(5, 0), (5, SIZE - 1)], fill=(58, 66, 78, 255), width=1)
    d.line([(0, 0), (0, SIZE - 1)], fill=(24, 27, 33, 255), width=2)

    d.rectangle([SIZE - 6, 0, SIZE - 1, SIZE - 1], fill=(22, 26, 32, 255))
    d.rectangle([SIZE - 4, 0, SIZE - 1, SIZE - 1], fill=(32, 36, 42, 255))
    d.line([(SIZE - 6, 0), (SIZE - 6, SIZE - 1)], fill=(58, 66, 78, 255), width=1)
    d.line([(SIZE - 1, 0), (SIZE - 1, SIZE - 1)], fill=(24, 27, 33, 255), width=2)

    if mode != "bottom":
        d.rectangle([0, 0, SIZE - 1, 5], fill=(22, 26, 32, 255))
        d.rectangle([0, 0, SIZE - 1, 3], fill=(32, 36, 42, 255))
        d.line([(5, 5), (SIZE - 6, 5)], fill=(58, 66, 78, 255), width=1)
        d.line([(0, 0), (SIZE - 1, 0)], fill=(24, 27, 33, 255), width=2)
    if mode != "top":
        d.rectangle([0, SIZE - 6, SIZE - 1, SIZE - 1], fill=(22, 26, 32, 255))
        d.rectangle([0, SIZE - 4, SIZE - 1, SIZE - 1], fill=(32, 36, 42, 255))
        d.line([(5, SIZE - 6), (SIZE - 6, SIZE - 6)], fill=(58, 66, 78, 255), width=1)
        d.line([(0, SIZE - 1), (SIZE - 1, SIZE - 1)], fill=(24, 27, 33, 255), width=2)
    return img


def save_mirror_pbr(name: str, rgba: Image.Image, mode: str = "plain"):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    # Flat normal with subtle frame bevel
    n_img = Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255))
    nd = ImageDraw.Draw(n_img)
    if mode != "bottom":
        nd.rectangle([0, 0, SIZE - 1, 4], fill=(128, 108, 252, 255))
    if mode != "top":
        nd.rectangle([0, SIZE - 5, SIZE - 1, SIZE - 1], fill=(128, 148, 252, 255))
    nd.rectangle([0, 0, 4, SIZE - 1], fill=(108, 128, 252, 255))
    nd.rectangle([SIZE - 5, 0, SIZE - 1, SIZE - 1], fill=(148, 128, 252, 255))
    n_img.save(TEXTURES / f"{name}_n.png", optimize=True)
    # High-gloss mirror glass inside, matte anodized aluminum frame
    s_img = Image.new("RGBA", (SIZE, SIZE), (242, 118, 0, 255))
    sd = ImageDraw.Draw(s_img)
    sd.rectangle([0, 0, 5, SIZE - 1], fill=(110, 145, 0, 255))
    sd.rectangle([SIZE - 6, 0, SIZE - 1, SIZE - 1], fill=(110, 145, 0, 255))
    if mode != "bottom":
        sd.rectangle([0, 0, SIZE - 1, 5], fill=(110, 145, 0, 255))
    if mode != "top":
        sd.rectangle([0, SIZE - 6, SIZE - 1, SIZE - 1], fill=(110, 145, 0, 255))
    s_img.save(TEXTURES / f"{name}_s.png", optimize=True)


def draw_corrupted_fountain_liquid() -> Image.Image:
    # 128x128 turbid dark gray-crimson sludge with swirling murky red ripples & pale oily highlight veins
    img = Image.new("RGBA", (SIZE, SIZE), (56, 22, 24, 244))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            nx = (x - SIZE * 0.5) / 18.0
            nz = (y - SIZE * 0.5) / 18.0
            ring = math.sin(math.hypot(nx, nz) * 3.1 + math.sin(nx * 0.9) * 0.8)
            swirl = math.sin(x * 0.14 + math.cos(y * 0.11) * 1.4) * math.cos(y * 0.13)
            vein = max(0.0, swirl * 0.7 + ring * 0.3)
            if vein > 0.62:
                # Pale oily crimson highlight vein
                r, g, b = 148, 58, 60
            else:
                r = int(58 + 38 * (0.5 + 0.5 * ring) + 18 * vein)
                g = int(20 + 10 * (0.5 + 0.5 * ring))
                b = int(22 + 12 * (0.5 + 0.5 * ring))
            px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), 244)
    return img


def save_liquid_pbr(name: str, rgba: Image.Image):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    n_img = Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255))
    npx = n_img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            nx = int(128 + 14 * math.cos(x * 0.22 + math.sin(y * 0.15)))
            ny = int(128 + 14 * math.sin(y * 0.22 + math.cos(x * 0.15)))
            npx[x, y] = (nx, ny, 252, 255)
    n_img.save(TEXTURES / f"{name}_n.png", optimize=True)
    s_img = Image.new("RGBA", (SIZE, SIZE), (210, 45, 0, 255))
    s_img.save(TEXTURES / f"{name}_s.png", optimize=True)


def draw_executive_desk_wood() -> Image.Image:
    # 128x128 rich dark lacquered mahogany wood grain for the executive pedestal
    img = Image.new("RGBA", (SIZE, SIZE), (58, 24, 18, 255))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            grain = math.sin(y * 0.25 + math.sin(x * 0.08) * 2.2) * 0.5 + math.sin(x * 0.05 + y * 0.14) * 0.5
            r = int(58 + 16 * grain)
            g = int(24 + 8 * grain)
            b = int(17 + 6 * grain)
            px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), 255)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(38, 15, 11, 255), width=3)
    d.rectangle([10, 14, SIZE - 11, 56], outline=(42, 17, 13, 255), width=2)
    d.rectangle([10, 70, SIZE - 11, 114], outline=(42, 17, 13, 255), width=2)
    return img


def draw_executive_red_phone() -> Image.Image:
    # 128x128 glossy crimson executive hotline enamel with brass dial/cradle & lit amber/green indicator LED
    img = Image.new("RGBA", (SIZE, SIZE), (156, 22, 26, 255))
    d = ImageDraw.Draw(img)
    for y in range(SIZE):
        shade = int(18 * math.cos(y / float(SIZE) * math.pi))
        d.line([(0, y), (SIZE - 1, y)], fill=(148 + shade, 20 + shade // 4, 24 + shade // 4, 255))
    # Brass rotary dial & cradle plates
    d.ellipse([34, 38, 94, 98], fill=(198, 162, 68, 255), outline=(92, 68, 22, 255), width=3)
    d.ellipse([48, 52, 80, 84], fill=(162, 26, 30, 255), outline=(120, 92, 32, 255), width=2)
    for k in range(10):
        ang = k * (math.pi * 2 / 10)
        hx = int(64 + math.cos(ang) * 22)
        hy = int(68 + math.sin(ang) * 22)
        d.ellipse([hx - 4, hy - 4, hx + 4, hy + 4], fill=(28, 22, 22, 255))
    # Tiny lit status LEDs (amber + green)
    d.rectangle([16, 16, 28, 24], fill=(255, 196, 48, 255))
    d.rectangle([34, 16, 46, 24], fill=(74, 232, 92, 255))
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(98, 14, 16, 255), width=2)
    return img


def save_solid_pbr(name: str, rgba: Image.Image, smooth: int, f0: int):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255)).save(TEXTURES / f"{name}_n.png", optimize=True)
    Image.new("RGBA", (SIZE, SIZE), (smooth, f0, 0, 255)).save(TEXTURES / f"{name}_s.png", optimize=True)


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
    # Slate-blue polymer/steel M.E.G. tactical field crate
    side = Image.new("RGBA", (SIZE, SIZE), (34, 54, 82, 255))
    px = side.load()
    for y in range(SIZE):
        for x in range(SIZE):
            rib = 6 if (y // 8) % 2 == 0 and 24 <= x <= 104 and 32 <= y <= 108 else 0
            grain = int(math.sin(x * 0.25 + y * 0.17) * 3)
            px[x, y] = (34 + rib + grain, 54 + rib + grain, 82 + rib + grain, 255)
    sd = ImageDraw.Draw(side)
    # Dark charcoal corner bumpers
    for bx, by in [(0, 0), (SIZE - 20, 0), (0, SIZE - 20), (SIZE - 20, SIZE - 20)]:
        sd.rectangle([bx, by, bx + 19, by + 19], fill=(24, 28, 34, 255))
    sd.rectangle([0, 0, SIZE - 1, 14], fill=(28, 34, 42, 255))
    sd.rectangle([0, SIZE - 12, SIZE - 1, SIZE - 1], fill=(24, 28, 34, 255))
    # Thin yellow hazard accent stripe & metallic latch plates
    sd.rectangle([20, 16, SIZE - 21, 22], fill=(216, 174, 42, 255))
    for lx in (24, 92):
        sd.rectangle([lx, 12, lx + 12, 34], fill=(138, 148, 158, 255), outline=(44, 48, 54, 255), width=2)
    # Stenciled white M.E.G. mark
    st = (224, 234, 244, 255)
    sd.line([(40, 86), (40, 62), (48, 74), (56, 62), (56, 86)], fill=st, width=3)
    sd.line([(64, 62), (64, 86)], fill=st, width=3)
    sd.line([(64, 62), (76, 62)], fill=st, width=3)
    sd.line([(64, 74), (74, 74)], fill=st, width=3)
    sd.line([(64, 86), (76, 86)], fill=st, width=3)
    sd.arc([82, 62, 96, 86], start=35, end=325, fill=st, width=3)
    sd.line([(90, 74), (96, 74), (96, 83)], fill=st, width=3)

    n_side = make_bevel_normal(5, 22)
    s_side = Image.new("RGBA", (SIZE, SIZE), (58, 20, 12, 255))
    ssd = ImageDraw.Draw(s_side)
    for lx in (24, 92):
        ssd.rectangle([lx, 12, lx + 12, 34], fill=(175, 195, 0, 255))

    top = Image.new("RGBA", (SIZE, SIZE), (38, 60, 92, 255))
    tpx = top.load()
    for y in range(SIZE):
        for x in range(SIZE):
            rib = 7 if (20 <= x <= 108 and 18 <= y <= 110 and (x // 12) % 2 == 0) else 0
            tpx[x, y] = (38 + rib, 60 + rib, 92 + rib, 255)
    td = ImageDraw.Draw(top)
    td.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(24, 28, 34, 255), width=8)
    for bx, by in [(0, 0), (SIZE - 22, 0), (0, SIZE - 22), (SIZE - 22, SIZE - 22)]:
        td.rectangle([bx, by, bx + 21, by + 21], fill=(24, 28, 34, 255))
    td.polygon([(64, 42), (86, 64), (64, 86), (42, 64)], fill=(28, 44, 68, 255), outline=(216, 174, 42, 255))
    n_top = make_bevel_normal(6, 26)
    s_top = Image.new("RGBA", (SIZE, SIZE), (62, 22, 10, 255))
    return (side, n_side, s_side), (top, n_top, s_top)


def draw_industrial_toolbox():
    # Red enameled steel cantilever toolbox
    side = Image.new("RGBA", (SIZE, SIZE), (154, 28, 28, 255))
    px = side.load()
    for y in range(SIZE):
        for x in range(SIZE):
            scuff = int(math.sin(x * 0.19 + y * 0.31) * 5 + math.cos(x * 0.07) * 4)
            px[x, y] = (max(0, min(255, 152 + scuff)), max(0, min(255, 28 + scuff // 3)), max(0, min(255, 28 + scuff // 3)), 255)
    sd = ImageDraw.Draw(side)
    # Corner guards & recessed clasp band
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(82, 18, 18, 255), width=4)
    sd.rectangle([6, 44, SIZE - 7, 52], fill=(192, 198, 206, 255), outline=(58, 62, 68, 255), width=1)
    sd.rectangle([50, 32, 78, 66], fill=(176, 184, 192, 255), outline=(48, 52, 58, 255), width=2)
    sd.rectangle([58, 42, 70, 56], fill=(74, 78, 84, 255))
    for bx, by in [(0, 0), (SIZE - 16, 0), (0, SIZE - 16), (SIZE - 16, SIZE - 16)]:
        sd.rectangle([bx, by, bx + 15, by + 15], fill=(42, 46, 52, 255))

    n_side = make_bevel_normal(4, 24)
    nd = ImageDraw.Draw(n_side)
    nd.rectangle([6, 44, SIZE - 7, 52], fill=(128, 150, 250, 255))
    s_side = Image.new("RGBA", (SIZE, SIZE), (145, 42, 0, 255))
    ssd = ImageDraw.Draw(s_side)
    ssd.rectangle([6, 44, SIZE - 7, 52], fill=(195, 210, 0, 255))
    ssd.rectangle([50, 32, 78, 66], fill=(195, 210, 0, 255))

    top = Image.new("RGBA", (SIZE, SIZE), (160, 32, 32, 255))
    tpx = top.load()
    for y in range(SIZE):
        for x in range(SIZE):
            rib = 8 if 28 <= y <= 100 and (y // 16) % 2 == 0 else 0
            tpx[x, y] = (160 + rib, 32 + rib // 2, 32 + rib // 2, 255)
    td = ImageDraw.Draw(top)
    td.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(82, 18, 18, 255), width=5)
    td.rectangle([38, 52, 50, 76], fill=(176, 184, 192, 255), outline=(48, 52, 58, 255), width=2)
    td.rectangle([78, 52, 90, 76], fill=(176, 184, 192, 255), outline=(48, 52, 58, 255), width=2)
    n_top = make_bevel_normal(5, 24)
    s_top = Image.new("RGBA", (SIZE, SIZE), (150, 42, 0, 255))
    return (side, n_side, s_side), (top, n_top, s_top)


def draw_office_steel_safe():
    # Heavy charcoal steel office safe (front with brushed steel dial + side casing)
    side = Image.new("RGBA", (SIZE, SIZE), (48, 52, 58, 255))
    spx = side.load()
    for y in range(SIZE):
        for x in range(SIZE):
            br = int(math.sin(y * 0.7 + math.sin(x * 0.08)) * 3 + math.cos(x * 0.05) * 2)
            spx[x, y] = (48 + br, 52 + br, 58 + br, 255)
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(28, 31, 36, 255), width=5)
    for rx, ry in [(12, 12), (SIZE - 13, 12), (12, SIZE - 13), (SIZE - 13, SIZE - 13)]:
        sd.ellipse([rx - 3, ry - 3, rx + 3, ry + 3], fill=(92, 98, 106, 255), outline=(24, 26, 30, 255))
    n_side = make_bevel_normal(5, 26)
    spec_side = Image.new("RGBA", (SIZE, SIZE), (112, 165, 0, 255))

    front = side.copy()
    fd = ImageDraw.Draw(front)
    # Recessed door seam & hinge knuckles on left
    fd.rectangle([10, 10, SIZE - 11, SIZE - 11], outline=(20, 22, 26, 255), width=3)
    fd.rectangle([14, 14, SIZE - 15, SIZE - 15], outline=(70, 76, 84, 255), width=1)
    for hy in (28, 82):
        fd.rectangle([6, hy, 14, hy + 18], fill=(116, 122, 130, 255), outline=(24, 26, 30, 255), width=1)
    # Brushed steel combination dial & tick marks
    fd.ellipse([44, 34, 84, 74], fill=(168, 174, 184, 255), outline=(30, 34, 38, 255), width=3)
    fd.ellipse([54, 44, 74, 64], fill=(42, 46, 52, 255), outline=(120, 126, 134, 255), width=2)
    for k in range(12):
        ang = k * (math.pi * 2 / 12)
        x0 = int(64 + math.cos(ang) * 14)
        y0 = int(54 + math.sin(ang) * 14)
        x1 = int(64 + math.cos(ang) * 19)
        y1 = int(54 + math.sin(ang) * 19)
        fd.line([(x0, y0), (x1, y1)], fill=(28, 30, 34, 255), width=1)
    # Handle plate & tiny green/red lock LED
    fd.rectangle([86, 62, 106, 86], fill=(138, 144, 154, 255), outline=(26, 28, 32, 255), width=2)
    fd.rectangle([88, 34, 98, 40], fill=(74, 224, 110, 255))

    n_front = make_bevel_normal(5, 28)
    nfd = ImageDraw.Draw(n_front)
    nfd.rectangle([10, 10, SIZE - 11, SIZE - 11], outline=(102, 102, 250, 255), width=3)
    spec_front = Image.new("RGBA", (SIZE, SIZE), (118, 172, 0, 255))
    sfd = ImageDraw.Draw(spec_front)
    sfd.ellipse([44, 34, 84, 74], fill=(205, 225, 0, 255))
    sfd.rectangle([86, 62, 106, 86], fill=(190, 215, 0, 255))
    return (front, n_front, spec_front), (side, n_side, spec_side)


def draw_executive_broadcast_screen():
    # Glowing emerald CRT/projection broadcast monitor wall + gunmetal vented housing
    side = Image.new("RGBA", (SIZE, SIZE), (34, 38, 44, 255))
    spx = side.load()
    for y in range(SIZE):
        for x in range(SIZE):
            br = int(math.sin(y * 0.6 + math.sin(x * 0.1)) * 3)
            spx[x, y] = (34 + br, 38 + br, 44 + br, 255)
    sd = ImageDraw.Draw(side)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(20, 22, 26, 255), width=4)
    for vy in range(28, 100, 10):
        sd.rectangle([22, vy, SIZE - 23, vy + 4], fill=(16, 18, 22, 255))
    n_side = make_bevel_normal(4, 22)
    s_side = Image.new("RGBA", (SIZE, SIZE), (105, 160, 0, 255))

    front = Image.new("RGBA", (SIZE, SIZE), (24, 27, 32, 255))
    fd = ImageDraw.Draw(front)
    fd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(42, 46, 54, 255), width=3)
    fd.rectangle([7, 7, SIZE - 8, SIZE - 8], fill=(12, 34, 26, 255), outline=(62, 68, 76, 255), width=2)
    fpx = front.load()
    for y in range(10, SIZE - 10):
        scan = -6 if (y % 4 == 0) else 4
        for x in range(10, SIZE - 10):
            glow = int(10 * math.exp(-((x - 64) ** 2 + (y - 64) ** 2) / 2800.0))
            fpx[x, y] = (10, max(0, min(255, 40 + scan + glow)), max(0, min(255, 28 + scan // 2 + glow)), 255)
    # Grid lines
    for gx in range(20, SIZE - 12, 18):
        fd.line([(gx, 10), (gx, SIZE - 11)], fill=(18, 62, 46, 255), width=1)
    for gy in range(20, SIZE - 12, 18):
        fd.line([(10, gy), (SIZE - 11, gy)], fill=(18, 62, 46, 255), width=1)
    # 4 vertical signal telemetry bars on left
    for k, h_bar in enumerate((38, 62, 50, 74)):
        bx0 = 16 + k * 7
        fd.rectangle([bx0, 106 - h_bar, bx0 + 4, 106], fill=(56, 224, 142, 255))
    # Sine-wave carrier waveform across middle
    pts = []
    for x in range(44, SIZE - 14, 2):
        amp = 22.0 * math.sin((x - 44) * 0.19) * (0.65 + 0.35 * math.cos((x - 44) * 0.07))
        pts.append((x, int(64 + amp)))
    fd.line(pts, fill=(84, 255, 168, 255), width=3)
    pts2 = [(px_x, int(64 + (py_y - 64) * 0.45)) for px_x, py_y in pts]
    fd.line(pts2, fill=(238, 198, 72, 255), width=1)
    # LIVE status indicator in top-right corner
    fd.ellipse([102, 15, 112, 25], fill=(236, 48, 52, 255), outline=(120, 18, 20, 255), width=1)

    n_front = make_bevel_normal(7, 26)
    s_front = Image.new("RGBA", (SIZE, SIZE), (230, 85, 0, 255))
    sfd = ImageDraw.Draw(s_front)
    sfd.rectangle([0, 0, SIZE - 1, 7], fill=(95, 145, 0, 255))
    sfd.rectangle([0, SIZE - 8, SIZE - 1, SIZE - 1], fill=(95, 145, 0, 255))
    sfd.rectangle([0, 0, 7, SIZE - 1], fill=(95, 145, 0, 255))
    sfd.rectangle([SIZE - 8, 0, SIZE - 1, SIZE - 1], fill=(95, 145, 0, 255))
    return (front, n_front, s_front), (side, n_side, s_side)


def main():
    # 1. meg_chalk_sign (normal + slashed)
    save_cutout_pbr("meg_chalk_sign", draw_meg_chalk_sign(False), smooth=18, f0=8, porosity=65)
    save_cutout_pbr("meg_chalk_sign_slashed", draw_meg_chalk_sign(True), smooth=16, f0=8, porosity=70)
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
    save_cutout_pbr("caution_tape_barrier", draw_caution_tape(), smooth=28, f0=10, porosity=30)
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
        save_mirror_pbr(tex_name, draw_office_dark_mirror(mode), mode)
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
    save_liquid_pbr("corrupted_fountain_liquid", draw_corrupted_fountain_liquid())
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
    save_solid_pbr("executive_desk_wood", draw_executive_desk_wood(), smooth=110, f0=18)
    save_solid_pbr("executive_red_phone", draw_executive_red_phone(), smooth=210, f0=42)
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
    print("Generated Level 4 Batch 1..4 + Route A/B POI assets complete")


if __name__ == "__main__":
    main()
