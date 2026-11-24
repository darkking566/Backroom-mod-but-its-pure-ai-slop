# Level 5：恐怖旅館（Terror Hotel）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level5`（程式放 `src/main/java/dev/noclip/backrooms/level5/`）
- **建議維度 ID**：`noclip_backrooms:level_5`
- **出口登記鍵（`LevelExits`）**：`"5"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

1920–1930 年代復古華麗卻令人不安的無盡飯店，分為大廳、貝弗莉室（Beverly Room）與極高溫的鍋爐房三區，迴盪著遙遠的留聲機爵士樂。

## Wikidot / Fandom 設定對照

兩邊皆以復古豪華飯店與鍋爐房為核心；Wikidot 設有神祕主宰「Level 5 實體（野獸）」與強烈精神干擾。

## 常見實體與威脅

死亡飛蛾、獵犬、竊皮者、無面靈與鍋爐房內的高威脅實體。

## 出入口關聯

入口：Level 3 電梯、Level 4 樓梯。出口：深入鍋爐房通往 Level 6；特定門通往 Level 3、Level 4 或其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level5/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("5", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_5.json`、`dimension_type/level_5.json`、`worldgen/biome/level_5.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level5/`、`models/block/level5/`；測試放 `src/test` 與 `src/gametest` 的 `level5/`。
