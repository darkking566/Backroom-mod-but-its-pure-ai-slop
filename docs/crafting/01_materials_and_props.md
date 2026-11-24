# 01. 素材來源、既有方塊拆解與異常穿模家具總表

本文件列出所有非怪物來源的材料取得管道，包含：**既有 Level 0／Level 1 方塊拆解表**、**新增 24 種異常穿模家具與環境物件**，以及 **76 種基礎／進階加工元件 ID 與用途**。

---

## 一、既有 Level 0／Level 1 方塊拆解掉落表

> 一般工具（達對應 Tier）敲碎會掉落表列元件；使用 **精準採集（Silk Touch）** 則掉落原方塊本體。

### Level 0 既有方塊（`level0/ModBlocks.java`）

| 方塊 ID | 所需工具 | 拆解掉落素材 |
|---|---|---|
| `wallpaper` / `pale_wallpaper` | Tier 0 | `damp_paper_scrap`（受潮碎壁紙 ×1–2）、`plaster_dust`（石膏粉 ×1）、偶掉 `rotten_wood_strip`（腐朽木條） |
| `wallpaper_baseboard` / `pale_wallpaper_baseboard` | Tier 0 | `rotten_wood_strip`（腐朽木條 ×2）、`rusty_nail`（生鏽釘子 ×1–2） |
| `rough_wallpaper` / `rough_wallpaper_baseboard` | Tier 1 | `concrete_rubble`（碎混凝土 ×2）、`plaster_dust`（×1）、`rusty_nail`（×1） |
| `red_wallpaper` / `red_wallpaper_baseboard` / `peeling_red_wallpaper` | **Tier 3**（紅室封鎖突破） | `red_room_shard`（紅室血晶碎屑 ×1–2）、`cursed_fiber`（異常紅絲 ×1） |
| `carpet` / `dry_carpet` / `thick_carpet` | Tier 0 | `damp_carpet_fiber`（潮濕地毯纖維 ×2–3）、`thick_carpet` 額外有機率擠出 `murky_water_vial`（混濁地毯水） |
| `ceiling_tile` | Tier 0 | `plaster_dust`（石膏粉 ×2）、`mineral_wool`（隔音礦棉 ×1）、偶掉 `thin_wire`（細鐵絲） |
| `fluorescent_light` / `broken_fluorescent_light` | Tier 0 | `glass_tube_shard`（燈管碎玻璃 ×2）、`copper_wire`（紅銅線 ×1–2）、`phosphor_powder`（既有磷粉 ×1）、低機率 `ballast_coil`（鎮流器線圈） |
| `red_fluorescent_light` | **Tier 3** | `red_room_shard`（×1）、`ballast_coil`（×1）、`glass_tube_shard`（×2） |

### Level 1 既有方塊（`level1/Level1Blocks.java`）

| 方塊 ID | 所需工具 | 拆解掉落素材 |
|---|---|---|
| `garage_floor` / `garage_wall` / `painted_concrete_wall` / `corridor_floor` | Tier 1 | `concrete_rubble`（碎混凝土 ×2–3）、低機率 `rebar_fragment`（斷裂鋼筋） |
| `garage_pillar` / `garage_pillar_base` / `garage_pillar_marked` | **Tier 2** | `rebar_fragment`（斷裂鋼筋 ×2–3）、`concrete_rubble`（×2）、`alloy`（既有合金 ×1） |
| `old_brick` | Tier 1 | `cracked_brick`（舊碎磚 ×2–3）、`plaster_dust`（×1） |
| `padded_wall` | Tier 0 | `foam_padding`（隔音泡棉 ×2–3）、`synthetic_fabric`（合成布料 ×2） |
| `garage_light` / `broken_garage_light` / `bare_bulb` / `caged_lamp` | Tier 0 | `glass_tube_shard`（×2）、`copper_wire`（×2）、`scrap_metal`（廢金屬片 ×1）、`phosphor_powder`（既有磷粉 ×1） |
| `ceiling_pipe` / `leaking_ceiling_pipe` | Tier 1 | `iron_pipe`（生鏽鐵管 ×1–2）、`pipe_valve`（金屬閥門，35%）、`rusty_screws`（生鏽螺絲 ×2） |
| `metal_shelf` | Tier 1 | `angle_iron`（角鐵 ×2）、`scrap_metal`（×2）、`rusty_screws`（×2） |
| `office_desk` | Tier 0 | `treated_plank`（舊辦公木板 ×2–3）、`rusty_nail`（×2）、`metal_spring`（彈簧，30%） |
| `old_computer` | Tier 1 | `old_circuit_board`（舊電路板 ×1）、`capacitor`（電容 ×1–2）、`plastic_casing`（廢塑膠殼 ×2）、`copper_wire`（×2）、低機率 `microchip`（老式微晶片） |
| `booth_wall` / `booth_roof` / `barrier_base` / `barrier_arm` | Tier 1 | `sheet_metal`（薄鋼板 ×1–2）、`alloy`（既有合金 ×1）、`plastic_casing`（×1） |
| `generator` | **Tier 2** | `electric_motor`（馬達核心 ×1）、`high_voltage_coil`（高壓線圈 ×2）、`steel_gear`（鋼製齒輪 ×2）、`alloy`（×2–3） |
| `fuel_drum` | Tier 1 | `sheet_metal`（×2）、`crude_fuel_can`（粗製機油罐 ×1–2） |
| `floodlight` | Tier 1 | `optical_lens`（光學透鏡 ×1）、`copper_wire`（×2）、`ballast_coil`（×1）、`alloy`（×1） |
| `tarp` / `cot` / `bedroll` | Tier 0 | `synthetic_fabric`（×2–3）、`iron_pipe`（行軍床掉 ×1） |

### Level 2 管道區與倉庫雜物拆解（`level2`）
| 方塊 ID | 所需工具 | 拆解掉落材料 |
|---|---|---|
| `toolbox`（工具箱） | Tier 0 | `sheet_metal`（×1）、`rusty_screws`（×2–3）、低機率 `alloy`（×1） |
| `cable_reel`（伸縮電線盤） | Tier 0 | `copper_wire`（×3–4）、`plastic_casing`（×1）、`rotten_wood_strip`（×2） |
| `lumber_stack`（木材垛） | Tier 0 | `rotten_wood_strip`（×3–4）、`rusty_nail`（×2） |
| `scrap_heap`（廢料堆） | Tier 0 | `scrap_metal`（×2）、`rusty_screws`（×2）、`iron_pipe`（×1） |
| `oil_drum`（油桶） | Tier 1 | `sheet_metal`（×2）、`crude_fuel_can`（×1） |
| `machine_cabinet` / `machine_panel` | Tier 1–2 | `old_circuit_board`（×1）、`capacitor`（×2）、`copper_wire`（×2）、`steel_gear`（×1） |

---

## 二、新增「異常穿模家具與環境物件」（No-Clipped Props，共 24 種）

這些物件在生成時刻意**半截插入牆壁、斜傾卡進地毯或懸空卡在天花板邊角**，強化後室「現實碎片卡出」的詭異感，同時是核心素材來源。

| 編號 | 物件 ID | 中文名 | 出現樓層 | 所需工具 | 拆解主要掉落 |
|---|---|---|---|---|---|
| P01 | `noclipped_wood_chair` | 穿牆木椅 | Level 0, 1, 4, 5, 52 | Tier 0 | `rotten_wood_strip` ×2–3、`rusty_nail` ×2 |
| P02 | `noclipped_office_chair` | 歪斜旋轉辦公椅 | Level 0, 1, 4, 4.3 | Tier 0 | `foam_padding` ×2、`plastic_casing` ×2、`iron_pipe` ×1、`metal_spring` ×1 |
| P03 | `noclipped_folding_table` | 半埋折疊長桌 | Level 0, 1, 1.01, 20 | Tier 0 | `treated_plank` ×2、`angle_iron` ×1、`rusty_screws` ×2 |
| P04 | `noclipped_file_cabinet` | 嵌牆鐵皮檔案櫃 | Level 0(罕見), 1, 4, 4.3 | Tier 1 | `sheet_metal` ×2、`metal_spring` ×2、`damp_paper_scrap` ×3、偶掉武器藍圖殘頁 |
| P05 | `noclipped_crt_tv` | 卡牆老式顯像管電視 | Level 0, 1, 18, Blue Channel | Tier 1 | `glass_tube_shard` ×3、`old_circuit_board` ×1、`copper_wire` ×3、`capacitor` ×2 |
| P06 | `noclipped_water_cooler` | 傾斜飲水機 | Level 0(罕見), 1, 4 | Tier 0 | `plastic_casing` ×3、`almond_water`（既有，50%）、`pipe_valve` ×1 |
| P07 | `noclipped_vending_machine` | 故障販賣機 | Level 1, 4, 6.1, 33 | Tier 1 | `sheet_metal` ×2、`electric_motor` ×1、`capacitor` ×2、`almond_water` ×1–2、食物 |
| P08 | `noclipped_fridge` | 生鏽破冰箱 | Level 1.01, 4.3, 11 | Tier 1 | `sheet_metal` ×2、`copper_pipe`（銅管）×2、`coolant_vial`（冷媒管）×1 |
| P09 | `boarded_barricade` | 封門木板柵欄 | Level 1, 1.1, 2, 52 | Tier 1（撬棍） | `treated_plank` ×3、`rusty_nail` ×4（打破後可進入後方密室／寶箱房） |
| P10 | `barbed_wire_fence` | 鏽蝕鐵絲網 | Level 1, 2, 3, 20 | Tier 1（斷線鉗） | `barbed_wire`（帶刺鐵絲 ×2–3）、`thin_wire` ×2 |
| P11 | `industrial_fuse_box` | 工業配電箱 | Level 1, 2, 3 | **Tier 2**（絕緣電工剪） | `high_voltage_coil` ×1–2、`capacitor` ×3、`battery_cell`（電池芯）×1–2、`copper_wire` ×3 |
| P12 | `reinforced_steel_bulkhead` | 加固鋼製隔牆 | Level 2, 3, 54 | **Tier 2**（液壓剪/切割機） | `heavy_steel_plate`（重鋼板）×2、`alloy` ×2–3、`steel_gear` ×1 |
| P13 | `steam_boiler_unit` | 高壓蒸氣鍋爐 | Level 1.01, 2, 5 | **Tier 2** | `heavy_steel_plate` ×2、`pipe_valve` ×2、`thermal_core`（焦熱核心，25%）、`coal_coke`（焦炭）×3 |
| P14 | `hospital_gurney` | 翻覆醫院推床 | Level !, 14 | Tier 1 | `iron_pipe` ×2、`synthetic_fabric` ×2、`steel_wheel`（鋼輪軸）×2、偶掉繃帶 |
| P15 | `vintage_chandelier` | 復古水晶吊燈 | Level 5, 143 | Tier 1 | `brass_ingot`（黃銅錠）×1–2、`prism_glass`（稜鏡水晶片）×2、`copper_wire` ×2 |
| P16 | `arcade_cabinet` | 破舊街機台 | Level 40, 283, 389, 3999 | Tier 1 | `microchip` ×1–2、`neon_tube`（霓虹燈管）×2、`old_circuit_board` ×2、`plastic_casing` ×2 |
| P17 | `neon_sign_prop` | 故障霓虹招牌 | Level 33, 40, 154, 399 | Tier 1 | `neon_tube` ×2–3、`high_voltage_coil` ×1、`glass_tube_shard` ×2 |
| P18 | `mold_vent_grate` | 黴菌通風口格柵 | Level 1.01, 2, 8 | Tier 1 | `sheet_metal` ×1、`toxic_spore_sac`（毒孢子囊）×1–2、`rusty_screws` ×2 |
| P19 | `pool_ceramic_grate` | 泳池瓷磚排水柵 | Level 37, 37.1 | Tier 1 | `ceramic_shard`（高硬陶瓷片）×3、`stainless_rod`（不鏽鋼桿）×1、`pure_almond_drop`（凝露） |
| P20 | `red_anomaly_cluster` | 紅室血晶簇 | Level 0 紅室、Level ! | **Tier 3**（異常共振鎬） | `red_room_shard`（紅室血晶）×2–4、`blood_resonance_core`（血鳴晶核，20%） |
| P21 | `crystal_vein_node` | 異常水晶礦脈 | Level 197, 8 | **Tier 3** | `anomaly_crystal`（異常結晶）×2–3、`prism_glass` ×2、`phosphor_powder` ×2 |
| P22 | `glitched_wall_seam` | 現實裂縫牆 | Level 0.2, 404, -0 | **Tier 3** | `reality_fragment`（現實碎塊）×1–2、`void_dust`（虛空微塵）×2 |
| P23 | `sealed_vault_wall` | 終局封印牆 | Level 998, Shady Grey, Boss 房 | **Tier 4**（破界工具） | `void_alloy_ingot`（虛空秘銀錠）×1–2、`void_dust` ×3 |
| P24 | `wanderer_remains` | 失蹤流浪者殘骸 | 全樓層罕見 | Tier 0 | `bone_fragment`（枯骨）×2、隨機彈藥／工具零件／日記殘頁／藍圖 |

---

## 三、完整素材與中間加工元件清單（共 76 種）

> 標註 `[既有]` 者直接使用本模組現行 Item ID，確保向後相容。

### A. 基礎拆解與建材原料（18 種）
| ID | 中文名 | 取得／加工方式 |
|---|---|---|
| `damp_paper_scrap` | 受潮碎壁紙 | 拆壁紙、檔案櫃；4 個可合紙張或火引 |
| `rotten_wood_strip` | 腐朽木條 | 拆踢腳板、木椅；烘烤或合成為木柄／木板 |
| `treated_plank` | 舊辦公木板 | 拆辦公桌、封門木板、折疊桌；做工作站與槍托 |
| `damp_carpet_fiber` | 潮濕地毯纖維 | 拆地毯；加工成線繩、防滑纏布 |
| `synthetic_fabric` | 合成布料 | 拆軟墊牆、帆布、推床、睡袋；做繃帶與護甲 |
| `foam_padding` | 隔音泡棉 | 拆軟墊牆、旋轉椅；做消音器內襯與護甲減震 |
| `plaster_dust` | 石膏粉 | 拆天花板磚、壁紙；做鑄模與基礎化學穩定劑 |
| `mineral_wool` | 隔音礦棉 | 拆天花板磚；做隔熱服與消音層 |
| `concrete_rubble` | 碎混凝土 | 拆水泥牆／地板；磨粉做配重與重鈍器核心 |
| `cracked_brick` | 舊碎磚 | 拆老磚牆；做熔爐與研磨石 |
| `glass_tube_shard` | 燈管碎玻璃 | 拆日光燈、電視；熔鍊成玻璃或做玻璃刀刃 |
| `ceramic_shard` | 高硬陶瓷片 | 拆泳池磚與排水柵；做防彈插板與高硬刃具 |
| `plastic_casing` | 廢塑膠殼 | 拆老電腦、飲水機、街機；熱壓成聚合物槍身／握把 |
| `rusty_nail` | 生鏽釘子 | 拆踢腳板、木家具；做釘棒、釘槍彈藥 |
| `rusty_screws` | 生鏽螺絲組 | 拆貨架、水管、桌子；精密組裝必備緊固件 |
| `thin_wire` | 細鐵絲 | 拆天花板、鐵絲網；做開鎖器、絆索、綁紮 |
| `barbed_wire` | 帶刺鐵絲 | 拆鐵絲網（斷線鉗）；做狼牙棒、防禦陷阱 |
| `bone_fragment` | 枯骨碎片 | 流浪者殘骸、肢團；做骨刃與骨灰催化劑 |

### B. 金屬與冶金原料（16 種）
| ID | 中文名 | 取得／加工方式 |
|---|---|---|
| `alloy` `[既有]` | 合金 | Level 1 補給箱、掠奪者、拆柱子與發電機（現有：4 合金→1 鐵錠） |
| `scrap_metal` | 廢金屬片 | 拆燈具、貨架、雜物；4 廢金屬片＋焦炭 → 1 `alloy` |
| `iron_pipe` | 生鏽鐵管 | 拆天花板水管、行軍床、辦公椅；直接當初期棍棒或做槍管胚 |
| `angle_iron` | 角鐵 | 拆鐵貨架、折疊桌；做工作站骨架與重斧刃背 |
| `rebar_fragment` | 斷裂鋼筋 | 拆水泥柱（Tier 2）；鍛造長槍桿、撬棍、穿甲彈芯 |
| `sheet_metal` | 薄鋼板 | 拆檔案櫃、販賣機、油桶、收費亭；做護甲與機匣 |
| `heavy_steel_plate` | 重鋼板 | 拆加固隔牆、鍋爐（Tier 2），或 4 `sheet_metal`＋2 `alloy` 鍛壓 |
| `refined_steel_ingot` | 精煉鋼錠 | 改裝台／熔煉：`iron_ingot` ×2 ＋ `alloy` ×2 ＋ `coal_coke` ×1 |
| `copper_wire` | 紅銅線 | 拆日光燈、電腦、電視、配電箱；所有電子與電磁裝備必備 |
| `copper_pipe` | 銅管 | 拆冰箱、蒸餾器零件；做化學蒸餾組與精密槍管內襯 |
| `brass_ingot` | 黃銅錠 | 拆復古吊燈，或 2 `copper_wire` ＋ 1 `scrap_metal` ＋ `plaster_dust` 熔合；做子彈彈殼與中式銅錢劍 |
| `lead_chunk` | 鉛塊 | 拆老電池、配重塊、重型門栓；做子彈彈頭與鈍器配重 |
| `tungsten_alloy` | 鎢鋼合金 | `refined_steel_ingot` ×2 ＋ `rebar_fragment` ×2 ＋ `thermal_core` ×1；做穿甲狙擊彈與高階兵器 |
| `stainless_rod` | 不鏽鋼桿 | 拆泳池設施或精鋼拉拔；做西洋刺劍、長槍桿與精密槍管 |
| `anomalous_mythril` | 異常秘銀 | 異常煉成壇：`refined_steel_ingot` ×2 ＋ `anomaly_crystal` ×2 ＋ `purified_almond_essence` ×1 |
| `void_alloy_ingot` | 虛空秘銀錠 | 拆封印牆（Tier 4），或 `anomalous_mythril` ×2 ＋ `void_dust` ×4 ＋ Boss 核心煉成 |

### C. 電子與機械元件（14 種）
| ID | 中文名 | 取得／加工方式 |
|---|---|---|
| `metal_spring` | 金屬彈簧 | 拆辦公椅、檔案櫃；槍械擊發組與復進機構必備 |
| `pipe_valve` | 金屬閥門 | 拆水管、鍋爐、飲水機；做噴火器、液壓剪與蒸餾組 |
| `steel_gear` | 鋼製齒輪 | 拆發電機、鋼隔牆，或 `refined_steel_ingot` 加工；做切割機、機械弩／槍械機匣 |
| `steel_wheel` | 鋼輪軸 | 拆推床、貨車架；做重機具滑軌 |
| `ballast_coil` | 鎮流器線圈 | 拆日光燈、探照燈；做電擊槍、強光手電筒、線圈組 |
| `capacitor` | 電容 | 拆老電腦、電視、配電箱、販賣機；做電子電路與能量儲存 |
| `old_circuit_board` | 舊電路板 | 拆老電腦、街機、電視；做進階工具與聲波裝置 |
| `microchip` | 老式微晶片 | 拆老電腦（低機率）、街機台；做精密電子瞄具、共振鎬、實驗槍 |
| `battery_cell` | 電池芯 | 拆配電箱，或 `copper_wire` ＋ `lead_chunk` ＋ `acid_vial` 化學合成；手電筒充電與電動工具核心 |
| `high_voltage_coil` | 高壓線圈 | 拆發電機、配電箱、霓虹招牌；做電磁軌道槍、雷弧法杖、切割機 |
| `electric_motor` | 馬達核心 | 拆發電機、販賣機；做工業切割機、液壓剪、電鋸 |
| `optical_lens` | 光學透鏡 | 拆探照燈，或 `prism_glass` 打磨；做瞄具、狙擊鏡、聖光法杖 |
| `prism_glass` | 稜鏡水晶片 | 拆復古吊燈、異常水晶礦脈；做光學與光系魔法核心 |
| `neon_tube` | 霓虹燈管 | 拆街機、霓虹招牌；做螢光武器與驅暗燈具 |

### D. 化學、燃料與後室液體（14 種）
| ID | 中文名 | 取得／加工方式 |
|---|---|---|
| `almond_water` `[既有]` | 杏仁水 | Level 1 補給箱／置物櫃、飲水機、販賣機 |
| `phosphor_powder` `[既有]` | 磷粉 | Level 1 補給箱、拆日光燈與水晶礦；做底火、照明彈、驅暗符 |
| `murky_water_vial` | 混濁地毯水 | 擠壓厚重地毯或積水區裝瓶；蒸餾過濾可得純水或副產黴渣 |
| `coal_coke` | 焦炭 | 拆鍋爐、燒烤木條；高溫冶煉精煉鋼必備燃料 |
| `crude_fuel_can` | 粗製機油罐 | 拆油桶；供噴火器、發電機與工業切割機燃料，亦可精煉 |
| `coolant_vial` | 冷媒管 | 拆破冰箱；做冰寒法杖與高溫冷卻系統 |
| `niter_powder` | 硝石粉 | 蒸餾潮濕水泥粉／黴菌殘渣或洞穴採集（Level 8）；火藥主原料 |
| `sulfur_dust` | 硫磺粉 | 鍋爐殘渣、Level 2/5 高溫區或蒸餾酸渣；火藥與酸液原料 |
| `black_powder` | 黑火藥 | 3×3：`niter_powder` ×2 ＋ `sulfur_dust` ×1 ＋ `coal_coke` ×1（土製槍與初期炸藥） |
| `smokeless_powder` | 無煙火藥 | 化學蒸餾組：`black_powder` ×2 ＋ `acid_vial` ×1 ＋ `damp_paper_scrap` ×2（正規槍械彈藥必備） |
| `primer_cap` | 子彈底火 | 改裝台：`brass_ingot` ×1 ＋ `phosphor_powder` ×2 ＋ `niter_powder` ×1 → 產 12 個 |
| `acid_vial` | 強酸瓶 | 死亡飛蛾酸囊蒸餾，或 `sulfur_dust` ＋ `murky_water_vial` 提煉；做電池、無煙火藥與酸液塗層 |
| `toxic_spore_sac` | 毒孢子囊 | 拆黴菌通風口（Level 1.01）；做毒霧彈與腐蝕塗層 |
| `purified_almond_essence` | 純化杏仁精粹 | 化學蒸餾組：`almond_water` ×3 ＋ `phosphor_powder` ×1 ＋ `anomaly_crystal`（或 `smiler_remnant`）×1；魔法武器核心催化劑 |

### E. 異常與高階能量素材（14 種，與怪物掉落互補）
| ID | 中文名 | 取得／加工方式 |
|---|---|---|
| `red_room_shard` | 紅室血晶 | 拆紅室牆／紅燈／紅晶簇（Tier 3）；做紅室兵器與共振組件 |
| `cursed_fiber` | 異常紅絲 | 拆紅室壁紙（Tier 3）；做高階咒符與綁帶 |
| `blood_resonance_core` | 血鳴晶核 | 紅室血晶簇（20%）或 9 `red_room_shard` 煉成；Tier 3 破壁核心 |
| `anomaly_crystal` | 異常結晶 | 拆水晶礦脈（Level 197，Tier 3）或有眼睛的箱子罕見開出 |
| `thermal_core` | 焦熱核心 | 拆蒸氣鍋爐（25%）或 Level 5 鍋爐房母蛾掉落；火焰武器與鎢鋼必備 |
| `reality_fragment` | 現實碎塊 | 拆現實裂縫牆（Level 0.2/404，Tier 3）；空間穿牆杖核心 |
| `void_dust` | 虛空微塵 | 拆裂縫牆、封印牆或 Level 998/999 採集 |
| `almond_mana_cell` | 杏仁靈能瓶 | 蒸餾組：`purified_almond_essence` ×1 ＋ `battery_cell` ×1 ＋ `prism_glass` ×1（魔法法杖通用充能彈藥） |
| `blueprint_scrap` | 殘破藍圖頁 | 檔案櫃、軍械箱、流浪者殘骸；4 張可拼合 `weapon_blueprint_blank` 解鎖進階配方 |
| `weapon_grip_wrap` | 防滑纏布握把 | 3×3：`rotten_wood_strip` ×1 ＋ `damp_carpet_fiber` ×2 ＋ `synthetic_fabric` ×1 |
| `polymer_frame` | 熱壓聚合物塊 | 蒸餾／加熱：`plastic_casing` ×3 ＋ `crude_fuel_can` ×1（現代槍械護木／握把） |
| `quench_oil` | 鍛造淬火油 | 蒸餾組：`crude_fuel_can` ×1 ＋ `almond_water` ×1 ＋ `bone_fragment` ×1（鍛造高階冷兵器必備） |
| `silencer_baffle` | 消音阻流芯 | 改裝台：`foam_padding` ×2 ＋ `mineral_wool` ×2 ＋ `sheet_metal` ×1 |
| `resonance_capacitor` | 異常共振電容 | 異常煉成壇：`high_voltage_coil` ×1 ＋ `microchip` ×1 ＋ `anomaly_crystal` ×2 |
