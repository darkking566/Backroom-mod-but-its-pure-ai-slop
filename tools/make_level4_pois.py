"""Generates assets ONLY for Level 4 POI-specific blocks without touching any existing Level 4 textures or models.

    py tools/make_level4_pois.py

Follows docs/look/STYLE_GUIDE.md:
- 128x128 surface/cutout textures with labPBR _n / _s maps
- Transparent margins retain foreground RGB so realistic resizing produces crisp edges
"""

import json
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


def draw_office_dark_mirror() -> Image.Image:
    # 128x128 opaque dark mirror glass: sleek anodized dark aluminum border, obsidian-blue-gray mirror,
    # diagonal cool-silver specular reflection bands, and a faint ghostly silhouette in pale mist.
    img = Image.new("RGBA", (SIZE, SIZE), (18, 22, 28, 255))
    d = ImageDraw.Draw(img)

    # Outer anodized dark aluminum frame & inner bevel
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], fill=(32, 36, 42, 255))
    d.rectangle([4, 4, SIZE - 5, SIZE - 5], fill=(22, 26, 32, 255))
    d.rectangle([6, 6, SIZE - 7, SIZE - 7], fill=(15, 19, 26, 255))

    # Subtle vertical foggy reflection tint gradient & faint office horizon
    for y in range(7, SIZE - 7):
        t = (y - 7) / (SIZE - 14)
        base_r = int(14 + 10 * (1.0 - t))
        base_g = int(18 + 14 * (1.0 - t))
        base_b = int(26 + 18 * (1.0 - t))
        d.line([(7, y), (SIZE - 8, y)], fill=(base_r, base_g, base_b, 255))

    # Diagonal cool-silver specular reflection bands
    d.polygon([(14, 7), (42, 7), (7, 42), (7, 14)], fill=(42, 52, 66, 255))
    d.polygon([(52, 7), (68, 7), (7, 68), (7, 52)], fill=(34, 43, 56, 255))
    d.polygon([(SIZE - 8, 56), (SIZE - 8, 76), (56, SIZE - 8), (76, SIZE - 8)], fill=(36, 45, 58, 255))

    # Faint ghostly silhouette standing in pale mist near center-right
    mist_color = (48, 58, 70, 255)
    silhouette = (9, 11, 15, 255)
    d.ellipse([66, 38, 98, 92], fill=mist_color)
    d.ellipse([75, 44, 89, 58], fill=silhouette)
    d.polygon([(70, 60), (94, 60), (98, 108), (66, 108)], fill=silhouette)

    # Re-draw crisp inner highlight & shadow border around mirror glass
    d.rectangle([5, 5, SIZE - 6, SIZE - 6], outline=(58, 66, 78, 255), width=1)
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(24, 27, 33, 255), width=2)
    return img


def save_mirror_pbr(name: str, rgba: Image.Image):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rgba.save(TEXTURES / f"{name}.png", optimize=True)
    # Flat normal with subtle frame bevel
    n_img = Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255))
    nd = ImageDraw.Draw(n_img)
    nd.rectangle([0, 0, SIZE - 1, 4], fill=(128, 108, 252, 255))
    nd.rectangle([0, SIZE - 5, SIZE - 1, SIZE - 1], fill=(128, 148, 252, 255))
    nd.rectangle([0, 0, 4, SIZE - 1], fill=(108, 128, 252, 255))
    nd.rectangle([SIZE - 5, 0, SIZE - 1, SIZE - 1], fill=(148, 128, 252, 255))
    n_img.save(TEXTURES / f"{name}_n.png", optimize=True)
    # High-gloss mirror glass inside, matte anodized aluminum frame
    s_img = Image.new("RGBA", (SIZE, SIZE), (242, 118, 0, 255))
    sd = ImageDraw.Draw(s_img)
    sd.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=(110, 145, 0, 255), width=6)
    s_img.save(TEXTURES / f"{name}_s.png", optimize=True)


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

    # 3. office_dark_mirror (Batch 2 POI #9 - unbreakable opaque dark reflective mirror)
    save_mirror_pbr("office_dark_mirror", draw_office_dark_mirror())
    write_json(MODELS / "office_dark_mirror.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": NS + "office_dark_mirror"}
    })
    write_json(BLOCKSTATES / "office_dark_mirror.json", {
        "variants": {"": {"model": NS + "office_dark_mirror"}}
    })
    write_item_files("office_dark_mirror")
    print("Generated Level 4 Batch 1+2 POI assets: meg_chalk_sign, caution_tape_barrier, office_dark_mirror")


if __name__ == "__main__":
    main()
