# Level 6：熄燈（Lights Out）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level6`（程式放 `src/main/java/dev/noclip/backrooms/level6/`）
- **建議維度 ID**：`noclip_backrooms:level_6`
- **出口登記鍵（`LevelExits`）**：`"6"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

絕對漆黑的金屬走廊與管線空間，任何外部光源幾乎失效（或極難照亮），伴隨強烈妄想與空間錯亂，極易迷失理智。

## Wikidot / Fandom 設定對照

兩邊核心一致皆為「完全黑暗與精神壓力層」；實作時可考慮壓低手電筒可視距離並強化聽覺導航。

## 常見實體與威脅

無明確常見實體記錄，或僅存難以目擊的暗影威脅（最大威脅是環境與理智）。

## 出入口關聯

入口：Level 5 鍋爐房深處。出口：被電線絆倒有機會掉入 Level 6.1（零食間）；向下探索可通往 Level 7 或 Level 8。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level6/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("6", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_6.json`、`dimension_type/level_6.json`、`worldgen/biome/level_6.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level6/`、`models/block/level6/`；測試放 `src/test` 與 `src/gametest` 的 `level6/`。
