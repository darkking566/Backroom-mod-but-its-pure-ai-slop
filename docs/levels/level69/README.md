# Level 69：苦難公路之旅（The Road Trip of Affliction）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level69`（程式放 `src/main/java/dev/noclip/backrooms/level69/`）
- **建議維度 ID**：`noclip_backrooms:level_69`
- **出口登記鍵（`LevelExits`）**：`"69"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

Wikidot 為濃霧與漆黑籠罩的無盡廢棄公路，規則嚴苛——待在車內相對安全，下車步入濃霧將面臨致命伏擊；Fandom 則為荒漠中的無盡汽車旅館。

## Wikidot / Fandom 設定對照

Wikidot 的「濃霧公路＋載具求生」極具特色與玩法辨識度；Fandom 為沙漠汽車旅館。

## 常見實體與威脅

迷霧中的無形與高速伏擊實體（離開安全區即觸發）。

## 出入口關聯

入口：Level 11 或停車場的特定車輛進入。出口：駕車行駛極長距離穿越迷霧邊界。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level69/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("69", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_69.json`、`dimension_type/level_69.json`、`worldgen/biome/level_69.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level69/`、`models/block/level69/`；測試放 `src/test` 與 `src/gametest` 的 `level69/`。
