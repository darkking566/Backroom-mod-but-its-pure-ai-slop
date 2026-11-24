# Level 2：管道噩夢（Pipe Dreams）

- **分類**：主樓層（核心）
- **目前狀態**：未開放（Level 1 往下樓梯已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level2`（程式放 `src/main/java/dev/noclip/backrooms/level2/`）
- **建議維度 ID**：`noclip_backrooms:level_2`
- **出口登記鍵（`LevelExits`）**：`"2"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

狹長、悶熱且壓抑的混凝土維修通道，牆面與天花板佈滿滾燙蒸氣管線、壓力錶與生鏽鐵門，部分區段極端高溫並伴隨管線異響。

## Wikidot / Fandom 設定對照

兩邊高度一致，皆為長走廊管線維修區；Wikidot 更強調高溫區段與「辦公區／電梯」分支，Fandom 亦保留經典窄管線長廊。

## 常見實體與威脅

微笑者、獵犬、肢團、竊皮者、幼年無面靈、鈍人等，敵對密度明顯高於 Level 1。

## 出入口關聯

入口：Level 1 往下的黑暗樓梯（已預留出口 `"2"`）。出口：未上鎖的防火門通往 Level 3、電梯或門通往 Level 4，偶爾回到 Level 1。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level2/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("2", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_2.json`、`dimension_type/level_2.json`、`worldgen/biome/level_2.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level2/`、`models/block/level2/`；測試放 `src/test` 與 `src/gametest` 的 `level2/`。
