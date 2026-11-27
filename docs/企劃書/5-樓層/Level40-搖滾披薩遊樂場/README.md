# Level 40：搖滾披薩遊樂場（Roller Rockin' Pizza!）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level40`（程式放 `src/main/java/dev/noclip/backrooms/level40/`）
- **建議維度 ID**：`noclip_backrooms:level_40`
- **出口登記鍵（`LevelExits`）**：`"40"`
- **參考來源**：Wikidot

## 環境與核心特色

80 年代末復古風格的無盡室內披薩餐廳與街機遊樂場，鋪設印有星球圖案的深藍霓虹螢光地毯，充滿懷舊電子音效與奇特的發光醬汁。

## Wikidot / Fandom 設定對照

Wikidot 經典懷舊霓虹遊樂場層；Fandom 曾有廢棄錄影帶店等不同版本。

## 常見實體與威脅

幾乎無敵對實體，屬於相對安全且具特色道具的趣味樓層。

## 出入口關聯

入口：由街機或商業類樓層切入。出口：遊樂場特定機台或後門通往其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level40/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("40", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_40.json`、`dimension_type/level_40.json`、`worldgen/biome/level_40.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level40/`、`models/block/level40/`；測試放 `src/test` 與 `src/gametest` 的 `level40/`。
