"""Baked shading for box models (the cure for the "plastic" look): each item gets its own 128x128 atlas, one patch per
face, and Blender (blender_bake.py, headless) bakes ambient occlusion and a soft top light onto it. The atlas is then
painted: the face's material, darkened in corners and gaps and where light does not reach, dust in the crevices, the
outer edges worn bright. The model is rewritten to use the atlas.
"""
import copy
import hashlib
import json
import math
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

from lib import OUT, lum, made

BLENDER = Path("E:/SteamLibrary/steamapps/common/Blender/blender.exe")
SIZE = 128
SCRIPT = Path(__file__).resolve().parent / "blender_bake.py"

# How a face's uv axes run in Minecraft (the default uv of each face): for each face, the corners at
# (u0, v0), (u1, v0), (u1, v1), (u0, v1) as (x, y, z) picked from (from, to) per axis.
CORNERS = {
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_size(face, frm, to):
    sx, sy, sz = (b - a for a, b in zip(frm, to))
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


def rotate(p, rot):
    if not rot:
        return list(p)
    axis = {"x": 0, "y": 1, "z": 2}[rot["axis"]]
    a = math.radians(rot["angle"])
    o = rot["origin"]
    v = [p[i] - o[i] for i in range(3)]
    c, s = math.cos(a), math.sin(a)
    i, j = [(1, 2), (2, 0), (0, 1)][axis]
    vi, vj = v[i], v[j]
    v[i], v[j] = vi * c - vj * s, vi * s + vj * c
    return [v[k] + o[k] for k in range(3)]


def layout(elements):
    """Shelf-packs every face into the atlas at the finest density that fits; returns [(element, face, rect)]."""
    faces = [(e, f) for e in elements for f in e["faces"]]
    for density in (4.0, 3.5, 3.0, 2.5, 2.0, 1.6, 1.25, 1.0):
        placed, x, y, shelf = [], 1, 1, 0
        ok = True
        for e, f in sorted(faces, key=lambda ef: -face_size(ef[1], ef[0]["from"], ef[0]["to"])[1]):
            w, h = face_size(f, e["from"], e["to"])
            pw, ph = max(1, math.ceil(w * density)), max(1, math.ceil(h * density))
            if x + pw + 1 > SIZE:
                x, y, shelf = 1, y + shelf + 2, 0
            if y + ph + 1 > SIZE or pw + 2 > SIZE:
                ok = False
                break
            placed.append((e, f, (x, y, pw, ph)))
            x += pw + 2
            shelf = max(shelf, ph)
        if ok:
            return placed, density
    raise ValueError("faces do not fit in the atlas")


def run_blender(jobs):
    work = Path(tempfile.mkdtemp(prefix="item_bake_"))
    payload = {"size": SIZE, "items": []}
    for item_id, placed in jobs.items():
        faces = []
        for e, f, (x, y, w, h) in placed:
            frm, to = e["from"], e["to"]
            corners = [[(frm, to)[sel[k]][k] for k in range(3)] for sel in CORNERS[f]]
            corners = [rotate(c, e.get("rotation")) for c in corners]
            normal = rotate(NORMALS[f], {**e["rotation"], "origin": [0, 0, 0]} if e.get("rotation") else None)
            faces.append({"corners": corners, "uv": [[x, y], [x + w, y], [x + w, y + h], [x, y + h]], "normal": normal})
        payload["items"].append({"id": item_id, "faces": faces})
    job = work / "job.json"
    job.write_text(json.dumps(payload), encoding="utf8")
    result = subprocess.run([str(BLENDER), "-b", "--factory-startup", "-P", str(SCRIPT), "--", str(job), str(work)],
                            capture_output=True, text=True, timeout=1800)
    if "BAKED" not in result.stdout:
        raise RuntimeError("Blender bake failed:\n" + result.stdout[-3000:] + result.stderr[-3000:])
    return work


def _gray(path):
    return np.asarray(Image.open(path).convert("L"), np.float32) / 255


def paint(item_id, placed, density, work):
    """The atlas: each face's material, shaded by the bake, worn at the edges, dusty in the gaps."""
    ao, light = _gray(work / f"{item_id}_ao.png"), _gray(work / f"{item_id}_light.png")
    filled = np.zeros((SIZE, SIZE), bool)
    for _, _, (x, y, w, h) in placed:
        filled[y:y + h, x:x + w] = True
    top = max(float(np.percentile(light[filled], 92)), 1e-3)
    atlas = np.zeros((SIZE, SIZE, 4), np.float32)
    spec = np.zeros((SIZE, SIZE, 4), np.float32)
    for e, f, (x, y, w, h) in placed:
        name = e["faces"][f]["texture"][1:]
        image, pbr = made(name)
        smooth, f0, porosity = pbr[:3]
        emissive = pbr[3] if len(pbr) > 3 else 0.0
        seed = hashlib.md5(f"{item_id}{name}{e['from']}{f}".encode()).digest()
        jj, ii = np.mgrid[0:h, 0:w]
        sx = ((ii / density) * 4 + seed[0] % 64).astype(int) % 64
        sy = ((jj / density) * 4 + seed[1] % 64).astype(int) % 64
        rgba = image[sy, sx]
        rgb = rgba[..., :3].copy()
        if not (e.get("light_emission") or emissive):
            a = ao[y:y + h, x:x + w]
            l = np.clip(light[y:y + h, x:x + w] / top, 0, 1.15)
            shade = (0.45 + 0.55 * a) * (0.70 + 0.42 * l)
            rgb *= shade[..., None]
            dust = np.clip((0.62 - a) / 0.4, 0, 1)[..., None]
            rgb = rgb * (1 - 0.35 * dust) + np.array([0.20, 0.17, 0.13]) * 0.35 * dust * (rgb.mean(axis=2, keepdims=True) > 0.05)
            # Outer edges catch the light and the wear: a pixel's rim, brighter on metal.
            rim = np.zeros((h, w), bool)
            rim[0, :] = rim[-1, :] = rim[:, 0] = rim[:, -1] = True
            gain = 0.16 if f0 >= 230 or smooth > 0.5 else 0.07
            rgb[rim] = np.clip(rgb[rim] * (1 + gain) + gain * 0.25, 0, 1)
        atlas[y:y + h, x:x + w, :3] = np.clip(rgb, 0, 1)
        atlas[y:y + h, x:x + w, 3] = rgba[..., 3]
        spec[y:y + h, x:x + w] = (smooth, f0 / 255, porosity * 64 / 255, emissive * 254 / 255 if emissive else 1.0)
    # Spread each patch a pixel outwards so the faces never pick up their neighbours' colours.
    for _ in range(2):
        grown = ndimage.grey_dilation(atlas[..., 3] > 0, size=3) & ~(atlas[..., 3] > 0)
        for c in range(4):
            near = ndimage.maximum_filter(atlas[..., c], size=3)
            atlas[..., c] = np.where(grown, near, atlas[..., c])
            near_s = ndimage.maximum_filter(spec[..., c], size=3)
            spec[..., c] = np.where(grown, near_s, spec[..., c])
    return atlas, spec


def write_atlas(folder, item_id, atlas, spec):
    path = OUT / "textures" / folder / "look" / "baked"
    path.mkdir(parents=True, exist_ok=True)
    Image.fromarray((atlas * 255).round().astype(np.uint8), "RGBA").save(path / f"{item_id}.png")
    height = ndimage.gaussian_filter(lum(atlas[..., :3]), 0.7)
    gx, gy = ndimage.sobel(height, 1), ndimage.sobel(height, 0)
    n = np.dstack([-gx * 2.5, -gy * 2.5, np.ones_like(gx)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    normal = np.dstack([(n[..., 0] + 1) / 2, (n[..., 1] + 1) / 2, np.ones_like(height), 0.5 + height * 0.5])
    Image.fromarray((np.clip(normal, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path / f"{item_id}_n.png")
    Image.fromarray((np.clip(spec, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path / f"{item_id}_s.png")
    return f"noclip_backrooms:{folder}/look/baked/{item_id}"


def bake(items):
    """items: id -> (elements, folder). Returns id -> (elements using the atlas, texture id)."""
    jobs, densities = {}, {}
    for item_id, (elements, _) in items.items():
        jobs[item_id], densities[item_id] = layout(elements)
    work = run_blender(jobs)
    out = {}
    for item_id, (elements, folder) in items.items():
        placed = jobs[item_id]
        atlas, spec = paint(item_id, placed, densities[item_id], work)
        texture = write_atlas(folder, item_id, atlas, spec)
        new = copy.deepcopy(elements)
        index = {id(e): k for k, e in enumerate(elements)}
        for e, f, (x, y, w, h) in placed:
            face = new[index[id(e)]]["faces"][f]
            face["texture"] = "#baked"
            face["uv"] = [round(x * 16 / SIZE, 4), round(y * 16 / SIZE, 4), round((x + w) * 16 / SIZE, 4), round((y + h) * 16 / SIZE, 4)]
        out[item_id] = (new, texture)
    return out
