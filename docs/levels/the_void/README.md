# The Void：虛空

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.the_void`（程式放 `src/main/java/dev/noclip/backrooms/the_void/`）
- **建議維度 ID**：`noclip_backrooms:the_void`
- **出口登記鍵（`LevelExits`）**：`"void"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

存在於所有樓層夾縫之外的絕對漆黑無限墜落空間，沒有地板、沒有方向感，從深坑或破洞跌出樓層邊界時可能墜入其中。

## Wikidot / Fandom 設定對照

兩邊共同的底層世界觀邊界；本模組 Level 0 已有深坑區，之後可考慮把極端墜落事件銜接到此處。

## 常見實體與威脅

無實體，純粹的無盡墜落與虛無消耗。

## 出入口關聯

入口：跌破樓層底層結構或失敗的極端卡出。出口：極低機率在漫長墜落後隨機摔進另一樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/the_void/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("void", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/the_void.json`、`dimension_type/the_void.json`、`worldgen/biome/the_void.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/the_void/`、`models/block/the_void/`；測試放 `src/test` 與 `src/gametest` 的 `the_void/`。
