"""Shared pieces of the realistic items pack: texture helpers, the material registry, box models and their writing.

Materials are 64x64 (docs/畫面規範/風格規範.md: props 64), each with labPBR _n / _s, made on first use and written into
whichever atlas (item or block) a model that uses them needs. Models are plain Minecraft box models.
"""
import hashlib
import json
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

TOOLS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOLS))
sys.dont_write_bytecode = True
import make_level2_realistic as R  # noqa: E402  (its CC0 photo downloader and cache)

ROOT = TOOLS.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
PACK = ROOT / "src/main/resources/resourcepacks/realistic_items"
OUT = PACK / "assets/noclip_backrooms"
S = 64

# ---------------------------------------------------------------- texture helpers


def noise(seed, blur, size=S):
    rng = np.random.default_rng(seed)
    a = ndimage.gaussian_filter(rng.standard_normal((size, size)), blur, mode="wrap")
    a -= a.min()
    return a / max(a.max(), 1e-6)


_PHOTOS = {}


def photo(asset, crop, seed):
    """A square of a CC0 photo material (ambientCG), scaled down to the texture."""
    if asset not in _PHOTOS:
        _PHOTOS[asset] = Image.open(R.source(asset)["Color"]).convert("RGB")
    image = _PHOTOS[asset]
    rnd = random.Random(seed)
    x, y = rnd.randint(0, image.width - crop), rnd.randint(0, image.height - crop)
    return np.asarray(image.crop((x, y, x + crop, y + crop)).resize((S, S), Image.LANCZOS), np.float32) / 255


def lum(rgb):
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def col(c):
    return np.array(c, np.float32) / 255 if max(c) > 1 else np.array(c, np.float32)


def tint(rgb, colour, keep=0.35):
    """The photo's light and dark, in this colour; `keep` of the photo's own colour stays."""
    l = lum(rgb)[..., None]
    target = col(colour) * (l / max(float(l.mean()), 1e-3))
    return np.clip(target * (1 - keep) + rgb * keep, 0, 1)


def flat(colour, seed, var=0.08, blur=1.5):
    return np.clip(np.ones((S, S, 3)) * col(colour) * (1 - var + 2 * var * noise(seed, blur))[..., None], 0, 1)


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
        for t in range(rnd.randint(max(1, length // 2), length)):
            px, py = int(x + np.cos(a) * t) % S, int(y + np.sin(a) * t) % S
            out[py, px] = out[py, px] * 0.45 + col(colour) * 0.55
    return out


def spots(rgb, seed, colour, threshold, blur=2.0, mix=1.0):
    mask = (noise(seed, blur) > threshold)[..., None]
    return np.where(mask, rgb * (1 - mix) + col(colour) * mix, rgb)


def bands(colour_a, colour_b, period, axis=0, duty=0.5):
    idx = np.arange(S)
    on = (idx % period) < period * duty
    rgb = np.where(on[:, None, None] if axis == 0 else on[None, :, None], col(colour_a), col(colour_b))
    return np.broadcast_to(rgb, (S, S, 3)).astype(np.float32).copy()


def rgba(rgb, alpha=None):
    a = np.ones(rgb.shape[:2], np.float32) if alpha is None else alpha
    return np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)])


# ---------------------------------------------------------------- materials
# name -> maker; a maker returns (rgba image, (smoothness, f0, porosity[, emissive])). f0 230+ are labPBR's metals.

# How far each item's boxes were moved (centred) after being drawn: the Blender meshes are moved the same way.
SHIFTS = {}

MAKERS = {}
_MADE = {}
_WRITTEN = set()


def material(name):
    def register(fn):
        MAKERS[name] = fn
        return fn
    return register


def define(name, fn):
    MAKERS[name] = fn


def made(name):
    if name not in _MADE:
        if name not in MAKERS:
            raise KeyError(f"no material {name}")
        _MADE[name] = MAKERS[name]()
    return _MADE[name]


def write_material(name, folder):
    if (name, folder) in _WRITTEN:
        return
    image, pbr = made(name)
    smooth, f0, porosity = pbr[:3]
    emissive = pbr[3] if len(pbr) > 3 else 0.0
    path = OUT / "textures" / folder / "look"
    path.mkdir(parents=True, exist_ok=True)
    Image.fromarray((image * 255).round().astype(np.uint8), "RGBA").save(path / f"{name}.png")
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
    _WRITTEN.add((name, folder))


# ---------------------------------------------------------------- models

FACES = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")


def _uv(face, frm, to, seed):
    sx, sy, sz = (abs(b - a) for a, b in zip(frm, to))
    w, h = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]
    w, h = min(w, 16), min(h, 16)
    digest = hashlib.md5(f"{seed}{face}{frm}{to}".encode()).digest()
    u0 = round((16 - w) * digest[0] / 255, 2)
    v0 = round((16 - h) * digest[1] / 255, 2)
    return [u0, v0, round(u0 + w, 2), round(v0 + h, 2)]


def box(frm, to, tex, faces=FACES, rot=None, emit=0, seed=0):
    """A box from `frm` to `to` (sixteenths of a block) in material `tex`; rot = (axis, angle, origin)."""
    frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
    element = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for face in faces:
        element["faces"][face] = {"texture": "#" + tex, "uv": _uv(face, frm, to, seed)}
    if rot:
        axis, angle, origin = rot
        element["rotation"] = {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin]}
    if emit:
        element["light_emission"] = emit
    return element


def column(x0, x1, y0, y1, w, tex, **kw):
    """A box centred on x = z = 8, w wide, from y0 to y1 (x0/x1 unused, kept for symmetry with bar())."""
    h = w / 2
    return box((8 - h, y0, 8 - h), (8 + h, y1, 8 + h), tex, **kw)


def bar(y0, y1, wx, wz, tex, cx=8.0, cz=8.0, **kw):
    """A vertical bar centred on (cx, cz)."""
    return box((cx - wx / 2, y0, cz - wz / 2), (cx + wx / 2, y1, cz + wz / 2), tex, **kw)


def shift(elements, dx=0.0, dy=0.0, dz=0.0):
    for e in elements:
        e["from"] = [round(a + d, 3) for a, d in zip(e["from"], (dx, dy, dz))]
        e["to"] = [round(a + d, 3) for a, d in zip(e["to"], (dx, dy, dz))]
        if "rotation" in e:
            e["rotation"]["origin"] = [round(a + d, 3) for a, d in zip(e["rotation"]["origin"], (dx, dy, dz))]
    return elements


def extent(elements, axis):
    return min(e["from"][axis] for e in elements), max(e["to"][axis] for e in elements)


def centre_y(elements):
    """Moves a tall item so its middle is the model's middle (y = 8); returns the shift."""
    lo, hi = extent(elements, 1)
    dy = round(8 - (lo + hi) / 2, 3)
    shift(elements, dy=dy)
    return dy


def model(elements, folder, display=None, parent=None, atlas=None):
    """A model JSON. With `atlas` (a baked texture id, see bake.py) every face uses that one texture."""
    data = {}
    if parent:
        data["parent"] = parent
    if atlas:
        data["textures"] = {"baked": atlas, "particle": atlas}
    else:
        textures = sorted({f["texture"][1:] for e in elements for f in e["faces"].values()})
        for name in textures:
            write_material(name, folder)
        data["textures"] = {t: f"noclip_backrooms:{folder}/look/{t}" for t in textures}
        data["textures"]["particle"] = data["textures"][textures[0]]
    data["elements"] = elements
    if display:
        data["display"] = display
    return data


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf8")


def gemini_display(path):
    """The poses Gemini set in its own model of the same item (the inventory, ground and frame poses are kept)."""
    file = ASSETS / "models" / f"{path}.json"
    if not file.exists():
        return {}
    return json.loads(file.read_text(encoding="utf8")).get("display") or {}


def pose(rotation, translation, scale):
    return {"rotation": list(rotation), "translation": list(translation), "scale": [scale] * 3}


def hands(rotation, translation, scale):
    """First-person poses for both hands (the left mirrored). Weapons are first moved into place by Gemini's
    first-person animation (ItemInHandRendererWeaponAnimation); these are on top of that."""
    left = [rotation[0], -rotation[1], -rotation[2]]
    return {"firstperson_righthand": pose(rotation, translation, scale), "firstperson_lefthand": pose(left, translation, scale)}


def third(rotation, translation, scale):
    left = [rotation[0], -rotation[1], -rotation[2]]
    return {"thirdperson_righthand": pose(rotation, translation, scale), "thirdperson_lefthand": pose(left, translation, scale)}
