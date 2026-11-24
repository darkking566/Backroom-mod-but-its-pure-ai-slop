# Level 197：打烊超市／水晶洞穴

- **分類**：主樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 發光自動門已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level197`（程式放 `src/main/java/dev/noclip/backrooms/level197/`）
- **建議維度 ID**：`noclip_backrooms:level_197`
- **出口登記鍵（`LevelExits`）**：`"197"`
- **參考來源**：Fandom / Wikidot / 狐霖後室

## 環境與核心特色

Level 1 機房入口是「會發光的自動門」（貼近 Fandom 的「打烊超市」無盡深夜大賣場）；Wikidot 版本則是佈滿異常發光礦石的「水晶洞穴（Crystal Caverns）」。

## Wikidot / Fandom 設定對照

Fandom（Closing Supermarket：永夜自動門無盡超市，物資多且燈光閃爍）與 Level 1 自動門入口最契合；Wikidot 為多礦脈水晶洞窟。實作時建議以深夜超市為主，或做雙區域。

## 常見實體與威脅

超市巡邏暗影／笑魘；水晶洞穴版則有礦石精神影響與洞穴實體。

## 出入口關聯

入口：Level 1 機房亮著燈的自動門（已預留出口 `"197"`）。出口：員工通道門或收銀區後門通往其他商業／倉儲層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level197/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("197", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_197.json`、`dimension_type/level_197.json`、`worldgen/biome/level_197.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level197/`、`models/block/level197/`；測試放 `src/test` 與 `src/gametest` 的 `level197/`。
