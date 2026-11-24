# Level Fun：享樂層（=)）

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_fun`（程式放 `src/main/java/dev/noclip/backrooms/level_fun/`）
- **建議維度 ID**：`noclip_backrooms:level_fun`
- **出口登記鍵（`LevelExits`）**：`"fun"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

充滿彩色氣球、生日橫幅、長餐桌與詭異歡樂音樂的派對大廳，牆上與文件常寫著誘騙流浪者前來的 `=)` 訊息，實際上是派對客設下的致命狩獵場。

## Wikidot / Fandom 設定對照

兩邊皆極具知名度；Wikidot 著重文件被竄改的詭異感，Fandom 更強化派對客與享樂戰爭劇情。

## 常見實體與威脅

派對客（Partygoers，核心敵對實體，接觸具高度致命與同化威脅）。

## 出入口關聯

入口：在 Level 1 天花板卡出或走進寫著 `=)` 的彩色派對門。出口：極難逃脫，需趁隙找到未上鎖的灰白後門逃出。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_fun/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("fun", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_fun.json`、`dimension_type/level_fun.json`、`worldgen/biome/level_fun.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_fun/`、`models/block/level_fun/`；測試放 `src/test` 與 `src/gametest` 的 `level_fun/`。
