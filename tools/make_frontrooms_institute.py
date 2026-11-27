"""The M.E.G. institute's new look (docs/企劃書/1-遊戲玩法/README.md, "研究所改版"), after the research facility at the
end of the film (A24's Backrooms, 2026): white walls over a mustard wainscot, an acoustic tile ceiling with troffers,
vinyl tile floors, terrazzo in the halls, dark one-way observation windows, a hospital wing in mint with an orange rail;
outside, exposed-aggregate precast panels, bronze-tinted ribbon windows, asphalt, sodium lamps.

Every surface starts from a CC0 photo (ambientCG, cached in tools/.cache/ambientcg, fetched once when missing),
brought to the wanted colour and given the grime of a place in use. Re-skins five of the institute's old blocks (same
IDs: institute_wall, institute_wainscot, institute_floor, institute_ceiling, institute_light) and writes the new
blocks' textures (128 px, labPBR _n and _s; glass with alpha), models, blockstates and item models. Run after
tools/make_frontrooms.py, which would otherwise paint the five old ones back.

Run: python tools/make_frontrooms_institute.py
"""
import glob
import io
import json
import os
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_textures as mt  # noqa: E402
from make_textures import X, Y, SIZE, clouds, flat, noise, paint, step, tint  # noqa: E402
from make_frontrooms import ASSETS, MODELS, NS, TEXTURES, box, cube_all, grime, variant, write_json  # noqa: E402

CACHE = Path(__file__).resolve().parent / ".cache" / "ambientcg"
PHOTOS = {
    "plaster": "PaintedPlaster017", "terrazzo": "Terrazzo004", "asphalt": "Asphalt031", "ceiling": "OfficeCeiling001",
    "tiles": "Tiles107", "aggregate": "Concrete049", "concrete": "Concrete034", "paint": "PaintedMetal004",
}


def fetch(asset):
    """Downloads an ambientCG material (CC0) into the cache, as the other texture tools do."""
    def get(url):
        return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "noclip-backrooms-texture-tool"}), timeout=300).read()

    answer = json.loads(get(f"https://ambientcg.com/api/v2/full_json?id={asset}&include=downloadData"))
    link = None
    for found in answer.get("foundAssets", []):
        for category in found.get("downloadFolders", {}).values():
            for download in category.get("downloadFiletypeCategories", {}).get("zip", {}).get("downloads", []):
                if download.get("attribute") == "1K-JPG":
                    link = download.get("downloadLink") or download.get("fullDownloadPath")
    if not link or not re.match(r"^https://(www\.)?ambientcg\.com/", link):
        raise SystemExit(f"ambientCG has no 1K download for {asset}")
    folder = CACHE / asset / "1K-JPG"
    folder.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(get(link))) as archive:
        for member in archive.namelist():
            name = os.path.basename(member)
            if re.fullmatch(r"[A-Za-z0-9_\-]+\.(jpg|png)", name):
                (folder / name).write_bytes(archive.read(member))


def photo(key, part=1.0, at=(0.0, 0.0), channel="Color"):
    """A square piece of a photo (part of its width, from a corner given as fractions) at the texture's size, 0..1."""
    asset = PHOTOS[key]
    found = glob.glob(str(CACHE / asset / "*" / f"*_{channel}.jpg"))
    if not found:
        fetch(asset)
        found = glob.glob(str(CACHE / asset / "*" / f"*_{channel}.jpg"))
    image = Image.open(found[0]).convert("RGB")
    side = int(min(image.width, image.height) * part)
    left, top = int(at[0] * (image.width - side)), int(at[1] * (image.height - side))
    image = image.crop((left, top, left + side, top + side)).resize((SIZE, SIZE), Image.LANCZOS)
    return np.asarray(image, dtype=float) / 255.0


def surface(color, key, part=1.0, at=(0.0, 0.0), contrast=0.6, smooth=0.3, f0=10, porosity=0, bump=0.6):
    """A material of one colour carrying a photo's light and dark (and its bumps), not its own colour."""
    picture = photo(key, part, at)
    light = picture.mean(axis=2)
    detail = light / max(light.mean(), 1e-3) - 1
    m = flat(color, smooth, f0, porosity)
    m.color *= (1 + detail * contrast)[..., None]
    m.height += detail * 4 * bump
    return m


def save_rgba(name, image):
    Image.fromarray(np.clip(np.rint(image), 0, 255).astype("uint8"), "RGBA").save(TEXTURES / f"{name}.png", optimize=True)


# Inside -----------------------------------------------------------------------------------------------------------------

def white_wall(seed):
    m = surface((232, 229, 220), "plaster", 0.5, (0.2, 0.3), contrast=0.35, smooth=0.35, porosity=20)
    grime(m, seed, 0.07)
    scuff = step(0.55, 0.9, noise(seed + 3, 24, 6)) * step(96, 128, Y)
    tint(m, scuff * 0.3, (0.88, 0.86, 0.82))
    return m


def wainscot(seed, color=(206, 150, 26), rail=None):
    m = surface(color, "plaster", 0.5, (0.6, 0.1), contrast=0.3, smooth=0.45, porosity=10)
    # Kicked, scraped and touched where people walk past.
    scrapes = step(0.62, 0.95, noise(seed + 5, 40, 8)) * step(70, 112, Y)
    tint(m, scrapes * 0.35, (0.82, 0.80, 0.76))
    grime(m, seed, 0.08)
    # The vinyl cove base along the floor.
    paint(m, box(0, 116, 128, 128, 0.6), (64, 64, 62), height=1.2, smooth=0.5, f0=20)
    paint(m, box(0, 115, 128, 117, 0.4), (40, 40, 38), height=0.6)
    if rail:
        # A wooden-looking handrail on brackets at hip height (the hospital hall in the film).
        paint(m, box(0, 6, 128, 20, 0.8), rail, height=3, smooth=0.55, f0=30)
        paint(m, box(0, 18, 128, 21, 0.5), tuple(c * 0.6 for c in rail), height=1)
        for bx in (30, 94):
            paint(m, box(bx, 20, bx + 6, 28, 0.5), (150, 150, 146), height=2, f0=180, smooth=0.6)
    return m


def vinyl_floor(seed):
    # Vinyl composition tile, two by two to a block, each tile a shade off its neighbours; polished and walked on.
    m = surface((206, 203, 194), "tiles", 1 / 3, (0.33, 0.33), contrast=0.25, smooth=0.6, f0=20)
    m.color *= (1 + clouds(seed, 6) * 0.05)[..., None]
    for tx in (0, 1):
        for ty in (0, 1):
            shade = [1.0, 0.95, 0.97, 1.02][tx * 2 + ty]
            tint(m, box(tx * 64, ty * 64, tx * 64 + 64, ty * 64 + 64, 0.5), (shade,) * 3)
    lines = np.minimum(np.minimum(X % 64, 63 - X % 64), np.minimum(Y % 64, 63 - Y % 64))
    paint(m, 1 - step(0, 1.2, lines), (150, 148, 142), height=-0.8)
    speck = step(0.82, 0.95, noise(seed + 9, 64)) * 0.5
    tint(m, speck, (0.8, 0.79, 0.76))
    grime(m, seed, 0.1)
    return m


def ceiling(seed):
    # Acoustic tiles in a white grid, two by two to a block.
    m = surface((224, 222, 214), "ceiling", 1 / 3, (0.0, 0.0), contrast=0.6, smooth=0.1, porosity=60, bump=0.4)
    pits = step(0.6, 0.9, noise(seed + 2, 96)) * 0.12
    tint(m, pits, (0.86, 0.86, 0.84))
    grime(m, seed, 0.06)
    return m


def troffer(seed):
    m = flat((214, 214, 210), 0.6, 160)
    paint(m, box(6, 6, 122, 122, 0.6), (190, 190, 186), height=-1)
    lens = box(12, 12, 116, 116, 0.6)
    prism = 0.92 + 0.08 * ((X // 3 + Y // 3) % 2)
    paint(m, lens, (252, 250, 240), smooth=0.8, glow=1.0)
    tint(m, lens * (1 - prism), (0.9, 0.9, 0.9))
    paint(m, box(62, 12, 66, 116, 0.5), (200, 200, 196), height=0.5, glow=0)
    return m


def terrazzo(seed):
    m = surface((198, 196, 190), "terrazzo", 0.5, (0.4, 0.6), contrast=0.55, smooth=0.75, f0=30, bump=0.2)
    m.color *= (1 + clouds(seed, 4) * 0.03)[..., None]
    grime(m, seed, 0.05)
    return m


def observation_window():
    """One-way glass in a white aluminium frame: tinted and half see-through, so from the lit room the dark room behind
    shows only darkness, and from the dark room the lit one shows."""
    image = np.zeros((SIZE, SIZE, 4))
    sheen = 0.08 * step(0, 128, X + Y * 0.6) + 0.04 * clouds(41, 3)
    image[..., 0], image[..., 1], image[..., 2] = 16 + sheen * 120, 19 + sheen * 120, 23 + sheen * 130
    image[..., 3] = 150
    frame = 1 - box(5, 5, 123, 123, 0.4)
    for c, v in enumerate((214, 212, 206)):
        image[..., c] = image[..., c] * (1 - frame) + v * frame
    image[..., 3] = np.maximum(image[..., 3], frame * 255)
    return image


def containment_glass():
    """Wired safety glass in a bolted steel frame, faintly blue."""
    image = np.zeros((SIZE, SIZE, 4))
    image[..., 0], image[..., 1], image[..., 2], image[..., 3] = 176, 214, 222, 64
    wire = (1 - step(0, 1.1, np.minimum(X % 16, 16 - X % 16))) + (1 - step(0, 1.1, np.minimum(Y % 16, 16 - Y % 16)))
    wire = np.clip(wire, 0, 1)
    for c, v in enumerate((120, 128, 130)):
        image[..., c] = image[..., c] * (1 - wire) + v * wire
    image[..., 3] = np.maximum(image[..., 3], wire * 150)
    frame = 1 - box(4, 4, 124, 124, 0.4)
    for c, v in enumerate((70, 74, 78)):
        image[..., c] = image[..., c] * (1 - frame) + v * frame
    image[..., 3] = np.maximum(image[..., 3], frame * 255)
    for px, py in ((2, 2), (125, 2), (2, 125), (125, 125), (64, 2), (64, 125), (2, 64), (125, 64)):
        bolt = 1 - step(0.8, 1.6, np.hypot(X - px, Y - py))
        for c, v in enumerate((150, 154, 156)):
            image[..., c] = image[..., c] * (1 - bolt) + v * bolt
    return image


# Outside ----------------------------------------------------------------------------------------------------------------

def precast(seed):
    m = surface((182, 174, 158), "aggregate", 0.6, (0.3, 0.3), contrast=0.32, smooth=0.1, porosity=90, bump=0.8)
    streak = step(0.35, 0.95, noise(seed + 7, 48, 3)) * step(0, 128, Y) * 0.22
    tint(m, streak, (0.8, 0.78, 0.74))
    grime(m, seed, 0.08)
    return m


def ribbon_window():
    """Bronze-tinted glass between anodised sills: dark by day, a glimpse of the rooms when they are lit."""
    image = np.zeros((SIZE, SIZE, 4))
    sky = 0.14 * (1 - Y / 128) + 0.05 * clouds(43, 3)
    image[..., 0], image[..., 1], image[..., 2] = 46 + sky * 160, 50 + sky * 160, 52 + sky * 170
    image[..., 3] = 205
    frame = (1 - box(0, 4, 128, 124, 0.4)) + (1 - box(3, 0, 128, 128, 0.4))
    frame = np.clip(frame, 0, 1)
    for c, v in enumerate((92, 82, 68)):
        image[..., c] = image[..., c] * (1 - frame) + v * frame
    image[..., 3] = np.maximum(image[..., 3], frame * 255)
    return image


def steel(seed):
    m = surface((60, 62, 64), "paint", 0.5, (0.5, 0.5), contrast=0.4, smooth=0.35, f0=40)
    grime(m, seed, 0.1)
    return m


def sign(seed):
    m = flat((26, 42, 90), 0.7, 40)
    m.color *= (1 + clouds(seed, 3)[..., None] * 0.04)
    return m


def asphalt(seed, line=False):
    m = surface((74, 74, 76), "asphalt", 0.5, (0.5, 0.2), contrast=0.7, smooth=0.15, porosity=70, bump=0.6)
    m.color *= (1 + clouds(seed, 4)[..., None] * 0.06)
    if line:
        # Road paint, worn where the tyres go.
        worn = step(0.25, 0.75, noise(seed + 4, 32, 16))
        stripe = box(44, 0, 84, 128, 1.2) * (0.6 + 0.4 * worn)
        paint(m, stripe, (226, 222, 206), height=0.3, smooth=0.3)
    return m


def sidewalk(seed):
    m = surface((178, 176, 170), "concrete", 0.6, (0.2, 0.6), contrast=0.5, smooth=0.15, porosity=70)
    lines = np.minimum(np.minimum(X, 127 - X), np.minimum(Y, 127 - Y))
    paint(m, 1 - step(0, 1.6, lines), (128, 126, 120), height=-1.2)
    grime(m, seed, 0.1)
    return m


def sodium_lamp(seed):
    m = flat((70, 72, 74), 0.4, 40)
    lens = box(14, 14, 114, 114, 1.0)
    paint(m, lens, (255, 184, 86), smooth=0.8, glow=1.0)
    tint(m, lens * (0.08 * clouds(seed, 3)), (0.9, 0.85, 0.8))
    return m


TEXTURES_SET = {
    "institute_wall": lambda: white_wall(31),
    "institute_wainscot": lambda: wainscot(32),
    "institute_floor": lambda: vinyl_floor(33),
    "institute_ceiling": lambda: ceiling(34),
    "institute_light": lambda: troffer(35),
    "lobby_terrazzo": lambda: terrazzo(36),
    "mint_wall": lambda: white_wall_tinted(37, (172, 212, 196)),
    "mint_wainscot": lambda: wainscot(38, (162, 204, 188), rail=(206, 112, 46)),
    "precast_panel": lambda: precast(39),
    "steel_frame": lambda: steel(40),
    "facade_sign": lambda: sign(41),
    "campus_asphalt": lambda: asphalt(42),
    "campus_line": lambda: asphalt(42, line=True),
    "campus_sidewalk": lambda: sidewalk(43),
    "sodium_lamp": lambda: sodium_lamp(44),
}


def white_wall_tinted(seed, color):
    m = surface(color, "plaster", 0.5, (0.7, 0.6), contrast=0.35, smooth=0.35, porosity=20)
    grime(m, seed, 0.07)
    return m


NEW_BLOCKS = ["lobby_terrazzo", "mint_wall", "mint_wainscot", "observation_window", "containment_glass", "precast_panel",
              "ribbon_window", "steel_frame", "facade_sign", "campus_asphalt", "campus_line", "campus_sidewalk"]


def shaped_models():
    """The thin steel pole, and the street lamp's head: a flat housing with its orange lens underneath."""
    def side(uv):
        return {d: {"texture": "#s", "uv": uv} for d in ("north", "south", "east", "west")}

    pole = {"parent": "minecraft:block/block", "textures": {"particle": NS + "steel_frame", "s": NS + "steel_frame"},
            "elements": [{"from": [6, 0, 6], "to": [10, 16, 10], "faces": side([6, 0, 10, 16])
                          | {"up": {"texture": "#s", "uv": [6, 6, 10, 10]}, "down": {"texture": "#s", "uv": [6, 6, 10, 10]}}}]}
    head = {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "sodium_lamp", "s": NS + "steel_frame", "l": NS + "sodium_lamp"},
            "elements": [{"from": [6, 0, 6], "to": [10, 10, 10], "faces": side([6, 6, 10, 16])},
                         {"from": [1, 10, 1], "to": [15, 14, 15], "faces": side([1, 2, 15, 6])
                          | {"up": {"texture": "#s", "uv": [1, 1, 15, 15]}, "down": {"texture": "#l", "uv": [1, 1, 15, 15]}}}]}
    for name, model in (("steel_pole", pole), ("sodium_lamp", head)):
        write_json(MODELS / f"{name}.json", model)
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": variant(name)}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES
    for name, make in TEXTURES_SET.items():
        mt.save(name, make())
    save_rgba("observation_window", observation_window())
    save_rgba("containment_glass", containment_glass())
    save_rgba("ribbon_window", ribbon_window())
    for name in NEW_BLOCKS:
        write_json(MODELS / f"{name}.json", cube_all(name))
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": variant(name)}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})
    shaped_models()
    preview()
    print("Institute textures written.")


def preview():
    names = list(TEXTURES_SET) + ["observation_window", "containment_glass", "ribbon_window"]
    cols, side = 6, 192
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * side, rows * side), (40, 40, 40))
    for i, name in enumerate(names):
        tile = Image.open(TEXTURES / f"{name}.png").convert("RGBA").resize((side - 8, side - 8), Image.NEAREST)
        back = Image.new("RGBA", tile.size, (150, 170, 190, 255))
        sheet.paste(Image.alpha_composite(back, tile).convert("RGB"), ((i % cols) * side + 4, (i // cols) * side + 4))
    out = Path(__file__).resolve().parent.parent / "docs/企劃書/1-遊戲玩法/圖片/institute_textures.png"
    sheet.save(out, optimize=True)


if __name__ == "__main__":
    main()
