# Level -2：溢出管廊（Overflow）

- **分類**：負樓層（Fandom／經典衍生）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_neg2`（程式放 `src/main/java/dev/noclip/backrooms/level_neg2/`）
- **建議維度 ID**：`noclip_backrooms:level_neg2`
- **出口登記鍵（`LevelExits`）**：`"-2"`
- **參考來源**：Fandom / 經典後室

## 環境與核心特色

水淹沒腳踝至胸口的扭曲走廊與漏水管道系統，牆面常呈現異常錯位或由多個低樓層建築碎片拼貼而成，水體偶爾沸騰或極寒。

## Wikidot / Fandom 設定對照

Fandom 經典負樓層之一（可視為 Level 2 的水淹鏡像變體）。

## 常見實體與威脅

水中與管線暗處潛伏的敵對實體。

## 出入口關聯

入口：Level -1 特定潮濕門。出口：穿過水下裂口通往更深負樓層或水系主樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_neg2/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("-2", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_neg2.json`、`dimension_type/level_neg2.json`、`worldgen/biome/level_neg2.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_neg2/`、`models/block/level_neg2/`；測試放 `src/test` 與 `src/gametest` 的 `level_neg2/`。
