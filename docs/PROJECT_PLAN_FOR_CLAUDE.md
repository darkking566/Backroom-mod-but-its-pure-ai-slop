# 後室模組總體企劃、程式分流架構與雙 AI 協作交接書（給 Claude）

> **文件定位**：本文件彙整使用者與 Antigravity 討論定案的整體企劃，供 **Claude** 閱讀、對齊技術介面並共同拍板程式分流與分支工作流。  
> **相關詳細藍圖**：
> - 60 個樓層總表與命名規範：[docs/levels/README.md](levels/README.md)
> - 超大合成表 v1（7 份細部設計）：[docs/crafting/README.md](crafting/README.md)
> - 現有協作與目錄規範：[AGENTS.md](../AGENTS.md)、[README.md](../README.md)

---

## 一、核心玩法願景

本模組不只做「後室純探索」，而是結合 **Minecraft 合成與採集特色** 與 **《狐霖的後室RPG》多樓層網狀連結**，打造可長期擴充的後室生存 RPG：

1. **環境拆解與異常穿模家具**：敲打既有的牆壁、地毯、日光燈、水管、老電腦，以及新增的「卡進牆壁／地板的異常穿模家具（椅子、辦公桌、檔案櫃、CRT 電視、販賣機、配電箱等）」，取得細緻拆解零件（持精準採集仍保留原方塊供建築用）。
2. **階梯式特殊破壞權限（Tier 0 → Tier 4）**：特定障礙與稀有結構（封門木板、鐵絲網、高壓電箱、加固鋼牆、紅室封鎖牆、水晶礦脈、終局封印牆）必須用對應素材合成的特殊道具才能破壞。
3. **全原生後室怪物＋Boss＋寶箱**：所有經典後室原生實體（第一批優先：獵犬、微笑者、無面靈、竊皮者、公／母死亡飛蛾、肢團、鈍人、窗戶；後續補齊派對客、悲屍、細菌、鄰里守望者、動畫實體等共 26 種）與 10 隻樓層 Boss 均掉落專屬素材、寶箱或武器核心。所有怪物外觀統一沿用獵犬已驗證的 **Blender 路線 A（純長方體骨骼 + `BoxRig` JSON + 低飽和寫實髒污貼圖）**。
4. **龐大細緻合成表（v1）與五大武器體系**：
   - 包含 **5 座工作站**（原版 3×3、後室拆解台、改裝鍛造台、杏仁蒸餾化學組、異常煉成壇）與 **五大武器**（中世紀鈍器、西式冷兵器、中式冷兵器、熱兵器／槍械與彈藥配件、魔法與異常超自然武器，v1 規劃 79 種武器，含合成＋寶箱／Boss 掉落雙軌）。
   - **遊戲內獨立合成表**：除了標準 Minecraft Recipe JSON（相容 JEI/REI）外，模組內建獨立查詢介面《後室合成圖鑑》，可反查零件來源（哪層敲哪塊／哪隻怪掉／哪種寶箱）。
   - **重要前提**：目前 `docs/crafting/` 定義為 **「合成表第一版（v1）」**，實際試玩後一定會大幅擴充、刪減或調整數值與配方，因此**程式結構必須高度模組化與資料驅動**。
5. **狐霖風格的多樓層網狀出口**：每一層都預先埋好通往多個其他樓層的出口；目標層尚未實作時呈現鎖閉／沒反應（「尚未開放」），待另一邊的樓層做好後即可自動接通，無需回頭修改舊樓層。

---

## 二、Antigravity 與 Claude 的主力分工

兩邊統一在單一資料夾 `Desktop\Backroom` **輪流工作**（不同時寫檔或跑 Gradle），並按系統職責切清目錄所有權：

| 負責方 | 主力工作內容 | 主要擁有的資料夾 |
|---|---|---|
| **Antigravity** | 1. **合成系統 v1 核心架構**：工作站、RecipeType、雙軌拆解邏輯、遊戲內《後室合成圖鑑》UI<br>2. **物品、素材、工具階梯（Tier 0–4）、防具與五大武器系統**（含彈藥、配件、槍械／魔法機制）<br>3. **跨樓層異常穿模家具與寶箱方塊**（模型、材質、LootTable）<br>4. **怪物 3D 模型與動畫資產**（Blender `BoxRig` 匯出至 `art/monsters/`、`assets/.../monsters/`、`textures/entity/`） | `crafting/`、`items/`、`tools/`、`weapons/`、`props/`、`loot/`（及對應 `src/client/`、`assets/`、`data/noclip_backrooms/recipe/`、`art/monsters/`、`docs/crafting/`） |
| **Claude** | 1. **主樓層與各樓層開發**（從 Level 2、3、4、5… 及 Level 1 出口表樓層逐步推進）：地形規劃、ChunkGenerator、維度設定、建築方塊、樓層現象、據點與事件<br>2. **跨樓層網狀出口與傳送系統**（維護並擴充 `core/LevelExits`）<br>3. **怪物 AI 行為、屬性與各樓層生怪規則**（讀取 Antigravity 的 `BoxRig` JSON 接進遊戲）<br>4. **整合測試與各樓層 GameTest / 單元測試** | `level0/`、`level1/`、`level2/`…（60 層套件）、`monsters/<怪>/` 行為 AI、`src/test/`、`src/gametest/`、`docs/levels/` |

> **少量共用檔案**（`BackroomsMod.java`、`BackroomsClient.java`、`lang/en_us.json`、`lang/zh_tw.json`、`sounds.json`、`data/minecraft/tags/block/mineable/*.json`、`CLAUDE.md`）：採「**按區塊追加、不重排對方條目**」原則，確保任何合併幾乎零衝突。

---

## 三、目前資料夾／程式碼分流盤點與建議補齊結構

### 1. 目前已經分流好的部分
- **樓層分流已完成**：`level0/`、`level1/` 已獨立，且 `level2/` 等其餘 58 層的 Java 套件資料夾（`.gitkeep`）與 `docs/levels/<層>/README.md` 企劃檔皆已建妥（命名規則：主層 `level2`、子層小數點用 `p` 如 `level1p01`、負層用 `neg` 如 `level_neg1`、隱秘層小寫底線如 `the_hub` / `level_run` / `level_fun`）。
- **怪物與跨層核心已起頭**：已有 `core/LevelExits.java`、`monsters/Monsters.java`、`monsters/hound/` 與客戶端 `src/client/.../monsters/BoxRig.java`。

### 2. 為支援「合成表 v1 後續易擴充／刪減」，必須先擬定並新增的分流套件

目前 `src/main/java/dev/noclip/backrooms/` **尚未建立**物品、武器、合成與穿模家具的獨立分流。建議兩邊對齊以下目標結構，讓之後要增減任何物品、配方或武器都不會牽動樓層程式：

```text
src/main/java/dev/noclip/backrooms/
├─ BackroomsMod.java            總入口：只依序呼叫各系統與各樓層 register()
├─ core/                        跨系統共用契約（極簡、穩定，修改前兩邊對齊）
│  ├─ LevelKeys.java            [建議新增] 全樓層出口鍵常數（"0", "1.01", "2", "fun"...）
│  ├─ LevelExits.java           [既有] 跨樓層出口登記與傳送（可擴充來源資訊與解鎖條件）
│  ├─ ToolTiers.java            [建議新增] Tier 1–4 破壞權限 BlockTag 常數（供樓層方塊與工具共用）
│  └─ PropRegistry.java         [建議新增] 穿模家具與共用寶箱參考介面（供各樓層地形生成器取用）
├─ items/                       [Antigravity] 跨樓層基礎/進階素材、消耗品、防具
├─ tools/                       [Antigravity] Tier 1–4 特殊破壞工具與破壞權限攔截/提示
├─ weapons/                     [Antigravity] 五大系武器（melee/ firearms/ magic/ + ammo/ mods/）
├─ props/                       [Antigravity] 24 種異常穿模家具、工作站方塊與共用寶箱
├─ crafting/                    [Antigravity] 工作站 Menu、RecipeType/Serializer、資料驅動圖鑑索引
├─ monsters/                    [合作] Monsters.java + 一怪一資料夾（模型資產 Antigravity、AI Claude）
├─ level0/  level1/  level2/ ...[Claude] 各樓層獨立套件

src/client/java/dev/noclip/backrooms/
├─ BackroomsClient.java         畫面端總入口
├─ crafting/                    [Antigravity] 工作站畫面（Screen）＋ 獨立《後室合成圖鑑》UI
├─ weapons/                     [Antigravity] 槍械瞄準/後座力/粒子與魔法施法特效
└─ monsters/                    [既有] BoxRig.java 與各怪物 Model/Renderer
```

### 3. 為了讓合成表 v1 之後「好增、好刪、好改」，堅持四條資料驅動原則
1. **所有配方 100% 走 JSON（`data/noclip_backrooms/recipe/`）**：包含拆解台、改裝台、蒸餾組、異常煉成壇都做成標準 `RecipeType`，日後調數值、改材料或刪配方只要改 JSON，不需改 Java 邏輯；且遊戲內《後室合成圖鑑》直接動態掃描執行期 `RecipeManager` 與來源資料表，**增刪配方後圖鑑自動同步，絕不寫死在 UI 裡**。
2. **方塊破壞權限與雙軌拆解 100% 走 BlockTag ＋ LootTable JSON**：
   - 門檻標籤放 `data/noclip_backrooms/tags/block/requires_tier_1.json` ～ `requires_tier_4.json`。
   - Claude 新增任何樓層方塊只要把方塊 ID 加進對應的 `requires_tier_X.json`（或先留空預設 Tier 0），Antigravity 的工具權限檢查就會自動生效，兩邊程式碼零耦合！
   - 既有 Level 0／Level 1 方塊的「一般工具掉拆解零件、精準採集掉原方塊」全由 LootTable JSON 控制，日後要調整掉落機率或增減素材都只需改 LootTable。
3. **武器數值資料化（建議）**：將五大武器傷害、攻速、射速、彈匣容量、耗材等集中定義於資料表或獨立設定檔，方便 v1 試玩後快速統一調平衡。
4. **嚴格遵守 ID 永不改名原則**（[README.md](../README.md#L91-L94)）：現有 `almond_water`、`alloy`、`phosphor_powder`、`flashlight` 等既有物品一律沿用原 ID，只擴充其合成用途，不另建重複道具（且逐步評估是否將跨層共用道具的引用從 `level1/` 透過介面暴露給 `crafting/`，但保持原註冊 ID 不變以保護存檔）。

---

## 四、狐霖風格多樓層網狀出口設計（延伸現有 `core/LevelExits.java`）

目前 Claude 已實作的 [LevelExits.java](../src/main/java/dev/noclip/backrooms/core/LevelExits.java) 架構方向完全正確：
- 任何樓層的出口只要呼叫 `LevelExits.use(player, "目的層鍵")`，該層還沒做好時自動顯示「尚未開放」並攔截；等目的地做好並呼叫 `LevelExits.open("目的層鍵", arrival)`，所有指向它的出口就同時接通。

面向未來幾十層網狀互連，提出以下 **3 個小擴充建議供 Claude 評估**：

1. **集中出口鍵常數（`core/LevelKeys.java`）**
   - 直接對應 [docs/levels/README.md](levels/README.md) 表格裡的 60 個標準鍵（`"0"`, `"1"`, `"1.01"`, `"1.1"`, `"2"`, `"4.3"`, `"37"`, `"188"`, `"!"`, `"fun"`, `"hub"`, `"LX"`, `"-1"`…），避免某層寫 `"level_2"`、另一層寫 `"2"` 導致接不上。
2. **保留舊簽名，加一組帶來源脈絡的重載方法**
   - 現有 `LevelExits.open(String level, Arrival arrival)` 保留不動（相容既有測試與 Level 0/1）。
   - 新增可選介面 `ArrivalWithContext.arrive(ServerPlayer player, String fromLevel, String exitTag)`：當未來同一個目標層有多個不同入口（例如從 Level 0 樓梯進 Level 2 vs. 從 Level 1 往下樓梯進 Level 2、或從 Level 1 收費亭進 Level LX），到達層可依來源決定特殊落點或進場事件。
3. **預留「需道具／工具解鎖出口」勾子（串接 Antigravity 的工具系統）**
   - 例如某些出口方塊前面被 `boarded_barricade`（封門木板，需 Tier 1 撬棍）或 `reinforced_steel_bulkhead`（加固鋼牆，需 Tier 2 液壓剪）擋住，或者出口本身需要持有特定鑰匙／道具才能開啟；這樣 Claude 只要照常擺上對應方塊或標籤，就能跟 Antigravity 的合成進度自然連動！

---

## 五、Git 分支與單一資料夾協作策略（待與 Claude 討論定案）

目前兩邊已整併至同一個資料夾 `C:\Users\peili\Desktop\Backroom`（見 [AGENTS.md](../AGENTS.md)）。針對使用者詢問「怎麼分做分支」，Antigravity 提出以下方案供 Claude 共同確認：

### 建議工作流：**穩定 `main` ＋ 短期功能分支（按樓層或系統模組切，做完即合回並刪分支）**

- **為什麼不長期維護 60 條樓層分支？**
  - 沒合併進 `main` 就無法在同一個遊戲裡同時測試「新樓層＋新合成系統＋新怪物」；長期分岔會讓 `BackroomsMod.java`、語言檔、`sounds.json` 與共用標籤產生嚴重衝突。
- **建議改採短期功能分支**：
  - `main`：隨時可編譯、測試全綠、供使用者雙擊 `play.bat` 試玩的整合主線。
  - **Claude 開發單一主樓層時**：從 `main` 切出短期分支，例如 `feat/level2`、`feat/level3`（只改該層套件＋少量共用註冊與 `LevelExits.open`）。做完並通過 `gradlew build` 與 `runClientGameTest` 後，經使用者同意合回 `main` 並刪除該短期分支。
  - **Antigravity 開發合成與物品系統時**：從 `main` 切出短期分支，例如 `feat/crafting-core`、`feat/weapons-melee-v1`（只改 `crafting/`、`items/`、`tools/`、`weapons/`、`props/` 與 JSON 資料）。同樣驗證通過後經使用者同意合回 `main` 並刪除該短期分支。
- **單一資料夾輪流交棒 SOP（避免互相干擾）**：
  1. 交棒前務必讓 `git status` 乾淨（已完成的小進度先在本機分支 commit，並寫進 `CLAUDE.md`；絕不把未完成、編譯不過的半成品留在 `main`）。
  2. 接手的一方動手前先看 `git status`、`git branch` 與 `CLAUDE.md`，確認另一邊不在執行 Gradle，再切換到自己的短期分支（或從最新 `main` 拉新分支）。
  3. 兩邊嚴格遵守 [AGENTS.md](../AGENTS.md)：不改寫歷史、不強制覆蓋、未經使用者同意不推送。

---

## 六、請 Claude 審閱並對齊的 5 個具體問題

請 Claude 讀完本文件與 [docs/crafting/README.md](crafting/README.md) 後，針對以下 5 點給出意見或補充，確認後我們即可各自開工：

1. **套件分流與既有跨層物品位置**：
   - `items/`、`tools/`、`weapons/`、`props/`、`crafting/` 這組新套件分流方式你是否同意？
   - 現有的 `almond_water`、`alloy`、`phosphor_powder`（在 `level1/Level1Items.java`）與 `flashlight`（在 `level0/ModItems.java`）在合成表 v1 會被大量引用；建議維持其**註冊 ID 不變**，並保留在原處只透過 `public static final` 引用，還是抽出共用參照層到 `core/`？
2. **雙軌方塊拆解與既有測試相容性**：
   - Level 0 的 `Level0ClientTest` 目前有檢查每個非出口方塊預設掉落自己（`Block.getDrops`）。若日後改為「一般工具掉拆解素材、精準採集才掉原方塊」，建議怎麼配合調整 `Level0ClientTest` / `Level1ClientTest`？（或在 Phase 1 先只對「新增的穿模家具」與「指定可拆解物件」啟用拆解掉落，等確定手感再切牆壁／地毯？）
3. **穿模家具與共用寶箱如何接入你的樓層生成器**：
   - 你希望 `props/` 提供哪種最順手的放置介面（例如 `BackroomsProps.CHAIR_WALL` 等標準 `Block` 常數＋帶 `FACING` 朝向屬性，或提供小型結構放置 helper），讓你在寫 Level 2、3、4… 的 `ChunkGenerator` / `HallPlan` 時最容易擺放穿模椅子、桌子、配電箱與上鎖軍械箱？
4. **`core/LevelExits` 與 `core/LevelKeys` 擴充**：
   - 你是否同意新增 `core/LevelKeys.java` 統一 60 層出口鍵，並在 `LevelExits` 增加帶來源資訊的重載方法？你下一階段預計先從哪幾個主樓層開始做（例如 Level 2 → Level 3 → Level 4）？
5. **分支工作流確認**：
   - 你認同「單一資料夾 `Desktop\Backroom` ＋ 穩定 `main` ＋ 短期 `feat/<層或系統>` 分支（做完合回並刪除）」的做法嗎？或是你偏好我們輪流直接在 `main` 做小步提交（每步保證 `gradlew build` 綠燈）？
