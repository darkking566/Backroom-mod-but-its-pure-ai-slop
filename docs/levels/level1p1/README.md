# Level 1.1：腐敗的走廊（Corrupted Corridor）

- **分類**：子樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 孤零零門框已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level1p1`（程式放 `src/main/java/dev/noclip/backrooms/level1p1/`）
- **建議維度 ID**：`noclip_backrooms:level_1.1`
- **出口登記鍵（`LevelExits`）**：`"1.1"`
- **參考來源**：Wikidot / 狐霖後室

## 環境與核心特色

狹窄筆直、牆面為白色煤渣磚、地板鋪著藍色磁磚的無限長廊。前段物資豐富且安靜，但越往深處走環境越腐朽扭曲、燈光閃爍並伴隨強烈壓迫感。

## Wikidot / Fandom 設定對照

Wikidot 定義為 Level 1 的亞層（窄長藍磚走廊，深度與風險正相關）；本模組已由 Level 1 來回穿過 4 次的孤立門框預留出口 `"1.1"`。

## 常見實體與威脅

深處會出現笑魘、獵犬與異常腐化現象。

## 出入口關聯

入口：Level 1 停車區／倉庫區孤立門框來回穿過 4 次（已預留出口 `"1.1"`）。出口：特定轉角門回 Level 1 或通往 Level 2。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level1p1/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("1.1", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_1.1.json`、`dimension_type/level_1.1.json`、`worldgen/biome/level_1.1.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level1p1/`、`models/block/level1p1/`；測試放 `src/test` 與 `src/gametest` 的 `level1p1/`。
