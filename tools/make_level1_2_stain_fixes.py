"""Targeted Level 1 & Level 2 stain & repetition fixes (Batch 3).

Fixes stamp-like damp_garage_floor (Level 1), single-texture/camo-like utility_floor,
damp_utility_floor, puddled_utility_floor, utility_ceiling, and harsh stained_utility_wall
(Level 2) — all of which are also reused in Level 4's Unfinished Wing, Sub-Tunnel, and Plenum.

Does NOT touch pack.mcmeta, omega_cot.json, or any unrelated Level 0/1/2/3/4 assets.

    python tools/make_level1_2_stain_fixes.py
"""

import json
import sys
import time
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

import make_textures as mt  # noqa: E402
import make_level1 as l1  # noqa: E402
import make_level2 as l2  # noqa: E402
import make_level2_realistic as R  # noqa: E402
import make_level0p1_realistic as Z  # noqa: E402
import resize_realistic as RR  # noqa: E402
from make_textures import SIZE, X, Y, clouds, inside, noise, paint, step, tint  # noqa: E402

ROOT = TOOLS.parent
ASSETS = mt.ASSETS
STATES = ASSETS / "blockstates"
REALISTIC_PACK = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block"


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def resize_target_realistic(folder: Path, names):
    """Downscale generated 512px PNGs (+ _n/_s) cleanly to 128x128 surface resolution."""
    for name in names:
        for suf in ("", "_n", "_s"):
            p = folder / f"{name}{suf}.png"
            for attempt in range(5):
                try:
                    if suf == "_s":
                        with Image.open(p) as raw:
                            img = raw.convert("RGBA")
                        if img.width > RR.SURFACE:
                            rgb = img.convert("RGB").resize((RR.SURFACE, RR.SURFACE), Image.Resampling.BOX)
                            a = img.getchannel("A").resize((RR.SURFACE, RR.SURFACE), Image.Resampling.NEAREST)
                            r, g, b = rgb.split()
                            Image.merge("RGBA", (r, g, b, a)).save(p)
                    else:
                        RR.resize(p, RR.SURFACE, False)
                    break
                except OSError:
                    if attempt == 4:
                        raise
                    time.sleep(0.25)


# --------------------------------------------------------------------------- Level 1: damp_garage_floor

def damp_garage_base(variant):
    """Seamless / edge-flowing damp concrete matching Level 1 garage_floor (118, 118, 114)."""
    seed = 300 + variant * 10
    m = l1.concrete((118, 118, 114), seed)
    if variant == 0:
        # Broad seamless damp concrete sheet
        sheet = clouds(380, 2) * 0.65 + clouds(381, 5) * 0.35
        mask = step(0.05, 0.56, sheet)
        tint(m, mask * 0.72, (0.76, 0.78, 0.80))
        paint(m, mask, smooth=0.52, porosity=35)
    elif variant == 1:
        # Edge-creeping water seepage flowing softly across one side/corner
        flow = 1 - step(20, 96, X * 0.74 + Y * 0.44 + clouds(382, 3) * 32.0)
        mottle = step(-0.1, 0.5, clouds(383, 5))
        mask = np.clip(flow * (0.55 + 0.45 * mottle), 0, 1)
        tint(m, mask * 0.76, (0.72, 0.74, 0.77))
        paint(m, mask, smooth=0.68, porosity=20)
    elif variant == 2:
        # Broad mild dampness + soft off-center deeper wet patch
        base = step(0.02, 0.55, clouds(384, 2))
        tint(m, base * 0.48, (0.81, 0.83, 0.85))
        dist = np.hypot(X - 46, Y - 76) + clouds(385, 4) * 26.0
        pool = (1 - step(16, 44, dist)) * inside()
        tint(m, pool * 0.78, (0.70, 0.72, 0.75))
        paint(m, np.clip(base * 0.45 + pool, 0, 1), smooth=0.76, porosity=15)
    elif variant == 3:
        # Soft diagonal wet tire-drag / runoff band
        band_dist = np.abs((X - 64) * 0.64 - (Y - 64) * 0.76 + clouds(386, 3) * 22.0)
        band = 1 - step(12, 40, band_dist)
        feather = step(-0.15, 0.5, clouds(387, 5))
        mask = np.clip(band * (0.5 + 0.5 * feather), 0, 1)
        tint(m, mask * 0.72, (0.78, 0.80, 0.82))
        paint(m, mask, smooth=0.46, porosity=45)
    else:
        # Mild overall damp concrete sheen
        mask = step(0.0, 0.62, clouds(388, 2) * 0.7 + noise(389, 6) * 0.3)
        tint(m, mask * 0.58, (0.83, 0.85, 0.87))
        paint(m, mask, smooth=0.36, porosity=60)
    return m


def fix_level1():
    tex_dir = ASSETS / "textures/block/level1"
    mod_dir = ASSETS / "models/block/level1"
    mt.OUT = tex_dir
    names = ["damp_garage_floor", "damp_garage_floor_1", "damp_garage_floor_2",
             "damp_garage_floor_3", "damp_garage_floor_4"]

    for k, name in enumerate(names):
        mt.save(name, damp_garage_base(k))

    # Realistic 512px -> 128px using exact make_level1_realistic concrete pipeline
    subs = Z.substrates()
    c = subs["concrete"]
    c.color = c.color - R.blur(c.color, 12) + c.color.mean(axis=(0, 1))
    Z.GRAIN["concrete"] = 0.045
    Z.OLD = tex_dir
    Z.kind = lambda _name: "concrete"
    R.OUT = REALISTIC_PACK / "level1"

    whole_c = subs["concrete"]
    for name in names:
        shift = zlib.crc32(name.encode()) % R.S, zlib.crc32(name[::-1].encode()) % R.S
        moved = R.Material(
            np.roll(whole_c.color, shift, axis=(0, 1)),
            height=np.roll(whole_c.height, shift, axis=(0, 1)),
            normal=None if whole_c.normal is None else np.roll(whole_c.normal, shift, axis=(0, 1)),
            smooth=np.roll(whole_c.smooth, shift, axis=(0, 1)),
            f0=whole_c.f0,
            porosity=whole_c.porosity,
        )
        R.save(name, Z.composite(name, {"concrete": moved}))

    resize_target_realistic(REALISTIC_PACK / "level1", names)

    # New models damp_garage_floor_3 and _4 MUST use cube_bottom_top (keep 0..2 untouched)
    for name in ("damp_garage_floor_3", "damp_garage_floor_4"):
        write_json(mod_dir / f"{name}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": f"noclip_backrooms:block/level1/{name}",
                "side": "noclip_backrooms:block/level1/garage_floor",
                "bottom": "noclip_backrooms:block/level1/garage_floor",
            },
        })

    weights = [(0, 3), (1, 2), (2, 2), (3, 2), (4, 3)]
    entries = []
    for k, w in weights:
        mname = f"noclip_backrooms:block/level1/{names[k]}"
        for y in (0, 90, 180, 270):
            e = {"model": mname, "weight": w}
            if y:
                e["y"] = y
            entries.append(e)
    write_json(STATES / "damp_garage_floor.json", {"variants": {"": entries}})
    return names


# --------------------------------------------------------------------------- Level 2: utility surfaces

def roll_mat(m, shift):
    """Clone a make_level2_realistic.Material rolled along (Y, X)."""
    out = m.copy()
    out.color = np.roll(out.color, shift, axis=(0, 1))
    out.height = np.roll(out.height, shift, axis=(0, 1))
    out.smooth = np.roll(out.smooth, shift, axis=(0, 1))
    if out.normal is not None:
        out.normal = np.roll(out.normal, shift, axis=(0, 1))
    return out


def apply_soft_damp_patches(m, seed, coverage=0.45, puddle=False, dark_mult=0.74):
    """Version of R.apply_damp_patches with controllable contrast so damp floors blend seamlessly."""
    n = R.fbm(seed, base=3, octaves=5)
    mask = R.smoothstep(1 - coverage - 0.12, 1 - coverage + 0.08, n)
    m.color = R.mix(m.color, m.color * dark_mult, mask)
    m.smooth = np.maximum(m.smooth, mask * (0.94 if puddle else 0.78))
    m.porosity = np.maximum(m.porosity, mask * 0.85)
    if puddle:
        deep = R.smoothstep(0.62, 0.78, n)
        water_col = R.rgb(36, 48, 56)
        rim = R.smoothstep(0.57, 0.62, n) * (1 - deep)
        m.color = R.mix(m.color, water_col, deep * 0.72)
        m.color = R.mix(m.color, m.color * 1.22, rim * 0.42)
        m.smooth = np.maximum(m.smooth, deep * 0.97)
        m.f0 = np.maximum(m.f0, deep * 16)
        m.height = R.mix(m.height, 0.38, deep * 0.85)
        if m.normal is not None:
            m.normal = m.normal * (1 - deep[..., None] * 0.9)
    return m


def soften_macro_clouds(mat):
    """Blend 65% high-passed grain + 35% original to tame harsh repeating macro blotches."""
    mean = mat.color.mean(axis=(0, 1), keepdims=True)
    hp = np.clip(mat.color - R.blur(mat.color, 24) + mean, 0, 1)
    mat.color = R.mix(mat.color, hp, 0.65)
    return mat


def damp_utility_base(variant):
    """Base 128px damp_utility_floor using seamless/edge-spanning moisture."""
    m = l2.floor(570 + variant * 11)
    if variant in (0, 2):
        sheet = clouds(571 + variant, 2) * 0.6 + clouds(572 + variant, 5) * 0.4
        mask = step(0.05, 0.55, sheet)
    else:
        mask = np.clip((1 - step(24, 98, X * 0.7 + Y * 0.45 + clouds(573 + variant, 3) * 30.0)), 0, 1)
    tint(m, mask * 0.68, (0.74, 0.76, 0.78))
    paint(m, mask, smooth=0.65, porosity=20)
    return m


def puddle_utility_base(variant):
    """Base 128px puddled_utility_floor with varied organic puddle shapes."""
    m = l2.floor(590 + variant * 11)
    sheet = clouds(591 + variant * 3, 3) * 0.7 + noise(592 + variant * 3, 16) * 0.3
    damp = step(0.0, 0.48, sheet)
    tint(m, damp * 0.6, (0.74, 0.76, 0.78))
    cx, cy = [(52, 60), (74, 48), (44, 78), (68, 72)][variant]
    d = np.hypot(X - cx, Y - cy) + clouds(593 + variant * 3, 5) * 18.0
    puddle = (1 - step(18, 34, d)) * inside()
    paint(m, puddle, (48, 56, 62), height=-0.4, smooth=0.95, f0=16, porosity=0)
    return m


def fix_level2():
    tex_dir = ASSETS / "textures/block/level2"
    mod_dir = ASSETS / "models/block/level2"
    mt.OUT = tex_dir

    # 1. Base 128px textures
    floor_names = ["utility_floor", "utility_floor_1", "utility_floor_2", "utility_floor_3"]
    damp_names = ["damp_utility_floor", "damp_utility_floor_1", "damp_utility_floor_2", "damp_utility_floor_3"]
    puddle_names = ["puddled_utility_floor", "puddled_utility_floor_1", "puddled_utility_floor_2", "puddled_utility_floor_3"]
    ceil_names = ["utility_ceiling", "utility_ceiling_1", "utility_ceiling_2"]
    wall_names = ["stained_utility_wall", "stained_utility_wall_1", "stained_utility_wall_2", "stained_utility_wall_3"]

    for k, name in enumerate(floor_names):
        mt.save(name, l2.floor(550 + k * 11))
    for k, name in enumerate(damp_names):
        mt.save(name, damp_utility_base(k))
    for k, name in enumerate(puddle_names):
        mt.save(name, puddle_utility_base(k))
    for k, name in enumerate(ceil_names):
        mt.save(name, l2.ceiling(610 + k * 13))
    for k, name in enumerate(wall_names):
        mt.save(name, l2.stained(510 + k * 17))

    # 2. Realistic 512px -> 128px using ambientCG cache via make_level2_realistic helpers
    R.OUT = REALISTIC_PACK / "level2"
    clean = R.blur(R.load(R.source("Wallpaper002A")["Color"], gray=True), 1)
    WATER = R.smoothstep(0.03, 0.22, clean - R.blur(R.load(R.source("Wallpaper002B")["Color"], gray=True), 1))
    MOULD = R.smoothstep(0.05, 0.35, clean - R.blur(R.load(R.source("Wallpaper002C")["Color"], gray=True), 1))

    floor_raw = soften_macro_clouds(
        R.load_pbr_substrate("Concrete019", R.rgb(118, 120, 114), contrast=0.82, normal_strength=0.9, default_smooth=0.18, porosity=0.45)
    )
    ceil_raw = soften_macro_clouds(
        R.load_pbr_substrate("Concrete019", R.rgb(68, 70, 72), contrast=0.88, normal_strength=0.95, default_smooth=0.14, porosity=0.5)
    )
    wall_raw = R.load_pbr_substrate("Concrete034", R.rgb(138, 138, 132), contrast=0.85, normal_strength=0.95, default_smooth=0.14, porosity=0.5)
    form_line = R.smoothstep(1.5, 0.0, np.abs((R.Y % 128) - 1)) * 0.35
    wall_raw.color *= (1 - form_line[..., None] * 0.18)
    wall_raw.height -= form_line * 0.08

    shifts = [(k * 173 % R.S, k * 291 % R.S) for k in range(4)]
    joint = np.maximum(
        R.smoothstep(2.0, 0.0, np.abs(R.X - R.S / 2)),
        R.smoothstep(2.0, 0.0, np.abs(R.Y - R.S / 2)),
    ) * 0.22

    # Dry utility_floor (4 variants)
    for k, name in enumerate(floor_names):
        m = roll_mat(floor_raw, shifts[k])
        if k in (0, 2):
            m.color *= (1 - joint[..., None] * 0.22)
            m.height -= joint * 0.08
        micro_grime = R.smoothstep(0.25, 0.78, R.fbm(550 + k * 7, base=4, octaves=4))
        m.color = R.mix(m.color, m.color * R.rgb(228, 224, 214), micro_grime * 0.22)
        R.save(name, m)

    # Damp utility_floor (4 variants, softer contrast)
    damp_covs = (0.42, 0.54, 0.36, 0.48)
    for k, name in enumerate(damp_names):
        m = roll_mat(floor_raw, shifts[k])
        if k == 0:
            m.color *= (1 - joint[..., None] * 0.20)
            m.height -= joint * 0.07
        R.save(name, apply_soft_damp_patches(m, seed=21 + k * 13, coverage=damp_covs[k], puddle=False, dark_mult=0.74))

    # Puddled utility_floor (4 variants)
    puddle_covs = (0.58, 0.48, 0.66, 0.52)
    for k, name in enumerate(puddle_names):
        m = roll_mat(floor_raw, shifts[k])
        R.save(name, apply_soft_damp_patches(m, seed=31 + k * 13, coverage=puddle_covs[k], puddle=True, dark_mult=0.72))

    # Utility ceiling (3 variants)
    for k, name in enumerate(ceil_names):
        m = roll_mat(ceil_raw, shifts[k])
        offset = k * 14.0
        cable_shadow = np.clip(np.sin((R.X + offset) / 42.0 * np.pi) ** 12, 0, 1) * (0.36 + 0.08 * (k == 0))
        m.color = R.mix(m.color, R.rgb(34, 36, 40), cable_shadow)
        m.height += cable_shadow * 0.16
        if k > 0:
            grime = R.smoothstep(0.3, 0.8, R.fbm(610 + k * 9, base=3, octaves=4))
            m.color = R.mix(m.color, m.color * 0.82, grime * 0.25)
        R.save(name, m)

    # Stained utility_wall (4 restrained, natural variants)
    stain_opacities = (0.56, 0.52, 0.62, 0.64)
    mould_opacities = (0.38, 0.34, 0.44, 0.48)
    water_shifts = ((90, 40), (210, 180), (45, 310), (320, 120))
    mould_shifts = ((0, 0), (160, 70), (280, 30), (95, 220))
    for k, name in enumerate(wall_names):
        m = roll_mat(wall_raw, shifts[k])
        w_roll = np.roll(WATER, water_shifts[k], axis=(0, 1))
        m_roll = np.roll(MOULD, mould_shifts[k], axis=(0, 1))
        stain_mask = R.smoothstep(0.18, 0.68, w_roll * 0.85 + R.fbm(12 + k * 7, base=2) * 0.35)
        mould_mask = R.smoothstep(0.40, 0.78, m_roll * (0.4 + 0.6 * R.T))
        m.color = R.mix(m.color, m.color * R.rgb(174, 160, 138), stain_mask * stain_opacities[k])
        m.color = R.mix(m.color, R.rgb(66, 72, 58), mould_mask * mould_opacities[k])
        m.porosity = np.maximum(m.porosity, stain_mask * 0.65)
        if k == 2:
            # Subtle vertical leak streaks from top edge
            streaks = R.smoothstep(0.55, 0.82, R.blur(R.fbm(71, base=12, octaves=3), (12.0, 1.5))) * (1 - R.T * 0.65)
            m.color = R.mix(m.color, m.color * R.rgb(168, 156, 140), streaks * 0.38)
        elif k == 3:
            # Lower-edge dampness creep
            low = R.smoothstep(0.58, 0.96, R.T + R.fbm(75, base=4) * 0.16)
            m.color = R.mix(m.color, m.color * 0.78, low * 0.45)
            m.smooth = np.maximum(m.smooth, low * 0.34)
        R.save(name, m)

    all_l2_names = floor_names + damp_names + puddle_names + ceil_names + wall_names
    resize_target_realistic(REALISTIC_PACK / "level2", all_l2_names)

    # 3. Models for new Level 2 variants (keep existing base + stained_utility_wall_mirrored intact)
    new_l2_models = (
        floor_names[1:] + damp_names[1:] + puddle_names[1:] + ceil_names[1:] + wall_names[1:]
    )
    for name in new_l2_models:
        write_json(mod_dir / f"{name}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"noclip_backrooms:block/level2/{name}"},
        })

    # 4. Blockstates
    def rotated_entries(mnames):
        out = []
        for mname in mnames:
            for y in (0, 90, 180, 270):
                e = {"model": f"noclip_backrooms:block/level2/{mname}"}
                if y:
                    e["y"] = y
                out.append(e)
        return out

    write_json(STATES / "utility_floor.json", {"variants": {"": rotated_entries(floor_names)}})
    write_json(STATES / "damp_utility_floor.json", {"variants": {"": rotated_entries(damp_names)}})
    write_json(STATES / "puddled_utility_floor.json", {"variants": {"": rotated_entries(puddle_names)}})
    write_json(STATES / "utility_ceiling.json", {"variants": {"": rotated_entries(ceil_names)}})
    write_json(STATES / "stained_utility_wall.json", {"variants": {"": [
        {"model": "noclip_backrooms:block/level2/stained_utility_wall", "weight": 2},
        {"model": "noclip_backrooms:block/level2/stained_utility_wall_mirrored", "weight": 2},
        {"model": "noclip_backrooms:block/level2/stained_utility_wall_1", "weight": 3},
        {"model": "noclip_backrooms:block/level2/stained_utility_wall_2", "weight": 3},
        {"model": "noclip_backrooms:block/level2/stained_utility_wall_3", "weight": 3},
    ]}})
    return all_l2_names


def verify(l1_names, l2_names):
    checked_states = (
        "damp_garage_floor",
        "utility_floor",
        "damp_utility_floor",
        "puddled_utility_floor",
        "utility_ceiling",
        "stained_utility_wall",
    )
    prefix = "noclip_backrooms:block/"
    for bs_name in checked_states:
        state = json.loads((STATES / f"{bs_name}.json").read_text(encoding="utf8"))
        for entry in state["variants"].values():
            for v in (entry if isinstance(entry, list) else [entry]):
                mpath = ASSETS / f"models/block/{v['model'][len(prefix):]}.json"
                assert mpath.is_file(), f"Missing model: {mpath}"
                mdata = json.loads(mpath.read_text(encoding="utf8"))
                for tval in mdata.get("textures", {}).values():
                    if tval.startswith(prefix):
                        rel_tex = tval[len(prefix):]
                        for suf in ("", "_n", "_s"):
                            btex = ASSETS / f"textures/block/{rel_tex}{suf}.png"
                            assert btex.is_file(), f"Missing base texture: {btex}"

    for lvl, names in (("level1", l1_names), ("level2", l2_names)):
        for name in names:
            for suf in ("", "_n", "_s"):
                rtex = REALISTIC_PACK / lvl / f"{name}{suf}.png"
                assert rtex.is_file(), f"Missing realistic texture: {rtex}"
                with Image.open(rtex) as im:
                    assert im.size == (RR.SURFACE, RR.SURFACE), f"Unexpected size {im.size} for {rtex}"


def main():
    l1_names = fix_level1()
    l2_names = fix_level2()
    verify(l1_names, l2_names)
    print(f"Batch 3 complete: {len(l1_names)} Level 1 + {len(l2_names)} Level 2 textures (base + 128px realistic + _n/_s) & 6 blockstates verified.")


if __name__ == "__main__":
    main()
