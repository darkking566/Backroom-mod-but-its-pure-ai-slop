"""Magic and anomalous items: things found in the Backrooms and gone strange. Staffs of insulated poles, copper pipe,
ceramic insulators and rusted fittings; books that were left in the damp; a hotel desk bell, a hand bell, a pocket
watch, a censer. A little light where something is wrong, and dim. The noclip wand is the approved trial piece.
Staffs stand like the melee weapons (melee.weapon); the small things are held like ammunition (guns.small).
"""
import math

from guns import blk, small
from lib import bar, box
from melee import chain, curved, grip, knob, pole, studs, weapon


def static_pulse_staff():
    parts = [pole(0, 30, 1.5, "rubber_black", bands=(6, 14, 22), band_tex="yellow_paint"), knob(0, 2.0, "gun_black", 1)]
    parts.append([bar(30, 31.5, 2.6, 2.6, "ceramic"), bar(31.5, 33, 2.0, 2.0, "ceramic"), bar(33, 34.5, 2.6, 2.6, "ceramic")])
    for i, y in enumerate((34.8, 35.6, 36.4)):
        parts.append([blk(6.2, y, 6.2, 9.8, y + 0.5, 9.8, "copper_winding", 10 + i)])
    parts.append([blk(7.6, 37, 7.6, 8.4, 39.5, 8.4, "copper", 20), blk(7.8, 39.5, 7.8, 8.2, 40.3, 8.2, "spark_blue", 21, emit=4)])
    return weapon(parts, 8)


def magic_prism_light_staff():
    parts = [pole(0, 30, 1.3, "lamp_metal", bands=(10, 20), band_tex="rubber_black"), knob(0, 1.8, "rubber_black", 1)]
    parts.append([blk(6.0, 30, 7.2, 10.0, 31, 8.8, "steel", 1), blk(6.0, 31, 7.2, 6.8, 36, 8.8, "steel", 2), blk(9.2, 31, 7.2, 10.0, 36, 8.8, "steel", 3)])
    parts.append([blk(7.0, 31.4, 7.0, 9.0, 35.6, 9.0, "glass_clear", 4, rot=("y", 45, (8, 33.5, 8))),
                  blk(7.6, 32.4, 7.6, 8.4, 34.6, 8.4, "fluoro_tube", 5, emit=4)])
    return weapon(parts, 8)


def magic_steam_flame_staff():
    parts = [pole(0, 28, 1.5, "copper", bands=(4, 16, 27), band_tex="brass"), knob(0, 2.0, "brass", 1)]
    parts.append([blk(8.75, 18, 7.0, 9.2, 20.4, 9.0, "steel_dark", 1), blk(9.2, 18.4, 6.6, 11.2, 20.0, 9.4, "brass", 2),
                  blk(11.2, 18.6, 6.8, 11.3, 19.8, 9.2, "dial", 3, faces=("east",))])
    parts.append([blk(5.0, 23, 7.6, 11.0, 23.6, 8.4, "tank_red", 4), blk(7.6, 23, 5.0, 8.4, 23.6, 11.0, "tank_red", 5)])
    parts.append([bar(28, 30, 2.4, 2.4, "iron_black"), bar(30, 31, 1.6, 1.6, "iron_black"), blk(7.5, 31, 7.5, 8.5, 32, 8.5, "ember", 6, emit=4)])
    return weapon(parts, 7)


def magic_arc_lightning_staff():
    parts = [pole(0, 30, 1.4, "dark_wood", bands=(0, 8), band_tex="steel_dark")]
    for i, y in enumerate((14, 18, 22, 26)):
        parts.append([blk(6.2, y, 6.2, 9.8, y + 1.0, 9.8, "ceramic", 1 + i), blk(6.8, y + 1.0, 6.8, 9.2, y + 1.6, 9.2, "ceramic", 10 + i)])
    parts.append([bar(30, 31.5, 2.2, 2.2, "steel_dark")])
    for side in (-1, 1):
        parts.append([blk(8 + side * 1.6 - 0.3, 31.5, 7.7, 8 + side * 1.6 + 0.3, 36, 8.3, "copper", 20 + side,
                          rot=("z", -12 * side, (8 + side * 1.6, 31.5, 8)))])
    parts.append([blk(7.8, 34.5, 7.8, 8.2, 35.5, 8.2, "spark_blue", 30, emit=4)])
    return weapon(parts, 7)


def magic_abyssal_frost_scepter():
    parts = [grip(0, 7, 1.5, "leather_black"), knob(0, 2.2, "bronze", 1), bar(7, 18, 1.3, 1.3, "bronze")]
    parts.append([bar(18, 19, 3.0, 3.0, "bronze")])
    for i, (x, z, h, a) in enumerate(((7.2, 7.4, 6, 10), (8.8, 8.2, 5, -14), (7.8, 9.0, 4, 6), (8.4, 6.8, 4.5, -8))):
        parts.append([blk(x - 0.7, 19, z - 0.7, x + 0.7, 19 + h, z + 0.7, "frost", 10 + i, rot=("z", a, (x, 19, z)), emit=2)])
    return weapon(parts, 3.5)


def magic_plague_censer_wand():
    parts = [grip(0, 6, 1.4, "rag_grey"), bar(6, 16, 1.2, 1.2, "dark_wood"), bar(16, 16.8, 2.6, 2.6, "iron_black")]
    for x, z in ((6.9, 6.9), (9.1, 6.9), (6.9, 9.1), (9.1, 9.1)):
        parts.append([blk(x - 0.25, 16.8, z - 0.25, x + 0.25, 21.4, z + 0.25, "iron_black", int(x * z))])
    parts.append([blk(7.0, 17.4, 7.0, 9.0, 20.6, 9.0, "glass_green", 40), bar(21.4, 22.2, 2.8, 2.8, "iron_black"), bar(22.2, 23.2, 1.0, 1.0, "iron_black")])
    return weapon(parts, 3)


def magic_clump_grapple():
    parts = [grip(0, 6, 1.6, "leather_brown"), knob(0, 2.0, "iron_black", 1), bar(6, 7, 2.4, 2.4, "iron_black"), chain(8, 7, 13)]
    parts.append([blk(6.4, 13, 6.4, 9.6, 15.4, 9.6, "flesh", 1)])
    for i, a in enumerate((0, 72, 144, 216, 288)):
        r = math.radians(a)
        x, z = 8 + math.cos(r) * 1.5, 8 + math.sin(r) * 1.5
        parts.append([blk(x - 0.4, 15.4, z - 0.4, x + 0.4, 18.4, z + 0.4, "bone", 10 + i, rot=("z", 12 if x > 8 else -12, (x, 15.4, z))),
                      blk(x - 0.3, 18.4, z - 0.3, x + 0.3, 19.6, z + 0.3, "fang", 20 + i, rot=("z", 28 if x > 8 else -28, (x, 18.4, z)))])
    return weapon(parts, 3)


def magic_crimson_scythe():
    parts = [pole(0, 40, 1.5, "dark_wood", bands=(14, 39), band_tex="iron_crimson"), knob(0, 2.0, "iron_crimson", 1)]
    parts.append([blk(7.0, 22, 7.0, 13.0, 23.2, 9.0, "dark_wood", 1)])
    parts.append([bar(39, 41, 2.4, 2.4, "iron_crimson")])
    x, y = 8.0, 40.0
    for i, (length, ang) in enumerate(((5, -4), (5, -12), (4.5, -22), (4, -34))):
        parts.append([blk(x, y - 1.6, 7.6, x + length + 0.3, y, 8.4, "iron_crimson", 10 + i, rot=("z", ang, (x, y, 8))),
                      blk(x, y - 2.0, 7.75, x + length + 0.3, y - 1.6, 8.25, "blade_blood", 20 + i, rot=("z", ang, (x, y, 8)))])
        r = math.radians(ang)
        x += length * math.cos(r)
        y += length * math.sin(r)
    return weapon(parts, 10)


def magic_fate_dice_staff():
    parts = [pole(0, 30, 1.4, "lacquer_black", bands=(10, 20, 29), band_tex="gilt"), knob(0, 2.0, "gilt", 1)]
    for side in (-1, 1):
        parts.append([blk(8 + side * 1.9 - 0.3, 30, 7.7, 8 + side * 1.9 + 0.3, 34.6, 8.3, "gilt", 10 + side, rot=("z", 8 * side, (8, 30, 8)))])
    parts.append([blk(6.2, 30.8, 6.2, 9.8, 34.4, 9.8, "die", 20, rot=("y", 20, (8, 32.6, 8)))])
    return weapon(parts, 8)


def magic_void_rift_staff():
    parts = [pole(0, 36, 1.6, "void", bands=(6, 18, 30), band_tex="iron_black"), knob(0, 2.2, "iron_black", 1)]
    parts.append([blk(7.85, 2, 7.1, 8.15, 34, 7.2, "void_crack", 1, emit=3, faces=("north",))])
    for i, (x, z, h, a) in enumerate(((6.8, 7.6, 7, 16), (9.2, 8.4, 8, -18), (8.0, 9.2, 6, 4), (7.6, 6.8, 5, -6))):
        parts.append([blk(x - 0.8, 36, z - 0.8, x + 0.8, 36 + h, z + 0.8, "void", 10 + i, rot=("z", a, (x, 36, z)))])
    parts.append([blk(7.6, 37, 7.6, 8.4, 41, 8.4, "void_crack", 20, emit=4)])
    return weapon(parts, 9)


# ---------------------------------------------------------------- held like small things


def held(elements, gui=0.85):
    """Small magic things: Gemini's magic animation brings them close to the eye, so they are held smaller."""
    from lib import hands
    elements, display = small(elements, gui)
    display.update(hands([0, 30, 0], [1.5, 1.0, -1.5], 0.34))
    return elements, display


def _book(cover, clasp="brass", extra=()):
    parts = [blk(4.0, 1, 5.0, 12.0, 11, 5.8, cover, 1), blk(4.0, 1, 10.2, 12.0, 11, 11.0, cover, 2), blk(4.0, 1, 5.8, 4.8, 11, 10.2, cover, 3),
             blk(4.8, 1.3, 5.8, 11.7, 10.7, 10.2, "pages", 4), blk(11.6, 5.4, 4.8, 12.4, 6.6, 11.2, clasp, 5),
             blk(3.8, 0.8, 4.8, 5.0, 2.0, 11.2, clasp, 6), blk(3.8, 10.0, 4.8, 5.0, 11.2, 11.2, clasp, 7)]
    return held(parts + list(extra), 0.85)


def magic_acid_grimoire():
    return _book("book_moth", "bronze", [blk(6, 4, 4.9, 10, 8, 5.0, "glass_green", 9, faces=("north",))])


def magic_party_grimoire():
    return _book("book_party", "steel_worn", [blk(6.4, 4.4, 4.9, 9.6, 7.6, 5.0, "label", 9, faces=("north",))])


def magic_astral_codex():
    return _book("book_astral", "brass", [blk(7.4, 5.4, 4.9, 8.6, 6.6, 5.0, "crystal_amber", 9, emit=2, faces=("north",))])


def magic_phosphor_talisman():
    parts = []
    for i, (x, a) in enumerate(((6.0, -10), (8.0, 0), (10.0, 10))):
        parts.append(blk(x - 1.4, 1, 7.9 - i * 0.1, x + 1.4, 13, 8.0 - i * 0.1, "talisman", 1 + i, rot=("z", a, (x, 1, 8))))
    parts.append(blk(5.6, 2, 7.6, 10.4, 3, 8.4, "twine", 9))
    parts.append(blk(7.4, 11.6, 7.7, 8.6, 12.4, 7.8, "tip_phosphor", 10, emit=2, faces=("north",)))
    return held(parts, 0.85)


def magic_almond_censer():
    parts = [blk(5.2, 1.0, 5.2, 10.8, 5.0, 10.8, "brass", 1), blk(5.8, 5.0, 5.8, 10.2, 6.8, 10.2, "brass", 2),
             blk(7.2, 6.8, 7.2, 8.8, 7.6, 8.8, "brass", 3), blk(6.0, 4.0, 4.9, 10.0, 4.6, 5.2, "almond_ember", 4, emit=3)]
    parts += chain(6.0, 6.8, 13.6, w=0.35) + chain(10.0, 6.8, 13.6, w=0.35)
    parts.append(blk(5.6, 13.6, 7.6, 10.4, 14.2, 8.4, "brass", 5))
    return held(parts, 0.85)


def magic_screech_bell():
    parts = [blk(7.2, 9, 7.2, 8.8, 15, 8.8, "dark_wood", 1), blk(7.0, 8.4, 7.0, 9.0, 9.0, 9.0, "brass", 2)]
    for i, (y0, y1, w) in enumerate(((7.0, 8.4, 3.0), (5.0, 7.0, 4.2), (3.0, 5.0, 5.4), (1.4, 3.0, 6.6))):
        parts.append(blk(8 - w / 2, y0, 8 - w / 2, 8 + w / 2, y1, 8 + w / 2, "brass", 10 + i))
    parts.append(blk(7.6, 0.6, 7.6, 8.4, 1.4, 8.4, "iron_black", 20))
    return held(parts, 0.85)


def magic_beast_summon_bell():
    parts = [blk(3.8, 0.5, 3.8, 12.2, 1.6, 12.2, "dark_wood", 1), blk(4.4, 1.6, 4.4, 11.6, 2.0, 11.6, "brass", 2)]
    for i, (y0, y1, w) in enumerate(((2.0, 3.6, 6.4), (3.6, 4.8, 5.4), (4.8, 5.8, 4.0), (5.8, 6.4, 2.4))):
        parts.append(blk(8 - w / 2, y0, 8 - w / 2, 8 + w / 2, y1, 8 + w / 2, "brass", 10 + i))
    parts.append(blk(7.5, 6.4, 7.5, 8.5, 7.4, 8.5, "steel", 20))
    return held(parts, 0.9)


def magic_decoy_mirror():
    parts = [blk(4.0, 4, 7.4, 12.0, 14, 8.6, "dark_wood", 1), blk(5.0, 5, 7.3, 11.0, 13, 7.4, "lens", 2, faces=("north",)),
             blk(7.8, 5, 7.25, 8.2, 13, 7.3, "steel", 3, faces=("north",)), blk(5.0, 8.8, 7.25, 11.0, 9.2, 7.3, "steel", 4, faces=("north",)),
             blk(7.2, 0.5, 7.4, 8.8, 4, 8.6, "dark_wood", 5)]
    return held(parts, 0.85)


def magic_stopmotion_watch():
    parts = [blk(4.6, 3, 7.0, 11.4, 9.8, 9.0, "brass", 1), blk(5.2, 3.6, 6.9, 10.8, 9.2, 7.0, "dial", 2, faces=("north",)),
             blk(7.4, 9.8, 7.4, 8.6, 10.8, 8.6, "brass", 3), blk(7.0, 10.8, 7.6, 9.0, 11.6, 8.4, "brass", 4)]
    parts += chain(8, 11.6, 15.4, w=0.3)
    return held(parts, 0.9)


ITEMS = {name: fn for name, fn in globals().items() if callable(fn) and (name.startswith("magic_") or name == "static_pulse_staff")}
