# Level 1.01：Level 1 地下室（The Basement of Level 1）

- **分類**：子樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 地窖門已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level1p01`（程式放 `src/main/java/dev/noclip/backrooms/level1p01/`）
- **建議維度 ID**：`noclip_backrooms:level_1.01`
- **出口登記鍵（`LevelExits`）**：`"1.01"`
- **參考來源**：Fandom / 狐霖後室

## 環境與核心特色

位於 Level 1 下方的無盡積灰地下室與地堡走廊，充滿報廢鍋爐房、老舊儲藏架與潮濕通風管道，通風口會飄散有毒黴菌孢子。

## Wikidot / Fandom 設定對照

主要出自 Fandom 亞層設定（Wikidot 較少採用此編號）；在本模組中已由 Level 1 與地面齊平的地窖門預留出口 `"1.01"`。

## 常見實體與威脅

毒黴菌孢子環境危害，以及躲藏在儲藏室暗處的少量爬行實體。

## 出入口關聯

入口：Level 1 停車區／倉庫區與地面齊平的地窖門（已預留出口 `"1.01"`）。出口：往上爬梯回到 Level 1，或深入鍋爐區通往 Level 2。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level1p01/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("1.01", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_1.01.json`、`dimension_type/level_1.01.json`、`worldgen/biome/level_1.01.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level1p01/`、`models/block/level1p01/`；測試放 `src/test` 與 `src/gametest` 的 `level1p01/`。
