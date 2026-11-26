# Level 154：雷射遊戲館（Laser Tag）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level154`（程式放 `src/main/java/dev/noclip/backrooms/level154/`）
- **建議維度 ID**：`noclip_backrooms:level_154`
- **出口登記鍵（`LevelExits`）**：`"154"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

漆黑中閃爍霓虹螢光塗鴉的無盡室內雷射槍戰迷宮，地板與障礙牆塗滿發光幾何線條，適合做螢光迷宮探索與特殊道具層。

## Wikidot / Fandom 設定對照

兩邊核心皆為霓虹黑光燈雷射迷宮，視覺風格與前幾層形成強烈對比。

## 常見實體與威脅

少量敵對實體（派對客／笑魘常出沒於未點亮角落）。

## 出入口關聯

入口：由街機或購物中心樓層的霓虹門進入。出口：找到出口標誌門或完成場館區域。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level154/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("154", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_154.json`、`dimension_type/level_154.json`、`worldgen/biome/level_154.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level154/`、`models/block/level154/`；測試放 `src/test` 與 `src/gametest` 的 `level154/`。
