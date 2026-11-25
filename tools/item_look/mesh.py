"""Items modelled in Blender with real shapes (blender_mesh.py): the Python side. Hands Blender the materials, runs it,
paints the final texture from its bakes (colour, shaded by ambient occlusion, dust in the gaps), and writes the mesh
and texture into the pack. In the game core.look.MeshItemRenderer draws them; the item's box model stays as the
"base" that gives the poses, so hands, sights and frames work as before.
"""
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

from bake import BLENDER
from lib import MAKERS, OUT, lum, made

SCRIPT = Path(__file__).resolve().parent / "blender_mesh.py"
BAKE = 256
FINAL = 128


def run(items):
    """items: id -> (shift, folder). Returns id -> (mesh resource id, texture resource id)."""
    work = Path(tempfile.mkdtemp(prefix="item_mesh_"))
    materials = {}
    for name in MAKERS:
        image, pbr = made(name)
        path = work / f"mat_{name}.png"
        Image.fromarray((image * 255).round().astype(np.uint8), "RGBA").save(path)
        materials[name] = {"png": str(path), "pbr": list(pbr[:3])}
    job = {"size": BAKE, "materials": materials, "items": [{"id": i, "shift": list(s)} for i, (s, _) in items.items()]}
    (work / "job.json").write_text(json.dumps(job), encoding="utf8")
    result = subprocess.run([str(BLENDER), "-b", "--factory-startup", "-P", str(SCRIPT), "--", str(work / "job.json"), str(work)],
                            capture_output=True, text=True, timeout=3600)
    if "MESHED" not in result.stdout:
        raise RuntimeError("Blender modelling failed:\n" + result.stdout[-4000:] + result.stderr[-3000:])
    out = {}
    for item_id, (_, folder) in items.items():
        mesh_dir = OUT / "models" / "mesh"
        mesh_dir.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(work / f"{item_id}.obj", mesh_dir / f"{item_id}.obj")
        texture = paint(item_id, folder, work)
        out[item_id] = (f"noclip_backrooms:models/mesh/{item_id}.obj", texture)
    return out


def _load(path, mode="RGB"):
    return np.asarray(Image.open(path).convert(mode), np.float32) / 255


def paint(item_id, folder, work):
    color = _load(work / f"{item_id}_color.png")
    ao = _load(work / f"{item_id}_ao.png", "L")
    spec = _load(work / f"{item_id}_spec.png")
    shade = 0.42 + 0.58 * ao
    rgb = color * shade[..., None]
    dust = np.clip((0.6 - ao) / 0.4, 0, 1)[..., None]
    rgb = rgb * (1 - 0.3 * dust) + np.array([0.19, 0.16, 0.12]) * 0.3 * dust
    small = lambda a, mode: np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).round().astype(np.uint8), mode)
                                       .resize((FINAL, FINAL), Image.LANCZOS), np.float32) / 255
    rgb = small(rgb, "RGB")
    spec = small(spec, "RGB")
    path = OUT / "textures" / folder / "mesh"
    path.mkdir(parents=True, exist_ok=True)
    Image.fromarray((rgb * 255).round().astype(np.uint8), "RGB").save(path / f"{item_id}.png")
    height = ndimage.gaussian_filter(lum(rgb), 0.7)
    gx, gy = ndimage.sobel(height, 1), ndimage.sobel(height, 0)
    n = np.dstack([-gx * 2.0, -gy * 2.0, np.ones_like(gx)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    normal = np.dstack([(n[..., 0] + 1) / 2, (n[..., 1] + 1) / 2, np.ones_like(height), 0.5 + height * 0.5])
    Image.fromarray((np.clip(normal, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path / f"{item_id}_n.png")
    specular = np.dstack([spec, np.ones_like(height)])
    Image.fromarray((np.clip(specular, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path / f"{item_id}_s.png")
    return f"noclip_backrooms:textures/{folder}/mesh/{item_id}.png"
