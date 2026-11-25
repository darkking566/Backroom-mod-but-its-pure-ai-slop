"""Draws the money items (core.economy): a lost old coin and a tape of static, 32x32 item icons, with their models.

Run from the repository root:  py -I tools/make_economy_items.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
S = 32


def save(name, image):
    tex = ASSETS / "textures/item/core" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    image.save(tex)
    model = ASSETS / "models/item/core" / f"{name}.json"
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({"parent": "minecraft:item/generated",
                                 "textures": {"layer0": f"noclip_backrooms:item/core/{name}"}}, indent=2) + "\n", encoding="utf8")
    item = ASSETS / "items" / f"{name}.json"
    item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/core/{name}"}}, indent=2) + "\n",
                    encoding="utf8")


def coin():
    """A worn brass coin seen at a slight tilt: a thick rim, a rubbed face with a square hole, scratches."""
    rnd = random.Random(7)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    cx, cy, rx, ry = 15.5, 16.5, 12.5, 11.0
    for y in range(S):
        for x in range(S):
            # The edge (thickness) below the face.
            ex, ey = (x - cx) / rx, (y - cy - 2.0) / ry
            fx, fy = (x - cx) / rx, (y - cy) / ry
            face = fx * fx + fy * fy
            edge = ex * ex + ey * ey
            if face <= 1.0:
                light = 0.5 - 0.35 * fx - 0.45 * fy
                rim = face > 0.68
                base = (166, 128, 62) if not rim else (130, 96, 44)
                if rim and fy < 0:
                    base = (188, 150, 80)
                shade = 0.78 + 0.32 * light + rnd.uniform(-0.06, 0.06)
                # Green-brown tarnish in the hollows.
                if not rim and rnd.random() < 0.16:
                    base = (112, 104, 62)
                c = tuple(max(0, min(255, int(v * shade))) for v in base)
                px[x, y] = c + (255,)
            elif edge <= 1.0 and y > cy:
                px[x, y] = (92, 66, 30, 255) if (x + y) % 3 else (78, 56, 26, 255)
    # A square hole in the middle, like an old cash coin.
    for y in range(14, 19):
        for x in range(13, 18):
            px[x, y] = (0, 0, 0, 0)
    for x in range(12, 19):
        for y in (13, 19):
            if px[x, y][3]:
                px[x, y] = (110, 80, 36, 255)
    for y in range(13, 20):
        for x in (12, 18):
            if px[x, y][3]:
                px[x, y] = (110, 80, 36, 255)
    # Characters worn almost smooth: four small marks round the hole.
    for x, y in ((15, 9), (15, 23), (8, 16), (22, 16)):
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            r, g, b, a = px[x + dx, y + dy]
            px[x + dx, y + dy] = (int(r * 0.72), int(g * 0.72), int(b * 0.7), a)
    # Scratches.
    for _ in range(3):
        a = rnd.uniform(0, math.pi)
        x0, y0 = rnd.uniform(8, 22), rnd.uniform(9, 22)
        for t in range(5):
            x, y = int(x0 + math.cos(a) * t), int(y0 + math.sin(a) * t)
            if 0 <= x < S and 0 <= y < S and px[x, y][3] and not (13 <= x <= 17 and 14 <= y <= 18):
                r, g, b, al = px[x, y]
                px[x, y] = (min(255, r + 40), min(255, g + 36), min(255, b + 24), al)
    return img


def tape():
    """A cassette with a faded yellow label, two reels and a strip of television static across the window."""
    rnd = random.Random(11)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    x0, y0, x1, y1 = 2, 7, 29, 25
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            corner = (x in (x0, x1)) and (y in (y0, y1))
            if corner:
                continue
            border = x in (x0, x1) or y in (y0, y1)
            v = 34 + rnd.randint(-4, 4)
            px[x, y] = (v + 6, v + 6, v + 8, 255) if not border else (16, 16, 18, 255)
    # The label, scuffed.
    for y in range(9, 18):
        for x in range(5, 27):
            v = rnd.randint(-10, 8)
            px[x, y] = (214 + v, 186 + v, 96 + v, 255)
    for x in range(7, 25):
        px[x, 11] = (120, 96, 50, 255) if x % 5 else (214, 186, 96, 255)
    for x in range(7, 18):
        px[x, 15] = (150, 122, 66, 255)
    # The window with the reels, and static on the tape between them.
    for y in range(18, 23):
        for x in range(9, 23):
            g = rnd.randint(60, 210)
            px[x, y] = (g, g, g, 255)
    for cx in (12, 19):
        for y in range(18, 23):
            for x in range(cx - 2, cx + 3):
                d = (x - cx) ** 2 + (y - 20) ** 2
                if d <= 5:
                    px[x, y] = (226, 226, 220, 255) if d <= 1 else (92, 84, 70, 255)
    # Screw holes and the bottom edge.
    for x, y in ((4, 9), (27, 9), (4, 23), (27, 23)):
        px[x, y] = (96, 96, 100, 255)
    for x in range(7, 25):
        px[x, 24] = (24, 24, 26, 255)
    return img


if __name__ == "__main__":
    save("lost_coin", coin())
    save("static_tape", tape())
    sheet = Image.new("RGBA", (S * 2 * 4 + 12, S * 4 + 8), (40, 40, 40, 255))
    for i, name in enumerate(("lost_coin", "static_tape")):
        icon = Image.open(ASSETS / "textures/item/core" / f"{name}.png").resize((S * 4, S * 4), Image.NEAREST)
        sheet.alpha_composite(icon, (4 + i * (S * 4 + 4), 4))
    out = ROOT / "docs/guild/items.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print("wrote", out)
