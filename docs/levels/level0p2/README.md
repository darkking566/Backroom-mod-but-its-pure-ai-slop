# Level 0.2：翻修爛攤子（Remodeled Mess）

- **分類**：代表性子樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level0p2`（程式放 `src/main/java/dev/noclip/backrooms/level0p2/`）
- **建議維度 ID**：`noclip_backrooms:level_0.2`
- **出口登記鍵（`LevelExits`）**：`"0.2"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

看似被神祕組織「後室翻修公司（BRC）」重新裝潢過的精緻版 Level 0：乾燥紅地毯、雪白牆面與整齊家具，但隨著探索會引發連鎖崩塌，露出底下殘破骨架。

## Wikidot / Fandom 設定對照

Wikidot 極具創意的 Level 0 亞層，很適合直接複用並改裝 Level 0 的迷宮框架＋動態坍塌事件。

## 常見實體與威脅

幾乎無實體，主要風險為建築結構連鎖坍塌。

## 出入口關聯

入口：Level 0 偶發出現的翻新白牆或施工門。出口：在崩塌中逃進出口門返回 Level 0。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level0p2/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("0.2", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_0.2.json`、`dimension_type/level_0.2.json`、`worldgen/biome/level_0.2.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level0p2/`、`models/block/level0p2/`；測試放 `src/test` 與 `src/gametest` 的 `level0p2/`。
