"""The film props: one row each, shared by the data generator and checked against FilmProps.java by FilmClientTest.

id, size (width, height, depth in blocks), mount, material (drops, sound, tool), Chinese name, English name.

Mounts: multi = a multi-block prop (every cell solid); floor = one solid block; small = a low solid box on the floor;
low = flat on the floor, walked over (tapes, rugs, a bare mattress); wall = hung on a wall, its back against it;
ceiling = hung from the ceiling (signs, the chandelier).
"""

PROPS = [
    # The showroom's seating
    ("film_sofa_tan", (3, 1, 1), "multi", "upholstered", "棕褐色沙發", "Tan Overstuffed Sofa"),
    ("film_sofa_brown", (3, 1, 1), "multi", "upholstered", "深棕色沙發", "Chocolate Sofa"),
    ("film_sofa_tropical", (3, 1, 1), "multi", "upholstered", "藍灰葉紋沙發", "Blue Leaf-Print Sofa"),
    ("film_sofa_quilted", (3, 1, 1), "multi", "upholstered", "米白絎縫沙發", "Cream Quilted Sofa"),
    ("film_loveseat_floral", (2, 1, 1), "multi", "upholstered", "粉彩花布雙人沙發", "Floral Loveseat"),
    ("film_recliner_leather", (1, 1, 1), "floor", "upholstered", "棕色皮革躺椅", "Leather Recliner"),
    ("film_recliner_mauve", (1, 1, 1), "floor", "upholstered", "藕紫色躺椅", "Mauve Recliner"),
    ("film_recliner_tan", (1, 1, 1), "floor", "upholstered", "卡其色躺椅", "Tan Recliner"),
    ("film_recliner_bluegrey", (1, 1, 1), "floor", "upholstered", "灰藍色躺椅", "Blue-Grey Recliner"),
    ("film_swivel_chair_teal", (1, 1, 1), "floor", "upholstered", "孔雀藍旋轉椅", "Teal Swivel Chair"),
    ("film_armchair_wood_trim", (1, 1, 1), "floor", "upholstered", "木框扶手椅", "Wood-Trim Armchair"),
    ("film_accent_chair_diamond", (1, 1, 1), "floor", "upholstered", "菱格紋單椅", "Diamond Accent Chair"),
    ("film_ottoman_diamond", (1, 1, 1), "small", "upholstered", "菱格紋腳凳", "Diamond Ottoman"),
    ("film_ottoman_tufted", (1, 1, 1), "small", "upholstered", "拉扣腳凳", "Tufted Ottoman"),
    ("film_ladderback_chair", (1, 1, 1), "floor", "wood", "梯背餐椅", "Ladder-Back Chair"),
    ("film_school_chair", (1, 1, 1), "floor", "metal", "學校椅", "School Chair"),
    ("film_resin_chair", (1, 1, 1), "floor", "plastic", "白色塑膠庭院椅", "White Resin Chair"),
    ("film_wicker_chair", (1, 1, 1), "floor", "wood", "白藤椅", "White Wicker Chair"),
    ("film_throne", (1, 2, 1), "multi", "wood", "金色寶座", "Gilded Throne"),
    ("film_five_legged_stool", (1, 1, 1), "floor", "wood", "五腳凳", "Five-Legged Stool"),
    ("film_bistro_set", (2, 1, 2), "multi", "metal", "鑄鐵咖啡桌椅組", "Wrought-Iron Bistro Set"),
    # Tables and storage
    ("film_dining_set", (2, 1, 2), "multi", "wood", "八角餐桌組", "Octagonal Dining Set"),
    ("film_coffee_table_glass", (1, 1, 1), "small", "wood", "玻璃茶几", "Glass-Top Coffee Table"),
    ("film_tv_tray", (1, 1, 1), "floor", "metal", "電視折疊餐盤架", "TV Tray Table"),
    ("film_end_table_lamp", (1, 2, 1), "multi", "lamp", "邊桌與檯燈", "End Table with Lamp"),
    ("film_armoire", (1, 2, 1), "multi", "wood", "橡木衣櫥", "Oak Armoire"),
    ("film_entertainment_center", (2, 2, 1), "multi", "electronics", "橡木電視櫃", "Oak Entertainment Center"),
    ("film_bookcase_hutch", (1, 2, 1), "multi", "wood", "櫻桃木書櫃", "Cherry Bookcase Hutch"),
    ("film_dresser", (2, 2, 1), "multi", "wood", "五斗櫃與鏡子", "Dresser with Mirror"),
    ("film_nightstand", (1, 1, 1), "floor", "lamp", "床頭櫃與檯燈", "Nightstand with Lamp"),
    ("film_checkout_counter", (2, 1, 1), "multi", "register", "收銀台", "Checkout Counter"),
    # Beds
    ("film_showroom_bed", (2, 2, 2), "multi", "upholstered", "展示床", "Showroom Bed"),
    ("film_bare_mattress", (1, 1, 2), "low", "upholstered", "床墊", "Bare Mattress"),
    ("film_mattress_stack", (2, 2, 2), "multi", "upholstered", "床墊堆", "Mattress Stack"),
    # Lights, decor, the store
    ("film_torchiere", (1, 2, 1), "multi", "lamp", "上照式落地燈", "Torchiere Lamp"),
    ("film_chandelier", (1, 1, 1), "ceiling", "lamp", "黃銅吊燈", "Brass Chandelier"),
    ("film_watercolor_print", (1, 1, 1), "wall", "paper", "水彩海景畫", "Watercolor Print"),
    ("film_wicker_screen", (2, 2, 1), "multi", "wood", "藤編屏風", "Rattan Folding Screen"),
    ("film_carpet_rolls", (2, 3, 1), "multi", "carpet", "地毯捲", "Carpet Rolls"),
    ("film_cardboard_standee", (1, 2, 1), "multi", "paper", "紙板人形立牌", "Cardboard Standee"),
    ("film_ship_wheel", (1, 2, 1), "multi", "wood", "船舵", "Ship's Wheel"),
    ("film_coat_rack", (1, 2, 1), "multi", "wood", "衣帽架", "Coat Rack"),
    ("film_oriental_rug", (2, 1, 2), "low", "carpet", "東方地毯", "Oriental Rug"),
    ("film_patio_umbrella_set", (2, 3, 2), "multi", "plastic", "庭院遮陽傘桌椅", "Patio Umbrella Set"),
    ("film_sun_lounger", (1, 1, 2), "multi", "plastic", "庭院躺椅", "Sun Lounger"),
    ("film_sign_sale75", (1, 1, 1), "ceiling", "paper", "特價吊牌", "Hanging Sale Sign"),
    ("film_sign_must_go", (1, 1, 1), "ceiling", "paper", "全面出清吊牌", "Everything Must Go Sign"),
    ("film_sign_on_sale", (1, 1, 1), "ceiling", "paper", "促銷吊牌", "On Sale Sign"),
    ("film_banner_no_credit", (3, 1, 1), "wall", "paper", "免信用分期布條", "No Credit Needed Banner"),
    ("film_sign_pirate", (2, 2, 1), "wall", "paper", "海盜特價看板", "Pirate Prices Board"),
    # Piles
    ("film_furniture_pile", (3, 4, 3), "multi", "pile", "家具山", "Furniture Pile"),
    ("film_chair_pile", (2, 5, 2), "multi", "chair_pile", "頂到天花板的椅子山", "Chair Pile"),
    ("film_sofa_pile", (2, 3, 2), "multi", "upholstered", "沙發山", "Sofa Pile"),
    ("film_ottoman_pile", (2, 3, 2), "multi", "upholstered", "腳凳山", "Ottoman Pile"),
    ("film_box_stack", (1, 1, 1), "floor", "paper", "紙箱堆", "Box Stack"),
    # Electronics, mannequins, laundry
    ("film_fax_machine", (1, 1, 1), "small", "electronics", "傳真機", "Fax Machine"),
    ("film_tv_vcr", (1, 1, 1), "floor", "tv_vcr", "電視與錄放影機", "TV and VCR"),
    ("film_tv_wall", (2, 2, 1), "multi", "electronics", "電視牆", "Wall of TVs"),
    ("film_mannequin", (1, 2, 1), "multi", "plastic", "人體模特兒", "Mannequin"),
    ("film_mannequin_waving", (1, 2, 1), "multi", "plastic", "舉手的人體模特兒", "Waving Mannequin"),
    ("film_laundry_pile", (1, 1, 1), "small", "laundry", "衣服堆", "Laundry Pile"),
    ("film_laundry_basket", (1, 1, 1), "small", "laundry", "洗衣籃", "Laundry Basket"),
    # VHS tapes (pick up and sell)
    ("film_vhs_tape", (1, 1, 1), "low", "vhs_1", "錄影帶", "VHS Tape"),
    ("film_vhs_scatter", (1, 1, 1), "low", "vhs_scatter", "散落的錄影帶", "Scattered VHS Tapes"),
    ("film_vhs_pile_small", (1, 1, 1), "small", "vhs_small", "錄影帶小堆", "Small Pile of VHS Tapes"),
    ("film_vhs_pile_large", (2, 2, 2), "multi", "vhs_large", "錄影帶山", "Mound of VHS Tapes"),
    ("film_vhs_box", (1, 1, 1), "small", "vhs_box", "一箱錄影帶", "Box of VHS Tapes"),
    ("film_vhs_shelf", (1, 2, 1), "multi", "vhs_shelf", "錄影帶出租架", "VHS Rental Shelf"),
]

# The labels written on the tapes (lore line), Chinese and English.
VHS_LABELS = [
    ("家庭聖誕 '89", "FAMILY XMAS '89"),
    ("請勿覆蓋", "DO NOT TAPE OVER"),
    ("婚禮（拷貝）", "WEDDING (COPY)"),
    ("店內盤點 6/91", "STORE INVENTORY 6/91"),
    ("（沒有標籤）", "(no label)"),
    ("員工訓練影片 #3", "STAFF TRAINING #3"),
    ("地下室？？？", "BASEMENT ???"),
    ("週六早上的卡通", "Saturday cartoons"),
    ("家庭錄影", "HOME VIDEO"),
    ("1991 清倉廣告", "1991 CLEARANCE AD"),
    ("不要看", "DON'T WATCH"),
    ("生日", "Birthday"),
]

# What each material gives when it is broken: (item, min, max, chance).
DROPS = {
    "upholstered": [("rags", 2, 4, 1.0), ("foam_padding", 2, 4, 1.0), ("metal_spring", 1, 2, 0.7), ("broken_table_leg", 0, 2, 1.0)],
    "wood": [("broken_table_leg", 1, 3, 1.0), ("rotten_wood_strip", 2, 4, 1.0), ("screw_nail_box", 1, 1, 0.4)],
    "metal": [("scrap_metal", 1, 3, 1.0), ("rusty_screws", 1, 2, 0.8)],
    "plastic": [("plastic_casing", 1, 3, 1.0)],
    "electronics": [("crt_tube", 1, 1, 0.6), ("green_circuit", 1, 2, 1.0), ("copper_wire", 1, 3, 1.0), ("plastic_casing", 1, 2, 1.0)],
    "lamp": [("copper_wire", 1, 2, 1.0), ("glass_tube_shard", 1, 2, 0.8), ("rags", 1, 1, 0.6), ("rotten_wood_strip", 1, 2, 0.5)],
    "paper": [("damp_paper_scrap", 2, 5, 1.0), ("magazine_bundle", 1, 1, 0.3)],
    "carpet": [("rags", 3, 6, 1.0)],
    "laundry": [("rags", 3, 6, 1.0)],
    "register": [("sheet_metal", 1, 2, 1.0), ("plastic_casing", 1, 2, 1.0), ("lost_coin", 1, 3, 0.5), ("green_circuit", 1, 1, 0.5)],
    "pile": [("broken_table_leg", 3, 6, 1.0), ("rotten_wood_strip", 3, 6, 1.0), ("rags", 2, 4, 1.0), ("scrap_metal", 1, 3, 1.0),
             ("screw_nail_box", 1, 2, 1.0), ("damp_paper_scrap", 2, 4, 1.0)],
    "chair_pile": [("broken_table_leg", 4, 8, 1.0), ("scrap_metal", 2, 5, 1.0), ("plastic_casing", 2, 4, 1.0), ("rusty_screws", 2, 4, 1.0)],
    "tv_vcr": [("crt_tube", 1, 1, 0.6), ("green_circuit", 1, 2, 1.0), ("copper_wire", 1, 2, 1.0)],
    "vhs_shelf": [("rotten_wood_strip", 2, 4, 1.0)],
}

# How many tapes each VHS prop (and the TV corner) gives.
TAPES = {"vhs_1": (1, 1), "vhs_scatter": (3, 5), "vhs_small": (6, 10), "vhs_large": (20, 30), "vhs_box": (8, 8),
         "vhs_shelf": (12, 20), "tv_vcr": (2, 4)}

# Which tool breaks them faster (block tags), and how they sound.
TOOL = {"upholstered": "axe", "wood": "axe", "pile": "axe", "vhs_shelf": "axe", "lamp": "axe", "metal": "pickaxe", "chair_pile": "pickaxe",
        "register": "pickaxe", "electronics": "pickaxe", "tv_vcr": "pickaxe"}
