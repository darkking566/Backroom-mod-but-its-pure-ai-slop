# Level 6.1：零食間（The Snackrooms）

- **分類**：代表性子樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level6p1`（程式放 `src/main/java/dev/noclip/backrooms/level6p1/`）
- **建議維度 ID**：`noclip_backrooms:level_6.1`
- **出口登記鍵（`LevelExits`）**：`"6.1"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

與地獄般漆黑的 Level 6 形成極端反差的避風港：燈光明亮的 1980 年代美食街、餐廳與自動販賣機迷宮，貨架每週自動補滿各式零食、食物與杏仁水。

## Wikidot / Fandom 設定對照

兩邊一致公認最安全、最受玩家喜愛的補給與交易天堂（M.E.G. 與多數友善聚落聚集地）。

## 常見實體與威脅

無敵對實體，偶見友善的無面靈店員。

## 出入口關聯

入口：在 Level 6 漆黑中意外被電線絆倒進入。出口：各餐廳門可安全前往 Level 4、Level 11 等安全層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level6p1/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("6.1", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_6.1.json`、`dimension_type/level_6.1.json`、`worldgen/biome/level_6.1.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level6p1/`、`models/block/level6p1/`；測試放 `src/test` 與 `src/gametest` 的 `level6p1/`。
