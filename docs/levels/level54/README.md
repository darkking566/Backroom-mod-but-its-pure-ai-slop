# Level 54：螺旋樓梯間（Stairwell of Spirals）

- **分類**：主樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 加固門已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level54`（程式放 `src/main/java/dev/noclip/backrooms/level54/`）
- **建議維度 ID**：`noclip_backrooms:level_54`
- **出口登記鍵（`LevelExits`）**：`"54"`
- **參考來源**：Wikidot / Fandom / 狐霖後室

## 環境與核心特色

向無盡深處盤旋而下的巨型螺旋樓梯井，剝落的淺藍色潮濕牆面伴隨腐木氣味，每層轉角側牆有通往各類房間或他層的門。

## Wikidot / Fandom 設定對照

Wikidot 與中文社群經典設定一致，垂直螺旋空間在 Minecraft 128–384 高度或循環傳送階梯下極具視覺特色。Level 1 機房加固門已預留出口 `"54"`。

## 常見實體與威脅

稀疏分布的笑魘、獵犬，以及專屬特色實體（天魚、甲殼人等）。

## 出入口關聯

入口：Level 1 機房加固的門（已預留出口 `"54"`）。出口：兩側特定門可通往多個不同主樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level54/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("54", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_54.json`、`dimension_type/level_54.json`、`worldgen/biome/level_54.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level54/`、`models/block/level54/`；測試放 `src/test` 與 `src/gametest` 的 `level54/`。
