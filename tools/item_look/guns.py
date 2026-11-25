"""Firearms, ammunition and attachments. A gun points along -z (forward), its top up; x is across. Sizes are for the
hand, not to the millimetre: a pistol about 13 sixteenths long, a rifle about 26, a long rifle 32. The pipe pistol is
the approved trial piece (ported.py).
"""
from lib import box, extent, hands, pose, shift, third

# ---------------------------------------------------------------- parts


def tube(z0, z1, r, tex, y=8.0, x=8.0, seed=0, rot=None):
    return box((x - r, y - r, min(z0, z1)), (x + r, y + r, max(z0, z1)), tex, seed=seed, rot=rot)


def blk(x0, y0, z0, x1, y1, z1, tex, seed=0, rot=None, emit=0, faces=None):
    kw = {"faces": faces} if faces else {}
    return box((x0, y0, z0), (x1, y1, z1), tex, seed=seed, rot=rot, emit=emit, **kw)


def grip(z, y_top, h, tex, w=1.6, d=2.2, angle=17, seed=0):
    return blk(8 - w / 2, y_top - h, z - d / 2, 8 + w / 2, y_top, z + d / 2, tex, seed, rot=("x", angle, (8, y_top, z)))


def trigger(z, y, tex="gun_black"):
    return [blk(7.8, y - 1.0, z - 0.2, 8.2, y, z + 0.2, tex, 61), blk(7.75, y - 1.4, z - 1.4, 8.25, y - 1.1, z + 1.0, tex, 62),
            blk(7.75, y - 1.4, z - 1.4, 8.25, y, z - 1.1, tex, 63)]


def mag(z, y_top, h, tex, w=1.3, d=1.6, angle=8, seed=0):
    return blk(8 - w / 2, y_top - h, z - d / 2, 8 + w / 2, y_top, z + d / 2, tex, seed, rot=("x", angle, (8, y_top, z)))


def stock(z0, z1, y_top, y_bot, tex, w=1.6, seed=0, drop=6):
    return blk(8 - w / 2, y_bot, z0, 8 + w / 2, y_top, z1, tex, seed, rot=("x", drop, (8, y_top, z0)))


def scope(z0, z1, y, r, tex="gun_black", lens="lens"):
    return [tube(z0 + 1.2, z1 - 1.2, r * 0.7, tex, y=y, seed=71), tube(z0, z0 + 1.2, r, tex, y=y, seed=72), tube(z1 - 1.2, z1, r, tex, y=y, seed=73),
            blk(8 - r * 0.6, y - r * 0.6, z0 - 0.05, 8 + r * 0.6, y + r * 0.6, z0, lens, 74, faces=("north",)),
            blk(8 - r * 0.6, y - r * 0.6, z1, 8 + r * 0.6, y + r * 0.6, z1 + 0.05, lens, 75, faces=("south",)),
            blk(7.6, y + r * 0.7, (z0 + z1) / 2 - 0.6, 8.4, y + r * 0.7 + 0.8, (z0 + z1) / 2 + 0.6, tex, 76),
            blk(8 + r * 0.7, y - 0.4, (z0 + z1) / 2 - 0.6, 8 + r * 0.7 + 0.7, y + 0.4, (z0 + z1) / 2 + 0.6, tex, 77)]


def rail(z0, z1, y, tex="rail"):
    return blk(7.4, y, z0, 8.6, y + 0.5, z1, tex, 81)


def post(z, y, h=0.8, tex="gun_black"):
    return blk(7.75, y, z - 0.3, 8.25, y + h, z + 0.3, tex, 82)


def flatten(parts):
    out = []
    for p in parts:
        if isinstance(p, dict):
            out.append(p)
        else:
            out.extend(flatten(p))
    return out


# ---------------------------------------------------------------- poses

# Held forward from outside: the model's -z to the hand's forward, its top up (worked out from the melee tries:
# [-90, 0, 0] stands a model's +y up in the hand; turning it about the forward axis keeps the gun the right way up).
THIRD_GUN = [-90, 180, 0]
THIRD_TRY = {}


def gun(name, parts):
    elements = flatten(parts)
    lo, hi = extent(elements, 2)
    length = hi - lo
    shift(elements, dz=round(8 - (lo + hi) / 2, 3))
    fp = round(0.52 * (15 / length) ** 0.45, 3)
    tp = round(0.8 * (15 / length) ** 0.5, 3)
    display = {
        "gui": pose([22, 135, 0], [0.5, 0.5, 0], round(min(0.9, 17 / length), 3)),
        "ground": pose([0, 0, 0], [0, 2, 0], round(0.5 * min(1.0, 18 / length), 3)),
        "fixed": pose([0, 90, 0], [0, 0, 0], round(min(0.85, 17 / length), 3)),
    }
    display.update(hands([0, 0, 0], [0, 2.5, -1.2], fp))
    display.update(third(THIRD_TRY.get(name, THIRD_GUN), [0, 2.0, 1.5], tp))
    return elements, display


def small(elements, gui=0.9):
    """Ammunition and attachments: held like a small block."""
    elements = flatten(elements)
    display = {"gui": pose([25, 40, 0], [0, 0, 0], gui), "ground": pose([0, 0, 0], [0, 2, 0], 0.5),
               "fixed": pose([0, 0, 0], [0, 0, 0], 0.8)}
    display.update(hands([0, 45, 0], [0, 3.5, 0], 0.5))
    display.update(third([0, 0, 0], [0, 3, 1], 0.55))
    return elements, display


# ---------------------------------------------------------------- firearms


def gun_pipe_shotgun():
    return gun("gun_pipe_shotgun", [
        tube(-10, 6, 0.95, "rust_pipe", x=7.05, seed=1), tube(-10, 6, 0.95, "galv_steel", x=8.95, seed=2),
        [blk(6.0, 6.9, z, 10.0, 9.1, z + 0.8, "bare_steel", 3 + i) for i, z in enumerate((-7, -2, 3))],
        blk(6.4, 6.6, 6, 9.6, 9.4, 9.5, "galv_steel", 6), trigger(8.4, 6.6, "bare_steel"),
        grip(10.2, 7.2, 4.6, "scrap_wood", seed=7), stock(9.5, 20, 8.6, 6.2, "scrap_wood", 1.8, 8),
        blk(7.0, 7.4, 13, 9.0, 8.4, 15, "duct_tape", 9, rot=("x", 6, (8, 8.6, 9.5))),
    ])


def gun_nailgun():
    return gun("gun_nailgun", [
        blk(6.4, 7.0, -3, 9.6, 11.0, 9, "yellow_paint", 1), blk(6.9, 7.4, -6, 9.1, 10.0, -3, "gun_black", 2),
        blk(7.4, 7.6, -7, 8.6, 9.0, -6, "bare_steel", 3), grip(7.5, 7.2, 5.4, "rubber_black", 1.8, 2.4, 12, 4),
        blk(7.5, 2.2, 6.6, 8.5, 3.0, 8.6, "brass", 5, rot=("x", 12, (8, 7.2, 7.5))),
        blk(7.3, 3.6, -5.6, 8.7, 7.0, -4.2, "gun_black", 6, rot=("x", -28, (8, 7.0, -4.9))),
        blk(7.6, 3.8, -5.4, 8.4, 6.8, -4.6, "nail_heads", 7, rot=("x", -28, (8, 7.0, -4.9))), trigger(6.2, 7.0),
    ])


def gun_taser():
    return gun("gun_taser", [
        blk(6.8, 7.4, -2, 9.2, 9.8, 7, "olive_paint", 1), blk(6.6, 7.0, -4.4, 9.4, 10.2, -2, "gun_polymer", 2),
        blk(7.0, 7.4, -4.5, 9.0, 9.8, -4.4, "yellow_paint", 3, faces=("north",)), grip(6.2, 7.4, 4.6, "gun_polymer", 1.7, 2.3, 16, 4),
        trigger(3.6, 7.4), blk(7.7, 9.8, 4.2, 8.3, 10.3, 5.0, "red_dot", 5, emit=2),
    ])


def gun_flamethrower():
    return gun("gun_flamethrower", [
        tube(-12, 4, 0.7, "steel_rusty", y=9.6, seed=1), tube(-13.4, -12, 1.0, "iron_black", y=9.6, seed=2),
        blk(7.8, 8.6, -13.6, 8.2, 9.0, -13.4, "ember", 3, emit=4),
        blk(5.6, 4.6, -2, 10.4, 8.6, 9, "tank_red", 4), blk(6.0, 4.2, -1.6, 10.0, 4.6, 8.6, "iron_black", 5),
        blk(7.2, 8.6, 1.5, 8.8, 9.6, 2.5, "brass", 6), blk(7.0, 8.4, 4, 9.0, 11.0, 6, "copper", 7),
        grip(7.2, 4.6, 4.0, "rubber_black", 1.7, 2.2, 12, 8), grip(-5, 9.0, 3.8, "rubber_black", 1.5, 1.8, -10, 9),
        blk(6.4, 8.6, 8, 9.6, 9.4, 10, "galv_steel", 10),
    ])


def gun_revolver():
    return gun("gun_revolver", [
        tube(-6, 1.6, 0.6, "gun_black", y=9.2, seed=1), blk(7.4, 9.6, -6, 8.6, 10.2, 1.6, "gun_black", 2),
        blk(6.7, 7.4, 1.6, 9.3, 10.0, 5.2, "steel_dark", 3), blk(7.2, 7.0, 5.2, 8.8, 10.2, 7.2, "gun_black", 4),
        blk(7.6, 10.2, 5.6, 8.4, 11.0, 6.8, "gun_black", 5, rot=("x", -20, (8, 10.2, 6.2))),
        grip(7.6, 7.4, 5.2, "gun_wood", 1.7, 2.6, 22, 6), trigger(4.6, 7.4), post(-5.6, 9.8, 0.6),
    ])


def gun_pistol_9mm():
    return gun("gun_pistol_9mm", [
        blk(7.1, 8.6, -4, 8.9, 10.4, 7.4, "gun_black", 1), blk(7.2, 7.4, -3.4, 8.8, 8.6, 6.6, "gun_polymer", 2),
        grip(5.6, 7.6, 5.4, "gun_polymer", 1.7, 2.6, 16, 3), mag(5.4, 2.6, 0.8, "gun_black", 1.8, 2.8, 16, 4),
        trigger(2.2, 7.4, "gun_polymer"), post(-3.4, 10.4, 0.5), post(6.8, 10.4, 0.5), rail(-3.2, 0.4, 7.0, "rail"),
    ])


def gun_heavy_handgun():
    return gun("gun_heavy_handgun", [
        blk(6.8, 8.4, -6, 9.2, 10.8, 8, "steel_worn", 1), blk(7.0, 7.2, -5, 9.0, 8.4, 7, "gun_black", 2),
        [blk(6.75, 9.4, z, 9.25, 10.4, z + 0.6, "gun_black", 3 + i) for i, z in enumerate((-5.4, -4.2))],
        grip(6.2, 7.4, 6.0, "gun_wood", 2.0, 3.0, 16, 5), trigger(2.6, 7.2), post(-5.6, 10.8, 0.6), post(7.4, 10.8, 0.6),
    ])


def gun_double_barrel():
    return gun("gun_double_barrel", [
        tube(-16, 3, 0.85, "steel_dark", x=7.15, y=9.0, seed=1), tube(-16, 3, 0.85, "steel_dark", x=8.85, y=9.0, seed=2),
        blk(6.6, 7.2, -10, 9.4, 8.4, -2, "gun_wood", 3), blk(6.6, 7.0, 3, 9.4, 10.0, 7, "steel_worn", 4),
        trigger(5.6, 7.0), stock(7, 20, 9.4, 6.6, "gun_wood", 1.8, 5, 8), grip(8.4, 7.4, 2.6, "gun_wood", 1.6, 2.4, 30, 6),
        blk(7.85, 9.85, -15.8, 8.15, 10.15, -15.4, "brass", 7),
    ])


def gun_pump_shotgun():
    return gun("gun_pump_shotgun", [
        tube(-14, 3, 0.75, "gun_black", y=9.4, seed=1), tube(-12, 2, 0.6, "steel_dark", y=7.9, seed=2),
        blk(6.9, 7.2, -9, 9.1, 8.8, -3.5, "gun_polymer", 3), blk(6.8, 7.0, 2, 9.2, 10.4, 9, "gun_black", 4),
        trigger(7.4, 7.0), stock(9, 21, 10.0, 6.6, "gun_polymer", 1.8, 5, 6), grip(9.6, 7.4, 3.2, "gun_polymer", 1.6, 2.4, 22, 6),
        post(-13.6, 10.1, 0.4), rail(3, 8, 10.4),
    ])


def gun_auto_shotgun():
    return gun("gun_auto_shotgun", [
        tube(-13, 2, 0.8, "blade_acid", y=9.4, seed=1), blk(6.8, 7.6, -9, 9.2, 9.0, -2, "gun_polymer", 2),
        blk(6.6, 7.0, 2, 9.4, 10.6, 10, "gun_black", 3), blk(6.0, 3.0, 2.6, 10.0, 7.0, 6.6, "olive_paint", 4),
        trigger(7.6, 7.0), stock(10, 19, 10.2, 6.8, "gun_polymer", 1.8, 5, 4), grip(9.0, 7.2, 3.6, "gun_polymer", 1.6, 2.4, 18, 6),
        rail(2.4, 9.6, 10.6), tube(-14, -13, 1.0, "steel_dark", y=9.4, seed=7),
    ])


def gun_scrap_smg():
    return gun("gun_scrap_smg", [
        tube(-8, 8, 1.1, "galv_steel", seed=1), tube(-11, -8, 0.5, "rust_pipe", seed=2),
        blk(9.1, 6.6, -2.6, 12.6, 8.0, -1.2, "steel_rusty", 3), grip(4.4, 7.0, 4.2, "duct_tape", 1.7, 2.4, 14, 4),
        trigger(1.8, 7.0, "bare_steel"), blk(7.7, 6.6, 8, 8.3, 7.2, 16, "bare_steel", 5), blk(7.7, 6.6, 15.4, 8.3, 10.0, 16, "bare_steel", 6),
        blk(7.7, 9.4, 8, 8.3, 10.0, 16, "bare_steel", 7), post(-7.6, 9.1, 0.6, "bare_steel"),
    ])


def gun_tactical_smg():
    return gun("gun_tactical_smg", [
        blk(7.0, 7.4, -3, 9.0, 10.0, 7, "gun_black", 1), tube(-12, -3, 0.9, "gun_polymer", y=8.6, seed=2),
        mag(1.0, 7.4, 5.0, "gun_black", 1.3, 1.6, 4, 3), grip(5.6, 7.4, 3.6, "gun_polymer", 1.6, 2.2, 18, 4), trigger(3.2, 7.4),
        grip(-5, 7.6, 2.6, "gun_polymer", 1.2, 1.2, -6, 5), rail(-3, 6, 10.0), stock(7, 14, 9.4, 8.6, "steel_dark", 1.4, 6, 0),
        blk(7.0, 7.2, 13.4, 9.0, 9.6, 14.2, "rubber_black", 7),
    ])


def gun_assault_rifle():
    return gun("gun_assault_rifle", [
        tube(-14, -7, 0.4, "gun_black", y=8.8, seed=1), tube(-15.4, -14, 0.55, "steel_dark", y=8.8, seed=2),
        blk(6.9, 7.6, -7, 9.1, 10.0, 0, "vented", 3), blk(7.0, 7.0, 0, 9.0, 10.2, 7, "gun_black", 4), rail(-6.8, 7, 10.2),
        mag(2.0, 7.0, 3.2, "gun_black", 1.3, 1.8, 6, 5), mag(1.7, 3.9, 2.4, "gun_black", 1.3, 1.8, 20, 6),
        grip(5.8, 7.2, 3.8, "gun_polymer", 1.6, 2.2, 20, 7), trigger(3.8, 7.0), stock(7, 15, 9.6, 6.8, "gun_polymer", 1.8, 8, 4),
        blk(7.2, 6.6, 14.4, 8.8, 9.8, 15.2, "rubber_black", 9), post(-6.6, 10.2, 1.2),
        blk(7.4, 10.7, 2.4, 8.6, 11.9, 4.6, "gun_black", 10), blk(7.6, 11.0, 2.35, 8.4, 11.6, 2.4, "red_dot", 11, emit=2, faces=("north",)),
    ])


def gun_bolt_sniper():
    return gun("gun_bolt_sniper", [
        tube(-20, 2, 0.5, "steel_dark", y=9.0, seed=1), blk(6.9, 6.8, -10, 9.1, 9.0, 3, "gun_wood", 2),
        blk(7.0, 8.4, 2, 9.0, 10.0, 8, "steel_worn", 3), blk(9.0, 9.2, 6.2, 10.6, 9.6, 6.8, "steel_worn", 4),
        blk(10.4, 8.8, 6.0, 11.2, 9.6, 7.0, "steel_dark", 5), trigger(6.4, 8.4), grip(8.8, 8.0, 3.0, "gun_wood", 1.6, 2.4, 26, 6),
        stock(8, 20, 9.6, 6.6, "gun_wood", 1.9, 7, 7), scope(-1, 8, 11.6, 0.95),
        blk(7.6, 7.4, -9, 8.4, 7.8, -2, "gun_black", 8),
    ])


def gun_antimateriel_rifle():
    return gun("gun_antimateriel_rifle", [
        tube(-21, 0, 0.75, "gun_black", y=9.0, seed=1), blk(6.6, 8.0, -23, 9.4, 10.0, -21, "steel_dark", 2),
        [blk(6.4, 8.6, z, 9.6, 9.4, z + 0.4, "gun_black", 3 + i) for i, z in enumerate((-22.6, -21.8))],
        blk(6.6, 7.0, -2, 9.4, 10.4, 9, "olive_paint", 5), mag(4.0, 7.0, 3.8, "gun_black", 1.8, 2.6, 4, 6),
        grip(7.6, 7.0, 3.6, "gun_polymer", 1.7, 2.4, 22, 7), trigger(6.0, 7.0), scope(-1, 8, 12.2, 1.1),
        blk(7.6, 7.0, 9, 8.4, 10.0, 18, "steel_dark", 8), blk(7.0, 6.2, 17, 9.0, 10.4, 18.4, "rubber_black", 9),
        blk(6.6, 6.4, -16, 7.0, 8.8, -15.4, "steel_dark", 10, rot=("x", 30, (6.8, 8.8, -15.7))),
        blk(9.0, 6.4, -16, 9.4, 8.8, -15.4, "steel_dark", 11, rot=("x", 30, (9.2, 8.8, -15.7))),
    ])


def gun_railgun():
    parts = [blk(7.0, 9.2, -16, 9.0, 9.7, 2, "gun_black", 1), blk(7.0, 7.3, -16, 9.0, 7.8, 2, "gun_black", 2),
             blk(7.7, 7.8, -15.6, 8.3, 9.2, 2, "spark_blue", 3, emit=3)]
    for i, z in enumerate(range(-14, 2, 3)):
        parts.append(blk(6.6, 6.8, z, 9.4, 10.2, z + 1.2, "copper_winding", 4 + i))
    parts += [blk(6.4, 6.4, 2, 9.6, 10.6, 9, "gun_black", 10), blk(6.0, 4.2, 3.0, 10.0, 6.4, 7.6, "gun_polymer", 11),
              blk(6.1, 4.6, 2.9, 9.9, 5.0, 3.0, "spark_blue", 12, emit=2, faces=("north",)), grip(8.4, 6.6, 3.4, "rubber_black", 1.6, 2.4, 18, 13),
              trigger(6.8, 6.4), stock(9, 17, 9.8, 7.0, "gun_black", 1.6, 14, 5), blk(9.6, 6.0, 6.0, 10.2, 6.4, 13, "wire_black", 15)]
    return gun("gun_railgun", parts)


def gun_sonic_pulse():
    parts = []
    for i, (z0, r) in enumerate(((-12, 2.4), (-10.6, 1.9), (-9.2, 1.4), (-7.8, 1.0))):
        parts.append(tube(z0, z0 + 1.4, r, "brass" if i == 0 else "steel_dark", y=9.0, seed=1 + i))
    parts += [blk(6.6, 7.0, -6.4, 9.4, 10.8, 6, "olive_paint", 6), tube(-1, 4, 0.9, "glass_milk", y=11.8, seed=7),
              blk(7.0, 10.8, -1.4, 9.0, 11.0, 4.4, "brass", 8), grip(3.4, 7.2, 4.4, "rubber_black", 1.7, 2.4, 16, 9), trigger(1.2, 7.0),
              blk(7.6, 8.6, -12.15, 8.4, 9.4, -12.05, "spark_blue", 10, emit=2, faces=("north",))]
    return gun("gun_sonic_pulse", parts)


def office_crossbow():
    parts = [blk(7.2, 7.4, -6, 8.8, 8.6, 9, "ruler", 1), blk(7.0, 6.0, 7, 9.0, 8.6, 11, "pen_white", 2),
             grip(5.6, 7.4, 3.4, "gun_polymer", 1.4, 2.0, 20, 3), trigger(3.8, 7.4, "steel")]
    for side in (1, -1):
        parts.append(blk(8 if side > 0 else 1.4, 8.0, -6, 14.6 if side > 0 else 8, 8.6, -5.0, "ruler", 4,
                         rot=("y", -14 * side, (8, 8.3, -5.5))))
    parts += [blk(1.8, 8.15, -3.6, 14.2, 8.3, -3.4, "twine", 5), tube(-9, 3, 0.25, "pen_blue", y=8.9, seed=6),
              blk(7.6, 8.65, -9.6, 8.4, 9.15, -9, "steel", 7), blk(7.0, 8.6, -6.8, 9.0, 9.4, -6.0, "gun_black", 8)]
    return gun("office_crossbow", parts)


# ---------------------------------------------------------------- ammunition and attachments


def _rounds(xs, z, y0, h, case, tip, r=0.45, tip_h=0.9):
    out = []
    for i, x in enumerate(xs):
        out.append(blk(x - r, y0, z - r, x + r, y0 + h, z + r, case, 20 + i))
        out.append(blk(x - r * 0.75, y0 + h, z - r * 0.75, x + r * 0.75, y0 + h + tip_h, z + r * 0.75, tip, 40 + i))
    return out


def ammo_9mm():
    parts = [blk(4.5, 1, 5, 11.5, 4.5, 11, "cardboard", 1), blk(4.5, 4.5, 10.4, 11.5, 8.5, 11, "cardboard", 2, rot=("x", -20, (8, 4.5, 11)))]
    parts += _rounds((5.6, 7.0, 8.4, 9.8), 7.0, 4.5, 1.4, "brass_case", "copper_tip", 0.5, 0.8)
    parts += _rounds((6.3, 7.7, 9.1), 8.6, 4.5, 1.4, "brass_case", "copper_tip", 0.5, 0.8)
    return small(parts)


def _shells(tex):
    parts = []
    for i, (x, z) in enumerate(((6.2, 6.4), (9.0, 6.6), (7.6, 9.2), (10.0, 9.4))):
        parts.append(blk(x - 1, 1, z - 1, x + 1, 6.5, z + 1, tex, 1 + i))
        parts.append(blk(x - 1.05, 1, z - 1.05, x + 1.05, 2.4, z + 1.05, "brass_case", 10 + i))
    return small(parts)


def ammo_12g_buck():
    return _shells("shell_red")


def ammo_12g_acid():
    return _shells("shell_green")


def _clip(tip):
    parts = [blk(4.2, 1, 7.4, 11.8, 1.6, 8.6, "steel_dark", 1)]
    parts += _rounds((5.0, 6.2, 7.4, 8.6, 9.8, 11.0), 8.0, 1.6, 6.0, "brass_case", tip, 0.5, 2.0)
    return small(parts)


def ammo_556():
    return _clip("copper_tip")


def ammo_556_phosphor():
    return _clip("tip_phosphor")


def _long_rounds(tip):
    parts = _rounds((6.0, 8.0, 10.0), 8.0, 0.5, 9.0, "brass_case", "copper_tip", 0.7, 2.4)
    parts += [blk(x - 0.4, 11.7, 7.6, x + 0.4, 12.3, 8.4, tip, 60 + i) for i, x in enumerate((6.0, 8.0, 10.0))]
    return small(parts, 0.8)


def ammo_308():
    return _long_rounds("copper_tip")


def ammo_308_ap():
    return _long_rounds("gun_black")


def ammo_scrap_slug():
    parts = [blk(4.6, 0.5, 4.6, 11.4, 3.0, 11.4, "rag_grey", 1)]
    for i, (x, z, s) in enumerate(((6.2, 6.4, 1.4), (8.6, 6.0, 1.2), (7.4, 8.4, 1.5), (9.6, 8.8, 1.2), (6.0, 9.6, 1.1))):
        parts.append(blk(x - s / 2, 3.0, z - s / 2, x + s / 2, 3.0 + s, z + s / 2, "lead", 10 + i))
    return small(parts)


def ammo_steel_nails():
    parts = [blk(3.0, 4, 7.6, 13.0, 4.6, 8.4, "cardboard", 1)]
    for i in range(12):
        x = 3.6 + i * 0.8
        parts.append(blk(x - 0.15, 4.6, 7.85, x + 0.15, 10, 8.15, "steel", 10 + i))
        parts.append(blk(x - 0.35, 10, 7.65, x + 0.35, 10.3, 8.35, "nail_heads", 30 + i))
    return small(parts, 0.85)


def ammo_fuel_canister():
    return small([blk(4.6, 0.5, 5.4, 11.4, 9.5, 10.6, "tank_red", 1), blk(5.4, 9.5, 6.2, 10.6, 10.2, 9.8, "tank_red", 2),
                  blk(9.0, 10.2, 7.2, 10.4, 12.0, 8.8, "brass", 3), blk(5.6, 9.5, 7.6, 8.4, 10.8, 8.4, "iron_black", 4),
                  blk(4.5, 3.0, 5.3, 11.5, 3.6, 10.7, "iron_black", 5)], 0.85)


def ammo_rail_slug():
    return small([blk(7.4, 1, 7.4, 8.6, 13, 8.6, "steel_dark", 1), blk(7.6, 13, 7.6, 8.4, 14.6, 8.4, "blade_edge", 2),
                  [blk(6.0, 1.0 + i * 0.01, 7.85, 10.0, 3.2, 8.15, "gun_black", 3 + i, rot=("y", a, (8, 2, 8))) for i, a in enumerate((0, 45, -45))],
                  blk(7.3, 6.0, 7.3, 8.7, 6.6, 8.7, "copper", 7)], 0.85)


def ammo_pen_bolt():
    parts = []
    for i, (x, z) in enumerate(((6.8, 7.0), (8.0, 7.6), (9.2, 7.0), (7.4, 8.6), (8.6, 8.6))):
        parts.append(blk(x - 0.4, 2, z - 0.4, x + 0.4, 12, z + 0.4, "pen_blue" if i % 2 == 0 else "pen_white", 1 + i))
        parts.append(blk(x - 0.15, 12, z - 0.15, x + 0.15, 13.2, z + 0.15, "steel", 10 + i))
        parts.append(blk(x - 0.9, 2.2, z - 0.05, x + 0.9, 4.2, z + 0.05, "label", 20 + i))
    parts.append(blk(6.2, 6.4, 6.4, 9.8, 7.2, 9.2, "rubber_black", 30))
    return small(parts, 0.85)


def mod_silencer():
    return small([blk(6.4, 1, 6.4, 9.6, 13, 9.6, "gun_black", 1), blk(6.2, 1, 6.2, 9.8, 2.2, 9.8, "steel_dark", 2),
                  blk(6.2, 11.8, 6.2, 9.8, 13, 9.8, "steel_dark", 3), blk(7.4, 13, 7.4, 8.6, 13.2, 8.6, "iron_black", 4)], 0.85)


def mod_red_dot():
    return small([blk(5.0, 2, 6.0, 11.0, 3.2, 10.0, "rail", 1), blk(5.6, 3.2, 6.4, 10.4, 7.6, 9.6, "gun_black", 2),
                  blk(6.4, 4.0, 6.3, 9.6, 7.0, 6.4, "lens", 3, faces=("north",)), blk(7.8, 5.3, 6.25, 8.2, 5.7, 6.3, "red_dot", 4, emit=3, faces=("north",)),
                  blk(10.4, 4.8, 7.4, 11.0, 5.8, 8.6, "steel_dark", 5)])


def mod_sniper_scope():
    return small(scope(1, 15, 8.0, 1.6), 0.8)


def mod_weapon_light():
    return small([blk(6.6, 1, 6.6, 9.4, 11, 9.4, "gun_black", 1), blk(6.2, 11, 6.2, 9.8, 13, 9.8, "steel_dark", 2),
                  blk(6.6, 13, 6.6, 9.4, 13.1, 9.4, "fluoro_tube", 3, emit=4, faces=("up",)), blk(9.4, 4, 7.6, 10.2, 5.2, 8.4, "rubber_black", 4)])


def mod_ext_mag():
    return small([blk(5.2, 1, 6.8, 7.6, 12, 9.2, "gun_black", 1, rot=("z", -4, (6.4, 1, 8))),
                  blk(8.4, 1, 6.8, 10.8, 12, 9.2, "gun_black", 2, rot=("z", 4, (9.6, 1, 8))),
                  blk(5.0, 5.0, 6.6, 11.0, 6.6, 9.4, "duct_tape", 3), blk(5.0, 8.0, 6.6, 11.0, 9.2, 9.4, "duct_tape", 4),
                  blk(5.8, 12, 7.4, 7.0, 12.6, 8.6, "brass_case", 5), blk(9.0, 12, 7.4, 10.2, 12.6, 8.6, "brass_case", 6)])


def mod_bayonet():
    from melee import blade
    return small([blk(6.6, 1, 7.2, 9.4, 4.4, 8.8, "leather_black", 1), blk(5.4, 4.4, 7.0, 10.6, 5.2, 9.0, "steel_dark", 2),
                  blk(4.8, 4.0, 7.3, 5.8, 6.0, 8.7, "steel_dark", 3), blade(5.2, 14.5, 1.8, 0.5, "blade", "blade_edge", tip=3, double=False)], 0.85)


ITEMS = {name: fn for name, fn in globals().items()
         if callable(fn) and name.startswith(("gun_", "ammo_", "mod_", "office_crossbow"))}
