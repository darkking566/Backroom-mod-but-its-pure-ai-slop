# Level 143：粉紅大理石宮殿／聖堡

- **分類**：主樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 粉紅大理石宮殿畫已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level143`（程式放 `src/main/java/dev/noclip/backrooms/level143/`）
- **建議維度 ID**：`noclip_backrooms:level_143`
- **出口登記鍵（`LevelExits`）**：`"143"`
- **參考來源**：Fandom / Wikidot / 狐霖後室

## 環境與核心特色

Level 1 機房的入口外觀是「粉紅大理石宮殿的畫」（對應 Fandom 的 Artist's Luxury 奢華粉紅大理石鍍金大廳），而 Wikidot 保留版則為橙霧籠罩的中世紀「聖堡（The Hallowed Castle）」。

## Wikidot / Fandom 設定對照

Fandom 為粉紅大理石與金色雕飾的無盡藝術宮殿（與 Level 1 已畫好的入口畫作呼應）；Wikidot 為中世紀聖堡與聖騎士／女巫。實作時建議以粉紅大理石宮殿為主軸，或把城堡區做為外庭。

## 常見實體與威脅

Fandom：罕見異常與雕塑威脅；Wikidot：敵對聖騎士、宴會幽靈與女巫。

## 出入口關聯

入口：Level 1 機房粉紅大理石宮殿的畫（已預留出口 `"143"`）。出口：走出外圍庭院大門可通往 Level 10 或 Level 11。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level143/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("143", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_143.json`、`dimension_type/level_143.json`、`worldgen/biome/level_143.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level143/`、`models/block/level143/`；測試放 `src/test` 與 `src/gametest` 的 `level143/`。
