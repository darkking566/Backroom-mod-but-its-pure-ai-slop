"""The Frontrooms' own blocks (docs/企劃書/1-遊戲玩法/README.md, "前室"): the M.E.G. institute's walls, floors,
ceilings and lights, its blast doors and their lamps, lockers, transport pads, the sealed archive hatch and files, and
the quarantine wall around the grounds with its fence, floodlights and warning signs.

Writes 128 px textures (with labPBR _n and _s), block models, blockstates, item models and nothing else: the
institute's blocks cannot be broken, so they have no loot tables.

Run: python tools/make_frontrooms.py
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_textures as mt  # noqa: E402
from make_textures import X, Y, SIZE, clouds, flat, metal, noise, paint, step, tint  # noqa: E402
from make_textures import box as _box  # noqa: E402
from make_level2 import text_mask  # noqa: E402

ASSETS = mt.ASSETS
TEXTURES = ASSETS / "textures/block/frontrooms"
MODELS = ASSETS / "models/block/frontrooms"
NS = "noclip_backrooms:block/frontrooms/"
FACING = {"north": 0, "east": 90, "south": 180, "west": 270}


def box(left, top, right, bottom, soft=1.0):
    # A hard edge (soft 0) would divide by zero in step().
    return _box(left, top, right, bottom, max(soft, 0.01))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def grime(m, seed, amount=0.12):
    tint(m, step(0.1, 0.7, clouds(seed, 4)) * amount, (0.86, 0.84, 0.8))


def seams(m, color, every=128, width=1.5, height=-1.2):
    lines = np.minimum(np.minimum(X % every, every - 1 - X % every), np.minimum(Y % every, every - 1 - Y % every))
    paint(m, 1 - step(0, width, lines), color, height=height)


def rivets(m, points, color=(170, 174, 176)):
    for px, py in points:
        paint(m, 1 - step(1.2, 2.4, np.hypot(X - px, Y - py)), color, height=1.5, f0=180, smooth=0.7)


# Textures

def institute_wall(seed):
    m = flat((228, 231, 229), 0.45, 15)
    m.color *= (1 + noise(seed, 16) * 0.012)[..., None]
    seams(m, (176, 180, 182))
    paint(m, 1 - step(0, 1.2, np.abs(Y - 64)), (196, 200, 202), height=-0.6)
    rivets(m, [(6, 6), (121, 6), (6, 121), (121, 121), (6, 70), (121, 70)])
    grime(m, seed, 0.06)
    tint(m, step(80, 128, Y) * 0.06, (0.92, 0.9, 0.86))
    return m


def wainscot(seed):
    m = metal((70, 76, 82), 0.5)
    seams(m, (40, 44, 48))
    paint(m, box(0, 6, 128, 16, 0.5), (36, 150, 150), smooth=0.6, height=0.6)
    paint(m, box(0, 16, 128, 18, 0.5), (30, 34, 38), height=-0.5)
    for i in range(6):
        rng = np.random.default_rng(seed + i)
        x0, y0 = rng.uniform(10, 118), rng.uniform(60, 120)
        paint(m, (1 - step(0, 1.2, np.abs((Y - y0) - 0.3 * (X - x0)))) * box(x0 - 14, 0, x0 + 14, 128, 2), (104, 108, 112), height=-0.4)
    grime(m, seed, 0.1)
    return m


def floor(seed, work=False):
    m = flat((148, 152, 154), 0.35, 12)
    m.color *= (1 + noise(seed, 32) * 0.03)[..., None]
    rng = np.random.default_rng(seed)
    specks = rng.random((SIZE, SIZE))
    paint(m, (specks > 0.985).astype(float), (196, 198, 196))
    paint(m, (specks < 0.012).astype(float), (96, 100, 102))
    seams(m, (108, 112, 114), every=64, width=1.0, height=-0.8)
    if work:
        edge = np.minimum(np.minimum(X, 127 - X), np.minimum(Y, 127 - Y))
        band = 1 - step(9, 10, edge)
        stripes = ((X + Y) // 8 % 2).astype(float)
        paint(m, band, (214, 170, 32), smooth=0.4)
        paint(m, band * stripes, (34, 34, 32))
    grime(m, seed + 3, 0.1)
    return m


def ceiling(seed):
    m = flat((222, 224, 220), 0.2, 8)
    seams(m, (150, 154, 156), every=64, width=1.4, height=-1)
    dots = ((X % 6 == 3) & (Y % 6 == 3)).astype(float)
    paint(m, dots * 0.7, (178, 180, 178), height=-0.3)
    grime(m, seed, 0.05)
    return m


def light(glow_color, frame=(150, 154, 156), dim=1.0):
    m = metal(frame, 0.6)
    lens = box(10, 10, 118, 118, 1)
    paint(m, lens, glow_color, smooth=0.3, glow=dim)
    bars = ((X - 10) % 27 < 2).astype(float) * box(10, 10, 118, 118, 0)
    paint(m, bars * 0.5, tuple(int(c * 0.82) for c in glow_color))
    return m


def concrete(seed):
    m = flat((150, 150, 146), 0.15, 8, porosity=60)
    m.color *= (1 + clouds(seed, 6)[..., None] * 0.06)
    paint(m, 1 - step(0, 1, np.abs(Y % 32 - 0.5)), (122, 122, 118), height=-0.5)
    rng = np.random.default_rng(seed)
    for _ in range(14):
        px, py = rng.uniform(0, 128, 2)
        paint(m, 1 - step(0.4, 1.4, np.hypot(X - px, Y - py)), (104, 104, 100), height=-1)
    return m


def counter_side(seed):
    m = metal((176, 182, 186), 0.6)
    seams(m, (132, 138, 142))
    paint(m, box(0, 0, 128, 14, 0.5), (236, 238, 236), smooth=0.7)
    grime(m, seed, 0.05)
    return m


def counter_top(seed):
    m = flat((236, 238, 236), 0.75, 40)
    m.color *= (1 + noise(seed, 16) * 0.01)[..., None]
    seams(m, (196, 200, 202), width=1.2)
    return m


def blast_door(seed, upper):
    m = metal((112, 118, 124), 0.55)
    paint(m, 1 - step(0, 1.5, np.abs(X - 64)), (40, 44, 48), height=-2)   # where the two leaves meet
    for x0 in (10, 72):
        paint(m, box(x0, 12, x0 + 46, 116, 1), (124, 130, 136), height=1.2)
    if upper:
        mask = text_mask("M.E.G.", 20, (64, 46), "arialbd.ttf")
        paint(m, mask, (226, 228, 226))
        paint(m, box(20, 70, 108, 86, 1), (40, 44, 48), smooth=0.8, f0=200)   # a viewing slit
        paint(m, box(22, 72, 106, 84, 0), (90, 140, 160), smooth=0.9, f0=220)
    else:
        band = box(0, 92, 128, 128, 0.5)
        stripes = ((X + Y) // 12 % 2).astype(float)
        paint(m, band, (220, 176, 30), smooth=0.4)
        paint(m, band * stripes, (30, 30, 28))
    rivets(m, [(x, y) for x in (6, 58, 70, 122) for y in (6, 60, 122)])
    grime(m, seed, 0.14)
    return m


def door_lamp(lit_color):
    m = institute_wall(41)
    paint(m, box(36, 36, 92, 92, 1.5), (60, 64, 68), height=1.5, f0=160)
    disc = 1 - step(20, 23, np.hypot(X - 64, Y - 64))
    paint(m, disc, lit_color, smooth=0.9, glow=0.9)
    paint(m, (1 - step(6, 12, np.hypot(X - 58, Y - 58))) * 0.6, tuple(min(255, c + 80) for c in lit_color))
    return m


def locker_front(seed, weapons=False):
    m = metal((92, 108, 124) if not weapons else (60, 66, 70), 0.5)
    paint(m, box(4, 4, 124, 124, 1), tuple(c + 8 for c in ((92, 108, 124) if not weapons else (60, 66, 70))), height=1)
    for y0 in (14, 22, 30):
        paint(m, box(24, y0, 104, y0 + 4, 0.5), (34, 38, 44), height=-1.5)
    if weapons:
        mesh = box(20, 42, 108, 112, 1)
        grid_lines = (((X % 6) < 1.2) | ((Y % 6) < 1.2)).astype(float)
        paint(m, mesh, (24, 26, 28))
        for x0 in (34, 58, 82):
            paint(m, box(x0, 48, x0 + 6, 108, 0.5) * mesh, (90, 84, 70))
        paint(m, mesh * grid_lines, (120, 124, 128), height=0.5)
    else:
        paint(m, box(40, 50, 88, 64, 0.5), (232, 226, 196))
        paint(m, text_mask("M.E.G.", 9, (64, 57), "arialbd.ttf") * box(40, 50, 88, 64, 0), (40, 40, 40))
        paint(m, box(100, 70, 108, 98, 0.5), (150, 154, 158), height=2, f0=200, smooth=0.8)
    grime(m, seed, 0.12)
    return m


def locker_side(seed):
    m = metal((92, 108, 124), 0.5)
    seams(m, (64, 76, 88))
    grime(m, seed, 0.1)
    return m


def pad_top(seed):
    m = metal((54, 58, 64), 0.6)
    r = np.hypot(X - 64, Y - 64)
    paint(m, 1 - step(52, 54, r), (70, 76, 84), height=1)
    ring = (1 - step(2, 4, np.abs(r - 40))) + (1 - step(1, 2.5, np.abs(r - 22)))
    paint(m, np.clip(ring, 0, 1), (90, 230, 240), glow=0.85, smooth=0.9)
    for angle in range(0, 360, 45):
        a = np.radians(angle)
        along = (X - 64) * np.cos(a) + (Y - 64) * np.sin(a)
        across = -(X - 64) * np.sin(a) + (Y - 64) * np.cos(a)
        paint(m, (1 - step(1, 2, np.abs(across))) * (step(44, 46, along) * (1 - step(52, 54, along))),
              (90, 230, 240), glow=0.6)
    grime(m, seed, 0.06)
    return m


def hatch_top(seed, opened=False):
    m = metal((96, 100, 104), 0.5)
    edge = np.minimum(np.minimum(X, 127 - X), np.minimum(Y, 127 - Y))
    band = 1 - step(11, 12, edge)
    paint(m, band, (220, 176, 30))
    paint(m, band * ((X + Y) // 10 % 2).astype(float), (30, 30, 28))
    if opened:
        paint(m, box(16, 16, 112, 112, 1), (12, 12, 14), height=-6)
    else:
        paint(m, box(20, 20, 108, 108, 1), (110, 114, 118), height=1)
        paint(m, box(76, 30, 100, 62, 0.5), (30, 34, 36), height=1.5)
        for i, (kx, ky) in enumerate([(81, 38), (88, 38), (95, 38), (81, 46), (88, 46), (95, 46), (81, 54), (88, 54), (95, 54)]):
            paint(m, box(kx - 2, ky - 2, kx + 2, ky + 2, 0.3), (150, 156, 160))
        paint(m, box(76, 24, 100, 28, 0.3), (200, 40, 40), glow=0.7)
    grime(m, seed, 0.14)
    return m


def file_top(seed):
    m = flat((92, 70, 52), 0.4, 10)
    m.color *= (1 + noise(seed, 32, 4) * 0.05)[..., None]
    rng = np.random.default_rng(seed)
    for _ in range(5):
        cx, cy = rng.uniform(30, 98, 2)
        a = rng.uniform(-0.5, 0.5)
        u = (X - cx) * np.cos(a) + (Y - cy) * np.sin(a)
        v = -(X - cx) * np.sin(a) + (Y - cy) * np.cos(a)
        folder = box(-24, -17, 24, 17, 0.6)
        mask = (step(-25, -24, u) * (1 - step(24, 25, u)) * step(-18, -17, v) * (1 - step(17, 18, v)))
        paint(m, mask, (214, 186, 120), height=1)
        paint(m, mask * (1 - step(0, 1, np.abs(v + 8))) , (196, 168, 104))
        paint(m, mask * (1 - step(0.5, 1, np.abs(v - 3))) * step(-16, -15, u) * (1 - step(10, 11, u)), (60, 60, 60))
    return m


def file_side(seed):
    m = flat((80, 60, 44), 0.35, 10)
    m.color *= (1 + noise(seed, 4, 32) * 0.05)[..., None]
    paint(m, box(0, 0, 128, 12, 0.5), (62, 46, 34))
    paint(m, box(40, 40, 88, 56, 0.5), (150, 140, 120), height=1, f0=150)
    return m


def quarantine_wall(seed):
    m = flat((172, 170, 164), 0.12, 8, porosity=80)
    m.color *= (1 + clouds(seed, 5)[..., None] * 0.07)
    streak = step(0.3, 0.9, noise(seed + 7, 64, 4)) * step(0, 128, Y)
    tint(m, streak * 0.25, (0.78, 0.76, 0.72))
    seams(m, (138, 136, 130), width=1.2)
    for x0 in (16, 80):
        paint(m, box(x0, 0, x0 + 3, 128, 0.5), (150, 148, 142), height=-0.6)
    return m


def quarantine_band(seed):
    m = quarantine_wall(seed)
    band = box(0, 38, 128, 90, 0.5)
    paint(m, band, (220, 176, 30), smooth=0.3)
    paint(m, band * ((X + Y) // 14 % 2).astype(float), (30, 30, 28))
    paint(m, box(18, 52, 110, 76, 0.5), (226, 222, 210))
    paint(m, text_mask("QUARANTINE", 13, (64, 64), "arialbd.ttf") * box(18, 52, 110, 76, 0), (170, 30, 26))
    return m


def floodlight(seed):
    m = metal((70, 74, 78), 0.5)
    paint(m, box(14, 14, 114, 114, 1), (60, 62, 66), height=1)
    lens = 1 - step(40, 43, np.hypot(X - 64, Y - 64))
    paint(m, lens, (255, 248, 222), smooth=0.95, glow=1.0)
    grime(m, seed, 0.1)
    return m


def warning_sign(seed):
    m = flat((226, 184, 34), 0.5, 20)
    paint(m, box(6, 6, 122, 122, 0.5) * (1 - box(12, 12, 116, 116, 0.5)), (30, 30, 28))
    tri_y = Y - 14
    tri = step(0, 1, tri_y) * (1 - step(48, 49, tri_y)) * (1 - step(0, 1, np.abs(X - 64) - tri_y * 0.6))
    paint(m, tri, (30, 30, 28))
    inner = step(8, 9, tri_y) * (1 - step(44, 45, tri_y)) * (1 - step(0, 1, np.abs(X - 64) - (tri_y - 8) * 0.6 + 4))
    paint(m, inner, (226, 184, 34))
    bolt = text_mask("!", 30, (64, 44), "arialbd.ttf")
    paint(m, bolt, (30, 30, 28))
    paint(m, text_mask("DANGER", 15, (64, 82), "arialbd.ttf"), (30, 30, 28))
    paint(m, text_mask("HIGH VOLTAGE", 10, (64, 102), "arialbd.ttf"), (30, 30, 28))
    grime(m, seed, 0.12)
    return m


def fence():
    """Wires on insulators, the rest see-through (saved with alpha, so it needs no _n or _s)."""
    image = np.zeros((SIZE, SIZE, 4))
    for wy in (14, 40, 66, 92, 118):
        wire = 1 - step(0.6, 1.4, np.abs(Y - wy - np.sin(X / 128 * np.pi) * 2))
        image[..., :3] += wire[..., None] * np.array([150, 152, 150])
        image[..., 3] = np.maximum(image[..., 3], wire * 255)
    for wy in (14, 40, 66, 92, 118):
        for wx in (4, 124):
            ins = box(wx - 4, wy - 3, wx + 4, wy + 3, 0.3)
            image[..., :3] = image[..., :3] * (1 - ins[..., None]) + ins[..., None] * np.array([230, 220, 190])
            image[..., 3] = np.maximum(image[..., 3], ins * 255)
    image[..., 3] = np.where(image[..., 3] > 100, 255, 0)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(image, 0, 255).astype("uint8"), "RGBA").save(TEXTURES / "electric_fence.png", optimize=True)


TEXTURE_SET = {
    "institute_wall": lambda: institute_wall(1),
    "institute_wainscot": lambda: wainscot(2),
    "institute_floor": lambda: floor(3),
    "institute_work_floor": lambda: floor(4, work=True),
    "institute_ceiling": lambda: ceiling(5),
    "institute_light": lambda: light((250, 252, 255)),
    "emergency_light": lambda: light((230, 40, 30), frame=(70, 70, 72)),
    "institute_concrete": lambda: concrete(6),
    "institute_counter": lambda: counter_side(7),
    "institute_counter_top": lambda: counter_top(8),
    "blast_door_lower": lambda: blast_door(9, False),
    "blast_door_upper": lambda: blast_door(10, True),
    "door_lamp_red": lambda: door_lamp((230, 36, 30)),
    "door_lamp_green": lambda: door_lamp((60, 230, 80)),
    "meg_locker": lambda: locker_front(11),
    "meg_locker_side": lambda: locker_side(12),
    "weapon_locker": lambda: locker_front(13, weapons=True),
    "transport_pad": lambda: pad_top(14),
    "archive_hatch": lambda: hatch_top(15),
    "archive_hatch_open": lambda: hatch_top(15, opened=True),
    "archive_file": lambda: file_top(16),
    "archive_file_side": lambda: file_side(17),
    "quarantine_wall": lambda: quarantine_wall(18),
    "quarantine_band": lambda: quarantine_band(19),
    "quarantine_floodlight": lambda: floodlight(20),
    "warning_sign": lambda: warning_sign(21),
}


def textures():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES
    for name, make in TEXTURE_SET.items():
        mt.save(name, make())
    fence()


# Models and blockstates

def tex(name):
    return NS + name


def cube_all(name, texture=None):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": tex(texture or name)}}


def column(side, end):
    return {"parent": "minecraft:block/cube_column", "textures": {"side": tex(side), "end": tex(end)}}


def orientable(front, side, top):
    return {"parent": "minecraft:block/orientable", "textures": {"front": tex(front), "side": tex(side), "top": tex(top)}}


def face(texture, uv=(0, 0, 16, 16), cull=None):
    f = {"texture": "#" + texture, "uv": list(uv)}
    if cull:
        f["cullface"] = cull
    return f


def element(frm, to, faces):
    return {"from": list(frm), "to": list(to), "faces": faces}


def custom(textures, elements, particle):
    return {"parent": "minecraft:block/block", "textures": {"particle": tex(particle), **{k: tex(v) for k, v in textures.items()}},
            "elements": elements}


def all_faces(texture, uvs=None):
    return {d: face(texture, (uvs or {}).get(d, (0, 0, 16, 16))) for d in ("north", "south", "east", "west", "up", "down")}


def variant(model, y=0, x=0):
    v = {"model": NS + model}
    if x:
        v["x"] = x
    if y:
        v["y"] = y
    return v


def models_and_states():
    simple = {
        "institute_wall": cube_all("institute_wall"),
        "institute_wainscot": cube_all("institute_wainscot"),
        "institute_floor": cube_all("institute_floor"),
        "institute_work_floor": cube_all("institute_work_floor"),
        "institute_ceiling": cube_all("institute_ceiling"),
        "institute_light": cube_all("institute_light"),
        "institute_emergency_light": cube_all("emergency_light"),
        "institute_concrete": cube_all("institute_concrete"),
        "institute_counter": column("institute_counter", "institute_counter_top"),
        "quarantine_wall": cube_all("quarantine_wall"),
        "quarantine_band": cube_all("quarantine_band"),
        "quarantine_floodlight": cube_all("quarantine_floodlight"),
    }
    for name, model in simple.items():
        write_json(MODELS / f"{name}.json", model)
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": variant(name)}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})

    # Blast doors: a thick plate in the middle of the wall; open, only the frame posts are left (the leaves slid in).
    for half in ("lower", "upper"):
        t = {"d": f"blast_door_{half}", "e": "institute_wall"}
        closed = [element((0, 0, 5), (16, 16, 11), {"north": face("d"), "south": face("d"), "east": face("e", (5, 0, 11, 16)),
                                                     "west": face("e", (5, 0, 11, 16)), "up": face("e", (0, 5, 16, 11)), "down": face("e", (0, 5, 16, 11))})]
        write_json(MODELS / f"blast_door_{half}.json", custom(t, closed, f"blast_door_{half}"))
        frame = [element((0, 0, 5), (1, 16, 11), all_faces("e")), element((15, 0, 5), (16, 16, 11), all_faces("e"))]
        write_json(MODELS / f"blast_door_{half}_open.json", custom(t, frame, f"blast_door_{half}"))
    variants = {}
    for axis, y in (("x", 0), ("z", 90)):
        for half in ("lower", "upper"):
            for opened in (False, True):
                for requires in range(6):
                    variants[f"axis={axis},half={half},open={str(opened).lower()},requires={requires}"] = variant(
                        f"blast_door_{half}{'_open' if opened else ''}", y)
    write_json(ASSETS / "blockstates/blast_door.json", {"variants": variants})
    write_json(ASSETS / "items/blast_door.json", {"model": {"type": "minecraft:model", "model": NS + "blast_door_lower"}})

    for color in ("red", "green"):
        write_json(MODELS / f"door_lamp_{color}.json", cube_all(f"door_lamp_{color}"))
    write_json(ASSETS / "blockstates/door_lamp.json", {"variants": {
        f"requires={r},unlocked={str(u).lower()}": variant("door_lamp_green" if u else "door_lamp_red") for r in range(6) for u in (False, True)}})
    write_json(ASSETS / "items/door_lamp.json", {"model": {"type": "minecraft:model", "model": NS + "door_lamp_red"}})

    for name, front, side in (("meg_locker", "meg_locker", "meg_locker_side"), ("weapon_locker", "weapon_locker", "meg_locker_side")):
        write_json(MODELS / f"{name}.json", orientable(front, side, side))
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {f"facing={f}": variant(name, y) for f, y in FACING.items()}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})

    pad = [element((0, 0, 0), (16, 2, 16), {"up": face("t"), "down": face("s"), "north": face("s", (0, 14, 16, 16)),
                                            "south": face("s", (0, 14, 16, 16)), "east": face("s", (0, 14, 16, 16)), "west": face("s", (0, 14, 16, 16))})]
    write_json(MODELS / "transport_pad.json", custom({"t": "transport_pad", "s": "meg_locker_side"}, pad, "transport_pad"))
    write_json(ASSETS / "blockstates/transport_pad.json", {"variants": {"": variant("transport_pad")}})
    write_json(ASSETS / "items/transport_pad.json", {"model": {"type": "minecraft:model", "model": NS + "transport_pad"}})

    for opened in (False, True):
        name = "archive_hatch_open" if opened else "archive_hatch"
        write_json(MODELS / f"{name}.json", column("institute_concrete", name) if not opened
                   else custom({"t": name, "s": "institute_concrete"}, [element((0, 0, 0), (16, 3, 16), {
                       "up": face("t"), "down": face("s"), "north": face("s", (0, 13, 16, 16)), "south": face("s", (0, 13, 16, 16)),
                       "east": face("s", (0, 13, 16, 16)), "west": face("s", (0, 13, 16, 16))})], name))
    write_json(ASSETS / "blockstates/archive_hatch.json", {"variants": {"open=false": variant("archive_hatch"), "open=true": variant("archive_hatch_open")}})
    write_json(ASSETS / "items/archive_hatch.json", {"model": {"type": "minecraft:model", "model": NS + "archive_hatch"}})

    write_json(MODELS / "archive_file.json", column("archive_file_side", "archive_file"))
    write_json(ASSETS / "blockstates/archive_file.json", {"variants": {"": variant("archive_file")}})
    write_json(ASSETS / "items/archive_file.json", {"model": {"type": "minecraft:model", "model": NS + "archive_file"}})

    cross = [element((0.8, 0, 8), (15.2, 16, 8), {"north": face("f"), "south": face("f")}),
             element((8, 0, 0.8), (8, 16, 15.2), {"east": face("f"), "west": face("f")})]
    fence_model = custom({"f": "electric_fence"}, cross, "electric_fence")
    fence_model["ambientocclusion"] = False
    write_json(MODELS / "electric_fence.json", fence_model)
    write_json(ASSETS / "blockstates/electric_fence.json", {"variants": {"": variant("electric_fence")}})
    write_json(ASSETS / "items/electric_fence.json", {"model": {"type": "minecraft:model", "model": NS + "electric_fence"}})

    sign = [element((1, 2, 15), (15, 14, 16), {"north": face("s"), "south": face("b"), "east": face("b", (0, 0, 1, 12)),
                                            "west": face("b", (0, 0, 1, 12)), "up": face("b", (0, 0, 16, 1)), "down": face("b", (0, 0, 16, 1))})]
    write_json(MODELS / "warning_sign.json", custom({"s": "warning_sign", "b": "institute_concrete"}, sign, "warning_sign"))
    # The sign's face is the model's north side; facing names the way it looks out (vanilla's convention for orientable).
    write_json(ASSETS / "blockstates/warning_sign.json", {"variants": {f"facing={f}": variant("warning_sign", y) for f, y in FACING.items()}})
    write_json(ASSETS / "items/warning_sign.json", {"model": {"type": "minecraft:model", "model": NS + "warning_sign"}})


def main():
    textures()
    models_and_states()
    print("Frontrooms blocks written.")


if __name__ == "__main__":
    main()
