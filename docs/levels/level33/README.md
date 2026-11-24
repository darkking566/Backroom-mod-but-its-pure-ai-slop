# Level 33：無盡購物中心（The Infinite Mall）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level33`（程式放 `src/main/java/dev/noclip/backrooms/level33/`）
- **建議維度 ID**：`noclip_backrooms:level_33`
- **出口登記鍵（`LevelExits`）**：`"33"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

空曠無人的 1980 年代霓虹購物中心。初入時乾淨平靜，但深入長距離後會逐步進入「腐化狀態」，出現積水、植物蔓生、燈光壞損與嚴重幻覺。

## Wikidot / Fandom 設定對照

兩邊高度一致，非常適合用距離函數做出「越走越腐化」的動態地形漸變機制！

## 常見實體與威脅

前段極少；深入腐化區後出現多類敵對實體與強烈精神污染。

## 出入口關聯

入口：從城市或商業樓層的商場自動門進入。出口：在尚未完全腐化前折返，或尋找特定安全門。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level33/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("33", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_33.json`、`dimension_type/level_33.json`、`worldgen/biome/level_33.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level33/`、`models/block/level33/`；測試放 `src/test` 與 `src/gametest` 的 `level33/`。
