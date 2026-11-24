# Level 4.3：無盡辦公室／腐朽空屋

- **分類**：子樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 純白門已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level4p3`（程式放 `src/main/java/dev/noclip/backrooms/level4p3/`）
- **建議維度 ID**：`noclip_backrooms:level_4.3`
- **出口登記鍵（`LevelExits`）**：`"4.3"`
- **參考來源**：Fandom / Wikidot / 狐霖後室

## 環境與核心特色

融合或二選一的設計方向：Fandom 為佈滿辦公隔間、電腦與冰箱並會誘發工作強迫症的「無盡辦公室」；Wikidot 保留版為陰暗多塵的「腐朽空屋」工業地下室。

## Wikidot / Fandom 設定對照

Fandom（The Cubicles：無限辦公隔間＋精神誘導）與 Wikidot（Decayed：黑暗乾燥發霉地下室＋失落篷蝠）差異大，實作前需擇一主軸。Level 1 機房純白門已預留出口 `"4.3"`。

## 常見實體與威脅

Fandom：幾乎無實體但具精神洗腦效應；Wikidot：失落篷蝠（The Lost Cloak）等漂浮威脅。

## 出入口關聯

入口：Level 1 機房純白的門（已預留出口 `"4.3"`）、Level 4 異常門。出口：打破隔間或找到出口門回 Level 4。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level4p3/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("4.3", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_4.3.json`、`dimension_type/level_4.3.json`、`worldgen/biome/level_4.3.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level4p3/`、`models/block/level4p3/`；測試放 `src/test` 與 `src/gametest` 的 `level4p3/`。
