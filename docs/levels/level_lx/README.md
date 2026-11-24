# Level LX：盡力補償（狐霖後室事件層）

- **分類**：特殊樓層（Level 1 收費亭事件預留）
- **目前狀態**：未開放（Level 1 收費亭抽屜事件預留）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_lx`（程式放 `src/main/java/dev/noclip/backrooms/level_lx/`）
- **建議維度 ID**：`noclip_backrooms:level_lx`
- **出口登記鍵（`LevelExits`）**：`"LX"`
- **參考來源**：狐霖的後室RPG / 衍生設定

## 環境與核心特色

《狐霖的後室RPG》中，於 Level 1 收費亭翻抽屜拿取手電筒時觸發傳送的特殊代價樓層「盡力補償」（見 docs/levels/level1/README.md 收費亭筆記）。

## Wikidot / Fandom 設定對照

屬於中文後室 RPG 衍生機制層，不在標準 Wikidot 主表中；已由 Level 1 收費亭抽屜預留銜接點。

## 常見實體與威脅

依實作規劃（可設計成限時逃脫或資源交換試煉房間）。

## 出入口關聯

入口：Level 1 收費亭翻抽屜取得手電筒時觸發傳送。出口：完成事件或找到出口門返回 Level 1 或其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_lx/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("LX", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_lx.json`、`dimension_type/level_lx.json`、`worldgen/biome/level_lx.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_lx/`、`models/block/level_lx/`；測試放 `src/test` 與 `src/gametest` 的 `level_lx/`。
