"""Realistic 512 px Level 3 (Electrical Station) textures for the built-in "realistic" resource pack.

Every static square Level 3 block texture is redrawn at 512 px with labPBR _n / _s companions in
src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level3/, then resized by
tools/resize_realistic.py to 128 px (whole-block surfaces) or 64 px (props).
Brick walls use make_level2_realistic.brick_substrate (running-bond rectangular brick + Concrete034 grain,
never Bricks075A rubble stone). All photo substrates come from tools/.cache/ambientcg/.

Run: python tools/make_level3_realistic.py
"""
import sys
import zlib

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
import make_level0p1_realistic as Z  # noqa: E402
import make_level2_realistic as R  # noqa: E402

ROOT = R.ROOT
OLD = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level3"
R.OUT = R.PACK / "assets/noclip_backrooms/textures/block/level3"
R.OLD = OLD
R.PREVIEW = ROOT / "docs/look/level3_realistic.png"
Z.OLD = OLD

MATERIAL = [
    (("sludge_steel", "chain", "car_paint", "car_front", "car_rear"), "rust"),
    (("brass", "bronze", "copper", "elevator_"), "brass"),
    (("bars_", "lock", "conduit", "electrical_", "light_housing", "cage", "generator_", "switchgear_",
      "green_steel", "conveyor_side", "press_", "meg_wall", "table_top", "tray", "cables", "base_gamma_door_"), "steel"),
    (("valve", "tyre", "cable_black"), "paint"),
    (("parts_crate_", "meg_crate_"), "wood"),
    (("cot_canvas",), "fabric"),
    (("glazed_tile",), "tile"),
    (("dusty_floor", "station_ceiling", "cinder_block", "pillar", "marble", "sparking_outlet", "purpified_brick"), "concrete"),
]
PLAIN = ("light_lens", "floodlight_lens", "bulb_warm", "bulb_off", "stained_glass", "car_glass",
         "insulator", "meg_sign", "meg_arrow_r", "meg_arrow_l", "meg_warning")


def kind(name):
    if any(k in name for k in PLAIN):
        return None
    for keys, material in MATERIAL:
        if any(k in name for k in keys):
            return material
    return "concrete"


def make_substrates():
    subs = Z.substrates()
    # Fine concrete grain without repeating cloud patches across floors and ceilings.
    c = subs["concrete"]
    c.color = c.color - R.blur(c.color, 12) + c.color.mean(axis=(0, 1))
    Z.GRAIN["concrete"] = 0.045
    subs["tile"] = R.Material(c.color.copy(), height=c.height.copy(), normal=None if c.normal is None else c.normal.copy(),
                              smooth=0.72, f0=14, porosity=0.08, bump=16.0)
    Z.GRAIN["tile"] = 0.035
    subs["brass"] = R.load_pbr_substrate("Metal049A", R.rgb(186, 152, 92), contrast=0.75, normal_strength=0.55,
                                         default_smooth=0.55, f0=220, porosity=0.05)
    Z.GRAIN["brass"] = 0.07
    return subs


def shifted_subs(whole, name):
    shift = zlib.crc32(name.encode()) % R.S, zlib.crc32(name[::-1].encode()) % R.S
    out = {}
    for material, sub in whole.items():
        moved = R.Material(sub.color, height=sub.height, normal=sub.normal, smooth=sub.smooth, f0=sub.f0, porosity=sub.porosity)
        for attr in ("color", "height", "normal", "smooth"):
            value = getattr(moved, attr)
            if isinstance(value, np.ndarray) and value.ndim >= 2:
                setattr(moved, attr, np.roll(value, shift, axis=(0, 1)))
        out[material] = moved
    return out, shift


def make_brick(name, target_rgb, mortar_rgb, base_seed, shift):
    k = int(name.rsplit("_", 1)[-1]) if name[-1].isdigit() else 0
    m = R.brick_substrate(target_rgb, seed=base_seed + k, contrast=0.85, mortar_rgb=mortar_rgb,
                          default_smooth=0.14, porosity=0.65, shift=shift)
    soot = R.smoothstep(0.45, 0.85, R.fbm(base_seed + 50 + k, base=3, octaves=4)) * 0.28
    m.color = R.mix(m.color, m.color * R.rgb(160, 152, 144), soot)
    return m


def make_sludge_puddle():
    src = Image.open(OLD / "sludge_puddle.png").convert("RGBA")
    rgb = np.asarray(src.convert("RGB").resize((R.S, R.S), Image.LANCZOS), dtype=np.float32) / 255
    alpha = np.asarray(src.getchannel("A").resize((R.S, R.S), Image.NEAREST), dtype=np.float32) / 255
    ripple = R.fbm(3301, base=6, octaves=4) * 0.12
    return R.Material(rgb, height=0.35 + ripple, smooth=0.95, f0=18, porosity=0.05, bump=14.0, alpha=alpha)


def main():
    whole = make_substrates()
    Z.kind = kind
    names = []
    for f in sorted(OLD.glob("*.png")):
        if f.stem.endswith(("_n", "_s")) or f.with_suffix(".png.mcmeta").exists():
            continue
        with Image.open(f) as image:
            if image.width != image.height:
                continue
        names.append(f.stem)

    counts = {}
    for name in names:
        subs, shift = shifted_subs(whole, name)
        if name.startswith("red_brick"):
            mat = make_brick(name, R.rgb(146, 78, 52), R.rgb(68, 55, 46), 3100, shift)
            tag = "brick_red"
        elif name.startswith("brown_brick"):
            mat = make_brick(name, R.rgb(112, 82, 58), R.rgb(62, 52, 44), 3200, shift)
            tag = "brick_brown"
        elif name == "sludge_puddle":
            mat = make_sludge_puddle()
            tag = "sludge"
        else:
            mat = Z.composite(name, subs)
            tag = kind(name) or "plain"
        R.save(name, mat)
        counts[tag] = counts.get(tag, 0) + 1

    R.preview(["red_brick", "brown_brick", "glazed_tile", "cinder_block", "purpified_brick", "dusty_floor",
               "electrical_front", "switchgear_front", "brass", "bronze", "pillar", "elevator_0"])
    print(f"{len(names)} Level 3 512px textures (+ _n/_s) -> {R.OUT.relative_to(ROOT)}", counts)


if __name__ == "__main__":
    main()
    import resize_realistic  # noqa: E402
    resize_realistic.main()
