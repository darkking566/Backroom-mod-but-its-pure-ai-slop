# Level 999：虛空之島（The Island of the Void）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level999`（程式放 `src/main/java/dev/noclip/backrooms/level999/`）
- **建議維度 ID**：`noclip_backrooms:level_999`
- **出口登記鍵（`LevelExits`）**：`"999"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

第一集群（Cluster I）的終點：漂浮於寂靜無垠星空虛無中的一座孤寂熱帶沙灘小島，耳邊會不斷響起直指內心罪惡與遺憾的低語，唯有直面並懺悔才能真正離開。

## Wikidot / Fandom 設定對照

Wikidot 0–999 編號體系的經典終章，心理試煉感極強，在 Minecraft 裡做空島＋星空維度氣氛絕佳。

## 常見實體與威脅

無實體敵人，純粹的心理試煉與內心低語。

## 出入口關聯

入口：從 Level 998 或特定極端事件抵達。出口：完成懺悔後可返回主世界（Frontrooms）或進入更高層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level999/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("999", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_999.json`、`dimension_type/level_999.json`、`worldgen/biome/level_999.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level999/`、`models/block/level999/`；測試放 `src/test` 與 `src/gametest` 的 `level999/`。
