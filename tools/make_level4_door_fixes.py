"""Targeted Level 4 door texture & model fixes.

Splits office_fire_door and the 7 two-high Level 4 exit/elevator doors into
dedicated _top and _bottom 128px textures (base + _n/_s maps) and updates their
block models without running make_level4.main() wholesale.

    python tools/make_level4_door_fixes.py
    python tools/make_level4_realistic.py --only office_fire_door office_elevator office_stairwell_5 office_stairwell_6 office_basement_71 office_door_4p3 office_door_11 office_door_13
"""

import json
import sys
from pathlib import Path

import numpy as np

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

import make_level2 as l2  # noqa: E402
import make_level4 as l4  # noqa: E402
import make_textures as mt  # noqa: E402
from make_textures import SIZE, X, Y, box, flat, metal, noise, paint, step  # noqa: E402

TEXTURES = l4.TEXTURES
MODELS = l4.MODELS
NS = l4.NS


def fire_door_half(top: bool):
    """Upper or lower half of the orange-wood office fire door."""
    m = flat((168, 114, 58), smooth=0.34, f0=12, porosity=40)
    grain = noise(410, 64, 16) * 0.08
    m.color *= (1 + grain)[..., None]

    side_frame = 1 - step(4, 6, np.minimum(X, SIZE - X))
    if top:
        bezel = box(44, 18, 76, 110, 0.6)
        paint(m, bezel, (92, 98, 104), height=0.5, smooth=0.50, f0=140)
        slit = box(48, 22, 72, 106, 0.7)
        paint(m, slit, (38, 44, 50), height=-0.6, smooth=0.88, f0=20)
        closer = box(14, 9, 54, 15, 0.5)
        paint(m, closer, (166, 170, 174), height=0.9, smooth=0.65, f0=190)
        frame = np.maximum(side_frame, 1 - step(4, 6, Y))
    else:
        plate = box(90, 12, 108, 46, 0.5)
        paint(m, plate, (188, 192, 196), height=0.7, smooth=0.72, f0=220)
        lever = box(80, 24, 103, 30, 0.5)
        paint(m, lever, (214, 218, 222), height=1.4, smooth=0.82, f0=235)
        lock = 1 - step(2.0, 3.2, np.hypot(X - 99, Y - 38))
        paint(m, lock, (146, 150, 156), height=1.0, smooth=0.75, f0=220)
        kick = step(96, 98, Y)
        paint(m, kick, (42, 46, 52), height=0.5, smooth=0.42, f0=125)
        frame = side_frame

    paint(m, frame, (220, 224, 222), height=1.2, smooth=0.4)
    return m


def elevator_door_half(top: bool):
    """Upper or lower half of the brushed-steel/brass Level 3 elevator doors."""
    m = metal((164, 168, 174), 0.65)
    center = 1 - step(1.5, 2.5, np.abs(X - 64))
    brass_lr = 1 - step(5, 7, np.minimum(X, SIZE - X))
    if top:
        paint(m, center, (54, 58, 62), height=-1.5)
        transom = box(40, 12, 88, 26, 0.5)
        paint(m, transom, (32, 36, 40), height=0.4, smooth=0.8)
        ind = l2.text_mask("3", 12, (64, 19), "arialbd.ttf") * transom
        paint(m, ind, (240, 206, 118), glow=0.65)
        frame = np.maximum(brass_lr, 1 - step(5, 7, Y))
    else:
        sill = step(116, 118, Y)
        paint(m, sill, (74, 78, 84), height=-0.2, smooth=0.45, f0=180)
        paint(m, center * (1 - sill), (54, 58, 62), height=-1.5)
        panel = box(96, 14, 114, 46, 0.5)
        paint(m, panel, (48, 52, 56), height=0.6)
        btn = 1 - step(2, 3.5, np.hypot(X - 105, Y - 30))
        paint(m, btn, (240, 210, 120), glow=0.7)
        frame = brass_lr

    paint(m, frame, (176, 148, 88), height=1.2, smooth=0.65, f0=210)
    return m


def exit_door_half(bg, stripe, label, top: bool):
    """Upper or lower half of a two-high labeled Level 4 exit/stairwell door."""
    m = flat(bg, smooth=0.42, f0=80, porosity=20)
    seam_lr = 1 - step(3, 5, np.minimum(X, SIZE - X))
    if top:
        band = box(10, 34, 118, 68, 0.5)
        paint(m, band, stripe, height=0.4, smooth=0.55)
        txt = l2.text_mask(label, 28 if len(label) <= 2 else 20, (64, 51), "arialbd.ttf") * band
        paint(m, txt, (244, 246, 248), glow=0.35)
        molding = np.maximum(
            box(18, 82, 110, 85, 0.6),
            np.maximum(box(18, 82, 21, 120, 0.6), box(107, 82, 110, 120, 0.6)),
        )
        paint(m, molding * 0.45, tuple(max(0, c - 26) for c in bg), height=-0.4, smooth=0.38)
        seam = np.maximum(seam_lr, 1 - step(3, 5, Y))
    else:
        kick = step(108, 110, Y)
        paint(m, kick, (52, 56, 62), height=0.3, smooth=0.40, f0=120)
        plate = box(94, 14, 110, 38, 0.5)
        paint(m, plate, (178, 182, 188), height=0.7, smooth=0.70, f0=210)
        knob = 1 - step(3.5, 5, np.hypot(X - 102, Y - 24))
        paint(m, knob, (206, 208, 212), height=1.4, smooth=0.80, f0=220)
        seam = seam_lr

    paint(m, seam, (52, 56, 62), height=-0.8)
    return m


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES

    textures = {
        "office_fire_door_top": fire_door_half(top=True),
        "office_fire_door_bottom": fire_door_half(top=False),
        "office_elevator_top": elevator_door_half(top=True),
        "office_elevator_bottom": elevator_door_half(top=False),
    }

    exit_specs = [
        ("office_stairwell_5", (196, 198, 196), (168, 52, 46), "5"),
        ("office_stairwell_6", (62, 64, 68), (40, 42, 46), "6"),
        ("office_basement_71", (118, 120, 118), (74, 78, 82), "71"),
        ("office_door_4p3", (242, 244, 246), (222, 226, 232), "4.3"),
        ("office_door_11", (148, 156, 166), (72, 116, 168), "11"),
        ("office_door_13", (172, 194, 206), (118, 164, 186), "13"),
    ]
    for name, bg, stripe, label in exit_specs:
        textures[f"{name}_top"] = exit_door_half(bg, stripe, label, top=True)
        textures[f"{name}_bottom"] = exit_door_half(bg, stripe, label, top=False)

    for name, material in textures.items():
        mt.save(name, material)

    fire_tex = {"bottom": NS + "office_fire_door_bottom", "top": NS + "office_fire_door_top"}
    fire_models = 0
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for opn in ("", "_open"):
                mname = f"office_fire_door_{half}_{hinge}{opn}"
                write_json(
                    MODELS / f"{mname}.json",
                    {"parent": f"minecraft:block/door_{half}_{hinge}{opn}", "textures": fire_tex},
                )
                fire_models += 1

    two_high_names = ("office_elevator",) + tuple(spec[0] for spec in exit_specs)
    exit_models = 0
    for name in two_high_names:
        for p, suffix in ((0, "bottom"), (1, "top")):
            write_json(
                MODELS / f"{name}_{p}.json",
                {
                    "parent": "minecraft:block/orientable",
                    "textures": {
                        "front": f"{NS}{name}_{suffix}",
                        "side": f"{NS}office_drywall",
                        "top": f"{NS}office_drywall",
                    },
                },
            )
            exit_models += 1

    # Verify every model references existing _top/_bottom base textures + _n/_s maps
    for name in textures:
        for suffix in ("", "_n", "_s"):
            assert (TEXTURES / f"{name}{suffix}.png").is_file(), f"missing {name}{suffix}.png"

    print(
        f"Door fixes complete: {len(textures)} textures (+_n/_s) and "
        f"{fire_models + exit_models} block models updated."
    )


if __name__ == "__main__":
    main()
