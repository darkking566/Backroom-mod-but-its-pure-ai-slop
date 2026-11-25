"""Realistic 512 px Level 0.1 (Zenith Station) textures for the built-in "realistic" resource pack.

Every Level 0.1 block texture is redrawn at 512 px with labPBR _n / _s companions in
src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level0p1/.
Each one keeps the drawn 128 px design (panel seams, screens, stripes, cut-out shapes, glowing parts) and takes its
surface from a CC0 ambientCG photo material picked by what the block is made of: painted or bare steel, carpet,
fabric, wood, concrete, ice or rust. The photo materials are the ones make_level2_realistic.py already caches in
tools/.cache/ambientcg/, so this needs no new downloads. Uses that script's helpers.

Run: python tools/make_level0p1_realistic.py
"""
import sys

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
import make_level2_realistic as R  # noqa: E402

ROOT = R.ROOT
OLD = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level0p1"
R.OUT = R.PACK / "assets/noclip_backrooms/textures/block/level0p1"
R.OLD = OLD
R.PREVIEW = ROOT / "docs/look/level0p1_realistic.png"
S = R.S

# What each texture is made of: the first name fragment that matches wins (so "frost_wall" is ice before it is wall).
MATERIAL = [
    (("frost", "icicle", "ice_drift"), "ice"),
    (("scorch", "slag", "rust", "damaged", "broken"), "rust"),
    (("carpet", "gym_mat", "mat_side", "gym_wall", "bed_", "bunk", "clutter_cloth", "bag", "bench_pad", "lab_coat"), "fabric"),
    (("desk", "frame_wood", "crate"), "wood"),
    (("kitchen_tile", "kitchen_wall", "garden", "worn_floor", "hazard_floor"), "concrete"),
    (("hull", "bulkhead", "girder", "truss", "catwalk", "tether", "server", "console", "stair", "rail", "weights", "bar_steel",
      "treadmill", "rack", "vent", "speaker", "coil", "pots", "tank", "sink", "faucet", "stove", "counter", "locker", "fridge"), "steel"),
]
# Glass, light panels, screens and the singularity: keep the drawing as it is, only sharpened to 512 px.
PLAIN = ("glass", "light", "lamp", "screen", "countdown", "singularity", "plasma", "orb", "plant", "vines", "leaves", "cables",
         "tear", "doorway", "art", "mirror", "notes", "core", "beacon")


# How strongly each photo shows through (relative brightness swing of one standard deviation).
GRAIN = {"steel": 0.07, "paint": 0.06, "fabric": 0.14, "carpet": 0.16, "wood": 0.1, "concrete": 0.1, "ice": 0.08, "rust": 0.14}


def substrates():
    return {
        "steel": R.load_pbr_substrate("Metal049A", R.rgb(150, 156, 162), contrast=0.8, normal_strength=0.6, default_smooth=0.5, f0=200, porosity=0.05),
        "paint": R.load_pbr_substrate("Metal032", R.rgb(200, 204, 208), contrast=0.6, normal_strength=0.45, default_smooth=0.34, f0=20, porosity=0.1),
        "fabric": R.load_pbr_substrate("Fabric028", R.rgb(120, 120, 124), contrast=0.9, normal_strength=0.8, default_smooth=0.06, porosity=0.8),
        "carpet": R.load_pbr_substrate("Carpet016", R.rgb(110, 110, 114), contrast=0.9, normal_strength=0.9, default_smooth=0.04, porosity=0.9),
        "wood": R.load_pbr_substrate("Wood051", R.rgb(146, 110, 70), contrast=0.9, normal_strength=0.8, default_smooth=0.3, porosity=0.4),
        "concrete": R.load_pbr_substrate("Concrete019", R.rgb(150, 150, 146), contrast=0.85, normal_strength=0.7, default_smooth=0.22, porosity=0.4),
        "ice": R.load_pbr_substrate("Ice003", R.rgb(206, 228, 246), contrast=0.9, normal_strength=0.9, default_smooth=0.8, f0=16, porosity=0.05),
        "rust": R.load_pbr_substrate("Rust004", R.rgb(110, 80, 64), contrast=1.0, normal_strength=1.0, default_smooth=0.18, f0=28, porosity=0.6),
    }


def kind(name):
    if any(k in name for k in PLAIN):
        return None
    if "carpet" in name or "gym_mat" in name:
        return "carpet"
    for keys, material in MATERIAL:
        if any(k in name for k in keys):
            return material
    return "paint"  # the station's painted white and grey panels: walls, floors, ceilings, doors, hatches


def composite(name, subs):
    """The 128 px drawing at 512 px, with the photo material's grain, relief and gloss laid over its mid-tones."""
    src = Image.open(OLD / f"{name}.png")
    rgba = np.asarray(src.convert("RGBA").resize((S, S), Image.LANCZOS), dtype=np.float32) / 255
    has_alpha = rgba[..., 3].min() < 0.999
    if has_alpha:
        # Cut-outs stay crisp: the alpha comes from a nearest-neighbour upscale so no half-transparent fringe appears.
        rgba[..., 3] = np.asarray(src.convert("RGBA").getchannel("A").resize((S, S), Image.NEAREST), dtype=np.float32) / 255
    base = rgba[..., :3]
    material = kind(name)
    old_s = OLD / f"{name}_s.png"
    s = np.asarray(Image.open(old_s).convert("RGBA").resize((S, S), Image.BILINEAR), dtype=np.float32) / 255 if old_s.exists() else None
    glow = s[..., 3] if s is not None and s[..., 3].min() < 0.999 else None
    if material is None:
        smooth = s[..., 0] if s is not None else 0.6
        f0 = np.clip(s[..., 1] * 255, 8, 240) if s is not None else 20
        return R.Material(base, height=base.mean(axis=2) * 0.3 + 0.35, smooth=smooth, f0=f0, porosity=0.05, glow=glow, bump=10.0,
                          alpha=rgba[..., 3] if has_alpha else None)
    sub = subs[material]
    luma = sub.color.mean(axis=2)
    grain = (luma - luma.mean()) / (luma.std() + 1e-6) * GRAIN[material]  # the photo's scratches, weave or pores, at a set strength
    color = np.clip(base * (1 + grain[..., None]), 0, 1)
    height = np.clip(base.mean(axis=2) * 0.5 + sub.height * 0.5, 0, 1)
    smooth = sub.smooth if s is None else np.clip(s[..., 0] * 0.5 + sub.smooth * 0.5, 0.03, 0.95)
    return R.Material(color, height=height, normal=sub.normal, smooth=smooth, f0=sub.f0, porosity=sub.porosity, glow=glow,
                      bump=24.0, alpha=rgba[..., 3] if has_alpha else None)


def main():
    subs = substrates()
    names = sorted(f.stem for f in OLD.glob("*.png") if not f.stem.endswith(("_n", "_s")))
    counts = {}
    for name in names:
        R.save(name, composite(name, subs))
        counts[kind(name) or "plain"] = counts.get(kind(name) or "plain", 0) + 1
    R.preview(["wall_panel", "quarters_wall", "kitchen_wall", "deck_floor", "station_corridor_floor", "quarters_carpet",
               "hull_plate", "bulkhead", "damaged_wall", "frost_wall", "console_front", "desk_top"])
    print(f"{len(names)} Level 0.1 512px textures (+ _n/_s) -> {R.OUT.relative_to(ROOT)}", counts)


if __name__ == "__main__":
    main()
