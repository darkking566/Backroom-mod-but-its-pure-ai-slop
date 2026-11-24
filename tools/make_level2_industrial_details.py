import json
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "noclip_backrooms")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "noclip_backrooms")
TEX_DIR = os.path.join(ASSETS, "textures", "block", "level2")
MODEL_DIR = os.path.join(ASSETS, "models", "block", "level2")
STATE_DIR = os.path.join(ASSETS, "blockstates")
ITEM_DIR = os.path.join(ASSETS, "items")
LOOT_DIR = os.path.join(DATA, "loot_table", "blocks")


def ensure_dirs():
    for d in (TEX_DIR, MODEL_DIR, STATE_DIR, ITEM_DIR, LOOT_DIR):
        os.makedirs(d, exist_ok=True)


def noisy_fill(w, h, base, var, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = rng.randint(-var, var)
            px[x, y] = (
                max(0, min(255, base[0] + d)),
                max(0, min(255, base[1] + d)),
                max(0, min(255, base[2] + d)),
                255,
            )
    return img


def make_breaker_box_tex():
    img = noisy_fill(64, 64, (92, 98, 102), 8, 401)
    px = img.load()
    # Outer bezel
    for i in range(64):
        for b in (0, 1, 2, 61, 62, 63):
            px[i, b] = (58, 62, 66, 255)
            px[b, i] = (58, 62, 66, 255)
    # Recessed door seam
    for i in range(6, 58):
        for b in (6, 57):
            px[i, b] = (42, 46, 50, 255)
            px[b, i] = (42, 46, 50, 255)
    # Handle latch on right
    for y in range(26, 38):
        for x in range(49, 53):
            px[x, y] = (34, 36, 40, 255)
    # Small yellow high-voltage triangle in center
    for dy in range(10):
        for dx in range(-dy // 2, dy // 2 + 1):
            x, y = 32 + dx, 24 + dy
            px[x, y] = (212, 174, 38, 255)
    # Status LED (dim green/red)
    for y in range(12, 16):
        for x in range(12, 16):
            px[x, y] = (176, 44, 38, 255)
        for x in range(20, 24):
            px[x, y] = (54, 152, 72, 255)
    img.save(os.path.join(TEX_DIR, "breaker_box.png"))


def make_pressure_gauge_tex():
    # Brushed steel backplate + circular dial
    img = noisy_fill(64, 64, (116, 120, 124), 7, 402)
    px = img.load()
    cx, cy = 32, 32
    for y in range(64):
        for x in range(64):
            dist2 = (x - cx) ** 2 + (y - cy) ** 2
            if dist2 <= 24 * 24:
                if dist2 >= 20 * 20:
                    px[x, y] = (64, 68, 72, 255)  # Brass/steel rim
                else:
                    px[x, y] = (218, 214, 198, 255)  # Aged dial face
    # Red danger arc on upper right
    for y in range(12, 32):
        for x in range(32, 52):
            dist2 = (x - cx) ** 2 + (y - cy) ** 2
            if 14 * 14 <= dist2 <= 18 * 18:
                px[x, y] = (182, 46, 42, 255)
    # Needle pointing toward high pressure
    for t in range(16):
        nx = int(cx + t * 0.75)
        ny = int(cy - t * 0.55)
        for dx in (-1, 0):
            for dy in (-1, 0):
                px[nx + dx, ny + dy] = (28, 30, 32, 255)
    # Center pin
    for y in range(30, 35):
        for x in range(30, 35):
            px[x, y] = (52, 54, 58, 255)
    img.save(os.path.join(TEX_DIR, "pressure_gauge.png"))


def make_hazard_sign_tex():
    img = noisy_fill(64, 64, (206, 164, 34), 8, 403)
    px = img.load()
    # Diagonal black hazard stripes along top & bottom borders
    for y in range(64):
        for x in range(64):
            if y < 12 or y >= 52:
                if ((x + y) // 8) % 2 == 0:
                    px[x, y] = (30, 32, 34, 255)
            elif x < 3 or x >= 61:
                px[x, y] = (46, 48, 52, 255)
    # Center dark warning bar / text-like markings
    for y in range(22, 42):
        for x in range(10, 54):
            if y in (22, 41) or x in (10, 53):
                px[x, y] = (34, 36, 38, 255)
            elif 26 <= y <= 29 and (x % 5 != 0) and 14 <= x <= 50:
                px[x, y] = (34, 36, 38, 255)
            elif 33 <= y <= 36 and (x % 4 != 0) and 16 <= x <= 48:
                px[x, y] = (34, 36, 38, 255)
    img.save(os.path.join(TEX_DIR, "hazard_sign.png"))


def make_drain_grate_tex():
    # Concrete rim with dark iron grate bars in center
    img = noisy_fill(64, 64, (118, 120, 114), 10, 404)
    px = img.load()
    for y in range(6, 58):
        for x in range(6, 58):
            if x < 10 or x >= 54 or y < 10 or y >= 54:
                px[x, y] = (54, 58, 62, 255)  # Steel frame
            elif x % 8 in (0, 1, 2) or y in (30, 31, 32, 33):
                px[x, y] = (68, 72, 76, 255)  # Grate bars
            else:
                px[x, y] = (18, 22, 24, 255)  # Dark sump below
    img.save(os.path.join(TEX_DIR, "drain_grate.png"))


def make_floor_cable_duct_tex():
    # Concrete floor with a recessed steel cable trench along X (horizontal)
    img = noisy_fill(64, 64, (118, 120, 114), 10, 405)
    px = img.load()
    for y in range(18, 46):
        for x in range(64):
            if y in (18, 19, 20, 43, 44, 45):
                px[x, y] = (62, 66, 70, 255)  # Steel channel lip
            elif 24 <= y <= 27:
                px[x, y] = (36, 38, 42, 255)  # Black power cable
            elif 30 <= y <= 33:
                px[x, y] = (158, 92, 42, 255)  # Orange/copper cable
            elif 36 <= y <= 39:
                px[x, y] = (46, 86, 124, 255)  # Blue data cable
            else:
                px[x, y] = (24, 26, 28, 255)  # Trench shadow
    img.save(os.path.join(TEX_DIR, "floor_cable_duct.png"))


def make_ceiling_vent_tex():
    img = noisy_fill(64, 64, (84, 88, 92), 7, 406)
    px = img.load()
    for i in range(64):
        for b in (0, 1, 2, 3, 60, 61, 62, 63):
            px[i, b] = (64, 68, 72, 255)
            px[b, i] = (64, 68, 72, 255)
    for y in range(6, 58):
        for x in range(6, 58):
            if y % 6 in (0, 1, 2):
                px[x, y] = (22, 24, 26, 255)
            elif y % 6 == 3:
                px[x, y] = (98, 102, 106, 255)
    img.save(os.path.join(TEX_DIR, "ceiling_vent.png"))


def make_snapped_cables_tex():
    img = noisy_fill(64, 64, (36, 38, 42), 6, 407)
    px = img.load()
    # Copper tips near bottom
    for y in range(52, 64):
        for x in range(64):
            px[x, y] = (178, 104, 52, 255) if (x // 4) % 2 == 0 else (142, 82, 42, 255)
    img.save(os.path.join(TEX_DIR, "snapped_cables.png"))


def all_faces(tex):
    return {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def write_common(name, elements, textures, variants):
    write_json(
        os.path.join(MODEL_DIR, f"{name}.json"),
        {"parent": "minecraft:block/block", "textures": textures, "elements": elements},
    )
    write_json(os.path.join(STATE_DIR, f"{name}.json"), {"variants": variants})
    write_json(
        os.path.join(ITEM_DIR, f"{name}.json"),
        {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:block/level2/{name}"}},
    )
    write_json(
        os.path.join(LOOT_DIR, f"{name}.json"),
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1.0,
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                    "entries": [{"type": "minecraft:item", "name": f"noclip_backrooms:{name}"}],
                }
            ],
            "random_sequence": f"noclip_backrooms:blocks/{name}",
        },
    )


def facing_variants(name):
    m = f"noclip_backrooms:block/level2/{name}"
    return {
        "facing=north": {"model": m},
        "facing=east": {"model": m, "y": 90},
        "facing=south": {"model": m, "y": 180},
        "facing=west": {"model": m, "y": 270},
    }


def axis_variants(name):
    m = f"noclip_backrooms:block/level2/{name}"
    return {
        "axis=x": {"model": m},
        "axis=z": {"model": m, "y": 90},
    }


def main():
    ensure_dirs()
    make_breaker_box_tex()
    make_pressure_gauge_tex()
    make_hazard_sign_tex()
    make_drain_grate_tex()
    make_floor_cable_duct_tex()
    make_ceiling_vent_tex()
    make_snapped_cables_tex()

    # Wall attachments (mounted against +Z wall, facing north by default)
    write_common(
        "breaker_box",
        [{"from": [3, 2, 13], "to": [13, 14, 16], "faces": all_faces("#box")}],
        {"particle": "noclip_backrooms:block/level2/breaker_box", "box": "noclip_backrooms:block/level2/breaker_box"},
        facing_variants("breaker_box"),
    )
    write_common(
        "pressure_gauge",
        [
            {"from": [4, 4, 14], "to": [12, 12, 16], "faces": all_faces("#gauge")},
            {"from": [7, 0, 14.5], "to": [9, 4, 15.5], "faces": all_faces("#pipe")},
        ],
        {
            "particle": "noclip_backrooms:block/level2/pressure_gauge",
            "gauge": "noclip_backrooms:block/level2/pressure_gauge",
            "pipe": "noclip_backrooms:block/level2/toolbox_metal",
        },
        facing_variants("pressure_gauge"),
    )
    write_common(
        "hazard_sign",
        [{"from": [2, 3, 15], "to": [14, 13, 16], "faces": all_faces("#sign")}],
        {"particle": "noclip_backrooms:block/level2/hazard_sign", "sign": "noclip_backrooms:block/level2/hazard_sign"},
        facing_variants("hazard_sign"),
    )

    # Floor details (full 16x16x16 solid floor blocks)
    floor_base = "noclip_backrooms:block/level2/utility_floor"
    write_common(
        "drain_grate",
        [
            {
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": {
                    "up": {"texture": "#grate"},
                    "down": {"texture": "#floor"},
                    "north": {"texture": "#floor"},
                    "south": {"texture": "#floor"},
                    "east": {"texture": "#floor"},
                    "west": {"texture": "#floor"},
                },
            }
        ],
        {"particle": "noclip_backrooms:block/level2/drain_grate", "grate": "noclip_backrooms:block/level2/drain_grate", "floor": floor_base},
        {"": {"model": "noclip_backrooms:block/level2/drain_grate"}},
    )
    write_common(
        "floor_cable_duct",
        [
            {
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": {
                    "up": {"texture": "#duct"},
                    "down": {"texture": "#floor"},
                    "north": {"texture": "#floor"},
                    "south": {"texture": "#floor"},
                    "east": {"texture": "#floor"},
                    "west": {"texture": "#floor"},
                },
            }
        ],
        {"particle": "noclip_backrooms:block/level2/floor_cable_duct", "duct": "noclip_backrooms:block/level2/floor_cable_duct", "floor": floor_base},
        axis_variants("floor_cable_duct"),
    )

    # Ceiling details
    write_common(
        "ceiling_vent",
        [{"from": [2, 14.5, 2], "to": [14, 16, 14], "faces": all_faces("#vent")}],
        {"particle": "noclip_backrooms:block/level2/ceiling_vent", "vent": "noclip_backrooms:block/level2/ceiling_vent"},
        {"": {"model": "noclip_backrooms:block/level2/ceiling_vent"}},
    )
    write_common(
        "snapped_cables",
        [
            {"from": [4, 5, 5], "to": [5.5, 16, 6.5], "faces": all_faces("#cable")},
            {"from": [7.5, 7, 9], "to": [9, 16, 10.5], "faces": all_faces("#cable")},
            {"from": [10.5, 9, 5.5], "to": [12, 16, 7], "faces": all_faces("#cable")},
            {"from": [3.5, 14.5, 4.5], "to": [12.5, 16, 11.5], "faces": all_faces("#plate")},
        ],
        {
            "particle": "noclip_backrooms:block/level2/snapped_cables",
            "cable": "noclip_backrooms:block/level2/snapped_cables",
            "plate": "noclip_backrooms:block/level2/toolbox_metal",
        },
        {"": {"model": "noclip_backrooms:block/level2/snapped_cables"}},
    )
    print("Generated 7 Level 2 industrial detail block assets.")


if __name__ == "__main__":
    main()
