# The Manila Room：馬尼拉房間

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.the_manila_room`（程式放 `src/main/java/dev/noclip/backrooms/the_manila_room/`）
- **建議維度 ID**：`noclip_backrooms:the_manila_room`
- **出口登記鍵（`LevelExits`）**：`"manila"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

藏身於 Level 0 深處的孤立小房間，牆壁貼著淡牛皮紙色（馬尼拉色）壁紙，中央放著一張木桌、椅子與 M.E.G. 留給新手流浪者的生存手冊與指南。

## Wikidot / Fandom 設定對照

Wikidot 經典新手引導秘室，可作為獨立微維度或 Level 0 罕見特殊結構房間，讓玩家在遊戲內讀到後室指南書！

## 常見實體與威脅

無實體（絕對安全區）。

## 出入口關聯

入口：在 Level 0 迷失深處極低機率出現的單獨木門。出口：推門回到 Level 0 或透過特定方法進入 Level 1。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/the_manila_room/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("manila", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/the_manila_room.json`、`dimension_type/the_manila_room.json`、`worldgen/biome/the_manila_room.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/the_manila_room/`、`models/block/the_manila_room/`；測試放 `src/test` 與 `src/gametest` 的 `the_manila_room/`。
