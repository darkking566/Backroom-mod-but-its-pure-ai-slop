# Level 404：遺忘之境（404 Not Found）

- **分類**：高編號／特色樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level404`（程式放 `src/main/java/dev/noclip/backrooms/level404/`）
- **建議維度 ID**：`noclip_backrooms:level_404`
- **出口登記鍵（`LevelExits`）**：`"404"`
- **參考來源**：Fandom / Wikidot

## 環境與核心特色

現實紋理破碎、空間殘缺並會逐步剝奪流浪者記憶的異常失真領域，部分建築與方塊像未載入完成的錯位片段般懸浮於虛空。

## Wikidot / Fandom 設定對照

兩邊皆圍繞「遺失／錯誤代碼 404／記憶抹除」展開，視覺上非常適合做出錯位方塊與破圖故障美學。

## 常見實體與威脅

異常故障影體與記憶侵蝕效應。

## 出入口關聯

入口：極端卡牆錯誤或破損門。出口：在完全失憶前找到完整的實心結構門。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level404/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("404", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_404.json`、`dimension_type/level_404.json`、`worldgen/biome/level_404.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level404/`、`models/block/level404/`；測試放 `src/test` 與 `src/gametest` 的 `level404/`。
