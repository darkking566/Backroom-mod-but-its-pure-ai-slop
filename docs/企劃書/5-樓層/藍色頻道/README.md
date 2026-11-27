# The Blue Channel：藍色頻道

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.the_blue_channel`（程式放 `src/main/java/dev/noclip/backrooms/the_blue_channel/`）
- **建議維度 ID**：`noclip_backrooms:the_blue_channel`
- **出口登記鍵（`LevelExits`）**：`"blue_channel"`
- **參考來源**：Wikidot 經典

## 環境與核心特色

一片無窮無盡、發出微弱藍光的類比電視訊號虛空空間，漂浮著失真的電視畫面與建築碎片，是連結後室各層的深層異常介質。

## Wikidot / Fandom 設定對照

Wikidot 著名的超現實隱秘層，深藍色微光與雪花雜訊氛圍非常獨特。

## 常見實體與威脅

無傳統實體，但空間訊號不穩定極易將人撕裂或隨機傳送。

## 出入口關聯

入口：透過故障電視機或極深層卡出進入。出口：走進漂浮的畫面碎片切入對應樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/the_blue_channel/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("blue_channel", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/the_blue_channel.json`、`dimension_type/the_blue_channel.json`、`worldgen/biome/the_blue_channel.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/the_blue_channel/`、`models/block/the_blue_channel/`；測試放 `src/test` 與 `src/gametest` 的 `the_blue_channel/`。
