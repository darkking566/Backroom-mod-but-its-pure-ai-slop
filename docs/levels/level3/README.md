# Level 3：發電站（Electrical Station）

- **狀態**：已實作（第一版骨架；已規劃完整升級藍圖，照 `latest:level-3` 分批補齊）
- **程式**：`src/main/java/dev/noclip/backrooms/level3/`（入口是 `Level3.java`）
- **維度**：`noclip_backrooms:level_3`；出口登記鍵 `"3"`（Level 2 噴了紅色「3」的門會接到這裡）
- **貼圖與模型**：`textures/block/level3/`、`models/block/level3/`，由 `tools/make_level3.py` 產生；預覽在 `docs/levels/level3/textures.png`
- **參考來源**：[中文維基 latest:level-3](https://backrooms-wiki-cn.wikidot.com/latest:level-3) 與 [Wikidot Level 3](http://backrooms-wiki.wikidot.com/level-3)（作者 Natedagreat563，生存難度 4）。
- **直接進入**：先到 Level 2，或在測試世界用 `/execute in noclip_backrooms:level_3 run tp @s 2.5 33 2.5`（落點不一定在走道上）

## 照 Wiki 做了什麼

| Wiki 的描述 | 遊戲裡 |
|---|---|
| 磚牆顏色不一，地板是積灰的水泥，天花板是昏暗的日光燈 | 牆面依區塊分成橘紅磚、褐磚、米色釉面磁磚（下面一圈深綠牆裙）、灰色空心磚；地板積灰 |
| 走道尺寸「極度不一致」，有些又窄又矮，得彎腰、爬行或側身才過得去 | 迷宮的每一格各有一種尺寸：寬（4 格寬 4 格高）、一般（2 寬 3 高）、窄（1 寬 2 高）、矮（2 寬但只有 1.5 格高，**要蹲著走**）。兩格相接時取比較小的那邊 |
| 牆上佈滿古老的電器：電線、電纜、電箱、機器 | 牆上有電線管、電箱，偶爾有冒火花、碰了會觸電的插座 |
| 牆上的管子流著滾燙、有毒的黑色淤泥 | 沿牆的黑泥管，有的會滴黑泥；寬走道偶爾有一灘黑泥，踩到會燙傷、噁心、走不快 |
| 掉落的電線、會爆裂傷人的閃爍燈管、會爆炸的發電機 | 寬走道偶爾有通電的斷線，踩到會觸電；閃爍的燈管偶爾會在你頭上爆裂；靠近發電機時，它偶爾會過載，幾秒後爆炸 |
| 大部分區域被打不壞的鐵柵擋住；有些鐵柵有上鎖的門，鎖可以撬開 | 多出來的通道約一半被白色或黑色鐵柵擋住（看得到、過不去，也打不壞）；少數必經的路上是鐵柵門，**一直右鍵就能撬開鎖**（每下有機會成功，撬 6 下就開） |
| 被窺視感熱點：又暗又窄的區段，讓人害怕、聽到哭聲、尖叫、笑聲、低語、呼吸聲 | 燈全壞的窄道和矮道是熱點：走進去會「覺得有東西在看著你」，背後不時傳來怪聲，偶爾眼前一暗 |
| 紫化：卡進牆裡的實體讓牆和身體變紫 | 牆上零星的紫白大理石紋磚（微微發光） |
| 發電機房：大房間，滿是電器和發電機 | 中間是發電機（頂上有陶瓷絕緣子），牆邊一排綠色配電盤，地上有斷線 |
| 組裝線：開放的大工廠，輸送帶生產東西 | 三排輸送帶（站上去會被帶著走），上面有零件箱，兩端是沖壓機，天花板垂著吊鉤 |
| 鍋爐房：黑泥的來源 | 黃銅鍋爐和閥門，地上一灘灘黑泥，頭頂是黑泥管 |
| 聖所：棕色磚牆的空曠教堂，希臘羅馬式柱子，台座上的天使雕像；實體看到雕像會害怕離開，是唯一天然的避難所 | 兩排螺旋柱、彩繪玻璃窗、中央台座上的青銅天使。**獵犬靠近天使就會逃走**，聖所附近也不會出現獵犬 |
| Base Gamma：M.E.G. 在這裡最大的基地 | 有 M.E.G. 標誌、行軍床、補給箱的亮燈據點，獵犬不會在附近出現 |
| 實體：獵犬會伏擊、成群行動 | 待在暗處時，偶爾 2～3 隻獵犬從後方追上來 |
| M.E.G. 在 Base Gamma 周圍立了許多路標，幫迷路的人找到基地 | Base Gamma 附近 14 格內的寬走道牆上，有藍色的「M.E.G. BASE GAMMA →」路標，順著走就到據點 |
| M.E.G. 在所有已知的熱點都裝了警告標誌 | 通往被窺視感熱點的走道口，牆上有黃黑相間的「CAUTION / SCOPOPHOBIA HOTSPOT AHEAD」警告牌 |

## 出口（照 Wiki 的 Entrances and Exits）

| 出口 | 通往 | 說明 |
|---|---|---|
| 電梯 | Level 4 或 Level 5（隨機） | 黃銅格柵門，上面有樓層指針。走道牆上，最難找 |
| Base Gamma 的門 | Level 11 | 在 Base Gamma 據點的牆上，有 M.E.G. 標誌和密碼鎖 |
| 生鏽的車 | Level 69 | 偶爾出現的一間房裡停著一台車，上車（右鍵）就到 Level 69 |

越往下的樓層越難找（每個 96×96 區域的機率：0.06＋0.18×log10(1＋樓層)）。這些樓層都還沒做，會顯示「尚未開放」；Level 4、5 做好後電梯就會接通（只開了一層時，電梯會去開著的那層）。

Wiki 沒有寫從 Level 3 回 Level 2 的路，所以目前進來後只能往下走（或死亡重生）。

## 畫面規格（照 [`docs/look/SOP.md`](../../look/SOP.md)）

| 項目 | 內容 |
|---|---|
| 材質 | 既有 71 張基礎貼圖（`textures/block/level3/`）。牆面：橘紅磚、褐磚、米色釉面磁磚＋深綠牆裙、灰色空心磚、紫化磚、M.E.G. 金屬牆；地板：積灰水泥；天花板：暗管水泥、矮天花板；金屬與機具：黑白鐵柵、電線管、電箱、黑泥管、綠色配電盤、發電機、黃銅鍋爐、輸送帶、青銅天使、彩繪玻璃、生鏽的車、黃銅電梯。寫實材質包沿用 `tools/.cache/ambientcg` 既有 CC0 素材（`Concrete019`、`Concrete034`、`Metal032`、`Metal049A`、`Rust004`、`Wood051`、`Fabric028`）；**磚牆沿用 Level 2 順砌磚底＋`Concrete034` 細紋，不使用亂石牆 `Bricks075A`**。產出前經 `tools/resize_realistic.py` 縮至整格面 128×128、道具 64×64，全部附 labPBR `_n`／`_s`，牆地天花補髒舊變體。 |
| 顏色 | 偏暖舊工業磚色（暗橘紅、褐磚、灰泥）配冷灰金屬電箱、深綠磁磚牆裙與工業綠配電盤、舊黃銅鍋爐與青銅天使；紫化磚、聖所彩繪玻璃與 M.E.G. 藍白標誌作低飽和局部點綴。 |
| 尺寸 | 6×6 格迷宮單元；四種走道剖面：寬（4×4）、一般（2×3）、窄（1×2）、矮（2×1.5 需蹲行）；大廳高度 6 格（發電室／鍋爐房 2×2～3×2 單元；組裝線／聖所／Base Gamma 3×3 單元；車房 1×2 單元）。 |
| 燈 | 走道日光燈（`station_light`／`flickering_station_light`／`broken_station_light`）、窄道與鍋爐房護網燈泡（`cage_bulb`／`dead_cage_bulb`）、組裝線與基地探照燈（`station_floodlight`）。採 3×3 單元串聯電路（目前亮 55%／閃 15%／壞 30%）；升級時補「快壞昏黃」狀態對齊規範四態（亮／快壞／閃／壞）。光色：工業老舊冷白與鎢絲橘黃混搭、聖所暖暗。 |
| 氣氛 | 於 `src/client/java/dev/noclip/backrooms/core/look/Look.java` 的 `Look.Mood` 新增專屬 `LEVEL_3`（實測定案：`start=6, end=30, falloff=0.18F`，取代原本沿用的 `DEFAULT(9, 44, 0.15F)`）。 |
| 光影 | 特別檢查濕黑泥的鏡面反光（高光滑度 `_s`）、火花插座／紫化磚／彩繪玻璃／電梯面板的自發光（`light_emission` 與 `_s` 發光通道）、以及鐵柵後方的深黑縱深，不讓開光影時暗處過亮。 |
| 道具 | 既有 53 個專屬方塊；規劃新增原文道具（64×64，可收集物附完好／損壞／穿模原則）：紫化實體殘骸、8 頁狂亂筆記、聖所古文字牌匾、壁畫與塗臉天使畫、鐵柵後失聯無人機與殘破希臘像、B.N.T.G. 當舖櫃檯與展櫃（含《後室終極食譜》彩蛋）、藍色救贖鸚鵡壁畫與藍光燈、U.E.C. 實驗裝置，以及適度融入既有 3D 工業穿模家具（`BackroomsProps`）。 |
| 掉落 | 對齊 [`docs/levels/MATERIALS.md`](../MATERIALS.md) 第六節：發電機／配電盤→`electric_motor`、`high_voltage_coil`、`steel_gear`（Tier 2）；電箱／插座→`copper_wire`、`capacitor`；斷電線需絕緣剪→`copper_wire` ×3；黃銅鍋爐→`brass_ingot`、`pipe_valve`、小機率 `thermal_core`；輸送帶→`rubber_conveyor_belt`、`steel_wheel`；聖所彩繪玻璃→Tier 3 拆得 `sanctuary_stained_glass`；天使雕像與黑白鐵柵維持不可破壞。 |

## 升級藍圖（照 `latest:level-3` 分 5 批實裝）

### 第 1 批（Batch 1）：寫實材質包與黑暗氣氛（**已完成**）
- 新增 `tools/make_level3_polish.py` 補上燈具第四態「快壞的日光燈（`dim_station_light`）」與牆／地／天花板髒舊多變體（4 種磚／磁磚不鏡像變體、3 種×4 向積灰地板、3 種×0/180 天花板）。
- 新增 `tools/make_level3_realistic.py`，產生 Level 3 86 張寫實材質＋labPBR `_n`／`_s`（經 `tools/resize_realistic.py` 縮至 128／64，紅磚與褐磚採順砌磚＋`Concrete034` 細紋），並產出對照圖 [`docs/look/level3_realistic.png`](../../look/level3_realistic.png)。
- 於 `Look.Mood` 加入 `LEVEL_3(6, 30, 0.18F)`，同步更新 `pack.mcmeta`、語言檔、`docs/look/README.md`。
- 驗收：`gradlew build` + `runClientGameTest -Plevels=3` 與 `runClientGameTest -Plevels=3 -Pshaders` 雙組通過，雙組遊戲畫面對照見 [`docs/look/level3_ingame.jpg`](../../look/level3_ingame.jpg)。

### 第 2 批（Batch 2）：高智能多實體生態與聖所全域庇護（**已完成**）
- 實作 `Level3Monsters` 並於 `Level3` 註冊：接入敵對武裝無面靈（持石製武器與機率盾牌，組裝線更常出現）、成群死亡飛蛾（2～3 隻）、笑魇、肢團（可鑽進窄／矮道）、竊皮者與悲尸，均避開 Base Gamma 與聖所。
- 擴充 `Faceling.setHostile(boolean)` 支援 Level 3 敵對模式（不影響其他樓層原本的溫和無面靈）。
- 擴充 `Level3Events.scareOff` 讓聖所天使雕像驅離所有後室敵對實體（不限於獵犬）。
- 可選支線（尚未實作，需另建 3D `BoxRig` 模型）：原文的高智能「尸鼠（Death Rat）」與簡易電氣陷阱。
- 驗收：`gradlew build` + `runClientGameTest -Plevels=3` 通過（驗證 6 種實體生成、無面靈敵意武裝、聖所驅離獵犬／死亡飛蛾／無面靈）。

### 第 3 批（Batch 3）：四大據點升級與新據點實裝（**已完成**）
- **M.E.G. Gamma 基地升級**：新增監視器（`security_camera`）、安檢鐵柵隔斷、行軍床右鍵安頓設重生點（`Level3RestBlock`）、拆解台／改裝鍛造台，以及 M.E.G. 軍需官與補給員（可右鍵交易）。
- **B.N.T.G. 存儲設施（`BNTG_STORAGE`）**：整排貨箱庫房、當舖櫃檯、講台展櫃、免生怪安全區與 B.N.T.G. 收購商／檔案員。
- **杰瑞的信眾「藍色救贖」（`BLUE_SALVATION`）**：改建自廢棄發電室的白色乾牆（`foj_white_wall`）、藍色日光燈（`blue_station_light`）、滿牆藍鸚鵡壁畫（`jerry_mural`）、殘破發電機與斷線。
- **U.E.C.「錫拉殖民地」（`SCYLLA_COLONY`）**：極暗區域敵對實驗室、U.E.C. 實驗裝置（`uec_apparatus`）、上鎖軍械箱／異常金庫箱與敵對 `U.E.C. Operative` 駐衛。
- 新增 `tools/make_level3_bases.py` 產出 5 個新方塊貼圖／模型／方塊狀態／戰利品表，並同步更新 `make_level3_realistic.py` 寫實材質包與雙語翻譯。
- 驗收：`Level3LayoutTest.allFourFactionBasesTurnUp` + `gradlew build` + `runClientGameTest -Plevels=3` 通過，並實機檢視 4 座據點截圖。

### 第 4 批（Batch 4）：原文異常細節、彩蛋 POI 與物品豐富度

> 完整 POI 企劃（含 Gamma 基地本體）見 [`POI.md`](POI.md)，跟下面的條目衝突時以它為準。
- **鐵柵後方景觀**：在不可通過鐵柵對側偶爾放置失聯探測車／無人機、風化希臘女性雕像、壁畫、黑泥污水坑、後室地鐵零碎零件。
- **紫化現場**：除了零星紫磚外，偶爾出現卡進牆壁的紫化殘骸與牆上悲傷銘文。
- **藝術品、聖所古文字與 8 頁狂亂筆記**：牆面塗臉天使畫、帶翼骷髏畫、聖所基座失落文字牌匾、以及可右鍵閱讀的 8 頁神秘作者筆記。
- **物品豐富度**：組裝線輸送帶與發電室可搜刮成堆杏仁水、火鹽、電池與工業零件，呼應原文「高風險、最高物資回報」。
- 驗收：單元測試確認壁掛／POI 不擋路不懸空，實機測試驗證筆記閱讀與組裝線物資取得。

### 第 5 批（Batch 5）：方塊拆解掉落與工業穿模家具
- 依 [`docs/levels/MATERIALS.md`](../MATERIALS.md) 第六節補齊 Level 3 全部機具、管線、斷線、鍋爐、輸送帶與聖所彩繪玻璃的戰利品表與工具門檻。
- 在寬走道與大廳角落適度融入既有 3D 工業穿模家具（`BackroomsProps`），確保不影響原本迷宮連通性與避開出口緩衝區。
- 驗收：`gradlew build` + `runClientGameTest -Plevels=3` 完整通過並更新 `docs/levels/MATERIALS.md` 與 `CLAUDE.md`。

## 測試

- 單元測試 `Level3LayoutTest`：每一格都走得到（鐵柵門算可通過、鐵柵不算）、通道至少容得下一個人、三種出口都會出現且面對地板、鐵柵和柵門確實存在、熱點不會太多、落點不在窄道或熱點
- 實機測試 `Level3ClientTest`：從 Level 2 進來、拍四種尺寸的走道和六種大廳、撬開柵門、三種出口都會拒絕、斷線觸電、黑泥燙傷與噁心、燈管爆裂、發電機過載爆炸、被窺視感、天使嚇走獵犬、獵犬成群出現
- 畫俯視地圖：`LEVEL3_MAP=map.png ./gradlew test --tests dev.noclip.backrooms.level3.MapDump`
