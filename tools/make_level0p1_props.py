"""Level 0.1's room furnishings: what bedrooms, galleys, gyms and hydroponic greenhouses were missing.

    py tools/make_level0p1_props.py

Writes only its own blocks' textures (128x128, with the labPBR maps), models, blockstates, item definitions, drop tables and
language lines, and adds them to the pickaxe tag. Unlike make_level0p1.py it never redraws anything else, so it is safe to run on its own.
Uses that script's drawing helpers.
"""
import sys

import numpy as np

sys.dont_write_bytecode = True
import make_level0p1 as L  # noqa: E402

mt, NS = L.mt, L.NS
SIZE, X, Y = L.SIZE, L.X, L.Y
box, step, tint, paint, flat, noise, clouds, lines = L.box, L.step, L.tint, L.paint, L.flat, L.noise, L.clouds, L.lines
el, sides = L.el, L.sides
FULLUV = [0, 0, 16, 16]


def base(color, seed, smooth=0.5, f0=30, grain=0.04):
    m = flat(color, smooth, f0)
    L.grain(m, seed, grain)
    return m


def metal(color, seed, spacing=128):
    return L.plate(color, seed, 0.6, 80, spacing)


# Textures

def desk_top():
    m = base((176, 150, 118), 301, 0.35, 10, 0.05)
    paint(m, (1 - step(0.0, 0.07, np.abs(noise(302, 3, 24)))) * 0.35, (140, 114, 86))  # wood grain
    paint(m, box(0, 0, 128, 4), (110, 88, 66))
    L.weather(m, 303, 0.25)
    return m


def desk_wood():
    return base((150, 126, 98), 304, 0.3, 10)


def desk_side():
    m = desk_wood()
    paint(m, box(18, 20, 110, 56), (128, 104, 80), height=-1.2)
    paint(m, box(18, 70, 110, 106), (128, 104, 80), height=-1.2)
    paint(m, box(54, 34, 74, 40), (210, 204, 190), height=1.5, smooth=0.8)
    paint(m, box(54, 84, 74, 90), (210, 204, 190), height=1.5, smooth=0.8)
    return m


def desk_lamp():
    m = flat((255, 236, 190), 0.8, 20)
    m.glow += 1.0
    return m


def wardrobe_front():
    m = metal((148, 154, 164), 311)
    paint(m, box(14, 10, 114, 118), (30, 32, 36), height=-3)  # the open door, black inside
    for x, c in ((28, (190, 70, 60)), (44, (70, 110, 170)), (60, (200, 190, 150)), (76, (90, 150, 100)), (92, (120, 90, 140))):
        paint(m, box(x, 22, x + 10, 78 + (x % 3) * 8), c, height=1.2)  # clothes hanging
    paint(m, box(14, 18, 114, 21), (180, 184, 190), height=2)  # the rail
    paint(m, box(14, 96, 114, 112), (60, 52, 46), height=-1)
    return m


def frame_art():
    m = flat((66, 98, 134), 0.5, 10)
    sky = step(0.0, 1.0, Y / SIZE)
    tint(m, sky * 0.6, (1.5, 1.1, 0.7))
    paint(m, box(0, 78, 128, 128), (52, 104, 70))
    paint(m, (1 - step(0.0, 1.0, np.hypot(X - 88, Y - 40) / 16)) * 0.95, (244, 220, 140))
    paint(m, step(0.2, 0.7, noise(321, 9, 3)) * box(0, 66, 128, 90) * 0.5, (110, 128, 150))
    border = 1 - box(8, 8, 120, 120, 0.6)
    paint(m, border, (88, 68, 52), height=1.5)
    return m


def frame_wood():
    return base((88, 68, 52), 322, 0.3, 10)


def clutter(color, accent, seed):
    m = base(color, seed, 0.4, 10)
    paint(m, box(20, 40, 108, 54), accent)
    paint(m, box(20, 80, 108, 90), accent)
    return m


def mug():
    m = base((236, 232, 224), 331, 0.8, 40)
    paint(m, box(0, 0, 128, 18), (58, 36, 26), height=-1.5)  # coffee
    return m


def plate_stack():
    m = base((232, 234, 232), 332, 0.85, 40)
    paint(m, (1 - step(40, 46, np.hypot(X - 64, Y - 64))) * 0.3, (180, 184, 188), height=-1.2)
    paint(m, (1 - step(0.0, 1.0, np.abs(np.hypot(X - 64, Y - 64) - 48) / 3)) * 0.6, (120, 150, 190))
    return m


def sink_front():
    m = metal((178, 182, 188), 341)
    paint(m, box(16, 14, 112, 108), (150, 154, 160), height=-1.2)
    paint(m, box(54, 48, 74, 54), (80, 84, 90), height=1.5)
    return m


def sink_top():
    m = base((184, 188, 194), 342, 0.8, 80)
    paint(m, box(22, 26, 106, 102, 3), (74, 80, 88), height=-4, smooth=0.9)
    paint(m, (1 - step(30, 36, np.hypot(X - 64, Y - 64))) * 0.7, (40, 44, 50), height=-6)
    return m


def faucet():
    return base((196, 200, 206), 343, 0.9, 90)


def fridge_front():
    m = metal((214, 218, 222), 351)
    paint(m, box(0, 60, 128, 64), (60, 64, 70), height=-2)  # the seam between the doors
    paint(m, box(100, 18, 108, 52), (150, 154, 160), height=2.5, smooth=0.9)
    paint(m, box(100, 76, 108, 112), (150, 154, 160), height=2.5, smooth=0.9)
    paint(m, box(24, 22, 40, 26), (90, 200, 120), glow=0.6)  # a little status light
    L.weather(m, 352, 0.4)
    return m


def pots():
    m = base((86, 90, 98), 361, 0.7, 90)
    paint(m, box(0, 0, 128, 12), (60, 64, 70))
    paint(m, (1 - step(24, 28, np.hypot(X - 32, Y - 70))) * 0.9, (44, 48, 54))
    paint(m, (1 - step(18, 22, np.hypot(X - 90, Y - 80))) * 0.9, (150, 80, 60))
    return m


def bench_pad():
    m = base((46, 50, 58), 371, 0.3, 10)
    paint(m, lines(32, 0.8) * 0.5, (20, 22, 26), height=-1)
    paint(m, box(0, 0, 128, 6), (170, 60, 50))
    return m


def bar_steel():
    return base((150, 154, 162), 372, 0.85, 90)


def plates_texture():
    m = base((30, 32, 36), 373, 0.5, 40)
    paint(m, (1 - step(10, 14, np.hypot(X - 64, Y - 64))) * 0.9, (150, 154, 160))
    return m


def bag():
    m = base((176, 52, 44), 381, 0.35, 10, 0.05)
    paint(m, box(0, 14, 128, 20), (30, 30, 34))
    paint(m, box(0, 104, 128, 110), (30, 30, 34))
    L.weather(m, 382, 0.3)
    return m


def mirror():
    m = flat((176, 198, 214), 0.95, 120)
    paint(m, (1 - step(0.0, 1.0, np.abs(X - Y * 0.8 - 60) / 14)) * 0.35, (236, 246, 252))
    paint(m, (1 - step(0.0, 1.0, np.abs(X - Y * 0.8 - 20) / 5)) * 0.3, (236, 246, 252))
    paint(m, 1 - box(6, 6, 122, 122, 0.6), (96, 100, 108), height=1.5, smooth=0.5)
    return m


def dispenser():
    m = metal((220, 224, 228), 391)
    paint(m, box(40, 40, 88, 52), (60, 150, 220), glow=0.2)
    paint(m, box(40, 70, 88, 84), (214, 70, 60), glow=0.2)
    paint(m, box(54, 96, 74, 104), (60, 64, 70), height=-1.5)
    return m


def jug():
    m = flat((156, 206, 238), 0.9, 40)
    paint(m, step(0.2, 0.8, noise(392, 3, 6)) * 0.4, (200, 232, 250))
    return m


def mat_side():
    m = base((70, 92, 150), 396, 0.2, 6, 0.05)
    paint(m, box(0, 0, 128, 10), (50, 68, 112))
    paint(m, box(0, 118, 128, 128), (50, 68, 112))
    paint(m, lines(64, 1.0, axes="y") * 0.6, (46, 60, 98), height=-1)
    return m


def rack_frame():
    return metal((120, 128, 138), 401)


def rack_tray():
    m = base((96, 70, 56), 402, 0.2, 6, 0.06)
    paint(m, step(0.3, 0.7, noise(403, 12)) * 0.5, (70, 50, 40))
    paint(m, 1 - box(4, 4, 124, 124, 0.6), (200, 204, 208), height=1.5)
    return m


def grow_light():
    m = flat((255, 150, 235), 0.7, 20)
    m.glow += 1.0
    paint(m, lines(16, 1.0, axes="x") * 0.5, (255, 214, 250), glow=1.0)
    paint(m, 1 - box(6, 6, 122, 122, 0.8), (150, 150, 156), height=2, glow=0.0)
    return m


def tank_side():
    m = metal((132, 150, 164), 411, 64)
    paint(m, box(0, 0, 128, 10), (80, 90, 100))
    paint(m, box(0, 118, 128, 128), (80, 90, 100))
    paint(m, lines(64, 1.0, axes="x") * 0.5, (80, 90, 100), height=-1)
    return m


def tank_front():
    m = tank_side()
    paint(m, box(54, 24, 74, 104), (50, 60, 70), height=-2)
    paint(m, box(58, 28, 70, 28 + 56), (70, 170, 230), glow=0.4)  # the level gauge
    paint(m, box(48, 90, 80, 100), (190, 196, 204), height=2, smooth=0.9)
    return m


def vines():
    m = flat((54, 124, 60), 0.3, 8)
    alpha = np.zeros((SIZE, SIZE))
    rng = np.random.default_rng(421)
    for i in range(6):
        x0, length = rng.uniform(14, 114), rng.uniform(70, 126)
        sway = rng.uniform(-6, 6)
        stem = (1 - step(1.2, 2.4, np.abs(X - (x0 + sway * np.sin(Y / 24.0 + i))))) * (1 - step(length - 4, length, Y))
        alpha = np.maximum(alpha, stem)
        for yy in range(10, int(length), 14):
            cx = x0 + sway * np.sin(yy / 24.0 + i) + (8 if (yy // 14) % 2 else -8)
            leaf = np.maximum(0, 1 - np.hypot((X - cx) / 7, (Y - yy) / 5))
            alpha = np.maximum(alpha, step(0.0, 0.25, leaf))
            tint(m, step(0.0, 0.5, leaf) * 0.3, (1.0, 1.3, 0.9))
    m.color *= (1 + noise(422, 18) * 0.12)[..., None]
    L.ALPHA["hanging_vines"] = np.where(alpha > 0.5, 255.0, 0.0)
    return m


# Second batch: ceiling speakers, cold and hot stretch details, salvage

def speaker_bottom():
    m = metal((176, 180, 188), 501)
    paint(m, box(16, 16, 112, 112), (40, 42, 46), height=-3)
    paint(m, lines(8, 1.0, axes="x") * box(16, 16, 112, 112), (92, 96, 104), height=1)
    paint(m, box(58, 58, 70, 70), (60, 160, 230), glow=0.3, height=2)
    return m


def speaker_side():
    return metal((150, 156, 166), 502, 64)


def speaker_top():
    return metal((120, 126, 136), 503)


def icicles():
    m = flat((200, 230, 246), 0.9, 90)
    alpha = np.zeros((SIZE, SIZE))
    rng = np.random.default_rng(511)
    for i in range(9):
        cx, length, width = rng.uniform(8, 120), rng.uniform(40, 100), rng.uniform(5, 9)
        alpha = np.maximum(alpha, step(0.0, 1.0, 1 - np.abs(X - cx) / (width * (1 - Y / length)).clip(0.5)) * (Y < length))
    tint(m, 0.25 * noise(512, 10), (0.8, 1.0, 1.1))
    L.ALPHA["icicles"] = np.where(alpha > 0.5, 255.0, 0.0)
    return m


def ice_drift():
    m = base((214, 232, 244), 521, 0.85, 80, 0.05)
    paint(m, step(0.2, 0.7, noise(522, 14)) * 0.5, (160, 200, 232))
    return m


def slag_pile():
    m = base((42, 36, 34), 531, 0.3, 10, 0.08)
    paint(m, step(0.55, 0.8, noise(532, 12)), (255, 120, 30), glow=0.9)
    return m


def vent_top():
    m = metal((70, 68, 70), 541, 32)
    paint(m, lines(16, 3.0, axes="x") * box(8, 8, 120, 120), (255, 130, 40), glow=1.0)
    return m


def vent_side():
    return metal((70, 68, 70), 542, 64)


# Third batch: the wormhole lab and Rhea's things

def countdown_front():
    m = flat((20, 22, 26), 0.6, 20)
    paint(m, 1 - box(4, 4, 124, 124, 0.6), (90, 94, 102), height=1.5)
    red = (255, 40, 30)
    for x0 in (10, 36, 72, 98):
        paint(m, box(x0, 34, x0 + 20, 94) * (1 - box(x0 + 5, 40, x0 + 15, 88)), red, glow=1.0)
    paint(m, box(58, 50, 66, 58), red, glow=1.0)
    paint(m, box(58, 70, 66, 78), red, glow=1.0)
    return m


def coil_side():
    m = metal((110, 104, 128), 601, 32)
    paint(m, lines(32, 3.0, axes="y") * 0.8, (170, 90, 255), glow=0.8)
    return m


def coil_core():
    m = flat((190, 130, 255), 0.7, 20)
    m.glow += 1.0
    paint(m, (0.5 + 0.5 * noise(602, 6)) * 0.5, (240, 220, 255), glow=1.0)
    return m


def lab_notes():
    m = flat((232, 228, 214), 0.3, 10)
    for i, y in enumerate(range(14, 120, 12)):
        paint(m, box(12, y, 116 - (i * 7) % 30, y + 2), (60, 60, 90))
    paint(m, box(0, 0, 128, 3), (190, 186, 172))
    L.weather(m, 603, 0.2)
    return m


def lab_coat():
    """A coat on a hook, cut out: shoulders, two sleeves hanging, the body flaring a little toward the hem."""
    m = flat((226, 230, 234), 0.3, 10)
    half = np.abs(X - 63.5)
    body = (half < 26 + Y * 0.08) & (Y > 10)
    sleeves = (half > 28 + Y * 0.06) & (half < 44 + Y * 0.04) & (Y > 16) & (Y < 104)
    hook = (half < 4) & (Y <= 10)
    shape = body | sleeves | hook
    L.ALPHA["lab_coat"] = np.where(shape, 255.0, 0.0)
    # A darker rim so the white coat reads against the pale station wall.
    inner = shape.copy()
    for dy, dx in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        inner &= np.roll(np.roll(shape, dy, 0), dx, 1)
    paint(m, (shape & ~inner).astype(float), (128, 134, 142), height=-1)
    paint(m, hook.astype(float), (120, 124, 130))
    paint(m, ((half > 26 + Y * 0.08) & (half < 29 + Y * 0.06)).astype(float) * 0.6, (180, 186, 194), height=-1)  # where sleeve meets body
    paint(m, box(44, 0, 84, 30), (200, 206, 212), height=-1)  # the collar
    paint(m, box(60, 30, 68, 128), (190, 196, 204), height=-1)  # the opening
    paint(m, box(16, 74, 44, 100), (206, 212, 218), height=1.5)  # a pocket
    paint(m, box(18, 80, 24, 100), (50, 80, 170))  # a pen
    L.weather(m, 604, 0.3)
    return m


TEXTURES = {
    "desk_top": desk_top, "desk_wood": desk_wood, "desk_side": desk_side, "desk_lamp": desk_lamp, "wardrobe_front": wardrobe_front,
    "frame_art": frame_art, "frame_wood": frame_wood,
    "clutter_book": lambda: clutter((176, 60, 52), (230, 220, 200), 333),
    "clutter_cloth": lambda: clutter((70, 110, 170), (210, 214, 220), 334),
    "clutter_mug": mug, "plate_stack": plate_stack,
    "sink_front": sink_front, "sink_top": sink_top, "faucet": faucet, "fridge_front": fridge_front, "pots": pots,
    "bench_pad": bench_pad, "bar_steel": bar_steel, "plates": plates_texture, "bag": bag, "mirror": mirror,
    "dispenser": dispenser, "jug": jug, "mat_side": mat_side,
    "rack_frame": rack_frame, "rack_tray": rack_tray, "grow_light": grow_light, "tank_side": tank_side, "tank_front": tank_front,
    "hanging_vines": vines,
    "speaker_bottom": speaker_bottom, "speaker_side": speaker_side, "speaker_top": speaker_top, "icicles": icicles, "ice_drift": ice_drift,
    "slag_pile": slag_pile, "vent_top": vent_top, "vent_side": vent_side,
    "countdown_front": countdown_front, "coil_side": coil_side, "coil_core": coil_core, "lab_notes": lab_notes, "lab_coat": lab_coat,
}


# Models. A facing block's front is north; wall-mounted ones sit against the south face (the room's wall behind them).

def model(textures, elements):
    textures = {k: NS + v for k, v in textures.items()}
    textures.setdefault("particle", next(iter(textures.values())))
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def cuboid(frm, to, face, **extra):
    """A box whose faces all show one texture in full (so small boxes show a whole small picture)."""
    return el(frm, to, sides(face, uv=FULLUV), **extra)


def model_desk():
    # Drawers only on the pedestals' fronts (north); the other faces and the top's edge are plain wood.
    pedestal = lambda x0, x1: el((x0, 0, 1), (x1, 11, 15), {"north": {"texture": "#side", "uv": FULLUV},
                                                            **sides("#wood", ("south", "east", "west", "up", "down"))})
    return model({"top": "desk_top", "side": "desk_side", "wood": "desk_wood", "lamp": "desk_lamp", "steel": "bar_steel"}, [
        el((0, 11, 0), (16, 13, 16), {"up": "#top", "down": "#wood", **sides("#wood", ("north", "south", "east", "west"))}),
        pedestal(0, 4), pedestal(12, 16),
        cuboid((11, 13, 11), (14, 14, 14), "#steel"), cuboid((12, 14, 12), (13, 16, 13), "#steel"),
        el((10, 15, 10), (15, 16, 15), {"up": "#lamp", "down": "#lamp", **sides("#lamp", ("north", "south", "east", "west"))}, shade=False, light_emission=14)])


def model_wardrobe():
    return {"parent": "minecraft:block/orientable", "textures": {"front": NS + "wardrobe_front", "side": NS + "locker_side", "top": NS + "locker_side"}}


def model_bunk():
    posts = [((0, 0, 0), (2, 16, 2)), ((14, 0, 0), (16, 16, 2)), ((0, 0, 14), (2, 16, 16)), ((14, 0, 14), (16, 16, 16))]
    return model({"side": "bed_side", "top": "bed_top", "steel": "bar_steel"},
                 [cuboid(a, b, "#steel") for a, b in posts] + [
                     el((2, 2, 0.5), (14, 6, 15.5), {"up": "#top", "down": "#side", **sides("#side", ("north", "south", "east", "west"))}),
                     el((2, 9, 0.5), (14, 13, 15.5), {"up": "#top", "down": "#side", **sides("#side", ("north", "south", "east", "west"))}),
                     cuboid((14, 3, 3), (16, 4, 13), "#steel")])


def model_frame():
    return model({"art": "frame_art", "wood": "frame_wood"}, [
        el((2, 3, 14.5), (14, 13, 16), {"north": {"texture": "#art", "uv": FULLUV}, **sides("#wood", ("south", "east", "west", "up", "down"), uv=FULLUV)})])


def model_items():
    # A mug, a book and a folded shirt, standing where the table top is (3 pixels below this block's floor).
    return model({"book": "clutter_book", "cloth": "clutter_cloth", "mug": "clutter_mug"}, [
        cuboid((2, -3, 3), (6, -2, 11), "#book"), cuboid((2.5, -2, 3.5), (5.5, -1, 10.5), "#cloth"),
        el((9, -3, 4), (12, 0, 7), {"up": {"texture": "#mug", "uv": FULLUV}, **sides("#mug", ("north", "south", "east", "west", "down"), uv=FULLUV)}),
        cuboid((8, -3, 9), (13, -2, 14), "#cloth")])


def model_sink():
    return model({"front": "sink_front", "top": "sink_top", "side": "locker_side", "faucet": "faucet"}, [
        el((0, 0, 0), (16, 13, 16), {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"}),
        el((0, 13, 0), (16, 14, 16), {"up": "#top", "down": "#side", **sides("#side", ("north", "south", "east", "west"))}),
        cuboid((7.5, 14, 11), (8.5, 16, 12), "#faucet"), cuboid((7.5, 15, 8), (8.5, 16, 12), "#faucet")])


def model_fridge():
    return {"parent": "minecraft:block/orientable", "textures": {"front": NS + "fridge_front", "side": NS + "locker_side", "top": NS + "locker_side"}}


def model_pots():
    return model({"pot": "pots", "steel": "bar_steel"}, [
        cuboid((1, 12, 14), (15, 13, 15.5), "#steel"),
        cuboid((2, 6, 13), (6, 12, 16), "#pot"), cuboid((7.5, 8, 13), (11.5, 12, 16), "#pot"), cuboid((12, 4, 13), (14, 12, 15.5), "#steel")])


def model_plates():
    return model({"plate": "plate_stack"}, [
        el((3, -3, 3), (13, 0, 13), {"up": {"texture": "#plate", "uv": FULLUV}, **sides("#plate", ("north", "south", "east", "west", "down"), uv=FULLUV)}),
        cuboid((5, 0, 5), (11, 1, 11), "#plate")])


def model_bench_press():
    return model({"pad": "bench_pad", "bar": "bar_steel", "plates": "plates"}, [
        cuboid((4, 0, 2), (6, 6, 14), "#bar"), cuboid((10, 0, 2), (12, 6, 14), "#bar"),
        el((3, 6, 1), (13, 8, 15), {"up": {"texture": "#pad", "uv": FULLUV}, **sides("#pad", ("north", "south", "east", "west", "down"), uv=FULLUV)}),
        cuboid((0, 11, 3), (16, 12, 4), "#bar"), cuboid((0, 11, 12), (16, 12, 13), "#bar"),
        cuboid((0, 8, 3), (1, 15, 13), "#plates"), cuboid((15, 8, 3), (16, 15, 13), "#plates"),
        cuboid((1, 8, 2), (3, 12, 4), "#bar"), cuboid((13, 8, 2), (15, 12, 4), "#bar")])


def model_bag():
    return model({"bag": "bag", "steel": "bar_steel"}, [
        cuboid((3, 0, 3), (13, 2, 13), "#steel"), cuboid((7, 2, 7), (9, 4, 9), "#steel"),
        el((4.5, 4, 4.5), (11.5, 16, 11.5), {"up": "#bag", "down": "#bag", **sides("#bag", ("north", "south", "east", "west"))})])


def model_mirror():
    return model({"glass": "mirror", "steel": "bar_steel"}, [
        el((1, 1, 15), (15, 15, 16), {"north": {"texture": "#glass", "uv": FULLUV}, **sides("#steel", ("south", "east", "west", "up", "down"), uv=FULLUV)})])


def model_dispenser():
    return model({"body": "dispenser", "jug": "jug", "side": "locker_side"}, [
        el((3, 0, 3), (13, 11, 13), {"north": "#body", "south": "#side", "east": "#side", "west": "#side", "up": "#side", "down": "#side"}),
        el((4, 11, 4), (12, 16, 12), {"up": "#jug", "down": "#jug", **sides("#jug", ("north", "south", "east", "west"))}, shade=False)])


def model_mats():
    return model({"mat": "mat_side"}, [
        el((1, 0, 1), (15, 2, 15), {"up": "#mat", "down": "#mat", **sides("#mat", ("north", "south", "east", "west"))}),
        el((2, 2, 2), (14, 4, 14), {"up": "#mat", "down": "#mat", **sides("#mat", ("north", "south", "east", "west"))}),
        el((1, 4, 1), (15, 6, 15), {"up": "#mat", "down": "#mat", **sides("#mat", ("north", "south", "east", "west"))})])


def model_rack():
    elements = [cuboid((0, 0, 0), (2, 16, 2), "#frame"), cuboid((14, 0, 0), (16, 16, 2), "#frame"),
                cuboid((0, 0, 14), (2, 16, 16), "#frame"), cuboid((14, 0, 14), (16, 16, 16), "#frame")]
    for y in (1, 6, 11):
        elements.append(el((1, y, 1), (15, y + 1, 15), {"up": "#tray", "down": "#frame", **sides("#frame", ("north", "south", "east", "west"))}))
        elements.append(el((0, y + 1, 8), (16, y + 5, 8), {"north": "#plant", "south": "#plant"}, shade=False))
        elements.append(el((8, y + 1, 0), (8, y + 5, 16), {"east": "#plant", "west": "#plant"}, shade=False))
    return model({"frame": "rack_frame", "tray": "rack_tray", "plant": "planter_plant"}, elements)


def model_grow_light():
    return model({"light": "grow_light", "steel": "bar_steel"}, [
        el((1, 14, 1), (15, 16, 15), {"down": {"texture": "#light", "uv": FULLUV}, **sides("#steel", ("north", "south", "east", "west", "up"), uv=FULLUV)}, shade=False, light_emission=15)])


def model_tank():
    return model({"side": "tank_side", "front": "tank_front", "steel": "bar_steel"}, [
        el((2, 0, 2), (14, 16, 14), {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#side", "down": "#side"}),
        cuboid((6, 10, 14), (10, 12, 16), "#steel")])


def model_vines():
    return model({"v": "hanging_vines"}, [
        el((0, 0, 8), (16, 16, 8), {"north": "#v", "south": "#v"}, shade=False),
        el((8, 0, 0), (8, 16, 16), {"east": "#v", "west": "#v"}, shade=False)])


def model_speaker():
    return {"parent": "minecraft:block/cube_bottom_top", "textures": {"bottom": NS + "speaker_bottom", "side": NS + "speaker_side", "top": NS + "speaker_top"}}


def model_icicles():
    return model({"v": "icicles"}, [
        el((0, 0, 8), (16, 16, 8), {"north": "#v", "south": "#v"}, shade=False),
        el((8, 0, 0), (8, 16, 16), {"east": "#v", "west": "#v"}, shade=False)])


def model_drift():
    return model({"t": "ice_drift"}, [el((1, 0, 1), (15, 5, 15), {**sides("#t", uv=FULLUV)})])


def model_slag():
    return model({"t": "slag_pile"}, [el((2, 0, 2), (14, 4, 14), {**sides("#t", uv=FULLUV)})])


def model_vent():
    return model({"top": "vent_top", "side": "vent_side"}, [
        el((1, 0, 1), (15, 2, 15), {"up": {"texture": "#top", "uv": FULLUV}, **sides("#side", ("north", "south", "east", "west", "down"), uv=FULLUV)})])


def model_countdown():
    return model({"front": "countdown_front", "side": "speaker_side"}, [
        el((1, 3, 14), (15, 13, 16), {"north": {"texture": "#front", "uv": FULLUV}, **sides("#side", ("south", "east", "west", "up", "down"), uv=FULLUV)})])


def model_coil():
    rings = [cuboid((2, y, 2), (14, y + 2, 14), "#side") for y in (1, 7, 13)]
    return model({"side": "coil_side", "core": "coil_core"}, [cuboid((5, 0, 5), (11, 16, 11), "#core")] + rings)


def model_notes():
    return model({"p": "lab_notes"}, [
        cuboid((2, 0, 3), (12, 1, 13), "#p"),
        el((4, 1, 2), (14, 2, 12), sides("#p", uv=FULLUV), rotation={"angle": 22.5, "axis": "y", "origin": [9, 1, 7]})])


def model_coat():
    return model({"c": "lab_coat"}, [
        el((0, 0, 15), (16, 16, 15.5), {"north": {"texture": "#c", "uv": FULLUV}, "south": {"texture": "#c", "uv": [16, 0, 0, 16]}})])


MODELS = {
    "crew_desk": model_desk, "open_wardrobe": model_wardrobe, "bunk_bed": model_bunk, "wall_frame": model_frame, "personal_items": model_items,
    "kitchen_sink": model_sink, "fridge": model_fridge, "hanging_pots": model_pots, "dish_stack": model_plates,
    "bench_press": model_bench_press, "punching_bag": model_bag, "wall_mirror": model_mirror, "water_dispenser": model_dispenser, "mat_stack": model_mats,
    "hydroponic_rack": model_rack, "grow_light": model_grow_light, "water_tank": model_tank, "hanging_vines": model_vines,
    "ceiling_speaker": model_speaker, "icicles": model_icicles, "ice_drift": model_drift, "slag_pile": model_slag, "heat_vent": model_vent,
    "countdown_screen": model_countdown, "bridge_coil": model_coil, "research_notes": model_notes, "lab_coat": model_coat,
}
FACING = {"crew_desk", "open_wardrobe", "bunk_bed", "wall_frame", "kitchen_sink", "fridge", "hanging_pots", "bench_press", "wall_mirror", "water_dispenser", "water_tank", "countdown_screen", "lab_coat"}

NAMES_EN = {
    "crew_desk": "Crew Desk", "open_wardrobe": "Open Wardrobe", "bunk_bed": "Bunk Bed", "wall_frame": "Framed Picture", "personal_items": "Personal Belongings",
    "kitchen_sink": "Galley Sink", "fridge": "Galley Fridge", "hanging_pots": "Hanging Pots", "dish_stack": "Stacked Dishes",
    "bench_press": "Bench Press", "punching_bag": "Punching Bag", "wall_mirror": "Wall Mirror", "water_dispenser": "Water Dispenser", "mat_stack": "Stacked Mats",
    "hydroponic_rack": "Hydroponic Rack", "grow_light": "Grow Light", "water_tank": "Water Tank", "hanging_vines": "Hanging Vines",
    "ceiling_speaker": "Ceiling Speaker", "icicles": "Icicles", "ice_drift": "Ice Drift", "slag_pile": "Slag Pile", "heat_vent": "Heat Vent",
    "countdown_screen": "Countdown Screen", "bridge_coil": "Bridge Emitter Coil", "research_notes": "Research Notes", "lab_coat": "Lab Coat",
}
NAMES_ZH = {
    "crew_desk": "船員書桌", "open_wardrobe": "敞開的衣櫃", "bunk_bed": "上下鋪", "wall_frame": "相框", "personal_items": "私人物品",
    "kitchen_sink": "廚房水槽", "fridge": "廚房冰箱", "hanging_pots": "掛著的鍋具", "dish_stack": "一疊餐盤",
    "bench_press": "臥推椅", "punching_bag": "沙包", "wall_mirror": "牆鏡", "water_dispenser": "飲水機", "mat_stack": "一疊墊子",
    "hydroponic_rack": "水培架", "grow_light": "植物生長燈", "water_tank": "水箱", "hanging_vines": "垂掛的藤蔓",
    "ceiling_speaker": "天花板廣播喇叭", "icicles": "冰柱", "ice_drift": "積冰", "slag_pile": "熔渣堆", "heat_vent": "排熱孔",
    "countdown_screen": "倒數螢幕", "bridge_coil": "橋接發射線圈", "research_notes": "研究筆記", "lab_coat": "實驗袍",
}


# What B.N.T.G. comes for: wiring and electronics broken down into raw materials (replaces these blocks' plain "drop itself" tables).
def salvage(name, entries):
    pools = []
    for item, low, high, chance in entries:
        entry = {"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]}
        conditions = [{"condition": "minecraft:survives_explosion"}]
        if chance < 1:
            conditions.append({"condition": "minecraft:random_chance", "chance": chance})
        pools.append({"rolls": 1.0, "conditions": conditions, "entries": [entry]})
    return {"type": "minecraft:block", "pools": pools, "random_sequence": f"noclip_backrooms:blocks/{name}"}


SALVAGE = {
    "hanging_cables": [("minecraft:copper_ingot", 0, 2, 1), ("minecraft:redstone", 0, 1, 0.5)],
    "console": [("minecraft:redstone", 1, 2, 1), ("minecraft:iron_nugget", 1, 3, 1)],
    "broken_console": [("minecraft:redstone", 1, 3, 1), ("minecraft:iron_nugget", 2, 5, 1), ("minecraft:gold_nugget", 0, 2, 1),
                       ("noclip_backrooms:alloy", 1, 1, 0.1)],
    "server_rack": [("minecraft:redstone", 2, 4, 1), ("minecraft:gold_nugget", 1, 4, 1), ("minecraft:iron_ingot", 0, 2, 1),
                    ("minecraft:copper_ingot", 1, 2, 1), ("noclip_backrooms:alloy", 1, 1, 0.15)],
}


MESSAGES_EN = [
    "[Notes] Einstein-Rosen bridge, run 41: the field holds. Countdown set. If this works, nobody will ever be far from anywhere again.",
    "[Notes] The countdown screens all read zero. They have read zero for a very long time. I do not remember turning them off.",
    "[Notes] The bridge did not open where we aimed it. It opened under us. I could see the station from outside, and then I could not.",
    "[Notes] Nobody answers. The corridors are yellow now, and I keep walking them. I am only going to sit down for a moment.",
]
MESSAGES_ZH = [
    "【筆記】愛因斯坦－羅森橋，第 41 次運轉：場穩住了。倒數已設定。成功的話，往後沒有人會離任何地方太遠。",
    "【筆記】倒數螢幕全都停在零。已經停很久了。我不記得自己關過它們。",
    "【筆記】橋沒有開在我們瞄準的地方，它開在我們腳下。我看見了站的外面，然後就看不見了。",
    "【筆記】沒有人回應。走廊現在是黃色的，我一直在走。我只是想坐下來一下。",
]


def main():
    L.TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = L.TEXTURES
    for name, draw in TEXTURES.items():
        L.save_texture(name, draw())
    blockstates = {}
    for name, build in MODELS.items():
        L.write_json(L.MODELS / f"{name}.json", build())
        blockstates[name] = L.facing_state(name) if name in FACING else {"variants": {"": {"model": NS + name}}}
        L.write_json(L.ASSETS / f"blockstates/{name}.json", blockstates[name])
        L.write_json(L.ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})
        L.write_json(L.DATA / f"noclip_backrooms/loot_table/blocks/{name}.json", L.loot_table(name))
    for name, entries in SALVAGE.items():
        L.write_json(L.DATA / f"noclip_backrooms/loot_table/blocks/{name}.json", salvage(name, entries))
    # Every model's textures must exist (the rest of the level's models are checked by make_level0p1.py).
    for name in MODELS:
        data = L.json.loads((L.MODELS / f"{name}.json").read_text(encoding="utf8"))
        for value in data.get("textures", {}).values():
            if value.startswith(NS):
                for suffix in ("", "_n", "_s"):
                    assert (L.TEXTURES / f"{value[len(NS):]}{suffix}.png").is_file(), (name, value, suffix)
    for file, names in (("en_us.json", NAMES_EN), ("zh_tw.json", NAMES_ZH)):
        path = L.ASSETS / "lang" / file
        data = L.json.loads(path.read_text(encoding="utf8"))
        for name, text in names.items():
            data[f"block.noclip_backrooms.{name}"] = text
        path.write_text(L.json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
    for file, texts in (("en_us.json", MESSAGES_EN), ("zh_tw.json", MESSAGES_ZH)):
        path = L.ASSETS / "lang" / file
        data = L.json.loads(path.read_text(encoding="utf8"))
        for i, text in enumerate(texts):
            data[f"message.noclip_backrooms.level0p1.rhea.{i}"] = text
        path.write_text(L.json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
    L.update_tags(list(MODELS))
    print(f"{len(TEXTURES)} textures, {len(MODELS)} blocks")


if __name__ == "__main__":
    main()
