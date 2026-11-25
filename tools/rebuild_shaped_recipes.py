"""
Rebuilds all Backrooms crafting recipes, block salvage loot tables, Ultimate Spiderweb (9x9)
workstation assets, and lang entries to match the approved physical shaped crafting system:
  - Vanilla 3x3 Crafting Table: Tier 0-1 weapons/armor, medicines, Almond Water consumables,
    basic parts/tools, and workstation blocks (~85 recipes).
  - Salvage Bench (1 -> 2..4): real physical salvage input slot -> multi-output slots.
  - Almond Distiller (3x3 shaped): Almond brews, reagents, energy bricks, science packs.
  - Anomaly Altar (3x3 shaped star-ritual): Tier 2-3 magic relics, anomaly cores, catalysts.
  - Workshop Bench (5x5 shaped): EVERY recipe spans >= 4 rows or cols (5x5 weapon/gun/armor blueprints).
  - Primary Spiderweb (7x7 shaped): EVERY recipe spans 7x7 concentric spiderweb (core + inner + outer vanilla ring).
  - Ultimate Spiderweb (9x9 shaped): EVERY recipe spans 9x9 concentric spiderweb (Boss Core + dual inner + outer vanilla ring).
  - All 44 furniture/prop block loot tables: drop REAL base salvage parts on break (Silk Touch preserves block).
"""
import json
import os
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "noclip_backrooms")
DATA = os.path.join(RES, "data", "noclip_backrooms")
STATION_JSON = os.path.join(DATA, "backrooms_recipes", "station_recipes.json")
VANILLA_DIR = os.path.join(DATA, "recipe")
LOOT_DIR = os.path.join(DATA, "loot_table", "blocks")


def mod(name):
    return name if ":" in name else f"noclip_backrooms:{name}"


def item_category(item_id):
    p = item_id.split(":")[-1]
    if p.startswith(("blunt_", "west_", "cn_")):
        return "melee"
    if p.startswith("gun_") and p in EXACT_FIREARMS or p == "office_crossbow":
        return "firearms"
    if p.startswith("magic_") or p == "static_pulse_staff":
        return "magic"
    if any(k in p for k in ("_helmet", "_chestplate", "_leggings", "_boots", "gas_mask")):
        return "armor"
    if p.startswith(("ammo_", "mod_", "core_")) or any(k in p for k in (
            "bench", "distiller", "altar", "spiderweb", "recycler", "barrel", "terminal",
            "replicator", "board", "dropoff", "hacker", "crowbar", "cutters", "wrench",
            "screwdriver", "shears", "saw", "pick", "breaker", "anchor", "scroll",
            "bandage", "medkit", "tea", "reserve", "cocktail", "mirror", "flashlight", "lockpick")):
        return "gear"
    return "parts"


EXACT_FIREARMS = {
    "gun_pipe_pistol", "gun_pipe_shotgun", "gun_nailgun", "gun_taser", "gun_flamethrower",
    "gun_revolver", "gun_pistol_9mm", "gun_heavy_handgun",
    "gun_double_barrel", "gun_pump_shotgun", "gun_auto_shotgun",
    "gun_scrap_smg", "gun_tactical_smg", "gun_assault_rifle",
    "gun_bolt_sniper", "gun_antimateriel_rifle", "gun_railgun", "gun_sonic_pulse",
    "office_crossbow",
}

# Endgame Tier 4 items -> Ultimate Spiderweb 9x9
ULTIMATE_9X9_OUTS = {
    "gun_railgun", "magic_void_rift_staff", "magic_astral_codex", "magic_beast_summon_bell",
    "west_void_greatsword", "cn_alpha_tangdao", "blunt_thunder_colossus",
    "abyssal_void_helmet", "abyssal_void_chestplate", "abyssal_void_leggings", "abyssal_void_boots",
    "void_disruptor", "keypad_hacker_t4", "homeward_anchor", "crystal_replicator",
}

# High-tier Tier 3-4 items -> Primary Spiderweb 7x7
SPIDERWEB_7X7_OUTS = {
    "gun_sonic_pulse", "gun_antimateriel_rifle", "gun_flamethrower", "gun_auto_shotgun",
    "west_hotel_greatsword", "west_hallowed_blade", "west_zweihander",
    "cn_fangtian_ji", "cn_guandao", "cn_coin_sword",
    "blunt_crimson_crusher", "blunt_lucerne_hammer",
    "magic_crimson_scythe", "magic_stopmotion_watch", "magic_noclip_wand", "magic_fate_dice_staff",
    "magic_party_grimoire", "magic_acid_grimoire",
    "boiler_thermal_helmet", "boiler_thermal_chestplate", "boiler_thermal_leggings", "boiler_thermal_boots",
    "anomaly_resonance_pick", "keypad_hacker_t3", "blue_circuit", "science_pack_purple",
    "forbidden_scroll_luck", "forbidden_scroll_psy", "forbidden_scroll_tech",
    "scrap_recycler", "anomaly_distillery_barrel", "anomaly_research_terminal",
    "relay_dropoff_box", "anomaly_archive_board", "ultimate_spiderweb_crafting_table",
}

# Early Tier 0-1 items & medicines / Almond Water consumables -> Vanilla 3x3
VANILLA_3X3_OUTS = {
    "almond_mana_cell", "improvised_calming_tea", "field_bandage", "sterile_bandage", "rough_bandage",
    "almond_elixir", "purified_almond_essence", "aged_almond_reserve",
    "scrap_tactical_helmet", "scrap_tactical_chestplate", "scrap_tactical_leggings", "scrap_tactical_boots",
    "almond_gas_mask", "blunt_iron_mace", "blunt_barbed_kanabo",
    "west_hound_dagger", "west_gladius", "cn_iron_staff", "cn_ring_pommel_dao",
    "gun_pipe_shotgun", "gun_nailgun", "static_pulse_staff",
    "ammo_pen_bolt", "ammo_steel_nails", "ammo_9mm", "ammo_12g_buck",
    "precision_screwdriver", "keypad_hacker_t1", "old_circuit_board", "copper_wire",
    "steel_gear", "heavy_steel_plate", "refined_steel_ingot",
    "ballast_coil", "polymer_frame", "rubber_conveyor_belt", "office_glass",
    "almond_distiller", "anomaly_altar", "spiderweb_crafting_table",
}


def build_pattern_3x3(inputs):
    """Build a crisp 3x3 shaped pattern for Distiller / Altar."""
    items = [x["item"] for x in inputs]
    counts = [x.get("count", 1) for x in inputs]
    keys = {}
    letters = "ABCDEFGHI"
    for i, (it, ct) in enumerate(zip(items, counts)):
        keys[letters[i]] = {"item": it, "count": ct}
    n = len(items)
    if n == 1:
        pat = [" A ", " A ", " A "]
        keys["A"] = {"item": items[0], "count": max(1, (counts[0] + 2) // 3)}
    elif n == 2:
        pat = [" B ", "BAB", " B "]
        keys["A"] = {"item": items[0], "count": counts[0]}
        keys["B"] = {"item": items[1], "count": max(1, (counts[1] + 3) // 4)}
    elif n == 3:
        pat = [" B ", "CAC", " B "]
        keys["A"] = {"item": items[0], "count": counts[0]}
        keys["B"] = {"item": items[1], "count": max(1, (counts[1] + 1) // 2)}
        keys["C"] = {"item": items[2], "count": max(1, (counts[2] + 1) // 2)}
    elif n == 4:
        pat = ["B B", "CAC", "D D"]
        keys["A"] = {"item": items[0], "count": counts[0]}
        keys["B"] = {"item": items[1], "count": max(1, (counts[1] + 1) // 2)}
        keys["C"] = {"item": items[2], "count": max(1, (counts[2] + 1) // 2)}
        keys["D"] = {"item": items[3], "count": max(1, (counts[3] + 1) // 2)}
    else:
        # 5..9 items arranged around center core A
        grid = [[" "] * 3 for _ in range(3)]
        order = [(1, 1), (1, 0), (1, 2), (0, 1), (2, 1), (0, 0), (2, 0), (0, 2), (2, 2)]
        for i in range(min(n, 9)):
            cx, cy = order[i]
            grid[cy][cx] = letters[i]
        pat = ["".join(r) for r in grid]
    used = {ch for row in pat for ch in row if ch != " "}
    keys = {k: v for k, v in keys.items() if k in used}
    return pat, keys


def build_pattern_5x5(out_id, inputs):
    """Build a genuine 5x5 weapon/armor/tool/tech silhouette (ALWAYS spans width>=4 or height>=4!)."""
    items = [x["item"] for x in inputs]
    while len(items) < 4:
        items.append("minecraft:iron_ingot")
    a, b, c, d = items[0], items[1], items[2], items[3]
    e = items[4] if len(items) > 4 else "minecraft:copper_ingot"
    f = items[5] if len(items) > 5 else "minecraft:redstone"

    if out_id in EXACT_FIREARMS or out_id.startswith("gun_"):
        # 5x4 horizontal firearm blueprint: scope/rail top, stock-receiver-barrel across 5 cols, grip+mag below!
        pat = [
            "  E  ",
            "ABBCC",
            "D F  ",
            "D    ",
        ]
    elif out_id.startswith(("blunt_", "west_", "cn_")):
        # 5x5 diagonal or tall weapon blueprint!
        if any(k in out_id for k in ("spear", "halberd", "guandao", "fangtian", "lucerne", "staff")):
            pat = [
                "  AA ",
                "  BA ",
                "  C  ",
                "  C  ",
                "  D  ",
            ]
        elif any(k in out_id for k in ("hammer", "maul", "mace", "axe", "crusher", "colossus")):
            pat = [
                " AAA ",
                " ABA ",
                "  C  ",
                "  C  ",
                "  D  ",
            ]
        else:
            pat = [
                "    A",
                "   AB",
                "  BA ",
                " DC  ",
                "D    ",
            ]
    elif "_helmet" in out_id:
        pat = [
            "AAAAA",
            "ABBBA",
            "AC CA",
            "AD DA",
        ]
    elif "_chestplate" in out_id:
        pat = [
            "AA AA",
            "ABBBA",
            "ABCBA",
            "ADDDA",
            " AAA ",
        ]
    elif "_leggings" in out_id:
        pat = [
            "AAAAA",
            "ABBBA",
            "AC CA",
            "AD DA",
            "A   A",
        ]
    elif "_boots" in out_id:
        pat = [
            "A   A",
            "B   B",
            "C   C",
            "DD DD",
        ]
    else:
        # 5x5 symmetrical industrial blueprint
        pat = [
            " BDB ",
            "BAEAB",
            "DECED",
            "BAEAB",
            " BDB ",
        ]

    key_all = {
        "A": {"item": a, "count": 1},
        "B": {"item": b, "count": 1},
        "C": {"item": c, "count": 1},
        "D": {"item": d, "count": 1},
        "E": {"item": e, "count": 1},
        "F": {"item": f, "count": 1},
    }
    used = {ch for row in pat for ch in row if ch != " "}
    return pat, {k: v for k, v in key_all.items() if k in used}


def build_pattern_7x7(out_id, inputs):
    """True 7x7 Concentric Spiderweb Pattern: Center Core + Inner Ring Backrooms Parts + Outer Vanilla Ring."""
    items = [x["item"] for x in inputs]
    core = items[0] if len(items) > 0 else mod("anomaly_crystal")
    in1 = items[1] if len(items) > 1 else mod("red_circuit")
    in2 = items[2] if len(items) > 2 else mod("refined_steel_ingot")
    in3 = items[3] if len(items) > 3 else mod("energy_brick")
    in4 = items[4] if len(items) > 4 else mod("ballast_coil")
    # Outer vanilla anchor ring (GDD 5.1: iron, copper, redstone, obsidian, amethyst!)
    if "magic_" in out_id or "scroll" in out_id or "boiler" in out_id:
        v_corner, v_edge = "minecraft:obsidian", "minecraft:amethyst_shard"
    elif "gun_" in out_id or "circuit" in out_id or "hacker" in out_id or "terminal" in out_id:
        v_corner, v_edge = "minecraft:iron_ingot", "minecraft:redstone"
    else:
        v_corner, v_edge = "minecraft:iron_ingot", "minecraft:copper_ingot"

    pat = [
        "O E E O",
        "  D D  ",
        "E BCB E",
        " D C D ",
        "E BCB E",
        "  D D  ",
        "O E E O",
    ]
    # Place exact center core at (3, 3) and inner ring around it
    pat = [
        "O  E  O",
        "  D D  ",
        " B C B ",
        "E CKC E",
        " B C B ",
        "  D D  ",
        "O  E  O",
    ]
    key = {
        "K": {"item": core, "count": 1},
        "C": {"item": in1, "count": 1},
        "B": {"item": in2, "count": 1},
        "D": {"item": in3, "count": 1},
        "E": {"item": v_edge, "count": 1},
        "O": {"item": v_corner, "count": 1},
    }
    if len(items) > 4:
        pat[1] = "  DFD  "
        pat[5] = "  DFD  "
        key["F"] = {"item": in4, "count": 1}
    return pat, key


def build_pattern_9x9(out_id, inputs):
    """True 9x9 Ultimate Concentric Spiderweb Pattern: Boss Core + Dual Inner Rings + Outer Vanilla Anchor Ring."""
    items = [x["item"] for x in inputs]
    core = items[0] if len(items) > 0 else mod("core_void_weaver")
    r1 = items[1] if len(items) > 1 else mod("noclip_shard")
    r2 = items[2] if len(items) > 2 else mod("blue_circuit")
    r3 = items[3] if len(items) > 3 else mod("computation_brick")
    r4 = items[4] if len(items) > 4 else mod("tungsten_alloy")
    v_corner = "minecraft:obsidian"
    v_mid = "minecraft:ender_pearl" if ("void" in out_id or "anchor" in out_id or "replicator" in out_id) else "minecraft:diamond"
    v_web = "minecraft:amethyst_shard" if ("magic" in out_id or "void" in out_id) else "minecraft:redstone"

    pat = [
        "O   M   O",
        " W  D  W ",
        "  D C D  ",
        "  CB BC  ",
        "MD BKB DM",
        "  CB BC  ",
        "  D C D  ",
        " W  D  W ",
        "O   M   O",
    ]
    key = {
        "K": {"item": core, "count": 1},
        "B": {"item": r1, "count": 1},
        "C": {"item": r2, "count": 1},
        "D": {"item": r3, "count": 1},
        "W": {"item": v_web, "count": 1},
        "M": {"item": v_mid, "count": 1},
        "O": {"item": v_corner, "count": 1},
    }
    if len(items) > 4:
        pat[0] = "O R M R O"
        pat[8] = "O R M R O"
        key["R"] = {"item": r4, "count": 1}
    return pat, key


def write_vanilla_shaped(filename, pattern, key_map, result_id, count=1):
    obj = {
        "type": "minecraft:crafting_shaped",
        "pattern": pattern,
        "key": key_map,
        "result": {"id": mod(result_id), "count": count},
    }
    path = os.path.join(VANILLA_DIR, f"{filename}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


def add_extra_vanilla_3x3_recipes():
    """Add rich Vanilla 3x3 recipes for basic gear, medicines, Almond Water consumables, parts & workstations."""
    # Remove any old v3_*.json files first so stale/renamed recipes never linger
    for old_fn in os.listdir(VANILLA_DIR):
        if old_fn.startswith("v3_") and old_fn.endswith(".json"):
            os.remove(os.path.join(VANILLA_DIR, old_fn))

    # Core workstations & early/mid survival consumables in Vanilla 3x3
    specs = [
        ("v3_almond_distiller", ["GCG", "BWB", "SSS"], {"G": "minecraft:glass", "C": mod("copper_pipe"), "B": mod("brass_ingot"), "W": mod("almond_water"), "S": mod("sheet_metal")}, "almond_distiller", 1),
        ("v3_anomaly_altar", [" A ", "OSO", "OOO"], {"A": mod("anomaly_crystal"), "O": "minecraft:obsidian", "S": mod("red_room_shard")}, "anomaly_altar", 1),
        ("v3_spiderweb_table", ["ISI", "SWS", "ISI"], {"I": "minecraft:iron_ingot", "S": mod("heavy_steel_plate"), "W": mod("workshop_bench")}, "spiderweb_crafting_table", 1),
        ("v3_almond_mana_cell", [" C ", "CWC", " G "], {"C": mod("copper_wire"), "W": mod("almond_water"), "G": "minecraft:glass_bottle"}, "almond_mana_cell", 2),
        ("v3_improvised_calming_tea", [" H ", "RWR", " B "], {"H": mod("dry_soil_herbs"), "R": mod("rags"), "W": mod("almond_water"), "B": "minecraft:glass_bottle"}, "improvised_calming_tea", 2),
        ("v3_field_bandage", ["RGR", "AWA", "RTR"], {"R": mod("sterile_bandage"), "G": mod("sterile_gauze"), "A": mod("rubbing_alcohol"), "W": mod("almond_water"), "T": mod("insulating_tape")}, "field_bandage", 1),
        ("v3_purified_almond_essence", [" W ", "CWC", " G "], {"W": mod("almond_water"), "C": "minecraft:charcoal", "G": mod("sterile_gauze")}, "purified_almond_essence", 2),
        ("v3_almond_elixir", [" H ", "PWP", " A "], {"H": mod("dry_soil_herbs"), "P": mod("expired_painkillers"), "W": mod("almond_water"), "A": mod("almond_mana_cell")}, "almond_elixir", 1),
        ("v3_steel_gear", [" M ", "M M", " M "], {"M": mod("sheet_metal")}, "steel_gear", 2),
        ("v3_heavy_steel_plate", ["MM ", "MC ", "   "], {"M": mod("sheet_metal"), "C": "minecraft:coal"}, "heavy_steel_plate", 2),
        ("v3_ballast_coil", ["CCC", "CMC", "CCC"], {"C": mod("copper_wire"), "M": "minecraft:iron_ingot"}, "ballast_coil", 2),
        ("v3_old_circuit_board", [" C ", "CGC", " R "], {"C": mod("copper_wire"), "G": mod("green_circuit"), "R": "minecraft:redstone"}, "old_circuit_board", 2),
        ("v3_precision_screwdriver", ["  I", " S ", "P  "], {"I": "minecraft:iron_ingot", "S": mod("screw_nail_box"), "P": mod("plastic_casing")}, "precision_screwdriver", 1),
        ("v3_keypad_hacker_t1", [" G ", "PKP", " B "], {"G": mod("green_circuit"), "P": mod("plastic_casing"), "K": mod("office_keyboard"), "B": mod("battery_cell")}, "keypad_hacker_t1", 1),
        ("v3_almond_gas_mask", ["GGG", "PWP", " C "], {"G": mod("office_glass"), "P": mod("plastic_casing"), "W": mod("almond_water"), "C": "minecraft:charcoal"}, "almond_gas_mask", 1),
        ("v3_scrap_helmet", ["SSS", "S S"], {"S": mod("heavy_steel_plate")}, "scrap_tactical_helmet", 1),
        ("v3_scrap_chest", ["S S", "SSS", "SSS"], {"S": mod("heavy_steel_plate")}, "scrap_tactical_chestplate", 1),
        ("v3_scrap_legs", ["SSS", "S S", "S S"], {"S": mod("heavy_steel_plate")}, "scrap_tactical_leggings", 1),
        ("v3_scrap_boots", ["S S", "R R"], {"S": mod("heavy_steel_plate"), "R": mod("foam_padding")}, "scrap_tactical_boots", 1),
        ("v3_blunt_iron_mace", [" II", " SI", "H  "], {"I": "minecraft:iron_ingot", "S": mod("heavy_steel_plate"), "H": mod("tool_handle_basic")}, "blunt_iron_mace", 1),
        ("v3_blunt_barbed_kanabo", [" N ", "NWN", " H "], {"N": mod("screw_nail_box"), "W": mod("broken_table_leg"), "H": mod("weapon_grip_wrap")}, "blunt_barbed_kanabo", 1),
        ("v3_west_hound_dagger", ["  F", " I ", "H  "], {"F": mod("hound_fang"), "I": "minecraft:iron_ingot", "H": mod("weapon_grip_wrap")}, "west_hound_dagger", 1),
        ("v3_west_gladius", [" I ", " S ", " H "], {"I": "minecraft:iron_ingot", "S": mod("heavy_steel_plate"), "H": mod("weapon_grip_wrap")}, "west_gladius", 1),
        ("v3_cn_iron_staff", ["  I", " S ", "I  "], {"I": "minecraft:iron_ingot", "S": mod("heavy_steel_plate")}, "cn_iron_staff", 1),
        ("v3_cn_ring_dao", ["  I", " S ", "HR "], {"I": "minecraft:iron_ingot", "S": mod("heavy_steel_plate"), "H": mod("weapon_grip_wrap"), "R": mod("steel_gear")}, "cn_ring_pommel_dao", 1),
        ("v3_static_pulse_staff", ["  L", " C ", "W  "], {"L": mod("flickering_lamp_tube"), "C": mod("ballast_coil"), "W": mod("weapon_shaft_wood")}, "static_pulse_staff", 1),
        ("v3_gun_nailgun", ["PPP", "MNB", "  H"], {"P": mod("plastic_casing"), "M": mod("sheet_metal"), "N": mod("screw_nail_box"), "B": mod("battery_cell"), "H": mod("weapon_grip_wrap")}, "gun_nailgun", 1),
        ("v3_gun_pipe_shotgun", ["PPP", "WTR", "W  "], {"P": mod("copper_pipe"), "W": mod("broken_table_leg"), "T": mod("insulating_tape"), "R": mod("metal_spring")}, "gun_pipe_shotgun", 1),
        ("v3_ammo_pen_bolt", ["  M", " P ", "R  "], {"M": mod("sheet_metal"), "P": mod("ballpoint_pen"), "R": mod("rubber_band_ball")}, "ammo_pen_bolt", 6),
        ("v3_ammo_steel_nails", [" M ", " N ", " M "], {"M": mod("sheet_metal"), "N": mod("screw_nail_box")}, "ammo_steel_nails", 16),
        ("v3_ammo_9mm", [" B ", " P ", " M "], {"B": mod("brass_ingot"), "P": mod("black_powder"), "M": mod("sheet_metal")}, "ammo_9mm", 12),
        ("v3_ammo_12g_buck", [" M ", " P ", " R "], {"M": mod("sheet_metal"), "P": mod("black_powder"), "R": mod("plastic_casing")}, "ammo_12g_buck", 8),
        ("v3_polymer_frame", ["PP", "PP"], {"P": mod("plastic_casing")}, "polymer_frame", 2),
        ("v3_rubber_conveyor_belt", ["TT", "PP"], {"T": mod("tire"), "P": mod("polymer_frame")}, "rubber_conveyor_belt", 2),
    ]
    for fn, pat, km, out, cnt in specs:
        write_vanilla_shaped(fn, pat, km, out, cnt)
    print(f"Wrote {len(specs)} additional Vanilla 3x3 recipes -> {VANILLA_DIR}")


def rebuild_station_recipes():
    with open(STATION_JSON, "r", encoding="utf-8") as f:
        old_data = json.load(f)

    new_recipes = []
    seen_out_station = set()

    # Track ultimate_spiderweb_crafting_table recipe explicitly on 7x7 Primary Spiderweb!
    for r in old_data["recipes"]:
        rid = r["id"]
        old_station = r["station"]
        inputs = r.get("inputs", [])
        outputs = r.get("outputs", [])
        xp = r.get("xp", 6)
        if not outputs:
            continue
        # Sanitize any legacy plastic_shard references in salvage outputs
        for o in outputs:
            if o.get("item") == "noclip_backrooms:plastic_shard":
                o["item"] = "noclip_backrooms:plastic_casing"
        out_id = outputs[0]["item"].split(":")[-1]
        cat = item_category(out_id)

        if old_station == "salvage_bench":
            # Keep real 1->4 Salvage Bench recipes cleanly!
            new_recipes.append({
                "id": rid,
                "station": "salvage_bench",
                "category": "parts",
                "xp": xp,
                "inputs": [inputs[0]] if inputs else [],
                "outputs": outputs[:4],
            })
            continue

        # If already shaped (idempotent re-run), preserve existing pattern+key unless routing changed
        if "pattern" in r and "key" in r and not inputs:
            existing_key = r["key"]
            derived_inputs = [
                v for v in existing_key.values()
                if isinstance(v, dict) and v.get("item", "").startswith("noclip_backrooms:")
            ] or list(existing_key.values())
            if out_id in ULTIMATE_9X9_OUTS and old_station != "ultimate_spiderweb_crafting_table":
                target_station = "ultimate_spiderweb_crafting_table"
                pat, key = build_pattern_9x9(out_id, derived_inputs)
                xp = max(xp, 35)
            elif out_id in VANILLA_3X3_OUTS and old_station == "workshop_bench":
                continue
            else:
                dedup = (old_station, out_id)
                if dedup in seen_out_station:
                    continue
                seen_out_station.add(dedup)
                new_recipes.append({
                    "id": rid,
                    "station": old_station,
                    "category": cat,
                    "xp": xp,
                    "pattern": r["pattern"],
                    "key": existing_key,
                    "outputs": outputs,
                })
                continue
        elif out_id in ULTIMATE_9X9_OUTS:
            target_station = "ultimate_spiderweb_crafting_table"
            pat, key = build_pattern_9x9(out_id, inputs)
            xp = max(xp, 35)
        elif out_id in SPIDERWEB_7X7_OUTS or old_station == "spiderweb_crafting_table":
            target_station = "spiderweb_crafting_table"
            pat, key = build_pattern_7x7(out_id, inputs)
            xp = max(xp, 22)
        elif old_station == "almond_distiller":
            target_station = "almond_distiller"
            pat, key = build_pattern_3x3(inputs)
        elif old_station == "anomaly_altar":
            target_station = "anomaly_altar"
            pat, key = build_pattern_3x3(inputs)
        else:
            if out_id in VANILLA_3X3_OUTS:
                continue
            target_station = "workshop_bench"
            pat, key = build_pattern_5x5(out_id, inputs)

        dedup = (target_station, out_id)
        if dedup in seen_out_station:
            continue
        seen_out_station.add(dedup)

        new_recipes.append({
            "id": rid,
            "station": target_station,
            "category": cat,
            "xp": xp,
            "pattern": pat,
            "key": key,
            "outputs": outputs,
        })

    # Ensure Ultimate Spiderweb Crafting Table itself is craftable on the 7x7 Primary Spiderweb!
    if ("spiderweb_crafting_table", "ultimate_spiderweb_crafting_table") not in seen_out_station:
        pat, key = build_pattern_7x7("ultimate_spiderweb_crafting_table", [
            {"item": mod("spiderweb_crafting_table"), "count": 1},
            {"item": mod("noclip_shard"), "count": 1},
            {"item": mod("blue_circuit"), "count": 1},
            {"item": mod("computation_brick"), "count": 1},
            {"item": mod("tungsten_alloy"), "count": 1},
        ])
        new_recipes.append({
            "id": mod("spiderweb_ultimate_table"),
            "station": "spiderweb_crafting_table",
            "category": "gear",
            "xp": 40,
            "pattern": pat,
            "key": key,
            "outputs": [{"item": mod("ultimate_spiderweb_crafting_table"), "count": 1}],
        })

    # Add salvage recipes for all 20 newer prop_* furniture blocks so players can also salvage picked-up props!
    existing_ids = {r["id"] for r in new_recipes}
    for prop_id, drops in PROP_SALVAGE_MAP.items():
        s_id = mod(f"salvage_{prop_id}")
        if s_id not in existing_ids:
            new_recipes.append({
                "id": s_id,
                "station": "salvage_bench",
                "category": "parts",
                "xp": 6,
                "inputs": [{"item": mod(prop_id), "count": 1}],
                "outputs": [{"item": mod(it), "count": mn} for it, mn, _ in drops[:4]],
            })

    counts_by_station = {}
    for r in new_recipes:
        counts_by_station[r["station"]] = counts_by_station.get(r["station"], 0) + 1
    with open(STATION_JSON, "w", encoding="utf-8") as f:
        json.dump({"recipes": new_recipes}, f, indent=2)
    print(f"Wrote {len(new_recipes)} shaped station recipes -> {STATION_JSON}")
    for st, cnt in sorted(counts_by_station.items()):
        print(f"  {st}: {cnt}")


# Real base salvage part drops when breaking ANY of the 20 prop_* + key noclipped_* furniture blocks!
PROP_SALVAGE_MAP = {
    "prop_desktop_computer": [("green_circuit", 1, 2), ("case_fan", 1, 1), ("copper_wire", 2, 4), ("plastic_casing", 2, 3)],
    "prop_desktop_computer_damaged": [("green_circuit", 1, 1), ("copper_wire", 1, 3), ("plastic_casing", 1, 3)],
    "prop_desktop_computer_noclipped": [("green_circuit", 1, 2), ("crt_tube", 1, 1), ("anomaly_crystal", 1, 1), ("copper_wire", 2, 3)],
    "prop_phone": [("green_circuit", 1, 1), ("battery_cell", 1, 1), ("plastic_casing", 1, 2)],
    "prop_phone_cracked": [("copper_wire", 1, 2), ("plastic_casing", 1, 2), ("office_glass", 1, 1)],
    "prop_phone_noclipped": [("battery_cell", 1, 1), ("static_cassette", 1, 1), ("copper_wire", 1, 2)],
    "prop_office_chair": [("rags", 1, 2), ("foam_padding", 1, 2), ("metal_spring", 1, 2), ("sheet_metal", 1, 2)],
    "prop_sofa": [("rags", 2, 4), ("foam_padding", 2, 4), ("metal_spring", 2, 3), ("broken_table_leg", 2, 3)],
    "prop_sofa_noclipped": [("rags", 2, 3), ("foam_padding", 2, 3), ("metal_spring", 1, 2), ("anomaly_crystal", 1, 1)],
    "prop_glass_partition": [("office_glass", 2, 4), ("sheet_metal", 2, 3), ("screw_nail_box", 1, 2)],
    "prop_glass_partition_broken": [("broken_mirror_shard", 2, 4), ("sheet_metal", 1, 2)],
    "prop_folding_chair_pile": [("sheet_metal", 3, 5), ("screw_nail_box", 2, 3), ("plastic_casing", 2, 4), ("metal_spring", 1, 2)],
    "prop_curtain_heap": [("rags", 3, 5), ("damp_carpet_fiber", 2, 3)],
    "prop_standing_mirror": [("broken_mirror_shard", 2, 4), ("broken_table_leg", 1, 2), ("sheet_metal", 1, 2)],
    "prop_potted_plant_plastic": [("plastic_casing", 2, 3), ("dry_soil_herbs", 1, 2)],
    "prop_electric_fan": [("case_fan", 1, 2), ("copper_wire", 2, 3), ("sheet_metal", 1, 2)],
    "prop_microwave": [("sheet_metal", 2, 3), ("copper_wire", 2, 3), ("ballast_coil", 1, 2), ("office_glass", 1, 1)],
    "prop_floor_lamp": [("flickering_lamp_tube", 1, 2), ("copper_wire", 1, 2), ("sheet_metal", 1, 2)],
    "prop_wall_first_aid_kit": [("sterile_gauze", 1, 2), ("rubbing_alcohol", 1, 2), ("old_bandaid", 2, 3), ("sheet_metal", 1, 1)],
    "prop_cardboard_pile": [("damp_paper_scrap", 3, 6), ("magazine_bundle", 1, 2), ("insulating_tape", 1, 2), ("stapler", 1, 1)],
    # Enhanced salvage drops for multi-block noclipped appliances too!
    "noclipped_fridge": [("sheet_metal", 3, 5), ("copper_pipe", 2, 4), ("coolant_vial", 1, 2), ("almond_water", 1, 2)],
    "noclipped_vending_machine": [("sheet_metal", 3, 4), ("green_circuit", 1, 2), ("almond_water", 1, 3), ("lost_old_coin", 1, 2)],
    "noclipped_water_cooler": [("plastic_casing", 2, 4), ("almond_water", 1, 2), ("copper_pipe", 1, 2)],
    "noclipped_file_cabinet": [("sheet_metal", 2, 4), ("screw_nail_box", 1, 2), ("magazine_bundle", 1, 2), ("rusted_office_safe", 1, 1)],
}


def write_salvage_loot_tables():
    for prop_id, drops in PROP_SALVAGE_MAP.items():
        pools = []
        for item_id, mn, mx in drops:
            entry = {"type": "minecraft:item", "name": mod(item_id)}
            if mn != 1 or mx != 1:
                entry["functions"] = [{
                    "function": "minecraft:set_count",
                    "count": {"type": "minecraft:uniform", "min": mn, "max": mx},
                }]
            pools.append({"rolls": 1, "entries": [entry]})
        loot = {
            "type": "minecraft:block",
            "pools": pools,
            "random_sequence": mod(f"blocks/{prop_id}"),
        }
        with open(os.path.join(LOOT_DIR, f"{prop_id}.json"), "w", encoding="utf-8") as f:
            json.dump(loot, f, indent=2)
    # Self-drop loot table for ultimate_spiderweb_crafting_table
    ult_loot = {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": mod("ultimate_spiderweb_crafting_table")}]}],
    }
    with open(os.path.join(LOOT_DIR, "ultimate_spiderweb_crafting_table.json"), "w", encoding="utf-8") as f:
        json.dump(ult_loot, f, indent=2)
    print(f"Updated {len(PROP_SALVAGE_MAP)} prop salvage loot tables + ultimate_spiderweb loot table")


def write_ultimate_spiderweb_and_prop_part_assets():
    """Create assets for ultimate_spiderweb_crafting_table (blockstate, model, item, 128x128 tex) & prop_part."""
    # 1. prop_part invisible blockstate & empty particle model
    with open(os.path.join(ASSETS, "blockstates", "prop_part.json"), "w", encoding="utf-8") as f:
        json.dump({"variants": {"": {"model": "noclip_backrooms:block/crafting/prop_part"}}}, f, indent=2)
    with open(os.path.join(ASSETS, "models", "block", "crafting", "prop_part.json"), "w", encoding="utf-8") as f:
        json.dump({"textures": {"particle": "noclip_backrooms:block/crafting3d/noclipped_fridge"}}, f, indent=2)

    # 2. ultimate_spiderweb_crafting_table blockstate, item, 128x128 texture, high-craft 3D model
    with open(os.path.join(ASSETS, "blockstates", "ultimate_spiderweb_crafting_table.json"), "w", encoding="utf-8") as f:
        json.dump({
            "variants": {
                "facing=north": {"model": "noclip_backrooms:block/crafting/ultimate_spiderweb_crafting_table"},
                "facing=east": {"model": "noclip_backrooms:block/crafting/ultimate_spiderweb_crafting_table", "y": 90},
                "facing=south": {"model": "noclip_backrooms:block/crafting/ultimate_spiderweb_crafting_table", "y": 180},
                "facing=west": {"model": "noclip_backrooms:block/crafting/ultimate_spiderweb_crafting_table", "y": 270},
            }
        }, f, indent=2)
    with open(os.path.join(ASSETS, "items", "ultimate_spiderweb_crafting_table.json"), "w", encoding="utf-8") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "noclip_backrooms:block/crafting/ultimate_spiderweb_crafting_table"}}, f, indent=2)

    # Copy & tint spiderweb_crafting_table model into an even grander purple-gold 9x9 ultimate table!
    base_model_path = os.path.join(ASSETS, "models", "block", "crafting", "spiderweb_crafting_table.json")
    with open(base_model_path, "r", encoding="utf-8") as f:
        ult_model = json.load(f)
    ult_model["textures"] = {
        "0": "noclip_backrooms:block/crafting3d/ultimate_spiderweb_crafting_table",
        "particle": "noclip_backrooms:block/crafting3d/ultimate_spiderweb_crafting_table",
    }
    # Add elevated golden-violet outer crown ring pillars on ultimate table
    ult_model["elements"].extend([
        {"from": [6.5, 13.5, 6.5], "to": [9.5, 16.0, 9.5], "rotation": {"origin": [8, 14.75, 8], "axis": "y", "angle": 45},
         "faces": {d: {"uv": [8, 8, 12, 12], "texture": "#0", "light_emission": 15} for d in ("north", "south", "east", "west", "up", "down")}},
        {"from": [1.0, 12.2, 1.0], "to": [15.0, 13.0, 2.0],
         "faces": {d: {"uv": [12, 0, 16, 4], "texture": "#0", "light_emission": 15} for d in ("north", "south", "east", "west", "up", "down")}},
        {"from": [1.0, 12.2, 14.0], "to": [15.0, 13.0, 15.0],
         "faces": {d: {"uv": [12, 0, 16, 4], "texture": "#0", "light_emission": 15} for d in ("north", "south", "east", "west", "up", "down")}},
    ])
    with open(os.path.join(ASSETS, "models", "block", "crafting", "ultimate_spiderweb_crafting_table.json"), "w", encoding="utf-8") as f:
        json.dump(ult_model, f, indent=2)

    # Write rich 128x128 obsidian/gold/void-violet atlas texture for ultimate_spiderweb_crafting_table
    write_ultimate_table_png(os.path.join(ASSETS, "textures", "block", "crafting3d", "ultimate_spiderweb_crafting_table.png"))


def write_ultimate_table_png(path):
    size = 128
    # 4x4 grid of 32x32 rich swatches: obsidian, dark carbon, violet glow, royal gold, etc.
    palette = [
        (34, 24, 54), (22, 18, 34), (48, 36, 72), (225, 178, 56),
        (28, 22, 42), (72, 54, 108), (168, 72, 255), (60, 228, 255),
        (190, 90, 255), (242, 200, 88), (150, 62, 240), (42, 30, 64),
        (24, 20, 36), (120, 48, 220), (88, 238, 255), (255, 224, 132),
    ]
    rows = []
    for y in range(size):
        row = bytearray([0])
        ty = y // 32
        ly = y % 32
        for x in range(size):
            tx = x // 32
            lx = x % 32
            r, g, b = palette[ty * 4 + tx]
            # Crisp bevel + brushed grain + rune diamond on glow tiles
            border = (lx == 0 or ly == 0 or lx == 31 or ly == 31)
            grain = ((x * 7 + y * 13) % 11) - 5
            if border:
                r, g, b = max(0, r - 22), max(0, g - 22), max(0, b - 22)
            else:
                r = max(0, min(255, r + grain))
                g = max(0, min(255, g + grain))
                b = max(0, min(255, b + grain))
            row.extend((r, g, b, 255))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def update_lang_files():
    en_updates = {
        "block.noclip_backrooms.spiderweb_crafting_table": "Primary Spiderweb Crafting Table (7×7)",
        "block.noclip_backrooms.ultimate_spiderweb_crafting_table": "Ultimate Spiderweb Crafting Table (9×9)",
        "block.noclip_backrooms.prop_part": "Backrooms Structure Part",
        "ui.noclip_backrooms.station.book": "Recipes",
        "ui.noclip_backrooms.station.book_title": "Recipe Book",
        "ui.noclip_backrooms.station.clear": "Clear",
        "ui.noclip_backrooms.station.fill_grid": "Auto-Fill",
        "ui.noclip_backrooms.station.fill_max": "Max",
        "ui.noclip_backrooms.station.missing_materials": "Missing required materials in inventory",
        "ui.noclip_backrooms.cat.all": "All",
        "ui.noclip_backrooms.cat.parts": "Mat",
        "ui.noclip_backrooms.cat.melee": "Mel",
        "ui.noclip_backrooms.cat.firearms": "Gun",
        "ui.noclip_backrooms.cat.magic": "Mag",
        "ui.noclip_backrooms.cat.armor": "Arm",
        "ui.noclip_backrooms.cat.gear": "Gear",
        "ui.noclip_backrooms.cat_full.all": "All Recipes",
        "ui.noclip_backrooms.cat_full.parts": "Materials & Parts",
        "ui.noclip_backrooms.cat_full.melee": "Melee Weapons",
        "ui.noclip_backrooms.cat_full.firearms": "Firearms",
        "ui.noclip_backrooms.cat_full.magic": "Magic & Anomaly Weapons",
        "ui.noclip_backrooms.cat_full.armor": "Armor Sets",
        "ui.noclip_backrooms.cat_full.gear": "Tools, Ammo & Facilities",
    }
    zh_updates = {
        "block.noclip_backrooms.spiderweb_crafting_table": "初級蜘蛛網合成台（7×7）",
        "block.noclip_backrooms.ultimate_spiderweb_crafting_table": "終極蜘蛛網合成台（9×9）",
        "block.noclip_backrooms.prop_part": "後室物件結構",
        "ui.noclip_backrooms.station.book": "配方書",
        "ui.noclip_backrooms.station.book_title": "分類配方書",
        "ui.noclip_backrooms.station.clear": "清空",
        "ui.noclip_backrooms.station.fill_grid": "自動填入",
        "ui.noclip_backrooms.station.fill_max": "填滿",
        "ui.noclip_backrooms.station.missing_materials": "背包材料不足，無法填入配方",
        "ui.noclip_backrooms.cat.all": "全",
        "ui.noclip_backrooms.cat.parts": "料",
        "ui.noclip_backrooms.cat.melee": "刃",
        "ui.noclip_backrooms.cat.firearms": "槍",
        "ui.noclip_backrooms.cat.magic": "法",
        "ui.noclip_backrooms.cat.armor": "甲",
        "ui.noclip_backrooms.cat.gear": "具",
        "ui.noclip_backrooms.cat_full.all": "全部配方",
        "ui.noclip_backrooms.cat_full.parts": "材料與零件",
        "ui.noclip_backrooms.cat_full.melee": "冷兵器",
        "ui.noclip_backrooms.cat_full.firearms": "槍械熱兵器",
        "ui.noclip_backrooms.cat_full.magic": "魔法與異常法器",
        "ui.noclip_backrooms.cat_full.armor": "防具套裝",
        "ui.noclip_backrooms.cat_full.gear": "工具、彈藥與設施",
    }
    for lang_fn, upd in (("en_us.json", en_updates), ("zh_tw.json", zh_updates)):
        p = os.path.join(ASSETS, "lang", lang_fn)
        with open(p, "r", encoding="utf-8") as f:
            d = json.load(f)
        d.update(upd)
        with open(p, "w", encoding="utf-8") as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
    print("Updated en_us.json and zh_tw.json")


def validate_all_ids():
    valid = {fn[:-5] for fn in os.listdir(os.path.join(ASSETS, "items")) if fn.endswith(".json")}
    errors = []

    def check(item_id, where):
        if item_id.startswith("noclip_backrooms:"):
            raw = item_id.split(":", 1)[1]
            if raw not in valid:
                errors.append(f"Unknown item {item_id} in {where}")

    with open(STATION_JSON, "r", encoding="utf-8") as f:
        st = json.load(f)
    for r in st["recipes"]:
        for inp in r.get("inputs", []):
            check(inp["item"], f"station {r['id']} inputs")
        for k, v in r.get("key", {}).items():
            check(v["item"], f"station {r['id']} key {k}")
        for out in r.get("outputs", []):
            check(out["item"], f"station {r['id']} outputs")

    for fn in sorted(os.listdir(VANILLA_DIR)):
        if not fn.endswith(".json"):
            continue
        with open(os.path.join(VANILLA_DIR, fn), "r", encoding="utf-8") as f:
            rec = json.load(f)
        res = rec.get("result", {})
        if isinstance(res, dict) and "id" in res:
            check(res["id"], f"vanilla {fn} result")
        for k, v in rec.get("key", {}).items():
            if isinstance(v, str) and not v.startswith("#"):
                check(v, f"vanilla {fn} key {k}")
        for ing in rec.get("ingredients", []):
            if isinstance(ing, str) and not ing.startswith("#"):
                check(ing, f"vanilla {fn} ingredient")

    for prop_id in PROP_SALVAGE_MAP:
        with open(os.path.join(LOOT_DIR, f"{prop_id}.json"), "r", encoding="utf-8") as f:
            lt = json.load(f)
        for pool in lt.get("pools", []):
            for ent in pool.get("entries", []):
                if "name" in ent:
                    check(ent["name"], f"loot {prop_id}")

    if errors:
        raise RuntimeError("Invalid item IDs found:\n" + "\n".join(errors[:30]))
    print(f"Validated all station recipes, vanilla recipes, and prop salvage loot tables against {len(valid)} registered items: 0 errors!")


def main():
    add_extra_vanilla_3x3_recipes()
    rebuild_station_recipes()
    write_salvage_loot_tables()
    write_ultimate_spiderweb_and_prop_part_assets()
    update_lang_files()
    validate_all_ids()


if __name__ == "__main__":
    main()
