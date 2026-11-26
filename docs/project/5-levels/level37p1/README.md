# Level 37.1：深暗泳池（Dark Poolrooms）

- **分類**：代表性子樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level37p1`（程式放 `src/main/java/dev/noclip/backrooms/level37p1/`）
- **建議維度 ID**：`noclip_backrooms:level_37.1`
- **出口登記鍵（`LevelExits`）**：`"37.1"`
- **參考來源**：Wikidot / Fandom 延伸

## 環境與核心特色

泳池房（Level 37）下層或陰暗分支：燈光熄滅、水溫冰冷、深水通道幽閉壓抑，並存在具腐蝕性或感染風險的異常池水（水瘟）。

## Wikidot / Fandom 設定對照

結合 Level 37 的「黑暗區域」與社群常見的暗黑泳池子層設計，為療癒的泳池房增添恐怖對照區。

## 常見實體與威脅

水瘟（Hydrolitis Plague）感染風險與深水陰影威脅。

## 出入口關聯

入口：Level 37 深入無光水域或潛入底層排水道。出口：游回亮光拱廊返回 Level 37，或通往 Level 7。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level37p1/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("37.1", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_37.1.json`、`dimension_type/level_37.1.json`、`worldgen/biome/level_37.1.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level37p1/`、`models/block/level37p1/`；測試放 `src/test` 與 `src/gametest` 的 `level37p1/`。
