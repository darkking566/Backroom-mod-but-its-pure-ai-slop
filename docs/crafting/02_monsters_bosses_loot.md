# 02. 原生後室怪物全圖鑑、Boss 清單、專屬掉落與寶箱表

所有實體均依循本模組統一標準：**Blender 路線 A（精細扭曲純長方體骨骼 + `BoxRig` JSON + 低飽和寫實髒污貼圖）**，實作時統一歸於 `src/main/java/dev/noclip/backrooms/monsters/<怪物名>/`。

---

## 一、常駐原生後室實體總表（共 26 種）

> 標註 `[已實作]` 者已在專案程式碼中存在，僅需補上／擴充專屬戰利品表。

### 第一批：核心原生怪物（Level 0 ～ Level 6，優先實作）

| 編號 | 實體 ID | 中文名 | 主要出沒樓層 | 行為特色 | 專屬掉落素材（ID 與用途） |
|---|---|---|---|---|---|
| E01 | `hound` `[已實作]` | 獵犬（Hound） | Level 1(停電), 2, 3, ! | 四足潛行、蹲低蓄力撲咬；盯視可短暫威懾 | `hound_fang`（獵犬獠牙 ×1–2：骨刃/尖刺武器）、`hound_matted_hair`（濕黑長毛 ×1–3：深色織布/偽裝）、`hound_sinew`（狂暴筋腱 ×1：弓弦/武器強化/鏈枷） |
| E02 | `smiler` `[初版已有]` | 微笑者（Smiler） | Level 1(停電), 2, 3, 6, 54 | 藏身黑暗、被光源與奔跑吸引；需用驅暗／桃木／強光反制 | `smiler_remnant`（微笑殘光 ×1：光暗魔法核心）、`shadow_mote`（暗影微粒 ×1–2：消音/暗屬性武器）、`dread_crystal`（恐懼凝晶，20%：高階法器） |
| E03 | `marauder` `[初版已有]` | 掠奪者（Marauder） | Level 1, 2, 11(荒廢區) | 敵對人類掠奪者，持弩與戰斧成群出沒 | `alloy`（既有 ×1–2）、`weapon_parts_scrap`（破損武器零件 ×1）、`ammo_9mm`（偶掉 2–5 發） |
| E04 | `faceling_adult` | 成年無面靈（Faceling） | Level 4, 5, 11, 6.1, 52 | 中立（被攻擊才反擊），常模仿人類日常行為 | `faceless_membrane`（無面皮膜 ×1：面具/偽裝道具）、`worn_suit_cloth`（破舊西裝布 ×1–2：高階輕甲內襯） |
| E05 | `faceling_child` | 幼年無面靈（Child Faceling） | Level 2, 3, 5, 9 | 體型小、成群結隊具高度惡意與偷襲傾向 | `memory_sliver`（失真記憶碎片 ×1–2：精神類魔法）、`rusty_nail`（隨身雜物 ×1–2） |
| E06 | `skin_stealer` | 竊皮者（Skin-Stealer） | Level 1(深處), 2, 3, 4, 5 | 平時緩慢徘徊、飢餓時高速獵殺並偽裝成玩家或人形（流半透明血） | `thick_disguise_hide`（偽裝厚皮 ×1–2：竊皮斗篷/中階護甲）、`morph_gland`（變形腺體，35%：特殊藥劑/偽裝器）、`translucent_blood_vial`（半透明血液 ×1：異常煉成觸媒） |
| E07 | `deathmoth_male` | 雄性死亡飛蛾 | Level 2, 3, 5 | 體型較小、相對溫和，被光源吸引 | `moth_wing_scale`（蛾翼鱗粉 ×1–2：照明劑/輕量羽化附魔） |
| E08 | `deathmoth_female` | 雌性死亡飛蛾（巨型） | Level 2, 3, 5(鍋爐房) | 體型龐大、極具攻擊性，可飛行並噴吐強酸 | `acid_sac`（強酸囊 ×1–2：蒸餾強酸/酸液彈/酸液魔典）、`acid_crystal`（酸液結晶，30%：酸蝕武器塗層）、`moth_chitin`（耐高溫幾丁質 ×1–2：隔熱防具） |
| E09 | `clump` | 肢團（Clump） | Level 2, 3, 8 | 由無數扭曲手臂組成的球團，躲在暗角用超長手臂拉扯玩家 | `tangled_knuckle`（糾纏骨節 ×1–2：重鈍器/鏈枷錘頭）、`grasping_tendon`（抓握肌腱 ×1–2：鉤爪/流星錘/拉扯法器） |
| E10 | `duller` | 鈍人（Duller） | Level 2, 3, 4(陰影處) | 灰白無五官、移動完全無聲、接觸會剝奪感官與聲音 | `ash_residue`（灰暗殘渣 ×1–2：潛行塗層）、`silence_core`（靜默核，30%：槍械消音器核心/潛行靴） |
| E11 | `window_entity` | 窗戶（Window） | Level 1, 4, 5, 188 | 偽裝成透光窗戶誘惑靠近，靠近後伸出黑影抓取 | `false_glass`（虛假玻璃片 ×1–2：幻術武器）、`lure_cornea`（誘捕眼膜，35%：高倍光學狙擊鏡/窺視鏡） |

### 第二批：中高樓層與特色原生實體（Level 7 ～ 隱秘樓層）

| 編號 | 實體 ID | 中文名 | 主要出沒樓層 | 行為特色 | 專屬掉落素材（ID 與用途） |
|---|---|---|---|---|---|
| E12 | `partygoer` | 派對客（Partygoer） | Level Fun, 283, ! | 黃色高大笑臉人形，移動詭異、近戰高傷並施加混亂／感染減益 | `yellow_party_hide`（黃色派對革 ×1–2：特殊防具）、`smiley_patch`（笑臉面片 ×1：精神干擾道具）、`party_balloon_rubber`（異常氣球皮 ×1–2：彈力/爆裂道具） |
| E13 | `party_host` | 派對主人（Party Host） | Level Fun(核心室) | 固定於天花板或大廳中央指揮派對客，釋放精神幻覺 | `host_nerve_cord`（主腦神經束 ×1）、`dread_crystal` ×2 |
| E14 | `wretch` | 悲屍／疫病怪（Wretch） | Level 0(極深), 2, 3, 6, 8 | 徹底喪失理智的變異流浪者，紅腫狂暴、極速衝鋒 | `withered_heart`（枯萎心臟，40%：血煞/暗黑兵器）、`corrupted_marrow`（腐化骨髓 ×1–2：高階淬火料） |
| E15 | `bacteria` | 細菌（Bacteria，經典衍生） | Level 0(極深/超大廳), 0.2 | 細長漆黑扭曲絲狀人形，伴隨高頻撕裂咆哮高速追擊 | `black_filament`（黑絲菌束 ×2：強化複合纖維/黑刀刃）、`static_screech_core`（高頻噪聲核，35%：聲波槍與干擾鈴） |
| E16 | `shadow_stalker` | 影行者（Shadow Stalker） | Level 0(陰影實化), 6, -1 | 在完全無光處實質化突襲，怕強光與磷火 | `shadow_mote` ×2–3、`void_dust` ×1 |
| E17 | `neighborhood_watch` | 鄰里守望者（Neighborhood Watch） | Level 9 | 極高細長四肢蜘蛛狀巡邏巨體，在夜街高處偵測視野 | `watch_ocular_lens`（巨型夜視晶體 ×1：夜視瞄具/頭盔）、`rebar_fragment` ×2、`long_limb_bone`（巨肢長骨 ×2：長戟/狙擊槍架） |
| E18 | `animation_entity` | 動畫實體（Animation） | Level 94(夜晚) | 夜晚出沒的定格抽動卡通偶怪物，攻速不規則 | `cel_paint_shard`（定格顏料片 ×1–2：時間遲緩懷錶材料）、`animation_joint`（異常關節軸 ×1–2） |
| E19 | `lost_cloak` | 失落篷蝠（The Lost Cloak） | Level 4.3, 6 | 漂浮空中的破舊布篷狀實體，包裹目標造成窒息 | `spectral_cloth`（幽靈篷布 ×1–2：漂浮披風/減重附魔） |
| E20 | `hydrolitis_spawn` | 水瘟影體（Hydrolitis） | Level 37.1, 7, -2 | 潛伏於深暗水域的腐蝕性水生感染體 | `plague_ichor`（水瘟膿液 ×1–2：劇毒塗層/抗毒藥劑原料）、`ceramic_shard` ×1 |
| E21 | `spiral_crab` | 樓梯甲殼人／天魚 | Level 54 | 棲息於無盡螺旋樓梯井壁的甲殼與浮空實體 | `spiral_chitin`（螺旋甲殼 ×1–2：重型中世紀板甲/盾牌） |
| E22 | `hallowed_paladin` | 聖堡騎士／女巫 | Level 143 | 巡邏城堡的鎧甲守衛與施法者 | `gilded_steel_scrap`（鍍金鋼片 ×1–2：西式聖劍/宮廷武器）、`rune_parchment`（符文羊皮紙 ×1） |
| E23 | `game_pawn` | 遊戲棋偶 | Level 389 | 遊戲大師召喚的機械／木偶棋子士兵 | `brass_ingot` ×1、`steel_gear` ×1–2、`luck_token`（命運籌碼，30%） |

### 非殺害／友善互動原生實體（3 種，靠互動交易／贈禮拿稀有素材）

| 編號 | 實體 ID | 中文名 | 所在樓層 | 互動取得獎勵（不建議擊殺） |
|---|---|---|---|---|
| N01 | `stray_cat` `[已實作]` | 補給小貓 | Level 1 | 餵魚或杏仁水有機會叼來 `alloy`、`blueprint_scrap` 或幸運掛飾 |
| N02 | `kitty` | Kitty | Level 974 | 贈送可愛物品後安全傳送離開，並贈予 `pastel_charm`（夢幻護符：免疫精神混亂材料） |
| N03 | `blanche` | Blanche（檔案館主） | Level 906 | 交付探索日記／藍圖殘頁，換取 `astral_parchment`（星界羊皮紙：頂級魔導書必備）與任意已開放樓層傳送 |

---

## 二、十大樓層 Boss 清單（必掉 Boss 寶箱＋專屬核心＋藍圖）

| 編號 | Boss ID | 中文名 | 所在樓層與觸發條件 | 必掉專屬核心（100%） | 專屬武器／裝備解鎖 |
|---|---|---|---|---|---|
| B01 | `boss_red_warden` | 血廊守衛（Red Room Core） | Level 0 紅室極深處（破開紅晶祭壇觸發） | `core_crimson_heart`（血廊之心） | 解鎖 **紅室血咒大鐮**、**紅室碎骨重錘**、Tier 3 **紅室破壁錘** |
| B02 | `boss_alpha_hound` | 畸變獵犬王「群首」 | Level 1 連續三次停電未修發電機觸發狩獵事件 | `core_alpha_fang`（犬王斷牙核心） | 解鎖 **犬王雙刃唐刀**、**狂暴獵裝**、Tier 2 強化工具藍圖 |
| B03 | `boss_pipe_amalgam` | 蒸氣巨肢團「管廊絞肉機」 | Level 2 高溫死巷蒸氣室 | `core_steam_turbine`（高壓蒸氣核） | 解鎖 **焦熱蒸氣火焰杖**、**噴火器**、**肢團骨節重鏈枷** |
| B04 | `boss_volt_colossus` | 雷電巨骸（Overload Colossus） | Level 3 主變電站核心房 | `core_arc_reactor`（過載電弧核心） | 解鎖 **電磁線圈軌道槍**、**高壓雷弧法杖**、**巨骸雷鳴重錘** |
| B05 | `boss_brood_deathmoth` | 焦熱母蛾女皇 | Level 5 鍋爐房最底層 | `core_acid_queen`（焦酸女皇腺核） | 解鎖 **死亡飛蛾強酸魔典**、**腐蝕連發霰彈槍**、隔熱母蛾甲 |
| B06 | `boss_hotel_beast` | 酒店之獸（The Beast of Level 5） | Level 5 貝弗莉室暗面深廳 | `core_melancholy_bell`（憂鬱青銅鈴核） | 解鎖 **酒店野獸召喚鈴**、**貴族鍍金雙手巨劍** |
| B07 | `boss_tiny_leviathan` | 七層之物「Tiny」 | Level 7 深海裂谷水域 | `core_abyssal_scale`（深淵海鱗核） | 解鎖 **深海冰寒權杖**、**反器材穿甲重狙**、深海潛水戰甲 |
| B08 | `boss_the_animator` | 動畫師（The Animator） | Level 94 透明浮空城堡王座 | `core_stopmotion_gear`（定格時鐘核） | 解鎖 **時間遲緩懷錶**、**方天畫戟（時停強化）** |
| B09 | `boss_game_master` | 遊戲大師（Entity 99） | Level 389 中央儲藏大廳對局敗後／宣戰觸發 | `core_fate_die`（命運二十面骰核） | 解鎖 **遊戲大師命運骰杖**、**聲波脈衝震盪槍** |
| B10 | `boss_the_landlord` | 房東／虛空織者（終局 Boss） | Th3 Sh4dy Gr3y 深階／Level 998 黑域祭壇 | `core_void_weaver`（虛空織核） | 解鎖 **Tier 4 虛空裂解器**、**虛空裂隙巨杖**、**虛空裂隙巨劍**、**九環血煞斬馬刀** |

---

## 三、五大樓層寶箱與戰利品池

| 寶箱 ID | 中文名 | 出現位置與開啟條件 | 主要產出內容 |
|---|---|---|---|
| `supply_crate` `[既有]` | 補給箱 | Level 1 停車區、Level 4、Level 20（徒手可開） | 食物、`almond_water`、`alloy`、`phosphor_powder`、基礎工具零件、偶出 Tier 0–1 簡易武器（鐵管、小刀、木棍） |
| `eyed_crate` `[既有]` | 有眼睛的箱子 | Level 1、Level 2、Level 5（右鍵開後閉眼） | 較高量 `almond_water`、`alloy`、`blueprint_scrap`、`microchip`、低機率直接開出中階冷兵器或土製槍成品 |
| `locked_armory_crate` | 上鎖軍械箱 | Level 1 機房、Level 2、Level 3、Level 11 警局（**需 Tier 1 撬棍或開鎖器開啟**） | 正規槍械零件（槍管/機匣/彈匣）、各式彈藥、戰術配件、中/西式成品武器（短劍、唐橫刀、左輪、泵動霰彈槍） |
| `anomaly_vault_chest` | 異常密室寶箱 | 藏於封門木板、加固隔牆或紅室／水晶隱藏牆後方（**需 Tier 2–3 工具破壁進入**） | `anomaly_crystal`、`red_room_shard`、`purified_almond_essence`、`anomalous_mythril`、高階魔法法器、特殊武器藍圖（聖堡騎士劍、桃木驅暗劍強化版、西洋刺劍） |
| `boss_trophy_chest` | Boss 傳說寶箱 | 擊敗任一樓層 Boss 後原地生成（必掉） | 對應 Boss 專屬核心（100%）＋ 1 把對應樓層傳說／高階成品武器或專屬圖紙 ＋ 大量精煉鋼／鎢鋼／純化杏仁精粹 |
