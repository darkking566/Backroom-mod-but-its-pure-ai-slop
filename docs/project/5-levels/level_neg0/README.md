# Level -0：零之反面（The Negative Lobby）

- **分類**：負樓層（Fandom／經典衍生）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_neg0`（程式放 `src/main/java/dev/noclip/backrooms/level_neg0/`）
- **建議維度 ID**：`noclip_backrooms:level_neg0`
- **出口登記鍵（`LevelExits`）**：`"-0"`
- **參考來源**：Fandom / 衍生社群

## 環境與核心特色

Level 0 的鏡像崩壞反面：褪色成灰白或單色高對比的無盡迷宮、重力偶發異常、音效倒放且空間極不穩定。

## Wikidot / Fandom 設定對照

主要流行於 Fandom 與各類後室遊戲（Wikidot 後來大多整併或刪除純負數編號）。

## 常見實體與威脅

異常影體與強烈空間失真。

## 出入口關聯

入口：在 Level 0 發生罕見逆向卡牆。出口：尋找恢復彩色的牆面卡回 Level 0 或墜入 Level -1。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_neg0/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("-0", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_neg0.json`、`dimension_type/level_neg0.json`、`worldgen/biome/level_neg0.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_neg0/`、`models/block/level_neg0/`；測試放 `src/test` 與 `src/gametest` 的 `level_neg0/`。
