# The Promised Land：應許之地

- **分類**：隱秘樓層（Enigmatic／Fandom）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.the_promised_land`（程式放 `src/main/java/dev/noclip/backrooms/the_promised_land/`）
- **建議維度 ID**：`noclip_backrooms:the_promised_land`
- **出口登記鍵（`LevelExits`）**：`"promised_land"`
- **參考來源**：Fandom 經典

## 環境與核心特色

傳說中極罕見的安全天堂：粉紅色調的 400 層度假飯店，窗外飄著雲朵般的粉紅樹林，播放著舒緩音樂、物資無盡且完全沒有敵對實體。

## Wikidot / Fandom 設定對照

主要源自 Fandom 社群與熱門後室遊戲的夢幻庇護所設定。

## 常見實體與威脅

無敵對實體。

## 出入口關聯

入口：極少數高階安全線索或特定終局事件。出口：可作為長期安頓基地或通往主世界。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/the_promised_land/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("promised_land", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/the_promised_land.json`、`dimension_type/the_promised_land.json`、`worldgen/biome/the_promised_land.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/the_promised_land/`、`models/block/the_promised_land/`；測試放 `src/test` 與 `src/gametest` 的 `the_promised_land/`。
