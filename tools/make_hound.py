"""Makes the Hound's extra textures and its spawn egg item.

The Hound itself (model, clips, texture) comes from Antigravity's Blender export in art/monsters/hound/;
its spec and texture are copied as they are to assets/noclip_backrooms/monsters/hound.json and
textures/entity/hound.png. This script only adds:
  textures/entity/hound_eyes.png              the eyes alone, drawn full bright over the model
  textures/item/monsters/hound_spawn_egg.png  the spawn egg, plus its item model and item definition
Needs Pillow:  py tools/make_hound.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
SPEC = ASSETS / "monsters/hound.json"
TEXTURE = ASSETS / "textures/entity/hound.png"


def eyes():
    """Only the eye region of the atlas, pale and a little yellow; everything else black and clear."""
    spec = json.loads(SPEC.read_text(encoding="utf8"))
    region = spec["texture_atlas"]["regions"]["eye"]
    source = Image.open(TEXTURE).convert("RGBA")
    out = Image.new("RGBA", source.size, (0, 0, 0, 0))
    u, v, w, h = region["u"], region["v"], region["w"], region["h"]
    for y in range(v, v + h):
        for x in range(u, u + w):
            r, g, b, _ = source.getpixel((x, y))
            grey = (r + g + b) / 3 / 255
            out.putpixel((x, y), (int(235 * grey), int(225 * grey), int(170 * grey), 255))
    out.save(ASSETS / "textures/entity/hound_eyes.png", optimize=True)


def spawn_egg():
    """A 16x16 egg: grey hide, a curtain of black hair over the top, two pale eyes and a red mouth."""
    rng = random.Random(7)
    hide, hide_dark, hide_light = (112, 103, 94), (80, 73, 67), (140, 131, 121)
    hair, hair_light = (22, 20, 19), (48, 44, 41)
    outline = (16, 14, 13)
    inside = set()
    for y in range(16):
        for x in range(16):
            t = (y - 1) / 14
            rx = 5.3 * (0.72 + 0.28 * min(1.0, t * 1.6))
            if ((x + 0.5 - 8) / rx) ** 2 + ((y + 0.5 - 8.6) / 6.9) ** 2 <= 1:
                inside.add((x, y))
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    strands = {x: (5 if 6 <= x <= 9 else 9) + rng.randrange(4) for x in range(16)}
    for x, y in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            colour = outline
        elif y <= strands[x] and not (5 <= x <= 10 and 6 <= y <= 10):
            colour = hair_light if (x + y) % 4 == 0 else hair
        else:
            shade = (x - 6) * 0.08 + (y - 9) * 0.05 + rng.uniform(-0.08, 0.08)
            colour = hide_light if shade < -0.15 else hide_dark if shade > 0.2 else hide
        image.putpixel((x, y), colour + (255,))
    for x, y, colour in ((6, 8, (236, 230, 200)), (9, 8, (236, 230, 200)),
                         (6, 10, (120, 22, 22)), (7, 10, (150, 30, 28)), (8, 10, (150, 30, 28)), (9, 10, (120, 22, 22)),
                         (7, 11, (226, 214, 180)), (8, 11, (120, 22, 22))):
        image.putpixel((x, y), colour + (255,))
    folder = ASSETS / "textures/item/monsters"
    folder.mkdir(parents=True, exist_ok=True)
    image.save(folder / "hound_spawn_egg.png", optimize=True)
    write_json(ASSETS / "models/item/monsters/hound_spawn_egg.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "noclip_backrooms:item/monsters/hound_spawn_egg"}})
    write_json(ASSETS / "items/hound_spawn_egg.json",
               {"model": {"type": "minecraft:model", "model": "noclip_backrooms:item/monsters/hound_spawn_egg"}})


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    eyes()
    spawn_egg()
    print("hound textures written")
