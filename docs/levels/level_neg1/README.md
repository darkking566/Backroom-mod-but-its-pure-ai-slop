# Level -1：黑白長廊／迷霧地下室（The Hallway of Doors）

- **分類**：負樓層（Fandom／經典衍生）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_neg1`（程式放 `src/main/java/dev/noclip/backrooms/level_neg1/`）
- **建議維度 ID**：`noclip_backrooms:level_neg1`
- **出口登記鍵（`LevelExits`）**：`"-1"`
- **參考來源**：Fandom / 經典後室

## 環境與核心特色

黑白高對比、被濃霧籠罩的無限長廊與兩側無數扇未知的門（另一常見版本為充滿濃霧的漆黑地下混凝土車庫走廊），環境寂靜卻極易迷失。

## Wikidot / Fandom 設定對照

Fandom 經典負樓層起點，兩側成排大門的設計很適合做成通往其他負樓層的轉運長廊。

## 常見實體與威脅

稀少但具高度不可預測性（霧中伏擊或門後陷阱）。

## 出入口關聯

入口：Level 0／Level 1 異常逆向卡出。出口：推開兩側不同顏色的門通往 Level -2 或其他負樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_neg1/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("-1", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_neg1.json`、`dimension_type/level_neg1.json`、`worldgen/biome/level_neg1.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_neg1/`、`models/block/level_neg1/`；測試放 `src/test` 與 `src/gametest` 的 `level_neg1/`。
