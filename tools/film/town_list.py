"""The staff town's objects in the Frontrooms (docs/project/1-gameplay/README.md, "前室的夜晚：398"): a street of
houses for M.E.G.'s people, a park, a corner store. After 狐霖's Frontrooms ("Earth" by day, Level 398 by night): the
mailbox full of letters, the picnic in the park, the clock that runs slow, the swings.

One row each: id, size (width, height, depth in blocks), mount, material, Chinese name, English name (as film_list.py).
"""

PROPS = [
    ("town_mailbox", (1, 1, 1), "floor", "metal", "路邊信箱", "Curbside Mailbox"),
    ("town_picnic_table", (2, 1, 2), "multi", "wood", "野餐桌", "Picnic Table"),
    ("town_park_bench", (2, 1, 1), "multi", "wood", "公園長椅", "Park Bench"),
    ("town_swing_set", (3, 2, 1), "multi", "metal", "鞦韆", "Swing Set"),
    ("town_street_clock", (1, 3, 1), "multi", "metal", "路邊時鐘", "Street Clock"),
    ("town_fire_hydrant", (1, 1, 1), "small", "metal", "消防栓", "Fire Hydrant"),
    ("town_trash_can", (1, 1, 1), "small", "metal", "公園垃圾桶", "Park Trash Can"),
    ("town_store_shelf", (1, 2, 1), "multi", "store", "雜貨店貨架", "Corner Store Shelf"),
    ("town_store_sign", (5, 1, 1), "wall", "store", "雜貨店招牌", "Corner Store Sign"),
]

DROPS = {
    "metal": [("scrap_metal", 1, 3, 1.0), ("rusty_screws", 1, 2, 0.8)],
    "wood": [("broken_table_leg", 1, 3, 1.0), ("rotten_wood_strip", 2, 4, 1.0), ("screw_nail_box", 1, 1, 0.4)],
    "store": [("rotten_wood_strip", 1, 3, 1.0), ("expired_canned_food", 0, 2, 1.0)],
}

TOOL = {"metal": "pickaxe", "wood": "axe", "store": "axe"}

LIGHT = {"town_street_clock": 6}
