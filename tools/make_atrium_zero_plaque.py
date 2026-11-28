"""Generates dedicated 64x64 + labPBR assets and JSON definitions for atrium_zero_plaque.

    py tools/make_atrium_zero_plaque.py

Follows docs/畫面規範/風格規範.md:
- 64x64 prop textures with labPBR _n / _s maps
- Writes both standard and realistic resourcepack textures
"""

import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
REALISTIC = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms"
NS = "noclip_backrooms:block/level4/"
SIZE = 64


def write_json(path: Path, obj: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def clamp_byte(v: float) -> int:
    return max(0, min(255, int(round(v))))


def height_to_normal(height: list[list[float]], strength: float = 2.6) -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        ym = (y - 1) % SIZE
        yp = (y + 1) % SIZE
        for x in range(SIZE):
            xm = (x - 1) % SIZE
            xp = (x + 1) % SIZE
            dx = height[y][xp] - height[y][xm]
            dy = height[yp][x] - height[ym][x]
            r = clamp_byte(128.0 - dx * (strength * 28.0))
            g = clamp_byte(128.0 - dy * (strength * 28.0))
            a = max(20, min(255, int(round(128.0 + height[y][x] * 54.0))))
            px[x, y] = (r, g, 255, a)
    return img


def save_both(name: str, color: Image.Image, normal: Image.Image, spec: Image.Image):
    for root in (ASSETS, REALISTIC):
        tex_dir = root / "textures/block/level4"
        tex_dir.mkdir(parents=True, exist_ok=True)
        color.save(tex_dir / f"{name}.png")
        normal.save(tex_dir / f"{name}_n.png")
        spec.save(tex_dir / f"{name}_s.png")


def build_plinth_textures():
    rng = random.Random(4201)
    color = Image.new("RGBA", (SIZE, SIZE))
    spec = Image.new("RGBA", (SIZE, SIZE), (48, 42, 18, 0))
    px = color.load()
    height = [[0.0] * SIZE for _ in range(SIZE)]

    for y in range(SIZE):
        for x in range(SIZE):
            n = rng.gauss(0.0, 1.0)
            border = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            shade = (-14.0 + border * 3.0) if border < 4 else 0.0
            r = clamp_byte(68.0 + n * 5.0 + shade)
            g = clamp_byte(72.0 + n * 5.0 + shade)
            b = clamp_byte(76.0 + n * 5.5 + shade)
            px[x, y] = (r, g, b, 255)
            height[y][x] = n * 0.12 - (0.18 if border < 4 else 0.0)

    normal = height_to_normal(height, strength=1.8)
    save_both("atrium_zero_plinth", color, normal, spec)


def build_plaque_textures():
    rng = random.Random(4202)
    brush = [rng.gauss(0.0, 1.0) for _ in range(SIZE)]
    color = Image.new("RGBA", (SIZE, SIZE))
    px = color.load()
    for y in range(SIZE):
        b = brush[y]
        for x in range(SIZE):
            g = rng.gauss(0.0, 0.7)
            px[x, y] = (
                clamp_byte(178.0 + b * 5.0 + g * 4.0),
                clamp_byte(142.0 + b * 4.5 + g * 3.5),
                clamp_byte(68.0 + b * 3.0 + g * 3.0),
                255,
            )

    d = ImageDraw.Draw(color)
    d.rectangle([2, 2, SIZE - 3, SIZE - 3], outline=(104, 82, 36, 255), width=2)
    d.rectangle([5, 5, SIZE - 6, SIZE - 6], outline=(212, 178, 96, 255), width=1)
    for rx, ry in [(7, 7), (SIZE - 8, 7), (7, SIZE - 8), (SIZE - 8, SIZE - 8)]:
        d.ellipse([rx - 2, ry - 2, rx + 2, ry + 2], fill=(224, 192, 112, 255), outline=(92, 70, 28, 255))

    etch = (82, 62, 24, 255)
    d.ellipse([23, 10, 40, 27], outline=etch, width=2)
    d.line([(31, 8), (31, 29)], fill=etch, width=1)
    d.line([(21, 18), (42, 18)], fill=etch, width=1)
    for y in (34, 40, 46, 52):
        w = 34 if y in (34, 46) else 26
        x0 = (SIZE - w) // 2
        d.line([(x0, y), (x0 + w, y)], fill=etch, width=2)

    lum_img = color.convert("L")
    lpx = lum_img.load()
    spec = Image.new("RGBA", (SIZE, SIZE))
    spx = spec.load()
    height = [[0.0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        b = brush[y]
        for x in range(SIZE):
            lum = lpx[x, y] / 255.0
            height[y][x] = (lum - 0.52) * 1.4 + b * 0.08
            if lum < 0.38:
                spx[x, y] = (90, 120, 0, 0)
            else:
                spx[x, y] = (196, 232, 0, 0)

    normal = height_to_normal(height, strength=2.6)
    save_both("atrium_zero_plaque", color, normal, spec)


def box_faces(tex_key: str) -> dict:
    return {f: {"uv": [0, 0, 16, 16], "texture": f"#{tex_key}"} for f in ("north", "south", "east", "west", "up", "down")}


def main():
    build_plinth_textures()
    build_plaque_textures()

    write_json(ASSETS / "models/block/level4/atrium_zero_plaque.json", {
        "textures": {
            "plinth": NS + "atrium_zero_plinth",
            "plaque": NS + "atrium_zero_plaque",
            "particle": NS + "atrium_zero_plinth"
        },
        "elements": [
            {
                "from": [2, 0, 2],
                "to": [14, 2, 14],
                "faces": box_faces("plinth")
            },
            {
                "from": [4, 2, 4],
                "to": [12, 10, 12],
                "faces": box_faces("plinth")
            },
            {
                "from": [3, 10, 3],
                "to": [13, 11.5, 13],
                "faces": box_faces("plinth")
            },
            {
                "from": [3.5, 11.5, 4],
                "to": [12.5, 13.5, 12],
                "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 12, 8]},
                "faces": box_faces("plaque")
            }
        ]
    })

    write_json(ASSETS / "blockstates/atrium_zero_plaque.json", {
        "variants": {
            "facing=north": {"model": NS + "atrium_zero_plaque"},
            "facing=east": {"model": NS + "atrium_zero_plaque", "y": 90},
            "facing=south": {"model": NS + "atrium_zero_plaque", "y": 180},
            "facing=west": {"model": NS + "atrium_zero_plaque", "y": 270}
        }
    })

    write_json(ASSETS / "models/item/atrium_zero_plaque.json", {
        "parent": NS + "atrium_zero_plaque"
    })
    write_json(ASSETS / "items/atrium_zero_plaque.json", {
        "model": {
            "type": "minecraft:model",
            "model": "noclip_backrooms:item/atrium_zero_plaque"
        }
    })
    print("Generated atrium_zero_plaque 64x64 labPBR textures and models.")


if __name__ == "__main__":
    main()
