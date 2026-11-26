# Level 94：動畫（Motion）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level94`（程式放 `src/main/java/dev/noclip/backrooms/level94/`）
- **建議維度 ID**：`noclip_backrooms:level_94`
- **出口登記鍵（`LevelExits`）**：`"94"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

外表像一幅純白微粒感的 1950 年代懷舊彩繪山丘小鎮，天空浮著一座透明浮空城堡；白天安全，但入夜後會有詭異的定格動畫人偶怪物四處獵殺！

## Wikidot / Fandom 設定對照

後室公認神作之一，兩邊核心完全一致：日夜交替極端反差（白天怪異寧靜、夜晚高危獵殺）＋空中城堡終局挑戰。

## 常見實體與威脅

動畫實體（Animations，夜晚大舉出沒）、浮空城堡內的「動畫師」與傀儡。

## 出入口關聯

入口：從其他異常小鎮或建築卡入。出口：登上浮空城堡通過試煉即可離開。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level94/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("94", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_94.json`、`dimension_type/level_94.json`、`worldgen/biome/level_94.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level94/`、`models/block/level94/`；測試放 `src/test` 與 `src/gametest` 的 `level94/`。
