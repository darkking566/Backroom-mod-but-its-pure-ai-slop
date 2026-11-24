# Level 0：教學關卡／大廳（The Lobby）

- **分類**：主樓層（核心）
- **目前狀態**：已實作
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level0`（程式放 `src/main/java/dev/noclip/backrooms/level0/`）
- **建議維度 ID**：`noclip_backrooms:level_0`
- **出口登記鍵（`LevelExits`）**：`"0"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

無限延伸的潮濕黃色壁紙辦公室迷宮、發霉地毯與嗡嗡作響的日光燈。目前模組已實作經典區、柱廳、拱門、昏暗區、深坑、紅室、樓梯間、超大廳、Edge Shift、手電筒與陰影幻覺。

## Wikidot / Fandom 設定對照

兩邊核心設定一致：單一無盡黃室迷宮與低頻日光燈噪音，作為幾乎所有流浪者進入後室的第一站。

## 常見實體與威脅

原設幾乎無實體（孤立效應）；本模組已加入陰影幻覺與紅室困死機制。

## 出入口關聯

入口：主世界卡牆窒息進入、Level 1 往上樓梯。出口：綠色 EXIT 門回重生點；待 4～7 分鐘出現閃爍牆通往 Level 1。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level0/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("0", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_0.json`、`dimension_type/level_0.json`、`worldgen/biome/level_0.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level0/`、`models/block/level0/`；測試放 `src/test` 與 `src/gametest` 的 `level0/`。
