# Level 9223372036854775807：64 位元上限真結局（True Final Level）

- **分類**：高編號／特色樓層（Fandom）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level9223372036854775807`（程式放 `src/main/java/dev/noclip/backrooms/level9223372036854775807/`）
- **建議維度 ID**：`noclip_backrooms:level_9223372036854775807`
- **出口登記鍵（`LevelExits`）**：`"9223372036854775807"`
- **參考來源**：Fandom 經典

## 環境與核心特色

Fandom 標誌性傳說樓層（64 位元有號整數最大值 `2^63 - 1`）：一片純白無盡、佈滿黑白錯視螺旋樓梯的抽象空間，據說爬到第 5,000 階縱身躍下即可逃離後室（或墜入虛空）。

## Wikidot / Fandom 設定對照

Fandom 獨有代表作（Wikidot 不收錄超大整數樓層）；極具網路迷因與視覺衝擊力。

## 常見實體與威脅

無已知實體，純空間與心理極限考驗。

## 出入口關聯

入口：極端罕見的卡出故障或特定終局線索。出口：依特定階梯高度躍下返回主世界或掉入 The Void。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level9223372036854775807/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("9223372036854775807", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_9223372036854775807.json`、`dimension_type/level_9223372036854775807.json`、`worldgen/biome/level_9223372036854775807.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level9223372036854775807/`、`models/block/level9223372036854775807/`；測試放 `src/test` 與 `src/gametest` 的 `level9223372036854775807/`。
