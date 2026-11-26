# Level 389：遊戲大廳（The Gaming Hall）

- **分類**：主樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 十字鎬門已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level389`（程式放 `src/main/java/dev/noclip/backrooms/level389/`）
- **建議維度 ID**：`noclip_backrooms:level_389`
- **出口登記鍵（`LevelExits`）**：`"389"`
- **參考來源**：Wikidot / Fandom / 狐霖後室

## 環境與核心特色

由無數風格突變的房間與走廊組成，全數匯聚向擺滿街機、桌遊與致命試煉關卡的中央大廳，整層受神祕實體「遊戲大師（Game Master）」掌控。

## Wikidot / Fandom 設定對照

Wikidot 與 Fandom 核心一致，皆以遊戲大師主導的高風險心理／益智逃脫為賣點。Level 1 機房畫著十字鎬的門已預留出口 `"389"`。

## 常見實體與威脅

遊戲大師（Entity 99 / Game Master）及其棋子／機關守衛。

## 出入口關聯

入口：Level 1 機房畫著十字鎬的門（已預留出口 `"389"`）。出口：贏得遊戲大師的對局後由其送往安全層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level389/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("389", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_389.json`、`dimension_type/level_389.json`、`worldgen/biome/level_389.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level389/`、`models/block/level389/`；測試放 `src/test` 與 `src/gametest` 的 `level389/`。
