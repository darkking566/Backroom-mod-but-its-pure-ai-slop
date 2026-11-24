# Level 283：遊樂場／街機廳（Playplace / The Arcade）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level283`（程式放 `src/main/java/dev/noclip/backrooms/level283/`）
- **建議維度 ID**：`noclip_backrooms:level_283`
- **出口登記鍵（`LevelExits`）**：`"283"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

由無數彩色塑膠爬行管、球池、滑梯與復古遊樂設施組成的無盡兒童室內樂園／街機複合空間，外觀繽紛卻令人感到詭異窒息。

## Wikidot / Fandom 設定對照

極具代表性的「兒童樂園閾限空間（Playplace）」美學；常與 Level Fun 有危險連結。

## 常見實體與威脅

暗藏於滑梯與球池深處的派對客與爬行實體。

## 出入口關聯

入口：購物中心或披薩店的兒童遊戲區入口。出口：小心辨認非陷阱的彩色出口門。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level283/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("283", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_283.json`、`dimension_type/level_283.json`、`worldgen/biome/level_283.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level283/`、`models/block/level283/`；測試放 `src/test` 與 `src/gametest` 的 `level283/`。
