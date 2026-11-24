# Level 52：學校長廊（School Halls）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level52`（程式放 `src/main/java/dev/noclip/backrooms/level52/`）
- **建議維度 ID**：`noclip_backrooms:level_52`
- **出口登記鍵（`LevelExits`）**：`"52"`
- **參考來源**：Fandom / 經典社群

## 環境與核心特色

無限延伸的空蕩中學走廊、置物櫃與教室，燈光穩定、氣氛略顯寂寥，教室內可找到筆記與少量補給，是後室代表性的校園閾限空間。

## Wikidot / Fandom 設定對照

Fandom 與早期中文社群常對應大衛馬歇爾高校等校園群落；Wikidot 曾做過調整。校園走廊與置物櫃機制很適合在 Minecraft 實現。

## 常見實體與威脅

稀少（偶有友善或中立的無面靈學生與老師）。

## 出入口關聯

入口：Level 4 或 Level 11 的學校側門。出口：走出校園大門可通往郊區或城市樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level52/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("52", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_52.json`、`dimension_type/level_52.json`、`worldgen/biome/level_52.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level52/`、`models/block/level52/`；測試放 `src/test` 與 `src/gametest` 的 `level52/`。
