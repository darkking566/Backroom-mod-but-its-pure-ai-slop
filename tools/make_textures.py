"""Draws every block texture from scratch. Edit the colours, then run:

    python tools/make_textures.py

Needs Pillow. Also writes docs/textures.png, a tiled preview.
"""

import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block"
SIZE = 16


def texture(seed, pixel):
    rng = random.Random(seed)
    image = Image.new("RGB", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            color, grain = pixel(x, y, rng)
            shift = rng.randint(-grain, grain)
            image.putpixel((x, y), tuple(max(0, min(255, c + shift)) for c in color))
    return image


def wallpaper(x, y, rng):
    # Every period divides 16, so the pattern carries across blocks without seams.
    if x % 8 == 0:
        return (190, 169, 85), 2  # stripe
    if x % 8 == 1:
        return (219, 201, 120), 2  # highlight beside the stripe
    column = x % 8 - 2
    shift = 4 if x >= 8 else 0  # stagger neighbouring columns
    if column in range(1, 5) and (y + shift) % 8 == [2, 1, 1, 2][column - 1]:
        return (193, 173, 91), 2  # chevron
    return (209, 191, 109), 2


def baseboard(x, y, rng):
    if y == 12:
        return (176, 158, 96), 1  # lit top edge
    if y in (13, 14):
        return (139, 120, 66), 2
    if y == 15:
        return (92, 77, 43), 2  # dirt line at the floor
    return wallpaper(x, y, rng)


def carpet(x, y, rng):
    roll = rng.random()
    if roll < 0.14:
        return (138, 117, 67), 3  # damp fibre
    if roll > 0.90:
        return (168, 146, 91), 3  # dry fibre
    return (154, 132, 78), 4


def ceiling_tile(x, y, rng):
    if x == 0 or y == 0:
        return (164, 157, 130), 1  # grid
    if x == 1 or y == 1:
        return (222, 216, 188), 1  # bevel
    if rng.random() < 0.07:
        return (183, 176, 147), 2  # pinhole
    return (211, 204, 174), 2


def light(x, y, rng):
    # No frame on the short sides: lights are always placed in pairs along X.
    if y in (0, 15):
        return (164, 157, 130), 1
    if y in (1, 14):
        return (205, 198, 166), 1
    if y in (4, 5, 10, 11):
        return (255, 255, 240), 0  # tube
    if y in (3, 6, 9, 12):
        return (255, 250, 208), 0  # glow around the tube
    return (243, 233, 176), 1


def broken_light(x, y, rng):
    if y in (0, 15):
        return (164, 157, 130), 1
    if y in (1, 14):
        return (150, 144, 120), 1
    if y in (4, 5, 10, 11):
        blackened = x < 3 or x > 12
        return ((62, 58, 47) if blackened else (134, 130, 110)), 3
    return (96, 91, 74), 2


def pale_wallpaper(x, y, rng):
    if x % 8 == 0:
        return (206, 203, 189), 2
    if x % 8 == 1:
        return (230, 228, 216), 2
    column = x % 8 - 2
    shift = 4 if x >= 8 else 0
    if column in range(1, 5) and (y + shift) % 8 == [2, 1, 1, 2][column - 1]:
        return (212, 209, 196), 2
    return (222, 219, 206), 2


def pale_baseboard(x, y, rng):
    if y == 12:
        return (195, 191, 178), 1
    if y in (13, 14):
        return (168, 164, 151), 2
    if y == 15:
        return (122, 118, 106), 2
    return pale_wallpaper(x, y, rng)


def red_wallpaper(x, y, rng):
    if x % 8 == 0:
        return (112, 26, 24), 2
    if x % 8 == 1:
        return (145, 42, 38), 2
    column = x % 8 - 2
    shift = 4 if x >= 8 else 0
    if column in range(1, 5) and (y + shift) % 8 == [2, 1, 1, 2][column - 1]:
        return (118, 30, 28), 2
    return (132, 34, 31), 2


def red_baseboard(x, y, rng):
    if y == 12:
        return (102, 24, 22), 1
    if y in (13, 14):
        return (76, 18, 17), 2
    if y == 15:
        return (48, 12, 12), 2
    return red_wallpaper(x, y, rng)


def peeling_red_wallpaper(x, y, rng):
    # Irregular torn patches revealing dark red underneath the yellow wallpaper.
    torn = (
        (3 <= x <= 11 and 4 <= y <= 12 and (x + y * 2) % 5 != 0)
        or (1 <= x <= 5 and 9 <= y <= 14)
        or (10 <= x <= 14 and 2 <= y <= 6)
    )
    if torn:
        return red_wallpaper(x, y, rng)
    # Slightly darkened curled paper edge beside torn patches.
    edge = (
        (2 <= x <= 12 and 3 <= y <= 13)
        or (0 <= x <= 6 and 8 <= y <= 15)
        or (9 <= x <= 15 and 1 <= y <= 7)
    )
    color, grain = wallpaper(x, y, rng)
    if edge:
        return tuple(max(0, c - 28) for c in color), grain + 1
    return color, grain


def red_light(x, y, rng):
    if y in (0, 15):
        return (110, 42, 38), 1
    if y in (1, 14):
        return (132, 52, 48), 1
    if y in (4, 5, 10, 11):
        return (235, 76, 68), 0
    if y in (3, 6, 9, 12):
        return (195, 52, 46), 0
    return (156, 38, 35), 1


def thick_carpet(x, y, rng):
    roll = rng.random()
    if roll < 0.22:
        return (98, 79, 44), 4
    if roll > 0.88:
        return (126, 104, 62), 4
    return (112, 91, 52), 5


def exit_light(x, y, rng):
    if y in (0, 15):
        return (58, 92, 66), 1
    if y in (1, 14):
        return (78, 124, 88), 1
    if y in (4, 5, 10, 11):
        return (196, 255, 208), 0
    if y in (3, 6, 9, 12):
        return (122, 236, 148), 0
    return (62, 176, 92), 1


def exit_door(x, y, rng):
    if x in (0, 15):
        return (52, 78, 58), 1  # frame
    if y == 15:
        return (40, 46, 42), 1  # threshold
    if y in (1, 2) and 2 <= x <= 13:
        return ((214, 218, 206) if y == 1 else (128, 134, 124)), 1  # push bar
    if x in (1, 14):
        return (128, 182, 138), 1
    if y >= 11:
        return (132, 160, 136), 2  # kick plate
    return (86, 148, 100), 2


def exit_door_top(x, y, rng):
    if x in (0, 15) or y == 0:
        return (52, 78, 58), 1  # frame
    if 5 <= x <= 10 and 3 <= y <= 10:
        if x in (5, 10) or y in (3, 10):
            return (44, 52, 46), 1
        return (236, 255, 242), 1  # daylight in the window
    if x in (1, 14):
        return (128, 182, 138), 1
    return (86, 148, 100), 2


# 3x5 letters, one column apart
EXIT_TEXT = ["### # # ### ###", "#   # #  #   # ", "###  #   #   # ", "#   # #  #   # ", "### # # ###  # "]


def exit_sign(x, y, rng):
    if y in (2, 13):
        return (40, 46, 42), 0  # housing
    if y < 2 or y > 13:
        return (60, 68, 62), 1
    if 5 <= y <= 9 and x < 15 and EXIT_TEXT[y - 5][x] == "#":
        return (238, 255, 240), 0
    return (24, 150, 66), 1


TEXTURES = {
    "wallpaper": wallpaper,
    "wallpaper_baseboard": baseboard,
    "carpet": carpet,
    "ceiling_tile": ceiling_tile,
    "fluorescent_light": light,
    "broken_fluorescent_light": broken_light,
    "pale_wallpaper": pale_wallpaper,
    "pale_wallpaper_baseboard": pale_baseboard,
    "red_wallpaper": red_wallpaper,
    "red_wallpaper_baseboard": red_baseboard,
    "peeling_red_wallpaper": peeling_red_wallpaper,
    "red_fluorescent_light": red_light,
    "thick_carpet": thick_carpet,
    "exit_light": exit_light,
    "exit_door": exit_door,
    "exit_door_top": exit_door_top,
    "exit_sign": exit_sign,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    images = {name: texture(name, pixel) for name, pixel in TEXTURES.items()}
    for name, image in images.items():
        image.save(OUT / f"{name}.png")

    scale, tiles, gap, columns = 6, 3, 12, 3
    side = SIZE * scale * tiles
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * side + (columns + 1) * gap, rows * side + (rows + 1) * gap), (30, 30, 30))
    for i, (name, image) in enumerate(images.items()):
        left, top = gap + (i % columns) * (side + gap), gap + (i // columns) * (side + gap)
        for row in range(tiles):
            for column in range(tiles):
                # Show baseboards as they appear in game: one row under plain wallpaper.
                base_pair = {
                    "wallpaper_baseboard": "wallpaper",
                    "pale_wallpaper_baseboard": "pale_wallpaper",
                    "red_wallpaper_baseboard": "red_wallpaper",
                }.get(name)
                tile_img = images[base_pair] if (base_pair and row < tiles - 1) else image
                tile = tile_img.resize((SIZE * scale, SIZE * scale), Image.Resampling.NEAREST)
                sheet.paste(tile, (left + column * SIZE * scale, top + row * SIZE * scale))
    sheet.save(ROOT / "docs/textures.png")


if __name__ == "__main__":
    main()
