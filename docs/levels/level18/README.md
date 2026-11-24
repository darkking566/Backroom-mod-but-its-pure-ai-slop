# Level 18：回憶（Memories）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level18`（程式放 `src/main/java/dev/noclip/backrooms/level18/`）
- **建議維度 ID**：`noclip_backrooms:level_18`
- **出口登記鍵（`LevelExits`）**：`"18"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

幻化為流浪者 2～5 歲童年記憶中的臥室、幼稚園或老家走廊，氛圍極度懷舊孤寂，空氣中常伴隨溫柔卻悲傷的低語。

## Wikidot / Fandom 設定對照

兩邊設定高度一致，是非常經典的情感／懷舊向閾限空間層。

## 常見實體與威脅

無敵對實體，但常有象徵童年記憶的引導玩偶或聲音。

## 出入口關聯

入口：偶發從低樓層卡出。出口：面對回憶並走出記憶中的大門可安全傳往其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level18/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("18", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_18.json`、`dimension_type/level_18.json`、`worldgen/biome/level_18.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level18/`、`models/block/level18/`；測試放 `src/test` 與 `src/gametest` 的 `level18/`。
