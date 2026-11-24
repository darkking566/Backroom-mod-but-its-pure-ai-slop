# Level 3999：真實結局（The True Ending）

- **分類**：高編號／特色樓層（Fandom）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level3999`（程式放 `src/main/java/dev/noclip/backrooms/level3999/`）
- **建議維度 ID**：`noclip_backrooms:level_3999`
- **出口登記鍵（`LevelExits`）**：`"3999"`
- **參考來源**：Fandom 經典

## 環境與核心特色

Fandom 最具人氣的高編號終局層之一：霓虹燈光閃耀的巨型玻璃街機大廳，進入時會收到一張寫滿個人待辦事項的清單，完成清單後推開玻璃大門即可真正回到現實世界！

## Wikidot / Fandom 設定對照

屬於 Fandom 超過 999 編號體系的代表作（Wikidot 不採四位數主編號）；非常適合作為本模組的「真結局通關目標層」。

## 常見實體與威脅

無敵對實體（包含各類友善／中立 NPC 與任務機制）。

## 出入口關聯

入口：多個高難度樓層的稀有紫色或霓虹門。出口：完成任務清單後走過玻璃大門返回主世界。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level3999/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("3999", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_3999.json`、`dimension_type/level_3999.json`、`worldgen/biome/level_3999.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level3999/`、`models/block/level3999/`；測試放 `src/test` 與 `src/gametest` 的 `level3999/`。
