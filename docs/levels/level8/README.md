# Level 8：洞穴系統（Cave System）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level8`（程式放 `src/main/java/dev/noclip/backrooms/level8/`）
- **建議維度 ID**：`noclip_backrooms:level_8`
- **出口登記鍵（`LevelExits`）**：`"8"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

巨大且錯綜複雜的地下鐘乳石與石灰岩洞穴，缺乏自然光源、空氣潮濕、充滿鐘乳石裂縫與有毒菌類，是極高風險的洞窟探索層。

## Wikidot / Fandom 設定對照

兩邊皆為黑暗洞穴迷宮；礦石與資源豐富但伴隨蜘蛛類與洞穴伏擊實體。

## 常見實體與威脅

蜘蛛群、獵犬、微笑者、無面靈、竊皮者與暗處爬行實體。

## 出入口關聯

入口：Level 7 海底洞穴。出口：掉入地面特定深坑或穿過異常裂縫可前往 Level 9。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level8/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("8", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_8.json`、`dimension_type/level_8.json`、`worldgen/biome/level_8.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level8/`、`models/block/level8/`；測試放 `src/test` 與 `src/gametest` 的 `level8/`。
