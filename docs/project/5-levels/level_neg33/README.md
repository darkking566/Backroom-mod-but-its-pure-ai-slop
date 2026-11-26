# Level -33：寂靜飯店／冰冷商廊（Lukewarm Hotel）

- **分類**：負樓層（Fandom／經典衍生）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_neg33`（程式放 `src/main/java/dev/noclip/backrooms/level_neg33/`）
- **建議維度 ID**：`noclip_backrooms:level_neg33`
- **出口登記鍵（`LevelExits`）**：`"-33"`
- **參考來源**：Fandom 經典

## 環境與核心特色

陰冷、潮濕、燈光慘白的無盡荒廢商旅走廊與空泳池複合體，溫度隨深入不斷驟降，最終整個空間被冰霜覆蓋。

## Wikidot / Fandom 設定對照

Fandom 高人氣負樓層，兼具飯店走廊與極寒凍傷生存機制。

## 常見實體與威脅

極稀少，以失溫凍結與孤立環境威脅為主。

## 出入口關聯

入口：負樓層走廊或 Level 33 異常門。出口：在徹底冰封前找到溫暖的紅光門離開。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_neg33/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("-33", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_neg33.json`、`dimension_type/level_neg33.json`、`worldgen/biome/level_neg33.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_neg33/`、`models/block/level_neg33/`；測試放 `src/test` 與 `src/gametest` 的 `level_neg33/`。
