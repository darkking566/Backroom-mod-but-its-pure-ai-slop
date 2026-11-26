# Level 188：百窗庭院（The Courtyard of Windows）

- **分類**：主樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 插座牆已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level188`（程式放 `src/main/java/dev/noclip/backrooms/level188/`）
- **建議維度 ID**：`noclip_backrooms:level_188`
- **出口登記鍵（`LevelExits`）**：`"188"`
- **參考來源**：Fandom / 經典後室 / 狐霖後室

## 環境與核心特色

後室最具代表性的地標影像之一：四面圍繞著數百扇方形白色窗戶的封閉方形飯店中庭，多數窗戶亮著詭異燈光並暗藏陷阱，少數房間可進入探索。

## Wikidot / Fandom 設定對照

經典百窗庭院（原型為倫敦希斯洛假日酒店中庭）在 Fandom 與全球後室社群知名度極高；Wikidot 曾因版權／重寫歷經變動。Level 1 機房插滿插座的牆已預留出口 `"188"`。

## 常見實體與威脅

窗戶實體（Windows）、偶爾在室內出現的無面靈與笑魘。

## 出入口關聯

入口：Level 1 機房插滿插座的牆（已預留出口 `"188"`）。出口：辨識安全窗戶或室內門可通往其他飯店／室內樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level188/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("188", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_188.json`、`dimension_type/level_188.json`、`worldgen/biome/level_188.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level188/`、`models/block/level188/`；測試放 `src/test` 與 `src/gametest` 的 `level188/`。
