"""The trial pieces (tools/make_trial_items.py, compared side by side and approved on 2026-11-25), kept as they were
until their categories are redone: the crowbar, the noclip wand, the pipe pistol, the copper wire, the almond elixir,
and two props."""
from lib import box, gemini_display, hands


def _display(item, extra):
    display = dict(gemini_display(f"item/crafting/{item}"))
    display.update(extra)
    return display


def improvised_crowbar():
    shaft = ("y", 45, (8, 8.8, 8))
    return [
        box((7.3, 0, 7.3), (8.7, 17.6, 8.7), "red_paint", seed=1),
        box((7.3, 0, 7.3), (8.7, 17.6, 8.7), "red_paint", rot=shaft, seed=2),
        box((7.3, 17, 6.2), (8.7, 18.6, 8.7), "red_paint", seed=3),
        box((7.3, 15.4, 4.6), (8.7, 18.6, 6.2), "red_paint", seed=4),
        box((7.45, 14.2, 4.6), (8.55, 15.4, 5.8), "bare_steel", seed=5),
        box((7.3, -1.6, 7.6), (8.7, 0, 8.4), "bare_steel", rot=("x", 15, (8, -0.8, 8)), seed=6),
    ], _display("improvised_crowbar", hands([-30, 0, 6], [0, 2.5, 0], 0.52))


def magic_noclip_wand():
    return [
        box((7.3, -2, 7.3), (8.7, 18, 8.7), "old_wood", seed=1),
        box((7.0, 4, 7.0), (9.0, 6.5, 9.0), "wallpaper_strip", seed=2),
        box((7.0, 9, 7.0), (9.0, 11, 9.0), "wallpaper_strip", seed=3),
        box((7.0, 13.5, 7.0), (9.0, 15.5, 9.0), "wallpaper_strip", seed=4),
        box((6.9, 16.4, 6.9), (9.1, 18, 9.1), "duct_tape", seed=5),
        box((6.5, 18, 6.5), (9.5, 19.6, 9.5), "lamp_metal", seed=6),
        box((7.2, 19.6, 7.2), (8.8, 25.4, 8.8), "fluoro_tube", emit=7, seed=7),
        box((7.5, 25.4, 7.5), (8.3, 26.3, 8.1), "fluoro_tube", emit=7, seed=8),
        box((8.7, 14.6, 7.8), (9.1, 18.4, 8.2), "wire_black", seed=9),
        box((9.1, 14.6, 7.8), (9.6, 15.0, 8.2), "copper_bright", seed=10),
    ], _display("magic_noclip_wand", hands([-26, 0, 6], [0, 1.5, 0], 0.46))


def gun_pipe_pistol():
    grip = ("x", 17, (8, 7, 10.4))
    return [
        box((7, 7, -3), (9, 9, 7), "galv_steel", seed=1),
        box((6.8, 6.8, -3.2), (9.2, 9.2, -2.4), "rust_pipe", seed=2),
        box((6.8, 6.8, 0.6), (9.2, 9.2, 1.4), "bare_steel", seed=3),
        box((6.8, 6.8, 3.8), (9.2, 9.2, 4.6), "bare_steel", seed=4),
        box((6.4, 6.4, 6.6), (9.6, 9.6, 9.4), "galv_steel", seed=5),
        box((7.4, 7.4, 9.4), (8.6, 8.6, 10.2), "rust_pipe", seed=6),
        box((7.7, 9.6, 8.4), (8.3, 10.6, 9.0), "bare_steel", seed=7),
        box((7.05, 1.8, 9.2), (8.95, 7.2, 11.6), "scrap_wood", rot=grip, seed=8),
        box((6.95, 3.6, 9.1), (9.05, 5.0, 11.7), "duct_tape", rot=grip, seed=9),
        box((7.4, 6.2, 6.8), (8.6, 7.0, 10.4), "galv_steel", seed=10),
        box((7.8, 5.4, 7.6), (8.2, 6.4, 8.0), "bare_steel", seed=11),
        box((7.75, 4.9, 6.4), (8.25, 5.2, 8.8), "bare_steel", seed=12),
        box((7.75, 5.2, 6.4), (8.25, 6.4, 6.7), "bare_steel", seed=13),
        box((7.75, 9.0, -2.2), (8.25, 9.5, -1.6), "bare_steel", seed=14),
    ], _display("gun_pipe_pistol", hands([0, 0, 0], [0, 2.5, 1], 0.6))


def copper_wire():
    return [
        box((4, 4, 6), (12, 12, 6.8), "spool", seed=1),
        box((4, 4, 9.2), (12, 12, 10), "spool", seed=2),
        box((5, 5, 6.8), (11, 11, 9.2), "copper_winding", seed=3),
        box((7.2, 7.2, 5.95), (8.8, 8.8, 6.0), "tape_black", faces=("north",), seed=4),
        box((10.9, 7.7, 7.8), (13.2, 8.1, 8.2), "copper_winding", seed=5),
        box((12.8, 8.1, 7.8), (13.2, 10.2, 8.2), "copper_winding", seed=6),
        box((12.8, 10.2, 7.8), (13.2, 10.8, 8.2), "copper_bright", seed=7),
    ], _display("copper_wire", {**hands([0, 45, 0], [0, 3.5, 0], 0.5),
                                "gui": {"rotation": [25, 40, 0], "translation": [0, 0, 0], "scale": [0.8] * 3}})


def almond_elixir():
    return [
        box((5.5, 1.6, 5.5), (10.5, 10.5, 10.5), "glass_milk", seed=1),
        box((6.2, 10.5, 6.2), (9.8, 11.6, 9.8), "glass_milk", seed=2),
        box((7.1, 11.6, 7.1), (8.9, 13.4, 8.9), "glass_milk", seed=3),
        box((7.2, 13.4, 7.2), (8.8, 14.8, 8.8), "cork", seed=4),
        box((5.45, 3.6, 5.45), (10.55, 7.4, 10.55), "label", faces=("north", "south", "east", "west"), seed=5),
        box((7.0, 12.3, 7.0), (9.0, 12.7, 9.0), "twine", faces=("north", "south", "east", "west"), seed=6),
    ], _display("almond_elixir", hands([0, 30, 0], [0, 3.5, 0], 0.5))


def prop_office_chair():
    star = ("y", 22.5, (8, 1, 8))
    back = ("x", -10, (8, 9.2, 12.7))
    parts = [
        box((2.6, 1.0, 7.5), (13.4, 1.7, 8.5), "dark_plastic", rot=star, seed=1),
        box((7.5, 1.0, 2.6), (8.5, 1.7, 13.4), "dark_plastic", rot=star, seed=2),
    ]
    for frm, to in (((2.2, 0, 7.3), (3.6, 1, 8.7)), ((12.4, 0, 7.3), (13.8, 1, 8.7)), ((7.3, 0, 2.2), (8.7, 1, 3.6)), ((7.3, 0, 12.4), (8.7, 1, 13.8))):
        parts.append(box(frm, to, "dark_plastic", rot=star, seed=3))
    parts += [
        box((7, 1, 7), (9, 2.2, 9), "dark_plastic", seed=4),
        box((7.2, 2.2, 7.2), (8.8, 3.6, 8.8), "dark_plastic", seed=5),
        box((7.4, 3.6, 7.4), (8.6, 5.6, 8.6), "chrome_worn", seed=6),
        box((4.6, 5.6, 4.6), (11.4, 6.2, 11.4), "dark_plastic", seed=7),
        box((3.4, 6.2, 3.2), (12.6, 8.0, 12.2), "office_fabric", seed=8),
        box((7.2, 6.0, 11.4), (8.8, 10.2, 12.4), "dark_plastic", rot=back, seed=9),
        box((3.8, 9.2, 11.8), (12.2, 16.0, 13.6), "office_fabric", rot=back, seed=10),
        box((4.0, 9.4, 13.6), (12.0, 15.8, 14.0), "dark_plastic", rot=back, seed=11),
    ]
    for x0 in (3.0, 12.2):
        parts.append(box((x0, 6.6, 6.6), (x0 + 0.8, 10.4, 7.6), "dark_plastic", seed=12))
        parts.append(box((x0 - 0.2, 10.4, 5.4), (x0 + 1.0, 11.2, 10.2), "dark_plastic", seed=13))
    return parts


def prop_desktop_computer():
    return [
        box((3, 2, 5), (13, 11, 7.4), "beige_plastic", seed=1),
        box((4, 3, 4.9), (12, 10, 5.0), "crt_screen", faces=("north",), emit=3, seed=2),
        box((4, 2.6, 7.4), (12, 10.2, 11), "beige_plastic", seed=3),
        box((5, 3.4, 11), (11, 9.2, 13), "beige_plastic", seed=4),
        box((5, 0, 5.8), (11, 1, 11.2), "beige_plastic", seed=5),
        box((6.5, 1, 6.6), (9.5, 2, 10), "beige_plastic", seed=6),
        box((3.2, 0, 0.6), (12.8, 0.7, 3.8), "keyboard", faces=("up",), rot=("x", 4, (8, 0, 2.2)), seed=7),
        box((3.2, 0, 0.6), (12.8, 0.7, 3.8), "beige_plastic", faces=("north", "south", "east", "west", "down"), rot=("x", 4, (8, 0, 2.2)), seed=8),
        box((13.2, 0, 1.4), (14.4, 0.5, 3.0), "beige_plastic", seed=9),
        box((13.6, 0, 3.0), (14.0, 0.15, 6.0), "cable", seed=10),
    ]


ITEMS = {f.__name__: f for f in (improvised_crowbar, magic_noclip_wand, gun_pipe_pistol, copper_wire, almond_elixir)}
PROPS = {f.__name__: f for f in (prop_office_chair, prop_desktop_computer)}
