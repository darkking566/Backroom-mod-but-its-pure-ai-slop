# 後室樓層總覽索引（共 316 層）

本清單整合了本模組目前既有的樓層與出口（含 Level 1 出口表與收費亭事件），以及 **Backrooms Wikidot**、**Backrooms Fandom** 與衍生社群最具代表性的主樓層、子樓層、負樓層與隱秘樓層。

> 這一章是 [企劃書](../README.md) 的第 5 章。已做好的樓層（0、0.1、1、2、3）各自的說明在各層資料夾的 `README.md`；每一層能收集的材料見 [合成：各層收集](../3-crafting/by-level.md)。

## 已經做好的樓層

| Level | 名稱 | 誰做的 | 說明 |
| --- | --- | --- | --- |
| 0 | 大廳（The Lobby） | 朋友（2026-11-26 改版企劃已定案） | [level0/README.md](level0/README.md) |
| 0.1 | 天頂站（Zenith Station） | Claude，之後 Gemini 修 | [level0p1/README.md](level0p1/README.md) |
| 1 | 宜居地帶／停車場（Habitable Zone） | Claude，之後朋友和 Gemini 加內容 | [level1/README.md](level1/README.md) |
| 2 | 廢棄維修通道（Abandoned Utility Halls） | Claude，之後 Gemini 加內容 | [level2/README.md](level2/README.md) |
| 3 | 電力站（Electrical Station） | Claude | [level3/README.md](level3/README.md) |

每一層開工時，先下載 Wikidot 的照片照著做（見 [`docs/look/SOP.md`](../../look/SOP.md)）。同一個編號 Wikidot 和 Fandom 不一樣時，開工前問使用者要照哪一邊。

## 樓層架構原則（原企劃書第 4.3 節）

- **不能開 300 個維度**（記憶體會爆）：風格相近的樓層合在少數維度裡，用超大座標分區（每區隔 10,000 格以上並限制邊界）或自訂生成器＋生態域切換來隔開不同的 Level。目前已做的 5 層是各自一個維度。
- **分層開發**：
  - 30～50 個核心主題樓層，精細設計結構與 Boss；
  - 其餘 250 層以上，用資料驅動的樓層引擎批量擴充：地形骨架＋方塊替換表＋家具掉落池＋實體池。
- **各主題樓層的資源分工**（各司其職，見 [合成](../3-crafting/README.md)）：

| 主題 | 樓層 | 資源 |
| --- | --- | --- |
| 大廳 | Level 0 | 辦公雜項、家電回收、基礎求生、陽春醫療 |
| 工業／倉庫 | Level 1、2、3 | 重金屬、管線、火鹽、工業機械、中階彈藥材料 |
| 醫院 | — | 高階醫療包、正規止痛藥、嗎啡、手術組件 |
| 礦坑 | — | 自訂礦物、金屬冶煉與鍛造材料 |
| 魔法森林 | Level 39 等 | 魔導植物、法術核心、扣理智的強力法杖與卷軸 |
| 叢林 | — | 異變植物、稀有草藥、劇毒與高級有機材料 |
| 稀有實驗室 | — | 黑科技、刻意偏強的實驗武器 |
| 異界聯動（地獄、終界、侏羅紀等） | — | 終局核心、保險箱擴容材料、神裝鍛造核心 |

- **特殊樓層的解鎖條件**：鑰匙卡（消耗品）、多階密碼門駭客器 1～4 階（永久升級）、完成該層的機關任務（例如修發電機）、或特定商人任務。見 [遊戲玩法：出口](../1-gameplay/README.md#出口)。
- **雙軸難度**（層數＋離降落點的距離）見 [遊戲玩法：層數與難度](../1-gameplay/README.md#在後室裡層數與難度)。

## 通用樓層設計標準（2026-11-26 定案）【定】

除了地形外觀與怪物生態，**之後每一層（包含既有樓層翻新）都必須一併規劃以下兩套體驗系統**，避免樓層淪為重複空殼：

### 1. 每層專屬「微小事件系統」
- 每一層開工企劃時，都必須設計屬於該層主題的**微小事件表**（目標每層至少 8～12 種），不能只靠靜態建築與怪物追逐撐場面。
- 四大事件軸線：
  1. **搜刮與環境檢定**：檢查天花板、敲擊空心牆、翻找特定家具、檢視異常物件；串聯玩家四維能力值（力量／敏捷／感知／智力）給予額外提示、避免失誤或開出隱藏分支。
  2. **突發環境演出**：走到一半突然發生的視聽異常（例如骨牌式熄燈、無源電話響起、背後多出腳步聲、機器自啟、滴水與跳電），打破漫長探索的單調感。
  3. **空間異常與微謎題**：短迴圈走廊、局部空間移位、需要附近扳電箱／找保險絲／繞通風口才能進去的微型密室。
  4. **理智與偽裝干擾**：低理智時出現誤導性線索、變形的牆面塗鴉、異常倒影或失真無線電，與拍立得取證、生體掃描器等道具形成互動。

### 2. 多樣化「非門異常出口」演算法（模糊線索 → 動作觸發）
參考《狐霖的後室 RPG》與 Backrooms Wikidot／Fandom 設定，各樓層的跨層出口**不要只用「一扇門右鍵傳送」呈現**，改採統一的環境物件演算法：
- **載體多樣化**：海報／掛畫、牆上小窗戶、地下井蓋／格柵／通風管道、地板坑洞與下層通道、異色／翹起的地毯、裂開或空心的牆壁、巨型鏡子、特殊燈光走廊、異常階梯／電梯、桌邊物件與少數主題門框。
- **模糊線索敘事**：出口不直白標示「前往 Level XXX」，而是靠**視覺特徵＋靠近／右鍵觀察的 1～2 句模糊環境線索**暗示目的地（例如一張泛黃海報模糊描繪著 1980 年代濃霧裡的山莊與松林，玩家**撕下或打掉**海報即被傳送至 Level 161「阿帕拉契的荒涼」；地板格柵透出滾燙蒸氣與金屬震動即暗示 Level 2「管道夢境」）。**感知**較高的玩家可讀到更清楚的細節提示。
- **動作觸發類型**：
  - `破壞／撕除`：左鍵打掉或右鍵撕下海報、掀開地毯砸穿地板、敲破空心牆面；
  - `鑽入／跨越`：走進閃爍牆、推開牆上窗戶跨出、掀開鐵格柵下井、蹲下鑽通風口、跳入特定坑洞；
  - `凝視／跟隨`：盯著異常大鏡數秒、追隨遠處一閃而過的異常剪影、沿著燈光變色的長廊直走到底；
  - `特定動作／條件`：來回穿門數次、墊高推開半空門、對特定裝置互動。
- **統一接線與密度控制**：全部出口透過 `core/LevelExits.use(player, key)` 觸發；目標樓層未開放時仍保留現場物件與模糊線索，觸發後提示空間尚未成型且不傳送，待目標層實作並呼叫 `LevelExits.open(...)` 後自動全面接通。每一層依樓層類型配置「主線必出出口（如 Level 0 → Level 1）」＋「每趟高密度探索區內隨機抽樣數條支線／異質出口」，確保玩家每次出勤都能撞見意料之外的分流。

## 做到 200 層以上的技術規劃（2026-11-28）

現在 7 個區域（Level 0、0.1、1、2、3、4 與前室）的量：程式約 3.5 萬行、方塊模型約 780 個、方塊貼圖 1,047 張（平均每層約 136 張）、貼圖連寫實包約 75 MB，模組資源共 151 MB。照這個做法做到 200 層，大約要 2.7 萬張貼圖、2～3 GB。真正卡住的有兩件，下面兩項都是使用者定的。

### 1. 方塊貼圖統一 32 像素【定】

- 原因：所有方塊貼圖要拼成一張大圖，大圖的邊長有顯卡上限（大多數 16384，舊內顯 8192，部分 NVIDIA 32768），整張放在顯存裡。

  | 解析度 | 上限（16384） | 上限（8192） | 200 層約要的顯存 |
  | --- | --- | --- | --- |
  | 128 | 1.6 萬張，不夠 | 4 千張，不夠 | 約 2.3 GB，塞不進去 |
  | 64 | 6.5 萬張 | 1.6 萬張，不夠 | 約 0.7 GB |
  | 32 | 26 萬張 | 6.5 萬張 | 約 0.2 GB |
  | 16 | 100 萬張 | 26 萬張 | 約 0.05 GB |

  開光影（Iris）時，法線與反光貼圖各要再拼一張，大約變 3 倍。

- 顯卡：
  - 舊內顯只有 32 撐得住 200 層；
  - 現在的筆電內顯、入門獨顯（2～4 GB）跑 32 很輕鬆；
  - 128 不論顯存多大都卡在數量上限。
- 做法：
  - 貼圖要「為 32 像素設計」，花紋重新畫清楚，不是把 128 直接縮小（直接縮小會糊）；
  - 現有各層的貼圖都要換，`docs/look/STYLE_GUIDE.md` 的 128／64 規範跟著改（換完之前照舊）；Level 0、4 是朋友的，要一起排時間。
- 【建議】另附一個可選的 64 像素高畫質資源包，給 4 GB 以上顯卡的玩家自己開（64 的 200 層約 0.7 GB，還在上限內）。
- 不受影響：Blender 做的 3D 物件（家具、研究所與小鎮的物件）不放在那張大圖裡。

### 2. 樓層的維度用到才建立、整隊離開就刪除【定】

- 有人走進某一層時，才建立這一層的一份維度；同一隊進同一層，共用同一份；整隊都離開後刪掉。
- 每次建立都換新的種子，「回到去過的層是全新的一片」自然成立，存檔也不會一直變大。
- 維度的「類型」（高度、天空、霧）事先定好幾種，建立時從裡面挑。
- 配合的遊戲規則（已定，見 [遊戲玩法](../1-gameplay/README.md) 的「死亡」）：死了不噴裝、等隊友到下一層復活、整隊全滅才噴裝；安頓只用來回 M.E.G.。所以維度刪掉時不會弄丟東西，也不會有人醒在不存在的地方。
- 只是程式內部的事、**玩家看不出差別**的兩件工程：
  - Minecraft 只在開伺服器時照固定名單建好世界，遊戲中途不會自己生新世界。要用 Fabric 的 Fantasy 函式庫（要先確認支援 26.2）或自己寫一份。
  - 現在約有 180 處、分散在約 100 個檔案，直接寫死「在 Level 0 這個世界」。改成每次新建之後，要改成「在任何一份 Level 0」。程式有朋友的、Gemini 的、Claude 的，要先講好，由一個人一次改完。

### 備用企劃：樓層工具包（待朋友同意，還沒定）

把每層都會重寫的部分抽成一套共用工具，新樓層多數只要寫一份設定檔；想做細的樓層照樣可以自己手工做。**樓層之間互相連通不受影響**：出口本來就經過共用的出口系統，抽出來之後出口寫成一張表，任何一層連到任何一層都只是加一行，非門出口也一樣。

- 內容：
  1. 樓層設定檔：每層一份，寫名字、維度類型、地形、材質、燈、出口表、生怪表、物資、事件、霧與亮度、難度；
  2. 地形積木：格子迷宮、房間、走廊、大廳、柱廳、多層樓梯、戶外地形、水；
  3. 材質庫：共用的牆、地、天花板、飾條、燈（四種狀態），每層換色加幾張特色貼圖；
  4. 共用系統：出口（門與非門）、停電、微小事件、生怪、寶箱與可拆物件、據點與撤離；
  5. 動態維度管理（上面第 2 項）；
  6. 自動檢查：每層都跑落點站得住、出口走得到、生成速度。
- 限制：
  - 同一套積木做的樓層容易長得像，每層要留特色；
  - 特殊樓層（0.1 的球殼、水上的 Level 7、郊區的 Level 9 這類）還是要自己寫；
  - 改共用的部分會影響所有用到的樓層，要靠自動檢查把關。
- 分工（建議）：
  - Claude：工具包骨架、動態維度、設定檔格式、出口表、自動檢查、材質庫、死亡與復活規則、180 處的修改，先拿 Level 2 或 3 當試點；
  - 朋友：用工具包做新樓層（寫設定檔、加特色），主樓層可以照樣手工做；
  - Gemini：每層的物品、寶箱內容、合成、武器，倖存者支線這類事件內容。
- 順序（建議）：
  1. 定規則（死亡、32 像素、動態維度：已定）；
  2. 工具包骨架與試點；
  3. 材質庫換 32 像素，現有各層換過去；
  4. 朋友用工具包做新樓層，Gemini 補每層的物品。

## 資料夾命名規則
- **一般主樓層**：`level<編號>`（例：`level0`、`level37`、`level999`）
- **子樓層（小數點用 `p`）**：`level<主層>p<子層>`（例：`level1p01`、`level1p1`、`level4p3`、`level6p1`）——符合 Java 套件語法且不與 `level11` 撞名
- **負樓層（負號用 `neg`）**：`level_neg<編號>`（例：`level_neg0`、`level_neg1`、`level_neg33`）
- **隱秘／特殊樓層**：英文小寫底線（例：`the_hub`、`level_run`、`level_fun`、`level_lx`）
- 第一批 60 層已預先建立 `docs/project/5-levels/<資料夾>/README.md` 與 `src/main/java/dev/noclip/backrooms/<資料夾>/`。
- 第二批 256 層（見文末「第二批擴充」）先只列在這份索引，開工時再照該層的開工檢查表建立資料夾，避免 `src` 底下堆幾百個空資料夾。
- 子樓層、中文原創層的資料夾名同上規則：`0.1` → `level0p1`、`6.31` → `level6p31`、中文原創 `C-72` → `level_c72`。Fandom 的版本和 Wikidot 同號時加 `_fd`（例：`level161_fd`）；同一個出口鍵在遊戲裡只能接一層，開工前要二選一。

## 主樓層（核心）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level0`](level0/README.md) | Level 0：教學關卡／大廳（The Lobby） | `0` | Wikidot / Fandom 共通 | 已實作 |
| [`level1`](level1/README.md) | Level 1：宜居地帶／停車場（Habitable Zone） | `1` | Wikidot / Fandom / 狐霖後室 | 已實作（詳見既有 README） |
| [`level2`](level2/README.md) | Level 2：廢棄維修通道（Abandoned Utility Halls；Fandom 叫 Pipe Dreams） | `2` | Wikidot（照片與文字）／Fandom | 已實作（從 Level 1 往下的樓梯進入） |
| [`level3`](level3/README.md) | Level 3：發電站（Electrical Station） | `3` | Wikidot（照片與文字） | 已實作（從 Level 2 噴了「3」的門進入） |
| [`level4`](level4/README.md) | Level 4：廢棄辦公室（Abandoned Office） | `4` | 中文維基 / Wikidot | 已實作（第一版，從 Level 2 門與 Level 3 電梯進入） |
| [`level5`](level5/README.md) | Level 5：恐怖旅館（Terror Hotel） | `5` | Wikidot / Fandom 共通 | 未開放 |
| [`level6`](level6/README.md) | Level 6：熄燈（Lights Out） | `6` | Wikidot / Fandom 共通 | 未開放 |
| [`level7`](level7/README.md) | Level 7：深海恐懼症（Thalassophobia） | `7` | Wikidot / Fandom 共通 | 未開放 |
| [`level8`](level8/README.md) | Level 8：洞穴系統（Cave System） | `8` | Wikidot / Fandom 共通 | 未開放 |
| [`level9`](level9/README.md) | Level 9：黑暗郊區（Darkened Suburbs） | `9` | Wikidot / Fandom 共通 | 未開放 |
| [`level10`](level10/README.md) | Level 10：豐裕麥田（Field of Wheat / Bumper Crop） | `10` | Wikidot / Fandom 共通 | 未開放 |
| [`level11`](level11/README.md) | Level 11：無盡城市（The Endless City） | `11` | Wikidot / Fandom 共通 | 未開放 |

## 子樓層（Level 1 已預留出口）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level1p01`](level1p01/README.md) | Level 1.01：Level 1 地下室（The Basement of Level 1） | `1.01` | Fandom / 狐霖後室 | 未開放（Level 1 地窖門已預留出口） |
| [`level1p1`](level1p1/README.md) | Level 1.1：腐敗的走廊（Corrupted Corridor） | `1.1` | Wikidot / 狐霖後室 | 未開放（Level 1 孤零零門框已預留出口） |
| [`level4p3`](level4p3/README.md) | Level 4.3：無盡辦公室／腐朽空屋 | `4.3` | Fandom / Wikidot / 狐霖後室 | 未開放（Level 1 純白門已預留出口） |

## 主樓層（Level 1 已預留出口）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level54`](level54/README.md) | Level 54：螺旋樓梯間（Stairwell of Spirals） | `54` | Wikidot / Fandom / 狐霖後室 | 未開放（Level 1 加固門已預留出口） |
| [`level143`](level143/README.md) | Level 143：粉紅大理石宮殿／聖堡 | `143` | Fandom / Wikidot / 狐霖後室 | 未開放（Level 1 粉紅大理石宮殿畫已預留出口） |
| [`level188`](level188/README.md) | Level 188：百窗庭院（The Courtyard of Windows） | `188` | Fandom / 經典後室 / 狐霖後室 | 未開放（Level 1 插座牆已預留出口） |
| [`level197`](level197/README.md) | Level 197：打烊超市／水晶洞穴 | `197` | Fandom / Wikidot / 狐霖後室 | 未開放（Level 1 發光自動門已預留出口） |
| [`level389`](level389/README.md) | Level 389：遊戲大廳（The Gaming Hall） | `389` | Wikidot / Fandom / 狐霖後室 | 未開放（Level 1 十字鎬門已預留出口） |
| [`level960`](level960/README.md) | Level 960：混凝土迷宮（Maze of Concrete） | `960` | Fandom / 狐霖後室 | 未開放（Level 1 裂開柱子已預留出口） |

## 特殊樓層（Level 1 收費亭事件預留）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level_lx`](level_lx/README.md) | Level LX：盡力補償（狐霖後室事件層） | `LX` | 狐霖的後室RPG / 衍生設定 | 未開放（Level 1 收費亭抽屜事件預留） |

## 經典主樓層

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level12`](level12/README.md) | Level 12：矩陣（Matrix） | `12` | Wikidot / Fandom | 未開放 |
| [`level14`](level14/README.md) | Level 14：天堂（Paradise；狐霖的後室設定為「冷漠病院／孤冷醫院」） | `14` | Wikidot / Fandom / 狐霖後室 | 未開放 |
| [`level18`](level18/README.md) | Level 18：回憶（Memories） | `18` | Wikidot / Fandom 共通 | 未開放 |
| [`level20`](level20/README.md) | Level 20：倉庫（The Warehouse） | `20` | Fandom / 經典 Wikidot | 未開放 |
| [`level33`](level33/README.md) | Level 33：無盡購物中心（The Infinite Mall） | `33` | Wikidot / Fandom 共通 | 未開放 |
| [`level37`](level37/README.md) | Level 37：泳池房（The Poolrooms / Sublimity） | `37` | Wikidot / Fandom 共通 | 未開放 |
| [`level39`](level39/README.md) | Level 39：魔法森林／巨物恐懼 | `39` | Wikidot / Fandom | 未開放 |
| [`level40`](level40/README.md) | Level 40：搖滾披薩遊樂場（Roller Rockin' Pizza!） | `40` | Wikidot | 未開放 |
| [`level52`](level52/README.md) | Level 52：學校長廊（School Halls） | `52` | Fandom / 經典社群 | 未開放 |
| [`level69`](level69/README.md) | Level 69：苦難公路之旅（The Road Trip of Affliction） | `69` | Wikidot / Fandom | 未開放 |
| [`level94`](level94/README.md) | Level 94：動畫（Motion） | `94` | Wikidot / Fandom 共通 | 未開放 |
| [`level97`](level97/README.md) | Level 97：燈塔（The Lighthouse） | `97` | Wikidot / Fandom | 未開放 |
| [`level154`](level154/README.md) | Level 154：雷射遊戲館（Laser Tag） | `154` | Wikidot / Fandom | 未開放 |
| [`level283`](level283/README.md) | Level 283：遊樂場／街機廳（Playplace / The Arcade） | `283` | Wikidot / Fandom | 未開放 |
| [`level399`](level399/README.md) | Level 399：霓虹天堂／安全巴士旅途 | `399` | Wikidot / Fandom | 未開放 |
| [`level906`](level906/README.md) | Level 906：天鵝座檔案館（Cygnus Archive） | `906` | Wikidot / Fandom | 未開放 |
| [`level974`](level974/README.md) | Level 974：Kitty 的家（Kitty's House） | `974` | Wikidot / Fandom 共通 | 未開放 |
| [`level998`](level998/README.md) | Level 998：天頂／向死之路（Zenith） | `998` | Wikidot / Fandom | 未開放 |
| [`level999`](level999/README.md) | Level 999：虛空之島（The Island of the Void） | `999` | Wikidot / Fandom 共通 | 未開放 |

## 高編號／特色樓層

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level404`](level404/README.md) | Level 404：遺忘之境（404 Not Found） | `404` | Fandom / Wikidot | 未開放 |

## 高編號／特色樓層（Fandom）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level3999`](level3999/README.md) | Level 3999：真實結局（The True Ending） | `3999` | Fandom 經典 | 未開放 |
| [`level9223372036854775807`](level9223372036854775807/README.md) | Level 9223372036854775807：64 位元上限真結局（True Final Level） | `9223372036854775807` | Fandom 經典 | 未開放 |

## 代表性子樓層

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level0p2`](level0p2/README.md) | Level 0.2：翻修爛攤子（Remodeled Mess） | `0.2` | Wikidot / Fandom | 未開放 |
| [`level6p1`](level6p1/README.md) | Level 6.1：零食間（The Snackrooms） | `6.1` | Wikidot / Fandom 共通 | 未開放 |
| [`level37p1`](level37p1/README.md) | Level 37.1：深暗泳池（Dark Poolrooms） | `37.1` | Wikidot / Fandom 延伸 | 未開放 |

## 負樓層（Fandom／經典衍生）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`level_neg0`](level_neg0/README.md) | Level -0：零之反面（The Negative Lobby） | `-0` | Fandom / 衍生社群 | 未開放 |
| [`level_neg1`](level_neg1/README.md) | Level -1：黑白長廊／迷霧地下室（The Hallway of Doors） | `-1` | Fandom / 經典後室 | 未開放 |
| [`level_neg2`](level_neg2/README.md) | Level -2：溢出管廊（Overflow） | `-2` | Fandom / 經典後室 | 未開放 |
| [`level_neg33`](level_neg33/README.md) | Level -33：寂靜飯店／冰冷商廊（Lukewarm Hotel） | `-33` | Fandom 經典 | 未開放 |

## 隱秘樓層（Enigmatic）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`the_hub`](the_hub/README.md) | The Hub：樞紐 | `hub` | Wikidot / Fandom 共通 | 未開放 |
| [`the_manila_room`](the_manila_room/README.md) | The Manila Room：馬尼拉房間 | `manila` | Wikidot / Fandom 共通 | 未開放 |
| [`level_run`](level_run/README.md) | Level !：快逃命（Run for Your Life!） | `!` | Wikidot / Fandom 共通 | 未開放 |
| [`level_fun`](level_fun/README.md) | Level Fun：享樂層（=)） | `fun` | Wikidot / Fandom 共通 | 未開放 |
| [`the_end`](the_end/README.md) | The End：終點（陷阱圖書館） | `end` | Wikidot / Fandom 共通 | 未開放 |
| [`th3_sh4dy_gr3y`](th3_sh4dy_gr3y/README.md) | Th3 Sh4dy Gr3y：陰影灰域 | `shady_grey` | Wikidot / Fandom | 未開放 |
| [`the_void`](the_void/README.md) | The Void：虛空 | `void` | Wikidot / Fandom 共通 | 未開放 |
| [`the_blue_channel`](the_blue_channel/README.md) | The Blue Channel：藍色頻道 | `blue_channel` | Wikidot 經典 | 未開放 |

## 隱秘樓層（Enigmatic／Fandom）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| [`the_promised_land`](the_promised_land/README.md) | The Promised Land：應許之地 | `promised_land` | Fandom 經典 | 未開放 |

---

# 第二批擴充（Claude 補充，2026-11-24）

第一批 60 層之外，再從下面的來源補上 256 層，合計 316 層。另外列了 52 個候補（不算在總數裡）。

## 資料來源

| 來源 | 規模 | 本批用法 |
|---|---|---|
| [Backrooms Wikidot 樓層總表](http://backrooms-wiki.wikidot.com/normal-levels-i) | Level 0～999 都有，加上大量子樓層 | 0～199 的主樓層與 0～99 的子樓層全部列入（沒有內容的空號跳過） |
| [Backrooms Wikidot 無編號樓層](http://backrooms-wiki.wikidot.com/unnumbered-levels) | 47 個 | 列為候補 |
| [後室中文維基：原創層級](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i)（另有[系列 II](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-ii)） | C-1～C-1900 左右，很多空號 | 從 C-1～C-80 挑標題就看得出場景的 22 層 |
| [B 站後室 Wiki](https://wiki.biligame.com/backrooms/%E5%B1%82%E7%BA%A7) | 大多從 Wikidot、Fandom 搬運 | 只參考，沒有另外列 |
| [Escape the Backrooms 全關卡攻略](https://gameplay.tips/guides/escape-the-backrooms-all-levels-guide.html)（2023-11-13） | 遊戲 23 關 | 做成下面的「遊戲玩法參考」，不另外加樓層 |
| [Backrooms Fandom 總表](https://backrooms.fandom.com/wiki/Level_List)（另有[中文分站](https://backrooms.fandom.com/zh/wiki/%E6%9B%B4%E5%A4%9A%E6%A5%BC%E5%B1%82)） | 和 Wikidot 各自編號的另一套設定，數量很多 | 網站擋自動讀取；從搜尋結果確認得到的 29 層列在「Fandom 版本」 |
| Backrooms Freewriting Wiki、Liminal Archives | 社群另開的 wiki | 沒有找到可讀的總表，只記下有這些來源 |

想再加的話：Wikidot 的 200～999 號還有幾百層（例如 233 The Lukewarm Hotel、289 Overflow），中文維基還有上千個原創層。

## 名稱對照（第一批）

Wikidot 和 Fandom 是兩套不同的設定，同一個編號的名稱和內容常常不一樣。下面是第一批裡、和 Wikidot 總表目前名稱不同的樓層，開工前要先決定照哪一邊做：

| 樓層 | 第一批寫的名稱 | Wikidot 總表目前的名稱 |
|---|---|---|
| Level 0 | 大廳（The Lobby） | Threshold（The Lobby 是 Fandom 的叫法） |
| Level 2 | Pipe Dreams | Abandoned Utility Halls |
| Level 9 | Darkened Suburbs | The Suburbs |
| Level 11 | The Endless City | The City That Never Sleeps |
| Level 20 | The Warehouse | Fun Zone Boreas Structure |
| Level 37.1 | Dark Poolrooms | The Sanguine Reservoir |
| Level 52 | School Halls | DESTINATION REACHED |
| Level 143 | 粉紅大理石宮殿 | 空號（目前沒有文章）；第一批的設定來自狐霖的後室 |
| Level 197 | 打烊超市／水晶洞穴 | Crystal Caverns |
| Level 283 | Playplace / The Arcade | PlayLand |
| Level -2 | Overflow | Wikidot 的 Overflow 是 Level 289 |
| Level -33 | Lukewarm Hotel | Wikidot 的 The Lukewarm Hotel 是 Level 233 |
| Level !、Level Fun | 標為 Wikidot / Fandom 共通 | Wikidot 的無編號總表裡沒有，應該是 Fandom 的樓層 |

## Wikidot 主樓層 0～99（第一批沒有的 75 層）

中文是暫譯；23、73、83 的原標題用了花體字，這裡寫成一般英文。

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| `level13` | Level 13：溫水煮青蛙（The Boiling Frogs） | `13` | [Wikidot](http://backrooms-wiki.wikidot.com/level-13) | 未開放 |
| `level15` | Level 15：未來廳堂（Futuristic Halls） | `15` | [Wikidot](http://backrooms-wiki.wikidot.com/level-15) | 未開放 |
| `level16` | Level 16：變異地形（Altered Topography） | `16` | [Wikidot](http://backrooms-wiki.wikidot.com/level-16) | 未開放 |
| `level17` | Level 17：載體（The Carrier） | `17` | [Wikidot](http://backrooms-wiki.wikidot.com/level-17) | 未開放 |
| `level19` | Level 19：閣樓地板（Attic Floorboards） | `19` | [Wikidot](http://backrooms-wiki.wikidot.com/level-19) | 未開放 |
| `level21` | Level 21：編號之門（Numbered Doors） | `21` | [Wikidot](http://backrooms-wiki.wikidot.com/level-21) | 未開放 |
| `level22` | Level 22：遺留的廢墟（Ruins Left Behind） | `22` | [Wikidot](http://backrooms-wiki.wikidot.com/level-22) | 未開放 |
| `level23` | Level 23：石化花園（The Petrified Garden） | `23` | [Wikidot](http://backrooms-wiki.wikidot.com/level-23) | 未開放 |
| `level24` | Level 24：月球（The Moon） | `24` | [Wikidot](http://backrooms-wiki.wikidot.com/level-24) | 未開放 |
| `level25` | Level 25：四分樞紐（The Quarter Hub） | `25` | [Wikidot](http://backrooms-wiki.wikidot.com/level-25) | 未開放 |
| `level26` | Level 26：歡樂號郵輪 =)（The SS Fun =)） | `26` | [Wikidot](http://backrooms-wiki.wikidot.com/level-26) | 未開放 |
| `level27` | Level 27：地堡溫泉（The Bunker Springs） | `27` | [Wikidot](http://backrooms-wiki.wikidot.com/level-27) | 未開放 |
| `level28` | Level 28：最終美德（Final Virtue） | `28` | [Wikidot](http://backrooms-wiki.wikidot.com/level-28) | 未開放 |
| `level29` | Level 29：許珀里安（Hyperian） | `29` | [Wikidot](http://backrooms-wiki.wikidot.com/level-29) | 未開放 |
| `level30` | Level 30：偏離現實（Shifted Beyond Reality） | `30` | [Wikidot](http://backrooms-wiki.wikidot.com/level-30) | 未開放 |
| `level31` | Level 31：溜冰場（The Roller Rink） | `31` | [Wikidot](http://backrooms-wiki.wikidot.com/level-31) | 未開放 |
| `level32` | Level 32：骸骨女王之森（Forest of the Skeleton Queen） | `32` | [Wikidot](http://backrooms-wiki.wikidot.com/level-32) | 未開放 |
| `level34` | Level 34：下水道系統（Sewer System） | `34` | [Wikidot](http://backrooms-wiki.wikidot.com/level-34) | 未開放 |
| `level35` | Level 35：空蕩的停車場（An Empty Car Park） | `35` | [Wikidot](http://backrooms-wiki.wikidot.com/level-35) | 未開放 |
| `level36` | Level 36：永恆機場（Timeless Airport） | `36` | [Wikidot](http://backrooms-wiki.wikidot.com/level-36) | 未開放 |
| `level38` | Level 38：摺疊點（Fold Point） | `38` | [Wikidot](http://backrooms-wiki.wikidot.com/level-38) | 未開放 |
| `level41` | Level 41：黑湖（The Black Lake） | `41` | [Wikidot](http://backrooms-wiki.wikidot.com/level-41) | 未開放 |
| `level42` | Level 42：值得一看的地方（a place of interest） | `42` | [Wikidot](http://backrooms-wiki.wikidot.com/level-42) | 未開放 |
| `level43` | Level 43：水世界（Water World） | `43` | [Wikidot](http://backrooms-wiki.wikidot.com/level-43) | 未開放 |
| `level44` | Level 44：腐蝕中的零售店（Corroding Retail Outlet） | `44` | [Wikidot](http://backrooms-wiki.wikidot.com/level-44) | 未開放 |
| `level45` | Level 45：深淵公司（Abyss Inc.） | `45` | [Wikidot](http://backrooms-wiki.wikidot.com/level-45) | 未開放 |
| `level46` | Level 46：阿拉伯沙漠（Arabian Desert） | `46` | [Wikidot](http://backrooms-wiki.wikidot.com/level-46) | 未開放 |
| `level47` | Level 47：蝰蛇林（The Adderwood） | `47` | [Wikidot](http://backrooms-wiki.wikidot.com/level-47) | 未開放 |
| `level48` | Level 48：夕陽海灘（The Sunset Beach） | `48` | [Wikidot](http://backrooms-wiki.wikidot.com/level-48) | 未開放 |
| `level49` | Level 49：未滿的火焰之河（The Unfilled River of Phlegethon） | `49` | [Wikidot](http://backrooms-wiki.wikidot.com/level-49) | 未開放 |
| `level50` | Level 50：垂死的公路（The moribund Highway） | `50` | [Wikidot](http://backrooms-wiki.wikidot.com/level-50) | 未開放 |
| `level51` | Level 51：被遺忘的城市（The Forgotten City） | `51` | [Wikidot](http://backrooms-wiki.wikidot.com/level-51) | 未開放 |
| `level53` | Level 53：孤身一人（Alone） | `53` | [Wikidot](http://backrooms-wiki.wikidot.com/level-53) | 未開放 |
| `level55` | Level 55：中途休息站（Pit Stop） | `55` | [Wikidot](http://backrooms-wiki.wikidot.com/level-55) | 未開放 |
| `level56` | Level 56：凍傷（Frostbite） | `56` | [Wikidot](http://backrooms-wiki.wikidot.com/level-56) | 未開放 |
| `level57` | Level 57：白晝美術館（Diurnal Art Gallery） | `57` | [Wikidot](http://backrooms-wiki.wikidot.com/level-57) | 未開放 |
| `level58` | Level 58：水上奇景（Water Wonder） | `58` | [Wikidot](http://backrooms-wiki.wikidot.com/level-58) | 未開放 |
| `level59` | Level 59：溺歌（Drownsong） | `59` | [Wikidot](http://backrooms-wiki.wikidot.com/level-59) | 未開放 |
| `level60` | Level 60：海灣步道（The Baywalk） | `60` | [Wikidot](http://backrooms-wiki.wikidot.com/level-60) | 未開放 |
| `level61` | Level 61：後室鄉村俱樂部（The Backrooms Country Club） | `61` | [Wikidot](http://backrooms-wiki.wikidot.com/level-61) | 未開放 |
| `level62` | Level 62：叢林後院（Jungle Backyard） | `62` | [Wikidot](http://backrooms-wiki.wikidot.com/level-62) | 未開放 |
| `level63` | Level 63：寧靜（Tranquility） | `63` | [Wikidot](http://backrooms-wiki.wikidot.com/level-63) | 未開放 |
| `level64` | Level 64：潛伏的黑暗（The Lurking Darkness） | `64` | [Wikidot](http://backrooms-wiki.wikidot.com/level-64) | 未開放 |
| `level65` | Level 65：染血花園（Bloodstained Garden） | `65` | [Wikidot](http://backrooms-wiki.wikidot.com/level-65) | 未開放 |
| `level66` | Level 66：展品（Exhibit） | `66` | [Wikidot](http://backrooms-wiki.wikidot.com/level-66) | 未開放 |
| `level67` | Level 67：渴望麵包坊（Αρτοποιείο Desire） | `67` | [Wikidot](http://backrooms-wiki.wikidot.com/level-67) | 未開放 |
| `level68` | Level 68：吞噬者劇院（Theater The Eater） | `68` | [Wikidot](http://backrooms-wiki.wikidot.com/level-68) | 未開放 |
| `level70` | Level 70：寒冷鎮（Froidetown） | `70` | [Wikidot](http://backrooms-wiki.wikidot.com/level-70) | 未開放 |
| `level71` | Level 71：虛空地下室（Void Basement） | `71` | [Wikidot](http://backrooms-wiki.wikidot.com/level-71) | 未開放 |
| `level72` | Level 72：深坑（The Pit） | `72` | [Wikidot](http://backrooms-wiki.wikidot.com/level-72) | 未開放 |
| `level73` | Level 73：赤紅之地（The Redlands） | `73` | [Wikidot](http://backrooms-wiki.wikidot.com/level-73) | 未開放 |
| `level74` | Level 74：怯場（Stage Fright） | `74` | [Wikidot](http://backrooms-wiki.wikidot.com/level-74) | 未開放 |
| `level75` | Level 75：閾限（Liminality） | `75` | [Wikidot](http://backrooms-wiki.wikidot.com/level-75) | 未開放 |
| `level76` | Level 76：自焦木邊緣歸來（Back from the Charwood Brink） | `76` | [Wikidot](http://backrooms-wiki.wikidot.com/level-76) | 未開放 |
| `level77` | Level 77：喔，雪納度（Oh, Shenandoah） | `77` | [Wikidot](http://backrooms-wiki.wikidot.com/level-77) | 未開放 |
| `level78` | Level 78：太空站（Space Station） | `78` | [Wikidot](http://backrooms-wiki.wikidot.com/level-78) | 未開放 |
| `level79` | Level 79：機器中的幽靈（Ghosts in the Machine） | `79` | [Wikidot](http://backrooms-wiki.wikidot.com/level-79) | 未開放 |
| `level80` | Level 80：遺忘之言（Sententia Oblita） | `80` | [Wikidot](http://backrooms-wiki.wikidot.com/level-80) | 未開放 |
| `level81` | Level 81：下班後的結局（Afterhours Ending） | `81` | [Wikidot](http://backrooms-wiki.wikidot.com/level-81) | 未開放 |
| `level82` | Level 82：自動化交易（Self-Automated Trade） | `82` | [Wikidot](http://backrooms-wiki.wikidot.com/level-82) | 未開放 |
| `level83` | Level 83：沉沒的潛艇（The Sunken Submarine） | `83` | [Wikidot](http://backrooms-wiki.wikidot.com/level-83) | 未開放 |
| `level84` | Level 84：變動的樹籬迷宮（The Shifting Hedge Maze） | `84` | [Wikidot](http://backrooms-wiki.wikidot.com/level-84) | 未開放 |
| `level85` | Level 85：永恆光輝（Brilliant Evermore） | `85` | [Wikidot](http://backrooms-wiki.wikidot.com/level-85) | 未開放 |
| `level86` | Level 86：河門塔（Rivergate Tower） | `86` | [Wikidot](http://backrooms-wiki.wikidot.com/level-86) | 未開放 |
| `level87` | Level 87：時間走廊（Hallways of Time） | `87` | [Wikidot](http://backrooms-wiki.wikidot.com/level-87) | 未開放 |
| `level88` | Level 88：靜音之湖（Muted Lake） | `88` | [Wikidot](http://backrooms-wiki.wikidot.com/level-88) | 未開放 |
| `level89` | Level 89：恐怖公園（Dreaded Park） | `89` | [Wikidot](http://backrooms-wiki.wikidot.com/level-89) | 未開放 |
| `level90` | Level 90：新裝修的家（Newly Renovated Home） | `90` | [Wikidot](http://backrooms-wiki.wikidot.com/level-90) | 未開放 |
| `level91` | Level 91：不過是回憶（But a Memory） | `91` | [Wikidot](http://backrooms-wiki.wikidot.com/level-91) | 未開放 |
| `level92` | Level 92：輻射避難所（Fallout Shelter） | `92` | [Wikidot](http://backrooms-wiki.wikidot.com/level-92) | 未開放 |
| `level93` | Level 93：山頂（The Summit） | `93` | [Wikidot](http://backrooms-wiki.wikidot.com/level-93) | 未開放 |
| `level95` | Level 95：來吧，甜美的博愛（Come, Sweet Agape） | `95` | [Wikidot](http://backrooms-wiki.wikidot.com/level-95) | 未開放 |
| `level96` | Level 96：慰藉（Solace） | `96` | [Wikidot](http://backrooms-wiki.wikidot.com/level-96) | 未開放 |
| `level98` | Level 98：市中心餐館（Downtown Diner） | `98` | [Wikidot](http://backrooms-wiki.wikidot.com/level-98) | 未開放 |
| `level99` | Level 99：食屍鬼鎮（Ghoul Town） | `99` | [Wikidot](http://backrooms-wiki.wikidot.com/level-99) | 未開放 |

## Wikidot 子樓層 0～99（第一批沒有的 39 層）

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| `level0p1` | Level 0.1：天頂站（Zenith Station） | `0.1` | Wikidot | 已開放 |
| `level0p3` | Level 0.3：冰凍房間（The Icy Rooms） | `0.3` | Wikidot | 未開放 |
| `level0p5` | Level 0.5：水域幽閉醫務室（Aquaclaustrophobic Infirmary） | `0.5` | Wikidot | 未開放 |
| `level0p7` | Level 0.7：追憶街區（The Reminiscence District） | `0.7` | Wikidot | 未開放 |
| `level1p2` | Level 1.2：混凝土花園（Concrete Garden） | `1.2` | Wikidot | 未開放 |
| `level1p3` | Level 1.3：惡意（Malignance） | `1.3` | Wikidot | 未開放 |
| `level1p5` | Level 1.5：倒轉（Inverted） | `1.5` | Wikidot | 未開放 |
| `level2p1` | Level 2.1：上鎖（Locked） | `2.1` | Wikidot | 未開放 |
| `level3p5` | Level 3.5：電子都市（Electropolis） | `3.5` | Wikidot | 未開放 |
| `level5p1` | Level 5.1：恐怖旅館賭場盛大開幕（GRAND OPENING OF THE TERROR HOTEL CASINO） | `5.1` | Wikidot | 未開放 |
| `level5p2` | Level 5.2：美景（Scenic Views） | `5.2` | Wikidot | 未開放 |
| `level5p3` | Level 5.3：普羅米修斯圖書館（Promethei Bibliotheca） | `5.3` | Wikidot | 未開放 |
| `level5p5` | Level 5.5：荒廢修道院（The Ruined Monasteries） | `5.5` | Wikidot | 未開放 |
| `level6p2` | Level 6.2：霓虹迷宮（The Neon Maze） | `6.2` | Wikidot | 未開放 |
| `level6p3` | Level 6.3：極黑（Vantablack） | `6.3` | Wikidot | 未開放 |
| `level6p31` | Level 6.31：刺穿帷幕（Pierce the Veil） | `6.31` | Wikidot | 未開放 |
| `level7p6` | Level 7.6：撤離（Evacuation） | `7.6` | Wikidot | 未開放 |
| `level7p7` | Level 7.7：被遺棄的殘骸（The Forsaken Debris） | `7.7` | Wikidot | 未開放 |
| `level7p8` | Level 7.8：穿刺之海（Impaled Ocean） | `7.8` | Wikidot | 未開放 |
| `level8p1` | Level 8.1：死寂洞窟（The Dead Caverns） | `8.1` | Wikidot | 未開放 |
| `level9p2` | Level 9.2：黑市（Black Market） | `9.2` | Wikidot | 未開放 |
| `level9p3` | Level 9.3：陰天流形（The Overcast Manifold） | `9.3` | Wikidot | 未開放 |
| `level9p5` | Level 9.5：羅徹斯特藍調（Rochester Blues） | `9.5` | Wikidot | 未開放 |
| `level9p9` | Level 9.9：D 日（D-Day） | `9.9` | Wikidot | 未開放 |
| `level10p1` | Level 10.1：屍湖（Corpse Lake） | `10.1` | Wikidot | 未開放 |
| `level10p2` | Level 10.2：乾草堆天堂（Hay Bale Heaven） | `10.2` | Wikidot | 未開放 |
| `level11p1` | Level 11.1：私人企業（Private Enterprise） | `11.1` | Wikidot | 未開放 |
| `level11p2` | Level 11.2：避難所（The Refuge） | `11.2` | Wikidot | 未開放 |
| `level11p3` | Level 11.3：紅燈區（The Red Light District） | `11.3` | Wikidot | 未開放 |
| `level11p5` | Level 11.5：建築？它們現在睡著了（The Buildings? They're Sleeping Now） | `11.5` | Wikidot | 未開放 |
| `level15p1` | Level 15.1：徒勞（Futility） | `15.1` | Wikidot | 未開放 |
| `level36p1` | Level 36.1：星界飛機（Astral Airplane） | `36.1` | Wikidot | 未開放 |
| `level46p1` | Level 46.1：鹽丘與煉獄修道院（The Saline Dunes and The Purgatorial Monastery） | `46.1` | Wikidot | 未開放 |
| `level48p1` | Level 48.1：夜光大地（Noctilucent Ground） | `48.1` | Wikidot | 未開放 |
| `level49p1` | Level 49.1：銘記逝者（Remember the Fallen） | `49.1` | Wikidot | 未開放 |
| `level49p2` | Level 49.2：訓練場 M-1（Training Grounds M-1） | `49.2` | Wikidot | 未開放 |
| `level51p2` | Level 51.2：她的搖籃（Her Cradle） | `51.2` | Wikidot | 未開放 |
| `level94p1` | Level 94.1：國王永不降臨之地（Place where the King never comes.） | `94.1` | Wikidot | 未開放 |
| `level97p5` | Level 97.5：我的淚（不）會成海（My Tears Are (Not) Becoming a Sea） | `97.5` | Wikidot | 未開放 |

## Wikidot 主樓層 100～199（91 層）

138、169、176、183、190 是空號，沒有列；143 雖然是空號，第一批已經依狐霖的後室列入。

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| `level100` | Level 100：短暫的和平（Brief Peace） | `100` | [Wikidot](http://backrooms-wiki.wikidot.com/level-100) | 未開放 |
| `level101` | Level 101：你的夢將不再破曉（Your Dream Shall Never Dawn Again） | `101` | [Wikidot](http://backrooms-wiki.wikidot.com/level-101) | 未開放 |
| `level102` | Level 102：悲傷（Kanashimi） | `102` | [Wikidot](http://backrooms-wiki.wikidot.com/level-102) | 未開放 |
| `level103` | Level 103：花開墓穴（Bloom Crypt） | `103` | [Wikidot](http://backrooms-wiki.wikidot.com/level-103) | 未開放 |
| `level104` | Level 104：世界盡頭（The Edge of the World） | `104` | [Wikidot](http://backrooms-wiki.wikidot.com/level-104) | 未開放 |
| `level105` | Level 105：祈禱所（Prayerhouse） | `105` | [Wikidot](http://backrooms-wiki.wikidot.com/level-105) | 未開放 |
| `level106` | Level 106：向下（Going Down） | `106` | [Wikidot](http://backrooms-wiki.wikidot.com/level-106) | 未開放 |
| `level107` | Level 107：碎裂的現實（Splintered Reality） | `107` | [Wikidot](http://backrooms-wiki.wikidot.com/level-107) | 未開放 |
| `level108` | Level 108：沃土（Fertile Grounds） | `108` | [Wikidot](http://backrooms-wiki.wikidot.com/level-108) | 未開放 |
| `level109` | Level 109：霓虹醫院（The Neon Hospital） | `109` | [Wikidot](http://backrooms-wiki.wikidot.com/level-109) | 未開放 |
| `level110` | Level 110：永恆的終結（Everlasting End） | `110` | [Wikidot](http://backrooms-wiki.wikidot.com/level-110) | 未開放 |
| `level111` | Level 111：樣品樓層（The Model Floor） | `111` | [Wikidot](http://backrooms-wiki.wikidot.com/level-111) | 未開放 |
| `level112` | Level 112：嘴（Mouths） | `112` | [Wikidot](http://backrooms-wiki.wikidot.com/level-112) | 未開放 |
| `level113` | Level 113：真實機械之地（Verum Terra Machinis） | `113` | [Wikidot](http://backrooms-wiki.wikidot.com/level-113) | 未開放 |
| `level114` | Level 114：好夢（Sweet Dreams） | `114` | [Wikidot](http://backrooms-wiki.wikidot.com/level-114) | 未開放 |
| `level115` | Level 115：腐朽檔案館（Decaying Archives） | `115` | [Wikidot](http://backrooms-wiki.wikidot.com/level-115) | 未開放 |
| `level116` | Level 116：神之尖塔（Godspire） | `116` | [Wikidot](http://backrooms-wiki.wikidot.com/level-116) | 未開放 |
| `level117` | Level 117：伊莎貝爾峽谷（Isabelle's Gorge） | `117` | [Wikidot](http://backrooms-wiki.wikidot.com/level-117) | 未開放 |
| `level118` | Level 118：最黑暗的營地（The Darkest Campground） | `118` | [Wikidot](http://backrooms-wiki.wikidot.com/level-118) | 未開放 |
| `level119` | Level 119：普通的水上樂園……？（Average Waterpark..?） | `119` | [Wikidot](http://backrooms-wiki.wikidot.com/level-119) | 未開放 |
| `level120` | Level 120：損壞的伺服馬達（Broken Servos） | `120` | [Wikidot](http://backrooms-wiki.wikidot.com/level-120) | 未開放 |
| `level121` | Level 121：消逝群島（The Fading Islands） | `121` | [Wikidot](http://backrooms-wiki.wikidot.com/level-121) | 未開放 |
| `level122` | Level 122：清潔工點亮的商場（The Janitor's Lit Up Mall） | `122` | [Wikidot](http://backrooms-wiki.wikidot.com/level-122) | 未開放 |
| `level123` | Level 123：回想（Recall） | `123` | [Wikidot](http://backrooms-wiki.wikidot.com/level-123) | 未開放 |
| `level124` | Level 124：神遊（Fugue） | `124` | [Wikidot](http://backrooms-wiki.wikidot.com/level-124) | 未開放 |
| `level125` | Level 125：遺產巷（Legacy Lane） | `125` | [Wikidot](http://backrooms-wiki.wikidot.com/level-125) | 未開放 |
| `level126` | Level 126：一圖之值（A Picture's Worth） | `126` | [Wikidot](http://backrooms-wiki.wikidot.com/level-126) | 未開放 |
| `level127` | Level 127：頭痛（Headache） | `127` | [Wikidot](http://backrooms-wiki.wikidot.com/level-127) | 未開放 |
| `level128` | Level 128：廣闊戶外（The Great Outdoors） | `128` | [Wikidot](http://backrooms-wiki.wikidot.com/level-128) | 未開放 |
| `level129` | Level 129：白色森林（The White Forest） | `129` | [Wikidot](http://backrooms-wiki.wikidot.com/level-129) | 未開放 |
| `level130` | Level 130：威爾伯恩醫院（Wellborn Hospital） | `130` | [Wikidot](http://backrooms-wiki.wikidot.com/level-130) | 未開放 |
| `level131` | Level 131：窗簾是藍色的（The curtains are blue） | `131` | [Wikidot](http://backrooms-wiki.wikidot.com/level-131) | 未開放 |
| `level132` | Level 132：莫里岡公國（The Duchy of Morigont） | `132` | [Wikidot](http://backrooms-wiki.wikidot.com/level-132) | 未開放 |
| `level133` | Level 133：永恆（Aeternum） | `133` | [Wikidot](http://backrooms-wiki.wikidot.com/level-133) | 未開放 |
| `level134` | Level 134：海之傳說（Tales of the Sea） | `134` | [Wikidot](http://backrooms-wiki.wikidot.com/level-134) | 未開放 |
| `level135` | Level 135：醒著的夢（Waking Dream） | `135` | [Wikidot](http://backrooms-wiki.wikidot.com/level-135) | 未開放 |
| `level136` | Level 136：衣櫃裡的骷髏（Skeletons in the Closet） | `136` | [Wikidot](http://backrooms-wiki.wikidot.com/level-136) | 未開放 |
| `level137` | Level 137：蛾之鳥之蜂（Bee of the Bird of the Moth） | `137` | [Wikidot](http://backrooms-wiki.wikidot.com/level-137) | 未開放 |
| `level139` | Level 139：戰士的試煉（The Warrior's Trial） | `139` | [Wikidot](http://backrooms-wiki.wikidot.com/level-139) | 未開放 |
| `level140` | Level 140：蜂蜜（Honey） | `140` | [Wikidot](http://backrooms-wiki.wikidot.com/level-140) | 未開放 |
| `level141` | Level 141：鍋爐房（The Boiler Rooms） | `141` | [Wikidot](http://backrooms-wiki.wikidot.com/level-141) | 未開放 |
| `level142` | Level 142：Earthjump 工作室™（Earth Earthjump Studios™） | `142` | [Wikidot](http://backrooms-wiki.wikidot.com/level-142) | 未開放 |
| `level144` | Level 144：巨人堤道（The Giant's Causeway） | `144` | [Wikidot](http://backrooms-wiki.wikidot.com/level-144) | 未開放 |
| `level145` | Level 145：永久隔離（Perpetual Isolation） | `145` | [Wikidot](http://backrooms-wiki.wikidot.com/level-145) | 未開放 |
| `level146` | Level 146：感恩酒館（The Tavern of Thanks） | `146` | [Wikidot](http://backrooms-wiki.wikidot.com/level-146) | 未開放 |
| `level147` | Level 147：如此熟悉的路線（Such a familiar route） | `147` | [Wikidot](http://backrooms-wiki.wikidot.com/level-147) | 未開放 |
| `level148` | Level 148：活著的樓層（The Living Level） | `148` | [Wikidot](http://backrooms-wiki.wikidot.com/level-148) | 未開放 |
| `level149` | Level 149：椰子群島（The Coconut Isles） | `149` | [Wikidot](http://backrooms-wiki.wikidot.com/level-149) | 未開放 |
| `level150` | Level 150：記憶鑄造廠（Memory Foundry） | `150` | [Wikidot](http://backrooms-wiki.wikidot.com/level-150) | 未開放 |
| `level151` | Level 151：娃娃屋（The Dollhouse） | `151` | [Wikidot](http://backrooms-wiki.wikidot.com/level-151) | 未開放 |
| `level152` | Level 152：監視遊戲（The Surveillance Game） | `152` | [Wikidot](http://backrooms-wiki.wikidot.com/level-152) | 未開放 |
| `level153` | Level 153：布洛克書店（Brock's Book Shop） | `153` | [Wikidot](http://backrooms-wiki.wikidot.com/level-153) | 未開放 |
| `level155` | Level 155：石英構成（Quartz Composition） | `155` | [Wikidot](http://backrooms-wiki.wikidot.com/level-155) | 未開放 |
| `level156` | Level 156：後台（The Backstage） | `156` | [Wikidot](http://backrooms-wiki.wikidot.com/level-156) | 未開放 |
| `level157` | Level 157：棋局（The Chess Game） | `157` | [Wikidot](http://backrooms-wiki.wikidot.com/level-157) | 未開放 |
| `level158` | Level 158：猛攻車站（ONSLAUGHT STATION） | `158` | [Wikidot](http://backrooms-wiki.wikidot.com/level-158) | 未開放 |
| `level159` | Level 159：野獸之地（Beastland） | `159` | [Wikidot](http://backrooms-wiki.wikidot.com/level-159) | 未開放 |
| `level160` | Level 160：承諾記憶之城（The City Of Promised Memories） | `160` | [Wikidot](http://backrooms-wiki.wikidot.com/level-160) | 未開放 |
| `level161` | Level 161：屍體之屋（The Cadaver House） | `161` | [Wikidot](http://backrooms-wiki.wikidot.com/level-161) | 未開放 |
| `level162` | Level 162：混凝土牆（The Concrete Wall） | `162` | [Wikidot](http://backrooms-wiki.wikidot.com/level-162) | 未開放 |
| `level163` | Level 163：虛空之中（Amidst The Void） | `163` | [Wikidot](http://backrooms-wiki.wikidot.com/level-163) | 未開放 |
| `level164` | Level 164：罪惡之城（Sin City） | `164` | [Wikidot](http://backrooms-wiki.wikidot.com/level-164) | 未開放 |
| `level165` | Level 165：虛假的幸福（Lying Bliss） | `165` | [Wikidot](http://backrooms-wiki.wikidot.com/level-165) | 未開放 |
| `level166` | Level 166：天坑街 130 號（130 SINKHOLE ST.） | `166` | [Wikidot](http://backrooms-wiki.wikidot.com/level-166) | 未開放 |
| `level167` | Level 167：美國國民消遣（America's Favorite Pastime） | `167` | [Wikidot](http://backrooms-wiki.wikidot.com/level-167) | 未開放 |
| `level168` | Level 168：願望成真（The Wish Came True） | `168` | [Wikidot](http://backrooms-wiki.wikidot.com/level-168) | 未開放 |
| `level170` | Level 170：混凝土箱（Concrete Boxes） | `170` | [Wikidot](http://backrooms-wiki.wikidot.com/level-170) | 未開放 |
| `level171` | Level 171：宏大幻覺（A Grand Illusion） | `171` | [Wikidot](http://backrooms-wiki.wikidot.com/level-171) | 未開放 |
| `level172` | Level 172：凍結的勇氣（Frozen Courage） | `172` | [Wikidot](http://backrooms-wiki.wikidot.com/level-172) | 未開放 |
| `level173` | Level 173：水墓洞窟（Watery Grave Caves） | `173` | [Wikidot](http://backrooms-wiki.wikidot.com/level-173) | 未開放 |
| `level174` | Level 174：永恆伽瑪（Eternal Gamma） | `174` | [Wikidot](http://backrooms-wiki.wikidot.com/level-174) | 未開放 |
| `level175` | Level 175：單色瘋狂（Monochrome Madness） | `175` | [Wikidot](http://backrooms-wiki.wikidot.com/level-175) | 未開放 |
| `level177` | Level 177：起立鼓掌（Standing Ovation） | `177` | [Wikidot](http://backrooms-wiki.wikidot.com/level-177) | 未開放 |
| `level178` | Level 178：京都之夢（Kyoto Dreams） | `178` | [Wikidot](http://backrooms-wiki.wikidot.com/level-178) | 未開放 |
| `level179` | Level 179：失物招領二手店（Thrift for the Lost and Found） | `179` | [Wikidot](http://backrooms-wiki.wikidot.com/level-179) | 未開放 |
| `level180` | Level 180：航海者之海（Voyager's Sea） | `180` | [Wikidot](http://backrooms-wiki.wikidot.com/level-180) | 未開放 |
| `level181` | Level 181：受保護的洞窟（Protected Caverns） | `181` | [Wikidot](http://backrooms-wiki.wikidot.com/level-181) | 未開放 |
| `level182` | Level 182：芳香療法（Aromatherapy） | `182` | [Wikidot](http://backrooms-wiki.wikidot.com/level-182) | 未開放 |
| `level184` | Level 184：被遺忘的堡壘原野（Field of Forgotten Forts） | `184` | [Wikidot](http://backrooms-wiki.wikidot.com/level-184) | 未開放 |
| `level185` | Level 185：下一班從月台發車的列車（The Next Train to Depart From Platform） | `185` | [Wikidot](http://backrooms-wiki.wikidot.com/level-185) | 未開放 |
| `level186` | Level 186：無盡山谷（The Infinite Valley） | `186` | [Wikidot](http://backrooms-wiki.wikidot.com/level-186) | 未開放 |
| `level187` | Level 187：無人的夏天（Nobody's Summer） | `187` | [Wikidot](http://backrooms-wiki.wikidot.com/level-187) | 未開放 |
| `level189` | Level 189：後室超市 BACKMART™（BACKMART™） | `189` | [Wikidot](http://backrooms-wiki.wikidot.com/level-189) | 未開放 |
| `level191` | Level 191：邊跑邊射.EXE（RUN-AND-GUN.EXE） | `191` | [Wikidot](http://backrooms-wiki.wikidot.com/level-191) | 未開放 |
| `level192` | Level 192：空想性錯視（Pareidolia） | `192` | [Wikidot](http://backrooms-wiki.wikidot.com/level-192) | 未開放 |
| `level193` | Level 193：哀悼（Mourning） | `193` | [Wikidot](http://backrooms-wiki.wikidot.com/level-193) | 未開放 |
| `level194` | Level 194：你不會下載一座雨林（YOU WOULDN'T DOWNLOAD A RAINFOREST） | `194` | [Wikidot](http://backrooms-wiki.wikidot.com/level-194) | 未開放 |
| `level195` | Level 195：鈦木林（Titanium Timbers） | `195` | [Wikidot](http://backrooms-wiki.wikidot.com/level-195) | 未開放 |
| `level196` | Level 196：帽子星球（Planet of Hats） | `196` | [Wikidot](http://backrooms-wiki.wikidot.com/level-196) | 未開放 |
| `level198` | Level 198：隨我走進風暴（follow me into the storm） | `198` | [Wikidot](http://backrooms-wiki.wikidot.com/level-198) | 未開放 |
| `level199` | Level 199：夢境森林（Oneiric Forest） | `199` | [Wikidot](http://backrooms-wiki.wikidot.com/level-199) | 未開放 |

## 中文原創樓層（後室中文維基，22 層）

標題轉成繁體。

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 |
|---|---|---|---|---|
| `level_c6` | Level C-6：不晝城 | `C-6` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c7` | Level C-7：雨一直下 | `C-7` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c10` | Level C-10：淺水湖 | `C-10` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c13` | Level C-13：居民樓 | `C-13` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c15` | Level C-15：紫月之嶼 | `C-15` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c18` | Level C-18：酒窖 | `C-18` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c20` | Level C-20：礦洞 | `C-20` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c22` | Level C-22：艙室 | `C-22` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c26` | Level C-26：瓜田 | `C-26` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c27` | Level C-27：菌菇之森 | `C-27` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c32` | Level C-32：公共盥洗室 | `C-32` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c44` | Level C-44：浴缸。 | `C-44` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c46` | Level C-46：公路 | `C-46` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c47` | Level C-47：單人學校 | `C-47` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c48` | Level C-48：登記入住！ | `C-48` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c50` | Level C-50：之乎者也圖書館 | `C-50` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c54` | Level C-54：長險七號公路 | `C-54` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c59` | Level C-59：養雞場 | `C-59` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c67` | Level C-67：樓道 | `C-67` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c72` | Level C-72：外婆的澎湖灣 | `C-72` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c76` | Level C-76：殯儀館 | `C-76` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |
| `level_c80` | Level C-80：積雪公路 | `C-80` | [後室中文維基](http://backrooms-wiki-cn.wikidot.com/normal-levels-cn-i) | 未開放 |

## Fandom 版本（29 層）

Fandom 從 Level 12 左右開始，同一個編號的內容就跟 Wikidot 完全不同（例如 Wikidot 的 161 是 The Cadaver House，Fandom 的 161 是阿帕拉契的荒涼）。Fandom 網站擋自動讀取，這裡只列搜尋結果裡確認得到標題的樓層；名稱跟 Wikidot 一樣的（15 Futuristic Halls、21 Numbered Doors）沒有重複列。論壇上有人說 Fandom 在 2025 年把負樓層改成正編號，第一批的負樓層可能已經過時，開工前要再確認。

| 資料夾 / 套件 | 樓層名稱 | 出口鍵 | 來源 | 狀態 | 說明 |
|---|---|---|---|---|---|
| `level0p1_fd` | Level 0.1：深邃空洞（Deep Emptiness） | `0.1` | [Fandom](https://backrooms.fandom.com/wiki/Level_0.1) | 未開放 | 總表上叫 The Danger Zone；一長排門，門後大多是空房間 |
| `level0p11` | Level 0.11：水損（Water Damage） | `0.11` | [Fandom 總表](https://backrooms.fandom.com/wiki/Level_List) | 未開放 | Escape the Backrooms 的「更可怕的 Level 0」就是這層 |
| `level1p5_fd` | Level 1.5：潛伏的危險（Lurking Danger） | `1.5` | [Fandom 總表](https://backrooms.fandom.com/wiki/Level_List) | 未開放 |  |
| `level2p1_fd` | Level 2.1：地下綜合設施（The Subterranean Complex） | `2.1` | [Fandom](https://backrooms.fandom.com/wiki/Level_2.1) | 未開放 | 地下混凝土隧道和洞穴，高溫、實體多 |
| `level12_fd` | Level 12：建築 // 血肉之中（Aedificium // In the Flesh） | `12` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level13_fd` | Level 13：玻璃瘋狂（Vitrum Madness） | `13` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level13p3` | Level 13.3：潟湖營地（Camp Lagoon） | `13.3` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level16_fd` | Level 16：J. 貝瑞精神病院（J. Barry Mental Asylum） | `16` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level19_fd` | Level 19：深淵旅店（Abyss Inn） | `19` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level19p2` | Level 19.2：折磨長廊（Torturous Halls） | `19.2` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level22_fd` | Level 22：旋轉木馬亂鬥！（Merry-go-mash!） | `22` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 | 舊版總表叫 Polluted Ruins |
| `level23_fd` | Level 23：維也納的星期天（Viennese Sunday） | `23` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level24_fd` | Level 24：外星（Extraterrestre） | `24` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level25_fd` | Level 25：故障停用（Out of Order） | `25` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level26_fd` | Level 26：異世界（Otherworldly） | `26` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level27_fd` | Level 27：木質荒蕪（Ligneous Desolation） | `27` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level28_fd` | Level 28：閣樓（The Attic） | `28` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level29_fd` | Level 29：藝術視角（Artistic View） | `29` | [Fandom 9～99](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_9-99) | 未開放 |  |
| `level30_fd` | Level 30：水域循環（Aquatic Cycles） | `30` | [Fandom](https://backrooms.fandom.com/wiki/Level_30) | 未開放 | 分區相連的大型泳池設施，各區安全程度不同 |
| `level100_fd` | Level 100：無聲的孤寂（Soundless Solitude） | `100` | [Fandom](https://backrooms.fandom.com/wiki/Level_100) | 未開放 | 兩邊都是海、被水包圍的狹長沙洲 |
| `level120_fd` | Level 120：咽喉要道（Chokepoint） | `120` | [Fandom 100～199](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_100-199) | 未開放 |  |
| `level150_fd` | Level 150：花開忘憂（Flowers Bloom, Forget Worries） | `150` | [Fandom 100～199](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_100-199) | 未開放 |  |
| `level161_fd` | Level 161：阿帕拉契的荒涼（Appalachian Desolation） | `161` | [Fandom](https://backrooms.fandom.com/wiki/Level_161) | 未開放 | 從 1980 年開始的時間迴圈；異常森林大火，資源稀少、沒有文明 |
| `level161p1` | Level 161.1：阿帕拉契山莊（Appalachian Lodge） | `161.1` | [Fandom](https://backrooms.fandom.com/wiki/Level_161.1) | 未開放 | 從 161 進入；像 1970 年代阿帕拉契山區度假村，很危險 |
| `level170_fd` | Level 170：錄影帶出租店（Video Rental Store） | `170` | [Fandom 100～199](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_100-199) | 未開放 | 修訂版總表叫 Simulation |
| `level180_fd` | Level 180：田園小屋（Cottagecore） | `180` | [Fandom 100～199](https://backrooms.fandom.com/wiki/Backrooms_Wiki:Levels_100-199) | 未開放 |  |
| `level200` | Level 200：共鳴（Resonance） | `200` | [Fandom](https://backrooms.fandom.com/wiki/Level_200) | 未開放 | 聲音會不停迴盪的巨大峽谷群 |
| `level330` | Level 330：溫吞旅館（Lukewarm Hotel） | `330` | [Fandom](https://backrooms.fandom.com/wiki/Level_330) | 未開放 | 第一批把這個名字放在 Level -33 |
| `level499` | Level 499：無盡的保齡球館（The Mother of All Bowling Alleys） | `499` | Wikidot / Fandom / 狐霖後室 | 未開放 | 霓虹燈與無盡球道、酒吧與街機，狐霖熱門補給層（Level 0／1 可連通） |
| `level_pi` | Level π：圓周率層（3.14159265358...） | `π` | [Fandom](https://backrooms.fandom.com/wiki/Level_Pi) | 未開放 | 夾在 Level 3 和 4 之間；到處是彩色加油站，關於它的句子都會押韻 |

## 候補（不算在總數裡）

### Wikidot 無編號樓層（第一批沒有的 44 個）

Agoraphobia（廣場恐懼症）、A Long Forgotten Exclamation...（久被遺忘的驚嘆……）、Ataraxia（心神寧靜）、The Basement（地下室）、The Broken（破碎者）、Catwalk（高空走道）、The Crypt Reality（地窖現實）、Dark Academia（暗黑學院）、Death's Door（死亡之門）、The Forgotten Forest（被遺忘的森林）、The Foundry（鑄造廠）、The Frontrooms（前室）、The Future（未來）、The Grave（墳墓）、Greyrealm — Domain of Upsilon（灰域：Upsilon 的領域）、The Heart of the Universe（宇宙之心）、Hephaestus' Forge（赫菲斯托斯的熔爐）、Remords（悔恨）、home（家）、Hotel Virginia（維吉尼亞旅館）、Induced Fear（誘發的恐懼）、In Extremis（瀕死之際）、Level 14 - The Military Hospital（軍醫院（另一個 Level 14））、Level Θ（Level Θ）、Limbus St.（靈薄街）、Lost Love（失落的愛）、The Middlesorts（（暫無譯名））、Mind Palace（記憶宮殿）、Mirth Ending（歡笑結局）、Monochrome（單色）、The Night Bus（夜間巴士）、October 31, 2000（2000 年 10 月 31 日）、Oneiroscape（夢境地景）、Our Moon（我們的月亮）、PA98（PA98）、The Prenatal（出生之前）、Recurring Dreams（反覆的夢）、Serenity（寧謐）、The Serpent's Void（巨蛇的虛空）、Starfinder Ending（尋星者結局）、the_shadow_spiral（影之螺旋）、The Travelling Market（流動市集）、The Virus Beyond the Door（門後的病毒）、The World Factory（世界工廠）

### Wikidot 100 號以後的子樓層

184.1 Daisy Fields（雛菊原野）、204.1 The Cabalistic Catacombs（神秘地下墓穴）、356.1 The Writings on the Wall（牆上的字）、405.1 I am man, perishing, the living dead.（我是人，正在消亡，活著的死者）、410.1 A Perfectly Abnormal Basement（完美異常的地下室）、422.1 Reflections（倒影）、484.1 The White Room（白房間）、522.1 The Echo Chamber（回音室）

## 遊戲玩法參考：Escape the Backrooms 的關卡

這款遊戲把後室樓層做成可以玩的關卡，做我們自己的樓層時可以參考它的解謎和怪物安排。順序是遊戲裡的關卡順序，不是樓層編號；遊戲之後的更新加了更多關卡（Steam 頁面寫 30 關以上）。

| 遊戲第幾關 | 樓層 | 玩法重點 |
|---|---|---|
| 1 | Level 0：大廳 | 收集梯子零件與鑰匙，平衡走過坑洞；細菌（Bacteria） |
| 2 | Level 1：停車場 | 紅色車門、電梯密碼、4 把鑰匙；Skinwalker |
| 3 | Lights Out（微笑者層） | 燈每 7 秒閃一次，微笑者 |
| 4 | Pipe Dreams | 走車站那條路，被蜘蛛狀的微笑者追 |
| 5 | Electrical Station | 三根拉桿開三道閘門；獵犬、帶電的水 |
| 6 | Abandoned Office | 數家具數量輸入販賣機、監視器上鎖；沒有實體 |
| 7 | Terror Hotel | 照片排序、殺蟲劑噴飛蛾、搭電梯；Skin-Stealer、獵犬 |
| 8 | Boiler Rooms | 巨大迷宮、死亡飛蛾、沒有出口標誌的房間 |
| 9 | 回到 Pipe Dreams | 走「Fun」那條路到橘色房間 |
| 10 | Level Fun | 派對客；蹲下、躲桌子底、氣球房與溜滑梯房 |
| 11 | Poolrooms | 探索拱廊，找帶紅光與出口標誌的門；雜訊代表危險 |
| 12 | Level !（Run for your life） | 沿巨大走廊一路狂奔；派對客、微笑者 |
| 13 | The End | 收集錄影帶打開出口球；圖書館員（瞎的，靠聲音） |
| 14 | Level 9223372036854775807 | 爬樓梯到 94，數字會亂跳，到 94 跳下去 |
| 15 | The Housing Levels | 房屋、卡車、用繩子上浮空城堡；夜晚的人偶男孩 |
| 16 | The Clown Castle | 移動平台、別看動畫國王、雲霄飛車 |
| 17 | Lights Out（黑暗版） | 拿光達掃描器走黑暗迷宮；Wretch |
| 18 | Thalassophobia | 坐船去燈塔、鯊魚籠、海底洞穴；巨大鮟鱇魚 |
| 19 | Cave Systems | 死亡飛蛾與吊橋，再來是 Skin-Stealer 與梯子 |
| 20 | Water Damage（更可怕的 Level 0） | 拉桿、電鋸、限時拉桿；細菌 |
| 21 | Darkened Suburbs | 屋內終端機、Wretch、守望者眼球；大宅裡找 4 個罐子 |
| 22 | Bumper Crop | 直路、穀倉、風車塔；Faceling 掠奪者與農夫 |
| 23 | Level 3999（街機廳） | 走過街機廳就是結局 |
