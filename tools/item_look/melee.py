"""The 41 melee weapons. Each is drawn standing up, hilt at the bottom, the flat of a blade facing the viewer (x across,
z its thickness); centre_y() then moves it so its middle is the model's middle. Proportions are real ones (a block is
a metre: a knife about 0.9, a long sword 1.8, a polearm 2.7 sixteenths... of the block, i.e. 14, 28, 44).
"""
import math

import numpy as np

from lib import bar, box, centre_y, define, extent, hands, pose, rgba, third
from mats import metal

define("coins", lambda: _coins())


def _coins():
    from lib import S, grime
    base = metal((132, 100, 62), 300, smooth=0.45, f0=232)[0][..., :3].copy()
    y, x = np.mgrid[0:S, 0:S]
    for cy in range(8, S, 16):
        for cx in range(8, S, 16):
            r = np.hypot(x - cx, y - cy)
            base[(r > 6.5) & (r < 7.5)] *= 0.55
            base[(np.abs(x - cx) < 2) & (np.abs(y - cy) < 2)] = (0.12, 0.1, 0.08)
    return rgba(grime(base, 301, 0.35, 0.2)), (0.45, 232, 0.2)


# ---------------------------------------------------------------- parts


def grip(y0, y1, w, tex):
    return [bar(y0, y1, w, w, tex, seed=int(y0 * 7))]


def knob(y, s, tex, h=None):
    return [bar(y - (h or s), y, s, s, tex, seed=int(y * 3))]


def cross(y, span, h, t, tex, droop=0.0):
    if not droop:
        return [bar(y, y + h, span, t, tex, seed=int(y * 5))]
    half = span / 2
    return [bar(y, y + h, h + 0.4, t + 0.3, tex),
            box((8, y, 8 - t / 2), (8 + half, y + h, 8 + t / 2), tex, rot=("z", droop, (8, y + h / 2, 8))),
            box((8 - half, y, 8 - t / 2), (8, y + h, 8 + t / 2), tex, rot=("z", -droop, (8, y + h / 2, 8)))]


def blade(y0, y1, w, t, tex="blade", edge="blade_edge", tip=4.0, fuller=0.0, double=True, steps=3):
    """A straight blade: a core, bright bevelled edges, a stepped point, a fuller down the middle."""
    e = min(0.7, w * 0.22)
    top = y1 - tip
    core_l = 8 - w / 2 + (e if double else 0)
    core_r = 8 + w / 2 - e
    out = [box((core_l, y0, 8 - t / 2), (core_r, top, 8 + t / 2), tex, seed=int(y0 * 11 + w))]
    out.append(box((core_r, y0, 8 - t * 0.3), (8 + w / 2, top, 8 + t * 0.3), edge, seed=3))
    if double:
        out.append(box((8 - w / 2, y0, 8 - t * 0.3), (core_l, top, 8 + t * 0.3), edge, seed=4))
    for k in range(steps):
        ww = w * (1 - (k + 1) / (steps + 1))
        cx = 8 if double else 8 + (w - ww) / 2
        out.append(box((cx - ww / 2, top + k * tip / steps, 8 - t * 0.4), (cx + ww / 2, top + (k + 1) * tip / steps, 8 + t * 0.4), edge, seed=5 + k))
    if fuller:
        out.append(box((8 - fuller / 2, y0 + 1, 8 - t / 2 - 0.05), (8 + fuller / 2, top - 1.5, 8 + t / 2 + 0.05), "blade_dark",
                       faces=("north", "south"), seed=9))
    return out


def curved(y0, length, w, t, bend, tex="blade", edge="blade_edge", segs=5, tail=1.6, tip=3.0):
    """A curved, single-edged blade: segments turning a little more each, the edge on the outer side."""
    out = []
    px, py = 8.0, y0
    seg = length / segs
    for i in range(segs):
        ang = bend * (i + 0.5) / segs * (tail if i == segs - 1 else 1)
        rot = ("z", -ang, (px, py, 8))
        out.append(box((px - w / 2 + 0.55, py, 8 - t / 2), (px + w / 2, py + seg + 0.35, 8 + t / 2), tex, rot=rot, seed=20 + i))
        out.append(box((px - w / 2, py, 8 - t * 0.3), (px - w / 2 + 0.55, py + seg + 0.35, 8 + t * 0.3), edge, rot=rot, seed=30 + i))
        r = math.radians(ang)
        px += seg * math.sin(r)
        py += seg * math.cos(r)
    r = math.radians(bend * tail)
    rot = ("z", -bend * tail, (px, py, 8))
    out.append(box((px - w * 0.3, py, 8 - t * 0.35), (px + w * 0.45, py + tip, 8 + t * 0.35), edge, rot=rot, seed=40))
    return out


def pole(y0, y1, w, tex, bands=(), band_tex="steel_dark"):
    out = [bar(y0, y1, w, w, tex, seed=int(y1))]
    for b in bands:
        out.append(bar(b, b + 1.0, w + 0.35, w + 0.35, band_tex, seed=int(b * 13)))
    return out


def leaf(y0, w, t, length, tex="blade", edge="blade_edge"):
    """A spear's leaf-shaped head: widening, then narrowing to the point."""
    widths = [0.45, 0.8, 1.0, 0.9, 0.7, 0.45, 0.22]
    seg = length / len(widths)
    out = []
    for i, f in enumerate(widths):
        ww = w * f
        out.append(box((8 - ww / 2, y0 + i * seg, 8 - t / 2), (8 + ww / 2, y0 + (i + 1) * seg + 0.05, 8 + t / 2),
                       edge if f < 0.5 else tex, seed=50 + i))
    return out


def head(x0, x1, y0, y1, t, tex, seed=0):
    return [box((x0, y0, 8 - t / 2), (x1, y1, 8 + t / 2), tex, seed=seed)]


def studs(y0, y1, n, r, tex="nail_heads", size=0.8, seed=0):
    """Nails or spikes sticking out of a round shaft of radius r."""
    out = []
    for i in range(n):
        a = i * 2.39996 + seed
        y = y0 + (y1 - y0) * (i + 0.5) / n
        x, z = 8 + math.cos(a) * r, 8 + math.sin(a) * r
        out.append(box((x - size / 2, y - size / 2, z - size / 2), (x + size / 2, y + size / 2, z + size / 2), tex, seed=60 + i))
    return out


def chain(x, y0, y1, tex="chain", w=0.7):
    out = []
    y = y0
    i = 0
    while y < y1:
        wide = i % 2 == 0
        out.append(box((x - (w if wide else w * 0.45), y, 8 - (w * 0.45 if wide else w)), (x + (w if wide else w * 0.45), min(y1, y + 1.1),
                                                                                             8 + (w * 0.45 if wide else w)), tex, seed=70 + i))
        y += 1.0
        i += 1
    return out


# ---------------------------------------------------------------- poses


# Held upright from outside (tried against [0, 0, 0], [90, 0, 0] and [0, 0, 90] in ItemLookClientTest; only this one
# stands the weapon up in the hand).
THIRD_TURN = [-90, 0, 0]
THIRD_TRY = {}
MAX_SPAN = 46.0


def display(elements, grip_y, name="", k=1.0):
    """Poses for a weapon held by its grip at grip_y (before centring). k: how much the model was shrunk to fit
    Minecraft's limits (the poses scale it back up)."""
    lo, hi = extent(elements, 1)
    length = (hi - lo) / k
    d = ((lo + hi) / 2 - grip_y) / k
    fp = 0.34 if length < 20 else 0.42 if length <= 28 else 0.42 * (28 / length) ** 0.5
    tp = 0.85 if length <= 28 else 0.85 * (28 / length) ** 0.5
    ty = max(d * fp - 4.5, 1.5 if length < 20 else -9)
    out = {
        "gui": pose([12, 30, -42], [0, 0, 0], round(min(0.95, 21 / length) / k, 3)),
        "ground": pose([0, 0, 0], [0, 2, 0], round(0.5 * min(1.0, 22 / length) / k, 3)),
        "fixed": pose([0, 0, -45], [0, 0, 0], round(min(0.9, 20 / length) / k, 3)),
    }
    out.update(hands([-30, 0, 6], [1.0, round(ty, 2), -1.0], round(fp / k, 3)))
    out.update(third(THIRD_TRY.get(name, THIRD_TURN), [0, round(d * tp - 4.0, 2), 1.0], round(tp / k, 3)))
    return out


def fit(elements):
    """Shrinks a weapon longer than Minecraft allows (48 sixteenths) evenly about its middle; returns the factor."""
    lo, hi = extent(elements, 1)
    if hi - lo <= MAX_SPAN:
        return 1.0
    k = MAX_SPAN / (hi - lo)
    cy = (lo + hi) / 2

    def p(v):
        return [round(8 + (v[0] - 8) * k, 3), round(cy + (v[1] - cy) * k, 3), round(8 + (v[2] - 8) * k, 3)]

    for e in elements:
        e["from"], e["to"] = p(e["from"]), p(e["to"])
        if "rotation" in e:
            e["rotation"]["origin"] = p(e["rotation"]["origin"])
    return k


def flatten(parts):
    out = []
    for p in parts:
        if isinstance(p, dict):
            out.append(p)
        else:
            out.extend(flatten(p))
    return out


def weapon(parts, grip_y):
    import inspect
    name = inspect.stack()[1].function
    elements = flatten(parts)
    lo, hi = extent(elements, 1)
    k = fit(elements)
    grip_scaled = (lo + hi) / 2 + (grip_y - (lo + hi) / 2) * k
    poses = display(elements, grip_scaled, name, k)
    centre_y(elements)
    return elements, poses


# ---------------------------------------------------------------- the weapons
# Each returns (elements, display).


def blunt_rusty_pipe():
    return weapon([
        grip(0, 7, 2.4, "tape_black"), bar(-0.6, 0, 2.2, 2.2, "galv_steel"),
        bar(0, 12, 2, 2, "rust_pipe", seed=1), bar(12, 21, 2, 2, "rust_pipe", seed=2),
        bar(21, 23.6, 2.8, 2.8, "galv_steel", seed=3), bar(23.6, 27.4, 2.8, 2.8, "galv_steel", seed=4),
        [box((9.4, 24.2, 7), (11.6, 26.8, 9), "galv_steel", seed=5), box((11.6, 24, 6.8), (12, 27, 9.2), "rust_pipe", seed=6)],
    ], 3.5)


def blunt_nailed_bat():
    parts = [grip(0, 7, 2.0, "tape_black"), knob(0, 2.6, "bat_wood", 0.8)]
    for k, (y0, y1, w) in enumerate(((7, 12, 2.2), (12, 17, 2.7), (17, 22, 3.2), (22, 26.5, 3.5))):
        parts.append(bar(y0, y1, w, w, "bat_wood", seed=k))
    parts.append(studs(16, 26, 11, 1.85, "nail_heads", 0.7))
    parts.append(bar(26.5, 27, 3.0, 3.0, "bat_wood"))
    return weapon(parts, 3.5)


def blunt_barbed_kanabo():
    parts = [grip(0, 8, 2.2, "leather_black"), knob(0, 3, "iron_black", 1)]
    parts.append(bar(8, 28, 4.2, 4.2, "dark_wood"))
    for y in range(10, 27, 3):
        parts.append(bar(y, y + 0.5, 4.6, 4.6, "barbed", seed=y))
    parts.append(studs(10, 27, 18, 2.3, "iron_black", 0.9, 1))
    return weapon(parts, 4)


def blunt_iron_mace():
    return weapon([grip(0, 9, 1.8, "leather_brown"), knob(0, 2.4, "steel_worn", 1), bar(9, 19, 1.4, 1.4, "steel_worn"),
                   bar(19, 24, 4.0, 4.0, "iron_black"), studs(19.5, 23.5, 10, 2.2, "steel", 1.0)], 4)


def blunt_flanged_mace():
    parts = [grip(0, 9, 1.8, "leather_black"), knob(0, 2.4, "steel", 1), bar(9, 18, 1.5, 1.5, "steel")]
    parts.append([box((5.4, 18, 7.75), (10.6, 25, 8.25), "steel_dark", seed=1), box((7.75, 18, 5.4), (8.25, 25, 10.6), "steel_dark", seed=2)])
    for a in (45, -45):
        parts.append([box((5.4, 18, 7.75), (10.6, 25, 8.25), "steel_dark", rot=("y", a, (8, 21, 8)), seed=a)])
    parts.append(bar(25, 26.5, 1.2, 1.2, "steel"))
    return weapon(parts, 4)


def blunt_warhammer_1h():
    return weapon([grip(0, 9, 1.8, "cord_black"), knob(0, 2.2, "steel_dark", 1), bar(9, 21, 1.5, 1.5, "dark_wood"),
                   [box((4.5, 20, 6.8), (8, 23.4, 9.2), "steel_dark", seed=1), box((8, 20.6, 7.2), (12.6, 22.8, 8.8), "steel_worn", seed=2),
                    box((3.2, 20.8, 7.3), (4.5, 22.6, 8.7), "steel", seed=3)], bar(23.4, 25.6, 1.0, 1.0, "steel")], 4)


def blunt_clump_flail():
    parts = [grip(0, 9, 1.9, "leather_brown"), knob(0, 2.4, "bone_dark", 1), bar(9, 11, 2.3, 2.3, "iron_black")]
    parts.append(chain(8, 11, 17))
    knuckles = [((6.2, 17, 6.4), (9.6, 20.2, 9.4)), ((7.6, 19.4, 7.0), (10.8, 22.6, 10.0)), ((5.4, 19.8, 6.6), (8.4, 22.8, 9.6)),
                ((6.8, 22.2, 6.8), (9.6, 24.8, 9.6))]
    for i, (a, b) in enumerate(knuckles):
        parts.append([box(a, b, "bone" if i % 2 == 0 else "flesh", seed=80 + i)])
    parts.append(studs(18, 24, 6, 2.0, "bone_dark", 0.8, 2))
    return weapon(parts, 4)


def blunt_twohand_maul():
    return weapon([grip(0, 14, 2.0, "leather_black"), knob(0, 2.4, "steel_dark", 1), bar(14, 28, 2.0, 2.0, "dark_wood"),
                   [box((3.4, 27, 5.8), (12.6, 33, 10.2), "steel_dark", seed=1)],
                   [box((3.2, 27.6, 5.6), (12.8, 28.4, 10.4), "steel_rusty", seed=2), box((3.2, 31.6, 5.6), (12.8, 32.4, 10.4), "steel_rusty", seed=3)]],
                  6)


def blunt_lucerne_hammer():
    parts = [pole(0, 38, 1.6, "dark_wood", bands=(6, 20, 33), band_tex="steel_dark"), knob(0, 2.0, "steel_dark", 1)]
    parts.append([box((5.0, 33, 7.2), (8, 36, 8.8), "steel_dark", seed=1)])
    parts.append([box((3.4, 33.2, 7.0), (5.0, 35.8, 9.0), "steel", seed=2), box((3.0, 33.6, 7.4), (3.4, 35.4, 8.6), "steel", seed=3)])
    parts.append([box((8, 33.8, 7.5), (12.6, 35.2, 8.5), "steel_worn", rot=("z", 12, (8, 34.5, 8)), seed=4)])
    parts.append(leaf(36, 1.8, 1.2, 7, "steel", "blade_edge"))
    return weapon(parts, 8)


def blunt_crimson_crusher():
    parts = [grip(0, 12, 2.2, "leather_red"), knob(0, 2.8, "iron_crimson", 1), bar(12, 25, 2.2, 2.2, "iron_crimson")]
    parts.append([box((2.6, 24, 5.2), (13.4, 31, 10.8), "iron_crimson", seed=1)])
    parts.append([box((5.2, 30.2, 6.6), (7.4, 32.8, 8.6), "crystal_red", emit=3, rot=("z", 14, (6.3, 31, 7.6)), seed=2),
                  box((9.2, 30.4, 7.4), (11.0, 32.4, 9.2), "crystal_red", emit=3, rot=("z", -18, (10.1, 31, 8.3)), seed=3),
                  box((2.0, 26, 7.0), (2.6, 29, 9.0), "crystal_red", emit=2, seed=4)])
    parts.append([box((2.4, 24.6, 5.0), (13.6, 25.4, 11.0), "steel_rusty", seed=5)])
    return weapon(parts, 5)


def blunt_thunder_colossus():
    parts = [grip(0, 12, 2.3, "hide_hound"), knob(0, 3.0, "bone_dark", 1.2), bar(12, 26, 2.4, 2.4, "bone")]
    parts.append([box((2.0, 25, 4.8), (14.0, 33, 11.2), "bone_dark", seed=1)])
    for y in (26.2, 28.8, 31.4):
        parts.append([box((1.7, y, 4.5), (14.3, y + 1.0, 11.5), "copper", seed=int(y))])
    parts.append([box((1.6, 27.6, 7.6), (1.75, 30.4, 8.4), "spark_blue", emit=4, seed=2), box((14.25, 27.6, 7.6), (14.4, 30.4, 8.4), "spark_blue", emit=4, seed=3)])
    parts.append(studs(25.5, 32.5, 6, 3.6, "fang", 1.1, 3))
    return weapon(parts, 5)


def west_scrap_knife():
    return weapon([grip(0, 5, 1.6, "duct_tape"), bar(5, 5.6, 2.6, 1.4, "galv_steel"),
                   blade(5.6, 14, 2.2, 0.5, "steel_worn", "blade_edge", tip=3, double=False)], 2.5)


def west_hound_dagger():
    return weapon([grip(0, 5, 1.7, "hide_hound"), knob(0, 2.0, "bone_dark", 0.8), bar(5, 5.8, 3.4, 1.4, "bone_dark"),
                   blade(5.8, 15.5, 2.0, 0.7, "fang", "fang", tip=3.5)], 2.5)


def west_gladius():
    return weapon([knob(0.8, 2.6, "bone", 1.6), grip(0.8, 5, 1.6, "bone"), bar(5, 6.2, 3.6, 2.4, "dark_wood"),
                   blade(6.2, 21, 3.0, 0.6, "blade", "blade_edge", tip=4, fuller=0.6)], 3)


def west_arming_sword():
    return weapon([knob(0.6, 2.2, "steel_dark", 1.4), grip(0.6, 6, 1.4, "leather_brown"), cross(6, 8.0, 1.0, 1.2, "steel_dark"),
                   blade(7, 28, 2.4, 0.55, "blade", "blade_edge", tip=4.5, fuller=0.6)], 3.2)


def west_rapier():
    return weapon([knob(0.6, 1.8, "steel", 1.4), grip(0.6, 5.5, 1.2, "cord_black"),
                   [box((6.2, 5.5, 7.1), (9.8, 6.3, 8.9), "steel", seed=1), box((9.4, 1.0, 7.6), (9.9, 6.0, 8.1), "steel", seed=2),
                    box((5.6, 5.8, 7.6), (6.2, 8.0, 8.1), "steel", seed=3), box((9.8, 5.8, 7.6), (10.4, 7.6, 8.1), "steel", seed=4)],
                   blade(6.3, 30, 0.9, 0.5, "blade", "blade_edge", tip=3)], 3)


def west_viking_axe():
    return weapon([grip(0, 5, 1.6, "leather_brown"), bar(0, 22, 1.4, 1.4, "dark_wood"),
                   [box((8.6, 16.5, 7.4), (11.2, 21.5, 8.6), "steel_worn", seed=1), box((11.2, 15.0, 7.55), (13.4, 22.0, 8.45), "steel_worn", seed=2),
                    box((13.4, 14.6, 7.6), (14.0, 22.4, 8.4), "blade_edge", seed=3), box((5.8, 18.0, 7.3), (7.4, 21.0, 8.7), "steel_dark", seed=4)]], 3)


def west_claymore():
    return weapon([knob(0.6, 2.2, "steel_dark", 1.4), grip(0.6, 10, 1.5, "leather_black"), cross(10, 11.0, 1.1, 1.3, "steel_dark", droop=18),
                   blade(11.1, 38, 2.8, 0.6, "blade", "blade_edge", tip=5, fuller=0.7)], 5)


def west_executioner_axe():
    parts = [grip(0, 10, 1.8, "leather_black"), bar(0, 32, 1.6, 1.6, "dark_wood")]
    for side in (1, -1):
        x0, x1 = (8.8, 13.6) if side > 0 else (2.4, 7.2)
        e0, e1 = (13.6, 14.4) if side > 0 else (1.6, 2.4)
        parts.append([box((x0, 23.0, 7.45), (x1, 31.0, 8.55), "steel_dark", seed=1 + side), box((e0, 22.4, 7.6), (e1, 31.6, 8.4), "blade_edge", seed=3 + side)])
    parts.append([box((7.0, 25, 7.0), (9.0, 30, 9.0), "steel_dark", seed=7)])
    return weapon(parts, 5)


def west_halberd():
    parts = [pole(0, 40, 1.5, "dark_wood", bands=(14, 34), band_tex="steel"), knob(0, 1.9, "steel_dark", 1)]
    parts.append([box((8.6, 32, 7.5), (12.6, 37, 8.5), "steel_worn", seed=1), box((12.6, 31.4, 7.6), (13.3, 37.6, 8.4), "blade_edge", seed=2),
                  box((4.4, 34.0, 7.6), (7.4, 35.0, 8.4), "steel_worn", rot=("z", -20, (7.4, 34.5, 8)), seed=3)])
    parts.append(leaf(40, 1.6, 0.9, 7, "steel", "blade_edge"))
    return weapon(parts, 9)


def west_zweihander():
    parts = [knob(0.6, 2.4, "steel_dark", 1.6), grip(0.6, 12, 1.6, "cord_black"), cross(12, 13.0, 1.1, 1.4, "steel_dark", droop=10),
             bar(13.1, 17, 2.0, 0.8, "blade_dark"), [box((5.4, 16.0, 7.7), (6.6, 17.0, 8.3), "steel_dark", seed=1), box((9.4, 16.0, 7.7), (10.6, 17.0, 8.3), "steel_dark", seed=2)],
             blade(17, 42, 2.9, 0.65, "blade", "blade_edge", tip=5, fuller=0.8)]
    return weapon(parts, 6)


def west_acid_scimitar():
    return weapon([knob(0.6, 2.0, "bronze", 1.2), grip(0.6, 6, 1.5, "leather_brown"), cross(6, 5.6, 0.9, 1.3, "bronze"),
                   curved(6.9, 20, 2.6, 0.55, 24, "blade_acid", "blade_edge", segs=5)], 3)


def west_hallowed_blade():
    return weapon([knob(0.6, 2.4, "gilt", 1.6), grip(0.6, 6.5, 1.5, "cord_blue"), cross(6.5, 9.0, 1.1, 1.4, "gilt", droop=-10),
                   blade(7.6, 30, 2.6, 0.55, "blade", "blade_edge", tip=4.5, fuller=0.8),
                   [box((7.75, 9.5, 7.7), (8.25, 24, 8.3), "gilt", faces=("north", "south"), seed=9)]], 3.5)


def west_hotel_greatsword():
    return weapon([knob(0.6, 2.6, "brass", 1.6), grip(0.6, 11, 1.7, "velvet_wine"), bar(11, 12.4, 10.0, 1.6, "brass"),
                   [box((6.6, 12.4, 7.4), (9.4, 14.0, 8.6), "brass", seed=1)],
                   blade(14, 40, 3.2, 0.65, "blade_dark", "blade_edge", tip=5, fuller=0.9)], 5.5)


def west_void_greatsword():
    return weapon([knob(0.6, 2.6, "void", 1.6), grip(0.6, 11, 1.7, "leather_black"), cross(11, 11.0, 1.2, 1.6, "void", droop=-16),
                   blade(12.2, 41, 3.4, 0.7, "blade_void", "blade_dark", tip=5.5),
                   [box((7.8, 13, 7.6), (8.2, 34, 8.4), "void_crack", faces=("north", "south"), emit=4, seed=9)]], 5.5)


def cn_wood_staff():
    return weapon([pole(0, 42, 1.4, "old_wood", bands=(0, 41), band_tex="steel_worn")], 14)


def cn_iron_staff():
    return weapon([pole(0, 42, 1.4, "iron_black", bands=(0, 8, 33, 41), band_tex="brass")], 14)


def cn_peachwood_sword():
    return weapon([knob(0.4, 2.0, "peach_wood", 1.0), grip(0.4, 6, 1.4, "cord_red"), bar(6, 7, 4.0, 1.4, "peach_wood"),
                   blade(7, 26, 2.4, 0.7, "peach_wood", "peach_wood", tip=4),
                   [box((7.0, 14, 7.6), (9.0, 18.5, 7.62), "label", faces=("north",), seed=8)],
                   [box((7.5, -3.5, 7.6), (8.5, 0.4, 8.4), "tassel_red", seed=9)]], 3)


def _ring(y, s, tex):
    t = 0.5
    return [box((8 - s / 2, y, 7.6), (8 + s / 2, y + t, 8.4), tex, seed=1), box((8 - s / 2, y + s - t, 7.6), (8 + s / 2, y + s, 8.4), tex, seed=2),
            box((8 - s / 2, y, 7.6), (8 - s / 2 + t, y + s, 8.4), tex, seed=3), box((8 + s / 2 - t, y, 7.6), (8 + s / 2, y + s, 8.4), tex, seed=4)]


def cn_ring_pommel_dao():
    return weapon([_ring(-2.6, 2.8, "iron_black"), grip(0, 6, 1.4, "cord_black"), bar(6, 6.6, 2.4, 1.6, "iron_black"),
                   blade(6.6, 27, 2.2, 0.6, "blade_dark", "blade_edge", tip=3, double=False)], 3)


def cn_tang_hengdao():
    return weapon([knob(0.4, 1.8, "iron_black", 0.8), grip(0.4, 8, 1.5, "cord_black"), bar(8, 8.6, 3.2, 3.2, "brass"),
                   blade(8.6, 32, 2.2, 0.6, "blade", "blade_edge", tip=3, double=False, fuller=0.5)], 4)


def cn_han_jian():
    core = blade(7, 30, 2.2, 0.55, "blade", "blade_edge", tip=4)
    ridge = [box((7.6, 7, 7.6), (8.4, 26, 8.4), "steel", rot=("y", 45, (8, 16, 8)), seed=11)]
    return weapon([knob(0.4, 2.2, "bronze", 1.2), grip(0.4, 6, 1.4, "cord_blue"), bar(6, 7, 3.6, 1.8, "bronze"), core, ridge], 3)


def cn_yanling_dao():
    return weapon([knob(0.4, 1.8, "brass", 0.8), grip(0.4, 7, 1.5, "cord_red"), bar(7, 7.6, 3.0, 3.0, "brass"),
                   blade(7.6, 18, 2.3, 0.6, "blade", "blade_edge", tip=0.01, double=False, steps=1),
                   curved(18, 10, 2.3, 0.6, 30, "blade", "blade_edge", segs=4, tail=1.4)], 3.5)


def cn_miaodao():
    return weapon([knob(0.4, 1.8, "brass", 0.8), grip(0.4, 12, 1.5, "cord_black"), bar(12, 12.6, 3.0, 3.0, "brass"),
                   curved(12.6, 26, 2.0, 0.55, 12, "blade", "blade_edge", segs=6)], 6)


def cn_nunchaku():
    return weapon([[box((5.6, 0, 7.2), (7.2, 12, 8.8), "lacquer_black", seed=1), box((8.8, 0, 7.2), (10.4, 12, 8.8), "lacquer_black", seed=2),
                    box((5.5, 11.2, 7.1), (7.3, 12.2, 8.9), "steel", seed=3), box((8.7, 11.2, 7.1), (10.5, 12.2, 8.9), "steel", seed=4)],
                   chain(6.4, 12.2, 14.6, w=0.45), chain(9.6, 12.2, 14.6, w=0.45),
                   [box((6.2, 14.4, 7.8), (9.8, 15.0, 8.2), "chain", seed=5)]], 5)


def cn_red_tassel_spear():
    parts = [pole(0, 38, 1.4, "lacquer_red", bands=(37,), band_tex="iron_black")]
    tassel = []
    for i in range(7):
        a = i * 0.9
        x, z = 8 + math.cos(a) * 0.9, 8 + math.sin(a) * 0.9
        tassel.append(box((x - 0.35, 33.5 + (i % 3) * 0.6, z - 0.35), (x + 0.35, 38, z + 0.35), "tassel_red", seed=90 + i))
    parts.append(tassel)
    parts.append([bar(38, 39.5, 1.2, 1.2, "iron_black")])
    parts.append(leaf(39.5, 2.2, 0.8, 8, "blade", "blade_edge"))
    return weapon(parts, 10)


def cn_meteor_hammer():
    parts = [[box((7.4, 0, 7.4), (8.6, 3.0, 8.6), "leather_brown", seed=1)], chain(8, 3, 15)]
    parts.append([box((5.8, 15, 5.8), (10.2, 19.6, 10.2), "flesh_dark", seed=2), box((6.6, 14.6, 6.6), (9.4, 20.0, 9.4), "flesh", seed=3)])
    parts.append(studs(15.2, 19.4, 6, 2.2, "bone", 0.9, 4))
    return weapon(parts, 1.5)


def cn_sleeve_darts():
    return weapon([[box((6.0, 0, 6.0), (10.0, 9, 10.0), "steel_dark", seed=1), box((5.8, 1.4, 5.8), (10.2, 2.4, 10.2), "leather_brown", seed=2),
                    box((5.8, 6.4, 5.8), (10.2, 7.4, 10.2), "leather_brown", seed=3), box((6.4, 9, 6.4), (9.6, 9.6, 9.6), "brass", seed=4)],
                   [box((x - 0.2, 9.6, z - 0.2), (x + 0.2, 11.4, z + 0.2), "blade_edge", seed=5 + i) for i, (x, z) in
                    enumerate(((7.2, 7.2), (8.8, 7.2), (7.2, 8.8), (8.8, 8.8), (8, 8)))]], 4)


def cn_guandao():
    parts = [pole(0, 34, 1.6, "lacquer_red", bands=(0, 33), band_tex="bronze")]
    parts.append([bar(33, 35, 2.4, 2.4, "bronze")])
    parts.append(curved(35, 11, 3.6, 0.6, -26, "blade", "blade_edge", segs=4, tail=1.3))
    parts.append([box((4.8, 36, 7.7), (6.2, 38, 8.3), "steel_worn", seed=7)])
    return weapon(parts, 10)


def cn_fangtian_ji():
    parts = [pole(0, 36, 1.5, "lacquer_red", bands=(0, 35), band_tex="brass")]
    parts.append([bar(35, 36.6, 2.0, 2.0, "brass")])
    parts.append(leaf(36.6, 2.0, 0.7, 8, "blade", "blade_edge"))
    for side in (1, -1):
        base_x = 8 + side * 1.0
        for k in range(4):
            y = 36.2 + k * 1.3
            x0 = base_x + side * (1.6 + 0.6 * math.sin(k / 3 * math.pi))
            parts.append([box((min(base_x, x0), y, 7.7), (max(base_x, x0) + (0.6 if side > 0 else -0.0), y + 1.4, 8.3), "blade", seed=100 + k * side)])
        parts.append([box((8 + side * 3.0 - 0.3, 35.6, 7.75), (8 + side * 3.0 + 0.3, 41.4, 8.25), "blade_edge", seed=110 + side)])
    return weapon(parts, 10)


def cn_coin_sword():
    parts = [knob(0.4, 1.8, "cord_red", 0.8), grip(0.4, 6, 1.4, "cord_red"), bar(6, 7, 4.2, 1.6, "coins")]
    for k in range(8):
        y = 7 + k * 2.4
        parts.append([box((6.8, y, 7.6), (9.2, y + 2.4, 8.4), "coins", seed=120 + k)])
    parts.append([box((7.6, 26.2, 7.65), (8.4, 28.0, 8.35), "coins", seed=130), box((7.8, 7, 7.55), (8.2, 26.2, 8.45), "cord_red", seed=131)])
    parts.append([box((7.5, -3.5, 7.6), (8.5, 0.4, 8.4), "tassel_red", seed=132)])
    return weapon(parts, 3)


def cn_alpha_tangdao():
    return weapon([knob(0.4, 2.2, "iron_black", 1.0), grip(0.4, 11, 1.8, "hide_hound"),
                   [box((5.0, 11, 7.0), (11.0, 12.2, 9.0), "fang", seed=1), box((4.0, 11.2, 7.4), (5.0, 13.6, 8.6), "fang", seed=2),
                    box((11.0, 11.2, 7.4), (12.0, 13.6, 8.6), "fang", seed=3)],
                   blade(12.2, 40, 3.2, 0.7, "blade_blood", "blade_edge", tip=4, double=False, fuller=0.6)], 5.5)


WEAPONS = {name: fn for name, fn in globals().items() if name.startswith(("blunt_", "west_", "cn_")) and callable(fn)}
