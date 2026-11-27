# Level 4 興趣點（POI）企劃

- **定位**：承接 [`README.md`](README.md) 的多層雨霧辦公大樓架構（`OPEN_OFFICE`、`SUITE`、`ATRIUM`、`LIGHTWELL`、`DARK_WING`、`UNFINISHED_WING`、`ELEVATOR_LOBBY`、`AUDITORIUM`、`MEZZANINE`、天花板 `plenum` 夾層、地下 `subTunnel` 機電廊、`BALCONY`、高低 `skybridge` 與 M.E.G. Omega／Amor Incrementum／T.B.D. 三大陣營），新增 **20 種不與既有物件重複、分 4 個稀有度等級**的環境敘事與互動 POI。
- **工程原則**：
  - 純函式地形選點放在 `Level4PoiPlan.java`（依世界種子與區域座標確定性產生），嚴格避開 `OMEGA_BASE`、`AMOR_CAMP`、`TBD_ROOM`、`SOLID_CORE`、`OUTSIDE`、既有出口 `PlacedExit` 與陽台門洞，絕不切斷連通性。
  - 最大化沿用既有 `Level4Blocks`、`BackroomsProps` 與 `BackroomsItems`，並補齊 **3 款現代辦公／工業收納容器方塊**（`office_supply_crate`、`industrial_toolbox`、`office_steel_safe`）取代過往借用的原版木桶。
  - 所有一次性物資搜刮與 Amor 小壇仇恨狀態持久化於伺服器 `SavedData`（`level4_poi_state`），防止重登重複刷取。

---

## 一、普通（Common）— 高頻率環境敘事與微補給（6 種）

| # | 常數（`PoiKind`） | 名稱 | 出現位置 | 視覺長相與構成 | 玩法與收益／風險 |
|---|---|---|---|---|---|
| **1** | `BREAKROOM_CORNER` | 被拆剩的茶水間殘角 | `SUITE`／`OPEN_OFFICE` 牆角 | 潮濕地毯、石英半磚流理台、水槽（`CAULDRON`）與塑膠盆栽（`PROP_POTTED_PLANT_PLASTIC`） | 右鍵流理台（限 1 次）讀便條並得空玻璃瓶、潮濕廢紙碎片，18% 機率摸出 1 瓶 `almond_water` |
| **2** | `MEG_CHALK_WALL` | M.E.G. 粉筆／噴漆路標牆 | `OPEN_OFFICE` 柱列／`skybridge` 入口 | 牆面掛 **[NEW]** `meg_chalk_sign`（附正常版與被爪痕劃破 `slashed` 變體） | 正常版右鍵顯示最近 `OMEGA_BASE` 方向距離並微幅回理智（60s 冷卻）；鄰近 `DARK_WING` 或陷阱窗時出現爪痕版，右鍵發出警告並扣少許理智 |
| **3** | `SEALED_MEETING_ROOM` | 貼滿封條的失修會議室 | `SUITE`（1×1／2×1 隔間） | 門口掛 **[NEW]** `caution_tape_barrier` 黃黑警戒封條，房內擺折疊桌、辦公椅、檔案櫃與損壞電腦 | 右鍵或打破扯開封條後進入拆解家具；右鍵會議桌可讀探索隊短訊，指出附近游離出口或配電箱方向 |
| **4** | `CEILING_DRIP_SPOT` | 天花板滴水接水處 | `DARK_WING`／`OPEN_OFFICE`（優先在 `plenum` 正下方） | 吊頂缺一塊露出管線或直接通往上方 `plenum` 夾層、地上擴大的 `office_damp_carpet` 與接水鍋釜 | 定期滴水粒子與水聲；拿空玻璃瓶右鍵只裝得普通濁水（`murky_water_vial`），抬頭可發現通往 `plenum` 的入口 |
| **5** | `ACTIVE_TERMINAL_ISLAND` | 未關閉終端的雙人辦公島 | `OPEN_OFFICE` 格子間內 | 兩組對坐的折疊桌＋老電腦（`PROP_DESKTOP_COMPUTER`）＋辦公椅 | 右鍵螢幕讀出詭異公司排班公告，並一次性拆出 `ballast_coil`／`old_circuit_board` 或 `lost_coin` |
| **6** | `WINDOW_PAINT_CART` | 半途而廢的塗窗作業推車 | 外牆廊道／`LIGHTWELL` 外圈 | 5 格窗帶一半為 `office_blacked_window`、另一半保留 `office_trap_window`／`office_rain_window`；旁放濕地毯、黑漆桶（`CAULDRON`）與紅漆工具箱（`industrial_toolbox`） | 右鍵工具箱一次性取得 2 個 `black_dye` 與 1 個 `charcoal`，可直接將旁邊沒塗完的陷阱窗塗黑化解 |

---

## 二、少見（Uncommon）— 具互動機關與支線價值（6 種）

| # | 常數（`PoiKind`） | 名稱 | 出現位置 | 視覺長相與構成 | 玩法與收益／風險 |
|---|---|---|---|---|---|
| **7** | `MISSING_WANDERER_CAMP` | 失蹤探索者的過夜營位 | `BALCONY` 內角／`MEZZANINE` 二樓／`UNFINISHED_WING` | 綠地毯睡袋、微亮提燈、散落杏仁水瓶與 `office_supply_crate` | 右鍵物資箱（限 1 次）給 `bandage`、雜物零件（`insulating_tape`／`scrap_metal`）與失蹤者手記 |
| **8** | `LOCKED_PBX_ROOM` | 上鎖的電話總機／交換機房 | 偏僻 `SUITE`／`subTunnel` 側室 | 鐵網牆＋上鎖鐵門、內有雙配電開關箱（`office_breaker` 與 `industrial_fuse_box` 共用冷卻）、電話、`industrial_toolbox` 與機櫃方塊 | 持撬棍（`crowbar`）或 Tier 2+ 工具撬開鐵門；搜出 `capacitor`／`copper_wire`／`battery_cell`，扳動任一開關可切換鄰近 `DARK_WING` 燈光 |
| **9** | `DELAYED_REFLECTION_MIRROR` | 延遲倒影的轉角暗窗 | `LIGHTWELL` 轉角／`MEZZANINE` 護欄旁 | **[NEW]** `office_dark_mirror`（3 態 `silhouette=0..2`，僅中央 2 格高拼接成單一黑影，兩側與高處皆為純暗鏡） | 靠近 5 格內連續凝視 ≥3 秒扣少許理智並在身後響起單獨腳步聲；高感知玩家有機會透過鏡面看出 `Level 13` 出口方向 |
| **10** | `AMOR_FIELD_SHRINE` | Amor Incrementum 野外暗角小壇 | `DARK_WING` 深處或偏僻 `SUITE` | 挖開 1 格辦公地毯嵌入 1 格 `amor_planter`（Level 0 黃壁紙基質）並長出 1～2 朵發光菌類 | 可安全採摘少量菌類；若玩家敲毀小壇培養槽，會在 `Level4Bases` 標記褻瀆仇恨，靠近 Amor 陣營將直接敵對（直到獻上聖物才和解） |
| **11** | `TBD_OVERFLOW_CAGE` | T.B.D. 溢出的封鎖貨籠 | `UNFINISHED_WING`／`ELEVATOR_LOBBY` 死角 | 完整封閉鐵網圍牆與頂網，內放 `tbd_hoard_crate`、`tbd_curio_shelf`、`office_supply_crate` 與 `office_steel_safe` | 75% 開出 T.B.D. 奇物（`Level4Items.CURIOS`）或普通零件，25% 翻出 `lost_coin`／`alloy` |
| **12** | `PLENUM_MAINTENANCE_HUB` | 懸吊式風管交會維修台 | `plenum`（天花板夾層內部交會處） | 3×3 拓寬鐵柵／混凝土檢修台、吊掛昏黃檢修燈、中央地板鐵柵窺視口與 `industrial_toolbox` | 獎勵爬進 `plenum` 探索的玩家：開出 `iron_pipe`／`steel_gear`／`rusty_screws`，並可透過地板格柵安全俯瞰下方 `DARK_WING` 怪物 |

---

## 三、稀有（Rare）— 強氛圍反差與路線轉折（5 種）

| # | 常數（`PoiKind`） | 名稱 | 出現位置 | 視覺長相與構成 | 玩法與收益／風險 |
|---|---|---|---|---|---|
| **13** | `STORM_BREACH_CORRIDOR` | 暴風雨決堤的碎窗走廊 | 高架 `skybridge` 或大樓外緣廊道 | 保留 1 格高散熱片矮窗台（防墜落），上方窗格破開、地面大片 `office_damp_carpet`、燈管熄滅 | 強化暴風雨聲與閃電粒子、對側 `office_trap_window` 風險較高；地上遺落被沖刷出的 `battery_cell`／`glass_bottle`／散落杏仁水瓶 |
| **14** | `STALLED_FREIGHT_ELEVATOR` | 卡死在半層的生鏽懸停貨梯 | `ELEVATOR_LOBBY` 其中一道垂直井道 | 深灰金屬導軌＋裂紋混凝土井壁、雙條垂落鋼纜＋懸吊作業燈、破口黃黑封鎖線、掀開的頂蓋與 `office_supply_crate` | 須藉助側面階梯／維修梯跳上梯頂再鑽入內部；開出較多 `alloy`、`bandage` 與 `purified_almond_essence` |
| **15** | `EVACUATED_OUTPOST` | M.E.G. 撤離前哨殘點 | 遠離 `OMEGA_BASE` 之 `AUDITORIUM` 旁或獨立 2×2 翼區 | 歪斜 `omega_barricade`、塗黑窗、1 張臨時軍床、拆解台與殘留 `omega_locker` | **無 NPC 駐守**之次級避風港：右鍵軍床僅回復血量與理智（120s 冷卻），**絕不覆蓋 Omega 基地重生點**；置物櫃可拿一次殘留物資 |
| **16** | `CORRUPTED_RED_ATRIUM` | 變質「紅霧泉眼」中庭 | 低機率（~14%）取代遠離起點的 `ATRIUM` | 吊頂昏暗壞燈、枯萎植物、中央水池換成 **[NEW]** `corrupted_fountain_pool`（暗沉泛紅水池） | 直接飲用或普通裝瓶會中毒（`POISON`＋`NAUSEA`＋扣理智）；蹲下右鍵或高感知識破後可取 1 份變質樣本（沿用既有 `toxic_spore_sac`／`red_room_shard`） |
| **17** | `SUBTUNNEL_ABYSS_DOOR` | 地下機電盡頭的深淵暗門 | `subTunnel` 機電廊中段南側地下室 | 深灰金屬冷水機塔＋紅漆 `industrial_toolbox`、鐵欄門框打開通往南側深處的三格寬懸空石階（兩側鏤空深淵黑洞＋鐵欄扶手＋懸吊燈籠），最底端接 `office_basement_71` 門 | 可拆得 `electric_motor`／`pipe_valve`；凝視深淵黑坑聽見金屬回音，踏下深淵石階盡頭可通往第 `"2"` 或 `"71"` 層 |

---

## 四、極稀有／傳說（Ultra-Rare）— 改變單局記憶的地標奇觀（3 種）

| # | 常數（`PoiKind`） | 名稱 | 出現位置 | 視覺長相與構成 | 玩法與收益／風險 |
|---|---|---|---|---|---|
| **18** | `FROZEN_FRIDAY_1998` | 「1998 最後一個週五」靜止樓層 | 超低機率（~4%）取代一整座 `SUITE`＋相鄰 `OPEN_OFFICE` | **全區多格皆擺滿**整齊 `office_desk`＋桌上 `prop_desktop_computer`＋對坐 `prop_office_chair`＋盆栽＋檔案櫃＋飲水機＋`office_steel_safe`＋未拆封杏仁水瓶群 | 踏入後**絕對無怪**且**全區徹底靜音（關雨聲與冷氣嗡鳴）**；右鍵保險箱得 `calendar_1998`／`lost_coin`／杏仁水，點桌上電腦讀未寄出週五排班郵件 |
| **19** | `INVERTED_MIRROR_LIGHTWELL` | 上下鏡像倒錯的「天窗深淵庭」 | 超低機率（~8%）取代一座完整 `LIGHTWELL` | **10 格挑高鏡像迴廊＋人眼高度倒掛天井架構**：`y=42` 倒掛地毯與倒懸燈管，中段嵌 `office_dark_mirror` 對稱軸、天井內懸掛倒立窗框與支柱，`MIRROR_13` 出口框於暗鏡拱門內 | 視覺奇觀地標；環廊死角固定藏有一條**永不消失**的 `MIRROR_13` 安全出口與 `office_supply_crate` |
| **20** | `EXECUTIVE_BROADCAST_SUITE` | 仍在運作的總裁室與無源廣播廳 | 超低機率（~10%）附著於 `AUDITORIUM` 後側或高層夾層 | 完整封閉式深橡木包廂（原木角柱＋拱門維持動線）、**面朝簡報廳之 `3×2` 落地玻璃觀景窗**、整排發光 **[NEW]** `executive_broadcast_screen` 無源廣播牆、深木倒階梯主官長桌＋紅地毯＋**[NEW]** `executive_red_phone`＋`office_steel_safe` | 接聽紅色電話觸發專屬無源廣播事件（指路遠處稀有地標／永久出口，或進行理智考驗），點擊廣播螢幕讀出無源波形提示；打開保險箱得濃縮杏仁精華、合金與高階零件 |

---

## 五、分批實作與驗收排程

| 批次 | 包含 POI | 新增方塊 | 狀態 |
|---|---|---|---|
| **Batch 1** | #1, #2, #3, #4, #5, #6, #7, #10（8 種） | `meg_chalk_sign`、`caution_tape_barrier` | 【已實作】 |
| **Batch 2** | #8, #9, #11, #12, #14, #15（6 種） | `office_dark_mirror` | 【已實作】 |
| **Batch 3** | #13, #16, #17（3 種） | `corrupted_fountain_pool` | 【已實作】 |
| **Batch 4** | #18, #19, #20（3 種） | `executive_red_phone` | 【已實作】 |
| **Route A 精修** | 消滅全部 11 處原版木桶＋#8/#9/#11/#18/#20 精緻化 | `office_supply_crate`、`industrial_toolbox`、`office_steel_safe`、`office_dark_mirror`（3 態剪影） | 【已實作】 |
| **Route B 精修** | 空間格局重做（#14/#17/#18/#19/#20） | `executive_broadcast_screen` | 【已實作】 |

每批驗收基準：
1. `Level4PoiPlanTest` + `Level4LayoutTest` 全部通過（機率覆蓋、零據點／出口重疊、100% 連通性）。
2. `.\gradlew.bat build --no-daemon` 全部通過。
3. `.\gradlew.bat runClientGameTest -Plevels=4 -Psections=pois,blocks --no-daemon`（含無光影與必要時 `-Pshaders` 雙驗證），逐一檢視實機截圖確認無懸空／穿模／破牆／缺貼圖。
