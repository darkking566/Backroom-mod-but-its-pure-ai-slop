# Noclip: Backrooms

Fabric 模組，Minecraft 26.2、Java 25，模組代號 `noclip_backrooms`。在主世界卡進牆裡就會掉進後室，一層一層往下走。

- 開遊戲試玩：雙擊 `play.bat`
- 編譯並跑單元測試：`.\gradlew.bat build`
- 真實遊戲自動測試：`.\gradlew.bat runClientGameTest`；只跑某幾層加 `-Plevels=1,3`（Level 0.1 寫 `0.1`，畫面風格測試寫 `look`），Level 1 只跑某幾段再加 `-Psections=people,secrets`
- 進度與試玩方法：[tasks/todo.md](tasks/todo.md)；協作規則：[CLAUDE.md](CLAUDE.md)；素材授權：[CREDITS.md](CREDITS.md)

## 資料夾怎麼分：一層一個資料夾，跨樓層的放 core

```
src/main/java/dev/noclip/backrooms/
├─ BackroomsMod.java        模組入口：依序呼叫每一層的 register()；也放共用的 MOD_ID、id()
├─ core/                    好幾層都會用到的機制
│  └─ LevelExits.java       出口登記處：每個出口寫著通往哪一層，這裡決定通不通
│     （客戶端的 src/client/.../core/look/ 是畫面風格：寫實材質包與攝影機濾鏡，見 docs/look/README.md）
├─ core/floors/             第幾層（狐霖式層數）、換 Level 時的標題、走出口落在全新的一片；見 docs/project/1-gameplay/README.md
├─ level0/                  Level 0「大廳」（入口檔 Level0.java）
├─ level1/                  Level 1「停車場」（入口檔 Level1.java）
├─ core/people/             人物（居民、巡邏隊、施工影子）：Person、People 登記八種造型；模型 `monsters/people.json`、貼圖 `textures/entity/people/`、Blender 原檔 `art/people/`，由 `tools/make_people.py` 與 `tools/blender_people.py` 產生
├─ level2/                  Level 2「廢棄維修通道」（入口檔 Level2.java）
├─ level3/                  Level 3「發電站」（入口檔 Level3.java）
└─ monsters/                怪物（可能出現在好幾層）：Monsters.java 登記全部怪物，一隻一個資料夾（hound/）

src/client/java/dev/noclip/backrooms/   只在遊戲畫面端執行的程式（伺服器不會載入）
├─ BackroomsClient.java     畫面端入口：登記怪物的模型和繪製方式
└─ monsters/                BoxRig.java 讀 Blender 匯出的怪物規格檔；hound/ 是獵犬的模型與繪製

src/main/resources/assets/noclip_backrooms/
├─ textures/block/level0/  textures/block/level1/      方塊貼圖（含光影用的 _n、_s）
├─ models/block/level0/    models/block/level1/        方塊模型
├─ textures/item/level0/   textures/item/level1/       物品圖示
├─ models/item/level0/     models/item/level1/         物品模型
├─ sounds/level0/                                      音效檔
├─ monsters/hound.json                                 怪物規格檔（Blender 匯出：關節、長方體、動畫）
├─ textures/entity/                                    怪物貼圖
└─ blockstates/  items/  lang/  sounds.json            ← Minecraft 規定只能放在一起（檔名就是方塊或物品 ID）

src/main/resources/data/noclip_backrooms/
├─ dimension/  dimension_type/  worldgen/biome/        一層一個檔：level_0.json、level_1.json
├─ loot_table/blocks/                                  依方塊 ID，只能放一起
├─ recipe/  tags/block/
└─ ../minecraft/tags/block/mineable/                   挖掘工具標籤，每層的方塊都加在這

src/test/java/dev/noclip/backrooms/level0/  level1/        單元測試（迷宮、地形一定走得通……）
src/gametest/java/dev/noclip/backrooms/level0/  level1/    真實遊戲測試；新增的要登記在 src/gametest/resources/fabric.mod.json

tools/make_textures.py      畫 Level 0 的貼圖，也是共用的繪圖工具
tools/make_level0_realistic.py  寫實材質包（`resourcepacks/realistic/`）的 Level 0 照片材質；見 docs/look/README.md
tools/make_level1.py        畫 Level 1 的貼圖，並寫出 Level 1 的模型、blockstates、物品模型
tools/make_level2.py        畫 Level 2 的貼圖，並寫出 Level 2 的模型、blockstates、物品模型、掉落物表
tools/make_level3.py        Level 3 的同上
tools/make_hound.py         獵犬的發光眼睛貼圖與生怪蛋

art/monsters/hound/         怪物的原始檔（Blender .blend、.glb、預覽圖、交接說明），不會打包進模組

docs/project/               全部企劃書（總目錄 docs/project/README.md）
  1-gameplay/               遊戲玩法（故事、循環、配額、層數、死亡與撤離、前哨）
  2-player/                 玩家能力值（RPG）
  3-crafting/               合成（工作站、材料、武器、全部配方表與網狀圖、物品預覽圖）
  4-monsters/               怪物
  5-levels/                 樓層：316 層的索引；每層一個資料夾（已做好的 0、0.1、1、2、3 有說明、截圖與貼圖預覽）
docs/look/                  畫面規範（貼圖尺寸、光影設定、每層流程、寫實物品）
```

**每一層自己管自己**：方塊、物品、音效、維度、地形、事件都放在那一層的資料夾，那層的入口檔負責全部註冊。所以擴充 Level 0 只會動到 `level0/` 和 Level 0 的資源資料夾，不會碰到別層。

**只能放在一起的檔案**（`blockstates/`、`items/`、`lang/*.json`、`sounds.json`、`loot_table/blocks/`、挖掘工具標籤）：直接加新條目就好，不同層的條目互不影響。

## 跨樓層的東西放哪

一層可以通往很多層，以後還會一直加。規則是**出口歸出發的那層，落點歸到達的那層**：

| 情況 | 放哪 | 例子 |
|---|---|---|
| 某層裡的一個出口（門、樓梯、裂縫……） | 出口所在的那層 | Level 1 往上的樓梯在 `level1/` |
| 到達某層時站在哪 | 被到達的那層，並在入口檔用 `LevelExits.open("層號", ...)` 登記 | 到達 Level 0 的 `Level0Arrival` 在 `level0/` |
| 長在別層裡、專門通往這層的入口 | 被進入的那層（誰被進入，誰負責入口） | Level 0 的閃爍牆通往 Level 1，所以 `FlickeringWalls` 在 `level1/` |
| 好幾層都會用到的機制 | `core/` | `LevelExits` |
| 怪物本身（長相、動作、AI） | `monsters/` | 獵犬在 `monsters/hound/` |
| 某層什麼時候生出哪種怪物 | 那一層 | 停車場停電放出獵犬：`level1/Hounds.java` |

出口只要寫上目的地的層號（例如 `"188"`）就好。那層還沒做的話，出口會顯示「尚未開放」；等那層用 `LevelExits.open` 登記，所有通往它的出口就一起接通，不用回頭改。

git 分支不是用來分樓層的：大家都在同一個資料夾的 `main` 輪流工作（規則見 CLAUDE.md）。`src/main` 是 Gradle 的「主程式」資料夾，跟 git 的 `main` 分支無關。

## 新增一層（以 Level 2 為例）

1. 建立 `level2/Level2.java`：`public static final ResourceKey<Level> LEVEL_2`，以及 `public static void register()`。這一層的其他程式都放在 `level2/`。
2. 在 `data/noclip_backrooms/` 加 `dimension/level_2.json`、`dimension_type/level_2.json`、`worldgen/biome/level_2.json`。
3. 在 `BackroomsMod.onInitialize()` 加一行 `Level2.register();`。
4. 在 `Level2.register()` 裡寫 `LevelExits.open("2", 到達的方法)`，Level 1 往下的樓梯就會自動通到這裡。
5. 貼圖、模型放 `textures/block/level2/`、`models/block/level2/`。
6. 測試放 `src/test/.../level2/`、`src/gametest/.../level2/`，並把遊戲測試登記到 `src/gametest/resources/fabric.mod.json`。

## 子樓層（Level 0.1、1.1、4.3……）

子樓層就是「屬於某一層、但有自己維度」的樓層。做法跟主樓層一樣（見上一節），多的只有三件事，共用的部分放在 `core/`：

- **登記用 `Sublevels.register(...)`**，不要直接用 `LevelExits.open`。它會同時記下「父層是誰」（離開時回哪裡）和「隨機切出（cut-out）」的去處與權重，例如 Level 0.1：`Sublevels.register("0.1", "0", LEVEL_0_1, Map.of("1", 85, "0", 15), ZenithTravel::enter)`。
- **出口方塊用 `core/LevelExitBlock`**：右鍵就走，行為由 `LevelExitBlock.to("層號")`（固定去某層）、`leave("0.1")`（回父層）、`cutOut("0.1")`（隨機切出）決定，不用每個子樓層自己寫方塊類別。
- **落點用 `core/Landing`**：`Landing.tryLand(player, level, 腳的位置)` 會檢查地板是實心、頭上沒東西才傳送。

**入口歸被進入的那層**（跟主樓層一樣）：子樓層自己負責在父層放入口（例如 Level 0.1 在 Level 0 載入區塊時放艙門，見 `level0p1/ZenithEntrance`），父層的程式不用改。

維度 ID 用句點：Level 0.1 是 `noclip_backrooms:level_0.1`（見下面的 ID 規則）。Java 套件和資料夾用 `p`：`level0p1`。

## 新增怪物

1. 在 Blender 照獵犬的標準做（純長方體骨骼、3×3 貼圖分區），匯出成跟 `monsters/hound.json` 一樣格式的規格檔，放到 `assets/noclip_backrooms/monsters/`，貼圖放 `textures/entity/`，原始檔放 `art/monsters/<名字>/`。
2. 在 `Monsters.java` 登記實體與生怪蛋，AI 寫在 `monsters/<名字>/`。
3. 在 `src/client/.../monsters/<名字>/` 寫模型（用 `BoxRig.load("<名字>")` 讀規格檔，動畫直接用規格檔裡的片段）和繪製，並在 `BackroomsClient` 登記。
4. 要牠在哪層出現，就在那層寫生怪規則。

## ID 規則

- 方塊、物品、維度、音效的 ID 一旦用了就**永遠不改**：存檔記的是 ID，改名等於舊存檔裡的東西消失。搬檔案、改資料夾不會影響存檔。
- 子樓層用句點：Level 1.1 是 `level_1.1`，不要寫成 `level_1_1`，會跟 Level 11 撞名。
