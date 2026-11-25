"""Claude's trial models: eight items and props redrawn in the levels' style, to compare with the current ones.

Box models (Minecraft's own way of drawing), materials at 64 pixels per block (docs/look/STYLE_GUIDE.md), built from
CC0 photo materials (ambientCG, cached in tools/.cache/ambientcg) plus wear and grime, each with labPBR _n / _s. Dull,
worn colours; light only where something really gives light (a fluorescent tube, a CRT), and dim.

Everything goes into the built-in resource pack "claude_trial" (off by default; core.look.TrialPack), which replaces
the eight items' models while it is on. Gemini's models are not touched. The pose in hand, on the ground and in the
inventory is copied from Gemini's models, so only the look differs.

Run from the repository root:  py -I tools/make_trial_items.py
"""
import hashlib
import json
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
sys.dont_write_bytecode = True
import make_level2_realistic as R  # noqa: E402  (its CC0 photo downloader and cache)

ROOT = TOOLS.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
PACK = ROOT / "src/main/resources/resourcepacks/claude_trial"
OUT = PACK / "assets/noclip_backrooms"
S = 64

# ---------------------------------------------------------------- texture helpers


def noise(seed, blur, size=S):
    rng = np.random.default_rng(seed)
    a = ndimage.gaussian_filter(rng.standard_normal((size, size)), blur, mode="wrap")
    a -= a.min()
    return a / max(a.max(), 1e-6)


def photo(asset, crop, seed):
    """A square of a CC0 photo material, scaled down to the texture."""
    files = R.source(asset)
    image = Image.open(files["Color"]).convert("RGB")
    rnd = random.Random(seed)
    x, y = rnd.randint(0, image.width - crop), rnd.randint(0, image.height - crop)
    return np.asarray(image.crop((x, y, x + crop, y + crop)).resize((S, S), Image.LANCZOS), np.float32) / 255


def lum(rgb):
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def tint(rgb, colour, keep=0.35):
    """The photo's light and dark, in this colour; `keep` of the photo's own colour stays."""
    l = lum(rgb)[..., None]
    target = np.array(colour, np.float32) / 255 * (l / max(float(l.mean()), 1e-3))
    return np.clip(target * (1 - keep) + rgb * keep, 0, 1)


def grime(rgb, seed, amount=0.35, edges=0.25):
    """Low dirt in blotches, and darker edges where hands and dust collect."""
    dirt = noise(seed, 5) * noise(seed + 1, 2)
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    edge = np.minimum(np.minimum(x, 1 - x), np.minimum(y, 1 - y))
    rim = np.clip(1 - edge / 0.12, 0, 1)
    shade = 1 - amount * dirt - edges * rim
    return np.clip(rgb * shade[..., None], 0, 1)


def scratches(rgb, seed, count=10, colour=(0.75, 0.74, 0.70), length=10):
    rnd = random.Random(seed)
    out = rgb.copy()
    for _ in range(count):
        x, y = rnd.uniform(0, S), rnd.uniform(0, S)
        a = rnd.uniform(0, np.pi)
        for t in range(rnd.randint(length // 2, length)):
            px, py = int(x + np.cos(a) * t) % S, int(y + np.sin(a) * t) % S
            out[py, px] = out[py, px] * 0.45 + np.array(colour) * 0.55
    return out


def rgba(rgb, alpha=None):
    a = np.ones(rgb.shape[:2], np.float32) if alpha is None else alpha
    return np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)])


# ---------------------------------------------------------------- materials


def m_rust_pipe():
    base = tint(photo("Rust004", 300, 1), (120, 72, 44), 0.5)
    bare = tint(photo("Metal032", 300, 2), (92, 90, 86), 0.2)
    mask = (noise(3, 3) > 0.58)[..., None]
    return rgba(grime(np.where(mask, bare, base), 4, 0.3)), (0.18, 230, 0.6)


def m_galv_steel():
    base = tint(photo("Metal032", 260, 5), (128, 128, 122), 0.3)
    spots = tint(photo("Rust004", 200, 6), (110, 70, 42), 0.4)
    mask = (noise(7, 2.5) > 0.72)[..., None]
    return rgba(scratches(grime(np.where(mask, spots, base), 8, 0.35), 9)), (0.42, 230, 0.2)


def m_bare_steel():
    base = tint(photo("Metal032", 200, 10), (150, 148, 142), 0.25)
    return rgba(scratches(grime(base, 11, 0.25), 12, 14)), (0.55, 230, 0.1)


def m_red_paint():
    paint = np.ones((S, S, 3), np.float32) * np.array([118, 34, 28]) / 255
    paint *= (0.85 + 0.25 * noise(13, 1.2))[..., None]
    rust = tint(photo("Rust004", 220, 14), (104, 62, 38), 0.5)
    steel = tint(photo("Metal032", 200, 15), (98, 96, 92), 0.2)
    chips = noise(16, 1.6)
    out = np.where((chips > 0.62)[..., None], steel, paint)
    out = np.where((chips > 0.74)[..., None], rust, out)
    return rgba(scratches(grime(out, 17, 0.35), 18, 16, (0.55, 0.52, 0.48))), (0.32, 30, 0.4)


def m_tape_black():
    v = 0.12 + 0.05 * noise(19, 1.0)
    y = np.arange(S)[:, None] * np.ones((1, S))
    v = v + 0.06 * (y % 6 == 0) + 0.03 * (y % 6 == 1)
    rgb = np.dstack([v, v, v * 1.05])
    return rgba(grime(rgb, 20, 0.2, 0.1)), (0.5, 20, 0.0)


def m_duct_tape():
    base = tint(photo("Fabric028", 160, 21), (150, 152, 150), 0.1)
    y = np.arange(S)[:, None] * np.ones((1, S))
    base = base * (1 - 0.12 * (y % 8 == 0))[..., None]
    return rgba(grime(base, 22, 0.35, 0.15)), (0.45, 30, 0.1)


def m_old_wood():
    base = tint(photo("Wood051", 320, 23), (112, 86, 60), 0.4)
    return rgba(scratches(grime(base, 24, 0.4), 25, 6, (0.6, 0.5, 0.38))), (0.18, 20, 0.5)


def m_scrap_wood():
    base = tint(photo("Wood051", 220, 26), (96, 76, 56), 0.4)
    out = grime(base, 27, 0.45, 0.35)
    for x, y in ((14, 20), (44, 40), (30, 52)):
        out[y - 1:y + 2, x - 1:x + 2] = (0.16, 0.15, 0.14)
    return rgba(out), (0.12, 20, 0.6)


def m_wallpaper_strip():
    """A torn strip of Level 0's own wallpaper (from the realistic pack), yellowed and soiled."""
    src = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level0/wallpaper.png"
    image = np.asarray(Image.open(src).convert("RGB").resize((S, S), Image.LANCZOS), np.float32) / 255
    out = grime(image * 0.9, 28, 0.4, 0.2)
    y, x = np.mgrid[0:S, 0:S]
    torn = noise(29, 1.0) > 0.86
    out[torn & ((y < 6) | (y > S - 7))] = (0.55, 0.48, 0.33)
    return rgba(out), (0.1, 25, 0.5)


def m_fluoro_tube():
    y = np.mgrid[0:S, 0:S][0] / (S - 1)
    v = 0.86 - 0.10 * noise(30, 4)
    rgb = np.dstack([v, v * 0.97, v * 0.86])
    # Blackened ends, dust.
    ends = (y < 0.08) | (y > 0.92)
    rgb[ends] *= 0.45
    rgb *= (1 - 0.25 * noise(31, 1.5) * noise(32, 6))[..., None]
    return rgba(rgb), (0.75, 40, 0.0, 0.55)


def m_lamp_metal():
    base = tint(photo("Metal049A", 220, 33), (176, 172, 160), 0.2)
    return rgba(scratches(grime(base, 34, 0.45, 0.3), 35, 10, (0.45, 0.42, 0.38))), (0.35, 40, 0.2)


def m_wire_black():
    v = 0.10 + 0.04 * noise(36, 0.8)
    return rgba(np.dstack([v, v, v])), (0.4, 20, 0.0)


def m_copper_winding():
    y = np.arange(S)[:, None] * np.ones((1, S))
    band = 0.75 + 0.25 * np.cos(y / 2.0 * np.pi)
    copper = np.array([176, 100, 58]) / 255 * band[..., None]
    copper = copper * (0.85 + 0.25 * lum(photo("Metal032", 160, 37))[..., None])
    green = np.array([96, 120, 92]) / 255
    patina = (noise(38, 3) > 0.7)[..., None]
    out = np.where(patina, copper * 0.55 + green * 0.45, copper)
    return rgba(grime(out, 39, 0.3, 0.1)), (0.6, 234, 0.0)


def m_copper_bright():
    v = 0.85 + 0.15 * noise(40, 1.0)
    rgb = np.dstack([v * 0.82, v * 0.5, v * 0.3])
    return rgba(rgb), (0.75, 234, 0.0)


def m_spool():
    base = tint(photo("Wood051", 400, 41), (122, 98, 70), 0.3)
    out = grime(base, 42, 0.35, 0.3)
    out[20:30, 8:40] = out[20:30, 8:40] * 0.4 + np.array([0.80, 0.76, 0.62]) * 0.6
    for x in range(10, 38, 3):
        out[24, x] = (0.30, 0.28, 0.24)
    return rgba(out), (0.15, 20, 0.5)


def m_glass_milk():
    """A dirty bottle: clear glass above, almond-milk below a meniscus, sediment at the bottom."""
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    milk = np.array([0.88, 0.83, 0.70]) * (0.9 + 0.1 * noise(43, 3))[..., None]
    glass = np.array([0.70, 0.74, 0.70]) * (0.95 + 0.05 * noise(44, 2))[..., None]
    level = 0.30 + 0.02 * np.sin(x * 9)
    rgb = np.where((y > level)[..., None], milk, glass)
    rgb = np.where((y > 0.9)[..., None], rgb * 0.8, rgb)
    rgb = np.where((np.abs(y - level) < 0.02)[..., None], rgb * 1.08, rgb)
    alpha = np.where(y > level, 0.94, 0.55)
    streak = np.abs(x - 0.25) < 0.04
    rgb[streak] = rgb[streak] * 0.6 + 0.4
    rgb = grime(rgb, 45, 0.25, 0.1)
    return rgba(rgb, alpha), (0.85, 40, 0.0)


def m_cork():
    base = np.ones((S, S, 3)) * np.array([0.62, 0.48, 0.32])
    base *= (0.8 + 0.3 * noise(46, 0.7))[..., None]
    specks = noise(47, 0.5) > 0.8
    base[specks] *= 0.55
    return rgba(grime(base, 48, 0.2)), (0.1, 20, 0.7)


def m_label():
    paper = np.ones((S, S, 3)) * np.array([0.82, 0.76, 0.60])
    paper *= (0.9 + 0.12 * noise(49, 2))[..., None]
    rnd = random.Random(50)
    for row in (18, 28, 38):
        x = 8
        while x < 54:
            w = rnd.randint(2, 6)
            yy = row + rnd.randint(-1, 1)
            paper[yy:yy + 2, x:x + w] = (0.28, 0.24, 0.20)
            x += w + rnd.randint(1, 3)
    stain = noise(51, 6) > 0.7
    paper[stain] *= np.array([0.86, 0.80, 0.70])
    return rgba(grime(paper, 52, 0.3, 0.3)), (0.08, 20, 0.6)


def m_twine():
    y, x = np.mgrid[0:S, 0:S]
    v = 0.55 + 0.15 * np.sin((x + y) * 1.3)
    rgb = np.dstack([v * 0.92, v * 0.78, v * 0.55])
    return rgba(rgb), (0.1, 20, 0.6)


# Props (block textures).


def m_office_fabric():
    base = tint(photo("Fabric028", 180, 53), (58, 63, 74), 0.15)
    stains = noise(54, 5) > 0.72
    base[stains] *= 0.72
    worn = noise(55, 3) > 0.8
    base[worn] = base[worn] * 0.7 + 0.3 * np.array([0.42, 0.44, 0.48])
    return rgba(grime(base, 56, 0.25, 0.3)), (0.06, 20, 0.7)


def m_dark_plastic():
    v = 0.16 + 0.04 * noise(57, 2)
    rgb = np.dstack([v, v, v * 1.04])
    return rgba(scratches(grime(rgb, 58, 0.2, 0.15), 59, 8, (0.32, 0.32, 0.32))), (0.45, 40, 0.0)


def m_chrome_worn():
    base = tint(photo("Metal032", 160, 60), (160, 160, 156), 0.25)
    pits = noise(61, 1.2) > 0.78
    base[pits] = base[pits] * 0.5 + np.array([0.36, 0.24, 0.16]) * 0.5
    return rgba(grime(base, 62, 0.2)), (0.7, 230, 0.0)


def m_beige_plastic():
    y = np.mgrid[0:S, 0:S][0] / (S - 1)
    base = np.ones((S, S, 3)) * np.array([0.78, 0.74, 0.60])
    base *= (0.92 + 0.06 * noise(63, 4))[..., None]
    base = base * (1 - 0.10 * y)[..., None] + np.array([0.04, 0.02, -0.04]) * y[..., None]
    return rgba(scratches(grime(base, 64, 0.35, 0.3), 65, 6, (0.62, 0.58, 0.48))), (0.35, 40, 0.0)


def m_crt_screen():
    y, x = np.mgrid[0:S, 0:S] / (S - 1)
    v = 0.12 + 0.05 * (1 - ((x - 0.5) ** 2 + (y - 0.5) ** 2) * 2)
    rgb = np.dstack([v * 0.85, v * 1.05, v * 0.92])
    rgb[(np.mgrid[0:S, 0:S][0] % 3) == 0] *= 0.82
    # A faint dead image: a cursor and two lines of text left burnt in.
    rgb[14:16, 10:40] += 0.06
    rgb[20:22, 10:30] += 0.05
    rgb[26:29, 10:12] += 0.10
    glare = np.clip(1 - np.hypot(x - 0.3, y - 0.25) / 0.25, 0, 1) * 0.08
    rgb += glare[..., None]
    dust = noise(66, 0.6) > 0.86
    rgb[dust] += 0.05
    return rgba(rgb), (0.9, 40, 0.0, 0.18)


def m_keyboard():
    rgb = np.ones((S, S, 3)) * np.array([0.56, 0.53, 0.44])
    for ky in range(4, S - 4, 8):
        for kx in range(2, S - 2, 6):
            shade = 0.80 + 0.12 * random.Random(kx * 97 + ky).random()
            rgb[ky:ky + 6, kx:kx + 5] = np.array([0.80, 0.76, 0.64]) * shade
            rgb[ky + 5, kx:kx + 5] *= 0.75
    return rgba(grime(rgb, 67, 0.4, 0.2)), (0.3, 40, 0.0)


def m_cable():
    v = 0.14 + 0.03 * noise(68, 0.8)
    return rgba(np.dstack([v, v, v])), (0.35, 20, 0.0)


ITEM_MATERIALS = {
    "rust_pipe": m_rust_pipe, "galv_steel": m_galv_steel, "bare_steel": m_bare_steel, "red_paint": m_red_paint,
    "tape_black": m_tape_black, "duct_tape": m_duct_tape, "old_wood": m_old_wood, "scrap_wood": m_scrap_wood,
    "wallpaper_strip": m_wallpaper_strip, "fluoro_tube": m_fluoro_tube, "lamp_metal": m_lamp_metal, "wire_black": m_wire_black,
    "copper_winding": m_copper_winding, "copper_bright": m_copper_bright, "spool": m_spool, "glass_milk": m_glass_milk,
    "cork": m_cork, "label": m_label, "twine": m_twine,
}
BLOCK_MATERIALS = {
    "office_fabric": m_office_fabric, "dark_plastic": m_dark_plastic, "chrome_worn": m_chrome_worn,
    "beige_plastic": m_beige_plastic, "crt_screen": m_crt_screen, "keyboard": m_keyboard, "cable": m_cable,
}


def save_material(folder, name, make):
    image, pbr = make()
    smooth, f0, porosity = pbr[:3]
    emissive = pbr[3] if len(pbr) > 3 else 0.0
    path = OUT / "textures" / folder / "trial"
    path.mkdir(parents=True, exist_ok=True)
    Image.fromarray((image * 255).round().astype(np.uint8), "RGBA").save(path / f"{name}.png")
    # labPBR: normal (xy, AO, height) from the colour's light and dark; specular (smoothness, F0, porosity, emission).
    height = ndimage.gaussian_filter(lum(image[..., :3]), 0.8, mode="wrap")
    gx, gy = ndimage.sobel(height, 1, mode="wrap"), ndimage.sobel(height, 0, mode="wrap")
    n = np.dstack([-gx * 2.5, -gy * 2.5, np.ones_like(gx)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    normal = np.dstack([(n[..., 0] + 1) / 2, (n[..., 1] + 1) / 2, np.ones_like(height), 0.5 + height * 0.5])
    Image.fromarray((np.clip(normal, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path / f"{name}_n.png")
    rough = smooth * (0.85 + 0.3 * (height - height.mean()))
    spec = np.dstack([np.clip(rough, 0, 1), np.full_like(height, f0 / 255), np.full_like(height, porosity * 64 / 255),
                      np.full_like(height, emissive * 254 / 255 if emissive else 1.0)])
    Image.fromarray((np.clip(spec, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path / f"{name}_s.png")
    return image


# ---------------------------------------------------------------- models

FACES = ("north", "south", "east", "west", "up", "down")


def _uv(face, frm, to, seed):
    sx, sy, sz = (abs(b - a) for a, b in zip(frm, to))
    w, h = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]
    w, h = min(w, 16), min(h, 16)
    digest = hashlib.md5(f"{seed}{face}{frm}{to}".encode()).digest()
    u0 = round((16 - w) * digest[0] / 255, 2)
    v0 = round((16 - h) * digest[1] / 255, 2)
    return [u0, v0, round(u0 + w, 2), round(v0 + h, 2)]


def box(frm, to, tex, faces=FACES, rot=None, emit=0, seed=0):
    element = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for face in faces:
        element["faces"][face] = {"texture": "#" + tex, "uv": _uv(face, frm, to, seed)}
    if rot:
        axis, angle, origin = rot
        element["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
    if emit:
        element["light_emission"] = emit
    return element


def model(elements, folder, display=None, parent=None):
    textures = sorted({f["texture"][1:] for e in elements for f in e["faces"].values()})
    data = {}
    if parent:
        data["parent"] = parent
    data["textures"] = {t: f"noclip_backrooms:{folder}/trial/{t}" for t in textures}
    data["textures"]["particle"] = data["textures"][textures[0]]
    data["elements"] = elements
    if display:
        data["display"] = display
    return data


def gemini_display(path):
    data = json.loads((ASSETS / "models" / f"{path}.json").read_text(encoding="utf8"))
    return data.get("display")


def hands(rotation, translation, scale):
    """First-person poses for both hands (the left mirrored). Weapons are first moved into place by Gemini's
    first-person animation (ItemInHandRendererWeaponAnimation); these are on top of that."""
    left = [rotation[0], -rotation[1], -rotation[2]]
    return {"firstperson_righthand": {"rotation": rotation, "translation": translation, "scale": [scale] * 3},
            "firstperson_lefthand": {"rotation": left, "translation": translation, "scale": [scale] * 3}}


# Our own poses in hand (Gemini's were never shown: its weapons are drawn from Blender meshes in hand), and an
# inventory view of the spool that shows the copper.
POSES = {
    "blunt_rusty_pipe": hands([-30, 0, 6], [0, 0, 0], 0.5),
    "improvised_crowbar": hands([-30, 0, 6], [0, 2.5, 0], 0.52),
    "magic_noclip_wand": hands([-26, 0, 6], [0, 1.5, 0], 0.46),
    "gun_pipe_pistol": hands([0, 0, 0], [0, 2.5, 1], 0.6),
    "copper_wire": {**hands([0, 45, 0], [0, 3.5, 0], 0.5), "gui": {"rotation": [25, 40, 0], "translation": [0, 0, 0], "scale": [0.8] * 3}},
    "almond_elixir": hands([0, 30, 0], [0, 3.5, 0], 0.5),
}


def rusty_pipe():
    return [
        box((7, -1, 7), (9, 12, 9), "rust_pipe", seed=1),
        box((7, 12, 7), (9, 20, 9), "rust_pipe", seed=2),
        box((6.8, -0.5, 6.8), (9.2, 6.5, 9.2), "tape_black"),
        box((6.9, -1.6, 6.9), (9.1, -1, 9.1), "galv_steel"),
        box((6.6, 20, 6.6), (9.4, 22.6, 9.4), "galv_steel", seed=3),
        box((6.6, 22.6, 6.6), (9.4, 26.4, 9.4), "galv_steel", seed=4),
        box((9.4, 23.2, 7), (11.6, 25.8, 9), "galv_steel", seed=5),
        box((11.6, 23, 6.8), (12, 26, 9.2), "rust_pipe", seed=6),
    ]


def crowbar():
    shaft = ("y", 45, (8, 8.8, 8))
    return [
        box((7.3, 0, 7.3), (8.7, 17.6, 8.7), "red_paint", seed=1),
        box((7.3, 0, 7.3), (8.7, 17.6, 8.7), "red_paint", rot=shaft, seed=2),
        box((7.3, 17, 6.2), (8.7, 18.6, 8.7), "red_paint", seed=3),
        box((7.3, 15.4, 4.6), (8.7, 18.6, 6.2), "red_paint", seed=4),
        box((7.45, 14.2, 4.6), (8.55, 15.4, 5.8), "bare_steel", seed=5),
        box((7.3, -1.6, 7.6), (8.7, 0, 8.4), "bare_steel", rot=("x", 15, (8, -0.8, 8)), seed=6),
    ]


def noclip_wand():
    return [
        box((7.3, -2, 7.3), (8.7, 18, 8.7), "old_wood", seed=1),
        box((7.0, 4, 7.0), (9.0, 6.5, 9.0), "wallpaper_strip", seed=2),
        box((7.0, 9, 7.0), (9.0, 11, 9.0), "wallpaper_strip", seed=3),
        box((7.0, 13.5, 7.0), (9.0, 15.5, 9.0), "wallpaper_strip", seed=4),
        box((6.9, 16.4, 6.9), (9.1, 18, 9.1), "duct_tape", seed=5),
        box((6.5, 18, 6.5), (9.5, 19.6, 9.5), "lamp_metal", seed=6),
        box((7.2, 19.6, 7.2), (8.8, 25.4, 8.8), "fluoro_tube", emit=7, seed=7),
        box((7.5, 25.4, 7.5), (8.3, 26.3, 8.1), "fluoro_tube", emit=7, seed=8),
        box((8.7, 14.6, 7.8), (9.1, 18.4, 8.2), "wire_black", seed=9),
        box((9.1, 14.6, 7.8), (9.6, 15.0, 8.2), "copper_bright", seed=10),
    ]


def pipe_pistol():
    grip = ("x", 17, (8, 7, 10.4))
    return [
        box((7, 7, -3), (9, 9, 7), "galv_steel", seed=1),
        box((6.8, 6.8, -3.2), (9.2, 9.2, -2.4), "rust_pipe", seed=2),
        box((6.8, 6.8, 0.6), (9.2, 9.2, 1.4), "bare_steel", seed=3),
        box((6.8, 6.8, 3.8), (9.2, 9.2, 4.6), "bare_steel", seed=4),
        box((6.4, 6.4, 6.6), (9.6, 9.6, 9.4), "galv_steel", seed=5),
        box((7.4, 7.4, 9.4), (8.6, 8.6, 10.2), "rust_pipe", seed=6),
        box((7.7, 9.6, 8.4), (8.3, 10.6, 9.0), "bare_steel", seed=7),
        box((7.05, 1.8, 9.2), (8.95, 7.2, 11.6), "scrap_wood", rot=grip, seed=8),
        box((6.95, 3.6, 9.1), (9.05, 5.0, 11.7), "duct_tape", rot=grip, seed=9),
        box((7.4, 6.2, 6.8), (8.6, 7.0, 10.4), "galv_steel", seed=10),
        box((7.8, 5.4, 7.6), (8.2, 6.4, 8.0), "bare_steel", seed=11),
        box((7.75, 4.9, 6.4), (8.25, 5.2, 8.8), "bare_steel", seed=12),
        box((7.75, 5.2, 6.4), (8.25, 6.4, 6.7), "bare_steel", seed=13),
        box((7.75, 9.0, -2.2), (8.25, 9.5, -1.6), "bare_steel", seed=14),
    ]


def copper_spool():
    return [
        box((4, 4, 6), (12, 12, 6.8), "spool", seed=1),
        box((4, 4, 9.2), (12, 12, 10), "spool", seed=2),
        box((5, 5, 6.8), (11, 11, 9.2), "copper_winding", seed=3),
        box((7.2, 7.2, 5.95), (8.8, 8.8, 6.0), "tape_black", faces=("north",), seed=4),
        box((10.9, 7.7, 7.8), (13.2, 8.1, 8.2), "copper_winding", seed=5),
        box((12.8, 8.1, 7.8), (13.2, 10.2, 8.2), "copper_winding", seed=6),
        box((12.8, 10.2, 7.8), (13.2, 10.8, 8.2), "copper_bright", seed=7),
    ]


def elixir():
    return [
        box((5.5, 1.6, 5.5), (10.5, 10.5, 10.5), "glass_milk", seed=1),
        box((6.2, 10.5, 6.2), (9.8, 11.6, 9.8), "glass_milk", seed=2),
        box((7.1, 11.6, 7.1), (8.9, 13.4, 8.9), "glass_milk", seed=3),
        box((7.2, 13.4, 7.2), (8.8, 14.8, 8.8), "cork", seed=4),
        box((5.45, 3.6, 5.45), (10.55, 7.4, 10.55), "label", faces=("north", "south", "east", "west"), seed=5),
        box((7.0, 12.3, 7.0), (9.0, 12.7, 9.0), "twine", faces=("north", "south", "east", "west"), seed=6),
    ]


def office_chair():
    star = ("y", 22.5, (8, 1, 8))
    back = ("x", -10, (8, 9.2, 12.7))
    parts = [
        box((2.6, 1.0, 7.5), (13.4, 1.7, 8.5), "dark_plastic", rot=star, seed=1),
        box((7.5, 1.0, 2.6), (8.5, 1.7, 13.4), "dark_plastic", rot=star, seed=2),
    ]
    for frm, to in (((2.2, 0, 7.3), (3.6, 1, 8.7)), ((12.4, 0, 7.3), (13.8, 1, 8.7)), ((7.3, 0, 2.2), (8.7, 1, 3.6)), ((7.3, 0, 12.4), (8.7, 1, 13.8))):
        parts.append(box(frm, to, "dark_plastic", rot=star, seed=3))
    parts += [
        box((7, 1, 7), (9, 2.2, 9), "dark_plastic", seed=4),
        box((7.2, 2.2, 7.2), (8.8, 3.6, 8.8), "dark_plastic", seed=5),
        box((7.4, 3.6, 7.4), (8.6, 5.6, 8.6), "chrome_worn", seed=6),
        box((4.6, 5.6, 4.6), (11.4, 6.2, 11.4), "dark_plastic", seed=7),
        box((3.4, 6.2, 3.2), (12.6, 8.0, 12.2), "office_fabric", seed=8),
        box((7.2, 6.0, 11.4), (8.8, 10.2, 12.4), "dark_plastic", rot=back, seed=9),
        box((3.8, 9.2, 11.8), (12.2, 16.0, 13.6), "office_fabric", rot=back, seed=10),
        box((4.0, 9.4, 13.6), (12.0, 15.8, 14.0), "dark_plastic", rot=back, seed=11),
    ]
    for x0 in (3.0, 12.2):
        parts.append(box((x0, 6.6, 6.6), (x0 + 0.8, 10.4, 7.6), "dark_plastic", seed=12))
        parts.append(box((x0 - 0.2, 10.4, 5.4), (x0 + 1.0, 11.2, 10.2), "dark_plastic", seed=13))
    return parts


def desktop_computer():
    return [
        box((3, 2, 5), (13, 11, 7.4), "beige_plastic", seed=1),
        box((4, 3, 4.9), (12, 10, 5.0), "crt_screen", faces=("north",), emit=3, seed=2),
        box((4, 2.6, 7.4), (12, 10.2, 11), "beige_plastic", seed=3),
        box((5, 3.4, 11), (11, 9.2, 13), "beige_plastic", seed=4),
        box((5, 0, 5.8), (11, 1, 11.2), "beige_plastic", seed=5),
        box((6.5, 1, 6.6), (9.5, 2, 10), "beige_plastic", seed=6),
        box((3.2, 0, 0.6), (12.8, 0.7, 3.8), "keyboard", faces=("up",), rot=("x", 4, (8, 0, 2.2)), seed=7),
        box((3.2, 0, 0.6), (12.8, 0.7, 3.8), "beige_plastic", faces=("north", "south", "east", "west", "down"), rot=("x", 4, (8, 0, 2.2)), seed=8),
        box((13.2, 0, 1.4), (14.4, 0.5, 3.0), "beige_plastic", seed=9),
        box((13.6, 0, 3.0), (14.0, 0.15, 6.0), "cable", seed=10),
    ]


ITEMS = {
    "blunt_rusty_pipe": rusty_pipe, "improvised_crowbar": crowbar, "magic_noclip_wand": noclip_wand,
    "gun_pipe_pistol": pipe_pistol, "copper_wire": copper_spool, "almond_elixir": elixir,
}
PROPS = {"prop_office_chair": office_chair, "prop_desktop_computer": desktop_computer}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def main():
    previews = []
    for name, make in ITEM_MATERIALS.items():
        previews.append((name, save_material("item", name, make)))
    for name, make in BLOCK_MATERIALS.items():
        previews.append((name, save_material("block", name, make)))
    for item, build in ITEMS.items():
        display = dict(gemini_display(f"item/crafting/{item}") or {})
        display.update(POSES.get(item, {}))
        write(OUT / "models/item/trial" / f"{item}.json", model(build(), "item", display))
        write(OUT / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/trial/{item}"}})
    for prop, build in PROPS.items():
        write(OUT / "models/block/trial" / f"{prop}.json", model(build(), "block", parent="minecraft:block/block"))
        write(OUT / "items" / f"{prop}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:block/trial/{prop}"}})
        states = json.loads((ASSETS / "blockstates" / f"{prop}.json").read_text(encoding="utf8"))
        for variant in states.get("variants", {}).values():
            for v in variant if isinstance(variant, list) else [variant]:
                v["model"] = f"noclip_backrooms:block/trial/{prop}"
        write(OUT / "blockstates" / f"{prop}.json", states)
    write(PACK / "pack.mcmeta", {"pack": {"description": {"translate": "pack.noclip_backrooms.claude_trial.description",
                                                          "fallback": "Claude's trial models for eight items"},
                                          "min_format": 88, "max_format": 88}})
    # A sheet of the materials, for a look without the game.
    cell = 96
    sheet = Image.new("RGB", (cell * 7, cell * ((len(previews) + 6) // 7)), (30, 30, 30))
    for i, (name, image) in enumerate(previews):
        tile = Image.fromarray((image * 255).round().astype(np.uint8), "RGBA").resize((cell - 8, cell - 8), Image.NEAREST)
        bg = Image.new("RGBA", tile.size, (60, 60, 60, 255))
        bg.alpha_composite(tile)
        sheet.paste(bg.convert("RGB"), ((i % 7) * cell + 4, (i // 7) * cell + 4))
    out = ROOT / "docs/look/trial_materials.png"
    sheet.save(out)
    print(f"{len(previews)} materials, {len(ITEMS) + len(PROPS)} models -> {PACK.relative_to(ROOT)}; sheet {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
