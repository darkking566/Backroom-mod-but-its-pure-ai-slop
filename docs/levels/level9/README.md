# Level 9：黑暗郊區（Darkened Suburbs）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level9`（程式放 `src/main/java/dev/noclip/backrooms/level9/`）
- **建議維度 ID**：`noclip_backrooms:level_9`
- **出口登記鍵（`LevelExits`）**：`"9"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

永恆深夜的現代美式郊區住宅街道，偶有閃爍路燈與空無一人的獨棟木屋，街道與後院藏著大量高速追擊的危險實體。

## Wikidot / Fandom 設定對照

兩邊皆為永夜危險郊區，標誌著從室內迷宮進入「大型戶外異常樓層」的轉折點。

## 常見實體與威脅

鄰里守望者（Neighborhood Watch）、獵犬、死亡飛蛾、笑魘、竊皮者、觀察者等。

## 出入口關聯

入口：Level 8 洞穴地板裂縫。出口：沿著標示道路一路前行百英里可抵達 Level 11，或走進泥土田間小路進入 Level 10。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level9/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("9", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_9.json`、`dimension_type/level_9.json`、`worldgen/biome/level_9.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level9/`、`models/block/level9/`；測試放 `src/test` 與 `src/gametest` 的 `level9/`。
