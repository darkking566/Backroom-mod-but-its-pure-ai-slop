# Level 0：教學關卡／大廳（The Lobby）

- **分類**：主樓層（核心）
- **目前狀態**：已實作
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level0`（程式放 `src/main/java/dev/noclip/backrooms/level0/`）
- **建議維度 ID**：`noclip_backrooms:level_0`
- **出口登記鍵（`LevelExits`）**：`"0"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

無限延伸的潮濕黃色壁紙辦公室迷宮、發霉地毯與嗡嗡作響的日光燈。（2026-11-24 起各區大約一半的燈亮、其餘是壞燈，燈下一圈光、燈之間暗；見 `Maze.lampWorks`）目前模組已實作經典區、柱廳、拱門、昏暗區、深坑、紅室、樓梯間、超大廳、Edge Shift、手電筒與陰影幻覺；特殊大區與一般區域內另設有興趣點（`ZonePoiPlan`：深淵前哨平台、超大廳懸空光井休息區、紅室異常祭壇、昏暗區 CRT 微光訊號，以及稀疏的玻璃隔間辦公室、前人失蹤現場、異常穿模補給角、可互動配電壁龕 `FuseBoxEvents` 與窺視玻璃窗室）。

## Wikidot / Fandom 設定對照

兩邊核心設定一致：單一無盡黃室迷宮與低頻日光燈噪音，作為幾乎所有流浪者進入後室的第一站。

## 常見實體與威脅

原設幾乎無實體（孤立效應）；本模組已加入陰影幻覺與紅室困死機制。

## 出入口關聯

入口：主世界卡牆窒息進入、Level 1 往上樓梯。出口：綠色 EXIT 門回重生點（2026-11-26 起很稀有，大約每 8 區 560×560 格才有一扇，見 `docs/project/1-gameplay/README.md`）；待 1.5～3 分鐘出現閃爍牆通往 Level 1；另有極稀有的伏筆出口門（`hub_door` → `"hub"` 樞紐、`fun_door` → `"fun"` 享樂層，目標樓層開放後自動接通）。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level0/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("0", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_0.json`、`dimension_type/level_0.json`、`worldgen/biome/level_0.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level0/`、`models/block/level0/`；測試放 `src/test` 與 `src/gametest` 的 `level0/`。
