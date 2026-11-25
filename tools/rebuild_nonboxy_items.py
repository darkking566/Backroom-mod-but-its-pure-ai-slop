"""
Upgrades all non-weapon 3D item models in:
  src/main/resources/assets/noclip_backrooms/models/item/crafting/*.json
to crisp, realistic, NON-BOXY silhouettes (slim flat magazines, thin PCBs with micro-chips,
tapered bottles & flasks, slender pens/keys/screwdrivers/barrels/light tubes, notched gears,
coiled springs, curved fangs, octagonal coins, open reflex sights, faceted glowing cores).
Preserves each item's existing 128x128 16-tile atlas texture and legal Minecraft [-16, 32] bounds.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELS_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "noclip_backrooms", "models", "item", "crafting")

WEAPON_IDS = {
    "gun_pipe_pistol", "gun_pipe_shotgun", "gun_nailgun", "gun_taser", "gun_flamethrower",
    "gun_revolver", "gun_pistol_9mm", "gun_heavy_handgun",
    "gun_double_barrel", "gun_pump_shotgun", "gun_auto_shotgun",
    "gun_scrap_smg", "gun_tactical_smg", "gun_assault_rifle",
    "gun_bolt_sniper", "gun_antimateriel_rifle", "gun_railgun", "gun_sonic_pulse",
    "office_crossbow", "static_pulse_staff",
}

DISPLAY_STD = {
    "thirdperson_righthand": {"rotation": [12, 0, 0], "translation": [0, 2.0, 0.5], "scale": [0.68, 0.68, 0.68]},
    "thirdperson_lefthand": {"rotation": [12, 0, 0], "translation": [0, 2.0, 0.5], "scale": [0.68, 0.68, 0.68]},
    "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [1.2, 2.8, 0.5], "scale": [0.65, 0.65, 0.65]},
    "firstperson_lefthand": {"rotation": [0, -25, 0], "translation": [1.2, 2.8, 0.5], "scale": [0.65, 0.65, 0.65]},
    "gui": {"rotation": [28, 38, 0], "translation": [0, 0.5, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"translation": [0, 2.2, 0], "scale": [0.55, 0.55, 0.55]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
}


def box(x0, y0, z0, x1, y1, z1, tile=0, glow=False, rot=None):
    """Build a crisp MC JSON element mapped to one of the 16 32x32 tiles in our 128x128 atlas."""
    u0 = (tile % 4) * 4.0
    v0 = (tile // 4) * 4.0
    uv = [u0 + 0.25, v0 + 0.25, u0 + 3.75, v0 + 3.75]
    face = {"uv": uv, "texture": "#all"}
    if glow:
        face["light_emission"] = 15
    el = {
        "from": [round(x0, 2), round(y0, 2), round(z0, 2)],
        "to": [round(x1, 2), round(y1, 2), round(z1, 2)],
        "faces": {d: dict(face) for d in ("north", "south", "east", "west", "up", "down")},
    }
    if rot:
        el["rotation"] = rot
    return el


def rot_z(cx, cy, cz, ang):
    return {"origin": [cx, cy, cz], "axis": "z", "angle": ang}


def rot_x(cx, cy, cz, ang):
    return {"origin": [cx, cy, cz], "axis": "x", "angle": ang}


def rot_y(cx, cy, cz, ang):
    return {"origin": [cx, cy, cz], "axis": "y", "angle": ang}


def build_slim_magazine(extended=False):
    """True slim flat curved rifle/pistol magazine (only 1.8px thick instead of a fat block!)."""
    y_bot = 1.4 if extended else 3.2
    els = [
        # Upper straight mag body (thin in X: 7.1..8.9!)
        box(7.1, 8.2, 5.8, 8.9, 14.5, 9.6, tile=1),
        # Lower angled banana curve!
        box(7.15, y_bot + 0.8, 6.2, 8.85, 8.6, 10.0, tile=1, rot=rot_x(8.0, 8.4, 8.0, 12.5)),
        # Metallic feed lips + brass cartridge visible on top
        box(7.2, 14.4, 6.0, 8.8, 15.2, 9.4, tile=0),
        box(7.45, 14.9, 6.3, 8.55, 15.6, 9.1, tile=4),
        # Reinforced polymer baseplate flush with angled lower body
        box(6.85, y_bot, 7.1 if extended else 6.7, 9.15, y_bot + 1.1, 11.4 if extended else 11.0, tile=0, rot=rot_x(8.0, y_bot + 0.6, 8.8, 12.5)),
        # Slim vertical spine ribs + glowing round witness windows
        box(6.95, 9.2, 7.3, 9.05, 13.4, 8.1, tile=2),
        box(7.0, 4.6, 7.8, 9.0, 5.4, 8.8, tile=8, glow=True, rot=rot_x(8.0, 8.4, 8.0, 12.5)),
        box(7.0, 6.4, 7.7, 9.0, 7.2, 8.7, tile=8, glow=True, rot=rot_x(8.0, 8.4, 8.0, 12.5)),
    ]
    return els


def build_ammo_rounds(id_name):
    """Tapered slim cartridges / shells / pen bolts / glowing rail slugs (NO fat cubes!)."""
    if "pen_bolt" in id_name or "nail" in id_name:
        return [
            box(7.4, 1.2, 7.4, 8.6, 13.6, 8.6, tile=3),
            box(7.6, 13.6, 7.6, 8.4, 15.5, 8.4, tile=5),
            box(7.1, 2.0, 7.7, 8.9, 4.4, 8.3, tile=8, glow=True),
            box(7.7, 2.0, 7.1, 8.3, 4.4, 8.9, tile=8, glow=True),
        ]
    if "rail_slug" in id_name:
        return [
            box(7.0, 2.0, 7.0, 9.0, 13.8, 9.0, tile=0),
            box(7.4, 13.8, 7.4, 8.6, 15.6, 8.6, tile=5),
            box(6.6, 4.0, 6.6, 9.4, 5.2, 9.4, tile=10),
            box(6.6, 7.2, 6.6, 9.4, 8.4, 9.4, tile=10),
            box(6.6, 10.4, 6.6, 9.4, 11.6, 9.4, tile=10),
            box(7.2, 2.6, 7.2, 8.8, 13.2, 8.8, tile=8, glow=True),
        ]
    if "fuel" in id_name:
        return [
            box(5.6, 1.5, 5.6, 10.4, 12.2, 10.4, tile=11),
            box(6.4, 12.2, 6.4, 9.6, 13.4, 9.6, tile=0),
            box(7.2, 13.4, 7.2, 8.8, 15.0, 8.8, tile=4),
            box(5.4, 5.0, 5.4, 10.6, 8.0, 10.6, tile=13),
            box(6.8, 8.8, 5.3, 9.2, 10.4, 10.7, tile=8, glow=True),
        ]
    # 3 crisp staggered brass bullets / shotgun shells standing cleanly side-by-side!
    els = []
    glow_tip = "acid" in id_name or "phosphor" in id_name or "_ap" in id_name
    for ox, oz in ((-2.6, -0.8), (0.0, 0.8), (2.6, -0.4)):
        # Brass casing + rim + tapered tip
        els.append(box(6.9 + ox, 2.2, 6.9 + oz, 9.1 + ox, 3.0, 9.1 + oz, tile=4))
        els.append(box(7.1 + ox, 3.0, 7.1 + oz, 8.9 + ox, 10.6, 8.9 + oz, tile=11 if "12g" in id_name else 4))
        els.append(box(7.35 + ox, 10.6, 7.35 + oz, 8.65 + ox, 12.8, 8.65 + oz, tile=8 if glow_tip else 5, glow=glow_tip))
        els.append(box(7.65 + ox, 12.8, 7.65 + oz, 8.35 + ox, 14.3, 8.35 + oz, tile=8 if glow_tip else 10, glow=glow_tip))
    return els


def build_slim_circuit_or_card(is_card=False, glow_chip=True):
    """Ultra-slim 1.0px thick PCB circuit board / keycard with surface micro-chips & gold pins!"""
    if is_card:
        return [
            box(3.5, 4.8, 7.6, 12.5, 11.2, 8.4, tile=1),
            box(4.2, 6.8, 7.45, 6.6, 9.2, 8.55, tile=4),
            box(3.5, 9.6, 7.5, 12.5, 10.6, 8.5, tile=8, glow=True),
            box(7.4, 5.6, 7.5, 11.6, 6.4, 8.5, tile=5),
        ]
    return [
        # Ultra-thin PCB wafer (0.9px thick in Z!)
        box(2.2, 2.8, 7.55, 13.8, 13.2, 8.45, tile=14),
        # Central silicon microchip + glowing die
        box(5.6, 5.8, 7.05, 10.4, 10.2, 8.95, tile=1),
        box(6.8, 6.8, 6.95, 9.2, 9.2, 9.05, tile=8, glow=glow_chip),
        # Secondary capacitors & SMD resistors
        box(3.0, 9.8, 7.15, 4.8, 12.0, 8.85, tile=4),
        box(11.0, 4.0, 7.15, 12.8, 6.8, 8.85, tile=10),
        box(3.2, 4.0, 7.25, 4.6, 7.6, 8.75, tile=5),
        # Gold edge connector finger row along bottom!
        box(3.4, 2.1, 7.6, 12.6, 2.9, 8.4, tile=4),
    ]


def build_slim_sheet_or_scroll(kind):
    if kind == "scroll":
        return [
            box(4.4, 3.2, 7.3, 11.6, 12.8, 8.7, tile=12),
            box(3.4, 12.4, 6.9, 12.6, 13.8, 9.1, tile=4),
            box(3.4, 2.2, 6.9, 12.6, 3.6, 9.1, tile=4),
            box(5.6, 6.2, 7.15, 10.4, 9.8, 8.85, tile=8, glow=True),
            box(7.2, 4.4, 7.15, 8.8, 11.6, 8.85, tile=9, glow=True),
        ]
    if kind == "glass":
        return [
            box(2.0, 2.0, 7.65, 14.0, 14.0, 8.35, tile=7),
            box(2.0, 13.2, 7.45, 14.0, 14.0, 8.55, tile=0),
            box(2.0, 2.0, 7.45, 14.0, 2.8, 8.55, tile=0),
            box(2.0, 2.8, 7.45, 2.8, 13.2, 8.55, tile=0),
            box(13.2, 2.8, 7.45, 14.0, 13.2, 8.55, tile=0),
        ]
    # Thin metal plate / rag / paper / magazine sheet (1.1px thick with crisp corner fold / rivets)
    return [
        box(2.2, 2.6, 7.45, 13.8, 13.4, 8.55, tile=0 if kind == "plate" else 12),
        box(2.8, 3.2, 7.35, 4.0, 4.4, 8.65, tile=4),
        box(12.0, 3.2, 7.35, 13.2, 4.4, 8.65, tile=4),
        box(2.8, 11.6, 7.35, 4.0, 12.8, 8.65, tile=4),
        box(12.0, 11.6, 7.35, 13.2, 12.8, 8.65, tile=4),
        box(4.6, 6.6, 7.3, 11.4, 9.4, 8.7, tile=5),
    ]


def build_bottle_or_flask(kind):
    """Tapered bottle / Erlenmeyer science pack / Molotov cocktail / syringe — ZERO chunky cubes!"""
    if kind == "syringe":
        return [
            box(7.1, 4.4, 7.1, 8.9, 11.6, 8.9, tile=7),
            box(7.35, 5.2, 7.35, 8.65, 10.6, 8.65, tile=8, glow=True),
            box(6.4, 4.0, 6.4, 9.6, 4.6, 9.6, tile=5),
            box(7.6, 1.4, 7.6, 8.4, 4.2, 8.4, tile=0),
            box(6.8, 0.8, 6.8, 9.2, 1.6, 9.2, tile=4),
            box(7.75, 11.6, 7.75, 8.25, 15.6, 8.25, tile=5),
        ]
    if kind == "flask":
        # Factorio conical Erlenmeyer science pack with rich colored glowing reagent!
        return [
            box(4.6, 1.6, 4.6, 11.4, 5.2, 11.4, tile=15, glow=True),
            box(5.6, 5.2, 5.6, 10.4, 8.0, 10.4, tile=15, glow=True),
            box(6.5, 8.0, 6.5, 9.5, 10.4, 9.5, tile=7),
            box(7.0, 10.4, 7.0, 9.0, 13.4, 9.0, tile=7),
            box(6.5, 13.2, 6.5, 9.5, 14.2, 9.5, tile=4),
        ]
    if kind == "molotov":
        return [
            box(5.4, 1.4, 5.4, 10.6, 8.6, 10.6, tile=7),
            box(5.7, 1.8, 5.7, 10.3, 7.4, 10.3, tile=10),
            box(6.6, 8.6, 6.6, 9.4, 11.0, 9.4, tile=7),
            box(7.1, 11.0, 7.1, 8.9, 13.4, 8.9, tile=7),
            # Stuffed glowing flaming rag wick!
            box(7.2, 13.0, 7.2, 8.8, 15.6, 8.8, tile=12, rot=rot_z(8.0, 13.2, 8.0, 22.5)),
            box(6.8, 14.6, 7.0, 9.2, 16.4, 9.0, tile=8, glow=True, rot=rot_z(8.0, 14.8, 8.0, 22.5)),
        ]
    # Tapered sleek bottle / vial
    return [
        box(5.6, 1.6, 5.6, 10.4, 9.4, 10.4, tile=7),
        box(5.9, 2.0, 5.9, 10.1, 8.4, 10.1, tile=8, glow=True),
        box(6.4, 9.4, 6.4, 9.6, 11.2, 9.6, tile=7),
        box(6.9, 11.2, 6.9, 9.1, 13.6, 9.1, tile=7),
        box(6.6, 13.4, 6.6, 9.4, 14.6, 9.4, tile=4),
        box(5.4, 4.4, 5.4, 10.6, 7.2, 10.6, tile=12),
    ]


def build_gear_or_ring(kind):
    if kind == "gear":
        els = [
            box(5.0, 5.0, 7.2, 11.0, 11.0, 8.8, tile=0),
            box(6.8, 6.8, 7.0, 9.2, 9.2, 9.0, tile=1),
        ]
        # 8 crisp radial gear teeth!
        for ang in (0, 45):
            els.append(box(7.1, 3.0, 7.3, 8.9, 13.0, 8.7, tile=5, rot=rot_z(8, 8, 8, ang)))
            els.append(box(3.0, 7.1, 7.3, 13.0, 8.9, 8.7, tile=5, rot=rot_z(8, 8, 8, ang)))
        return els
    if kind == "coil_wire":
        return [
            box(4.2, 4.2, 6.8, 11.8, 5.4, 9.2, tile=10),
            box(4.2, 10.6, 6.8, 11.8, 11.8, 9.2, tile=10),
            box(4.2, 5.4, 6.8, 5.4, 10.6, 9.2, tile=10),
            box(10.6, 5.4, 6.8, 11.8, 10.6, 9.2, tile=10),
            box(4.8, 7.3, 7.2, 11.2, 8.7, 8.8, tile=4, rot=rot_z(8, 8, 8, 22.5)),
        ]
    # Coiled spring / tape roll / tire
    els = []
    for y in (3.0, 5.4, 7.8, 10.2, 12.6):
        els.append(box(5.2, y, 5.2, 10.8, y + 1.0, 10.8, tile=5, rot=rot_z(8, y + 0.5, 8, 22.5)))
    return els


def build_slender_tool_or_part(kind):
    if kind == "pen":
        return [
            box(7.4, 2.0, 7.4, 8.6, 13.6, 8.6, tile=1),
            box(7.65, 13.6, 7.65, 8.35, 15.4, 8.35, tile=5),
            box(7.2, 9.2, 8.4, 7.7, 13.2, 9.1, tile=4),
            box(7.35, 1.2, 7.35, 8.65, 2.4, 8.65, tile=8, glow=True),
        ]
    if kind == "screwdriver":
        return [
            box(6.9, 1.2, 6.9, 9.1, 6.8, 9.1, tile=2),
            box(7.2, 6.8, 7.2, 8.8, 7.6, 8.8, tile=4),
            box(7.65, 7.6, 7.65, 8.35, 14.6, 8.35, tile=5),
            box(7.45, 14.6, 7.8, 8.55, 15.8, 8.2, tile=5),
        ]
    if kind == "key":
        return [
            box(5.6, 1.6, 7.4, 10.4, 5.4, 8.6, tile=4),
            box(6.8, 2.6, 7.3, 9.2, 4.4, 8.7, tile=1),
            box(7.45, 5.4, 7.45, 8.55, 14.4, 8.55, tile=4),
            box(8.55, 11.2, 7.55, 10.8, 12.2, 8.45, tile=4),
            box(8.55, 13.0, 7.55, 10.4, 14.0, 8.45, tile=4),
        ]
    if kind == "barrel_tube":
        return [
            box(7.0, 0.6, 7.0, 9.0, 15.4, 9.0, tile=0),
            box(6.6, 1.4, 6.6, 9.4, 3.2, 9.4, tile=1),
            box(6.6, 12.6, 6.6, 9.4, 14.2, 9.4, tile=1),
            box(7.3, 4.0, 7.3, 8.7, 12.0, 8.7, tile=5),
        ]
    if kind == "lamp_tube":
        return [
            box(7.1, 0.8, 7.1, 8.9, 15.2, 8.9, tile=8, glow=True),
            box(6.8, 0.2, 6.8, 9.2, 1.4, 9.2, tile=5),
            box(6.8, 14.6, 6.8, 9.2, 15.8, 9.2, tile=5),
        ]
    if kind == "fan":
        els = [
            box(2.4, 2.4, 7.0, 13.6, 3.6, 9.0, tile=1),
            box(2.4, 12.4, 7.0, 13.6, 13.6, 9.0, tile=1),
            box(2.4, 3.6, 7.0, 3.6, 12.4, 9.0, tile=1),
            box(12.4, 3.6, 7.0, 13.6, 12.4, 9.0, tile=1),
            box(6.6, 6.6, 6.8, 9.4, 9.4, 9.2, tile=0),
        ]
        for ang in (0, 45):
            els.append(box(3.8, 7.2, 7.5, 12.2, 8.8, 8.5, tile=5, rot=rot_z(8, 8, 8, ang)))
            els.append(box(7.2, 3.8, 7.5, 8.8, 12.2, 8.5, tile=5, rot=rot_z(8, 8, 8, ang)))
        return els
    if kind == "coin":
        return [
            box(4.6, 4.6, 7.45, 11.4, 11.4, 8.55, tile=4),
            box(4.6, 4.6, 7.50, 11.4, 11.4, 8.50, tile=4, rot=rot_z(8, 8, 8, 45)),
            box(6.4, 6.4, 7.35, 9.6, 9.6, 8.65, tile=8, glow=True),
        ]
    if kind == "cassette":
        return [
            box(2.2, 4.4, 7.2, 13.8, 11.6, 8.8, tile=1),
            box(3.0, 6.2, 7.05, 13.0, 10.6, 8.95, tile=12),
            box(4.6, 7.2, 6.95, 6.8, 9.4, 9.05, tile=0),
            box(9.2, 7.2, 6.95, 11.4, 9.4, 9.05, tile=0),
            box(6.8, 7.6, 7.0, 9.2, 9.0, 9.0, tile=8, glow=True),
        ]
    if kind == "crystal_core":
        return [
            box(6.2, 3.4, 6.2, 9.8, 12.6, 9.8, tile=8, glow=True, rot=rot_y(8, 8, 8, 45)),
            box(6.6, 4.4, 6.6, 9.4, 11.6, 9.4, tile=9, glow=True, rot=rot_z(8, 8, 8, 22.5)),
            box(4.2, 7.5, 4.2, 11.8, 8.5, 11.8, tile=4, rot=rot_x(8, 8, 8, 22.5)),
            box(4.8, 7.6, 4.8, 11.2, 8.4, 11.2, tile=8, glow=True, rot=rot_z(8, 8, 8, -22.5)),
        ]
    if kind == "scope_mod":
        return [
            box(6.2, 6.6, 2.0, 9.8, 10.2, 14.0, tile=1),
            box(5.6, 6.0, 1.4, 10.4, 10.8, 4.8, tile=0),
            box(6.0, 6.4, 12.0, 10.0, 10.4, 14.6, tile=0),
            box(6.4, 6.8, 1.2, 9.6, 10.0, 1.8, tile=8, glow=True),
            box(7.3, 10.2, 7.2, 8.7, 11.6, 8.8, tile=4),
            box(6.6, 4.8, 5.0, 9.4, 6.6, 6.4, tile=0),
            box(6.6, 4.8, 9.8, 9.4, 6.6, 11.2, tile=0),
        ]
    if kind == "fang_claw":
        return [
            box(6.6, 2.2, 6.6, 9.4, 6.4, 9.4, tile=12),
            box(7.0, 6.0, 6.8, 9.0, 10.4, 9.2, tile=12, rot=rot_x(8, 6.2, 8, -12.5)),
            box(7.3, 9.8, 6.2, 8.7, 13.6, 8.2, tile=5, rot=rot_x(8, 10.0, 7.2, -22.5)),
            box(7.6, 12.8, 5.2, 8.4, 15.2, 6.6, tile=8, glow=True, rot=rot_x(8, 13.0, 6.0, -35)),
        ]
    if kind == "bandage_roll":
        return [
            box(5.4, 4.2, 5.4, 10.6, 11.8, 10.6, tile=12),
            box(6.8, 4.0, 6.8, 9.2, 12.0, 9.2, tile=3),
            box(10.0, 4.6, 7.6, 13.8, 11.4, 8.4, tile=12, rot=rot_y(10.2, 8, 8, 15)),
            box(5.2, 7.2, 5.2, 10.8, 8.8, 10.8, tile=8, glow=False),
        ]
    if kind == "stapler":
        return [
            box(6.4, 4.2, 3.0, 9.6, 5.4, 13.0, tile=1),
            box(6.6, 5.4, 10.6, 9.4, 7.6, 12.6, tile=0),
            box(6.5, 6.2, 3.2, 9.5, 8.2, 12.6, tile=11, rot=rot_x(8, 6.6, 11.6, 10)),
            box(7.2, 5.4, 3.6, 8.8, 6.2, 9.6, tile=5),
        ]
    if kind == "hacker":
        return [
            box(4.6, 2.2, 6.8, 11.4, 12.8, 9.2, tile=1),
            box(5.4, 7.8, 6.65, 10.6, 11.8, 9.35, tile=8, glow=True),
            box(5.4, 3.2, 6.65, 10.6, 7.0, 9.35, tile=14),
            box(9.8, 12.8, 7.6, 10.6, 15.8, 8.4, tile=5),
            box(5.8, 12.8, 7.5, 7.0, 14.4, 8.5, tile=4),
        ]
    # Slender crafted handheld part / bar / shard
    return [
        box(5.6, 2.6, 6.8, 10.4, 12.4, 9.2, tile=0, rot=rot_z(8, 8, 8, 10)),
        box(6.4, 4.0, 6.5, 9.6, 11.0, 9.5, tile=5, rot=rot_z(8, 8, 8, 10)),
        box(6.8, 11.6, 7.0, 9.2, 14.2, 9.0, tile=4, rot=rot_z(8, 8, 8, 10)),
    ]


def classify_item(id_name):
    if any(k in id_name for k in ("gun_mag_", "ext_mag")):
        return build_slim_magazine("ext" in id_name or "rifle" in id_name)
    if id_name.startswith("ammo_"):
        return build_ammo_rounds(id_name)
    if any(k in id_name for k in ("circuit", "module_", "controller", "computation")):
        return build_slim_circuit_or_card(is_card=False, glow_chip=True)
    if any(k in id_name for k in ("keycard", "blueprint", "parchment")):
        return build_slim_circuit_or_card(is_card=True)
    if "scroll" in id_name:
        return build_slim_sheet_or_scroll("scroll")
    if "glass" in id_name or "mirror" in id_name or "lens" in id_name:
        return build_slim_sheet_or_scroll("glass")
    if any(k in id_name for k in ("plate", "sheet", "scrap_metal", "panel", "armor_plate")):
        return build_slim_sheet_or_scroll("plate")
    if any(k in id_name for k in ("rags", "paper", "magazine_bundle", "hide", "cloth", "fiber", "leather", "fabric", "gauze", "bandaid")):
        return build_slim_sheet_or_scroll("sheet")
    if any(k in id_name for k in ("syringe", "adrenaline", "shot")):
        return build_bottle_or_flask("syringe")
    if "science_pack_" in id_name:
        return build_bottle_or_flask("flask")
    if "molotov" in id_name:
        return build_bottle_or_flask("molotov")
    if any(k in id_name for k in ("bottle", "vial", "almond_", "alcohol", "tea", "salve", "stabilizer", "reserve", "coolant", "acid_sac")):
        return build_bottle_or_flask("bottle")
    if "gear" in id_name:
        return build_gear_or_ring("gear")
    if any(k in id_name for k in ("wire", "coil", "filament")):
        return build_gear_or_ring("coil_wire")
    if any(k in id_name for k in ("spring", "tape", "tire", "rubber_band", "conveyor", "seal")):
        return build_gear_or_ring("spring")
    if "ballpoint_pen" in id_name:
        return build_slender_tool_or_part("pen")
    if "screwdriver" in id_name or "lockpick" in id_name:
        return build_slender_tool_or_part("screwdriver")
    if "key" in id_name and "keyboard" not in id_name and "keypad" not in id_name:
        return build_slender_tool_or_part("key")
    if any(k in id_name for k in ("barrel", "silencer", "pipe", "rebar", "shaft", "handle", "table_leg")):
        return build_slender_tool_or_part("barrel_tube")
    if "lamp_tube" in id_name or "crt_tube" in id_name:
        return build_slender_tool_or_part("lamp_tube")
    if "fan" in id_name:
        return build_slender_tool_or_part("fan")
    if "coin" in id_name:
        return build_slender_tool_or_part("coin")
    if "cassette" in id_name:
        return build_slender_tool_or_part("cassette")
    if any(k in id_name for k in ("core_", "crystal", "shard", "fire_salt", "eyeball", "charm", "anchor")):
        return build_slender_tool_or_part("crystal_core")
    if any(k in id_name for k in ("scope", "red_dot", "weapon_light", "flashlight")):
        return build_slender_tool_or_part("scope_mod")
    if any(k in id_name for k in ("fang", "claw", "knuckle", "horn", "bayonet")):
        return build_slender_tool_or_part("fang_claw")
    if "bandage" in id_name:
        return build_slender_tool_or_part("bandage_roll")
    if "stapler" in id_name:
        return build_slender_tool_or_part("stapler")
    if "hacker" in id_name or "phone" in id_name or "terminal" in id_name:
        return build_slender_tool_or_part("hacker")
    return None  # Keep already-distinct sculpted tools/armors/equipment as-is!


def main():
    updated = 0
    for fn in sorted(os.listdir(MODELS_DIR)):
        if not fn.endswith(".json"):
            continue
        item_id = fn[:-5]
        if item_id in WEAPON_IDS or item_id.startswith(("blunt_", "west_", "cn_", "magic_")):
            continue
        if any(k in item_id for k in ("_helmet", "_chestplate", "_leggings", "_boots", "gas_mask", "crowbar", "cutters", "wrench", "shears", "saw", "pick", "breaker")):
            continue
        new_els = classify_item(item_id)
        if not new_els:
            continue
        p = os.path.join(MODELS_DIR, fn)
        with open(p, "r", encoding="utf-8") as f:
            model = json.load(f)
        tex_keys = [k for k in model.get("textures", {}).keys() if k != "particle"]
        tex_ref = f"#{tex_keys[0]}" if tex_keys else "#0"
        for el in new_els:
            for face in el["faces"].values():
                face["texture"] = tex_ref
        model["elements"] = new_els
        model["display"] = DISPLAY_STD
        with open(p, "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)
        updated += 1
    print(f"Upgraded {updated} non-weapon item models to slim, non-boxy 3D silhouettes!")


if __name__ == "__main__":
    main()
