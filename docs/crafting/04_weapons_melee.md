# 04. 冷兵器細緻合成表（中世紀鈍器 / 西式兵器 / 中式兵器，共 42 把）

冷兵器採取「**先鍛造胚件與柄具 → 淬火／纏柄 → 總裝**」的多層細緻合成流程。三大體系各有鮮明戰鬥定位，而非單純數值換皮。

---

## 一、冷兵器通用鍛造中間零件（先於 3×3 或改裝台製作）

| 零件 ID | 中文名 | 製作位置 | 細緻配方 |
|---|---|---|---|
| `weapon_shaft_wood` | 強化長木桿 | 3×3 | `treated_plank` ×3 ＋ `damp_carpet_fiber` ×2 ＋ `rusty_nail` ×2 |
| `weapon_shaft_steel` | 鋼芯長槍桿 | 改裝台 | `rebar_fragment` ×2 ＋ `stainless_rod` ×1 ＋ `weapon_grip_wrap` ×2 |
| `crossguard_iron` | 鐵製護手／格檔盤 | 改裝台 | `alloy` ×2 ＋ `sheet_metal` ×1 ＋ `rusty_screws` ×2 |
| `crossguard_brass` | 精雕黃銅護手 | 改裝台 | `brass_ingot` ×2 ＋ `refined_steel_ingot` ×1 |
| `pommel_lead` | 鉛芯配重球 | 改裝台 | `lead_chunk` ×2 ＋ `scrap_metal` ×1 |
| `forged_iron_blank` | 重鍛鐵刃胚 | 改裝台 | `iron_ingot` ×2 ＋ `alloy` ×2 ＋ `coal_coke` ×1 |
| `forged_steel_blank` | 精煉鋼刃胚 | 改裝台 | `refined_steel_ingot` ×3 ＋ `quench_oil` ×1 ＋ `coal_coke` ×2 |
| `forged_tungsten_blank` | 鎢鋼長刃胚 | 改裝台 | `tungsten_alloy` ×3 ＋ `quench_oil` ×2 ＋ `corrupted_marrow` ×1 |
| `forged_mythril_blank` | 異常秘銀刃胚 | 異常煉成壇 | `anomalous_mythril` ×3 ＋ `purified_almond_essence` ×1 ＋ `quench_oil` ×1 |
| `spiked_head_iron` | 尖刺錘頭 | 改裝台 | `alloy` ×3 ＋ `rusty_nail` ×4 ＋ `lead_chunk` ×1 |
| `heavy_hammer_head` | 重鋼戰錘頭 | 改裝台 | `heavy_steel_plate` ×2 ＋ `refined_steel_ingot` ×2 ＋ `lead_chunk` ×2 |
| `forged_chain_link` | 精鍛重鎖鏈 | 改裝台 | `refined_steel_ingot` ×2 ＋ `thin_wire` ×4 ＋ `grasping_tendon` ×1 |

---

## 二、中世紀鈍器系（12 把）
> **體系定位**：高破甲、強擊退、附帶短暫暈眩或破盾／破木障能力，對重甲掠奪者與甲殼實體（Level 54 甲殼人、聖騎士）有額外破防加成。

| 編號 | ID | 中文名 | 階級 | 攻擊 / 攻速 | 特色機制 | 取得與細緻合成配方 |
|---|---|---|---|---|---|---|
| M01 | `blunt_rusty_pipe` | 生鏽鐵管 | T0 | 5.0 / 1.4 | 擊退+1 | 拆天花板水管直接取得，或 3×3：`iron_pipe` ×1 ＋ `weapon_grip_wrap` ×1 |
| M02 | `improvised_crowbar` | 撬棍（兼工具） | T1 | 6.5 / 1.5 | 兼 Tier 1 破壞工具與開軍械箱 | 見 `03_tools_and_stations.md` |
| M03 | `blunt_nailed_bat` | 強化釘棒 | T1 | 7.5 / 1.3 | 附帶 3 秒流血 | 3×3：`treated_plank` ×2 ＋ `rusty_nail` ×6 ＋ `weapon_grip_wrap` ×1 |
| M04 | `blunt_barbed_kanabo` | 鐵絲狼牙棒 | T1 | 9.0 / 1.1 | 強流血＋大擊退 | 改裝台：`weapon_shaft_wood` ×1 ＋ `barbed_wire` ×3 ＋ `lead_chunk` ×2 ＋ `hound_fang` ×3 |
| M05 | `blunt_iron_mace` | 鐵製釘頭錘 | T1 | 8.5 / 1.3 | 無視 20% 護甲 | 改裝台：`spiked_head_iron` ×1 ＋ `tool_handle_reinforced` ×1 ＋ `pommel_lead` ×1 |
| M06 | `blunt_flanged_mace` | 精鋼法蘭錘 | T2 | 10.5 / 1.25 | 無視 35% 護甲、破盾 | 改裝台：`refined_steel_ingot` ×4 ＋ `heavy_steel_plate` ×1 ＋ `tool_handle_reinforced` ×1 ＋ `pommel_lead` ×1（亦出於軍械箱） |
| M07 | `blunt_warhammer_1h` | 單手戰錘 | T2 | 10.0 / 1.2 | 重擊擊退＋震飛敵人 | 改裝台：`heavy_hammer_head` ×1 ＋ `tool_handle_reinforced` ×1 ＋ `crossguard_iron` ×1 |
| M08 | `blunt_clump_flail` | 肢團骨節鏈枷 | T2 | 12.0 / 1.0 | 繞過盾牌格擋、距離+0.8 格 | 改裝台：`forged_chain_link` ×2 ＋ `tangled_knuckle` ×3 ＋ `spiked_head_iron` ×1 ＋ `tool_handle_reinforced` ×1 |
| M09 | `blunt_twohand_maul` | 雙手重戰錘 | T2 | 14.5 / 0.85 | 蓄力跳劈震地小範圍擊退 | 改裝台：`heavy_hammer_head` ×2 ＋ `weapon_shaft_steel` ×1 ＋ `pommel_lead` ×2 ＋ `alloy` ×3 |
| M10 | `blunt_lucerne_hammer` | 破城長柄錘 | T3 | 13.5 / 0.95 | 距離+1.5 格、對重甲/Boss 額外+30%傷 | 改裝台：`heavy_hammer_head` ×1 ＋ `tungsten_alloy` ×2 ＋ `weapon_shaft_steel` ×1 ＋ `rebar_fragment` ×2 |
| M11 | `blunt_crimson_crusher` | 紅室碎骨重錘 | T3 | 17.0 / 0.85 | 命中觸發紅室壓迫震波、可破紅室牆 | 異常煉成壇：`blunt_twohand_maul` ×1 ＋ `core_crimson_heart` ×1 ＋ `red_room_shard` ×6 ＋ `corrupted_marrow` ×2 |
| M12 | `blunt_thunder_colossus` | 巨骸雷鳴重錘（Boss） | T4 | 20.0 / 0.9 | 蓄力砸地釋放連鎖電弧麻痺周圍怪物 | 異常煉成壇：`blunt_lucerne_hammer` ×1 ＋ `core_arc_reactor`（雷電巨骸）×1 ＋ `high_voltage_coil` ×3 ＋ `void_alloy_ingot` ×2 |

---

## 三、西式冷兵器系（14 把）
> **體系定位**：平衡萬用、突刺穿甲、右鍵格擋彈反與多段範圍順劈，部分中世紀騎士武裝可於 Level 143 聖堡寶箱取得或自鍛。

| 編號 | ID | 中文名 | 階級 | 攻擊 / 攻速 | 特色機制 | 取得與細緻合成配方 |
|---|---|---|---|---|---|---|
| W01 | `west_scrap_knife` | 廢料生存小刀 | T0 | 4.5 / 2.4 | 極高攻速、可加速剝取怪物素材 | 3×3：`scrap_metal` ×2 ＋ `glass_tube_shard` ×1 ＋ `weapon_grip_wrap` ×1 |
| W02 | `west_hound_dagger` | 獵犬骨刃匕首 | T1 | 6.5 / 2.3 | 背刺傷害 ×1.8、附帶撕裂流血 | 改裝台：`hound_fang` ×3 ＋ `hound_sinew` ×2 ＋ `bone_fragment` ×1 ＋ `weapon_grip_wrap` ×1 |
| W03 | `west_gladius` | 精鋼短劍 | T1 | 7.5 / 1.8 | 攻守均衡、右鍵快速格擋 | 改裝台：`forged_iron_blank` ×1 ＋ `crossguard_iron` ×1 ＋ `weapon_grip_wrap` ×1 ＋ `pommel_lead` ×1（軍械箱可出） |
| W04 | `west_arming_sword` | 騎士長劍 | T2 | 9.5 / 1.6 | 完美彈反後下擊必暴擊 | 改裝台：`forged_steel_blank` ×1 ＋ `crossguard_iron` ×1 ＋ `tool_handle_reinforced` ×1 ＋ `pommel_lead` ×1 |
| W05 | `west_rapier` | 西洋細刺劍 | T2 | 8.0 / 2.2 | 距離+0.5 格、突刺無視 45% 護甲 | 改裝台：`stainless_rod` ×2 ＋ `forged_steel_blank` ×1 ＋ `crossguard_brass` ×1 ＋ `weapon_grip_wrap` ×1 |
| W06 | `west_viking_axe` | 維京單手戰斧 | T2 | 10.5 / 1.25 | 破盾、兼斧類拆木障 | 改裝台：`forged_steel_blank` ×1 ＋ `angle_iron` ×2 ＋ `tool_handle_reinforced` ×1 ＋ `hound_sinew` ×1 |
| W07 | `west_claymore` | 蘇格蘭大劍 | T2 | 12.5 / 1.1 | 寬角度橫掃（最多同時擊中 4 目標） | 改裝台：`forged_steel_blank` ×2 ＋ `crossguard_iron` ×1 ＋ `weapon_grip_wrap` ×2 ＋ `pommel_lead` ×1 |
| W08 | `west_executioner_axe` | 雙刃處刑巨斧 | T2 | 15.0 / 0.85 | 目標血量低於 35% 時斬殺傷害 +40% | 改裝台：`forged_steel_blank` ×2 ＋ `heavy_steel_plate` ×2 ＋ `weapon_shaft_steel` ×1 ＋ `corrupted_marrow` ×1 |
| W09 | `west_halberd` | 騎士長戟 | T3 | 13.0 / 1.05 | 距離+1.6 格、可刺可劈兼擊退衝鋒獵犬 | 改裝台：`forged_tungsten_blank` ×1 ＋ `weapon_shaft_steel` ×1 ＋ `long_limb_bone` ×1 ＋ `rusty_screws` ×4 |
| W10 | `west_zweihander` | 德國雙手巨劍 | T3 | 15.5 / 0.95 | 超大範圍揮砍＋蓄力橫掃擊退群怪 | 改裝台：`forged_tungsten_blank` ×2 ＋ `crossguard_brass` ×1 ＋ `weapon_grip_wrap` ×2 ＋ `pommel_lead` ×2 |
| W11 | `west_acid_scimitar` | 飛蛾酸液彎刀 | T3 | 12.0 / 1.65 | 命中附加腐蝕（削甲＋持續酸傷） | 改裝台＋化學：`forged_steel_blank` ×1 ＋ `acid_crystal` ×2 ＋ `acid_sac` ×2 ＋ `crossguard_brass` ×1 |
| W12 | `west_hallowed_blade` | 聖堡鍍金長劍 | T3 | 14.0 / 1.65 | 對暗影／悲屍／亡靈類傷害 +50% | Level 143 聖堡寶箱限定或異常煉成壇：`west_arming_sword` ×1 ＋ `gilded_steel_scrap` ×4 ＋ `purified_almond_essence` ×2 ＋ `rune_parchment` ×1 |
| W13 | `west_hotel_greatsword` | 憂鬱貴族巨劍（Boss） | T3 | 17.5 / 1.05 | 擊殺回復少量生命並驅散恐懼減益 | 異常煉成壇：`west_zweihander` ×1 ＋ `core_melancholy_bell`（酒店野獸）×1 ＋ `gilded_steel_scrap` ×3 ＋ `anomalous_mythril` ×2 |
| W14 | `west_void_greatsword` | 虛空裂隙巨劍（Boss） | T4 | 22.0 / 1.1 | 揮砍發射短距虛空劍氣穿透敵人 | 異常煉成壇：`forged_mythril_blank` ×2 ＋ `void_alloy_ingot` ×3 ＋ `core_void_weaver`（房東）×1 ＋ `reality_fragment` ×2 |

---

## 四、中式冷兵器系（16 把）
> **體系定位**：靈動連段、長距精準突刺、大範圍橫掃劈砍，並獨具**針對後室暗影／微笑者／精神異常怪的「驅邪鎮魂」特效**。

| 編號 | ID | 中文名 | 階級 | 攻擊 / 攻速 | 特色機制 | 取得與細緻合成配方 |
|---|---|---|---|---|---|---|
| C01 | `cn_wood_staff` | 齊眉木棍 | T0 | 5.0 / 1.7 | 距離+0.8 格、低成本防身逼退 | 3×3：`rotten_wood_strip` ×3 ＋ `damp_carpet_fiber` ×2 |
| C02 | `cn_iron_staff` | 精鋼齊眉棍 | T1 | 8.0 / 1.55 | 距離+1.0 格、連擊第 3 下範圍掃擊 | 改裝台：`iron_pipe` ×2 ＋ `alloy` ×2 ＋ `weapon_grip_wrap` ×2 |
| C03 | `cn_peachwood_sword` | 驅暗桃木劍 | T1 | 6.0（對暗影/微笑者 ×2.8！）/ 1.8 | **早期反制微笑者與影行者神裝**，命中爆發磷光 | 3×3：`treated_plank` ×2 ＋ `phosphor_powder` ×3 ＋ `almond_water` ×1 ＋ `cursed_fiber`（或 `synthetic_fabric`）×1 |
| C04 | `cn_ring_pommel_dao` | 漢代環首刀 | T1 | 8.5 / 1.65 | 單手持刀穩定快劈、耐度極高 | 改裝台：`forged_iron_blank` ×1 ＋ `alloy` ×1 ＋ `weapon_grip_wrap` ×1 ＋ `brass_ingot` ×1 |
| C05 | `cn_tang_hengdao` | 唐橫刀 | T2 | 10.5 / 1.75 | 拔刀首擊（切手 1.5 秒內）必暴擊 +35% | 改裝台：`forged_steel_blank` ×1 ＋ `crossguard_brass` ×1 ＋ `weapon_grip_wrap` ×1 ＋ `quench_oil` ×1（軍械箱可出） |
| C06 | `cn_han_jian` | 精鋼八面漢劍 | T2 | 10.0 / 1.85 | 雙刃靈動快刺＋右鍵架招減傷 50% | 改裝台：`forged_steel_blank` ×1 ＋ `stainless_rod` ×1 ＋ `crossguard_brass` ×1 ＋ `pommel_lead` ×1 |
| C07 | `cn_yanling_dao` | 雁翎刀 | T2 | 11.0 / 1.65 | 弧形刀身、連續命中遞增攻速 | 改裝台：`forged_steel_blank` ×1 ＋ `moth_wing_scale` ×2 ＋ `crossguard_brass` ×1 ＋ `weapon_grip_wrap` ×1 |
| C08 | `cn_miaodao` | 長柄雙手苗刀 | T2 | 13.0 / 1.35 | 距離+1.1 格、大範圍縱橫連斬 | 改裝台：`forged_steel_blank` ×2 ＋ `weapon_shaft_wood` ×1 ＋ `crossguard_brass` ×1 ＋ `hound_sinew` ×2 |
| C09 | `cn_nunchaku` | 重鋼雙節棍 | T2 | 7.5 / 2.6 | 超高連擊速、每第 4 下擊暈目標 1 秒 | 改裝台：`iron_pipe` ×2 ＋ `forged_chain_link` ×1 ＋ `weapon_grip_wrap` ×2 ＋ `lead_chunk` ×2 |
| C10 | `cn_red_tassel_spear` | 紅纓長槍 | T2 | 11.5 / 1.4 | 距離+1.8 格、衝刺突刺穿透多名敵人 | 改裝台：`forged_steel_blank` ×1 ＋ `weapon_shaft_steel` ×1 ＋ `synthetic_fabric` ×2 ＋ `red_room_shard`（或紅染料）×1 |
| C11 | `cn_meteor_hammer` | 肢團流星錘 | T2 | 12.5 / 1.15 | 右鍵可甩出 5 格遠距拉拽／砸擊敵人 | 改裝台：`forged_chain_link` ×3 ＋ `grasping_tendon` ×2 ＋ `lead_chunk` ×3 ＋ `tangled_knuckle` ×2 |
| C12 | `cn_sleeve_darts` | 暴雨袖箭／飛鏢（副手/主手） | T2 | 6.0 ×3 連發 | 瞬間連射 3 枚細鋼鏢（可塗死亡飛蛾強酸） | 改裝台：`stainless_rod` ×2 ＋ `metal_spring` ×2 ＋ `sheet_metal` ×2 ＋ `rusty_nail` ×6（彈藥可量產） |
| C13 | `cn_guandao` | 青龍偃月關刀 | T3 | 16.0 / 0.95 | 距離+1.6 格、重劈帶 180° 廣角破甲斬 | 改裝台：`forged_tungsten_blank` ×2 ＋ `weapon_shaft_steel` ×1 ＋ `crossguard_brass` ×2 ＋ `spiral_chitin` ×2 |
| C14 | `cn_fangtian_ji` | 方天畫戟（Boss 強化） | T3 | 17.5 / 1.15 | 距離+1.8 格；蓄力前衝可短暫定格敵人（搭配動畫師核） | 異常煉成壇：`forged_tungsten_blank` ×2 ＋ `weapon_shaft_steel` ×1 ＋ `core_stopmotion_gear`（動畫師）×1 ＋ `anomalous_mythril` ×2 |
| C15 | `cn_coin_sword` | 鎮魂八卦銅錢劍 | T3 | 11.5（對微笑者/紅室/精神怪 ×3.0！） | 右鍵插地展開 8 格驅暗結界（15 秒壓制暗影與紅室噁心） | 異常煉成壇：`brass_ingot` ×6 ＋ `cursed_fiber` ×3 ＋ `smiler_remnant` ×3 ＋ `purified_almond_essence` ×2 ＋ `dread_crystal` ×1 |
| C16 | `cn_alpha_tangdao` | 犬王血煞斬馬刀（Boss） | T4 | 21.0 / 1.2 | 融合犬王獠牙與黑絲菌束：極速流血＋吸血回復 | 異常煉成壇：`cn_miaodao` ×1 ＋ `core_alpha_fang`（獵犬王）×1 ＋ `core_void_weaver` ×1 ＋ `black_filament` ×4 ＋ `withered_heart` ×2 |
