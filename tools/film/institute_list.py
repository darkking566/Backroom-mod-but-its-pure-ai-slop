"""The M.E.G. institute's objects (docs/企劃書/1-遊戲玩法/README.md, "研究所改版"), after the research facility at the
end of the film (A24's Backrooms, 2026): a gurney and equipment wrapped in plastic, a steel interview table with a blue
folder, an X-ray viewer, hazmat suits, CRT consoles, a decontamination shower, and the machine that sends people in.

One row each: id, size (width, height, depth in blocks), mount, material, Chinese name, English name. Mounts as in
film_list.py. The building's generator places them (frontrooms/InstituteLayout, plans from tools/make_institute_plans.py).
"""

PROPS = [
    # The receiving room and the medical wing
    ("inst_gurney", (1, 1, 2), "multi", "medical", "擔架床", "Hospital Gurney"),
    ("inst_wrapped_equipment", (1, 2, 1), "multi", "plastic", "包著塑膠布的器材", "Equipment Wrapped in Plastic"),
    ("inst_interview_table", (2, 1, 1), "multi", "metal", "詢問室鐵桌", "Interview Table"),
    ("inst_stacking_chair", (1, 1, 1), "floor", "metal", "鐵管塑膠椅", "Stacking Chair"),
    ("inst_waiting_chairs", (3, 1, 1), "multi", "metal", "候診連排椅", "Waiting Room Chairs"),
    ("inst_xray_viewer", (2, 1, 1), "wall", "electronics", "X 光看片燈箱", "X-Ray Viewer"),
    ("inst_iv_stand", (1, 2, 1), "multi", "medical", "點滴架", "IV Stand"),
    ("inst_wheelchair", (1, 1, 1), "floor", "medical", "輪椅", "Wheelchair"),
    ("inst_water_fountain", (1, 1, 1), "wall", "metal", "壁掛飲水機", "Wall Drinking Fountain"),
    # Decontamination and the suits
    ("inst_decon_shower", (1, 2, 1), "multi", "metal", "除汙淋浴間", "Decontamination Shower"),
    ("inst_hazmat_rack", (2, 2, 1), "multi", "hazmat", "防護衣吊架", "Hazmat Suit Rack"),
    # Offices, control rooms, labs
    ("inst_monitor_console", (2, 2, 1), "multi", "electronics", "CRT 監視台", "CRT Monitor Console"),
    ("inst_office_desk", (2, 1, 1), "multi", "metal", "鐵製辦公桌與電腦", "Steel Desk with Computer"),
    ("inst_file_cabinet", (1, 2, 1), "multi", "metal", "四層檔案櫃", "Four-Drawer File Cabinet"),
    ("inst_tape_drive", (1, 2, 1), "multi", "electronics", "磁帶機櫃", "Tape Drive Cabinet"),
    ("inst_lab_bench", (2, 1, 1), "multi", "lab", "實驗桌", "Lab Bench"),
    ("inst_specimen_tank", (1, 2, 1), "multi", "lab", "標本槽", "Specimen Tank"),
    # B1
    ("inst_deployment_gate", (3, 4, 2), "multi", "machine", "深層傳送裝置", "Deep Deployment Gate"),
]

# What each material gives when it is broken (only outside the institute: inside, M.E.G.'s things cannot be broken).
DROPS = {
    "medical": [("scrap_metal", 1, 3, 1.0), ("rags", 1, 2, 1.0), ("sterile_gauze", 1, 1, 0.3)],
    "plastic": [("plastic_casing", 1, 3, 1.0), ("scrap_metal", 1, 2, 0.6)],
    "metal": [("scrap_metal", 1, 3, 1.0), ("rusty_screws", 1, 2, 0.8)],
    "electronics": [("crt_tube", 1, 1, 0.5), ("green_circuit", 1, 2, 1.0), ("copper_wire", 1, 3, 1.0), ("plastic_casing", 1, 2, 1.0)],
    "hazmat": [("rags", 2, 4, 1.0), ("plastic_casing", 1, 2, 1.0)],
    "lab": [("glass_tube_shard", 1, 3, 1.0), ("scrap_metal", 1, 2, 1.0), ("green_circuit", 0, 1, 1.0)],
    "machine": [("copper_wire", 4, 8, 1.0), ("green_circuit", 2, 4, 1.0), ("sheet_metal", 2, 4, 1.0), ("scrap_metal", 3, 6, 1.0)],
}

TOOL = {"medical": "pickaxe", "metal": "pickaxe", "electronics": "pickaxe", "lab": "pickaxe", "machine": "pickaxe"}

# Light given off (screens, lit viewers, the tanks' glow, the gate).
LIGHT = {"inst_xray_viewer": 8, "inst_monitor_console": 5, "inst_tape_drive": 3, "inst_specimen_tank": 6, "inst_deployment_gate": 10}
