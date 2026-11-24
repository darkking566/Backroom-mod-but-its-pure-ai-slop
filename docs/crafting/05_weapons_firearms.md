# 05. 熱兵器／槍械系（18 把）、精密零件樹、彈藥鏈與戰術配件

槍械系統主打**高度細緻的分件組裝**與**聲響風險機制**：在後室開火威力強大，但未裝消音器時會產生巨大槍聲，吸引周圍的**微笑者、獵犬與掠奪者**！

---

## 一、槍械精密組裝零件樹（全部於 `workshop_bench` 改裝鍛造台製作）

| 零件 ID | 中文名 | 細緻合成配方 | 用途 |
|---|---|---|---|
| `gun_barrel_pipe` | 簡易平滑槍管 | `iron_pipe` ×1 ＋ `scrap_metal` ×2 ＋ `rusty_screws` ×2 | 土製槍、土炮、雙管散彈槍 |
| `gun_barrel_rifled_short` | 精製膛線短槍管 | `refined_steel_ingot` ×2 ＋ `copper_pipe` ×1 ＋ `steel_gear` ×1 | 手槍、左輪、衝鋒槍 |
| `gun_barrel_rifled_long` | 精製膛線長槍管 | `refined_steel_ingot` ×3 ＋ `stainless_rod` ×1 ＋ `copper_pipe` ×1 | 突擊步槍、戰鬥步槍、泵動霰彈槍 |
| `gun_barrel_heavy_tungsten` | 鎢鋼重型狙擊管 | `tungsten_alloy` ×3 ＋ `stainless_rod` ×2 ＋ `quench_oil` ×1 | 栓動狙擊槍、反器材重狙 |
| `gun_receiver_basic` | 簡易沖壓機匣 | `sheet_metal` ×3 ＋ `metal_spring` ×2 ＋ `rusty_screws` ×4 | 土製槍、釘槍、土製衝鋒槍 |
| `gun_receiver_precision` | 精密鋼製機匣 | `refined_steel_ingot` ×3 ＋ `steel_gear` ×2 ＋ `metal_spring` ×2 ＋ `rusty_screws` ×4 | 正規手槍、霰彈槍、步槍 |
| `gun_trigger_group` | 擊發與復進組件 | `metal_spring` ×3 ＋ `alloy` ×2 ＋ `rusty_screws` ×3 | 所有正規槍械必備核心 |
| `gun_revolver_cylinder` | 六發轉輪彈巢 | `refined_steel_ingot` ×2 ＋ `steel_gear` ×1 ＋ `brass_ingot` ×1 | 左輪手槍 |
| `gun_pump_action` | 泵動滑軌組件 | `stainless_rod` ×2 ＋ `metal_spring` ×2 ＋ `polymer_frame` ×1 | 泵動戰術霰彈槍 |
| `gun_stock_wood` | 木質握把與槍托 | `treated_plank` ×3 ＋ `weapon_grip_wrap` ×1 ＋ `rusty_screws` ×2 | 獵槍、傳統步槍、狙擊槍 |
| `gun_stock_polymer` | 戰術聚合物槍托 | `polymer_frame` ×3 ＋ `foam_padding` ×1 ＋ `rusty_screws` ×2 | 現代衝鋒槍、突擊步槍、戰術散彈槍 |
| `gun_mag_pistol` | 手槍／衝鋒槍彈匣 | `sheet_metal` ×2 ＋ `metal_spring` ×1 | 手槍與衝鋒槍裝填 |
| `gun_mag_rifle` | 步槍標準彈匣 | `sheet_metal` ×2 ＋ `polymer_frame` ×1 ＋ `metal_spring` ×2 | 突擊步槍／戰鬥步槍 |
| `gun_coil_accelerator` | 電磁線圈加速組 | `high_voltage_coil` ×3 ＋ `capacitor` ×4 ＋ `microchip` ×1 ＋ `copper_wire` ×6 | 電擊槍、電磁軌道槍 |

---

## 二、18 種熱兵器與實驗槍械完整清單

### A. 工業改裝與土製系（5 把 — Level 1–2 就能湊零件手搓！）

| 編號 | ID | 中文名 | 階級 | 使用彈藥 | 特色與定位 | 細緻組裝配方 |
|---|---|---|---|---|---|---|
| G01 | `gun_pipe_pistol` | 單發土製管槍 | T1 | `ammo_9mm` 或 `ammo_scrap_slug` | 單發裝填慢但近距離威力不俗，初期保命槍 | 3×3：`gun_barrel_pipe` ×1 ＋ `gun_receiver_basic` ×1 ＋ `tool_handle_basic` ×1 ＋ `metal_spring` ×1 |
| G02 | `gun_pipe_shotgun` | 雙管土製散彈槍 | T1 | `ammo_12g_buck` | 連噴 2 發極高近戰爆發，後座力大、噪音大 | 改裝台：`gun_barrel_pipe` ×2 ＋ `gun_receiver_basic` ×1 ＋ `gun_stock_wood` ×1 ＋ `gun_trigger_group` ×1 |
| G03 | `gun_nailgun` | 氣動工業釘槍 | T1 | `ammo_steel_nails` | **低噪音！** 用生鏽釘子當廉價彈藥，極適合在 Level 1 防微笑者騷擾 | 改裝台：`gun_receiver_basic` ×1 ＋ `pipe_valve` ×2 ＋ `electric_motor` ×1 ＋ `battery_cell` ×1 ＋ `polymer_frame` ×1 |
| G04 | `gun_taser` | 高壓電擊槍 | T2 | `battery_cell` | 命中麻痺獵犬／竊皮者 3.5 秒，零槍聲 | 改裝台：`gun_receiver_basic` ×1 ＋ `high_voltage_coil` ×1 ＋ `capacitor` ×2 ＋ `copper_wire` ×4 ＋ `insulated_grip` ×1 |
| G05 | `gun_flamethrower` | 鍋爐改裝噴火器（Boss） | T3 | `ammo_fuel_canister` | 持續範圍火焰噴射，克制群聚肢團、死亡飛蛾與派對客 | 改裝台：`gun_barrel_pipe` ×2 ＋ `pipe_valve` ×3 ＋ `core_steam_turbine`（蒸氣巨肢團）×1 ＋ `heavy_steel_plate` ×2 ＋ `crude_fuel_can` ×2 |

### B. 正規手槍系（3 把）

| 編號 | ID | 中文名 | 階級 | 使用彈藥 | 特色與定位 | 細緻組裝配方（或軍械箱開出） |
|---|---|---|---|---|---|---|
| G06 | `gun_revolver` | 六發左輪手槍 | T2 | `ammo_9mm` / `.357` | 高單發停阻、永不卡彈、可掛紅點 | 改裝台：`gun_barrel_rifled_short` ×1 ＋ `gun_revolver_cylinder` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_stock_wood` ×1 |
| G07 | `gun_pistol_9mm` | 半自動手槍（9mm） | T2 | `ammo_9mm` | 15 發輕快副武器，裝消音器後是最佳潛行清怪槍 | 改裝台：`gun_barrel_rifled_short` ×1 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_mag_pistol` ×1 ＋ `polymer_frame` ×1 |
| G08 | `gun_heavy_handgun` | 重型大口徑手槍（.50） | T3 | `ammo_308` | 單發穿透重甲與獵犬頭顱，高後座力 | 改裝台：`gun_barrel_rifled_short` ×1 ＋ `tungsten_alloy` ×2 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_mag_pistol` ×1 |

### C. 正規霰彈槍系（3 把）

| 編號 | ID | 中文名 | 階級 | 使用彈藥 | 特色與定位 | 細緻組裝配方 |
|---|---|---|---|---|---|---|
| G09 | `gun_double_barrel` | 經典雙管獵槍 | T2 | `ammo_12g_*` | 右鍵可雙管齊射，瞬間重創撲來的獵犬 | 改裝台：`gun_barrel_rifled_long` ×2 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_stock_wood` ×1 |
| G10 | `gun_pump_shotgun` | 泵動戰術霰彈槍 | T2 | `ammo_12g_*` | 8 發管倉、穩定清怪主力，支援手電導軌與消音器 | 改裝台：`gun_barrel_rifled_long` ×1 ＋ `gun_pump_action` ×1 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_stock_polymer` ×1 |
| G11 | `gun_auto_shotgun` | 腐蝕全自動戰鬥霰彈槍（Boss） | T3 | `ammo_12g_*` | 12 發彈鼓連射、內建耐酸槍管（使用酸液彈傷+35%） | 改裝台：`gun_pump_shotgun` ×1 ＋ `core_acid_queen`（焦熱母蛾女皇）×1 ＋ `electric_motor` ×1 ＋ `heavy_steel_plate` ×2 |

### D. 衝鋒槍、步槍與狙擊槍系（5 把）

| 編號 | ID | 中文名 | 階級 | 使用彈藥 | 特色與定位 | 細緻組裝配方 |
|---|---|---|---|---|---|---|
| G12 | `gun_scrap_smg` | 土製簡易衝鋒槍 | T2 | `ammo_9mm` | 高射速近戰潑水，精準度偏低 | 改裝台：`gun_barrel_rifled_short` ×1 ＋ `gun_receiver_basic` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_mag_pistol` ×2 ＋ `iron_pipe` ×1 |
| G13 | `gun_tactical_smg` | 戰術消音衝鋒槍 | T2 | `ammo_9mm` | 射速快、極穩、適合搭配消音器跑 Level 1–3 狹窄走廊 | 改裝台：`gun_barrel_rifled_short` ×1 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_stock_polymer` ×1 ＋ `gun_mag_pistol` ×2 |
| G14 | `gun_assault_rifle` | 標準突擊步槍（5.56mm） | T3 | `ammo_556` | 全能主力步槍，中遠距精準點射／連發 | 改裝台：`gun_barrel_rifled_long` ×1 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_stock_polymer` ×1 ＋ `gun_mag_rifle` ×2 ＋ `alloy` ×3 |
| G15 | `gun_bolt_sniper` | 栓動精準狙擊槍（.308） | T3 | `ammo_308` | 超遠射程高爆頭倍率，適合超大廳與 Level 9/10/11 戶外狙擊 | 改裝台：`gun_barrel_heavy_tungsten` ×1 ＋ `gun_receiver_precision` ×1 ＋ `gun_trigger_group` ×1 ＋ `gun_stock_wood` ×1 ＋ `optic_sniper_scope` ×1 |
| G16 | `gun_antimateriel_rifle` | 深淵反器材重狙（Boss） | T4 | `ammo_308_ap` | 貫穿多隻目標與薄牆，對巨型 Boss 造成毀滅打擊 | 異常煉成壇：`gun_bolt_sniper` ×1 ＋ `core_abyssal_scale`（七層之物）×1 ＋ `tungsten_alloy` ×4 ＋ `long_limb_bone` ×2 |

### E. 後室異常實驗槍系（2 把）

| 編號 | ID | 中文名 | 階級 | 使用彈藥 | 特色與定位 | 細緻組裝配方 |
|---|---|---|---|---|---|---|
| G17 | `gun_railgun` | 電磁線圈軌道槍（Boss） | T4 | `ammo_rail_slug` ＋ `battery_cell` | 蓄力瞬發超高速電磁貫穿彈，附帶強電弧破盾 | 異常煉成壇：`gun_barrel_heavy_tungsten` ×1 ＋ `gun_coil_accelerator` ×2 ＋ `core_arc_reactor`（雷電巨骸）×1 ＋ `gun_stock_polymer` ×1 ＋ `void_alloy_ingot` ×1 |
| G18 | `gun_sonic_pulse` | 聲波脈衝震盪槍（Boss） | T3 | `almond_mana_cell` | 發射無形震盪波大範圍擊退怪群並震碎窗戶／幻覺實體 | 異常煉成壇：`gun_receiver_precision` ×1 ＋ `static_screech_core`（細菌）×2 ＋ `core_fate_die`（遊戲大師）×1 ＋ `old_circuit_board` ×2 ＋ `brass_ingot` ×3 |

---

## 三、細緻彈藥合成鏈（先做彈殼與火藥 → 壓裝成彈）

### 彈藥基礎組件
| ID | 中文名 | 製作位置 | 配方 |
|---|---|---|---|
| `casing_pistol` | 手槍黃銅彈殼（×16） | 改裝台 | `brass_ingot` ×2 |
| `casing_rifle` | 步槍黃銅彈殼（×12） | 改裝台 | `brass_ingot` ×2 ＋ `copper_wire` ×1 |
| `hull_shotgun` | 12 號塑膠霰彈殼（×12） | 改裝台 | `plastic_casing` ×2 ＋ `brass_ingot` ×1 |
| `bullet_lead_tip` | 鉛芯彈頭（×16） | 改裝台 | `lead_chunk` ×2 |
| `bullet_ap_core` | 鎢鋼穿甲彈芯（×8） | 改裝台 | `tungsten_alloy` ×1 ＋ `rebar_fragment` ×2 |

### 各口徑與特種彈藥配方（皆於改裝台壓裝）
| 彈藥 ID | 中文名 | 配方與產量 | 特殊效果 |
|---|---|---|---|
| `ammo_steel_nails` | 釘槍鋼釘排（×24） | `rusty_nail` ×8 ＋ `scrap_metal` ×1 | 極低成本、無聲 |
| `ammo_9mm` | 9mm 標準手槍彈（×16） | `casing_pistol` ×16 ＋ `primer_cap` ×4 ＋ `smokeless_powder` ×1 ＋ `bullet_lead_tip` ×16 | 手槍／衝鋒槍標準彈 |
| `ammo_12g_buck` | 12 號鹿彈（×8） | `hull_shotgun` ×8 ＋ `primer_cap` ×4 ＋ `smokeless_powder` ×2 ＋ `lead_chunk` ×2 | 單發 8 顆彈丸高近傷 |
| `ammo_12g_acid` | 12 號飛蛾強酸霰彈（×8） | `ammo_12g_buck` ×8 ＋ `acid_vial` ×1 ＋ `acid_crystal` ×1 | 範圍潑濺腐蝕破甲 |
| `ammo_556` | 5.56mm 步槍彈（×12） | `casing_rifle` ×12 ＋ `primer_cap` ×4 ＋ `smokeless_powder` ×2 ＋ `bullet_lead_tip` ×12 | 突擊步槍標準彈 |
| `ammo_556_phosphor` | 5.56mm 磷光驅暗彈（×12） | `ammo_556` ×12 ＋ `phosphor_powder` ×4 ＋ `purified_almond_essence` ×1 | 命中爆發微光並對微笑者／暗影雙倍傷 |
| `ammo_308` | .308 重型狙擊彈（×8） | `casing_rifle` ×8 ＋ `primer_cap` ×4 ＋ `smokeless_powder` ×2 ＋ `bullet_lead_tip` ×8 | 狙擊與大口徑手槍 |
| `ammo_308_ap` | .308 鎢鋼穿甲彈（×8） | `casing_rifle` ×8 ＋ `primer_cap` ×4 ＋ `smokeless_powder` ×3 ＋ `bullet_ap_core` ×8 | 無視 65% 護甲＋穿透目標 |
| `ammo_fuel_canister` | 噴火器高壓油罐（×2） | `crude_fuel_can` ×2 ＋ `sheet_metal` ×1 ＋ `sulfur_dust` ×2 | 供噴火器持續噴射 |
| `ammo_rail_slug` | 軌道槍鎢鋼彈丸（×6） | `tungsten_alloy` ×2 ＋ `copper_wire` ×4 ＋ `anomaly_crystal` ×1 | 軌道槍專用貫穿彈 |

---

## 四、戰術改裝配件（於 `workshop_bench` 安裝至支援槍械）

| 配件 ID | 中文名 | 細緻配方 | 實戰效果 |
|---|---|---|---|
| `mod_silencer` | 靜默消音器 | `iron_pipe` ×1 ＋ `silencer_baffle` ×2 ＋ `silence_core`（鈍人）×1 | **大幅降低開槍聲響半徑 80%**，避免驚動停電中的微笑者與遠處獵犬 |
| `mod_red_dot` | 反射紅點瞄具 | `optical_lens` ×1 ＋ `plastic_casing` ×1 ＋ `phosphor_powder` ×1 ＋ `battery_cell` ×1 | 縮短瞄準速度、提升準度 |
| `mod_sniper_scope` | 誘捕高倍狙擊鏡 | `optical_lens` ×2 ＋ `lure_cornea`（窗戶）×1 ＋ `iron_pipe` ×1 ＋ `microchip` ×1 | 8× 變焦並於黑暗中微幅標記怪物輪廓 |
| `mod_weapon_light` | 槍掛戰術手電 | `flashlight` ×1 ＋ `angle_iron` ×1 ＋ `rusty_screws` ×2 | 持槍時按鍵開關動態光束，空出副手 |
| `mod_ext_mag` | 擴容雙聯彈匣 | `gun_mag_rifle` ×1 ＋ `metal_spring` ×2 ＋ `polymer_frame` ×1 | 彈匣容量 +50% |
| `mod_bayonet` | 槍口刺刀 | `west_hound_dagger` ×1 ＋ `angle_iron` ×1 ＋ `rusty_screws` ×2 | 沒子彈時可直接近戰突刺並附流血 |
