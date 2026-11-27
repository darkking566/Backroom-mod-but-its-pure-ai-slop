# 03. 工具階梯（Tier 0–4）、五大工作站、防具與求生道具合成表

本文件定義完整的**漸進式特殊破壞工具**（決定能拆哪一層的障礙與稀有方塊）、**五大工作站**，以及**防具／探險求生道具**的細緻配方。

---

## 一、五大工作站合成表

| 工作站 ID | 中文名 | 製作位置 | 細緻合成配方 | 功能說明 |
|---|---|---|---|---|
| `backrooms_compendium` | 《後室合成圖鑑》 | 3×3 工作台（或隨身快捷鍵） | `damp_paper_scrap` ×4 ＋ `damp_carpet_fiber` ×2 ＋ `phosphor_powder` ×1 | 開啟獨立合成表與素材來源查詢介面 |
| `salvage_bench` | 後室拆解台 | 3×3 工作台 | `treated_plank` ×3 ＋ `angle_iron` ×2 ＋ `rusty_screws` ×4 ＋ `scrap_metal` ×2 | 將家具／舊電器／多餘武器拆解出高階元件 |
| `workshop_bench` | 後室改裝鍛造台 | 3×3 工作台 | `heavy_steel_plate`（或 `sheet_metal` ×4）＋ `treated_plank` ×3 ＋ `alloy` ×4 ＋ `rusty_screws` ×4 ＋ `pipe_valve` ×1 | 解鎖 4×4 精密配方：冷兵器鍛造、槍械與彈藥組裝、Tier 2 工具 |
| `almond_distiller` | 杏仁蒸餾化學組 | 改裝鍛造台 | `copper_pipe` ×3 ＋ `glass_tube_shard` ×4 ＋ `pipe_valve` ×2 ＋ `sheet_metal` ×2 ＋ `almond_water` ×1 | 提純杏仁精粹、無煙火藥、強酸、淬火油與靈能瓶 |
| `anomaly_altar` | 異常煉成壇 | 改裝鍛造台 | `refined_steel_ingot` ×4 ＋ `anomaly_crystal` ×2 ＋ `red_room_shard` ×2 ＋ `purified_almond_essence` ×2 ＋ `old_circuit_board` ×2 | 解鎖魔法／異常武器、異常秘銀、Tier 3–4 破界工具 |

---

## 二、階梯式特殊破壞工具（Tier 0 → Tier 4）

> 工具採「**先做部件（柄／頭／動力模組），再總裝**」的細緻流程；部分工具同時可作為武器或開鎖道具。

### 中間工具零件（先於 3×3 或改裝台製作）
| 零件 ID | 中文名 | 合成位置 | 配方 |
|---|---|---|---|
| `tool_handle_basic` | 簡易防滑木柄 | 3×3 | `rotten_wood_strip` ×2 ＋ `damp_carpet_fiber` ×2 ＋ `rusty_nail` ×1 |
| `tool_handle_reinforced` | 鋼芯強化握柄 | 改裝台 | `iron_pipe` ×1 ＋ `weapon_grip_wrap` ×2 ＋ `rusty_screws` ×2 |
| `insulated_grip` | 絕緣橡膠握柄 | 改裝台 | `polymer_frame` ×1 ＋ `synthetic_fabric` ×2 ＋ `foam_padding` ×1 |
| `hydraulic_module` | 液壓動力組件 | 改裝台 | `electric_motor` ×1 ＋ `pipe_valve` ×2 ＋ `steel_gear` ×2 ＋ `crude_fuel_can` ×1 |
| `resonance_emitter` | 異常共振發射頭 | 異常煉成壇 | `resonance_capacitor` ×1 ＋ `blood_resonance_core` ×1 ＋ `prism_glass` ×2 ＋ `copper_wire` ×4 |

### Tier 1 工具（Level 0–1 初期解鎖，可破封門木板、水管、貨架、老電腦、鐵絲網、軍械箱）

| 工具 ID | 中文名 | 製作位置 | 細緻配方 | 專屬破壞權限與用途 |
|---|---|---|---|---|
| `improvised_crowbar` | 撬棍（Crowbar） | 3×3 | `iron_pipe` ×1 ＋ `alloy` ×2 ＋ `weapon_grip_wrap` ×1 ＋ `rusty_nail` ×2 | 拆封門木板、檔案櫃、老電腦、撬開上鎖軍械箱；兼任初期鈍器 |
| `wire_cutters` | 斷線鉗 | 3×3 | `scrap_metal` ×3 ＋ `rusty_screws` ×2 ＋ `metal_spring` ×1 ＋ `tool_handle_basic` ×2 | 剪斷鏽蝕鐵絲網、快速拆解電子線材（紅銅線產量 +50%） |
| `pipe_wrench` | 工業管鉗 | 3×3 | `alloy` ×3 ＋ `angle_iron` ×1 ＋ `rusty_screws` ×2 ＋ `weapon_grip_wrap` ×1 | 拆解水管、鍋爐閥門、販賣機；兼任重型鈍器 |
| `lockpick_set` | 簡易開鎖組（消耗） | 3×3 | `thin_wire` ×3 ＋ `scrap_metal` ×1 ＋ `rusty_nail` ×1 → 產 3 組 | 無聲開啟上鎖軍械箱（不像撬棍會發出噪音吸引附近獵犬／掠奪者） |

### Tier 2 工具（Level 1 機房／Level 2–3 解鎖，可破水泥柱、高壓電箱、發電機、加固鋼牆）

| 工具 ID | 中文名 | 製作位置 | 細緻配方 | 專屬破壞權限與用途 |
|---|---|---|---|---|
| `insulated_shears` | 絕緣電工剪 | 改裝台 | `wire_cutters` ×1 ＋ `refined_steel_ingot` ×2 ＋ `insulated_grip` ×2 ＋ `ceramic_shard` ×2 | 安全拆解 Level 3 **工業配電箱** 與帶電設備（徒手或鐵工具拆會觸電重傷） |
| `hydraulic_cutter` | 液壓剪 | 改裝台 | `refined_steel_ingot` ×4 ＋ `hydraulic_module` ×1 ＋ `tool_handle_reinforced` ×2 ＋ `heavy_steel_plate` ×1 | 剪開 Level 2/3 **加固鋼製隔牆**、重型鐵門與鐵欄 |
| `industrial_saw` | 工業切割機（需燃料/電池） | 改裝台 | `electric_motor` ×1 ＋ `refined_steel_ingot` ×3 ＋ `steel_gear` ×3 ＋ `sheet_metal` ×2 ＋ `battery_cell` ×2 ＋ `insulated_grip` ×1 | 拆解 **水泥大柱**、**發電機**、**蒸氣鍋爐**（高速切割鋼筋與鋼板） |

### Tier 3 工具（中深層異常解鎖，可破紅室封鎖牆、水晶礦脈、現實裂縫牆、隱藏密室）

| 工具 ID | 中文名 | 製作位置 | 細緻配方 | 專屬破壞權限與用途 |
|---|---|---|---|---|
| `anomaly_resonance_pick` | 異常共振鎬 | 異常煉成壇 | `tungsten_alloy` ×3 ＋ `resonance_emitter` ×1 ＋ `tool_handle_reinforced` ×1 ＋ `battery_cell` ×2 ＋ `purified_almond_essence` ×1 | 開採 **異常水晶礦脈（Level 197）**、**紅室血晶簇**、**現實裂縫牆** |
| `crimson_wallbreaker` | 紅室破壁錘 | 異常煉成壇 | `heavy_steel_plate` ×2 ＋ `core_crimson_heart`（血廊之心）×1 ＋ `red_room_shard` ×4 ＋ `tool_handle_reinforced` ×1 | 強制破開 **紅室封鎖牆**（突破紅室困死鎖挖機制！）與隱藏密室石牆 |

### Tier 4 工具（終局 Boss 解鎖，可破高危樓層與終局封印牆）

| 工具 ID | 中文名 | 製作位置 | 細緻配方 | 專屬破壞權限與用途 |
|---|---|---|---|---|
| `void_disruptor` | 虛空裂解器 | 異常煉成壇 | `anomaly_resonance_pick` ×1 ＋ `void_alloy_ingot` ×3 ＋ `core_void_weaver`（虛空織核）×1 ＋ `core_arc_reactor` ×1 ＋ `almond_mana_cell` ×2 | 破壞 **終局封印牆**（Level 998 / Th3 Sh4dy Gr3y）與解鎖終極隱藏寶庫 |

---

## 三、探險求生道具、強化手電與防具合成表

### 探險照明與求生消耗品
| 道具 ID | 中文名 | 製作位置 | 細緻配方 | 效果 |
|---|---|---|---|---|
| `flashlight` `[既有]` | 手電筒（亦可自行組裝） | 改裝台 | `iron_pipe` ×1 ＋ `optical_lens` ×1 ＋ `battery_cell` ×1 ＋ `copper_wire` ×2 ＋ `plastic_casing` ×1 | 既有動態光束手電筒（現可額外自製，不只靠收費亭）。【已做】電量 5 格（每格約 4 分鐘），一顆電池芯補 2 格；裝電池三種方法：拿著 Shift＋右鍵、在背包裡把電池點到手電筒上、跟電池芯無序合成。另有 Level 3 的「汲能手電筒」（`ambient_flashlight`）不用電池，還沒擺進世界 |
| `flashlight_battery_pack` | 備用手電電池組 | 改裝台 | `battery_cell` ×2 ＋ `copper_wire` ×2 ＋ `plastic_casing` ×1 | 右鍵幫 `flashlight` 補滿電力 |
| `tactical_uv_flashlight` | 強光驅暗戰術手電 | 異常煉成壇 | `flashlight` ×1 ＋ `prism_glass` ×2 ＋ `phosphor_powder` ×4 ＋ `smiler_remnant` ×2 ＋ `high_voltage_coil` ×1 | 照射可灼傷影行者並短暫暈眩微笑者（耗電較高） |
| `field_bandage` | 戰地止血繃帶 | 3×3 | `synthetic_fabric` ×2 ＋ `damp_carpet_fiber` ×2 ＋ `almond_water` ×1 | 快速回復 3 顆心並止血 |
| `almond_elixir` | 高濃縮杏仁藥劑 | 化學蒸餾組 | `purified_almond_essence` ×1 ＋ `almond_water` ×2 ＋ `moth_wing_scale` ×1 | 立刻清除黑暗／反胃／失明／紅室壓迫，賦予 45 秒理智護盾＋回復 II |
| `phosphor_flare` | 磷粉信號彈（投擲） | 改裝台 | `phosphor_powder` ×3 ＋ `black_powder` ×1 ＋ `damp_paper_scrap` ×2 → 產 4 枚 | 丟出照亮大片區域 30 秒，吸引並暫時困住趨光怪 |
| `tripwire_shrapnel_mine` | 廢料破片絆雷 | 改裝台 | `thin_wire` ×2 ＋ `black_powder` ×2 ＋ `rusty_nail` ×4 ＋ `sheet_metal` ×1 | 埋在走廊，怪物（獵犬/悲屍）踩中爆炸並造成大流血與緩速 |
| `almond_gas_mask` | 過濾防毒面具（頭盔） | 改裝台 | `polymer_frame` ×2 ＋ `optical_lens` ×2 ＋ `coal_coke` ×3 ＋ `synthetic_fabric` ×2 | 免疫 Level 1.01 毒黴菌孢子與強酸霧氣 |

### 特色功能防具套組（4 套代表性進階防具）
| 套組 | 關鍵材料（每件依頭/胸/腿/靴比例遞減） | 製作位置 | 套裝特技 |
|---|---|---|---|
| **廢料治安套（Scrap Tactical）** | `sheet_metal` ＋ `foam_padding` ＋ `synthetic_fabric` ＋ `alloy` | 改裝台 | 基礎高防禦＋減少獵犬撲咬流血傷害 |
| **竊皮偽裝套（Skin-Stealer Cloak）** | `thick_disguise_hide` ＋ `faceless_membrane` ＋ `silence_core`（靴） | 改裝台 | 大幅縮小怪物偵測距離，步行噪音降低（不驚動微笑者與獵犬） |
| **焦熱隔熱套（Boiler Thermal Suit）** | `moth_chitin` ＋ `mineral_wool` ＋ `heavy_steel_plate` ＋ `coolant_vial` | 改裝台 | 免疫 Level 2/5 高溫灼傷與雌性死亡飛蛾強酸腐蝕 |
| **深海與虛空探索套（Abyssal / Void Rig）** | `void_alloy_ingot` ＋ `core_abyssal_scale` ＋ `watch_ocular_lens`（頭） | 異常煉成壇 | 水下呼吸＋永久微夜視＋大幅減免紅室與高層精神反噬 |
