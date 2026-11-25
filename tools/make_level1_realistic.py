"""Realistic 512 px Level 1 (the parking garage) textures for the built-in "realistic" resource pack.

Every square Level 1 block texture is redrawn at 512 px with labPBR _n / _s companions in
src/main/resources/resourcepacks/realistic/assets/noclip_backrooms/textures/block/level1/.
Same method as make_level0p1_realistic.py: the drawn design (lane lines, stains, panels, signs, glowing parts) stays,
and the surface comes from a CC0 ambientCG photo material picked by what the block is made of. The photos are the ones
make_level2_realistic.py already caches in tools/.cache/ambientcg/.
The animated flickering wallpaper (a frame strip) is left to the 128 px original.

Run: python tools/make_level1_realistic.py
"""
import sys

from PIL import Image

sys.dont_write_bytecode = True
import make_level2_realistic as R  # noqa: E402
import make_level0p1_realistic as Z  # noqa: E402

ROOT = R.ROOT
OLD = ROOT / "src/main/resources/assets/noclip_backrooms/textures/block/level1"
R.OUT = R.PACK / "assets/noclip_backrooms/textures/block/level1"
R.OLD = OLD
R.PREVIEW = ROOT / "docs/look/level1_realistic.png"
Z.OLD = OLD  # Z.composite reads the 128 px drawing and its _s map from here

# What each texture is made of: the first name fragment that matches wins.
MATERIAL = [
    (("rust", "frozen", "reinforced"), "rust"),
    (("bed_sheet", "clinic_sheet", "bedroll", "cot_canvas", "tarp", "padded"), "fabric"),
    (("crate", "desk_top", "desk_leg", "door_frame_wood", "legend_floor", "legend_ceiling", "shelf_top", "book_shelf", "root_door",
      "white_wood_door", "signup_booth", "cardboard", "counter", "painting_frame", "booth"), "wood"),
    (("door_jamb", "door_knob", "metal_door", "shelf_frame", "shelf_board", "generator", "drum", "barrier", "locker", "pipe",
      "scaffold", "copper", "bunker_door", "elevator_door", "light_housing", "bulb_socket", "jersey", "computer_case", "site_machine",
      "wall_vent", "pickaxe_door", "cold_door", "sign_back", "barrel"), "steel"),
    (("car_paint", "van_paint", "vending_shell", "white_door", "automatic_door", "cubicle", "symbol_door", "construction_cone",
      "tyre", "dark_plastic"), "paint"),
    (("old_brick", "gothic_brick", "gild_wall", "garden_wall"), "brick"),
]
# Glass, lamps, screens, neon, signs with artwork, plants and marks: keep the drawing, only sharpened to 512 px.
PLAIN = ("glass", "lens", "lamp", "bulb_glass", "screen", "neon", "glitch", "_sign", "sign_frame", "painting", "phosphor", "marker",
         "garden_fern", "garden_flower", "garden_grass", "moss", "leafy", "twigs", "soil", "pot", "darkness", "outdoor_gap", "embers",
         "tail_light", "bulb_cord", "wastebasket", "floodlight", "gauge")


def kind(name):
    if name in ("palace_painting", "floor_painting"):
        return None
    if name.endswith("_sign") or any(k in name for k in PLAIN):
        return None
    for keys, material in MATERIAL:
        if any(k in name for k in keys):
            return material
    return "concrete"  # the garage itself: floors, walls, pillars, ceilings, painted concrete, the sectors' stone


def main():
    subs = Z.substrates()
    # Only the photo's fine grain for concrete: its broad patches would repeat block after block across a garage floor.
    c = subs["concrete"]
    c.color = c.color - R.blur(c.color, 12) + c.color.mean(axis=(0, 1))
    Z.GRAIN["concrete"] = 0.045
    subs["brick"] = R.load_pbr_substrate("Bricks075A", R.rgb(140, 120, 104), contrast=0.7, normal_strength=0.8, default_smooth=0.15, porosity=0.6)
    Z.GRAIN.setdefault("brick", 0.08)
    Z.kind = kind  # Z.composite asks Z.kind which photo to lay over each drawing
    names = []
    for f in sorted(OLD.glob("*.png")):
        if f.stem.endswith(("_n", "_s")) or f.with_suffix(".png.mcmeta").exists():
            continue
        with Image.open(f) as image:
            if image.width != image.height:
                continue
        names.append(f.stem)
    counts = {}
    import zlib
    import numpy as np
    whole = dict(subs)
    for name in names:
        # Each texture takes its own crop of the photo, so the variants of one block do not share the same marks.
        shift = zlib.crc32(name.encode()) % R.S, zlib.crc32(name[::-1].encode()) % R.S
        for material, sub in whole.items():
            moved = R.Material(sub.color, height=sub.height, normal=sub.normal, smooth=sub.smooth, f0=sub.f0, porosity=sub.porosity)
            for attr in ("color", "height", "normal", "smooth"):
                value = getattr(moved, attr)
                if isinstance(value, np.ndarray) and value.ndim >= 2:
                    setattr(moved, attr, np.roll(value, shift, axis=(0, 1)))
            subs[material] = moved
        R.save(name, Z.composite(name, subs))
        counts[kind(name) or "plain"] = counts.get(kind(name) or "plain", 0) + 1
    R.preview(["garage_floor", "garage_floor_line", "garage_wall", "garage_pillar", "garage_ceiling", "corridor_floor",
               "painted_wall", "old_brick", "crate_side", "metal_door", "car_paint", "legend_floor"])
    print(f"{len(names)} Level 1 512px textures (+ _n/_s) -> {R.OUT.relative_to(ROOT)}", counts)


if __name__ == "__main__":
    main()
