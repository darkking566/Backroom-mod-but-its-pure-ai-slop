"""Draws Manila Room block textures (128x128 + labPBR _n/_s), Almond Water sprite (16x16),
and generates blockstates, block/item models, and loot tables for Manila Room assets.

Run:
    python tools/make_manila.py
"""

import json
from pathlib import Path
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_textures as mt

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
DATA = ROOT / "src/main/resources/data/noclip_backrooms"
BLOCK_OUT = ASSETS / "textures/block"
ITEM_OUT = ASSETS / "textures/item"
SIZE = mt.SIZE
X, Y = mt.X, mt.Y


def manila_wallpaper():
    m = mt.flat((194, 158, 108), smooth=0.24, f0=10, porosity=24)
    fibre = mt.noise(71, 128, 48) * 0.55 + mt.noise(72, 64, 128) * 0.45
    grain = mt.clouds(73, 8)
    m.color *= (1 + fibre * 0.045 + grain * 0.03)[..., None]
    m.height += fibre * 0.18 + grain * 0.12

    col = X % 64
    mt.paint(m, mt.box_x(col, 0, 1.4), (166, 131, 84), height=-0.45, smooth=0.20)
    mt.paint(m, mt.box_x(col, 1.8, 2.8), (208, 174, 124), height=0.3, smooth=0.28)
    return m


def manila_wood_plank(base=(136, 94, 56), dark=(104, 68, 38), light=(158, 114, 72), seed=80, vertical=False):
    m = mt.flat(base, smooth=0.46, f0=14, porosity=8)
    u, v = (X, Y) if vertical else (Y, X)
    plank_idx = (u // 32).astype(int)
    in_plank = u % 32
    rng = np.random.default_rng(seed)
    plank_tones = rng.uniform(0.90, 1.10, 4)
    for idx in range(4):
        mask = (plank_idx == idx).astype(float)
        m.color *= (1 + (plank_tones[idx] - 1) * mask)[..., None]

    offset = (plank_idx * 37) % 128
    v_shifted = (v + offset) % 128
    grain_wave = np.sin((u % 32) * 0.6 + np.sin(v_shifted * 0.12) * 2.2) * 0.5 + 0.5
    fine = mt.noise(seed + 1, 16 if vertical else 128, 128 if vertical else 16)
    m.color += (np.array(light) - np.array(base)) * (grain_wave * 0.35)[..., None]
    m.color += (np.array(dark) - np.array(base)) * (np.clip(-fine, 0, 1) * 0.35)[..., None]
    m.height += (grain_wave - 0.5) * 0.25 + fine * 0.15

    seam = np.clip((in_plank < 1.8).astype(float) + (in_plank > 30.8).astype(float), 0, 1)
    mt.paint(m, seam, dark, height=-0.9, smooth=0.18)

    joint_pos = np.where((plank_idx % 2) == 0, 16.0, 80.0)
    joint = (np.abs(v - joint_pos) < 1.2).astype(float)
    mt.paint(m, joint, dark, height=-0.8, smooth=0.18)
    return m


def manila_light():
    m = mt.metal((194, 174, 146), smooth=0.5)
    housing = mt.box(4, 4, 124, 124, soft=1.5)
    mt.paint(m, housing, (226, 204, 166), height=0.5, smooth=0.6)
    face = mt.box(10, 10, 118, 118, soft=2.0)
    dist = np.hypot((X - 64) / 56.0, (Y - 64) / 56.0)
    warmth = np.clip(1.0 - dist * 0.28, 0.72, 1.0)
    glow_color = np.dstack([255 * warmth, 224 * warmth, 168 * warmth])
    m.color += (glow_color - m.color) * face[..., None]
    m.height += face * 0.9
    m.smooth += (0.85 - m.smooth) * face
    m.glow += 0.88 * face
    return m


def void_stone():
    m = mt.flat((16, 14, 22), smooth=0.18, f0=18, porosity=12)
    c1 = mt.clouds(91, 8)
    c2 = mt.noise(92, 64, 64)
    flecks = np.clip(mt.noise(93, 32, 32) - 0.65, 0, 1) * 2.8
    m.color += (np.array([34, 28, 48]) - m.color) * np.clip(c1 * 0.5 + 0.3, 0, 1)[..., None]
    m.color += (np.array([56, 42, 78]) - m.color) * flecks[..., None]
    m.height += c1 * 0.9 + c2 * 0.45
    m.glow += flecks * 0.12
    return m


def manila_door_leaf(top=False):
    m = manila_wood_plank(base=(128, 84, 48), dark=(88, 54, 28), light=(152, 106, 66), seed=101 if not top else 102, vertical=True)
    if top:
        outer = mt.box(18, 14, 110, 112, soft=1.0)
        inner = mt.box(24, 20, 104, 106, soft=1.0)
        mt.paint(m, outer * (1 - inner), (92, 58, 32), height=-0.9, smooth=0.35)
        mt.paint(m, inner, (118, 76, 44), height=-0.4, smooth=0.45)
    else:
        outer = mt.box(18, 16, 110, 114, soft=1.0)
        inner = mt.box(24, 22, 104, 108, soft=1.0)
        mt.paint(m, outer * (1 - inner), (92, 58, 32), height=-0.9, smooth=0.35)
        mt.paint(m, inner, (118, 76, 44), height=-0.4, smooth=0.45)
        plate = mt.box(96, 4, 108, 22, soft=0.6)
        knob = 1 - mt.step(3.2, 4.4, np.hypot(X - 102, Y - 13))
        mt.paint(m, plate, (186, 152, 68), height=0.8, smooth=0.82, f0=220)
        mt.paint(m, knob, (218, 182, 86), height=1.8, smooth=0.90, f0=235)
    return m


def manila_cabinet_front(opened=False):
    m = manila_wood_plank(base=(122, 80, 44), dark=(84, 52, 26), light=(144, 98, 58), seed=112, vertical=False)
    for top, bottom in ((14, 58), (68, 112)):
        frame = mt.box(12, top, 116, bottom, soft=1.0)
        inset = mt.box(16, top + 4, 112, bottom - 4, soft=1.0)
        mt.paint(m, frame * (1 - inset), (76, 46, 24), height=-0.8, smooth=0.25)
        if opened and top == 14:
            mt.paint(m, inset, (42, 28, 18), height=-2.0, smooth=0.15)
        else:
            mt.paint(m, inset, (134, 90, 52), height=0.4, smooth=0.48)
            handle = mt.box(52, (top + bottom) / 2 - 3, 76, (top + bottom) / 2 + 3, soft=0.6)
            mt.paint(m, handle, (206, 172, 82), height=1.4, smooth=0.88, f0=230)
    return m


def manila_chair_cushion():
    m = mt.flat((114, 108, 84), smooth=0.14, f0=8, porosity=42)
    weave = (np.sin(X * 0.9) * np.sin(Y * 0.9)) * 0.5 + mt.noise(121, 64, 64) * 0.6
    m.color *= (1 + weave * 0.05)[..., None]
    m.height += weave * 0.22
    border = 1 - mt.box(8, 8, 120, 120, soft=2.0)
    mt.paint(m, border, (92, 86, 66), height=-0.5)
    return m


def manila_paper():
    m = mt.flat((198, 174, 134), smooth=0.24, f0=10, porosity=20)
    sheet = mt.box(18, 14, 110, 114, soft=0.8)
    mt.paint(m, sheet, (238, 231, 214), height=0.4, smooth=0.22)
    for row in range(28, 100, 10):
        line_len = 82 if row != 88 else 56
        line = mt.box(28, row, 28 + line_len, row + 3.2, soft=0.4)
        mt.paint(m, line, (68, 62, 54), height=0.45)
    stamp = 1 - mt.step(8, 9.5, np.hypot(X - 88, Y - 94))
    mt.paint(m, stamp, (164, 52, 46), height=0.45)
    return m


def almond_water_sprite():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = color

    rect(6, 1, 9, 2, (132, 92, 54, 255))
    px[7, 1] = (158, 114, 70, 255)
    rect(6, 3, 9, 4, (144, 186, 152, 235))
    px[6, 3] = (108, 148, 118, 245)
    px[9, 3] = (108, 148, 118, 245)
    rect(5, 5, 10, 5, (144, 186, 152, 235))
    px[5, 5] = (108, 148, 118, 245)
    px[10, 5] = (108, 148, 118, 245)
    rect(4, 6, 11, 14, (108, 148, 118, 245))
    rect(5, 6, 10, 13, (224, 214, 186, 245))
    for y in range(6, 13):
        px[5, y] = (242, 238, 224, 250)
    rect(5, 8, 10, 11, (246, 238, 214, 255))
    rect(6, 9, 9, 10, (176, 126, 74, 255))
    px[7, 9] = (212, 166, 108, 255)
    rect(5, 13, 10, 13, (172, 166, 144, 245))
    return img


def write_json(path: Path, data: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def box_elem(f, t, tex_map, cull=None):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        tex = tex_map.get(face, tex_map.get("all"))
        if tex is None:
            continue
        entry = {"texture": tex}
        if cull and face in cull:
            entry["cullface"] = face
        faces[face] = entry
    return {"from": f, "to": t, "faces": faces}


def write_assets():
    # 1. Simple cube blocks
    for name in ("manila_wallpaper", "manila_wood_floor", "manila_light", "void_stone"):
        write_json(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"noclip_backrooms:block/{name}"},
        })
        write_json(ASSETS / f"blockstates/{name}.json", {
            "variants": {"": {"model": f"noclip_backrooms:block/{name}"}}
        })

    # 2. Baseboard wall
    write_json(ASSETS / "models/block/manila_wallpaper_baseboard.json", {
        "parent": "noclip_backrooms:block/baseboard_wall",
        "textures": {
            "side": "noclip_backrooms:block/manila_wallpaper_baseboard",
            "top": "noclip_backrooms:block/manila_wallpaper",
            "board": "noclip_backrooms:block/manila_board",
        },
    })
    write_json(ASSETS / "blockstates/manila_wallpaper_baseboard.json", {
        "variants": {"": {"model": "noclip_backrooms:block/manila_wallpaper_baseboard"}}
    })

    # 3. Doors (lower + upper, closed + open)
    for half, leaf_tex in (("manila_door", "manila_door_leaf"), ("manila_door_top", "manila_door_leaf_top")):
        jamb = box_elem([0, 0, 6], [1.5, 16, 10], {"all": "#wood"})
        lintel = [box_elem([0, 14.5, 6], [16, 16, 10], {"all": "#wood"})] if half == "manila_door_top" else []
        top_max = 14.5 if half == "manila_door_top" else 16
        closed_leaf = box_elem([1.5, 0, 6.8], [16, top_max, 9.2], {"all": "#leaf"})
        open_leaf = box_elem([0, 0, 1], [2.4, top_max, 15], {"all": "#leaf"})
        for suffix, leaf in (("", closed_leaf), ("_open", open_leaf)):
            write_json(ASSETS / f"models/block/{half}{suffix}.json", {
                "parent": "minecraft:block/block",
                "textures": {
                    "particle": f"noclip_backrooms:block/{leaf_tex}",
                    "leaf": f"noclip_backrooms:block/{leaf_tex}",
                    "wood": "noclip_backrooms:block/manila_wood",
                },
                "elements": [jamb, *lintel, leaf],
            })
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for is_open in ("false", "true"):
                suffix = "_open" if is_open == "true" else ""
                entry = {"model": f"noclip_backrooms:block/{half}{suffix}"}
                if y:
                    entry["y"] = y
                variants[f"facing={facing},open={is_open}"] = entry
        write_json(ASSETS / f"blockstates/{half}.json", {"variants": variants})

    # 4. Octagonal table quadrant (base facing=north is NW corner of 2x2 table)
    table_elements = [
        box_elem([4, 13.5, 4], [16, 16, 16], {"all": "#wood", "up": "#top"}),
        box_elem([1, 13.5, 7], [4, 16, 16], {"all": "#wood", "up": "#top"}),
        box_elem([7, 13.5, 1], [16, 16, 4], {"all": "#wood", "up": "#top"}),
        box_elem([7, 0, 7], [10.5, 13.5, 10.5], {"all": "#wood"}),
    ]
    write_json(ASSETS / "models/block/manila_table.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "noclip_backrooms:block/manila_table_top",
            "top": "noclip_backrooms:block/manila_table_top",
            "wood": "noclip_backrooms:block/manila_wood",
        },
        "elements": table_elements,
    })
    write_json(ASSETS / "blockstates/manila_table.json", {
        "variants": {
            "facing=north": {"model": "noclip_backrooms:block/manila_table"},
            "facing=east": {"model": "noclip_backrooms:block/manila_table", "y": 90},
            "facing=south": {"model": "noclip_backrooms:block/manila_table", "y": 180},
            "facing=west": {"model": "noclip_backrooms:block/manila_table", "y": 270},
        }
    })

    # 5. Under-table supply cabinet (with matching octagonal table corner on top!)
    for opened, front_tex in ((False, "manila_cabinet_front"), (True, "manila_cabinet_front_open")):
        suffix = "_open" if opened else ""
        cab_elements = [
            box_elem([4, 13.5, 4], [16, 16, 16], {"all": "#wood", "up": "#top"}),
            box_elem([1, 13.5, 7], [4, 16, 16], {"all": "#wood", "up": "#top"}),
            box_elem([7, 13.5, 1], [16, 16, 4], {"all": "#wood", "up": "#top"}),
            box_elem([5, 0, 5], [15, 13.5, 15], {"all": "#wood", "north": "#front", "west": "#front"}),
        ]
        write_json(ASSETS / f"models/block/manila_cabinet{suffix}.json", {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": "noclip_backrooms:block/manila_wood",
                "top": "noclip_backrooms:block/manila_table_top",
                "wood": "noclip_backrooms:block/manila_wood",
                "front": f"noclip_backrooms:block/{front_tex}",
            },
            "elements": cab_elements,
        })
    cab_variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for is_open in ("false", "true"):
            suffix = "_open" if is_open == "true" else ""
            entry = {"model": f"noclip_backrooms:block/manila_cabinet{suffix}"}
            if y:
                entry["y"] = y
            cab_variants[f"facing={facing},open={is_open}"] = entry
    write_json(ASSETS / "blockstates/manila_cabinet.json", {"variants": cab_variants})

    # 6. Chair (facing=north sits facing north, backrest on south side)
    chair_elements = [
        box_elem([2.5, 0, 2.5], [4.5, 6.5, 4.5], {"all": "#wood"}),
        box_elem([11.5, 0, 2.5], [13.5, 6.5, 4.5], {"all": "#wood"}),
        box_elem([2.5, 0, 11.5], [4.5, 16, 13.5], {"all": "#wood"}),
        box_elem([11.5, 0, 11.5], [13.5, 16, 13.5], {"all": "#wood"}),
        box_elem([2.5, 6.5, 2.5], [13.5, 8.5, 13.5], {"all": "#wood", "up": "#cushion"}),
        box_elem([4.5, 10.5, 11.8], [11.5, 15, 13.2], {"all": "#wood"}),
    ]
    write_json(ASSETS / "models/block/manila_chair.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "noclip_backrooms:block/manila_wood",
            "wood": "noclip_backrooms:block/manila_wood",
            "cushion": "noclip_backrooms:block/manila_chair_cushion",
        },
        "elements": chair_elements,
    })
    write_json(ASSETS / "blockstates/manila_chair.json", {
        "variants": {
            "facing=north": {"model": "noclip_backrooms:block/manila_chair"},
            "facing=east": {"model": "noclip_backrooms:block/manila_chair", "y": 90},
            "facing=south": {"model": "noclip_backrooms:block/manila_chair", "y": 180},
            "facing=west": {"model": "noclip_backrooms:block/manila_chair", "y": 270},
        }
    })

    # 7. Guide note on table
    write_json(ASSETS / "models/block/manila_note.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "noclip_backrooms:block/manila_paper",
            "paper": "noclip_backrooms:block/manila_paper",
        },
        "elements": [
            box_elem([2, 0, 2], [14, 1.0, 14], {"all": "#paper"})
        ],
    })
    write_json(ASSETS / "blockstates/manila_note.json", {
        "variants": {
            "facing=north": {"model": "noclip_backrooms:block/manila_note"},
            "facing=east": {"model": "noclip_backrooms:block/manila_note", "y": 90},
            "facing=south": {"model": "noclip_backrooms:block/manila_note", "y": 180},
            "facing=west": {"model": "noclip_backrooms:block/manila_note", "y": 270},
        }
    })

    # 8. Block item definitions + self-drop loot tables
    blocks = (
        "manila_wallpaper",
        "manila_wallpaper_baseboard",
        "manila_wood_floor",
        "manila_light",
        "void_stone",
        "manila_door",
        "manila_door_top",
        "manila_table",
        "manila_cabinet",
        "manila_chair",
        "manila_note",
    )
    for name in blocks:
        write_json(ASSETS / f"items/{name}.json", {
            "model": {"type": "minecraft:model", "model": f"noclip_backrooms:block/{name}"}
        })
        if name not in ("void_stone", "manila_note"):
            write_json(DATA / f"loot_table/blocks/{name}.json", {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "bonus_rolls": 0,
                    "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })

    # 9. Almond Water item model
    write_json(ASSETS / "models/item/almond_water.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "noclip_backrooms:item/almond_water"},
    })
    write_json(ASSETS / "items/almond_water.json", {
        "model": {"type": "minecraft:model", "model": "noclip_backrooms:item/almond_water"}
    })


def main():
    BLOCK_OUT.mkdir(parents=True, exist_ok=True)
    ITEM_OUT.mkdir(parents=True, exist_ok=True)

    textures = {
        "manila_wallpaper": manila_wallpaper(),
        "manila_wallpaper_baseboard": mt.grime(manila_wallpaper()),
        "manila_board": mt.board((104, 72, 46)),
        "manila_wood_floor": manila_wood_plank(base=(146, 102, 60), dark=(106, 69, 38), light=(172, 124, 78), seed=81),
        "manila_light": manila_light(),
        "void_stone": void_stone(),
        "manila_wood": manila_wood_plank(base=(122, 82, 46), dark=(88, 56, 30), light=(146, 102, 62), seed=82),
        "manila_table_top": manila_wood_plank(base=(132, 88, 50), dark=(96, 60, 32), light=(158, 110, 68), seed=83),
        "manila_door_leaf": manila_door_leaf(top=False),
        "manila_door_leaf_top": manila_door_leaf(top=True),
        "manila_cabinet_front": manila_cabinet_front(opened=False),
        "manila_cabinet_front_open": manila_cabinet_front(opened=True),
        "manila_chair_cushion": manila_chair_cushion(),
        "manila_paper": manila_paper(),
    }

    for name, mat in textures.items():
        mt.save(name, mat)

    almond_water_sprite().save(ITEM_OUT / "almond_water.png", optimize=True)
    write_assets()
    mt.check()
    print(f"Generated {len(textures)} block materials ({len(textures) * 3} files), almond_water.png, and all models/blockstates.")


if __name__ == "__main__":
    main()
