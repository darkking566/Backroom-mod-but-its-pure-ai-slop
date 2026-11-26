# Level 4：廢棄辦公室（Abandoned Office）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level4`（程式放 `src/main/java/dev/noclip/backrooms/level4/`）
- **建議維度 ID**：`noclip_backrooms:level_4`
- **出口登記鍵（`LevelExits`）**：`"4"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

幾乎全空的現代化辦公大樓，灰色地毯、玻璃隔間、飲水機與自動販賣機遍佈，窗外常為永恆細雨，是極受歡迎的安全補給與據點層。

## Wikidot / Fandom 設定對照

兩邊皆為安全度極高的杏仁水主要產區與人類據點集中地；需注意不可接近外觀異常的窗戶（窗戶實體）。

## 常見實體與威脅

極少（偶見窗戶實體、極稀少無面靈與獵犬）。

## 出入口關聯

入口：Level 2／Level 3 電梯或門。出口：通往 Level 5、Level 6，或由特定門／異常處進入 Level 4.3。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level4/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("4", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_4.json`、`dimension_type/level_4.json`、`worldgen/biome/level_4.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level4/`、`models/block/level4/`；測試放 `src/test` 與 `src/gametest` 的 `level4/`。
