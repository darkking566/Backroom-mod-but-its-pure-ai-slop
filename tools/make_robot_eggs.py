"""Makes the three robot spawn eggs (texture, item model, item definition). Needs Pillow:  py tools/make_robot_eggs.py"""
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/noclip_backrooms"
# kind: (body, dark, accent) -- cleaner is warning-stripe yellow, chef is white with an amber eye, servant is rusted with a red eye
EGGS = {"cleaner": ((196, 200, 204), (120, 124, 130), (230, 190, 40)),
        "chef": ((230, 230, 226), (150, 150, 146), (240, 170, 40)),
        "servant": ((120, 96, 76), (70, 56, 46), (200, 40, 36))}


def egg(body, dark, accent):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            t = (y - 1) / 14
            rx = 5.3 * (0.72 + 0.28 * min(1.0, t * 1.6))
            if ((x + 0.5 - 8) / rx) ** 2 + ((y + 0.5 - 8.6) / 6.9) ** 2 <= 1:
                image.putpixel((x, y), (dark if x > 9 else body) + (255,))
    # outline: any opaque pixel with a transparent 4-neighbour
    out = image.copy()
    for y in range(16):
        for x in range(16):
            if image.getpixel((x, y))[3] and any(
                    not (0 <= x + dx < 16 and 0 <= y + dy < 16) or image.getpixel((x + dx, y + dy))[3] == 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), (30, 30, 34, 255))
    for x, y in ((6, 7), (9, 7), (6, 8), (9, 8)):  # visor eyes
        out.putpixel((x, y), accent + (255,))
    for x in range(5, 11):  # stripe across the belly
        out.putpixel((x, 11), accent + (255,))
    return out


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    for kind, colours in EGGS.items():
        name = f"robot_{kind}_spawn_egg"
        folder = ASSETS / "textures/item/monsters"
        folder.mkdir(parents=True, exist_ok=True)
        egg(*colours).save(folder / f"{name}.png", optimize=True)
        write_json(ASSETS / f"models/item/monsters/{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"noclip_backrooms:item/monsters/{name}"}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/monsters/{name}"}})
    print("robot spawn eggs written")
